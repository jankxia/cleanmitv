package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The questions that buy more watching time: one at a time, four answers, a star per right one.
 *
 * <p>Three ways to play, chosen in the setup page: a round of n questions where {@code passRatio}
 * of them has to be right; a streak, where n in a row has to be right; or practice, which pays no
 * time and only clears the practice pool.
 *
 * <p>Nothing here unlocks on its own: time is granted only by {@link Kids#grantQuizTime}, which
 * caps what one round can buy at what is left of the day's quota. A broken or missing question
 * bank grants the round and closes, so a bad asset can never leave a child locked out.
 */
public class KidsQuizActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<KidsBank.Q> round = new ArrayList<>();
    private final List<TextView> optionViews = new ArrayList<>();
    private KidsPlan plan;
    private KidsLedger ledger;
    private boolean practice;
    private int index;
    private int correct;
    private int streak;
    private boolean answered;
    private boolean waitingNext;
    private boolean closing;

    private TextView progress;
    private TextView score;
    private TextView question;
    private TextView feedback;
    private LinearLayout options;
    private LinearLayout resultPanel;
    private TextView resultIcon;
    private TextView resultTitle;
    private TextView resultSub;
    private View againButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Kids.init(this);
        KidsWatch.quizShowing = true;
        plan = Kids.plan();
        ledger = Kids.ledger();
        practice = KidsPlan.MODE_PRACTICE.equals(plan.playMode);

        if (KidsBank.all(this).isEmpty()) {
            // No bank, no questions: the round pays out rather than leaving the TV locked forever.
            Toast.makeText(this, R.string.quiz_empty, Toast.LENGTH_LONG).show();
            Kids.grantQuizTime(plan.sessionMinutes);
            close();
            return;
        }

        setContentView(R.layout.activity_kids_quiz);
        progress = findViewById(R.id.progress);
        score = findViewById(R.id.score);
        question = findViewById(R.id.question);
        feedback = findViewById(R.id.feedback);
        options = findViewById(R.id.options);
        resultPanel = findViewById(R.id.resultPanel);
        resultIcon = findViewById(R.id.resultIcon);
        resultTitle = findViewById(R.id.resultTitle);
        resultSub = findViewById(R.id.resultSub);
        againButton = findViewById(R.id.againButton);

        againButton.setOnClickListener(v -> restart());
        findViewById(R.id.closeButton).setOnClickListener(v -> close());

        startRound();
    }

    private void startRound() {
        round.clear();
        index = 0;
        correct = 0;
        streak = 0;
        answered = false;
        waitingNext = false;
        resultPanel.setVisibility(View.GONE);
        int n = Math.max(1, practice ? Math.max(2, plan.extraQuestions) : plan.questionsPerRound);
        round.addAll(KidsBank.pickWithPractice(this, n, practice ? 0 : ledger.extraPool, plan.subjects()));
        if (round.isEmpty()) {
            Kids.grantQuizTime(plan.sessionMinutes);
            close();
            return;
        }
        showQuestion();
    }

    private void showQuestion() {
        if (index >= round.size()) {
            finishRound();
            return;
        }
        KidsBank.Q q = round.get(index);
        progress.setText(getString(R.string.quiz_progress, index + 1, round.size()));
        score.setText(getString(R.string.quiz_score, correct));
        question.setText(q.text);
        feedback.setText(R.string.quiz_pick);
        answered = false;
        waitingNext = false;

        options.removeAllViews();
        optionViews.clear();
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < q.options.length; i++) {
            TextView view = (TextView) inflater.inflate(R.layout.item_quiz_option, options, false);
            view.setText(optionLabel(i) + "   " + q.options[i]);
            final int choice = i;
            view.setOnClickListener(v -> answer(choice));
            options.addView(view);
            optionViews.add(view);
        }
        if (!optionViews.isEmpty()) optionViews.get(0).requestFocus();
    }

    private String optionLabel(int i) {
        return String.valueOf((char) ('A' + i));
    }

    private void answer(int choice) {
        if (answered || index >= round.size()) return;
        answered = true;
        KidsBank.Q q = round.get(index);
        boolean right = choice == q.answer;
        KidsBank.record(this, q.id, right, Kids.day());
        Kids.recordAnswer(right);

        if (right) {
            correct++;
            streak++;
            optionViews.get(choice).setBackgroundResource(R.drawable.quiz_option_right);
            feedback.setText(R.string.quiz_right);
        } else {
            streak = 0;
            optionViews.get(choice).setBackgroundResource(R.drawable.quiz_option_wrong);
            if (q.answer < optionViews.size()) {
                optionViews.get(q.answer).setBackgroundResource(R.drawable.quiz_option_right);
            }
            feedback.setText(getString(R.string.quiz_wrong, q.options[q.answer]));
        }
        score.setText(getString(R.string.quiz_score, correct));

        long delay = plan.autoNextSeconds > 0 ? plan.autoNextSeconds * 1_000L : 0;
        if (delay > 0) {
            handler.postDelayed(nextQuestion, delay);
        } else {
            waitingNext = true;
            feedback.setText(getString(R.string.quiz_next));
        }
    }

    private final Runnable nextQuestion = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || closing) return;
            index++;
            showQuestion();
        }
    };

    private void finishRound() {
        answered = true;
        int n = round.size();
        boolean pass;
        int rewarded = 0;
        if (practice) {
            pass = true;
        } else if (KidsPlan.MODE_STREAK.equals(plan.playMode)) {
            pass = streak >= n;
        } else {
            pass = correct >= plan.passCount(n);
        }

        if (pass) {
            if (practice) {
                ledger.extraPool = 0;
                ledger.save(this);
            } else {
                rewarded = Kids.grantQuizTime(plan.sessionMinutes);
            }
        }
        showResult(pass, rewarded, n);
    }

    private void showResult(boolean pass, int rewarded, int n) {
        resultPanel.setVisibility(View.VISIBLE);
        if (practice) {
            resultIcon.setText("\uD83C\uDF1F");
            resultTitle.setText(R.string.quiz_practice_title);
            resultSub.setText(getString(R.string.quiz_practice_sub, correct, n));
            againButton.setVisibility(View.GONE);
        } else if (pass) {
            resultIcon.setText("\uD83C\uDF89");
            resultTitle.setText(R.string.quiz_pass_title);
            if (rewarded > 0) {
                resultSub.setText(getString(R.string.quiz_pass_sub, correct, n, rewarded));
            } else {
                // Right, but the day's quota is already full: the round buys nothing more.
                resultSub.setText(getString(R.string.quiz_pass_no_time, correct, n));
            }
            againButton.setVisibility(View.VISIBLE);
        } else {
            resultIcon.setText("\uD83D\uDE0A");
            resultTitle.setText(R.string.quiz_fail_title);
            int need = KidsPlan.MODE_STREAK.equals(plan.playMode) ? n : plan.passCount(n);
            resultSub.setText(getString(R.string.quiz_fail_sub, correct, need));
            againButton.setVisibility(
                    ledger.roundsLeft(plan.roundsPerDay) ? View.VISIBLE : View.GONE);
        }
        findViewById(R.id.closeButton).requestFocus();
        if (againButton.getVisibility() == View.VISIBLE) againButton.requestFocus();
    }

    private void restart() {
        if (closing) return;
        if (!ledger.roundsLeft(plan.roundsPerDay)) return;
        startRound();
    }

    private void close() {
        closing = true;
        KidsWatch.poke();
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        if (waitingNext && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            waitingNext = false;
            handler.removeCallbacks(nextQuestion);
            index++;
            showQuestion();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Nothing behind this screen is reachable while the TV is locked.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(nextQuestion);
        KidsWatch.quizShowing = false;
        KidsWatch.poke();
    }
}
