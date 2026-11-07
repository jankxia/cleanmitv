package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
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
    private TextView quizButton;
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
        quizButton = findViewById(R.id.quizButton);
        grantRow = findViewById(R.id.grantRow);
        leaveButton = findViewById(R.id.leaveButton);

        parentButton.setOnClickListener(v -> askParent());
        quizButton.setOnClickListener(v -> startQuiz());
        // Each choice asks for the PIN again: the panel being open is not the permission.
        findViewById(R.id.grant15).setOnClickListener(v -> grantBehindPin(15));
        findViewById(R.id.grant30).setOnClickListener(v -> grantBehindPin(30));
        findViewById(R.id.grant60).setOnClickListener(v -> grantBehindPin(60));
        findViewById(R.id.grantDay).setOnClickListener(v -> grantBehindPin(null));
        leaveButton.setOnClickListener(v -> leave());

        ((TextView) findViewById(R.id.grant15)).setText(getString(R.string.kids_grant, 15));
        ((TextView) findViewById(R.id.grant30)).setText(getString(R.string.kids_grant, 30));
        ((TextView) findViewById(R.id.grant60)).setText(getString(R.string.kids_grant, 60));
        ((TextView) findViewById(R.id.grantDay)).setText(R.string.kids_grant_day);

        render();
        handler.post(secondTick);
        handler.post(lockCheck);
    }

    /**
     * The lock is re-judged here rather than trusted from the last guard tick: a stale field once
     * let the screen close while the day was still spent, which is the one thing it must not do.
     */
    private final Runnable lockCheck = new Runnable() {
        @Override
        public void run() {
            Kids.Lock fresh = Kids.lockNow(System.currentTimeMillis());
            if (fresh == null) {
                Kids.lock = null;
                if (!isFinishing() && !granting) {
                    finish();
                    return;
                }
            } else if (fresh != Kids.lock) {
                Kids.lock = fresh;
                render();
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
        } else if (lock == Kids.Lock.QUIZ) {
            // The day is used up, but questions still buy time: this is the way on, not a full stop.
            icon.setText("\uD83D\uDCDA");
            title.setText(R.string.kids_lock_quiz);
            subtitle.setText(getString(R.string.kids_lock_quiz_sub,
                    Kids.plan().sessionMinutes, Kids.plan().questionsPerRound));
        } else if (lock == Kids.Lock.NO_WATCH) {
            // The day's minutes are set to "not at all": no hour opens it, not even a break's end.
            icon.setText("\uD83C\uDF19");
            title.setText(R.string.kids_lock_none);
            subtitle.setText(R.string.kids_lock_none_sub);
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
        // "The rest of today" would only ever hand the day back to a child, so it is not offered
        // once the day is spent — the parent still has 15 / 30 / 60.
        findViewById(R.id.grantDay).setVisibility(
                lock == Kids.Lock.DAY_USED ? View.GONE : View.VISIBLE);
        parentButton.setVisibility(granting ? View.GONE : View.VISIBLE);
        boolean quiz = lock == Kids.Lock.QUIZ;
        quizButton.setVisibility(quiz && !granting ? View.VISIBLE : View.GONE);
        if (quiz && !granting) quizButton.requestFocus();
    }

    /** Off to the questions: the lock screen steps aside and the quiz takes over. */
    private void startQuiz() {
        if (Kids.lock != Kids.Lock.QUIZ) return;
        // Hold the guard off across the hand-over: the lock is still set while the quiz opens.
        KidsWatch.quizShowing = true;
        try {
            startActivity(new Intent(this, KidsQuizActivity.class));
            finish();
        } catch (Exception e) {
            KidsWatch.quizShowing = false;
        }
    }

    private void askParent() {
        // Without a PIN nothing could ever match, so the lock would report a wrong PIN forever.
        if (!Kids.pinSet()) {
            Toast.makeText(this, R.string.kids_pin_first, Toast.LENGTH_LONG).show();
            return;
        }
        pinOpen = true;
        PinDialog verify = new PinDialog(this, PinDialog.VERIFY, Kids.pinArrows(),
                new PinDialog.PinListener() {
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

    /** A break / extra time takes the PIN too: the buttons are behind it, one by one. */
    private void grantBehindPin(Integer minutes) {
        if (!granting || pinOpen) return;
        if (!Kids.pinSet()) {
            Toast.makeText(this, R.string.kids_pin_first, Toast.LENGTH_LONG).show();
            return;
        }
        pinOpen = true;
        PinDialog verify = new PinDialog(this, PinDialog.VERIFY, Kids.pinArrows(),
                new PinDialog.PinListener() {
            @Override public void onPin(String pin) {
                pinOpen = false;
                if (Kids.pinMatches(pin)) grant(minutes);
                else Toast.makeText(KidsLockActivity.this, R.string.pin_wrong, Toast.LENGTH_LONG).show();
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
        // Behind the PIN only: granting is set when a parent's PIN has just been verified.
        if (!granting) return;
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
