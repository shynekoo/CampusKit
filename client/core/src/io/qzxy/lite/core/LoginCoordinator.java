// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.util.Objects;

/** Reject duplicate submissions and responses from cancelled or superseded attempts. */
public final class LoginCoordinator {
    public enum State { SIGNED_OUT, SIGNING_IN, SIGNED_IN }
    private State state = State.SIGNED_OUT;
    private long generation;
    private AccountSession session;

    public synchronized long begin() {
        if (state != State.SIGNED_OUT) throw new IllegalStateException("Login already active");
        state = State.SIGNING_IN;
        return ++generation;
    }

    public synchronized boolean succeed(long attempt, AccountSession value) {
        if (state != State.SIGNING_IN || attempt != generation) return false;
        session = Objects.requireNonNull(value, "Authenticated session required");
        state = State.SIGNED_IN;
        return true;
    }

    public synchronized boolean fail(long attempt) {
        if (state != State.SIGNING_IN || attempt != generation) return false;
        state = State.SIGNED_OUT;
        return true;
    }

    public synchronized void signOut() {
        ++generation;
        session = null;
        state = State.SIGNED_OUT;
    }

    public synchronized State state() { return state; }
    public synchronized AccountSession session() { return session; }
}
