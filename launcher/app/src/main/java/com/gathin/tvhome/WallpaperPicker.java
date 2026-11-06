package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Local wallpaper thumbnails with ordinary D-pad focus and one explicit choice. */
final class WallpaperPicker {
    private WallpaperPicker() { }

    /** 跟随主题 / 无壁纸 + the local thumbnails. */
    static void show(Activity activity, List<File> images, Consumer<String> picked) {
        show(activity, images, null, false, picked);
    }

    /** The same grid with a third special cell, "必应滚动壁纸", which comes back as "bing". */
    static void show(Activity activity, List<File> images, String bingRow, Consumer<String> picked) {
        show(activity, images, bingRow, false, picked);
    }

    /** The kept Bing days themselves, newest first — so picking the collection shows what it kept. */
    static void showBing(Activity activity, List<File> images, String bingRow, Consumer<String> picked) {
        show(activity, images, bingRow, true, picked);
    }

    private static void show(Activity activity, List<File> images, String bingRow, boolean bingView,
                             Consumer<String> picked) {
        float density = activity.getResources().getDisplayMetrics().density;
        boolean day = PersonalAppearance.day(activity);
        boolean hasBing = bingRow != null;
        int specials = hasBing ? 3 : 2;
        ScrollView scroll = new ScrollView(activity);
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(3);
        int pad = Math.round(12 * density);
        grid.setPadding(pad, pad, pad, pad);
        scroll.addView(grid);
        TextView title = new TextView(activity);
        title.setText(bingView
                ? "壁纸 · 必应滚动壁纸（保留 " + images.size() + " 张，最新在前）"
                : "壁纸 · " + images.size() + " 张本地精选");
        title.setTextSize(22);
        title.setTextColor(day ? 0xFF24384F : 0xFFF3F7FF);
        title.setPadding(pad * 2, Math.round(18 * density), pad * 2, pad);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null).create();
        List<Bitmap> thumbnails = new ArrayList<>();
        int width = Math.round(Math.min(720 * density,
                activity.getResources().getDisplayMetrics().widthPixels * 0.86f));
        int cellWidth = (width - 6 * pad) / 3;
        int total = images.size() + specials;
        for (int index = 0; index < total; index++) {
            final String choice = index == 0 ? null : index == 1 ? "none"
                    : hasBing && index == 2 ? "bing" : images.get(index - specials).getName();
            LinearLayout cell = new LinearLayout(activity);
            cell.setId(View.generateViewId());
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
            cell.setFocusable(true);
            cell.setClickable(true);
            StateListDrawable states = new StateListDrawable();
            GradientDrawable focused = new GradientDrawable();
            focused.setColor(day ? 0xFFFFFFFF : 0xFF334765);
            focused.setCornerRadius(12 * density);
            focused.setStroke(Math.max(2, Math.round(density)), day ? 0xFF4776B8 : Color.WHITE);
            GradientDrawable normal = new GradientDrawable();
            normal.setColor(day ? 0x70FFFFFF : 0x18FFFFFF);
            normal.setCornerRadius(12 * density);
            states.addState(new int[]{android.R.attr.state_focused}, focused);
            states.addState(new int[]{}, normal);
            cell.setBackground(states);
            GridLayout.LayoutParams layout = new GridLayout.LayoutParams();
            layout.width = cellWidth;
            layout.height = Math.round(118 * density);
            layout.setMargins(pad / 2, pad / 2, pad / 2, pad / 2);
            grid.addView(cell, layout);
            if (index >= specials) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 8;
                Bitmap bitmap = BitmapFactory.decodeFile(images.get(index - specials).getAbsolutePath(), options);
                if (bitmap != null) thumbnails.add(bitmap);
                ImageView image = new ImageView(activity);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(bitmap);
                cell.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.round(78 * density)));
            }
            TextView label = new TextView(activity);
            label.setText(index == 0 ? "跟随主题" : index == 1 ? "无壁纸"
                    : hasBing && index == 2 ? bingRow
                    : bingView ? dayLabel(choice) : PersonalAppearance.wallpaperName(choice));
            label.setTextSize(index < specials ? 18 : 14);
            label.setTextColor(day ? 0xFF24384F : 0xFFF3F7FF);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            cell.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setOnClickListener(view -> { picked.accept(choice); dialog.dismiss(); });
        }
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(day ? 0xFF3D6596 : 0xFFA8C7EF);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(width, Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.82f));
            GradientDrawable background = new GradientDrawable();
            background.setColor(day ? 0xFFF0F4FA : 0xFF101D30);
            background.setCornerRadius(20 * density);
            dialog.getWindow().setBackgroundDrawable(background);
        }
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    /** "bing-2026-11-06.jpg" reads as "2026-11-06": the day, not the file name. */
    private static String dayLabel(String name) {
        return name.startsWith("bing-") && name.length() >= 15 ? name.substring(5, 15) : name;
    }
}
