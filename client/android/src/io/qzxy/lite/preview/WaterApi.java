// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.SmsLoginProtocol;
import io.qzxy.lite.core.WaterOrder;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Device order routes. Stage 0.5.0 ships the read-only status query only; the start, stop and
 * settlement routes are deliberately absent so no build in this stage can send a control request.
 *
 * Every route is listed explicitly (no shared whitelist with the account API) and nothing is ever
 * retried automatically: an unknown outcome must be re-checked, not repeated.
 */
final class WaterApi {
    /** Read-only: reports whether this device already has an order, ours or someone else's. */
    static final String ORDER_USING_PATH = "order/tcpDevice/query/rateOrder/using";
    private static final List<String> ROUTES = Arrays.asList(ORDER_USING_PATH);

    private WaterApi() { }

    /**
     * @return the current order state for this device
     * @throws IOException when the server rejects the query, or when the device was never verified
     * @throws IllegalArgumentException when the reply carries a state this build cannot interpret
     */
    static WaterOrder orderStatus(AccountSession session, DeviceLookup lookup) throws IOException, JSONException {
        if (lookup == null || !lookup.ready) throw new IOException("设备资料尚未通过校验，暂不能查询订单状态");
        String serial = lookup.profile.requestSerial();
        if (serial.isEmpty()) throw new IOException("设备编号缺失，暂不能查询订单状态");
        JSONObject reply = request(ORDER_USING_PATH, session, lookup.account, serial);
        int code = HttpForm.responseCode(reply);
        if (HttpForm.failed(reply, code) && code != WaterOrder.IN_USE_CODE) throw HttpForm.rejection(reply, code);
        JSONObject data = reply.optJSONObject("data");
        return WaterOrder.parse(code, DeviceDetails.fields(data == null ? new JSONObject() : data));
    }

    static WaterOrder recordedOrderStatus(AccountSession session, AccountProfile account, io.qzxy.lite.core.PendingUse record) throws IOException, JSONException {
        if(record==null || !record.projectId().equals(session.projectId()) || !record.projectId().equals(account.projectId)
            || (record.isCommand() && !record.belongsTo(account.accountId))) throw new IOException("请登录旧记录所属账户后再核查");
        JSONObject reply=request(ORDER_USING_PATH,session,account,record.requestSerial());
        int code=HttpForm.responseCode(reply);
        if(HttpForm.failed(reply,code) && code!=WaterOrder.IN_USE_CODE)throw HttpForm.rejection(reply,code);
        JSONObject data=reply.optJSONObject("data");
        return WaterOrder.parse(code,DeviceDetails.fields(data==null?new JSONObject():data));
    }

    private static JSONObject request(String path, AccountSession session, AccountProfile account, String serial)
            throws IOException, JSONException {
        if (!ROUTES.contains(path)) throw new IllegalArgumentException("Unknown water route");
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("phoneSystem", "android"); fields.put("version", "6.5.28");
        fields.put("userId", session.userId()); fields.put("loginCode", session.loginCode());
        fields.put("projectId", account.projectId);
        if (!account.accountId.isEmpty()) fields.put("accountId", account.accountId);
        if (!account.telephone.isEmpty()) {
            fields.put("telephone", account.telephone); fields.put("telPhone", account.telephone);
        }
        fields.put("snCode", serial); fields.put("xfModel", "0");
        return HttpForm.request(SmsLoginProtocol.ORIGIN, path, SmsLoginProtocol.form(fields), true);
    }

    /**
     * False for the whole of stage 0.5.0. Kept as a named switch so a future stage has to change it
     * deliberately, and so a test can prove that no control path is reachable in this build.
     */
    static boolean controlEnabled() { return false; }

    /** Diagnostics only: lets a test assert that the control routes are not registered yet. */
    static boolean isKnownRoute(String path) { return ROUTES.contains(path); }
}
