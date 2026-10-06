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
   capture(r,"release-accounts");onMain(r,()->check(find(a.getWindow().getDecorView(),"CampusKit 1.0.0 · 第三方校园客户端",false)!=null));
  }finally{r.runOnMainSync(()->a.finish());r.waitForIdleSync();prefs.edit().clear().commit();}
  return n;
 }
}
