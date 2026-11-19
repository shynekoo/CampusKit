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

    # --- bottom navigation: three persistent tabs, selected state driven by nav -------------
    # UI-2: the three tabs now live in one AnimatedNavBar built once by MainShell, not rebuilt
    # by every render(). Keep the "three entries survive" guard, retargeted at the new owner.
    nav_src_path = PREVIEW / "AnimatedNavBar.java"
    nav_src = nav_src_path.read_text(encoding="utf-8") if nav_src_path.exists() else ""
    for label in ("今日", "课表", "账号"):
        require(nav_src, '"%s"' % label, "AnimatedNavBar",
                "bottom navigation tab must survive every refactor")
    require(activity, "nav.tab()==0", "TimetableActivity", "tab selection reads nav state")

    # --- adaptive timetable: board measures itself, pager hands down the real height --------
    require(board, "protected void onMeasure", "WeekBoard",
            "adaptive row height relies on the board's own measurement")
    require(board, "minimum", "WeekBoard", "min readable row height guard")
    require(activity, "verticalPosition()", "TimetableActivity",
            "week switch must preserve per-week scroll position")
    require(activity, "shiftWeek", "TimetableActivity", "week switch gesture/arrow")
    # 回本周 jumps several weeks at once: there is no adjacent page to slide in, and the old
    # "slide out then rebuild" order showed a blank board then snapped to the new week when the
    # animation ended. It must commit the week first and play the content transition instead.
    gcw_start = activity.find("private void goCurrentWeek()")
    gcw_block = activity[gcw_start:activity.find("\n    private ", gcw_start + 1)] if gcw_start >= 0 else ""
    require(gcw_block, "jumpWeek(delta)", "goCurrentWeek", "回本周 must land through jumpWeek")
    jw_start = activity.find("private void jumpWeek(int delta)")
    jw_block = activity[jw_start:activity.find("\n    private ", jw_start + 1)] if jw_start >= 0 else ""
    require(jw_block, "week=next;", "jumpWeek", "the target week is committed before the transition")
    require(jw_block, "renderContent(true,", "jumpWeek",
            "the new board must be on screen during the transition, not appear after it")
    require(jw_block, "true);", "jumpWeek",
            "a multi-week jump must travel a full page like a swipe, not use the tab crossfade")
    require(jw_block, "tabScroll[nav.tab()]=scroll", "jumpWeek",
            "a multi-week jump keeps the vertical reading position")
    require(board, "-(delta>0?getWidth():-getWidth())", "WeekBoard.Pager",
            "a multi-week slide must move exactly one page, not |delta| pages")
    require(board, "MotionSpec.WEEK_SLIDE_DURATION", "WeekBoard.Pager",
            "the pager's slide must share the one week-slide duration token")
    sw_start = activity.find("private void selectWeek()")
    sw_block = activity[sw_start:activity.find("\n    private ", sw_start + 1)] if sw_start >= 0 else ""
    require(sw_block, "Math.abs(delta)==1", "selectWeek",
            "an adjacent week picked in the sheet uses the pager's existing page, not a rebuild")

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

    # 学业情况查询 (2.5.8.2): the today-教务 card must keep its entry, and the screen must go
    # through the shared signed-in EduClient rather than opening the school site in a WebView.
    status_activity = PREVIEW / "AcademicStatusActivity.java"
    require(status_activity.read_text(encoding="utf-8") if status_activity.exists() else "",
            "EduClient.academicStatus()", "AcademicStatusActivity",
            "academic status must use the shared signed-in client")
    require(activity, '"学业情况"', "TimetableActivity",
            "today-教务 card must expose the academic status entry")
    require(activity, "openAcademicStatus()", "TimetableActivity",
            "academic status tile must route to its own screen")
    require(activity, "AcademicStatusActivity.class", "TimetableActivity",
            "academic status tile must launch AcademicStatusActivity")
    core = ROOT / "client/core/src/io/qzxy/lite/core/EduAcademic.java"
    require(core.read_text(encoding="utf-8") if core.exists() else "",
            "static Report parse(String html)", "EduAcademic",
            "academic status extraction must live in the testable core")

    # 外观页 (2.5.8.3): the preview corner must convert dp to px like WeekBoard does, or it shows
    # ~1/3 of the real radius on density screens. The two explanatory captions were removed on
    # request; keep them gone, and keep the theme's first swatch labelled 默认 not 品牌蓝.
    appearance = (PREVIEW / "AppearanceActivity.java").read_text(encoding="utf-8")
    require(appearance, "drawRoundRect(rect, dp(draftRadius)", "AppearanceActivity",
            "preview corner must convert dp to px to match the real timetable")
    for stale in ("打开后往回滑时", "这一块就是课表本身", '"品牌蓝"'):
        checks += 1
        if stale in appearance:
            failures.append("AppearanceActivity: removed copy %r came back" % stale)

    # 学业情况页 (2.5.8.4): the crash was a View added to results twice. card() must stay a pure
    # factory and addCard() the single attach point; note must be set once in onCreate, not per show.
    status_activity = PREVIEW / "AcademicStatusActivity.java"
    status = status_activity.read_text(encoding="utf-8") if status_activity.exists() else ""
    require(status, "private void addCard(LinearLayout card)", "AcademicStatusActivity",
            "single attach point for academic cards")
    card_block = status[status.find("private LinearLayout card()"):status.find("private void addCard(")]
    checks += 1
    if "results.addView" in card_block:
        failures.append("AcademicStatusActivity: card() attaches itself again (double-parent crash)")
    checks += 1
    if status.count("screen.note(") != 1:
        failures.append("AcademicStatusActivity: note must be set once, not stacked on every show")

    # 教务 card order: row 1 = 成绩/空教室/教务系统, row 2 = 学业情况/河工程/科信.
    ac = activity[activity.find("private void academicCard("):activity.find("private void openEdu(")]
    checks += 1
    expected = ['"score","成绩"', '"room","空教室"', '"cap","教务系统"',
                '"check","学业情况"', '"school","河工程官网"', '"globe","科信官网"']
    positions = [ac.find(t) for t in expected]
    if -1 in positions or positions != sorted(positions):
        failures.append("TimetableActivity: 教务 card tile order does not match row1=成绩/空教室/教务系统 row2=学业情况/河工程/科信")

    # 学业情况页 (2.5.8.5): the page carries 一级/二级 categories and the screen must show that
    # hierarchy — one card per second-level category, deeper nodes as indented rows keyed off depth.
    require(status, "private void tree(List<EduAcademic.Node> nodes)", "AcademicStatusActivity",
            "academic categories must render as a tree, not one flat card per node")
    require(status, "node.depth <= 2", "AcademicStatusActivity",
            "first/second-level categories own the cards")
    require(status, "dp(14) * Math.max(0, node.depth - 2)", "AcademicStatusActivity",
            "nested rows must indent by depth so 一级/二级 read as levels")
    show_block = status[status.find("private void show(EduAcademic.Report report)"):status.find("private void tree(")]
    checks += 1
    if "addCard(nodeCard(" in show_block or "addCard(leafCard(" in show_block:
        failures.append("AcademicStatusActivity: show() flattened the tree into per-node cards again")
    core_academic = (ROOT / "client/core/src/io/qzxy/lite/core/EduAcademic.java").read_text(encoding="utf-8")
    require(core_academic, "public final int depth", "EduAcademic",
            "core must expose the school's tree depth")
    require(core_academic, r"\\.appendTo\\", "EduAcademic",
            "depth must come from the page's own appendTo parent links")

    # 学业情况页 (2.5.8.6): each group's courses were missing — only the categories were shown.
    # Courses now come from the school's own per-group endpoint and open on tap.
    require(core_academic, "public static List<Course> parseCourses(String json)", "EduAcademic",
            "course rows must be parsed in the testable core")
    require(core_academic, "public static final class Course", "EduAcademic",
            "courses need a typed model, not raw JSON strings")
    client = (PREVIEW / "EduClient.java").read_text(encoding="utf-8")
    require(client, "ACADEMIC_COURSES", "EduClient",
            "the per-group course endpoint must be named by the feature")
    require(client, 'if (path.startsWith(ACADEMIC_PAGE) || path.startsWith(ACADEMIC_COURSES)) return;',
            "EduClient",
            "the course query must be added to the read-only path allow-list explicitly")
    require(status, "private void toggle(EduAcademic.Node node", "AcademicStatusActivity",
            "courses must open on tap rather than all at once")
    require(status, "rowGroup(node, !hasChildren)", "AcademicStatusActivity",
            "only a group that holds courses may be tappable")
    require(status, "courses.put(node.id, list)", "AcademicStatusActivity",
            "a fetched group must be cached so re-opening costs nothing")
    require(status, '"未通过", report.failed', "AcademicStatusActivity",
            "the headline must show the counts the school reports, not a subset")

    # --- CampusKit UI 2.0 / UI-1: centralized design + motion tokens ------------------------
    ui_tokens = (PREVIEW / "UiTokens.java")
    motion_spec = (PREVIEW / "MotionSpec.java")
    tokens_src = ui_tokens.read_text(encoding="utf-8") if ui_tokens.exists() else ""
    motion_src = motion_spec.read_text(encoding="utf-8") if motion_spec.exists() else ""
    require(tokens_src, "CARD_RADIUS = 18", "UiTokens", "UI-1 card radius baseline is 18dp")
    require(tokens_src, "NAV_RADIUS = 28", "UiTokens", "nav bar corner refined to 28dp")
    require(tokens_src, "PILL_RADIUS = 16", "UiTokens", "selection pill corner refined to 16dp")
    require(tokens_src, "PILL_W = 64", "UiTokens", "selection pill width 64dp")
    require(tokens_src, "PILL_H = 32", "UiTokens", "selection pill height 32dp (behind icon only)")
    require(tokens_src, "NAV_CONTENT_HEIGHT = 72", "UiTokens", "nav content height in 68-76dp")
    require(tokens_src, "MIN_TOUCH = 48", "UiTokens", "48dp minimum touch target")
    require(tokens_src, "NAV_ICON = 22", "UiTokens", "22dp nav icon")
    require(tokens_src, "NAV_TEXT = 12", "UiTokens", "12sp nav label")
    require(tokens_src, "PAGE_TITLE = 28", "UiTokens", "28sp page title")
    require(tokens_src, "CARD_TITLE = 16", "UiTokens", "16sp card title")
    require(tokens_src, "SPACE_4 = 4", "UiTokens", "4dp spacing base")
    require(motion_src, "PRESS_DURATION = 140", "MotionSpec", "140ms press feedback")
    require(motion_src, "NAV_DURATION = 260", "MotionSpec", "260ms nav capsule")
    require(motion_src, "TAB_ENTER_DURATION = 220", "MotionSpec", "220ms tab enter")
    require(motion_src, "TAB_EXIT_DURATION = 180", "MotionSpec", "180ms tab exit")
    require(motion_src, "TAB_OFFSET_DP = 8", "MotionSpec", "8dp tab offset")
    require(motion_src, "PathInterpolator(0.2f, 0f, 0f, 1f)", "MotionSpec", "indicator interpolator")
    require(motion_src, "areAnimatorsEnabled()", "MotionSpec", "Android 8+ animation-off detection")
    require(motion_src, "ANIMATOR_DURATION_SCALE", "MotionSpec", "pre-8 animation scale fallback")
    # tokens must be actually used, not dead code
    require(activity, "UiTokens.CARD_RADIUS", "TimetableActivity", "whiteCard must use the shared token")
    require(nav_src, "UiTokens.NAV_RADIUS", "AnimatedNavBar", "nav bar must use the shared token")
    # narrow-screen adaptation must survive tokenization
    screens = (PREVIEW / "AppUi.java").read_text(encoding="utf-8")
    require(screens, "static int side(Context c,int base)", "AppUi",
            "AppUi.side() narrow-screen adaptation must be preserved")

    # --- CampusKit UI 2.0 / UI-2: persistent shell + animated nav, no rebuild per tab --------
    shell_path = PREVIEW / "MainShell.java"
    shell_src = shell_path.read_text(encoding="utf-8") if shell_path.exists() else ""
    require(shell_src, "class MainShell extends LinearLayout", "MainShell", "persistent shell type")
    require(shell_src, "headerHost", "MainShell", "header host region")
    require(shell_src, "contentHost", "MainShell", "content host region")
    require(shell_src, "navHolder", "MainShell", "nav holder region")
    require(shell_src, "AnimatedNavBar", "MainShell", "shell owns the one nav bar")
    require(shell_src, "WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()", "MainShell",
            "system bar + cutout insets handled once by shell")
    require(shell_src, "setPadding", "MainShell", "shell applies the single inset padding")
    # AnimatedNavBar: one indicator + one tabRow, three tabs built ONCE, capsule + colour anim.
    require(nav_src, "class AnimatedNavBar extends FrameLayout", "AnimatedNavBar", "nav bar type")
    require(nav_src, "indicator", "AnimatedNavBar", "moving capsule indicator")
    require(nav_src, "tabRow", "AnimatedNavBar", "three-slot tab row")
    require(nav_src, "AppUi.icon(", "AnimatedNavBar", "reuse existing icon family")
    require(nav_src, "ArgbEvaluator", "AnimatedNavBar", "selected/unselected colour interpolation")
    require(nav_src, "invalidateSelf()", "AnimatedNavBar", "reuse cached icon drawable, no per-frame alloc")
    require(nav_src, "setContentDescription", "AnimatedNavBar", "selected tab announced to a11y")
    require(nav_src, "MotionSpec.NAV_DURATION", "AnimatedNavBar", "capsule uses shared nav duration")
    require(nav_src, "MotionSpec.shouldAnimate(", "AnimatedNavBar", "animation-off handled")
    require(nav_src, "translationX", "AnimatedNavBar", "capsule moves by translationX only")
    require(nav_src, "target < 0 || target > 2", "AnimatedNavBar", "selected index always 0/1/2")
    # --- nav visual refinement: house icon, fixed-size icon, pill behind icon only ----------
    require(nav_src, '"home", "calendar", "user"', "AnimatedNavBar",
            "today tab uses a house icon; calendar/user unchanged")
    require(screens, 'case "home":', "AppUi", "house icon drawing must be provided by the shared icon family")
    require(nav_src, "UiTokens.PILL_W", "AnimatedNavBar", "pill width comes from the 64dp token")
    require(nav_src, "UiTokens.PILL_H", "AnimatedNavBar", "pill height comes from the 32dp token")
    require(nav_src, "AppUi.dp(context, UiTokens.NAV_ICON),AppUi.dp(context, UiTokens.NAV_ICON)",
            "AnimatedNavBar", "icon ImageView must use a fixed dp size, not wrap_content (0x0 bug)")
    require(nav_src, "iconSlots", "AnimatedNavBar", "per-tab icon slots keep icon/pill/label aligned")
    require(nav_src, "indicator.layout(originX, iconTop, originX + indicatorW, iconTop + indicatorH)",
            "AnimatedNavBar", "pill is positioned behind the icon row, not the whole tab")
    require(nav_src, "UiTokens.MIN_TOUCH", "AnimatedNavBar", "each tab keeps a 48dp touch target")
    # Activity: shell created once, tabs never re-inflated on select, split render flow present.
    require(activity, "setContentView(shell)", "TimetableActivity", "setContentView only inside ensureShell")
    checks += 1
    if activity.count("setContentView(") != 1:
        failures.append("TimetableActivity: setContentView must appear exactly once (ensureShell only)")
    checks += 1
    if activity.count("new AnimatedNavBar(") != 0:
        failures.append("TimetableActivity: nav bar must be owned by MainShell, not rebuilt by activity")
    checks += 1
    if activity.count("new MainShell(") != 1:
        failures.append("TimetableActivity: MainShell must be created once")
    st = activity.find("private void selectTab(")
    select_block = activity[st:activity.find("\n    private ", st + 1)] if st >= 0 else ""
    checks += 1
    if "new LinearLayout" in select_block or "setContentView(" in select_block:
        failures.append("selectTab: must not allocate new tab views or re-inflate the shell")
    require(activity, "private void ensureShell()", "TimetableActivity", "ensureShell split out")
    require(activity, "private void renderHeader()", "TimetableActivity", "renderHeader split out")
    require(activity, "private LinearLayout buildScreen(LinearLayout target)", "TimetableActivity",
            "buildScreen fills the current screen root")
    require(activity, "private void renderContent(boolean animate, int direction)", "TimetableActivity",
            "renderContent owns content swap")
    require(activity, "private void selectTab(int target)", "TimetableActivity", "unified selectTab flow")
    require(activity, "renderContent(false, 0)", "TimetableActivity", "render() never animates tabs")
    require(activity, "renderContent(true, direction)", "TimetableActivity", "selectTab animates tabs")
    require(activity, "navBar.select(target, true)", "TimetableActivity", "capsule animates on user tab switch")
    # MainNavigator back order and Page/CardLayer must be byte-for-byte unchanged.
    nav_core = (ROOT / "client/core/src/io/qzxy/lite/core/MainNavigator.java").read_text(encoding="utf-8")
    require(nav_core, "if (tab == 2 && campusView == 1) { campusView = 0; return true; }", "MainNavigator",
            "campus records back step unchanged")
    require(nav_core, "if (tab != 0) { tab = 0; campusView = 0; return true; }", "MainNavigator",
            "non-today back step unchanged")
    require(activity, "if (nav.consumeBack()) { render(); return; }", "TimetableActivity",
            "back uses non-animated render, no race with system gesture")
    # Rapid tab switching must clear stale transition views so contentHost never keeps >1 finished page.
    rc_start = activity.find("private void renderContent(boolean animate, int direction, final boolean slide)")
    rc_block = activity[rc_start:activity.find("\n    private ", rc_start + 1)] if rc_start >= 0 else ""
    require(rc_block, "getChildCount() > 1", "renderContent",
            "stale transition views from a cancelled switch must be removed")
    require(rc_block, "removeViewAt(0)", "renderContent",
            "orphaned transition pages must be pruned before adding the new page")
    require(rc_block, "generation != transitionGeneration", "renderContent",
            "late animation callbacks must not delete the newest page")
    # UI-3 crossfade parameters: alpha 0.7->1 + +/-8dp offset in 220ms, old alpha 1->0 in 180ms.
    require(rc_block, "next.setAlpha(slide ? 1f : .7f)", "renderContent",
            "tab crossfade starts the new page at alpha 0.7; the slide stays opaque")
    require(rc_block, ".7f + .3f*t", "renderContent", "new page fades up to alpha 1")
    require(rc_block, "MotionSpec.TAB_ENTER_DURATION", "renderContent", "enter uses 220ms")
    require(rc_block, "MotionSpec.TAB_EXIT_DURATION", "renderContent", "exit uses 180ms")
    require(rc_block, "MotionSpec.TAB_OFFSET_DP", "renderContent", "horizontal offset uses the 8dp token")
    # 跨周跳转走 slide 分支：一屏位移、两页同向同行、时长与 Pager 共用 token、旧页不淡出。
    require(rc_block, "slide ? pageWidth() * direction", "renderContent",
            "a week jump travels one full page, not the 8dp tab offset")
    require(rc_block, "MotionSpec.WEEK_SLIDE_DURATION", "renderContent",
            "a week jump shares the pager's slide duration")
    require(rc_block, "old.setTranslationX(-offset * t)", "renderContent",
            "the leaving board travels with the incoming one instead of fading in place")
    require(rc_block, "if (!slide) next.setAlpha(.7f + .3f*t)", "renderContent",
            "the full-page slide must not fade; alpha belongs to the tab crossfade only")
    require(rc_block, "next.setAlpha(1f); next.setTranslationX(0f);", "renderContent",
            "new page is forced to final alpha/translationX after transition")
    # Rapid-switch race: the 180ms exit ends before the 220ms enter. Clearing the animator list in
    # exit's end callback lets the still-running enter escape cancellation on the next switch.
    require(rc_block, "new ArrayList<>(contentAnimators)", "renderContent",
            "cancel must iterate a snapshot; end callbacks remove from the live list")
    require(rc_block, "enter.addListener", "renderContent",
            "longer enter animation owns final cleanup, not the shorter exit")
    require(rc_block, "exit.addListener", "renderContent",
            "exit end removes only the outgoing page")
    checks += 1
    guards = rc_block.count("if (generation != transitionGeneration) return;")
    if guards < 4:
        failures.append("renderContent: need generation guards on both update listeners and both end "
                        "callbacks (found %d)" % guards)
    # UI-3 scroll restore: a rebuild of the *same* screen keeps the reader's place, and the restore
    # itself must (a) carry the transition generation, (b) refuse a page a later switch replaced,
    # and (c) run after layout - post() is not ordered after the first measure/layout.
    require(rc_block, "restoreScroll(nav.tab(), tabScroll[nav.tab()], generation)", "renderContent",
            "scroll restore must carry the transition generation")
    render_start = activity.find("private void render()")
    render_block = activity[render_start:activity.find("\n    private ", render_start + 1)] if render_start >= 0 else ""
    require(render_block, "contentTab == nav.tab() && contentCampus == nav.campusView()", "render",
            "a same-screen rebuild captures its own scroll instead of reusing a stale one")
    require(render_block, "renderContent(false, 0)", "render", "render() still never animates tabs")
    require(activity, "addOnPreDrawListener", "TimetableActivity",
            "scroll restore waits for layout (pre-draw), not a bare post()")
    require(activity, "tab != nav.tab()", "restoreScroll",
            "a stale restore must not scroll a different tab's page")
    require(rc_block, "IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS", "renderContent",
            "the leaving page leaves the accessibility tree during the crossfade")
    require(page, "protected boolean backMayStay()", "Page", "Page back contract untouched")
    require(card, "static void clearPending()", "CardLayer", "CardLayer snapshot policy untouched")
    require(board, "protected void onMeasure", "WeekBoard", "WeekBoard adaptive measure untouched")

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
