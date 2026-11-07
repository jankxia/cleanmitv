package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The watching hours, half an hour at a time: seven columns (Monday to Sunday) of forty-eight
 * cells. Green may be watched, grey may not; a cell toggles with the remote's OK.
 *
 * <p>The grid is written straight into {@link KidsPlan#weekSlots} and holds from the next tick. It
 * adds to the "latest" hour in {@link Kids.Rules} rather than replacing it: a closed cell locks the
 * TV even inside the window.
 */
public class KidsScheduleActivity extends Activity {

    private static final int ROWS = 48;
    private static final int COLS = 7;

    private final TextView[][] cells = new TextView[COLS][ROWS];
    private LinearLayout grid;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Kids.init(this);
        setContentView(R.layout.activity_kids_schedule);
        grid = findViewById(R.id.grid);

        findViewById(R.id.allOn).setOnClickListener(v -> setAll(true));
        findViewById(R.id.allOff).setOnClickListener(v -> setAll(false));
        findViewById(R.id.doneButton).setOnClickListener(v -> finish());

        build();
    }

    private void build() {
        KidsPlan plan = Kids.plan();
        String slots = KidsPlan.gridOk(plan.weekSlots) ? plan.weekSlots : KidsPlan.allOpen();
        grid.removeAllViews();
        int cellHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 26,
                getResources().getDisplayMetrics());
        int gap = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4,
                getResources().getDisplayMetrics());

        for (int row = 0; row < ROWS; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, cellHeight + gap));

            TextView label = new TextView(this);
            label.setText(String.format("%02d:%02d", row / 2, (row % 2) * 30));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            label.setTextColor(0xFFACB8C1);
            label.setLayoutParams(new LinearLayout.LayoutParams(dp(76), cellHeight));
            line.addView(label);

            for (int col = 0; col < COLS; col++) {
                TextView cell = new TextView(this);
                cell.setFocusable(true);
                cell.setFocusableInTouchMode(true);
                cell.setClickable(true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cellHeight, 1f);
                lp.setMarginEnd(gap);
                cell.setLayoutParams(lp);
                final int r = row;
                final int c = col;
                cell.setOnClickListener(v -> toggle(c, r));
                line.addView(cell);
                cells[col][row] = cell;
                paint(cell, slots.charAt(col * ROWS + row) == '1');
            }
            grid.addView(line);
        }
        cells[0][0].requestFocus();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private void paint(View cell, boolean open) {
        cell.setBackgroundResource(open ? R.drawable.slot_on : R.drawable.slot_off);
    }

    private String current() {
        String slots = Kids.plan().weekSlots;
        return KidsPlan.gridOk(slots) ? slots : KidsPlan.allOpen();
    }

    private void save(String slots) {
        KidsPlan plan = Kids.plan();
        plan.weekSlots = slots;
        plan.slotControl = true;
        Kids.savePlan();
    }

    private void toggle(int col, int row) {
        StringBuilder sb = new StringBuilder(current());
        int at = col * ROWS + row;
        boolean open = sb.charAt(at) == '1';
        sb.setCharAt(at, open ? '0' : '1');
        save(sb.toString());
        paint(cells[col][row], !open);
    }

    private void setAll(boolean open) {
        StringBuilder sb = new StringBuilder(COLS * ROWS);
        for (int i = 0; i < COLS * ROWS; i++) sb.append(open ? '1' : '0');
        save(sb.toString());
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) paint(cells[col][row], open);
        }
    }
}
