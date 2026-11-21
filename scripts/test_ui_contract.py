# SPDX-License-Identifier: MPL-2.0
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


def forbid(source: str, needle: str, where: str, why: str):
    global checks
    checks += 1
    if needle in source:
        failures.append("%s: still has %r (%s)" % (where, needle, why))


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

    # 教务 card order: row 1 = 成绩/空教室/学业情况/学生评价 (the four queries), row 2 =
    # 教务系统/河工程官网/科信官网 (entries and official sites). 学生评价 was added in 2.5.10 and
    # belongs with the other three reads, not in the entries row.
    ac = activity[activity.find("private void academicCard("):activity.find("private void openEdu(")]
    checks += 1
    expected = ['"score","成绩"', '"room","空教室"', '"check","学业情况"', '"eval","学生评价"',
                '"cap","教务系统"', '"school","河工程官网"', '"globe","科信官网"']
    positions = [ac.find(t) for t in expected]
    if -1 in positions or positions != sorted(positions):
        failures.append("TimetableActivity: 教务 card tile order does not match row1=成绩/空教室/学业情况/学生评价 row2=教务系统/河工程/科信")

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
    require(client, '|| path.startsWith(ACADEMIC_COURSES_EXTRA)) return;',
            "EduClient",
            "both course queries must be on the read-only path allow-list explicitly")
    require(status, "private void toggle(EduAcademic.Node node", "AcademicStatusActivity",
            "courses must open on tap rather than all at once")
    require(status, "private boolean opensCourses(boolean hasChildren)", "AcademicStatusActivity",
            "tappable course rows must be decided by one helper, not a depth guess")
    require(status, "return !hasChildren;", "AcademicStatusActivity",
            "only a leaf holds courses, which is where the school puts its expander")
    require(status, "rowGroup(node, opensCourses(hasChildren))", "AcademicStatusActivity",
            "only a leaf group may be tappable")
    require(core_academic, "public final boolean planCourses", "EduAcademic",
            "each node must say which course endpoint serves it")
    require(client, "ACADEMIC_COURSES_EXTRA", "EduClient",
            "non-plan courses come from their own endpoint; the plan query answers them with []")
    require(status, "EduClient.academicCourses(node.id, node.planCourses)", "AcademicStatusActivity",
            "the course request must follow the node's endpoint choice")
    require(status, "courses.put(node.id, list)", "AcademicStatusActivity",
            "a fetched group must be cached so re-opening costs nothing")
    require(status, '"未通过", report.failed', "AcademicStatusActivity",
            "the headline must show the counts the school reports, not a subset")

    # 学生评价 (2.6.1): a native list and a native question sheet — no WebView anywhere in the
    # flow. The two things that must never come back: opening the school's page as a document, and
    # a screen that posts a score the reader did not pick.
    evaluation = PREVIEW / "EvaluationActivity.java"
    evaluation_src = evaluation.read_text(encoding="utf-8") if evaluation.exists() else ""
    form_activity = PREVIEW / "EvaluationFormActivity.java"
    form_src = form_activity.read_text(encoding="utf-8") if form_activity.exists() else ""
    core_evaluation = ROOT / "client/core/src/io/qzxy/lite/core/EduEvaluation.java"
    core_evaluation_src = core_evaluation.read_text(encoding="utf-8") if core_evaluation.exists() else ""
    sheet = ROOT / "client/core/src/io/qzxy/lite/core/EduEvaluationForm.java"
    sheet_src = sheet.read_text(encoding="utf-8") if sheet.exists() else ""

    # The list: from the shared session, paged, and filterable by state.
    require(evaluation_src, "EduClient.evaluations()", "EvaluationActivity",
            "the list must come from the shared signed-in client, not a scraped page")
    require(core_evaluation_src, "public static int pageCount(String json, int pageSize)", "EduEvaluation",
            "the grid pages; a list that reads only page one silently drops courses")
    # 学校的 jgh_id 是 256 个字符，按显示宽度（80）剪掉后会被判「参数异常」（200，6,923 B）。
    # id 必须走 ident()（整串保留），显示用的名字才走 text()。
    require(core_evaluation_src, 'teacherId = ident(raw.get("jgh_id"))', "EduEvaluation",
            "教职工 id 必须整串带回学校，不能按显示宽度截断")
    require(core_evaluation_src, 'classId = ident(raw.get("jxb_id"))', "EduEvaluation",
            "教学班 id 同样整串保留")
    require(client, "EduEvaluation.pageCount(reply, EVAL_PAGE_SIZE)", "EduClient",
            "the paging loop must follow the count the school reports")
    require(evaluation_src, 'stat(row, "未评", pending)', "EvaluationActivity",
            "the headline must show 未评 / 已评 / 暂存 as three separate counts")
    require(evaluation_src, 'stat(row, "已评", done)', "EvaluationActivity",
            "已评 belongs on the headline next to 未评")
    require(evaluation_src, 'stat(row, "暂存", saved)', "EvaluationActivity",
            "暂存 belongs on the headline too")
    require(evaluation_src, "private static final int PENDING = 1;", "EvaluationActivity",
            "the list needs a state filter")
    require(evaluation_src, "filter = which;", "EvaluationActivity",
            "tapping a filter chip must actually change what is shown")
    require(evaluation_src, "report.withState(EduEvaluation.UNEVALUATED)", "EvaluationActivity",
            "the 未评 filter must select on the parsed state, not on a string")
    require(evaluation_src, "MotionSpec.LIST_TOGGLE_DURATION", "EvaluationActivity",
            "the card fold must use the shared list toggle duration, like 学业情况 and 更新日志")
    require(evaluation_src, "MotionSpec.shouldAnimate(this)", "EvaluationActivity",
            "with system animations off the fold must land on its end state instead of animating")
    require(evaluation_src, "card.running.cancel()", "EvaluationActivity",
            "a second tap mid-fold must cancel the first animator, not race it")
    require(evaluation_src, "int target = opening ? expandHeight(card.body) : 0;", "EvaluationActivity",
            "the fold must animate the body's own height")
    require(evaluation_src, "EvaluationFormActivity.EXTRA_GRADE_ENTRY", "EvaluationActivity",
            "every id the form request needs must cross over: re-deriving them would be a second "
            "source of truth for the same values")
    require(evaluation_src, "openedForm", "EvaluationActivity",
            "returning from the sheet must reload the list, or a submitted row stays 未评")

    # The native sheet: parsed from the school's markup, answered on native controls.
    require(sheet_src, "public static Form parse(String html)", "EduEvaluationForm",
            "the school returns its form as an HTML fragment; reading it is what makes the screen native")
    require(sheet_src, "public static String body(Form form", "EduEvaluationForm",
            "the request body must be built from the parsed questions, not from fixed field names")
    require(sheet_src, "\"panel-pjdx\"", "EduEvaluationForm",
            "the 评价模板 div is how the school's own script groups questions")
    require(sheet_src, "\"tr-xspj\"", "EduEvaluationForm",
            "one tr-xspj is one 评价项; the ids live on its data-* attributes")
    require(sheet_src, "\"input-xspj\"", "EduEvaluationForm",
            "the visible label of an option is the text of its div.input-xspj wrapper")
    require(sheet_src, "modelList[", "EduEvaluationForm",
            "the school's request nests modelList[i].xspjList[j].childXspjList[k]")
    require(sheet_src, "public static final int UNSUPPORTED", "EduEvaluationForm",
            "a question whose control cannot be reproduced must be reported, not faked")
    require(form_src, "EduEvaluationForm.parse(html)", "EvaluationFormActivity",
            "the sheet screen must render what the parser read")
    require(form_src, "EduEvaluationForm.body(form, answers, notes, submit)", "EvaluationFormActivity",
            "the screen must post the parsed answers, not a hand-built request")
    require(form_src, "EduClient.evaluationForm(", "EvaluationFormActivity",
            "the questions must come from the school's own endpoint")
    require(form_src, "EduClient.evaluationWrite(", "EvaluationFormActivity",
            "暂存/提交 go through the client's allow-listed transport")
    require(form_src, "Theme.dialog(this)", "EvaluationFormActivity",
            "提交 is one-way, so it must be confirmed once more before it is sent")
    require(form_src, '"提交后学校不再接收修改', "EvaluationFormActivity",
            "the confirmation must say that a submission cannot be undone")
    require(form_src, "int missing = missing();", "EvaluationFormActivity",
            "提交 must refuse while a 必填项 is unanswered")
    require(form_src, 'if (missing > 0 || !anyAnswered())', "EvaluationFormActivity",
            "一个选项都没选 and 只选一部分 both count as unfinished")
    require(form_src, 'toast("请完成所有题目后再提交")', "EvaluationFormActivity",
            "没填完点提交 must show a toast, not only a line of status text")
    require(form_src, 'toastAndLeave(submit ? "已完成提交" : "已暂存")', "EvaluationFormActivity",
            "写成功 must toast 已完成提交 / 已暂存 and then return to the list")
    require(form_src, "if (!isFinishing() && !isDestroyed()) finish();", "EvaluationFormActivity",
            "自动返回 must not finish a screen that is already gone")
    require(form_src, "Toast.makeText(this, message, Toast.LENGTH_SHORT).show()", "EvaluationFormActivity",
            "the feedback must be a real Toast")
    require(form_src, "if (!item.question.supported()) {", "EvaluationFormActivity",
            "an unsupported question must block 提交 instead of sending a wrong parameter")
    require(form_src, "EduEvaluationForm.outcome(reply)", "EvaluationFormActivity",
            "success must be read from the school's own sentence, not assumed")
    require(form_src, "carryOver(parsed)", "EvaluationFormActivity",
            "a 暂存 sheet must show the reader's own previous answers")
    require(form_src, "readOnly", "EvaluationFormActivity",
            "a submitted evaluation is history: it must open without its buttons")
    require(activity, "EvaluationActivity.class", "TimetableActivity",
            "the 教务 card must launch the evaluation screen")

    # The regressions this round is fixing, kept as guards so they cannot come back silently.
    checks += 1
    if "Math.max(0, value)" in evaluation_src or "Math.max(0, pixels)" in evaluation_src:
        failures.append("学生评价: the fold's height writer must not floor the value — WRAP_CONTENT "
                        "is -2 and MATCH_PARENT -1, and clamping either to 0 is what made every "
                        "card snap shut right after its open animation")
    checks += 1
    if "android.webkit" in form_src or "new WebView(" in form_src or ".loadUrl(" in form_src:
        failures.append("学生评价: the sheet must be native — a WebView here is what the rewrite "
                        "replaced, and it is what left the panel at 0% Complete")
    checks += 1
    if "MENU_PAGE" in form_src or "ajaxPrefilter" in form_src:
        failures.append("学生评价: the menu-shell mounting is gone with the WebView; a native sheet "
                        "reads the fragment directly")
    checks += 1
    if "优秀" in form_src or "良好" in form_src or "及格" in form_src:
        failures.append("学生评价: option labels must come from the school's form; a hardcoded "
                        "等级 word would drift the moment the template changes")
    checks += 1
    if "answer.pick = option.value" in form_src and "setOnClickListener" not in form_src:
        failures.append("学生评价: nothing may be picked without the reader tapping it")
    require(client, 'path.startsWith("/xspjgl/xspj_cxXspjDisplay.html")', "EduClient",
            "the question fragment must be on the allow-list by name")
    require(client, 'path.startsWith("/xspjgl/xspj_tjXspj.html")', "EduClient",
            "提交 is a named route, not a folder")
    require(client, 'path.startsWith("/xspjgl/xspj_bcXspj.html")', "EduClient",
            "暂存 is a named route too")
    # 2026-11-20 用真实账号逐项实测（传热学 · 孟鑫，账号匿名化），三种失败各有确定原因：
    #   · 查询串里再拼一份 id → 200 + 「参数异常」页（6,923 B）
    #   · 少了 gnmkdm         → HTTP 911 + 392 B 的页面模型 JSON
    #   · 与官方完全一致的一发 → 200、46,234 B、1 个面板 11 道题
    # 所以这里只发一次，「多试几种载体」不再是修复手段。
    require(client, '"/xspjgl/xspj_cxXspjDisplay.html?gnmkdm=N401605"', "EduClient",
            "the sheet route must carry the module id the school's own menu passes; without it "
            "this deployment answers 911 with a page-model JSON instead of a form")
    require(client, 'form.put("jxb_id", classId)', "EduClient",
            "all seven ids go into the body, the way the school's own $(...).load(url, data) sends them")
    require(client, 'form.put("pjmbmcb_id", modelId)', "EduClient",
            "pjmbmcb_id is sent even when empty: jQuery serialises an undefined data field as an "
            "empty string, and both forms were measured to work")
    if "EVAL_DISPLAY" in client:
        failures.append("学生评价: the no-query variant is gone on purpose — it answered 911; "
                        "bringing it back as a first attempt only hides the real cause")
    checks += 1
    if "fetch(shapes[i][1], null)" in client or '{"body+query",' in client:
        failures.append("学生评价: the fallback matrix must stay out — a query-string copy of the "
                        "ids is exactly what the school answers with 「参数异常」")
    require(client, "private static boolean looksLikeSheet(String html)", "EduClient",
            "a page is judged by whether it holds a 评价表, not by a sentence it may contain")
    require(client, "private static String refusal(String html)", "EduClient",
            "a refusal arrives as a sentence inside a 200 page, so it must be read — otherwise "
            "every refusal is shown as「学校没有下发评价项」and blames the wrong side")
    require(client, "private static String sheetFailure(int status)", "EduClient",
            "911 and 500 mean different things on this deployment and are worth telling apart")
    require(client, "status == 911", "EduClient",
            "911 is「模块号没被认出」— measured, not guessed")
    require(client, '"form", "official", "bytes", html.length()', "EduClient",
            "the one request that ran must record its shape and reply size: without the size a log "
            "cannot tell a 2 KB error page from a 46 KB form")
    require(client, "readable(html.length())", "EduClient",
            "the size must ride along in the sentence the reader sees, so one screenshot is enough")

    require(client, "classId.isEmpty() || courseId.isEmpty()", "EduClient",
            "the three ids must be checked before the request: the school cannot say which one is "
            "missing, and the reader needs to know whether to reload or fall back to the web page")
    require(client, "static String evaluationWrite(String body, boolean submit)", "EduClient",
            "the write path must be one method taking the body the parser built")
    require(client, "EVAL_PAGE_SIZE", "EduClient",
            "the list must ask for a page size it can reason about")
    # 请求体的三层 id：学校 buildRequestMap() 对每一行都回填 pjzbxm_id / pfdjdmb_id / zsmbmcb_id，
    # 一级指标表自己还有一个 pjzbxm_id。缺任何一层，学校都记不住这道题。
    require(sheet_src, 'out.add(prefix + "pjzbxm_id", question.itemId)', "EduEvaluationForm",
            "each row must carry its 二级指标 id")
    require(sheet_src, 'out.add(prefix + "pfdjdmb_id", question.levelId)', "EduEvaluationForm",
            "each row must carry its 评分等级 id")
    require(sheet_src, 'out.add(prefix + "zsmbmcb_id", question.templateId)', "EduEvaluationForm",
            "each row must carry its 模板名称 id — the live template needs all three")
    require(sheet_src, 'out.add(tablePrefix + "pjzbxm_id", tableId)', "EduEvaluationForm",
            "the 一级指标 table has its own pjzbxm_id, different from the row's")
    require(sheet_src, 'out.add(key + "pjzt", allRequired ? "1" : "0")', "EduEvaluationForm",
            "buildRequestMap() writes pjzt=1 only when every required item is answered")
    require(sheet_src, 'question.templateId = attr(tag, "data-zsmbmcb_id")', "EduEvaluationForm",
            "the template id comes from the row itself, never from a guess")
    # 真实片段不闭合：线上这张表有 63 个 <div> 和 73 个 </div>。按嵌套切会只读到 2 道题，
    # 而按「下一个同类开始标签」切才读得到 11 道。
    require(sheet_src, "private static List<int[]> flat(", "EduEvaluationForm",
            "the fragment is parsed by sibling boundaries, not by counting nesting")
    require(sheet_src, 'flat(text, "div", "panel-pjdx")', "EduEvaluationForm",
            "panels are split at the next panel, because the markup does not balance its divs")
    checks += 1
    if 'blocks(text, "div", "panel-pjdx")' in sheet_src:
        failures.append("学生评价: depth-counted panels came back — on the live 传热学 sheet they "
                        "hold 2 of 11 questions, and the reader is told the school sent nothing")
    # 学校对写操作只回一句话（实测 "暂存成功！"，七字节 JSON 字符串），判断必须严格。
    require(sheet_src, 'text.contains("不成功") || text.contains("失败")', "EduEvaluationForm",
            "negations are tested before success: 提交不成功 contains 成功, and reporting it as "
            "finished is the one mistake this method must never make")
    neg = sheet_src.find('text.contains("不成功")')
    ok_at = sheet_src.find('text.contains("整体提交成功")')
    if neg < 0 or ok_at < 0 or ok_at < neg:
        failures.append("学生评价: success must be checked after the negations")
    require(sheet_src, "private static String words(String reply)", "EduEvaluationForm",
            "the reply is a JSON string literal; its quotes have to come off before comparing")
    # 写完之后以学校记录为准，而不是以那句话为准。
    require(form_src, "EduClient.evaluationState(classId, teacherId)", "EvaluationFormActivity",
            "after a write the screen must re-read the school's own state")
    require(form_src, 'final String classId = value(EXTRA_CLASS), teacherId = value(EXTRA_TEACHER_ID)',
            "EvaluationFormActivity",
            "状态回读必须拿列表行自己的 id：表单 HTML 的 data-jgh_id 与列表 jgh_id 是两个不同的值")
    require(form_src, "question.shown()", "EvaluationFormActivity",
            "已提交的行答案以纯文本展示，必须回显而不是报成不支持题型")
    require(form_src, "answerText.isEmpty()", "EvaluationFormActivity",
            "只读回显为空时也要明说，不留空白题")
    require(form_src, "state == EduEvaluation.SUBMITTED", "EvaluationFormActivity",
            "回读到已提交时仍然确认成功")
    require(form_src, "state == EduEvaluation.SAVED && outcome == EduEvaluationForm.OK",
            "EvaluationFormActivity",
            "回读只否决「回话说成功却仍是暂存」这一种；列表异步导致的 UNKNOWN 不能吞掉成功")
    forbid(form_src, "else if (outcome == EduEvaluationForm.OK) outcome = EduEvaluationForm.UNCLEAR;",
            "EvaluationFormActivity",
            "这条降级曾让真机上提交成功却不飘字、不返回")
    require(form_src, '"write.unverified"', "EvaluationFormActivity",
            "a success sentence without a state change is its own diagnosis, not a success")
    require(client, "static int evaluationState(String classId, String teacherId)", "EduClient",
            "the state check is one method, so both paths read it the same way")

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

    # --- 数据与隐私 / 使用公告：文案必须仍然是代码里发生的事 -------------------------------
    legal_path = PREVIEW / "LegalText.java"
    legal = legal_path.read_text(encoding="utf-8") if legal_path.exists() else ""
    privacy_src_path = PREVIEW / "PrivacyActivity.java"
    privacy_src = privacy_src_path.read_text(encoding="utf-8") if privacy_src_path.exists() else ""
    notice_page_path = PREVIEW / "NoticeActivity.java"
    notice_page = notice_page_path.read_text(encoding="utf-8") if notice_page_path.exists() else ""
    notice_dialog_path = PREVIEW / "NoticeDialog.java"
    notice_dialog = notice_dialog_path.read_text(encoding="utf-8") if notice_dialog_path.exists() else ""
    manifest = (ROOT / "client/android/AndroidManifest.xml").read_text(encoding="utf-8")

    require(legal, "static final String[][] PRIVACY", "LegalText", "隐私文案只有一个来源")
    require(legal, "static final String[][] NOTICE", "LegalText", "公告文案与隐私文案同源")
    # 发布信息必须是真值：邮箱用「意见反馈」里那个公开地址，日期为发布当日，不能留占位符。
    require(legal, 'CONTACT_EMAIL = "shynekoo@outlook.com"', "LegalText",
            "隐私联系邮箱必须是真实可用的公开邮箱")
    checks += 1
    if "【" in legal:
        failures.append("LegalText: 发版文案里不允许残留【…】占位符")
    require(legal, "EFFECTIVE_DATE = \"20", "LegalText", "生效日期必须已填写")
    # 与 EduVault 的事实对齐：主动保存时密码是加密落盘的，不能写成「不写入本地加密存储」。
    require(legal, "如果您在应用中主动保存教务账号", "LegalText",
            "教务密码的保存方式必须按 EduVault 的真实行为描述")
    require(legal, "AES-256-GCM", "LegalText", "本地凭据的加密方式与各 Vault 一致")
    # 应用没有充值/收银能力（AlipayLogin 只有 authV2，用水是交给支付宝的 URI）。
    require(legal, "本应用不提供充值、收银或代下单功能", "LegalText",
            "不得声称应用内可以充值或收银")
    # 权限表：manifest 里的每个权限都必须在文案里被披露，避免加了权限没改文案。
    # Wi-Fi 状态权限代码里从来没用过（Alipay SDK 只引用 ConnectivityManager），已从清单移除；
    # 如果有人又把它加回来，这条会立刻失败，提醒同步文案。
    disclosures = {
        "android.permission.INTERNET": "网络访问",
        "android.permission.ACCESS_NETWORK_STATE": "网络状态",
        "android.permission.CAMERA": "相机",
        "android.permission.REQUEST_INSTALL_PACKAGES": "安装应用请求权限",
    }
    checks += 1
    declared = set(__import__("re").findall(
        r'uses-permission android:name="([^"]+)"', manifest))
    if declared != set(disclosures):
        failures.append("manifest 权限表变了（%s），隐私文案与 disclosures 需要同步"
                        % ", ".join(sorted(declared)))
    for permission, keyword in disclosures.items():
        require(legal, keyword, "LegalText",
                "%s 必须在隐私文案里被披露" % permission.split(".")[-1])
    # 同一邮箱，两处写法：隐私页与「意见反馈」不能各写各的。
    checks += 1
    feedback = (PREVIEW / "FeedbackActivity.java").read_text(encoding="utf-8")
    if '"shynekoo@outlook.com"' not in feedback:
        failures.append("LegalText 的隐私邮箱必须与 FeedbackActivity 的公开反馈邮箱一致")

    # 两个独立入口 + 既有交互：入口卡片、公告页、弹窗读同一份文字、公告版本号已提升。
    require(privacy_src, "LegalText.PRIVACY", "PrivacyActivity", "隐私页读共享文案")
    require(privacy_src, "NoticeActivity.class", "PrivacyActivity", "隐私页仍能进入使用公告")
    require(privacy_src, "clearSessions", "PrivacyActivity", "清除登录状态的既有动作保留")
    require(privacy_src, "clearAll", "PrivacyActivity", "清除全部本机数据的既有动作保留")
    require(privacy_src, '"edu-account-state"', "PrivacyActivity", "全部清除仍覆盖教务账号状态")
    require(notice_page, "LegalText.NOTICE", "NoticeActivity", "公告页读共享文案")
    require(notice_dialog, "LegalText.NOTICE", "NoticeDialog", "首次启动弹窗与公告页同源")
    require(notice_dialog, "VERSION = 3", "NoticeDialog", "公告文案改版后应重新提醒一次")
    require(manifest, 'android:name=".NoticeActivity"', "AndroidManifest",
            "使用公告页需要注册，否则入口点了会崩")
    require(manifest, 'android:name=".PrivacyActivity"', "AndroidManifest",
            "数据与隐私页仍在清单里")
    # --- 匿名使用统计：开关在「更多」，删除在「数据与隐私」，触发在 App 的前台切换 ---------
    more_src_path = PREVIEW / "MoreActivity.java"
    more_src = more_src_path.read_text(encoding="utf-8")
    analytics_prefs_path = PREVIEW / "AnalyticsPreferences.java"
    analytics_prefs = analytics_prefs_path.read_text(encoding="utf-8") if analytics_prefs_path.exists() else ""
    app_src = (PREVIEW / "App.java").read_text(encoding="utf-8")
    require(more_src, "private void analyticsSetting(", "MoreActivity",
            "匿名使用统计的能力保留，隐私页与日后恢复都还指得到它")
    forbid(more_src, "analyticsSetting(card", "MoreActivity",
           "读者要求隐藏统计入口：「更多」页不得摆出这个开关")
    require(more_src, '"匿名使用统计"', "MoreActivity", "开关的标题是读者看得懂的那四个字")
    require(more_src, "AnalyticsReporter.setConsent(this, true)", "MoreActivity",
            "打开统计要经过统一的同意入口")
    require(more_src, "AnalyticsReporter.setConsent(this, false)", "MoreActivity",
            "关闭统计要停上报并请求删除")
    require(analytics_prefs, "getBoolean(KEY_CONSENT, false)", "AnalyticsPreferences",
            "统计默认关闭，升级安装不得自动开启")
    require(app_src, "AnalyticsReporter.onForeground", "App",
            "统计只在进程进入前台时检查一次")
    require(privacy_src, '"匿名使用统计"', "PrivacyActivity", "隐私页要交代统计这件事")
    require(privacy_src, "删除已上传的匿名记录", "PrivacyActivity",
            "隐私页要能单独删除已上传的匿名记录")
    require(legal, '"5. 匿名使用统计"', "LegalText", "隐私说明必须有一节讲统计")
    require(legal, "90 天", "LegalText", "统计记录的保留期必须写明")
    # 离线可读：这两页不许引入 WebView 或任何请求。注释里提到这些词不算（说明文里会解释
    # 「不用 WebView」），只看真正的代码行。
    def code_lines(text):
        return "\n".join(line for line in text.splitlines()
                         if not line.strip().startswith(("*", "//")))

    for name, source in (("PrivacyActivity", privacy_src), ("NoticeActivity", notice_page),
                         ("LegalText", legal), ("LegalView", (PREVIEW / "LegalView.java").read_text(encoding="utf-8"))):
        checks += 1
        body = code_lines(source)
        if "WebView" in body or "loadUrl" in body or "openConnection" in body:
            failures.append("%s: 法律文本页面必须离线可读，不得加载网页或发起请求" % name)

    # --- 更新日志：数据只能来自已发布的版本，页面必须离线、只读 ---------------------------
    changelog_path = PREVIEW / "ChangelogActivity.java"
    changelog = changelog_path.read_text(encoding="utf-8") if changelog_path.exists() else ""
    data_path = PREVIEW / "AppChangelog.java"
    data = data_path.read_text(encoding="utf-8") if data_path.exists() else ""
    more_src = (PREVIEW / "MoreActivity.java").read_text(encoding="utf-8")

    require(data, "static final Entry[] ENTRIES", "AppChangelog", "更新日志必须只有一个数据源")
    require(data, "static Entry[] released()", "AppChangelog", "页面要按 released 过滤，未发布的版本不上屏")
    require(data, "static Entry current(String versionName)", "AppChangelog",
            "「当前版本」只能由安装版本号匹配得出")

    import datetime
    import json
    import re

    entries = re.findall(r'new Entry\("([^"]+)",\s*(\d+),\s*"(\d{4}-\d{2}-\d{2})"', data)
    checks += 1
    if len(entries) < 3:
        failures.append("AppChangelog: 至少要收录 3 个已发布版本（找到 %d）" % len(entries))
    versions = [row[0] for row in entries]
    codes = [int(row[1]) for row in entries]
    checks += 1
    if len(set(versions)) != len(versions):
        failures.append("AppChangelog: 版本号有重复 %s" % versions)
    checks += 1
    if codes != sorted(codes, reverse=True):
        failures.append("AppChangelog: 版本必须按版本代码从新到旧排列 %s" % codes)
    checks += 1
    for version, _code, day in entries:
        try:
            datetime.date.fromisoformat(day)
        except ValueError:
            failures.append("AppChangelog: %s 的发布日期不是合法日期（%s）" % (version, day))

    # 与线上正式清单对齐：最新一条必须是 update.json 指向的那一版。发版忘了加日志、
    # 或者把还没发布的版本写进来，这里都会红。
    online = json.loads((ROOT / "delivery/update/update.json").read_text(encoding="utf-8"))
    checks += 1
    if entries and (versions[0] != online["version"] or codes[0] != int(online["versionCode"])):
        failures.append("AppChangelog: 最新条目（%s / %s）与线上 update.json（%s / %s）不一致"
                        % (versions[0] if versions else "-", codes[0] if codes else "-",
                           online["version"], online["versionCode"]))
    checks += 1
    leaked = [v for v, c in zip(versions, codes) if c > int(online["versionCode"])]
    if leaked:
        failures.append("AppChangelog: 这些版本还没发布，不能写进更新日志：%s" % ", ".join(leaked))

    # 每条都要有发布记录可查，免得日志里出现一段没人发过的版本。
    changelog_md = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    checks += 1
    undocumented = [v for v in versions
                    if ("## [%s]" % v) not in changelog_md
                    and not (ROOT / ("delivery/release-%s.md" % v)).exists()]
    if undocumented:
        failures.append("AppChangelog: 这些版本找不到发布记录：%s" % ", ".join(undocumented))

    require(changelog, "extends Page", "ChangelogActivity", "返回沿用 Page 的机制")
    require(changelog, "AppChangelog.released()", "ChangelogActivity", "页面只展示正式发布的版本")
    require(changelog, "AppChangelog.current(", "ChangelogActivity", "「当前版本」按安装版本号匹配")
    require(changelog, "MotionSpec.LIST_TOGGLE_DURATION", "ChangelogActivity",
            "展开/收起时长取自 MotionSpec")
    require(changelog, "MotionSpec.shouldAnimate(this)", "ChangelogActivity",
            "系统关动画时必须直接到终态")
    require(changelog, "card.running.cancel()", "ChangelogActivity",
            "连续点击要先停掉上一条动画，不能两条抢同一个高度")
    require(changelog, "generation != card.generation", "ChangelogActivity",
            "过期的动画回调不得改状态")
    require(more_src, "ChangelogActivity.class", "MoreActivity", "更多页要有更新日志入口")
    # 公告只留一个入口：它挂在《数据与隐私》页末。更多页再摆一条，读者就得猜哪个才是正的。
    checks += 1
    if "NoticeActivity" in code_lines(more_src):
        failures.append("MoreActivity: 使用公告不再单独成条，入口只在 PrivacyActivity 页末")
    require(manifest, 'android:name=".ChangelogActivity" android:exported="false"', "AndroidManifest",
            "更新日志页必须注册且不导出")
    checks += 1
    if "WebView" in code_lines(changelog) or "openConnection" in code_lines(changelog) \
            or "openConnection" in code_lines(data):
        failures.append("更新日志必须离线可读，不得加载网页或发起请求")

    # --- 三渠道更新：正式清单 + 预览清单，开关只在「更多」页出现一处 ------------------------
    update_src = (PREVIEW / "UpdateCheck.java").read_text(encoding="utf-8")
    core_dir = ROOT / "client/core/src/io/qzxy/lite/core"
    sources_src = (core_dir / "UpdateSources.java").read_text(encoding="utf-8")
    manifest_src = (core_dir / "UpdateManifest.java").read_text(encoding="utf-8")
    channel_src = (core_dir / "UpdateChannel.java").read_text(encoding="utf-8")
    version_src = (core_dir / "ReleaseVersion.java").read_text(encoding="utf-8")

    require(update_src, "beta.json", "UpdateCheck", "预览清单是独立的第二个文件")
    require(update_src, "UpdateChannel.pick(", "UpdateCheck", "两个清单之间由 core 规则选，不在页面里比大小")
    require(update_src, "KEY_BETA", "UpdateCheck", "开关状态要落盘")
    require(update_src, "getBoolean(KEY_BETA, false)", "UpdateCheck", "默认关闭：没保存过就是关")
    checks += 1
    # 默认值不得随「当前装的是不是预览包」变化：预览开关只能由读者自己打开。
    if "isBeta(AppUi.version" in code_lines(update_src):
        failures.append("UpdateCheck: 开关默认值不得按安装包类型猜，预览渠道必须默认关")
    require(update_src, "manifest.versionCode == skipped(activity)", "UpdateCheck",
            "「稍后再说」仍按版本代码记住，同一版本不重复打扰")
    require(update_src, "generation()", "UpdateCheck", "切换渠道要作废在飞的检查")
    require(update_src, "check.stale", "UpdateCheck", "被作废的结果要留日志")
    require(channel_src, "preview.beta", "UpdateChannel", "预览清单里若不是预览版名，不能当预览推荐")
    require(channel_src, "preview.versionCode > best.versionCode", "UpdateChannel",
            "两个清单同时候选时按版本代码取大")
    require(version_src, "beta\\\\.", "ReleaseVersion", "预览版命名 X.Y.Z-beta.N")
    require(manifest_src, "ReleaseVersion.isWellFormed", "UpdateManifest", "版名校验只有一处来源")
    checks += 1
    if "version.compareTo" in code_lines(manifest_src) or "compareTo" in code_lines(manifest_src):
        failures.append("UpdateManifest: 版本新旧不得用字符串比较，只认 versionCode")

    # 预览渠道不得降低任何一道校验：字节数、SHA-256、包名、版本代码、签名证书、逐跳 HTTPS。
    require(update_src, "progress.done != manifest.bytes", "UpdateCheck", "字节数校验对两个渠道一致")
    require(update_src, "hex.toString().equals(manifest.sha256)", "UpdateCheck", "SHA-256 校验对两个渠道一致")
    require(update_src, "安装包版本与说明不符", "UpdateCheck", "下载包的 versionCode 必须等于清单")
    require(update_src, "安装包签名与本应用不符", "UpdateCheck", "签名证书必须与本应用一致")
    require(update_src, "setInstanceFollowRedirects(false)", "UpdateCheck", "重定向手动逐跳，防降级")
    require(update_src, "UpdateUrls.next(", "UpdateCheck", "每一跳都过 HTTPS 与跳数策略")

    require(more_src, "接收预览版更新", "MoreActivity", "开关在「更多」页")
    require(more_src, "提前体验新功能，可能存在不稳定情况", "MoreActivity", "副标题按约定文案")
    require(more_src, "UpdateCheck.setBetaEnabled(this, true)", "MoreActivity", "保存开关")
    require(more_src, "预览版供开发者测试使用，可能不稳定，不建议开启。", "MoreActivity",
            "打开前必须先说明预览版是给开发者测试的、可能不稳定")
    require(more_src, "setNegativeButton(\"保持关闭\"", "MoreActivity", "取消要能明确回到关闭")
    require(more_src, "setOnCancelListener(dialog -> revert(", "MoreActivity",
            "点弹窗外或返回键取消，开关也要拨回关闭")
    require(more_src, "previewChannel(card, true)", "MoreActivity", "开关挨着更新日志，不另开块")
    # 更新渠道是独立的一项，与「接收预览版更新」并列：一个选去哪读，一个选读哪个通道。
    require(more_src, "sourceSetting(card, true)", "MoreActivity", "更新渠道挨着更新日志")
    require(more_src, 'text("更新渠道"', "MoreActivity", "更新渠道的可视标题")
    require(more_src, "（默认）", "MoreActivity", "第一项要点明是默认渠道")
    require(more_src, "final String[] names = UpdateSources.HOSTS", "MoreActivity", "选项来自 core 的渠道表")
    require(more_src, "UpdateSources.HOSTS[picked[0]]", "MoreActivity", "显示的渠道名也取自同一张表")
    require(more_src, "UpdateCheck.setSource(this, UpdateSources.keyOf(picked[0]))", "MoreActivity",
            "渠道选择要落盘")
    require(more_src, "setSingleChoiceItems(names, picked[0]", "MoreActivity", "渠道是三选一")
    # 渠道表本身也要守住：三条地址都写死在 core，测试与应用读同一份，新增渠道不能只改页面。
    require(sources_src, '"国内", "GitHub", "Gitee"', "UpdateSources", "三个渠道按这个顺序出现，首个显示是国内")
    require(sources_src, '"workbuddy", "github", "gitee"', "UpdateSources", "每个渠道都有落盘用的键")
    require(sources_src, "gitee.com", "UpdateSources", "Gitee 的清单地址")
    require(sources_src, "/download/release/update.json", "UpdateSources",
            "Gitee 没有 latest 路由，正式清单必须挂在固定 tag 上")
    require(update_src, 'getString(KEY_SOURCE, "workbuddy")', "UpdateCheck", "渠道默认是 WorkBuddy")
    require(update_src, "UpdateSources.normalize(", "UpdateCheck", "未知渠道一律读成默认，不落到没验证过的主机")
    require(update_src, "UpdateSources.manifests(source)", "UpdateCheck", "三个渠道都从同一份清单表取地址")
    checks += 1
    # 确认框只在「打开」这一侧：关掉是安全方向，不必再问一次。
    if more_src.count("UpdateCheck.setBetaEnabled(this, true)") != 1:
        failures.append("MoreActivity: 开启只能有一条路径（确认框的确认按钮），不得绕过提示")
    if more_src.count('text("接收预览版更新"') != 1:
        failures.append("MoreActivity: 开关的可视标题只能有一条，读者会以为是两个设置")

    # 版本号：清单里的名字必须符合规范，代码必须高于线上正式清单（否则覆盖安装就会失败）。
    import re as _re
    name_match = _re.search(r'android:versionName="([^"]+)"', manifest)
    checks += 1
    manifest_name = name_match.group(1) if name_match else ""
    if not _re.fullmatch(r"[0-9]{1,2}(\.[0-9]{1,2}){2}(-beta\.[1-9][0-9]{0,2})?", manifest_name):
        failures.append("AndroidManifest: versionName（%s）必须是 X.Y.Z 或 X.Y.Z-beta.N" % manifest_name)
    code_match = _re.search(r'android:versionCode="(\d+)"', manifest)
    checks += 1
    if not code_match or int(code_match.group(1)) <= int(online["versionCode"]):
        failures.append("AndroidManifest: versionCode 必须高于线上正式版（%s），否则覆盖安装会被系统拒绝"
                        % online["versionCode"])
    checks += 1
    beta_path = ROOT / "delivery/update/beta.json"
    if beta_path.exists():
        beta_manifest = json.loads(beta_path.read_text(encoding="utf-8"))
        if not _re.fullmatch(r"[0-9]{1,2}(\.[0-9]{1,2}){2}-beta\.[1-9][0-9]{0,2}",
                             str(beta_manifest.get("version", ""))):
            failures.append("beta.json: version 必须是 X.Y.Z-beta.N")
        if int(beta_manifest.get("versionCode", 0)) <= int(online["versionCode"]):
            failures.append("beta.json: 预览版 versionCode 必须高于线上正式版")
        required = {"version", "versionCode", "notes", "url", "bytes", "sha256"}
        if not required.issubset(beta_manifest):
            failures.append("beta.json: 缺少字段 %s" % ", ".join(sorted(required - set(beta_manifest))))

    # --- diagnostics page (2.5.8): filters, pagination, export, confirm-before-clear ------
    # The page exists for one job: turn "课表加载失败" into "网络超时，停在请求阶段". A refactor
    # that drops the filters, the paging window or the masked export breaks that job silently.
    diag_page = PREVIEW / "DiagActivity.java"
    diag_src = diag_page.read_text(encoding="utf-8") if diag_page.exists() else ""
    require(diag_src, "Diag.records(0)", "DiagActivity", "页面读结构化记录，不读原始文件")
    require(diag_src, "levelFilter", "DiagActivity", "按等级筛选")
    require(diag_src, "moduleFilter", "DiagActivity", "按模块筛选")
    require(diag_src, "PAGE = 40", "DiagActivity", "分页窗口：长日志不能一次建满 View")
    require(diag_src, "ACTION_CREATE_DOCUMENT", "DiagActivity", "导出走系统新建文件，不申请存储权限")
    require(diag_src, "StandardCharsets.UTF_8", "DiagActivity", "导出为 UTF-8 文本")
    require(diag_src, "Diag.clear()", "DiagActivity", "清空按钮真的清文件")
    require(diag_src, "setNegativeButton", "DiagActivity", "清空必须先确认")
    require(diag_src, "Switch", "DiagActivity", "诊断记录开关")
    require(diag_src, "DiagCatalog.summary", "DiagActivity", "列表标题走统一文案，不显示原始事件代码")
    checks += 1
    if "Diag.dump(" in diag_src:
        failures.append("DiagActivity: 旧的自由文本 dump() 不能再用，页面读 DiagEvent")
    for path in PREVIEW.glob("*.java"):
        if "RecyclerView" in code_lines(path.read_text(encoding="utf-8")):
            failures.append("%s: 不引入 AndroidX RecyclerView（项目无 AndroidX）" % path.name)
    require(diag_src, "DiagReportActivity.class", "DiagActivity", "故障报告入口")

    files_src = (PREVIEW / "DiagFiles.java").read_text(encoding="utf-8")
    require(files_src, "256 * 1024L", "DiagFiles", "单文件 256 KB 上限")
    require(files_src, "ARCHIVES = 3", "DiagFiles", "三个历史文件，总量约 1 MB")
    require(files_src, "7L * 24 * 60 * 60 * 1000", "DiagFiles", "历史保留 7 天")
    require(files_src, "renameTo", "DiagFiles", "轮转整文件改名，不截断用户正在看的记录")

    diag_core_src = (PREVIEW / "Diag.java").read_text(encoding="utf-8")
    require(diag_core_src, "ArrayBlockingQueue", "Diag", "写盘走队列，不阻塞主线程")
    require(diag_core_src, "setDefaultUncaughtExceptionHandler", "Diag", "崩溃兜底")
    require(diag_core_src, "previous.uncaughtException", "Diag", "不吞掉异常，交回系统处理器")
    require(diag_core_src, "sink.append(block.toString())", "Diag", "崩溃记录同步、有界落盘")
    checks += 1
    if "setupLogCallback" in diag_core_src:
        failures.append("Diag: SDK 原文日志不再接入（里面逐字带着授权码）")

    report_src = (PREVIEW / "DiagReportActivity.java").read_text(encoding="utf-8") \
        if (PREVIEW / "DiagReportActivity.java").exists() else ""
    require(report_src, "MAX_RECORDS", "DiagReportActivity", "报告只带有限条数")
    require(report_src, "故障模块", "DiagReportActivity", "报告写明所选模块")
    require(report_src, "问题描述", "DiagReportActivity", "报告包含用户描述")
    require(manifest, 'android:name=".DiagReportActivity" android:exported="false"', "AndroidManifest",
            "故障报告页必须注册且不导出")
    require(more_src, "BuildFlags.DIAG_BASIC || BuildFlags.DIAGNOSTICS", "MoreActivity",
            "诊断入口在正式版与内部版都出现")
    require(more_src, "DiagActivity.class", "MoreActivity", "更多页要有诊断日志入口")

    # --- 开源许可：MPL-2.0 全文随包附带，入口与弹窗不得再写 MIT -----------------------
    licence_root = (ROOT / "LICENSE").read_text(encoding="utf-8")
    licence_raw = (ROOT / "client/android/res/raw/mpl_license.txt").read_text(encoding="utf-8")
    require(licence_root, "Mozilla Public License Version 2.0", "LICENSE",
            "根目录是 MPL 2.0 官方全文")
    require(licence_raw, "Exhibit A - Source Code Form License Notice", "res/raw/mpl_license.txt",
            "App 内展示的协议全文必须完整，含 Exhibit A")
    checks += 1
    if licence_root.strip() != licence_raw.strip():
        failures.append("LICENSE 与 res/raw/mpl_license.txt 必须是同一份 MPL 全文，否则 App 内与仓库不一致")
    require(more_src, '"开源许可"', "MoreActivity", "入口叫「开源许可」，副标题写 MPL-2.0")
    require(more_src, "R.raw.mpl_license", "MoreActivity", "协议全文随 APK 附带，离线可读")
    require(more_src, "openRawResource", "MoreActivity", "正文从本地资源读，不走网络")
    forbid(more_src, "MIT License", "MoreActivity", "项目已不是 MIT，弹窗与入口不得再写 MIT")
    require(more_src, "client/THIRD_PARTY.md", "MoreActivity",
            "第三方组件提示继续指向 THIRD_PARTY.md，不并入 MPL")
    require(more_src, "sourceNotice() + \"\\n\\n\" + licenceText()", "MoreActivity",
            "协议弹窗先给源码地址与本机版本号，再给协议正文（MPL 3.2 的源码告知义务）")
    require(more_src, "AppUi.version(this)", "MoreActivity", "弹窗写的是本机安装的版本号，不是硬编码")
    require(more_src, "\"源码地址：\" + REPO", "MoreActivity", "源码地址与「本项目 GitHub 仓库」同一常量")

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
