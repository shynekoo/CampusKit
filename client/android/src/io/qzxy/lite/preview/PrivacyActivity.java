package io.qzxy.lite.preview;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 数据与隐私：完整的隐私说明，加上「本机怎么清」的那两个按钮。
 *
 * <p>正文来自 {@link LegalText#PRIVACY}，与「使用公告」共用同一套排版（{@link LegalView}）——
 * 全部是打包进 APK 的静态文字，没有 WebView、没有网络请求，所以断网也能读。清除动作本身由
 * {@link AccountVault} / {@link LifeSessionVault} / {@link EduVault} 在 worker 线程完成，
 * 这部分逻辑未随文案改动。</p>
 */
public final class PrivacyActivity extends Page {
    private static final int DANGER = 0xffb42318;
    /**
     * Everything the app writes as settings; cleared only by the "all" action.
     * pickup-widget no longer has a writer (the Cainiao widget host was removed), but installs from
     * before 2.1.1 still carry its id, so it stays in the wipe list.
     */
    private static final String[] SETTINGS = {
        "campus-v1", "pickup-widget", "water-history",
        "edu-account-state", "life798-login-ui", "notice-v1", "update-v1"
    };

    private TextView status;
    private Button clearSessions, clearAll;

    private int dp(int n) { return AppUi.dp(this, n); }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(4), 1f);
        return view;
    }

    private Button action(String label, boolean danger, Runnable task) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(danger ? DANGER : AppUi.ACCENT);
        button.setMinHeight(dp(50));
        button.setBackground(danger
            ? AppUi.outlined(this, AppUi.SURFACE, 14, 0xfff0c2b8)
            : AppUi.outlined(this, AppUi.SOFT, 14, AppUi.LINE));
        button.setOnClickListener(v -> task.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        button.setLayoutParams(params);
        return button;
    }

    /** 清除卡片：说明 + 两个既有动作。文案改动不碰这里的行为。 */
    private LinearLayout clearingCard() {
        LinearLayout clearing = new LinearLayout(this);
        clearing.setOrientation(LinearLayout.VERTICAL);
        clearing.setPadding(dp(18), dp(16), dp(18), dp(16));
        clearing.setBackground(AppUi.outlined(this, AppUi.SURFACE, 18, AppUi.LINE));

        TextView title = text("清除本机数据", 16, AppUi.INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(2));
        clearing.addView(title);

        TextView explain = text("清除登录状态只删趣智校园、慧生活798、教务系统三份会话，课表保留；"
            + "清除全部会把课表、背景和组件设置一并删除，无法恢复，但课表之后可以从教务系统重新导入。",
            13, AppUi.MUTED);
        explain.setPadding(0, 0, 0, 0);
        clearing.addView(explain);

        clearSessions = action("清除登录状态", true, () -> confirm(false));
        clearing.addView(clearSessions);
        clearAll = action("清除全部本机数据", true, () -> confirm(true));
        clearing.addView(clearAll);

        status = text("", 12, AppUi.MUTED);
        status.setPadding(0, dp(10), 0, 0);
        clearing.addView(status);
        return clearing;
    }

    /** 使用公告的独立入口卡片：整篇公告在本页正文之后另开一页读，比塞进对话框好读。 */
    private LinearLayout noticeCard() {
        LinearLayout notice = new LinearLayout(this);
        notice.setOrientation(LinearLayout.VERTICAL);
        notice.setPadding(dp(18), dp(16), dp(18), dp(16));
        notice.setBackground(AppUi.outlined(this, AppUi.SURFACE, 18, AppUi.LINE));

        TextView title = text("使用公告", 16, AppUi.INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(2));
        notice.addView(title);

        TextView remember = text("公告在每次打开时显示，勾选「以后不再提醒」后停止。这里可以随时再看一遍。",
            13, AppUi.MUTED);
        notice.addView(remember);
        notice.addView(action("阅读使用公告", false,
            () -> startActivity(new Intent(this, NoticeActivity.class))));
        return notice;
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LegalView.Screen screen = LegalView.screen(this, "数据与隐私",
            "本机存了什么、发到哪里、怎么删");
        LinearLayout content = screen.content;

        for (String[] section : LegalText.PRIVACY) LegalView.section(this, content, section);
        // 隐私说明在 §6 讲「怎么删」，这里是真正能按的两个按钮，紧跟在说明后面。
        content.addView(clearingCard());
        View gap = new View(this);
        content.addView(gap, new LinearLayout.LayoutParams(1, dp(12)));
        content.addView(noticeCard());

        setContentView(screen.root);
        screen.root.requestApplyInsets();
    }

    private void confirm(final boolean everything) {
        Theme.dialog(this)
            .setTitle(everything ? "清除全部本机数据？" : "清除登录状态？")
            .setMessage(everything
                ? "登录状态、课表、开学日期、自定义背景、桌面组件与公告偏好都会删除，无法恢复。"
                : "趣智校园、慧生活798、教务系统的登录状态会被删除，课表和其他设置保留。下次使用需要重新登录。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清除", (dialog, which) -> wipe(everything))
            .show();
    }

    private void wipe(final boolean everything) {
        if (status == null) return;
        status.setText("正在清除…");
        clearSessions.setEnabled(false);
        clearAll.setEnabled(false);
        final Context context = getApplicationContext();
        new Thread(() -> {
            StringBuilder failed = new StringBuilder();
            try { new AccountVault(context).clear(); } catch (Exception failure) { failed.append("趣智校园会话 "); }
            try { new LifeSessionVault(context).clear(); } catch (Exception failure) { failed.append("慧生活798 会话 "); }
            try { new EduVault(context).clear(); } catch (Exception failure) { failed.append("教务账号 "); }
            // Deleting the stored secret is not enough: EduClient keeps the signed-in cookie and
            // the account in static fields, and would happily answer the next query without them.
            try {
                EduClient.cancelSignOut();
                EduClient.signOut();
                EduClient.remember("", "");
            } catch (Exception failure) { failed.append("教务登录态 "); }
            if (everything) {
                try { new PendingUseVault(context).clear(); } catch (Exception failure) { failed.append("未结用水记录 "); }
                TimetableStore.wipe(context);
                for (String name : SETTINGS) context.getSharedPreferences(name, MODE_PRIVATE).edit().clear().commit();
            }
            final String problem = failed.toString();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                clearSessions.setEnabled(true);
                clearAll.setEnabled(true);
                // WebView cookies would be copied back into EduClient on the next visit.
                CookieManager.getInstance().removeAllCookies(ok -> CookieManager.getInstance().flush());
                WebStorage.getInstance().deleteAllData();
                if (!problem.isEmpty()) {
                    status.setText("以下内容没能清除：" + problem + "。请重试，或卸载应用后重装。");
                    return;
                }
                status.setText(everything ? "已清除全部本机数据。" : "已清除。三个账号下次使用都需要重新登录。");
                // Screens behind this one cached their signed-in state; rebuild the home screen so
                // both the login state and (for the full wipe) the timetable are re-read.
                Intent home = new Intent(this, TimetableActivity.class);
                home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(home);
            });
        }, "privacy-wipe").start();
    }
}
