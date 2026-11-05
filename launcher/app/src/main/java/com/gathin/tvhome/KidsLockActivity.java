package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Over Home while Kids mode is locked: the day's time used, a break, or the hours the TV may be
 * watched. Nothing behind it can be reached; a parent's PIN gives more time or leaves the mode.
 * Ported from aerial-tv's KidsLockScreen.
 */
public class KidsLockActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView icon;
    private TextView title;
    private TextView subtitle;
    private TextView parentButton;
    private View grantRow;
    private TextView leaveButton;
    private boolean granting;
    private boolean pinOpen;

    private final Runnable secondTick = new Runnable() {
        @Override
        public void run() {
            if (Kids.lock == Kids.Lock.BREAK) render();
            handler.postDelayed(this, 1_000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Kids.init(this);
        KidsWatch.lockScreenShowing = true;
        setContentView(R.layout.activity_kids_lock);
        icon = findViewById(R.id.icon);
        title = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        parentButton = findViewById(R.id.parentButton);
        grantRow = findViewById(R.id.grantRow);
        leaveButton = findViewById(R.id.leaveButton);

        parentButton.setOnClickListener(v -> askParent());
        findViewById(R.id.grant15).setOnClickListener(v -> grant(15));
        findViewById(R.id.grant30).setOnClickListener(v -> grant(30));
        findViewById(R.id.grant60).setOnClickListener(v -> grant(60));
        findViewById(R.id.grantDay).setOnClickListener(v -> grant(null));
        leaveButton.setOnClickListener(v -> leave());

        ((TextView) findViewById(R.id.grant15)).setText(getString(R.string.kids_grant, 15));
        ((TextView) findViewById(R.id.grant30)).setText(getString(R.string.kids_grant, 30));
        ((TextView) findViewById(R.id.grant60)).setText(getString(R.string.kids_grant, 60));
        ((TextView) findViewById(R.id.grantDay)).setText(R.string.kids_grant_day);

        render();
        handler.post(secondTick);
        handler.post(lockCheck);
    }

    /** The lock can clear on its own (a day rolls over, an allowance was set elsewhere): close. */
    private final Runnable lockCheck = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && Kids.lock == null && !granting) {
                finish();
                return;
            }
            handler.postDelayed(this, 2_000);
        }
    };

    private void render() {
        Kids.Lock lock = Kids.lock;
        if (lock == null) return;
        boolean kids = Kids.kind() == Kids.Kind.KIDS;
        if (lock == Kids.Lock.BREAK) {
            icon.setText("\uD83C\uDF1E");
            title.setText(R.string.kids_lock_break);
            long left = Math.max(0, (Kids.usage().breakUntil - System.currentTimeMillis()) / 1000);
            subtitle.setText(getString(R.string.kids_lock_break_sub,
                    String.format("%d:%02d", left / 60, left % 60)));
        } else if (lock == Kids.Lock.DAY_USED) {
            icon.setText("\uD83C\uDF19");
            title.setText(R.string.kids_lock_day);
            subtitle.setText(getString(R.string.kids_lock_day_sub, durationText(Kids.usage().usedMs)));
        } else {
            icon.setText("\uD83C\uDF19");
            title.setText(R.string.kids_lock_late);
            Kids.Rules rules = Kids.rules();
            subtitle.setText(getString(R.string.kids_lock_late_sub,
                    Kids.KidsTime.clock(rules.startMinute), Kids.KidsTime.clock(rules.endMinute)));
        }
        leaveButton.setVisibility(granting ? View.VISIBLE : View.GONE);
        leaveButton.setText(kids ? R.string.cc_kids_leave : R.string.cc_elders_leave);
        grantRow.setVisibility(granting ? View.VISIBLE : View.GONE);
        parentButton.setVisibility(granting ? View.GONE : View.VISIBLE);
    }

    private void askParent() {
        pinOpen = true;
        PinDialog verify = new PinDialog(this, PinDialog.VERIFY, new PinDialog.PinListener() {
            @Override public void onPin(String pin) {
                pinOpen = false;
                if (Kids.pinMatches(pin)) {
                    granting = true;
                    render();
                } else {
                    Toast.makeText(KidsLockActivity.this, R.string.pin_wrong, Toast.LENGTH_LONG).show();
                }
            }

            @Override public void onCancel() {
                pinOpen = false;
            }
        });
        verify.show();
    }

    private void grant(Integer minutes) {
        Kids.allow(minutes);
        KidsWatch.poke();
        finish();
    }

    private void leave() {
        Kids.suspend();
        KidsWatch.poke();
        finish();
    }

    private String durationText(long ms) {
        int minutes = (int) Math.max(0, (ms + 30_000L) / 60_000L);
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) return getString(R.string.kids_m, m);
        if (m == 0) return getString(R.string.kids_h, h);
        return getString(R.string.kids_hm, h, m);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Nothing behind this screen is reachable; Back does not leave it.
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(secondTick);
        handler.removeCallbacks(lockCheck);
        KidsWatch.lockScreenShowing = false;
    }
}
