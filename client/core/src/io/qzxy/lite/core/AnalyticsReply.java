// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/**
 * 把统计服务的应答翻成四句话：记上了、今天早记过了、被服务端拒绝了、这次没成。
 *
 * <p>客户端的后续动作由这四种判定唯一决定，不在别处再写一遍：</p>
 *
 * <ul>
 *   <li>{@link #COUNTED} / {@link #DUPLICATE} —— 服务端已经有今天这条记录，本地可以记下
 *       「今天报过了」，不必再发。</li>
 *   <li>{@link #REFUSED} —— 4xx：请求本身不合规（版本号形状、字段超限）。重发没有意义，
 *       但也不记「今天报过了」，下次进前台照常再试一次，免得一次错判就把整天抹掉。</li>
 *   <li>{@link #TRANSIENT} —— 429 / 5xx / 断网 / 看不懂的应答：什么都不记，下次进前台再试。</li>
 * </ul>
 *
 * <p>只有服务端明确确认过，才会被当成「今天已经统计过」——本地从不假设请求成功。</p>
 */
public final class AnalyticsReply {
    /** 新增了一条。 */
    public static final int COUNTED = 0;
    /** 今天已经有这条记录了（联合主键去重生效）。 */
    public static final int DUPLICATE = 1;
    /** 服务端明确拒绝：请求不合规，重发无意义。 */
    public static final int REFUSED = 2;
    /** 这次没成：网络、限流、服务端故障、应答读不懂。 */
    public static final int TRANSIENT = 3;

    private AnalyticsReply() { }

    /**
     * @param httpStatus HTTP 状态；{@code 0} 表示请求根本没拿到应答（断网、超时、DNS）。
     * @param body       应答正文，可以为 null。
     */
    public static int of(int httpStatus, String body) {
        if (httpStatus == 0) return TRANSIENT;
        if (httpStatus == 200) {
            if (!hasOk(body)) return TRANSIENT;
            return has(body, "\"counted\":false") ? DUPLICATE : COUNTED;
        }
        if (httpStatus == 429 || httpStatus >= 500) return TRANSIENT;
        if (httpStatus >= 400) return REFUSED;
        // 3xx 不该出现（请求不跟随重定向），出现了就当这次没成。
        return TRANSIENT;
    }

    /** 服务端是否已经认可这颗心：两种情况都算，本地可以记「今天报过了」。 */
    public static boolean accepted(int verdict) {
        return verdict == COUNTED || verdict == DUPLICATE;
    }

    /** 是否值得在下次进入前台时再试一次。 */
    public static boolean retryable(int verdict) {
        return verdict == TRANSIENT;
    }

    private static boolean hasOk(String body) {
        return has(body, "\"ok\":true");
    }

    /** 空白容忍：服务端用 JSON.stringify，但中间层可能改写成带空格的形状。 */
    private static boolean has(String body, String needle) {
        if (body == null) return false;
        String compact = body.replaceAll("\\s+", "");
        return compact.contains(needle);
    }
}
