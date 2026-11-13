package com.gathin.tvhome;

import android.app.Activity;
import android.util.Log;
import android.widget.FrameLayout;

/**
 * A settings page opens in its own window, where Home's picture is gone: without it the page's glass
 * sits on flat black. This gives the page the same wallpaper player Home uses, so it shows the same
 * picture (the rotation remembers the one on screen) and its rows are glass over the same wallpaper
 * the top pills are glass over.
 */
final class GlassBackdrop {
    private static final String TAG = "TvHomeGlass";

    private GlassBackdrop() {
    }

    /** Start the page's wallpaper; the caller drives stop/close with the page's own lifecycle. */
    static WallpaperPlayer attach(Activity activity) {
        FrameLayout backdrop = activity.findViewById(R.id.backdrop);
        Log.i(TAG, "attach view=" + (backdrop != null));
        if (backdrop == null) return null;
        Config config = Config.load(activity);
        Log.i(TAG, "enabled=" + config.wallpaperEnabled + " files=" + config.wallpapers.size()
                + " bg=" + Integer.toHexString(config.backgroundColor) + " dim=" + config.wallpaperDim);
        WallpaperPlayer player = new WallpaperPlayer(activity, backdrop, config);
        Log.i(TAG, "children=" + backdrop.getChildCount());
        if (backdrop.getChildCount() == 0) backdrop.setBackgroundColor(0xFFFF0000);
        return player;
    }
}
