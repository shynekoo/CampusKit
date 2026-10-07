package io.qzxy.lite.preview;
import android.app.Instrumentation;
import android.content.*;
import org.json.*;
import java.util.*;
final class ScheduleFeatureTests {
    private static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError("Schedule check "+checks);}
    static int run(Instrumentation runner)throws Exception{
        checks=0;
        JSONObject c=new JSONObject().put("name","测试课程").put("room","A101").put("teacher","测试").put("day",0).put("first",1).put("last",2).put("from",1).put("to",20).put("parity",0).put("color",3);
        check(ScheduleInfo.status(c,509).equals("待上课"));check(ScheduleInfo.status(c,510).equals("正在上课"));check(ScheduleInfo.status(c,604).equals("正在上课"));check(ScheduleInfo.status(c,605).equals("已结束"));
        check(ScheduleInfo.week("2026-08-31","2026-10-05")==6);check(ScheduleInfo.week("2026-08-31","2026-10-04")==5);check(ScheduleInfo.week("2026-08-31","2026-08-30")==0);
        String json=new JSONArray().put(c).toString();check(ScheduleInfo.today(json,"2026-08-31","2026-10-05").size()==1);check(ScheduleInfo.today(json,"2026-08-31","2026-10-06").isEmpty());
        c.put("parity",1);check(ScheduleInfo.today(new JSONArray().put(c).toString(),"2026-08-31","2026-10-05").isEmpty());c.put("parity",0);
        check(ScheduleInfo.summary(Collections.singletonList(c),509).contains("08:30"));check(ScheduleInfo.summary(Collections.singletonList(c),605).equals("今天的课程已上完"));
        android.text.Spanned ended=(android.text.Spanned)ScheduleWidget.courseText(Collections.singletonList(c),605);
        android.text.style.StrikethroughSpan[] strikes=ended.getSpans(0,ended.length(),android.text.style.StrikethroughSpan.class);
        check(strikes.length==1);check(ended.subSequence(ended.getSpanStart(strikes[0]),ended.getSpanEnd(strikes[0])).toString().equals("测试课程"));check(!ended.toString().contains("已结束"));check(ended.toString().contains("A101"));
        android.text.Spanned upcoming=(android.text.Spanned)ScheduleWidget.courseText(Collections.singletonList(c),509);check(upcoming.getSpans(0,upcoming.length(),android.text.style.StrikethroughSpan.class).length==0);check(upcoming.toString().contains("待上课"));
        SharedPreferences prefs=runner.getTargetContext().getSharedPreferences("schedule-test-only",Context.MODE_PRIVATE);prefs.edit().clear().putString("courses",json).putString("monday","2026-08-31").putString("token","must-not-export").commit();
        JSONObject backup=TimetableBackup.exportData(prefs);check(!backup.has("token"));check(!backup.toString().contains("must-not-export"));
        JSONObject parsed=TimetableBackup.parse(backup.toString().getBytes("UTF-8"));check(new JSONArray(parsed.getString("courses")).getJSONObject(0).getInt("color")==3);
        check(TimetableBackup.restore(prefs,parsed));check(prefs.getString("courses","").equals(json));
        JSONObject bad=new JSONObject(backup.toString()).put("monday","2026-10-06");boolean rejected=false;try{TimetableBackup.parse(bad.toString().getBytes("UTF-8"));}catch(Exception expected){rejected=true;}check(rejected);check(prefs.getString("courses","").equals(json));
        bad=new JSONObject(backup.toString()).put("version",99);rejected=false;try{TimetableBackup.parse(bad.toString().getBytes("UTF-8"));}catch(Exception expected){rejected=true;}check(rejected);
        android.graphics.Bitmap bmp=android.graphics.Bitmap.createBitmap(10,10,android.graphics.Bitmap.Config.ARGB_8888);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bmp.recycle();String encoded=TimetableBackup.image(out.toByteArray());backup.put("background",encoded);check(TimetableBackup.parse(backup.toString().getBytes("UTF-8")).getString("background").equals(encoded));
        bad=new JSONObject(backup.toString()).put("background","invalid");rejected=false;try{TimetableBackup.parse(bad.toString().getBytes("UTF-8"));}catch(Exception expected){rejected=true;}check(rejected);
        boolean[] weeks=new boolean[30];weeks[0]=true;weeks[2]=true;weeks[7]=true;CourseWeeks.put(c,weeks);TimetableActivity.validate(c);
        check(CourseWeeks.includes(c,1));check(!CourseWeeks.includes(c,2));check(CourseWeeks.includes(c,8));check(!CourseWeeks.includes(c,9));
        check(CourseWeeks.label(c).equals("第 1、3、8 周"));
        check(ScheduleInfo.today(new JSONArray().put(c).toString(),"2026-08-31","2026-09-07").isEmpty());
        check(ScheduleInfo.today(new JSONArray().put(c).toString(),"2026-08-31","2026-09-14").size()==1);
        backup.put("courses",new JSONArray().put(c).toString());check(new JSONArray(TimetableBackup.parse(backup.toString().getBytes("UTF-8")).getString("courses")).getJSONObject(0).getJSONArray("weeks").length()==3);
        rejected=false;try{CourseWeeks.put(c,new boolean[30]);}catch(Exception expected){rejected=true;}check(rejected);
        for(String invalid:new String[]{"[]","[0]","[31]","[2,2]","[3,1]","[1.5]","[\"2\"]"}){c.put("weeks",new JSONArray(invalid));rejected=false;try{TimetableActivity.validate(c);}catch(Exception expected){rejected=true;}check(rejected);}
        c.remove("weeks");c.put("from",1).put("to",16).put("parity",2);check(!CourseWeeks.includes(c,1));check(CourseWeeks.includes(c,2));check(CourseWeeks.selection(c)[15]);
        prefs.edit().clear().commit();return checks;
    }
}
