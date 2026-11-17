package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 使用公告：the one screen everybody reads on the first launch and then, mostly, never again.
 *
 * <p>It is shown on every start until the reader ticks 以后不再提醒, which is stored as a version
 * number rather than a boolean: bumping {@link #VERSION} after a wording change brings the notice
 * back for everyone, including people who had silenced the previous text.</p>
 *
 * <p>The dialog is cancellable, and cancelling deliberately writes nothing — the next start shows it
 * again. Only the button records a choice, so a stray tap outside the dialog never counts as
 * consent to stop showing it.</p>
 */
final class NoticeDialog {
    private static final String PREFS = "notice-v1";
    private static final String KEY_SILENCED = "silenced";
    /** Raise this when the text below changes enough that it should be read again. */
    private static final int VERSION = 2;

    /** Set while the dialog is on screen, so the update check can wait its turn behind it. */
    private static volatile boolean showing;

    private static final String HEADLINE = "使用公告";
    private static final String SUBHEAD = "科信小助手是个人开发的第三方客户端";

    /** Sub-heading and body, in reading order. Kept short so the dialog fits a 5-inch screen. */
    private static final String[][] SECTIONS = {
        {"它不是官方应用",
            "科信小助手由个人开发，与趣智校园、慧生活798、河北工程大学及科信学院都没有隶属关系，"
            + "也没有获得授权或背书。文中出现的名称只用来说明它连接的是哪项服务。"},
        {"账号只存在这台手机上",
            "手机号和验证码只用于登录趣智校园。登录成功后保存的是用系统密钥库加密的会话，"
            + "不保存明文密码；慧生活798、教务系统的凭据同理。它们都在应用私有目录里，其他应用读不到。"},
        {"课表来自学校，也只写在本机",
            "课表、成绩、空教室通过你自己的教务系统登录态获取，导入后写入本机，"
            + "供课表页和桌面组件使用，不会回传到任何第三方。"},
        {"没有服务器，也没有统计",
            "应用不设后端，不上传、不汇总、不分享任何数据，没有广告和埋点。"
            + "网络请求只发往趣智校园、慧生活798、学校教务系统，以及你主动点开的淘宝/菜鸟身份码页面。"},
        {"扫码会真的开始用水",
            "扫浴室、饮水机的设备码不是预览：设备会被启动，当场出水或运行，费用由趣智校园、"
            + "慧生活按实际用量结算，可能直接从余额或绑定的支付方式里扣。启动这一步通常由支付宝或"
            + "慧生活原版完成，本应用只负责把设备交给它们；本应用不充值、不代下单，但水是你在用。"
            + "设备不在眼前时不要扫，要停下请立即结束用水，或改在原版操作。"},
        {"随时可以清除",
            "「校园 → 更多 → 数据与隐私」列出了本机保存的每一项，并支持一键清除登录状态、课表与设置。"
            + "卸载应用同样会删掉这些数据。"},
        {"风险由使用者承担",
            "使用第三方客户端可能不符合对方的用户协议，由此带来的账号风险由使用者自行承担。"
            + "请勿用于商业用途或批量操作。"},
    };

    private NoticeDialog() {}

    /** True while a notice dialog is up. Two dialogs stacked on the first screen is one too many. */
    static boolean showing() { return showing; }

    /** True until the reader has ticked 以后不再提醒 for the current wording. */
    static boolean wanted(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SILENCED, 0) < VERSION;
    }

    /** Called from the home screen: only shows the notice when it has not been silenced. */
    static void showIfNeeded(Activity activity) {
        if (wanted(activity)) show(activity, true);
    }

    /** Called from 数据与隐私: always shows, and leaves the silence choice exactly as it was. */
    static void showForReading(Activity activity) {
        show(activity, false);
    }

    private static void show(Activity activity, boolean recordChoice) {
        int dp = AppUi.dp(activity, 1);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(22 * dp, 18 * dp, 22 * dp, 12 * dp);

        TextView title = new TextView(activity);
        title.setText(HEADLINE);
        title.setTextSize(21);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(AppUi.INK);
        panel.addView(title);

        TextView subhead = new TextView(activity);
        subhead.setText(SUBHEAD);
        subhead.setTextSize(12);
        subhead.setTextColor(AppUi.MUTED);
        subhead.setPadding(0, 2 * dp, 0, 10 * dp);
        panel.addView(subhead);

        // Scrolls only when the wording outgrows the space the dialog can safely take.
        ScrollView scroll = new CappedScroll(activity, (int) (activity.getResources()
            .getDisplayMetrics().heightPixels * 0.52));
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 2 * dp, 0);
        for (String[] section : SECTIONS) {
            TextView heading = new TextView(activity);
            heading.setText(section[0]);
            heading.setTextSize(14);
            heading.setTypeface(null, Typeface.BOLD);
            heading.setTextColor(AppUi.INK);
            heading.setPadding(0, 10 * dp, 0, 3 * dp);
            body.addView(heading);

            TextView paragraph = new TextView(activity);
            paragraph.setText(section[1]);
            paragraph.setTextSize(13);
            paragraph.setTextColor(AppUi.MUTED);
            paragraph.setLineSpacing(4 * dp, 1f);
            body.addView(paragraph);
        }
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        final CheckBox quiet = new CheckBox(activity);
        quiet.setText("以后不再提醒");
        quiet.setTextSize(13);
        quiet.setTextColor(AppUi.MUTED);
        quiet.setButtonTintList(android.content.res.ColorStateList.valueOf(AppUi.ACCENT));
        quiet.setChecked(!wanted(activity));

        Button start = new Button(activity);
        start.setText("知道了，开始使用");
        start.setAllCaps(false);
        start.setTextSize(16);
        start.setTextColor(0xffffffff);
        start.setMinHeight(50 * dp);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(AppUi.ACCENT);
        fill.setCornerRadius(14 * dp);
        start.setBackground(fill);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        startParams.topMargin = 14 * dp;
        panel.addView(start, startParams);
        panel.addView(quiet, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(panel).create();
        start.setOnClickListener(v -> {
            if (recordChoice) {
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putInt(KEY_SILENCED, quiet.isChecked() ? VERSION : 0).apply();
            }
            dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> showing = false);
        showing = true;
        dialog.show();
    }

    /** A ScrollView that never grows past the height the dialog can afford. */
    private static final class CappedScroll extends ScrollView {
        private final int cap;

        CappedScroll(Context context, int cap) {
            super(context);
            this.cap = Math.max(AppUi.dp(context, 160), cap);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
        }
    }
}
