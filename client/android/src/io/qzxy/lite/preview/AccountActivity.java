package io.qzxy.lite.preview;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.LoginPersistence;
import io.qzxy.lite.core.SmsLoginProtocol;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-triggered SMS account integration; never triggers device or payment operations. */
public final class AccountActivity extends Activity {
    /** Set by the 余额 entry so the balance query starts on its own once the session is loaded. */
    static final String EXTRA_ACTION = "io.qzxy.lite.preview.action";
    static final String ACTION_BALANCE = "balance";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final int BLUE = AppUi.ACCENT, INK = AppUi.INK, MUTED = AppUi.MUTED;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private AccountVault vault;
    private EditText phone, code;
    private Button send, login;
    private CheckBox acknowledged;
    private TextView status;
    private boolean busy, pendingLogin, destroyed;
    private long nextSendAt;
    private LoginPersistence loginWrite;
    private boolean balanceOnOpen;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        vault = new AccountVault(getApplicationContext());
        balanceOnOpen = getIntent() != null && ACTION_BALANCE.equals(getIntent().getStringExtra(EXTRA_ACTION));
        nextSendAt = getPreferences(MODE_PRIVATE).getLong("nextSendAt", 0);
        if (nextSendAt > System.currentTimeMillis() + 60000) nextSendAt = 0;
        form();
        setBusy(true);
        WORKER.execute(() -> {
            try {
                AccountSession session = vault.load();
                update(() -> { setBusy(false); if (session != null) signedIn(false); });
            } catch (Exception failure) {
                update(() -> { setBusy(false); status.setText("本地登录状态无法读取，请重新登录。登录成功后会替换旧状态。"); });
            }
        });
        tick();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3), 1); return view;
    }
    private void gap(LinearLayout panel, int size) { panel.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size))); }
    private LinearLayout panel() {
        final int side = AppUi.side(this, AppUi.compact(this) ? 20 : 24);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(side, dp(20), side, dp(24)); panel.setBackgroundColor(AppUi.BG);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            panel.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(side + bars.left, dp(20) + bars.top, side + bars.right, dp(24) + bars.bottom); return insets;
            });
        }
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(panel);
        setContentView(scroll); panel.requestApplyInsets();
        TextView back = text("‹ 返回科信小助手", 16, BLUE); back.setPadding(0, dp(12), 0, dp(12));
        back.setOnClickListener(v -> finish()); panel.addView(back); gap(panel, 24);
        return panel;
    }
    private Button button(String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        button.setTextSize(16); button.setTextColor(Color.WHITE); button.setMinHeight(dp(54));
        GradientDrawable background = new GradientDrawable(); background.setColor(BLUE); background.setCornerRadius(dp(16));
        button.setBackground(background); button.setOnClickListener(v -> action.run()); return button;
    }
    private void add(LinearLayout panel, View view) { panel.addView(view, new LinearLayout.LayoutParams(-1, -2)); }
    private EditText input(String hint) {
        EditText value = new EditText(this); value.setHint(hint); value.setSingleLine(true);
        value.setTextSize(18); value.setTextColor(INK); value.setMinHeight(dp(58)); value.setSaveEnabled(false);
        value.setInputType(InputType.TYPE_CLASS_NUMBER); return value;
    }
    private void form() {
        LinearLayout panel = panel();
        TextView title = text("短信验证码登录", 27, INK); title.setTypeface(null, Typeface.BOLD); add(panel, title); gap(panel, 12);
        add(panel, text("第三方客户端。手机号和验证码用于登录趣智校园，登录信息加密保存在本机。", 15, MUTED)); gap(panel, 26);
        phone = input("手机号"); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(11)}); add(panel, phone); gap(panel, 16);
        code = input("短信验证码"); code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        add(panel, code); gap(panel, 16);
        send = button("获取验证码", this::sendCode); add(panel, send); gap(panel, 24);
        acknowledged = new CheckBox(this); acknowledged.setText("我确认使用自己的手机号登录，知悉未注册号码可能按原版规则自动注册。");
        acknowledged.setTextSize(13); acknowledged.setTextColor(MUTED); add(panel, acknowledged); gap(panel, 16);
        login = button("登录", this::signIn); add(panel, login); gap(panel, 20);
        status = text("验证码只用于本次登录；登录凭据加密保存在本机。", 14, MUTED); add(panel, status); gap(panel, 26);
        add(panel, text("科信小助手 " + AppUi.version(this), 13, MUTED));
    }
    private void setBusy(boolean value) {
        busy = value;
        phone.setEnabled(!value); code.setEnabled(!value); acknowledged.setEnabled(!value);
        login.setEnabled(!value); login.setAlpha(value ? 0.5f : 1);
        updateSend();
    }
    private void updateSend() {
        if (send == null) return;
        long left = Math.max(0, (nextSendAt - System.currentTimeMillis() + 999) / 1000);
        send.setText(left > 0 ? left + " 秒后可重新获取" : "获取验证码");
        send.setEnabled(!busy && left == 0); send.setAlpha(send.isEnabled() ? 1 : 0.5f);
    }
    private void tick() { if (destroyed) return; updateSend(); handler.postDelayed(this::tick, 1000); }
    private void update(Runnable action) { handler.post(() -> { if (!destroyed) action.run(); }); }
    private String phoneValue() { String value = phone.getText().toString().trim(); SmsLoginProtocol.validatePhone(value); return value; }
    private String error(Exception failure) {
        if (failure instanceof org.json.JSONException) return "服务器响应格式与预期不符，请保留原版使用。";
        if (failure instanceof java.net.SocketTimeoutException) return "请求超时。若已收到验证码，请直接填写，不必重复发送。";
        if (failure instanceof javax.net.ssl.SSLException) return "安全连接失败，请检查网络和手机时间。";
        if (failure instanceof java.net.UnknownHostException || failure instanceof java.net.ConnectException) return "网络连接失败，请检查网络后重试。";
        if (failure instanceof java.io.IOException || failure instanceof IllegalArgumentException) {
            String message = failure.getMessage();
            if (message != null && !message.contains("http") && message.length() <= 220) return message;
        }
        return "操作未完成，请稍后重试。";
    }
    private void sendCode() {
        if (busy || System.currentTimeMillis() < nextSendAt) return;
        final String number;
        try { number = phoneValue(); } catch (IllegalArgumentException invalid) { status.setText(invalid.getMessage()); return; }
        nextSendAt = System.currentTimeMillis() + 60000;
        getPreferences(MODE_PRIVATE).edit().putLong("nextSendAt", nextSendAt).apply();
        setBusy(true); status.setText("正在请求验证码…");
        WORKER.execute(() -> {
            try { SmsAccountApi.send(number); update(() -> { setBusy(false); status.setText("验证码已发送，请在上方填写。"); }); }
            catch (Exception failure) { update(() -> { setBusy(false); status.setText(error(failure)); }); }
        });
    }
    private void signIn() {
        if (busy) return;
        if (!acknowledged.isChecked()) { status.setText("请先确认下方登录说明。"); return; }
        final String number, otp;
        try { number = phoneValue(); otp = code.getText().toString().trim(); SmsLoginProtocol.loginFields(number, otp); }
        catch (IllegalArgumentException invalid) { status.setText(invalid.getMessage()); return; }
        final LoginPersistence attempt = new LoginPersistence(new LoginPersistence.Store() {
            public AccountSession load() throws Exception { return vault.load(); }
            public void save(AccountSession session) throws Exception { vault.save(session); }
            public void clear() throws Exception { vault.clear(); }
        });
        loginWrite = attempt;
        pendingLogin = true; setBusy(true); status.setText("正在登录…");
        WORKER.execute(() -> {
            try {
                AccountSession session = SmsAccountApi.login(number, otp);
                if (cancelled.get()) return;
                attempt.stage(session);
                if (cancelled.get()) { attempt.cancel(); return; }
                update(() -> { pendingLogin = false; WORKER.execute(attempt::accept); busy = false; code.setText(""); signedIn(true); });
            } catch (Exception failure) { update(() -> { pendingLogin = false; setBusy(false); status.setText(error(failure)); }); }
        });
    }
    private void signedIn(boolean fresh) {
        // 登录成功一律回到上一页，不再看 returnToAccountInfo：调用页 onResume 会自己重新读取
        // 本机登录状态，留在这里只是多让读者按一次返回。
        if(fresh){setResult(RESULT_OK);finish();return;}
        send = null;
        LinearLayout panel = panel();
        add(panel, text("已保存登录状态", 28, INK)); gap(panel, 16);
        TextView verification = text("已保存账号，可在账户页检查登录状态。", 15, MUTED);
        add(panel, verification); gap(panel, 24);
        TextView accountInfo = text("点击下方查询，验证登录状态并读取校园账户余额。", 15, MUTED); add(panel, accountInfo); gap(panel, 20);
        Button refresh = button("查询账号与余额", () -> {}); add(panel, refresh); gap(panel, 24);
        Button repair = button("补全校园信息（短信验证）", () -> { form(); setBusy(false); }); add(panel, repair); gap(panel, 24);
        add(panel, text("当前版本仍不能真实用水。", 13, MUTED)); gap(panel, 24);
        Button logout = button("退出科信小助手账号", () -> {});
        refresh.setOnClickListener(v -> {
            refresh.setEnabled(false); logout.setEnabled(false); repair.setEnabled(false); accountInfo.setText("正在验证账号…");
            WORKER.execute(() -> {
                try {
                    AccountSession saved = vault.load();
                    if (saved == null) throw new java.io.IOException("本机登录状态不存在，请重新登录");
                    AccountProfile profile = SmsAccountApi.profile(saved);
                    update(() -> { verification.setText("服务器已确认当前登录状态有效。"); accountInfo.setText(profile.description() + "\n正在查询余额…"); });
                    String balance;
                    try { balance = SmsAccountApi.wallet(saved, profile); }
                    catch (Exception failure) { balance = "余额查询未完成：" + error(failure); }
                    final String result = balance;
                    update(() -> accountInfo.setText("登录状态已验证\n" + profile.description() + "\n\n" + result));
                } catch (Exception failure) { update(() -> accountInfo.setText("账号查询未完成：" + error(failure))); }
                finally { update(() -> { refresh.setEnabled(true); logout.setEnabled(true); repair.setEnabled(true); }); }
            });
        });
        logout.setOnClickListener(v -> {
            logout.setEnabled(false);
            WORKER.execute(() -> {
                try { vault.clear(); update(() -> { form(); setBusy(false); status.setText("本机登录状态已清除。"); }); }
                catch (Exception failure) { update(() -> { logout.setEnabled(true); android.widget.Toast.makeText(this, "清除失败，请重试", 0).show(); }); }
            });
        });
        add(panel, logout);
        // Arriving from the 余额 entry: run the query without making the user tap again.
        if (balanceOnOpen) { balanceOnOpen = false; refresh.post(refresh::performClick); }
    }
    @Override protected void onDestroy() {
        destroyed = true; cancelled.set(true); handler.removeCallbacksAndMessages(null);
        final LoginPersistence attempt = loginWrite;
        if (pendingLogin && attempt != null) WORKER.execute(() -> {
            try { attempt.cancel(); } catch (Exception ignored) { /* Do not delete the previous session on rollback failure. */ }
        });
        super.onDestroy();
    }
}
