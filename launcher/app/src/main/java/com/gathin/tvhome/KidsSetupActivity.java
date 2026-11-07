package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
        bindMinutes(R.id.weekday30, 30, true);
        bindMinutes(R.id.weekday60, 60, true);
        bindMinutes(R.id.weekday90, 90, true);
        bindMinutes(R.id.weekday120, 120, true);
        bindMinutes(R.id.weekend0, 0, false);
        bindMinutes(R.id.weekend30, 30, false);
        bindMinutes(R.id.weekend60, 60, false);
        bindMinutes(R.id.weekend90, 90, false);
        bindMinutes(R.id.weekend120, 120, false);
        bindLatest(R.id.latest0, 0);
        bindLatest(R.id.latest20, 20 * 60);
        bindLatest(R.id.latest21, 21 * 60);
        bindLatest(R.id.latest22, 22 * 60);
        bindBreak(R.id.break0, 0);
        bindBreak(R.id.break30, 30);
        bindBreak(R.id.break45, 45);
        bindBreak(R.id.break60, 60);

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
            Kids.turnOff();
            KidsWatch.poke();
            finish();
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
            Kids.chooseRules(weekday ? r.withWeekday(minutes) : r.withWeekend(minutes));
            KidsWatch.poke();
            refresh();
        });
    }

    private void bindLatest(int id, int endMinute) {
        findViewById(id).setOnClickListener(v -> {
            Kids.Rules r = Kids.rules();
            Kids.chooseRules(r.copy(-1, -1, -1, endMinute, -1, -1));
            KidsWatch.poke();
            refresh();
        });
    }

    private void bindBreak(int id, int every) {
        findViewById(id).setOnClickListener(v -> {
            Kids.Rules r = Kids.rules();
            Kids.chooseRules(r.copy(-1, -1, -1, -1, every, -1));
            KidsWatch.poke();
            refresh();
        });
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
        setWeekdaySelected(R.id.weekdayNone, R.id.weekday30, R.id.weekday60, R.id.weekday90, R.id.weekday120,
                rules.weekdayMinutes);
        setMinutesSelected(R.id.weekend0, R.id.weekend30, R.id.weekend60, R.id.weekend90, R.id.weekend120,
                rules.weekendMinutes);
        setSelected(R.id.latest0, rules.endMinute == 0);
        setSelected(R.id.latest20, rules.endMinute == 20 * 60);
        setSelected(R.id.latest21, rules.endMinute == 21 * 60);
        setSelected(R.id.latest22, rules.endMinute == 22 * 60);
        setSelected(R.id.break0, rules.breakEvery == 0);
        setSelected(R.id.break30, rules.breakEvery == 30);
        setSelected(R.id.break45, rules.breakEvery == 45);
        setSelected(R.id.break60, rules.breakEvery == 60);

        boolean kids = kind == Kids.Kind.KIDS;
        startButton.setText(kids ? R.string.kids_start_kids : R.string.kids_start_elders);
        turnOffButton.setVisibility(Kids.enabled() ? View.VISIBLE : View.GONE);
        pinButton.setText(Kids.pinSet()
                ? getString(R.string.kids_pin_change) + " · " + getString(
                        Kids.pinArrows() ? R.string.pin_kind_arrows_short : R.string.pin_kind_digits_short)
                : getString(R.string.kids_pin_set));
        appsTitle.setText(getString(R.string.kids_apps, Kids.allowed().size()));
        appsHint.setText(getString(kids ? R.string.kids_apps_hint : R.string.kids_elders_apps_hint));
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void setWeekdaySelected(int noneId, int id30, int id60, int id90, int id120, int minutes) {
        setSelected(noneId, minutes < 0);
        setSelected(id30, minutes == 30);
        setSelected(id60, minutes == 60);
        setSelected(id90, minutes == 90);
        setSelected(id120, minutes == 120);
    }

    private void setMinutesSelected(int id0, int id30, int id60, int id90, int id120, int minutes) {
        setSelected(id0, minutes == 0);
        setSelected(id30, minutes == 30);
        setSelected(id60, minutes == 60);
        setSelected(id90, minutes == 90);
        setSelected(id120, minutes == 120);
    }

    private void setSelected(int id, boolean on) {
        findViewById(id).setSelected(on);
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
