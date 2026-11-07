package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The quiz-for-time plan: how much watching a round of questions buys, how many rounds a day is
 * worth, and the schedule that decides when the TV may be watched at all.
 *
 * <p>Kept apart from {@link Kids.Rules} on purpose: Rules is the small, frozen set the time
 * arithmetic was built around, and every field here is optional on top of it. Every choice is
 * made on the TV itself - the parent walks through {@link KidsSetupActivity} with the remote -
 * so nothing here is reachable from the network.
 *
 * <p>Half-hour slots are a 7x48 string of '1' (the TV may be watched) and '0': day 0 is Monday,
 * slot 0 is 00:00-00:30. An empty string means the window in {@link Kids.Rules} applies instead.
 */
public final class KidsPlan {

    static final String PREFS = "kids_plan";

    /** Answer a round of questions: pass buys sessionMinutes. */
    public static final String MODE_ROUND = "round";
    /** Answer questions until questionsPerRound right in a row; a wrong answer starts over. */
    public static final String MODE_STREAK = "streak";
    /** No time at all: only the practice pool is cleared. */
    public static final String MODE_PRACTICE = "practice";

    // ---- Quiz for time ----
    public boolean quizGate;
    public int sessionMinutes = 10;
    public int questionsPerRound = 5;
    /** Percent of the round that has to be right; 60 is 3 of 5. */
    public int passRatio = 60;
    public int roundsPerDay = 3;
    public String playMode = MODE_ROUND;
    public int autoNextSeconds = 3;

    // ---- Question bank ----
    public static final String STAGE_PRIMARY = "primary";
    public static final String STAGE_MIDDLE = "middle";
    public static final String STAGE_HIGH = "high";
    /** Which school stage the questions come from; only primary is stocked for now. */
    public String stage = STAGE_PRIMARY;
    public boolean subjectChinese = true;
    public boolean subjectMath = true;
    public boolean subjectEnglish = true;

    // ---- Practice pool, stars, review ----
    public boolean extraEnabled = true;
    public int wrongThreshold = 3;
    public int extraQuestions = 2;
    public boolean starReward;
    public int[] starMinutes = {1, 1, 2, 3, 5};
    public boolean spacedRepetition = true;

    // ---- Schedule ----
    /** 7x48 half-hour slots; empty means use Rules.startMinute/endMinute. */
    public String weekSlots = "";
    /** Whether a closed slot locks the TV, or only suspends the quiz offer. */
    public boolean slotControl = true;
    /** Whether the quiz can be offered outside the window; off, so bedtime stays bedtime. */
    public boolean quizOutsideHours;
    /** Today counts as a weekend: the weekend minutes and Saturday's slots apply. */
    public boolean holidayWeekend;

    public static KidsPlan load(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        KidsPlan plan = new KidsPlan();
        plan.quizGate = p.getBoolean("quiz_gate", plan.quizGate);
        plan.sessionMinutes = p.getInt("session_minutes", plan.sessionMinutes);
        plan.questionsPerRound = p.getInt("questions_per_round", plan.questionsPerRound);
        plan.passRatio = p.getInt("pass_ratio", plan.passRatio);
        plan.roundsPerDay = p.getInt("rounds_per_day", plan.roundsPerDay);
        plan.playMode = p.getString("play_mode", plan.playMode);
        plan.autoNextSeconds = p.getInt("auto_next", plan.autoNextSeconds);
        plan.stage = p.getString("stage", plan.stage);
        plan.subjectChinese = p.getBoolean("subject_chinese", plan.subjectChinese);
        plan.subjectMath = p.getBoolean("subject_math", plan.subjectMath);
        plan.subjectEnglish = p.getBoolean("subject_english", plan.subjectEnglish);
        plan.extraEnabled = p.getBoolean("extra_enabled", plan.extraEnabled);
        plan.wrongThreshold = p.getInt("wrong_threshold", plan.wrongThreshold);
        plan.extraQuestions = p.getInt("extra_questions", plan.extraQuestions);
        plan.starReward = p.getBoolean("star_reward", plan.starReward);
        for (int i = 0; i < plan.starMinutes.length; i++) {
            plan.starMinutes[i] = p.getInt("star_" + i, plan.starMinutes[i]);
        }
        plan.spacedRepetition = p.getBoolean("spaced", plan.spacedRepetition);
        plan.weekSlots = p.getString("week_slots", plan.weekSlots);
        plan.slotControl = p.getBoolean("slot_control", plan.slotControl);
        plan.quizOutsideHours = p.getBoolean("quiz_outside", plan.quizOutsideHours);
        plan.holidayWeekend = p.getBoolean("holiday_weekend", plan.holidayWeekend);
        return plan;
    }

    public void save(Context context) {
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putBoolean("quiz_gate", quizGate)
                .putInt("session_minutes", sessionMinutes)
                .putInt("questions_per_round", questionsPerRound)
                .putInt("pass_ratio", passRatio)
                .putInt("rounds_per_day", roundsPerDay)
                .putString("play_mode", playMode)
                .putInt("auto_next", autoNextSeconds)
                .putString("stage", stage)
                .putBoolean("subject_chinese", subjectChinese)
                .putBoolean("subject_math", subjectMath)
                .putBoolean("subject_english", subjectEnglish)
                .putBoolean("extra_enabled", extraEnabled)
                .putInt("wrong_threshold", wrongThreshold)
                .putInt("extra_questions", extraQuestions)
                .putBoolean("star_reward", starReward)
                .putBoolean("spaced", spacedRepetition)
                .putString("week_slots", weekSlots)
                .putBoolean("slot_control", slotControl)
                .putBoolean("quiz_outside", quizOutsideHours)
                .putBoolean("holiday_weekend", holidayWeekend);
        for (int i = 0; i < starMinutes.length; i++) e.putInt("star_" + i, starMinutes[i]);
        e.apply();
    }

    /** How many of a round of n have to be right to pass. */
    public int passCount(int n) {
        int need = (int) Math.ceil(n * (passRatio / 100.0));
        return Math.max(1, Math.min(n, need));
    }

    /** A 7x48 grid with every slot open: the default the schedule page starts from. */
    public static String allOpen() {
        StringBuilder sb = new StringBuilder(7 * 48);
        for (int i = 0; i < 7 * 48; i++) sb.append('1');
        return sb.toString();
    }

    /** Whether the grid is usable; anything else falls back to the window in Rules. */
    public static boolean gridOk(String slots) {
        if (slots == null || slots.length() != 7 * 48) return false;
        for (int i = 0; i < slots.length(); i++) {
            char c = slots.charAt(i);
            if (c != '0' && c != '1') return false;
        }
        return true;
    }

    /** The day's slots, with Saturday's for a holiday today. day: 0 = Monday. */
    public boolean slotOpen(int day, int halfHour) {
        if (!gridOk(weekSlots)) return true;
        int row = holidayWeekend ? 5 : day;
        return weekSlots.charAt(row * 48 + halfHour) == '1';
    }

    /** The subjects asked for; empty means every subject, so nothing can leave the round bare. */
    public java.util.Set<String> subjects() {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (subjectChinese) set.add("chinese");
        if (subjectMath) set.add("math");
        if (subjectEnglish) set.add("english");
        return set;
    }
}
