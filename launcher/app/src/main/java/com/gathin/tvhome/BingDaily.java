package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The day's Bing picture for the wallpaper picker's "必应每日精选" choice. Pulls the 4K image
 * from bing.wdbyte.com's gallery (the site the user picked), falling back to Bing's own
 * HPImageArchive JSON, and saves it over the theme's single wallpaper. Once a day: the fetch
 * date is remembered, so coming back to Home updates the picture on new days only.
 */
final class BingDaily {
    /** The local date the current picture was fetched on (Config.SETTINGS_NAME). */
    static final String DATE_KEY = "bing_daily_date";

    private static final String PAGE_URL = "https://bing.wdbyte.com/zh-cn/";
    private static final String JSON_URL =
            "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN";
    private static final Pattern UHD = Pattern.compile(
            "https://cn\\.bing\\.com/th\\?id=[^\"'\\s]+_UHD\\.jpg&pid=hp&w=3840[^\"'\\s]*");
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static final int TIMEOUT_MS = 15000;

    interface Callback {
        /** {@code refreshed} is true when a new picture was fetched and saved. */
        void onDone(boolean refreshed, String message);
    }

    private BingDaily() { }

    /**
     * Fetches and saves the day's picture. {@code force} bypasses the once-a-day guard (the user
     * picked the choice again); otherwise nothing happens when today's picture is already saved.
     * The callback runs on the main thread. When Home is visible and the picture changed, Home
     * reloads its wallpaper background.
     */
    static void fetchToday(final Context context, final boolean force, final Callback callback) {
        final SharedPreferences prefs =
                context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        final String today = LocalDate.now().toString();
        if (!force && today.equals(prefs.getString(DATE_KEY, ""))) {
            callback.onDone(false, "");
            return;
        }
        new Thread(() -> {
            boolean ok = false;
            String message = "";
            File dir = Config.dir(context);
            File target = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
            try {
                if (target != null) target.getParentFile().mkdirs();
                String imageUrl = pageImage();
                if (imageUrl == null) imageUrl = jsonImage();
                if (imageUrl == null) {
                    message = "获取必应今日壁纸地址失败";
                } else {
                    byte[] bytes = download(imageUrl);
                    if (bytes == null || bytes.length == 0) {
                        message = "下载必应今日壁纸失败";
                    } else if (target == null) {
                        message = "壁纸目录不可用";
                    } else {
                        // Write beside the wallpaper first, then move it into place, so WallpaperPlayer
                        // never decodes a half-written file.
                        File tmp = new File(dir, Config.WALLPAPER_NAME + ".tmp");
                        FileOutputStream out = new FileOutputStream(tmp);
                        out.write(bytes);
                        out.close();
                        if (!tmp.renameTo(target)) {
                            FileInputStream in = new FileInputStream(tmp);
                            FileOutputStream to = new FileOutputStream(target);
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = in.read(buffer)) >= 0) to.write(buffer, 0, read);
                            in.close();
                            to.close();
                            tmp.delete();
                        }
                        prefs.edit().putString(DATE_KEY, today).apply();
                        ok = true;
                        message = "必应今日壁纸已应用";
                    }
                }
            } catch (Exception e) {
                Log.w("BingDaily", "fetch failed", e);
                message = "获取必应壁纸失败";
            }
            final boolean done = ok;
            final String text = message;
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onDone(done, text);
                MainActivity main = MainActivity.instance;
                if (done && main != null && !main.isDestroyed() && main.isResumed()) {
                    main.reloadWallpaper();
                }
            });
        }, "bing-daily").start();
    }

    /** The first 4K gallery image on bing.wdbyte.com/zh-cn/, or null when the page is unreachable. */
    private static String pageImage() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(PAGE_URL).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        connection.setInstanceFollowRedirects(true);
        try {
            if (connection.getResponseCode() != 200) return null;
            byte[] body = read(connection.getInputStream(), 512 * 1024);
            if (body == null) return null;
            Matcher matcher = UHD.matcher(new String(body, "UTF-8"));
            return matcher.find() ? matcher.group() : null;
        } finally {
            connection.disconnect();
        }
    }

    /** Fallback: Bing's own HPImageArchive JSON for today. */
    private static String jsonImage() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(JSON_URL).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setInstanceFollowRedirects(true);
        try {
            if (connection.getResponseCode() != 200) return null;
            byte[] body = read(connection.getInputStream(), 256 * 1024);
            if (body == null) return null;
            JSONObject root = new JSONObject(new String(body, "UTF-8"));
            JSONArray images = root.optJSONArray("images");
            JSONObject first = images == null || images.length() == 0 ? null : images.optJSONObject(0);
            String url = first == null ? "" : first.optString("url", "");
            if (url.isEmpty() || !url.startsWith("/")) return null;
            return "https://cn.bing.com" + url;
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setInstanceFollowRedirects(true);
        try {
            if (connection.getResponseCode() != 200) return null;
            return read(connection.getInputStream(), MAX_BYTES);
        } finally {
            connection.disconnect();
        }
    }

    /** Reads at most {@code limit} bytes; null when the stream overflows it. */
    private static byte[] read(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (out.size() + read > limit) return null;
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
