// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Versioned authenticated storage envelope; key is supplied by Android Keystore. */
public final class SessionCipher {
    private static final byte[] AAD = "io.qzxy.lite/session/v1".getBytes(StandardCharsets.US_ASCII);
    private SessionCipher() {}

    public static byte[] seal(AccountSession session, SecretKey key) throws GeneralSecurityException, IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeUTF(session.userId());
        output.writeUTF(session.loginCode());
        output.writeUTF(session.projectId());
        byte[] plain = bytes.toByteArray();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(AAD);
            cipher.updateAAD(new byte[]{2});
            byte[] iv = cipher.getIV();
            if (iv.length != 12) throw new GeneralSecurityException("Unexpected IV length");
            byte[] encrypted = cipher.doFinal(plain);
            ByteArrayOutputStream envelope = new ByteArrayOutputStream();
            envelope.write(2);
            envelope.write(iv);
            envelope.write(encrypted);
            return envelope.toByteArray();
        } finally { Arrays.fill(plain, (byte) 0); }
    }

    public static AccountSession open(byte[] envelope, SecretKey key) throws GeneralSecurityException, IOException {
        if (envelope == null || envelope.length < 33 || envelope.length > 16384 || (envelope[0] != 1 && envelope[0] != 2)) {
            throw new GeneralSecurityException("Invalid session envelope");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, envelope, 1, 12));
        cipher.updateAAD(AAD);
        if (envelope[0] == 2) cipher.updateAAD(new byte[]{2});
        byte[] plain = cipher.doFinal(envelope, 13, envelope.length - 13);
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(plain));
            String userId = input.readUTF(), loginCode = input.readUTF();
            AccountSession session = new AccountSession(userId, loginCode, envelope[0] == 2 ? input.readUTF() : "");
            if (input.available() != 0) throw new IOException("Unexpected session fields");
            return session;
        } finally { Arrays.fill(plain, (byte) 0); }
    }
}
