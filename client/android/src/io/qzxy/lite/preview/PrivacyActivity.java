package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 数据与隐私：what this app keeps on the phone, where the network requests go, and how to wipe it.
 *
 * <p>The page exists because the app deliberately has no account server, so "your data" is entirely
 * local — which means the honest answer is a list of local files plus a delete button, not a policy
 * document. Anything that touches the keystore is deleted on a worker thread, matching
 * {@link AccountVault}'s requirement that its key operations never run on the main thread.</p>
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

    private void gap(LinearLayout parent, int size) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size)));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 18, AppUi.LINE));
        return card;
    }

    private TextView cardTitle(String value) {
        TextView title = text(value, 16, AppUi.INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(2));
        return title;
    }

    /** A label with its explanation underneath, separated by a hairline. */
    private void row(LinearLayout card, String label, String detail, boolean divider) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(9), 0, dp(9));
        block.addView(text(label, 14, AppUi.INK));
        TextView note = text(detail, 12, AppUi.MUTED);
        note.setPadding(0, dp(2), 0, 0);
        block.addView(note);
        card.addView(block, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        if (divider) {
            View rule = new View(this);
            rule.setBackgroundColor(AppUi.LINE);
            card.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        }
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

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUi.BG);
        final int side = AppUi.side(this, 14);
        root.setPadding(side, dp(8), side, 0);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(side + bars.left, dp(8) + bars.top, side + bars.right, bars.bottom);
                return insets;
            });
        }

        TextView back = text("‹ 返回", 15, AppUi.ACCENT);
        back.setPadding(dp(2), dp(10), dp(2), dp(10));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setMinHeight(dp(48));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView heading = text("数据与隐私", 26, AppUi.INK);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        TextView caption = text("本机存了什么、发到哪里、怎么删", 12, AppUi.MUTED);
        caption.setPadding(0, dp(2), 0, dp(12));
        root.addView(caption);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(16));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout stored = card();
        stored.addView(cardTitle("保存在这台手机上"));
        row(stored, "趣智校园登录会话",
            "加密文件，只有这台手机的系统密钥能解开；不保存明文密码", true);
        row(stored, "慧生活798 登录会话", "加密文件，同上", true);
        row(stored, "教务系统账号",
            "只在你主动保存过账号时存在；同样加密，学校拒绝过的密码会停止自动重试", true);
        row(stored, "每个课表的课程、开学日期、背景", "应用设置；课表可以有多个，各自独立存储，卸载即删", true);
        row(stored, "取件组件、公告偏好", "应用设置：桌面组件配置和「不再提醒」的选择", false);
        content.addView(stored);
        gap(content, 12);

        LinearLayout network = card();
        network.addView(cardTitle("不会离开这台手机"));
        TextView noServer = text("没有账号服务器，没有统计和广告，也没有第三方 SDK。"
            + "所有网络请求只发往下面这些地址：", 13, AppUi.MUTED);
        noServer.setPadding(0, dp(4), 0, dp(2));
        network.addView(noServer);
        row(network, "v3-api.china-qzxy.cn", "趣智校园：登录、余额、设备状态与用水启停", true);
        row(network, "i.ilife798.com", "慧生活798：登录、设备资料与用水启停", true);
        row(network, "jwglxxfwpt.hebeu.edu.cn", "教务系统：课表、成绩、空教室", true);
        row(network, "www.hebeu.edu.cn · kexin.hebeu.edu.cn", "学校官网，在应用内浏览", true);
        row(network, "淘宝 / 菜鸟", "只在你点「身份码」时交给系统或淘宝打开，应用本身不访问", false);
        content.addView(network);
        gap(content, 12);

        LinearLayout permissions = card();
        permissions.addView(cardTitle("权限"));
        row(permissions, "网络", "上面这些请求都需要它", true);
        row(permissions, "相机", "只用于扫码取景：不拍照、不录像、不保存画面", false);
        TextView none = text("没有申请位置、通讯录、短信读取和存储权限。", 12, AppUi.MUTED);
        none.setPadding(0, dp(10), 0, 0);
        permissions.addView(none);
        content.addView(permissions);
        gap(content, 12);

        LinearLayout clearing = card();
        clearing.addView(cardTitle("清除"));
        TextView explain = text("清除登录状态只删三份会话，课表保留；清除全部会把课表、背景和组件设置一并删除，"
            + "无法恢复，但课表之后可以从教务系统重新导入。", 13, AppUi.MUTED);
        explain.setPadding(0, dp(4), 0, 0);
        clearing.addView(explain);
        clearSessions = action("清除登录状态", true, () -> confirm(false));
        clearing.addView(clearSessions);
        clearAll = action("清除全部本机数据", true, () -> confirm(true));
        clearing.addView(clearAll);
        status = text("", 12, AppUi.MUTED);
        status.setPadding(0, dp(10), 0, 0);
        clearing.addView(status);
        content.addView(clearing);
        gap(content, 12);

        LinearLayout notice = card();
        notice.addView(cardTitle("使用公告"));
        TextView remember = text("公告在每次打开时显示，勾选「以后不再提醒」后停止。这里可以随时再看一遍。",
            13, AppUi.MUTED);
        remember.setPadding(0, dp(4), 0, 0);
        notice.addView(remember);
        notice.addView(action("重新查看使用公告", false, () -> NoticeDialog.showForReading(this)));
        content.addView(notice);

        setContentView(root);
        root.requestApplyInsets();
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
