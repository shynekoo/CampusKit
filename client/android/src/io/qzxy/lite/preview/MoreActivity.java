package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
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
 * 更多：the two things about this project that live outside the phone — where the source is, and
 * under what terms it may be used.
 *
 * <p>Both are one tap from the account page and neither needs the network to be understood, so the
 * licence is shown here in full rather than as a link that fails on a phone with no browser. The
 * repository is a link, because a copy of GitHub inside this app would be a second browser nobody
 * asked for; the page hands the address to whatever app already handles web pages.</p>
 */
public final class MoreActivity extends Activity {
    private static final String REPO = "https://github.com/shynekoo/kexin-xiaozhushou";

    private static final String MIT =
        "MIT License\n\n"
        + "Copyright (c) 2026 shynekoo\n\n"
        + "Permission is hereby granted, free of charge, to any person obtaining a copy of this "
        + "software and associated documentation files (the \"Software\"), to deal in the Software "
        + "without restriction, including without limitation the rights to use, copy, modify, merge, "
        + "publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons "
        + "to whom the Software is furnished to do so, subject to the following conditions:\n\n"
        + "The above copyright notice and this permission notice shall be included in all copies or "
        + "substantial portions of the Software.\n\n"
        + "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, "
        + "INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR "
        + "PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE "
        + "FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR "
        + "OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER "
        + "DEALINGS IN THE SOFTWARE.";

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
        card.setBackground(AppUi.outlined(this, 0xffffffff, 18, AppUi.LINE));
        return card;
    }

    /** One row: icon, title, the address or licence name underneath, chevron on the right. */
    private void entry(LinearLayout parent, String icon, String title, String subtitle, boolean divider,
                       Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        row.setMinimumHeight(dp(56));
        row.setFocusable(true);
        row.setContentDescription(title + "，" + subtitle);

        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageDrawable(AppUi.icon(this, icon, AppUi.ACCENT, 22));
        mark.setBackground(AppUi.shape(this, AppUi.SOFT, 11));
        mark.setPadding(dp(8), dp(8), dp(8), dp(8));
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, dp(8), 0);
        TextView name = text(title, 16, AppUi.INK);
        name.setTypeface(null, Typeface.BOLD);
        labels.addView(name);
        TextView caption = text(subtitle, 12, AppUi.MUTED);
        caption.setPadding(0, dp(2), 0, 0);
        labels.addView(caption);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView chevron = text("›", 20, 0xffb3bcc9);
        row.addView(chevron);

        AppUi.press(row, AppUi.SURFACE, 12);
        row.setOnClickListener(v -> action.run());
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

    private void openRepository() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPO)));
        } catch (ActivityNotFoundException noBrowser) {
            // A phone with no browser cannot follow a link, and a silent tap reads as a broken row.
            Toast.makeText(this, REPO, Toast.LENGTH_LONG).show();
        } catch (RuntimeException blocked) {
            Toast.makeText(this, REPO, Toast.LENGTH_LONG).show();
        }
    }

    private void showLicence() {
        TextView body = text(MIT, 13, AppUi.INK);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(dp(18), dp(8), dp(18), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this)
            .setTitle("MIT License")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show();
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

        TextView heading = text("更多", 26, AppUi.INK);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        TextView caption = text("源码与许可证", 12, AppUi.MUTED);
        caption.setPadding(0, dp(2), 0, dp(12));
        root.addView(caption);

        LinearLayout card = card();
        entry(card, "globe", "本项目 GitHub 仓库", "github.com/shynekoo/kexin-xiaozhushou", true, this::openRepository);
        entry(card, "score", "查看开源许可证", "MIT License", false, this::showLicence);
        root.addView(card);

        TextView note = text("客户端源码公开在 GitHub。第三方组件（ZXing、PDFBox、支付宝 SDK）"
            + "各自的许可证见仓库中的 client/THIRD_PARTY.md。", 12, AppUi.MUTED);
        note.setPadding(dp(4), dp(14), dp(4), 0);
        root.addView(note);

        TextView version = text("科信小助手 " + AppUi.version(this), 12, AppUi.MUTED);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(24), 0, dp(16));
        root.addView(version);

        setContentView(root);
        root.requestApplyInsets();
    }
}
