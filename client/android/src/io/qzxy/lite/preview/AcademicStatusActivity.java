package io.qzxy.lite.preview;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import io.qzxy.lite.core.EduAcademic;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 学生学业情况查询: GPA, course statistics and the credit-group tree, read straight from the
 * school's own page through {@link EduClient} (the same signed-in session grades use).
 *
 * <p>Extraction is unit-tested in the core module; this screen only lays the result out. It keeps
 * the same card language as the grades screen — a headline card, then one calm row per credit
 * group with a thin progress bar — so nothing here depends on the school's own styling.</p>
 */
public final class AcademicStatusActivity extends Page {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DeviceScreen screen;
    private LinearLayout results;
    private TextView status;
    private int generation;
    private boolean busy;
    private boolean needsLogin;
    private boolean afterAccount;
    private final android.os.Handler watchdog = new android.os.Handler(android.os.Looper.getMainLooper());

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        screen = new DeviceScreen(this, "学业情况");
        screen.icon("↻", "重新读取", this::reload);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(AppUi.MUTED);
        status.setPadding(dp(2), 0, dp(2), dp(10));
        status.setText("正在读取教务系统…");
        screen.root.addView(status);

        LinearBar loginBar = new LinearBar();
        loginBar.add(action("教务账号", this::openAccount));
        loginBar.add(action("网页登录", this::openLogin));
        loginBar.add(action("退出登录", this::signOut));
        screen.root.addView(loginBar);

        screen.note("数据来自学校教务系统「学生学业情况查询」，仅供修读情况参考；学分与要求以学校为准。");

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        screen.root.addView(results, new LinearLayout.LayoutParams(-1, -2));
        load();
    }

    /** Three equal-width text buttons, matching the account row on the grades screen. */
    private final class LinearBar extends LinearLayout {
        LinearBar() {
            super(AcademicStatusActivity.this);
            setOrientation(LinearLayout.HORIZONTAL);
        }
        void add(TextView view) {
            addView(view, new LinearLayout.LayoutParams(0, -2, 1));
        }
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
                EduVault vault = new EduVault(getApplicationContext());
                EduVault.Credential credential = vault.load();
                EduClient.remember(credential == null ? "" : credential.user,
                    credential == null ? "" : credential.password,
                    credential != null && credential.refused);
                EduAcademic.Report report = EduClient.academicStatus();
                post(ticket, () -> show(report));
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
        results.addView(hint("学业情况需要先登录教务系统。"));
    }

    private void openAccount() {
        afterAccount = true;
        startActivity(new android.content.Intent(this, EduAccountActivity.class));
    }

    private void openLogin() {
        afterAccount = true;
        startActivity(new android.content.Intent(this, EduLoginActivity.class));
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

    private void show(EduAcademic.Report report) {
        busy = false;
        needsLogin = false;
        screen.loading(false);
        results.removeAllViews();
        if (report.nodes.isEmpty() && report.gpa.isEmpty()) {
            status.setText("学校这次没有返回学业情况数据。");
            results.addView(hint("换用网页登录后再试，或到教务系统确认已开放该模块。"));
            return;
        }
        status.setText("共 " + report.nodes.size() + " 个课程类别" + (report.gpa.isEmpty() ? "" : " · GPA " + report.gpa));
        headCard(report);
        for (EduAcademic.Node node : report.nodes) addCard(nodeCard(node));
    }

    /** Headline card: GPA, earned/required credits and the course counts school reported. */
    private void headCard(EduAcademic.Report report) {
        LinearLayout card = card();
        TextView label = new TextView(this);
        label.setText("课程修读情况");
        label.setTextSize(13);
        label.setTextColor(AppUi.MUTED);
        card.addView(label);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.BOTTOM);
        top.setPadding(0, dp(6), 0, dp(4));
        TextView gpa = metric(report.gpa.isEmpty() ? "—" : report.gpa);
        gpa.setTextSize(30);
        top.addView(gpa);
        TextView gpaLabel = new TextView(this);
        gpaLabel.setText("  平均学分绩点");
        gpaLabel.setTextSize(12);
        gpaLabel.setTextColor(AppUi.MUTED);
        gpaLabel.setPadding(0, 0, 0, dp(4));
        top.addView(gpaLabel);
        card.addView(top);

        // Earned / required credits from the 主修 root node (first row the school emits).
        String earned = "", required = "";
        for (EduAcademic.Node node : report.nodes) {
            if ("主修".equals(node.name)) { earned = node.earned; required = node.required; break; }
        }
        if (!earned.isEmpty()) {
            TextView credits = new TextView(this);
            credits.setText("已获总学分 " + earned + " / 毕业要求 " + (required.isEmpty() ? "—" : required));
            credits.setTextSize(14);
            credits.setTextColor(AppUi.INK);
            credits.setPadding(0, dp(6), 0, dp(2));
            card.addView(credits);
            ProgressBar bar = bar(earned, required);
            card.addView(bar, new LinearLayout.LayoutParams(-1, dp(8)));
        }

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(10), 0, 0);
        stat(stats, "计划课程", report.plan);
        stat(stats, "通过", report.passed);
        stat(stats, "在读", report.studying);
        stat(stats, "未修", report.unread);
        card.addView(stats);
        addCard(card);
    }

    private void stat(LinearLayout row, String label, String value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(0, 0, dp(10), 0);
        TextView v = new TextView(this);
        v.setText(value == null || value.isEmpty() ? "—" : value);
        v.setTextSize(16);
        v.setTypeface(null, Typeface.BOLD);
        v.setTextColor(AppUi.ACCENT);
        cell.addView(v);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(AppUi.MUTED);
        cell.addView(l);
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
    }

    /** One credit group: name, earned/required, a thin progress bar and a pass marker. */
    private LinearLayout nodeCard(EduAcademic.Node node) {
        LinearLayout card = card();
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(node.name);
        name.setTextSize(14);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(AppUi.INK);
        name.setMaxLines(2);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (node.passed) {
            TextView pass = new TextView(this);
            pass.setText("已达标");
            pass.setTextSize(11);
            pass.setTextColor(AppUi.ACCENT);
            head.addView(pass);
        }
        card.addView(head);

        TextView value = new TextView(this);
        value.setText(node.earned + " / " + (node.required.isEmpty() ? "—" : node.required) + " 学分");
        value.setTextSize(12);
        value.setTextColor(AppUi.MUTED);
        value.setPadding(0, dp(2), 0, dp(4));
        card.addView(value);
        card.addView(bar(node.earned, node.required), new LinearLayout.LayoutParams(-1, dp(6)));
        return card;
    }

    private ProgressBar bar(String earned, String required) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        int max = 0, have = 0;
        try { have = Math.round(Float.parseFloat(earned)); } catch (RuntimeException ignored) { }
        try { max = Math.round(Float.parseFloat(required)); } catch (RuntimeException ignored) { }
        if (max <= 0) max = Math.max(1, have);
        bar.setMax(max);
        bar.setProgress(Math.max(0, Math.min(max, have)));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(AppUi.ACCENT));
        return bar;
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        results.addView(card, params);
    }

    private TextView metric(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(AppUi.ACCENT);
        return view;
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
        if (needsLogin || afterAccount) {
            needsLogin = false;
            afterAccount = false;
            reload();
        }
    }

    @Override protected void onStop() {
        super.onStop();
        EduClient.release();
    }

    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
