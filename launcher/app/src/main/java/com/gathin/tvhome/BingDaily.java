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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pictures behind the wallpaper picker's "必应滚动壁纸" choice. One file per day under
 * {@code files/bing/}, named {@code bing-YYYY-MM-DD.jpg} after the day the picture belongs to, so
 * the names sort the collection newest first. A fetch takes the newest {@link #KEEP} days from
 * bing.wdbyte.com's gallery (the site the user picked) in one go, which also fills the collection
 * on the first choice instead of leaving a single picture to grow for a week; Bing's own
 * HPImageArchive JSON is the fallback when the gallery page cannot be read. The fetch date is
 * remembered, so everyday use only pulls once a day and picks up the new day's picture.
 */
final class BingDaily {
    /** The local date the last fetch ran on (Config.SETTINGS_NAME). */
    static final String DATE_KEY = "bing_daily_date";
    /** Where the day's pictures live, beside the theme's wallpapers/ collection. */
    static final String DIR = "bing";
    /** How many days are kept (the newest days stay, newest first); older pictures are deleted. */
    private static final int KEEP = 7;

    private static final String PAGE_URL = "https://bing.wdbyte.com/zh-cn/";
    private static final String JSON_URL =
            "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN";
    /** One gallery card: the 4K link, then the day it belongs to ({@code data-date}). */
    private static final Pattern ENTRY = Pattern.compile(
            "<a href=\"(https://cn[.]bing[.]com/th\\?id=[^\"]+_UHD[.]jpg[^\"]*)\"[^>]*"
                    + "class=\"bing-download\"[^>]*data-date=\"(20\\d\\d-\\d\\d-\\d\\d)\"");
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static final int TIMEOUT_MS = 15000;

    interface Callback {
        /** {@code refreshed} is true when at least one picture was fetched and saved. */
        void onDone(boolean refreshed, String message);
    }

    private BingDaily() { }

    /**
     * Fetches the newest {@link #KEEP} day pictures, skipping the days already saved. {@code force}
     * bypasses the once-a-day guard (the user picked the choice again); otherwise nothing happens
     * when a fetch already ran today. The callback runs on the main thread. When Home is visible
     * and the collection changed, Home reloads its wallpaper background.
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
            boolean refreshed = false;
            String message = "";
            File dir = Config.dir(context);
            File folder = dir == null ? null : new File(dir, DIR);
            try {
                if (folder == null) {
                    message = "壁纸目录不可用";
                } else {
                    folder.mkdirs();
                    List<String[]> days = pageDays();
                    if (days.isEmpty()) {
                        String fallback = jsonImage();
                        if (fallback != null) days.add(new String[]{today, fallback});
                    }
                    if (days.isEmpty()) {
                        message = "获取必应壁纸地址失败";
                    } else {
                        for (String[] day : days) {
                            File target = new File(folder, "bing-" + day[0] + ".jpg");
                            if (target.isFile() && target.length() > 0) continue;
                            byte[] bytes = download(day[1]);
                            if (bytes == null || bytes.length == 0) continue;
                            save(folder, target, bytes);
                            refreshed = true;
                        }
                        if (refreshed) prefs.edit().putString(DATE_KEY, today).apply();
                        prune(folder);
                        int kept = wallpapers(context).size();
                        if (kept == 0) {
                            message = "下载必应壁纸失败";
                        } else if (refreshed) {
                            message = "必应滚动壁纸已更新，保留 " + kept + " 张";
                        }
                    }
                }
            } catch (Exception e) {
                Log.w("BingDaily", "fetch failed", e);
                message = "获取必应壁纸失败";
            }
            final boolean done = refreshed;
            final String text = message;
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onDone(done, text);
                MainActivity main = MainActivity.instance;
                if (done && main != null && !main.isDestroyed() && main.resumed) {
                    main.reloadWallpaper();
                }
            });
        }, "bing-daily").start();
    }

    /** Writes beside the picture first, then moves it into place, so no reader sees half a file. */
    private static void save(File folder, File target, byte[] bytes) throws Exception {
        File tmp = new File(folder, target.getName() + ".tmp");
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
    }

    /** Keeps the newest {@link #KEEP} day files; the names sort by date. */
    private static void prune(File folder) {
        File[] entries = folder.listFiles();
        if (entries == null) return;
        java.util.Arrays.sort(entries, (a, b) -> b.getName().compareTo(a.getName()));
        int kept = 0;
        for (File entry : entries) {
            if (!entry.isFile() || !entry.getName().endsWith(".jpg")) continue;
            if (++kept > KEEP) entry.delete();
        }
    }

    /**
     * The kept day files, newest first, for Config to use as the wallpaper collection while the
     * "必应滚动壁纸" choice is on, and for the picker to show. Empty when nothing was fetched yet.
     */
    static List<File> wallpapers(Context context) {
        List<File> out = new ArrayList<>();
        File dir = Config.dir(context);
        File folder = dir == null ? null : new File(dir, DIR);
        File[] entries = folder == null ? null : folder.listFiles();
        if (entries == null) return out;
        java.util.Arrays.sort(entries, (a, b) -> b.getName().compareTo(a.getName()));
        for (File entry : entries) {
            if (entry.isFile() && entry.length() > 0 && entry.getName().endsWith(".jpg")) out.add(entry);
            if (out.size() >= KEEP) break;
        }
        return out;
    }

    /**
     * The newest {@link #KEEP} gallery days as {@code {date, 4k-url}}, newest first, or an empty
     * list when the page is unreachable or its markup changed.
     */
    private static List<String[]> pageDays() throws Exception {
        List<String[]> out = new ArrayList<>();
        HttpURLConnection connection = (HttpURLConnection) new URL(PAGE_URL).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        connection.setInstanceFollowRedirects(true);
        try {
            if (connection.getResponseCode() != 200) return out;
            byte[] body = read(connection.getInputStream(), 1024 * 1024);
            if (body == null) return out;
            Matcher matcher = ENTRY.matcher(new String(body, "UTF-8"));
            while (matcher.find() && out.size() < KEEP) {
                String date = matcher.group(2);
                boolean seen = false;
                for (String[] entry : out) seen |= entry[0].equals(date);
                if (!seen) out.add(new String[]{date, matcher.group(1)});
            }
            return out;
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
