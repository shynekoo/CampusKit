// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/**
 * The one-line encouragement on the 今日 page. It changes once a day and is stable within a day, so
 * reopening the app does not reshuffle it.
 *
 * Selection is derived from the calendar date alone — no network, no randomness, no clock reads — which
 * makes it reproducible in a test. Dates are converted with the days-from-civil algorithm instead of
 * {@code java.util.Calendar} so the result cannot depend on the device time zone.
 */
public final class DailyQuote {
    private static final String[] QUOTES = {
        "今天的一小步，是明天的一大步。",
        "慢一点没关系，别停下来就好。",
        "先把眼前这一节听懂，剩下的自然会来。",
        "状态不好也去做一点，做完会好一点。",
        "你不是在熬时间，你是在攒本事。",
        "今天的课，是以后某天的底气。",
        "不必事事完美，先把事情做完。",
        "早起的那半小时，一整天都在还你。",
        "会的东西再多一点，选择就再多一点。",
        "把难的那门放到精力最好的时候。",
        "进步往往是看不见的，直到你回头看。",
        "别人在跑，你也可以按自己的配速。",
        "一节课四十五分钟，认真听就是赚到。",
        "不想学的时候就少学一点，但别断。",
        "你今天的坚持，是在替明天的自己减负。",
        "把大目标拆小，小到今晚就能做完。",
        "累是正常的，说明你在往上走。",
        "现在多背一个公式，考场上少慌一次。",
        "允许自己休息，但别允许自己放弃。",
        "别急，扎实比快更重要。",
        "每一次按时起床，都是一次小胜利。",
        "把注意力收回来，就赢过了大半的人。",
        "今天解决一个小问题，期末就少一个大问题。",
        "认真生活的人，运气都不会太差。",
    };

    private DailyQuote() { }

    public static int count() { return QUOTES.length; }

    /** The quote for a {@code yyyy-MM-dd} date; throws when the date is malformed. */
    public static String forDate(String isoDate) {
        return QUOTES[(int) Math.floorMod(daysFromCivil(isoDate), QUOTES.length)];
    }

    /**
     * Days since 1970-01-01 for a {@code yyyy-MM-dd} date, by Howard Hinnant's days-from-civil
     * algorithm: pure integer arithmetic, so no time zone or locale can change the answer.
     */
    static long daysFromCivil(String isoDate) {
        if (isoDate == null || !isoDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("日期格式应为 年-月-日");
        int year = Integer.parseInt(isoDate.substring(0, 4));
        int month = Integer.parseInt(isoDate.substring(5, 7));
        int day = Integer.parseInt(isoDate.substring(8, 10));
        if (month < 1 || month > 12 || day < 1 || day > 31) throw new IllegalArgumentException("日期超出范围");
        long adjusted = year - (month <= 2 ? 1 : 0);
        long era = (adjusted >= 0 ? adjusted : adjusted - 399) / 400;
        long yearOfEra = adjusted - era * 400;
        long dayOfYear = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1;
        long dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * 146097 + dayOfEra - 719468;
    }
}
