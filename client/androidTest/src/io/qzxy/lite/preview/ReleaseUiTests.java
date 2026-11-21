// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Release layout fixtures, isolated from the real CampusKit package and accounts. */
final class ReleaseUiTests {
 static int n;
 static void check(boolean b){n++;if(!b)throw new AssertionError("Release UI "+n);}
 static TextView find(View v,String text,boolean clickable){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText())&&(!clickable||v.isClickable()))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=find(((ViewGroup)v).getChildAt(i),text,clickable);if(t!=null)return t;}return null;}
 static void capture(Instrumentation r,String name)throws Exception{Thread.sleep(600);r.waitForIdleSync();android.graphics.Bitmap b=r.getUiAutomation().takeScreenshot();if(b!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(r.getTargetContext().getExternalFilesDir(null),name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}}
 static void onMain(Instrumentation r,Runnable a){Throwable[] fail={null};r.runOnMainSync(()->{try{a.run();}catch(Throwable e){fail[0]=e;}});if(fail[0]!=null)throw new AssertionError(fail[0]);}
 static int run(Instrumentation r)throws Exception{
  n=0;Context c=r.getTargetContext();java.util.Calendar date=java.util.Calendar.getInstance();int day=(date.get(java.util.Calendar.DAY_OF_WEEK)+5)%7;date.add(java.util.Calendar.DAY_OF_MONTH,-day);
  String monday=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(date.getTime());JSONArray classes=new JSONArray();
  String[] names={"自动控制原理","传热学","工程制图与计算机辅助设计"};
  for(int i=0;i<3;i++)classes.put(new JSONObject().put("name",names[i]).put("room","主教楼 A203").put("teacher","示例教师").put("day",day).put("first",i*2+1).put("last",i*2+2).put("from",1).put("to",16).put("parity",0));
  android.content.SharedPreferences prefs=c.getSharedPreferences("timetable-v1",0);prefs.edit().putString("monday",monday).putString("courses",classes.toString()).commit();
  Activity a=r.startActivitySync(new Intent(c,TimetableActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{capture(r,"release-today");onMain(r,()->{View root=a.getWindow().getDecorView();check(find(root,"自动控制原理",false)!=null);check(find(root,"趣智校园",false)!=null);check(find(root,"账号",true)!=null);check(find(root,"账号登录",false)==null);find(root,"课表",true).performClick();});
   capture(r,"release-week");onMain(r,()->{View root=a.getWindow().getDecorView();check(find(root,"管理",true)!=null);check(find(root,"本周",true)!=null);find(root,"账号",true).performClick();});
   capture(r,"release-accounts");onMain(r,()->check(find(a.getWindow().getDecorView(),"CampusKit 1.0.7 · 第三方校园客户端",false)!=null));
   onMain(r,()->{try{java.lang.reflect.Method method=TimetableActivity.class.getDeclaredMethod("allCourses");method.setAccessible(true);method.invoke(a);}catch(Exception e){throw new RuntimeException(e);}});capture(r,"all-courses");r.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);r.waitForIdleSync();
   final Dialog[] picker={null};final int[] chosen={-1,-1,-1};
   onMain(r,()->picker[0]=CoursePickers.time(a,3,7,7,(weekday,first,last)->{chosen[0]=weekday;chosen[1]=first;chosen[2]=last;}));
   check(picker[0].getWindow().getAttributes().windowAnimations!=0);
   capture(r,"picker-time");onMain(r,()->find(picker[0].getWindow().getDecorView(),"确定",true).performClick());check(chosen[0]==3&&chosen[1]==7&&chosen[2]==7);
   boolean[] original=new boolean[30];original[0]=true;final boolean[][] selected={null};
   onMain(r,()->picker[0]=CoursePickers.weeks(a,original,value->selected[0]=value));capture(r,"picker-weeks");
   onMain(r,()->{find(picker[0].getWindow().getDecorView(),"单周",true).performClick();find(picker[0].getWindow().getDecorView(),"取消",true).performClick();});check(selected[0]==null&&!original[2]);
   onMain(r,()->{picker[0]=CoursePickers.weeks(a,original,value->selected[0]=value);find(picker[0].getWindow().getDecorView(),"双周",true).performClick();find(picker[0].getWindow().getDecorView(),"确定",true).performClick();});check(selected[0][1]&&!selected[0][0]&&selected[0][29]);
   final int[] pickedWeek={0};onMain(r,()->picker[0]=CoursePickers.selectWeek(a,6,value->pickedWeek[0]=value));capture(r,"picker-single-week");
   check(picker[0].getWindow().getAttributes().windowAnimations==a.getResources().getIdentifier("InstantWeekPopup","style",a.getPackageName()));onMain(r,()->{check(find(picker[0].getWindow().getDecorView(),"6",true).isSelected());find(picker[0].getWindow().getDecorView(),"8",true).performClick();});check(pickedWeek[0]==8&&!picker[0].isShowing());
  }finally{r.runOnMainSync(()->a.finish());r.waitForIdleSync();prefs.edit().clear().commit();}
  return n;
 }
}
