// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import io.qzxy.lite.core.EduTimetable;
import io.qzxy.lite.core.TimetableWebCourseParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Adapts 正方's {@code kbList} JSON to the exact {@code {courses:[{day,title,text}]}} shape the
 * rendered-web-grid import already consumes, so both paths share one parser and one validation.
 *
 * <p>Field names differ between 正方 deployments; each value has a short list of known aliases. A
 * row whose weekday, sections, weeks, room or teacher cannot be read is skipped and counted rather
 * than silently turning into a wrong class. If nothing survives, no import is attempted.</p>
 */
final class EduTimetableReader {
    static final class Result {
        final String json;
        final int total, kept, skipped;
        Result(String json, int total, int kept, int skipped) {
            this.json = json; this.total = total; this.kept = kept; this.skipped = skipped;
        }
    }

    private EduTimetableReader() { }

    static Result parse(String raw) throws Exception {
        if (raw == null || raw.length() > 2_000_000) throw new IllegalArgumentException("教务课表数据过大");
        JSONObject data;
        try { data = new JSONObject(raw); }
        catch (JSONException invalid) { throw new IllegalArgumentException("教务课表返回的不是可识别的 JSON"); }
        JSONArray list = data.optJSONArray("kbList");
        if (list == null) throw new IllegalArgumentException(
            "教务课表没有返回 kbList（这个学期可能没有课，或学校用了不同的课表接口）");
        JSONArray courses = new JSONArray();
        int skipped = 0;
        for (int i = 0; i < list.length() && courses.length() < 500; i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) { skipped++; continue; }
            try {
                int day = EduTimetable.day(value(item, "xqj", "day"), value(item, "xqjmc", "xqjdm"));
                String sections = EduTimetable.sections(value(item, "jc", "jcdm2", "skjc", "kcsj"));
                String weekRaw = value(item, "zcd", "zcmc", "zc", "qsjsz");
                String parityRaw = value(item, "sfdsz", "dsz");
                java.util.List<String> weeks = EduTimetable.weekSegments(weekRaw, parityRaw);
                String name = EduTimetable.clean(value(item, "kcmc", "kcmc1", "courseName"));
                String room = EduTimetable.clean(value(item, "cdmc", "cdmc1", "jxdd", "place"));
                String teacher = EduTimetable.clean(value(item, "xm", "jsxm", "teaxms", "teacher"));
                // One 正方 row can carry several week ranges (调课). Each becomes a separate
                // course so the strict single-range parser still owns every written row.
                for (String week : weeks) {
                    String text = EduTimetable.courseText(sections, week, room, teacher, name);
                    TimetableWebCourseParser.parse(day, name, text);
                    courses.put(new JSONObject().put("day", day).put("title", name).put("text", text));
                }
            } catch (Exception odd) {
                skipped++;
            }
        }
        if (courses.length() == 0) throw new IllegalArgumentException(
            "教务课表里没有能识别的课程（共 " + list.length() + " 条，已跳过 " + skipped + " 条）");
        return new Result(new JSONObject().put("courses", courses).toString(),
            list.length(), courses.length(), skipped);
    }

    /** First non-empty alias; missing values stay empty so the strict parser can reject the row. */
    private static String value(JSONObject item, String... keys) {
        for (String key : keys) {
            String text = item.optString(key, "");
            if (text != null && !text.trim().isEmpty() && !"null".equals(text)) return text;
        }
        return "";
    }
}
