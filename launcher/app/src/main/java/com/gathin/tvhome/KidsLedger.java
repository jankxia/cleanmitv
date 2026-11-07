package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the quiz has bought today: the minutes still to be watched, how many rounds were already
 * passed, and the practice pool.
 *
 * <p>The day's quota in {@link Kids.Usage} is what the parent set; this is the top-up on it. A
 * millisecond watched comes off {@link #creditMs} first and only the rest counts against the
 * quota, so a round really does buy time. Credit and rounds are per day; the practice pool carries
 * over, which is the point of it.
 */
public final class KidsLedger {

    static final String PREFS = "kids_ledger";

    public long creditMs;
    public int roundsToday;
    public int extraPool;
    /** Wrong answers since the pool last paid out; the threshold tops the pool up. */
    public int wrongSince;
    public String day = "";

    public static KidsLedger load(Context context) {
        KidsLedger l = new KidsLedger();
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        l.creditMs = p.getLong("credit_ms", 0);
        l.roundsToday = p.getInt("rounds_today", 0);
        l.extraPool = p.getInt("extra_pool", 0);
        l.wrongSince = p.getInt("wrong_since", 0);
        l.day = p.getString("day", "");
        return l;
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("credit_ms", creditMs)
                .putInt("rounds_today", roundsToday)
                .putInt("extra_pool", extraPool)
                .putInt("wrong_since", wrongSince)
                .putString("day", day)
                .apply();
    }

    /** A new day: yesterday's credit and rounds are gone, the practice pool stays. */
    public void roll(String today) {
        if (today == null || today.equals(day)) return;
        day = today;
        creditMs = 0;
        roundsToday = 0;
    }

    /** Watching ms, charged to the credit first; returns how much of it the credit covered. */
    public long spend(long ms) {
        if (ms <= 0 || creditMs <= 0) return 0;
        long covered = Math.min(ms, creditMs);
        creditMs -= covered;
        return covered;
    }

    public void grant(long ms) {
        creditMs = Math.max(0, creditMs + ms);
    }

    /** Room for another round today? */
    public boolean roundsLeft(int roundsPerDay) {
        return roundsToday < roundsPerDay;
    }
}
