// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/**
 * Decides how much of the screen a periodic clock tick must refresh.
 *
 * <p>The old rule ({@code newDay || tab == 0}) rebuilt the entire View tree on every tick while the
 * user sat on 今日, just to move one "正在上课" marker. This class splits that into three levels so
 * the activity can update a handful of existing views instead of calling {@code setContentView}
 * again. Keeping the decision here makes it unit-testable and stops the activity quietly regressing
 * to a full rebuild.</p>
 */
public final class RefreshDecision {
    public enum Level {
        /** Nothing depends on the clock right now; leave the view untouched. */
        NONE,
        /** Same day on 今日: only the current/next-class text and per-row live state change. */
        TODAY_DYNAMIC,
        /** A new day (any tab): week number, date header and today's class list all change. */
        FULL
    }

    private String lastDay;

    public RefreshDecision() { this(""); }

    /** @param lastDay the day the screen currently displays, so a restored screen is not rebuilt twice. */
    public RefreshDecision(String lastDay) { this.lastDay = lastDay == null ? "" : lastDay; }

    /** The day currently displayed; callers keep this in sync with their persisted value. */
    public String lastDay() { return lastDay; }

    /**
     * @return the smallest refresh that keeps the screen correct for {@code todayIso} on {@code tab}.
     * A tick on a non-today tab within the same day is a no-op; on 今日 it is an in-place update;
     * crossing midnight always forces a full rebuild regardless of tab.
     */
    public Level forTick(String todayIso, int tab) {
        boolean newDay = !todayIso.equals(lastDay);
        lastDay = todayIso;
        if (newDay) return Level.FULL;
        return tab == 0 ? Level.TODAY_DYNAMIC : Level.NONE;
    }
}
