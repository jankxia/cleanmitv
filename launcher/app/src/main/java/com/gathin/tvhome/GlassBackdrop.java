package com.gathin.tvhome;

import android.app.Activity;
import android.widget.FrameLayout;

/**
 * A settings page opens in its own window, where Home's picture is gone: without it the page's glass
 * sits on flat black. This gives the page the same wallpaper player Home uses, so it shows the same
 * picture (the rotation remembers the one on screen) and its rows are glass over the same wallpaper
 * the top pills are glass over.
 */
final class GlassBackdrop {
    private GlassBackdrop() {
    }

    /** Start the page's wallpaper; the caller drives stop/close with the page's own lifecycle. */
    static WallpaperPlayer attach(Activity activity) {
        FrameLayout backdrop = activity.findViewById(R.id.backdrop);
        if (backdrop == null) return null;
        return new WallpaperPlayer(activity, backdrop, Config.load(activity));
    }
}
