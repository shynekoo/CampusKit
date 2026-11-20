package io.qzxy.lite.preview;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.qzxy.lite.core.EduEvaluation;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 学生评价（评教）: which 教学班 still need an evaluation, and which are already done.
 *
 * <p>Read through {@link EduClient} on the session the grades and 学业情况 screens already share, so
 * no second login is needed. The list is grouped by state — 未评 first, because that is the only
 * group that needs the reader to do something — and every row opens the school's own evaluation
 * form for that 教学班.</p>
 *
 * <p>Nothing is scored here. Submission is one-way at the school, and a score filled in on the
 * reader's behalf would be the app's opinion, not theirs; the form is the school's, opened with the
 * ids this list already holds. Coming back reloads the list so a submitted evaluation shows up as
 * 已提交 without a manual refresh.</p>
 */
public final class EvaluationActivity extends Page {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DeviceScreen screen;
    private LinearLayout results;
    private TextView status;
    private int generation;
    private boolean busy, needsLogin, afterAccount, openedForm;
    private final android.os.Handler watchdog = new android.os.Handler(android.os.Looper.getMainLooper());

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

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

        screen.note("数据来自学校教务系统「学生评价」；评分和提交都在学校页面上完成，这里只读取评价状态。");

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
                EduEvaluation.Report report = EduClient.evaluations();
                EduEvaluation.Counts counts = null;
                try { counts = EduClient.evaluationCounts(); }
                catch (Exception noCounts) { /* The list is the point; totals are an extra. */ }
                final EduEvaluation.Counts summary = counts;
                post(ticket, () -> show(report, summary));
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
        results.addView(hint("可以点右上角「重新读取」再试一次。"));
    }

    private void show(EduEvaluation.Report report, EduEvaluation.Counts counts) {
        busy = false;
        needsLogin = false;
        screen.loading(false);
        results.removeAllViews();
        if (report.size() == 0) {
            status.setText(counts != null && counts.submitted > 0
                ? "本期 " + counts.submitted + " 项都已提交，没有待评的课程。"
                : "本期没有需要评价的课程。");
            results.addView(hint("教务系统没有返回评价列表：可能本期评教还没开始，或者已经结束。"));
            return;
        }
        int pending = report.withState(EduEvaluation.UNEVALUATED).size();
        status.setText(pending == 0
            ? "共 " + report.size() + " 项，都已评价。"
            : "共 " + report.size() + " 项 · 还有 " + pending + " 项未评");
        summaryCard(report, counts);
        section("未评", report.withState(EduEvaluation.UNEVALUATED));
        section("暂存", report.withState(EduEvaluation.SAVED));
        section("已提交", report.withState(EduEvaluation.SUBMITTED));
        section("其他状态", report.withState(EduEvaluation.UNKNOWN));
    }

    /**
     * The headline: how much is left, then the school's own totals.
     *
     * <p>Three counts come from two different places — 未评/暂存/已提交 computed from the rows so the
     * card always agrees with the list below it, and the term totals from the school's 评价情况
     * query when it answers. When the two disagree the row count wins: it is what the reader can
     * actually open and act on.</p>
     */
    private void summaryCard(EduEvaluation.Report report, EduEvaluation.Counts counts) {
        LinearLayout card = card();
        int pending = report.withState(EduEvaluation.UNEVALUATED).size();
        int saved = report.withState(EduEvaluation.SAVED).size();
        int done = report.withState(EduEvaluation.SUBMITTED).size();

        TextView headline = new TextView(this);
        headline.setText(pending == 0 ? "都评完了" : "还有 " + pending + " 项要评");
        headline.setTextSize(20);
        headline.setTypeface(null, Typeface.BOLD);
        headline.setTextColor(pending == 0 ? AppUi.MUTED : AppUi.ACCENT);
        card.addView(headline);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        stat(row, "未评", Integer.toString(pending));
        stat(row, "暂存", Integer.toString(saved));
        stat(row, "已提交", Integer.toString(done));
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

    private void stat(LinearLayout row, String label, String value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(0, 0, dp(10), 0);
        TextView number = new TextView(this);
        number.setText(value);
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

    /** One state group: a heading plus its rows, skipped entirely when the group is empty. */
    private void section(String title, List<EduEvaluation.Item> items) {
        if (items.isEmpty()) return;
        TextView heading = new TextView(this);
        heading.setText(title + " · " + items.size());
        heading.setTextSize(12);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(AppUi.MUTED);
        heading.setPadding(dp(2), dp(2), 0, dp(6));
        results.addView(heading);

        LinearLayout card = card();
        boolean first = true;
        for (EduEvaluation.Item item : items) {
            if (!first) card.addView(divider());
            first = false;
            card.addView(row(item));
        }
        addCard(card);
    }

    /**
     * One 教学班. The course name is what the reader recognises; teacher, class and 学时类型 go
     * underneath, and the state sits on the right so a 已提交 row does not read as "still to do".
     */
    private LinearLayout row(EduEvaluation.Item item) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(48));
        line.setPadding(0, dp(9), 0, dp(9));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(item.course.isEmpty() ? item.className : item.course);
        name.setTextSize(15);
        name.setTextColor(item.saved() ? AppUi.INK : item.done() ? AppUi.MUTED : AppUi.INK);
        name.setMaxLines(2);
        labels.addView(name);

        String detail = details(item);
        if (!detail.isEmpty()) {
            TextView extra = new TextView(this);
            extra.setText(detail);
            extra.setTextSize(12);
            extra.setTextColor(AppUi.MUTED);
            extra.setPadding(0, dp(3), 0, 0);
            extra.setMaxLines(2);
            labels.addView(extra);
        }
        line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView chip = new TextView(this);
        chip.setText(item.label());
        chip.setTextSize(12);
        chip.setTextColor(item.done() ? AppUi.MUTED : AppUi.ACCENT);
        chip.setPadding(dp(8), dp(2), dp(8), dp(2));
        chip.setBackground(AppUi.shape(this, AppUi.SOFT, 8));
        line.addView(chip);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(18);
        arrow.setTextColor(0xffb3bcc9);
        arrow.setPadding(dp(6), 0, 0, 0);
        line.addView(arrow);

        line.setContentDescription(item.course + "，" + item.teacher + "，" + item.label());
        AppUi.press(line, 0x00ffffff, 8);
        line.setOnClickListener(v -> openForm(item));
        return line;
    }

    private String details(EduEvaluation.Item item) {
        StringBuilder out = new StringBuilder();
        if (!item.teacher.isEmpty()) out.append(item.teacher);
        String detail = item.detail();
        if (!detail.isEmpty()) {
            if (out.length() > 0) out.append(" · ");
            out.append(detail);
        }
        if (item.done()) {
            if (out.length() > 0) out.append(" · ");
            out.append("已提交，只能查看");
        } else if (item.saved()) {
            if (out.length() > 0) out.append(" · ");
            out.append("已暂存，可继续评");
        }
        return out.toString();
    }

    /**
     * Opens the school's own 学生评价 page on this 教学班, which is the only place the form can be
     * submitted from: the fragment has no 提交 button of its own, the page does. Only the 教学班 id
     * crosses over — the ids the form needs are read by the school's own page from its own grid.
     */
    private void openForm(EduEvaluation.Item item) {
        openedForm = true;
        Intent intent = new Intent(this, EvaluationFormActivity.class);
        intent.putExtra(EvaluationFormActivity.EXTRA_CLASS, item.classId);
        intent.putExtra(EvaluationFormActivity.EXTRA_TITLE,
            (item.course.isEmpty() ? "学生评价" : item.course)
                + (item.teacher.isEmpty() ? "" : " · " + item.teacher));
        try {
            startActivity(intent);
        } catch (RuntimeException unavailable) {
            openedForm = false;
            status.setText("无法打开学校评价页面。");
        }
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(AppUi.LINE);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
        return line;
    }

    /** Creates a styled card but does NOT attach it — the caller decides placement via addCard. */
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 12, AppUi.LINE));
        return card;
    }

    /** The single place a card reaches results, with its bottom spacing. */
    private void addCard(LinearLayout card) {
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
        // Coming back from the school's form is the one moment the state can have changed; a plain
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
