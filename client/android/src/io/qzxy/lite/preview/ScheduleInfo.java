// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;
final class ScheduleInfo {
    static String date(){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date());}
    static int minutes(){Calendar c=Calendar.getInstance();return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);}
    static int minute(String s){return Integer.parseInt(s.substring(0,2))*60+Integer.parseInt(s.substring(3));}
    static int week(String monday,String date){try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("UTC"));return (int)Math.floorDiv((f.parse(date).getTime()-f.parse(monday).getTime())/86400000L,7)+1;}catch(Exception e){return 0;}}
    static List<JSONObject> today(String json,String monday,String date)throws Exception{
        List<JSONObject> out=new ArrayList<>();int week=week(monday,date);JSONArray all=new JSONArray(json); Calendar day=Calendar.getInstance();day.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(date));
        for(int i=0;i<all.length();i++){JSONObject c=all.getJSONObject(i);int parity=c.optInt("parity");
            if(c.optInt("day")==(day.get(Calendar.DAY_OF_WEEK)+5)%7&&CourseWeeks.includes(c,week))out.add(c);}
        out.sort((a,b)->Integer.compare(a.optInt("first"),b.optInt("first")));return out;
    }
    static String status(JSONObject c,int now){
        String start=ClassTimes.start(c.optInt("first")),end=ClassTimes.end(c.optInt("last"));
        if(start.isEmpty()||end.isEmpty())return "时间待设置";
        if(now>=minute(end))return "已结束";
        return now<minute(start)?"待上课":"正在上课";
    }
    static String summary(List<JSONObject> list,int now){
        for(JSONObject c:list)if(status(c,now).equals("正在上课"))return "正在上课 · "+c.optString("name")+" · "+c.optString("room");
        for(JSONObject c:list)if(status(c,now).equals("待上课"))return "下一节 "+ClassTimes.start(c.optInt("first"))+" · "+c.optString("name")+" · "+c.optString("room");
        for(JSONObject c:list)if(status(c,now).equals("时间待设置"))return "部分课程时间待设置";
        return list.isEmpty()?"今天没有课":"今天的课程已上完";
    }
}
