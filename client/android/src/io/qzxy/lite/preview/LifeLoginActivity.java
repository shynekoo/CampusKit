package io.qzxy.lite.preview;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import org.json.*;
public final class LifeLoginActivity extends Activity {
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private EditText phone,captcha,code;private ImageView picture;private TextView status;private Button send,login,refresh,clear,verify;
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
    @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null)sentPhone=state.getString("sentPhone","");getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(22),dp(24),dp(22),dp(24));form.setBackgroundColor(AppUi.BG);scroll.addView(form);setContentView(scroll);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});}
        Button back=new Button(this);back.setText("返回科信小助手");back.setOnClickListener(v->finish());form.addView(back);
        TextView title=new TextView(this);title.setText("登录慧生活798");title.setTextSize(24);form.addView(title);
        TextView intro=new TextView(this);intro.setText("第三方客户端。手机号和验证码用于登录慧生活，登录信息加密保存在本机。");intro.setTextSize(14);intro.setPadding(0,dp(12),0,dp(12));form.addView(intro);
        phone=input(form,"手机号",3);picture=new ImageView(this);picture.setAdjustViewBounds(true);form.addView(picture,new LinearLayout.LayoutParams(-1,dp(72)));
        refresh=button(form,"加载 / 更换图形验证码",()->loadCaptcha());captcha=input(form,"图形验证码",1);picture.setVisibility(View.GONE);refresh.setVisibility(View.GONE);captcha.setVisibility(View.GONE);
        consent=new CheckBox(this);consent.setText("我了解第三方身份，同意将上述信息发送至慧生活用于登录");form.addView(consent);
        send=button(form,"获取短信验证码",()->sendSms());code=input(form,"短信验证码",2);login=button(form,"登录并保存到本机",()->signIn());
        verify=button(form,"检查登录状态",()->run(()->{
            JSONObject session=new LifeSessionVault(this).load();if(session==null)return "尚未登录，请先完成短信验证";
            int result=LifeAuthApi.verify(session);return result>=0?"服务器确认登录有效。请从今日页扫码进入设备。":"服务器未确认登录有效（状态码 "+result+"），暂不能判断是否过期；本机登录已保留。";
        }));
        clear=button(form,"清除本机慧生活登录",()->run(()->{new LifeSessionVault(this).clear();sentPhone="";runOnUiThread(()->{if(!isDestroyed()){phone.setText("");code.setText("");captcha.setText("");picture.setImageDrawable(null);ready=false;}});return "本机登录已清除";}));
        status=new TextView(this);status.setTextSize(14);form.addView(status);run(()->new LifeSessionVault(this).exists()?"已保存账号，可检查登录状态":"填写手机号并获取短信验证码");
    }
    private EditText input(LinearLayout form,String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine();e.setInputType(type);e.setSaveEnabled(false);if(Build.VERSION.SDK_INT>=26)e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);form.addView(e);return e;}
    private Button button(LinearLayout form,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->action.run());form.addView(b);return b;}
    private interface Job{String execute()throws Exception;}
    private void run(Job job){if(busy||closed)return;busy=true;status.setText("正在处理…");enable(false);worker.execute(()->{String message;try{message=job.execute();}catch(Exception error){message=error instanceof IllegalArgumentException?error.getMessage():"请求未完成或返回异常，请检查网络和验证码后重试。";}final String result=message;runOnUiThread(()->{if(isDestroyed())return;busy=false;enable(true);status.setText(result);});});}
    private void enable(boolean value){send.setEnabled(value);login.setEnabled(value);refresh.setEnabled(value);clear.setEnabled(value);verify.setEnabled(value);consent.setEnabled(value);updateSend();phone.setEnabled(value);captcha.setEnabled(value);code.setEnabled(value);}
    private void loadCaptcha(){if(busy)return;ready=false;captcha.setText("");final double current=new java.security.SecureRandom().nextDouble();run(()->{byte[] bytes=LifeAuthApi.request(LifeAuthApi.captchaPath(current,System.currentTimeMillis()),null);Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null)throw new java.io.IOException();runOnUiThread(()->{if(!isDestroyed()){seed=current;ready=true;picture.setImageBitmap(bitmap);}});return "请填写图片中的验证码，然后获取短信验证码";});}
    private void sendSms(){if(busy)return;if(!consent.isChecked()){status.setText("请先确认登录信息用途");return;}if(!ready){picture.setVisibility(View.VISIBLE);refresh.setVisibility(View.VISIBLE);captcha.setVisibility(View.VISIBLE);loadCaptcha();return;}
        if(remaining()>0){status.setText("请稍后再获取验证码");return;}
        final String number=phone.getText().toString().trim(),answer=captcha.getText().toString().trim();final double current=seed;
        try{final JSONObject body=LifeAuthApi.sms(number,answer,current);if(!getSharedPreferences("life798-login-ui",MODE_PRIVATE).edit().putLong("smsAttemptAt",System.currentTimeMillis()).commit()){status.setText("无法保存请求状态，请重试");return;}run(()->{JSONObject r=LifeAuthApi.post("/api/v1/acc/login/code",body);if(LifeAuthApi.resultCode(r)<0)return "验证码未发送成功，请更换图形验证码后重试";sentPhone=number;return "短信验证码已发送，请在下方输入";});}catch(Exception e){status.setText("请核对手机号和图形验证码");}
    }
    private void signIn(){if(busy)return;if(!consent.isChecked()){status.setText("请先确认登录信息用途");return;}final String number=phone.getText().toString().trim();if(!number.equals(sentPhone)){status.setText("请先为此手机号获取短信验证码");return;}
        try{final JSONObject body=LifeAuthApi.login(number,code.getText().toString().trim());run(()->{JSONObject session=LifeAuthApi.session(LifeAuthApi.post("/api/v1/acc/login",body));if(closed||isFinishing()||isDestroyed())return "已取消保存";new LifeSessionVault(this).save(session);runOnUiThread(()->{if(!isDestroyed()){code.setText("");if(getIntent().getBooleanExtra("returnToAccountInfo",false))finish();}});return "登录成功，凭据已加密保存在本机。可从今日页扫码进入设备。";});}catch(Exception e){status.setText("请填写有效的短信验证码");}}
    @Override protected void onDestroy(){closed=true;timer.removeCallbacks(countdown);worker.shutdown();super.onDestroy();}
}
