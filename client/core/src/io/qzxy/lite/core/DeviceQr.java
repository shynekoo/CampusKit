// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.util.Locale;

/** Offline parser for the MAC-bearing device QR branch in the supplied 6.5.28 app. */
public final class DeviceQr {
    private final String deviceType, bluetoothAddress, queryAddress;
    private final boolean ble;
    private DeviceQr(String type, String bluetooth, String query, boolean ble) {
        this.deviceType = type; this.bluetoothAddress = bluetooth; this.queryAddress = query; this.ble = ble;
    }
    public static DeviceQr parse(String raw) {
        if (raw == null || raw.length() > 2048 || raw.trim().isEmpty())
            throw new IllegalArgumentException("二维码内容为空或过长");
        for (int i = 0; i < raw.length(); i++)
            if (Character.isISOControl(raw.charAt(i))) throw new IllegalArgumentException("二维码包含无效字符");
        String[] fields = raw.split(",", -1);
        if (fields[0].equalsIgnoreCase("KLCXKJ-Recharge"))
            throw new IllegalArgumentException("这是充值码，请使用设备二维码");
        if (!(fields[0].equalsIgnoreCase("KLCXKJ-Water") || fields[0].startsWith("https://qzxy.ink/")))
            throw new IllegalArgumentException("暂不支持这种设备二维码");
        if (fields.length < 3 || !fields[1].matches("[A-Za-z0-9_-]{1,32}"))
            throw new IllegalArgumentException("设备二维码缺少类型或地址");
        if (fields[1].equalsIgnoreCase("T"))
            throw new IllegalArgumentException("洗衣液投放器不能作为独立用水设备");
        String address = fields[2].toUpperCase(Locale.ROOT);
        if (!address.matches("[0-9A-F]{12}|(?:[0-9A-F]{2}:){5}[0-9A-F]{2}"))
            throw new IllegalArgumentException("设备地址格式不正确");
        boolean ble = !address.startsWith("00");
        String query = address.startsWith("C0") ? "00" + address.substring(2) : address;
        String compact = address.replace(":", "");
        StringBuilder bluetooth = new StringBuilder();
        for (int i = 0; i < compact.length(); i += 2) {
            if (i != 0) bluetooth.append(':');
            bluetooth.append(compact, i, i + 2);
        }
        return new DeviceQr(fields[1], bluetooth.toString(), query, ble);
    }
    public String deviceType() { return deviceType; }
    public String bluetoothAddress() { return bluetoothAddress; }
    public String queryAddress() { return queryAddress; }
    /** Original app's prefix-based hint; actual transport must be confirmed against the device. */
    public boolean bleHint() { return ble; }
    @Override public String toString() { return "DeviceQr[device address hidden]"; }
}
