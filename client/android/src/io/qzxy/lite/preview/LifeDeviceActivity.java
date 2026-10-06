package io.qzxy.lite.preview;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
public final class LifeDeviceActivity extends Activity {
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private TextView info,status;private Button query,water;private String qr;private LifeDeviceProfile profile;private boolean busy;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);qr=getIntent().getStringExtra("qrId");if(qr==null||!qr.matches("[A-Za-z0-9_-]{1,64}")){finish();return;}
        ScrollView scroll=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(20),dp(18),dp(20),dp(18));form.setBackgroundColor(AppUi.BG);scroll.addView(form);setContentView(scroll);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});}
        button(form,"返回扫码",()->finish());text(form,"慧生活 · 设备",25);info=text(form,"已识别设备码。点击读取设备资料，使用本机保存的慧生活账号查询。",16);
        query=button(form,"读取设备资料",()->query());button(form,"账户信息",()->startActivity(new Intent(this,AccountInfoActivity.class).putExtra("life798",true)));
        water=button(form,"进入独立用水",()->{if(profile!=null)startActivity(new Intent().setClassName(this,"io.qzxy.lite.preview.LifeWaterActivity").putExtra("did",profile.id));});water.setEnabled(false);water.setVisibility(BuildFlags.WATER_CONTROL?View.VISIBLE:View.GONE);
        status=text(form,"先读取设备资料，再进入独立用水。读取资料不会开启用水。",14);
        button(form,"在慧生活原版使用",()->handoff());
    }
    private TextView text(LinearLayout parent,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(AppUi.INK);t.setPadding(0,dp(10),0,dp(10));parent.addView(t);return t;}
    private Button button(LinearLayout parent,String value,Runnable action){Button b=new Button(this);b.setText(value);b.setOnClickListener(v->action.run());parent.addView(b);return b;}
    private void query(){if(busy)return;busy=true;query.setEnabled(false);status.setText("正在读取设备信息…");final LifeDeviceProfile previous=profile;
        worker.execute(()->{String stage="读取本机账号";try{org.json.JSONObject session=new LifeSessionVault(this).load();if(session==null){result(null,"请先在账号登录页登录慧生活，再返回读取设备资料。");return;}
            String id;
            if(previous==null){stage="识别设备编号";id=LifeDeviceProfile.resolvedId(LifeDeviceApi.get(LifeDeviceApi.resolvePath(qr),session));}else id=previous.id;
            if(isDestroyed()||isFinishing())return;
            stage="读取设备详情";LifeDeviceProfile p=LifeDeviceProfile.refreshed(LifeDeviceApi.get(LifeDeviceApi.detailPath(id),session),id);
            result(p,"设备资料已更新，可进入独立用水核对支付方式。");
        }catch(Exception failure){result(null,stage+"失败："+LifeDeviceFailure.describe(failure)+"。\n没有发送开关水指令。");}});
    }
    private void result(LifeDeviceProfile p,String message){runOnUiThread(()->{if(isDestroyed()||isFinishing())return;busy=false;query.setEnabled(true);status.setText(message);water.setEnabled(p!=null);if(p!=null){profile=p;query.setText("刷新设备状态");info.setText(p.name+"\n\n"+p.location+"\n"+p.enterprise+"\n\n"+p.typeLabel()+"\n联网状态："+p.networkLabel()+"\n运行状态："+p.statusLabel());}else if(profile!=null){info.setText("上次读取："+profile.name+"\n当前状态未确认");}});}
    private void handoff(){if(busy)return;new AlertDialog.Builder(this).setTitle("在慧生活原版使用").setMessage("原版接收设备后可能立即开始用水并扣费。请确认你在设备旁，并能现场停止。").setNegativeButton("取消",null).setPositiveButton("确认转交",(d,w)->{try{startActivity(new Intent().setClassName("com.cloudora.android","com.ilife.module.device.view.activity.NfcActivity").putExtra("ID_KEY",qr).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}catch(Exception e){status.setText("无法打开慧生活原版，请检查是否已安装。");}}).show();}
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
