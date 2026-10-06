package io.qzxy.lite.core;

import java.util.Map;

/**
 * Response shapes for the start confirmation and the settlement, reconstructed from 6.5.28.
 *
 * Both come from two interchangeable sources in the original client: an MQTT push and an HTTP
 * fallback query with identical payloads. Only the HTTP path is implemented here (MQTT is a later
 * stage), which is why every field is parsed strictly from a plain string map.
 */
public final class WaterEvents {
    /** downRate result codes: 0 and 36 both mean the device accepted the order. */
    public static final int START_ACCEPTED = 0, START_NO_BALANCE = 2, START_ACCEPTED_ALT = 36;
    private static final long MAX_ACCOUNT_ID = 999999999999999L;

    private WaterEvents() { }

    /** The device-side answer to a start request; carries the order number the stop call needs. */
    public static final class Started {
        private final int result, accountType;
        private final long accountId;
        private final String orderNo, prepaidYuan, maskedOrderNo, deviceSncode;

        private Started(int result, long accountId, int accountType, String orderNo, String prepaidYuan,
                        String maskedOrderNo, String deviceSncode) {
            this.result = result; this.accountId = accountId; this.accountType = accountType;
            this.orderNo = orderNo; this.prepaidYuan = prepaidYuan; this.maskedOrderNo = maskedOrderNo;
            this.deviceSncode = deviceSncode;
        }

        /**
         * @throws IllegalArgumentException when the payload cannot be interpreted, so an unclear
         *         answer never gets mistaken for a confirmed one
         */
        public static Started parse(Map<String, String> data) {
            if (data == null) throw new IllegalArgumentException("开启结果缺少数据");
            String rawResult = value(data, "result");
            if (!rawResult.matches("-?[0-9]{1,9}")) throw new IllegalArgumentException("开启结果缺少状态码");
            String rawAccount = value(data, "accountId");
            if (!rawAccount.matches("[0-9]{1,15}")) throw new IllegalArgumentException("开启结果缺少账户标识");
            long accountId = Long.parseLong(rawAccount);
            if (accountId <= 0 || accountId > MAX_ACCOUNT_ID) throw new IllegalArgumentException("开启结果账户标识异常");
            String rawType = value(data, "accountType");
            int accountType = rawType.matches("-?[0-9]{1,9}") ? Integer.parseInt(rawType) : 0;
            String orderNo = value(data, "timeIds", "orderNo");
            if (!orderNo.isEmpty() && !orderNo.matches("[A-Za-z0-9_-]{1,64}"))
                throw new IllegalArgumentException("开启结果的订单号异常");
            return new Started(Integer.parseInt(rawResult), accountId, accountType, orderNo,
                Money.fromThousandths(value(data, "perMoney", "preDeductMoney")),
                orderNo.length() <= 4 ? orderNo : orderNo.substring(orderNo.length() - 4),
                value(data, "deviceSncode", "deviceSnCode", "snCode"));
        }

        public int result() { return result; }
        public long accountId() { return accountId; }
        /** 2 marks a shared/family account in the original model; its exact meaning is unconfirmed. */
        public int accountType() { return accountType; }
        /** Only for the stop call; never displayed. */
        public String orderNo() { return orderNo; }
        public String prepaidYuan() { return prepaidYuan; }
        public String maskedOrderNo() { return maskedOrderNo; }
        /** Only used to check the confirmation belongs to the device we asked about. */
        public String deviceSncode() { return deviceSncode; }
        public boolean accepted() { return result == START_ACCEPTED || result == START_ACCEPTED_ALT; }
        public boolean noBalance() { return result == START_NO_BALANCE; }

        /**
         * The original HTTP R4 accepts a matching id OR accountType == 2. Here the id must match;
         * accountType alone never authorizes controlling a different account's order.
         */
        public boolean belongsTo(long ourAccountId) { return ourAccountId > 0 && accountId == ourAccountId; }

        /** HTTP result queries are already scoped by snCode; reject a conflicting payload identity. */
        public boolean usableFor(long owner, String serial) {
            return accepted() && belongsTo(owner) && !orderNo.isEmpty() && serial != null
                && !serial.isEmpty() && (deviceSncode.isEmpty() || deviceSncode.equals(serial));
        }

        /** Original HTTP fallback R4 uses account and order, with no mandatory MQTT result field. */
        public static Started fromHttpConfirmation(Map<String, String> data) {
            if (data == null) throw new IllegalArgumentException("开启结果缺少数据");
            Map<String, String> copy = new java.util.LinkedHashMap<>(data);
            if (!copy.containsKey("result")) copy.put("result", "0");
            Started value = parse(copy);
            if (value.accepted() && value.orderNo.isEmpty()) throw new IllegalArgumentException("开启结果缺少订单号");
            return value;
        }

        public String describe() {
            if (noBalance()) return "开启失败：账户余额不足。";
            if (!accepted()) return "开启未被设备接受（状态码 " + result + "）。";
            return "设备已确认开启。\n预扣金额：¥ " + (prepaidYuan.isEmpty() ? "未提供" : prepaidYuan)
                + (orderNo.isEmpty() ? "" : "\n订单号：****" + maskedOrderNo);
        }

        @Override public String toString() {
            return "Started[result=" + result + ", order=" + (maskedOrderNo.isEmpty() ? "none" : "****" + maskedOrderNo) + "]";
        }
    }

    /**
     * Close-result query outcome, expressed in the reply's error code. The dispatch route uses these
     * codes too, but its 0 is only an acknowledgement: callers must still run the result query.
     */
    public static final class Stopped {
        /** 0 the device accepted the stop; 308 the device was already closed; 309 the order number was wrong. */
        public static final int ACCEPTED = 0, ALREADY_CLOSED = 308, BAD_ORDER_NO = 309;
        private final int code;

        private Stopped(int code) { this.code = code; }

        public static Stopped of(int responseCode) { return new Stopped(responseCode); }

        public int code() { return code; }
        public boolean accepted() { return code == ACCEPTED; }
        /** The water is already off, so the goal of the stop request is met even though it was refused. */
        public boolean alreadyClosed() { return code == ALREADY_CLOSED; }
        /** True when the water may be treated as off; anything else must be reconciled, not retried. */
        public boolean satisfied() { return accepted() || alreadyClosed(); }
        public boolean badOrderNo() { return code == BAD_ORDER_NO; }

        public String describe() {
            if (accepted()) return "设备已确认关水。";
            if (alreadyClosed()) return "设备报告该订单已经关闭，无需重复关水。请前往账单核对本次消费。";
            if (badOrderNo()) return "关水被拒绝：订单号不正确。请在现场确认水流后重新查询订单状态。";
            return "关水未被设备确认（状态码 " + code + "）。请在现场确认水流，不要直接重复关水。";
        }

        @Override public String toString() { return "Stopped[code=" + code + "]"; }
    }

    /** The bill produced after the device reports the order closed. */
    public static final class Settled {
        private final String duration, prepaidYuan, chargedYuan, refundedYuan;

        private Settled(String duration, String prepaidYuan, String chargedYuan, String refundedYuan) {
            this.duration = duration; this.prepaidYuan = prepaidYuan;
            this.chargedYuan = chargedYuan; this.refundedYuan = refundedYuan;
        }

        /**
         * @throws IllegalArgumentException when no usable amount is present, because a bill without an
         *         amount must not be shown as a completed settlement
         */
        public static Settled parse(Map<String, String> data) {
            if (data == null) throw new IllegalArgumentException("结算结果缺少数据");
            String charged = Money.fromThousandths(value(data, "upMoney"));
            String refunded = Money.fromThousandths(value(data, "upLeadMoney"));
            if (charged.isEmpty() && refunded.isEmpty()) throw new IllegalArgumentException("结算结果缺少金额");
            String duration = value(data, "fishTime");
            if (duration.length() > 40 || duration.matches(".*[\\p{Cntrl}].*")) duration = "";
            return new Settled(duration, Money.fromThousandths(value(data, "perMoney")), charged, refunded);
        }

        /** Server-formatted duration text, shown as-is. */
        public String duration() { return duration; }
        public String prepaidYuan() { return prepaidYuan; }
        /** Actual charge, in yuan. */
        public String chargedYuan() { return chargedYuan; }
        /** Pre-deduction returned to the account, in yuan. */
        public String refundedYuan() { return refundedYuan; }

        public String describe() {
            StringBuilder out = new StringBuilder("结算完成。");
            if (!duration.isEmpty()) out.append("\n用水时长：").append(duration);
            if (!chargedYuan.isEmpty()) out.append("\n实际扣费：¥ ").append(chargedYuan);
            if (!prepaidYuan.isEmpty()) out.append("\n预扣金额：¥ ").append(prepaidYuan);
            if (!refundedYuan.isEmpty()) out.append("\n退还预扣：¥ ").append(refundedYuan);
            out.append("\n请以趣智校园账单为准。");
            return out.toString();
        }

        @Override public String toString() {
            return "Settled[charged=" + chargedYuan + ", refunded=" + refundedYuan + "]";
        }
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
}
