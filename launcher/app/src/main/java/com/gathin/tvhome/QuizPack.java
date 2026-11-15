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
    private static final String CHECK_KEY = "checked";
    /** A finished pack is only re-checked against the mirror this often. */
    private static final long CHECK_GAP = 5 * 60 * 1000L;

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
        // Local first, then the author's reverse proxy, then GitHub last: a browser reaches GitHub
        // through a proxy, but a TV on a bare link often cannot open it at all, so it must not stand
        // in front of a mirror that does answer.
        for (int id : new int[]{R.string.quiz_pack_url, R.string.quiz_pack_url_backup,
                R.string.quiz_pack_url_github}) {
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
                .putLong(CHECK_KEY, System.currentTimeMillis())
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
        final Context app = c.getApplicationContext();
        final String have = version(c);
        if (!have.isEmpty()) {
            // The pack is complete, but the mirror may have a newer one - that is the whole point of
            // "题库更新". Probing costs one small manifest read, so it is throttled rather than run
            // on every open, and a probe that fails changes nothing.
            long checked = prefs(app).getLong(CHECK_KEY, 0);
            if (System.currentTimeMillis() - checked < CHECK_GAP) return;
            prefs(app).edit().putLong(CHECK_KEY, System.currentTimeMillis()).apply();
            syncing = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String remote = remoteVersion(app);
                        if (remote.isEmpty() || remote.equals(have)) {
                            Log.i("QuizPack", "pack up to date: " + have);
                            return;
                        }
                        Log.i("QuizPack", "pack out of date " + have + " -> " + remote);
                        int done = sync(app, null);
                        Log.i("QuizPack", "pack ready: " + done + " files, version " + version(app));
                    } catch (Exception e) {
                        Log.w("QuizPack", "pack check failed, keeping " + have + ": " + e);
                    } finally {
                        syncing = false;
                    }
                }
            }, "quiz-pack").start();
            return;
        }
        // A version is only written once every listed file went in, so it doubles as "complete".
        // Checking for "some json on disk" instead would freeze a half finished pack in place.
        syncing = true;
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

    /** The version a mirror is serving, "" when none of them answers. */
    private static String remoteVersion(Context c) {
        for (String base : candidates(c)) {
            if (base.isEmpty()) continue;
            try {
                JSONObject root = new JSONObject(new String(get(base, 30000), StandardCharsets.UTF_8));
                String v = root.optString("version", "").trim();
                if (!v.isEmpty()) return v;
            } catch (Exception e) {
                Log.w("QuizPack", "version probe failed " + base + ": " + e);
            }
        }
        return "";
    }

    /** Throws away the downloaded pack, back to the bundled questions. */
    public static void clear(Context c) {
        File dir = KidsBank.packDir(c);
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        prefs(c).edit().putString(VERSION_KEY, "").putLong(STAMP_KEY, 0).putLong(CHECK_KEY, 0).apply();
        KidsBank.forget();
    }

    /** One read is up to 32 KB: OpenList in front of Quark tears long responses down. */
    private static final int CHUNK = 32768;

    /**
     * A whole file: one plain read when that works, otherwise a ranged walk over the same URL.
     * The set drops long responses with "unexpected end of stream" while short ranged reads go
     * through, and OpenList advertises Accept-Ranges, so the fallback is cheap and verifiable.
     */
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
        Log.w("QuizPack", "plain read failed, going ranged: " + last);
        return getRanged(link, readTimeout);
    }

    private static byte[] getRanged(String link, int readTimeout) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int total = -1;
        for (int attempt = 0; attempt < 6; attempt++) {
            int pos = out.size();
            int end = total > 0 ? Math.min(pos + CHUNK, total) - 1 : pos + CHUNK - 1;
            HttpURLConnection conn = (HttpURLConnection) new URL(link).openConnection();
            conn.setConnectTimeout(connectTimeout(link));
            conn.setReadTimeout(readTimeout);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "CleanMiTV/quiz");
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setRequestProperty("Range", "bytes=" + pos + "-" + end);
            try {
                int code = conn.getResponseCode();
                if (code == 200) {
                    // The mirror ignored the range: this response is the whole file.
                    return readAll(conn);
                }
                if (code != 206) throw new Exception("HTTP " + code);
                String cr = conn.getHeaderField("Content-Range");
                if (cr != null && total < 0) {
                    int slash = cr.lastIndexOf('/');
                    if (slash > 0) {
                        try {
                            total = Integer.parseInt(cr.substring(slash + 1).trim());
                        } catch (NumberFormatException ignored) {
                            total = -1;
                        }
                    }
                }
                byte[] part = readAll(conn);
                if (part.length == 0) throw new Exception("empty range at " + pos);
                out.write(part, 0, part.length);
                attempt = 0;
                if (total > 0 && out.size() >= total) break;
                if (total < 0 && part.length < CHUNK) break;
            } catch (Exception e) {
                if (attempt == 5) throw e;
                try {
                    Thread.sleep(400L * (attempt + 1));
                } catch (InterruptedException ignored) {
                    // next try
                }
            } finally {
                conn.disconnect();
            }
        }
        byte[] body = out.toByteArray();
        if (total > 0 && body.length != total) throw new Exception("short read " + body.length + "/" + total);
        return body;
    }

    private static byte[] readAll(HttpURLConnection conn) throws Exception {
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /**
     * A LAN hop answers in milliseconds, so waiting the full 15 s on it only delays the fallback; a
     * remote hop gets a little more room because DNS and TLS setup are the slow parts there. The
     * three hops are tried in order, so each one has to give up quickly for the next to be useful.
     */
    private static int connectTimeout(String link) {
        return link.startsWith("http://192.168.") || link.startsWith("http://10.")
                || link.startsWith("http://127.0.0.1") || link.startsWith("http://localhost/")
                ? 5000 : 10000;
    }

    private static byte[] get(String link, int readTimeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(link).openConnection();
        conn.setConnectTimeout(connectTimeout(link));
        conn.setReadTimeout(readTimeout);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "CleanMiTV/quiz");
        // OpenList answers Content-Length but never closes a kept-alive response, so the set sits
        // in read() until the socket times out and reports "unexpected end of stream". Asking it to
        // close turns a 20 s hang into a 0.3 s complete read.
        conn.setRequestProperty("Connection", "close");
        conn.setRequestProperty("Accept-Encoding", "identity");
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
