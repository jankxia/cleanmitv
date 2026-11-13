package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A settings page opens in its own window, so Home's picture is not behind it: the page would sit on
 * flat black and its glass would have nothing to be glass over. Home hands over the picture it is
 * showing and this paints it full screen, dimmed, under the page's translucent rows.
 */
final class GlassBackdrop {
    static final String EXTRA = "com.gathin.tvhome.extra.WALLPAPER";
    private static final String TAG = "TvHomeGlass";

    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor();
    /** Wide enough for a dimmed 1080p backdrop, small enough to decode in tens of milliseconds. */
    private static final int MAX_WIDTH = 1280;
    /** Dark enough that white row text keeps its contrast on a bright picture. */
    private static final int SCRIM = 0x99000000;
    private static final int NO_SCRIM = 0xB0000000;

    private GlassBackdrop() {
    }

    /** Paint the backdrop behind the whole window of {@code activity}; Home's picture or its colour. */
    static void apply(Activity activity) {
        Config config = Config.load(activity);
        String path = activity.getIntent() == null ? null : activity.getIntent().getStringExtra(EXTRA);
        if (TextUtils.isEmpty(path) && !config.wallpapers.isEmpty()) {
            path = config.wallpapers.get(0).getAbsolutePath();
        }
        Log.i(TAG, "extra=" + activity.getIntent().getStringExtra(EXTRA)
                + " wallpapers=" + config.wallpapers.size() + " use=" + path);
        if (TextUtils.isEmpty(path)) {
            paint(activity, colourOf(config), SCRIM);
            return;
        }
        String file = path;
        DECODER.execute(() -> {
            Bitmap bitmap = decode(file);
            Log.i(TAG, "decoded " + file + " -> " + (bitmap == null ? "null"
                    : bitmap.getWidth() + "x" + bitmap.getHeight()));
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    paint(activity, colourOf(config), SCRIM);
                } else {
                    paint(activity, new BitmapDrawable(activity.getResources(), bitmap), SCRIM);
                }
            });
        });
    }

    private static Drawable colourOf(Config config) {
        return new ColorDrawable(config.backgroundColor);
    }

    /** The window's own background sits under the content view, so nothing above it can cover it. */
    private static void paint(Activity activity, Drawable under, int scrim) {
        LayerDrawable layers = new LayerDrawable(new Drawable[]{under, new ColorDrawable(scrim)});
        activity.getWindow().setBackgroundDrawable(layers);
        Log.i(TAG, "painted " + layers.getNumberOfLayers() + " layers");
    }

    private static Bitmap decode(String path) {
        File file = new File(path);
        if (!file.isFile()) {
            Log.i(TAG, "not a file: " + path);
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0) {
            Log.i(TAG, "no bounds: " + path);
            return null;
        }
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try {
            return BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError e) {
            Log.i(TAG, "oom: " + path);
            return null;
        }
    }
}
