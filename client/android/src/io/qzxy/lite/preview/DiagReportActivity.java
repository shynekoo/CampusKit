// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.qzxy.lite.core.DiagCatalog;
import io.qzxy.lite.core.DiagEvent;

/**
 * 故障报告：把「哪个版本、什么设备、哪个模块、用户怎么描述的、最后一次异常、最近一小时的
 * 脱敏记录」装进一个文本文件。
 *
 * <p>它是诊断日志的打包，不是新的一份数据：记录仍然来自 {@link Diag}，仍然只在本机，仍然由
 * 使用者主动导出。这里多出来的只有两样东西——使用者自己选的模块、自己写的问题描述。没有
 * 服务器、没有账号、没有自动上传。</p>
 *
 * <p>为什么让用户选模块：一份没有方向的日志很难读。选定「课表」之后，报告里的记录只留这一个
 * 模块，看的人不必先猜哪几行有关。</p>
 */
public final class DiagReportActivity extends Page {
    private static final int EXPORT_REQUEST = 72;
    /** 报告里最多带这么多条：再多也不会有助于定位，只会让文件难读。 */
    private static final int MAX_RECORDS = 200;
    private static final int MAX_DESCRIPTION = 1500;

    private static final String[][] MODULES = {
        {"", "全部模块"},
        {"TIMETABLE", "课表"},
        {"EDU", "登录"},
        {"NETWORK", "网络"},
        {"LIFE", "慧生活"},
        {"QZXY", "趣智校园"},
        {"UPDATE", "更新"},
        {"UI", "界面"},
    };

    private String module = "";
    private LinearLayout moduleRow;
    private EditText description;

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
        Diag.install(this);

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
        back.setMinHeight(dp(48));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView heading = text("故障报告", 26, AppUi.INK);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        TextView caption = text("选一个模块，写下遇到的问题，导出成一份可直接发出去的文本", 12, AppUi.MUTED);
        caption.setLineSpacing(dp(2), 1f);
        caption.setPadding(0, dp(2), 0, dp(12));
        root.addView(caption);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(12));

        body.addView(sectionTitle("哪一部分出了问题"));
        moduleRow = new LinearLayout(this);
        moduleRow.setOrientation(LinearLayout.HORIZONTAL);
        moduleRow.setPadding(0, dp(4), 0, dp(10));
        body.addView(moduleRow);
        rebuildModules();

        body.addView(sectionTitle("当时是什么情况"));
        description = new EditText(this);
        description.setHint("例如：点「自动导入」后一直转圈，最后提示网络超时");
        description.setHintTextColor(AppUi.MUTED);
        description.setTextColor(AppUi.INK);
        description.setTextSize(14);
        description.setGravity(Gravity.TOP | Gravity.START);
        description.setMinLines(3);
        description.setMaxLines(6);
        description.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        description.setBackground(AppUi.outlined(this, AppUi.SURFACE, 12, AppUi.LINE));
        description.setPadding(dp(12), dp(10), dp(12), dp(10));
        body.addView(description, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout note = new LinearLayout(this);
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(12), dp(12), dp(12), dp(12));
        note.setBackground(AppUi.shape(this, AppUi.SOFT, 12));
        String[] lines = {
            "报告包含：App 版本、Android 版本、设备型号、所选模块、你写的问题描述、最近 " + MAX_RECORDS + " 条脱敏记录与最近一次异常。",
            "不包含：账号、密码、Cookie、Token、学号、手机号、课程明细、请求正文。",
            "报告只保存在本机，由你决定发给谁。",
        };
        for (String line : lines) {
            TextView item = text("· " + line, 11, AppUi.MUTED);
            item.setLineSpacing(dp(3), 1f);
            item.setPadding(0, dp(2), 0, dp(2));
            note.addView(item);
        }
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(12);
        body.addView(note, noteParams);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(8), 0, dp(4));

        android.widget.Button export = new android.widget.Button(this);
        export.setText("导出报告");
        export.setAllCaps(false);
        export.setTextSize(15);
        export.setTextColor(AppUi.ON_ACCENT);
        export.setBackground(AppUi.shape(this, AppUi.ACCENT, 12));
        export.setOnClickListener(v -> export());

        TextView copy = text("复制报告", 14, AppUi.ACCENT);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(dp(14), dp(10), dp(2), dp(10));
        copy.setMinHeight(dp(46));
        AppUi.press(copy, 0x00ffffff, 10);
        copy.setOnClickListener(v -> copy(report()));

        bar.addView(export, new LinearLayout.LayoutParams(0, dp(46), 1));
        bar.addView(copy);
        root.addView(bar);

        setContentView(root);
        root.requestApplyInsets();
    }

    private TextView sectionTitle(String label) {
        TextView view = text(label, 13, AppUi.INK);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(6), 0, 0);
        return view;
    }

    private void rebuildModules() {
        moduleRow.removeAllViews();
        for (String[] entry : MODULES) {
            final String code = entry[0];
            boolean selected = code.equals(module);
            TextView chip = text(entry[1], 12, selected ? AppUi.ON_ACCENT : AppUi.INK);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setMinHeight(dp(32));
            chip.setBackground(AppUi.shape(this, selected ? AppUi.ACCENT : AppUi.SOFT, 999));
            chip.setOnClickListener(v -> {
                module = code;
                rebuildModules();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(8);
            moduleRow.addView(chip, params);
        }
    }

    /** 报告正文。所有内容都在本机拼好，导出与复制用的是同一份。 */
    private String report() {
        StringBuilder out = new StringBuilder();
        out.append("科信小助手 故障报告").append('\n')
            .append("生成时间 ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date())).append('\n')
            .append("App 版本 ").append(AppUi.version(this)).append(" (").append(versionCode()).append(')').append('\n')
            .append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(')').append('\n')
            .append("设备 ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            .append("故障模块 ").append(module.isEmpty() ? "未指定" : DiagCatalog.module(module)).append('\n');

        String words = description.getText() == null ? "" : description.getText().toString().trim();
        out.append('\n').append("问题描述").append('\n')
            .append(words.isEmpty() ? "（未填写）" : clip(words)).append('\n');

        List<Diag.Entry> all = Diag.records(0);
        List<Diag.Entry> picked = new ArrayList<>();
        for (Diag.Entry entry : all) {
            if (!module.isEmpty() && !module.equals(entry.event.module)) continue;
            picked.add(entry);
        }
        if (picked.size() > MAX_RECORDS) picked = picked.subList(picked.size() - MAX_RECORDS, picked.size());

        out.append('\n').append("最近记录（").append(picked.size()).append(" 条，已脱敏）").append('\n')
            .append("────────────").append('\n');
        if (picked.isEmpty()) out.append("没有记录。诊断记录可能处于关闭状态。").append('\n');
        for (Diag.Entry entry : picked) {
            out.append(entry.event.line).append('\n');
            if (!entry.detail().isEmpty()) out.append(entry.detail()).append('\n');
        }

        Diag.Entry last = null;
        for (Diag.Entry entry : picked) if (entry.event.atLeast(DiagEvent.ERROR)) last = entry;
        out.append('\n').append("最近一次异常").append('\n').append("────────────").append('\n');
        out.append(last == null ? "没有错误级记录。" : last.event.line
            + (last.detail().isEmpty() ? "" : "\n" + last.detail())).append('\n');
        return out.toString();
    }

    private String clip(String text) {
        String clean = text.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ");
        if (clean.length() <= MAX_DESCRIPTION) return clean;
        return clean.substring(0, MAX_DESCRIPTION) + "…";
    }

    private long versionCode() {
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                .getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception unknown) {
            return 0;
        }
    }

    private void export() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "campuskit-report-" + stamp + ".txt");
        try {
            startActivityForResult(intent, EXPORT_REQUEST);
        } catch (RuntimeException unavailable) {
            copy(report());
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_REQUEST || result != RESULT_OK || data == null) return;
        Uri target = data.getData();
        if (target == null) { toast("没有选择保存位置"); return; }
        try (OutputStream output = getContentResolver().openOutputStream(target, "wt")) {
            if (output == null) { toast("无法写入所选位置"); return; }
            output.write(report().getBytes(StandardCharsets.UTF_8));
            output.flush();
            toast("报告已导出");
        } catch (Exception failure) {
            Diag.failure("DIAG", "log.write.failed", "WRITE_FAILED", failure, Diag.trace());
            toast("导出失败，请改用「复制报告」");
        }
    }

    private void copy(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) { toast("无法访问剪贴板"); return; }
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText("故障报告", text));
        } catch (RuntimeException blocked) {
            toast("复制未成功");
            return;
        }
        if (Build.VERSION.SDK_INT < 33) toast("已复制");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
