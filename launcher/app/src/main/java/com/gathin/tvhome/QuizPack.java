package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The optional question pack: a manifest and one JSON per grade, downloaded from wherever the author
 * hosts them (OpenList in front of a Quark drive, or any plain HTTP folder).
 *
 * <p>The manifest is a single file the author edits when the questions change:
 *
 * <pre>
 * {"version": "2026-11",
 *  "files": [{"name": "primary-1.json", "url": "http://host/d/quark/quiz/primary-1.json",
 *             "sha256": "..."}]}
 * </pre>
 *
 * <p>Every file is checked before it goes in: the question parse must succeed, and the hash must
 * match when the manifest carries one. A file only replaces its bundled twin once it has passed, so a
 * half finished download can never leave the bank empty.
 *
 * <p>The address comes from the {@code quiz_pack_url} string in config.xml, or from the same key
 * saved in this package's preferences - which the hidden settings row "question pack" writes on
 * download, so a build without a compiled-in address can still be pointed at one.
 */
public final class QuizPack {

    private static final String PREFS = "quiz_pack";
    private static final String URL_KEY = "url";
    private static final String VERSION_KEY = "version";
    private static final String STAMP_KEY = "at";

    /** Where the download got to, for the row's hint. */
    public interface Progress {
        void onStep(String name, int done, int total);
    }

    private QuizPack() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Every manifest address worth trying, in order: the one saved from the settings page or the
     * web form, then the LAN mirror, then the WAN one. The TV sits next to the OpenList box, so the
     * LAN address wins when it is there at all.
     */
    public static List<String> candidates(Context c) {
        List<String> out = new ArrayList<>();
        String saved = prefs(c).getString(URL_KEY, "").trim();
        if (!saved.isEmpty()) out.add(saved);
        for (int id : new int[]{R.string.quiz_pack_url, R.string.quiz_pack_url_backup}) {
            String u = c.getString(id).trim();
            if (!u.isEmpty() && !out.contains(u)) out.add(u);
        }
        return out;
    }

    /** The manifest address in use: the saved one, else the first compiled default. */
    public static String url(Context c) {
        List<String> out = candidates(c);
        return out.isEmpty() ? "" : out.get(0);
    }

    public static void setUrl(Context c, String url) {
        prefs(c).edit().putString(URL_KEY, url == null ? "" : url.trim()).apply();
    }

    /** The version of the pack in place, or "" when the bundled files are still the ones in use. */
    public static String version(Context c) {
        return prefs(c).getString(VERSION_KEY, "");
    }

    public static String lastDownload(Context c) {
        long at = prefs(c).getLong(STAMP_KEY, 0);
        return at == 0 ? "" : Kids.day();
    }

    /**
     * Downloads the manifest and every grade file it lists. Runs on a worker thread: it touches the
     * network and the file system, and the caller shows the result.
     *
     * @return how many files went in
     */
    public static int sync(Context c, Progress progress) throws Exception {
        Exception last = null;
        for (String base : candidates(c)) {
            try {
                return syncFrom(c, base, progress);
            } catch (Exception e) {
                // A mirror that is down must not stop the next one from answering.
                last = e;
                Log.w("QuizPack", "mirror failed " + base + ": " + e);
            }
        }
        throw last == null ? new Exception("no pack url") : last;
    }

    /**
     * Downloads a whole manifest from one address. Runs on a worker thread: it touches the network
     * and the file system, and the caller shows the result.
     *
     * @return how many files went in
     */
    private static int syncFrom(Context c, String base, Progress progress) throws Exception {
        if (base.isEmpty()) throw new Exception("no pack url");
        String manifest = new String(get(base, 60000), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(manifest);
        JSONArray files = root.getJSONArray("files");
        File dir = KidsBank.packDir(c);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new Exception("no pack folder");
        String folder = base.substring(0, base.lastIndexOf('/') + 1);
        int done = 0;
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            String name = f.getString("name");
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            if (!name.endsWith(".json")) continue;
            String link = f.optString("url", "");
            if (link.isEmpty()) link = folder + name;
            try {
                byte[] body = getWithRetry(link, 180000);
                String sha = f.optString("sha256", "").trim().toLowerCase(Locale.US);
                // A short manifest entry (the author once wrote 16 hex digits) still pins the
                // file: the computed digest has to start with it.
                if (!sha.isEmpty() && !sha256(body).startsWith(sha)) {
                    throw new Exception("checksum " + name);
                }
                String text = new String(body, StandardCharsets.UTF_8);
                if (!KidsBank.usable(text)) throw new Exception("bad file " + name);
                File tmp = new File(dir, name + ".tmp");
                write(tmp, body);
                File target = new File(dir, name);
                if (target.exists() && !target.delete()) throw new Exception("busy " + name);
                if (!tmp.renameTo(target)) throw new Exception("rename " + name);
                done++;
            } catch (Exception e) {
                // One bad file must not sink the twelve: it is listed and retried next time.
                Log.w("QuizPack", "failed " + name + ": " + e);
                missing.add(name);
            }
            // Quark behind OpenList dislikes a burst of reads; give it a moment between files.
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
                // carry on
            }
            if (progress != null) progress.onStep(name, done, files.length());
        }
        if (!missing.isEmpty()) {
            throw new Exception("left " + missing.size() + ": " + missing);
        }
        prefs(c).edit()
                .putString(VERSION_KEY, root.optString("version", ""))
                .putLong(STAMP_KEY, System.currentTimeMillis())
                .apply();
        KidsBank.forget();
        return done;
    }

    private static volatile boolean syncing;

    /**
     * Pulls the full pack in the background when nothing has been downloaded yet. The apk only
     * carries a few dozen questions per grade as an offline floor, so the first time the quiz opens
     * on a fresh install this fetches the real bank; a failed fetch just leaves the floor in place
     * and the next open tries again.
     */
    public static void ensure(final Context c) {
        if (syncing) return;
        // A version is only written once every listed file went in, so it doubles as "complete".
        // Checking for "some json on disk" instead would freeze a half finished pack in place.
        if (!version(c).isEmpty()) return;
        syncing = true;
        final Context app = c.getApplicationContext();
        Log.i("QuizPack", "no pack on device, fetching " + url(c));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int done = sync(app, null);
                    Log.i("QuizPack", "pack ready: " + done + " files, version " + version(app));
                } catch (Exception e) {
                    Log.w("QuizPack", "pack fetch failed, bundled floor kept: " + e);
                } finally {
                    syncing = false;
                }
            }
        }, "quiz-pack").start();
    }

    /** Throws away the downloaded pack, back to the bundled questions. */
    public static void clear(Context c) {
        File dir = KidsBank.packDir(c);
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        prefs(c).edit().putString(VERSION_KEY, "").putLong(STAMP_KEY, 0).apply();
        KidsBank.forget();
    }

    /** OpenList in front of Quark drops a long read now and then; three tries ride that out. */
    private static byte[] getWithRetry(String link, int readTimeout) throws Exception {
        Exception last = null;
        for (int i = 0; i < 3; i++) {
            try {
                return get(link, readTimeout);
            } catch (Exception e) {
                last = e;
                try {
                    Thread.sleep(600L * (i + 1));
                } catch (InterruptedException ignored) {
                    // go straight to the next try
                }
            }
        }
        throw last;
    }

    private static byte[] get(String link, int readTimeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(link).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(readTimeout);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "CleanMiTV/quiz");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void write(File file, byte[] body) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(body);
            out.flush();
        }
    }

    private static String sha256(byte[] body) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] sum = md.digest(body);
        StringBuilder sb = new StringBuilder(sum.length * 2);
        for (byte b : sum) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }
}
