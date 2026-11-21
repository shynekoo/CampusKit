// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 诊断日志的字段白名单与脱敏。
 *
 * <p>这里刻意不是「先记全文、再正则擦一遍」。那种做法只要有一个没预料到的字段名，原文就整段
 * 落盘了；而日志文件是要被复制、发出去的。这里的规则是反过来的：<b>只有白名单里的字段名能进
 * 文件，每个字段还各有取值形状</b>——数字字段必须是数字，枚举字段必须是 {@code [A-Za-z0-9_.:/-]}，
 * 其余一律写 {@code OTHER}。任何自由文本（请求体、响应体、Cookie、课程名、成绩）都没有入口，
 * 因为没有一个字段能承载它们。</p>
 *
 * <p>异常只留类型和调用栈的类名/方法名/行号：堆栈里的消息可能带着服务器原话，类名不会。
 * {@link #scrub} 只用于清理这类兜底文本，不是安全边界本身。</p>
 */
public final class DiagRedact {
    /** 单个字段值的上限。阶段名、错误码远小于这个数。 */
    public static final int MAX_VALUE = 72;
    /** 清理后的自由文本上限（异常消息兜底时才会用到）。 */
    public static final int MAX_TEXT = 160;
    /** 一段堆栈最多保留的帧数：够看出抛在哪里，又不至于把文件撑满。 */
    public static final int MAX_FRAMES = 12;

    /** 枚举型字段：允许字母、数字和少量分隔符，不允许空格与引号。 */
    private static final Set<String> TOKENS = set(
        "stage", "reason", "step", "method", "kind", "result", "slot", "error", "webview",
        "page", "version", "channel", "beta", "form");
    /** 机型名：厂商会带空格和括号，但只可能是 ASCII，长度也有限。 */
    private static final Set<String> DEVICES = set("device");
    /** 数字型字段：耗时、次数、HTTP 状态、业务码。 */
    private static final Set<String> NUMBERS = set(
        "duration", "attempts", "count", "bytes", "size", "ms", "http", "code", "status",
        "week", "term", "line", "lost", "courses", "frames");
    /** 接口路径：只允许路径字符，{@code ?}、{@code =} 一律不接受，查询串因此不可能混进来。 */
    private static final Set<String> ROUTES = set("route");

    private static final Pattern TOKEN_VALUE = Pattern.compile("[A-Za-z0-9_.:+-]{1,72}");
    private static final Pattern DEVICE_VALUE = Pattern.compile("[A-Za-z0-9 _.()+,-]{1,48}");
    private static final Pattern ROUTE_VALUE = Pattern.compile("[A-Za-z0-9_./-]{1,96}");
    private static final Pattern NUMBER_VALUE = Pattern.compile("-?[0-9]{1,9}");

    /** <code>key=value</code> 形式的凭据。键名本身留下，值整段去掉。 */
    private static final Pattern SECRET = Pattern.compile(
        "(?i)\\b(pass(?:word|wd)?|pwd|token|auth_?code|access_?token|refresh_?token|cookie"
            + "|session(?:id)?|jsessionid|utdid|open_?id|uid|user_?id|account|username|phone|mobile"
            + "|idcard|student_?id|学号|身份证|密码)\\b\\s*[=:]\\s*\"?([^,;&\\s\"]+)");
    /** 链接只保留 scheme 与 host：路径里可能带一次性票据，查询串更可能带凭据。 */
    private static final Pattern LINK = Pattern.compile("(https?://[^/\\s?]+)[^\\s]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    /** JWT：三段点分 base64，一眼能认出来，也不该出现在日志里。 */
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}");
    /** 裸手机号（11 位，1 开头），前面不能紧挨数字。 */
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])1[3-9][0-9]{9}(?![0-9])");
    /** 长数字串：学号、订单号、身份证都落在这里。 */
    private static final Pattern LONG_DIGITS = Pattern.compile("(?<![0-9])[0-9]{10,}(?![0-9])");
    /** 长十六进制串：设备指纹、摘要、密钥片段。 */
    private static final Pattern LONG_HEX = Pattern.compile("\\b[0-9a-fA-F]{32,}\\b");
    /** 长 base64 串：授权码、签名。 */
    private static final Pattern LONG_BASE64 = Pattern.compile("\\b[A-Za-z0-9+/]{40,}={0,2}");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}]");

    private DiagRedact() { }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    /**
     * 一个字段能不能进日志、进去长什么样。
     *
     * @return 清理后的值；{@code null} 表示这个字段被整条丢弃（未知键名或形状不合）
     */
    public static String value(String key, String raw) {
        if (key == null || raw == null) return null;
        if (TOKENS.contains(key)) {
            String text = raw.trim();
            if (text.isEmpty()) return null;
            return TOKEN_VALUE.matcher(text).matches() ? text : "OTHER";
        }
        if (NUMBERS.contains(key)) {
            String text = raw.trim();
            return NUMBER_VALUE.matcher(text).matches() ? text : null;
        }
        if (DEVICES.contains(key)) {
            String text = raw.trim();
            if (text.isEmpty()) return null;
            return DEVICE_VALUE.matcher(text).matches() ? text : "OTHER";
        }
        if (ROUTES.contains(key)) {
            String text = route(raw);
            return text.isEmpty() ? null : text;
        }
        // 白名单之外：不记录。不是「擦一遍」，是不给入口。
        return null;
    }

    /** 接口路径只留 path 部分，查询串整个丢掉。 */
    public static String route(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        int cut = text.indexOf('?');
        if (cut >= 0) text = text.substring(0, cut);
        int hash = text.indexOf('#');
        if (hash >= 0) text = text.substring(0, hash);
        if (!ROUTE_VALUE.matcher(text).matches()) return "";
        return text;
    }

    /** 兜底清理：任何文本经过这里之后，凭据、手机号、学号、链接参数都已经不在了。 */
    public static String scrub(String raw) {
        if (raw == null) return "";
        String text = CONTROL.matcher(raw).replaceAll(" ");
        text = SECRET.matcher(text).replaceAll("$1=…");
        text = LINK.matcher(text).replaceAll("$1/…");
        text = MAIL.matcher(text).replaceAll("…@…");
        text = JWT.matcher(text).replaceAll("…");
        text = PHONE.matcher(text).replaceAll("…");
        text = LONG_DIGITS.matcher(text).replaceAll("…");
        text = LONG_HEX.matcher(text).replaceAll("…");
        text = LONG_BASE64.matcher(text).replaceAll("…");
        text = text.replaceAll("\\s+", " ").trim();
        return clip(text, MAX_TEXT);
    }

    /**
     * 安全堆栈：只留「哪个类哪个方法第几行」，异常消息一个字都不带——服务器原话里常带着
     * 账号或参数，而定位并不需要它。
     */
    public static String stack(Throwable error, int maxFrames) {
        if (error == null || maxFrames <= 0) return "";
        StringBuilder out = new StringBuilder();
        int frames = 0;
        for (Throwable level = error; level != null; level = level.getCause()) {
            if (level != error) out.append("\nCaused by: ");
            out.append(level.getClass().getName());
            for (StackTraceElement frame : level.getStackTrace()) {
                if (frames >= maxFrames) break;
                frames++;
                out.append("\n    at ").append(frame.getClassName()).append('.')
                    .append(frame.getMethodName()).append('(')
                    .append(frame.getFileName() == null ? "?" : frame.getFileName()).append(':')
                    .append(frame.getLineNumber()).append(')');
            }
            if (frames >= maxFrames) break;
        }
        return out.toString();
    }

    /**
     * 失败原因分类。日志里写的是这里给出的稳定代码，不是异常消息，所以既不会随文案变化，
     * 也不会把服务器原话带进文件。
     */
    public static String reason(Throwable error) {
        if (error == null) return "UNKNOWN";
        for (Throwable level = error; level != null; level = level.getCause()) {
            String name = level.getClass().getSimpleName();
            if (name.contains("SocketTimeout") || name.contains("Timeout")) return "NETWORK_TIMEOUT";
            if (name.contains("UnknownHost")) return "DNS";
            if (name.contains("SSL") || name.contains("Certificate") || name.contains("Cert")) return "TLS";
            if (name.contains("Connect")) return "NETWORK_CONNECT";
            if (name.contains("JSON")) return "PARSE";
            if ("NotSignedIn".equals(name)) return "SESSION_EXPIRED";
            if ("BadPassword".equals(name)) return "AUTH_REJECTED";
            if ("CaptchaNeeded".equals(name)) return "CAPTCHA";
            if ("HttpFailure".equals(name)) return "HTTP_STATUS";
            if ("ResponseFailure".equals(name)) return "SERVER_REJECTED";
            if ("SessionExpired".equals(name)) return "SESSION_EXPIRED";
            if ("Refused".equals(name)) return "AUTH_CANCELLED";
            if (name.contains("Security") || name.contains("Cipher")) return "CRYPTO";
            if (level instanceof IllegalArgumentException) return "INVALID_INPUT";
        }
        return error.getClass().getSimpleName().contains("IO") ? "NETWORK" : "UNKNOWN";
    }

    public static String clip(String text, int limit) {
        if (text == null) return "";
        return text.length() <= limit ? text : text.substring(0, Math.max(0, limit - 1)) + "…";
    }
}
