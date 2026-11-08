package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A tiny dependency-free HTTP server backed by a plain {@link ServerSocket}. It runs only while the
 * launcher settings screen is open and serves the same Wi-Fi segment, letting a phone or computer read
 * and write every launcher setting and upload a wallpaper without any third-party library.
 *
 * <p>Routes: {@code GET /} (the settings page), {@code GET /api/state} (current values and the allowed
 * choices), {@code POST /api/state} (write a whitelisted set of keys), and {@code POST /api/upload}
 * (raw image body, target name in the {@code X-Filename} header).
 */
final class RemoteConfigServer {
    private static final String TAG = "TvHome.Remote";
    static final int PORT = 8765;
    private static final int MAX_BODY = 10 * 1024 * 1024;
    private static final byte[] HEADER_END = {'\r', '\n', '\r', '\n'};

    private final Context appContext;
    private final SharedPreferences prefs;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running;

    RemoteConfigServer(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
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
            s.setSoTimeout(10000);
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
            route(s, method, path, body, xFilename);
        } catch (Exception e) {
            Log.w(TAG, "request failed", e);
        }
    }

    private void route(Socket socket, String method, String path, byte[] body, String xFilename)
            throws IOException {
        String base = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        if ("GET".equals(method) && ("/".equals(base) || "/index.html".equals(base))) {
            byte[] page = readAsset("remote/index.html");
            respond(socket, 200, "text/html; charset=utf-8", page);
        } else if ("GET".equals(method) && "/api/state".equals(base)) {
            respond(socket, 200, "application/json; charset=utf-8",
                    buildState().toString().getBytes(StandardCharsets.UTF_8));
        } else if ("POST".equals(method) && "/api/state".equals(base)) {
            JSONObject incoming = new JSONObject(new String(body, StandardCharsets.UTF_8));
            applyState(incoming);
            respond(socket, 200, "application/json; charset=utf-8",
                    buildState().toString().getBytes(StandardCharsets.UTF_8));
        } else if ("POST".equals(method) && "/api/upload".equals(base)) {
            String saved = saveUpload(body, xFilename);
            JSONObject result = new JSONObject().put("saved", saved);
            respond(socket, 200, "application/json; charset=utf-8",
                    result.toString().getBytes(StandardCharsets.UTF_8));
        } else {
            respond(socket, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------ state

    private JSONObject buildState() throws Exception {
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

        return new JSONObject().put("values", values).put("options", options);
    }

    private void applyState(JSONObject incoming) {
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray names = incoming.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            if (!ALLOWED_KEYS.contains(key)) continue;
            Object value = incoming.opt(key);
            if (value == null) continue;
            if (BOOLEAN_KEYS.contains(key)) {
                editor.putBoolean(key, Boolean.parseBoolean(value.toString()));
            } else if (INTEGER_KEYS.contains(key)) {
                int n = Integer.parseInt(value.toString());
                if (isValidInt(key, n)) editor.putInt(key, n);
            } else {
                String s = value.toString();
                if (isValidString(key, s)) editor.putString(key, s);
            }
        }
        long revision = prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1;
        editor.putLong(Config.SETTINGS_REVISION_KEY, revision);
        editor.apply();
    }

    private boolean isValidInt(String key, int value) {
        if ("columns".equals(key)) return value >= 4 && value <= 7;
        if (Config.ROWS_BELOW_DOCK_KEY.equals(key)) return value >= 0 && value <= 3;
        if (Config.DOCK_OPACITY_KEY.equals(key)) return value == 100 || value == 70
                || value == 50 || value == 30;
        return false;
    }

    private boolean isValidString(String key, String value) {
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

    // ------------------------------------------------------------------ upload

    private String saveUpload(byte[] body, String requested) throws IOException {
        if (body.length == 0) throw new IOException("empty upload");
        String filename = sanitize(requested);
        File dir = new File(Config.dir(appContext), ThemeFiles.WALLPAPERS);
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

    private void respond(Socket socket, int status, String contentType, byte[] content)
            throws IOException {
        String reason = status == 200 ? "OK" : status == 404 ? "Not Found" : "Bad Request";
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

    private static final Set<String> ALLOWED_KEYS = new HashSet<>(Arrays.asList(
            "columns", Config.ROWS_BELOW_DOCK_KEY, Config.DOCK_OPACITY_KEY, "animations",
            PersonalAppearance.MODE_KEY, PersonalAppearance.BACKGROUND_KEY,
            PersonalAppearance.AMBIENT_MOTION_KEY, Config.WALLPAPER_CHOICE_KEY,
            Weather.KEY_CITY, OpenOnStart.KEY));

    private static final Set<String> BOOLEAN_KEYS = new HashSet<>(Arrays.asList(
            "animations", PersonalAppearance.AMBIENT_MOTION_KEY));

    private static final Set<String> INTEGER_KEYS = new HashSet<>(Arrays.asList(
            "columns", Config.ROWS_BELOW_DOCK_KEY, Config.DOCK_OPACITY_KEY));
}
