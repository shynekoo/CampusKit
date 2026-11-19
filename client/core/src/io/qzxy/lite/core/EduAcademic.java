package io.qzxy.lite.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the school's 学生学业情况查询 page into a small, renderable report.
 *
 * <p>The 正方 academic site renders this module server-side into an embedded script that builds a
 * credit tree with jQuery: every node is a {@code <p class='title1' ... yxxf='..' yqzdxf='..'
 * sftg='..'>} carrying its earned/required credits, and the node name is a Chinese literal in the
 * adjacent script string. The same node is emitted once per campus branch, so nodes are de-duplicated
 * by their full id. Nothing here performs a request; it only turns the page text into values a
 * native screen can show, with hard caps so a surprising page cannot stall the UI.</p>
 */
public final class EduAcademic {
    /** The page is large (the whole credit tree is inlined), but never unbounded. */
    public static final int MAX_HTML = 4_000_000;
    public static final int MAX_NODES = 120;
    /** A credit group holds at most a few dozen courses; the caps only guard against a bad reply. */
    public static final int MAX_COURSES = 400;
    public static final int MAX_COURSES_JSON = 2_000_000;
    private static final int MAX_NAME = 60;

    /** One credit-group row of the tree: name plus earned/required credits and pass flag. */
    public static final class Node {
        /** The school's own id for this credit group; the course endpoint is keyed by it. */
        public final String id;
        public final String name;
        public final String earned;
        public final String required;
        public final boolean passed;
        /** 1 = top group (主修), 2 = its categories, and so on down the school's own tree. */
        public final int depth;
        /**
         * Which of the school's two course endpoints serves this row.
         *
         * <p>{@code true}: the plan endpoint ({@code cxJxzxjhxfyqKcxx}) — the courses the study
         * plan lists for this group. {@code false}: the non-plan endpoint
         * ({@code cxJxzxjhxfyqFKcxx}) — courses picked outside the plan (通识选修、公共艺术…), whose
         * credits still count towards this group. The split is the school's own rule: its script
         * compares {@code jdkcsx == '1'} and only takes the first branch when that holds.</p>
         *
         * <p>Sending the plan query to a non-plan group returns an empty array, which is exactly
         * the "右边有学分、点开却没有课程" symptom. When the page carries no {@code jdkcsx} at all
         * the old plan query is kept, so an older deployment behaves as before.</p>
         */
        public final boolean planCourses;
        Node(String id, String name, String earned, String required, boolean passed, int depth,
             boolean planCourses) {
            this.id = id; this.name = name; this.earned = earned; this.required = required;
            this.passed = passed; this.depth = depth; this.planCourses = planCourses;
        }
    }

    /**
     * One course inside a credit group, as the group's own endpoint returns it.
     *
     * <p>The school's fields are uneven: a studied course carries 学年/学期/成绩/绩点 and a status of
     * {@code 4}; one still being taken has {@code 1} and no score; one not yet taken has {@code 3}
     * and no year at all. Values arrive as quoted strings or bare numbers, and missing ones are
     * either absent or {@code null} — all of that is normalised here so the screen never has to
     * print "null".</p>
     */
    public static final class Course {
        public final String name;
        public final String code;
        public final String credit;
        public final String score;
        public final String point;
        public final String year;
        public final String term;
        /** 必修 / 限选 / 任选. */
        public final String kind;
        /** The school's 修读状态 code: 1 在读, 3 未修, 4/21 已通过, 2 未过. */
        public final String status;
        Course(String name, String code, String credit, String score, String point,
               String year, String term, String kind, String status) {
            this.name = name; this.code = code; this.credit = credit; this.score = score;
            this.point = point; this.year = year; this.term = term; this.kind = kind;
            this.status = status;
        }

        /** 4 and 21 are both "已通过" on the school's page; everything else is not a pass. */
        public boolean passed() { return "4".equals(status) || "21".equals(status); }

        /** The status word the school shows as an icon tooltip. */
        public String statusLabel() {
            if ("4".equals(status) || "21".equals(status)) return "已通过";
            if ("1".equals(status)) return "在读";
            if ("3".equals(status)) return "未修";
            if ("2".equals(status)) return "未过";
            if ("5".equals(status) || "6".equals(status) || "7".equals(status) || "8".equals(status))
                return "已替代";
            return "";
        }
    }

    /** The whole report: headline statistics and the de-duplicated credit tree. */
    public static final class Report {
        public String name = "";
        public String gpa = "";
        public String plan = "", passed = "", failed = "", unread = "", studying = "", extra = "";
        public final List<Node> nodes = new ArrayList<>();
        /** True when the page carried parent links, i.e. {@link Node#depth} is worth honouring. */
        public boolean layered;
    }

    private static final Pattern NODE = Pattern.compile(
        "<p\\s+class='title1'[^>]*id='p([^']+)'([^>]*)>(.{0,400}?)</p>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** The school puts {@code jdkcsx} (a course-count marker) on the row's {@code <div class='title'>},
     *  keyed there by {@code xfyqjd_id} — the same id the {@code <p>} carries as {@code p<id>}. */
    private static final Pattern TITLE_DIV = Pattern.compile(
        "<div\\s+class='title'([^>]*)>", Pattern.CASE_INSENSITIVE);
    /** The tree is built by jQuery, so a node's parent is the container its {@code <li>} is
     *  appended to: {@code .appendTo($("#ul父ID"))}. Root nodes append to the bare {@code #ul#start}. */
    private static final Pattern LIST_ITEM = Pattern.compile("<li\\s+id='li([^']*)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTACHED_TO = Pattern.compile(
        "\\.appendTo\\(\\s*\\$\\(\"#(?:ul|li)([0-9A-Za-z]*)\"", Pattern.CASE_INSENSITIVE);
    /** How far ahead of an {@code <li>} its {@code appendTo} may sit in the generated script. */
    private static final int ATTACH_WINDOW = 4000;
    /** A sane cap on nesting: the school's tree is ~5 deep, a broken page must not recurse away. */
    private static final int MAX_DEPTH = 12;
    private static final Pattern QUOTED_NAME = Pattern.compile("[\"']([\u4e00-\u9fa5][\u4e00-\u9fa5A-Za-z0-9（）()·]{1,40})[\"']");
    private static final Pattern CHINESE = Pattern.compile("[\u4e00-\u9fa5][\u4e00-\u9fa5A-Za-z0-9（）()·]{1,40}");
    private static final Pattern CREDIT = Pattern.compile("\\d+(?:\\.\\d{1,2})?");
    private static final Pattern I18N = Pattern.compile("\\$\\.i18n\\.(?:get|jwglxt\\[[^\\]]*\\])[^+;]{0,80}");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private EduAcademic() { }

    /**
     * Parses the page. Throws {@link IllegalArgumentException} on an empty or oversized body so a
     * redirect to a login page or an error page never becomes a silently empty screen.
     */
    public static Report parse(String html) {
        if (html == null || html.isEmpty() || html.length() > MAX_HTML)
            throw new IllegalArgumentException("页面内容为空或过大");
        Report report = new Report();
        // 正方 wraps every label in an HTML comment (计划总课程<!-- 计划总课程 -->&nbsp;118). Strip
        // those before matching the statistics, or the number is never adjacent to its label.
        String text = html.replaceAll("(?s)<!--.*?-->", " ");
        report.name = first(text, "同学，您的课程修读情况", "([\u4e00-\u9fa5]{2,6})&nbsp;同学");
        report.gpa = gpa(text);
        report.plan = stat(text, "计划总课程(?:&nbsp;|\\s)*(\\d+)");
        report.failed = stat(text, "未通过(?:&nbsp;|\\s)*(\\d+)");
        report.unread = stat(text, "未修(?:&nbsp;|\\s)*(\\d+)");
        report.studying = stat(text, "在读(?:&nbsp;|\\s)*(\\d+)");
        // "通过" appears three ways on this page: 计划内通过, 未通过, and 计划外：通过. Split at
        // 计划外 and drop the 未通过 figures first, or the last "通过 N 门" silently overwrites the
        // plan's passed count with the extra-curriculum count.
        int extraAt = text.indexOf("计划外");
        String head = extraAt >= 0 ? text.substring(0, extraAt) : text;
        String tail = extraAt >= 0 ? text.substring(extraAt) : "";
        String withoutFailed = head.replaceAll("未通过(?:&nbsp;|\\s)*\\d+", " ");
        report.passed = stat(withoutFailed, "通过(?:&nbsp;|\\s)*(\\d+)");
        report.extra = stat(tail, "通过(?:&nbsp;|\\s)*(\\d+)");
        // Nodes in the order the page first emits them; the same id repeats once per campus
        // branch, so the first complete copy wins.
        Map<String, Node> raw = new LinkedHashMap<String, Node>();
        List<String> order = new ArrayList<String>();
        // The school marks each group with jdkcsx and picks the course endpoint by comparing it to
        // '1'; anything else ("3", "0", empty) is served by the non-plan endpoint. Read it from both
        // the <p> itself and its <div class='title'> parent: deployments put it on either. A page
        // that carries no marker at all keeps the plan endpoint for every row.
        Map<String, String> courseMarks = courseMarks(text);
        boolean marked = !courseMarks.isEmpty();
        Matcher nodes = NODE.matcher(html);
        while (nodes.find() && raw.size() < MAX_NODES) {
            String id = nodes.group(1);
            if (raw.containsKey(id)) continue;
            String attributes = nodes.group(2);
            String name = titleName(clean(nodes.group(3)));
            if (name.isEmpty() || name.equals("同学")) continue;
            String ownMark = attribute(attributes, "jdkcsx").trim();
            boolean ownMarked = Pattern.compile("\\bjdkcsx='[^']*'").matcher(attributes).find();
            if (ownMarked) marked = true;
            String mark = ownMarked ? ownMark : value(courseMarks.get(id));
            raw.put(id, new Node(id, name, credit(attribute(attributes, "yxxf")),
                credit(attribute(attributes, "yqzdxf")),
                "1".equals(attribute(attributes, "sftg").trim()), 1,
                planCourses(id, mark, marked)));
            order.add(id);
        }
        if (!raw.isEmpty()) buildTree(html, raw, order, report);
        if (report.gpa.isEmpty() && report.nodes.isEmpty())
            throw new IllegalArgumentException("没有读到学业情况数据，页面可能改版了");
        return report;
    }

    /**
     * Reads one credit group's courses. That endpoint answers a flat JSON array; this parses the
     * small part of JSON the shape needs (objects of string / number / null members) instead of
     * pulling a JSON library into the platform-independent core, where tests run on a bare JVM.
     *
     * <p>Throws {@link IllegalArgumentException} on an empty, oversized or non-array body so a
     * refused request can never look like "this group has no courses".</p>
     */
    public static List<Course> parseCourses(String json) {
        if (json == null) throw new IllegalArgumentException("课程数据为空");
        String body = json.trim();
        if (body.isEmpty() || body.length() > MAX_COURSES_JSON)
            throw new IllegalArgumentException("课程数据为空或过大");
        if (body.charAt(0) != '[') throw new IllegalArgumentException("课程数据不是列表");
        List<Course> courses = new ArrayList<Course>();
        int at = body.indexOf('{');
        while (at >= 0 && courses.size() < MAX_COURSES) {
            int end = close(body, at);
            if (end < 0) break;
            Course course = course(members(body, at + 1, end));
            if (course != null) courses.add(course);
            at = body.indexOf('{', end + 1);
        }
        return courses;
    }

    /** The flat {@code "key": value} members of one object body, strings already unescaped. */
    private static Map<String, String> members(String body, int from, int to) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        int i = from;
        while (i < to) {
            if (body.charAt(i) != '"') { i++; continue; }
            int keyEnd = stringEnd(body, i);
            if (keyEnd < 0 || keyEnd >= to) break;
            String key = unescape(body.substring(i + 1, keyEnd));
            int at = keyEnd + 1;
            while (at < to && (body.charAt(at) == ':' || body.charAt(at) == ' ')) at++;
            if (at >= to) break;
            if (body.charAt(at) == '"') {
                int end = stringEnd(body, at);
                if (end < 0) break;
                out.put(key, unescape(body.substring(at + 1, end)));
                i = end + 1;
            } else if (body.charAt(at) == '{') {
                // This endpoint is flat; skip a nested object whole rather than mis-reading it.
                int end = close(body, at);
                if (end < 0) break;
                i = end + 1;
            } else {
                int end = at;
                while (end < to && body.charAt(end) != ',' && body.charAt(end) != '}') end++;
                out.put(key, body.substring(at, end).trim());
                i = end;
            }
        }
        return out;
    }

    /** Index of the brace closing the object that opens at {@code open}, or -1. */
    private static int close(String body, int open) {
        int depth = 0;
        for (int i = open; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"') {
                int end = stringEnd(body, i);
                if (end < 0) return -1;
                i = end;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (--depth == 0) return i;
            }
        }
        return -1;
    }

    /** Index of the quote closing the string that opens at {@code quote}, or -1. */
    private static int stringEnd(String body, int quote) {
        for (int i = quote + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"') return i;
        }
        return -1;
    }

    /** Resolves the escapes JSON allows; escapes used for layout become a space, not a line break. */
    private static String unescape(String raw) {
        if (raw.indexOf('\\') < 0) return raw;
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) { out.append(c); continue; }
            char next = raw.charAt(++i);
            if (next == 'u' && i + 4 < raw.length()) {
                try {
                    out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                    i += 4;
                } catch (NumberFormatException notHex) {
                    out.append(' ');
                }
            } else {
                out.append(next == 'n' || next == 'r' || next == 't' ? ' ' : next);
            }
        }
        return out.toString();
    }

    private static Course course(Map<String, String> members) {
        String name = clip(text(members, "KCMC"));
        if (name.isEmpty()) return null;
        return new Course(name,
            clip(text(members, "KCH")),
            credit(text(members, "XF")),
            text(members, "CJ"),
            text(members, "JD"),
            text(members, "XNMC"),
            text(members, "XQMMC"),
            clip(text(members, "KCXZMC")),
            digits(text(members, "XDZT")));
    }

    /** One member as text: absent and the literal {@code null} both mean "the school sent nothing". */
    private static String text(Map<String, String> members, String key) {
        String value = members.get(key);
        if (value == null) return "";
        value = value.trim();
        return "null".equals(value) ? "" : value;
    }

    private static String digits(String raw) {
        Matcher m = Pattern.compile("\\d+").matcher(raw == null ? "" : raw);
        return m.find() ? m.group() : "";
    }

    private static String clip(String text) {
        String value = text == null ? "" : text.trim();
        return value.length() <= MAX_NAME ? value : value.substring(0, MAX_NAME);
    }

    /**
     * Maps a row's {@code jdkcsx} to the endpoint the school itself would call.
     *
     * <p>Its script branches on {@code jdkcsx == '1'}: the plan endpoint for that value, the
     * non-plan endpoint for everything else. Two synthetic rows (其它课程 / 创新创业) are special-cased
     * by the school to the plan endpoint regardless of their marker. A page with no markers at all
     * keeps the plan endpoint, which is what every earlier build used.</p>
     */
    private static boolean planCourses(String id, String mark, boolean marked) {
        if ("qtkcxfyq".equals(id) || "cxcyqkxfyq".equals(id)) return true;
        if (!marked) return true;
        return "1".equals(mark == null ? "" : mark.trim());
    }

    /** A map value with {@code null} flattened to empty, so callers never test for null. */
    private static String value(String raw) { return raw == null ? "" : raw; }

    /**
     * Node id → the school's raw {@code jdkcsx} marker for that row.
     *
     * <p>Collected from the {@code <div class='title'>} that owns the row, keyed by its
     * {@code xfyqjd_id}; the value is kept as the school wrote it ("1", "3", "0", empty) because the
     * endpoint choice is a comparison against "1", not a truthiness test.</p>
     */
    private static Map<String, String> courseMarks(String html) {
        Map<String, String> marks = new LinkedHashMap<String, String>();
        Matcher div = TITLE_DIV.matcher(html);
        while (div.find()) {
            String attrs = div.group(1);
            String id = attribute(attrs, "xfyqjd_id").trim();
            Matcher marker = Pattern.compile("\\bjdkcsx='([^']*)'").matcher(attrs);
            if (id.isEmpty() || !marker.find()) continue;
            if (!marks.containsKey(id)) marks.put(id, marker.group(1).trim());
        }
        return marks;
    }

    /**
     * Recovers the school's own hierarchy. Each {@code <li id='li…'>} is appended to its parent's
     * container ({@code #ul…} / {@code #li…}); the bare {@code #ul#start} means a root. Walking
     * that parent map gives every node a depth and a parent-first order, which is exactly what a
     * nested layout needs. If a page ever drops those links the nodes stay flat and callers fall
     * back to a plain list, so this can only improve the screen, never empty it.
     */
    private static void buildTree(String html, Map<String, Node> raw, List<String> order, Report report) {
        Map<String, String> parent = new LinkedHashMap<String, String>();
        Matcher item = LIST_ITEM.matcher(html);
        Matcher attach = ATTACHED_TO.matcher(html);
        while (item.find()) {
            String id = item.group(1);
            if (id.isEmpty() || !raw.containsKey(id) || parent.containsKey(id)) continue;
            attach.region(item.end(), Math.min(html.length(), item.end() + ATTACH_WINDOW));
            if (!attach.find()) continue;
            String up = attach.group(1);
            if (!up.isEmpty() && !up.equals(id) && raw.containsKey(up)) parent.put(id, up);
        }
        if (parent.isEmpty()) {
            for (String id : order) report.nodes.add(raw.get(id));
            return;
        }
        Map<String, List<String>> children = new LinkedHashMap<String, List<String>>();
        for (String id : order) children.put(id, new ArrayList<String>());
        List<String> roots = new ArrayList<String>();
        for (String id : order) {
            String up = parent.get(id);
            if (up == null) roots.add(id); else children.get(up).add(id);
        }
        List<String> seen = new ArrayList<String>();
        for (String root : roots) walk(root, 1, raw, children, seen, report.nodes);
        // A node whose parent chain was cut still has to appear, or the screen silently loses
        // whole categories; treat it as a root rather than dropping it.
        for (String id : order) if (!seen.contains(id)) walk(id, 1, raw, children, seen, report.nodes);
        report.layered = true;
    }

    private static void walk(String id, int depth, Map<String, Node> raw, Map<String, List<String>> children,
                             List<String> seen, List<Node> out) {
        if (depth > MAX_DEPTH || seen.contains(id)) return;
        seen.add(id);
        Node node = raw.get(id);
        out.add(new Node(node.id, node.name, node.earned, node.required, node.passed, depth,
            node.planCourses));
        for (String child : children.get(id)) walk(child, depth + 1, raw, children, seen, out);
    }

    /** One attribute of a tag's attribute string, empty when absent (order never matters). */
    private static String attribute(String attributes, String name) {
        Matcher m = Pattern.compile("\\b" + name + "='([^']*)'").matcher(attributes == null ? "" : attributes);
        return m.find() ? m.group(1) : "";
    }

    private static String gpa(String html) {
        Matcher m = Pattern.compile("平均学分绩点.{0,400}?color:\\s*red;?[^>]*>\\s*(\\d+(?:\\.\\d+)?)",
            Pattern.DOTALL).matcher(html);
        return m.find() ? m.group(1) : "";
    }

    private static String stat(String html, String regex) {
        Matcher m = Pattern.compile(regex).matcher(html);
        return m.find() ? m.group(1) : "";
    }

    private static String first(String html, String anchor, String regex) {
        int at = html.indexOf(anchor);
        if (at < 0) return "";
        Matcher m = Pattern.compile(regex).matcher(html.substring(Math.max(0, at - 120), at + 40));
        return m.find() ? m.group(1) : "";
    }

    private static String credit(String raw) {
        Matcher m = CREDIT.matcher(raw == null ? "" : raw.trim());
        return m.find() ? m.group() : "0";
    }

    /** Strip script/i18n/HTML noise to the human-readable label of a tree node. */
    private static String clean(String raw) {
        String s = COMMENT.matcher(raw == null ? "" : raw).replaceAll(" ");
        s = I18N.matcher(s).replaceAll(" ");
        s = TAG.matcher(s).replaceAll(" ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("\"", " ").replace("'", " ");
        s = s.replace("\r", " ").replace("\n", " ").replace("\t", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Prefer a quoted Chinese literal, then the first Chinese run in the cleaned label. */
    private static String titleName(String title) {
        Matcher quoted = QUOTED_NAME.matcher(title);
        if (quoted.find()) return quoted.group(1).trim();
        Matcher chinese = CHINESE.matcher(title);
        return chinese.find() ? chinese.group().trim() : "";
    }
}
