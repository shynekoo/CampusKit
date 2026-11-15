package io.qzxy.lite.core;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;

/**
 * The school's 正方 (zfsoft) login form, reproduced without a browser.
 *
 * <p>The page posts four things that matter: the hidden {@code csrftoken}, the user name, the
 * language, and a password that its own JavaScript replaces with {@code hex2b64(RSAKey.encrypt(pw))}.
 * The key comes from {@code /xtgl/login_getPublicKey.html} as base64 modulus and exponent, so the
 * same PKCS#1 v1.5 encryption can be produced here with the platform's RSA cipher.</p>
 *
 * <p>Everything is parsing or arithmetic on strings, so the whole protocol is unit-testable; no
 * request is made here.</p>
 */
public final class EduLoginProtocol {
    public static final String USER = "yhm";
    public static final String PASSWORD = "mm";
    public static final String TOKEN = "csrftoken";

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("name\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE = Pattern.compile("value\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]*))",
        Pattern.CASE_INSENSITIVE);

    private EduLoginProtocol() { }

    /** Hidden fields the form would post, keyed by name; later duplicates win, as in a browser. */
    public static Map<String, String> fields(String html) {
        Map<String, String> form = new LinkedHashMap<>();
        if (html == null) return form;
        Matcher input = INPUT.matcher(html);
        while (input.find()) {
            String tag = input.group();
            Matcher name = NAME.matcher(tag);
            if (!name.find()) continue;
            String key = name.group(1).trim();
            if (key.isEmpty()) continue;
            form.put(key, attribute(tag));
        }
        return form;
    }

    private static String attribute(String tag) {
        Matcher value = VALUE.matcher(tag);
        if (!value.find()) return "";
        if (value.group(2) != null) return value.group(2).trim();
        if (value.group(3) != null) return value.group(3).trim();
        return value.group(4) == null ? "" : value.group(4).trim();
    }

    public static String csrf(String html) {
        String token = fields(html).get(TOKEN);
        return token == null ? "" : token;
    }

    /** True when the response is the login form rather than data, i.e. the session is gone. */
    public static boolean isLoginForm(String html) {
        return html != null && (html.contains("id=\"yhm\"") || html.contains("name=\"yhm\""));
    }

    public static boolean wrongPassword(String html) {
        if (html == null) return false;
        return html.contains("用户名或密码不正确") || html.contains("密码不正确") || html.contains("用户不存在")
            || html.contains("密码错误");
    }

    /** Only counts when the page really wants a code: a hidden modal mentioning 验证码 does not. */
    public static boolean captchaRequired(String html) {
        return html != null && (html.contains("id=\"yzm\"") || html.contains("name=\"yzm\""));
    }

    /** Minimal reader for {"modulus":"…","exponent":"…"}; the payload has no nesting. */
    public static String jsonValue(String json, String key) {
        if (json == null) return "";
        Matcher match = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
            .matcher(json);
        return match.find() ? match.group(1) : "";
    }

    /** The base64 string the page puts into {@code mm}: RSA/PKCS#1 v1.5 over the UTF-8 password. */
    public static String encryptPassword(String modulus, String exponent, String password)
        throws GeneralSecurityException {
        BigInteger n = new BigInteger(1, base64Decode(modulus));
        BigInteger e = new BigInteger(1, base64Decode(exponent));
        if (n.signum() <= 0 || e.signum() <= 0) throw new GeneralSecurityException("学校登录公钥无效。");
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA")
            .generatePublic(new RSAPublicKeySpec(n, e)));
        return base64Encode(cipher.doFinal(utf8(password)));
    }

    private static byte[] utf8(String text) {
        try {
            return text.getBytes("UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** java.util.Base64 is API 26, so the two directions are written out here. */
    public static String base64Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length + 2) / 3 * 4);
        int index = 0;
        while (index + 2 < data.length) {
            int chunk = ((data[index] & 0xff) << 16) | ((data[index + 1] & 0xff) << 8) | (data[index + 2] & 0xff);
            out.append(ALPHABET.charAt((chunk >> 18) & 63)).append(ALPHABET.charAt((chunk >> 12) & 63))
                .append(ALPHABET.charAt((chunk >> 6) & 63)).append(ALPHABET.charAt(chunk & 63));
            index += 3;
        }
        int rest = data.length - index;
        if (rest == 1) {
            int chunk = (data[index] & 0xff) << 16;
            out.append(ALPHABET.charAt((chunk >> 18) & 63)).append(ALPHABET.charAt((chunk >> 12) & 63)).append("==");
        } else if (rest == 2) {
            int chunk = ((data[index] & 0xff) << 16) | ((data[index + 1] & 0xff) << 8);
            out.append(ALPHABET.charAt((chunk >> 18) & 63)).append(ALPHABET.charAt((chunk >> 12) & 63))
                .append(ALPHABET.charAt((chunk >> 6) & 63)).append('=');
        }
        return out.toString();
    }

    public static byte[] base64Decode(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(text == null ? 0 : text.length() * 3 / 4);
        int buffer = 0, bits = 0;
        if (text == null) return out.toByteArray();
        for (int index = 0; index < text.length(); index++) {
            int digit = ALPHABET.indexOf(text.charAt(index));
            if (digit < 0) continue;
            buffer = (buffer << 6) | digit;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xff);
            }
        }
        return out.toByteArray();
    }
}
