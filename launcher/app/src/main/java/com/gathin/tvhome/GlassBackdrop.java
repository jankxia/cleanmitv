package com.gathin.tvhome;

import android.app.Activity;
import android.widget.FrameLayout;

/**
 * A settings page opens in its own window, where Home's picture is gone: without it the page's rows
 * sit on a plain background instead of over a picture the way the top pills do. This gives the page
 * the same wallpaper player Home uses, so it shows the picture the rotation remembers.
 */
final class GlassBackdrop {
    private GlassBackdrop() {
    }

    /** Start the page's wallpaper; the caller drives stop/close with the page's own lifecycle. */
    static WallpaperPlayer attach(Activity activity, Config config) {
        FrameLayout backdrop = activity.findViewById(R.id.backdrop);
        if (backdrop == null) return null;
        return new WallpaperPlayer(activity, backdrop, config);
    }
}
