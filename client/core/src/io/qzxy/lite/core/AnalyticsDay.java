// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/**
 * 统计用的「哪一天」，按北京时间（UTC+8）算，形状固定为 {@code YYYY-MM-DD}。
 *
 * <p>为什么不用 {@code java.time}：core 用 {@code --release 8} 编译，而 {@code java.time} 直到
 * Android 8.0（API 26）才在系统里；本应用 minSdk 24。为什么不用 {@code SimpleDateFormat}：
 * 它的输出跟着默认时区与 locale 走，同一台手机改个时区就能把「今天」算成别的日子。</p>
 *
 * <p>客户端只把这个值当作「今天是不是已经报过了」的本地对照；服务端另有自己的
 * {@code beijingDay()}，从不读客户端传来的日期。所以手机时钟不准最多让客户端多报几次，
 * 服务端仍然一天只记一条。</p>
 */
public final class AnalyticsDay {
    private static final long DAY_MS = 86400000L;
    /** 北京时间比 UTC 早 8 小时，没有夏令时。 */
    private static final long OFFSET_MS = 8L * 3600 * 1000;

    private AnalyticsDay() { }

    /** 该毫秒时刻对应的北京时间日期，例如 {@code 2026-11-20}。 */
    public static String of(long epochMillis) {
        return ofDays(Math.floorDiv(epochMillis + OFFSET_MS, DAY_MS));
    }

    /** 现在。单独抽出来，测试里就不必碰真实时钟。 */
    public static String today() {
        return of(System.currentTimeMillis());
    }

    /** 只接受 {@code YYYY-MM-DD}；其余（空串、带时间、别的写法）一律不算有效日期。 */
    public static boolean valid(String day) {
        if (day == null || day.length() != 10) return false;
        for (int i = 0; i < 10; i++) {
            char c = day.charAt(i);
            if (i == 4 || i == 7) {
                if (c != '-') return false;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        int month = Integer.parseInt(day.substring(5, 7));
        int date = Integer.parseInt(day.substring(8, 10));
        return month >= 1 && month <= 12 && date >= 1 && date <= 31;
    }

    /** 天数（1970-01-01 起，可为负）→ 公历年月日。Howard Hinnant 的 civil_from_days。 */
    private static String ofDays(long days) {
        long z = days + 719468;
        long era = Math.floorDiv(z, 146097);
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long year = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long date = doy - (153 * mp + 2) / 5 + 1;
        long month = mp < 10 ? mp + 3 : mp - 9;
        if (month <= 2) year++;
        return pad(year, 4) + "-" + pad(month, 2) + "-" + pad(date, 2);
    }

    private static String pad(long value, int width) {
        String text = Long.toString(value);
        StringBuilder out = new StringBuilder(width);
        for (int i = text.length(); i < width; i++) out.append('0');
        return out.append(text).toString();
    }
}
