package io.qzxy.lite.preview;
import android.app.*;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import io.qzxy.lite.core.AccountSession;
import org.json.JSONObject;

/** Synthetic account and device only, in the network-disabled instrumentation package. */
final class DeviceUiTests {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("Device UI/auth "+n);}
 static void settle(Instrumentation r,Activity a)throws Exception{java.lang.reflect.Field f=QzxyAlipayActivity.class.getDeclaredField("worker");f.setAccessible(true);((java.util.concurrent.ExecutorService)f.get(a)).submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);r.waitForIdleSync();}
 static TextView find(View v,String value){if(v instanceof TextView&&(value.contentEquals(((TextView)v).getText())||value.equals(v.getContentDescription())))return (TextView)v;if(v instanceof ViewGroup){for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=find(((ViewGroup)v).getChildAt(i),value);if(t!=null)return t;}}return null;}
 static void main(Instrumentation r,Runnable action){final Throwable[] e={null};r.runOnMainSync(()->{try{action.run();}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new AssertionError(e[0]);}
 static int run(Instrumentation r)throws Exception{
  n=0;
  check(HttpForm.rejection(new JSONObject().put("message","失效"),8) instanceof HttpForm.SessionExpired);
  check(HttpForm.rejection(new JSONObject().put("message","登录已失效，请重新登录"),10000) instanceof HttpForm.SessionExpired);
  check(!(HttpForm.rejection(new JSONObject().put("message","当前项目不支持该应用，请使用支付宝小程序"),10000) instanceof HttpForm.SessionExpired));
  check(!(HttpForm.rejection(new JSONObject().put("message","网络通讯慢"),1) instanceof HttpForm.SessionExpired));
  AccountSession old=new AccountSession("42","OLD_TEST_TOKEN","9"),fresh=new AccountSession("42","NEW_TEST_TOKEN","9");AccountVault vault=new AccountVault(r.getTargetContext());vault.save(fresh);
  check(!AccountVault.clearExpired(r.getTargetContext(),old));check(vault.load().loginCode().equals(fresh.loginCode()));check(AccountVault.clearExpired(r.getTargetContext(),fresh));check(vault.load()==null);
  JSONObject user=new JSONObject("{\"userId\":42,\"telephone\":\"13800000000\",\"userAccount\":{\"accountId\":7,\"projectId\":9}}");
  JSONObject device=new JSONObject("{\"projectId\":9,\"deviceId\":8,\"deviceName\":\"测试浴室设备\",\"bigTypeId\":4,\"isUse\":1,\"isOnline\":1,\"chargeMode\":17,\"withholdMoney\":\"10.0\",\"snCode\":\"SYNTHETIC233\"}");
  DeviceLookup model=DeviceDetails.lookup(device,"9",AccountProfile.parse(user,"42"));check(!QzxyAlipayActivity.summary(model).contains("预扣"));check(!QzxyAlipayActivity.detailText(model).contains("10.0"));
  final int[] mode={0};HttpForm.testTransport=(path,form,post)->{if(post)throw new AssertionError("Unexpected mutation");if(mode[0]==1)return new JSONObject().put("errorCode",8);if(mode[0]==2)throw new java.net.SocketTimeoutException();if(!path.equals("user/info")&&!path.equals("device/info/mac"))throw new AssertionError("Unexpected endpoint");return new JSONObject().put("errorCode",0).put("data",path.equals("user/info")?user:device);};
  vault.save(fresh);Activity activity=null;
  try{activity=r.startActivitySync(new Intent(r.getTargetContext(),QzxyAlipayActivity.class).putExtra("qr","KLCXKJ-Water,A,C0123456789A").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle(r,activity);final Activity a=activity;
   main(r,()->{View root=a.getWindow().getDecorView();check(find(root,"开启使用").isEnabled());check(find(root,"账户信息")==null);check(find(root,"查询历史用水记录")==null);check(find(root,"刷新设备状态")!=null);check(find(root,"设备详情")!=null);});
   Thread.sleep(900);r.waitForIdleSync();
   main(r,()->{View root=a.getWindow().getDecorView();check(root.getWidth()>0&&find(root,"开启使用").getWidth()>0);});
   android.graphics.Bitmap screenshot=r.getUiAutomation().takeScreenshot();if(screenshot!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(r.getTargetContext().getExternalFilesDir(null),"device-ui.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();}
   mode[0]=2;main(r,()->find(a.getWindow().getDecorView(),"刷新设备状态").performClick());settle(r,a);check(vault.load()!=null);
   mode[0]=1;main(r,()->find(a.getWindow().getDecorView(),"刷新设备状态").performClick());settle(r,a);check(vault.load()==null);
   main(r,()->{check(find(a.getWindow().getDecorView(),"重新登录").isEnabled());check(find(a.getWindow().getDecorView(),"登录已失效，请重新登录。")!=null);});
  }finally{if(activity!=null){final Activity a=activity;r.runOnMainSync(()->a.finish());r.waitForIdleSync();}HttpForm.testTransport=null;vault.clear();}
  return n;
 }
}
