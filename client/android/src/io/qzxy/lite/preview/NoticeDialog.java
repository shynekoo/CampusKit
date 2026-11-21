// SPDX-License-Identifier: MPL-2.0
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
    private static final int VERSION = 3;

    /** Set while the dialog is on screen, so the update check can wait its turn behind it. */
    private static volatile boolean showing;

    private static final String HEADLINE = "使用公告";
    private static final String SUBHEAD = "科信小助手是个人开发的第三方客户端";

    /**
     * 正文来自 {@link LegalText#NOTICE}：弹窗与「数据与隐私 → 阅读使用公告」整篇页读的是同一份文字，
     * 改文案只改一处。标题为空的那一节只排正文（开场白），不显示标题。
     */
    private static final String[][] SECTIONS = LegalText.NOTICE;

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

    /** 以只读方式弹出，不改动「不再提醒」的选择。当前入口（更多 / 数据与隐私）改为整篇的
     *  {@link NoticeActivity}，这里保留给需要在弹窗里复读的场景。 */
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
            int first = 0;
            if (!section[0].isEmpty()) {
                TextView heading = new TextView(activity);
                heading.setText(section[0]);
                heading.setTextSize(14);
                heading.setTypeface(null, Typeface.BOLD);
                heading.setTextColor(AppUi.INK);
                heading.setPadding(0, 10 * dp, 0, 3 * dp);
                body.addView(heading);
                first = 1;
            }
            for (int i = first; i < section.length; i++) {
                TextView paragraph = new TextView(activity);
                paragraph.setText(section[i]);
                paragraph.setTextSize(13);
                paragraph.setTextColor(AppUi.MUTED);
                paragraph.setLineSpacing(4 * dp, 1f);
                paragraph.setPadding(0, 0, 0, 6 * dp);
                body.addView(paragraph);
            }
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
        start.setTextColor(AppUi.ON_ACCENT);
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

        AlertDialog dialog = Theme.dialog(activity).setView(panel).create();
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
