package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Timetable import without a browser: pick a term, then sign in with the saved academic account and
 * read that term's timetable straight from the school's query endpoint.
 *
 * <p>The result goes back to {@link TimetableActivity} as the same course JSON the manual web import
 * produces, so validation is identical. A failed automatic attempt leaves the existing timetable
 * untouched and offers the old web flow as a fallback.</p>
 */
public final class EduTimetableImportActivity extends Page {
    private static final int WEB_REQUEST = 71;
    private static final int ACCOUNT_REQUEST = 72;

    /** The saved account is missing or was rejected: the account screen must come first. */
    private static final class AccountNeeded extends Exception {
        AccountNeeded(String text) { super(text); }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DeviceScreen screen;
    private TextView yearView, termView, status, account;
    private Button go;
    private final List<String[]> years = new ArrayList<>(), terms = new ArrayList<>();
    private int yearIndex, termIndex, generation;
    private boolean busy, pendingImport;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        screen = new DeviceScreen(this, "从教务系统导入课表");
        final String table = TimetableStore.currentName(this);
        TextView help = screen.text(screen.card,
            "选好学年和学期，点下面的按钮：用已保存的教务账号自动登录并抓取该学期课表，直接写入当前课表「"
            + table + "」。", 14);
        help.setTextColor(AppUi.MUTED);

        LinearLayout row = new LinearLayout(this);
        yearView = cell(row, "学年", () -> pick(true));
        termView = cell(row, "学期", () -> pick(false));
        screen.card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        status = screen.text(screen.card, "", 13);
        status.setTextColor(AppUi.MUTED);
        status.setText("正在检查教务账号…");

        // The same entry the grades screen keeps on screen: save the account here, or replace it.
        account = new TextView(this);
        account.setTextSize(13);
        account.setTextColor(AppUi.ACCENT);
        account.setPadding(dp(2), 0, dp(2), dp(8));
        account.setText("教务账号（学号和密码）→");
        account.setOnClickListener(v -> { if (!busy) openAccount(false); });
        screen.card.addView(account);

        go = screen.primary("自动导入该学期课表", this::startImport);

        Button web = new Button(this);
        web.setText("改用网页手动导入");
        web.setAllCaps(false);
        web.setTextSize(14);
        web.setTextColor(AppUi.ACCENT);
        web.setBackgroundColor(0x00ffffff);
        web.setOnClickListener(v -> startActivityForResult(new Intent(this, TimetableWebActivity.class), WEB_REQUEST));
        screen.root.addView(web, new LinearLayout.LayoutParams(-1, dp(44)));
        screen.note("导入会替换当前课表「" + table + "」，写入后可以撤销。没保存过账号时会先打开「教务账号」让你存一次；学校要求验证码时请用网页手动导入。");

        options();
        probeAccount();
    }

    private TextView cell(LinearLayout row, String label, Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(AppUi.outlined(this, AppUi.SURFACE, 12, AppUi.LINE));
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextSize(12);
        caption.setTextColor(AppUi.MUTED);
        box.addView(caption);
        TextView value = new TextView(this);
        value.setTextSize(17);
        value.setTypeface(null, Typeface.BOLD);
        value.setTextColor(AppUi.INK);
        box.addView(value);
        AppUi.press(box, AppUi.SURFACE, 12);
        box.setOnClickListener(v -> { if (!busy) action.run(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(box, params);
        return value;
    }

    /** Recent years and the term codes this school uses; "全部" is for grades, not timetables. */
    private void options() {
        EduClient.Semester local = EduClient.localSemesters();
        for (String[] option : local.years) if (!"ALL".equals(option[0])) years.add(option);
        for (String[] option : local.terms) if (!"ALL".equals(option[0])) terms.add(option);
        if (years.isEmpty()) years.add(new String[]{local.year, local.year + " 学年"});
        if (terms.isEmpty()) terms.add(new String[]{local.term, "当前学期"});
        yearIndex = indexOf(years, local.year);
        termIndex = indexOf(terms, local.term);
        paint();
    }

    private static int indexOf(List<String[]> options, String value) {
        for (int i = 0; i < options.size(); i++) if (options.get(i)[0].equals(value)) return i;
        return 0;
    }

    private void paint() {
        yearView.setText(years.get(yearIndex)[1]);
        termView.setText(terms.get(termIndex)[1]);
        yearView.setContentDescription("学年：" + years.get(yearIndex)[1]);
        termView.setContentDescription("学期：" + terms.get(termIndex)[1]);
    }

    /** The same bottom sheet and number wheels the timetable itself uses. */
    private void pick(final boolean isYear) {
        final List<String[]> list = isYear ? years : terms;
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) labels[i] = list.get(i)[1];
        LinearLayout body = CoursePickers.column(this);
        LinearLayout row = new LinearLayout(this);
        final NumberPicker wheel = CoursePickers.wheel(this, row, isYear ? "学年" : "学期",
            0, labels.length - 1, isYear ? yearIndex : termIndex, labels);
        body.addView(row);
        final Dialog dialog = CoursePickers.sheet(this, isYear ? "选择学年" : "选择学期", body);
        Button done = CoursePickers.button(this, "确定", true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(16);
        body.addView(done, params);
        done.setOnClickListener(v -> {
            if (isYear) yearIndex = wheel.getValue(); else termIndex = wheel.getValue();
            paint();
            dialog.dismiss();
        });
        dialog.show();
    }

    /**
     * Is there already an account to sign in with? The answer only changes the wording here; the
     * account screen is opened when it is actually needed, so the web import stays one tap away.
     */
    private void probeAccount() {
        final int ticket = generation;
        worker.execute(() -> {
            EduVault.Credential stored = null;
            try { stored = new EduVault(getApplicationContext()).load(); } catch (RuntimeException ignored) { }
            final boolean ready = stored != null && stored.complete() && !stored.refused;
            post(ticket, () -> {
                if (busy) return;
                status.setText(ready
                    ? "已保存教务账号，可以自动登录并导入。"
                    : "还没保存教务账号。点「自动导入」会先打开「教务账号」让你存一次。");
                account.setText(ready ? "更换教务账号 →" : "教务账号（学号和密码）→");
            });
        });
    }

    /** Opens the account screen; {@code thenImport} makes the import start once the account is saved. */
    private void openAccount(boolean thenImport) {
        pendingImport = thenImport;
        startActivityForResult(new Intent(this, EduAccountActivity.class), ACCOUNT_REQUEST);
    }

    private void startImport() {
        if (busy) return;
        busy = true;
        screen.loading(true);
        go.setEnabled(false);
        status.setText("正在自动登录教务系统并读取课表…");
        final String year = years.get(yearIndex)[0], term = terms.get(termIndex)[0];
        final String label = years.get(yearIndex)[1] + " · " + terms.get(termIndex)[1];
        final int ticket = ++generation;
        worker.execute(() -> {
            try {
                EduVault.Credential credential = new EduVault(getApplicationContext()).load();
                if (credential == null || !credential.complete())
                    throw new AccountNeeded("还没保存教务账号，正在打开「教务账号」，保存一次就会自动开始导入。");
                if (credential.refused)
                    throw new AccountNeeded("保存的密码曾被学校拒绝。请重新保存一次，或改用网页手动导入。");
                EduClient.reopen();
                EduClient.remember(credential.user, credential.password, false);
                if (!EduClient.signedIn()) EduClient.signIn();
                String raw = EduClient.timetable(year, term);
                EduTimetableReader.Result parsed = EduTimetableReader.parse(raw);
                TimetableWebReader.parse(parsed.json);   // final check with the shared course parser
                post(ticket, () -> {
                    setResult(RESULT_OK, new Intent()
                        .putExtra("courses", parsed.json)
                        .putExtra("auto", true)
                        .putExtra("term", label)
                        .putExtra("kept", parsed.kept)
                        .putExtra("skipped", parsed.skipped));
                    finish();
                });
            } catch (AccountNeeded need) {
                post(ticket, () -> {
                    busy = false;
                    screen.loading(false);
                    go.setEnabled(true);
                    status.setText(need.getMessage());
                    openAccount(true);
                });
            } catch (Exception failure) {
                final String message = describe(failure);
                post(ticket, () -> {
                    busy = false;
                    screen.loading(false);
                    go.setEnabled(true);
                    status.setText(message);
                });
            }
        });
    }

    private static String describe(Throwable failure) {
        if (failure instanceof IllegalArgumentException || failure instanceof EduClient.BadPassword
            || failure instanceof EduClient.CaptchaNeeded) {
            String text = failure.getMessage();
            return text == null || text.isEmpty() ? "课表读取失败，原课表未修改。" : text;
        }
        String text = EduClient.describe(failure);
        return text + " 原课表未修改；可点下面的按钮改用网页导入。";
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ACCOUNT_REQUEST) {
            final boolean saved = result == RESULT_OK, resume = pendingImport;
            pendingImport = false;
            if (saved) {
                status.setText("账号已保存。");
                account.setText("更换教务账号 →");
                if (resume) startImport();
            } else {
                status.setText("还没保存教务账号。导入前要先在「教务账号」存一次学号和密码。");
            }
            return;
        }
        if (request == WEB_REQUEST && result == RESULT_OK && data != null && data.hasExtra("courses")) {
            // Manual web import keeps its own review-and-confirm dialog back on the timetable screen.
            setResult(RESULT_OK, new Intent().putExtra("courses", data.getStringExtra("courses")).putExtra("auto", false));
            finish();
        }
    }

    @Override protected void onDestroy() {
        generation++;
        worker.shutdown();
        EduClient.release();
        super.onDestroy();
    }

    private void post(int ticket, Runnable task) {
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing() && ticket == generation) task.run(); });
    }
}
