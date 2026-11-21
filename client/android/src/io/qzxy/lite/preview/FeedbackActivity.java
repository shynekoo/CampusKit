// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 意见反馈：two ways to reach the person who builds this, both of them copied on tap.
 *
 * <p>The app has no account server and no crash reporter, so the only feedback channel is a person.
 * Printing an address nobody can select with one thumb is close to printing nothing, so each row is
 * a copy button: the address goes to the clipboard, and the reader pastes it wherever they write.</p>
 */
public final class FeedbackActivity extends Page {
    private static final String EMAIL = "shynekoo@outlook.com";
    private static final String GROUP = "1136506694";

    private int dp(int n) { return AppUi.dp(this, n); }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(4), 1f);
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 18, AppUi.LINE));
        return card;
    }

    /** One copyable contact: label, the address itself, and a word saying a tap copies it. */
    private void contact(LinearLayout parent, String icon, String label, String value, boolean divider) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        row.setMinimumHeight(dp(56));
        row.setFocusable(true);
        row.setContentDescription(label + " " + value + "，点按复制");

        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageDrawable(AppUi.icon(this, icon, AppUi.ACCENT, 22));
        mark.setBackground(AppUi.shape(this, AppUi.SOFT, 11));
        mark.setPadding(dp(8), dp(8), dp(8), dp(8));
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, dp(8), 0);
        TextView name = text(label, 12, AppUi.MUTED);
        labels.addView(name);
        TextView address = text(value, 16, AppUi.INK);
        address.setTypeface(null, Typeface.BOLD);
        // Not selectable on purpose: a selectable TextView takes the touch for its own selection
        // handles, and a tap on the address would then do nothing — the row is the copy button, and
        // the address is exactly where a thumb lands.
        labels.addView(address);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView action = text("复制", 14, AppUi.ACCENT);
        action.setTypeface(null, Typeface.BOLD);
        action.setPadding(dp(10), dp(8), dp(2), dp(8));
        action.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(action);

        AppUi.press(row, AppUi.SURFACE, 12);
        row.setOnClickListener(v -> copy(label, value));
        parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        if (divider) {
            View rule = new View(this);
            rule.setBackgroundColor(AppUi.LINE);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1)));
            params.setMargins(dp(52), 0, 0, 0);
            parent.addView(rule, params);
        }
    }

    private void copy(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) { toast("无法访问剪贴板，请手动记录：" + value); return; }
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        } catch (RuntimeException blocked) {
            // Some ROMs refuse clipboard writes from a background-ish context; say the value again
            // rather than letting the tap look like it did nothing.
            toast("复制未成功，请手动记录：" + value);
            return;
        }
        // Android 13 起系统自己会弹一次「已复制」，再弹一次就是两条叠在一起。
        if (Build.VERSION.SDK_INT < 33) toast("已复制" + label + "：" + value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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

        TextView heading = text("意见反馈", 26, AppUi.INK);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        TextView caption = text("问题、建议、想加的功能，都可以直接说", 12, AppUi.MUTED);
        caption.setPadding(0, dp(2), 0, dp(12));
        root.addView(caption);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(16));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout ways = card();
        contact(ways, "mail", "邮箱", EMAIL, true);
        contact(ways, "group", "QQ 群", GROUP, false);
        content.addView(ways);

        TextView version = text("科信小助手 " + AppUi.version(this), 12, AppUi.MUTED);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(18), 0, 0);
        content.addView(version);

        setContentView(root);
        root.requestApplyInsets();
    }
}
