package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 运行记录页：把 {@link Diag} 写下的内容原样摊开，并给一个「复制全部」。
 *
 * <p>出口只能是文本。慧生活登录页带 {@code FLAG_SECURE}，系统层面就截不了图，所以截图这条
 * 路从一开始就不存在——复制到剪贴板、粘进微信，是这个页面上唯一能把记录送出去的动作。</p>
 *
 * <p>打开时自动滚到底部：使用者要看的是「最后停在哪一行」，而不是开机的机器快照。字号固定
 * 11sp 等宽，宁可让记录显得密，也不做任何折叠和分页——按环节过滤会让人以为没记录。</p>
 */
public final class DiagActivity extends Activity {
    private TextView body, meta;
    private ScrollView scroll;

    private int dp(int n) { return AppUi.dp(this, n); }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
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
                v.setPadding(side + bars.left, dp(8) + bars.top, side + bars.right, dp(6));
                return insets;
            });
        }

        TextView back = text("‹ 返回", 15, AppUi.ACCENT);
        back.setPadding(dp(2), dp(10), dp(2), dp(10));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setMinHeight(dp(48));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView heading = text("运行记录", 26, AppUi.INK);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        TextView caption = text("手机上记的这几步怎么走的。不含手机号和授权码，可以放心发出去。", 12, AppUi.MUTED);
        caption.setLineSpacing(dp(3), 1f);
        caption.setPadding(0, dp(2), 0, dp(6));
        root.addView(caption);

        meta = text("", 11, AppUi.MUTED);
        meta.setPadding(dp(2), 0, 0, dp(6));
        root.addView(meta);

        body = text("", 11, AppUi.INK);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(2), 1f);
        body.setPadding(dp(14), dp(12), dp(14), dp(12));
        body.setBackground(AppUi.outlined(this, 0xffffffff, 14, AppUi.LINE));

        scroll = new ScrollView(this);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(8), 0, dp(4));

        Button copy = new Button(this);
        copy.setText("复制全部");
        copy.setAllCaps(false);
        copy.setTextSize(15);
        copy.setTextColor(0xffffffff);
        copy.setBackground(AppUi.shape(this, AppUi.ACCENT, 12));
        copy.setOnClickListener(v -> copyAll());
        bar.addView(copy, new LinearLayout.LayoutParams(0, dp(46), 1));

        TextView share = text("分享", 15, AppUi.ACCENT);
        share.setGravity(Gravity.CENTER);
        share.setPadding(dp(16), dp(10), dp(10), dp(10));
        share.setMinHeight(dp(46));
        AppUi.press(share, 0x00ffffff, 10);
        share.setOnClickListener(v -> shareAll());
        bar.addView(share);

        TextView clear = text("清空", 15, AppUi.MUTED);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(10), dp(10), dp(2), dp(10));
        clear.setMinHeight(dp(46));
        AppUi.press(clear, 0x00ffffff, 10);
        clear.setOnClickListener(v -> confirmClear());
        bar.addView(clear);

        root.addView(bar);
        setContentView(root);
        root.requestApplyInsets();

        render(true);
    }

    private void render(boolean toBottom) {
        String text = Diag.dump();
        body.setText(text);
        int lines = text.isEmpty() ? 0 : text.split("\n", -1).length;
        meta.setText(lines + " 行 · " + Math.max(1, text.length() / 1024) + " KB");
        if (toBottom) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void copyAll() {
        String text = Diag.dump();
        if (text.isEmpty()) { toast("还没有记录"); return; }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) { toast("无法访问剪贴板"); return; }
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText("运行记录", text));
        } catch (RuntimeException blocked) {
            toast("复制未成功，请长按文字手动选取");
            return;
        }
        if (Build.VERSION.SDK_INT < 33) toast("已复制全部记录");
    }

    private void shareAll() {
        String text = Diag.dump();
        if (text.isEmpty()) { toast("还没有记录"); return; }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "科信小助手 " + AppUi.version(this) + " 运行记录");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, "发送运行记录"));
        } catch (RuntimeException unavailable) {
            copyAll();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
            .setTitle("清空运行记录")
            .setMessage("清掉之后，这次故障的线索就没了。复制出去再清空比较稳妥。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空", (dialog, which) -> {
                Diag.clear();
                render(false);
            })
            .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
