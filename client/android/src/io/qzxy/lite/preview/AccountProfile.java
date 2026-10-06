package io.qzxy.lite.preview;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.json.JSONObject;
import org.json.JSONException;

/** Selected user/account fields only; no raw response persistence. */
final class AccountProfile {
    final String projectId, accountId, projectName, telephone;
    private AccountProfile(String projectId, String accountId, String projectName, String telephone) {
        this.projectId = projectId; this.accountId = accountId; this.projectName = projectName; this.telephone = telephone;
    }
    static String identifier(JSONObject object, String key) throws JSONException {
        if (!object.has(key) || object.isNull(key)) return "";
        String value = String.valueOf(object.get(key));
        if (!value.matches("[0-9]{1,20}")) throw new JSONException("Invalid identity field");
        return value;
    }
    /* org.json renders a JSON null as the string "null"; normalize to empty. */
    static String text(JSONObject object, String key) {
        return object.has(key) && !object.isNull(key) ? object.optString(key, "") : "";
    }
    static AccountProfile parse(JSONObject data, String expectedUser) throws JSONException {
        String user = identifier(data, data.has("userId") ? "userId" : "v3UserId");
        if (!expectedUser.equals(user)) throw new JSONException("Account identity mismatch");
        JSONObject account = data.optJSONObject("userAccount");
        if (account == null) account = data;
        String phone = text(data, "telephone").isEmpty() ? text(data, "telPhone") : text(data, "telephone");
        if (!phone.matches("1[0-9]{10}")) phone = "";
        return new AccountProfile(identifier(account, "projectId"), identifier(account, "accountId"),
            text(account, "projectName"), phone);
    }
    boolean hasAccount() { return !projectId.isEmpty() && !projectId.equals("0") && !accountId.isEmpty() && !accountId.equals("0"); }
    String description() {
        return (telephone.isEmpty() ? "账号已验证" : telephone.substring(0, 3) + "****" + telephone.substring(7))
            + "\n" + (projectName.isEmpty() ? (hasAccount() ? "校园账户已识别" : "尚未返回校园账户") : projectName);
    }
    private static long amount(JSONObject data, String key) throws JSONException {
        Object raw = data.get(key);
        if (!String.valueOf(raw).matches("-?[0-9]{1,10}")) throw new JSONException("Invalid amount");
        return Long.parseLong(String.valueOf(raw));
    }
    static String yuan(long units) { return BigDecimal.valueOf(units, 3).setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    static String walletText(JSONObject data, AccountProfile profile) throws JSONException {
        String account = identifier(data, "accountId"), project = identifier(data, "projectId");
        if ((!account.isEmpty() && !account.equals(profile.accountId)) || (!project.isEmpty() && !project.equals(profile.projectId)))
            throw new JSONException("Wallet account mismatch");
        long real = amount(data, data.has("accountMoney") ? "accountMoney" : "accountRealMoney");
        long given = amount(data, "accountGivenMoney");
        return "余额 ¥ " + yuan(real + given) + "\n本金 ¥ " + yuan(real) + " · 赠送 ¥ " + yuan(given);
    }
}
