package io.qzxy.lite.preview;

import android.webkit.CookieManager;
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
 * <p>The session belongs to the embedded login page: this client only borrows the cookies the user
 * already obtained there and never sees a password. Every request is pinned to the school's HTTPS
 * host and to a short list of read-only paths — the same three pages the school's own empty-classroom
 * screen uses — so a feature here cannot quietly start touching course selection or account data.</p>
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
    private static final String GRADES_QUERY = "/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005&doType=query";
    private static final int TIMEOUT = 15000, MAX_BYTES = 2_000_000, MAX_PAGES = 30;

    static final class NotSignedIn extends IOException {
        private static final long serialVersionUID = 1L;
        NotSignedIn() { super("需要先登录教务系统"); }
    }

    /** Everything the school page says about the current term, plus its own filter options. */
    static final class Page {
        String year = "", term = "", campus = "", type = "";
        final List<String[]> campuses = new ArrayList<>();
        final List<String[]> types = new ArrayList<>();
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

    /** Cookie header captured on the UI thread; WebView cookies are not read off it. */
    private static volatile String session = "";

    private EduClient() { }

    /** Call on the UI thread before any query: the session lives in the embedded browser. */
    static void capture() {
        try {
            String cookie = CookieManager.getInstance().getCookie(ORIGIN);
            session = cookie == null ? "" : cookie;
        } catch (RuntimeException unavailable) {
            session = "";
        }
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

    static Semester semesters() throws IOException {
        String html = fetch(GRADES_PAGE, null);
        Semester semester = new Semester();
        semester.year = EduSite.field(html, "xnm");
        semester.term = EduSite.field(html, "xqm");
        semester.years.addAll(EduSite.options(html, "xnm"));
        semester.terms.addAll(EduSite.options(html, "xqm"));
        if (semester.year.isEmpty() || semester.term.isEmpty())
            throw new IOException("没有读到成绩页的学期。");
        return semester;
    }

    // ---- queries -------------------------------------------------------------------------

    static List<Room> rooms(Page page, String campusId, String building, String type,
                            int week, int weekday, int from, int to) throws IOException {
        List<Room> rooms = new ArrayList<>();
        for (int index = 1; index <= MAX_PAGES; index++) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("xqh_id", campusId);
            form.put("xnm", page.year);
            form.put("xqm", page.term);
            form.put("jyfs", "0");
            form.put("zcd", EduSite.weekMask(week));
            form.put("xqj", Integer.toString(weekday));
            form.put("jcd", EduSite.periodMask(from, to));
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
            JSONObject reply = json(fetch(ROOMS_QUERY, form), "空教室查询");
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

    static List<Course> grades(String year, String term) throws IOException {
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
            JSONObject reply = json(fetch(GRADES_QUERY, form), "成绩查询");
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

    // ---- transport -----------------------------------------------------------------------

    private static String fetch(String path, Map<String, String> form) throws IOException {
        checkPath(path);
        Map<String, String> jar = new LinkedHashMap<>();
        String seeded = session;
        if (seeded != null) {
            for (String part : seeded.split(";")) {
                int split = part.indexOf('=');
                if (split > 0) jar.put(part.substring(0, split).trim(), part.substring(split + 1).trim());
            }
        }
        String url = ORIGIN + path;
        String method = form == null ? "GET" : "POST";
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
            connection.setRequestProperty("User-Agent", "CampusKit/1.2 (Android)");
            connection.setRequestProperty("Accept", "application/json, text/html;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Referer", ORIGIN + (path.startsWith("/cdjy/") ? ROOMS_PAGE : GRADES_PAGE));
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
                    if (location.contains("login_slogin")) throw new NotSignedIn();
                    url = new URL(target, location).toString();
                    if (status != 307 && status != 308) { method = "GET"; body = null; }
                    continue;
                }
                if (status == 401 || status == 403) throw new NotSignedIn();
                if (status != 200) throw new IOException("教务系统暂时无法响应（HTTP " + status + "）。");
                String text = read(connection);
                if (loginForm(text)) throw new NotSignedIn();
                return text;
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

    /**
     * The school's login form is the only page carrying a 用户名 input, so a session that silently
     * expired inside a 200 response is still recognised instead of being parsed as data.
     */
    private static boolean loginForm(String text) {
        return text.contains("id=\"yhm\"") || text.contains("name=\"yhm\"");
    }

    private static void checkPath(String path) throws IOException {        if (!path.startsWith("/cdjy/") && !path.startsWith("/cjcx/"))
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
