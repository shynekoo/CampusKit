package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import io.qzxy.lite.core.AccountSession;
import org.json.JSONObject;

/** Account overview; SMS fields are shown only in the separate login screen. */
public final class AccountInfoActivity extends Activity {
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean life,busy; private int generation;
    private TextView identity,state;private Button login,refresh,logout;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);life=getIntent().getBooleanExtra("life798",false);
        DeviceScreen screen=new DeviceScreen(this,life?"慧生活798":"趣智校园");
        refresh=screen.icon("↻","刷新账户",()->load());
        identity=screen.text(screen.card,"正在读取账号…",18);state=screen.text(screen.card,"",14);state.setTextColor(AppUi.MUTED);
        login=screen.primary("登录",()->startActivity(new Intent(this,life?LifeLoginActivity.class:AccountActivity.class).putExtra("returnToAccountInfo",true)));
        logout=button(screen.root,"退出登录",()->signOut());AppUi.press(logout,AppUi.SURFACE,10);
        if(life&&BuildFlags.WATER_CONTROL){Button recover=button(screen.root,"未完成的用水",()->startActivity(new Intent().setClassName(this,"io.qzxy.lite.preview.LifeWaterActivity")));AppUi.press(recover,AppUi.SURFACE,10);}
        screen.note("登录信息加密保存在此设备。");login.setVisibility(View.GONE);logout.setVisibility(View.GONE);
    }
    private TextView label(LinearLayout root,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(AppUi.INK);v.setPadding(0,dp(10),0,dp(10));root.addView(v);return v;}
    private Button button(LinearLayout root,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(AppUi.ACCENT);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.topMargin=dp(12);root.addView(b,lp);return b;}
    @Override protected void onResume(){super.onResume();load();}
    private void busy(boolean value){busy=value;login.setEnabled(!value);refresh.setEnabled(!value);logout.setEnabled(!value);}
    private void present(boolean signed,String info,String message){login.setVisibility(signed?View.GONE:View.VISIBLE);logout.setVisibility(signed?View.VISIBLE:View.GONE);refresh.setVisibility(signed?View.VISIBLE:View.GONE);identity.setText(info);state.setText(message);}
    private void post(int ticket,Runnable task){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()&&ticket==generation)task.run();});}
    private void load(){if(busy)return;busy(true);final int ticket=++generation;
        worker.execute(()->{
            boolean saved=false;AccountSession checked=null;
            try{
                if(life){JSONObject session=new LifeSessionVault(this).load();saved=session!=null;
                    if(!saved){post(ticket,()->present(false,"尚未登录","登录后在这里查看账户状态。"));return;}
                    String uid=session.getString("uid");String account="账户尾号 · "+uid.substring(Math.max(0,uid.length()-4));
                    post(ticket,()->present(true,account,"正在检查登录…"));
                    int code=LifeAuthApi.verify(session);post(ticket,()->state.setText(code>=0?"已登录":"暂时无法确认登录状态，请刷新重试（"+code+"）。"));
                }else{AccountSession session=new AccountVault(this).load();checked=session;saved=session!=null;
                    if(!saved){post(ticket,()->present(false,"尚未登录","登录后在这里查看账户信息与余额。"));return;}
                    post(ticket,()->present(true,"趣智校园账号","正在读取账户信息…"));
                    AccountProfile profile=SmsAccountApi.profile(session);post(ticket,()->{identity.setText(profile.description());state.setText("已登录 · 正在读取余额…");});
                    String balance=SmsAccountApi.wallet(session,profile);post(ticket,()->state.setText("已登录\n\n"+balance));
                }
            }catch(HttpForm.SessionExpired expired){boolean cleared=false;try{cleared=AccountVault.clearExpired(this,checked);}catch(Exception ignored){}final boolean removed=cleared;post(ticket,()->{if(removed){present(false,"趣智校园账号","登录已失效，请重新登录。");login.setText("重新登录");}else state.setText("登录状态已变化，请刷新账户信息。");});}
            catch(Exception failure){final boolean hadSession=saved;post(ticket,()->{if(hadSession)state.setText("暂时无法更新账户信息，已保留本机登录。稍后可刷新重试。");else present(false,"无法读取本机账号","可重新登录恢复账户信息。");});}
            finally{post(ticket,()->busy(false));}
        });
    }
    private void signOut(){if(busy)return;busy(true);final int ticket=++generation;worker.execute(()->{try{if(life)new LifeSessionVault(this).clear();else new AccountVault(this).clear();post(ticket,()->present(false,"尚未登录","已退出本机登录。"));}catch(Exception e){post(ticket,()->state.setText("退出失败，请重试。"));}finally{post(ticket,()->busy(false));}});}
    @Override protected void onDestroy(){generation++;worker.shutdown();super.onDestroy();}
}
