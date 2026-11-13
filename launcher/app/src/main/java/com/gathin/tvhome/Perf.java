package com.gathin.tvhome;

import android.os.SystemClock;
import android.util.Log;

/**
 * Temporary stage timer: one line per stage, each counting from the previous mark, so a cold start reads
 * as a breakdown in logcat. Flip ON to false once the numbers are in.
 */
final class Perf {
    static final boolean ON = true;
    private static final String TAG = "TvHomePerf";
    private static long last;

    private Perf() {
    }

    static void start() {
        last = SystemClock.uptimeMillis();
    }

    static void mark(String stage) {
        if (!ON) return;
        long now = SystemClock.uptimeMillis();
        Log.i(TAG, stage + " +" + (now - last) + "ms");
        last = now;
    }
}
