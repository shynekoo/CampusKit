package io.qzxy.lite.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 学生评价问卷：把学校服务端渲染的评价表单片段读成可以直接摆出来的题目，并能把作答还原成
 * 学校要求的那一串请求参数。
 *
 * <p>The school's evaluation form is a fragment of server-rendered HTML, not JSON. Its own script
 * ({@code xspj_display.js}) reads it back out of the DOM with a handful of selectors, and those
 * selectors are the contract this parser follows: one {@code div.panel-pjdx} per 评价模板, one
 * {@code table.table-xspj} per 一级指标, one {@code tr.tr-xspj} per 评价项, each row carrying the ids
 * on {@code data-*} attributes; the options are {@code input.radio-pjf} (single) /
 * {@code input[type=checkbox]} (multi) / {@code select[name=pjf]} (十级制) each wrapped in a
 * {@code div.input-xspj} whose text is the visible label; free text is {@code textarea.input-zgpj}
 * and the 评语 box is {@code textarea[name=py]}.</p>
 *
 * <p>Two things this class deliberately does not do. It never invents a question: a row whose markup
 * is not one of those shapes is reported as {@link #UNSUPPORTED} so the screen can say "这一项要在
 * 学校页面上填" instead of drawing a control that posts the wrong parameter. And it never guesses a
 * score: {@link #body} copies exactly what the reader picked, and sends nothing for a question they
 * left alone.</p>
 */
public final class EduEvaluationForm {
    /** A fragment is a few dozen kilobytes; the cap only guards against a surprising reply. */
    public static final int MAX_HTML = 2_000_000;
    private static final int MAX_QUESTIONS = 300;

    /** Pick one of {@link #options}. */
    public static final int CHOICE = 1;
    /** Pick any of {@link #options} (dtlx 3). */
    public static final int MULTI = 2;
    /** Type something: a 主观题 textarea, or a score field. */
    public static final int TEXT = 3;
    /** The school rendered a control this parser does not recognise; it must not be faked. */
    public static final int UNSUPPORTED = 0;

    /** Where a choice goes in the request: the 评分等级项目 id, or the raw score of a 十级制. */
    public static final String FIELD_OPTION = "pfdjdmxmb_id";
    public static final String FIELD_SCORE = "pjf";
    public static final String FIELD_TEXT = "zgpj";

    /** One selectable answer: the id (or score) that goes back to the school, and its label. */
    public static final class Option {
        public final String value;
        public final String label;
        public final boolean checked;
        Option(String value, String label, boolean checked) {
            this.value = value; this.label = label; this.checked = checked;
        }
    }

    /** One 评价项: what is asked, and what shape of answer it takes. */
    public static final class Question {
        public final String itemId;
        public final String levelId;
        public final String title;
        public final int kind;
        /** The request field a choice/value lands in; empty for {@link #UNSUPPORTED}. */
        public final String field;
        public final boolean required;
        public final boolean readOnly;
        public final List<Option> options = new ArrayList<Option>();
        /** Group and child index, exactly as the school's own request numbers them. */
        public final int group, child;

        Question(String itemId, String levelId, String title, int kind, String field,
                 boolean required, boolean readOnly, int group, int child) {
            this.itemId = itemId; this.levelId = levelId; this.title = title;
            this.kind = kind; this.field = field;
            this.required = required; this.readOnly = readOnly;
            this.group = group; this.child = child;
        }

        public boolean supported() { return kind != UNSUPPORTED; }
        public boolean answered(Answer answer) {
            if (kind == MULTI) return !answer.picks.isEmpty();
            if (kind == CHOICE) return !answer.pick.isEmpty();
            if (kind == TEXT) return !answer.text.trim().isEmpty();
            return answer.answered();
        }
    }

    /** One 评价模板 — the school usually ships one per 评价对象 (课程 / 教师 / 助教). */
    public static final class Panel {
        public final String modelId, modelName, dxdm, fxzgf, xspfbId;
        /** Questions grouped by 一级指标 table, so the request indices stay the school's own. */
        public final List<List<Question>> groups = new ArrayList<List<Question>>();
        /** Whether the panel offers a 评语 box. */
        public boolean comment;
        Panel(String modelId, String modelName, String dxdm, String fxzgf, String xspfbId) {
            this.modelId = modelId; this.modelName = modelName; this.dxdm = dxdm;
            this.fxzgf = fxzgf; this.xspfbId = xspfbId;
        }
        public String name() { return modelName.isEmpty() ? "评价" : modelName; }
        public int count() {
            int total = 0;
            for (List<Question> group : groups) total += group.size();
            return total;
        }
    }

    /** The whole form, plus the ids the request must echo. */
    public static final class Form {
        public final String classId, courseId, teacherId, studentType;
        public final String ztpjbl, jszdpjbl, xykzpjbl;
        public final List<Panel> panels = new ArrayList<Panel>();
        Form(String classId, String courseId, String teacherId, String studentType,
             String ztpjbl, String jszdpjbl, String xykzpjbl) {
            this.classId = classId; this.courseId = courseId; this.teacherId = teacherId;
            this.studentType = studentType;
            this.ztpjbl = ztpjbl; this.jszdpjbl = jszdpjbl; this.xykzpjbl = xykzpjbl;
        }
        public List<Question> questions() {
            List<Question> out = new ArrayList<Question>();
            for (Panel panel : panels) for (List<Question> group : panel.groups) out.addAll(group);
            return out;
        }
        /** How many items the school actually asked — the number the screen reports. */
        public int count() {
            int total = 0;
            for (Panel panel : panels) total += panel.count();
            return total;
        }
        /** A form with no 评价项 is not usable: the school has not opened this one. */
        public boolean empty() { return count() == 0; }
        /** One blank answer per question, in the same order {@link #questions()} reports them. */
        public List<Answer> answers() {
            List<Answer> out = new ArrayList<Answer>();
            for (int i = count(); i > 0; i--) out.add(new Answer());
            return out;
        }
    }

    /** What the reader picked for one question. Nothing is pre-filled from the school's opinion. */
    public static final class Answer {
        public String pick = "";
        public final List<String> picks = new ArrayList<String>();
        public String text = "";
        public void toggle(String value) {
            if (picks.contains(value)) picks.remove(value); else picks.add(value);
        }
        public boolean answered() {
            return !pick.isEmpty() || !picks.isEmpty() || !text.trim().isEmpty();
        }
    }

    private EduEvaluationForm() { }

    /**
     * Reads a form fragment.
     *
     * @throws IllegalArgumentException when the school sent no fragment, or one without a single
     *         recognised 评价项 — the caller turns that into a plain-language message.
     */
    public static Form parse(String html) {
        String source = html == null ? "" : html.trim();
        if (source.isEmpty()) throw new IllegalArgumentException("学校没有返回评价表单。");
        if (source.length() > MAX_HTML) throw new IllegalArgumentException("评价表单过大。");
        String text = clean(source);

        String classId = "", courseId = "", teacherId = "", type = "";
        String ztpjbl = "", jszdpjbl = "", xykzpjbl = "";
        List<int[]> bodies = blocks(text, "div", "xspj-body");
        if (!bodies.isEmpty()) {
            int[] body = bodies.get(0);
            String tag = text.substring(body[0], body[1]);
            classId = attr(tag, "data-jxb_id");
            courseId = attr(tag, "data-kch_id");
            teacherId = attr(tag, "data-jgh_id");
            type = attr(tag, "data-xsdm");
            ztpjbl = attr(tag, "data-ztpjbl");
            jszdpjbl = attr(tag, "data-jszdpjbl");
            xykzpjbl = attr(tag, "data-xykzpjbl");
        }

        Form form = new Form(classId, courseId, teacherId, type, ztpjbl, jszdpjbl, xykzpjbl);
        for (int[] panel : blocks(text, "div", "panel-pjdx")) {
            if (form.count() >= MAX_QUESTIONS) break;
            form.panels.add(panel(text.substring(panel[0], panel[1]), text.substring(panel[1], panel[2])));
        }
        if (form.panels.isEmpty()) {
            String words = excerpt(text);
            throw new IllegalArgumentException(words.isEmpty()
                ? "学校返回的表单里没有评价内容，这门课可能还没开放评教。"
                : "学校没有下发评价项：" + words);
        }
        return form;
    }

    /**
     * The request body the school's own 暂存 / 提交 button would send.
     *
     * <p>Same field names, same order, same indices: {@code modelList[i].xspjList[j].childXspjList[k]}
     * where {@code i} is the panel, {@code j} the 一级指标 table and {@code k} the row inside it.
     * An unanswered question contributes nothing, which is what the school's script does too.</p>
     */
    public static String body(Form form, List<Answer> answers, List<String> comments, boolean submit) {
        Body out = new Body();
        out.add("ztpjbl", form.ztpjbl);
        out.add("jszdpjbl", form.jszdpjbl);
        out.add("xykzpjbl", form.xykzpjbl);
        out.add("jxb_id", form.classId);
        out.add("kch_id", form.courseId);
        out.add("jgh_id", form.teacherId);
        out.add("xsdm", form.studentType);
        out.add("tjzt", submit ? "1" : "0");

        int index = 0;
        for (int i = 0; i < form.panels.size(); i++) {
            Panel panel = form.panels.get(i);
            List<Question> flat = new ArrayList<Question>();
            for (List<Question> group : panel.groups) flat.addAll(group);
            if (panel.modelId.isEmpty()) { index += flat.size(); continue; }

            String key = "modelList[" + i + "].";
            out.add(key + "pjmbmcb_id", panel.modelId);
            out.add(key + "pjmbmc", panel.modelName);
            out.add(key + "pjdxdm", panel.dxdm);
            out.add(key + "fxzgf", panel.fxzgf);
            // The school's script percent-encodes the 评语 itself and then lets the form encoder
            // encode it again, so the body carries it twice-encoded. Copied on purpose: the server
            // decodes twice for this field only, and a single-encoded 评语 comes back garbled.
            String comment = i < comments.size() && comments.get(i) != null ? comments.get(i) : "";
            out.add(key + "py", comment.isEmpty() ? "" : jsEncode(comment));
            out.add(key + "xspfb_id", panel.xspfbId);

            int g = -1;
            for (List<Question> group : panel.groups) {
                g++;
                int c = -1;
                for (Question question : group) {
                    c++;
                    Answer answer = index < answers.size() ? answers.get(index) : new Answer();
                    index++;
                    if (!question.supported()) continue;
                    String prefix = key + "xspjList[" + g + "].childXspjList[" + c + "].";
                    if (question.kind == MULTI) {
                        for (String value : answer.picks) out.add(prefix + question.field, value);
                    } else if (question.kind == CHOICE) {
                        if (!answer.pick.isEmpty()) out.add(prefix + question.field, answer.pick);
                    } else {
                        String value = answer.text.trim();
                        if (!value.isEmpty()) out.add(prefix + question.field, value);
                    }
                }
            }
        }
        return out.encoded();
    }

    // ---- what the school said back ---------------------------------------------------------

    /** The school answers 暂存/提交 with a sentence, not a status code. */
    public static final int OK = 1;
    public static final int FAILED = 2;
    public static final int UNCLEAR = 3;

    /** Classifies that sentence. Only the school's own words are grounds for calling it success. */
    public static int outcome(String reply) {
        String text = reply == null ? "" : reply;
        if (text.contains("整体提交成功") || text.contains("成功")) return OK;
        if (text.contains("失败") || text.contains("不能") || text.contains("请先")) return FAILED;
        return UNCLEAR;
    }

    /** The school's own sentence, tags stripped and shortened, safe to put on screen. */
    public static String note(String reply) {
        String words = excerpt(clean(reply == null ? "" : reply));
        if (words.length() > 120) words = words.substring(0, 120) + "…";
        return words;
    }

    // ---- html reading ---------------------------------------------------------------------

    /** Keeps [tagStart, contentStart, contentEnd) for every element of {@code name} carrying {@code token}. */
    private static List<int[]> blocks(String source, String name, String token) {
        List<int[]> out = new ArrayList<int[]>();
        int at = 0;
        while (at < source.length()) {
            int lt = source.indexOf('<', at);
            if (lt < 0) break;
            if (!opens(source, lt, name)) { at = lt + 1; continue; }
            int end = tagEnd(source, lt);
            if (end < 0) break;
            String tag = source.substring(lt, end);
            if (token == null || hasClass(tag, token)) {
                int close = closeOf(source, lt, name);
                out.add(new int[]{lt, end, close});
                at = close;
            } else at = end;
        }
        return out;
    }

    private static Panel panel(String tag, String body) {
        Panel panel = new Panel(attr(tag, "data-pjmbmcb_id"), attr(tag, "data-pjmbmc"),
            attr(tag, "data-pjdxdm"), attr(tag, "data-fxzgf"), attr(tag, "data-xspfb_id"));
        panel.comment = body.contains("name=\"py\"") || body.contains("name='py'");

        List<int[]> tables = blocks(body, "table", "table-xspj");
        if (tables.isEmpty()) {
            List<Question> rows = rows(body, 0);
            if (!rows.isEmpty()) panel.groups.add(rows);
        } else {
            for (int g = 0; g < tables.size(); g++) {
                int[] table = tables.get(g);
                panel.groups.add(rows(body.substring(table[1], table[2]), g));
            }
        }
        return panel;
    }

    private static List<Question> rows(String html, int group) {
        List<Question> out = new ArrayList<Question>();
        for (int[] row : blocks(html, "tr", "tr-xspj")) {
            String tag = html.substring(row[0], row[1]);
            String body = html.substring(row[1], row[2]);
            String title = cell(html.substring(row[1], row[2]));
            int child = out.size();
            boolean required = "1".equals(attr(tag, "data-sfbt"));
            out.add(question(attr(tag, "data-pjzbxm_id"), attr(tag, "data-pfdjdmb_id"),
                title, required, body, group, child));
        }
        return out;
    }

    /**
     * Reads one row's control.
     *
     * <p>The order matters: a row can carry several inputs at once (a 十级制 row ships both the
     * select and the star widget), so the most specific shape wins rather than the first tag seen.</p>
     */
    private static Question question(String itemId, String levelId, String title, boolean required,
                                     String body, int group, int child) {
        List<int[]> optionBlocks = blocks(body, "div", "input-xspj");

        // 1. radio — one 评分等级 per option, wrapped in div.input-xspj.
        List<Option> radios = options(body, optionBlocks, "radio");
        if (!radios.isEmpty())
            return filled(itemId, levelId, title, CHOICE, FIELD_OPTION, required, false, group, child, radios);

        // 2. checkbox — dtlx 3 multi-select.
        List<Option> boxes = options(body, optionBlocks, "checkbox");
        if (!boxes.isEmpty())
            return filled(itemId, levelId, title, MULTI, FIELD_OPTION, required, false, group, child, boxes);

        // 3. select[name=pjf] — 十级制 sends the raw score, not a 等级 id.
        String select = element(body, "select", "pjf");
        if (!select.isEmpty()) {
            List<Option> values = selected(select);
            if (!values.isEmpty())
                return filled(itemId, levelId, title, CHOICE, FIELD_SCORE, required, false, group, child, values);
        }

        // 4. textarea — 主观题. Matched by class, since that is how the school's script finds it.
        int[] area = elementWithClass(body, "textarea", "input-zgpj");
        if (area == null) area = elementWithClass(body, "textarea", null);
        if (area != null) {
            String tag = body.substring(area[0], area[1]);
            return new Question(itemId, levelId, title, TEXT, FIELD_TEXT,
                required, contains(tag, "readonly"), group, child);
        }

        // 5. a bare score field (range slider or number input).
        int[] score = scoreField(body);
        if (score != null) {
            Question question = new Question(itemId, levelId, title, TEXT, FIELD_SCORE,
                required, contains(body.substring(score[0], score[1]), "readonly"), group, child);
            return question;
        }
        return new Question(itemId, levelId, title, UNSUPPORTED, "", required, false, group, child);
    }

    private static Question filled(String itemId, String levelId, String title, int kind, String field,
                                   boolean required, boolean readOnly, int group, int child,
                                   List<Option> options) {
        Question question = new Question(itemId, levelId, title, kind, field, required, readOnly, group, child);
        question.options.addAll(options);
        return question;
    }

    /** Every {@code input[type=…]} inside the row, labelled by the {@code div.input-xspj} around it. */
    private static List<Option> options(String body, List<int[]> wrappers, String type) {
        List<Option> out = new ArrayList<Option>();
        int at = 0;
        while (at < body.length()) {
            int lt = body.indexOf('<', at);
            if (lt < 0) break;
            if (!opens(body, lt, "input")) { at = lt + 1; continue; }
            int end = tagEnd(body, lt);
            if (end < 0) break;
            String tag = body.substring(lt, end);
            at = end;
            if (!type.equals(attr(tag, "type"))) continue;
            String value = attr(tag, "data-pfdjdmxmb_id");
            if (value.isEmpty()) value = attr(tag, "value");
            if (value.isEmpty()) continue;
            String label = "";
            for (int[] wrapper : wrappers) {
                if (lt >= wrapper[1] && lt < wrapper[2]) {
                    label = text(body.substring(wrapper[1], wrapper[2]));
                    break;
                }
            }
            if (label.isEmpty()) label = attr(tag, "title");
            if (label.isEmpty()) label = value;
            out.add(new Option(value, label, contains(tag, "checked")));
        }
        return out;
    }

    /** {@code <option value="10" selected>10 分</option>} as a choice list. */
    private static List<Option> selected(String selectBody) {
        List<Option> out = new ArrayList<Option>();
        int at = 0;
        while (at < selectBody.length()) {
            int lt = selectBody.indexOf('<', at);
            if (lt < 0) break;
            if (!opens(selectBody, lt, "option")) { at = lt + 1; continue; }
            int end = tagEnd(selectBody, lt);
            if (end < 0) break;
            String tag = selectBody.substring(lt, end);
            int close = closeOf(selectBody, lt, "option");
            String label = text(selectBody.substring(end, close));
            at = close;
            String value = attr(tag, "value");
            if (value.isEmpty()) continue;   // a placeholder row ("请选择") is not an answer
            out.add(new Option(value, label.isEmpty() ? value : label, contains(tag, "selected")));
        }
        return out;
    }

    /** The first {@code <name>} whose {@code name=} attribute matches, as its inner html. */
    private static String element(String body, String name, String field) {
        int at = 0;
        while (at < body.length()) {
            int lt = body.indexOf('<', at);
            if (lt < 0) return "";
            if (!opens(body, lt, name)) { at = lt + 1; continue; }
            int end = tagEnd(body, lt);
            if (end < 0) return "";
            String tag = body.substring(lt, end);
            if (field == null || field.equals(attr(tag, "name"))) {
                int close = closeOf(body, lt, name);
                return body.substring(end, close);
            }
            at = end;
        }
        return "";
    }

    /** The tag bounds of the first {@code <name>} carrying {@code token} as a class. */
    private static int[] elementWithClass(String body, String name, String token) {
        int at = 0;
        while (at < body.length()) {
            int lt = body.indexOf('<', at);
            if (lt < 0) return null;
            if (!opens(body, lt, name)) { at = lt + 1; continue; }
            int end = tagEnd(body, lt);
            if (end < 0) return null;
            if (token == null || hasClass(body.substring(lt, end), token)) return new int[]{lt, end};
            at = end;
        }
        return null;
    }

    /** The score control of a row: {@code input.range-slider__range} or a plain number input. */
    private static int[] scoreField(String body) {
        int at = 0;
        while (at < body.length()) {
            int lt = body.indexOf('<', at);
            if (lt < 0) return null;
            if (!opens(body, lt, "input")) { at = lt + 1; continue; }
            int end = tagEnd(body, lt);
            if (end < 0) return null;
            String tag = body.substring(lt, end);
            at = end;
            String type = attr(tag, "type");
            boolean numeric = "range".equals(type) || "number".equals(type);
            boolean named = hasClass(tag, "range-slider__range") || hasClass(tag, "form-control");
            if (numeric || named) return new int[]{lt, end};
        }
        return null;
    }

    /** The text of the row's first cell, which is where the school puts the 评价项 name. */
    private static String cell(String row) {
        List<int[]> cells = blocks(row, "td", null);
        if (cells.isEmpty()) return "";
        String title = text(row.substring(cells.get(0)[1], cells.get(0)[2]));
        // The school marks a 必填项 with a red "*" inside the title cell. The screen draws its own
        // marker from data-sfbt, so the stray star is dropped rather than shown twice.
        while (title.startsWith("*")) title = title.substring(1).trim();
        return title;
    }

    // ---- primitives -----------------------------------------------------------------------

    private static String clean(String html) {
        String source = html;
        int guard = 0;
        while (guard++ < 200) {
            int at = indexOfIgnoreCase(source, "<script");
            if (at < 0) break;
            int end = indexOfIgnoreCase(source, "</script", at);
            if (end < 0) { source = source.substring(0, at); break; }
            int close = source.indexOf('>', end);
            source = source.substring(0, at) + (close < 0 ? "" : source.substring(close + 1));
        }
        guard = 0;
        while (guard++ < 200) {
            int at = indexOfIgnoreCase(source, "<style");
            if (at < 0) break;
            int end = indexOfIgnoreCase(source, "</style", at);
            if (end < 0) { source = source.substring(0, at); break; }
            int close = source.indexOf('>', end);
            source = source.substring(0, at) + (close < 0 ? "" : source.substring(close + 1));
        }
        guard = 0;
        while (guard++ < 200) {
            int at = source.indexOf("<!--");
            if (at < 0) break;
            int end = source.indexOf("-->", at);
            if (end < 0) { source = source.substring(0, at); break; }
            source = source.substring(0, at) + source.substring(end + 3);
        }
        return source;
    }

    private static int indexOfIgnoreCase(String source, String needle) {
        return indexOfIgnoreCase(source, needle, 0);
    }

    private static int indexOfIgnoreCase(String source, String needle, int from) {
        return source.toLowerCase(java.util.Locale.ROOT).indexOf(needle, Math.max(0, from));
    }

    /** True when {@code <} at {@code lt} opens {@code name} (and not a longer tag). */
    private static boolean opens(String source, int lt, String name) {
        if (lt + 1 + name.length() > source.length()) return false;
        if (source.charAt(lt) != '<') return false;
        if (!source.regionMatches(true, lt + 1, name, 0, name.length())) return false;
        int after = lt + 1 + name.length();
        if (after >= source.length()) return false;
        char c = source.charAt(after);
        return Character.isWhitespace(c) || c == '>' || c == '/';
    }

    /** Offset just past the {@code >} closing the tag opened at {@code lt}, honouring quotes. */
    private static int tagEnd(String source, int lt) {
        char quote = 0;
        for (int i = lt + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) { if (c == quote) quote = 0; continue; }
            if (c == '"' || c == '\'') { quote = c; continue; }
            if (c == '>') return i + 1;
        }
        return -1;
    }

    /** Offset of the {@code </name} that closes the element opened at {@code open}. */
    private static int closeOf(String source, int open, String name) {
        int depth = 0;
        int at = open;
        while (at < source.length()) {
            int lt = source.indexOf('<', at);
            if (lt < 0) return source.length();
            if (source.regionMatches(true, lt + 1, "/" + name, 0, name.length() + 1)) {
                depth--;
                if (depth == 0) return lt;
                int end = tagEnd(source, lt);
                at = end < 0 ? source.length() : end;
                continue;
            }
            if (opens(source, lt, name)) depth++;
            int end = tagEnd(source, lt);
            at = end < 0 ? source.length() : end;
        }
        return source.length();
    }

    private static String attr(String tag, String name) {
        int at = 0;
        while (at < tag.length()) {
            int hit = tag.indexOf(name, at);
            if (hit < 0) return "";
            boolean startOk = hit == 0 || Character.isWhitespace(tag.charAt(hit - 1));
            int after = hit + name.length();
            int cursor = after;
            while (cursor < tag.length() && Character.isWhitespace(tag.charAt(cursor))) cursor++;
            if (!startOk || cursor >= tag.length() || tag.charAt(cursor) != '=') {
                at = after;
                continue;
            }
            cursor++;
            while (cursor < tag.length() && Character.isWhitespace(tag.charAt(cursor))) cursor++;
            if (cursor >= tag.length()) return "";
            char quote = tag.charAt(cursor);
            if (quote == '"' || quote == '\'') {
                int end = tag.indexOf(quote, cursor + 1);
                return end < 0 ? "" : tag.substring(cursor + 1, end);
            }
            int end = cursor;
            while (end < tag.length() && !Character.isWhitespace(tag.charAt(end)) && tag.charAt(end) != '>') end++;
            return tag.substring(cursor, end);
        }
        return "";
    }

    private static boolean hasClass(String tag, String token) {
        String classes = attr(tag, "class");
        if (classes.isEmpty()) return false;
        int at = 0;
        while (at < classes.length()) {
            while (at < classes.length() && Character.isWhitespace(classes.charAt(at))) at++;
            int start = at;
            while (at < classes.length() && !Character.isWhitespace(classes.charAt(at))) at++;
            if (start < at && classes.substring(start, at).equals(token)) return true;
        }
        return false;
    }

    /** True when the tag carries {@code attribute} at all (value irrelevant), e.g. {@code readonly}. */
    private static boolean contains(String source, String attribute) {
        int at = 0;
        while (at < source.length()) {
            int hit = source.toLowerCase(java.util.Locale.ROOT).indexOf(attribute, at);
            if (hit < 0) return false;
            boolean startOk = hit == 0 || !Character.isLetterOrDigit(source.charAt(hit - 1));
            int after = hit + attribute.length();
            boolean endOk = after >= source.length() || !Character.isLetterOrDigit(source.charAt(after));
            if (startOk && endOk) return true;
            at = after;
        }
        return false;
    }

    private static String text(String html) {
        StringBuilder out = new StringBuilder(html.length());
        int at = 0;
        while (at < html.length()) {
            char c = html.charAt(at);
            if (c == '<') {
                int end = tagEnd(html, at);
                if (end < 0) break;
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') out.append(' ');
                at = end;
                continue;
            }
            out.append(c);
            at++;
        }
        return collapse(decode(out.toString()));
    }

    private static String collapse(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean space = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!space && out.length() > 0) out.append(' ');
                space = true;
                continue;
            }
            space = false;
            out.append(c);
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') end--;
        return out.substring(0, end);
    }

    private static String decode(String value) {
        if (value.indexOf('&') < 0) return value;
        String out = value;
        out = out.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                 .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                 .replace("&apos;", "'");
        return out;
    }

    /** A short, tag-free version of whatever the school sent, for an error line. */
    private static String excerpt(String html) {
        StringBuilder out = new StringBuilder(html.length());
        boolean space = false;
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '<') {
                int end = tagEnd(html, i);
                if (end < 0) break;
                space = true;
                i = end - 1;
                continue;
            }
            if (Character.isWhitespace(c)) { space = true; continue; }
            if (space && out.length() > 0) out.append(' ');
            space = false;
            out.append(c);
            if (out.length() >= 160) break;
        }
        return collapse(decode(out.toString())).trim();
    }

    /** The exact escaping {@code encodeURIComponent} produces, which is what the 评语 field expects. */
    private static String jsEncode(String value) {
        StringBuilder out = new StringBuilder(value.length() * 3);
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte raw : bytes) {
            int b = raw & 0xff;
            char c = (char) b;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_' || c == '.' || c == '!' || c == '~' || c == '*'
                || c == '\'' || c == '(' || c == ')') {
                out.append(c);
            } else {
                out.append('%');
                String hex = Integer.toHexString(b).toUpperCase(java.util.Locale.ROOT);
                if (hex.length() < 2) out.append('0');
                out.append(hex);
            }
        }
        return out.toString();
    }

    /** Ordered, repeatable form fields: a checkbox question contributes one field per pick. */
    private static final class Body {
        private final List<String[]> fields = new ArrayList<String[]>();
        void add(String name, String value) { fields.add(new String[]{name, value}); }
        String encoded() {
            StringBuilder out = new StringBuilder();
            for (String[] field : fields) {
                if (out.length() > 0) out.append('&');
                out.append(urlencode(field[0])).append('=').append(urlencode(field[1]));
            }
            return out.toString();
        }
        private String urlencode(String value) {
            try {
                return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
            } catch (java.io.UnsupportedEncodingException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }
}
