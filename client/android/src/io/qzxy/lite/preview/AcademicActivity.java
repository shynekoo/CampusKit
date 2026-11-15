package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Grades and empty classrooms straight from the school's own query interface.
 *
 * <p>No web page to click through: the screen asks the academic system for the current term, its
 * buildings, its period times and its real week calendar, then queries with the week, weekday and
 * period the user picked. Changing any filter re-queries by itself. Everything is read-only.</p>
 *
 * <p>Signing in still happens in the school's own page inside the app, so the password is typed once
 * into the school's form and is never stored or seen here.</p>
 */
public final class AcademicActivity extends Activity {
    private static final String[] WEEKDAYS = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, String> pick = new LinkedHashMap<>();
    private DeviceScreen screen;
    private LinearLayout fields, results;
    private TextView status;
    private String mode = "rooms";
    private EduClient.Page page;
    private EduClient.Campus campus;
    private EduClient.Semester semester;
    private int generation;
    private boolean busy, needsLogin, afterLogin, afterAccount;
    private final android.os.Handler watchdog = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Nothing should be able to leave the progress bar running forever. */
    private void watch(int ticket) {
        watchdog.removeCallbacksAndMessages(null);
        watchdog.postDelayed(() -> {
            if (ticket != generation || !busy) return;
            busy = false;
            screen.loading(false);
            status.setText("读取超时了。教学系统可能没响应，请检查网络后点右上角重新读取。");
            results.removeAllViews();
            results.addView(action("重试", this::reload));
        }, 45000);
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        mode = "grades".equals(getIntent().getStringExtra("mode")) ? "grades" : "rooms";
        screen = new DeviceScreen(this, "grades".equals(mode) ? "成绩" : "空教室");
        screen.icon("↻", "重新读取", this::reload);

        fields = screen.card;
        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(AppUi.MUTED);
        status.setPadding(dp(2), 0, dp(2), dp(10));
        status.setText("正在读取教务系统…");
        screen.root.addView(status);

        LinearLayout loginBar = new LinearLayout(this);
        loginBar.setOrientation(LinearLayout.HORIZONTAL);
        TextView account = action("教务账号", this::openAccount);
        account.setContentDescription("输入并保存教务系统学号和密码，之后自动登录");
        loginBar.addView(account, new LinearLayout.LayoutParams(0, -2, 1));
        TextView webLogin = action("网页登录", this::openLogin);
        webLogin.setContentDescription("打开教务系统登录页，手动登录一次");
        loginBar.addView(webLogin, new LinearLayout.LayoutParams(0, -2, 1));
        screen.root.addView(loginBar);

        results = paper();
        screen.note("数据来自学校教务系统。账号密码只加密保存在这台设备，仅用于登录；不想保存也可以用网页登录。")
            .setOnClickListener(v -> startActivity(new Intent(this, EduQueryActivity.class).putExtra("mode", mode)));
        load();
    }

    private LinearLayout paper() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(6), dp(16), dp(6));
        card.setBackground(AppUi.shape(this, 0xffffffff, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        screen.root.addView(card, params);
        return card;
    }

    private void reload() {
        if (busy) return;
        fields.removeAllViews();
        results.removeAllViews();
        load();
    }

    // ---- loading -------------------------------------------------------------------------

    private void load() {
        if (busy) return;
        EduClient.capture();
        busy = true;
        screen.loading(true);
        status.setText("正在读取教务系统…");
        final int ticket = ++generation;
        watch(ticket);
        worker.execute(() -> {
            try {
                EduVault.Credential credential = new EduVault(getApplicationContext()).load();
                EduClient.remember(credential == null ? "" : credential.user,
                    credential == null ? "" : credential.password);
                if ("grades".equals(mode)) {
                    EduClient.Semester loaded = EduClient.semesters();
                    post(ticket, () -> { busy = false; semester = loaded; buildFields(); query(); });
                } else {
                    EduClient.Page loadedPage = EduClient.page();
                    String campusId = choose(loadedPage);
                    EduClient.Campus loadedCampus = EduClient.campus(loadedPage.year, loadedPage.term, campusId);
                    post(ticket, () -> {
                        busy = false;
                        page = loadedPage;
                        campus = loadedCampus;
                        pick.put("campus", campusId);
                        buildFields();
                        query();
                    });
                }
            } catch (EduClient.NotSignedIn expired) {
                post(ticket, this::askLogin);
            } catch (EduClient.BadPassword wrong) {
                post(ticket, () -> fail(wrong.getMessage()));
            } catch (EduClient.CaptchaNeeded captcha) {
                post(ticket, () -> fail(captcha.getMessage()));
            } catch (Exception failure) {
                post(ticket, () -> fail(message(failure)));
            }
        });
    }

    private String choose(EduClient.Page loaded) {
        if (!loaded.campus.isEmpty()) return loaded.campus;
        return loaded.campuses.isEmpty() ? "" : loaded.campuses.get(0)[0];
    }

    private void askLogin() {
        busy = false;
        needsLogin = true;
        screen.loading(false);
        status.setText("还没登录教务系统。点上面的「教务账号」把学号和密码存一次，以后会自动登录；也可以点「网页登录」手动登一次。");
        results.removeAllViews();
        fields.removeAllViews();
    }

    private void openLogin() {
        afterLogin = true;
        startActivity(new Intent(this, EduLoginActivity.class));
    }

    private void openAccount() {
        afterAccount = true;
        startActivity(new Intent(this, EduAccountActivity.class));
    }

    private void fail(String text) {
        busy = false;
        screen.loading(false);
        status.setText(text);
        results.removeAllViews();
        results.addView(action("重试", this::reload));
    }

    private static String message(Exception failure) {
        return EduClient.describe(failure);
    }

    @Override protected void onResume() {
        super.onResume();
        if (needsLogin || afterLogin || afterAccount) {
            needsLogin = false;
            afterLogin = false;
            afterAccount = false;
            busy = false;
            reload();
        }
    }

    // ---- filters -------------------------------------------------------------------------

    private void buildFields() {
        fields.removeAllViews();
        if ("grades".equals(mode)) {
            buildSemesterFields();
            return;
        }
        ensure("week", defaultWeek());
        ensure("weekday", Integer.toString(today()));
        ensure("section", defaultSection());
        ensure("building", "");
        ensure("type", page == null ? "" : page.type);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addCell(row, "周次", weekLabel(pick.get("week")), () ->
            ask("周次", weekLabels(), weekValues(), "week"));
        addCell(row, "星期", weekdayLabel(pick.get("weekday")), () ->
            ask("星期", WEEKDAYS, weekdayValues(), "weekday"));
        fields.addView(row);
        row = new LinearLayout(this);
        addCell(row, "节次", sectionLabel(pick.get("section")), () ->
            ask("节次", sectionLabels(), sectionValues(), "section"));
        addCell(row, "教学楼", buildingLabel(pick.get("building")), () ->
            ask("教学楼", buildingLabels(), buildingValues(), "building"));
        fields.addView(row);
        row = new LinearLayout(this);
        addCell(row, "场地类别", typeLabel(pick.get("type")), () ->
            ask("场地类别", typeLabels(), typeValues(), "type"));
        if (page != null && page.campuses.size() > 1) {
            addCell(row, "校区", campusLabel(pick.get("campus")), () ->
                ask("校区", campusLabels(), campusValues(), "campus"));
        }
        fields.addView(row);
    }

    private void buildSemesterFields() {
        ensure("year", semester == null ? "" : semester.year);
        ensure("term", semester == null ? "" : semester.term);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addCell(row, "学年", label(semester == null ? null : semester.years, pick.get("year")), () ->
            ask("学年", labels(semester.years), values(semester.years), "year"));
        addCell(row, "学期", label(semester == null ? null : semester.terms, pick.get("term")), () ->
            ask("学期", labels(semester.terms), values(semester.terms), "term"));
        fields.addView(row);
    }

    private void ensure(String key, String value) {
        String current = pick.get(key);
        if (current == null || current.isEmpty()) pick.put(key, value == null ? "" : value);
    }

    private void addCell(LinearLayout row, String label, String value, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(6), dp(8), dp(6), dp(8));
        cell.setMinimumHeight(dp(56));
        AppUi.press(cell, 0x00ffffff, 10);
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(11);
        name.setTextColor(AppUi.MUTED);
        cell.addView(name);
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(15);
        text.setTypeface(null, Typeface.BOLD);
        text.setTextColor(AppUi.INK);
        text.setPadding(0, dp(2), 0, 0);
        cell.addView(text);
        cell.setContentDescription(label + "：" + value);
        cell.setOnClickListener(v -> action.run());
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void ask(String title, String[] labels, String[] values, String key) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels, (dialog, which) -> {
                String previous = pick.get(key);
                pick.put(key, values[which]);
                if (previous != null && previous.equals(values[which])) return;
                if ("campus".equals(key)) reloadCampus();
                else { buildFields(); query(); }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** Choosing another campus changes its buildings and week calendar, so they are re-read. */
    private void reloadCampus() {
        if (page == null) return;
        EduClient.capture();
        busy = true;
        screen.loading(true);
        status.setText("正在读取该校区…");
        final int ticket = ++generation;
        watch(ticket);
        final String campusId = pick.get("campus");
        worker.execute(() -> {
            try {
                EduClient.Campus loaded = EduClient.campus(page.year, page.term, campusId);
                post(ticket, () -> {
                    busy = false;
                    campus = loaded;
                    pick.remove("building");
                    buildFields();
                    query();
                });
            } catch (EduClient.BadPassword wrong) {
                post(ticket, () -> fail(wrong.getMessage()));
            } catch (EduClient.CaptchaNeeded captcha) {
                post(ticket, () -> fail(captcha.getMessage()));
            } catch (Exception failure) {
                post(ticket, () -> fail(message(failure)));
            }
        });
    }

    // ---- query ---------------------------------------------------------------------------

    private void query() {
        if (busy) return;
        EduClient.capture();
        busy = true;
        screen.loading(true);
        status.setText("正在查询…");
        final int ticket = ++generation;
        watch(ticket);
        if ("grades".equals(mode)) {
            final String year = pick.get("year"), term = pick.get("term");
            worker.execute(() -> {
                try {
                    List<EduClient.Course> courses = EduClient.grades(year, term);
                    post(ticket, () -> showGrades(courses));
                } catch (EduClient.NotSignedIn expired) {
                    post(ticket, this::askLogin);
                } catch (EduClient.BadPassword wrong) {
                    post(ticket, () -> fail(wrong.getMessage()));
                } catch (EduClient.CaptchaNeeded captcha) {
                    post(ticket, () -> fail(captcha.getMessage()));
                } catch (Exception failure) {
                    post(ticket, () -> fail(message(failure)));
                }
            });
            return;
        }
        if (campus == null) { busy = false; screen.loading(false); return; }
        final String campusId = pick.get("campus"), building = pick.get("building"), type = pick.get("type");
        final int week = number(pick.get("week"), 1), weekday = number(pick.get("weekday"), today());
        final int[] section = section(pick.get("section"));
        worker.execute(() -> {
            try {
                List<EduClient.Room> rooms = EduClient.rooms(page, campusId, building, type, week, weekday, section[0], section[1]);
                post(ticket, () -> showRooms(rooms, week, weekday, section));
            } catch (EduClient.NotSignedIn expired) {
                post(ticket, this::askLogin);
            } catch (Exception failure) {
                post(ticket, () -> fail(message(failure)));
            }
        });
    }

    private void showRooms(List<EduClient.Room> rooms, int week, int weekday, int[] section) {
        busy = false;
        needsLogin = false;
        screen.loading(false);
        results.removeAllViews();
        String where = typeLabel(pick.get("type"));
        String when = "第 " + week + " 周 · " + WEEKDAYS[Math.max(0, Math.min(6, weekday - 1))]
            + " · " + sectionText(section);
        if (rooms.isEmpty()) {
            status.setText(when + " 没有空教室。");
            results.addView(hint("这个时间没有查到空教室，换个节次或教学楼再看看。"));
            return;
        }
        status.setText(when + " · " + rooms.size() + " 间空教室");
        for (EduClient.Room room : rooms) {
            StringBuilder detail = new StringBuilder();
            if (!room.building.isEmpty()) detail.append(room.building);
            if (!where.isEmpty() && !where.equals("全部")) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append(where);
            }
            if (!room.seats.isEmpty()) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append(room.seats).append(" 座");
            }
            results.addView(line(room.name, detail.toString(), ""));
        }
    }

    private void showGrades(List<EduClient.Course> courses) {
        busy = false;
        needsLogin = false;
        screen.loading(false);
        results.removeAllViews();
        if (courses.isEmpty()) {
            status.setText("这个学期还没有成绩。");
            results.addView(hint("学校没有返回成绩。可能这学期还没出分。"));
            return;
        }
        status.setText("已显示 " + courses.size() + " 门课程");
        for (EduClient.Course course : courses) {
            StringBuilder detail = new StringBuilder();
            if (!course.credit.isEmpty()) detail.append("学分 ").append(course.credit);
            if (!course.gpa.isEmpty()) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append("绩点 ").append(course.gpa);
            }
            results.addView(line(course.name, detail.toString(), course.score));
        }
    }

    // ---- small views ---------------------------------------------------------------------

    private TextView hint(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(AppUi.MUTED);
        view.setPadding(0, dp(12), 0, dp(12));
        return view;
    }

    private View line(String title, String detail, String value) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(0, dp(6), 0, dp(6));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(15);
        name.setTextColor(AppUi.INK);
        left.addView(name);
        if (!detail.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(detail);
            sub.setTextSize(12);
            sub.setTextColor(AppUi.MUTED);
            sub.setPadding(0, dp(2), 0, 0);
            left.addView(sub);
        }
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        if (!value.isEmpty()) {
            TextView side = new TextView(this);
            side.setText(value);
            side.setTextSize(17);
            side.setTypeface(null, Typeface.BOLD);
            side.setTextColor(AppUi.ACCENT);
            side.setGravity(Gravity.END);
            row.addView(side);
        }
        wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this);
        divider.setBackgroundColor(AppUi.LINE);
        wrap.addView(divider, new LinearLayout.LayoutParams(-1, 1));
        return wrap;
    }

    private TextView action(String label, Runnable action) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(15);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(AppUi.ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48));
        AppUi.press(view, 0x00ffffff, 10);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    // ---- option labels -------------------------------------------------------------------

    private String defaultWeek() {
        int current = EduClient.currentWeek(campus);
        if (campus != null && campus.weeks.contains(current)) return Integer.toString(current);
        return campus == null || campus.weeks.isEmpty() ? "1" : Integer.toString(campus.weeks.get(0));
    }

    private String defaultSection() {
        if (campus == null) return "1-2";
        List<int[]> pairs = io.qzxy.lite.core.EduSite.pairs(campus.periods);
        if (pairs.isEmpty()) return "1-2";
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 18) return join(pairs.get(pairs.size() - 1));
        if (hour >= 12) return pairs.size() > 2 ? join(pairs.get(2)) : join(pairs.get(pairs.size() - 1));
        return join(pairs.get(0));
    }

    private static String join(int[] pair) { return pair[0] + "-" + pair[1]; }

    private String weekLabel(String value) {
        int week = number(value, 1);
        return "第 " + week + " 周" + (week == EduClient.currentWeek(campus) ? "（本周）" : "");
    }

    private String[] weekLabels() {
        List<String> out = new ArrayList<>();
        if (campus != null) for (Integer week : campus.weeks) {
            out.add("第 " + week + " 周" + (week == EduClient.currentWeek(campus) ? "（本周）" : ""));
        }
        return out.toArray(new String[0]);
    }

    private String weekdayLabel(String value) {
        int day = number(value, today());
        return WEEKDAYS[Math.max(0, Math.min(6, day - 1))];
    }

    private static String[] weekdayValues() {
        String[] out = new String[7];
        for (int i = 0; i < out.length; i++) out[i] = Integer.toString(i + 1);
        return out;
    }

    private String[] weekValues() {
        List<String> out = new ArrayList<>();
        if (campus != null) for (Integer week : campus.weeks) out.add(Integer.toString(week));
        return out.toArray(new String[0]);
    }

    private String sectionLabel(String value) {
        int[] pair = section(value);
        String times = "";
        if (campus != null) times = times(pair[0], pair[1]);
        String label = sectionText(pair);
        return times.isEmpty() ? label : label + " " + times;
    }

    private String sectionText(int[] pair) {
        return pair[0] == pair[1] ? "第 " + pair[0] + " 节" : "第 " + pair[0] + "-" + pair[1] + " 节";
    }

    private String times(int from, int to) {
        if (campus == null) return "";
        String first = time(from), last = time(to);
        if (first.isEmpty() && last.isEmpty()) return "";
        return first + "-" + last;
    }

    private String time(int section) {
        if (campus == null) return "";
        for (String line : campus.periodTimes.split("\n")) {
            String[] parts = line.trim().split(" ", 2);
            if (parts.length == 2 && parts[0].equals(Integer.toString(section))) return parts[1];
        }
        return "";
    }

    private String[] sectionLabels() {
        List<String> out = new ArrayList<>();
        if (campus != null) for (int[] pair : io.qzxy.lite.core.EduSite.pairs(campus.periods)) {
            String label = sectionText(pair);
            String times = times(pair[0], pair[1]);
            out.add(times.isEmpty() ? label : label + " " + times);
        }
        return out.toArray(new String[0]);
    }

    private String[] sectionValues() {
        List<String> out = new ArrayList<>();
        if (campus != null) for (int[] pair : io.qzxy.lite.core.EduSite.pairs(campus.periods)) out.add(join(pair));
        return out.toArray(new String[0]);
    }

    private String buildingLabel(String value) {
        if (value == null || value.isEmpty()) return "全部教学楼";
        for (String[] option : campus == null ? new ArrayList<String[]>() : campus.buildings)
            if (option[0].equals(value)) return option[1];
        return "全部教学楼";
    }

    private String[] buildingLabels() {
        List<String> out = new ArrayList<>();
        out.add("全部教学楼");
        if (campus != null) for (String[] option : campus.buildings) out.add(option[1]);
        return out.toArray(new String[0]);
    }

    private String[] buildingValues() {
        List<String> out = new ArrayList<>();
        out.add("");
        if (campus != null) for (String[] option : campus.buildings) out.add(option[0]);
        return out.toArray(new String[0]);
    }

    private String typeLabel(String value) {
        if (page == null) return "";
        if (value == null || value.isEmpty()) return "全部场地";
        for (String[] option : page.types) if (option[0].equals(value)) return option[1];
        return value;
    }

    private String[] typeLabels() {
        List<String> out = new ArrayList<>();
        if (page == null) return out.toArray(new String[0]);
        for (String[] option : page.types) out.add(option[1]);
        return out.toArray(new String[0]);
    }

    private String[] typeValues() {
        List<String> out = new ArrayList<>();
        if (page == null) return out.toArray(new String[0]);
        for (String[] option : page.types) out.add(option[0]);
        return out.toArray(new String[0]);
    }

    private String campusLabel(String value) {
        if (page != null) for (String[] option : page.campuses) if (option[0].equals(value)) return option[1];
        return value == null ? "" : value;
    }

    private String[] campusLabels() { return labels(page == null ? null : page.campuses); }
    private String[] campusValues() { return values(page == null ? null : page.campuses); }

    private static String[] labels(List<String[]> options) {
        List<String> out = new ArrayList<>();
        if (options != null) for (String[] option : options) out.add(option[1]);
        return out.toArray(new String[0]);
    }

    private static String[] values(List<String[]> options) {
        List<String> out = new ArrayList<>();
        if (options != null) for (String[] option : options) out.add(option[0]);
        return out.toArray(new String[0]);
    }

    private static String label(List<String[]> options, String value) {
        if (options != null) for (String[] option : options) if (option[0].equals(value)) return option[1];
        return value == null ? "" : value;
    }

    private static int[] section(String value) {
        if (value == null || value.isEmpty()) return new int[]{1, 2};
        String[] parts = value.split("-");
        int from = number(parts[0], 1);
        int to = parts.length > 1 ? number(parts[1], from) : from;
        return new int[]{from, to};
    }

    private static int today() {
        return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
    }

    private static int number(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (RuntimeException invalid) { return fallback; }
    }

    private void post(int ticket, Runnable task) {
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing() && ticket == generation) task.run(); });
    }

    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
