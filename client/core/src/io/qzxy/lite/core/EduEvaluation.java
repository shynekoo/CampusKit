package io.qzxy.lite.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生评价（评教）列表：把学校「学生评价」查询返回的列表 JSON 读成可渲染的一份报告。
 *
 * <p>The school's 正方 module answers its jqGrid query with one flat object per 教学班: which class,
 * which course, which teacher, which 学时类型 (理论/实验/实践) and — the point of the screen — in
 * which state the student left it ({@code tjzt}: {@code -1} 未评, {@code 0} 暂存, {@code 1} 已提交).
 * The reply carries ids the display form needs verbatim, so they are kept as strings and never
 * re-derived; a row without a 教学班 id cannot be opened, so it is dropped rather than shown as a
 * dead entry.</p>
 *
 * <p>Nothing here performs a request and nothing scores anything: the page only reports state, and
 * the evaluation itself stays on the school's own form. Parsing is plain string work, so the whole
 * extraction is unit-tested on the JVM.</p>
 */
public final class EduEvaluation {
    /** A term holds a few dozen 教学班; the caps only guard against a surprising reply. */
    public static final int MAX_JSON = 2_000_000;
    public static final int MAX_ROWS = 400;
    private static final int MAX_TEXT = 80;

    /** 未评 — the student has not opened it yet. */
    public static final int UNEVALUATED = -1;
    /** 暂存 — scores saved but not submitted. */
    public static final int SAVED = 0;
    /** 已提交 — submitted; the school no longer accepts changes. */
    public static final int SUBMITTED = 1;
    /** A state word the school added later; shown as-is instead of guessed at. */
    public static final int UNKNOWN = 2;

    /** One 教学班 row: what is taught, by whom, and how far the evaluation got. */
    public static final class Item {
        /** 教学班 id — the display form is keyed by it; empty rows are dropped. */
        public final String classId;
        public final String courseId;
        public final String teacherId;
        /** 学时类型代码 (01 理论 / 02 实验 / 04 实践), passed back to the form unchanged. */
        public final String studentType;
        /** 评价模板 id, empty when the school serves the current template by default. */
        public final String modelId;
        /** 是否成绩录入教师, echoed by the form; kept as the school's own flag. */
        public final String gradeEntry;
        public final String course;
        public final String teacher;
        public final String className;
        public final String department;
        public final String type;
        /** The school's own state word (未评 / 暂存 / 已提交). */
        public final String statusWord;
        /** The school's own state code, kept for the form POST. */
        public final String statusCode;
        public final String score;
        public final int state;

        Item(Map<String, String> raw) {
            classId = text(raw.get("jxb_id"));
            courseId = text(raw.get("kch_id"));
            teacherId = text(raw.get("jgh_id"));
            studentType = text(raw.get("xsdm"));
            modelId = text(raw.get("pjmbmcb_id"));
            gradeEntry = text(raw.get("sfcjlrjs"));
            course = text(raw.get("kcmc"));
            teacher = text(raw.get("jzgmc"));
            className = text(raw.get("jxbmc"));
            department = text(raw.get("jgmc"));
            type = text(raw.get("xsmc"));
            statusWord = text(raw.get("tjztmc"));
            statusCode = text(raw.get("tjzt"));
            score = text(raw.get("bfzpf"));
            state = stateOf(statusCode);
        }

        public boolean done() { return state == SUBMITTED; }
        public boolean saved() { return state == SAVED; }
        public boolean pending() { return state == UNEVALUATED; }

        /** The state as a word: the school's own when it gave one, otherwise the code's meaning. */
        public String label() {
            if (!statusWord.isEmpty()) return statusWord;
            switch (state) {
                case UNEVALUATED: return "未评";
                case SAVED: return "暂存";
                case SUBMITTED: return "已提交";
                default: return statusCode.isEmpty() ? "—" : statusCode;
            }
        }

        /** Course · 教学班 · 学时类型, on one line under the course name. */
        public String detail() {
            StringBuilder out = new StringBuilder();
            if (!className.isEmpty()) out.append(className);
            if (!type.isEmpty()) {
                if (out.length() > 0) out.append(" · ");
                out.append(type);
            }
            return out.toString();
        }
    }

    /** The list plus the counts the screen shows above it. */
    public static final class Report {
        public final List<Item> items;
        Report(List<Item> items) { this.items = items; }
        public int size() { return items.size(); }

        public List<Item> withState(int wanted) {
            List<Item> out = new ArrayList<Item>();
            for (Item item : items) if (item.state == wanted) out.add(item);
            return out;
        }
    }

    /** The school's own totals for the term: 暂存 / 已提交 / 未评. */
    public static final class Counts {
        public final int saved, submitted, pending;
        /** A hint the school adds when something is still missing (e.g. 评教后需评课). */
        public final String note;
        Counts(int saved, int submitted, int pending, String note) {
            this.saved = saved; this.submitted = submitted; this.pending = pending;
            this.note = note;
        }
        public boolean empty() { return saved == 0 && submitted == 0 && pending == 0; }
    }

    private EduEvaluation() { }

    /** Reads the list query's reply. A reply without {@code items} is an empty term, not an error. */
    public static Report parse(String json) {
        String body = body(json);
        List<Item> items = new ArrayList<Item>();
        int list = body.indexOf("\"items\"");
        if (list < 0) return new Report(items);
        int start = body.indexOf('[', list);
        if (start < 0) return new Report(items);
        int at = body.indexOf('{', start);
        while (at > start && items.size() < MAX_ROWS) {
            int end = objectEnd(body, at);
            if (end < 0) break;
            Item item = new Item(members(body, at + 1, end));
            if (!item.classId.isEmpty()) items.add(item);
            at = body.indexOf('{', end + 1);
        }
        return new Report(items);
    }

    /** Reads {@code xspj_cxXspjqk}'s flat totals; a missing key counts as zero. */
    public static Counts counts(String json) {
        String body = body(json);
        return new Counts(number(body, "bcgs"), number(body, "tjgs"), number(body, "wpgs"),
            text(value(body, "pjtsxx")));
    }

    /** The school's state code as one of the constants above. */
    public static int stateOf(String code) {
        String value = code == null ? "" : code.trim();
        if ("-1".equals(value)) return UNEVALUATED;
        if ("0".equals(value)) return SAVED;
        if ("1".equals(value)) return SUBMITTED;
        return UNKNOWN;
    }

    // ---- a very small JSON reader ---------------------------------------------------------
    //
    // Only what this module's replies need: a flat object per row plus a flat totals object.
    // Nested objects inside a row are skipped rather than descended into.

    private static String body(String json) {
        if (json == null) throw new IllegalArgumentException("评价数据为空");
        String body = json.trim();
        if (body.isEmpty()) throw new IllegalArgumentException("评价数据为空");
        if (body.length() > MAX_JSON) throw new IllegalArgumentException("评价数据过大");
        if (body.charAt(0) != '{') throw new IllegalArgumentException("评价数据不是对象");
        return body;
    }

    /** The flat {@code "key": "value"} members between two offsets; strings already unescaped. */
    private static Map<String, String> members(String body, int from, int to) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        int i = from;
        while (i < to) {
            if (body.charAt(i) != '"') { i++; continue; }
            int keyEnd = stringEnd(body, i);
            if (keyEnd < 0 || keyEnd >= to) break;
            String key = unescape(body.substring(i + 1, keyEnd));
            int colon = skipSpace(body, keyEnd + 1, to);
            if (colon >= to || body.charAt(colon) != ':') { i = keyEnd + 1; continue; }
            int at = skipSpace(body, colon + 1, to);
            if (at >= to) break;
            char first = body.charAt(at);
            if (first == '"') {
                int valueEnd = stringEnd(body, at);
                if (valueEnd < 0) break;
                out.put(key, unescape(body.substring(at + 1, valueEnd)));
                i = valueEnd + 1;
            } else if (first == '{' || first == '[') {
                // A nested value is not needed by this module: skip it whole, keep the row usable.
                int end = first == '{' ? objectEnd(body, at) : arrayEnd(body, at);
                if (end < 0) break;
                out.put(key, "");
                i = end + 1;
            } else {
                int end = at;
                while (end < to && body.charAt(end) != ',' && body.charAt(end) != '}') end++;
                out.put(key, scalar(body.substring(at, end)));
                i = end;
            }
        }
        return out;
    }

    /** One scalar member of the whole reply; "" when absent. */
    private static String value(String body, String key) {
        int end = objectEnd(body, 0);
        String found = members(body, 1, end < 0 ? body.length() : end).get(key);
        return found == null ? "" : found;
    }

    private static int number(String body, String key) {
        String raw = value(body, key);
        if (raw == null || raw.isEmpty()) return 0;
        try { return Math.max(0, Integer.parseInt(raw.trim())); }
        catch (NumberFormatException notANumber) { return 0; }
    }

    private static String scalar(String raw) {
        String text = raw.trim();
        if ("null".equals(text) || "true".equals(text) || "false".equals(text)) return "";
        return text;
    }

    private static int skipSpace(String body, int from, int to) {
        int at = from;
        while (at < to && Character.isWhitespace(body.charAt(at))) at++;
        return at;
    }

    /** Offset of the quote that closes the string opened at {@code start}. */
    private static int stringEnd(String body, int start) {
        int i = start + 1;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\\') { i += 2; continue; }
            if (c == '"') return i;
            i++;
        }
        return -1;
    }

    /** Offset of the brace that closes the object opened at {@code start}, or -1. */
    private static int objectEnd(String body, int start) {
        int depth = 0;
        int i = start;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '"') {
                int end = stringEnd(body, i);
                if (end < 0) return -1;
                i = end + 1;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return -1;
    }

    /** Offset of the bracket that closes the array opened at {@code start}, or -1. */
    private static int arrayEnd(String body, int start) {
        int depth = 0;
        int i = start;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '"') {
                int end = stringEnd(body, i);
                if (end < 0) return -1;
                i = end + 1;
                continue;
            }
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return -1;
    }

    /** JSON escapes as the page sends them; only the ones that show up in Chinese course names. */
    private static String unescape(String raw) {
        if (raw.indexOf('\\') < 0) return raw;
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) { out.append(c); continue; }
            char next = raw.charAt(++i);
            switch (next) {
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'u':
                    if (i + 4 < raw.length()) {
                        try {
                            out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException broken) { out.append(next); }
                    } else out.append(next);
                    break;
                default: out.append(next);
            }
        }
        return out.toString();
    }

    private static String text(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }
}
