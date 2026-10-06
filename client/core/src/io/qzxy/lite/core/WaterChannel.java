package io.qzxy.lite.core;

/** Only the observed explicit channel refusal qualifies; code 10000 alone is insufficient. */
public final class WaterChannel {
    private WaterChannel() { }
    public static boolean requiresAlipay(int code, String message) {
        return code == 10000 && message != null && message.contains("当前项目不支持该应用")
            && message.contains("支付宝小程序");
    }
}
