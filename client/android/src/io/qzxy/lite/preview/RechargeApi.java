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

    /**
     * A refusal worded for the screen. The recharge channel rejecting the request is not the same as
     * the account being signed out — the balance right above it was just read with the same session —
     * so it must not be dressed up as "登录已失效".
     */
    static final class Rejected extends IOException {
        private static final long serialVersionUID = 1L;
        Rejected(String message) { super(message); }
    }

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
     *
     * <p>Which route family a campus uses comes from the server, never from a guess. But a refusal
     * creates no order, so when the first family answers with an error the other one is tried once:
     * a wrong family shows up as exactly the symptom this screen had — "登录已失效" while the account
     * is fine — and costs the user the whole feature. A request that may have succeeded (network
     * failure, unparsable success) is never repeated.</p>
     */
    static String alipayOrder(AccountSession session, AccountProfile profile, String amount, boolean migrated)
            throws IOException, JSONException {
        String yuan = normalize(amount);
        if (!profile.hasAccount()) throw new IOException("当前账号没有返回校园账户，暂不能充值");
        if (profile.projectId.isEmpty() || profile.projectId.equals("0"))
            throw new IOException("登录信息缺少校园编号，请重新登录");
        Refusal first = null;
        for (boolean settlement : new boolean[]{migrated, !migrated}) {
            try {
                return order(session, profile, yuan, settlement);
            } catch (HttpForm.SessionExpired refused) {
                if (first == null) first = new Refusal(settlement, refused);
                else throw mismatch(first, settlement, refused);
            } catch (HttpForm.ResponseFailure refused) {
                if (first == null) first = new Refusal(settlement, refused);
                else throw mismatch(first, settlement, refused);
            }
        }
        throw new IOException("充值通道拒绝了这次请求（" + first.where() + "：" + first.reason() + "），未发起支付");
    }

    /** Which family was tried and what it answered, kept so the message can name both. */
    private static final class Refusal {
        final boolean settlement;
        final IOException failure;
        Refusal(boolean settlement, IOException failure) { this.settlement = settlement; this.failure = failure; }
        String where() { return settlement ? "支付宝直连通道" : "旧版钱包通道"; }
        String reason() {
            if (failure instanceof HttpForm.SessionExpired) {
                HttpForm.SessionExpired expired = (HttpForm.SessionExpired) failure;
                StringBuilder out = new StringBuilder();
                if (expired.code != 0) out.append("错误码 ").append(expired.code).append(' ');
                out.append(expired.detail.isEmpty() ? "登录校验未通过" : expired.detail);
                return out.toString().trim();
            }
            String message = failure.getMessage();
            return message == null || message.isEmpty() ? "未说明原因" : message;
        }
    }

    /**
     * Both families refused. Wording matters here: the balance was just read with the same session,
     * so this is the payment channel refusing the request, not the account expiring, and saying
     * "登录已失效" would send the user off to log in again for nothing.
     */
    private static IOException mismatch(Refusal first, boolean settlement, IOException second) {
        Refusal other = new Refusal(settlement, second);
        return new Rejected("两条充值通道都没接受这笔订单。"
            + first.where() + "：" + first.reason() + "；"
            + other.where() + "：" + other.reason()
            + "。余额刚才是用同一个登录状态查出来的，所以账号没有失效。把这句话发给我，我按错误码继续对。");
    }

    /** One route family, with the one signature retry the original salt allows. */
    private static String order(AccountSession session, AccountProfile profile, String yuan, boolean settlement)
            throws IOException, JSONException {
        for (int attempt = 0; attempt < 2; attempt++) {
            String signature = attempt == 0
                ? RechargeSignature.alipay(session.loginCode(), yuan, profile.telephone)
                : RechargeSignature.alipayFallback(session.loginCode(), yuan, profile.telephone);
            // Field set mirrors BaseNetActivity.t0 exactly: userId and telephone belong to the
            // settlement family only, accountId is dropped when the campus has none, and the
            // signature covers the same four fields the original signs before t0 runs.
            java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
            fields.put("payWay", "2");
            fields.put("rechargeMoney", yuan);
            fields.put("signature", signature);
            fields.put("projectId", profile.projectId);
            if (!profile.accountId.isEmpty() && !profile.accountId.equals("0"))
                fields.put("accountId", profile.accountId);
            if (settlement) fields.put("userId", session.userId());
            if (!profile.telephone.isEmpty()) {
                fields.put("telPhone", profile.telephone);
                if (settlement) fields.put("telephone", profile.telephone);
            }
            fields.put("loginCode", session.loginCode());
            fields.put("phoneSystem", "android");
            fields.put("version", "6.5.28");

            String path = settlement ? MIGRATED_PATH : LEGACY_PATH;
            if (!ROUTES.contains(path)) throw new IllegalArgumentException("Unknown recharge route");
            String origin = settlement ? MIGRATED_ORIGIN : LEGACY_ORIGIN;
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
