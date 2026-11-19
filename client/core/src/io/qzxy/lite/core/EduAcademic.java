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

    /** One credit-group row of the tree: name plus earned/required credits and pass flag. */
    public static final class Node {
        public final String name;
        public final String earned;
        public final String required;
        public final boolean passed;
        Node(String name, String earned, String required, boolean passed) {
            this.name = name; this.earned = earned; this.required = required; this.passed = passed;
        }
    }

    /** The whole report: headline statistics and the de-duplicated credit tree. */
    public static final class Report {
        public String name = "";
        public String gpa = "";
        public String plan = "", passed = "", failed = "", unread = "", studying = "", extra = "";
        public final List<Node> nodes = new ArrayList<>();
    }

    private static final Pattern NODE = Pattern.compile(
        "<p\\s+class='title1'[^>]*id='p([^']+)'[^>]*yxxf='([^']*)'[^>]*yqzdxf='([^']*)'[^>]*sftg='([^']*)'[^>]*>(.{0,400}?)</p>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
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
        Matcher nodes = NODE.matcher(html);
        Map<String, Node> unique = new LinkedHashMap<String, Node>();
        while (nodes.find() && unique.size() < MAX_NODES) {
            String id = nodes.group(1);
            String earned = credit(nodes.group(2));
            String required = credit(nodes.group(3));
            boolean passed = "1".equals(nodes.group(4).trim());
            String title = clean(nodes.group(5));
            String name = titleName(title);
            if (name.isEmpty() || name.equals("同学")) continue;
            // Several branches render the same id; keep the first valid one so the tree shows one
            // row per credit group, not one per campus branch. Empty-name branches are skipped above.
            if (!unique.containsKey(id)) unique.put(id, new Node(name, earned, required, passed));
        }
        report.nodes.addAll(unique.values());
        if (report.gpa.isEmpty() && report.nodes.isEmpty())
            throw new IllegalArgumentException("没有读到学业情况数据，页面可能改版了");
        return report;
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
