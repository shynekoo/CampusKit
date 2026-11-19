package io.qzxy.lite.core;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一条诊断事件：日期时间、等级、模块、稳定事件代码，加上少量白名单字段。
 *
 * <p>格式固定成一行，人眼可读、程序可解析：</p>
 *
 * <pre>
 * 2026-11-19 21:30:04.018 ERROR [TIMETABLE] load.failed trace=7f2c1a90 thread=main reason=NETWORK_TIMEOUT duration=2011
 * </pre>
 *
 * <p>为什么不用自由拼接的字符串：日志一旦是「一句话」，过滤、按等级统计、按模块聚合都只能靠
 * 正则猜；而稳定的事件代码让「课表加载失败」成为一个可查询的事实，而不是一段描述。</p>
 *
 * <p>格式化时再过一遍 {@link DiagRedact}：调用方即使传了没在白名单里的键，也只是被丢掉，
 * 不会写入文件。安全边界在数据出口，不在每个调用点。</p>
 */
public final class DiagEvent {
    public static final int INFO = 1, WARN = 2, ERROR = 3, CRASH = 4;

    private static final String[] NAMES = {"", "INFO", "WARN", "ERROR", "CRASH"};

    /** 时间戳前缀，界面按它切条目；也用于判断一行是不是新记录。 */
    private static final Pattern RECORD = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+"
            + "(INFO|WARN|ERROR|CRASH)\\s+\\[([A-Z0-9_]{2,12})\\]\\s+([a-z0-9_.]{1,48})(.*)$");

    private static final ThreadLocal<SimpleDateFormat> STAMP = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        }
    };

    public final int level;
    public final String date;
    public final String time;
    public final String module;
    public final String code;
    /** 记录行本身（不含随后的堆栈续行）。 */
    public final String line;
    /** 事件后的 {@code key=value} 片段，已脱敏；可能为空串。 */
    public final String tail;

    private DiagEvent(int level, String date, String time, String module, String code,
                      String tail, String line) {
        this.level = level;
        this.date = date;
        this.time = time;
        this.module = module;
        this.code = code;
        this.tail = tail;
        this.line = line;
    }

    public static String name(int level) {
        return level >= INFO && level <= CRASH ? NAMES[level] : "INFO";
    }

    public static int levelOf(String name) {
        for (int i = INFO; i <= CRASH; i++) if (NAMES[i].equals(name)) return i;
        return INFO;
    }

    /** 等级比较用：筛选「错误及以上」不必枚举。 */
    public int rank() { return level; }

    public boolean atLeast(int level) { return this.level >= level; }

    /**
     * 组装一行。
     *
     * @param millis 本地时间的毫秒值
     * @param fields 只保留白名单字段，顺序按传入顺序（用 {@link LinkedHashMap}）
     */
    public static String format(long millis, int level, String module, String code,
                                String trace, String thread, Map<String, String> fields) {
        StringBuilder out = new StringBuilder(160);
        out.append(STAMP.get().format(new Date(millis)))
            .append(' ').append(name(level))
            .append(" [").append(token(module, "APP"))
            .append("] ").append(token(code, "unknown"));
        if (trace != null && !trace.isEmpty()) out.append(" trace=").append(token(trace, "?"));
        if (thread != null && !thread.isEmpty()) out.append(" thread=").append(token(thread, "?"));
        if (fields != null) {
            for (Map.Entry<String, String> field : fields.entrySet()) {
                String value = DiagRedact.value(field.getKey(), field.getValue());
                if (value == null) continue;
                out.append(' ').append(field.getKey()).append('=').append(value);
            }
        }
        String line = out.toString();
        return line.length() > 480 ? line.substring(0, 480) : line;
    }

    /** 解析一行；续行（堆栈、快照明细）返回 {@code null}。 */
    public static DiagEvent parse(String line) {
        if (line == null) return null;
        Matcher found = RECORD.matcher(line);
        if (!found.matches()) return null;
        String tail = found.group(6) == null ? "" : found.group(6).trim();
        return new DiagEvent(levelOf(found.group(3)), found.group(1), found.group(2),
            found.group(4), found.group(5), tail, line);
    }

    /** 读取一个字段值；没有就返回空串。字段之间以空格分隔，值里不含空格。 */
    public String field(String key) {
        if (key == null || tail.isEmpty()) return "";
        for (String part : tail.split(" ")) {
            int split = part.indexOf('=');
            if (split > 0 && part.substring(0, split).equals(key)) return part.substring(split + 1);
        }
        return "";
    }

    /** 复制一份带额外字段的事件，顺序字段在前——界面展开时先看结论。 */
    public Map<String, String> fields() {
        Map<String, String> map = new LinkedHashMap<>();
        if (tail.isEmpty()) return map;
        for (String part : tail.split(" ")) {
            int split = part.indexOf('=');
            if (split > 0 && split + 1 < part.length()) {
                map.put(part.substring(0, split), part.substring(split + 1));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /** 模块与事件代码的形状约束，防止一行日志被伪造出第二个假事件。 */
    private static String token(String value, String fallback) {
        if (value == null || value.isEmpty()) return fallback;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '.' || c == '-' || c == ':' || c == '+';
            if (!ok) return fallback;
        }
        return value.length() > 48 ? value.substring(0, 48) : value;
    }
}
