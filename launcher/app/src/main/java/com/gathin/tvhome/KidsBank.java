package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The questions, read once from assets/kids-quiz.json, plus what has been answered so far.
 *
 * <p>Every question is ours: written for this fork, not copied from another app. Each has a subject
 * (math / poem / common), four options, an answer index, and a level from 1 to 5 that the star
 * reward pays by.
 *
 * <p>Progress lives in its own preferences: how often each id was right or wrong, and which were
 * asked today. A round prefers questions not answered today, then ones never answered right - which
 * is the whole of the spaced repetition worth having with a hundred questions.
 */
public final class KidsBank {

    static final String PREFS = "kids_bank";
    private static final String ASSET = "kids-quiz.json";

    /** One question. */
    public static final class Q {
        public final String id;
        public final String subject;
        public final String text;
        public final String[] options;
        public final int answer;
        public final int level;

        Q(String id, String subject, String text, String[] options, int answer, int level) {
            this.id = id;
            this.subject = subject;
            this.text = text;
            this.options = options;
            this.answer = answer;
            this.level = level;
        }
    }

    private static List<Q> cached;

    /** Every question, or an empty list if the asset is missing or broken. */
    public static synchronized List<Q> all(Context context) {
        if (cached != null) return cached;
        List<Q> list = new ArrayList<>();
        try (InputStream in = context.getAssets().open(ASSET)) {
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.getJSONArray("questions");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray opts = o.getJSONArray("o");
                String[] options = new String[opts.length()];
                for (int j = 0; j < options.length; j++) options[j] = opts.getString(j);
                int answer = o.getInt("a");
                if (options.length < 2 || answer < 0 || answer >= options.length) continue;
                list.add(new Q(o.getString("id"), o.optString("s", "common"),
                        o.getString("q"), options, answer, o.optInt("l", 1)));
            }
        } catch (Exception e) {
            list.clear();
        }
        cached = list;
        return list;
    }

    /** A round of up to n questions: not asked today first, then never answered right, then the rest. */
    public static List<Q> pick(Context context, int n) {
        return pick(context, n, null);
    }

    /** The same, only from the subjects picked; an empty set means every subject. */
    public static List<Q> pick(Context context, int n, Set<String> subjects) {
        List<Q> all = all(context);
        Set<String> seen = seenToday(context);
        boolean filter = subjects != null && !subjects.isEmpty();
        List<Q> fresh = new ArrayList<>();
        List<Q> unseen = new ArrayList<>();
        List<Q> rest = new ArrayList<>();
        for (Q q : all) {
            if (filter && !subjects.contains(q.subject)) continue;
            if (seen.contains(q.id)) rest.add(q);
            else if (right(context, q.id) == 0) unseen.add(q);
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
        // A subject picked with nothing in the bank would leave the round empty: take any subject.
        if (out.isEmpty() && filter) return pick(context, n, null);
        Collections.shuffle(out, random);
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }

    private static void add(List<Q> out, List<Q> from, int n) {
        for (Q q : from) {
            if (out.size() >= n) return;
            out.add(q);
        }
    }

    /** A round of the given size, the practice pool first, then the usual pick. */
    public static List<Q> pickWithPractice(Context context, int n, int practice, Set<String> subjects) {
        List<Q> list = new ArrayList<>(pick(context, n, subjects));
        if (practice > 0) {
            List<Q> extra = pick(context, practice, subjects);
            for (Q q : extra) {
                if (list.size() >= n + practice) break;
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
