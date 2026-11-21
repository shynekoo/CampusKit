// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import io.qzxy.lite.core.DeviceProfile;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** Adapter from the device/info/mac JSON payload to the strict core profile. */
final class DeviceDetails {
    private DeviceDetails() { }

    /** JSON null must not become the literal text "null". */
    private static String text(JSONObject data, String key) {
        Object value = data.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof String) return (String) value;
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return "";
    }

    static Map<String, String> fields(JSONObject data) {
        Map<String, String> fields = new LinkedHashMap<>();
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            fields.put(key, text(data, key));
        }
        return fields;
    }

    /** Unknown fields are ignored rather than displayed; only confirmed fields reach the user. */
    static DeviceProfile parse(JSONObject data) throws JSONException {
        try { return DeviceProfile.parse(fields(data)); }
        catch (IllegalArgumentException notRegistered) { throw new JSONException("Device not registered"); }
    }

    static String describe(JSONObject data, String expectedProject) throws JSONException {
        return parse(data).describe(expectedProject);
    }

    static DeviceLookup lookup(JSONObject data, String expectedProject, AccountProfile account) throws JSONException {
        DeviceProfile profile = parse(data);
        return new DeviceLookup(profile, account, profile.describe(expectedProject),
            profile.readyForWaterControl(expectedProject));
    }
}
