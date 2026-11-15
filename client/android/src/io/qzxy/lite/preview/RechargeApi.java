package io.qzxy.lite.preview;

import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.RechargeSignature;
import io.qzxy.lite.core.SmsLoginProtocol;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Alipay recharge order creation, reconstructed from 6.5.28 {@code RechargeActivity.U2} and
 * {@code ui.h.n()}.
 *
 * <p>Two route families exist: the legacy {@code wallet/recharge/aliPay} on wfwapi and the newer
 * {@code settlement/alipay/recharge/balance} on v3-api. The original client's {@code MyApp.f} is
 * hard-coded true, so {@code y0.n(int)} always returns true and {@code MyApp.e} is always true —
 * a genuine 6.5.28 client always uses the settlement family. The legacy family is kept only as a
 * fallback when the settlement family answers with a genuine session refusal.</p>
 *
 * <p>Only Alipay is implemented. WeChat app-pay binds the order to the merchant's signing
 * certificate, and UnionPay/cmb/juhe all need their own SDK, so this class cannot create those
 * orders; the screen tells the user to finish those in the official app.</p>
 *
 * <p>An order string is only produced for a request the server accepted, and paying it still
 * requires an explicit confirmation inside Alipay.</p>
 */
final class RechargeApi {
    private static final String LEGACY_ORIGIN = "https://wfwapi.china-qzxy.cn/";
    private static final String MIGRATED_ORIGIN = "https://v3-api.china-qzxy.cn/";
    private static final String LEGACY_PATH = "wallet/recharge/aliPay";
    private static final String MIGRATED_PATH = "settlement/alipay/recharge/balance";
    private static final List<String> ROUTES = Arrays.asList(LEGACY_PATH, MIGRATED_PATH);
    /** GET tier lists; the campus configures the amounts the settlement service will accept. */
    private static final String LEGACY_TIERS = "wallet/recharge/package";
    private static final String MIGRATED_TIERS = "settlement/recharge/package/list";
    private static final List<String> TIER_ROUTES = Arrays.asList(LEGACY_TIERS, MIGRATED_TIERS);
    private static final int MAX_TIERS = 8;

    private RechargeApi() { }

    /**
     * A refusal worded for the screen. A payment channel rejecting an order is not the same as the
     * account being signed out — the balance right above it was just read with the same session.
     */
    static final class Rejected extends IOException {
        private static final long serialVersionUID = 1L;
        Rejected(String message) { super(message); }
    }

    /** Keeps the server's own spelling: {@code 50.0} must not reach the order as {@code 50}. */
    static String normalize(String raw) { return RechargeSignature.normalizeAmount(raw); }

    /**
     * The amounts this campus has configured, in the exact text the server publishes ({@code 50.0}).
     * The settlement service rejects a submitted amount that is not one of these with 157
     * "充值金额错误", so the screen offers these instead of a made-up set of round numbers.
     * A campus with no configured tiers answers 158; the caller then keeps the built-in amounts.
     */
    static List<String> amounts(AccountSession session, AccountProfile profile, boolean settlement)
            throws IOException, JSONException {
        String path = settlement ? MIGRATED_TIERS : LEGACY_TIERS;
        if (!TIER_ROUTES.contains(path)) throw new IllegalArgumentException("Unknown recharge tier route");
        String origin = settlement ? MIGRATED_ORIGIN : LEGACY_ORIGIN;
        JSONObject reply = HttpForm.request(origin, path,
            SmsLoginProtocol.form(common(session, profile, settlement)), false);
        int code = HttpForm.responseCode(reply);
        if (HttpForm.failed(reply, code)) throw HttpForm.rejection(reply, code);
        JSONArray data = reply.optJSONArray("data");
        List<String> tiers = new ArrayList<>();
        if (data == null) return tiers;
        for (int i = 0; i < data.length() && tiers.size() < MAX_TIERS; i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null || item.isNull("amount")) continue;
            Object amount = item.get("amount");
            if (!(amount instanceof Number) && !(amount instanceof String)) continue;
            String text;
            try { text = RechargeSignature.normalizeAmount(String.valueOf(amount)); }
            catch (IllegalArgumentException odd) { continue; }
            if (!tiers.contains(text)) tiers.add(text);
        }
        return tiers;
    }

    /** The session fields {@code BaseNetActivity.t0} appends to every form. */
    private static Map<String, String> common(AccountSession session, AccountProfile profile, boolean settlement) {
        Map<String, String> fields = new LinkedHashMap<>();
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
        return fields;
    }

    /**
     * Creates the order. A business refusal (157 and friends) is a real answer from the correct
     * route: trying the other family only produces a misleading "登录失效" from a server this
     * account does not belong to, so only a session refusal triggers the fallback.
     */
    static String alipayOrder(AccountSession session, AccountProfile profile, String amount, boolean migrated)
            throws IOException, JSONException {
        String yuan = normalize(amount);
        if (!profile.hasAccount()) throw new IOException("当前账号没有返回校园账户，暂不能充值");
        if (profile.projectId.isEmpty() || profile.projectId.equals("0"))
            throw new IOException("登录信息缺少校园编号，请重新登录");
        boolean first = migrated;
        IOException refused;
        try {
            return order(session, profile, yuan, first);
        } catch (IOException failure) {
            refused = failure;
        }
        if (!(refused instanceof HttpForm.SessionExpired)) {
            if (refused instanceof HttpForm.ResponseFailure) throw actionable(refused, first, yuan);
            throw refused;
        }
        boolean other = !first;
        try {
            return order(session, profile, yuan, other);
        } catch (IOException second) {
            throw mismatch(new Refusal(first, refused), other, second);
        }
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

    /** Both families refused with a session error; never dress this up as the account expiring. */
    private static IOException mismatch(Refusal first, boolean settlement, IOException second) {
        Refusal other = new Refusal(settlement, second);
        return new Rejected("两条充值通道都没接受这笔订单。"
            + first.where() + "：" + first.reason() + "；"
            + other.where() + "：" + other.reason()
            + "。余额刚才是用同一个登录状态查出来的，所以账号没有失效，这是下单通道各自的拒绝，不用重新登录。");
    }

    /** A real business answer from the right route: say what to change, not "登录已失效". */
    private static IOException actionable(IOException refused, boolean settlement, String yuan) {
        String message = refused.getMessage() == null ? "" : refused.getMessage();
        String where = settlement ? "支付宝直连通道" : "旧版钱包通道";
        if (message.contains("157") || message.contains("充值金额"))
            return new Rejected("服务器没有接受 ¥" + yuan + " 这个充值金额（错误码 157）。"
                + "请回到充值页选学校配置里列出的金额，或换一个金额再试；"
                + "这笔订单没有创建，也没有扣款。");
        return new Rejected(where + "拒绝了这次下单：" + message + "。没有创建订单，也没有扣款。");
    }

    /** One route family, with the one signature retry the original salt allows. */
    private static String order(AccountSession session, AccountProfile profile, String yuan, boolean settlement)
            throws IOException, JSONException {
        for (int attempt = 0; attempt < 2; attempt++) {
            String signature = attempt == 0
                ? RechargeSignature.alipay(session.loginCode(), yuan, profile.telephone)
                : RechargeSignature.alipayFallback(session.loginCode(), yuan, profile.telephone);
            Map<String, String> fields = common(session, profile, settlement);
            fields.put("payWay", "2");
            fields.put("rechargeMoney", yuan);
            fields.put("signature", signature);

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
            IOException rejection = HttpForm.rejection(reply, code);
            // A rejected signature creates no order. Any business refusal — not just one whose
            // wording happens to say "签名" — is worth one attempt with the other native salt.
            if (attempt == 0 && rejection instanceof HttpForm.ResponseFailure) continue;
            throw rejection;
        }
        throw new IOException("支付订单创建失败，未发起支付");
    }
}
