package io.qzxy.lite.preview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.qzxy.lite.core.EduEvaluation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 学生评价（评教）: which 教学班 still need an evaluation, and which are already done.
 *
 * <p>Read through {@link EduClient} on the session the 成绩 and 学业情况 screens already share, and
 * laid out the way 学业情况 is: one card per 教学班, the head carrying what identifies it and a state
 * chip, the body holding the rest and the way in. Tapping the head folds the card open — the same
 * motion as the credit-group cards — and the button inside opens the native question sheet.</p>
 *
 * <p>Everything here is a read. The questions, the picking and the submitting all happen in
 * {@link EvaluationFormActivity}; this screen only says what is left and re-reads itself when the
 * reader comes back, so a submitted 教学班 shows as 已提交 without a manual refresh.</p>
 */
public final class EvaluationActivity extends Page {
    /** Above the list: which states are shown. */
    private static final int ALL = 0;
    private static final int PENDING = 1;
    private static final int SAVED = 2;
    private static final int DONE = 3;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DeviceScreen screen;
    private LinearLayout results, filters;
    private TextView status;
    private int generation;
    private boolean busy, needsLogin, afterAccount, openedForm;

    private EduEvaluation.Report report;
    private int filter = ALL;
    private final android.os.Handler watchdog = new android.os.Handler(android.os.Looper.getMainLooper());

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    /** One 教学班 card: its foldable body and which way it currently faces. */
    private static final class Card {
        LinearLayout body;
        TextView arrow;
        boolean open;
        ValueAnimator running;
        int generation;
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        screen = new DeviceScreen(this, "学生评价");
        screen.icon("↻", "重新读取", this::reload);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(AppUi.MUTED);
        status.setPadding(dp(2), 0, dp(2), dp(10));
        status.setText("正在读取教务系统…");
        screen.root.addView(status);

        LinearLayout loginBar = new LinearLayout(this);
        loginBar.setOrientation(LinearLayout.HORIZONTAL);
        loginBar.addView(action("教务账号", this::openAccount), new LinearLayout.LayoutParams(0, -2, 1));
        loginBar.addView(action("网页登录", this::openLogin), new LinearLayout.LayoutParams(0, -2, 1));
        loginBar.addView(action("退出登录", this::signOut), new LinearLayout.LayoutParams(0, -2, 1));
        screen.root.addView(loginBar);

        screen.note("数据来自学校教务系统「学生评价」。题目、选项与提交都走学校自己的接口，评分由你自己选。");

        filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, dp(2), 0, dp(8));
        screen.root.addView(filters);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        screen.root.addView(results, new LinearLayout.LayoutParams(-1, -2));
        load();
    }

    private void reload() {
        if (busy) return;
        results.removeAllViews();
        load();
    }

    private void load() {
        if (busy) return;
        EduClient.capture();
        busy = true;
        needsLogin = false;
        screen.loading(true);
        status.setText("正在读取教务系统…");
        final int ticket = ++generation;
        watchdog.removeCallbacksAndMessages(null);
        watchdog.postDelayed(() -> {
            if (ticket != generation || !busy) return;
            busy = false;
            screen.loading(false);
            status.setText("读取超时了。教学系统可能没响应，请检查网络后点右上角重新读取。");
        }, 45000);
        worker.execute(() -> {
            try {
                EduVault.Credential credential = new EduVault(getApplicationContext()).load();
                EduClient.remember(credential == null ? "" : credential.user,
                    credential == null ? "" : credential.password,
                    credential != null && credential.refused);
                EduEvaluation.Report list = EduClient.evaluations();
                EduEvaluation.Counts counts = null;
                try { counts = EduClient.evaluationCounts(); }
                catch (Exception noCounts) { /* The list is the point; totals are an extra. */ }
                final EduEvaluation.Counts summary = counts;
                post(ticket, () -> show(list, summary));
            } catch (EduClient.NotSignedIn expired) {
                post(ticket, this::askLogin);
            } catch (EduClient.BadPassword wrong) {
                post(ticket, () -> fail(wrong.getMessage()));
            } catch (EduClient.CaptchaNeeded captcha) {
                post(ticket, () -> fail(captcha.getMessage()));
            } catch (Exception failure) {
                post(ticket, () -> fail(EduClient.describe(failure)));
            }
        });
    }

    private void askLogin() {
        busy = false;
        needsLogin = true;
        screen.loading(false);
        status.setText("还没登录教务系统。把学号和密码存一次以后会自动登录；也可以点「网页登录」手动登一次。");
        results.removeAllViews();
        filters.removeAllViews();
        results.addView(hint("学生评价需要先登录教务系统。"));
    }

    private void openAccount() {
        afterAccount = true;
        startActivity(new Intent(this, EduAccountActivity.class));
    }

    private void openLogin() {
        afterAccount = true;
        startActivity(new Intent(this, EduLoginActivity.class));
    }

    private void signOut() {
        EduClient.signOutLater();
        status.setText("已退出教务登录。下次读取会重新登录一次。");
    }

    private void fail(String message) {
        busy = false;
        screen.loading(false);
        status.setText(message == null || message.isEmpty() ? "读取失败，请稍后重试。" : message);
        results.removeAllViews();
        filters.removeAllViews();
        results.addView(hint("可以点右上角「重新读取」再试一次；如果一直失败，用「网页登录」进教务系统确认评教是否已开放。"));
    }

    private void show(EduEvaluation.Report list, EduEvaluation.Counts counts) {
        busy = false;
        needsLogin = false;
        screen.loading(false);
        report = list;
        results.removeAllViews();
        filters.removeAllViews();

        if (list.size() == 0) {
            status.setText(counts != null && counts.submitted > 0
                ? "本期 " + counts.submitted + " 项都已提交，没有待评的课程。"
                : "本期没有需要评价的课程。");
            results.addView(hint("教务系统没有返回评价列表：可能本期评教还没开始，或者已经结束。"));
            return;
        }

        int pending = list.withState(EduEvaluation.UNEVALUATED).size();
        int saved = list.withState(EduEvaluation.SAVED).size();
        int done = list.withState(EduEvaluation.SUBMITTED).size();
        status.setText("共 " + list.size() + " 门 · " + pending + " 门待评");

        summaryCard(pending, saved, done, counts);
        chips(pending, saved, done);
        list();
    }

    /** 未评 / 已评 / 暂存, as the school counts them, on one row above the list. */
    private void summaryCard(int pending, int saved, int done, EduEvaluation.Counts counts) {
        LinearLayout card = card();
        TextView headline = new TextView(this);
        headline.setText(pending == 0 ? "都评完了" : "还有 " + pending + " 门要评");
        headline.setTextSize(20);
        headline.setTypeface(null, Typeface.BOLD);
        headline.setTextColor(pending == 0 ? AppUi.MUTED : AppUi.ACCENT);
        card.addView(headline);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        stat(row, "未评", pending);
        stat(row, "已评", done);
        stat(row, "暂存", saved);
        card.addView(row);

        if (counts != null && !counts.note.isEmpty()) {
            TextView note = new TextView(this);
            note.setText(counts.note);
            note.setTextSize(12);
            note.setTextColor(AppUi.MUTED);
            note.setPadding(0, dp(10), 0, 0);
            card.addView(note);
        }
        addCard(card);
    }

    private void stat(LinearLayout row, String label, int value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(0, 0, dp(10), 0);
        TextView number = new TextView(this);
        number.setText(Integer.toString(value));
        number.setTextSize(16);
        number.setTypeface(null, Typeface.BOLD);
        number.setTextColor(AppUi.ACCENT);
        cell.addView(number);
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextSize(11);
        caption.setTextColor(AppUi.MUTED);
        cell.addView(caption);
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
    }

    /** The filter row: tapping one shows only that state. Counts stay visible on each chip. */
    private void chips(int pending, int saved, int done) {
        chip("全部 " + (pending + saved + done), ALL);
        chip("未评 " + pending, PENDING);
        chip("暂存 " + saved, SAVED);
        chip("已评 " + done, DONE);
    }

    private void chip(String label, int which) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(34));
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        boolean on = filter == which;
        view.setTextColor(on ? AppUi.ON_ACCENT : AppUi.INK);
        view.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        view.setBackground(on ? AppUi.shape(this, AppUi.ACCENT, 17)
            : AppUi.outlined(this, AppUi.SURFACE, 17, AppUi.LINE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.rightMargin = dp(8);
        view.setOnClickListener(v -> {
            if (filter == which) return;
            filter = which;
            filters.removeAllViews();
            if (report != null) {
                chips(report.withState(EduEvaluation.UNEVALUATED).size(),
                    report.withState(EduEvaluation.SAVED).size(),
                    report.withState(EduEvaluation.SUBMITTED).size());
                list();
            }
        });
        filters.addView(view, params);
    }

    private void list() {
        results.removeAllViews();
        if (report == null) return;
        List<EduEvaluation.Item> shown = new ArrayList<EduEvaluation.Item>();
        // 未评 first: it is the only group that needs the reader to do something.
        if (filter == ALL || filter == PENDING) shown.addAll(report.withState(EduEvaluation.UNEVALUATED));
        if (filter == ALL || filter == SAVED) shown.addAll(report.withState(EduEvaluation.SAVED));
        if (filter == ALL || filter == DONE) shown.addAll(report.withState(EduEvaluation.SUBMITTED));
        if (filter == ALL) shown.addAll(report.withState(EduEvaluation.UNKNOWN));

        if (shown.isEmpty()) {
            results.addView(hint(filter == PENDING ? "没有待评的课程了。" : "这个状态下没有课程。"));
            return;
        }
        for (EduEvaluation.Item item : shown) addCard(courseCard(item));
    }

    /**
     * One 教学班 as a card: the course and its state on the head, teacher and class always visible,
     * the rest and the way in folded underneath. A 已提交 card is drawn in the quieter tone — it is
     * history, not something to act on.
     */
    private LinearLayout courseCard(final EduEvaluation.Item item) {
        LinearLayout card = card();
        final Card state = new Card();
        state.generation = ++cardGeneration;

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(30));

        TextView name = new TextView(this);
        name.setText(item.course.isEmpty() ? item.className : item.course);
        name.setTextSize(15);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(item.done() ? AppUi.MUTED : AppUi.INK);
        name.setMaxLines(3);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(chipFor(item));

        TextView arrow = new TextView(this);
        arrow.setText("▸");
        arrow.setTextSize(12);
        arrow.setTextColor(AppUi.MUTED);
        arrow.setPadding(dp(8), 0, 0, 0);
        head.addView(arrow);
        state.arrow = arrow;
        card.addView(head);

        // Teacher and 教学班 stay on the card face: they are how a reader tells two rows of the same
        // course apart, and hiding them behind a tap would make the list unreadable.
        TextView line = new TextView(this);
        line.setText(face(item));
        line.setTextSize(12);
        line.setTextColor(AppUi.MUTED);
        line.setMaxLines(2);
        line.setPadding(0, dp(4), 0, 0);
        card.addView(line);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        state.body = body;
        card.addView(body);
        fill(body, item);

        AppUi.press(head, 0x00ffffff, 8);
        head.setOnClickListener(v -> fold(state));
        card.setContentDescription(item.course + "，" + item.teacher + "，" + item.label());
        return card;
    }

    /** What stays visible: 教师 · 教学班 · 学时类型. */
    private String face(EduEvaluation.Item item) {
        StringBuilder out = new StringBuilder();
        if (!item.teacher.isEmpty()) out.append(item.teacher);
        String detail = item.detail();
        if (!detail.isEmpty()) {
            if (out.length() > 0) out.append(" · ");
            out.append(detail);
        }
        return out.toString();
    }

    /** What the fold holds: the department, the score when the school gave one, and the way in. */
    private void fill(LinearLayout body, EduEvaluation.Item item) {
        body.removeAllViews();
        if (!item.department.isEmpty()) body.addView(detail("开课单位", item.department));
        if (!item.score.isEmpty()) body.addView(detail("评价分数", item.score));
        if (item.done()) {
            body.addView(detail("状态", "已提交，学校不再接收修改；可以打开看看当时选了什么"));
        } else if (item.saved()) {
            body.addView(detail("状态", "已暂存，可以继续评"));
        } else {
            body.addView(detail("状态", "还没评，评完记得提交"));
        }

        TextView open = new TextView(this);
        open.setText(item.done() ? "查看这份评价" : item.saved() ? "继续评价" : "开始评价");
        open.setTextSize(14);
        open.setTypeface(null, Typeface.BOLD);
        open.setTextColor(AppUi.ON_ACCENT);
        open.setGravity(Gravity.CENTER);
        open.setMinHeight(dp(42));
        open.setBackground(AppUi.shape(this, AppUi.ACCENT, 10));
        AppUi.press(open, AppUi.ACCENT, 10);
        open.setOnClickListener(v -> openForm(item));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        body.addView(open, params);
    }

    private TextView detail(String label, String value) {
        TextView view = new TextView(this);
        view.setText(label + "：" + value);
        view.setTextSize(12);
        view.setTextColor(AppUi.MUTED);
        view.setPadding(0, dp(6), 0, 0);
        return view;
    }

    private TextView chipFor(EduEvaluation.Item item) {
        TextView chip = new TextView(this);
        chip.setText(item.label());
        chip.setTextSize(12);
        chip.setTextColor(item.done() ? AppUi.MUTED : AppUi.ACCENT);
        chip.setPadding(dp(8), dp(2), dp(8), dp(2));
        chip.setBackground(AppUi.shape(this, AppUi.SOFT, 8));
        return chip;
    }

    // ---- the fold -------------------------------------------------------------------------

    /** Bumped on every reload, so a fold started before a reload cannot land in a dead card. */
    private int cardGeneration;

    /**
     * Opens or closes one card, animating its height the way the 学业情况 cards do: one animator on
     * the body's height, cancelled and re-measured when the reader taps again mid-flight, and no
     * animation at all when the system asks for none.
     */
    private void fold(Card card) {
        if (card.body == null) return;
        boolean opening = !card.open;
        card.open = opening;
        card.arrow.setText(opening ? "▾" : "▸");

        int from = card.body.getHeight();
        if (card.running != null) card.running.cancel();
        card.body.setVisibility(View.VISIBLE);
        final int target = opening ? expandHeight(card.body) : 0;

        if (!MotionSpec.shouldAnimate(this) || from == target) {
            settle(card.body, opening ? ViewGroup.LayoutParams.WRAP_CONTENT : 0);
            return;
        }
        final int ticket = card.generation;
        ValueAnimator animator = ValueAnimator.ofInt(from, target);
        animator.setDuration(MotionSpec.LIST_TOGGLE_DURATION);
        animator.setInterpolator(MotionSpec.settle());
        animator.addUpdateListener(a -> {
            if (ticket != card.generation) return;
            height(card.body, (Integer) a.getAnimatedValue());
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (ticket != card.generation) return;   // a second tap took over
                card.running = null;
                settle(card.body, opening ? ViewGroup.LayoutParams.WRAP_CONTENT : 0);
            }
        });
        card.running = animator;
        animator.start();
    }

    /** The height the body wants, measured with the width it will actually get. */
    private int expandHeight(LinearLayout body) {
        int width = body.getWidth();
        if (width <= 0) width = screen.root.getWidth() - AppUi.dp(this, 32);
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
        body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return body.getMeasuredHeight();
    }

    private void height(View body, int value) {
        ViewGroup.LayoutParams params = body.getLayoutParams();
        if (params == null) return;
        // 这里只能原样写回。像素值（动画每帧）不需要钳制，而 WRAP_CONTENT 是 -2、MATCH_PARENT
        // 是 -1：早期版本给这个值加了「不小于 0」的兜底，把 settle() 要的自适应高度也压成了 0，
        // 于是卡片展开动画跑完就立刻塌回 0 高度——看起来正是「一点开就缩回去」，箭头却还停在 ▾。
        params.height = value;
        body.setLayoutParams(params);
    }

    private void settle(View body, int value) {
        height(body, value);
        body.setVisibility(value == 0 ? View.GONE : View.VISIBLE);
    }

    // ---- navigation -----------------------------------------------------------------------

    /**
     * Opens the native question sheet for this 教学班. The whole row crosses over, not just its id:
     * the form request needs all six ids the grid gave, and re-deriving them would be a second
     * source of truth for the same values.
     */
    private void openForm(EduEvaluation.Item item) {
        openedForm = true;
        Intent intent = new Intent(this, EvaluationFormActivity.class);
        intent.putExtra(EvaluationFormActivity.EXTRA_CLASS, item.classId);
        intent.putExtra(EvaluationFormActivity.EXTRA_COURSE, item.courseId);
        intent.putExtra(EvaluationFormActivity.EXTRA_TEACHER_ID, item.teacherId);
        intent.putExtra(EvaluationFormActivity.EXTRA_TYPE, item.studentType);
        intent.putExtra(EvaluationFormActivity.EXTRA_MODEL, item.modelId);
        intent.putExtra(EvaluationFormActivity.EXTRA_GRADE_ENTRY, item.gradeEntry);
        intent.putExtra(EvaluationFormActivity.EXTRA_STATUS, item.statusCode);
        intent.putExtra(EvaluationFormActivity.EXTRA_TITLE,
            (item.course.isEmpty() ? "学生评价" : item.course)
                + (item.teacher.isEmpty() ? "" : " · " + item.teacher));
        try {
            startActivity(intent);
        } catch (RuntimeException unavailable) {
            openedForm = false;
            status.setText("无法打开评价页面。");
        }
    }

    /** Creates a styled card but does NOT attach it — the caller decides placement via addCard. */
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 12, AppUi.LINE));
        return card;
    }

    /** The single place a card reaches results, with its bottom spacing. */
    private void addCard(LinearLayout card) {
        if (card.getChildCount() == 0) return;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        results.addView(card, params);
    }

    private TextView hint(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(AppUi.MUTED);
        view.setPadding(0, dp(16), 0, dp(16));
        return view;
    }

    private TextView action(String label, Runnable action) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(14);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(AppUi.ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(44));
        AppUi.press(view, 0x00ffffff, 10);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void post(int ticket, Runnable task) {
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing() && ticket == generation) task.run(); });
    }

    @Override protected void onResume() {
        super.onResume();
        // Coming back from the question sheet is the one moment the state can have changed; a plain
        // resume (screen off, another app) keeps the list that is already on screen.
        if (needsLogin || afterAccount || openedForm) {
            needsLogin = false;
            afterAccount = false;
            openedForm = false;
            reload();
        }
    }

    @Override protected void onStop() {
        super.onStop();
        EduClient.release();
    }

    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
