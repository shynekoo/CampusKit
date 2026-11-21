// SPDX-License-Identifier: MPL-2.0
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
        "edu-account-state", "life798-login-ui", "notice-v1", "update-v1",
        // 统计的同意状态、安装标识与「今天报过没有」：清除全部时一并清掉，等于退出统计。
        AnalyticsPreferences.PREFS
    };

    private TextView status;
    private TextView analyticsStatus;
    private Button clearSessions, clearAll, forgetAnalytics;

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

    /**
     * 匿名使用统计：本页是「已经发出去的东西」唯一能删的地方，所以它必须有一张自己的卡。
     *
     * <p>开关不在这里：同意与撤销都在「更多 → 匿名使用统计」（同一个人做决定的地方只有一处，
     * 两个开关迟早会显示成两种状态）。这里负责说清现在是什么状态，并提供删除已上传记录的动作。</p>
     */
    private LinearLayout analyticsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 18, AppUi.LINE));

        TextView title = text("匿名使用统计", 16, AppUi.INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(2));
        card.addView(title);

        TextView explain = text("默认关闭，开关在「校园 → 更多 → 匿名使用统计」。开启后每天最多向开发者"
            + "自建的统计服务发送一次随机安装标识与版本号，用于统计使用人数；不含学号、姓名、手机号、"
            + "课表或任何登录凭据，服务端保留 90 天。统计走独立云服务，教务、余额、用水等请求仍由"
            + "手机直连各自官方平台。", 13, AppUi.MUTED);
        card.addView(explain);

        analyticsStatus = text("", 12, AppUi.MUTED);
        analyticsStatus.setPadding(0, dp(10), 0, 0);
        card.addView(analyticsStatus);

        forgetAnalytics = action("删除已上传的匿名记录", true, this::confirmForget);
        card.addView(forgetAnalytics);
        refreshAnalytics();
        return card;
    }

    /** 状态行与按钮可用性跟着偏好走：没开过统计就没有可删的东西。 */
    private void refreshAnalytics() {
        if (analyticsStatus == null) return;
        if (!AnalyticsPreferences.configured()) {
            analyticsStatus.setText("统计服务尚未配置，本版不会上报任何数据。");
            forgetAnalytics.setEnabled(false);
            return;
        }
        boolean consented = AnalyticsPreferences.consented(this);
        boolean pending = AnalyticsPreferences.forgetWaiting(this);
        analyticsStatus.setText(consented ? "当前：已开启，每天最多上报一次。"
            : pending ? "当前：已关闭，历史记录待删除。" : "当前：已关闭。");
        forgetAnalytics.setEnabled(consented || pending || AnalyticsPreferences.installId(this) != null);
    }

    private void confirmForget() {
        if (AnalyticsPreferences.installId(this) == null) {
            analyticsStatus.setText("本机没有统计标识，服务端也没有这个安装的记录。");
            forgetAnalytics.setEnabled(false);
            return;
        }
        final boolean stillOn = AnalyticsPreferences.consented(this);
        Theme.dialog(this)
            .setTitle("删除已上传的匿名记录？")
            .setMessage("会请求统计服务删掉这个安装的全部历史记录。"
                + (stillOn
                    ? "统计目前仍处于开启状态，删除后标识保留，今天起重新开始记录；要彻底停掉请先在「更多」里关闭。"
                    : "统计保持关闭；重新开启会使用新的匿名标识。"))
            .setNegativeButton("取消", null)
            .setPositiveButton("删除", (dialog, which) -> forgetAnalytics())
            .show();
    }

    private void forgetAnalytics() {
        forgetAnalytics.setEnabled(false);
        analyticsStatus.setText("正在请求删除…");
        new Thread(() -> {
            final boolean removed = AnalyticsReporter.forget(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                analyticsStatus.setText(removed
                    ? "已删除该安装的匿名记录。"
                    : "删除请求没有成功，请确认网络后重试；重新开启统计会继续沿用原标识。");
                forgetAnalytics.setEnabled(AnalyticsPreferences.installId(this) != null);
            });
        }, "analytics-forget").start();
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
        // 隐私说明在 §7 讲「怎么删」，这里是真正能按的几个按钮，紧跟在说明后面。
        content.addView(analyticsCard());
        View analyticsGap = new View(this);
        content.addView(analyticsGap, new LinearLayout.LayoutParams(1, dp(12)));
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
                // 清掉本机的统计偏好之前，先尽力删掉服务端那条记录：标识一旦删掉，就再也找不到它了。
                // 删不掉也不拦住清除（用户要的是「现在清干净」），下次开启统计会是新的标识。
                try { AnalyticsReporter.forget(context); } catch (Exception failure) { failed.append("匿名统计记录 "); }
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
