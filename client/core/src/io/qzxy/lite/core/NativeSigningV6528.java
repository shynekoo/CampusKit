package io.qzxy.lite.core;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Independent implementation of the native signing transform in APK 6.5.28.
 * Caller-side field selection and serialization have NOT been recovered.
 * This class deliberately accepts an already serialized string and performs no HTTP requests.
 */
public final class NativeSigningV6528 {
    private static final String SUFFIX = "&key=sign-kailu-855c74a88b8b494187c99b08b8c9a744";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private NativeSigningV6528() {}

    public static String signSerialized(String serialized, String sessionKey) {
        Objects.requireNonNull(serialized, "serialized");
        Objects.requireNonNull(sessionKey, "sessionKey");
        String first = md5(modifiedUtf8(serialized + "&key=" + sessionKey));
        return md5(modifiedUtf8(first + SUFFIX));
    }

    // JNI GetStringUTFChars uses modified UTF-8, including for NUL and surrogate pairs.
    static byte[] modifiedUtf8(String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < input.length(); i++) {
            int c = input.charAt(i);
            if (c >= 1 && c <= 0x7f) {
                output.write(c);
            } else if (c <= 0x7ff) {
                output.write(0xc0 | c >> 6);
                output.write(0x80 | c & 0x3f);
            } else {
                output.write(0xe0 | c >> 12);
                output.write(0x80 | c >> 6 & 0x3f);
                output.write(0x80 | c & 0x3f);
            }
        }
        return output.toByteArray();
    }

    private static String md5(byte[] input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input);
            char[] output = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                output[i * 2] = HEX[(digest[i] & 255) >>> 4];
                output[i * 2 + 1] = HEX[digest[i] & 15];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("MD5 missing from runtime", impossible);
        }
    }
}
