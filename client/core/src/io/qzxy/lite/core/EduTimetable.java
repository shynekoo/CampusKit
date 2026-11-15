package io.qzxy.lite.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the field values 正方 returns from its student-timetable query into the same text shape the
 * existing web/PDF course parser already understands.
 *
 * <p>Keeping this a pure string adapter means one parser and one validation path for every import
 * source: the PDF reader, the rendered web grid and the query JSON all end up as
 * {@code (节次)周次/场地:…/教师:…/教学班:…}. Nothing here guesses a course that is missing a
 * field — an unrecognisable value is rejected by the caller and reported as a skipped row.</p>
 */
public final class EduTimetable {
    private static final Pattern RANGE = Pattern.compile("^(\\d{1,2})(?:-(\\d{1,2}))?$");
    private static final String[] WEEKDAYS = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};

    private EduTimetable() { }

    /** 0 = Monday. Accepts the numeric xqj and the Chinese name some deployments return. */
    public static int day(String number, String chinese) {
        String value = number == null ? "" : number.trim();
        if (value.matches("[1-7]")) return Integer.parseInt(value) - 1;
        String name = chinese == null ? "" : chinese.replaceAll("\\s+", "");
        for (int i = 0; i < WEEKDAYS.length; i++) if (WEEKDAYS[i].equals(name)) return i;
        if ("星期天".equals(name) || "周日".equals(name) || "周天".equals(name)) return 6;
        throw new IllegalArgumentException("星期字段无法识别");
    }

    /**
     * Section codes arrive as {@code 1-2}, {@code 01-02}, {@code 0102} or a single {@code 3}.
     * A single section stays a single number, so the downstream parser reads it as one class.
     */
    public static String sections(String raw) {
        String value = clean(raw).replaceAll("[^0-9\\-]", " ");
        String compact = value.replaceAll("\\s+", "");
        int first, last;
        Matcher range = Pattern.compile("^0*([0-9]{1,2})-0*([0-9]{1,2})$").matcher(compact);
        if (range.matches()) {
            first = Integer.parseInt(range.group(1));
            last = Integer.parseInt(range.group(2));
        } else if (compact.matches("0*[0-9]{1,2}")) {
            first = last = Integer.parseInt(compact);
        } else if (compact.matches("[0-9]{4}")) {
            // "0102" / "1011" are start/end section code pairs, not the number one thousand and two.
            first = Integer.parseInt(compact.substring(0, 2));
            last = Integer.parseInt(compact.substring(2));
        } else {
            throw new IllegalArgumentException("节次字段无法识别：" + compact);
        }
        if (first < 1 || last > 16 || first > last) throw new IllegalArgumentException("节次超出 1–16");
        return first == last ? Integer.toString(first) : first + "-" + last;
    }

    /**
     * A single week range only: {@code 1-16周}, {@code 01-16}, {@code 1-16周(单周)}. Several ranges
     * in one cell ({@code 1,3,5-16}) are refused rather than silently flattened into a wrong range.
     */
    public static String weeks(String raw, String parityRaw) {
        String value = clean(raw);
        String parity = clean(parityRaw);
        String single = "";
        if (value.contains("单") || parity.contains("单")) single = "单";
        else if (value.contains("双") || parity.contains("双")) single = "双";
        String core = value.replaceAll("周.*$", "").replaceAll("[^0-9\\-,\\uFF0C]", "");
        if (core.contains(",") || core.contains("\uFF0C") || core.indexOf('-') != core.lastIndexOf('-'))
            throw new IllegalArgumentException("多段周次暂不支持");
        Matcher range = RANGE.matcher(core);
        if (!range.matches()) throw new IllegalArgumentException("周次字段无法识别：" + core);
        int from = Integer.parseInt(range.group(1));
        int to = range.group(2) == null ? from : Integer.parseInt(range.group(2));
        if (from < 1 || to > 30 || from > to) throw new IllegalArgumentException("周次超出 1–30");
        String text = (from == to ? Integer.toString(from) : from + "-" + to) + "周";
        return single.isEmpty() ? text : text + "(" + single + "周)";
    }

    /** HTML fragments, entities and separators must not leak into a label or split a course cell. */
    public static String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace('/', ' ').replace('\\', ' ').replaceAll("[\\r\\n\\t]+", " ")
            .replaceAll("\\s+", " ").trim();
    }

    /** The exact shape {@link TimetableWebCourseParser} was written for. */
    public static String courseText(String sections, String weeks, String room, String teacher, String title) {
        return "(" + sections + "节)" + weeks + "/场地:" + room + "/教师:" + teacher + "/教学班:" + title;
    }
}
