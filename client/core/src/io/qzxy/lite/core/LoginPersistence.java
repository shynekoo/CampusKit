// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/** A login replacement on one serial worker; cancellation restores the prior session. */
public final class LoginPersistence {
    public interface Store {
        AccountSession load() throws Exception;
        void save(AccountSession session) throws Exception;
        void clear() throws Exception;
    }
    private final Store store;
    private AccountSession previous;
    private boolean staged;
    public LoginPersistence(Store store) { this.store = store; }
    public void stage(AccountSession next) throws Exception {
        if (staged) throw new IllegalStateException("Login already staged");
        previous = store.load();
        // Mark before writing so a failed partial save can still be rolled back.
        staged = true;
        try { store.save(next); } catch (Exception failure) {
            try { cancel(); } catch (Exception rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
    public void accept() { staged = false; previous = null; }
    public void cancel() throws Exception {
        if (!staged) return;
        if (previous == null) store.clear(); else store.save(previous);
        staged = false; previous = null;
    }
}
