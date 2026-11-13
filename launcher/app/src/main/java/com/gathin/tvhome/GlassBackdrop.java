package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A settings page opens in its own window, where Home's picture is gone: without it the page's glass
 * sits on flat black. Home hands over the picture it is showing and this fills the page's backdrop
 * view with it, so the translucent rows are glass over the same wallpaper the pills are glass over.
 */
final class GlassBackdrop {
    static final String EXTRA = "com.gathin.tvhome.extra.WALLPAPER";
    private static final String TAG = "TvHomeGlass";

    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor();
    /** Wide enough for a dimmed 1080p backdrop, small enough to decode in tens of milliseconds. */
    private static final int MAX_WIDTH = 1280;

    private GlassBackdrop() {
    }

    /** Fill {@code R.id.backdrop} with Home's picture, or its background colour when there is none. */
    static void apply(Activity activity) {
        Config config = Config.load(activity);
        ImageView backdrop = activity.findViewById(R.id.backdrop);
        if (backdrop == null) return;
        String path = activity.getIntent() == null ? null : activity.getIntent().getStringExtra(EXTRA);
        if (path == null && !config.wallpapers.isEmpty()) {
            path = config.wallpapers.get(0).getAbsolutePath();
        }
        if (path == null) {
            backdrop.setBackgroundColor(config.backgroundColor);
            return;
        }
        String file = path;
        DECODER.execute(() -> {
            Bitmap bitmap = decode(file);
            Log.i(TAG, "backdrop " + file + " -> " + (bitmap == null ? "null"
                    : bitmap.getWidth() + "x" + bitmap.getHeight()));
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    backdrop.setBackgroundColor(config.backgroundColor);
                } else {
                    backdrop.setImageBitmap(bitmap);
                }
            });
        });
    }

    private static Bitmap decode(String path) {
        File file = new File(path);
        if (!file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try {
            return BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
