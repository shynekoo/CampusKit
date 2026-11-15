package io.qzxy.lite.preview;

import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.RechargeSignature;
import io.qzxy.lite.core.SmsLoginProtocol;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Alipay recharge order creation, reconstructed from 6.5.28 {@code RechargeActivity.U2} and
 * {@code ui.h.n()}.
 *
 * <p>Two route families exist because the server was migrated per campus: the legacy
 * {@code wallet/recharge/aliPay} on wfwapi and the newer {@code settlement/alipay/recharge/balance}
 * on v3-api. Which one applies comes from {@code project/info/triple}, exactly as the original
 * {@code MyApp.e} flag does — it is never guessed.</p>
 *
 * <p>Only Alipay is implemented. WeChat app-pay binds the order to the merchant's signing
 * certificate, and UnionPay/cmb/juhe all need their own SDK, so this class cannot create those
 * orders; the screen tells the user to finish those in the official app.</p>
 *
 * <p>Nothing here is retried automatically. An order string is only produced for a request the
 * server accepted, and paying it still requires an explicit confirmation inside Alipay.</p>
 */
final class RechargeApi {
    private static final String LEGACY_ORIGIN = "https://wfwapi.china-qzxy.cn/";
    private static final String MIGRATED_ORIGIN = "https://v3-api.china-qzxy.cn/";
    private static final String LEGACY_PATH = "wallet/recharge/aliPay";
    private static final String MIGRATED_PATH = "settlement/alipay/recharge/balance";
    private static final List<String> ROUTES = Arrays.asList(LEGACY_PATH, MIGRATED_PATH);
    /** Integer yuan only: the original custom-amount box parses Integer.valueOf. */
    private static final Pattern AMOUNT = Pattern.compile("[1-9][0-9]{0,3}");

    private RechargeApi() { }

    static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!AMOUNT.matcher(value).matches())
            throw new IllegalArgumentException("请输入 1–1000 之间的整数金额（元）");
        int yuan = Integer.parseInt(value);
        if (yuan < 1 || yuan > 1000) throw new IllegalArgumentException("请输入 1–1000 之间的整数金额（元）");
        return String.valueOf(yuan);
    }

    /**
     * Creates an Alipay order and returns the signed order string. The caller must not treat this as
     * payment: only Alipay's own result confirms the recharge.
     */
    static String alipayOrder(AccountSession session, AccountProfile profile, String amount, boolean migrated)
            throws IOException, JSONException {
        String yuan = normalize(amount);
        if (!profile.hasAccount()) throw new IOException("当前账号没有返回校园账户，暂不能充值");
        if (profile.projectId.isEmpty() || profile.projectId.equals("0"))
            throw new IOException("登录信息缺少校园编号，请重新登录");
        for (int attempt = 0; attempt < 2; attempt++) {
            java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
            fields.put("phoneSystem", "android");
            fields.put("version", "6.5.28");
            fields.put("projectId", profile.projectId);
            fields.put("accountId", profile.accountId);
            fields.put("loginCode", session.loginCode());
            if (!profile.telephone.isEmpty()) {
                fields.put("telephone", profile.telephone);
                fields.put("telPhone", profile.telephone);
            }
            fields.put("payWay", "2");
            fields.put("rechargeMoney", yuan);
            fields.put("signature", attempt == 0
                ? RechargeSignature.alipay(session.loginCode(), yuan, profile.telephone)
                : RechargeSignature.alipayFallback(session.loginCode(), yuan, profile.telephone));

            String path = migrated ? MIGRATED_PATH : LEGACY_PATH;
            if (!ROUTES.contains(path)) throw new IllegalArgumentException("Unknown recharge route");
            String origin = migrated ? MIGRATED_ORIGIN : LEGACY_ORIGIN;
            JSONObject reply = HttpForm.request(origin, path, SmsLoginProtocol.form(fields), true);
            int code = HttpForm.responseCode(reply);
            if (code == 0 && !(reply.has("success") && !reply.optBoolean("success", true))) {
                Object data = reply.opt("data");
                if (!(data instanceof String) || ((String) data).length() < 20)
                    throw new IOException("服务器没有返回支付订单，未发起支付");
                return (String) data;
            }
            String message = reply.optString("errorMessage", reply.optString("message", ""));
            // A rejected signature creates no order, so trying the other native salt once is safe.
            if (attempt == 0 && (message.contains("签名") || message.toLowerCase(java.util.Locale.ROOT).contains("sign")))
                continue;
            throw HttpForm.rejection(reply, code);
        }
        throw new IOException("支付订单创建失败，未发起支付");
    }
}
