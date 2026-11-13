package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private TextView columnsChoice;
    private TextView rowsChoice;
    private TextView dockChoice;
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView ambientMotionChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean ambientMotion;
    private RemoteConfigServer remoteServer;
    /** Home's wallpaper behind the page's rows; null when the theme shows none. */
    private WallpaperPlayer backdrop;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        Config config = Config.load(this);
        // The same wallpaper Home shows, behind the rows: this page's glass needs something to be glass over.
        backdrop = GlassBackdrop.attach(this, config);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        ambientMotion = PersonalAppearance.ambientMotion(this);

        columnsChoice = findViewById(R.id.columnsChoice);
        columnsChoice.setOnClickListener(v -> showColumnsPicker());
        rowsChoice = findViewById(R.id.rowsChoice);
        rowsChoice.setOnClickListener(v -> showRowsPicker());
        dockChoice = findViewById(R.id.dockChoice);
        dockChoice.setOnClickListener(v -> showDockPicker());
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        findViewById(R.id.weatherCityChoice).setOnClickListener(v -> showWeatherCityPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            choices.edit().putBoolean("animations", animations).apply();
            changed();
            refresh();
        });
        ambientMotionChoice = findViewById(R.id.ambientMotionChoice);
        ambientMotionChoice.setOnClickListener(v -> {
            ambientMotion = !ambientMotion;
            choices.edit().putBoolean(PersonalAppearance.AMBIENT_MOTION_KEY, ambientMotion).apply();
            changed();
            refresh();
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(PersonalAppearance.MODE_KEY)
                    .remove(PersonalAppearance.BACKGROUND_KEY).remove(PersonalAppearance.AMBIENT_MOTION_KEY)
                    .remove(OpenOnStart.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            ambientMotion = PersonalAppearance.ambientMotion(this);
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        // Kids/Elders mode: who it is for, the apps it shows and the time limits.
        findViewById(R.id.kidsChoice).setOnClickListener(v ->
                open(new Intent(this, KidsSetupActivity.class)));
        // One-tap update: download the configured APK and open the system installer.
        findViewById(R.id.updateChoice).setOnClickListener(v -> {
            String url = choices.getString("update_url", "");
            if (url.isEmpty()) {
                Toast.makeText(this, R.string.update_no_url, Toast.LENGTH_LONG).show();
                return;
            }
            ApkUpdater.start(this, url);
            Toast.makeText(this, R.string.update_started, Toast.LENGTH_SHORT).show();
        });
        TextView appearance = findViewById(R.id.appearanceChoice);
        appearance.setVisibility(PersonalAppearance.enabled(this) ? View.VISIBLE : View.GONE);
        appearance.setOnClickListener(view -> showAppearancePicker());
        TextView backgroundChoice = findViewById(R.id.backgroundChoice);
        backgroundChoice.setVisibility(PersonalAppearance.enabled(this) ? View.VISIBLE : View.GONE);
        backgroundChoice.setOnClickListener(view -> {
            String background = choices.getString(PersonalAppearance.BACKGROUND_KEY, "app");
            int selected = "app".equals(background) ? 0 : "bing".equals(background) ? 2 : 1;
            new AlertDialog.Builder(this).setTitle("背景风格")
                    .setSingleChoiceItems(new String[]{"APP主色渐变 · 随焦点平滑变化",
                                    "壁纸 · 使用已选图片", "壁纸 · 必应滚动壁纸"},
                            selected, (dialog, which) -> {
                                if (which == 0) {
                                    choices.edit().putString(PersonalAppearance.BACKGROUND_KEY, "app")
                                            .remove(Config.WALLPAPER_CHOICE_KEY).apply();
                                    changed();
                                    refresh();
                                } else if (which == 1) {
                                    choices.edit().putString(PersonalAppearance.BACKGROUND_KEY, "wallpaper")
                                            .apply();
                                    changed();
                                    refresh();
                                } else {
                                    choices.edit().putString(PersonalAppearance.BACKGROUND_KEY, "bing")
                                            .remove(Config.WALLPAPER_CHOICE_KEY)
                                            .apply();
                                    changed();
                                    refresh();
                                    // Bring today's picture down right away; the saved choice shows the
                                    // days collected so far even if the fetch fails.
                                    BingDaily.fetchToday(this, true, (ok, message) -> {
                                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                                        if (ok) changed();
                                    });
                                }
                                dialog.dismiss();
                            }).setNegativeButton(android.R.string.cancel, null).show();
        });
        TextView displayInfo = findViewById(R.id.displayInfo);
        displayInfo.setVisibility(PersonalAppearance.enabled(this) ? View.VISIBLE : View.GONE);
        displayInfo.setText("桌面显示：" + Math.round(getWindowManager().getDefaultDisplay().getRefreshRate()) + " Hz · 使用系统可用的最高刷新率");
        refresh();
        PersonalAppearance.settings(this);
        // Hints are hidden until their row is focused, keeping the page compact until a row is selected.
        bindHint(R.id.ambientMotionChoice, R.id.ambientMotionHint);
        bindHint(R.id.defaultHomeChoice, R.id.defaultHomeHint);
        bindHint(R.id.kidsChoice, R.id.kidsHint);
        bindHint(R.id.updateChoice, R.id.updateHint);
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnsChoice.requestFocus();
        setupRemote();
    }

    /** Show the row's hint only while it holds focus; hide it again when focus moves away. */
    private void bindHint(int buttonId, int hintId) {
        View hint = findViewById(hintId);
        findViewById(buttonId).setOnFocusChangeListener((v, hasFocus) ->
                hint.setVisibility(hasFocus ? View.VISIBLE : View.GONE));
    }

    /** Start the LAN server and render the QR code/URL a phone or computer can open on the same Wi-Fi. */
    private void setupRemote() {
        QrCodeView qrView = findViewById(R.id.qrCode);
        TextView qrUrl = findViewById(R.id.qrUrl);
        try {
            remoteServer = new RemoteConfigServer(this);
            remoteServer.start();
            String ip = lanIpAddress();
            if (ip == null) throw new java.io.IOException("no LAN address");
            String url = "http://" + ip + ":" + RemoteConfigServer.PORT;
            qrView.setModules(QrCode.encode(url));
            qrUrl.setText(url);
        } catch (java.io.IOException e) {
            qrView.setVisibility(View.GONE);
            qrUrl.setText(R.string.qr_unavailable);
        }
    }

    /** First non-loopback IPv4 address, which on a TV is the Wi-Fi address other devices on the LAN reach. */
    private static String lanIpAddress() throws java.io.IOException {
        java.util.Enumeration<java.net.NetworkInterface> interfaces =
                java.net.NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            java.net.NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() || !iface.isUp()) continue;
            java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                java.net.InetAddress address = addresses.nextElement();
                if (!address.isLoopbackAddress()
                        && address.getHostAddress().indexOf(':') < 0) {
                    return address.getHostAddress();
                }
            }
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (backdrop != null) backdrop.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (backdrop != null) backdrop.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backdrop != null) {
            backdrop.close();
            backdrop = null;
        }
        if (remoteServer != null) {
            remoteServer.stop();
            remoteServer = null;
        }
    }

    private void showColumnsPicker() {
        new AlertDialog.Builder(this).setTitle(R.string.columns_label)
                .setSingleChoiceItems(new String[]{"4", "5", "6", "7"}, columns - 4, (dialog, which) -> {
                    int value = which + 4;
                    dialog.dismiss();
                    if (columns == value) return;
                    columns = value;
                    choices.edit().putInt("columns", value).apply();
                    changed();
                    refresh();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showRowsPicker() {
        new AlertDialog.Builder(this).setTitle(R.string.rows_below_dock_label)
                .setSingleChoiceItems(new String[]{"0", "1", "2", "3"}, rowsBelowDock, (dialog, which) -> {
                    dialog.dismiss();
                    if (rowsBelowDock == which) return;
                    rowsBelowDock = which;
                    choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, which).apply();
                    changed();
                    refresh();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showDockPicker() {
        String[] labels = new String[DOCK_OPACITIES.length];
        int checked = -1;
        for (int i = 0; i < DOCK_OPACITIES.length; i++) {
            labels[i] = DOCK_OPACITIES[i] + "%";
            if (DOCK_OPACITIES[i] == dockOpacity) checked = i;
        }
        new AlertDialog.Builder(this).setTitle(R.string.dock_opacity_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    int value = DOCK_OPACITIES[which];
                    dialog.dismiss();
                    if (dockOpacity == value) return;
                    dockOpacity = value;
                    choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                    changed();
                    refresh();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showAppearancePicker() {
        String[] modes = {"night", "day", "auto"};
        String[] labels = {"夜间 · 深色玻璃", "日间 · 明亮通透", "自动 · 07:00–19:00 为日间"};
        String current = PersonalAppearance.mode(this);
        int selected = "day".equals(current) ? 1 : "auto".equals(current) ? 2 : 0;
        new AlertDialog.Builder(this).setTitle("外观模式")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    choices.edit().putString(PersonalAppearance.MODE_KEY, modes[which]).apply();
                    changed();
                    dialog.dismiss();
                    refresh();
                    PersonalAppearance.settings(this);
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    /** The city the weather line reports: one list of every city Weather knows by name. */
    private void showWeatherCityPicker() {
        String city = Weather.city(this);
        int checked = 0;
        String[] names = new String[Weather.CITIES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = Weather.CITIES[i][0];
            if (names[i].equals(city)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.weather_city)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    Weather.setCity(this, names[which]);
                    // Fetch the new city now, so the line is right the moment Home comes back.
                    Weather.refresh(this, true, line -> refresh());
                    refresh();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showWallpaperPicker() {
        // "必应滚动壁纸": the row shows the days already kept (newest first) and pulls today's first.
        if ("bing".equals(choices.getString(PersonalAppearance.BACKGROUND_KEY, "app"))) {
            BingDaily.fetchToday(this, true, (ok, message) -> {
                if (ok) changed();
                refresh();
                showBingWallpapers();
            });
            return;
        }
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()
                && (!PersonalAppearance.enabled(this) || images.isEmpty())) images.add(0, single);
        if (PersonalAppearance.enabled(this)) {
            WallpaperPicker.show(this, images, getString(R.string.wallpaper_row_bing), choice -> {
                if ("bing".equals(choice)) {
                    chooseBing();
                    return;
                }
                choices.edit().putString(PersonalAppearance.BACKGROUND_KEY, "wallpaper").apply();
                if (choice == null) choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                else choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, choice).apply();
                changed();
                refresh();
            });
            return;
        }
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Switching to "必应滚动壁纸": pull today's picture, then show the collection that was kept. */
    private void chooseBing() {
        choices.edit().putString(PersonalAppearance.BACKGROUND_KEY, "bing")
                .remove(Config.WALLPAPER_CHOICE_KEY).apply();
        changed();
        refresh();
        BingDaily.fetchToday(this, true, (ok, message) -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            if (ok) changed();
            refresh();
            showBingWallpapers();
        });
    }

    /**
     * The kept Bing days as the picker's own grid — the first thing a parent sees after choosing it,
     * so the 7-day collection is verifiable on the TV. A kept picture is only shown, not picked:
     * the collection rotates the whole set every 30 minutes.
     */
    private void showBingWallpapers() {
        List<File> kept = BingDaily.wallpapers(this);
        if (kept.isEmpty()) {
            Toast.makeText(this, R.string.bing_wallpaper_empty, Toast.LENGTH_LONG).show();
        }
        WallpaperPicker.showBing(this, kept, getString(R.string.wallpaper_row_bing), choice -> {
            if ("none".equals(choice)) {
                choices.edit().putString(PersonalAppearance.BACKGROUND_KEY, "wallpaper")
                        .putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                changed();
                refresh();
                return;
            }
            if (choice == null) {
                // 跟随主题: no explicit wallpaper at all, the theme decides again.
                choices.edit().remove(PersonalAppearance.BACKGROUND_KEY)
                        .remove(Config.WALLPAPER_CHOICE_KEY).apply();
                changed();
                refresh();
                return;
            }
            String day = choice.length() >= 15 ? choice.substring(5, 15) : choice;
            Toast.makeText(this, getString(R.string.bing_wallpaper_kept, day), Toast.LENGTH_SHORT).show();
        });
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void refresh() {
        TextView appearance = findViewById(R.id.appearanceChoice);
        TextView backgroundChoice = findViewById(R.id.backgroundChoice);
        if (backgroundChoice != null) {
            String background = choices.getString(PersonalAppearance.BACKGROUND_KEY, "app");
            String state;
            if ("bing".equals(background)) {
                state = "壁纸 · 必应滚动壁纸（" + BingDaily.wallpapers(this).size() + " 张）";
            } else {
                state = PersonalAppearance.appBackdrop(this) ? "APP主色渐变" : "壁纸";
            }
            backgroundChoice.setText("背景：" + state);
        }
        if (appearance != null) appearance.setText("外观模式：" + AppearancePolicy.label(PersonalAppearance.mode(this)));
        if (columnsChoice != null) columnsChoice.setText(getString(R.string.columns_state, columns));
        if (rowsChoice != null) rowsChoice.setText(getString(R.string.rows_state, rowsBelowDock));
        if (dockChoice != null) dockChoice.setText(getString(R.string.dock_state, dockOpacity));
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        boolean appBackground = PersonalAppearance.appBackdrop(this);
        ambientMotionChoice.setVisibility(appBackground ? View.VISIBLE : View.GONE);
        // The hint's visibility is owned by focus; only force it hidden when the whole row is unavailable.
        if (!appBackground) findViewById(R.id.ambientMotionHint).setVisibility(View.GONE);
        ambientMotionChoice.setText(getString(R.string.ambient_motion_state,
                getString(animations && ambientMotion ? R.string.on : R.string.off)));
        ambientMotionChoice.setEnabled(animations);
        ambientMotionChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "bing".equals(choices.getString(PersonalAppearance.BACKGROUND_KEY, "app"))
                        ? "必应滚动壁纸（" + BingDaily.wallpapers(this).size() + " 张，30 分钟一换）"
                        : "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? PersonalAppearance.wallpaperName(wallpaperCurrent)
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        TextView weatherCity = findViewById(R.id.weatherCityChoice);
        if (weatherCity != null) {
            weatherCity.setText(getString(R.string.weather_city_state, Weather.city(this)));
        }
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(PersonalAppearance.MODE_KEY) || choices.contains(PersonalAppearance.BACKGROUND_KEY)
                || choices.contains(PersonalAppearance.AMBIENT_MOTION_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
