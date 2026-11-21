// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import io.qzxy.lite.core.*;
import org.json.*;
final class TimetableWebReader {
    static TimetablePdfParser.Result parse(String json) throws Exception {
        if(json==null || json.length()>250000) throw new IllegalArgumentException("课表内容过大");
        JSONObject data=new JSONObject(json); String error=data.optString("error");
        if(!error.isEmpty()) throw new IllegalArgumentException(error);
        JSONArray items=data.getJSONArray("courses");
        if(items.length()==0 || items.length()>500) throw new IllegalArgumentException("未找到完整课程");
        TimetablePdfParser.Result result=new TimetablePdfParser.Result();
        for(int i=0;i<items.length();i++) { JSONObject item=items.getJSONObject(i);
            result.courses.addAll(TimetableWebCourseParser.parseAll(item.getInt("day"),item.optString("title"),item.getString("text"))); }
        return result;
    }
}
