package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A tiny dependency-free HTTP server backed by a plain {@link ServerSocket}. It runs while the
 * launcher settings screen is open and serves the same Wi-Fi segment, letting a phone or computer
 * read and write every setting the hidden-settings screens own - the launcher look, the Kids and
 * Elders mode (kind, allowed apps, time rules, quiz plan and weekly schedule), the parental PINs
 * and a one-tap APK update - without any third-party library.
 *
 * <p>Routes: {@code GET /} (the page), {@code GET /api/state} (all values and choices),
 * {@code POST /api/state} (write the {@code values}/{@code kids}/{@code plan}/{@code update}
 * sections), {@code POST /api/pin} (reset a PIN from a plaintext entry), {@code POST /api/update}
 * (download and install the APK), and {@code POST /api/upload} (raw wallpaper body).
 */
final class RemoteConfigServer {
    private static final String TAG = "TvHome.Remote";
    static final int PORT = 8765;
    private static final int MAX_BODY = 100 * 1024 * 1024;
    private static final byte[] HEADER_END = {'\r', '\n', '\r', '\n'};

    /** The Kids prefs file name (Kids.PREFS is private); the plan one is shared as KidsPlan.PREFS. */
    private static final String KIDS_PREFS = "kids";
    private static final String UPDATE_URL_KEY = "update_url";

    /** A request whose body failed validation; answered with 400 and the message. */
    static final class BadRequest extends Exception {
        BadRequest(String message) {
            super(message);
        }
    }

    private final Context appContext;
    private final SharedPreferences prefs;
    private final SharedPreferences kidsPrefs;
    private final SharedPreferences planPrefs;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running;

    RemoteConfigServer(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        kidsPrefs = appContext.getSharedPreferences(KIDS_PREFS, Context.MODE_PRIVATE);
        planPrefs = appContext.getSharedPreferences(KidsPlan.PREFS, Context.MODE_PRIVATE);
    }

    boolean isRunning() {
        return running;
    }

    void start() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(PORT);
        running = true;
        pool = Executors.newCachedThreadPool();
        Thread acceptThread = new Thread(this::acceptLoop, "remote-config-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (pool != null) pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                pool.execute(() -> handle(client));
            } catch (IOException e) {
                if (running) Log.w(TAG, "accept failed", e);
            }
        }
    }

    // ------------------------------------------------------------------ request handling

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(30000);
            InputStream input = s.getInputStream();
            byte[] header = readUntil(input, HEADER_END);
            String text = new String(header, StandardCharsets.ISO_8859_1);
            String[] lines = text.split("\r\n");
            String[] requestLine = lines[0].split(" ");
            if (requestLine.length < 2) {
                respond(s, 400, "text/plain", "bad request".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String method = requestLine[0];
            String path = requestLine[1];
            int contentLength = 0;
            String xFilename = null;
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon < 0) continue;
                String key = lines[i].substring(0, colon).trim().toLowerCase();
                String value = lines[i].substring(colon + 1).trim();
                if ("content-length".equals(key)) contentLength = Integer.parseInt(value);
                if ("x-filename".equals(key)) xFilename = value;
            }
            byte[] body = contentLength > 0 ? readN(input, Math.min(contentLength, MAX_BODY)) : new byte[0];
            try {
                route(s, method, path, body, xFilename);
            } catch (BadRequest bad) {
                writeJson(s, 400, new JSONObject().put("error", bad.getMessage()));
            }
        } catch (Exception e) {
            Log.w(TAG, "request failed", e);
        }
    }

    private void route(Socket socket, String method, String path, byte[] body, String xFilename)
            throws Exception {
        String base = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        if ("GET".equals(method) && ("/".equals(base) || "/index.html".equals(base))) {
            respond(socket, 200, "text/html; charset=utf-8", readAsset("remote/index.html"));
        } else if ("GET".equals(method) && "/api/state".equals(base)) {
            writeJson(socket, 200, buildState());
        } else if ("POST".equals(method) && "/api/state".equals(base)) {
            JSONObject incoming = new JSONObject(new String(body, StandardCharsets.UTF_8));
            applyState(incoming);
            writeJson(socket, 200, buildState());
        } else if ("POST".equals(method) && "/api/pin".equals(base)) {
            JSONObject incoming = new JSONObject(new String(body, StandardCharsets.UTF_8));
            applyPin(incoming);
            writeJson(socket, 200, buildState());
        } else if ("POST".equals(method) && "/api/update".equals(base)) {
            JSONObject incoming = new JSONObject(new String(body, StandardCharsets.UTF_8));
            String url = incoming.optString("url", "").trim();
            if (url.isEmpty()) url = prefs.getString(UPDATE_URL_KEY, "");
            ApkUpdater.start(appContext, url);
            writeJson(socket, 200, new JSONObject().put("started", true));
        } else if ("POST".equals(method) && "/api/upload".equals(base)) {
            String saved = saveUpload(body, xFilename);
            writeJson(socket, 200, new JSONObject().put("saved", saved));
        } else {
            respond(socket, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------ state: read

    private JSONObject buildState() throws Exception {
        JSONObject root = new JSONObject();
        root.put("values", buildLauncherValues());
        root.put("options", buildLauncherOptions());
        root.put("kids", buildKids());
        root.put("plan", buildPlan());
        root.put("update", buildUpdate());
        return root;
    }

    private JSONObject buildLauncherValues() throws Exception {
        JSONObject values = new JSONObject();
        values.put("columns", prefs.getInt("columns", 4));
        values.put(Config.ROWS_BELOW_DOCK_KEY, prefs.getInt(Config.ROWS_BELOW_DOCK_KEY, 0));
        values.put(Config.DOCK_OPACITY_KEY, prefs.getInt(Config.DOCK_OPACITY_KEY, 100));
        values.put("animations", prefs.getBoolean("animations", true));
        values.put(PersonalAppearance.MODE_KEY,
                prefs.getString(PersonalAppearance.MODE_KEY, "night"));
        values.put(PersonalAppearance.BACKGROUND_KEY,
                prefs.getString(PersonalAppearance.BACKGROUND_KEY, "app"));
        values.put(PersonalAppearance.AMBIENT_MOTION_KEY,
                prefs.getBoolean(PersonalAppearance.AMBIENT_MOTION_KEY, true));
        values.put(Config.WALLPAPER_CHOICE_KEY,
                prefs.getString(Config.WALLPAPER_CHOICE_KEY, WallpaperRotation.CHOICE_NONE));
        values.put(Weather.KEY_CITY, prefs.getString(Weather.KEY_CITY, Weather.DEFAULT_CITY));
        values.put(OpenOnStart.KEY, prefs.getString(OpenOnStart.KEY, ""));
        return values;
    }

    private JSONObject buildLauncherOptions() throws Exception {
        JSONObject options = new JSONObject();
        options.put("columns", new JSONArray(Arrays.asList(4, 5, 6, 7)));
        options.put(Config.ROWS_BELOW_DOCK_KEY, new JSONArray(Arrays.asList(0, 1, 2, 3)));
        options.put(Config.DOCK_OPACITY_KEY, new JSONArray(Arrays.asList(100, 70, 50, 30)));
        options.put(PersonalAppearance.MODE_KEY,
                new JSONArray(Arrays.asList("night", "day", "auto")));
        options.put(PersonalAppearance.BACKGROUND_KEY,
                new JSONArray(Arrays.asList("app", "wallpaper", "bing")));
        JSONArray cities = new JSONArray();
        for (String[] city : Weather.CITIES) cities.put(city[0]);
        options.put("cities", cities);
        JSONArray wallpapers = new JSONArray();
        for (File f : wallpaperFiles()) wallpapers.put(f.getName());
        options.put("wallpapers", wallpapers);
        return options;
    }

    private JSONObject buildKids() throws Exception {
        JSONObject block = new JSONObject();
        JSONObject values = new JSONObject();
        values.put("kind", kidsPrefs.getString("kind", "kids"));
        values.put("enabled", kidsPrefs.getBoolean("enabled", false));
        values.put("weekday_minutes", kidsPrefs.getInt("weekday_minutes", 0));
        values.put("weekend_minutes", kidsPrefs.getInt("weekend_minutes", 0));
        values.put("end_minute", kidsPrefs.getInt("end_minute", 0));
        values.put("break_every", kidsPrefs.getInt("break_every", 0));
        values.put("stars", kidsPrefs.getInt("stars", 0));
        values.put("pin_set", kidsPrefs.getString("pin_hash", null) != null);
        values.put("pin_arrows", kidsPrefs.getBoolean("pin_arrows", false));
        values.put("elders_pin_set", kidsPrefs.getString("pin_hash_elders", null) != null);
        values.put("elders_pin_arrows", kidsPrefs.getBoolean("pin_arrows_elders", false));
        values.put("admin_pin_set", kidsPrefs.getString("admin_pin_hash", null) != null);
        block.put("values", values);

        List<String> allowed = Kids.allowed();
        JSONArray allowedArray = new JSONArray();
        for (String pkg : allowed) allowedArray.put(pkg);
        block.put("allowed", allowedArray);

        // Every Leanback app, with its current picked state and order.
        JSONArray apps = new JSONArray();
        for (AppInfo info : leanbackApps()) {
            JSONObject app = new JSONObject();
            app.put("pkg", info.pkg);
            app.put("label", info.label);
            int order = allowed.indexOf(info.pkg);
            app.put("allowed", order >= 0);
            app.put("order", order >= 0 ? order + 1 : 0);
            apps.put(app);
        }
        block.put("apps", apps);

        JSONObject options = new JSONObject();
        options.put("kind", new JSONArray(Arrays.asList("kids", "elders")));
        options.put("weekday_minutes", new JSONArray(Arrays.asList(-1, 10, 30, 60, 90, 120)));
        options.put("weekend_minutes", new JSONArray(Arrays.asList(0, 10, 30, 60, 90, 120)));
        options.put("end_minute", new JSONArray(Arrays.asList(0, 20 * 60, 21 * 60, 22 * 60)));
        options.put("break_every", new JSONArray(Arrays.asList(0, 5, 10, 30)));
        block.put("options", options);
        return block;
    }

    private JSONObject buildPlan() throws Exception {
        JSONObject block = new JSONObject();
        JSONObject values = new JSONObject();
        values.put("quiz_gate", planPrefs.getBoolean("quiz_gate", false));
        values.put("stage", planPrefs.getString("stage", KidsPlan.STAGE_PRIMARY));
        values.put("grade", planPrefs.getInt("grade", 1));
        values.put("subject_chinese", planPrefs.getBoolean("subject_chinese", true));
        values.put("subject_math", planPrefs.getBoolean("subject_math", true));
        values.put("subject_english", planPrefs.getBoolean("subject_english", true));
        values.put("subject_history", planPrefs.getBoolean("subject_history", false));
        values.put("subject_physics", planPrefs.getBoolean("subject_physics", false));
        values.put("subject_chemistry", planPrefs.getBoolean("subject_chemistry", false));
        values.put("subject_biology", planPrefs.getBoolean("subject_biology", false));
        values.put("subject_geography", planPrefs.getBoolean("subject_geography", false));
        values.put("subject_politics", planPrefs.getBoolean("subject_politics", false));
        values.put("session_minutes", planPrefs.getInt("session_minutes", 10));
        values.put("questions_per_round", planPrefs.getInt("questions_per_round", 5));
        values.put("pass_ratio", planPrefs.getInt("pass_ratio", 60));
        values.put("rounds_per_day", planPrefs.getInt("rounds_per_day", 3));
        values.put("play_mode", planPrefs.getString("play_mode", KidsPlan.MODE_ROUND));
        values.put("auto_next", planPrefs.getInt("auto_next", 3));
        values.put("extra_enabled", planPrefs.getBoolean("extra_enabled", true));
        values.put("star_reward", planPrefs.getBoolean("star_reward", false));
        values.put("holiday_weekend", planPrefs.getBoolean("holiday_weekend", false));
        values.put("week_slots", planPrefs.getString("week_slots", ""));
        block.put("values", values);

        JSONObject options = new JSONObject();
        options.put("stage", new JSONArray(Arrays.asList(
                KidsPlan.STAGE_PRIMARY, KidsPlan.STAGE_MIDDLE, KidsPlan.STAGE_HIGH)));
        options.put("session_minutes", new JSONArray(Arrays.asList(5, 10, 15, 20)));
        options.put("questions_per_round", new JSONArray(Arrays.asList(3, 5, 8)));
        options.put("pass_ratio", new JSONArray(Arrays.asList(50, 60, 80)));
        options.put("rounds_per_day", new JSONArray(Arrays.asList(1, 2, 3, 5)));
        options.put("play_mode", new JSONArray(Arrays.asList(
                KidsPlan.MODE_ROUND, KidsPlan.MODE_STREAK, KidsPlan.MODE_PRACTICE)));
        options.put("auto_next", new JSONArray(Arrays.asList(0, 3, 5)));
        block.put("options", options);
        return block;
    }

    private JSONObject buildUpdate() throws Exception {
        JSONObject update = new JSONObject();
        update.put("url", prefs.getString(UPDATE_URL_KEY, ""));
        update.put("can_install", ApkUpdater.canRequestInstall(appContext));
        update.put("state", ApkUpdater.state());
        update.put("progress", ApkUpdater.progress());
        update.put("message", ApkUpdater.message());
        return update;
    }

    // ------------------------------------------------------------------ state: write

    private void applyState(JSONObject incoming) throws BadRequest {
        if (incoming.has("values")) applyLauncher(incoming.optJSONObject("values"));
        if (incoming.has("kids")) applyKids(incoming.optJSONObject("kids"));
        if (incoming.has("plan")) applyPlan(incoming.optJSONObject("plan"));
        if (incoming.has("update")) applyUpdate(incoming.optJSONObject("update"));
    }

    private void applyLauncher(JSONObject incoming) {
        if (incoming == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray names = incoming.names();
        boolean touched = false;
        for (int i = 0; names != null && i < names.length(); i++) {
            String key = names.optString(i);
            if (!LAUNCHER_KEYS.contains(key)) continue;
            Object value = incoming.opt(key);
            if (value == null) continue;
            if (LAUNCHER_BOOL.contains(key)) {
                editor.putBoolean(key, Boolean.parseBoolean(value.toString()));
                touched = true;
            } else if (LAUNCHER_INT.contains(key)) {
                Integer n = parseInt(value);
                if (n != null && validLauncherInt(key, n)) {
                    editor.putInt(key, n);
                    touched = true;
                }
            } else {
                String s = value.toString();
                if (validLauncherString(key, s)) {
                    editor.putString(key, s);
                    touched = true;
                }
            }
        }
        if (touched) {
            editor.putLong(Config.SETTINGS_REVISION_KEY,
                    prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        }
    }

    private boolean validLauncherInt(String key, int value) {
        if ("columns".equals(key)) return value >= 4 && value <= 7;
        if (Config.ROWS_BELOW_DOCK_KEY.equals(key)) return value >= 0 && value <= 3;
        if (Config.DOCK_OPACITY_KEY.equals(key)) return value == 100 || value == 7
                || value == 50 || value == 30;
        return false;
    }

    private boolean validLauncherString(String key, String value) {
        if (PersonalAppearance.MODE_KEY.equals(key)) {
            return "night".equals(value) || "day".equals(value) || "auto".equals(value);
        }
        if (PersonalAppearance.BACKGROUND_KEY.equals(key)) {
            return "app".equals(value) || "wallpaper".equals(value) || "bing".equals(value);
        }
        if (Weather.KEY_CITY.equals(key)) {
            for (String[] city : Weather.CITIES) if (city[0].equals(value)) return true;
            return false;
        }
        if (Config.WALLPAPER_CHOICE_KEY.equals(key)) {
            if (WallpaperRotation.CHOICE_NONE.equals(value)) return true;
            for (File f : wallpaperFiles()) if (f.getName().equals(value)) return true;
            return false;
        }
        if (OpenOnStart.KEY.equals(key)) return value.length() <= 200;
        return false;
    }

    private void applyKids(JSONObject incoming) throws BadRequest {
        if (incoming == null) return;
        JSONObject values = incoming.optJSONObject("values");
        if (values != null) {
            JSONArray names = values.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String key = names.optString(i);
                Object value = values.opt(key);
                if (value == null) continue;
                switch (key) {
                    case "kind":
                        String kind = value.toString();
                        if ("kids".equals(kind)) Kids.chooseKind(Kids.Kind.KIDS);
                        else if ("elders".equals(kind)) Kids.chooseKind(Kids.Kind.ELDERS);
                        else throw new BadRequest("kind must be kids or elders");
                        break;
                    case "enabled":
                        Kids.chooseEnabled(Boolean.parseBoolean(value.toString()));
                        break;
                    case "weekday_minutes":
                    case "weekend_minutes":
                    case "end_minute":
                    case "break_every":
                        Integer n = parseInt(value);
                        if (n == null || !KIDS_RULE_OPTIONS.get(key).contains(n)) {
                            throw new BadRequest("bad value for " + key);
                        }
                        break;
                    default:
                        break;
                }
            }
            // Rule fields are applied together against the current Rules, then written once.
            if (values.has("weekday_minutes") || values.has("weekend_minutes")
                    || values.has("end_minute") || values.has("break_every")) {
                Kids.Rules r = Kids.rules();
                if (values.has("weekday_minutes")) {
                    r = r.withWeekday(values.getInt("weekday_minutes"));
                }
                if (values.has("weekend_minutes")) {
                    r = r.withWeekend(values.getInt("weekend_minutes"));
                }
                if (values.has("end_minute")) {
                    r = r.copy(-1, -1, -1, values.getInt("end_minute"), -1, -1);
                }
                if (values.has("break_every")) {
                    r = r.copy(-1, -1, -1, -1, values.getInt("break_every"), -1);
                }
                if (r.valid()) Kids.chooseRules(r);
                else throw new BadRequest("resulting rules are not valid");
            }
        }

        if (incoming.has("allowed")) {
            JSONArray array = incoming.optJSONArray("allowed");
            Set<String> installable = new HashSet<>();
            for (AppInfo info : leanbackApps()) installable.add(info.pkg);
            List<String> next = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; array != null && i < array.length(); i++) {
                String pkg = array.optString(i).trim();
                if (pkg.isEmpty() || !installable.contains(pkg) || seen.contains(pkg)) continue;
                seen.add(pkg);
                next.add(pkg);
            }
            Kids.chooseAllowed(next);
        }
        KidsWatch.poke();
    }

    private void applyPlan(JSONObject incoming) throws BadRequest {
        if (incoming == null) return;
        JSONObject values = incoming.optJSONObject("values");
        if (values == null) return;
        KidsPlan plan = Kids.plan();
        JSONArray names = values.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String key = names.optString(i);
            Object value = values.opt(key);
            if (value == null) continue;
            switch (key) {
                case "quiz_gate":
                    plan.quizGate = Boolean.parseBoolean(value.toString());
                    break;
                case "extra_enabled":
                    plan.extraEnabled = Boolean.parseBoolean(value.toString());
                    break;
                case "star_reward":
                    plan.starReward = Boolean.parseBoolean(value.toString());
                    break;
                case "holiday_weekend":
                    plan.holidayWeekend = Boolean.parseBoolean(value.toString());
                    break;
                case "stage":
                    String stage = value.toString();
                    if (!KidsPlan.STAGE_PRIMARY.equals(stage) && !KidsPlan.STAGE_MIDDLE.equals(stage)
                            && !KidsPlan.STAGE_HIGH.equals(stage)) {
                        throw new BadRequest("bad stage");
                    }
                    plan.stage = stage;
                    break;
                case "grade":
                    Integer grade = parseInt(value);
                    if (grade == null) throw new BadRequest("bad grade");
                    plan.grade = grade;
                    break;
                case "session_minutes":
                case "questions_per_round":
                case "pass_ratio":
                case "rounds_per_day":
                case "auto_next":
                    Integer n = parseInt(value);
                    if (n == null || !PLAN_OPTIONS.get(key).contains(n)) {
                        throw new BadRequest("bad value for " + key);
                    }
                    setPlanInt(plan, key, n);
                    break;
                case "play_mode":
                    String mode = value.toString();
                    if (!KidsPlan.MODE_ROUND.equals(mode) && !KidsPlan.MODE_STREAK.equals(mode)
                            && !KidsPlan.MODE_PRACTICE.equals(mode)) {
                        throw new BadRequest("bad play mode");
                    }
                    plan.playMode = mode;
                    break;
                case "week_slots":
                    String slots = value.toString();
                    if (!slots.isEmpty() && !KidsPlan.gridOk(slots)) {
                        throw new BadRequest("week_slots must be 336 chars of 0/1 or empty");
                    }
                    plan.weekSlots = slots;
                    break;
                default:
                    if (key.startsWith("subject_")) {
                        String subject = key.substring("subject_".length());
                        plan.setSubject(subject, Boolean.parseBoolean(value.toString()));
                    }
                    break;
            }
        }
        // Keep the grade inside its stage and at least one subject, as the TV does.
        plan.grade = plan.grade();
        if (plan.subjectCount() == 0) throw new BadRequest("keep at least one subject");
        Kids.savePlan();
        KidsWatch.poke();
    }

    private static void setPlanInt(KidsPlan plan, String key, int n) {
        switch (key) {
            case "session_minutes": plan.sessionMinutes = n; break;
            case "questions_per_round": plan.questionsPerRound = n; break;
            case "pass_ratio": plan.passRatio = n; break;
            case "rounds_per_day": plan.roundsPerDay = n; break;
            case "auto_next": plan.autoNextSeconds = n; break;
            default: break;
        }
    }

    private void applyUpdate(JSONObject incoming) {
        if (incoming == null) return;
        if (incoming.has("url")) {
            String url = incoming.optString("url", "").trim();
            if (url.isEmpty()) prefs.edit().remove(UPDATE_URL_KEY).apply();
            else if (url.length() <= 500) prefs.edit().putString(UPDATE_URL_KEY, url).apply();
        }
    }

    // ------------------------------------------------------------------ PIN

    private void applyPin(JSONObject incoming) throws BadRequest {
        String target = incoming.optString("target", "");
        boolean arrows = incoming.optBoolean("arrows", false);
        String pin = incoming.optString("pin", "");
        if (pin.length() != 4) throw new BadRequest("PIN must be exactly four entries");
        if (arrows) {
            for (int i = 0; i < 4; i++) {
                char c = pin.charAt(i);
                if (c != 'U' && c != 'D' && c != 'L' && c != 'R') {
                    throw new BadRequest("arrow PIN only uses U D L R");
                }
            }
        } else {
            for (int i = 0; i < 4; i++) {
                if (!Character.isDigit(pin.charAt(i))) {
                    throw new BadRequest("digit PIN only uses 0-9");
                }
            }
        }
        switch (target) {
            case "kids":
                Kids.resetPin(Kids.Kind.KIDS, pin, arrows);
                break;
            case "elders":
                Kids.resetPin(Kids.Kind.ELDERS, pin, arrows);
                break;
            case "admin":
                Kids.chooseAdminPin(pin, arrows);
                break;
            default:
                throw new BadRequest("target must be kids, elders or admin");
        }
    }

    // ------------------------------------------------------------------ apps / upload

    private static final class AppInfo {
        final String pkg;
        final String label;

        AppInfo(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    private List<AppInfo> leanbackApps() {
        PackageManager pm = appContext.getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<AppInfo> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(leanback, 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(appContext.getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            list.add(new AppInfo(pkg, String.valueOf(ri.loadLabel(pm))));
        }
        list.sort(Comparator.comparing(a -> a.label.toLowerCase()));
        return list;
    }

    private String saveUpload(byte[] body, String requested) throws IOException, BadRequest {
        if (body.length == 0) throw new BadRequest("empty upload");
        String filename = sanitize(requested);
        File base = Config.dir(appContext);
        if (base == null) throw new BadRequest("storage unavailable");
        File dir = new File(base, ThemeFiles.WALLPAPERS);
        if (!dir.exists()) dir.mkdirs();
        File target = new File(dir, filename);
        try (OutputStream out = new java.io.FileOutputStream(target)) {
            out.write(body);
        }
        return filename;
    }

    private String sanitize(String requested) {
        String name = requested == null ? "" : requested;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        String lower = name.toLowerCase();
        if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp"))) {
            name = name + ".jpg";
        }
        if (name.isEmpty() || name.startsWith(".")) name = "upload-" + System.nanoTime() + ".jpg";
        return name;
    }

    private List<File> wallpaperFiles() {
        File dir = Config.dir(appContext);
        if (dir == null) return new ArrayList<>();
        return WallpaperRotation.images(new File(dir, ThemeFiles.WALLPAPERS));
    }

    private static Integer parseInt(Object value) {
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ IO helpers

    private byte[] readAsset(String name) throws IOException {
        try (InputStream in = appContext.getAssets().open(name)) {
            return readAll(in);
        }
    }

    private static byte[] readUntil(InputStream in, byte[] marker) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int last = 0;
        int b;
        while ((b = in.read()) != -1) {
            out.write(b);
            byte[] current = out.toByteArray();
            if (current.length >= marker.length) {
                boolean match = true;
                for (int i = 0; i < marker.length; i++) {
                    if (current[current.length - marker.length + i] != marker[i]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    last = current.length - marker.length;
                    break;
                }
            }
        }
        byte[] all = out.toByteArray();
        return Arrays.copyOf(all, last);
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] buffer = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buffer, read, n - read);
            if (r < 0) break;
            read += r;
        }
        return Arrays.copyOf(buffer, read);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int r;
        while ((r = in.read(buffer)) != -1) out.write(buffer, 0, r);
        return out.toByteArray();
    }

    private void writeJson(Socket socket, int status, JSONObject json) throws IOException {
        respond(socket, status, "application/json; charset=utf-8",
                json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void respond(Socket socket, int status, String contentType, byte[] content)
            throws IOException {
        String reason;
        switch (status) {
            case 200: reason = "OK"; break;
            case 400: reason = "Bad Request"; break;
            case 404: reason = "Not Found"; break;
            default: reason = "Error"; break;
        }
        OutputStream out = socket.getOutputStream();
        String header = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + content.length + "\r\n"
                + "Connection: close\r\n"
                + "Access-Control-Allow-Origin: *\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.ISO_8859_1));
        out.write(content);
        out.flush();
    }

    // ------------------------------------------------------------------ key tables

    private static final Set<String> LAUNCHER_KEYS = new HashSet<>(Arrays.asList(
            "columns", Config.ROWS_BELOW_DOCK_KEY, Config.DOCK_OPACITY_KEY, "animations",
            PersonalAppearance.MODE_KEY, PersonalAppearance.BACKGROUND_KEY,
            PersonalAppearance.AMBIENT_MOTION_KEY,
            Config.WALLPAPER_CHOICE_KEY, Weather.KEY_CITY, OpenOnStart.KEY));

    private static final Set<String> LAUNCHER_BOOL = new HashSet<>(Arrays.asList(
            "animations", PersonalAppearance.AMBIENT_MOTION_KEY));

    private static final Set<String> LAUNCHER_INT = new HashSet<>(Arrays.asList(
            "columns", Config.ROWS_BELOW_DOCK_KEY, Config.DOCK_OPACITY_KEY));

    private static final Map<String, Set<Integer>> KIDS_RULE_OPTIONS;
    private static final Map<String, Set<Integer>> PLAN_OPTIONS;

    static {
        KIDS_RULE_OPTIONS = new java.util.HashMap<>();
        KIDS_RULE_OPTIONS.put("weekday_minutes",
                new HashSet<>(Arrays.asList(-1, 10, 30, 60, 90, 120)));
        KIDS_RULE_OPTIONS.put("weekend_minutes",
                new HashSet<>(Arrays.asList(0, 10, 30, 60, 90, 120)));
        KIDS_RULE_OPTIONS.put("end_minute",
                new HashSet<>(Arrays.asList(0, 20 * 60, 21 * 60, 22 * 60)));
        KIDS_RULE_OPTIONS.put("break_every", new HashSet<>(Arrays.asList(0, 5, 10, 30)));

        PLAN_OPTIONS = new java.util.HashMap<>();
        PLAN_OPTIONS.put("session_minutes", new HashSet<>(Arrays.asList(5, 10, 15, 20)));
        PLAN_OPTIONS.put("questions_per_round", new HashSet<>(Arrays.asList(3, 5, 8)));
        PLAN_OPTIONS.put("pass_ratio", new HashSet<>(Arrays.asList(50, 60, 80)));
        PLAN_OPTIONS.put("rounds_per_day", new HashSet<>(Arrays.asList(1, 2, 3, 5)));
        PLAN_OPTIONS.put("auto_next", new HashSet<>(Arrays.asList(0, 3, 5)));
    }
}
