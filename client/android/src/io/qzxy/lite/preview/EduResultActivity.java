package io.qzxy.lite.preview;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.qzxy.lite.core.EduTableColumns;
import java.util.ArrayList;
import java.util.List;

/**
 * Native rendering of whatever the academic system's own page contained.
 *
 * <p>Nothing is inferred: a table with a recognizable 课程/成绩 pair is shown as a score list, and
 * every other table is shown row by row so 空教室 results keep their original columns. When the
 * page could not be identified the screen says so instead of showing an empty list.</p>
 */
public final class EduResultActivity extends Activity {
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String json = EduTables.pending;
        EduTables.Page page;
        try {
            page = EduTables.parse(json);
        } catch (Exception failure) {
            page = null;
            showError(failure.getMessage());
            return;
        }
        DeviceScreen screen = new DeviceScreen(this, page.title.isEmpty() ? "教务查询结果" : page.title);
        TextView source = screen.note(host(page.url));
        source.setPadding(0, dp(2), 0, dp(10));

        int grades = 0, others = 0;
        for (EduTables.Table table : page.tables) {
            int[] columns = EduTableColumns.grade(table.headers);
            if (EduTableColumns.usable(columns)) { gradeCard(screen.root, table, columns); grades++; }
            else { plainCard(screen.root, table); others++; }
        }
        if (grades == 0 && others == 0) screen.note("没有可显示的表格。");
    }

    private void showError(String message) {
        DeviceScreen screen = new DeviceScreen(this, "教务查询结果");
        screen.text(screen.root, message == null || message.isEmpty() ? "没有读到表格" : message, 15);
        screen.note("请在教务页面自行进入查询结果，然后回到上一页点“读取本页”。");
    }

    private static String host(String url) {
        try {
            String value = java.net.URI.create(url).getHost();
            return value == null ? "" : "来源：" + value;
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 16, AppUi.LINE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        parent.addView(card, params);
        return card;
    }

    private TextView title(LinearLayout parent, String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(AppUi.INK);
        view.setPadding(0, 0, 0, dp(6));
        parent.addView(view);
        return view;
    }

    /** One course per row: name on the left, score on the right, credits underneath. */
    private void gradeCard(LinearLayout parent, EduTables.Table table, int[] columns) {
        LinearLayout card = card(parent);
        title(card, table.caption.isEmpty() ? "成绩（" + table.rows.size() + " 门）" : table.caption, 15);
        for (String[] row : table.rows) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, dp(8), 0, dp(8));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this);
            name.setText(cell(row, columns[EduTableColumns.NAME]));
            name.setTextSize(15);
            name.setTextColor(AppUi.INK);
            labels.addView(name);
            String detail = details(row, columns);
            if (!detail.isEmpty()) {
                TextView extra = new TextView(this);
                extra.setText(detail);
                extra.setTextSize(12);
                extra.setTextColor(AppUi.MUTED);
                labels.addView(extra);
            }
            line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

            TextView score = new TextView(this);
            score.setText(cell(row, columns[EduTableColumns.SCORE]));
            score.setTextSize(17);
            score.setTypeface(null, Typeface.BOLD);
            score.setTextColor(AppUi.ACCENT);
            score.setGravity(Gravity.END);
            line.addView(score);
            card.addView(line);
        }
    }

    private String details(String[] row, int[] columns) {
        List<String> parts = new ArrayList<>();
        if (columns[EduTableColumns.CREDIT] >= 0) {
            String credit = cell(row, columns[EduTableColumns.CREDIT]);
            if (!credit.isEmpty()) parts.add("学分 " + credit);
        }
        if (columns[EduTableColumns.GPA] >= 0) {
            String gpa = cell(row, columns[EduTableColumns.GPA]);
            if (!gpa.isEmpty()) parts.add("绩点 " + gpa);
        }
        return join(parts);
    }

    /** Everything that is not a score list keeps its own columns, in order. */
    private void plainCard(LinearLayout parent, EduTables.Table table) {
        LinearLayout card = card(parent);
        title(card, table.caption.isEmpty() ? "查询结果（" + table.rows.size() + " 行）" : table.caption, 15);
        if (!table.headers.isEmpty()) {
            TextView head = new TextView(this);
            head.setText(join(table.headers));
            head.setTextSize(12);
            head.setTypeface(null, Typeface.BOLD);
            head.setTextColor(AppUi.MUTED);
            head.setPadding(0, 0, 0, dp(6));
            card.addView(head);
        }
        int shown = 0;
        for (String[] row : table.rows) {
            if (shown++ >= 200) break;
            TextView line = new TextView(this);
            line.setText(join(java.util.Arrays.asList(row)));
            line.setTextSize(13);
            line.setTextColor(AppUi.INK);
            line.setPadding(0, dp(5), 0, dp(5));
            card.addView(line);
        }
        if (table.rows.size() > 200) {
            TextView tail = new TextView(this);
            tail.setText("仅显示前 200 行，共 " + table.rows.size() + " 行。");
            tail.setTextSize(12);
            tail.setTextColor(AppUi.MUTED);
            card.addView(tail);
        }
    }

    private static String join(List<String> cells) {
        StringBuilder out = new StringBuilder();
        for (String cell : cells) {
            if (cell == null || cell.isEmpty()) continue;
            if (out.length() > 0) out.append("  ·  ");
            out.append(cell);
        }
        return out.toString();
    }

    private static String cell(String[] row, int index) {
        return index >= 0 && index < row.length && row[index] != null ? row[index] : "";
    }
}
