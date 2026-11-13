package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.View;

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

    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor();
    /** Wide enough for a dimmed 1080p backdrop, small enough to decode in tens of milliseconds. */
    private static final int MAX_WIDTH = 1280;
    /** Dark enough that white row text keeps its contrast on a bright picture. */
    private static final int SCRIM = 0x99000000;
    private static final int NO_SCRIM = 0xB0000000;

    private GlassBackdrop() {
    }

    /** Paint the backdrop behind {@code root}; a picture from Home, or Home's own background colour. */
    static void apply(Activity activity, View root) {
        Config config = Config.load(activity);
        String path = activity.getIntent() == null ? null : activity.getIntent().getStringExtra(EXTRA);
        if (TextUtils.isEmpty(path)) path = config.wallpapers.isEmpty()
                ? null : config.wallpapers.get(0).getAbsolutePath();
        if (TextUtils.isEmpty(path)) {
            // No pictures at all: Home shows its background colour, so put that colour here instead.
            root.setBackground(new LayerDrawable(new Drawable[]{
                    new ColorDrawable(config.backgroundColor), new ColorDrawable(NO_SCRIM)}));
            return;
        }
        String file = path;
        DECODER.execute(() -> {
            Bitmap bitmap = decode(file);
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    root.setBackground(new LayerDrawable(new Drawable[]{
                            new ColorDrawable(config.backgroundColor), new ColorDrawable(NO_SCRIM)}));
                    return;
                }
                root.setBackground(new LayerDrawable(new Drawable[]{
                        new BitmapDrawable(activity.getResources(), bitmap), new ColorDrawable(SCRIM)}));
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
