package io.qzxy.lite.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps a course between a flat String field map and the typed {@link Course} model.
 *
 * <p>A String map is the boundary on purpose: the persisted form is a JSONObject in the Android
 * layer, but keeping the mapping logic on plain strings lets it be unit-tested in core with no
 * org.json dependency. The Android adapter only converts JSONObject to/from this map. Unknown
 * fields are carried through untouched so old backups and imported files keep their extensions.</p>
 *
 * <p>Weeks are encoded as a comma-separated list of numbers and inclusive ranges, e.g.
 * {@code 1,3,5,7-9}. The Android layer's CourseWeeks may persist them as a JSON array; its adapter
 * converts that array into this string form before calling here.</p>
 */
public final class CourseJsonMapper {
    private static final int MAX_WEEK = 30;

    private CourseJsonMapper() { }

    public static Course fromFields(Map<String, String> f) {
        if (f == null) throw new IllegalArgumentException("course fields");
        String name = value(f, "name");
        if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("bad name");
        String room = value(f, "room"), teacher = value(f, "teacher");
        if (room.length() > 100 || teacher.length() > 60) throw new IllegalArgumentException("bad room/teacher");
        int day = number(f, "day"), first = number(f, "first"), last = number(f, "last");
        if (day < 0 || day > 6 || first < 1 || last < first || last > 16)
            throw new IllegalArgumentException("bad day/sections");
        int color = f.containsKey("color") && !value(f, "color").isEmpty() ? number(f, "color") : 0;
        if (color < 0 || color > 12) throw new IllegalArgumentException("bad color");
        return new Course(name, room, teacher, day, first, last, color, parseWeeks(value(f, "weeks")));
    }

    /** Typed course back to the flat fields; every original key is preserved. */
    public static Map<String, String> toFields(Course course) {
        if (course == null) throw new IllegalArgumentException("course");
        Map<String, String> out = new LinkedHashMap<String, String>();
        out.put("name", course.name);
        out.put("room", course.room);
        out.put("teacher", course.teacher);
        out.put("day", Integer.toString(course.day));
        out.put("first", Integer.toString(course.first));
        out.put("last", Integer.toString(course.last));
        out.put("weeks", formatWeeks(course.weeks()));
        out.put("color", Integer.toString(course.color));
        return out;
    }

    /** Preserve unknown fields from the stored object while rewriting the known ones. */
    public static Map<String, String> toFields(Course course, Map<String, String> original) {
        Map<String, String> out = original == null
            ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(original);
        out.putAll(toFields(course));
        return out;
    }

    static Set<Integer> parseWeeks(String spec) {
        Set<Integer> weeks = new LinkedHashSet<Integer>();
        if (spec == null || spec.trim().isEmpty()) return weeks;
        for (String part : spec.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            int dash = token.indexOf('-');
            if (dash < 0) {
                weeks.add(week(token));
            } else {
                int from = week(token.substring(0, dash)), to = week(token.substring(dash + 1));
                if (from > to) throw new IllegalArgumentException("bad week range");
                for (int w = from; w <= to; w++) weeks.add(Integer.valueOf(w));
            }
        }
        return weeks;
    }

    static String formatWeeks(Set<Integer> weeks) {
        StringBuilder out = new StringBuilder();
        for (Integer week : weeks) {
            if (out.length() > 0) out.append(',');
            out.append(week.intValue());
        }
        return out.toString();
    }

    private static int week(String raw) {
        int value = number(raw);
        if (value < 1 || value > MAX_WEEK) throw new IllegalArgumentException("bad week");
        return value;
    }

    private static String value(Map<String, String> f, String key) {
        String raw = f.get(key);
        return raw == null ? "" : raw.trim();
    }

    private static int number(Map<String, String> f, String key) {
        return number(value(f, key));
    }

    private static int number(String raw) {
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException bad) { throw new IllegalArgumentException("bad integer: " + raw); }
    }
}
