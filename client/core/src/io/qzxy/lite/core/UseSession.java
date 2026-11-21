// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/** State transitions only: no credentials, network calls, or device commands. */
public final class UseSession {
    public enum State { IDLE, RESOLVING, READY, STARTING, ACTIVE, STOPPING, SETTLING, COMPLETED, RECONCILE }
    private State state = State.IDLE;

    public synchronized State state() { return state; }

    public synchronized void resolve() {
        require(State.IDLE, State.COMPLETED);
        state = State.RESOLVING;
    }

    public synchronized void deviceResolved() {
        require(State.RESOLVING);
        state = State.READY;
    }

    public synchronized void requestStart() {
        require(State.READY);
        state = State.STARTING;
    }

    public synchronized void startAcknowledged() {
        require(State.STARTING);
        state = State.ACTIVE;
    }

    public synchronized void requestStop() {
        require(State.ACTIVE);
        state = State.STOPPING;
    }

    public synchronized void stopAcknowledged() {
        require(State.STOPPING);
        state = State.SETTLING;
    }

    public synchronized void settlementAcknowledged() {
        require(State.SETTLING);
        state = State.COMPLETED;
    }

    public synchronized void connectionLost() {
        if (state == State.RESOLVING || state == State.READY) {
            state = State.IDLE;
        } else if (state == State.STARTING || state == State.ACTIVE ||
                   state == State.STOPPING || state == State.SETTLING) {
            state = State.RECONCILE;
        }
    }

    public synchronized void cancelBeforeStart() {
        require(State.RESOLVING, State.READY);
        state = State.IDLE;
    }

    /** The caller must first verify the remote outcome; blind retries are invalid. */
    public synchronized void reconcile(State verifiedState) {
        require(State.RECONCILE);
        if (verifiedState != State.ACTIVE && verifiedState != State.SETTLING && verifiedState != State.COMPLETED) {
            throw new IllegalArgumentException("Expected a verified remote session outcome");
        }
        state = verifiedState;
    }

    public synchronized void restore(State persistedState) {
        require(State.IDLE);
        if (persistedState == null) throw new NullPointerException("persistedState");
        // A recovered client cannot know that a previous command completed remotely.
        switch (persistedState) {
            case STARTING: case ACTIVE: case STOPPING: case SETTLING: case RECONCILE:
                state = State.RECONCILE;
                break;
            case COMPLETED:
                state = State.COMPLETED;
                break;
            default:
                state = State.IDLE;
        }
    }

    private void require(State... allowed) {
        for (State candidate : allowed) if (state == candidate) return;
        throw new IllegalStateException("Invalid transition from " + state);
    }
}
