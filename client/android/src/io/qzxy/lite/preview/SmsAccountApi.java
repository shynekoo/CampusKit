package io.qzxy.lite.preview;

import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.SmsLoginProtocol;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONException;

/** Fixed account routes, without logging or automatic retries. */
final class SmsAccountApi {
    /** Account-scoped routes only; device control routes live in WaterApi. */
    private static final List<String> ROUTES = Arrays.asList(SmsLoginProtocol.SEND_PATH,
        SmsLoginProtocol.LOGIN_PATH, "user/info", "account/wallet", "device/info/mac", "project/info/triple");

    private SmsAccountApi() { }

    private static JSONObject request(String path, String form, boolean post) throws IOException, JSONException {
        if (!ROUTES.contains(path)) throw new IllegalArgumentException("Unknown account route");
        return HttpForm.request(SmsLoginProtocol.ORIGIN, path, form, post);
    }

    private static java.util.Map<String, String> authenticated(AccountSession session) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("phoneSystem", "android"); fields.put("version", "6.5.28");
        fields.put("userId", session.userId()); fields.put("loginCode", session.loginCode());
        return fields;
    }

    static void send(String phone) throws IOException, JSONException {
        JSONObject reply = request(SmsLoginProtocol.SEND_PATH, SmsLoginProtocol.form(SmsLoginProtocol.sendFields(phone)), false);
        int code = HttpForm.responseCode(reply);
        if (HttpForm.failed(reply, code)) throw HttpForm.rejection(reply, code);
    }

    static AccountSession parseSession(JSONObject reply) throws IOException, JSONException {
        int code = HttpForm.responseCode(reply);
        if (code != 0 && code != 2) throw HttpForm.rejection(reply, code);
        if (reply.has("success") && !reply.getBoolean("success")) throw HttpForm.rejection(reply, code);
        JSONObject data = reply.getJSONObject("data");
        Object userId = data.has("userId") ? data.get("userId") : data.get("v3UserId");
        Object token = data.get("loginCode");
        if (!(token instanceof String)) throw new JSONException("Missing session credential");
        JSONObject account = data.optJSONObject("userAccount");
        if (account == null) account = data;
        String projectId = AccountProfile.identifier(account, "projectId");
        try { return new AccountSession(String.valueOf(userId), (String) token, projectId); }
        catch (IllegalArgumentException invalid) { throw new JSONException("Invalid authenticated identity"); }
    }

    static AccountSession login(String phone, String code) throws IOException, JSONException {
        return parseSession(request(SmsLoginProtocol.LOGIN_PATH, SmsLoginProtocol.form(SmsLoginProtocol.loginFields(phone, code)), true));
    }

    static AccountProfile profile(AccountSession session) throws IOException, JSONException {
        java.util.Map<String, String> fields = authenticated(session);
        if (session.projectId().isEmpty() || session.projectId().equals("0"))
            throw new IOException("登录信息缺少校园编号，请点击补全校园信息，重新进行一次短信登录");
        fields.put("projectId", session.projectId());
        return AccountProfile.parse(HttpForm.successfulData(request("user/info", SmsLoginProtocol.form(fields), false)), session.userId());
    }

    static String wallet(AccountSession session, AccountProfile profile) throws IOException, JSONException {
        if (!profile.hasAccount()) throw new IOException("当前账号没有返回校园账户，暂不能查询余额");
        java.util.Map<String, String> fields = authenticated(session);
        fields.put("projectId", profile.projectId); fields.put("accountId", profile.accountId);
        if (!profile.telephone.isEmpty()) {
            fields.put("telephone", profile.telephone); fields.put("telPhone", profile.telephone);
        }
        return AccountProfile.walletText(HttpForm.successfulData(request("account/wallet", SmsLoginProtocol.form(fields), false)), profile);
    }

    /** Matches ProjectInfo.getIsMigrated and Gson's primitive-int default. */
    static String migrationFlag(JSONObject project) throws JSONException {
        if (!project.has("migrateDTO") || project.isNull("migrateDTO")) return "1";
        JSONObject migration = project.getJSONObject("migrateDTO");
        if (!migration.has("isMigrated") || migration.isNull("isMigrated")) return "0";
        String value = AccountProfile.identifier(migration, "isMigrated");
        if (!value.matches("[0-9]{1,9}")) throw new JSONException("Invalid migration flag");
        return value;
    }

    /**
     * Original RechargeActivity reads {@code MyApp.e} before choosing between the legacy wallet
     * routes and the settlement routes, and that flag is set from {@code UserInfo.isMigrated} on
     * login and on every user/info refresh. Reading it from the profile we already fetched keeps the
     * recharge path at two requests instead of three — and keeps the decision on the same reply the
     * account fields came from, so the two can never disagree.
     */
    static boolean migrated(AccountProfile profile) {
        return profile.migrated;
    }

    /** Original ScanCodeActivity.c2 / ui.f.m read-only project route. */
    static String alipayWaterLink(AccountSession session, DeviceLookup device) throws IOException, JSONException {
        if (device == null || !device.alipayReady() || !device.profile.inProject(session.projectId()))
            throw new IOException("请先重新核对设备");
        AccountProfile fresh = profile(session);
        if (!fresh.projectId.equals(device.account.projectId) || !fresh.accountId.equals(device.account.accountId))
            throw new IOException("校园账户已变化，请重新扫码");
        java.util.Map<String, String> fields = authenticated(session);
        fields.put("projectId", fresh.projectId);
        fields.put("accountId", fresh.accountId);
        fields.put("telPhone", fresh.telephone);
        fields.put("telephone", fresh.telephone);
        JSONObject project = HttpForm.successfulData(request("project/info/triple", SmsLoginProtocol.form(fields), false));
        if (!fresh.projectId.equals(AccountProfile.identifier(project, "projectId")))
            throw new IOException("校园资料不匹配，未跳转");
        String migrated = migrationFlag(project);
        return io.qzxy.lite.core.AlipayWaterLink.device(device.profile.dsbTypeId(),device.profile.requestSerial(), session,
            fresh.accountId, fresh.projectId, fresh.telephone, migrated);
    }

    /** Read-only device lookup; the profile it returns is what gates every later order request. */
    static DeviceLookup device(AccountSession session, io.qzxy.lite.core.DeviceQr qr) throws IOException, JSONException {
        AccountProfile profile = profile(session);
        java.util.Map<String, String> fields = authenticated(session);
        fields.put("projectId", session.projectId());
        if (profile.hasAccount()) fields.put("accountId", profile.accountId);
        if (!profile.telephone.isEmpty()) { fields.put("telephone", profile.telephone); fields.put("telPhone", profile.telephone); }
        fields.put("isHaier", "0"); fields.put("isNew", "1"); fields.put("macAddress", qr.queryAddress());
        JSONObject data = HttpForm.successfulData(request("device/info/mac", SmsLoginProtocol.form(fields), false));
        return DeviceDetails.lookup(data, session.projectId(), profile);
    }
}
