// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.util.Map;

/**
 * Strict, offline view of the device/info/mac payload.
 *
 * Field names and semantics come from the supplied 6.5.28 DeviceInfo model; nothing here performs
 * network calls. Unconfirmed values (transport type, online flag) are exposed raw and never used to
 * authorise a water command on their own.
 */
public final class DeviceProfile {
    /** bigTypeId value that the original app routes to the bathroom water page. */
    public static final int BATHROOM_TYPE = 4;
    private static final int UNKNOWN = -1;

    private final String projectId, deviceId, deviceName, projectName, location;
    private final String serial, withholdMoney, feeDescription, disableTip, prompt;
    private final String dsbTypeName;
    private final int dsbTypeId, chargeMode, isUse, transport, online;

    private DeviceProfile(String projectId, String deviceId, String deviceName, String projectName,
                          String location, String serial, String withholdMoney, String feeDescription,
                          String disableTip, String prompt, String dsbTypeName, int dsbTypeId,
                          int chargeMode, int isUse, int transport, int online) {
        this.projectId = projectId; this.deviceId = deviceId; this.deviceName = deviceName;
        this.projectName = projectName; this.location = location; this.serial = serial;
        this.withholdMoney = withholdMoney; this.feeDescription = feeDescription;
        this.disableTip = disableTip; this.prompt = prompt; this.dsbTypeName = dsbTypeName;
        this.dsbTypeId = dsbTypeId; this.chargeMode = chargeMode; this.isUse = isUse;
        this.transport = transport; this.online = online;
    }

    /**
     * @throws IllegalArgumentException when the payload is not a registered device.
     */
    public static DeviceProfile parse(Map<String, String> fields) {
        if (fields == null) throw new IllegalArgumentException("设备资料为空");
        String projectId = identifier(fields, "projectId", "PrjID");
        String deviceId = identifier(fields, "deviceId", "DevID");
        if (deviceId.isEmpty() || projectId.isEmpty()) throw new IllegalArgumentException("设备未登记");
        String location = join(
                text(fields, "buildingName", "LDName"), text(fields, "floorName", "LCName"),
                text(fields, "roomName", "FJName"));
        return new DeviceProfile(projectId, deviceId, text(fields, "deviceName", "DevName"),
                text(fields, "projectName", "PrjName"), location, serial(fields),
                money(fields, "withholdMoney"), text(fields, "feeDescription"),
                text(fields, "disableTip"), text(fields, "deviceOrderPagePromptText"),
                text(fields, "bigTypeName", "DsbName"), integer(fields, "bigTypeId", "Dsbtypeid"),
                integer(fields, "chargeMode"), integer(fields, "isUse", "IsUse"),
                integer(fields, "communicationTypeId", "devTypeStatus"),
                integer(fields, "onlineStatusId", "isOnline"));
    }

    public String projectId() { return projectId; }
    public String deviceId() { return deviceId; }
    public String deviceName() { return deviceName; }
    public String projectName() { return projectName; }
    public String location() { return location; }
    public String withholdMoney() { return withholdMoney; }
    public String feeDescription() { return feeDescription; }
    public String disableTip() { return disableTip; }
    public String prompt() { return prompt; }
    public int dsbTypeId() { return dsbTypeId; }
    public int chargeMode() { return chargeMode; }
    public int isUse() { return isUse; }
    /** Raw communicationTypeId; its meaning is not yet confirmed against a real device. */
    public int transportHint() { return transport; }
    /** Raw isOnline: 1 online, 0 offline, -1 not reported. */
    public int online() { return online; }
    /** True only when the serial number passed strict validation. */
    public boolean hasSerial() { return !serial.isEmpty(); }
    /**
     * Validated serial number, exposed only so a request body can be built. It must never be shown to
     * the user, written to a log, or persisted; {@link #describe(String)} and {@link #toString()}
     * deliberately omit it. Empty when validation failed.
     */
    public String requestSerial() { return serial; }
    public boolean bathroom() { return dsbTypeId == BATHROOM_TYPE; }
    public boolean usable() { return isUse == 1; }
    public boolean inProject(String expectedProjectId) {
        return expectedProjectId != null && !expectedProjectId.isEmpty() && expectedProjectId.equals(projectId);
    }

    /** Gate for any future control feature; read-only verification does not depend on it. */
    public boolean readyForWaterControl(String expectedProjectId) {
        return bathroom() && usable() && hasSerial() && inProject(expectedProjectId);
    }

    public String describe(String expectedProjectId) {
        StringBuilder out = new StringBuilder();
        out.append(deviceName.isEmpty() ? (bathroom() ? "浴室用水设备" : "已登记设备") : deviceName);
        if (!dsbTypeName.isEmpty()) out.append("（").append(dsbTypeName).append("）");
        out.append('\n');
        if (!location.isEmpty()) out.append("位置：").append(location).append('\n');
        out.append("校园：").append(projectName.isEmpty() ? "名称未提供" : projectName);
        out.append(inProject(expectedProjectId) ? "（属于当前账户）" : "（不属于当前账户）").append('\n');
        out.append("设备类型：").append(dsbTypeId == UNKNOWN ? "未提供" : bathroom() ? "浴室（4）" : "其他（" + dsbTypeId + "）").append('\n');
        out.append("计费方式：").append(chargeMode == 0 ? "按使用时长" : chargeMode == 17 ? "按使用量" : "未提供").append('\n');
        if (!withholdMoney.isEmpty()) out.append("预扣金额：¥ ").append(withholdMoney).append('\n');
        out.append("使用状态：").append(isUse == 1 ? "允许使用" : isUse == 0 ? "暂停使用" : "未提供");
        if (isUse == 0 && !disableTip.isEmpty()) out.append("（").append(disableTip).append("）");
        out.append('\n');
        out.append("联网状态：").append(online == 1 ? "在线" : online == 0 ? "离线" : "未提供").append('\n');
        out.append("设备编号：").append(hasSerial() ? "已识别" : "未提供").append('\n');
        if (!prompt.isEmpty()) out.append("提示：").append(prompt).append('\n');
        out.append('\n').append(readyForWaterControl(expectedProjectId)
                ? "资料校验通过，可用于后续用水验证。"
                : "资料校验未通过，暂不能进入用水验证。");
        return out.toString();
    }

    @Override public String toString() {
        return "DeviceProfile[project=" + projectId + ", device=" + deviceId + ", type=" + dsbTypeId + "]";
    }

    private static String identifier(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.get(key);
            if (value == null) continue;
            String trimmed = value.trim();
            if (trimmed.matches("[1-9][0-9]{0,8}")) return trimmed;
        }
        return "";
    }

    private static int integer(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.get(key);
            if (value == null) continue;
            String trimmed = value.trim();
            if (trimmed.matches("-?[0-9]{1,9}")) return Integer.parseInt(trimmed);
        }
        return UNKNOWN;
    }

    /** Decimal yuan string as sent by the server; never a thousandths integer. */
    private static String money(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.matches("[0-9]{1,9}(\\.[0-9]{1,3})?") ? trimmed : "";
    }

    private static String serial(Map<String, String> fields) {
        String value = fields.get("snCode");
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.matches("[A-Za-z0-9_-]{1,64}") ? trimmed : "";
    }

    private static String text(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.get(key);
            if (value == null) continue;
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < value.length() && out.length() < 80; i++) {
                char c = value.charAt(i);
                if (!Character.isISOControl(c)) out.append(c);
            }
            String cleaned = out.toString().trim();
            if (!cleaned.isEmpty()) return cleaned;
        }
        return "";
    }

    private static String join(String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part);
        }
        return out.toString();
    }
}
