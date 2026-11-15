package io.qzxy.lite.core;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the academic system's own form values and derives the empty-classroom query fields.
 *
 * <p>The school page is the only source of the current academic year, term, campus list and week
 * calendar; none of them are compiled into the app, so a term rollover or a new campus needs no
 * release. The query fields are bit masks of the chosen weeks and periods, which is the shape the
 * school's own page posts.</p>
 */
public final class EduSite {
    private static final Pattern OPTION = Pattern.compile("<option\\b([^>]*)>([^<]*)</option>",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE = Pattern.compile("value\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    private EduSite() { }

    /** Value of an {@code id}: an input's value, or the selected option of a select. */
    public static String field(String html, String id) {
        if (html == null || id == null || id.isEmpty()) return "";
        String quoted = Pattern.quote(id);
        Matcher input = INPUT.matcher(html);
        while (input.find()) {
            String tag = input.group();
            if (!tag.matches("(?is).*\\bid\\s*=\\s*\"" + quoted + "\".*")) continue;
            Matcher value = VALUE.matcher(tag);
            if (value.find()) return value.group(1).trim();
        }
        String block = selectBlock(html, id);
        if (block.isEmpty()) return "";
        String first = "";
        Matcher option = OPTION.matcher(block);
        while (option.find()) {
            String[] parsed = option(option.group(1), option.group(2));
            if (parsed == null) continue;
            if (first.isEmpty()) first = parsed[0];
            if (parsed[2].equals("1")) return parsed[0];
        }
        return first;
    }

    /** Options of a select as {value, label}, in page order. */
    public static List<String[]> options(String html, String selectId) {
        List<String[]> found = new ArrayList<>();
        String block = selectBlock(html, selectId);
        if (block.isEmpty()) return found;
        Matcher option = OPTION.matcher(block);
        while (option.find()) {
            String[] parsed = option(option.group(1), option.group(2));
            if (parsed != null) found.add(new String[]{parsed[0], parsed[1]});
        }
        return found;
    }

    /** {@code 1 << (week - 1)} as the decimal string the page posts. */
    public static String weekMask(int week) {
        if (week < 1 || week > 62) throw new IllegalArgumentException("周次超出范围");
        return Long.toString(1L << (week - 1));
    }

    /** Bit mask for an inclusive period range, e.g. 1-2 becomes 3. */
    public static String periodMask(int from, int to) {
        if (from < 1 || to < from || to > 62) throw new IllegalArgumentException("节次超出范围");
        long bits = 0;
        for (int i = from; i <= to; i++) bits |= 1L << (i - 1);
        return Long.toString(bits);
    }

    /**
     * Teaching week of {@code today} for a term that starts on {@code monday}; 0 when unknown.
     * Dates are compared at UTC midnight so a daylight-saving shift cannot move a week boundary.
     */
    public static int weekOn(String monday, String today) {
        if (monday == null || today == null || monday.isEmpty() || today.isEmpty()) return 0;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
            format.setLenient(false);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            long start = format.parse(monday).getTime();
            long now = format.parse(today).getTime();
            long days = (now - start) / 86400000L;
            if (days < 0) return 0;
            return (int) (days / 7) + 1;
        } catch (Exception invalid) {
            return 0;
        }
    }

    /** Today as {@code yyyy-MM-dd} in the device's own time zone. */
    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new Date());
    }

    /**
     * Consecutive period pairs (1-2, 3-4, …) as they are timetabled. A trailing odd period is kept
     * on its own rather than widened, so a query never includes a period the user did not pick.
     */
    public static List<int[]> pairs(List<Integer> sections) {
        List<int[]> pairs = new ArrayList<>();
        if (sections == null) return pairs;
        int index = 0;
        while (index < sections.size()) {
            int first = sections.get(index);
            Integer second = index + 1 < sections.size() ? sections.get(index + 1) : null;
            if (second != null && second == first + 1) { pairs.add(new int[]{first, second}); index += 2; }
            else { pairs.add(new int[]{first, first}); index++; }
        }
        return pairs;
    }

    private static String[] option(String attributes, String label) {
        Matcher value = VALUE.matcher(attributes);
        if (!value.find()) return null;
        String text = label.replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return null;
        boolean selected = attributes.matches("(?is).*\\bselected\\b.*");
        return new String[]{value.group(1).trim(), text, selected ? "1" : "0"};
    }

    private static String selectBlock(String html, String id) {
        if (html == null) return "";
        String quoted = Pattern.quote(id);
        Matcher open = Pattern.compile("<select\\b[^>]*\\bid\\s*=\\s*\"" + quoted + "\"[^>]*>",
            Pattern.CASE_INSENSITIVE).matcher(html);
        if (!open.find()) return "";
        int end = html.toLowerCase(java.util.Locale.ROOT).indexOf("</select>", open.end());
        if (end < 0) return "";
        return html.substring(open.end(), end);
    }
}
