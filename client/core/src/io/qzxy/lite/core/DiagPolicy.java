package io.qzxy.lite.core;

/**
 * 诊断日志的启用策略。放在 core 是因为它需要被 JVM 断言锁住：编进包里不等于默认记录。
 *
 * <p>把运行时默认写成显式的 false，而不是靠「SharedPreferences 还没有这个键」这类偶然行为，
 * 是为了让正式版即使因为某个构建变体带上了诊断代码，也不会在用户不知情时抓取支付宝 SDK 日志。
 * 想记录必须有明确的开启动作。</p>
 */
public final class DiagPolicy {
    /** 即使诊断代码被编入，运行期默认也不记录。 */
    public static final boolean ENABLED_BY_DEFAULT = false;

    private DiagPolicy() { }

    /**
     * 从持久化值解析开关。任何缺失、空值或未知形式都落到默认（关闭），只有明确的
     * "on" 才开启——避免把历史遗留的任意非空字符串当成授权。
     */
    public static boolean defaultEnabled(String persisted) {
        if (persisted == null || persisted.length() == 0) return ENABLED_BY_DEFAULT;
        return "on".equals(persisted);
    }

    /** 显式动作才开；这条把「用户主动点开」与「默认」分开，供测试锁定。 */
    public static boolean explicitlyEnabled(boolean requested) {
        return requested;
    }
}
