// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
public final class LifeWaterActivity extends Activity {
 private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
 private DeviceScreen screen;private String deviceDetails="正在读取设备…";
 private String did,qrId,uid="";private boolean busy,closed,askedLogin;private TextView info,status;private Button start,stop,refresh;private List<Integer> pays=Collections.emptyList();
 // 诊断用：最近一次设备详情原文、最近一次控制请求的路径与回复。只在本机显示/复制，不写入任何文件。
  private JSONObject rawDetail,lastReply;private String lastPath="";
 @Override public void onCreate(Bundle saved){super.onCreate(saved);try{String requested=getIntent().getStringExtra("did");qrId=getIntent().getStringExtra("qrId");if(qrId!=null&&!qrId.matches("[A-Za-z0-9_%+./=~?-]{1,300}"))throw new IllegalArgumentException();if(requested==null&&qrId==null)throw new IllegalArgumentException();if(requested!=null)did=LifeWaterProtocol.id(requested);}catch(Exception e){Toast.makeText(this,"没有可用的设备信息，请重新扫码",Toast.LENGTH_LONG).show();finish();return;}
  screen=new DeviceScreen(this,"慧生活 · 用水");
  refresh=screen.icon("↻","刷新用水状态",()->refresh());screen.icon("ⓘ","设备详情",()->details());
  info=screen.text(screen.card,"正在核对设备…",18);
  start=screen.primary("开启用水",()->confirmStart());stop=screen.primary("结束用水",()->command(false,0));stop.setVisibility(View.GONE);
  status=screen.note("");
  screen.note("使用后请点结束，退出页面不会自动停止。");disable();
 }
 @Override protected void onResume(){super.onResume();if(did!=null||qrId!=null)refresh();}
 private void disable(){screen.loading(busy);start.setEnabled(false);stop.setEnabled(false);refresh.setEnabled(!busy);}
 private void post(Runnable r){runOnUiThread(()->{if(!closed&&!isFinishing())r.run();});}
 private JSONObject session()throws Exception{JSONObject s=new LifeSessionVault(this).load();if(s==null){post(()->{if(!askedLogin){askedLogin=true;startActivity(new Intent(this,LifeLoginActivity.class));}});throw new LifeDeviceFailure("请先登录慧生活");}return s;}
 private JSONObject detail(JSONObject s)throws Exception{return LifeDeviceApi.get(LifeDeviceApi.detailPath(did),s);}
 /**
  * 页面只有两种状态：设备空着就「开启用水」，本账号正在用就「结束用水」。
  * 以前这里还会因为本机留着一条「待核实记录」而两个按钮都不给，只留一个「核实并归档」——
  * 那条记录是上一次被服务器拒绝的开启留下的，设备根本没被占用，于是页面自己把自己锁死了。
  */
 private void refresh(){if(busy)return;busy=true;disable();status.setText("正在读取用水状态…");worker.execute(()->{try{JSONObject s=session();String user=s.getString("uid");if(did==null)did=LifeDeviceProfile.resolvedId(LifeDeviceApi.get(LifeDeviceApi.resolvePath(qrId),s));if(closed)return;JSONObject response=detail(s);rawDetail=response;LifeDeviceProfile p=LifeDeviceProfile.refreshed(response,did);String owner=LifeWaterProtocol.owner(response.getJSONObject("data"));
   List<Integer> options=Collections.emptyList();boolean mine=owner.equals(user);String message;
   if(mine)message="正在使用此设备，用完请点结束。";
   else if(!owner.isEmpty())message="设备当前使用者不是本账号，请用原版查看这笔记录。";
   else{try{options=LifeWaterProtocol.payments(response,did,user);message="设备已就绪，点击开启用水。";}catch(Exception e){message=LifeDeviceFailure.describe(e);}}
   final List<Integer> available=options;final String note=message;final boolean inUse=mine;
   post(()->{busy=false;screen.loading(false);uid=user;pays=available;info.setText(p.name+(p.location.isEmpty()?"":"\n"+p.location));deviceDetails=p.name+"\n"+p.location+"\n"+p.enterprise+"\n"+p.typeLabel()+"\n"+p.networkLabel()+" · "+p.statusLabel();status.setText(note);refresh.setEnabled(true);start.setVisibility(inUse?View.GONE:View.VISIBLE);start.setEnabled(!inUse&&!available.isEmpty());stop.setVisibility(inUse?View.VISIBLE:View.GONE);stop.setEnabled(inUse);});
  }catch(Exception e){post(()->{busy=false;screen.loading(false);disable();status.setText(LifeDeviceFailure.describe(e)+"。未发送控制请求。");});}});}
 private void confirmStart(){if(busy||pays.isEmpty())return;
  new AlertDialog.Builder(this).setTitle("确认开启用水").setNegativeButton("取消",null)
   .setPositiveButton("确认开启",(a,b)->command(true,LifeWaterProtocol.DEFAULT_PAYMENT))
   .setMessage("支付方式：支付宝免密，按设备资费扣费。开启后结束时点击“结束用水”。").show();}
 private void command(boolean opening,int pay){if(busy)return;busy=true;disable();status.setText("正在重新核对设备…");final String expectedUser=uid;
  worker.execute(()->{try{JSONObject s=session();String user=s.getString("uid");if(!user.equals(expectedUser))throw new LifeDeviceFailure("账号已变化，请刷新后再操作");
   JSONObject response=detail(s);
   if(opening){List<Integer> ok=LifeWaterProtocol.payments(response,did,user);
    if(!ok.contains(LifeWaterProtocol.DEFAULT_PAYMENT)){StringBuilder names=new StringBuilder();for(int i=0;i<ok.size();i++){if(i>0)names.append("、");names.append(LifeWaterProtocol.paymentName(ok.get(i)));}
     throw new LifeDeviceFailure("设备未提供支付宝免密（可用："+names+"），本应用固定用它，请在原版使用");}}
   else LifeWaterProtocol.canStop(response,did,user);
   if(closed)return;
   String path=opening?LifeWaterProtocol.startPath(did,pay):LifeWaterProtocol.endPath(did);lastPath=path;lastReply=null;
   JSONObject reply=LifeWaterProtocol.send(path,s,this);lastReply=reply;LifeDeviceFailure.success(reply);
   post(()->{busy=false;screen.loading(false);status.setText("请求已受理，正在核对设备状态；尚未确认结算。");refresh();});
  }catch(Exception e){
   // 被明确拒绝 = 指令没下发，设备状态没变，可以直接重试；其它情况（超时、HTTP 错误）结果未知，得刷新核实。
   if(e instanceof LifeDeviceFailure.Rejected)lastReply=((LifeDeviceFailure.Rejected)e).reply;
   final boolean unclear=!(e instanceof LifeDeviceFailure.Rejected);final String why=LifeDeviceFailure.describe(e);
   // -3 是设备/账户的状态话，不是协议问题：同样的请求再点一次结果一样，不能拿「可以重试」敷衍过去。
   final int rejected=unclear?0:((LifeDeviceFailure.Rejected)e).code;
   final String tail=unclear?"。结果未确认，请刷新核实设备状态。"
    :rejected==-3?"。未下发任何指令：设备不接受这次开启，可能是设备刚被他人占用或支付宝免密代扣未生效，可稍后重试或在原版查看。"
    :"。设备状态没有改变，可以重试。";
   post(()->{busy=false;screen.loading(false);disable();status.setText(why+tail);});}});
 }
 /** 身份字段一律不copy出去：诊断要的是设备结构与参数名，不是账号。 */
 private static final Set<String> SENSITIVE=new HashSet<>(Arrays.asList("token","uid","user","telephone","telphone","tel","phone","logincode","idcard","idcardno","pwd","password","opencode"));
 private void details(){
  AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("设备详情").setMessage(deviceDetails).setNegativeButton("关闭",null);
  if(rawDetail!=null)b.setNeutralButton("诊断…",(d,w)->new AlertDialog.Builder(this).setItems(LifeProbeEntry.items(),(x,which)->{if(which==0)copyDiagnostics();else if(which==1)readState();else LifeProbeEntry.run(this,did,pays.isEmpty()?LifeWaterProtocol.DEFAULT_PAYMENT:pays.get(0),rawDetail);}).show());
  b.show();
 }
 /**
  * 只读地把设备自己的说法取全：允许哪种使用方式、更细的运行状态、有没有未结账单。
  * 这三条都不改设备状态，所以不必站在机器旁边也能跑。
  */
 private void readState(){
  if(busy)return;busy=true;disable();status.setText("正在读取设备状态…");
  worker.execute(()->{
   String text;
   try{Object clean=scrub(LifeDiagnostics.collect(session(),did));text=clean instanceof JSONObject?((JSONObject)clean).toString(2):String.valueOf(clean);}
   catch(Exception e){text="读取未完成："+LifeDeviceFailure.describe(e);}
   if(text.length()>20000)text=text.substring(0,20000)+"…";
   final String report=text;
   post(()->{busy=false;screen.loading(false);refresh.setEnabled(true);status.setText("设备状态见弹窗，点「复制」发我。");
    new AlertDialog.Builder(this).setTitle("设备状态（只读）").setMessage(report).setPositiveButton("复制",(d,w)->{
     ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("慧生活设备状态",report));
     Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show();
    }).setNegativeButton("关闭",null).show();});
  });
 }
 /**
  * 把这次请求和服务器原文交出去。业务码 -1 只说明「它没接受」，服务端不会告诉我们缺什么，
  * 而这半年的两次翻车（登录、开用水）都是靠原始返回定下来的，留着它比继续猜有用。
  */
 private void copyDiagnostics(){
  String text;
  try{JSONObject out=new JSONObject();
   out.put("app",getPackageManager().getPackageInfo(getPackageName(),0).versionName);out.put("did",did==null?"":did);out.put("sent",lastPath);
   out.put("deviceVersionHeader",LifeVersion.DEVICE_VERSION);
   out.put("detail",scrub(rawDetail));out.put("reply",lastReply==null?JSONObject.NULL:scrub(lastReply));text=out.toString(2);
  }catch(Exception e){text="诊断信息生成失败："+LifeDeviceFailure.describe(e);}
  if(text.length()>12000)text=text.substring(0,12000)+"…";
  ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("慧生活用水诊断",text));
  Toast.makeText(this,"已复制，粘贴到聊天里即可（不含账号与凭据）",Toast.LENGTH_LONG).show();
 }
 private static Object scrub(Object value){
  if(value instanceof JSONObject){JSONObject src=(JSONObject)value,out=new JSONObject();Iterator<String> keys=src.keys();
   while(keys.hasNext()){String k=keys.next();if(SENSITIVE.contains(k.toLowerCase(Locale.ROOT)))continue;try{out.put(k,scrub(src.opt(k)));}catch(Exception ignored){}}
   return out;}
  if(value instanceof JSONArray){JSONArray src=(JSONArray)value,out=new JSONArray();for(int i=0;i<src.length();i++)out.put(scrub(src.opt(i)));return out;}
  return value;
 }
 @Override protected void onDestroy(){closed=true;worker.shutdown();super.onDestroy();}
}
