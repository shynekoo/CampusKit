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
    private static final Pattern HREF = Pattern.compile("href\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANCHOR = Pattern.compile("<a\\b([^>]*)>(.*?)</a>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    /** A menu entry as a script writes it: '/cjcx/cjcx_cjcx.html?gnmkdm=N305005'. */
    private static final Pattern MENU_PATH = Pattern.compile(
        "/[A-Za-z]{3,}/[A-Za-z0-9_]+\\.html(?:\\?[^\"'<>\\s)]*)?");
    /** A quoted token, the way an embedded script or JSON spells a menu item's name. */
    private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"\\r\\n]{1,60})['\"]");
    /** The school's own function code for the grade group, which survives a folder rename. */
    private static final Pattern GRADE_CODE = Pattern.compile("gnmkdm=N305\\d{3}", Pattern.CASE_INSENSITIVE);
    /** A Chinese character escaped the way an embedded script writes it. */
    private static final Pattern UNICODE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

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

    /**
     * Academic years the grade picker offers when the school page only renders the current year as a
     * hidden input — which is what 正方 does on the empty-classroom page, so that page alone would
     * leave the picker with exactly one choice. A year is coded as the calendar year the autumn term
     * starts: {@code 2026} means the 2026-2027 academic year. Newest first, at most a dozen.
     */
    public static List<String[]> yearOptions(String current, int count) {
        List<String[]> out = new ArrayList<>();
        if (current == null || !current.matches("(?:19|20)\\d{2}")) return out;
        int start = Integer.parseInt(current);
        if (count < 1) count = 1;
        if (count > 12) count = 12;
        for (int i = 0; i < count; i++) {
            int year = start - i;
            if (year < 1990) break;
            out.add(new String[]{Integer.toString(year), year + "-" + (year + 1) + " 学年"});
        }
        // The grade query accepts an empty year as "all years"; offer it as a real choice rather
        // than making the user walk six years one term at a time. A non-empty sentinel keeps the
        // picker's "fill in the default if unset" helper from silently undoing this choice.
        out.add(new String[]{"ALL", "全部学年"});
        return out;
    }

    /**
     * The terms 正方 uses, so a page that hides them still leaves every term of the year selectable.
     * Two codings are in circulation: {@code 1/2} on some deployments and {@code 3/12/16} on others.
     * Which one a school uses is read from the value its own page reports; an unknown code is kept as
     * the only option rather than guessed.
     */
    public static List<String[]> termOptions(String current) {
        List<String[]> out = new ArrayList<>();
        String value = current == null ? "" : current.trim();
        if (value.equals("1") || value.equals("2")) {
            out.add(new String[]{"1", "第一学期"});
            out.add(new String[]{"2", "第二学期"});
        } else if (value.equals("3") || value.equals("12") || value.equals("16")) {
            out.add(new String[]{"3", "第一学期"});
            out.add(new String[]{"12", "第二学期"});
            out.add(new String[]{"16", "第三学期"});
        } else if (value.matches("\\d{1,3}")) {
            out.add(new String[]{value, "当前学期"});
        }
        if (!out.isEmpty()) out.add(new String[]{"ALL", "全部学期"});
        return out;
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

    /** Bit mask for any set of periods, so 1, 2 and 5 become 19. */
    public static String periodMask(int[] sections) {
        if (sections == null || sections.length == 0) throw new IllegalArgumentException("节次为空");
        long bits = 0;
        for (int section : sections) {
            if (section < 1 || section > 62) throw new IllegalArgumentException("节次超出范围");
            bits |= 1L << (section - 1);
        }
        return Long.toString(bits);
    }

    /**
     * Parses a stored period selection — {@code 1,2,5} or {@code 1-4} — into sorted unique
     * periods. Anything out of range is dropped instead of widening the query.
     */
    public static int[] sections(String value) {
        java.util.TreeSet<Integer> found = new java.util.TreeSet<>();
        if (value != null) for (String part : value.split(",")) {
            String text = part.trim();
            if (text.isEmpty()) continue;
            int dash = text.indexOf('-');
            if (dash > 0) {
                int from = number(text.substring(0, dash)), to = number(text.substring(dash + 1));
                if (from >= 1 && to >= from && to <= 62) for (int i = from; i <= to; i++) found.add(i);
            } else {
                int single = number(text);
                if (single >= 1 && single <= 62) found.add(single);
            }
        }
        int[] out = new int[found.size()];
        int index = 0;
        for (int section : found) out[index++] = section;
        return out;
    }

    /** "第 1-2、5 节" — consecutive periods collapse, gaps stay visible. */
    public static String sectionLabel(int[] sections) {
        if (sections == null || sections.length == 0) return "";
        StringBuilder out = new StringBuilder("第 ");
        int start = sections[0], end = start;
        for (int index = 1; index <= sections.length; index++) {
            int current = index < sections.length ? sections[index] : -1;
            if (current == end + 1) { end = current; continue; }
            if (out.length() > 2) out.append('、');
            out.append(start == end ? Integer.toString(start) : start + "-" + end);
            start = current;
            end = current;
        }
        return out.append(" 节").toString();
    }

    /**
     * The grade page the school links from its own menu, as a path with its query.
     *
     * <p>Reading it instead of compiling it in means a renamed module or a different function code
     * still works. 正方 writes its menu two ways — an anchor, or a script/JSON entry such as
     * {@code openTab('成绩查询','/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005','N305005')} — and the
     * script form often writes every Chinese character as a backslash-u escape, which would otherwise
     * hide the word 成绩 completely. The caller decides what to do when the menu has no entry.</p>
     */
    public static String gradeLink(String html) {
        if (html == null) return "";
        String page = unescape(html);
        Matcher anchor = ANCHOR.matcher(page);
        String fallback = "";
        while (anchor.find()) {
            Matcher href = HREF.matcher(anchor.group(1));
            if (!href.find()) continue;
            String target = href.group(1).trim();
            if (target.isEmpty() || target.startsWith("#") || target.toLowerCase(java.util.Locale.ROOT).startsWith("javascript")) continue;
            if (!target.contains(".html")) continue;
            String text = anchor.group(2).replaceAll("<[^>]*>", " ").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
            if (!text.contains("成绩")) continue;
            String path = toPath(target);
            if (path.isEmpty()) continue;
            if (text.contains("成绩查询")) return path;
            if (fallback.isEmpty()) fallback = path;
        }
        if (!fallback.isEmpty()) return fallback;
        return menuPath(page);
    }

    /** Turns backslash-u and escaped-slash sequences of an embedded script back into text. */
    static String unescape(String html) {
        if (html.indexOf('\\') < 0) return html;
        Matcher escape = UNICODE.matcher(html);
        StringBuffer out = new StringBuffer(html.length());
        while (escape.find()) {
            String hex = escape.group(1);
            char decoded = (char) Integer.parseInt(hex, 16);
            escape.appendReplacement(out, Matcher.quoteReplacement(String.valueOf(decoded)));
        }
        escape.appendTail(out);
        return out.toString().replace("\\/", "/");
    }

    /**
     * A menu entry written by script or as JSON rather than as an anchor — the shape 正方's own pages
     * use. The path alone says nothing about which module it is, so the text around it decides: a
     * name containing 成绩, or the {@code N305xxx} function code, which survives a folder rename.
     * Entries whose own name says 成绩查询 win over ones that merely sit next to the word.
     */
    private static String menuPath(String html) {
        List<String> paths = new ArrayList<>();
        List<int[]> spans = new ArrayList<>();
        String cjcx = "", coded = "";
        Matcher path = MENU_PATH.matcher(html);
        while (path.find()) {
            String found = path.group();
            paths.add(found);
            spans.add(new int[]{path.start(), path.end()});
            if (found.contains("/cjcx/") && cjcx.isEmpty()) cjcx = found;
            if (GRADE_CODE.matcher(found).find() && coded.isEmpty()) coded = found;
        }
        // A menu name belongs to the path nearest it, not to every path within a wide window.
        // Without that, a 成绩 entry next to 选课 quietly turns the 选课 link into the grade page.
        String best = "", query = "";
        int nearest = Integer.MAX_VALUE, nearestQuery = Integer.MAX_VALUE;
        Matcher name = QUOTED.matcher(html);
        while (name.find()) {
            String label = name.group(1);
            if (!label.contains("成绩")) continue;
            int middle = (name.start() + name.end()) / 2;
            int chosen = -1, distance = Integer.MAX_VALUE;
            for (int i = 0; i < spans.size(); i++) {
                int gap = middle < spans.get(i)[0] ? spans.get(i)[0] - middle
                    : middle > spans.get(i)[1] ? middle - spans.get(i)[1] : 0;
                if (gap < distance) { distance = gap; chosen = i; }
            }
            if (chosen < 0 || distance > 200) continue;
            if (distance < nearest) { nearest = distance; best = paths.get(chosen); }
            if (label.contains("成绩查询") && distance < nearestQuery) {
                nearestQuery = distance; query = paths.get(chosen);
            }
        }
        if (!query.isEmpty()) return query;
        if (!best.isEmpty()) return best;
        if (!coded.isEmpty()) return coded;
        return cjcx;
    }

    private static String toPath(String target) {
        String path = target;
        int scheme = path.indexOf("://");
        if (scheme >= 0) {
            int slash = path.indexOf('/', scheme + 3);
            path = slash < 0 ? "" : path.substring(slash);
        }
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        if (path.isEmpty()) return "";
        return path.startsWith("/") ? path : "/" + path;
    }

    private static int number(String value) {
        try { return Integer.parseInt(value.trim()); } catch (RuntimeException invalid) { return 0; }
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

    /** {@code 1-2}, or {@code 7} for a period that is timetabled on its own. */
    public static String pairText(int[] pair) {
        if (pair == null || pair.length == 0) return "";
        return pair.length == 1 || pair[0] == pair[1] ? Integer.toString(pair[0]) : pair[0] + "-" + pair[1];
    }

    /** Which pairs the stored selection covers, so the picker opens on what is already searched. */
    public static boolean[] chosenPairs(List<int[]> pairs, int[] sections) {
        boolean[] chosen = new boolean[pairs == null ? 0 : pairs.size()];
        if (pairs == null || sections == null) return chosen;
        for (int index = 0; index < pairs.size(); index++) {
            for (int section : sections) if (contains(pairs.get(index), section)) { chosen[index] = true; break; }
        }
        return chosen;
    }

    /** Every period of the chosen pairs, in timetable order: {@code 1-2,5-6} becomes 1,2,5,6. */
    public static int[] periodsOf(List<int[]> pairs, boolean[] chosen) {
        List<Integer> found = new ArrayList<>();
        if (pairs != null && chosen != null) {
            for (int index = 0; index < pairs.size() && index < chosen.length; index++) {
                if (!chosen[index]) continue;
                for (int section : pairs.get(index)) if (!found.contains(section)) found.add(section);
            }
        }
        int[] out = new int[found.size()];
        for (int index = 0; index < out.length; index++) out[index] = found.get(index);
        return out;
    }

    /** The stored selection for the chosen pairs, e.g. {@code 1-2,5-6}. */
    public static String pairValue(List<int[]> pairs, boolean[] chosen) {
        StringBuilder out = new StringBuilder();
        if (pairs == null || chosen == null) return "";
        for (int index = 0; index < pairs.size() && index < chosen.length; index++) {
            if (!chosen[index]) continue;
            if (out.length() > 0) out.append(',');
            out.append(pairText(pairs.get(index)));
        }
        return out.toString();
    }

    private static boolean contains(int[] pair, int section) {
        for (int value : pair) if (value == section) return true;
        return false;
    }

    /**
     * Room type the empty-classroom screen starts on: 多媒体教室一 when the school lists it, because
     * that is the room these classes are actually taught in. Any other 多媒体 room is second best;
     * an empty string means every room type, which is the school's own "all" option.
     */
    public static String defaultType(List<String[]> types) {
        if (types == null) return "";
        for (String[] option : types)
            if (squeeze(option[1]).equals("多媒体教室一")) return option[0];
        for (String[] option : types)
            if (option[1].contains("多媒体")) return option[0];
        return "";
    }

    private static String squeeze(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
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
