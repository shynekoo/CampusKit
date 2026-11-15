package io.qzxy.lite.preview;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The academic account the user asked us to remember, sealed with a key that never leaves the
 * Android keystore.
 *
 * <p>Only this app can decrypt it, the file lives in no-backup storage, and deleting the key makes
 * any leftover file useless. Call from a worker thread; the keystore may block.</p>
 */
final class EduVault {
    private static final String ALIAS = "campuskit-edu-account-v1";
    private static final int IV_BYTES = 12;
    private final AtomicFile file;
    private final android.content.SharedPreferences state;

    static final class Credential {
        final String user, password;
        /** The school refused this password before; it is not worth a second automatic attempt. */
        final boolean refused;
        Credential(String user, String password) { this(user, password, false); }
        Credential(String user, String password, boolean refused) {
            this.user = user; this.password = password; this.refused = refused;
        }
        boolean complete() { return !user.isEmpty() && !password.isEmpty(); }
    }

    EduVault(Context context) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "edu-account.bin"));
        state = context.getSharedPreferences("edu-account-state", Context.MODE_PRIVATE);
    }

    /** Whether the last saved password was rejected, kept across restarts to avoid a lockout. */
    boolean refused() { return state.getBoolean("refused", false); }

    void refused(boolean value) { state.edit().putBoolean("refused", value).apply(); }

    void save(String user, String password) throws GeneralSecurityException, IOException {
        if (user == null || user.trim().isEmpty() || password == null || password.isEmpty())
            throw new GeneralSecurityException("账号和密码都要填写。");
        SecretKey key = key();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] sealed = cipher.doFinal((user.trim() + "\n" + password).getBytes("UTF-8"));
        byte[] payload = new byte[iv.length + sealed.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(sealed, 0, payload, iv.length, sealed.length);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(payload);
            file.finishWrite(output);
        } catch (IOException failure) {
            file.failWrite(output);
            throw failure;
        }
    }

    Credential load() {
        byte[] payload;
        try (FileInputStream input = file.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 8192) return null;
                bytes.write(buffer, 0, count);
            }
            payload = bytes.toByteArray();
        } catch (FileNotFoundException absent) {
            return null;
        } catch (IOException unreadable) {
            return null;
        }
        if (payload.length <= IV_BYTES) return null;
        try {
            SecretKey key = key();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, payload, 0, IV_BYTES));
            String text = new String(cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES), "UTF-8");
            int split = text.indexOf('\n');
            if (split <= 0) return null;
            return new Credential(text.substring(0, split), text.substring(split + 1), refused());
        } catch (Exception broken) {
            // Keystore reset or tampered file: treat as "no saved account" so the user can re-enter.
            return null;
        }
    }

    void clear() {
        try {
            keyStore().deleteEntry(ALIAS);
        } catch (Exception ignored) { }
        file.delete();
        state.edit().clear().apply();
    }

    private SecretKey key() throws GeneralSecurityException, IOException {
        KeyStore store = keyStore();
        SecretKey existing = (SecretKey) store.getKey(ALIAS, null);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    private static KeyStore keyStore() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }
}
