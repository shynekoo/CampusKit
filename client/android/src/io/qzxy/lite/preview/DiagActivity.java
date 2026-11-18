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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 日志页：把 {@link Diag} 写下的内容摊开，并给「复制全部 / 单条复制」。
 *
 * <p>出口只能是文本。慧生活登录页带 {@code FLAG_SECURE}，系统层面就截不了图，所以截图这条
 * 路从一开始就不存在——复制到剪贴板、粘进微信，是这个页面上唯一能把记录送出去的动作。</p>
 *
 * <p>页面分两段：顶部一张「相关信息」卡（本次进程的机器快照，可单独复制），下面是按条列出的
 * 事件。每条默认折三行，点一下展开——崩溃堆栈会有几十行，全铺开会把上一行挤到看不见。每条右侧
 * 各自带「复制」，需要发出去往往只有卡住前那几行，不必整份照发。</p>
 */
public final class DiagActivity extends Page {
    private static final Pattern TICK = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[");
    /** 一屏最多渲染这么多条：记录再长，翻到最底也只关心最后几步。 */
    private static final int MAX_ROWS = 120;

    private TextView meta;
    private TextView toggle;
    private LinearLayout sheet;
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

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹ 返回", 15, AppUi.ACCENT);
        back.setPadding(dp(2), dp(10), dp(2), dp(10));
        back.setMinHeight(dp(48));
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        toggle = text("", 13, AppUi.MUTED);
        toggle.setPadding(dp(10), dp(10), dp(2), dp(10));
        toggle.setMinHeight(dp(48));
        toggle.setOnClickListener(v -> {
            boolean next = !Diag.enabled();
            Diag.setEnabled(next);
            toast(next ? "已恢复记录" : "已暂停记录");
            render(false);
        });
        top.addView(toggle);
        root.addView(top);

        TextView heading = text("日志", 26, AppUi.INK);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        TextView caption = text("这次启动每一步走到哪。", 12, AppUi.MUTED);
        caption.setLineSpacing(dp(3), 1f);
        caption.setPadding(0, dp(2), 0, dp(6));
        root.addView(caption);

        meta = text("", 11, AppUi.MUTED);
        meta.setPadding(dp(2), 0, 0, dp(8));
        root.addView(meta);

        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(0, 0, 0, dp(14));
        scroll = new ScrollView(this);
        scroll.addView(sheet);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(8), 0, dp(4));

        Button copy = new Button(this);
        copy.setText("复制全部");
        copy.setAllCaps(false);
        copy.setTextSize(15);
        copy.setTextColor(AppUi.ON_ACCENT);
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

    /** 整份记录拆成条目：一条时间行开启一段，缩进行（堆栈、快照明细）跟着上一段走。 */
    private static List<String> blocks(String text) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = null;
        for (String line : text.split("\n")) {
            boolean start = TICK.matcher(line).lookingAt() || line.startsWith("=== ");
            if (start) {
                if (current != null) blocks.add(current.toString());
                current = new StringBuilder(line);
            } else if (line.trim().isEmpty()) {
                if (current != null) { blocks.add(current.toString()); current = null; }
            } else if (current != null) {
                current.append('\n').append(line);
            } else {
                current = new StringBuilder(line);
            }
        }
        if (current != null) blocks.add(current.toString());
        return blocks;
    }

    private void render(boolean toBottom) {
        String text = Diag.dump();
        boolean running = Diag.enabled();
        toggle.setText(running ? "暂停记录" : "开启记录");
        toggle.setTextColor(running ? AppUi.MUTED : AppUi.ACCENT);

        List<String> blocks = blocks(text);
        String info = "";
        List<String> events = new ArrayList<>();
        for (String block : blocks) {
            if (block.startsWith("=== ")) info = block; else events.add(block);
        }
        int from = Math.max(0, events.size() - MAX_ROWS);

        sheet.removeAllViews();
        if (!info.isEmpty()) sheet.addView(infoCard(info));
        if (events.isEmpty() && info.isEmpty()) {
            TextView empty = text("还没有记录。", 13, AppUi.MUTED);
            empty.setPadding(dp(2), dp(10), 0, 0);
            sheet.addView(empty);
        }
        for (int i = from; i < events.size(); i++) sheet.addView(row(events.get(i)));

        int lines = text.isEmpty() ? 0 : text.split("\n", -1).length;
        meta.setText(lines + " 行 · " + Math.max(1, text.length() / 1024) + " KB · "
            + (running ? "正在记录" : "已暂停") + (from > 0 ? " · 只显示最后 " + MAX_ROWS + " 条" : ""));
        if (toBottom) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private View infoCard(String info) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 14, AppUi.LINE));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("相关信息", 15, AppUi.INK);
        title.setTypeface(null, Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView copy = text("复制", 13, AppUi.ACCENT);
        copy.setPadding(dp(12), dp(6), dp(2), dp(6));
        copy.setMinHeight(dp(36));
        copy.setGravity(Gravity.CENTER);
        AppUi.press(copy, 0x00ffffff, 10);
        copy.setOnClickListener(v -> copy("相关信息", info));
        head.addView(copy);
        card.addView(head);

        TextView body = text(info, 11, AppUi.MUTED);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(2), 1f);
        body.setPadding(0, dp(6), 0, 0);
        card.addView(body);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    private View row(String block) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0, dp(6), 0, dp(6));

        TextView body = text(block, 11, AppUi.INK);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(2), 1f);
        body.setMaxLines(3);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        body.setOnClickListener(v -> {
            boolean collapsed = body.getMaxLines() == 3;
            body.setMaxLines(collapsed ? Integer.MAX_VALUE : 3);
            body.setEllipsize(collapsed ? null : android.text.TextUtils.TruncateAt.END);
        });
        line.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView copy = text("复制", 12, AppUi.ACCENT);
        copy.setPadding(dp(10), dp(2), dp(2), dp(2));
        copy.setGravity(Gravity.TOP);
        AppUi.press(copy, 0x00ffffff, 10);
        copy.setOnClickListener(v -> copy("一条记录", block));
        line.addView(copy);
        return line;
    }

    private void copy(String label, String text) {
        if (text == null || text.isEmpty()) { toast("还没有记录"); return; }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) { toast("无法访问剪贴板"); return; }
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        } catch (RuntimeException blocked) {
            toast("复制未成功，请长按文字手动选取");
            return;
        }
        if (Build.VERSION.SDK_INT < 33) toast("已复制");
    }

    private void copyAll() {
        String text = Diag.dump();
        if (text.isEmpty()) { toast("还没有记录"); return; }
        // 用代码块包一层：粘到微信或 issue 里不会被当成正文排版，等宽也保住了对齐。
        copy("日志", "```\n" + text + "\n```");
    }

    private void shareAll() {
        String text = Diag.dump();
        if (text.isEmpty()) { toast("还没有记录"); return; }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "科信小助手 " + AppUi.version(this) + " 日志");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, "发送日志"));
        } catch (RuntimeException unavailable) {
            copyAll();
        }
    }

    private void confirmClear() {
        Theme.dialog(this)
            .setTitle("清空日志")
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
