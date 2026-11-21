// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.UseCode;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 使用码：一个 8 位数字，在浴室或吹风机的键盘上输入即可开启，不用扫码。
 *
 * <p>页面只做四件事：显示当前码、开关、领取/重新领取、自动关闭时间。领取出来的码有 3 分钟
 * 有效期，所以领取后必须当场确认「使用此码」，否则下一次进来还得重领——这一点与扫码不同，
 * 不写清楚会让人以为码可以一直用下去。</p>
 */
public final class UseCodeActivity extends Page {
    private static final int VALID_SECONDS = 180;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AccountSession session;
    private AccountProfile profile;
    private UseCode current;
    private boolean busy, destroyed;
    private int generation;
    private DeviceScreen screen;
    private TextView state, code, expiry, note, hint;
    private Button toggle, regenerate, autoClose;
    private CountDownTimer countdown;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        screen = new DeviceScreen(this, "使用码");
        screen.loading(true);
        state = screen.text(screen.card, "正在读取使用码…", 15);
        code = screen.text(screen.card, "", 30);
        code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        code.setGravity(Gravity.CENTER);
        code.setLetterSpacing(0.18f);
        code.setPadding(0, screen.dp(14), 0, screen.dp(10));
        code.setTextIsSelectable(true);
        expiry = screen.text(screen.card, "", 13);
        expiry.setTextColor(AppUi.MUTED);
        regenerate = button(screen.card, "领取使用码", this::issue);
        toggle = button(screen.card, "关闭使用码", this::toggle);
        autoClose = button(screen.card, "自动关闭设置", this::chooseAutoClose);
        toggle.setVisibility(View.GONE);
        autoClose.setVisibility(View.GONE);
        hint = screen.text(screen.card, "", 13);
        hint.setTextColor(AppUi.MUTED);
        note = screen.note("使用码只用于在设备键盘上开启，不会开始计费；费用仍按设备实际使用结算。");
        load();
    }

    private Button button(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(AppUi.ACCENT);
        b.setMinHeight(screen.dp(48)); b.setBackground(AppUi.shape(this, AppUi.SOFT, 12));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = screen.dp(8);
        parent.addView(b, lp);
        return b;
    }

    @Override protected void onDestroy() {
        destroyed = true; generation++;
        if (countdown != null) countdown.cancel();
        worker.shutdownNow();
        super.onDestroy();
    }

    private void post(int ticket, Runnable task) {
        runOnUiThread(() -> { if (!destroyed && !isFinishing() && ticket == generation) task.run(); });
    }

    private void working(boolean value, String message) {
        busy = value;
        screen.loading(value);
        if (message != null) state.setText(message);
        toggle.setEnabled(!value);
        regenerate.setEnabled(!value);
        autoClose.setEnabled(!value);
    }

    private void load() {
        if (busy) return;
        working(true, "正在读取使用码…");
        final int ticket = ++generation;
        worker.execute(() -> {
            try {
                session = new AccountVault(this).load();
                if (session == null) {
                    post(ticket, () -> { working(false, "尚未登录"); needLogin(); });
                    return;
                }
                profile = SmsAccountApi.profile(session);
                UseCode code = UseCodeApi.detail(session, profile);
                post(ticket, () -> { working(false, null); render(code); });
            } catch (HttpForm.SessionExpired expired) {
                post(ticket, () -> { working(false, "登录已失效"); needLogin(); });
            } catch (Exception failure) {
                post(ticket, () -> {
                    working(false, null);
                    state.setText(error(failure));
                    regenerate.setEnabled(true);
                });
            }
        });
    }

    private void needLogin() {
        state.setText("使用码属于登录账户，请先登录趣智校园。");
        code.setText("");
        expiry.setText("");
        hint.setText("");
        regenerate.setText("去登录");
        regenerate.setEnabled(true);
        regenerate.setOnClickListener(v -> startActivity(new Intent(this, AccountActivity.class)));
    }

    private void render(UseCode value) {
        current = value;
        regenerate.setText(value.hasCode() ? "重新领取使用码" : "领取使用码");
        regenerate.setOnClickListener(v -> issue());
        state.setText(value.describe());
        state.setTextColor(value.enabled() ? AppUi.INK : AppUi.MUTED);
        boolean show = value.enabled() && value.hasCode();
        code.setText(show ? prettify(value.code()) : "— — — — — — — —");
        code.setTextColor(show ? AppUi.INK : AppUi.MUTED);
        expiry.setText(show && !value.expiringTime().isEmpty() ? value.expiringTime() : "");
        expiry.setVisibility(show && !value.expiringTime().isEmpty() ? View.VISIBLE : View.GONE);
        hint.setText(value.instructions());
        toggle.setText(value.enabled() ? "关闭使用码" : "开启使用码");
        toggle.setVisibility(View.VISIBLE);
        autoClose.setVisibility(value.configByProject() ? View.GONE : View.VISIBLE);
        autoClose.setText(value.autoCloseDescription().isEmpty()
            ? "自动关闭设置" : "自动关闭：" + value.autoCloseDescription());
        if (!value.canRegenerate() && !value.regenerateHint().isEmpty()) {
            regenerate.setText("暂不能领取");
            regenerate.setEnabled(false);
            hint.setText(value.regenerateHint());
        }
        if (value.configByProject()) note.setText("开关与自动关闭由学校统一设置，个人不能修改。");
        else note.setText("使用码只用于在设备键盘上开启，不会开始计费；费用仍按设备实际使用结算。");
    }

    /** 8 位码按 5+3 断开，和原版的「使用码 + 安全码」分组一致。 */
    private String prettify(String raw) {
        if (raw.length() == 8) return raw.substring(0, 5) + "  " + raw.substring(5);
        return raw;
    }

    private void toggle() {
        if (busy || current == null) return;
        if (current.enabled() && !current.codeWarning().isEmpty()) {
            confirm(current.codeWarning(), this::applyToggle);
            return;
        }
        applyToggle();
    }

    private void applyToggle() {
        if (busy) return;
        final boolean on = current == null || !current.enabled();
        working(true, on ? "正在开启…" : "正在关闭…");
        final int ticket = ++generation;
        worker.execute(() -> {
            try {
                UseCode updated = UseCodeApi.status(session, profile, on);
                post(ticket, () -> { working(false, null); render(updated); });
            } catch (Exception failure) {
                post(ticket, () -> { working(false, null); state.setText(error(failure)); });
            }
        });
    }

    private void issue() {
        if (busy || current == null) return;
        if (!current.canRegenerate()) { state.setText(current.regenerateHint()); return; }
        working(true, "正在领取使用码…");
        final int ticket = ++generation;
        worker.execute(() -> {
            try {
                UseCodeApi.Issued issued = UseCodeApi.generate(session, profile);
                post(ticket, () -> { working(false, null); showIssued(issued); });
            } catch (Exception failure) {
                post(ticket, () -> { working(false, null); state.setText(error(failure)); });
            }
        });
    }

    /** 领到不等于能用：要再调一次 set，服务器才会把码设为当前。倒计时按原版 180 秒走。 */
    private void showIssued(UseCodeApi.Issued issued) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(screen.dp(20), screen.dp(8), screen.dp(20), 0);
        TextView value = new TextView(this);
        value.setText(prettify(issued.code));
        value.setTextSize(30); value.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        value.setGravity(Gravity.CENTER); value.setTextColor(AppUi.INK); value.setTextIsSelectable(true);
        panel.addView(value);
        TextView left = new TextView(this);
        left.setText("有效期 3 分钟 · 还能领取 " + issued.remainTimes + " 次");
        left.setTextSize(13); left.setTextColor(AppUi.MUTED); left.setGravity(Gravity.CENTER);
        left.setPadding(0, screen.dp(8), 0, 0);
        panel.addView(left);
        AlertDialog dialog = Theme.dialog(this)
            .setTitle("领取使用码")
            .setView(panel)
            .setNegativeButton("稍后", null)
            .setPositiveButton("使用此码", (d, w) -> applyIssued(issued.code))
            .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        countdown = new CountDownTimer(VALID_SECONDS * 1000L, 1000L) {
            @Override public void onTick(long millis) {
                long seconds = (millis + 999) / 1000;
                left.setText(String.format("还剩 %d:%02d · 还能领取 %d 次",
                    seconds / 60, seconds % 60, issued.remainTimes));
            }
            @Override public void onFinish() {
                left.setText("已过期，请重新领取");
                Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (positive != null) positive.setEnabled(false);
            }
        }.start();
        dialog.setOnDismissListener(d -> { if (countdown != null) countdown.cancel(); });
    }

    private void applyIssued(String code) {
        if (busy) return;
        working(true, "正在启用使用码…");
        final int ticket = ++generation;
        worker.execute(() -> {
            try {
                UseCodeApi.apply(session, profile, code);
                UseCode updated = UseCodeApi.detail(session, profile);
                post(ticket, () -> { working(false, null); render(updated); });
            } catch (Exception failure) {
                post(ticket, () -> { working(false, null); state.setText(error(failure)); });
            }
        });
    }

    private void chooseAutoClose() {
        if (busy || current == null) return;
        working(true, null);
        final int ticket = ++generation;
        worker.execute(() -> {
            try {
                List<UseCodeApi.Option> options = UseCodeApi.options(session, profile);
                post(ticket, () -> { working(false, null); showOptions(options); });
            } catch (Exception failure) {
                post(ticket, () -> { working(false, null); state.setText(error(failure)); });
            }
        });
    }

    private void showOptions(List<UseCodeApi.Option> options) {
        if (options.isEmpty()) { state.setText("服务器没有返回自动关闭选项。"); return; }
        String[] labels = new String[options.size()];
        int checked = -1;
        for (int i = 0; i < options.size(); i++) {
            UseCodeApi.Option option = options.get(i);
            labels[i] = TextUtils.isEmpty(option.description) ? ("选项 " + option.id) : option.description;
            if (option.checked) checked = i;
        }
        final int selected = checked;
        Theme.dialog(this)
            .setTitle("自动关闭")
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                dialog.dismiss();
                saveOption(options.get(which));
            })
            .setNegativeButton("取消", null)
            .show();
        if (selected >= 0 && !TextUtils.isEmpty(options.get(selected).warnMark))
            hint.setText(options.get(selected).warnMark);
    }

    private void saveOption(UseCodeApi.Option option) {
        if (busy) return;
        working(true, "正在保存…");
        final int ticket = ++generation;
        worker.execute(() -> {
            try {
                UseCodeApi.saveOption(session, profile, option.id);
                UseCode updated = UseCodeApi.detail(session, profile);
                post(ticket, () -> { working(false, null); render(updated); });
            } catch (Exception failure) {
                post(ticket, () -> { working(false, null); state.setText(error(failure)); });
            }
        });
    }

    private void confirm(String message, Runnable action) {
        Theme.dialog(this)
            .setTitle("确认")
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定", (d, w) -> action.run())
            .show();
    }

    private String error(Exception failure) {
        if (failure instanceof org.json.JSONException) return "服务器响应格式与预期不符，请保留原版使用。";
        if (failure instanceof java.net.SocketTimeoutException) return "请求超时，请检查网络后重试。";
        if (failure instanceof javax.net.ssl.SSLException) return "安全连接失败，请检查网络和手机时间。";
        if (failure instanceof java.net.UnknownHostException || failure instanceof java.net.ConnectException)
            return "网络连接失败，请检查网络后重试。";
        if (failure instanceof java.io.IOException) {
            String message = failure.getMessage();
            if (message != null && message.length() <= 220) return message;
        }
        return "操作未完成，请稍后重试。";
    }
}
