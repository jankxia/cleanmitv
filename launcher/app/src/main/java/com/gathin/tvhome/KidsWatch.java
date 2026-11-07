package com.gathin.tvhome;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Kids and Elders mode while the TV is on, wherever it is (Kids): counts the time spent in apps
 * (not on Home or the clock), starts breaks, warns 5 and 1 minutes before a lock, and brings Home
 * back over whatever is in front once locked. Ported from aerial-tv's KidsWatch.
 *
 * <p>Other apps are kept away by the accessibility guard (InputKeyService when its window-event
 * listening is on) as their windows come up; without it, where usage stats may be read, the app in
 * front is looked at every few seconds instead. Runs in the launcher's process on the main thread.
 */
public final class KidsWatch {

    private static final long TICK_MS = 10_000L;
    /** Without the accessibility guard, how often usage stats are read for an app to keep away. */
    private static final long USAGE_TICK_MS = 2_000L;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Context app;
    private static boolean ticking;
    private static long lastTickAt;
    private static long idleMs;

    /** The last warning's step (5 or 1 minutes), so each is said once; 0 when none is due. */
    private static int warnedStep;
    private static long broughtBackAt = Long.MIN_VALUE / 2;

    /** Home (or the clock) is resumed: time there is not counted. */
    private static boolean homeInFront;

    /** The full-screen Kids lock is showing: nothing may bring Home over it. */
    public static volatile boolean lockScreenShowing;

    private static final Runnable loop = KidsWatch::tickAndPlan;

    private static final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // A parent who left the mode is gone once the TV sleeps.
            Kids.rearm();
        }
    };

    private KidsWatch() {}

    public static void start(Context context) {
        if (app == null) {
            app = context.getApplicationContext();
            Kids.init(context);
            try {
                app.registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
            } catch (Exception ignored) {
            }
        }
        poke();
    }

    /** Home came to the front or left it; look now rather than up to a tick later. */
    public static void setHomeInFront(boolean inFront) {
        homeInFront = inFront;
        if (app != null) handler.post(KidsWatch::tick);
    }

    /** The mode or its rules changed: look again now, and keep looking while it is on. */
    public static void poke() {
        if (app == null) return;
        handler.removeCallbacks(loop);
        handler.post(loop);
    }

    private static void tickAndPlan() {
        tick();
        if (!Kids.enabled()) {
            ticking = false;
            Kids.lock = null;
            return;
        }
        ticking = true;
        handler.removeCallbacks(loop);
        handler.postDelayed(loop, watchesUsage() ? USAGE_TICK_MS : TICK_MS);
    }

    private static void tick() {
        Context ctx = app;
        if (ctx == null) return;
        long now = System.currentTimeMillis();
        long at = SystemClock.elapsedRealtime();
        long since = lastTickAt == 0 ? 0 : Math.max(0, at - lastTickAt);
        lastTickAt = at;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        boolean interactive = pm == null || pm.isInteractive();
        boolean watching = Kids.active() && interactive && !homeInFront && !lockScreenShowing;
        if (watching) {
            idleMs = 0;
            // A gap longer than two ticks is the process held up (standby), not watching.
            Kids.count(Math.min(since, 2 * TICK_MS), 0, now);
        } else {
            idleMs += since;
            Kids.count(0, idleMs, now);
        }

        Kids.Lock lock = Kids.lockNow(now);
        Kids.lock = lock;
        if (lock != null && interactive && !homeInFront && !lockScreenShowing) {
            bringBack(ctx, "locked (" + lockName(lock) + ")");
        }

        if (watching && lock == null) warnIfDue(ctx, now);
        else if (lock != null) warnedStep = 0;
        if (watching && watchesUsage()) keepAwayByUsage(ctx);
    }

    private static void warnIfDue(Context ctx, long now) {
        long until = Kids.untilLockMs(now);
        int step;
        if (until == Long.MAX_VALUE || until > 5 * 60_000L) step = 0;
        else if (until > 60_000L) step = 5;
        else step = 1;
        if (step != 0 && (warnedStep == 0 || step < warnedStep)) {
            int minutes = (int) Math.max(1, (until + 59_999L) / 60_000L);
            boolean toBreak = Kids.rules().breakEvery > 0 && Kids.usage().allowUntil <= now
                    && Kids.rules().breakEvery * 60_000L - Kids.usage().continuousMs == until;
            warn(ctx, ctx.getString(toBreak ? R.string.kids_warn_break : R.string.kids_warn, minutes));
        }
        warnedStep = step;
    }

    private static void warn(Context ctx, String text) {
        try {
            Toast.makeText(ctx, text, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
        }
    }

    private static void bringBack(Context ctx, String why) {
        long now = SystemClock.uptimeMillis();
        if (now - broughtBackAt < 3_000) return;
        broughtBackAt = now;
        try {
            // Locked: show the lock itself, not a Home that would only be covered again on the next
            // tick. A child tapping an app then sees why it will not open, and the parent's PIN is
            // right there; unlocked keep-away just puts Home back in front.
            Intent i = Kids.lock != null ? new Intent(ctx, KidsLockActivity.class)
                    : new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    /**
     * Whether the mode keeps pkg off the screen: once locked every app, else whatever is not
     * allowed, when it is an app (it has a launcher entry) or the TV's settings.
     */
    public static boolean keepsAway(Context ctx, String pkg) {
        if (!Kids.active() || pkg == null || pkg.equals(ctx.getPackageName())) return false;
        // The tile just picked on Home: never bounce the app the tap was meant to open.
        if (Kids.justLaunched(pkg)) return false;
        Kids.Lock l = Kids.lock;
        if (l == null && (!Kids.blockOthers() || Kids.allowedPackages().contains(pkg))) return false;
        if (pkg.equals(keyboard(ctx))) return false;
        return Kids.sourcePlayers().contains(pkg) || isSettingsApp(ctx, pkg) || hasLauncherEntry(ctx, pkg);
    }

    private static String keyboard(Context ctx) {
        try {
            String s = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (s == null) return null;
            int slash = s.indexOf('/');
            return slash > 0 ? s.substring(0, slash) : s;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isSettingsApp(Context ctx, String pkg) {
        if ("com.android.settings".equals(pkg)) return true;
        return "com.xiaomi.mitv.settings".equals(pkg)
                || "com.tcl.settings".equals(pkg)
                || "com.amazon.tv.launcher".equals(pkg);
    }

    private static boolean hasLauncherEntry(Context ctx, String pkg) {
        try {
            return ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg) != null
                    || ctx.getPackageManager().getLaunchIntentForPackage(pkg) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Without the accessibility guard, usage stats say what is in front. */
    private static boolean watchesUsage() {
        return Kids.active() && (Kids.blockOthers() || Kids.lock != null)
                && !homeInFront && usageStatsAllowed(app);
    }

    public static boolean usageStatsAllowed(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
        try {
            AppOpsManagerCompat appOps = AppOpsManagerCompat.instance(ctx);
            return appOps.check(24) || appOps.check(43); // OPSTR_GET_USAGE_STATS / OPSTR_PACKAGE_USAGE_STATS
        } catch (Exception e) {
            return false;
        }
    }

    private static void keepAwayByUsage(Context ctx) {
        String front = frontByUsage(ctx);
        if (front == null || !keepsAway(ctx, front)) return;
        bringBack(ctx, front + " in front (usage stats)");
    }

    private static String frontByUsage(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT < 22) return null;
            long now = System.currentTimeMillis();
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            UsageEvents events = usm.queryEvents(now - 60_000L, now);
            if (events == null) return null;
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.getPackageName();
            }
            return last;
        } catch (Exception e) {
            return null;
        }
    }

    private static String lockName(Kids.Lock lock) {
        switch (lock) {
            case BREAK: return "break";
            case DAY_USED: return "day used";
            case NO_WATCH: return "no watching";
            case LATE: return "late";
            default: return "unlocked";
        }
    }

    /** What the lock says, for the diagnostics. */
    public static String diagnosticState() {
        if (!Kids.enabled()) return "Kids mode off";
        return "Kids mode " + Kids.kind().key + " active=" + Kids.active()
                + " allowed=" + Kids.allowed().size() + " block=" + Kids.blockOthers()
                + " used=" + (Kids.usage().usedMs / 60_000) + " min lock=" + lockName(Kids.lock)
                + " ticking=" + ticking;
    }

    /** AppOps check without the androidx dependency. */
    private static final class AppOpsManagerCompat {
        private final android.app.AppOpsManager manager;
        private final String pkg;

        private AppOpsManagerCompat(Context ctx) {
            manager = (android.app.AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            pkg = ctx.getPackageName();
        }

        static AppOpsManagerCompat instance(Context ctx) {
            return new AppOpsManagerCompat(ctx);
        }

        boolean check(int op) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    return manager.unsafeCheckOpNoThrow(String.valueOf(op), android.os.Process.myUid(), pkg)
                            == android.app.AppOpsManager.MODE_ALLOWED;
                }
                return manager.checkOpNoThrow(String.valueOf(op), android.os.Process.myUid(), pkg)
                        == android.app.AppOpsManager.MODE_ALLOWED;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
