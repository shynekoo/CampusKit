package io.qzxy.lite.core;

/**
 * 匿名活跃统计的请求形状与字段约束。
 *
 * <p>客户端能发出去的东西只有两样：一个自己生成的随机 UUID，和当前版本号。没有设备标识、
 * 没有学号、没有日期、没有业务数据——这不是「发了再删」，是请求体里就没有那些字段的位置。</p>
 *
 * <p>约束与服务端 {@code server/analytics-worker/src/rules.js} 一一对应：UUID 36 字符、
 * 版本号 32 字符且字符集固定、整包 512 字节以内。客户端先自查一遍，是为了在明显不合规时
 * 干脆不发请求，而不是让服务端去教它。</p>
 */
public final class AnalyticsRequest {
    /** 上报一天一次。 */
    public static final String ACTIVE_PATH = "/v1/active";
    /** 读者关闭统计时删除历史记录。 */
    public static final String FORGET_PATH = "/v1/forget";
    /** UUID 的 36 个字符。 */
    public static final int MAX_INSTALL_ID = 36;
    /** 版本号上限：{@code 2.6.0-beta.11} 只有 14 个字符。 */
    public static final int MAX_VERSION = 32;
    /** 整个请求体的上限。合法请求不到 100 字节。 */
    public static final int MAX_BODY = 512;

    private AnalyticsRequest() { }

    /** 只接受 {@code 8-4-4-4-12} 形式的十六进制 UUID（大小写均可）。 */
    public static boolean validInstallId(String value) {
        if (value == null) return false;
        String text = value.trim();
        if (text.length() != MAX_INSTALL_ID) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean dash = i == 8 || i == 13 || i == 18 || i == 23;
            if (dash) {
                if (c != '-') return false;
            } else if (!isHex(c)) {
                return false;
            }
        }
        return true;
    }

    /** 版本号只允许字母、数字与 {@code . _ + -}，长度有限。空格、引号、控制字符都进不来。 */
    public static boolean validVersion(String value) {
        if (value == null) return false;
        String text = value.trim();
        if (text.isEmpty() || text.length() > MAX_VERSION) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '+' || c == '-';
            if (!allowed) return false;
        }
        return true;
    }

    /** 上报体：{@code {"installId":"…","version":"…"}}。字段不合规直接抛，不拼半个包。 */
    public static String body(String installId, String version) {
        if (!validInstallId(installId)) throw new IllegalArgumentException("installId");
        if (!validVersion(version)) throw new IllegalArgumentException("version");
        String json = "{\"installId\":\"" + installId.trim().toLowerCase(java.util.Locale.US)
            + "\",\"version\":\"" + version.trim() + "\"}";
        if (json.length() > MAX_BODY) throw new IllegalArgumentException("body too large");
        return json;
    }

    /** 删除体：只带标识，不带版本。 */
    public static String forgetBody(String installId) {
        if (!validInstallId(installId)) throw new IllegalArgumentException("installId");
        return "{\"installId\":\"" + installId.trim().toLowerCase(java.util.Locale.US) + "\"}";
    }

    /**
     * 拼出完整地址。调用方给的是构建期写死的 base（见 {@code BuildFlags.ANALYTICS_ENDPOINT}），
     * 路径只能取本类的两个常量——不接收任意路径，也就没有「顺手请求别的地址」的口子。
     */
    public static String endpoint(String base, String path) {
        if (base == null || base.trim().isEmpty()) throw new IllegalArgumentException("endpoint not configured");
        if (!ACTIVE_PATH.equals(path) && !FORGET_PATH.equals(path))
            throw new IllegalArgumentException("unknown path");
        String root = base.trim();
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        String full = root + path;
        // 复用更新那条已经测过的规则：只允许 HTTPS、必须有主机、不许带账号密码。
        UpdateUrls.requireHttps(full);
        return full;
    }

    /** 构建时没给服务地址就是没配置：设置页会如实说「尚未配置」，并且一个请求都不发。 */
    public static boolean configured(String base) {
        if (base == null || base.trim().isEmpty()) return false;
        try {
            endpoint(base, ACTIVE_PATH);
            return true;
        } catch (RuntimeException unusable) {
            return false;
        }
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
