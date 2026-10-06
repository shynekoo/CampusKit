package io.qzxy.lite.preview;

import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.SmsLoginProtocol;
import io.qzxy.lite.core.WaterEvents;
import io.qzxy.lite.core.WaterOrder;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The five device-control routes. This file lives in the field-test source root and is only compiled
 * when the build is run with --field-water, so a read-only build cannot contain these endpoints at all
 * — not merely "does not call them". The route strings are absent from the read-only dex, which is
 * checked by scripts/verify_release.py.
 *
 * Every method sends exactly one request and never retries. A control request whose outcome is unknown
 * must be resolved by re-querying state, because repeating a start can bill twice and repeating a stop
 * can close an order that belongs to another account.
 *
 * Routes and request fields come from ui/d.java (MyApp.e == true) and BaseDeviceActivity.D3/G5/O4/Q4/U4
 * in 6.5.28; the shared fields (projectId, accountId, userId, telephone, telPhone, loginCode,
 * phoneSystem, version) come from BaseNetActivity.t0(map, true).
 */
final class WaterControl {
    /** Start: order/tcpDevice/downRate/rateOrder, fields snCode + xfModel. */
    static final String DOWN_RATE_PATH = "order/tcpDevice/downRate/rateOrder";
    /** Start confirmation fallback: order/tcpDevice/query/downRateResult, fields snCode + xfModel. */
    static final String DOWN_RATE_RESULT_PATH = "order/tcpDevice/query/downRateResult";
    /** Stop: order/tcpDevice/closeOrder, fields snCode + orderNo. */
    static final String CLOSE_ORDER_PATH = "order/tcpDevice/closeOrder";
    /** Stop confirmation fallback: order/tcpDevice/closeOrder/result/query, fields snCode + orderNo. */
    static final String CLOSE_RESULT_PATH = "order/tcpDevice/closeOrder/result/query";
    /** Bill: order/consumeOrder/result/query, field orderNo only. */
    static final String CONSUME_RESULT_PATH = "order/consumeOrder/result/query";

    static final List<String> ROUTES = Arrays.asList(DOWN_RATE_PATH, DOWN_RATE_RESULT_PATH,
        CLOSE_ORDER_PATH, CLOSE_RESULT_PATH, CONSUME_RESULT_PATH);

    private WaterControl() { }

    static final class UnsupportedChannel extends IOException {
        private static final long serialVersionUID = 1L;
        UnsupportedChannel() { super("服务器拒绝当前应用的开水通道，请使用“趣智校园”支付宝小程序（错误码 10000）。"); }
    }

    /**
     * The runtime half of the double gate. The build flag decides whether this code exists at all; this
     * check decides whether it may run right now. It is deliberately repeated by every entry point
     * rather than trusted once, and it happens before any socket is opened.
     */
    static void requireUsable(DeviceLookup lookup) throws IOException {
        if (!BuildFlags.WATER_CONTROL) throw new IOException("本版本不包含用水控制功能。");
        if (lookup == null || !lookup.ready) throw new IOException("设备资料尚未通过校验，不能发送用水指令。");
        if (!lookup.profile.hasSerial()) throw new IOException("设备编号缺失，不能发送用水指令。");
    }

    /** The device-side start request. One request; the caller must not send a second one. */
    static void start(AccountSession session, DeviceLookup lookup) throws IOException, JSONException {
        requireUsable(lookup);
        JSONObject reply = control(DOWN_RATE_PATH, session, lookup, lookup.profile.requestSerial(), null);
        int code = HttpForm.responseCode(reply);
        if (io.qzxy.lite.core.WaterChannel.requiresAlipay(code,
                reply.optString("errorMessage", reply.optString("message", "")))) throw new UnsupportedChannel();
        if (HttpForm.failed(reply, code)) throw HttpForm.rejection(reply, code);
        // BaseDeviceActivity.r/E3: success means dispatched; Q4 obtains the actual device result.
    }

    /** Read-only fallback that asks the device what the last start request resulted in. */
    static WaterEvents.Started startResult(AccountSession session, DeviceLookup lookup) throws IOException, JSONException {
        requireUsable(lookup);
        JSONObject reply = control(DOWN_RATE_RESULT_PATH, session, lookup, lookup.profile.requestSerial(), null);
        int code = HttpForm.responseCode(reply);
        if (HttpForm.failed(reply, code)) throw HttpForm.rejection(reply, code);
        return WaterEvents.Started.fromHttpConfirmation(DeviceDetails.fields(data(reply)));
    }

    /**
     * Dispatch one stop. Code 0 acknowledges dispatch; closeResult must confirm the device outcome.
     * Code 308 (already closed) is retained for explicit reconciliation.
     */
    static WaterEvents.Stopped closeOrder(AccountSession session, DeviceLookup lookup, String orderNo)
            throws IOException, JSONException {
        requireUsable(lookup);
        requireOrderNo(orderNo);
        JSONObject reply = control(CLOSE_ORDER_PATH, session, lookup, lookup.profile.requestSerial(), orderNo);
        return stopReply(reply);
    }

    /** Read-only fallback that asks the device whether the order really is closed. */
    static WaterEvents.Stopped closeResult(AccountSession session, DeviceLookup lookup, String orderNo)
            throws IOException, JSONException {
        requireUsable(lookup);
        requireOrderNo(orderNo);
        JSONObject reply = control(CLOSE_RESULT_PATH, session, lookup, lookup.profile.requestSerial(), orderNo);
        return stopReply(reply);
    }

    private static WaterEvents.Stopped stopReply(JSONObject reply) throws JSONException, IOException {
        int code = HttpForm.responseCode(reply);
        if (code == 0 && HttpForm.failed(reply, code)) throw HttpForm.rejection(reply, code);
        return WaterEvents.Stopped.of(code);
    }

    /**
     * The bill for one order. This route carries the order number and nothing else, so it is the only
     * control-family call that does not need a verified device profile; it is still refused without an
     * order number, and it never changes state.
     */
    static WaterEvents.Settled settle(AccountSession session, DeviceLookup lookup, String orderNo)
            throws IOException, JSONException {
        if (!BuildFlags.WATER_CONTROL) throw new IOException("本版本不包含用水控制功能。");
        requireOrderNo(orderNo);
        Map<String, String> fields = common(session, lookup);
        fields.put("orderNo", orderNo);
        JSONObject reply = HttpForm.request(SmsLoginProtocol.ORIGIN, CONSUME_RESULT_PATH,
            SmsLoginProtocol.form(fields), true);
        int code = HttpForm.responseCode(reply);
        if (HttpForm.failed(reply, code)) throw HttpForm.rejection(reply, code);
        JSONObject payload = data(reply);
        String returnedOrder = AccountProfile.text(payload, "orderNo");
        if (returnedOrder.isEmpty()) returnedOrder = AccountProfile.text(payload, "timeIds");
        if (!returnedOrder.isEmpty() && !orderNo.equals(returnedOrder)) throw new IOException("账单订单号不匹配。");
        return WaterEvents.Settled.parse(DeviceDetails.fields(payload));
    }

    /**
     * One request, one route, no retry. The route must be in this class's own whitelist, and the serial
     * number is only ever put into the request body.
     */
    private static JSONObject control(String path, AccountSession session, DeviceLookup lookup, String serial, String orderNo)
            throws IOException, JSONException {
        if (!ROUTES.contains(path)) throw new IllegalArgumentException("Unknown water control route");
        Map<String, String> fields = common(session, lookup);
        fields.put("snCode", serial);
        if (orderNo == null) fields.put("xfModel", "0");
        if (orderNo != null) fields.put("orderNo", orderNo);
        return HttpForm.request(SmsLoginProtocol.ORIGIN, path, SmsLoginProtocol.form(fields), true);
    }

    private static Map<String, String> common(AccountSession session, DeviceLookup lookup) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("phoneSystem", "android"); fields.put("version", "6.5.28");
        fields.put("userId", session.userId()); fields.put("loginCode", session.loginCode());
        if (lookup != null) {
            fields.put("projectId", lookup.account.projectId);
            if (!lookup.account.accountId.isEmpty()) fields.put("accountId", lookup.account.accountId);
            if (!lookup.account.telephone.isEmpty()) {
                fields.put("telephone", lookup.account.telephone); fields.put("telPhone", lookup.account.telephone);
            }
        }
        return fields;
    }

    private static void requireOrderNo(String orderNo) throws IOException {
        if (orderNo == null || !orderNo.matches("[A-Za-z0-9_-]{1,64}")) throw new IOException("订单号无效，不能结算或关水。");
    }

    private static JSONObject data(JSONObject reply) {
        JSONObject data = reply.optJSONObject("data");
        return data == null ? new JSONObject() : data;
    }

    /** Diagnostics only: proves this build registered the control routes at all. */
    static boolean isKnownRoute(String path) { return ROUTES.contains(path); }

    /** True when the read-only query says the device is free, which is the only state a start may follow. */
    static boolean mayStart(WaterOrder order) { return order != null && order.status() == WaterOrder.Status.NONE; }
}
