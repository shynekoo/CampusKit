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
 * periods the user picked. Changing any filter re-queries by itself. Everything is read-only.</p>
 *
 * <p>The saved account is used to sign in for the query and the session is closed when this screen
 * goes away, so the account is not left signed in on the school's side.</p>
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
    private boolean busy, needsLogin, afterLogin, afterAccount, handoff, loggedOut;
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
        TextView signOut = action("退出登录", this::signOut);
        signOut.setContentDescription("立即退出教务系统的登录状态");
        loginBar.addView(signOut, new LinearLayout.LayoutParams(0, -2, 1));
        screen.root.addView(loginBar);

        results = paper();
        screen.note("账号密码只加密存在这台设备。每次查询时临时登录教务系统，离开这个页面就自动退出。")
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
                EduVault vault = new EduVault(getApplicationContext());
                EduVault.Credential credential = vault.load();
                EduClient.remember(credential == null ? "" : credential.user,
                    credential == null ? "" : credential.password,
                    credential != null && credential.refused);
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
                refuse();
                post(ticket, () -> fail(wrong.getMessage()));
            } catch (EduClient.CaptchaNeeded captcha) {
                post(ticket, () -> fail(captcha.getMessage()));
            } catch (Exception failure) {
                post(ticket, () -> fail(message(failure)));
            }
        });
    }

    /** Remembers that the school said no, so the next launch does not spend another attempt. */
    private void refuse() {
        try { new EduVault(getApplicationContext()).refused(true); } catch (RuntimeException ignored) { }
    }

    /**
     * Campus the screen opens on: 科信 first, because that is where these students are — the page's
     * own default has been the other campus often enough to be worth overriding.
     */
    private String choose(EduClient.Page loaded) {
        for (String[] option : loaded.campuses) {
            if (option[1].contains("科信")) return option[0];
        }
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
        handoff = true;
        startActivity(new Intent(this, EduLoginActivity.class));
    }

    private void openAccount() {
        afterAccount = true;
        handoff = true;
        startActivity(new Intent(this, EduAccountActivity.class));
    }

    /** Manual sign-out: the next query signs in again by itself. */
    private void signOut() {
        EduClient.signOutLater();
        status.setText("已退出教务登录。下次查询会重新登录一次。");
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
        if (loggedOut) {
            loggedOut = false;
            EduClient.reopen();
            EduClient.capture();
        }
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
        ensure("type", "");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addCell(row, "周次", weekLabel(pick.get("week")), () ->
            ask("周次", weekLabels(), weekValues(), "week"));
        addCell(row, "星期", weekdayLabel(pick.get("weekday")), () ->
            ask("星期", WEEKDAYS, weekdayValues(), "weekday"));
        fields.addView(row);
        row = new LinearLayout(this);
        addCell(row, "节次", sectionLabel(), this::openSections);
        addCell(row, "教学楼", buildingLabel(pick.get("building")), () ->
            ask("教学楼", buildingLabels(), buildingValues(), "building"));
        fields.addView(row);
        row = new LinearLayout(this);
        addCell(row, "场地类型", typeLabel(pick.get("type")), () ->
            ask("场地类型", typeLabels(), typeValues(), "type"));
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

    /** Periods are checkboxes, not a range: 1-2 and 5-6 in one search is a normal ask. */
    private void openSections() {
        if (campus == null || campus.periods.isEmpty()) return;
        final java.util.List<Integer> all = campus.periods;
        int[] chosen = periods();
        String[] labels = new String[all.size()];
        final boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            int number = all.get(i);
            String time = time(number);
            labels[i] = "第 " + number + " 节" + (time.isEmpty() ? "" : "  " + time);
            for (int value : chosen) if (value == number) checked[i] = true;
        }
        new AlertDialog.Builder(this)
            .setTitle("节次（可多选）")
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("确定", (dialog, which) -> {
                StringBuilder value = new StringBuilder();
                for (int i = 0; i < all.size(); i++) {
                    if (!checked[i]) continue;
                    if (value.length() > 0) value.append(',');
                    value.append(all.get(i));
                }
                if (value.length() == 0) { status.setText("至少要选一节课。"); return; }
                String previous = pick.get("section");
                pick.put("section", value.toString());
                buildFields();
                if (previous == null || !previous.equals(value.toString())) query();
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
                refuse();
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
                    refuse();
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
        final int[] chosen = periods();
        final String mask = io.qzxy.lite.core.EduSite.periodMask(
            chosen.length == 0 ? new int[]{1, 2} : chosen);
        worker.execute(() -> {
            try {
                List<EduClient.Room> rooms = EduClient.rooms(page, campusId, building, type, week, weekday, mask);
                post(ticket, () -> showRooms(rooms, week, weekday, chosen));
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
        boolean everyType = where.equals("全部教室");
        String when = "第 " + week + " 周 · " + WEEKDAYS[Math.max(0, Math.min(6, weekday - 1))]
            + " · " + io.qzxy.lite.core.EduSite.sectionLabel(section);
        if (rooms.isEmpty()) {
            status.setText(when + " 没有空教室。");
            results.addView(hint("这个时间没有查到空教室，换个节次或教学楼再看看。"));
            return;
        }
        status.setText(when + " · " + rooms.size() + " 间空教室");
        for (EduClient.Room room : rooms) {
            StringBuilder detail = new StringBuilder();
            if (!room.building.isEmpty()) detail.append(room.building);
            String kind = everyType ? room.type : where;
            if (!kind.isEmpty()) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append(kind);
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
        if (campus == null) return "1,2";
        List<int[]> pairs = io.qzxy.lite.core.EduSite.pairs(campus.periods);
        if (pairs.isEmpty()) return "1,2";
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 18) return join(pairs.get(pairs.size() - 1));
        if (hour >= 12) return pairs.size() > 2 ? join(pairs.get(2)) : join(pairs.get(pairs.size() - 1));
        return join(pairs.get(0));
    }

    private static String join(int[] pair) { return pair[0] + "," + pair[1]; }

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

    private int[] periods() { return io.qzxy.lite.core.EduSite.sections(pick.get("section")); }

    private String sectionLabel() {
        int[] chosen = periods();
        String label = io.qzxy.lite.core.EduSite.sectionLabel(chosen);
        if (chosen.length == 0) return label;
        boolean contiguous = chosen[chosen.length - 1] - chosen[0] + 1 == chosen.length;
        if (!contiguous) return label;
        String times = times(chosen[0], chosen[chosen.length - 1]);
        return times.isEmpty() ? label : label + " " + times;
    }

    private String times(int from, int to) {
        if (campus == null) return "";
        String first = time(from), last = time(to);
        if (first.isEmpty() && last.isEmpty()) return "";
        return from == to ? first : first + "-" + last;
    }

    private String time(int section) {
        if (campus == null) return "";
        for (String line : campus.periodTimes.split("\n")) {
            String[] parts = line.trim().split(" ", 2);
            if (parts.length == 2 && parts[0].equals(Integer.toString(section))) return parts[1];
        }
        return "";
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

    /** An empty value means the school searches every room type, which is the default here. */
    private String typeLabel(String value) {
        if (value == null || value.isEmpty()) return "全部教室";
        if (page != null) for (String[] option : page.types) if (option[0].equals(value)) return option[1];
        return value;
    }

    private String[] typeLabels() {
        List<String> out = new ArrayList<>();
        out.add("全部教室");
        if (page != null) for (String[] option : page.types) {
            if (option[0].isEmpty()) continue;
            out.add(option[1]);
        }
        return out.toArray(new String[0]);
    }

    private String[] typeValues() {
        List<String> out = new ArrayList<>();
        out.add("");
        if (page != null) for (String[] option : page.types) {
            if (option[0].isEmpty()) continue;
            out.add(option[0]);
        }
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

    private static int today() {
        return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
    }

    private static int number(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (RuntimeException invalid) { return fallback; }
    }

    private void post(int ticket, Runnable task) {
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing() && ticket == generation) task.run(); });
    }

    /**
     * Leaving the screen ends the school session: the account is only signed in for the query, not
     * left open in the background where a shared phone or a long idle session could pick it up.
     */
    @Override protected void onStop() {
        super.onStop();
        if (handoff) { handoff = false; return; }
        EduClient.signOutLater();
        loggedOut = true;
    }

    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
