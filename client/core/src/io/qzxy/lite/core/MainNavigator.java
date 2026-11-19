package io.qzxy.lite.core;

/**
 * The parallel-tab and campus-scope navigation of the main screen, as a pure state machine.
 *
 * <p>The activity owns the views; this object owns only "which view is current" and the back
 * order. Extracting it makes the one rule that is easy to regress - back pops the campus records
 * scope, then the non-today tab, and only then closes the page - testable without a device. No
 * Android types, no view references, no storage; the caller persists and restores the two ints.</p>
 */
public final class MainNavigator {
    /** 0 = 今日, 1 = 课表, 2 = 趣智校园. */
    private int tab;
    /** Inside 趣智校园: 0 = entry list, 1 = records view. */
    private int campusView;

    public MainNavigator() { this(0, 0); }

    /** Restored values are clamped so a stale or forged bundle cannot leave an impossible screen. */
    public MainNavigator(int tab, int campusView) {
        this.tab = clamp(tab, 2);
        this.campusView = clamp(campusView, 1);
    }

    public int tab() { return tab; }
    public int campusView() { return campusView; }

    /** Change tab; leaving the 趣智校园 tab resets its internal scope to the entry list. */
    public void selectTab(int value) {
        tab = clamp(value, 2);
        campusView = 0;
    }

    public void openCampusRecords() {
        if (tab == 2) campusView = 1;
    }

    /** Back out of the campus records view to its entry list. */
    public void openCampusList() {
        if (tab == 2) campusView = 0;
    }

    /** True when back is handled inside the page rather than closing it. */
    public boolean backMayStay() { return tab != 0 || campusView == 1; }

    /**
     * Consume one level of internal navigation. Returns true when the caller should re-render
     * instead of finishing; false means back has nothing left to pop and the page should close.
     */
    public boolean consumeBack() {
        if (tab == 2 && campusView == 1) { campusView = 0; return true; }
        if (tab != 0) { tab = 0; campusView = 0; return true; }
        return false;
    }

    private static int clamp(int value, int max) {
        return value < 0 ? 0 : value > max ? max : value;
    }
}
