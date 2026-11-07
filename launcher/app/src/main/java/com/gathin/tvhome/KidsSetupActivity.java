package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Kids and Elders mode setup, reached from Launcher Settings: who it is for, the tiles it shows
 * (on the right, in the order picked) and, for children, the time limits. Each choice is kept at
 * once; "Start" goes into the mode. Ported from aerial-tv's KidsSetupSheet.
 */
public class KidsSetupActivity extends Activity {

    /** "kids" or "elders": which mode this screen opens on (the gear's menu passes it). */
    static final String EXTRA_KIND = "kids_kind";

    private final List<App> apps = new ArrayList<>();
    private AppListAdapter adapter;
    private TextView appsTitle;
    private TextView appsHint;
    private TextView startButton;
    private TextView turnOffButton;
    private TextView pinButton;

    private static final class App {
        final String pkg;
        final String label;
        final Drawable icon;

        App(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Kids.init(this);
        setContentView(R.layout.activity_kids_setup);

        appsTitle = findViewById(R.id.appsTitle);
        appsHint = findViewById(R.id.appsHint);
        startButton = findViewById(R.id.startButton);
        turnOffButton = findViewById(R.id.turnOffButton);
        pinButton = findViewById(R.id.pinButton);

        // Opened from the gear's menu: the kind it asked for is the one this screen starts on.
        String asked = getIntent().getStringExtra(EXTRA_KIND);
        if ("elders".equals(asked)) Kids.chooseKind(Kids.Kind.ELDERS);
        else if ("kids".equals(asked)) Kids.chooseKind(Kids.Kind.KIDS);

        RecyclerView list = findViewById(R.id.appsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter();
        list.setAdapter(adapter);

        loadApps();
        bindChips();
        bindPlanChips();
        refresh();
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(leanback, 0);
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            String label = String.valueOf(ri.loadLabel(pm));
            Drawable icon = ri.loadIcon(pm);
            apps.add(new App(pkg, label, icon));
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
        adapter.notifyDataSetChanged();
    }

    private void bindChips() {
        bindKind(R.id.kindKids, Kids.Kind.KIDS);
        bindKind(R.id.kindElders, Kids.Kind.ELDERS);
        // Other apps are always let through: the row keeps the one choice it has.
        findViewById(R.id.blockOff).setOnClickListener(v -> {
            Kids.chooseBlockOthers(false);
            refresh();
        });
        bindMinutes(R.id.weekdayNone, -1, true);
        bindMinutes(R.id.weekday10, 10, true);
        bindMinutes(R.id.weekday30, 30, true);
        bindMinutes(R.id.weekday60, 60, true);
        bindMinutes(R.id.weekday90, 90, true);
        bindMinutes(R.id.weekday120, 120, true);
        bindMinutes(R.id.weekend0, 0, false);
        bindMinutes(R.id.weekend10, 10, false);
        bindMinutes(R.id.weekend30, 30, false);
        bindMinutes(R.id.weekend60, 60, false);
        bindMinutes(R.id.weekend90, 90, false);
        bindMinutes(R.id.weekend120, 120, false);
        bindLatest(R.id.latest0, 0);
        bindLatest(R.id.latest20, 20 * 60);
        bindLatest(R.id.latest21, 21 * 60);
        bindLatest(R.id.latest22, 22 * 60);
        bindBreak(R.id.break0, 0);
        bindBreak(R.id.break5, 5);
        bindBreak(R.id.break10, 10);
        bindBreak(R.id.break30, 30);

        // Raising the lock screen over a video needs this: without it Android 11 refuses the launch.
        findViewById(R.id.overlayButton).setOnClickListener(v -> openOverlaySettings());

        startButton.setOnClickListener(v -> {
            // No PIN means no way out again: the mode can only be left with one, so ask for it here
            // and go straight into the mode once it is set.
            if (!Kids.pinSet()) {
                Toast.makeText(this, R.string.kids_pin_first, Toast.LENGTH_LONG).show();
                createPin(true);
                return;
            }
            beginMode();
        });
        turnOffButton.setOnClickListener(v -> {
            // Turning the mode off is leaving it; the parent's PIN is the only way through.
            if (Kids.pinSet() && !Kids.unlockedRecently()) {
                PinDialog verify = new PinDialog(this, PinDialog.VERIFY, Kids.pinArrows(),
                        new PinDialog.PinListener() {
                    @Override public void onPin(String pin) {
                        if (Kids.pinMatches(pin)) turnOff();
                        else toastWrong();
                    }

                    @Override public void onCancel() {
                    }
                });
                verify.show();
                return;
            }
            turnOff();
        });
        pinButton.setOnClickListener(v -> {
            if (Kids.pinSet()) {
                // Set again: verify the old one first, in the kind it was entered in.
                PinDialog verify = new PinDialog(this, PinDialog.VERIFY, Kids.pinArrows(),
                        new PinDialog.PinListener() {
                    @Override public void onPin(String pin) {
                        if (Kids.pinMatches(pin)) createPin(false);
                        else toastWrong();
                    }

                    @Override public void onCancel() {
                    }
                });
                verify.show();
            } else {
                createPin(false);
            }
        });
    }

    /** Off for good: the mode is disabled rather than suspended, so it does not come back at sleep. */
    private void turnOff() {
        Kids.turnOff();
        KidsWatch.poke();
        finish();
    }

    /** Into the mode: enabled, and not the suspended "parent is still here" state. */
    private void beginMode() {
        // No app picked is a real setup: the mode hides everything, Home shows only its own card.
        if (Kids.allowed().isEmpty()) {
            Toast.makeText(this, R.string.kids_no_apps_tip, Toast.LENGTH_LONG).show();
        }
        Kids.chooseEnabled(true);
        Kids.start();
        KidsWatch.poke();
        finish();
    }

    /**
     * Asks which kind of PIN to set, then opens the pad for it: four digits, or four D-pad presses.
     * startAfter: "Start" asked for a PIN first, so the mode begins as soon as it is saved.
     */
    private void createPin(boolean startAfter) {
        String[] kinds = {getString(R.string.pin_kind_digits), getString(R.string.pin_kind_arrows)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.kids_pin_kind)
                .setItems(kinds, (dialog, which) -> showCreatePin(which == 1, startAfter))
                .show();
    }

    private void showCreatePin(boolean arrows, boolean startAfter) {
        PinDialog create = new PinDialog(this, PinDialog.CREATE, arrows, new PinDialog.PinListener() {
            @Override public void onPin(String pin) {
                Kids.choosePin(pin, arrows);
                refresh();
                Toast.makeText(KidsSetupActivity.this, R.string.pin_saved, Toast.LENGTH_SHORT).show();
                if (startAfter) beginMode();
            }

            @Override public void onCancel() {
            }
        });
        create.show();
    }

    private void toastWrong() {
        Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_LONG).show();
    }

    private void bindKind(int id, Kids.Kind kind) {
        findViewById(id).setOnClickListener(v -> {
            Kids.chooseKind(kind);
            // Other apps stay open in both modes: that is the only choice the row offers.
            Kids.chooseBlockOthers(false);
            refresh();
        });
    }

    private void bindMinutes(int id, int minutes, boolean weekday) {
        findViewById(id).setOnClickListener(v -> {
            Kids.Rules r = Kids.rules();
            // copy() reads -1 as "leave alone", so a day set to "not at all" needs its own setter.
            Kids.Rules next = weekday ? r.withWeekday(minutes) : r.withWeekend(minutes);
            // A break longer than the day's minutes never comes, so a shorter day pulls the break in
            // with it instead of leaving a setting that can do nothing.
            if (!breakFits(next.breakEvery, next)) {
                int to = breakInside(shortestLimit(next));
                Toast.makeText(this, getString(R.string.kids_break_shrunk, breakLabel(to)),
                        Toast.LENGTH_SHORT).show();
                next = next.copy(-1, -1, -1, -1, to, -1);
            }
            Kids.chooseRules(next);
            KidsWatch.poke();
            refresh();
        });
    }

    private void bindLatest(int id, int endMinute) {
        findViewById(id).setOnClickListener(v -> {
            Kids.Rules r = Kids.rules();
            Kids.Rules next = r.copy(-1, -1, -1, endMinute, -1, -1);
            // A shorter window can leave a day's minutes too long to ever be used up.
            if (!limitFits(next.weekdayMinutes, next) || !limitFits(next.weekendMinutes, next)) {
                Toast.makeText(this, R.string.kids_limit_shrunk, Toast.LENGTH_SHORT).show();
                int wd = limitFits(next.weekdayMinutes, next) ? next.weekdayMinutes
                        : insideWindow(next.weekdayMinutes, next);
                int we = limitFits(next.weekendMinutes, next) ? next.weekendMinutes
                        : insideWindow(next.weekendMinutes, next);
                Kids.Rules fixed = next.withWeekday(wd).withWeekend(we);
                if (!breakFits(fixed.breakEvery, fixed)) {
                    fixed = fixed.copy(-1, -1, -1, -1, breakInside(shortestLimit(fixed)), -1);
                }
                next = fixed;
            }
            Kids.chooseRules(next);
            KidsWatch.poke();
            refresh();
        });
    }

    private void bindBreak(int id, int every) {
        findViewById(id).setOnClickListener(v -> {
            Kids.Rules r = Kids.rules();
            if (!breakFits(every, r)) {
                // The chip is hidden when it cannot work; this is the belt for the braces.
                Toast.makeText(this, getString(R.string.kids_break_shrunk,
                        breakLabel(breakInside(shortestLimit(r)))), Toast.LENGTH_SHORT).show();
                return;
            }
            Kids.chooseRules(r.copy(-1, -1, -1, -1, every, -1));
            KidsWatch.poke();
            refresh();
        });
    }

    /** The smallest day limit that is a real number of minutes; 0 when neither day has one. */
    private static int shortestLimit(Kids.Rules r) {
        int best = 0;
        if (r.weekdayMinutes > 0) best = r.weekdayMinutes;
        if (r.weekendMinutes > 0 && (best == 0 || r.weekendMinutes < best)) best = r.weekendMinutes;
        return best;
    }

    /** A break has to land inside the day's minutes; "no TV" and "unlimited" put no ceiling on it. */
    private static boolean breakFits(int every, Kids.Rules r) {
        if (every <= 0) return true;
        int limit = shortestLimit(r);
        return limit == 0 || every < limit;
    }

    /** The largest offered break that still lands inside a day of {@code limit} minutes. */
    private static int breakInside(int limit) {
        if (limit <= 0) return -1;
        if (limit > 30) return -1;
        if (limit > 10) return 10;
        if (limit > 5) return 5;
        return 0;
    }

    /** How long the day can run at most; 0 when "off by" is not set at all. */
    private static int windowMinutes(Kids.Rules r) {
        if (r.endMinute <= 0) return 0;
        return Math.max(0, r.endMinute - r.startMinute);
    }

    private static boolean limitFits(int minutes, Kids.Rules r) {
        if (minutes <= 0) return true;
        int win = windowMinutes(r);
        return win == 0 || minutes <= win;
    }

    /** The longest offered day length that still fits the window. */
    private static int insideWindow(int minutes, Kids.Rules r) {
        if (minutes <= 0) return minutes;
        int win = windowMinutes(r);
        if (win <= 0) return minutes;
        if (minutes <= win) return minutes;
        if (win > 60) return 60;
        if (win > 30) return 30;
        if (win > 10) return 10;
        return -1;
    }

    private String breakLabel(int every) {
        return every <= 0 ? getString(R.string.st_off) : every + " min";
    }

    /** Android 11 only lets a background app raise a window if it may draw over other apps. */
    private void openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.kids_overlay_on, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            // Some TV builds hide this screen; the system's own app list is the fallback.
            startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        }
    }

    /** Every quiz choice writes the plan at once; the tick after this picks the new rules up. */
    private void planChoice(int id, Runnable change) {
        findViewById(id).setOnClickListener(v -> {
            change.run();
            Kids.savePlan();
            KidsWatch.poke();
            refresh();
        });
    }

    /** The quiz-for-time choices: what a round buys, which questions it asks, and the day's grid. */
    private void bindPlanChips() {
        final KidsPlan plan = Kids.plan();
        planChoice(R.id.quizOn, () -> plan.quizGate = true);
        planChoice(R.id.quizOff, () -> plan.quizGate = false);

        planChoice(R.id.stagePrimary, () -> plan.stage = KidsPlan.STAGE_PRIMARY);
        // The other stages have no questions yet: the chips say so rather than pretend.
        View.OnClickListener later = v -> Toast.makeText(this, R.string.kids_stage_later,
                Toast.LENGTH_SHORT).show();
        findViewById(R.id.stageMiddle).setOnClickListener(later);
        findViewById(R.id.stageHigh).setOnClickListener(later);

        planChoice(R.id.subjectChinese, () -> plan.subjectChinese = !plan.subjectChinese);
        planChoice(R.id.subjectMath, () -> plan.subjectMath = !plan.subjectMath);
        planChoice(R.id.subjectEnglish, () -> plan.subjectEnglish = !plan.subjectEnglish);

        planChoice(R.id.quizMin5, () -> plan.sessionMinutes = 5);
        planChoice(R.id.quizMin10, () -> plan.sessionMinutes = 10);
        planChoice(R.id.quizMin15, () -> plan.sessionMinutes = 15);
        planChoice(R.id.quizMin20, () -> plan.sessionMinutes = 20);

        planChoice(R.id.quizQ3, () -> plan.questionsPerRound = 3);
        planChoice(R.id.quizQ5, () -> plan.questionsPerRound = 5);
        planChoice(R.id.quizQ8, () -> plan.questionsPerRound = 8);

        planChoice(R.id.quizPass50, () -> plan.passRatio = 50);
        planChoice(R.id.quizPass60, () -> plan.passRatio = 60);
        planChoice(R.id.quizPass80, () -> plan.passRatio = 80);

        planChoice(R.id.quizRounds1, () -> plan.roundsPerDay = 1);
        planChoice(R.id.quizRounds2, () -> plan.roundsPerDay = 2);
        planChoice(R.id.quizRounds3, () -> plan.roundsPerDay = 3);
        planChoice(R.id.quizRounds5, () -> plan.roundsPerDay = 5);

        planChoice(R.id.modeRound, () -> plan.playMode = KidsPlan.MODE_ROUND);
        planChoice(R.id.modeStreak, () -> plan.playMode = KidsPlan.MODE_STREAK);
        planChoice(R.id.modePractice, () -> plan.playMode = KidsPlan.MODE_PRACTICE);

        planChoice(R.id.extraOn, () -> plan.extraEnabled = true);
        planChoice(R.id.extraOff, () -> plan.extraEnabled = false);
        planChoice(R.id.starOn, () -> plan.starReward = true);
        planChoice(R.id.starOff, () -> plan.starReward = false);

        planChoice(R.id.next0, () -> plan.autoNextSeconds = 0);
        planChoice(R.id.next3, () -> plan.autoNextSeconds = 3);
        planChoice(R.id.next5, () -> plan.autoNextSeconds = 5);

        planChoice(R.id.holidayOn, () -> plan.holidayWeekend = true);
        planChoice(R.id.holidayOff, () -> plan.holidayWeekend = false);

        findViewById(R.id.scheduleButton).setOnClickListener(v ->
                startActivity(new Intent(this, KidsScheduleActivity.class)));
    }

    private void refresh() {
        Kids.Kind kind = Kids.kind();
        setSelected(R.id.kindKids, kind == Kids.Kind.KIDS);
        setSelected(R.id.kindElders, kind == Kids.Kind.ELDERS);
        // The row offers one choice: other apps are let through. A setting left at "kept away" from
        // an older build is put back in step the moment this page opens.
        if (Kids.blockOthers()) Kids.chooseBlockOthers(false);
        setSelected(R.id.blockOff, true);

        Kids.Rules rules = Kids.rules();
        // A break left over from an older combination is pulled back in the moment the page opens.
        if (!breakFits(rules.breakEvery, rules)) {
            rules = rules.copy(-1, -1, -1, -1, breakInside(shortestLimit(rules)), -1);
            Kids.chooseRules(rules);
        }
        setWeekdaySelected(R.id.weekdayNone, R.id.weekday10, R.id.weekday30, R.id.weekday60, R.id.weekday90,
                R.id.weekday120, rules.weekdayMinutes);
        setMinutesSelected(R.id.weekend0, R.id.weekend10, R.id.weekend30, R.id.weekend60, R.id.weekend90,
                R.id.weekend120, rules.weekendMinutes);
        keepFitting(R.id.weekday10, 10, rules);
        keepFitting(R.id.weekday30, 30, rules);
        keepFitting(R.id.weekday60, 60, rules);
        keepFitting(R.id.weekday90, 90, rules);
        keepFitting(R.id.weekday120, 120, rules);
        keepFitting(R.id.weekend10, 10, rules);
        keepFitting(R.id.weekend30, 30, rules);
        keepFitting(R.id.weekend60, 60, rules);
        keepFitting(R.id.weekend90, 90, rules);
        keepFitting(R.id.weekend120, 120, rules);
        setSelected(R.id.latest0, rules.endMinute == 0);
        setSelected(R.id.latest20, rules.endMinute == 20 * 60);
        setSelected(R.id.latest21, rules.endMinute == 21 * 60);
        setSelected(R.id.latest22, rules.endMinute == 22 * 60);
        // 45 and 60 are gone: a leftover setting from an older build reads as "off" here and is
        // written back as off, so the page never shows a row with nothing selected.
        if (rules.breakEvery == 45 || rules.breakEvery == 60) {
            rules = rules.copy(-1, -1, -1, -1, 0, -1);
            Kids.chooseRules(rules);
        }
        setSelected(R.id.break0, rules.breakEvery == 0);
        setSelected(R.id.break5, rules.breakEvery == 5);
        setSelected(R.id.break10, rules.breakEvery == 10);
        setSelected(R.id.break30, rules.breakEvery == 30);
        keepBreak(R.id.break5, 5, rules);
        keepBreak(R.id.break10, 10, rules);
        keepBreak(R.id.break30, 30, rules);

        TextView overlayButton = findViewById(R.id.overlayButton);
        boolean overlay = Settings.canDrawOverlays(this);
        overlayButton.setText(overlay ? R.string.kids_overlay_on : R.string.kids_overlay_off);
        overlayButton.setSelected(overlay);

        boolean kids = kind == Kids.Kind.KIDS;
        startButton.setText(kids ? R.string.kids_start_kids : R.string.kids_start_elders);
        turnOffButton.setVisibility(Kids.enabled() ? View.VISIBLE : View.GONE);
        pinButton.setText(Kids.pinSet()
                ? getString(R.string.kids_pin_change) + " · " + getString(
                        Kids.pinArrows() ? R.string.pin_kind_arrows_short : R.string.pin_kind_digits_short)
                : getString(R.string.kids_pin_set));
        appsTitle.setText(getString(R.string.kids_apps, Kids.allowed().size()));
        appsHint.setText(getString(kids ? R.string.kids_apps_hint : R.string.kids_elders_apps_hint));

        // The quiz buys time against a limit, so it only means something for children.
        KidsPlan plan = Kids.plan();
        findViewById(R.id.quizSection).setVisibility(kids ? View.VISIBLE : View.GONE);
        findViewById(R.id.quizBox).setVisibility(plan.quizGate ? View.VISIBLE : View.GONE);
        setSelected(R.id.quizOn, plan.quizGate);
        setSelected(R.id.quizOff, !plan.quizGate);
        setSelected(R.id.stagePrimary, KidsPlan.STAGE_PRIMARY.equals(plan.stage));
        setSelected(R.id.stageMiddle, false);
        setSelected(R.id.stageHigh, false);
        setSelected(R.id.subjectChinese, plan.subjectChinese);
        setSelected(R.id.subjectMath, plan.subjectMath);
        setSelected(R.id.subjectEnglish, plan.subjectEnglish);
        setSelected(R.id.quizMin5, plan.sessionMinutes == 5);
        setSelected(R.id.quizMin10, plan.sessionMinutes == 10);
        setSelected(R.id.quizMin15, plan.sessionMinutes == 15);
        setSelected(R.id.quizMin20, plan.sessionMinutes == 20);
        setSelected(R.id.quizQ3, plan.questionsPerRound == 3);
        setSelected(R.id.quizQ5, plan.questionsPerRound == 5);
        setSelected(R.id.quizQ8, plan.questionsPerRound == 8);
        setSelected(R.id.quizPass50, plan.passRatio == 50);
        setSelected(R.id.quizPass60, plan.passRatio == 60);
        setSelected(R.id.quizPass80, plan.passRatio == 80);
        setSelected(R.id.quizRounds1, plan.roundsPerDay == 1);
        setSelected(R.id.quizRounds2, plan.roundsPerDay == 2);
        setSelected(R.id.quizRounds3, plan.roundsPerDay == 3);
        setSelected(R.id.quizRounds5, plan.roundsPerDay == 5);
        setSelected(R.id.modeRound, KidsPlan.MODE_ROUND.equals(plan.playMode));
        setSelected(R.id.modeStreak, KidsPlan.MODE_STREAK.equals(plan.playMode));
        setSelected(R.id.modePractice, KidsPlan.MODE_PRACTICE.equals(plan.playMode));
        setSelected(R.id.extraOn, plan.extraEnabled);
        setSelected(R.id.extraOff, !plan.extraEnabled);
        setSelected(R.id.starOn, plan.starReward);
        setSelected(R.id.starOff, !plan.starReward);
        setSelected(R.id.next0, plan.autoNextSeconds == 0);
        setSelected(R.id.next3, plan.autoNextSeconds == 3);
        setSelected(R.id.next5, plan.autoNextSeconds == 5);
        setSelected(R.id.holidayOn, plan.holidayWeekend);
        setSelected(R.id.holidayOff, !plan.holidayWeekend);

        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void setWeekdaySelected(int noneId, int id10, int id30, int id60, int id90, int id120, int minutes) {
        setSelected(noneId, minutes < 0);
        setSelected(id10, minutes == 10);
        setSelected(id30, minutes == 30);
        setSelected(id60, minutes == 60);
        setSelected(id90, minutes == 90);
        setSelected(id120, minutes == 120);
    }

    private void setMinutesSelected(int id0, int id10, int id30, int id60, int id90, int id120, int minutes) {
        setSelected(id0, minutes == 0);
        setSelected(id10, minutes == 10);
        setSelected(id30, minutes == 30);
        setSelected(id60, minutes == 60);
        setSelected(id90, minutes == 90);
        setSelected(id120, minutes == 120);
    }

    private void setSelected(int id, boolean on) {
        findViewById(id).setSelected(on);
    }

    /** A day length longer than the window, or one that swallows the break, is taken off the row. */
    private void keepFitting(int id, int minutes, Kids.Rules rules) {
        findViewById(id).setVisibility(limitFits(minutes, rules) ? View.VISIBLE : View.GONE);
    }

    private void keepBreak(int id, int every, Kids.Rules rules) {
        findViewById(id).setVisibility(breakFits(every, rules) ? View.VISIBLE : View.GONE);
    }

    /** Every tile the mode can show; picking one puts it at the end of the mode's Home. */
    private final class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.Holder> {

        AppListAdapter() {
            setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        @Override
        public long getItemId(int position) {
            return apps.get(position).pkg.hashCode();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(KidsSetupActivity.this)
                    .inflate(R.layout.item_kids_app, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            App app = apps.get(position);
            h.name.setText(app.label);
            h.icon.setImageDrawable(app.icon);
            int index = Kids.allowed().indexOf(app.pkg);
            h.itemView.setSelected(index >= 0);
            if (index >= 0) {
                h.order.setVisibility(View.VISIBLE);
                h.order.setText(String.valueOf(index + 1));
            } else {
                h.order.setVisibility(View.INVISIBLE);
            }
            h.itemView.setOnClickListener(v -> {
                Kids.toggleAllowed(app.pkg);
                refresh();
            });
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView order;

            Holder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.icon);
                name = itemView.findViewById(R.id.name);
                order = itemView.findViewById(R.id.order);
            }
        }
    }
}
