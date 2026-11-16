package io.qzxy.lite.core;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Monday a term starts on, read from whatever the student types into 学期设置.
 *
 * <p>The stored value is always the canonical {@code yyyy-MM-dd}, because everything downstream —
 * the current week, the date under each weekday — parses it with one fixed format. What people type
 * is looser: {@code 2026-8-31} is the same day as {@code 2026-08-31}, and typing the zero for a
 * month or a day is easy to forget. So the zero is optional on input and restored on save.</p>
 *
 * <p>Validation stays strict where it matters: the date has to exist (2026-02-30 is refused, not
 * rolled forward to March) and it has to be a Monday, because a week is counted from it.</p>
 */
public final class TermDate {
    /** Year, month and day with optional leading zeros; anything else is not a date to us. */
    private static final Pattern SHAPE = Pattern.compile("(20\\d{2})-(\\d{1,2})-(\\d{1,2})");

    private TermDate() {}

    /**
     * @return the canonical {@code yyyy-MM-dd} for a Monday
     * @throws IllegalArgumentException when the text is not a real Monday date
     */
    public static String normalize(String value) {
        String text = value == null ? "" : value.trim();
        Matcher match = SHAPE.matcher(text);
        if (!match.matches()) throw new IllegalArgumentException();
        int year = Integer.parseInt(match.group(1));
        int month = Integer.parseInt(match.group(2));
        int day = Integer.parseInt(match.group(3));
        // A cleared calendar with lenient=false refuses 2026-02-30 instead of silently making it
        // 2026-03-02, so a typo can never become a different term.
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setLenient(false);
        calendar.clear();
        calendar.set(year, month - 1, day);
        calendar.getTime();
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) throw new IllegalArgumentException();
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
    }
}
