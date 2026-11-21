// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.content.Context;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.SessionCipher;

/** App-private, non-backup session storage. Invoke only from a serial worker thread. */
final class AccountVault {
    private static final String ALIAS = "qingyong-account-session-v1";
    private final AtomicFile file;

    AccountVault(Context context) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "account-session.bin"));
    }

    private static void workerOnly() {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Use account worker thread");
    }

    private static KeyStore keyStore() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    void save(AccountSession session) throws GeneralSecurityException, IOException {
        synchronized(AccountVault.class){saveLocked(session);}
    }
    private void saveLocked(AccountSession session) throws GeneralSecurityException, IOException {
        workerOnly();
        KeyStore store = keyStore();
        SecretKey key = (SecretKey) store.getKey(ALIAS, null);
        if (key == null) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build());
            key = generator.generateKey();
        }
        byte[] encrypted = SessionCipher.seal(session, key);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(encrypted);
            file.finishWrite(output);
        } catch (IOException failure) {
            file.failWrite(output);
            throw failure;
        }
    }

    AccountSession load() throws GeneralSecurityException, IOException {
        synchronized(AccountVault.class){return loadLocked();}
    }
    private AccountSession loadLocked() throws GeneralSecurityException, IOException {
        workerOnly();
        byte[] encrypted;
        // openRead also recovers AtomicFile backups left by an interrupted write.
        try (FileInputStream input = file.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 16384) throw new IOException("Invalid session file");
                bytes.write(buffer, 0, count);
            }
            encrypted = bytes.toByteArray();
        } catch (FileNotFoundException absent) { return null; }
        SecretKey key = (SecretKey) keyStore().getKey(ALIAS, null);
        if (key == null) throw new GeneralSecurityException("Session key unavailable; sign in again");
        return SessionCipher.open(encrypted, key);
    }

    void clear() throws GeneralSecurityException, IOException {
        synchronized(AccountVault.class){clearLocked();}
    }
    static synchronized boolean clearExpired(Context context,AccountSession checked) throws GeneralSecurityException, IOException {
        AccountVault vault=new AccountVault(context);AccountSession current=vault.loadLocked();
        if(current==null)return true;
        if(checked==null||!current.userId().equals(checked.userId())||!current.loginCode().equals(checked.loginCode())
            ||!current.projectId().equals(checked.projectId()))return false;
        vault.clearLocked();return true;
    }
    private void clearLocked() throws GeneralSecurityException, IOException {
        workerOnly();
        // Deleting the key first makes any leftover encrypted file unusable.
        keyStore().deleteEntry(ALIAS);
        file.delete();
    }
}
