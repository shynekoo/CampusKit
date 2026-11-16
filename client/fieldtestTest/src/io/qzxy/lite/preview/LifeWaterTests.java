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
  // 3.1.10 起开启走 dev/mode/start。路径与参数顺序照抄被服务端接受的那一条：同样的凭据、
  // 同样的 VersionCode，dev/start 只回「请升级最新版APP」，这条却回了 -3（设备状态话），
  // 说明版本协商已过。mode 取设备自己的 gene.mode，不自己编。
  check(LifeWaterProtocol.startPath("test",91,0).equals("/api/v1/dev/mode/start?did=test&mode=0&ptype=91&args=&rcp=false&cnt=1&upgrade=true"));
  check(LifeWaterProtocol.startPath("test",92,3).contains("mode=3&ptype=92"));
  check(LifeWaterProtocol.endPath("test").equals("/api/v1/dev/end?did=test&rcp=false"));
  for(String id:new String[]{"x&cmd=start","","x?y"}){rejected=false;try{LifeWaterProtocol.startPath(id,91,0);}catch(Exception e){rejected=true;}check(rejected);}
  rejected=false;try{LifeWaterProtocol.startPath("test",21,0);}catch(Exception e){rejected=true;}check(rejected);
  rejected=false;try{LifeWaterProtocol.startPath("test",91,-1);}catch(Exception e){rejected=true;}check(rejected);
  rejected=false;try{LifeWaterProtocol.startPath("test",91,1000);}catch(Exception e){rejected=true;}check(rejected);
  check(LifeWaterProtocol.mode(reply)==0);
  dev.getJSONObject("gene").put("mode",2);check(LifeWaterProtocol.mode(reply)==2);
  dev.getJSONObject("gene").put("mode",-1);rejected=false;try{LifeWaterProtocol.mode(reply);}catch(Exception e){rejected=true;}check(rejected);
  dev.getJSONObject("gene").remove("mode");check(LifeWaterProtocol.mode(reply)==0);
  // 余额只用来解释 -3，不用来拦人：没给时是 -1，0 时照发（后付费、赠送额度的规则我们看不全）。
  check(LifeWaterProtocol.balance(reply)==-1);
  data.put("wallet",new JSONObject().put("total",0));check(LifeWaterProtocol.balance(reply)==0);
  data.put("wallet",new JSONObject().put("total",1250));check(LifeWaterProtocol.balance(reply)==1250);
  android.content.Context c=runner.getTargetContext();
  // 官方 3.1.10 的 versionCode 就是 31010；把它原样报上去，dev/start 仍然回「请升级最新版APP」，
  // 所以那道门不是「版本号小于某个数」——2.3.5 抬到 50000 也是同一句。这里只保留判据本身：
  // 「请升级」是协议墙，其它负数（-1 参数错、-99 过期）是业务/凭据问题，两者不能混为一谈。
  check(LifeVersion.CODE==31010);
  check("1,1".equals(LifeVersion.APPLICATION_TYPE));
  check(LifeVersion.upgradeRequired(new JSONObject().put("code",-1).put("msg","请升级最新版APP")));
  check(!LifeVersion.upgradeRequired(new JSONObject().put("code",-1).put("msg","请求参数错误")));
  check(!LifeVersion.upgradeRequired(new JSONObject().put("code",-99).put("msg","登录状态已过期")));
  check(!LifeVersion.upgradeRequired(new JSONObject().put("code",0)));
  check(!LifeVersion.upgradeRequired(null));
  rejected=false;
  try{LifeDeviceFailure.success(new JSONObject().put("code",-1).put("msg","请求参数错误"));}
  catch(LifeDeviceFailure e){rejected=e instanceof LifeDeviceFailure.Rejected&&((LifeDeviceFailure.Rejected)e).code==-1&&e.getMessage().contains("-1")&&e.getMessage().contains("请求参数错误");}
  check(rejected);
  rejected=false;
  try{LifeDeviceFailure.success(new JSONObject().put("code",-99));}
  catch(LifeDeviceFailure e){rejected=e instanceof LifeDeviceFailure.Rejected&&((LifeDeviceFailure.Rejected)e).code==-99&&e.getMessage().contains("-99");}
  check(rejected);
  // Scan-to-water must accept a QR id before a device id has been resolved. With no account,
  // it stays on the page, offers 开启用水 (disabled until login) and asks for login — never a
  // "核实并归档" interstitial, which only ever existed to clear a journal the page wrote itself.
  new LifeSessionVault(c).clear();
  android.app.Activity screen=runner.startActivitySync(new android.content.Intent(c,LifeWaterActivity.class).putExtra("qrId","123456789012345").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  try {
   java.lang.reflect.Field executor=LifeWaterActivity.class.getDeclaredField("worker");executor.setAccessible(true);
   ((java.util.concurrent.ExecutorService)executor.get(screen)).submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);runner.waitForIdleSync();
   check(!screen.isFinishing());
   rejected=false;try{LifeWaterActivity.class.getDeclaredField("review");}catch(NoSuchFieldException gone){rejected=true;}check(rejected);
   java.lang.reflect.Field qrField=LifeWaterActivity.class.getDeclaredField("qrId");qrField.setAccessible(true);check("123456789012345".equals(qrField.get(screen)));
   final Throwable[] error={null};runner.runOnMainSync(()->{try{
    java.lang.reflect.Field startField=LifeWaterActivity.class.getDeclaredField("start");startField.setAccessible(true);check(!((android.widget.Button)startField.get(screen)).isEnabled());check(((android.widget.Button)startField.get(screen)).getVisibility()==android.view.View.VISIBLE);
    java.lang.reflect.Field stopField=LifeWaterActivity.class.getDeclaredField("stop");stopField.setAccessible(true);check(((android.widget.Button)stopField.get(screen)).getVisibility()==android.view.View.GONE);
    java.lang.reflect.Field statusField=LifeWaterActivity.class.getDeclaredField("status");statusField.setAccessible(true);check(((android.widget.TextView)statusField.get(screen)).getText().toString().contains("请先登录"));
   }catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);
  }finally{runner.runOnMainSync(()->screen.finish());runner.waitForIdleSync();}
  return n;
 }
}
