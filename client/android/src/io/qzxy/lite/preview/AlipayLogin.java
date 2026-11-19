package io.qzxy.lite.preview;

import android.app.Activity;
import com.alipay.sdk.app.AuthTask;
import java.util.Map;

/**
 * 支付宝「快捷登录」。
 *
 * <p>慧生活 3.1.5 的登录页里就带着这段授权串：交给支付宝，授权页会显示「慧生活798」，
 * 用户在支付宝里确认后拿回一个 auth_code。服务器用这个 code 换 uid/token，和官方客户端
 * 走的是同一条路（官方把它叫 openCode）。</p>
 *
 * <p>这段串是慧生活自己的应用身份，不是我们的；授权页上写的是他们的名字，风险也算在他们的
 * appid 上。这一点必须让使用者知道，登录页已经写明。</p>
 */
final class AlipayLogin {
    /** 官方 3.1.5 里写死的那一段（签名原样 344 字符，源码里的换行空格已去掉）。 */
    private static final String AUTH_INFO = "apiname=com.alipay.account.auth&app_id=2021002141696106&app_name=mc&auth_type=AUTHACCOUN" 
        + "T&biz_type=openservice&methodname=alipay.open.auth.sdk.code.get&pid=2088631403836084&pro" 
        + "duct_id=APP_FAST_LOGIN&scope=kuaijie&sign_type=RSA2&target_id=8277c93c196e4807a2d12dc226" 
        + "acbdc3&sign=dX29EK2ly3nRKHXfVaNMoFS6ZCoZYW5+/Sz1CCdWjINjfwduj8p6PqXzswYb0yZwdwvfEQhW2fdk" 
        + "dBw4nSUbpWwPRir4psIAHCCVijAR/TdVdM91+5fWHkMnxcKjJfX0y9oKQorWLi0nXlCJuBeT37aThWzr/T+M/fTv" 
        + "JnTuwdgu3dJOmc7DdTPmdtFcNy9CBOQBwnKFgBEFD/dZOHDMnQvfgIITPujqU6Z0syKDPEcSxOm7QuCpbKGMzQ0t" 
        + "J9Z4ABBSR6tEy/fwN3ArYLEZtgHft6qPW/6WRy27SsugemfaOiSakNM9NQcnrnfsp4Ave1+6CgV/BqfPGlOSXQAR" 
        + "kA==";

    /** 授权没走完，带一句能直接显示给读者的话。 */
    static final class Refused extends Exception {
        Refused(String message) { super(message); }
    }

    /**
     * 唤起支付宝并等它回来。必须从工作线程调用：SDK 内部是同步阻塞的，会绑定支付宝的服务、
     * 必要时退回 H5 授权页，整个过程和网络请求一样不能占着主线程。
     */
    static String code(Activity activity) throws Refused {
        Map<String, String> result;
        String trace = Diag.trace();
        long mark = Diag.mark();
        Diag.event(Diag.INFO, "AUTH", "auth.start", "trace", trace);
        try {
            result = new AuthTask(activity).authV2(AUTH_INFO, true);
        } catch (Throwable error) {
            // 记类型与阶段，不记 SDK 原文：那串里逐字带着授权码。
            Diag.failure("AUTH", "auth.failed", "AUTH_CANCELLED", error, trace);
            throw new Refused("没能唤起支付宝，请确认已安装并登录支付宝后重试。");
        }
        String status = value(result == null ? null : result.get("resultStatus"));
        String body = value(result == null ? null : result.get("result"));
        String memo = value(result == null ? null : result.get("memo"));
        // 这一行是整份记录里最关键的一行：有它，说明 SDK 回到了 App；没有它，就是回调丢了。
        Diag.event(Diag.INFO, "AUTH", "auth.returned", "trace", trace,
            "status", status, "duration", Diag.since(mark));
        if (!"9000".equals(status)) {
            Diag.event(Diag.WARN, "AUTH", "auth.failed", "trace", trace,
                "reason", "AUTH_CANCELLED", "status", status);
            if ("6001".equals(status)) throw new Refused("已取消支付宝授权。");
            throw new Refused(memo.isEmpty()
                ? "支付宝授权未完成（状态 " + (status.isEmpty() ? "未知" : status) + "）。"
                : memo);
        }
        String code = param(body, "auth_code");
        String resultCode = param(body, "result_code");
        if (!"200".equals(resultCode) || code.isEmpty()) {
            Diag.event(Diag.ERROR, "AUTH", "auth.failed", "trace", trace,
                "reason", "PARSE", "code", resultCode, "stage", "no_auth_code");
            throw new Refused("支付宝已经返回，但没有给出授权码（结果 " + (resultCode.isEmpty() ? "未知" : resultCode) + "）。");
        }
        return code;
    }

    private static String value(String text) { return text == null ? "" : text; }

    /** result 是一串 a=b&c=d，auth_code 可能带百分号转义。 */
    private static String param(String body, String key) {
        if (body == null) return "";
        for (String pair : body.split("&")) {
            int split = pair.indexOf('=');
            if (split <= 0) continue;
            if (!pair.substring(0, split).equals(key)) continue;
            return decode(pair.substring(split + 1));
        }
        return "";
    }

    /** 只解 %XX：授权码里的加号是加号，不是空格。 */
    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8");
        } catch (Exception error) {
            return value;
        }
    }

    private AlipayLogin() { }
}
