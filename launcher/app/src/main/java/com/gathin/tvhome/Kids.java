package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kids and Elders mode, ported from the aerial-tv launcher (app.orchard.tv.settings.Restricted):
 * a Home with only the tiles chosen here, nothing to rearrange, and for children optional time
 * limits and breaks. Starting it takes nothing; leaving it takes the parental PIN, and only until
 * the screensaver starts or the TV goes to sleep (suspended, see {@link #rearm()}).
 *
 * <p>Time rules apart from the clock and the UI so they can be tested ({@link KidsTime}): whether
 * the TV is locked now, and how long watching can go on before it is.
 *
 * <p>The owner's own Home (order, folders, hidden apps) is never touched: the mode keeps its own
 * list. All state lives in SharedPreferences and is written/read on the main thread only.
 */
public final class Kids {

    /** Who the restricted Home is for. */
    public enum Kind {
        /** Time limits and breaks, other apps kept away. */
        KIDS("kids"),
        /** Every app name under its tile; no limits unless chosen. */
        ELDERS("elders");

        public final String key;
        Kind(String key) { this.key = key; }

        static Kind of(String key) {
            return "elders".equals(key) ? ELDERS : KIDS;
        }
    }

    /** Why the restricted Home is locked, if it is. */
    public enum Lock {
        /** A break after breakEvery minutes of watching, until breakUntil (wall clock). */
        BREAK,
        /** The day's minutes are used up. */
        DAY_USED,
        /** The day's minutes are set to "not at all": no watching whatever the hour. */
        NO_WATCH,
        /** Outside the hours the TV may be watched. */
        LATE,
        /** The day is used up, but a round of questions still buys time: the quiz is the way on. */
        QUIZ
    }

    /**
     * The limits chosen; 0 is "none" for the day's minutes, endMinute and breakEvery, and -1 minutes
     * is "not at all": the whole day is locked, before any hour or break comes into it.
     */
    public static final class Rules {
        public final int weekdayMinutes;
        public final int weekendMinutes;
        /** The TV may be watched from this minute of the day... */
        public final int startMinute;
        /** ...up to this one; 0 for all day. Always after startMinute (see {@link #valid()}). */
        public final int endMinute;
        /** Minutes of watching before a break of {@link #breakMinutes}. */
        public final int breakEvery;
        public final int breakMinutes;

        Rules(int weekdayMinutes, int weekendMinutes, int startMinute, int endMinute,
              int breakEvery, int breakMinutes) {
            this.weekdayMinutes = weekdayMinutes;
            this.weekendMinutes = weekendMinutes;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.breakEvery = breakEvery;
            this.breakMinutes = breakMinutes;
        }

        Rules copy(int weekday, int weekend, int start, int end, int every, int breakMinutes) {
            return new Rules(
                    weekday < 0 ? this.weekdayMinutes : weekday,
                    weekend < 0 ? this.weekendMinutes : weekend,
                    start < 0 ? this.startMinute : start,
                    end < 0 ? this.endMinute : end,
                    every < 0 ? this.breakEvery : every,
                    breakMinutes < 0 ? this.breakMinutes : breakMinutes);
        }

        boolean any() {
            return weekdayMinutes != 0 || weekendMinutes != 0 || endMinute > 0 || breakEvery > 0;
        }

        /** The day's minutes set outright; -1 is "not at all", which {@link #copy} cannot carry. */
        Rules withWeekday(int minutes) {
            return new Rules(minutes, weekendMinutes, startMinute, endMinute, breakEvery, breakMinutes);
        }

        Rules withWeekend(int minutes) {
            return new Rules(weekdayMinutes, minutes, startMinute, endMinute, breakEvery, breakMinutes);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Rules)) return false;
            Rules r = (Rules) o;
            return weekdayMinutes == r.weekdayMinutes && weekendMinutes == r.weekendMinutes
                    && startMinute == r.startMinute && endMinute == r.endMinute
                    && breakEvery == r.breakEvery && breakMinutes == r.breakMinutes;
        }

        @Override
        public int hashCode() {
            int h = weekdayMinutes;
            h = 31 * h + weekendMinutes;
            h = 31 * h + startMinute;
            h = 31 * h + endMinute;
            h = 31 * h + breakEvery;
            h = 31 * h + breakMinutes;
            return h;
        }

        /** Within what the TV can keep: a day at most, a window that ends after it starts. */
        boolean valid() {
            return weekdayMinutes >= -1 && weekdayMinutes <= KidsTime.DAY
                    && weekendMinutes >= 0 && weekendMinutes <= KidsTime.DAY
                    && startMinute >= 0 && startMinute < KidsTime.DAY
                    && (endMinute == 0 || (endMinute > startMinute && endMinute < KidsTime.DAY))
                    && breakEvery >= 0 && breakEvery <= KidsTime.DAY
                    && breakMinutes >= 1 && breakMinutes <= 120;
        }
    }

    /** What has been watched: kept per day, in wall-clock and watching milliseconds. */
    public static final class Usage {
        public final long usedMs;
        /** Watching since the last break, or since the TV last sat idle for a break's length. */
        public final long continuousMs;
        /** A break runs until then (wall clock); 0 for none. */
        public final long breakUntil;
        /** A parent allowed watching until then whatever the limits say (wall clock); 0 for none. */
        public final long allowUntil;

        Usage(long usedMs, long continuousMs, long breakUntil, long allowUntil) {
            this.usedMs = usedMs;
            this.continuousMs = continuousMs;
            this.breakUntil = breakUntil;
            this.allowUntil = allowUntil;
        }

        Usage copy(long used, long continuous, long breakUntil, long allowUntil) {
            return new Usage(
                    used < 0 ? this.usedMs : used,
                    continuous < 0 ? this.continuousMs : continuous,
                    breakUntil < 0 ? this.breakUntil : breakUntil,
                    allowUntil < 0 ? this.allowUntil : allowUntil);
        }
    }

    /** The time rules, apart from the clock and the TV so that they can be tested. */
    public static final class KidsTime {
        public static final int DAY = 24 * 60;
        /** Where the window starts unless the phone page says otherwise: 6:00. */
        public static final int DEFAULT_START = 6 * 60;
        public static final int BREAK_MINUTES = 5;

        private KidsTime() {}

        /** "7:05" for a minute of the day. */
        public static String clock(int minute) {
            return String.format("%d:%02d", minute / 60, minute % 60);
        }

        static int limitMinutes(Rules rules, LocalDate day) {
            DayOfWeek w = day.getDayOfWeek();
            return (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) ? rules.weekendMinutes : rules.weekdayMinutes;
        }

        private static boolean late(Rules rules, LocalDateTime at) {
            if (rules.endMinute <= 0) return false;
            int m = at.getHour() * 60 + at.getMinute();
            return m < rules.startMinute || m >= rules.endMinute;
        }

        /** The lock the rules give at now, ignoring a parent's allowance. */
        static Lock ruleLock(Rules rules, Usage usage, long now, ZoneId zone) {
            LocalDateTime at = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime();
            int limit = limitMinutes(rules, at.toLocalDate());
            // "Not at all" comes before the hours and the breaks: no part of the day opens.
            if (limit < 0) return Lock.NO_WATCH;
            if (late(rules, at)) return Lock.LATE;
            if (limit > 0 && usage.usedMs >= limit * 60_000L) return Lock.DAY_USED;
            if (usage.breakUntil > now) return Lock.BREAK;
            return null;
        }

        static Lock lock(Rules rules, Usage usage, long now, ZoneId zone) {
            return usage.allowUntil > now ? null : ruleLock(rules, usage, now, zone);
        }

        /**
         * How long from now watching can go on before a lock, if one is coming: the day's minutes
         * left, the latest hour, the next break, or the end of a parent's allowance when the rules
         * would lock then. 0 when already locked; Long.MAX_VALUE when nothing will lock today.
         */
        static long untilLockMs(Rules rules, Usage usage, long now, ZoneId zone) {
            if (lock(rules, usage, now, zone) != null) return 0;
            LocalDateTime at = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime();
            if (usage.allowUntil > now) {
                Usage end = new Usage(usage.usedMs + (usage.allowUntil - now), usage.continuousMs,
                        usage.breakUntil, 0);
                return ruleLock(rules, end, usage.allowUntil, zone) != null
                        ? usage.allowUntil - now : Long.MAX_VALUE;
            }
            long best = Long.MAX_VALUE;
            int limit = limitMinutes(rules, at.toLocalDate());
            if (limit > 0) best = Math.min(best, limit * 60_000L - usage.usedMs);
            if (rules.endMinute > 0) {
                long cutoff = at.toLocalDate().atTime(rules.endMinute / 60, rules.endMinute % 60)
                        .atZone(zone).toInstant().toEpochMilli();
                best = Math.min(best, cutoff - now);
            }
            if (rules.breakEvery > 0) best = Math.min(best, rules.breakEvery * 60_000L - usage.continuousMs);
            return best;
        }

        /**
         * usage after watchedMs of watching (or idleMs of not watching) up to now: a break starts
         * once the continuous watching reaches the rules' length, and an idle stretch as long as a
         * break counts as one.
         */
        static Usage advance(Rules rules, Usage usage, long watchedMs, long idleMs, long now) {
            Usage u = usage;
            if (watchedMs > 0) u = u.copy(u.usedMs + watchedMs, u.continuousMs + watchedMs, -1, -1);
            if (idleMs >= rules.breakMinutes * 60_000L) u = u.copy(-1, 0, -1, -1);
            if (rules.breakEvery > 0 && u.continuousMs >= rules.breakEvery * 60_000L && u.allowUntil <= now) {
                u = u.copy(-1, 0, now + rules.breakMinutes * 60_000L, -1);
            }
            return u;
        }
    }

    private static final String PREFS = "kids";
    private static final int UNLOCK_MS = 10 * 60 * 1000;

    private static boolean started;
    private static Context app;

    private static boolean enabled;
    private static Kind kind = Kind.KIDS;
    /** Package names shown in the mode, in Home's order. */
    private static List<String> allowed = Collections.emptyList();
    /** The packages behind the allowed tiles; kept so the guard knows them without Home's app list. */
    private static Set<String> allowedPackages = Collections.emptySet();
    /** Packages that play the TV's inputs: kept away like apps, unless an input is allowed. */
    private static Set<String> sourcePlayers = Collections.emptySet();
    private static boolean blockOthers = false;
    private static Rules rules = new Rules(0, 0, KidsTime.DEFAULT_START, 0, 0, KidsTime.BREAK_MINUTES);
    /** A parent left the mode with the PIN; it comes back by itself (see {@link #rearm()}). */
    private static boolean suspended;
    private static Usage usage = new Usage(0, 0, 0, 0);
    private static String usageDay = "";

    /** The quiz-for-time plan and what it has bought so far; see {@link KidsPlan} and {@link KidsLedger}. */
    private static KidsPlan plan = new KidsPlan();
    private static KidsLedger ledger = new KidsLedger();

    /** Parental PIN, SHA-256 of "cleanmitv:" + pin; null when none is set. */
    private static String pinHash;
    private static boolean pinArrows;
    /** Elapsed-realtime up to which the PIN lets settings through without asking again. */
    private static long unlockedUntil;

    private Kids() {}

    /**
     * In the mode now. A mode with no apps picked still counts: that is the "hide everything" setup,
     * where Home shows only the Kids mode card.
     */
    public static boolean active() {
        return enabled && !suspended;
    }

    /** The lock now, recomputed by KidsWatch; null while not locked or not active. */
    public static volatile Lock lock;

    /** An app kept away by the guard, for Home to say so: its label (or package) and when. */
    public static volatile String blocked;
    public static volatile long blockedAt;

    /** A warning for Home to show while it is in front (KidsWatch); counts up for each new one. */
    public static volatile String warning;
    public static volatile long warningAt;

    public static void init(Context context) {
        if (started) return;
        started = true;
        app = context.getApplicationContext();
        SharedPreferences p = prefs();
        enabled = p.getBoolean("enabled", false);
        kind = Kind.of(p.getString("kind", null));
        allowed = new ArrayList<>(Arrays.asList(emptyIfNull(p.getString("allowed", null)).split("\n")));
        allowed.removeIf(String::isEmpty);
        allowedPackages = p.getStringSet("allowed_packages", null);
        if (allowedPackages == null) allowedPackages = Collections.emptySet();
        sourcePlayers = p.getStringSet("source_players", null);
        if (sourcePlayers == null) sourcePlayers = Collections.emptySet();
        blockOthers = p.getBoolean("block_others", false);
        Rules loaded = new Rules(
                p.getInt("weekday_minutes", 0),
                p.getInt("weekend_minutes", 0),
                p.getInt("start_minute", KidsTime.DEFAULT_START),
                p.getInt("end_minute", 0),
                p.getInt("break_every", 0),
                p.getInt("break_minutes", KidsTime.BREAK_MINUTES));
        rules = loaded.valid() ? loaded : new Rules(0, 0, KidsTime.DEFAULT_START, 0, 0, KidsTime.BREAK_MINUTES);
        usageDay = emptyIfNull(p.getString("usage_day", null));
        usage = new Usage(
                p.getLong("used_ms", 0),
                p.getLong("continuous_ms", 0),
                p.getLong("break_until", 0),
                p.getLong("allow_until", 0));
        pinHash = p.getString("pin_hash", null);
        pinArrows = p.getBoolean("pin_arrows", false);
        plan = KidsPlan.load(app);
        ledger = KidsLedger.load(app);
        rollDay(System.currentTimeMillis());
    }

    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }

    private static SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void chooseEnabled(boolean on) {
        enabled = on;
        // Turned on in setup: the parent is still there; the mode starts with "Start" or by itself.
        suspended = on;
        prefs().edit().putBoolean("enabled", on).apply();
    }

    public static void chooseKind(Kind k) {
        kind = k;
        prefs().edit().putString("kind", k.key).apply();
    }

    public static void chooseAllowed(List<String> keys) {
        allowed = new ArrayList<>(keys);
        prefs().edit().putString("allowed", join(keys)).apply();
    }

    public static void toggleAllowed(String pkg) {
        List<String> next = new ArrayList<>(allowed);
        if (next.contains(pkg)) next.remove(pkg);
        else next.add(pkg);
        chooseAllowed(next);
    }

    /** The packages behind the allowed tiles and every input's player; see {@link #allowedPackages}. */
    public static void notePackages(Set<String> allowedPkgs, Set<String> players) {
        if (allowedPkgs.equals(allowedPackages) && players.equals(sourcePlayers)) return;
        allowedPackages = new HashSet<>(allowedPkgs);
        sourcePlayers = new HashSet<>(players);
        prefs().edit()
                .putStringSet("allowed_packages", allowedPackages)
                .putStringSet("source_players", sourcePlayers)
                .apply();
    }

    public static void chooseBlockOthers(boolean on) {
        blockOthers = on;
        prefs().edit().putBoolean("block_others", on).apply();
    }

    /**
     * New rules hold at once: time a parent allowed under the old ones ends with them.
     */
    public static void chooseRules(Rules r) {
        if (!r.equals(rules) && usage.allowUntil > 0) saveUsage(usage.copy(-1, -1, -1, 0));
        rules = r;
        prefs().edit()
                .putInt("weekday_minutes", r.weekdayMinutes)
                .putInt("weekend_minutes", r.weekendMinutes)
                .putInt("start_minute", r.startMinute)
                .putInt("end_minute", r.endMinute)
                .putInt("break_every", r.breakEvery)
                .putInt("break_minutes", r.breakMinutes)
                .apply();
    }

    /** Into the mode, no PIN needed. */
    public static void start() {
        suspended = false;
    }

    /** Out of it for now, once the PIN was right. */
    public static void suspend() {
        suspended = true;
    }

    /** The mode comes back by itself: the screensaver started or the screen went off. */
    public static void rearm() {
        if (enabled && suspended) suspended = false;
    }

    /** Turned off for good. */
    public static void turnOff() {
        chooseEnabled(false);
        suspended = false;
    }

    private static String dayOf(long now) {
        return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    /** Today, yyyy-MM-dd: the day the ledger and the bank count by. */
    public static String day() {
        return dayOf(System.currentTimeMillis());
    }

    /** A new day starts with nothing watched; a break or an allowance from yesterday is over. */
    private static void rollDay(long now) {
        String day = dayOf(now);
        if (day.equals(usageDay)) return;
        usageDay = day;
        long allowUntil = usage.allowUntil > now ? usage.allowUntil : 0;
        saveUsage(new Usage(0, 0, 0, allowUntil));
        ledger.roll(day);
        ledger.save(app);
    }

    private static void saveUsage(Usage u) {
        usage = u;
        prefs().edit()
                .putString("usage_day", usageDay)
                .putLong("used_ms", u.usedMs)
                .putLong("continuous_ms", u.continuousMs)
                .putLong("break_until", u.breakUntil)
                .putLong("allow_until", u.allowUntil)
                .apply();
    }

    /** watchedMs more of watching, or idleMs of not watching, at now. */
    public static void count(long watchedMs, long idleMs, long now) {
        rollDay(now);
        Usage next = KidsTime.advance(rules, usage, watchedMs, idleMs, now);
        // Time bought with questions is spent before the day's quota is: a round really does add
        // watching. The break clock above still counts the whole stretch, as it should.
        if (watchedMs > 0) {
            long covered = ledger.spend(watchedMs);
            if (covered > 0) {
                ledger.save(app);
                next = new Usage(Math.max(0, next.usedMs - covered), next.continuousMs,
                        next.breakUntil, next.allowUntil);
            }
        }
        if (!sameUsage(next, usage)) saveUsage(next);
    }

    private static boolean sameUsage(Usage a, Usage b) {
        return a.usedMs == b.usedMs && a.continuousMs == b.continuousMs
                && a.breakUntil == b.breakUntil && a.allowUntil == b.allowUntil;
    }

    /** A parent allows watching for minutes more, or until the end of today with null. */
    public static void allow(Integer minutes) {
        long now = System.currentTimeMillis();
        rollDay(now);
        long until;
        if (minutes == null) {
            until = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } else {
            until = Math.max(now, usage.allowUntil) + minutes * 60_000L;
        }
        // A break being taken is over too; the watching starts counting toward the next one again.
        saveUsage(new Usage(usage.usedMs, 0, 0, until));
        lock = null;
    }

    /**
     * The rules as today reads them: a holiday keeps the weekend's minutes whatever the weekday.
     * The rest of the rule set is untouched.
     */
    private static Rules todayRules() {
        return plan.holidayWeekend ? rules.withWeekday(rules.weekendMinutes) : rules;
    }

    public static Lock lockNow(long now) {
        if (!active()) return null;
        rollDay(now);
        Lock lock = KidsTime.lock(todayRules(), usage, now, ZoneId.systemDefault());
        // The schedule grid closes the hour whether or not the window in Rules does; a day set to
        // "not at all" and a break being taken come first.
        if (lock != Lock.NO_WATCH && lock != Lock.BREAK && slotClosed(now)) lock = Lock.LATE;
        // Time already bought is spent before the quota is declared used up.
        if (lock == Lock.DAY_USED && ledger.creditMs > 0) return null;
        if (lock == Lock.DAY_USED && quizOffered(now)) return Lock.QUIZ;
        return lock;
    }

    /** Whether the half-hour now sits in a closed slot; false when the grid is unused. */
    private static boolean slotClosed(long now) {
        if (!plan.slotControl || !KidsPlan.gridOk(plan.weekSlots)) return false;
        LocalDateTime at = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime();
        int day = at.getDayOfWeek().getValue() - 1;
        int half = at.getHour() * 2 + (at.getMinute() >= 30 ? 1 : 0);
        return !plan.slotOpen(day, half);
    }

    /** Whether a round of questions would still buy time now. */
    private static boolean quizOffered(long now) {
        if (!plan.quizGate) return false;
        if (ledger.creditMs > 0) return false;
        if (!ledger.roundsLeft(plan.roundsPerDay)) return false;
        return canEarnMore(now);
    }

    /** Room left in the day's quota for the quiz to top up; a day with no limit needs none. */
    private static boolean canEarnMore(long now) {
        long cap = dailyCapMs(now);
        if (cap <= 0) return false;
        return usage.usedMs + ledger.creditMs < cap;
    }

    /**
     * The day's ceiling: the set limit plus everything the quiz can add. The quiz exists exactly
     * for the moment the plain limit runs out, so it has to sit above it, not inside it.
     */
    private static long dailyCapMs(long now) {
        int limit = limitToday(now);
        if (limit <= 0) return 0;
        long extra = (long) Math.max(0, plan.roundsPerDay) * Math.max(0, plan.sessionMinutes);
        return (limit + extra) * 60_000L;
    }

    /**
     * A round was passed: minutes more of watching, capped at what is left of the day's quota and
     * never more than asked. Returns what was actually granted - 0 when the day is already full.
     * Counts the round either way, so a pass cannot be farmed after the last one.
     */
    public static int grantQuizTime(int minutes) {
        if (!plan.quizGate) return 0;
        long now = System.currentTimeMillis();
        rollDay(now);
        ledger.roundsToday++;
        int grant = Math.max(0, minutes);
        long cap = dailyCapMs(now);
        if (cap > 0) {
            long roomMs = cap - usage.usedMs - ledger.creditMs;
            grant = (int) Math.max(0, Math.min(grant * 60_000L, roomMs) / 60_000L);
        }
        if (grant > 0) ledger.grant(grant * 60_000L);
        ledger.save(app);
        lock = active() ? lockNow(now) : null;
        return grant;
    }

    /** One answer, right or wrong: enough wrong ones top up the practice pool. */
    public static void recordAnswer(boolean correct) {
        if (!plan.extraEnabled) return;
        if (correct) {
            if (ledger.wrongSince > 0) {
                ledger.wrongSince = 0;
                ledger.save(app);
            }
            return;
        }
        ledger.wrongSince++;
        if (ledger.wrongSince >= plan.wrongThreshold) {
            ledger.extraPool += plan.extraQuestions;
            ledger.wrongSince = 0;
        }
        ledger.save(app);
    }

    public static KidsPlan plan() {
        return plan;
    }

    public static KidsLedger ledger() {
        return ledger;
    }

    /** Choices made in setup hold from the next tick on. */
    public static void savePlan() {
        plan.save(app);
        lock = active() ? lockNow(System.currentTimeMillis()) : null;
    }

    public static long untilLockMs(long now) {
        return active() ? KidsTime.untilLockMs(todayRules(), usage, now, ZoneId.systemDefault()) : Long.MAX_VALUE;
    }

    /** The day's limit, in minutes; 0 for none. A holiday counts as a weekend. */
    public static int limitToday(long now) {
        LocalDate day = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
        return KidsTime.limitMinutes(todayRules(), day);
    }

    // ---- Parental PIN (ported from AerialSettings) ----

    public static boolean pinSet() {
        return pinHash != null;
    }

    public static boolean pinArrows() {
        return pinArrows;
    }

    /** Whether pin is the PIN, without unlocking anything: for leaving Kids mode and its limits. */
    public static boolean pinMatches(String pin) {
        return pinHash != null && hash(pin).equals(pinHash);
    }

    /** Verify and unlock settings for a while. */
    public static boolean unlock(String pin) {
        if (!pinMatches(pin)) return false;
        unlockedUntil = android.os.SystemClock.elapsedRealtime() + UNLOCK_MS;
        return true;
    }

    /** True while a correct PIN unlocks settings without asking again. */
    public static boolean unlockedRecently() {
        return pinSet() && android.os.SystemClock.elapsedRealtime() <= unlockedUntil;
    }

    public static void choosePin(String pin, boolean arrows) {
        pinHash = pin == null ? null : hash(pin);
        pinArrows = pin != null && arrows;
        unlockedUntil = 0;
        prefs().edit().putString("pin_hash", pinHash).putBoolean("pin_arrows", pinArrows).apply();
    }

    private static String hash(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(("cleanmitv:" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String join(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(keys.get(i));
        }
        return sb.toString();
    }

    // ---- Getters for the UI ----

    public static boolean enabled() { return enabled; }
    public static Kind kind() { return kind; }
    public static List<String> allowed() { return allowed; }
    public static Set<String> allowedPackages() { return allowedPackages; }
    public static Set<String> sourcePlayers() { return sourcePlayers; }
    public static boolean blockOthers() { return blockOthers; }

    /** How long the app picked on Home is let through even if the allow-list missed it. */
    private static final long LAUNCH_GRACE_MS = 8_000;
    private static final java.util.Map<String, Long> launched = new java.util.HashMap<>();

    /**
     * The parent (or child) picked this tile on Home just now. The guard must not fight the tap it
     * came from, so this app is let through for a few seconds even when the allow-list is stale.
     */
    public static void noteLaunch(String pkg) {
        if (pkg == null) return;
        synchronized (launched) {
            long now = android.os.SystemClock.elapsedRealtime();
            launched.entrySet().removeIf(e -> now - e.getValue() > LAUNCH_GRACE_MS);
            launched.put(pkg, now);
        }
    }

    public static boolean justLaunched(String pkg) {
        if (pkg == null) return false;
        synchronized (launched) {
            Long at = launched.get(pkg);
            return at != null && android.os.SystemClock.elapsedRealtime() - at <= LAUNCH_GRACE_MS;
        }
    }
    public static Rules rules() { return rules; }
    public static Usage usage() { return usage; }
    public static boolean suspended() { return suspended; }
    public static boolean started() { return started; }
}
