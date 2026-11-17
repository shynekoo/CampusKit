package io.qzxy.lite.preview;

import android.webkit.CookieManager;
import io.qzxy.lite.core.EduLoginProtocol;
import io.qzxy.lite.core.EduSite;
import io.qzxy.lite.core.SmsLoginProtocol;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Read-only client for the school's academic system.
 *
 * <p>Signing in happens here when the user has saved an account: the same csrf token, RSA-encrypted
 * password and form post the school's own page uses, so no browser is involved. A session the user
 * starts in the school's own page — the embedded login screen or the portal — is adopted instead of
 * signing in a second time ({@link #capture}). One session then serves every academic screen and the
 * embedded page alike. Every request is pinned to
 * the school's HTTPS host and to a short list of read-only paths — the same three pages the school's
 * own empty-classroom screen uses — so a feature here cannot quietly start touching course selection
 * or account data.</p>
 *
 * <p>Both the current term and the week calendar come from the school on every search; no academic
 * year is compiled in, so September does not need a new release.</p>
 */
final class EduClient {
    static final String ORIGIN = "https://jwglxxfwpt.hebeu.edu.cn";
    private static final String HOST = "jwglxxfwpt.hebeu.edu.cn";
    private static final String ROOMS_PAGE = "/cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155";
    private static final String ROOMS_QUERY = "/cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&doType=query";
    private static final String ROOMS_META = "/cdjy/cdjy_cxXqjc.html?gnmkdm=N2155";
    private static final String ROOMS_TERM = "/cdjy/cdjy_cxQtlb.html?gnmkdm=N2155";
    private static final String GRADES_PAGE = "/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005&layout=default";
    /**
     * Grade modules to try when the menu has no usable link. 正方 ships more than one — 个人成绩 under
     * /cjcx/ and 考试课程成绩 under /kwgl/ — and a deployment may enable either, both or neither, so
     * the compiled-in route is never trusted on its own: this school answers 404 to it with its own
     * "page not found" screen.
     */
    private static final String[] GRADE_PATHS = {
        "/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005",
        "/cjcx/cjcx_cxXsgrcj.html",
        GRADES_PAGE,
        "/cjcx/cjcx_cxXsKccj_list.html?gnmkdm=N305005",
        "/kwgl/kscjcx_kscjcx.html?gnmkdm=N358105",
        "/kwgl/kscjcx_kscjcx.html",
    };
    /** The student timetable page, used as Referer for its own query endpoints. */
    private static final String TIMETABLE_PAGE = "/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default";
    /** 正方 names this query differently across deployments; try each with the same term fields. */
    private static final String[] TIMETABLE_QUERIES = {
        "/kbcx/xskbcx_cxXsKbcx.html?gnmkdm=N2151",
        "/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151",
        "/kbcx/xskbcx_cxXskbcx.html?gnmkdm=N2151",
    };
    private static final String LOGIN = "/xtgl/login_slogin.html";
    private static final String PUBLIC_KEY = "/xtgl/login_getPublicKey.html";
    private static final String MENU = "/xtgl/index_initMenu.html";
    private static final String LOGOUT = "/xtgl/logout.html";
    private static final int TIMEOUT = 15000, MAX_BYTES = 2_000_000, MAX_PAGES = 30;
    /**
     * How long one session is kept after the last 教务 screen closes.
     *
     * <p>Everything academic — grades, empty classrooms, the timetable import, the portal — shares
     * this one session, so a walk between those screens costs no sign-in and the school's log sees
     * one login instead of four. The window is long enough to cover a normal app visit; 退出登录
     * still ends the session at once, and the server drops an idle session by itself anyway.</p>
     */
    static final long IDLE_MS = 30 * 60 * 1000L;

    static final class NotSignedIn extends IOException {
        private static final long serialVersionUID = 1L;
        NotSignedIn() { super("需要先登录教务系统"); }
    }

    /** A status the school would not serve: kept with its code so a report can be acted on. */
    static final class HttpFailure extends IOException {
        private static final long serialVersionUID = 1L;
        final int status;
        HttpFailure(int status, String url, String snippet) {
            super("教务系统返回 HTTP " + status + "（" + shortPath(url) + "）" + (snippet.isEmpty() ? "" : "：" + snippet));
            this.status = status;
        }
    }

    /** The school refused the saved password; retrying would only walk towards a lockout. */
    static final class BadPassword extends IOException {
        private static final long serialVersionUID = 1L;
        BadPassword() { super("教务账号或密码不对，请修改后再试。"); }
        BadPassword(String message) { super(message); }
    }

    /** The school wants a human-typed code, which only its own page can show. */
    static final class CaptchaNeeded extends IOException {
        private static final long serialVersionUID = 1L;
        CaptchaNeeded() { super("学校要求输入验证码，请改用网页登录一次。"); }
    }

    /** Everything the school page says about the current term, plus its own filter options. */
    static final class Page {
        String year = "", term = "", campus = "", type = "";
        final List<String[]> campuses = new ArrayList<>();
        final List<String[]> types = new ArrayList<>();
        /** The school-wide 学年/学期 selects, which the grade screen offers as a term picker. */
        final List<String[]> years = new ArrayList<>();
        final List<String[]> terms = new ArrayList<>();
        boolean signedIn;
    }

    /** Campus-scoped data: buildings, periods and the real week calendar. */
    static final class Campus {
        String monday = "";
        final List<Integer> weeks = new ArrayList<>();
        final List<String[]> buildings = new ArrayList<>();
        final List<Integer> periods = new ArrayList<>();
        String periodTimes = "";
    }

    static final class Room {
        final String name, building, type, seats;
        Room(String name, String building, String type, String seats) {
            this.name = name; this.building = building; this.type = type; this.seats = seats;
        }
    }

    static final class Course {
        final String name, score, credit, gpa;
        Course(String name, String score, String credit, String gpa) {
            this.name = name; this.score = score; this.credit = credit; this.gpa = gpa;
        }
    }

    static final class Semester {
        String year = "", term = "";
        final List<String[]> years = new ArrayList<>();
        final List<String[]> terms = new ArrayList<>();
    }

    /** Session obtained by the built-in login; empty while signed out. */
    private static volatile String session = "";
    /**
     * Whose session {@link #session} is: the student number it was signed in with, or empty when the
     * session came from the school's own page and cannot be attributed. Used only to decide whether
     * a newly handed-over account is a different person or the same one already signed in.
     */
    private static volatile String sessionUser = "";
    /** Cookies held by the embedded browser; only used when no account is saved. */
    private static volatile String webCookies = "";
    /** The saved account, kept in this process only — the vault owns the durable copy. */
    private static volatile String user = "", password = "";
    /** Set once the school rejects the saved password, so it is never retried into a lockout. */
    private static volatile boolean rejected;
    /** True after leaving a query screen: a late response must not sign in again behind the user. */
    private static volatile boolean closed;
    /** A sign-out queued for when the screen was left; cancelled as soon as the account is used. */
    private static volatile java.util.concurrent.ScheduledFuture<?> pending;
    /** Grade page found by the last successful query, so a later term does not re-probe others. */
    private static volatile String gradesPage = "";
    /** The school's own grade link, read once per session; empty when its menu has no such entry. */
    private static volatile String menuLink = "";
    /** True once the menu has been read: an empty answer is a result too, not a reason to retry. */
    private static volatile boolean menuChecked;
    /**
     * A grade page the menu just named, allowed through the read-only path check while it is being
     * opened — this is the only way a query can leave the two folders compiled into the app.
     */
    private static volatile String discovery = "";

    private EduClient() { }

    /** Call on the UI thread before any query: WebView cookies are read there. */
    static void capture() {
        String cookie;
        try {
            String raw = CookieManager.getInstance().getCookie(ORIGIN);
            cookie = raw == null ? "" : raw;
        } catch (RuntimeException unavailable) {
            cookie = "";
        }
        webCookies = cookie;
        // A session the user started in the school's own page counts as signed in here too. Adopting
        // it is what keeps the page and the JSON queries on one session: without it the queries
        // signed in separately and the school dropped the page's session as the older one.
        if (session.isEmpty() && EduLoginProtocol.hasSession(cookie)) {
            session = EduLoginProtocol.mergeCookies("", cookie);
            sessionUser = "";
        }
    }

    /** Hands the saved account over. Empty values simply mean "no automatic sign-in". */
    static void remember(String name, String secret) { remember(name, secret, false); }

    /**
     * @param refused the school rejected this password before, so do not spend another attempt on
     *                it — the school locks accounts after a handful of failures.
     */
    static void remember(String name, String secret, boolean refused) {
        cancelSignOut();
        String nextUser = name == null ? "" : name;
        String nextSecret = secret == null ? "" : secret;
        boolean same = nextUser.equals(user) && nextSecret.equals(password);
        // A live session is kept — for the same account, or for one the school's own page signed in
        // and this app adopted. Signing in again at this point is what used to end the session the
        // user had just opened in that page.
        boolean keep = !session.isEmpty()
            && (same || sessionUser.isEmpty() || nextUser.equals(sessionUser));
        user = nextUser;
        password = nextSecret;
        rejected = same && refused;
        closed = false;
        if (keep) return;
        session = "";
        sessionUser = "";
    }

    static boolean signedIn() { return !session.isEmpty(); }

    static boolean hasCredentials() { return !user.isEmpty() && !password.isEmpty(); }

    /** The built-in session as a Cookie header, so a WebView can open an already signed-in page. */
    static String browserCookie() { return session; }

    /** The saved student number, used only by endpoints that repeat it in the query string. */
    static String studentId() { return user; }

    /**
     * Ends the school session on both sides: the server is told to drop it, then the local copy is
     * forgotten. Called when the query screen closes, so the account is not left signed in.
     */
    static void signOut() { signOut(session); }

    /** Signs out off the UI thread; used when a screen closes. */
    static void signOutLater() {
        final String cookie = session;
        EXIT.execute(() -> signOut(cookie));
    }

    /**
     * A 教务 screen closed: keep the session for the rest of the app visit instead of ending it.
     *
     * <p>Every academic screen calls this, and every one of them cancels it the moment the account is
     * used again — so the grades, the empty-classroom screen, the timetable import and the portal all
     * run on the single session this app signed in with. That is fewer logins (a school's lockout
     * counter only counts logins) and, just as importantly, it stops the app from signing in again
     * while the same account is open in a browser, which is what made the school end one of them.</p>
     */
    static void release() { signOutAfter(IDLE_MS); }

    /** Drops the local copy of a session the school no longer honours, without a logout request. */
    static void forgetSession() {
        session = "";
        sessionUser = "";
        webCookies = "";
    }

    /**
     * Signs out this session after {@code delayMillis}, unless the account is used again in between.
     *
     * <p>Leaving a screen used to end the school session at once, which meant every glance at the
     * grades cost a fresh sign-in — and a fresh sign-in is what a school's lockout counter counts,
     * and what made a second sign-in drop the session open elsewhere. Screens now queue a sign-out
     * a whole visit away and cancel it the moment the account is used again; 退出登录 still ends the
     * session immediately.</p>
     */
    static void signOutAfter(long delayMillis) {
        cancelSignOut();
        final String cookie = session;
        pending = EXIT.schedule(() -> signOut(cookie), Math.max(0, delayMillis),
            java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Back on screen in time: the queued sign-out is dropped and the session carries on. */
    static void cancelSignOut() {
        java.util.concurrent.ScheduledFuture<?> queued = pending;
        pending = null;
        if (queued != null) queued.cancel(false);
    }

    /**
     * @param cookie the session this request was queued for. A sign-in that happened meanwhile wins,
     *               so a quick return to the screen does not log the fresh session out.
     */
    private static synchronized void signOut(String cookie) {
        String held = cookie == null ? "" : cookie;
        if (!session.isEmpty() && !session.equals(held)) return;
        closed = true;
        session = "";
        sessionUser = "";
        webCookies = "";
        gradesPage = "";
        menuLink = "";
        menuChecked = false;
        discovery = "";
        if (held.isEmpty()) return;
        try {
            Map<String, String> jar = new LinkedHashMap<>();
            seed(jar, held);
            request(ORIGIN + LOGOUT, "GET", null, jar, ORIGIN + "/");
        } catch (Exception ignored) {
            // The local session is gone already; an unreachable logout page changes nothing here.
        }
    }

    /** Back on screen: a late automatic sign-in is allowed again, and a queued sign-out is dropped. */
    static void reopen() {
        cancelSignOut();
        closed = false;
    }

    private static final java.util.concurrent.ScheduledExecutorService EXIT =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "edu-signout");
            thread.setDaemon(true);
            return thread;
        });

    /** Turns transport failures into something a person can act on. */
    static String describe(Throwable failure) {
        if (failure instanceof java.net.UnknownHostException)
            return "连不上教务系统：域名解析失败，请检查网络（校外可能需要校园网或 VPN）。";
        if (failure instanceof java.net.SocketTimeoutException)
            return "教务系统响应超时，请稍后重试。";
        if (failure instanceof javax.net.ssl.SSLException)
            return "与教务系统的安全连接失败，请稍后重试。";
        String text = failure == null ? null : failure.getMessage();
        return text == null || text.isEmpty() ? "读取失败，请稍后重试。" : text;
    }

    // ---- pages ---------------------------------------------------------------------------

    static Page page() throws IOException {
        String html = fetch(ROOMS_PAGE, null);
        Page page = new Page();
        page.signedIn = true;
        page.year = EduSite.field(html, "xnm");
        page.term = EduSite.field(html, "xqm");
        page.campus = EduSite.field(html, "xqh_id");
        page.type = EduSite.field(html, "cdlb_id");
        page.campuses.addAll(EduSite.options(html, "xqh_id"));
        page.types.addAll(EduSite.options(html, "cdlb_id"));
        page.years.addAll(EduSite.options(html, "xnm"));
        page.terms.addAll(EduSite.options(html, "xqm"));
        if (page.year.isEmpty() || page.term.isEmpty())
            throw new IOException("没有读到当前学年学期，学校页面可能改版了。");
        return page;
    }

    static Campus campus(String year, String term, String campusId) throws IOException {
        String meta = fetch(ROOMS_META + "&xqh_id=" + encode(campusId) + "&xnm=" + encode(year)
            + "&xqm=" + encode(term), null);
        Campus campus = new Campus();
            JSONObject json = json(meta, "教学楼与节次");
            JSONArray buildings = json.optJSONArray("lhList");
            if (buildings != null) {
                for (int i = 0; i < buildings.length(); i++) {
                    JSONObject raw = buildings.optJSONObject(i);
                    if (raw == null) continue;
                    String owner = raw.optString("XQH_ID", campusId);
                    if (!owner.isEmpty() && !owner.equals(campusId)) continue;
                    String code = raw.optString("JXLDM", ""), name = raw.optString("JXLMC", "");
                    if (!code.isEmpty() && !name.isEmpty()) campus.buildings.add(new String[]{code, name});
                }
            }
            JSONArray periods = json.optJSONArray("jcList");
            StringBuilder times = new StringBuilder();
            if (periods != null) {
                for (int i = 0; i < periods.length(); i++) {
                    JSONObject raw = periods.optJSONObject(i);
                    if (raw == null) continue;
                    int number = raw.optInt("JCMC", 0);
                    if (number < 1 || number > 30) continue;
                    campus.periods.add(number);
                    if (times.length() > 0) times.append('\n');
                    times.append(number).append(' ').append(raw.optString("SJD", ""));
                }
            }
            campus.periodTimes = times.toString();
            if (campus.periods.isEmpty()) throw new IOException("学校没有返回上课节次。");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("xqh_id", campusId);
        form.put("xnm", year);
        form.put("xqm", term);
        form.put("flag", "0");
        JSONObject calendar = json(fetch(ROOMS_TERM, form), "校历");
        JSONObject current = calendar.optJSONObject("dqzcxq");
        if (current != null) campus.monday = current.optString("ZXRQ", "");
        JSONArray weeks = calendar.optJSONArray("nxqzcList");
        if (weeks != null) {
            for (int i = 0; i < weeks.length(); i++) {
                JSONObject raw = weeks.optJSONObject(i);
                if (raw == null) continue;
                if ("0".equals(raw.optString("zczt", "0"))) continue;
                int week = raw.optInt("dxqzc", 0);
                if (week >= 1 && week <= 30) campus.weeks.add(week);
            }
        }
        return campus;
    }

    /**
     * A term picker that needs no network at all.
     *
     * <p>Previously the grade screen asked the grade page for its term selectors before drawing
     * anything, so a school that returns 404 for that page left the screen with an error and no
     * picker — the user could not even try another term. The clock gives a usable starting point
     * (正方 codes 学年 by the year its autumn term begins), and the school's own values replace it
     * as soon as a page answers. The term code is the pair this school uses; a deployment using 1/2
     * is corrected by the page read, not guessed here.</p>
     */
    static Semester localSemesters() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int year = now.get(java.util.Calendar.YEAR);
        int month = now.get(java.util.Calendar.MONTH) + 1;
        if (month < 9) year--;                       // Spring still belongs to last autumn's year.
        String current = Integer.toString(year);
        String term = (month >= 9 || month < 3) ? "3" : "12";
        Semester semester = new Semester();
        semester.year = current;
        semester.term = term;
        semester.years.addAll(EduSite.yearOptions(current, 6));
        semester.terms.addAll(EduSite.termOptions(term));
        return semester;
    }

    /**
     * The terms the school knows, read from the empty-classroom page.
     *
     * <p>That page carries the same school-wide 学年/学期 fields the grade page does, and it is the
     * module this account is certain to have — it is the other half of the same feature and it works
     * here. Taking the list from there means a grade module the school disabled no longer hides the
     * term picker as well.</p>
     *
     * <p>The school renders both values as hidden inputs rather than selects, so its own option list
     * is usually empty. The picker therefore also gets the recent academic years and the terms 正方
     * uses, with the school's own options kept first and never duplicated. Without this, a user whose
     * current term has no grades would have no way to ask for an earlier one.</p>
     */
    static Semester semesters() throws IOException {
        Page page = page();
        Semester semester = new Semester();
        semester.year = page.year;
        semester.term = page.term;
        semester.years.addAll(page.years);
        semester.terms.addAll(page.terms);
        addMissing(semester.years, EduSite.yearOptions(page.year, 6));
        addMissing(semester.terms, EduSite.termOptions(page.term));
        // A page that renders the values without a select still has to offer something to pick.
        if (semester.years.isEmpty()) semester.years.add(new String[]{page.year, page.year});
        if (semester.terms.isEmpty()) semester.terms.add(new String[]{page.term, page.term});
        return semester;
    }

    /** Appends only values the school did not already offer, keeping its own labels and order. */
    private static void addMissing(List<String[]> options, List<String[]> extra) {
        for (String[] candidate : extra) {
            boolean known = false;
            for (String[] existing : options) if (existing[0].equals(candidate[0])) { known = true; break; }
            if (!known) options.add(candidate);
        }
    }

    /**
     * Grade modules to try, best first: whatever the school's own menu names, then the routes 正方
     * ships. Reading the menu keeps a moved or renamed module working without a release.
     */
    private static List<String> gradeEntries() {
        List<String> entries = new ArrayList<>();
        if (!gradesPage.isEmpty()) entries.add(gradesPage);
        String discovered = menuGradeLink();
        if (!discovered.isEmpty() && !entries.contains(discovered)) entries.add(discovered);
        for (String path : GRADE_PATHS) if (!entries.contains(path)) entries.add(path);
        return entries;
    }

    /**
     * The grade entry the school's own menu links. Read on every first query rather than compiled in,
     * so a module that moved or was renamed still opens — and silently returning "" lets the caller
     * fall back to the common routes. The home page is read as a second source because some
     * deployments serve the menu inline there instead of on the menu page.
     */
    private static String menuGradeLink() {
        if (menuChecked) return menuLink;
        for (String page : new String[]{MENU, "/"}) {
            try {
                String link = EduSite.gradeLink(request(ORIGIN + page, "GET", null, jar(), ORIGIN + page));
                if (link.contains(".html")) { menuLink = link; break; }
            } catch (Exception unavailable) {
                // Next source; a menu that cannot be read is not an error on its own.
            }
        }
        menuChecked = true;
        return menuLink;
    }

    private static String clip(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit) + "…";
    }

    /** Same page, in its query form: the school's script appends {@code doType=query} and posts. */
    private static String queryPath(String entry) {
        String base = entry;
        int hash = base.indexOf('#');
        if (hash >= 0) base = base.substring(0, hash);
        if (base.contains("doType=query")) return base;
        return base + (base.contains("?") ? "&" : "?") + "doType=query";
    }

    /**
     * The page a query belongs to, used as {@code Referer}. Derived from the requested path rather
     * than compiled in, because a discovered grade module can live somewhere else entirely.
     */
    private static String pageOf(String path) {
        String page = path.replace("&doType=query", "").replace("?doType=query", "");
        return page.isEmpty() ? GRADES_PAGE : page;
    }

    // ---- queries -------------------------------------------------------------------------

    static List<Room> rooms(Page page, String campusId, String building, String type,
                            int week, int weekday, String periodMask) throws IOException {
        List<Room> rooms = new ArrayList<>();
        for (int index = 1; index <= MAX_PAGES; index++) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("xqh_id", campusId);
            form.put("xnm", page.year);
            form.put("xqm", page.term);
            form.put("jyfs", "0");
            form.put("zcd", EduSite.weekMask(week));
            form.put("xqj", Integer.toString(weekday));
            form.put("jcd", periodMask);
            form.put("lh", building);
            form.put("cdlb_id", type);
            form.put("cdejlb_id", "");
            form.put("qszws", "");
            form.put("jszws", "");
            form.put("cdmc", "");
            form.put("cdjylx", "");
            form.put("sfbhkc", "");
            form.put("queryModel.showCount", "100");
            form.put("queryModel.currentPage", Integer.toString(index));
            form.put("queryModel.sortName", "cdbh");
            form.put("queryModel.sortOrder", "asc");
            form.put("_search", "false");
            form.put("nd", Long.toString(System.currentTimeMillis()));
            JSONObject reply = ask(ROOMS_QUERY, form, "空教室查询");
            JSONArray items = reply.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject raw = items.optJSONObject(i);
                    if (raw == null) continue;
                    String name = raw.optString("cdmc", "").trim();
                    if (name.isEmpty()) continue;
                    rooms.add(new Room(name, raw.optString("jxlmc", ""), raw.optString("cdlbmc", ""),
                        raw.optString("zws", "")));
                }
            }
            int pages = reply.optInt("totalPage", 1);
            if (index >= pages || items == null || items.length() == 0) break;
        }
        return rooms;
    }

    /**
     * Grades for one term.
     *
     * <p>The query itself is the test: a module that is not offered to this account answers 404 to
     * the query exactly as it does to the page, so opening the page first only doubled the requests
     * and turned one refusal into a screen that could not even offer a term to switch to. Each
     * candidate is queried directly and the first one that returns a result set wins.</p>
     */
    static List<Course> grades(String year, String term) throws IOException {
        // "ALL" is the picker's sentinel: the school wants an empty field for "every year/term".
        String xnm = "ALL".equals(year) ? "" : year;
        String xqm = "ALL".equals(term) ? "" : term;
        StringBuilder tried = new StringBuilder();
        int count = 0;
        for (String entry : gradeEntries()) {
            count++;
            discovery = entry;
            try {
                List<Course> courses = queryGrades(entry, xnm, xqm);
                gradesPage = entry;
                discovery = "";
                return courses;
            } catch (NotSignedIn expired) {
                discovery = "";
                throw expired;
            } catch (HttpFailure refused) {
                if (tried.length() > 0) tried.append('、');
                tried.append(refused.status).append(' ').append(shortPath(entry));
            } catch (IOException unavailable) {
                if (tried.length() > 0) tried.append('、');
                tried.append("无数据 ").append(shortPath(entry));
            }
        }
        discovery = "";
        throw new IOException("成绩接口打不开（试过 " + count + " 个地址："
            + clip(tried.toString(), 120) + "）。可以换上面的学年、学期再试一次；"
            + (menuGradeLink().isEmpty()
                ? "学校菜单里目前也没有成绩入口。"
                : "学校菜单里的成绩地址同样打不开。")
            + "若每个学期都一样，多半是学校没给这个账号开放网页成绩查询。");
    }

    private static List<Course> queryGrades(String entry, String year, String term) throws IOException {
        String path = queryPath(entry);
        List<Course> courses = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("xnm", year);
            form.put("xqm", term);
            form.put("_search", "false");
            form.put("nd", Long.toString(System.currentTimeMillis()));
            form.put("queryModel.showCount", "100");
            form.put("queryModel.currentPage", Integer.toString(index));
            form.put("queryModel.sortName", "");
            form.put("queryModel.sortOrder", "asc");
            form.put("time", "0");
            JSONObject reply = ask(path, form, "成绩查询");
            JSONArray items = reply.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject raw = items.optJSONObject(i);
                    if (raw == null) continue;
                    String name = raw.optString("kcmc", "").trim();
                    if (name.isEmpty()) continue;
                    courses.add(new Course(name, raw.optString("cj", ""), raw.optString("xf", ""),
                        raw.optString("jd", "")));
                }
            }
            int pages = reply.optInt("totalPage", 1);
            if (index >= pages || items == null || items.length() == 0) break;
        }
        return courses;
    }

    /**
     * The student timetable for one term, read from the same query endpoint the school's own page
     * calls — no browser, no rendered grid, no manual "读取当前课表" step.
     *
     * <p>正方 deployments rename that endpoint, so each known name is tried with the two parameter
     * shapes in circulation. Only a reply carrying {@code kbList} is accepted; an HTML error page or
     * a 404 is recorded and the next candidate is tried. Validation and storage belong to the caller,
     * so a surprising reply can never silently replace the timetable.</p>
     */
    static String timetable(String year, String term) throws IOException {
        if (year == null || !year.matches("(?:19|20)\\d{2}") || term == null || !term.matches("\\d{1,3}"))
            throw new IOException("请选择有效的学年和学期");
        // The page itself names the query endpoint it calls; that beats guessing across deployments.
        List<String> paths = new ArrayList<>();
        try {
            paths.addAll(EduSite.timetableQueries(fetch(TIMETABLE_PAGE, null)));
        } catch (IOException unavailable) {
            // An unreadable page only means the compiled-in candidates below are used as-is.
        }
        for (String path : TIMETABLE_QUERIES) if (!paths.contains(path)) paths.add(path);
        StringBuilder tried = new StringBuilder();
        int attempts = 0;
        for (String path : paths) {
            for (int variant = 0; variant < 2 && attempts < 12; variant++, attempts++) {
                Map<String, String> form = new LinkedHashMap<>();
                form.put("xnm", year);
                form.put("xqm", term);
                form.put("kzlx", "ck");
                String url = path;
                if (variant == 1) {
                    form.put("xsdm", ""); form.put("kclbdm", ""); form.put("jc", "");
                    form.put("sfzgzc", ""); form.put("sflxjc", "");
                    if (!studentId().isEmpty()) {
                        form.put("su", studentId());
                        if (!path.contains("su="))
                            url = path + (path.contains("?") ? "&" : "?") + "su=" + encode(studentId());
                    }
                }
                try {
                    JSONObject reply = ask(url, form, "课表");
                    if (reply.optJSONArray("kbList") != null) return reply.toString();
                    tried.append(shortPath(url)).append("(无课表数据) ");
                } catch (IOException failure) {
                    String message = failure.getMessage() == null ? "失败" : failure.getMessage();
                    tried.append(shortPath(url)).append('(').append(clip(message, 70)).append(") ");
                }
            }
            if (attempts >= 12) break;
        }
        throw new IOException("教务课表查询没有返回数据（试过 " + attempts + " 种）：" + tried);
    }

    // ---- transport -----------------------------------------------------------------------

    private static String fetch(String path, Map<String, String> form) throws IOException {
        checkPath(path);
        cancelSignOut();
        String method = form == null ? "GET" : "POST";
        String referer = ORIGIN + (path.startsWith("/cdjy/") ? ROOMS_PAGE
            : path.startsWith("/kbcx/") ? TIMETABLE_PAGE : pageOf(path));
        try {
            return checked(request(ORIGIN + path, method, form, jar(), referer));
        } catch (NotSignedIn expired) {
            if (!hasCredentials() || rejected || closed) throw expired;
            signIn();
            return checked(request(ORIGIN + path, method, form, jar(), referer));
        }
    }

    /**
     * Runs a query and, if the school refuses the POST, repeats the same parameters as a GET.
     * A few deployments answer 400 or 500 to the form post but still serve the URL query.
     */
    private static JSONObject ask(String path, Map<String, String> form, String what) throws IOException {
        try {
            return json(fetch(path, form), what);
        } catch (HttpFailure refused) {
            if (refused.status != 400 && refused.status < 500) throw refused;
            StringBuilder url = new StringBuilder(path).append(path.contains("?") ? '&' : '?')
                .append(SmsLoginProtocol.form(form));
            return json(fetch(url.toString(), null), what);
        }
    }

    /** A 200 response that is the login form means the session died between two requests. */
    private static String checked(String text) throws NotSignedIn {
        if (EduLoginProtocol.isLoginForm(text)) throw new NotSignedIn();
        return text;
    }

    /**
     * Signs in with the saved account the way the school's own page does: read the csrf token,
     * fetch the RSA key, post the encrypted password. A rejected password is never retried, because
     * the school locks accounts after repeated failures.
     */
    static synchronized void signIn() throws IOException {
        if (!hasCredentials()) throw new NotSignedIn();
        Map<String, String> jar = new LinkedHashMap<>();
        String html = request(ORIGIN + LOGIN, "GET", null, jar, ORIGIN + "/");
        if (EduLoginProtocol.csrf(html).isEmpty())
            throw new IOException("没有读到登录页的令牌，学校页面可能改版了。");
        String key = request(ORIGIN + PUBLIC_KEY + "?time=" + System.currentTimeMillis(), "GET", null, jar,
            ORIGIN + LOGIN);
        String modulus = EduLoginProtocol.jsonValue(key, "modulus");
        String exponent = EduLoginProtocol.jsonValue(key, "exponent");
        if (modulus.isEmpty() || exponent.isEmpty()) throw new IOException("没有读到学校的登录公钥。");
        Map<String, String> form = EduLoginProtocol.fields(html);
        form.put(EduLoginProtocol.USER, user);
        try {
            form.put(EduLoginProtocol.PASSWORD, EduLoginProtocol.encryptPassword(modulus, exponent, password));
        } catch (java.security.GeneralSecurityException failure) {
            throw new IOException("密码加密失败，请重试。");
        }
        String reply = request(ORIGIN + LOGIN + "?time=" + System.currentTimeMillis(), "POST", form, jar,
            ORIGIN + LOGIN);
        if (EduLoginProtocol.wrongPassword(reply)) {
            rejected = true;
            throw new BadPassword();
        }
        if (EduLoginProtocol.captchaRequired(reply)) {
            rejected = true;
            throw new CaptchaNeeded();
        }
        if (EduLoginProtocol.isLoginForm(reply)) {
            rejected = true;
            throw new BadPassword("登录没有成功，请核对账号密码。");
        }
        String cookies = cookies(jar);
        if (cookies.isEmpty()) throw new IOException("登录后没有拿到会话，请重试。");
        session = cookies;
        sessionUser = user;
        closed = false;
    }

    /** Cookies for the next request: the built-in session first, the embedded browser second. */
    private static Map<String, String> jar() {
        Map<String, String> jar = new LinkedHashMap<>();
        seed(jar, session);
        if (jar.isEmpty()) seed(jar, webCookies);
        return jar;
    }

    private static void seed(Map<String, String> jar, String header) {
        if (header == null) return;
        for (String part : header.split(";")) {
            int split = part.indexOf('=');
            if (split > 0) jar.put(part.substring(0, split).trim(), part.substring(split + 1).trim());
        }
    }

    private static String cookies(Map<String, String> jar) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : jar.entrySet()) {
            if (out.length() > 0) out.append("; ");
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    /** School-host HTTPS with redirects followed by hand, so a jump to the login page stays visible. */
    private static String request(String url, String method, Map<String, String> form,
                                  Map<String, String> jar, String referer) throws IOException {
        String body = form == null ? null : SmsLoginProtocol.form(form);
        for (int redirect = 0; redirect < 6; redirect++) {
            URL target = new URL(url);
            if (!"https".equals(target.getProtocol()) || !HOST.equals(target.getHost()))
                throw new IOException("拒绝连接非学校地址。");
            HttpsURLConnection connection = (HttpsURLConnection) target.openConnection();
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("User-Agent", "CampusKit/1.4 (Android)");
            connection.setRequestProperty("Accept", "application/json, text/html;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Referer", referer);
            if (!jar.isEmpty()) {
                StringBuilder cookie = new StringBuilder();
                for (Map.Entry<String, String> entry : jar.entrySet()) {
                    if (cookie.length() > 0) cookie.append("; ");
                    cookie.append(entry.getKey()).append('=').append(entry.getValue());
                }
                connection.setRequestProperty("Cookie", cookie.toString());
            }
            try {
                if (body != null) {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    connection.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                }
                int status = connection.getResponseCode();
                store(connection, jar);
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("学校返回了无效跳转。");
                    url = new URL(target, location).toString();
                    if (status != 307 && status != 308) { method = "GET"; body = null; }
                    continue;
                }
                if (status == 401 || status == 403) throw new NotSignedIn();
                if (status != 200) throw new HttpFailure(status, url, snippet(connection));
                return read(connection);
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("登录跳转次数异常，请重新登录。");
    }

    private static void store(HttpsURLConnection connection, Map<String, String> jar) {
        Map<String, List<String>> headers = connection.getHeaderFields();
        if (headers == null) return;
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (header.getKey() == null || !"set-cookie".equals(header.getKey().toLowerCase(Locale.ROOT))) continue;
            for (String value : header.getValue()) {
                if (value == null) continue;
                int split = value.indexOf('=');
                int end = value.indexOf(';');
                if (split <= 0) continue;
                String name = value.substring(0, split).trim();
                String text = end > split ? value.substring(split + 1, end).trim() : value.substring(split + 1).trim();
                if (name.isEmpty()) continue;
                if (text.isEmpty()) jar.remove(name); else jar.put(name, text);
            }
        }
    }

    /**
     * The first readable words of an error response, so a failure can be reported instead of only
     * described. Tags and scripts are dropped; nothing longer than a line is kept.
     */
    private static String snippet(HttpsURLConnection connection) {
        try (InputStream input = connection.getErrorStream()) {
            if (input == null) return "";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count = input.read(buffer);
            if (count > 0) out.write(buffer, 0, count);
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8)
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
            return text.length() > 90 ? text.substring(0, 90) + "…" : text;
        } catch (Exception unreadable) {
            return "";
        }
    }

    /** Path and query only: the host is the same for every request and only adds noise. */
    private static String shortPath(String url) {
        try {
            URL parsed = new URL(url);
            String query = parsed.getQuery();
            return parsed.getPath() + (query == null || query.isEmpty() ? "" : "?" + query);
        } catch (Exception invalid) {
            return url;
        }
    }

    private static String read(HttpsURLConnection connection) throws IOException {
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (out.size() + count > MAX_BYTES) throw new IOException("学校响应过大，已停止读取。");
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static JSONObject json(String text, String what) throws IOException {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) throw new IOException(what + "没有返回内容。");
        if (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[')
            throw new IOException("学校没有返回" + what + "数据，页面可能改版了。");
        try {
            return new JSONObject(trimmed);
        } catch (org.json.JSONException broken) {
            throw new IOException(what + "数据无法解析。");
        }
    }

    private static void checkPath(String path) throws IOException {
        if (path.startsWith("/cdjy/") || path.startsWith("/cjcx/") || path.startsWith("/kbcx/")) return;
        // 正方's other grade module. Named one path, not the whole folder, so this stays as narrow as
        // the feature needs: two read-only modules, nothing else.
        if (path.startsWith("/kwgl/kscjcx_kscjcx.html")) return;
        // A grade module the school moved out of /cjcx/: allowed only once its own menu named it.
        String named = gradesPage.isEmpty() ? discovery : gradesPage;
        if (!named.isEmpty() && path.startsWith(named)) return;
        throw new IOException("拒绝访问未授权的教务页面。");
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** Teaching week the term is in today, or 0 when the school did not return a start date. */
    static int currentWeek(Campus campus) {
        if (campus == null || campus.monday.isEmpty()) return 0;
        return EduSite.weekOn(campus.monday, EduSite.today());
    }
}
