package io.qzxy.lite.core;

import java.util.Map;

/**
 * Read-only view of the in-use order returned by order/tcpDevice/query/rateOrder/using.
 *
 * Evidence: the supplied 6.5.28 SnWaterResponse model and BaseDeviceActivity.W4. A reply code of 307
 * means the device is in use; ownership decides whether this account may continue. No command is
 * issued from this class.
 */
public final class WaterOrder {
    /**
     * NONE idle; MINE our own unfinished order; OTHER another account is using the device; PENDING the
     * device reports in-use but the reply does not say whose order it is, so it must be re-checked
     * rather than described as someone else's.
     */
    public enum Status { NONE, MINE, OTHER, PENDING }
    /** Reply code that means "device is currently in use" in the original client. */
    public static final int IN_USE_CODE = 307;

    private final Status status;
    private final String orderNo, prepaidYuan, maskedOrderNo;

    private WaterOrder(Status status, String orderNo, String prepaidYuan, String maskedOrderNo) {
        this.status = status; this.orderNo = orderNo; this.prepaidYuan = prepaidYuan;
        this.maskedOrderNo = maskedOrderNo;
    }

    /**
     * @param code reply code from the server (errorCode/code)
     * @param data data object fields, or null when the reply has no data
     * @throws IllegalArgumentException when the reply code is neither idle nor "in use"
     */
    public static WaterOrder parse(int code, Map<String, String> data) {
        if (code == 0) return new WaterOrder(Status.NONE, "", "", "");
        if (code != IN_USE_CODE) throw new IllegalArgumentException("服务器返回未知状态（" + code + "）");
        String owner = value(data, "isOwner");
        if ("false".equalsIgnoreCase(owner)) return new WaterOrder(Status.OTHER, "", "", "");
        if (!"true".equalsIgnoreCase(owner)) return new WaterOrder(Status.PENDING, "", "", "");
        String orderNo = value(data, "timeIds", "orderNo");
        if (!orderNo.matches("[A-Za-z0-9_-]{1,64}")) return new WaterOrder(Status.PENDING, "", "", "");
        return new WaterOrder(Status.MINE, orderNo, Money.fromThousandths(value(data, "perMoney", "preDeductMoney")), mask(orderNo));
    }

    public Status status() { return status; }
    /** Only for a future close/settle call; never shown to the user. */
    public String orderNo() { return orderNo; }
    public String prepaidYuan() { return prepaidYuan; }
    public String maskedOrderNo() { return maskedOrderNo; }
    /** True unless the device is confirmed idle: any in-use state, ours or unclear, blocks starting. */
    public boolean blocksStart() { return status != Status.NONE; }

    public String describe() {
        switch (status) {
            case MINE:
                return "检测到本账户的进行中订单。\n预扣金额：¥ " + (prepaidYuan.isEmpty() ? "未提供" : prepaidYuan)
                        + "\n订单号：****" + maskedOrderNo
                        + "\n请先在浴室确认水流状态，再决定继续用水或结算。";
            case OTHER:
                return "该设备正在被其他账户使用，暂不能开启用水。";
            case PENDING:
                return "该设备显示为使用中，但服务器未说明订单归属。\n"
                        + "请用官方客户端确认现场状态，或稍后重新查询；在此之前不要开水。";
            default:
                return "未发现进行中的用水订单，该设备当前空闲。";
        }
    }

    @Override public String toString() {
        return "WaterOrder[" + status + (maskedOrderNo.isEmpty() ? "" : ", ****" + maskedOrderNo) + "]";
    }

    private static String value(Map<String, String> data, String... keys) {
        if (data == null) return "";
        for (String key : keys) {
            String raw = data.get(key);
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) return trimmed;
        }
        return "";
    }

    /** Only the last four characters ever reach the screen. */
    private static String mask(String orderNo) {
        return orderNo.length() <= 4 ? orderNo : orderNo.substring(orderNo.length() - 4);
    }
}
