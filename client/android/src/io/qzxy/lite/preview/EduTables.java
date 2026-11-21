// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Strict reader for the JSON produced by {@code res/raw/edu_tables.js}.
 *
 * <p>Every bound is enforced here rather than in the page: a mis-detected page, a print view with
 * thousands of rows, or a page that echoes the whole semester must not be able to stall the UI.</p>
 */
final class EduTables {
    static final int MAX_JSON = 400_000, MAX_TABLES = 8, MAX_ROWS = 400, MAX_COLS = 24, MAX_CELL = 120;

    static final class Table {
        String caption = "";
        final List<String> headers = new ArrayList<>();
        final List<String[]> rows = new ArrayList<>();
    }

    static final class Page {
        String url = "", title = "";
        final List<Table> tables = new ArrayList<>();
    }

    /** Handoff for the results screen; keeps large payloads out of a Binder transaction. */
    static volatile String pending;

    private EduTables() { }

    static Page parse(String json) throws JSONException {
        if (json == null || json.isEmpty()) throw new JSONException("没有读到页面内容");
        if (json.length() > MAX_JSON) throw new JSONException("页面表格过大，请缩小查询范围后重试");
        JSONObject root = new JSONObject(json);
        String error = root.optString("error", "");
        if (!error.isEmpty()) throw new JSONException(error);
        Page page = new Page();
        page.url = root.optString("url", "");
        page.title = root.optString("title", "");
        JSONArray tables = root.optJSONArray("tables");
        if (tables == null || tables.length() == 0) throw new JSONException("这一页没有可读取的表格");
        for (int i = 0; i < tables.length() && page.tables.size() < MAX_TABLES; i++) {
            JSONObject raw = tables.optJSONObject(i);
            if (raw == null) continue;
            Table table = new Table();
            table.caption = cell(raw.optString("caption", ""));
            JSONArray headers = raw.optJSONArray("headers");
            if (headers != null) {
                for (int c = 0; c < headers.length() && c < MAX_COLS; c++) table.headers.add(cell(headers.optString(c, "")));
            }
            JSONArray rows = raw.optJSONArray("rows");
            if (rows == null) continue;
            for (int r = 0; r < rows.length() && table.rows.size() < MAX_ROWS; r++) {
                JSONArray row = rows.optJSONArray(r);
                if (row == null) continue;
                String[] cells = new String[Math.min(row.length(), MAX_COLS)];
                boolean any = false;
                for (int c = 0; c < cells.length; c++) {
                    cells[c] = cell(row.optString(c, ""));
                    any |= !cells[c].isEmpty();
                }
                if (any) table.rows.add(cells);
            }
            if (table.rows.isEmpty()) continue;
            page.tables.add(table);
        }
        if (page.tables.isEmpty()) throw new JSONException("这一页没有可读取的表格");
        return page;
    }

    private static String cell(String value) {
        if (value == null) return "";
        String text = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").replaceAll("\\s+", " ").trim();
        return text.length() > MAX_CELL ? text.substring(0, MAX_CELL) : text;
    }
}
