package io.qzxy.lite.preview;
import org.json.*;
public final class LifeWaterTests {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("Life water "+n);}
 public static int run(android.app.Instrumentation runner)throws Exception{
  n=0;JSONObject dev=new JSONObject().put("id","test").put("name","测试设备").put("status",1).put("bm",new JSONObject().put("dtype",6)).put("gene",new JSONObject().put("status",99)).put("ep",new JSONObject().put("setting",new JSONObject().put("payPwd",0)));
  JSONObject data=new JSONObject().put("device",dev).put("user",JSONObject.NULL).put("payItems",new JSONArray().put(new JSONObject().put("type",91)).put(new JSONObject().put("type",21)));
  JSONObject reply=new JSONObject().put("code",0).put("data",data);
  check(LifeWaterProtocol.payments(reply,"test","user").equals(java.util.Collections.singletonList(91)));
  for(int state:new int[]{0,1,10,20,30,98}){dev.getJSONObject("gene").put("status",state);boolean rejected=false;try{LifeWaterProtocol.payments(reply,"test","user");}catch(Exception e){rejected=true;}check(rejected);}dev.getJSONObject("gene").put("status",99);
  dev.put("status",0);boolean rejected=false;try{LifeWaterProtocol.payments(reply,"test","user");}catch(Exception e){rejected=true;}check(rejected);dev.put("status",1);
  data.put("user",new JSONObject().put("id","other"));rejected=false;try{LifeWaterProtocol.payments(reply,"test","user");}catch(Exception e){rejected=true;}check(rejected);
  rejected=false;try{LifeWaterProtocol.canStop(reply,"test","user");}catch(Exception e){rejected=true;}check(rejected);data.put("user",new JSONObject().put("id","user"));LifeWaterProtocol.canStop(reply,"test","user");check(true);data.put("user",JSONObject.NULL);
  data.put("prepay",true);rejected=false;try{LifeWaterProtocol.payments(reply,"test","user");}catch(Exception e){rejected=true;}check(rejected);data.put("prepay",false);
  data.remove("user");check(LifeWaterProtocol.payments(reply,"test","user").contains(91));data.put("user",JSONObject.NULL);
  dev.getJSONObject("ep").getJSONObject("setting").put("payPwd",1);rejected=false;try{LifeWaterProtocol.payments(reply,"test","user");}catch(Exception e){rejected=true;}check(rejected);
  check(LifeWaterProtocol.startPath("test",91).contains("ptype=91"));check(LifeWaterProtocol.endPath("test").equals("/api/v1/dev/end?did=test&rcp=false"));
  for(String id:new String[]{"x&cmd=start","","x?y"}){rejected=false;try{LifeWaterProtocol.startPath(id,91);}catch(Exception e){rejected=true;}check(rejected);}
  rejected=false;try{LifeWaterProtocol.startPath("test",21);}catch(Exception e){rejected=true;}check(rejected);
  android.content.Context c=runner.getTargetContext();java.io.File f=new java.io.File(c.getNoBackupFilesDir(),"life-water-pending.json");f.delete();
  LifeWaterRecord.claim(c,"user","test",true);check(new LifeWaterRecord(c).load().getString("phase").equals("START_SENT"));
  rejected=false;try{LifeWaterRecord.claim(c,"user","test",true);}catch(Exception e){rejected=true;}check(rejected);
  rejected=false;try{LifeWaterRecord.claim(c,"other","test",false);}catch(Exception e){rejected=true;}check(rejected);
  LifeWaterRecord.claim(c,"user","test",false);check(new LifeWaterRecord(c).load().getString("phase").equals("STOP_SENT"));
  rejected=false;try{LifeWaterRecord.claim(c,"user","test",false);}catch(Exception e){rejected=true;}check(rejected);
  new LifeWaterRecord(c).archive(c);check(new LifeWaterRecord(c).load()==null);f.delete();new java.io.File(c.getNoBackupFilesDir(),"life-water-last-reviewed.json").delete();
  // A record the server provably refused (业务码 -1, the request never reached its business logic)
  // must be droppable; leaving it behind is what strands the page on "上次开启没有完成".
  LifeWaterRecord.claim(c,"user","test",true);LifeWaterRecord.discard(c);check(new LifeWaterRecord(c).load()==null);
  rejected=false;
  try{LifeDeviceFailure.success(new JSONObject().put("code",-1).put("msg","请求参数错误"));}
  catch(LifeDeviceFailure e){rejected=e instanceof LifeDeviceFailure.Rejected&&((LifeDeviceFailure.Rejected)e).code==-1&&e.getMessage().contains("-1")&&e.getMessage().contains("请求参数错误");}
  check(rejected);
  rejected=false;
  try{LifeDeviceFailure.success(new JSONObject().put("code",-99));}
  catch(LifeDeviceFailure e){rejected=e instanceof LifeDeviceFailure.Rejected&&((LifeDeviceFailure.Rejected)e).code==-99&&e.getMessage().contains("-99");}
  check(rejected);
  // Scan-to-water must accept a QR id before a device id has been resolved. With no account,
  // it stays on the page and asks for login; it must not create a control journal or enable start.
  new LifeSessionVault(c).clear();
  android.app.Activity screen=runner.startActivitySync(new android.content.Intent(c,LifeWaterActivity.class).putExtra("qrId","123456789012345").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  try {
   java.lang.reflect.Field executor=LifeWaterActivity.class.getDeclaredField("worker");executor.setAccessible(true);
   ((java.util.concurrent.ExecutorService)executor.get(screen)).submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);runner.waitForIdleSync();
   check(!screen.isFinishing());check(new LifeWaterRecord(c).load()==null);
   java.lang.reflect.Field qrField=LifeWaterActivity.class.getDeclaredField("qrId");qrField.setAccessible(true);check("123456789012345".equals(qrField.get(screen)));
   final Throwable[] error={null};runner.runOnMainSync(()->{try{
    java.lang.reflect.Field startField=LifeWaterActivity.class.getDeclaredField("start");startField.setAccessible(true);check(!((android.widget.Button)startField.get(screen)).isEnabled());
    java.lang.reflect.Field stopField=LifeWaterActivity.class.getDeclaredField("stop");stopField.setAccessible(true);check(((android.widget.Button)stopField.get(screen)).getVisibility()==android.view.View.GONE);
    java.lang.reflect.Field statusField=LifeWaterActivity.class.getDeclaredField("status");statusField.setAccessible(true);check(((android.widget.TextView)statusField.get(screen)).getText().toString().contains("请先登录"));
   }catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);
  }finally{runner.runOnMainSync(()->screen.finish());runner.waitForIdleSync();}
  return n;
 }
}
