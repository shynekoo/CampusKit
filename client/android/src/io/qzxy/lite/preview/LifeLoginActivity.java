package io.qzxy.lite.preview;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import org.json.*;
/**
 * 慧生活798 的登录页。
 *
 * <p>只留支付宝授权这一条路。手机号 + 图形验证码 + 短信验证码那条路要过三道输入，其中图形验证码
 * 服务端时好时坏，失败时读者分不清是自己看错了还是通道关了；而支付宝授权一次确认就返回，
 * 还是官方现在主推的方式，两条路并排只会让人猜该点哪个。这条支路连同它的验证码图片一起删掉，
 * 页面上只剩一个真正的按钮。</p>
 *
 * <p>失败时把服务端原话显示出来，而不是一律「请求未完成」：读者需要知道是没装支付宝、
 * 授权被拒绝，还是网络不通——这三种情况的下一步完全不同。</p>
 */
public final class LifeLoginActivity extends Activity {
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private TextView status;private Button alipay,clear,verify;
    private boolean busy;
    private CheckBox consent;
    private volatile boolean closed;

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void gap(LinearLayout form,int n){form.addView(new View(this),new LinearLayout.LayoutParams(-1,dp(n)));}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);return v;}
    private TextView muted(String value,int size){TextView v=text(value,size);v.setTextColor(AppUi.MUTED);v.setLineSpacing(dp(3),1f);return v;}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        int side=AppUi.side(this,20);
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);
        form.setPadding(side,dp(10),side,dp(24));form.setBackgroundColor(AppUi.BG);
        scroll.addView(form);setContentView(scroll);
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        }

        TextView back=text("‹ 返回科信小助手",14);back.setTextColor(AppUi.ACCENT);back.setPadding(0,dp(8),0,dp(8));
        back.setOnClickListener(v->finish());form.addView(back);
        TextView title=text("登录慧生活798",24);title.setTypeface(null,Typeface.BOLD);form.addView(title);
        TextView intro=muted("第三方客户端。用支付宝授权登录，登录凭据加密保存在本机。",13);
        intro.setPadding(0,dp(6),0,dp(16));form.addView(intro);

        consent=new CheckBox(this);consent.setText("我了解这是第三方客户端，同意把支付宝授权码发送给慧生活用于登录");
        consent.setTextSize(13);consent.setTextColor(AppUi.MUTED);form.addView(consent);
        gap(form,16);

        // 唯一的入口：这是官方现在的登录方式，一次确认就回来，不用等短信，也不用认图片字符。
        alipay=primary(form,"支付宝一键登录",()->alipaySignIn());
        TextView hint=muted("会跳到支付宝。授权页显示的是官方应用名「慧生活798」，登录的是你自己的账号。",12);
        hint.setPadding(dp(2),dp(8),dp(2),dp(4));form.addView(hint);

        status=text("",13);status.setTextColor(AppUi.MUTED);status.setPadding(0,dp(16),0,dp(10));form.addView(status);
        LinearLayout secondary=new LinearLayout(this);secondary.setOrientation(1);
        secondary.setBackground(AppUi.outlined(this,0xffffffff,10,AppUi.LINE));
        form.addView(secondary,new LinearLayout.LayoutParams(-1,-2));
        verify=quiet(secondary,"检查登录状态",()->run(()->{
            JSONObject session=new LifeSessionVault(this).load();if(session==null)return "尚未登录，请先完成支付宝授权";
            int result=LifeAuthApi.verify(session);
            if(result>=0)return "服务器确认登录有效。请从今日页扫码进入设备。";
            // -99 是服务器的「登录状态已过期」，其余负数（-1 请求参数错误最常见）只是这次核验没问成。
            if(LifeAuthApi.expired(result))return "服务器说登录已过期，请重新用支付宝授权登录。";
            return "本机登录已保存。服务器这次没有返回可核对的状态（"+result+"），这不代表登录失效。";
        }));
        clear=quiet(secondary,"清除本机慧生活登录",()->run(()->{new LifeSessionVault(this).clear();return "本机登录已清除";}));

        // A saved session is worth saying before the reader taps anything, but it is only a hint: an
        // unreadable vault must not stop the page from opening.
        try{if(new LifeSessionVault(this).exists())status.setText("已保存账号，可检查登录状态或重新登录。");}catch(Exception ignored){}
    }
    private Button primary(LinearLayout form,String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(0xffffffff);
        b.setBackground(AppUi.shape(this,AppUi.ACCENT,12));b.setOnClickListener(v->action.run());
        form.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));return b;
    }
    private Button quiet(LinearLayout form,String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(AppUi.ACCENT);
        AppUi.press(b,0x00ffffff,10);b.setOnClickListener(v->action.run());
        form.addView(b,new LinearLayout.LayoutParams(-1,dp(44)));return b;
    }
    private interface Job{String execute()throws Exception;}
    private void run(Job job){
        if(busy||closed)return;
        busy=true;status.setText("正在处理…");enable(false);
        worker.execute(()->{
            String message;try{message=job.execute();}catch(Exception error){message=explain(error);}
            final String result=message;
            runOnUiThread(()->{if(isDestroyed())return;busy=false;enable(true);status.setText(result);});
        });
    }
    /** The server's own words when it spoke Chinese, otherwise a sentence about the network. */
    private static String explain(Exception error){
        if(error instanceof IllegalArgumentException&&error.getMessage()!=null)return error.getMessage();
        String text=error.getMessage();
        if(text!=null&&text.matches(".*[\\u4e00-\\u9fa5].*"))return text;
        return "网络不通或服务器没有响应，请检查网络后重试。";
    }
    private void enable(boolean value){alipay.setEnabled(value);clear.setEnabled(value);verify.setEnabled(value);consent.setEnabled(value);}
    /**
     * 支付宝授权登录：唤起支付宝，拿回 auth_code，再交给慧生活换登录凭据。
     *
     * <p>整段放在工作线程：SDK 是同步阻塞的，会绑定支付宝的服务；把它放主线程会卡住界面，
     * 也可能直接抛 NetworkOnMainThread。授权失败时页面原样留着，读者可以点重试。</p>
     */
    private void alipaySignIn(){
        if(busy)return;
        if(!consent.isChecked()){status.setText("请先确认登录信息用途");return;}
        run(()->{
            String openCode=AlipayLogin.code(this);
            JSONObject response=LifeAuthApi.post("/api/v1/acc/login",LifeAuthApi.alipay(openCode));
            if(LifeAuthApi.resultCode(response)<0){String why=LifeAuthApi.message(response);return why.isEmpty()?"支付宝授权成功，但慧生活没有接受这次登录":why;}
            JSONObject session=LifeAuthApi.session(response);
            if(closed||isFinishing()||isDestroyed())return "已取消保存";
            new LifeSessionVault(this).save(session);
            runOnUiThread(()->{if(isDestroyed()||isFinishing())return;setResult(RESULT_OK);finish();});
            return "已通过支付宝登录，凭据已加密保存在本机。";
        });
    }
    @Override protected void onDestroy(){closed=true;worker.shutdown();super.onDestroy();}
}
