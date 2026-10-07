package io.qzxy.lite.preview;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import io.qzxy.lite.core.*;

/** Explicit handoff. Returning to the foreground rechecks the saved account. */
public final class QzxyAlipayActivity extends Activity {
 private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
 private DeviceQr qr;private DeviceLookup device;private DeviceScreen screen;private TextView info,status;
 private Button open,refresh,details;private boolean busy,visible,closed,needsLogin;
 @Override public void onCreate(Bundle state){super.onCreate(state);try{qr=DeviceQr.parse(getIntent().getStringExtra("qr"));}catch(Exception e){finish();return;}
  screen=new DeviceScreen(this,"趣智校园");refresh=screen.icon("↻","刷新设备状态",this::lookup);details=screen.icon("ⓘ","设备详情",this::showDetails);details.setEnabled(false);
  info=screen.text(screen.card,"正在读取设备…",18);
  open=screen.primary("开启使用",()->{if(needsLogin)startActivity(new Intent(this,AccountActivity.class).putExtra("returnToAccountInfo",true));else handoff();});open.setEnabled(false);
  status=screen.note("正在核对设备与登录状态…");screen.note("在支付宝完成开启、结束与结算。");
 }
 private void post(Runnable action){runOnUiThread(()->{if(!closed&&!isFinishing())action.run();});}
 private void working(boolean value){busy=value;screen.loading(value);refresh.setEnabled(!value);details.setEnabled(!value&&device!=null);open.setEnabled(!value&&(needsLogin||device!=null&&device.alipayReady()));}
 private AccountSession account()throws Exception{AccountSession s=new AccountVault(this).load();if(s==null)throw new HttpForm.SessionExpired();return s;}
 private void failure(Exception e,AccountSession checked){
  if(e instanceof HttpForm.SessionExpired){boolean cleared=false;try{cleared=AccountVault.clearExpired(this,checked);}catch(Exception ignored){}final boolean removed=cleared;
   post(()->{device=null;needsLogin=removed;open.setText(removed?"重新登录":"开启使用");working(false);status.setText(removed?"登录已失效，请重新登录。":"账户状态已变化，请点击右上角刷新。");});
  }else post(()->{working(false);status.setText(failureMessage(e));});
 }
 static String failureMessage(Exception e){
  if(e instanceof java.net.SocketTimeoutException)return "请求超时，请点击右上角刷新重试。";
  if(e instanceof java.net.UnknownHostException||e instanceof java.net.ConnectException)return "无法连接趣智校园服务器，请检查网络后刷新。";
  if(e instanceof javax.net.ssl.SSLException)return "安全连接失败，请检查手机时间和网络后刷新。";
  if(e instanceof org.json.JSONException)return "设备数据格式异常，请稍后刷新重试。";
  return "设备资料暂时读取失败，请点击右上角刷新；本机登录已保留。";
 }
 private void lookup(){if(busy||qr==null)return;device=null;needsLogin=false;working(true);status.setText("正在核对设备与登录状态…");worker.execute(()->{AccountSession checked=null;try{checked=account();DeviceLookup d=SmsAccountApi.device(checked,qr);post(()->{device=d;info.setText(summary(d));screen.title.setText(d.profile.dsbTypeId()==7?"趣智校园 · 吹风机":"趣智校园 · 用水");open.setText("开启使用");working(false);status.setText(d.alipayReady()?"设备已就绪，点击后进入支付宝。":"设备暂不可用，可在右上角查看详情。");});}catch(Exception e){failure(e,checked);}});}
 static String summary(DeviceLookup d){DeviceProfile p=d.profile;String name=p.deviceName().isEmpty()?"校园设备":p.deviceName();String fee=p.chargeMode()==17?"按使用量计费":p.chargeMode()==0?"按使用时长计费":"资费以设备页面为准";return name+(p.location().isEmpty()?"":"\n"+p.location())+"\n\n"+fee;}
 static String detailText(DeviceLookup d){DeviceProfile p=d.profile;return summary(d)+"\n校园："+(p.projectName().isEmpty()?"当前校园":p.projectName())+"\n使用状态："+(p.usable()?"允许使用":"暂停使用")+"\n联网状态："+(p.online()==1?"在线":p.online()==0?"离线":"未提供")+"\n"+(d.alipayReady()?"设备核对通过":"设备通道或账户资料未通过校验");}
 private void showDetails(){if(device!=null)new AlertDialog.Builder(this).setTitle("设备详情").setMessage(detailText(device)).setPositiveButton("关闭",null).show();}
 private void handoff(){if(!BuildFlags.WATER_CONTROL||busy||device==null||!device.alipayReady())return;working(true);status.setText("正在打开支付宝…");worker.execute(()->{AccountSession checked=null;try{checked=account();DeviceLookup fresh=SmsAccountApi.device(checked,qr);String uri=SmsAccountApi.alipayWaterLink(checked,fresh);post(()->{working(false);if(!visible)return;try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(uri)).setPackage("com.eg.android.AlipayGphone"));status.setText("已打开支付宝，请在支付宝结束使用。");}catch(ActivityNotFoundException|SecurityException e){status.setText("无法打开支付宝，请确认已经安装。");}});}catch(Exception e){failure(e,checked);}});}
 @Override protected void onResume(){super.onResume();visible=true;if(screen!=null)lookup();}
 @Override protected void onPause(){visible=false;super.onPause();}
 @Override protected void onDestroy(){closed=true;worker.shutdown();super.onDestroy();}
}
