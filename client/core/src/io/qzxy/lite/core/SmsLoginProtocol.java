package io.qzxy.lite.core;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reconstructed from the supplied 6.5.28 LoginCodeActivity and network classes. */
public final class SmsLoginProtocol {
    public static final String ORIGIN = "https://v3-api.china-qzxy.cn/";
    public static final String SEND_PATH = "user/verification/code/get";
    public static final String LOGIN_PATH = "user/registerAndLogin";
    private SmsLoginProtocol() {}

    public static void validatePhone(String phone) {
        if (phone == null || !phone.matches("1[0-9]{10}")) throw new IllegalArgumentException("请输入 11 位手机号");
    }

    private static Map<String, String> common() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("phoneSystem", "android");
        fields.put("version", "6.5.28");
        return fields;
    }

    public static Map<String, String> sendFields(String phone) {
        validatePhone(phone);
        Map<String, String> fields = common();
        fields.put("telephone", phone);
        fields.put("typeId", "3");
        fields.put("platform", "1");
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(
                (phone.substring(0, 3) + phone.substring(7, 11) + "klcx").getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            fields.put("secret", hex.toString());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return fields;
    }

    public static Map<String, String> loginFields(String phone, String code) {
        validatePhone(phone);
        if (code == null || !code.matches("[0-9]{4,8}")) throw new IllegalArgumentException("请输入短信中的数字验证码");
        Map<String, String> fields = common();
        fields.put("type", "5");
        fields.put("telephone", phone);
        fields.put("smsCode", code);
        return fields;
    }

    public static String form(Map<String, String> fields) {
        StringBuilder result = new StringBuilder();
        try {
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (result.length() > 0) result.append('&');
                result.append(URLEncoder.encode(field.getKey(), "UTF-8")).append('=')
                    .append(URLEncoder.encode(field.getValue(), "UTF-8"));
            }
        } catch (UnsupportedEncodingException impossible) { throw new IllegalStateException(impossible); }
        return result.toString();
    }
}
