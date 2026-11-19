"""Guard the UI behaviour contract that refactors must not regress.

Run with: python scripts/test_ui_contract.py

This is a *static* contract test: it reads the Java sources and asserts the structural anchors
behind behaviours that cannot be exercised in this environment (gestures, adaptive layout,
scroll restore, snapshots). It is not a substitute for the real-device checklist in
docs/real-device-acceptance.md - it is the part that CAN be automated here, and it turns a silent
regression into a red build. A3/A1 must keep every one of these anchors green.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PREVIEW = ROOT / "client/android/src/io/qzxy/lite/preview"
ACTIVITY = PREVIEW / "TimetableActivity.java"
PAGE = PREVIEW / "Page.java"
CARD = PREVIEW / "CardLayer.java"
BOARD = PREVIEW / "WeekBoard.java"

failures = []
checks = 0


def require(source: str, needle: str, where: str, why: str):
    global checks
    checks += 1
    if needle not in source:
        failures.append("%s: missing %r (%s)" % (where, needle, why))


def main():
    global checks
    activity = ACTIVITY.read_text(encoding="utf-8")
    page = PAGE.read_text(encoding="utf-8")
    card = CARD.read_text(encoding="utf-8")
    board = BOARD.read_text(encoding="utf-8")

    # --- bottom navigation: three parallel views, selected state driven by nav -------------
    for label in ("今日", "课表", "账号"):
        require(activity, 'tabItem("%s"' % label, "TimetableActivity",
                "bottom navigation tab must survive every refactor")
    require(activity, "nav.tab()==0", "TimetableActivity", "tab selection reads nav state")

    # --- adaptive timetable: board measures itself, pager hands down the real height --------
    require(board, "protected void onMeasure", "WeekBoard",
            "adaptive row height relies on the board's own measurement")
    require(board, "minimum", "WeekBoard", "min readable row height guard")
    require(activity, "verticalPosition()", "TimetableActivity",
            "week switch must preserve per-week scroll position")
    require(activity, "shiftWeek", "TimetableActivity", "week switch gesture/arrow")

    # --- scroll position preserved across a rebuild ---------------------------------------
    require(activity, "getScrollY()", "TimetableActivity", "capture scroll before rebuild")
    require(activity, "scrollTo(0,y)", "TimetableActivity", "restore scroll after rebuild")

    # --- back gesture + card animation shell (Page/CardLayer) ------------------------------
    require(page, "protected boolean backMayStay()", "Page", "in-page back vs close-page decision")
    require(page, "void startActivity(Intent intent)", "Page", "startActivity triggers snapshot")
    require(card, "static void hold(", "CardLayer", "page snapshot for back animation")
    require(card, "private void animateTo(", "CardLayer", "card scale/alpha animation")
    require(card, "static void clearPending()", "CardLayer", "sensitive-page snapshot purge")

    # --- snapshot privacy (S4) is not undone by a UI refactor ------------------------------
    require(card, "allowPageSnapshot()", "CardLayer", "sensitive pages must stay snapshot-free")
    require(page, "allowPageSnapshot", "Page", "page-level snapshot policy hook")

    # --- dynamic today content: the only thing a clock tick actually changes --------------
    # After A3 these become direct update methods; until then they are computed inside render().
    require(activity, "ScheduleInfo.summary", "TimetableActivity",
            "today's current/next-class summary")
    require(activity, "ScheduleInfo.status", "TimetableActivity",
            "per-row 已结束 / 正在上课 state")
    # courseRow is where per-row live state is painted; it must remain present.
    require(activity, "private View courseRow(", "TimetableActivity",
            "today's live class rows")

    # --- A3: in-place today update, not a full rebuild on every tick -----------------------
    require(activity, "RefreshDecision.Level", "TimetableActivity",
            "clock tick must use the three-level refresh decision")
    require(activity, "updateTodayDynamic()", "TimetableActivity",
            "today tab must refresh dynamic labels in place")
    require(activity, "TODAY_DYNAMIC", "TimetableActivity",
            "in-place refresh branch must exist")
    require(activity, "setVisibility(live?View.VISIBLE:View.GONE)", "TimetableActivity",
            "live chip toggles by visibility, never by re-adding views")
    # The clock tick must NOT unconditionally call render() for the today tab any more.
    tick_block = activity[activity.find("private void refreshClock()"):activity.find("private void updateTodayDynamic()")]
    require(tick_block, "updateTodayDynamic()", "refreshClock",
            "same-day today tick must take the in-place path")
    if "clockRedraw.tick" in activity:
        failures.append("TimetableActivity: old unconditional clockRedraw.tick rebuild is still present")
    checks += 1

    # --- A1 staged Screen extraction -------------------------------------------------------
    # Screens are nested static classes of the activity so they can reuse its private UI helpers
    # with byte-for-byte identical behaviour - no visibility widening, no Context confusion.
    require(activity, "new CampusScreen(this)", "TimetableActivity",
            "render() must delegate the campus tab to CampusScreen")
    require(activity, "class CampusScreen", "TimetableActivity", "CampusScreen extracted")
    require(activity, "检查更新", "CampusScreen block", "campus content moved out of render path")
    # The old inline method body must be gone: campus rows are built in the Screen, not here.
    if "private void campusContent() {" in activity:
        failures.append("TimetableActivity still owns campusContent() inline (not extracted)")

    # A1 stage 2: today screen extraction, must keep the A3 in-place update wiring intact.
    require(activity, "new HomeScreen(this)", "TimetableActivity",
            "render() must delegate the today tab to HomeScreen")
    require(activity, "class HomeScreen", "TimetableActivity", "HomeScreen extracted")
    if "private void todayContent() {" in activity:
        failures.append("TimetableActivity still owns todayContent() inline (not extracted)")

    # A1 stage 3: schedule screen extraction, must keep pager wiring and adaptive height intact.
    require(activity, "new ScheduleScreen(this)", "TimetableActivity",
            "render() must delegate the schedule tab to ScheduleScreen")
    require(activity, "class ScheduleScreen", "TimetableActivity", "ScheduleScreen extracted")
    if "private void timetableContent() {" in activity:
        failures.append("TimetableActivity still owns timetableContent() inline (not extracted)")

    if failures:
        print("UI CONTRACT FAILED (%d/%d checks):" % (len(failures), checks))
        for f in failures:
            print("  - " + f)
        raise SystemExit(1)
    print("PASS: %d UI behaviour contract anchors intact" % checks)
    print("NOTE: gestures / adaptive layout / snapshots are statically guarded here; "
          "visual and runtime behaviour still requires the real-device checklist.")


if __name__ == "__main__":
    main()
