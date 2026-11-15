package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.qzxy.lite.core.AccountSession;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Top-up screen for the 趣智校园 campus wallet.
 *
 * <p>Money is never charged here. The screen only asks the server for an Alipay order, then hands it
 * to the Alipay app, where the user confirms with their own password. The balance is re-read after
 * returning, so "充值成功" is only ever shown when the server reports a higher balance — never
 * because an intent was launched.</p>
 */
public final class RechargeActivity extends Activity {
    private static final String[] PRESETS = {"20", "50", "100", "300"};
    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DeviceScreen screen;
    private TextView balance, hint, amountLabel;
    private EditText custom;
    private Button pay, login;
    private LinearLayout presets;
    private String amount = PRESETS[1];
    private int generation;
    private boolean busy;
    private boolean awaitingPayment;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        screen = new DeviceScreen(this, "充值");
        screen.icon("↻", "刷新余额", this::load);
        balance = screen.text(screen.card, "正在读取余额…", 18);
        balance.setTypeface(null, Typeface.BOLD);
        hint = screen.text(screen.card, "", 13);
        hint.setTextColor(AppUi.MUTED);

        screen.text(screen.card, "充值金额（元）", 14);
        presets = new LinearLayout(this);
        presets.setPadding(0, dp(8), 0, dp(4));
        screen.card.addView(presets);
        chipRow();

        custom = new EditText(this);
        custom.setHint("其他金额，1–1000 的整数");
        custom.setInputType(InputType.TYPE_CLASS_NUMBER);
        custom.setTextSize(16);
        custom.setSingleLine(true);
        custom.setPadding(dp(14), dp(10), dp(14), dp(10));
        custom.setBackground(AppUi.outlined(this, 0xffffffff, 12, AppUi.LINE));
        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(-1, -2);
        customParams.topMargin = dp(8);
        screen.card.addView(custom, customParams);

        amountLabel = screen.text(screen.card, "将充值 ¥" + amount, 14);
        amountLabel.setTextColor(AppUi.ACCENT);

        screen.text(screen.card, "支付方式", 14);
        TextView alipay = screen.text(screen.card, "支付宝", 16);
        alipay.setPadding(0, dp(6), 0, dp(2));
        TextView other = screen.text(screen.card,
            "微信支付、银联、校园卡等通道在原版里依赖各自 SDK，本客户端无法发起。需要这些通道时请在趣智校园原版中充值。", 13);
        other.setTextColor(AppUi.MUTED);

        pay = screen.primary("去支付宝支付", this::confirm);
        login = screen.primary("登录趣智校园", () ->
            startActivity(new Intent(this, AccountActivity.class).putExtra("returnToAccountInfo", true)));
        screen.note("付款在支付宝内完成，本应用不保存支付密码，也不会自动重复下单。");
        pay.setEnabled(false);
        login.setVisibility(View.GONE);
    }

    private void chipRow() {
        presets.removeAllViews();
        for (String value : PRESETS) {
            boolean selected = value.equals(amount);
            TextView chip = new TextView(this);
            chip.setText(value + " 元");
            chip.setTextSize(15);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(44));
            chip.setPadding(dp(6), dp(10), dp(6), dp(10));
            chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setTextColor(selected ? 0xffffffff : AppUi.INK);
            chip.setBackground(selected ? AppUi.shape(this, AppUi.ACCENT, 12)
                : AppUi.outlined(this, 0xffffffff, 12, AppUi.LINE));
            chip.setContentDescription("充值 " + value + " 元" + (selected ? "，已选中" : ""));
            chip.setOnClickListener(v -> pick(value));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            presets.addView(chip, params);
        }
    }

    private void pick(String value) {
        amount = value;
        custom.setText("");
        chipRow();
        amountLabel.setText("将充值 ¥" + amount);
    }

    private String chosen() {
        String typed = custom.getText().toString().trim();
        return typed.isEmpty() ? amount : typed;
    }

    private void busy(boolean value) {
        busy = value;
        pay.setEnabled(!value);
        login.setEnabled(!value);
        screen.loading(value);
    }

    private void load() {
        if (busy) return;
        busy(true);
        final int ticket = ++generation;
        worker.execute(() -> {
            AccountSession session = null;
            try {
                session = new AccountVault(this).load();
                if (session == null) {
                    post(ticket, () -> { balance.setText("尚未登录"); hint.setText("登录趣智校园后可以查询余额并充值。"); pay.setVisibility(View.GONE); login.setVisibility(View.VISIBLE); });
                    return;
                }
                final AccountSession signedIn = session;
                AccountProfile profile = SmsAccountApi.profile(signedIn);
                post(ticket, () -> { balance.setText("正在读取余额…"); hint.setText(profile.description()); });
                String text = SmsAccountApi.wallet(signedIn, profile);
                post(ticket, () -> { balance.setText(text); pay.setVisibility(View.VISIBLE); login.setVisibility(View.GONE); });
            } catch (HttpForm.SessionExpired expired) {
                try { AccountVault.clearExpired(this, session); } catch (Exception ignored) { }
                post(ticket, () -> { balance.setText("登录已失效"); hint.setText("请重新登录后再充值。"); pay.setVisibility(View.GONE); login.setVisibility(View.VISIBLE); });
            } catch (Exception failure) {
                final boolean hadSession = session != null;
                post(ticket, () -> {
                    if (hadSession) { balance.setText("暂时无法读取余额"); hint.setText("余额查询失败，仍可尝试充值；请先确认本机登录是否有效。"); }
                    else { balance.setText("尚未登录"); hint.setText("登录趣智校园后可以查询余额并充值。"); }
                    pay.setVisibility(hadSession ? View.VISIBLE : View.GONE);
                    login.setVisibility(hadSession ? View.GONE : View.VISIBLE);
                });
            } finally {
                post(ticket, () -> busy(false));
            }
        });
    }

    private void confirm() {
        if (busy) return;
        String value;
        try { value = RechargeApi.normalize(chosen()); }
        catch (IllegalArgumentException invalid) { hint.setText(invalid.getMessage()); return; }
        hint.setText("");
        new AlertDialog.Builder(this)
            .setTitle("确认充值")
            .setMessage("将创建一笔 ¥" + value + " 的充值订单，随后在支付宝中由你本人确认付款。\n\n未在支付宝完成付款不会扣款。")
            .setNegativeButton("取消", null)
            .setPositiveButton("去支付宝", (d, w) -> create(value))
            .show();
    }

    private void create(String value) {
        if (busy) return;
        busy(true);
        hint.setText("正在创建订单…");
        final int ticket = ++generation;
        worker.execute(() -> {
            try {
                AccountSession session = new AccountVault(this).load();
                if (session == null) throw new HttpForm.SessionExpired();
                AccountProfile profile = SmsAccountApi.profile(session);
                boolean migrated = SmsAccountApi.migrated(session, profile);
                String order = RechargeApi.alipayOrder(session, profile, value, migrated);
                post(ticket, () -> { busy(false); pay(order); });
            } catch (HttpForm.SessionExpired expired) {
                post(ticket, () -> { busy(false); hint.setText("登录已失效，请重新登录后再试。"); });
            } catch (Exception failure) {
                String message = failure.getMessage();
                post(ticket, () -> { busy(false); hint.setText(message == null || message.isEmpty() ? "订单创建失败，未发起支付。" : "未能创建订单：" + message); });
            }
        });
    }

    /** Hands the signed order to Alipay. This does not pay by itself; the user still confirms there. */
    private void pay(String order) {
        awaitingPayment = true;
        hint.setText("已在支付宝中打开支付页。完成付款后返回本页会自动刷新余额。");
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("alipays://platformapi/startapp?appId=20000067&url=" + Uri.encode(order)));
            intent.setPackage(ALIPAY_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException missing) {
            awaitingPayment = false;
            // A payment link is only useful with the Alipay app present.
            hint.setText("未找到支付宝，无法完成支付。这笔订单未支付，不会扣款。");
        } catch (RuntimeException blocked) {
            awaitingPayment = false;
            hint.setText("系统阻止了跳转支付宝。这笔订单未支付，不会扣款。");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (awaitingPayment) { awaitingPayment = false; load(); }
        else if (!busy && screen != null) load();
    }

    private void post(int ticket, Runnable task) {
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing() && ticket == generation) task.run(); });
    }

    @Override protected void onDestroy() { generation++; worker.shutdown(); super.onDestroy(); }
}
