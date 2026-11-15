package io.qzxy.lite.core;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Rebuild of the per-request signature the original client attaches to recharge calls.
 *
 * <p>Source: 6.5.28 {@code sj.e0.e(Map)} — sort the parameter names, append name+value for each,
 * append {@code JniUtils.getSk()}, then take the lowercase MD5 of the UTF-8 bytes. The native salt
 * is one of two literals; the original APK's own signature check sets a flag that selects
 * {@code sign-kailu=} (see analysis/private/native). That is the value a genuine client sends, so it
 * is the one used here.</p>
 *
 * <p>Only the four fields the original passes to {@code e()} are signed; the common session fields
 * added afterwards by the network layer are not part of the digest.</p>
 */
public final class RechargeSignature {
    /** JniUtils.getSk() for an unmodified original build. */
    static final String SALT = "sign-kailu=";
    /** The decoy the native code returns when its own signature check fails. */
    static final String SALT_TAMPERED = "keep&1!";

    private RechargeSignature() { }

    /** Alipay order: payWay 2, signed over loginCode / payWay / rechargeMoney / telPhone. */
    public static String alipay(String loginCode, String amount, String telephone) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("loginCode", loginCode);
        fields.put("payWay", "2");
        fields.put("rechargeMoney", amount);
        fields.put("telPhone", telephone);
        return sign(fields, SALT);
    }

    /** Same digest with the tamper-decoy salt, used only if the server rejects the primary value. */
    public static String alipayFallback(String loginCode, String amount, String telephone) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("loginCode", loginCode);
        fields.put("payWay", "2");
        fields.put("rechargeMoney", amount);
        fields.put("telPhone", telephone);
        return sign(fields, SALT_TAMPERED);
    }

    static String sign(Map<String, String> fields, String salt) {
        if (fields == null || fields.isEmpty()) throw new IllegalArgumentException("缺少待签名字段");
        List<String> names = new ArrayList<>(fields.keySet());
        Collections.sort(names);
        StringBuilder serialized = new StringBuilder();
        for (String name : names) {
            serialized.append(name);
            String value = fields.get(name);
            if (value != null) serialized.append(value);
        }
        serialized.append(salt);
        return md5Hex(serialized.toString().trim());
    }

    static String md5Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes("UTF-8"));
            char[] hex = "0123456789abcdef".toCharArray();
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                out[i * 2] = hex[(digest[i] & 255) >>> 4];
                out[i * 2 + 1] = hex[digest[i] & 15];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException missing) {
            throw new AssertionError("MD5 missing from runtime", missing);
        } catch (UnsupportedEncodingException missing) {
            throw new AssertionError("UTF-8 missing from runtime", missing);
        }
    }
}
