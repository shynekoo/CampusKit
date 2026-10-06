package io.qzxy.lite.preview;

import android.content.Context;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import io.qzxy.lite.core.PendingUse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * App-private, non-backup storage for the unverified-use record. Uses its own Keystore alias, so
 * signing out of the account (which deletes the session key) cannot silently destroy the only note
 * that an order may still be open. Invoke only from a serial worker thread.
 */
final class PendingUseVault {
    private static final String ALIAS = "qingyong-pending-use-v1";
    private final AtomicFile file;
    private final Context context;

    PendingUseVault(Context context) {
        this.context=context.getApplicationContext();
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "pending-use.bin"));
    }

    private static void workerOnly() {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Use a worker thread");
    }

    private static KeyStore keyStore() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    private static SecretKey key(boolean create) throws GeneralSecurityException, IOException {
        KeyStore store = keyStore();
        SecretKey existing = (SecretKey) store.getKey(ALIAS, null);
        if (existing != null || !create) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    synchronized void save(PendingUse record) throws GeneralSecurityException, IOException {
        workerOnly();
        byte[] encrypted = record.encode(key(true));
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

    /** @return null only when absent; unreadable records must block a new start. */
    synchronized PendingUse load() throws GeneralSecurityException, IOException {
        workerOnly();
        byte[] encrypted;
        try (FileInputStream input = file.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 4096) throw new IOException("Invalid recovery file");
                bytes.write(buffer, 0, count);
            }
            encrypted = bytes.toByteArray();
        } catch (FileNotFoundException absent) { return null; }
        SecretKey stored = key(false);
        if (stored == null) throw new GeneralSecurityException("Recovery key missing");
        return PendingUse.decode(encrypted, stored);
    }

    synchronized void clear() throws GeneralSecurityException, IOException {
        workerOnly();
        keyStore().deleteEntry(ALIAS);
        file.delete();
    }

    /** A read-only observation must never erase a control journal or another device's record. */
    void observe(io.qzxy.lite.core.DeviceProfile profile, io.qzxy.lite.core.WaterOrder order)
            throws GeneralSecurityException, IOException {
        synchronized (PendingUseVault.class) {
            PendingUse previous = load();
            if (previous != null && (previous.isCommand() || !previous.matches(profile))) return;
            PendingUse record = PendingUse.of(profile, order, System.currentTimeMillis());
            if (record != null) save(record);
            else if (!order.blocksStart()) clear();
        }
    }

    void saveCommand(PendingUse record) throws GeneralSecurityException, IOException {
        synchronized (PendingUseVault.class) {
            PendingUse previous = load();
            if (!record.isCommand()) throw new IOException("Expected command record");
            if (previous != null && (!previous.deviceId().equals(record.deviceId())
                    || !previous.projectId().equals(record.projectId())
                    || !previous.requestSerial().equals(record.requestSerial())
                    || (previous.isCommand() && !previous.belongsToAccount(record))
                    || (!previous.orderNo().isEmpty() && !previous.orderNo().equals(record.orderNo()))))
                throw new IOException("Another unresolved order exists");
            save(record);
        }
    }

    /** Atomic start reservation: two activities may not both create a new command. */
    void beginCommand(PendingUse record) throws GeneralSecurityException, IOException {
        synchronized (PendingUseVault.class) {
            if (!record.isCommand() || !record.status().equals("STARTING") || load() != null)
                throw new IOException("Unresolved use prevents a new start");
            save(record);
        }
    }

    /** Explicit user reconciliation only; never clears a known order or a different account/device. */
    void clearUnknownStart(io.qzxy.lite.core.DeviceProfile profile, String accountId)
            throws GeneralSecurityException, IOException {
        synchronized (PendingUseVault.class) {
            PendingUse previous = load();
            if (previous == null || !previous.matches(profile) || !previous.belongsTo(accountId)
                    || !previous.status().equals("STARTING") || !previous.orderNo().isEmpty())
                throw new IOException("Recovery record changed");
            clear();
        }
    }

    void clearSettled(io.qzxy.lite.core.DeviceProfile profile, String accountId, String orderNo)
            throws GeneralSecurityException, IOException {
        synchronized (PendingUseVault.class) {
            PendingUse previous = load();
            if (previous != null && previous.matches(profile) && previous.belongsTo(accountId)
                    && previous.orderNo().equals(orderNo)) clear();
        }
    }

    /** Caller must obtain a fresh idle result and explicit user confirmation for this exact record. */
    void clearRecordedUnknown(PendingUse expected, String accountId) throws GeneralSecurityException, IOException {
        synchronized(PendingUseVault.class) {
            PendingUse current=load();
            if(current==null || !current.sameRecord(expected) || !current.canDismissUnknown(accountId))throw new IOException("旧记录已变化，未解除");
            clear();
        }
    }

    /** Retires only an old unnumbered start after a live idle response; not a settlement claim. */
    void retireIdleUnknown(PendingUse expected,String owner) throws GeneralSecurityException,IOException {
        synchronized(PendingUseVault.class) {
            PendingUse current=load();
            if(current==null || !current.sameRecord(expected) || !current.mayRetireIdleUnknown(owner,System.currentTimeMillis()))throw new IOException("恢复记录已变化");
            // Keep a local outcome-unknown note instead of silently declaring a bill settled.
            if(!context.getSharedPreferences("water-history",Context.MODE_PRIVATE).edit()
                .putString("unconfirmed-"+current.savedAtMillis(),current.describe()+"\n后续查询设备空闲，已移出启动拦截；未取得结算结果。").commit())throw new IOException("历史记录保存失败");
            clear();
        }
    }
}
