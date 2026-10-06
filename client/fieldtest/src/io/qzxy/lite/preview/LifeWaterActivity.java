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
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle saved){super.onCreate(saved);try{String requested=getIntent().getStringExtra("did");qrId=getIntent().getStringExtra("qrId");if(qrId!=null&&!qrId.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException();if(requested==null&&qrId==null){JSONObject record=new LifeWaterRecord(this).load();if(record!=null)requested=record.getString("did");}if(requested!=null)did=LifeWaterProtocol.id(requested);else if(qrId==null)throw new IllegalArgumentException();}catch(Exception e){Toast.makeText(this,"没有可恢复的用水记录，请先扫码",Toast.LENGTH_LONG).show();finish();return;}
  screen=new DeviceScreen(this,"慧生活 · 用水");
  refresh=screen.icon("↻","刷新用水状态",()->refresh());screen.icon("ⓘ","设备详情",()->new AlertDialog.Builder(this).setTitle("设备详情").setMessage(deviceDetails).setPositiveButton("关闭",null).show());
  info=screen.text(screen.card,"正在核对设备…",18);
  start=screen.primary("开启用水",()->choose());stop=screen.primary("结束用水",()->command(false,0));stop.setVisibility(View.GONE);
  status=screen.note("");
  review=new Button(this);review.setText("核实账单并完成");review.setTextSize(14);review.setTextColor(0xff28745f);review.setBackgroundColor(android.graphics.Color.TRANSPARENT);review.setOnClickListener(v->review());screen.root.addView(review,new LinearLayout.LayoutParams(-1,dp(48)));review.setVisibility(View.GONE);
  screen.note("使用后请点结束，退出页面不会自动停止。");disable();
 }
 @Override protected void onResume(){super.onResume();if(did!=null||qrId!=null)refresh();}
 private TextView label(LinearLayout root,String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff243c32);t.setPadding(0,dp(10),0,dp(10));root.addView(t);return t;}
 private Button button(LinearLayout root,String s,Runnable action){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));return b;}
 private void disable(){start.setEnabled(false);stop.setEnabled(false);review.setEnabled(false);refresh.setEnabled(!busy);}
 private void post(Runnable r){runOnUiThread(()->{if(!closed&&!isFinishing())r.run();});}
 private JSONObject session()throws Exception{JSONObject s=new LifeSessionVault(this).load();if(s==null)throw new LifeDeviceFailure("请先登录慧生活");return s;}
 private JSONObject detail(JSONObject s)throws Exception{return LifeDeviceApi.get(LifeDeviceApi.detailPath(did),s);}
 private boolean matches(JSONObject r,String user)throws Exception{return r!=null&&r.getString("uid").equals(user)&&r.getString("did").equals(did);}
 private void refresh(){if(busy)return;busy=true;disable();status.setText("正在读取用水状态…");worker.execute(()->{try{JSONObject s=session();String user=s.getString("uid");if(did==null)did=LifeDeviceProfile.resolvedId(LifeDeviceApi.get(LifeDeviceApi.resolvePath(qrId),s));if(closed)return;JSONObject r=new LifeWaterRecord(this).load();JSONObject response=detail(s);LifeDeviceProfile p=LifeDeviceProfile.refreshed(response,did);String owner=LifeWaterProtocol.owner(response.getJSONObject("data"));
   List<Integer> options=Collections.emptyList();String message;
   if(r!=null&&!matches(r,user))message="另一个账号或设备有待核实记录，请先回原设备处理。";
   else if(r!=null){String phase=r.getString("phase");message=owner.equals(user)?"正在使用此设备，用完请点结束。":owner.isEmpty()?"设备已无使用者，请核实停水与账单后完成。":"设备当前使用者不是本账号，请在原版核实历史账单。";if(phase.equals("STOP_SENT"))message="已发送停止请求。"+message;}
   else{try{options=LifeWaterProtocol.payments(response,did,user);message="设备已就绪。";}catch(Exception e){message=LifeDeviceFailure.describe(e);}}
   final List<Integer> available=options;final String note=message;boolean canStop=owner.equals(user)&&(r==null||matches(r,user)&&!r.getString("phase").equals("STOP_SENT"));boolean canReview=r!=null&&matches(r,user)&&owner.isEmpty()&&p.status!=null&&p.status==99;
   post(()->{busy=false;uid=user;pending=r;pays=available;info.setText(p.name+(p.location.isEmpty()?"":"\n"+p.location));deviceDetails=p.name+"\n"+p.location+"\n"+p.enterprise+"\n"+p.typeLabel()+"\n"+p.networkLabel()+" · "+p.statusLabel();status.setText(note);refresh.setEnabled(true);start.setVisibility(r==null&&!canStop?View.VISIBLE:View.GONE);start.setEnabled(r==null&&!available.isEmpty());stop.setVisibility(canStop?View.VISIBLE:View.GONE);stop.setEnabled(canStop);review.setVisibility(canReview?View.VISIBLE:View.GONE);review.setEnabled(canReview);});
  }catch(Exception e){post(()->{busy=false;disable();status.setText(LifeDeviceFailure.describe(e)+"。未发送控制请求。");});}});}
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
   LifeWaterProtocol.send(opening?LifeWaterProtocol.startPath(did,pay):LifeWaterProtocol.endPath(did),s);
   post(()->{busy=false;status.setText("请求已受理，正在核对设备状态；尚未确认结算。");refresh();});
  }catch(Exception e){final boolean sent=recorded;post(()->{busy=false;disable();status.setText(LifeDeviceFailure.describe(e)+(sent?"。操作记录已保留，请刷新核实，不会自动重发。":"。未发送控制请求。"));});}});
 }
 private void review(){if(busy)return;new AlertDialog.Builder(this).setTitle("核实本次用水").setMessage("只有已确认现场停水，且在原版核实账单或确认没有产生订单后，才可归档。设备空闲不代表已结算。").setNegativeButton("取消",null).setPositiveButton("已核实，归档",(d,w)->{if(busy)return;busy=true;disable();worker.execute(()->{try{JSONObject s=session();JSONObject response=detail(s);LifeDeviceProfile p=LifeDeviceProfile.refreshed(response,did);LifeWaterRecord store=new LifeWaterRecord(this);if(!matches(store.load(),s.getString("uid"))||!LifeWaterProtocol.owner(response.getJSONObject("data")).isEmpty()||p.status==null||p.status!=99)throw new LifeDeviceFailure("设备状态已变化，记录未归档");store.archive(this);post(()->{busy=false;refresh();});}catch(Exception e){post(()->{busy=false;disable();status.setText(LifeDeviceFailure.describe(e));});}});}).show();}
 @Override protected void onDestroy(){closed=true;worker.shutdown();super.onDestroy();}
}
