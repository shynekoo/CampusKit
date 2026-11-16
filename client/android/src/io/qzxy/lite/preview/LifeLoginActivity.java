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
 * <p>图形验证码一进页面就加载并显示，不再要求先点一次「获取短信验证码」才出现——那一步什么都不发送，
 * 只是把隐藏的图片和输入框露出来，读者看到的是「按了没反应」，然后发现手机号下面突然多出三个控件。
 * 现在输入框和图片一进来就在，图片按控件尺寸居中显示，不再是 120×40 原图被拉成整屏宽。</p>
 *
 * <p>失败时把服务端原话显示出来（例如「图片验证码错误」），而不是一律「请求未完成」：
 * 读者需要知道是验证码看错了、手机号填错了，还是网络不通，这三种情况的下一步完全不同。</p>
 */
public final class LifeLoginActivity extends Activity {
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private EditText phone,captcha,code;private ImageView picture;private TextView status;private Button alipay,send,login,refresh,clear,verify;
    private boolean busy,ready;private double seed;private String sentPhone="";
    private CheckBox consent;
    private volatile boolean closed;
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final Runnable countdown=new Runnable(){public void run(){if(send!=null)updateSend();timer.postDelayed(this,1000);}};
    private long remaining(){long elapsed=System.currentTimeMillis()-getSharedPreferences("life798-login-ui",MODE_PRIVATE).getLong("smsAttemptAt",0);return elapsed>=0&&elapsed<60000?60000-elapsed:0;}
    private void updateSend(){long left=(remaining()+999)/1000;send.setText(left>0?left+" 秒后可再次获取":"获取短信验证码");send.setEnabled(!busy&&left==0);}
    @Override protected void onResume(){super.onResume();timer.post(countdown);}
    @Override protected void onPause(){timer.removeCallbacks(countdown);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("sentPhone",sentPhone);super.onSaveInstanceState(state);}

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void gap(LinearLayout form,int n){form.addView(new View(this),new LinearLayout.LayoutParams(-1,dp(n)));}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);return v;}
    private TextView muted(String value,int size){TextView v=text(value,size);v.setTextColor(AppUi.MUTED);v.setLineSpacing(dp(3),1f);return v;}

    /** A hairline with a word in the middle, separating the two ways in. */
    private View divider(String label){
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(18),0,dp(2));
        View left=new View(this);left.setBackgroundColor(AppUi.LINE);
        View right=new View(this);right.setBackgroundColor(AppUi.LINE);
        TextView caption=text(label,12);caption.setTextColor(AppUi.MUTED);caption.setPadding(dp(10),0,dp(10),0);
        row.addView(left,new LinearLayout.LayoutParams(0,Math.max(1,dp(1)),1));
        row.addView(caption);
        row.addView(right,new LinearLayout.LayoutParams(0,Math.max(1,dp(1)),1));
        return row;
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null)sentPhone=state.getString("sentPhone","");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        // Page margins come from the screen; the form keeps its own inset so a 320dp phone still
        // leaves the code field a usable width beside the captcha picture.
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
        TextView intro=muted("第三方客户端。用支付宝授权登录，或改走手机号验证码；登录凭据加密保存在本机。",13);
        intro.setPadding(0,dp(6),0,dp(16));form.addView(intro);

        consent=new CheckBox(this);consent.setText("我了解这是第三方客户端，同意把手机号或支付宝授权码发送给慧生活用于登录");
        consent.setTextSize(13);consent.setTextColor(AppUi.MUTED);form.addView(consent);
        gap(form,10);

        // 支付宝优先：这是官方现在的登录方式，一次确认就回来，不用等短信，也不用认图片字符。
        alipay=primary(form,"支付宝一键登录",()->alipaySignIn());
        TextView hint=muted("会跳到支付宝。授权页显示的是官方应用名「慧生活798」，登录的是你自己的账号。",12);
        hint.setPadding(dp(2),dp(8),dp(2),dp(4));form.addView(hint);

        form.addView(divider("或使用手机号验证码"));
        gap(form,8);
        phone=input(form,"手机号",android.text.InputType.TYPE_CLASS_PHONE);
        phone.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(11)});
        gap(form,12);

        // The picture sits beside the field it answers. On the narrowest phones it gives way (96dp
        // instead of 120dp) so the field keeps enough width to show what was typed.
        LinearLayout captchaRow=new LinearLayout(this);captchaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        captcha=new EditText(this);captcha.setHint("图形验证码");captcha.setSingleLine(true);
        captcha.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        captcha.setSaveEnabled(false);if(Build.VERSION.SDK_INT>=26)captcha.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        captcha.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(12)});
        captchaRow.addView(captcha,new LinearLayout.LayoutParams(0,-2,1));
        picture=new ImageView(this);
        // FIT_CENTER, not a stretched full-width strip: the picture is 120×40, and the old
        // match-parent 72dp box turned it into a blurry banner nobody could read the code off.
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        picture.setContentDescription("图形验证码图片");
        LinearLayout.LayoutParams pictureParams=new LinearLayout.LayoutParams(AppUi.compact(this)?dp(96):dp(120),dp(44));
        pictureParams.leftMargin=dp(10);captchaRow.addView(picture,pictureParams);
        form.addView(captchaRow);
        refresh=quiet(form,"看不清？换一张",()->loadCaptcha());
        gap(form,8);

        send=primary(form,"获取短信验证码",()->sendSms());
        gap(form,12);

        code=input(form,"短信验证码",android.text.InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        gap(form,12);
        login=primary(form,"登录并保存到本机",()->signIn());

        status=text("",13);status.setTextColor(AppUi.MUTED);status.setPadding(0,dp(12),0,dp(10));form.addView(status);
        LinearLayout secondary=new LinearLayout(this);secondary.setOrientation(1);
        secondary.setBackground(AppUi.outlined(this,0xffffffff,10,AppUi.LINE));
        form.addView(secondary,new LinearLayout.LayoutParams(-1,-2));
        verify=quiet(secondary,"检查登录状态",()->run(()->{
            JSONObject session=new LifeSessionVault(this).load();if(session==null)return "尚未登录，请先完成支付宝授权或短信验证";
            int result=LifeAuthApi.verify(session);
            if(result>=0)return "服务器确认登录有效。请从今日页扫码进入设备。";
            // -99 是服务器的「登录状态已过期」，其余负数（-1 请求参数错误最常见）只是这次核验没问成。
            if(LifeAuthApi.expired(result))return "服务器说登录已过期，请重新用支付宝授权或短信验证登录。";
            return "本机登录已保存。服务器这次没有返回可核对的状态（"+result+"），这不代表登录失效。";
        }));
        clear=quiet(secondary,"清除本机慧生活登录",()->run(()->{new LifeSessionVault(this).clear();sentPhone="";runOnUiThread(()->{if(!isDestroyed()){phone.setText("");code.setText("");captcha.setText("");picture.setImageDrawable(null);ready=false;}});return "本机登录已清除";}));

        // Load the picture at once: it is the first thing the reader needs, and a page whose only
        // visible field is a phone number looks like it is missing half of itself.
        // A saved session is worth saying before the picture arrives, but it is only a hint: an
        // unreadable vault must not stop the page from opening.
        try{if(new LifeSessionVault(this).exists())status.setText("已保存账号，可检查登录状态或重新登录。");}catch(Exception ignored){}
        loadCaptcha();
    }
    private EditText input(LinearLayout form,String hint,int type){
        EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setInputType(type);e.setSaveEnabled(false);
        if(Build.VERSION.SDK_INT>=26)e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        form.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
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
    private void enable(boolean value){alipay.setEnabled(value);send.setEnabled(value);login.setEnabled(value);refresh.setEnabled(value);clear.setEnabled(value);verify.setEnabled(value);consent.setEnabled(value);updateSend();phone.setEnabled(value);captcha.setEnabled(value);code.setEnabled(value);}
    private void loadCaptcha(){
        if(busy)return;
        ready=false;captcha.setText("");
        final double current=new java.security.SecureRandom().nextDouble();
        run(()->{
            byte[] bytes=LifeAuthApi.request(LifeAuthApi.captchaPath(current,System.currentTimeMillis()),null);
            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            if(bitmap==null)throw new java.io.IOException("验证码图片无法识别，请点「换一张」重试");
            runOnUiThread(()->{if(!isDestroyed()){seed=current;ready=true;picture.setImageBitmap(bitmap);}});
            return "已加载图形验证码。可直接用支付宝登录，或填写图中字符获取短信验证码。";
        });
    }
    /**
     * 支付宝授权登录：唤起支付宝，拿回 auth_code，再交给慧生活换登录凭据。
     *
     * <p>整段放在工作线程：SDK 是同步阻塞的，会绑定支付宝的服务；把它放主线程会卡住界面，
     * 也可能直接抛 NetworkOnMainThread。授权失败时手机号表单原样留着，读者可以立刻改走短信。</p>
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
            runOnUiThread(()->{if(!isDestroyed()&&getIntent().getBooleanExtra("returnToAccountInfo",false))finish();});
            return "已通过支付宝登录，凭据已加密保存在本机。";
        });
    }
    private void sendSms(){
        if(busy)return;
        if(!consent.isChecked()){status.setText("请先确认登录信息用途");return;}
        if(!ready){status.setText("验证码图片还没加载好，请点「换一张」重试");return;}
        if(remaining()>0){status.setText("请稍后再获取验证码");return;}
        final String number=phone.getText().toString().trim(),answer=captcha.getText().toString().trim();final double current=seed;
        try{
            final JSONObject body=LifeAuthApi.sms(number,answer,current);
            if(!getSharedPreferences("life798-login-ui",MODE_PRIVATE).edit().putLong("smsAttemptAt",System.currentTimeMillis()).commit()){status.setText("无法保存请求状态，请重试");return;}
            run(()->{
                JSONObject r=LifeAuthApi.post("/api/v1/acc/login/code",body);
                if(LifeAuthApi.resultCode(r)<0){
                    String why=LifeAuthApi.message(r);
                    // A rejected picture is stale by definition; hand out a fresh one instead of
                    // letting the reader retype into an image the server no longer expects.
                    ready=false;loadCaptcha();
                    return why.isEmpty()?"验证码未发送成功，请更换图形验证码后重试":why;
                }
                sentPhone=number;return "短信验证码已发送，请在下方输入";
            });
        }catch(Exception e){status.setText(explain(e));}
    }
    private void signIn(){
        if(busy)return;
        if(!consent.isChecked()){status.setText("请先确认登录信息用途");return;}
        final String number=phone.getText().toString().trim();
        if(!number.equals(sentPhone)){status.setText("请先为此手机号获取短信验证码");return;}
        try{
            final JSONObject body=LifeAuthApi.login(number,code.getText().toString().trim());
            run(()->{
                JSONObject response=LifeAuthApi.post("/api/v1/acc/login",body);
                if(LifeAuthApi.resultCode(response)<0){String why=LifeAuthApi.message(response);return why.isEmpty()?"登录未成功，请核对短信验证码后重试":why;}
                JSONObject session=LifeAuthApi.session(response);
                if(closed||isFinishing()||isDestroyed())return "已取消保存";
                new LifeSessionVault(this).save(session);
                runOnUiThread(()->{if(!isDestroyed()){code.setText("");if(getIntent().getBooleanExtra("returnToAccountInfo",false))finish();}});
                return "登录成功，凭据已加密保存在本机。可从今日页扫码进入设备。";
            });
        }catch(Exception e){status.setText(explain(e));}
    }
    @Override protected void onDestroy(){closed=true;timer.removeCallbacks(countdown);worker.shutdown();super.onDestroy();}
}
