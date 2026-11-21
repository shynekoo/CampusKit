// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/**
 * Coordinates the periodic "is the saved session still valid" check.
 *
 * <p>The main screen calls this on every resume. Three things must hold: a fast double-resume must
 * not launch two network checks, a just-verified session should not be re-checked on every tab tap,
 * and a timeout/DNS failure is not a logout. This object owns only those decisions - no Android, no
 * network, no storage - so they can be tested deterministically. The caller supplies the clock (an
 * increasing millisecond value) and performs the actual I/O.</p>
 */
public final class SessionCheck {
    private final long cacheMillis;
    private boolean inFlight;
    private boolean signedIn;
    private long lastSuccessAt = Long.MIN_VALUE;
    private boolean expiryNoticePending;

    public SessionCheck(long cacheMillis) { this.cacheMillis = cacheMillis; }

    /** Returns true and atomically claims the check; false means one is running or success is fresh. */
    public synchronized boolean shouldStart(long now) {
        if (inFlight) return false;
        if (signedIn && now - lastSuccessAt < cacheMillis) return false;
        inFlight = true;
        return true;
    }

    /** A confirmed-valid session: cache it and keep the user signed in. */
    public synchronized void finishValid(long now) {
        inFlight = false;
        signedIn = true;
        lastSuccessAt = now;
    }

    /** The server said the session is expired: clear it and queue a one-shot notice. */
    public synchronized void finishExpired(long now) {
        inFlight = false;
        signedIn = false;
        lastSuccessAt = Long.MIN_VALUE;
        expiryNoticePending = true;
    }

    /** Network/parse failure: release the in-flight claim but do not change sign-in state. */
    public synchronized void finishNetworkError(long now) {
        inFlight = false;
    }

    public synchronized boolean isSignedIn() { return signedIn; }

    public synchronized boolean consumeExpiryNotice() {
        boolean pending = expiryNoticePending;
        expiryNoticePending = false;
        return pending;
    }

    /** Called when the user logs in again so the cache is no longer treated as stale-expired. */
    public synchronized void reset() {
        inFlight = false;
        signedIn = false;
        lastSuccessAt = Long.MIN_VALUE;
        expiryNoticePending = false;
    }
}
