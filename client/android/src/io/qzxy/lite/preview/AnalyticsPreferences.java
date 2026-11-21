// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

import io.qzxy.lite.core.AnalyticsDay;
import io.qzxy.lite.core.AnalyticsRequest;

/**
 * 匿名统计的那点状态，全在这一个偏好文件里：同意了没有、匿名标识是什么、上次报的是哪一天、
 * 是否还欠一次删除。
 *
 * <p>四条规矩：</p>
 *
 * <ul>
 *   <li><b>默认关</b>：{@code consent} 没有默认值，读不到就是 false。升级安装也一样——
 *       旧版本从来没写过这个键，所以「升级后自动开启」在实现上无从发生。</li>
 *   <li><b>没同意就没有标识</b>：{@link #installId} 只读不建；标识唯一的生成点是
 *       {@link #ensureInstallId}，而它只在读者按下开启之后被调用。</li>
 *   <li><b>标识与设备无关</b>：{@code UUID.randomUUID()}，不读 ANDROID_ID / IMEI / OAID /
 *       MAC，也不带学号。应用私有目录里的一个随机串。</li>
 *   <li><b>卸载即失联</b>：清单里 {@code allowBackup="false"}，偏好不进系统备份；重装是新的
 *       {@code UUID}，继承不了上一份安装。</li>
 * </ul>
 */
final class AnalyticsPreferences {
    static final String PREFS = "analytics-v1";
    private static final String KEY_CONSENT = "consent";
    private static final String KEY_INSTALL = "install";
    private static final String KEY_DAY = "day";
    private static final String KEY_FORGET = "forget";

    private AnalyticsPreferences() { }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 读者是否明确同意过。默认 false，且不随版本升级改变。 */
    static boolean consented(Context context) {
        return store(context).getBoolean(KEY_CONSENT, false);
    }

    static void setConsented(Context context, boolean value) {
        store(context).edit().putBoolean(KEY_CONSENT, value).apply();
    }

    /** 已存在的匿名标识；没有就是 null。绝不在这里生成——生成就等于采集。 */
    static String installId(Context context) {
        String value = store(context).getString(KEY_INSTALL, null);
        return AnalyticsRequest.validInstallId(value) ? value : null;
    }

    /** 只在读者同意之后调用一次：生成并持久化随机标识。已存在就原样返回（升级要保住它）。 */
    static String ensureInstallId(Context context) {
        String existing = installId(context);
        if (existing != null) return existing;
        String created = UUID.randomUUID().toString();
        store(context).edit().putString(KEY_INSTALL, created).apply();
        return created;
    }

    /** 上一次被服务端确认的日期（本地按北京时间算的那一天）。空串表示还没成功过。 */
    static String reportedDay(Context context) {
        String value = store(context).getString(KEY_DAY, "");
        return AnalyticsDay.valid(value) ? value : "";
    }

    static void setReportedDay(Context context, String day) {
        store(context).edit().putString(KEY_DAY, AnalyticsDay.valid(day) ? day : "").apply();
    }

    /** 今天还没报过。判断顺序就是「先有同意、再有标识、最后才看日期」。 */
    static boolean dueToday(Context context) {
        if (!consented(context)) return false;
        if (installId(context) == null) return false;
        return !AnalyticsDay.today().equals(reportedDay(context));
    }

    /** 关闭统计时留下的删除请求：在删成功之前不能丢掉标识，否则服务端那条记录就找不回来了。 */
    static boolean forgetWaiting(Context context) {
        return store(context).getBoolean(KEY_FORGET, false);
    }

    static void setForgetWaiting(Context context, boolean value) {
        store(context).edit().putBoolean(KEY_FORGET, value).apply();
    }

    /** 本地一侧彻底忘掉这个安装：标识、上次日期、待删除标记一起清掉。 */
    static void clearIdentity(Context context) {
        store(context).edit()
            .remove(KEY_INSTALL).remove(KEY_DAY).remove(KEY_FORGET).apply();
    }

    /** 构建期有没有给统计服务地址。没给就一个请求都不发，设置页如实说明。 */
    static boolean configured() {
        return AnalyticsRequest.configured(BuildFlags.ANALYTICS_ENDPOINT);
    }
}
