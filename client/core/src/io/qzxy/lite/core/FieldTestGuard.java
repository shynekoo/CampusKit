// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/**
 * Timing policy for the controlled field test, kept out of the Android layer so the safety limits can
 * be tested with a synthetic clock.
 *
 * Three separate limits, because they guard three different things:
 * - {@link #CONFIRM_DEADLINE_MILLIS} bounds how long a start may stay unconfirmed. Past it the caller
 *   must stop guessing and tell the user to check the physical valve, not retry the start.
 * - {@link #POLL_INTERVAL_MILLIS} is how often the read-only confirmation query may run. Polling is
 *   safe to repeat; the start and stop commands are not, and are never repeated here.
 * - {@link #AUTO_STOP_MILLIS} is the field-test-only cap: water is stopped automatically after this
 *   long, so a first live run cannot quietly keep running and billing.
 */
public final class FieldTestGuard {
    public static final long CONFIRM_DEADLINE_MILLIS = 20000L;
    public static final long POLL_INTERVAL_MILLIS = 2500L;
    public static final long AUTO_STOP_MILLIS = 10000L;

    private long requestedAt = -1L, activeAt = -1L, lastPollAt = -1L;

    /** Marks that a start command was sent. The command itself is never re-sent. */
    public void startRequested(long now) { requestedAt = now; activeAt = -1L; lastPollAt = -1L; }

    public boolean awaitingConfirmation() { return requestedAt >= 0L && activeAt < 0L; }

    /** True once the start has stayed unconfirmed past the deadline. */
    public boolean confirmationOverdue(long now) { return awaitingConfirmation() && elapsed(now, requestedAt) >= CONFIRM_DEADLINE_MILLIS; }

    public boolean pollDue(long now) {
        return awaitingConfirmation() && (lastPollAt < 0L || elapsed(now, lastPollAt) >= POLL_INTERVAL_MILLIS);
    }

    public void polled(long now) { lastPollAt = now; }

    /** The device confirmed the water is running; the auto-stop cap starts counting from here. */
    public void confirmedActive(long now) { activeAt = now; }

    public boolean active() { return activeAt >= 0L; }

    public boolean autoStopDue(long now) { return active() && elapsed(now, activeAt) >= AUTO_STOP_MILLIS; }

    /** Whole seconds left before the automatic stop; 0 once it is due or the water is not running. */
    public long remainingAutoStopSeconds(long now) {
        if (!active()) return 0L;
        long left = AUTO_STOP_MILLIS - elapsed(now, activeAt);
        return left <= 0L ? 0L : (left + 999L) / 1000L;
    }

    public void reset() { requestedAt = -1L; activeAt = -1L; lastPollAt = -1L; }

    /** A clock that jumps backwards must not shorten a deadline, so negative elapsed counts as zero. */
    private static long elapsed(long now, long since) { return now < since ? 0L : now - since; }
}
