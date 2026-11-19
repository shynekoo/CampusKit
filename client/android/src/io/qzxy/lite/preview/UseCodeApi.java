package io.qzxy.lite.preview;

import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.SmsLoginProtocol;
import io.qzxy.lite.core.UseCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 使用码：详情、开关、领取、自动关闭选项。
 *
 * <p>全部是账号维度的 HTTP 表单接口（6.5.28 ui.a + CodeDetailActivity / CodeGenerateActivity），
 * 不碰设备订单，也不发蓝牙或 MQTT。每一页用到的路由都在这里显式列出，路由之外一律拒绝，
 * 避免以后误用一个没核对过的地址。</p>
 */
final class UseCodeApi {
    static final String DETAIL = "account/useCode/new";
    static final String STATUS = "account/useCode/new/status/update";
    static final String GENERATE = "account/useCode/new/generate";
    static final String APPLY = "account/useCode/new/set";
    static final String OPTIONS = "account/useCode/new/autoCloseConfig/list";
    static final String SAVE_OPTION = "account/useCode/new/autoCloseConfig/save";

    private static final List<String> ROUTES =
        Arrays.asList(DETAIL, STATUS, GENERATE, APPLY, OPTIONS, SAVE_OPTION);

    private UseCodeApi() { }

    /** 领取结果：8 位码 + 今天还能领几次。 */
    static final class Issued {
        final String code;
        final int remainTimes;
        Issued(String code, int remainTimes) { this.code = code; this.remainTimes = remainTimes; }
    }

    /** 自动关闭的一项；id 保存时回填，description 是给用户看的。 */
    static final class Option {
        final int id;
        final String description, warnMark;
        final boolean checked;
        Option(int id, String description, String warnMark, boolean checked) {
            this.id = id; this.description = description; this.warnMark = warnMark; this.checked = checked;
        }
    }

    static UseCode detail(AccountSession session, AccountProfile profile) throws IOException, JSONException {
        return parse(profile, data(request(DETAIL, fields(session, profile), false)));
    }

    static UseCode status(AccountSession session, AccountProfile profile, boolean on) throws IOException, JSONException {
        Map<String, String> fields = fields(session, profile);
        fields.put("useCodeStatus", on ? "1" : "0");
        JSONObject data = data(request(STATUS, fields, true));
        // 有的项目只回空 data，重新读一次详情比在客户端拼状态可靠。
        return data == null ? detail(session, profile) : parse(profile, data);
    }

    static Issued generate(AccountSession session, AccountProfile profile) throws IOException, JSONException {
        JSONObject data = data(request(GENERATE, fields(session, profile), true));
        if (data == null) throw new IOException("服务器没有返回使用码，请稍后再试");
        String code = data.optString("useCode", "").trim();
        if (!code.matches("[0-9]{5,8}")) throw new IOException("服务器返回的使用码格式无法识别");
        int remain = data.optInt("remainTimes", 0);
        return new Issued(code, Math.max(0, remain));
    }

    /** 把刚领到的码设为当前使用码；成功后详情页会重新读取一次。 */
    static void apply(AccountSession session, AccountProfile profile, String code) throws IOException, JSONException {
        if (!code.matches("[0-9]{5,8}")) throw new IOException("使用码格式无法识别");
        Map<String, String> fields = fields(session, profile);
        fields.put("useCode", code);
        check(request(APPLY, fields, true));
    }

    static List<Option> options(AccountSession session, AccountProfile profile) throws IOException, JSONException {
        JSONObject reply = request(OPTIONS, fields(session, profile), false);
        check(reply);
        JSONArray array = reply.optJSONArray("data");
        List<Option> list = new ArrayList<>();
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            int id = item.optInt("id", 0);
            if (id <= 0) continue;
            list.add(new Option(id, item.optString("description", ""), item.optString("warnMark", ""),
                item.optBoolean("isChecked", false)));
        }
        return list;
    }

    static void saveOption(AccountSession session, AccountProfile profile, int id) throws IOException, JSONException {
        if (id <= 0) throw new IOException("请选择一个自动关闭时间");
        Map<String, String> fields = fields(session, profile);
        fields.put("useCodeAutoCloseConfig", String.valueOf(id));
        check(request(SAVE_OPTION, fields, true));
    }

    private static Map<String, String> fields(AccountSession session, AccountProfile profile) throws IOException {
        if (profile == null || !profile.hasAccount()) throw new IOException("当前账号没有校园账户，暂不能使用使用码");
        if (!session.projectId().equals(profile.projectId)) throw new IOException("登录校园与账户校园不一致，请重新登录");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("phoneSystem", "android");
        fields.put("version", "6.5.28");
        fields.put("userId", session.userId());
        fields.put("loginCode", session.loginCode());
        fields.put("projectId", profile.projectId);
        fields.put("accountId", profile.accountId);
        if (!profile.telephone.isEmpty()) {
            fields.put("telephone", profile.telephone);
            fields.put("telPhone", profile.telephone);
        }
        return fields;
    }

    private static JSONObject request(String path, Map<String, String> fields, boolean post)
            throws IOException, JSONException {
        if (!ROUTES.contains(path)) throw new IllegalArgumentException("Unknown use-code route");
        String trace = Diag.trace();
        try {
            return HttpForm.request(SmsLoginProtocol.ORIGIN, path, SmsLoginProtocol.form(fields), post);
        } catch (IOException | JSONException failure) {
            // 趣智的失败往往只是「这一支接口今天不可用」，日志里留下路由与原因就够了。
            Diag.failure("QZXY", "code.failed", Diag.reason(failure), failure, trace);
            throw failure;
        }
    }

    private static void check(JSONObject reply) throws IOException, JSONException {
        int code = HttpForm.responseCode(reply);
        if (HttpForm.failed(reply, code)) {
            Diag.event(Diag.WARN, "QZXY", "request.rejected", "code", code);
            throw HttpForm.rejection(reply, code);
        }
    }

    private static JSONObject data(JSONObject reply) throws IOException, JSONException {
        check(reply);
        JSONObject data = reply.optJSONObject("data");
        return data == null || data == JSONObject.NULL ? null : data;
    }

    private static UseCode parse(AccountProfile profile, JSONObject data) throws IOException, JSONException {
        if (data == null) throw new IOException("服务器没有返回使用码资料，请稍后再试");
        Map<String, String> fields = new LinkedHashMap<>();
        for (Iterator<String> keys = data.keys(); keys.hasNext(); ) {
            String key = keys.next();
            Object value = data.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            fields.put(key, String.valueOf(value));
        }
        UseCode code;
        try { code = UseCode.parse(fields); }
        catch (IllegalArgumentException invalid) { throw new IOException(invalid.getMessage()); }
        if (code.accountId() != 0 && !String.valueOf(code.accountId()).equals(profile.accountId))
            throw new IOException("服务器返回的使用码不属于当前账户，已停止显示");
        return code;
    }
}
