package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The academic account, entered here once and reused for grades and empty classrooms.
 *
 * <p>Saving verifies the account immediately by signing in for real, so a typo is caught while the
 * person is still looking at the field rather than later behind a spinner. The password is sealed by
 * {@link EduVault}; this screen never writes it anywhere else and never logs it.</p>
 */
public final class EduAccountActivity extends Page {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DeviceScreen screen;
    private EditText user, password;
    private Button save;
    private TextView status;
    private EduVault vault;
    private boolean busy;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        screen = new DeviceScreen(this, "教务账号");
        vault = new EduVault(getApplicationContext());

        user = field("学号 / 用户名", false);
        password = field("密码", true);
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        password.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { start(); return true; }
            return false;
        });

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(AppUi.MUTED);
        status.setPadding(dp(2), dp(10), dp(2), dp(2));
        status.setText("账号还没保存。");
        screen.card.addView(status);

        save = screen.primary("保存并登录", this::start);

        Button clear = new Button(this);
        clear.setText("清除已保存的账号");
        clear.setAllCaps(false);
        clear.setTextSize(13);
        clear.setTextColor(AppUi.ACCENT);
        clear.setBackgroundColor(Color.TRANSPARENT);
        clear.setOnClickListener(v -> clearAccount());
        screen.root.addView(clear, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView web = new TextView(this);
        web.setText("学校要求验证码时改用网页登录 →");
        web.setTextSize(13);
        web.setTextColor(AppUi.MUTED);
        web.setGravity(Gravity.CENTER);
        web.setPadding(0, dp(6), 0, dp(6));
        web.setOnClickListener(v -> startActivity(new Intent(this, EduLoginActivity.class)));
        screen.root.addView(web);

        screen.note("密码用本机密钥库加密后只存在这台设备，不上传任何服务器；查询成绩、空教室和导入课表时会自动替你登录学校系统。");
        prefill();
    }

    private EditText field(String label, boolean secret) {
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextSize(12);
        caption.setTextColor(AppUi.MUTED);
        caption.setPadding(dp(4), 0, dp(4), dp(4));
        screen.card.addView(caption);

        EditText input = new EditText(this);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setTextColor(AppUi.INK);
        input.setHintTextColor(0xff9aa4b2);
        input.setHint(secret ? "教务系统密码" : "学号或用户名");
        input.setBackground(AppUi.outlined(this, AppUi.SURFACE, 10, AppUi.LINE));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        int type = secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT;
        input.setInputType(type);
        if (android.os.Build.VERSION.SDK_INT >= 26)
            input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        params.bottomMargin = dp(12);
        screen.card.addView(input, params);
        return input;
    }

    private void prefill() {
        worker.execute(() -> {
            EduVault.Credential stored = vault.load();
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                if (stored == null || !stored.complete()) {
                    status.setText("账号还没保存。");
                    return;
                }
                user.setText(stored.user);
                password.setText(stored.password);
                status.setText(stored.refused
                    ? "已保存 " + stored.user + "，但学校上次拒绝了这个密码。核对后重新保存，才会再自动登录。"
                    : "已保存 " + stored.user + "。改完直接保存即可。");
            });
        });
    }

    private void start() {
        if (busy) return;
        final String name = user.getText().toString().trim();
        final String secret = password.getText().toString();
        if (name.isEmpty() || secret.isEmpty()) {
            status.setText("账号和密码都要填。");
            return;
        }
        busy(true);
        status.setText("正在用这组账号登录学校系统…");
        worker.execute(() -> {
            String message;
            boolean ok = false;
            try {
                EduClient.remember(name, secret);
                EduClient.signIn();
                vault.save(name, secret);
                vault.refused(false);
                ok = true;
                message = "登录成功，已保存。以后查成绩和空教室不用再输密码。";
            } catch (EduClient.BadPassword wrong) {
                message = wrong.getMessage();
            } catch (EduClient.CaptchaNeeded captcha) {
                message = captcha.getMessage();
            } catch (Exception failure) {
                message = EduClient.describe(failure);
            }
            final String text = message;
            final boolean done = ok;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                busy(false);
                // 登录成功就交还控制权：教务门户、课表导入、成绩页都在 onResume/onActivityResult
                // 里接着把原来的查询跑完，停在这一页只会让读者自己再按一次返回。
                if (done) {
                    setResult(RESULT_OK);
                    finish();
                    return;
                }
                status.setText(text);
            });
        });
    }

    private void clearAccount() {
        if (busy) return;
        busy(true);
        status.setText("正在清除…");
        worker.execute(() -> {
            vault.clear();
            EduClient.remember("", "");
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                busy(false);
                user.setText("");
                password.setText("");
                status.setText("已清除保存的账号。");
                setResult(RESULT_CANCELED);
            });
        });
    }

    private void busy(boolean value) {
        busy = value;
        save.setEnabled(!value);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
