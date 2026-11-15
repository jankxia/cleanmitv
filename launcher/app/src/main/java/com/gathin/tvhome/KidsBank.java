package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The questions, one file per grade: assets/quiz/primary-1.json ... quiz/high-12.json, with a
 * downloaded pack allowed to stand in for the same names (see {@link QuizPack}).
 *
 * <p>Every question is ours: written for this fork, not copied from another app. Each has a subject
 * (math / poem / common), four options, an answer index, and a level from 1 to 5 that the star
 * reward pays by.
 *
 * <p>A grade is read the first time it is asked for and kept, so a round only pays for the grades it
 * may draw from instead of the whole bank.
 *
 * <p>Progress lives in its own preferences: how often each id was right or wrong, and which were
 * asked today. A round prefers questions not answered today, then ones never answered right - which
 * is the whole of the spaced repetition worth having with a hundred questions.
 */
public final class KidsBank {

    static final String PREFS = "kids_bank";
    /** Where the grade files live, inside the apk and inside a downloaded pack alike. */
    private static final String DIR = "quiz";

    /** One question. */
    public static final class Q {
        public final String id;
        /** primary / middle / high, and the grade inside it: 1-6, 7-9, 10-12. */
        public final String stage;
        public final int grade;
        public final String subject;
        public final String text;
        public final String[] options;
        public final int answer;
        public final int level;

        Q(String id, String stage, int grade, String subject, String text, String[] options,
           int answer, int level) {
            this.id = id;
            this.stage = stage;
            this.grade = grade;
            this.subject = subject;
            this.text = text;
            this.options = options;
            this.answer = answer;
            this.level = level;
        }
    }

    /** Parsed files, by name; a pack download calls {@link #forget()} to drop them. */
    private static final Map<String, List<Q>> loaded = new HashMap<>();

    /** The asset a stage and grade sit in, e.g. quiz/primary-1.json. */
    static String fileFor(String stage, int grade) {
        String prefix = KidsPlan.STAGE_MIDDLE.equals(stage) ? "middle"
                : KidsPlan.STAGE_HIGH.equals(stage) ? "high" : "primary";
        return DIR + "/" + prefix + "-" + grade + ".json";
    }

    /** A downloaded pack lives here; a file of the same name wins over the one in the apk. */
    static File packDir(Context context) {
        return new File(context.getFilesDir(), "quizpack");
    }

    /** True when at least one grade file is on the device. */
    public static boolean available(Context context) {
        File pack = packDir(context);
        if (pack.isDirectory()) {
            String[] names = pack.list();
            if (names != null) {
                for (String n : names) if (n.endsWith(".json")) return true;
            }
        }
        try {
            String[] names = context.getAssets().list(DIR);
            if (names != null) {
                for (String n : names) if (n.endsWith(".json")) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Drops the parsed files: a pack went in or out and the questions are no longer the same. */
    public static synchronized void forget() {
        loaded.clear();
    }

    /** True when the text is a grade file with at least one question the bank can use. */
    static boolean usable(String json) {
        List<Q> probe = new ArrayList<>();
        parse(json, probe);
        return !probe.isEmpty();
    }

    private static List<Q> read(Context context, String name) {
        List<Q> cached = loaded.get(name);
        if (cached != null) return cached;
        List<Q> list = new ArrayList<>();
        String json = null;
        File pack = new File(packDir(context), name.substring(DIR.length() + 1));
        if (pack.isFile()) json = text(pack);
        if (json == null) {
            try (InputStream in = context.getAssets().open(name)) {
                json = text(in);
            } catch (Exception ignored) {
            }
        }
        if (json != null) parse(json, list);
        loaded.put(name, list);
        return list;
    }

    private static String text(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return text(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(InputStream in) {
        try {
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void parse(String json, List<Q> out) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.getJSONArray("questions");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray opts = o.getJSONArray("o");
                String[] options = new String[opts.length()];
                for (int j = 0; j < options.length; j++) options[j] = opts.getString(j);
                int answer = o.getInt("a");
                if (options.length < 2 || answer < 0 || answer >= options.length) continue;
                out.add(new Q(o.getString("id"), o.optString("st", "primary"), o.optInt("g", 1),
                        o.optString("s", "common"), o.getString("q"), options, answer,
                        o.optInt("l", 1)));
            }
        } catch (Exception e) {
            out.clear();
        }
    }

    /** A round of up to n questions: not asked today first, then never answered right, then the rest. */
    public static List<Q> pick(Context context, int n) {
        return pick(context, n, null, null, null);
    }

    /** The same, only from the subjects picked; an empty set means every subject. */
    public static List<Q> pick(Context context, int n, Set<String> subjects) {
        return pick(context, n, null, null, subjects);
    }

    /**
     * The same, only from the grades picked in one stage; an empty grade set means every grade of
     * that stage, and an empty subject set every subject. A grade with nothing on file falls back to
     * the rest of its stage, then to the whole bank, so a thin year can never leave the round empty.
     */
    public static List<Q> pick(Context c, int n, String stage, Set<Integer> grades, Set<String> subjects) {
        List<Q> pool = stagePool(c, stage, grades, subjects);
        if (pool.isEmpty()) pool = stagePool(c, stage, null, subjects);
        if (pool.isEmpty()) pool = everyPool(c, subjects);

        Set<String> seen = seenToday(c);
        List<Q> fresh = new ArrayList<>();
        List<Q> unseen = new ArrayList<>();
        List<Q> rest = new ArrayList<>();
        for (Q q : pool) {
            if (seen.contains(q.id)) rest.add(q);
            else if (right(c, q.id) == 0) unseen.add(q);
            else fresh.add(q);
        }
        Random random = new Random(System.currentTimeMillis());
        Collections.shuffle(fresh, random);
        Collections.shuffle(unseen, random);
        Collections.shuffle(rest, random);
        List<Q> out = new ArrayList<>(n);
        add(out, unseen, n);
        add(out, fresh, n);
        add(out, rest, n);
        Collections.shuffle(out, random);
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }

    /** The questions of one stage from the grades picked (and the subjects on). */
    private static List<Q> stagePool(Context c, String stage, Set<Integer> grades, Set<String> subjects) {
        List<Q> out = new ArrayList<>();
        if (stage == null) return out;
        boolean anyGrade = grades == null || grades.isEmpty();
        boolean bySubject = subjects != null && !subjects.isEmpty();
        for (int grade : KidsPlan.grades(stage)) {
            if (!anyGrade && !grades.contains(grade)) continue;
            for (Q q : read(c, fileFor(stage, grade))) {
                if (bySubject && !subjects.contains(q.subject)) continue;
                out.add(q);
            }
        }
        return out;
    }

    /** Every grade of every stage: the last fallback, only read when the picked ones came up empty. */
    private static List<Q> everyPool(Context c, Set<String> subjects) {
        List<Q> out = new ArrayList<>();
        for (String stage : new String[]{KidsPlan.STAGE_PRIMARY, KidsPlan.STAGE_MIDDLE, KidsPlan.STAGE_HIGH}) {
            out.addAll(stagePool(c, stage, null, subjects));
        }
        return out;
    }

    private static void add(List<Q> out, List<Q> from, int n) {
        for (Q q : from) {
            if (out.size() >= n) return;
            out.add(q);
        }
    }

    /** A round of the given size, the practice pool first, then the usual pick. */
    public static List<Q> pickWithPractice(Context context, int n, int practice, String stage,
                                          Set<Integer> grades, Set<String> subjects) {
        List<Q> list = new ArrayList<>(pick(context, n, stage, grades, subjects));
        if (practice > 0) {
            List<Q> extra = pick(context, practice, stage, grades, subjects);
            for (Q q : extra) {
                if (list.size() >= n + practice) break;
                if (list.contains(q)) continue;
                list.add(q);
            }
        }
        return list;
    }

    // ---- What has been answered ----

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int right(Context c, String id) {
        return prefs(c).getInt("r_" + id, 0);
    }

    public static int wrong(Context c, String id) {
        return prefs(c).getInt("w_" + id, 0);
    }

    /** Marks the answer; returns how many wrong in a row since the pool last paid out. */
    public static void record(Context c, String id, boolean correct, String today) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (correct) e.putInt("r_" + id, right(c, id) + 1);
        else e.putInt("w_" + id, wrong(c, id) + 1);
        Set<String> seen = seenToday(c);
        seen.add(id);
        e.putStringSet("seen_day", seen).putString("seen_on", today).apply();
    }

    /** Ids asked today; a new day starts empty. */
    public static Set<String> seenToday(Context c) {
        SharedPreferences p = prefs(c);
        String day = p.getString("seen_on", "");
        Set<String> seen = p.getStringSet("seen_day", null);
        String today = Kids.day();
        if (seen == null || !today.equals(day)) return new HashSet<>();
        return new HashSet<>(seen);
    }

    /** Forgets all progress, for the "clear the bank" row in the setup page. */
    public static void reset(Context c) {
        prefs(c).edit().clear().apply();
    }
}
