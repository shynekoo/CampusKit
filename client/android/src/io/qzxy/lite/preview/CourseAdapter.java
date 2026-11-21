// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import io.qzxy.lite.core.Course;
import io.qzxy.lite.core.CourseJsonMapper;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Bridge between the persisted JSONObject course format and the typed core {@link Course} model.
 *
 * <p>The app keeps storing courses as a JSON array in SharedPreferences - that format and its keys
 * are unchanged, so old data and backups stay valid. This adapter only converts for code that wants
 * a typed course; the weekly set is translated to/from the JSON array that CourseWeeks owns. All
 * conversion logic lives in core and is covered by CoreTests; this class is the thin org.json edge.</p>
 */
final class CourseAdapter {
    private CourseAdapter() { }

    static Course fromJson(JSONObject json) throws JSONException {
        return CourseJsonMapper.fromFields(toFields(json));
    }

    static JSONObject toJson(Course course, JSONObject original) throws JSONException {
        java.util.Map<String, String> source = original == null ? null : toFields(original);
        java.util.Map<String, String> fields = CourseJsonMapper.toFields(course, source);
        JSONObject out = original == null ? new JSONObject() : new JSONObject(original.toString());
        for (java.util.Map.Entry<String, String> e : fields.entrySet()) out.put(e.getKey(), e.getValue());
        // Weeks are the one field whose storage form is an array, not a scalar string.
        JSONArray weeks = new JSONArray();
        for (int w : course.weeks()) weeks.put(w);
        out.put("weeks", weeks);
        out.put("from", firstWeek(course));
        out.put("to", lastWeek(course));
        out.put("parity", 0);
        return out;
    }

    /** Every value as a String; a JSON array of weeks becomes the mapper's comma/range form. */
    private static java.util.Map<String, String> toFields(JSONObject json) throws JSONException {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<String, String>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            if (value instanceof JSONArray) {
                if (!"weeks".equals(key)) continue;   // other arrays are unknown extensions
                JSONArray array = (JSONArray) value;
                StringBuilder spec = new StringBuilder();
                for (int i = 0; i < array.length(); i++) {
                    if (spec.length() > 0) spec.append(',');
                    spec.append(array.getInt(i));
                }
                out.put("weeks", spec.toString());
            } else if (value != JSONObject.NULL) {
                out.put(key, String.valueOf(value));
            }
        }
        return out;
    }

    private static int firstWeek(Course course) {
        int first = 1;
        for (int w : course.weeks()) { first = w; break; }
        return first;
    }

    private static int lastWeek(Course course) {
        int last = 1;
        for (int w : course.weeks()) last = w;
        return last;
    }
}
