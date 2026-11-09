package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Dependency-free APK updater: downloads the APK whose URL is configured on the hidden-settings page
 * (a GitHub release link or any direct file link) with a plain {@link HttpURLConnection}, follows
 * redirects, then hands the file to the system package installer through {@link ApkShareProvider}.
 *
 * <p>The URL must be a direct file link that returns the APK bytes. A Quark Drive share page is an
 * HTML page rather than the file, so the direct download link it exposes has to be used instead.
 * Progress is kept in static fields so the settings page and the web page can read it while it runs.
 */
final class ApkUpdater {
    private static final String TAG = "TvHome.Update";
    private static final String DIR_NAME = "updates";
    private static final String PART_NAME = "update.part";
    private static final String APK_NAME = "update.apk";

    public static final String IDLE = "idle";
    public static final String DOWNLOADING = "downloading";
    public static final String INSTALLING = "installing";
    public static final String ERROR = "error";

    private static volatile String state = IDLE;
    private static volatile int progress;
    private static volatile String message = "";

    private ApkUpdater() {
    }

    static File updateDir(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getCacheDir();
        File dir = new File(base, DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static String state() {
        return state;
    }

    static int progress() {
        return progress;
    }

    static String message() {
        return message;
    }

    /** Whether the OS lets this app launch an install without first sending the user to a settings page. */
    static boolean canRequestInstall(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    /** Downloads on a background thread and, once the file is complete, launches the installer. */
    static void start(final Context context, final String urlText) {
        if (urlText == null || urlText.trim().isEmpty()) {
            state = ERROR;
            message = "no update URL configured";
            return;
        }
        if (state == DOWNLOADING || state == INSTALLING) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> run(app, urlText.trim()), "apk-updater").start();
    }

    private static void run(Context context, String urlText) {
        try {
            state = DOWNLOADING;
            progress = 0;
            message = "";
            Log.i(TAG, "downloading " + urlText);
            File apk = download(context, urlText);
            verify(apk);
            state = INSTALLING;
            message = "";
            install(context, apk);
        } catch (Exception e) {
            state = ERROR;
            message = String.valueOf(e.getMessage());
            Log.w(TAG, "update failed: " + message, e);
        }
    }

    private static File download(Context context, String urlText) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "cleanmitv-updater");
            connection.connect();
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new Exception("HTTP " + status);
            }
            long total = connection.getContentLengthLong();
            File dir = updateDir(context);
            File part = new File(dir, PART_NAME);
            File apk = new File(dir, APK_NAME);
            try (InputStream in = connection.getInputStream();
                    FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[8192];
                long copied = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    copied += read;
                    if (total > 0) {
                        progress = (int) Math.max(0, Math.min(99, copied * 100 / total));
                    }
                }
            }
            if (part.length() == 0) throw new Exception("downloaded file is empty");
            if (apk.exists()) apk.delete();
            if (!part.renameTo(apk)) {
                // rename across a volume can fail; fall back to copy within the same dir.
                try (java.io.FileInputStream in = new java.io.FileInputStream(part);
                        FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                part.delete();
            }
            progress = 100;
            return apk;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** A quick sanity check before asking the installer to open it. */
    private static void verify(File apk) throws Exception {
        if (!apk.isFile() || apk.length() < 4096) {
            throw new Exception("not a valid APK");
        }
        // Java/Android zip files start with PK (0x50 0x4B); an HTML error page would not.
        try (java.io.FileInputStream in = new java.io.FileInputStream(apk)) {
            int first = in.read();
            int second = in.read();
            if (first != 0x50 || second != 0x4B) {
                throw new Exception("the link did not return an APK (use a direct file link)");
            }
        }
    }

    private static void install(Context context, File apk) throws Exception {
        if (!canRequestInstall(context)) {
            // Ask once for "install unknown apps"; the same update is started again afterwards.
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            state = ERROR;
            message = "grant the install permission, then update again";
            return;
        }
        Uri uri = ApkShareProvider.uriFor(context, apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        state = IDLE;
        message = "";
    }
}
