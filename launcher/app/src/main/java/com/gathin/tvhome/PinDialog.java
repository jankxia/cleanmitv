package com.gathin.tvhome;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The parental PIN pad, ported from aerial-tv's PinPad: four digits, from the remote's number keys
 * or the keypad on screen (most TV remotes here have no number keys), or four arrow keys.
 * Create mode asks twice and hands the confirmed PIN to onDone; Verify mode hands the entered PIN.
 */
public final class PinDialog extends Dialog {

    public static final int CREATE = 0;
    public static final int VERIFY = 1;

    private final int mode;
    private final boolean arrows;
    private final PinListener listener;

    private String entered = "";
    private String first;
    private TextView title;
    private TextView hint;
    private LinearLayout dots;
    private LinearLayout keypad;
    private TextView padFocus;
    private int errorRes;

    public interface PinListener {
        /** The PIN was entered (verified or created), exactly four digits. */
        void onPin(String pin);

        void onCancel();
    }

    public PinDialog(Context context, int mode, PinListener listener) {
        this(context, mode, false, listener);
    }

    /** arrows: the PIN is four D-pad presses (↑ ↓ ← →) rather than four digits. */
    public PinDialog(Context context, int mode, boolean arrows, PinListener listener) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.mode = mode;
        this.arrows = arrows;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        setContentView(build());
        setOnShowListener(dialog -> keypad.post(() -> {
            if (padFocus != null) padFocus.requestFocus();
            else if (keypad.getChildCount() > 0 && ((LinearLayout) keypad.getChildAt(0)).getChildCount() > 0) {
                ((LinearLayout) keypad.getChildAt(0)).getChildAt(0).requestFocus();
            }
        }));
    }

    private View build() {
        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#B3000000"));
        root.setPadding(0, 0, 0, 0);

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding((int) (36 * density), (int) (28 * density), (int) (36 * density), (int) (28 * density));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C232D);
        bg.setCornerRadius(28 * density);
        panel.setBackground(bg);

        title = new TextView(getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        panel.addView(title, lp(density, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        hint = new TextView(getContext());
        hint.setTextColor(Color.parseColor("#FFACB8C1"));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        hint.setMaxWidth((int) (340 * density));
        panel.addView(hint, lp(density, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dots = new LinearLayout(getContext());
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsLp.topMargin = (int) (18 * density);
        panel.addView(dots, dotsLp);

        keypad = new LinearLayout(getContext());
        keypad.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        padLp.topMargin = (int) (22 * density);
        panel.addView(keypad, padLp);

        root.addView(panel);
        return root;
    }

    private LinearLayout.LayoutParams lp(float density, int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private void redraw() {
        float density = getContext().getResources().getDisplayMetrics().density;
        int width = (int) (64 * density);
        int height = (int) (48 * density);
        if (dots.getChildCount() != 4) {
            dots.removeAllViews();
            for (int i = 0; i < 4; i++) {
                TextView dot = new TextView(getContext());
                int size = (int) (30 * density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                if (i > 0) lp.leftMargin = (int) (12 * density);
                dot.setLayoutParams(lp);
                dot.setGravity(Gravity.CENTER);
                dot.setTextSize(20);
                dot.setTextColor(Color.WHITE);
                dots.addView(dot);
            }
        }
        for (int i = 0; i < 4; i++) {
            TextView dot = (TextView) dots.getChildAt(i);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(!arrows && i < entered.length() ? 0xFFFFFFFF : 0x00000000);
            g.setStroke((int) (1.5f * density), Color.WHITE);
            dot.setBackground(g);
            // In arrows mode the dot spells the press it holds, so the parent can read the four back.
            dot.setText(arrows && i < entered.length()
                    ? String.valueOf(arrowGlyph(entered.charAt(i))) : "");
        }

        if (arrows) {
            keypad.removeAllViews();
            TextView pad = new TextView(getContext());
            pad.setText("\u2191   \u2193   \u2190   \u2192");
            pad.setTextColor(Color.parseColor("#FFACB8C1"));
            pad.setTextSize(30);
            pad.setGravity(Gravity.CENTER);
            pad.setPadding((int) (28 * density), (int) (10 * density), (int) (28 * density), (int) (10 * density));
            pad.setFocusable(true);
            pad.setFocusableInTouchMode(true);
            keypad.addView(pad, lp(density, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            padFocus = pad;
            return;
        }

        if (keypad.getChildCount() == 0) {
            String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {" ", "0", "<"}};
            for (String[] row : rows) {
                LinearLayout line = new LinearLayout(getContext());
                line.setOrientation(LinearLayout.HORIZONTAL);
                for (String key : row) {
                    if (" ".equals(key)) {
                        View gap = new View(getContext());
                        line.addView(gap, new LinearLayout.LayoutParams(width, height));
                        continue;
                    }
                    TextView k = new TextView(getContext());
                    k.setText("<".equals(key) ? "\u2039" : key);
                    k.setTextColor(Color.WHITE);
                    k.setTextSize(20);
                    k.setGravity(Gravity.CENTER);
                    k.setFocusable(true);
                    k.setClickable(true);
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(0xFF2E3642);
                    g.setCornerRadius(12 * getContext().getResources().getDisplayMetrics().density);
                    k.setBackground(g);
                    k.setOnFocusChangeListener((v, has) -> {
                        GradientDrawable d = new GradientDrawable();
                        d.setColor(has ? 0xFF5574A9 : 0xFF2E3642);
                        d.setCornerRadius(12 * getContext().getResources().getDisplayMetrics().density);
                        if (has) d.setStroke(2, Color.parseColor("#FFB9E7C7"));
                        v.setBackground(d);
                    });
                    final String digit = key;
                    k.setOnClickListener(v -> type("<".equals(digit) ? null : digit.charAt(0)));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
                    if (line.getChildCount() > 0) lp.leftMargin = (int) (12 * getContext().getResources().getDisplayMetrics().density);
                    line.addView(k, lp);
                }
                keypad.addView(line);
            }
        }
        padFocus = null;
    }

    @Override
    public void show() {
        entered = "";
        first = null;
        errorRes = 0;
        super.show();
        refresh();
    }

    /**
     * Keys are taken here rather than from a decor-view listener: a focused key on the on-screen pad
     * consumed D-pad presses first, so the PIN never filled in. Arrows mode only listens to the four
     * D-pad directions, digits mode only to the number keys; Back still closes the dialog.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (arrows) {
                char symbol = arrowOf(keyCode);
                if (symbol != 0) {
                    type(symbol);
                    return true;
                }
            } else if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                type((char) ('0' + keyCode - KeyEvent.KEYCODE_0));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                erase();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static char arrowGlyph(char symbol) {
        switch (symbol) {
            case 'U': return '\u2191';
            case 'D': return '\u2193';
            case 'L': return '\u2190';
            case 'R': return '\u2192';
            default: return symbol;
        }
    }

    private char arrowOf(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return 'U';
            case KeyEvent.KEYCODE_DPAD_DOWN: return 'D';
            case KeyEvent.KEYCODE_DPAD_LEFT: return 'L';
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 'R';
            default: return 0;
        }
    }

    private void type(Character digit) {
        if (digit != null) {
            if (entered.length() >= 4) return;
            entered += digit;
        } else {
            erase();
        }
        refresh();
        if (entered.length() == 4) complete();
    }

    private void erase() {
        if (!entered.isEmpty()) entered = entered.substring(0, entered.length() - 1);
        refresh();
    }

    private void refresh() {
        if (title == null) return;
        int titleRes;
        int hintRes;
        if (mode == CREATE) {
            titleRes = first == null ? R.string.pin_create : R.string.pin_confirm;
            hintRes = errorRes != 0 ? errorRes
                    : (arrows ? R.string.pin_arrows_create_hint : R.string.pin_create_hint);
        } else {
            titleRes = R.string.pin_enter;
            hintRes = errorRes != 0 ? errorRes
                    : (arrows ? R.string.pin_arrows_hint : R.string.pin_enter_hint);
        }
        title.setText(titleRes);
        hint.setText(hintRes);
        hint.setTextColor(errorRes != 0 ? Color.parseColor("#FFFF5B5B")
                : Color.parseColor("#FFACB8C1"));
        redraw();
    }

    private void complete() {
        if (mode == CREATE) {
            if (first == null) {
                first = entered;
                entered = "";
                errorRes = 0;
                refresh();
                return;
            }
            if (first.equals(entered)) {
                listener.onPin(entered);
                dismiss();
            } else {
                first = null;
                entered = "";
                errorRes = R.string.pin_mismatch;
                refresh();
            }
            return;
        }
        listener.onPin(entered);
        dismiss();
    }

    @Override
    public void onBackPressed() {
        listener.onCancel();
        dismiss();
    }
}
