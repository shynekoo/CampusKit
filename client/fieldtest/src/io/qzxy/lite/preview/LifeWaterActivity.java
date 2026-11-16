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
 private String did,qrId,uid="";private boolean busy,closed;private TextView info,status;private Button start,stop,refresh,review;private List<Integer> pays=Collections.emptyList();private JSONObject pending;
 // 诊断用：最近一次设备详情原文、最近一次控制请求的路径与回复。只在本机显示/复制，不写入任何文件。
 private JSONObject rawDetail,lastReply;private String lastPath="";
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle saved){super.onCreate(saved);try{String requested=getIntent().getStringExtra("did");qrId=getIntent().getStringExtra("qrId");if(qrId!=null&&!qrId.matches("[A-Za-z0-9_%+./=~?-]{1,300}"))throw new IllegalArgumentException();if(requested==null&&qrId==null){JSONObject record=new LifeWaterRecord(this).load();if(record!=null)requested=record.getString("did");}if(requested!=null)did=LifeWaterProtocol.id(requested);else if(qrId==null)throw new IllegalArgumentException();}catch(Exception e){Toast.makeText(this,"没有可恢复的用水记录，请先扫码",Toast.LENGTH_LONG).show();finish();return;}
  screen=new DeviceScreen(this,"慧生活 · 用水");
  refresh=screen.icon("↻","刷新用水状态",()->refresh());screen.icon("ⓘ","设备详情",()->details());
  info=screen.text(screen.card,"正在核对设备…",18);
  start=screen.primary("开启用水",()->choose());stop=screen.primary("结束用水",()->command(false,0));stop.setVisibility(View.GONE);
  status=screen.note("");
  review=new Button(this);review.setText("核实并归档");review.setTextSize(14);review.setTextColor(0xff28745f);review.setBackgroundColor(android.graphics.Color.TRANSPARENT);review.setOnClickListener(v->review());screen.root.addView(review,new LinearLayout.LayoutParams(-1,dp(48)));review.setVisibility(View.GONE);
  screen.note("使用后请点结束，退出页面不会自动停止。");disable();
 }
 @Override protected void onResume(){super.onResume();if(did!=null||qrId!=null)refresh();}
 private TextView label(LinearLayout root,String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff243c32);t.setPadding(0,dp(10),0,dp(10));root.addView(t);return t;}
 private Button button(LinearLayout root,String s,Runnable action){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));return b;}
 private void disable(){screen.loading(busy);start.setEnabled(false);stop.setEnabled(false);review.setEnabled(false);refresh.setEnabled(!busy);}
 private void post(Runnable r){runOnUiThread(()->{if(!closed&&!isFinishing())r.run();});}
 private JSONObject session()throws Exception{JSONObject s=new LifeSessionVault(this).load();if(s==null)throw new LifeDeviceFailure("请先登录慧生活");return s;}
 private JSONObject detail(JSONObject s)throws Exception{return LifeDeviceApi.get(LifeDeviceApi.detailPath(did),s);}
 private boolean matches(JSONObject r,String user)throws Exception{return r!=null&&r.getString("uid").equals(user)&&r.getString("did").equals(did);}
 private void refresh(){if(busy)return;busy=true;disable();status.setText("正在读取用水状态…");worker.execute(()->{try{JSONObject s=session();String user=s.getString("uid");if(did==null)did=LifeDeviceProfile.resolvedId(LifeDeviceApi.get(LifeDeviceApi.resolvePath(qrId),s));if(closed)return;JSONObject r=new LifeWaterRecord(this).load();JSONObject response=detail(s);rawDetail=response;LifeDeviceProfile p=LifeDeviceProfile.refreshed(response,did);String owner=LifeWaterProtocol.owner(response.getJSONObject("data"));
   List<Integer> options=Collections.emptyList();String message;
   if(r!=null&&!matches(r,user))message="另一个账号或设备有待核实记录，请先回原设备处理。";
   else if(r!=null){String phase=r.getString("phase");message=owner.equals(user)?"正在使用此设备，用完请点结束。":owner.isEmpty()?"上次开启没有完成：设备现在无人使用。确认没有产生账单后归档这条记录，就能重新开启。":"设备当前使用者不是本账号，请在原版核实历史账单。";if(phase.equals("STOP_SENT"))message="已发送停止请求。"+message;}
   else{try{options=LifeWaterProtocol.payments(response,did,user);message="设备已就绪。";}catch(Exception e){message=LifeDeviceFailure.describe(e);}}
   final List<Integer> available=options;final String note=message;boolean canStop=owner.equals(user)&&(r==null||matches(r,user)&&!r.getString("phase").equals("STOP_SENT"));boolean canReview=r!=null&&matches(r,user)&&owner.isEmpty()&&p.status!=null&&p.status==99;
   post(()->{busy=false;screen.loading(false);uid=user;pending=r;pays=available;info.setText(p.name+(p.location.isEmpty()?"":"\n"+p.location));deviceDetails=p.name+"\n"+p.location+"\n"+p.enterprise+"\n"+p.typeLabel()+"\n"+p.networkLabel()+" · "+p.statusLabel();status.setText(note);refresh.setEnabled(true);start.setVisibility(r==null&&!canStop?View.VISIBLE:View.GONE);start.setEnabled(r==null&&!available.isEmpty());stop.setVisibility(canStop?View.VISIBLE:View.GONE);stop.setEnabled(canStop);review.setVisibility(canReview?View.VISIBLE:View.GONE);review.setEnabled(canReview);});
  }catch(Exception e){post(()->{busy=false;screen.loading(false);disable();status.setText(LifeDeviceFailure.describe(e)+"。未发送控制请求。");});}});}
 private void choose(){if(busy||pays.isEmpty())return;final List<Integer> options=new ArrayList<>(pays);String[] names=new String[options.size()];for(int i=0;i<names.length;i++)names[i]=options.get(i)==91?"商家钱包":"余额";
  final int[] selected={0};
  AlertDialog.Builder confirm=new AlertDialog.Builder(this).setTitle("确认开启用水").setNegativeButton("取消",null).setPositiveButton("确认开启",(a,b)->command(true,options.get(selected[0])));
  if(options.size()==1)confirm.setMessage("支付方式："+names[0]+"。开启后按设备资费扣费，结束时点击“结束用水”。");
  else {TextView note=new TextView(this);note.setText("选择支付方式，确认后按设备资费扣费。结束时点击“结束用水”。");note.setPadding(dp(24),dp(12),dp(24),dp(8));confirm.setView(note).setSingleChoiceItems(names,0,(d,which)->selected[0]=which);}
  confirm.show();}
 private void command(boolean opening,int pay){if(busy)return;busy=true;disable();status.setText("正在重新核对设备…");final String expectedUser=uid;
  worker.execute(()->{boolean recorded=false;try{JSONObject s=session();String user=s.getString("uid");if(!user.equals(expectedUser))throw new LifeDeviceFailure("账号已变化，请刷新后再操作");LifeWaterRecord store=new LifeWaterRecord(this);JSONObject old=store.load();JSONObject response=detail(s);
   if(opening){if(old!=null)throw new LifeDeviceFailure("仍有待核实记录，未重复开启");if(!LifeWaterProtocol.payments(response,did,user).contains(pay))throw new LifeDeviceFailure("支付方式已变化，请重新核对");}
   else{if(old!=null&&(!matches(old,user)||old.getString("phase").equals("STOP_SENT")))throw new LifeDeviceFailure("停止结果待核实或记录不匹配，未重复请求");LifeWaterProtocol.canStop(response,did,user);}
   if(closed)return;
   LifeWaterRecord.claim(this,user,did,opening);recorded=true;
   String path=opening?LifeWaterProtocol.startPath(did,pay):LifeWaterProtocol.endPath(did);lastPath=path;lastReply=null;
   JSONObject reply=LifeWaterProtocol.send(path,s);lastReply=reply;LifeDeviceFailure.success(reply);
   post(()->{busy=false;screen.loading(false);status.setText("请求已受理，正在核对设备状态；尚未确认结算。");refresh();});
  }catch(Exception e){
   // 只有「参数没通过校验（-1）」能证明指令根本没下发：服务器连业务逻辑都没走到。
   // 这种拒绝留在本机记录里毫无意义，它只会让下次进页面被自己挡住——用户就是这么被卡住的。
   // 其它负数码、超时、HTTP 错误都可能是「已经发出去但结果未知」，记录必须留着。
   final boolean released=recorded&&e instanceof LifeDeviceFailure.Rejected&&((LifeDeviceFailure.Rejected)e).code==-1;
   if(released){try{LifeWaterRecord.discard(this);}catch(Exception ignored){}}
   final boolean sent=recorded&&!released;final String why=LifeDeviceFailure.describe(e);
   post(()->{busy=false;screen.loading(false);disable();
    if(released){Toast.makeText(this,why+"。记录已撤销，重新读取即可开启。",Toast.LENGTH_LONG).show();refresh();}
    else status.setText(why+(sent?"。操作记录已保留，请刷新核实，不会自动重发。":"。未发送控制请求。"));});}});
 }
 /** 身份字段一律不copy出去：诊断要的是设备结构与参数名，不是账号。 */
 private static final Set<String> SENSITIVE=new HashSet<>(Arrays.asList("token","uid","user","telephone","telphone","tel","phone","logincode","idcard","idcardno","pwd","password","opencode"));
 private void details(){AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("设备详情").setMessage(deviceDetails).setNegativeButton("关闭",null);if(rawDetail!=null)b.setNeutralButton("复制诊断信息",(d,w)->copyDiagnostics());b.show();}
 /**
  * 把这次请求和服务器原文交出去。业务码 -1 只说明「参数不对」，服务端不会告诉我们缺哪一个，
  * 而设备详情里就带着它期望的那套设置；复制下来比继续猜有用。
  */
 private void copyDiagnostics(){
  String text;
  try{JSONObject out=new JSONObject();
   out.put("app",getPackageManager().getPackageInfo(getPackageName(),0).versionName);out.put("did",did==null?"":did);out.put("sent",lastPath);
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
 private void review(){if(busy)return;new AlertDialog.Builder(this).setTitle("核实本次用水").setMessage("只有已确认现场停水，且在原版核实账单或确认没有产生订单后，才可归档。设备空闲不代表已结算。").setNegativeButton("取消",null).setPositiveButton("已核实，归档",(d,w)->{if(busy)return;busy=true;disable();worker.execute(()->{try{JSONObject s=session();JSONObject response=detail(s);LifeDeviceProfile p=LifeDeviceProfile.refreshed(response,did);LifeWaterRecord store=new LifeWaterRecord(this);if(!matches(store.load(),s.getString("uid"))||!LifeWaterProtocol.owner(response.getJSONObject("data")).isEmpty()||p.status==null||p.status!=99)throw new LifeDeviceFailure("设备状态已变化，记录未归档");store.archive(this);post(()->{busy=false;screen.loading(false);refresh();});}catch(Exception e){post(()->{busy=false;screen.loading(false);disable();status.setText(LifeDeviceFailure.describe(e));});}});}).show();}
 @Override protected void onDestroy(){closed=true;worker.shutdown();super.onDestroy();}
}
