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
 * The questions: one at a time, four answers, a star per right one.
 *
 * <p>Two ways in. The lockscreen opens it when the day is spent, and then it buys minutes: a round
 * of n questions where {@code passRatio} of them has to be right, a streak where n in a row has to
 * be right, or practice, which pays no time and only clears the practice pool. The setup page and
 * the dock's own tile open it as free practice ({@link #EXTRA_PRACTICE}): stars, no minutes, no
 * rounds-per-day limit, and Back is always a way out to the mode it came from.
 *
 * <p>Nothing here unlocks on its own: time is granted only by {@link Kids#grantQuizTime}, which
 * caps what one round can buy at what is left of the day's quota. A broken or missing question
 * bank grants the round and closes, so a bad asset can never leave a child locked out.
 */
public class KidsQuizActivity extends Activity {

    /** Opens the questions for practice: they ask no limit for a minute and never buy any. */
    static final String EXTRA_PRACTICE = "kids_quiz_practice";

    /** A right answer is worth this many stars; a round with every answer right gets one more. */
    private static final int STAR_PER_RIGHT = 1;
    private static final int STAR_PERFECT_BONUS = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<KidsBank.Q> round = new ArrayList<>();
    private final List<TextView> optionViews = new ArrayList<>();
    private KidsPlan plan;
    private KidsLedger ledger;
    private boolean practice;
    /** Came from "Answer now": practice that also escapes the rounds-per-day limit. */
    private boolean freePractice;
    private int index;
    private int correct;
    private int streak;
    /** Stars this round has earned so far, shown when the round ends. */
    private int earned;
    private boolean answered;
    private boolean waitingNext;
    private boolean closing;

    /** What was picked for each question in the round: -1 until it is answered. */
    private final List<Integer> picked = new ArrayList<>();
    private TextView prevButton;
    private TextView continueButton;
    private TextView progress;
    private TextView score;
    private TextView starsView;
    private TextView question;
    private TextView feedback;
    private TextView starPop;
    private LinearLayout options;
    private LinearLayout resultPanel;
    private TextView resultIcon;
    private TextView resultTitle;
    private TextView resultSub;
    private View againButton;
    private TextView closeButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Kids.init(this);
        KidsWatch.quizShowing = true;
        plan = Kids.plan();
        ledger = Kids.ledger();
        freePractice = getIntent().getBooleanExtra(EXTRA_PRACTICE, false);
        practice = freePractice || KidsPlan.MODE_PRACTICE.equals(plan.playMode);

        if (!KidsBank.available(this)) {
            // No bank, no questions: a locked round pays out rather than leaving the TV locked forever.
            Toast.makeText(this, R.string.quiz_empty, Toast.LENGTH_LONG).show();
            if (!freePractice) Kids.grantQuizTime(plan.sessionMinutes);
            close();
            return;
        }

        setContentView(R.layout.activity_kids_quiz);
        progress = findViewById(R.id.progress);
        score = findViewById(R.id.score);
        starsView = findViewById(R.id.stars);
        question = findViewById(R.id.question);
        feedback = findViewById(R.id.feedback);
        options = findViewById(R.id.options);
        resultPanel = findViewById(R.id.resultPanel);
        resultIcon = findViewById(R.id.resultIcon);
        resultTitle = findViewById(R.id.resultTitle);
        resultSub = findViewById(R.id.resultSub);
        againButton = findViewById(R.id.againButton);
        closeButton = findViewById(R.id.closeButton);
        prevButton = findViewById(R.id.prevButton);
        continueButton = findViewById(R.id.continueButton);
        starPop = findViewById(R.id.starPop);
        prevButton.setOnClickListener(v -> previous());
        continueButton.setOnClickListener(v -> next());

        againButton.setOnClickListener(v -> restart());
        closeButton.setOnClickListener(v -> close());

        // Practice is only ever left for the mode it came from, and the corner says so.
        View back = findViewById(R.id.backButton);
        if (freePractice) {
            back.setVisibility(View.VISIBLE);
            back.setOnClickListener(v -> close());
            closeButton.setText(R.string.quiz_back);
        }

        startRound();
    }

    /** Stars show in free practice whatever the setup says: they are the whole reward there. */
    private boolean starsOn() {
        return plan.starReward || freePractice;
    }

    private void startRound() {
        round.clear();
        picked.clear();
        index = 0;
        correct = 0;
        streak = 0;
        earned = 0;
        answered = false;
        waitingNext = false;
        resultPanel.setVisibility(View.GONE);
        int n = Math.max(1, plan.questionsPerRound);
        round.addAll(KidsBank.pickWithPractice(this, n, practice ? 0 : ledger.extraPool,
                plan.stage, plan.gradesSelected(), plan.subjects()));
        if (round.isEmpty()) {
            if (!freePractice) Kids.grantQuizTime(plan.sessionMinutes);
            close();
            return;
        }
        for (int i = 0; i < round.size(); i++) picked.add(-1);
        // The next round has to start somewhere else, or a child sees the same questions twice.
        KidsBank.remember(this, round);
        showQuestion();
    }

    /** Append a fresh batch of questions for free practice, skipping ones already in this round. */
    private boolean extendRound() {
        int n = Math.max(1, plan.questionsPerRound);
        List<KidsBank.Q> more = KidsBank.pickWithPractice(this, n, ledger.extraPool,
                plan.stage, plan.gradesSelected(), plan.subjects());
        boolean added = false;
        for (KidsBank.Q q : more) {
            if (round.contains(q)) continue;
            round.add(q);
            picked.add(-1);
            added = true;
        }
        return added;
    }

    private void showQuestion() {
        if (index >= round.size()) {
            // Free practice (the dock's "answer now") has no fixed length: top up the list and keep
            // going instead of showing the round-result panel, so a child can practise until they go back.
            if (freePractice && extendRound()) {
                // round now has more questions; fall through to render the next one
            } else {
                finishRound();
                return;
            }
        }
        KidsBank.Q q = round.get(index);
        progress.setText(getString(R.string.quiz_progress, index + 1, round.size()));
        showScore();
        showStars();
        question.setText(q.text);
        feedback.setText(R.string.quiz_pick);
        feedback.setTextColor(0xFF9A6A28);
        int choice = picked.get(index);
        answered = choice >= 0;
        waitingNext = false;

        options.removeAllViews();
        optionViews.clear();
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < q.options.length; i++) {
            TextView view = (TextView) inflater.inflate(R.layout.item_quiz_option, options, false);
            view.setText(optionLabel(i) + "   " + q.options[i]);
            final int pick = i;
            view.setOnClickListener(v -> answer(pick));
            options.addView(view);
            optionViews.add(view);
        }
        prevButton.setEnabled(index > 0);
        prevButton.setAlpha(index > 0 ? 1f : 0.4f);
        // Continue waits for the answer: skipping a question would skip the thing being asked.
        continueButton.setEnabled(answered);
        continueButton.setAlpha(answered ? 1f : 0.4f);
        if (answered) {
            // Stepping back: show what was picked, what was right, and let a press move on.
            markAnswer(q, choice);
            waitingNext = true;
        } else if (!optionViews.isEmpty()) {
            optionViews.get(0).requestFocus();
        }
    }

    private void showStars() {
        starsView.setVisibility(starsOn() ? View.VISIBLE : View.GONE);
        if (starsOn()) starsView.setText(getString(R.string.quiz_stars, Kids.stars()));
    }

    private String optionLabel(int i) {
        return String.valueOf((char) ('A' + i));
    }

    private void answer(int choice) {
        if (index >= round.size() || picked.get(index) >= 0) return;
        picked.set(index, choice);
        answered = true;
        KidsBank.Q q = round.get(index);
        boolean right = choice == q.answer;
        KidsBank.record(this, q.id, right, Kids.day());
        Kids.recordAnswer(right);

        if (right) {
            correct++;
            streak++;
            QuizSounds.playCorrect();
            if (starsOn()) {
                earned += STAR_PER_RIGHT;
                Kids.addStars(STAR_PER_RIGHT);
                showStars();
                popStar();
            }
        } else {
            streak = 0;
            QuizSounds.playWrong();
        }
        markAnswer(q, choice);
        showScore();
        continueButton.setEnabled(true);
        continueButton.setAlpha(1f);

        long delay = plan.autoNextSeconds > 0 ? plan.autoNextSeconds * 1_000L : 0;
        if (delay > 0) {
            handler.postDelayed(nextQuestion, delay);
        } else {
            waitingNext = true;
            feedback.setText(getString(R.string.quiz_next));
            feedback.setTextColor(0xFF9A6A28);
        }
    }

    /** The two counters in the corner: today's right and wrong, cleared at midnight. */
    private void showScore() {
        score.setText(getString(R.string.quiz_score_pair, ledger.rightToday, ledger.wrongToday));
    }

    /** Paints the pick red and the right answer green, and says which was which. */
    private void markAnswer(KidsBank.Q q, int choice) {
        boolean right = choice == q.answer;
        if (choice < optionViews.size() && !right) {
            optionViews.get(choice).setBackgroundResource(R.drawable.quiz_option_wrong);
        }
        if (q.answer < optionViews.size()) {
            optionViews.get(q.answer).setBackgroundResource(R.drawable.quiz_option_right);
        }
        if (right) {
            feedback.setText(R.string.quiz_right);
            feedback.setTextColor(0xFF2E8B57);
        } else {
            feedback.setText(getString(R.string.quiz_wrong_pick, q.options[choice], q.options[q.answer])
                    + "\n✊ " + getString(R.string.quiz_cheer));
            feedback.setTextColor(0xFFC0432F);
        }
    }

    /** On to the next question without waiting out the auto-advance. */
    private void next() {
        if (closing || !answered) return;
        handler.removeCallbacks(nextQuestion);
        waitingNext = false;
        index++;
        showQuestion();
    }

    /** One question back; an answered question shows its result again instead of asking twice. */
    private void previous() {
        if (closing || index == 0) return;
        handler.removeCallbacks(nextQuestion);
        waitingNext = false;
        index--;
        showQuestion();
    }

    /** A star flies in from below, bounces, then drifts off: one per right answer. */
    private void popStar() {
        if (starPop == null) return;
        QuizSounds.playStar();
        starPop.animate().cancel();
        starPop.setVisibility(View.VISIBLE);
        starPop.setAlpha(0f);
        starPop.setScaleX(0.4f);
        starPop.setScaleY(0.4f);
        starPop.setTranslationY(140f);
        starPop.animate().alpha(1f).scaleX(1.25f).scaleY(1.25f).translationY(0f)
                .setDuration(320)
                .withEndAction(() -> starPop.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .withEndAction(() -> starPop.animate().alpha(0f).translationY(-110f)
                                .setDuration(420)
                                .withEndAction(() -> starPop.setVisibility(View.INVISIBLE))
                                .start())
                        .start())
                .start();
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
                // "Practice only" clears the pool; the free practice leaves it for the next locked round.
                if (!freePractice) {
                    ledger.extraPool = 0;
                    ledger.save(this);
                }
            } else {
                rewarded = Kids.grantQuizTime(plan.sessionMinutes);
            }
        }
        // A round with every answer right is worth one more star.
        if (starsOn() && correct == n && n > 0) {
            earned += STAR_PERFECT_BONUS;
            Kids.addStars(STAR_PERFECT_BONUS);
        }
        showResult(pass, rewarded, n);
    }

    private void showResult(boolean pass, int rewarded, int n) {
        resultPanel.setVisibility(View.VISIBLE);
        if (practice) {
            resultIcon.setText(starsOn() ? "\u2B50" : "\uD83C\uDF1F");
            resultTitle.setText(R.string.quiz_practice_title);
            String sub = getString(R.string.quiz_practice_sub, correct, n);
            if (starsOn()) {
                sub = getString(R.string.quiz_star_earned, earned) + "\n"
                        + getString(R.string.quiz_star_total, Kids.stars()) + "\n" + sub;
            }
            resultSub.setText(sub);
            againButton.setVisibility(View.VISIBLE);
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
        closeButton.requestFocus();
        if (againButton.getVisibility() == View.VISIBLE) againButton.requestFocus();
    }

    private void restart() {
        if (closing) return;
        if (!freePractice && !ledger.roundsLeft(plan.roundsPerDay)) return;
        startRound();
    }

    private void close() {
        closing = true;
        KidsWatch.poke();
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Free practice only ever goes back to the mode it came from; a locked round cannot be escaped.
            if (freePractice) {
                close();
                return true;
            }
            return true;
        }
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
        // Free practice leaves for the mode it came from; nothing behind a locked round is reachable.
        if (freePractice) close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(nextQuestion);
        if (starPop != null) starPop.animate().cancel();
        KidsWatch.quizShowing = false;
        KidsWatch.poke();
    }
}
