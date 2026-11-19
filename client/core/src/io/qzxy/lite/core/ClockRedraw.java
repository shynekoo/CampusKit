package io.qzxy.lite.core;

/**
 * Decides whether a periodic clock tick needs the screen rebuilt.
 *
 * <p>The 今日 tab shows a live "current class" marker, so it redraws on each tick (the caller keeps
 * the scroll position). Other tabs only depend on the date - a new day changes the week number and
 * the highlighted column - so a tick within the same day is a no-op instead of a full rebuild.
 * Keeping the decision here means the rule is unit-tested and the activity cannot quietly regress
 * to rebuilding on every tick.</p>
 */
public final class ClockRedraw {
    private String lastDay;

    public ClockRedraw() { this(""); }

    /** Seed with the day the activity last displayed, so a restored screen does not redraw twice. */
    public ClockRedraw(String lastDay) { this.lastDay = lastDay == null ? "" : lastDay; }

    /** @return true when the caller should re-render; false to leave the current view untouched. */
    public boolean tick(String todayIso, int tab) {
        boolean newDay = !todayIso.equals(lastDay);
        lastDay = todayIso;
        return newDay || tab == 0;
    }
}
