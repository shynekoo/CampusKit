package io.qzxy.lite.preview;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import io.qzxy.lite.core.EduAcademic;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 学生学业情况查询: GPA, course statistics and the credit-group tree, read straight from the
 * school's own page through {@link EduClient} (the same signed-in session grades use).
 *
 * <p>The tree is the point of this module, so it is laid out as one: a card per 二级分类 holding
 * the earned/required credits and a bar, then one indented row per deeper group. A group that
 * actually holds courses is tappable and fetches them on demand — the school keeps those behind a
 * separate query, which is exactly what its own page does when an arrow is expanded. Extraction is
 * unit-tested in the core module; this screen only lays the result out.</p>
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
    /** Courses already fetched, per credit-group id, so re-opening a group costs nothing. */
    private final Map<String, List<EduAcademic.Course>> courses = new HashMap<>();
    /** Groups with a request in flight, so a double tap cannot post twice. */
    private final Set<String> loading = new HashSet<>();
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
        courses.clear();
        loading.clear();
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
        courses.clear();
        loading.clear();
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
        status.setText("共 " + report.nodes.size() + " 个课程类别 · 点课组看具体课程");
        headCard(report);
        tree(report.nodes);
    }

    /**
     * Lays the school's own tree out: one card per 一级 / 二级分类 with its credits and a bar, then
     * one indented row per deeper group. A row whose group has no children is where the school
     * keeps the courses, so it becomes the tappable one; tapping it fetches that group's courses
     * and drops them in underneath. A page that carries no parent links degrades to one card per
     * node instead of an empty screen.
     */
    private void tree(List<EduAcademic.Node> nodes) {
        LinearLayout body = null;
        for (int i = 0; i < nodes.size(); i++) {
            EduAcademic.Node node = nodes.get(i);
            boolean hasChildren = i + 1 < nodes.size() && nodes.get(i + 1).depth > node.depth;
            if (node.depth <= 2) {
                if (node.depth == 1 && hasChildren) {
                    // The root already names the headline card; its categories are the cards.
                    body = null;
                    continue;
                }
                LinearLayout card = card();
                body = categoryCard(card, node, hasChildren);
                addCard(card);
                continue;
            }
            if (body == null) {
                LinearLayout card = card();
                body = card;
                addCard(card);
            }
            body.addView(rowGroup(node, !hasChildren));
        }
    }

    /**
     * A category card's head plus its foldable body. A category that owns children folds open and
     * shut on tap; one that owns none is a leaf and opens its courses instead. The arrow is the
     * only affordance, so it is always present and always reflects state.
     */
    private LinearLayout categoryCard(LinearLayout card, EduAcademic.Node node, boolean hasChildren) {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(30));

        TextView name = new TextView(this);
        name.setText(node.name);
        name.setTextSize(15);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(AppUi.INK);
        name.setMaxLines(2);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(credit(node, 12));

        TextView arrow = new TextView(this);
        arrow.setTextSize(12);
        arrow.setTextColor(AppUi.MUTED);
        arrow.setPadding(dp(8), 0, 0, 0);
        arrow.setText(hasChildren ? "▾" : "▸");
        head.addView(arrow);
        card.addView(head);

        if (positive(node.required)) {
            ProgressBar bar = bar(node.earned, node.required);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(6));
            params.topMargin = dp(8);
            card.addView(bar, params);
        }

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        if (hasChildren) body.setPadding(0, dp(2), 0, 0);
        card.addView(body);

        AppUi.press(head, 0x00ffffff, 8);
        if (hasChildren) {
            head.setOnClickListener(v -> {
                boolean shown = body.getVisibility() == View.VISIBLE;
                body.setVisibility(shown ? View.GONE : View.VISIBLE);
                arrow.setText(shown ? "▸" : "▾");
            });
        } else {
            LinearLayout[] area = courseArea(body);
            head.setOnClickListener(v -> toggle(node, area[0], area[1], arrow));
        }
        return body;
    }

    /**
     * An empty block for a group's courses, with a hairline down its left edge.
     *
     * <p>Without the rule, a course row and the next group's row sit at nearly the same indent and
     * read as one list; the rule brackets the courses under the group they belong to. Returns
     * {@code [outer, inner]}: {@code outer} owns visibility, {@code inner} owns the rows.</p>
     */
    private LinearLayout[] courseArea(LinearLayout parent) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setVisibility(View.GONE);
        outer.setPadding(0, dp(2), 0, dp(4));
        View rule = new View(this);
        rule.setBackgroundColor(AppUi.LINE);
        outer.addView(rule, new LinearLayout.LayoutParams(dp(2), LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        outer.addView(inner, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(outer);
        return new LinearLayout[]{outer, inner};
    }

    /** One nested group row plus, for a leaf group, the container its courses expand into. */
    private LinearLayout rowGroup(EduAcademic.Node node, boolean leaf) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(30));
        line.setPadding(dp(14) * Math.max(0, node.depth - 2), node.depth == 3 ? dp(9) : dp(5), 0, dp(5));

        TextView name = new TextView(this);
        name.setText(node.name);
        name.setTextSize(node.depth >= 4 ? 12 : 13);
        // Third level is the first "heading" inside a card, so it stays bold; everything under it
        // is detail and reads as normal weight. Three steps (card / heading / detail) are enough
        // to make the hierarchy obvious without turning the card into a wall of bold text.
        if (node.depth == 3) name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(node.depth >= 4 ? AppUi.MUTED : AppUi.INK);
        name.setMaxLines(2);
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (node.passed) {
            TextView ok = new TextView(this);
            ok.setText("✓");
            ok.setTextSize(11);
            ok.setTextColor(AppUi.ACCENT);
            ok.setPadding(dp(6), 0, dp(6), 0);
            line.addView(ok);
        }
        line.addView(credit(node, node.depth >= 4 ? 11 : 12));
        box.addView(line);

        if (!leaf) return box;
        LinearLayout[] area = courseArea(box);

        TextView arrow = new TextView(this);
        arrow.setText("▸");
        arrow.setTextSize(11);
        arrow.setTextColor(AppUi.MUTED);
        arrow.setPadding(dp(8), 0, dp(2), 0);
        line.addView(arrow);
        AppUi.press(line, 0x00ffffff, 8);
        line.setOnClickListener(v -> toggle(node, area[0], area[1], arrow));
        return box;
    }

    /**
     * Opens or closes one credit group's courses, fetching them the first time.
     *
     * <p>The fetch runs on the same single worker as the page load, so a burst of taps queues
     * instead of racing the session. The callback is guarded by the generation taken when the tap
     * happened: a reload in between drops the reply rather than painting it into a dead view.</p>
     */
    private void toggle(EduAcademic.Node node, LinearLayout area, LinearLayout holder, TextView arrow) {
        if (area.getVisibility() == View.VISIBLE) {
            area.setVisibility(View.GONE);
            arrow.setText("▸");
            return;
        }
        List<EduAcademic.Course> known = courses.get(node.id);
        if (known != null) {
            fill(holder, known, node);
            area.setVisibility(View.VISIBLE);
            arrow.setText("▾");
            return;
        }
        if (loading.contains(node.id)) return;
        loading.add(node.id);
        holder.removeAllViews();
        holder.addView(courseNote("正在读取课程…"));
        area.setVisibility(View.VISIBLE);
        arrow.setText("…");
        final int ticket = generation;
        worker.execute(() -> {
            try {
                List<EduAcademic.Course> list = EduClient.academicCourses(node.id);
                post(ticket, () -> {
                    loading.remove(node.id);
                    if (area.getParent() == null) return;
                    courses.put(node.id, list);
                    fill(holder, list, node);
                    arrow.setText("▾");
                });
            } catch (Exception failure) {
                post(ticket, () -> {
                    loading.remove(node.id);
                    if (area.getParent() == null) return;
                    holder.removeAllViews();
                    boolean expired = failure instanceof EduClient.NotSignedIn;
                    TextView retry = courseNote(expired
                        ? "登录过期了，点右上角重新读取。"
                        : "课程没读出来，点这里重试。");
                    if (!expired) retry.setOnClickListener(v -> {
                        area.setVisibility(View.GONE);
                        arrow.setText("▸");
                        toggle(node, area, holder, arrow);
                    });
                    holder.addView(retry);
                    arrow.setText("▾");
                });
            }
        });
    }

    private void fill(LinearLayout holder, List<EduAcademic.Course> list, EduAcademic.Node node) {
        holder.removeAllViews();
        if (list.isEmpty()) {
            holder.addView(courseNote("这个课组还没有课程记录。"));
            return;
        }
        for (EduAcademic.Course course : list) holder.addView(courseRow(course, node.depth));
    }

    /**
     * One course: name, then its score (or the status word when there is none yet), then credit.
     * A passed course is the only one drawn in full contrast — the rest are context, not results.
     */
    private LinearLayout courseRow(EduAcademic.Course course, int groupDepth) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(14) * Math.max(0, groupDepth - 2) + dp(10), dp(4), 0, dp(4));

        TextView name = new TextView(this);
        name.setText(course.name);
        name.setTextSize(12);
        name.setTextColor(course.passed() ? AppUi.INK : AppUi.MUTED);
        name.setMaxLines(2);
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        String mark = course.score.isEmpty() ? course.statusLabel() : course.score;
        if (!mark.isEmpty()) {
            TextView score = new TextView(this);
            score.setText(mark);
            score.setTextSize(12);
            score.setTextColor(course.passed() ? AppUi.ACCENT : AppUi.MUTED);
            score.setPadding(dp(8), 0, dp(8), 0);
            line.addView(score);
        }
        TextView credit = new TextView(this);
        credit.setText(course.credit + " 学分");
        credit.setTextSize(11);
        credit.setTextColor(AppUi.MUTED);
        line.addView(credit);
        return line;
    }

    private TextView courseNote(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(AppUi.MUTED);
        view.setPadding(dp(14), dp(6), 0, dp(6));
        return view;
    }

    /** "24.5 / 45" — earned over required, or just earned when the school set no requirement. */
    private TextView credit(EduAcademic.Node node, int size) {
        TextView view = new TextView(this);
        view.setText(node.earned + " / " + (node.required.isEmpty() ? "—" : node.required));
        view.setTextSize(size);
        view.setTextColor(AppUi.MUTED);
        return view;
    }

    private boolean positive(String value) {
        try { return Float.parseFloat(value) > 0; } catch (RuntimeException notANumber) { return false; }
    }

    /** Headline card: GPA, earned/required credits and the course counts school reported. */
    private void headCard(EduAcademic.Report report) {
        LinearLayout card = card();
        String root = "";
        for (EduAcademic.Node node : report.nodes) {
            if (node.depth <= 1) { root = node.name; break; }
        }
        TextView label = new TextView(this);
        label.setText(root.isEmpty() ? "课程修读情况" : root + " · 课程修读情况");
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

        // Earned / required credits of the top group (主修): the only root the school gives a
        // requirement for. Matched by shape rather than by the name, so a rename cannot hide it.
        String earned = "", required = "";
        for (EduAcademic.Node node : report.nodes) {
            if (node.depth <= 1 && positive(node.required)) {
                earned = node.earned; required = node.required; break;
            }
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

        // Two rows of three: the school reports six counts, and six in one row would squeeze the
        // labels off. 未通过 and 计划外 belong here too — they are part of "how am I doing".
        card.addView(statRow("计划课程", report.plan, "通过", report.passed, "在读", report.studying));
        card.addView(statRow("未修", report.unread, "未通过", report.failed, "计划外通过", report.extra));
        addCard(card);
    }

    private LinearLayout statRow(String l1, String v1, String l2, String v2, String l3, String v3) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        stat(row, l1, v1);
        stat(row, l2, v2);
        stat(row, l3, v3);
        return row;
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

    /**
     * A horizontal credit bar. Both sides are scaled by 100 so half-credit courses still show
     * (24.5 / 45 must not round to 25 / 45); a zero requirement means "nothing to fill", and the
     * caller hides the bar rather than drawing a permanently empty one.
     */
    private ProgressBar bar(String earned, String required) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        int max = 0, have = 0;
        try { have = Math.round(Float.parseFloat(earned) * 100); } catch (RuntimeException ignored) { }
        try { max = Math.round(Float.parseFloat(required) * 100); } catch (RuntimeException ignored) { }
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
