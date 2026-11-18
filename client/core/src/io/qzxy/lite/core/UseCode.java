package io.qzxy.lite.core;

import java.util.Map;

/**
 * 使用码详情，来自 account/useCode/new（6.5.28 CodeDetailBean）。
 *
 * <p>使用码是扫码之外的第二条开门方式：服务器发一个 8 位数字，前 5 位在使用码输入框里输，
 * 后 3 位是安全码，设备键盘上连着按完就能开启浴室或吹风机。它不经过设备订单接口，也不需要
 * 蓝牙或 MQTT，因此这个类只描述服务器已经给出的状态，不含任何控制指令。</p>
 *
 * <p>字段全部按原版 Gson 类命名；码的位数服务端没有保证，所以这里只接受 5–8 位纯数字，
 * 其余一律当解析失败，避免把「登录页 HTML」之类的脏值当成码显示出来。</p>
 */
public final class UseCode {
    private final String code, expiringTime, autoCloseDescription, regenerateHint, codeWarning;
    private final int status, autoCloseConfig, accountId, projectId;
    private final boolean configByProject, canRegenerate;

    private UseCode(String code, int status, String expiringTime, int autoCloseConfig,
                    String autoCloseDescription, boolean configByProject, String regenerateHint,
                    boolean canRegenerate, String codeWarning, int accountId, int projectId) {
        this.code = code; this.status = status; this.expiringTime = expiringTime;
        this.autoCloseConfig = autoCloseConfig; this.autoCloseDescription = autoCloseDescription;
        this.configByProject = configByProject; this.regenerateHint = regenerateHint;
        this.canRegenerate = canRegenerate; this.codeWarning = codeWarning;
        this.accountId = accountId; this.projectId = projectId;
    }

    /**
     * @param data the {@code data} object of a detail / status reply, or null
     * @throws IllegalArgumentException when a numeric field is not a number, or the code is not 5–8 digits
     */
    public static UseCode parse(Map<String, String> data) {
        String code = value(data, "useCode", "randomCode", "useCodeRandom");
        if (!code.isEmpty() && !code.matches("[0-9]{5,8}")) throw new IllegalArgumentException("使用码格式无法识别");
        return new UseCode(code, number(data, "useCodeStatus"), value(data, "useCodeExpiringTime"),
            number(data, "useCodeAutoCloseConfig"), value(data, "useCodeAutoCloseConfigDescription"),
            flag(data, "isConfigByProject"), value(data, "resetAvailabilityWarMark"),
            number(data, "resetAvailability") != 0, value(data, "useCodeAutoCloseConfigWarnMark"),
            number(data, "accountId"), number(data, "projectId"));
    }

    /** 1 = 开启，其余一律当作关闭；服务端目前只回 0/1。 */
    public boolean enabled() { return status == 1; }
    public String code() { return code; }
    public boolean hasCode() { return !code.isEmpty(); }
    public String expiringTime() { return expiringTime; }
    public int autoCloseConfig() { return autoCloseConfig; }
    public String autoCloseDescription() { return autoCloseDescription; }
    /** true = 开关由学校统一设置，页面不该给用户改。 */
    public boolean configByProject() { return configByProject; }
    /** 不能重新领取时，按钮下面要显示的原因。 */
    public String regenerateHint() { return regenerateHint; }
    public boolean canRegenerate() { return canRegenerate; }
    /** 切换开关前的确认文案；为空表示不需要确认。 */
    public String codeWarning() { return codeWarning; }
    public int accountId() { return accountId; }
    public int projectId() { return projectId; }

    /** 前 5 位，使用码输入框要的部分。 */
    public String usePart() { return code.length() >= 5 ? code.substring(0, 5) : code; }
    /** 后 3 位安全码；只有 8 位码才有单独的安全码。 */
    public String securityPart() { return code.length() == 8 ? code.substring(5) : ""; }

    /** 详情页顶部那一行状态。 */
    public String describe() {
        if (!enabled()) return "已关闭，不可用";
        if (!hasCode()) return "已开启，尚未领取使用码";
        return "已开启，可使用";
    }

    /** 设备上怎么按这一串数字，写清楚比只显示 8 个数字有用。 */
    public String instructions() {
        if (!hasCode()) return "领取后把 8 位数字输入设备键盘即可开启。";
        if (securityPart().isEmpty()) return "在设备上输入 " + code + " 即可开启。";
        return "在设备上输入 " + usePart() + "（使用码）+ " + securityPart() + "（安全码）即可开启。";
    }

    private static String value(Map<String, String> data, String... keys) {
        if (data == null) return "";
        for (String key : keys) {
            String raw = data.get(key);
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (!trimmed.isEmpty() && !"null".equals(trimmed)) return trimmed;
        }
        return "";
    }

    private static int number(Map<String, String> data, String key) {
        String raw = value(data, key);
        if (raw.isEmpty()) return 0;
        if (!raw.matches("-?[0-9]{1,9}")) throw new IllegalArgumentException("字段 " + key + " 不是数字");
        return Integer.parseInt(raw);
    }

    private static boolean flag(Map<String, String> data, String key) {
        String raw = value(data, key);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    @Override public String toString() {
        return "UseCode[" + describe() + (hasCode() ? ", " + usePart() + "+***" : "") + "]";
    }
}
