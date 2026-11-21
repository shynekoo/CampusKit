// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.qzxy.lite.core.DiagCatalog;
import io.qzxy.lite.core.DiagEvent;

/**
 * 诊断日志页：把记录按等级、模块摊开，能筛、能展开、能导出。
 *
 * <p>它回答的问题很具体：用户说「课表加载不出来」，开发者需要在一屏里看到「请求超时 →
 * 加载失败」这两行，而不是在一份按时间顺序的流水里找。所以列表主行是「时间 + 等级 + 模块 +
 * 一句结论」，点开才是字段与堆栈。</p>
 *
 * <p>为什么不用 RecyclerView：项目里没有 AndroidX，引入它等于为一个页面加一整套依赖。
 * 这里用分页（每次 40 条）+ 普通 LinearLayout：记录再长，创建的 View 数量也有上限，滚动不会
 * 因为几百个子 View 卡住。翻页而不是「全部显示」的另一个原因：最需要看的是最后几步。</p>
 *
 * <p>出口只有两个：复制到剪贴板、导出成 UTF-8 文本。导出走系统的「新建文件」，不申请存储权限。
 * 两条路走出去的都是脱敏后的内容——脱敏发生在写入磁盘的那一步，页面和文件读的是同一份。</p>
 */
public final class DiagActivity extends Page {
    /** 一页 40 条：四十行足够看清一次故障的前因后果，也不会一次建出上百个 View。 */
    private static final int PAGE = 40;
    private static final int EXPORT_REQUEST = 71;

    /** 0 = 全部；其余按「至少这个等级」筛选。 */
    private int levelFilter = 0;
    /** null = 全部模块。 */
    private String moduleFilter = null;
    private int shown = PAGE;

    private LinearLayout levelRow;
    private LinearLayout moduleRow;
    private LinearLayout sheet;
    private ScrollView scroll;
    private TextView status;
    private TextView size;
    private Switch toggle;
    private TextView count;

    private List<Diag.Entry> filtered = new ArrayList<>();

    private int dp(int n) { return AppUi.dp(this, n); }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private TextView mono(String value, int size, int color) {
        TextView view = text(value, size, color);
        view.setTypeface(Typeface.MONOSPACE);
        return view;
    }

    private int danger() { return Theme.dark() ? 0xffff7b72 : AppUi.DANGER; }
    private int warning() { return Theme.dark() ? 0xffffc857 : 0xff9a6700; }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        // 页面打开时才初始化：日志目录不该因为一次启动就建出来，更不该在用户没看过日志页时写入。
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

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹ 返回", 15, AppUi.ACCENT);
        back.setPadding(dp(2), dp(10), dp(2), dp(10));
        back.setMinHeight(dp(48));
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView report = text("故障报告", 14, AppUi.ACCENT);
        report.setPadding(dp(12), dp(10), dp(2), dp(10));
        report.setMinHeight(dp(48));
        report.setGravity(Gravity.CENTER_VERTICAL);
        AppUi.press(report, 0x00ffffff, 10);
        report.setOnClickListener(v -> startActivity(new Intent(this, DiagReportActivity.class)));
        top.addView(report);
        root.addView(top);

        TextView heading = text("诊断日志", 26, AppUi.INK);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        TextView caption = text("用于排查功能异常与运行故障", 12, AppUi.MUTED);
        caption.setPadding(0, dp(2), 0, dp(10));
        root.addView(caption);

        root.addView(switchCard());

        levelRow = chipHolder();
        root.addView(chipRow(levelRow));
        moduleRow = chipHolder();
        root.addView(chipRow(moduleRow));

        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(0, dp(4), 0, dp(12));
        scroll = new ScrollView(this);
        scroll.addView(sheet);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(bottomBar());
        setContentView(root);
        root.requestApplyInsets();
        refresh();
    }

    /** 开关 + 状态 + 大小，放在一张卡片里：这三件事是同一个问题的三个面。 */
    private View switchCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(12), dp(12));
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 14, AppUi.LINE));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("诊断记录", 15, AppUi.INK);
        label.setTypeface(null, Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        toggle = new Switch(this);
        toggle.setShowText(false);
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states,
            new int[]{AppUi.ACCENT, Theme.dark() ? 0xff5a6472 : 0xffb3bcc9}));
        toggle.setTrackTintList(new ColorStateList(states,
            new int[]{(AppUi.ACCENT & 0x00ffffff) | 0x66000000, 0x22000000}));
        toggle.setChecked(Diag.enabled());
        toggle.setContentDescription("诊断记录开关");
        toggle.setOnCheckedChangeListener((button, checked) -> {
            Diag.setEnabled(checked);
            shown = PAGE;
            refresh();
            toast(checked ? "已开启记录，复现一次问题后再回来" : "已暂停记录");
        });
        row.addView(toggle);
        card.addView(row);

        status = text("", 12, AppUi.MUTED);
        size = text("", 12, AppUi.MUTED);
        status.setPadding(0, dp(4), 0, 0);
        size.setPadding(0, dp(2), 0, 0);
        card.addView(status);
        card.addView(size);

        TextView note = text("诊断日志只保存在本机，可能包含设备环境和错误信息。请确认内容后再分享。",
            11, AppUi.MUTED);
        note.setLineSpacing(dp(2), 1f);
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout chipHolder() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        holder.setPadding(0, dp(2), 0, dp(2));
        return holder;
    }

    private View chipRow(LinearLayout holder) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(holder);
        return scroll;
    }

    /** 一个筛选项。选中用主色，未选用浅底——不靠边框，深色主题下也不会糊成一片。 */
    private TextView chip(String label, boolean selected, Runnable action) {
        TextView view = text(label, 12, selected ? AppUi.ON_ACCENT : AppUi.INK);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        view.setMinHeight(dp(32));
        view.setBackground(AppUi.shape(this, selected ? AppUi.ACCENT : AppUi.SOFT, 999));
        view.setContentDescription(label + (selected ? "，已选中" : ""));
        view.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private void rebuildChips() {
        levelRow.removeAllViews();
        String[] levels = {"全部", "错误", "警告", "信息"};
        int[] values = {0, DiagEvent.ERROR, DiagEvent.WARN, DiagEvent.INFO};
        for (int i = 0; i < levels.length; i++) {
            final int value = values[i];
            levelRow.addView(chip(levels[i], levelFilter == value, () -> {
                levelFilter = value;
                shown = PAGE;
                refresh();
            }));
        }

        moduleRow.removeAllViews();
        moduleRow.addView(chip("全部模块", moduleFilter == null, () -> {
            moduleFilter = null;
            shown = PAGE;
            refresh();
        }));
        for (String module : DiagCatalog.MODULES) {
            moduleRow.addView(chip(DiagCatalog.module(module), module.equals(moduleFilter), () -> {
                moduleFilter = module;
                shown = PAGE;
                refresh();
            }));
        }
    }

    private void refresh() {
        rebuildChips();
        boolean running = Diag.enabled();
        toggle.setChecked(running);
        status.setText("记录状态：" + (running ? "正在记录" : "已暂停"));
        size.setText("日志大小：" + kilobytes(Diag.bytes()));

        List<Diag.Entry> all = Diag.records(0);
        filtered = new ArrayList<>();
        for (Diag.Entry entry : all) {
            if (levelFilter != 0 && !entry.event.atLeast(levelFilter)) continue;
            if (moduleFilter != null && !moduleFilter.equals(entry.event.module)) continue;
            filtered.add(entry);
        }

        sheet.removeAllViews();
        if (filtered.isEmpty()) {
            TextView empty = text(all.isEmpty()
                    ? (running ? "还没有记录。复现一次问题后再回到这里。" : "还没有开始记录。遇到问题时打开上面的开关，复现一次后再回来。")
                    : "当前筛选条件下没有记录。",
                13, AppUi.MUTED);
            empty.setLineSpacing(dp(3), 1f);
            empty.setPadding(dp(2), dp(12), dp(2), 0);
            sheet.addView(empty);
        } else {
            int from = Math.max(0, filtered.size() - shown);
            if (from > 0) sheet.addView(moreRow(filtered.size() - from));
            for (int i = from; i < filtered.size(); i++) sheet.addView(row(filtered.get(i)));
        }

        count.setText(filtered.size() == all.size()
            ? "共 " + all.size() + " 条"
            : "筛选出 " + filtered.size() + " 条 / 共 " + all.size() + " 条");
    }

    /** 「显示更多」：往前一页。放在列表顶部，因为最新的记录在下面。 */
    private View moreRow(int hidden) {
        TextView more = text("显示更早的 " + Math.min(PAGE, hidden) + " 条（更早 " + hidden + " 条未显示）",
            12, AppUi.ACCENT);
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(8), dp(10), dp(8), dp(10));
        more.setMinHeight(dp(44));
        AppUi.press(more, 0x00ffffff, 10);
        more.setOnClickListener(v -> {
            shown += PAGE;
            refresh();
        });
        return more;
    }

    private View row(Diag.Entry entry) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(10), 0, dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView time = mono(entry.event.time, 11, AppUi.MUTED);
        head.addView(time);

        TextView level = text(DiagEvent.name(entry.event.level), 10, levelColor(entry.event.level));
        level.setTypeface(null, Typeface.BOLD);
        level.setPadding(dp(8), 0, dp(8), 0);
        head.addView(level);

        TextView module = text(DiagCatalog.module(entry.event.module), 10, AppUi.MUTED);
        head.addView(module);
        box.addView(head);

        TextView title = text(DiagCatalog.summary(entry.event), 14, AppUi.INK);
        title.setLineSpacing(dp(2), 1f);
        title.setPadding(0, dp(4), 0, 0);
        box.addView(title);

        final View[] detail = {null};
        TextView hint = text("点击查看详细信息", 11, AppUi.ACCENT);
        hint.setPadding(0, dp(4), 0, 0);
        box.addView(hint);

        box.setOnClickListener(v -> {
            if (detail[0] != null) {
                box.removeView(detail[0]);
                detail[0] = null;
                hint.setText("点击查看详细信息");
                return;
            }
            detail[0] = detail(entry);
            box.addView(detail[0]);
            hint.setText("收起详细信息");
        });

        View divider = new View(this);
        divider.setBackgroundColor(AppUi.LINE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(box);
        wrap.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return wrap;
    }

    private int levelColor(int level) {
        if (level >= DiagEvent.ERROR) return danger();
        if (level == DiagEvent.WARN) return warning();
        return AppUi.MUTED;
    }

    /** 展开内容：原始记录行 + 字段 + 堆栈，附单条复制。 */
    private View detail(Diag.Entry entry) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(AppUi.shape(this, AppUi.SOFT, 10));

        StringBuilder body = new StringBuilder(entry.event.line);
        for (Map.Entry<String, String> field : entry.event.fields().entrySet()) {
            body.append('\n').append(field.getKey()).append(" = ").append(field.getValue());
        }
        if (!entry.detail().isEmpty()) body.append('\n').append(entry.detail());

        TextView raw = mono(body.toString(), 11, AppUi.INK);
        raw.setTextIsSelectable(true);
        raw.setLineSpacing(dp(2), 1f);
        panel.addView(raw);

        if (entry.event.atLeast(DiagEvent.ERROR)) {
            TextView hint = text("这一条可用于定位失败阶段：" + stageOf(entry.event), 11, AppUi.MUTED);
            hint.setPadding(0, dp(8), 0, 0);
            panel.addView(hint);
        }

        TextView copy = text("复制这一条", 12, AppUi.ACCENT);
        copy.setPadding(dp(2), dp(8), dp(2), dp(4));
        copy.setMinHeight(dp(36));
        copy.setOnClickListener(v -> copy("一条记录", entry.event.line + (entry.detail().isEmpty() ? "" : "\n" + entry.detail())));
        panel.addView(copy);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        panel.setLayoutParams(params);
        return panel;
    }

    /** 把阶段翻成人话：登录、请求、解析、写入，哪一段断了就看这一段。 */
    private String stageOf(DiagEvent event) {
        String stage = event.field("stage");
        if ("account".equals(stage)) return "账号读取阶段";
        if ("signin".equals(stage) || "page".equals(stage) || "key".equals(stage) || "post".equals(stage))
            return "登录阶段";
        if ("request".equals(stage)) return "网络请求阶段";
        if ("parse".equals(stage) || "no_auth_code".equals(stage)) return "数据解析阶段";
        if ("consent".equals(stage) || "consent_missing".equals(stage)) return "用户确认阶段";
        if (event.module.equals("NETWORK")) return "网络阶段";
        if (event.module.equals("UPDATE")) return "更新阶段";
        return "该模块报告失败的位置";
    }

    private View bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(8), 0, dp(4));

        count = text("", 11, AppUi.MUTED);
        count.setPadding(dp(2), 0, 0, 0);

        android.widget.Button copy = new android.widget.Button(this);
        copy.setText("复制日志");
        copy.setAllCaps(false);
        copy.setTextSize(14);
        copy.setTextColor(AppUi.ON_ACCENT);
        copy.setBackground(AppUi.shape(this, AppUi.ACCENT, 12));
        copy.setOnClickListener(v -> copyLogs());
        bar.addView(copy, new LinearLayout.LayoutParams(0, dp(44), 1));

        TextView export = text("导出文件", 14, AppUi.ACCENT);
        export.setGravity(Gravity.CENTER);
        export.setPadding(dp(14), dp(10), dp(6), dp(10));
        export.setMinHeight(dp(44));
        AppUi.press(export, 0x00ffffff, 10);
        export.setOnClickListener(v -> exportFile());
        bar.addView(export);

        TextView clear = text("清空", 14, AppUi.MUTED);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(8), dp(10), dp(2), dp(10));
        clear.setMinHeight(dp(44));
        AppUi.press(clear, 0x00ffffff, 10);
        clear.setOnClickListener(v -> confirmClear());
        bar.addView(clear);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(count);
        wrap.addView(bar);
        return wrap;
    }

    /** 当前筛选条件下的文本，页面复制与文件导出共用，避免两处各拼一份。 */
    private String filteredText() {
        if (levelFilter == 0 && moduleFilter == null) return Diag.text();
        StringBuilder out = new StringBuilder();
        out.append("科信小助手 诊断日志").append('\n')
            .append("筛选：").append(levelFilter == 0 ? "全部等级" : DiagEvent.name(levelFilter))
            .append(" · ").append(moduleFilter == null ? "全部模块" : DiagCatalog.module(moduleFilter)).append('\n')
            .append("仅保存在本机，可能包含设备环境与错误信息，请确认内容后再分享。").append('\n')
            .append("────────────").append('\n');
        for (Diag.Entry entry : filtered) {
            out.append(entry.event.line).append('\n');
            if (!entry.detail().isEmpty()) out.append(entry.detail()).append('\n');
        }
        if (filtered.isEmpty()) out.append("当前筛选条件下没有记录。").append('\n');
        return out.toString();
    }

    /**
     * 复制。有筛选时给一次选择：只发当前筛出来的那几条，还是整份记录。
     * 排查时通常只发相关的几条；确认问题在别处时才需要全部。
     */
    private void copyLogs() {
        List<Diag.Entry> all = Diag.records(0);
        if (all.isEmpty()) { toast("还没有记录"); return; }
        if (levelFilter == 0 && moduleFilter == null) {
            copy("诊断日志", Diag.text());
            return;
        }
        Theme.dialog(this)
            .setTitle("复制日志")
            .setMessage("当前筛选出 " + filtered.size() + " 条，全部记录 " + all.size() + " 条。")
            .setPositiveButton("当前筛选", (dialog, which) -> copy("诊断日志", filteredText()))
            .setNeutralButton("全部", (dialog, which) -> copy("诊断日志", Diag.text()))
            .setNegativeButton("取消", null)
            .show();
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

    /** 用系统的「新建文件」，把文本写进去：不申请存储权限，也不指定自己的目录。 */
    private void exportFile() {
        if (Diag.records(0).isEmpty()) { toast("还没有记录"); return; }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "campuskit-diag-" + stamp + ".txt");
        try {
            startActivityForResult(intent, EXPORT_REQUEST);
        } catch (RuntimeException unavailable) {
            toast("没有可用的文件保存器，请改用「复制日志」");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_REQUEST || result != RESULT_OK || data == null) return;
        Uri target = data.getData();
        if (target == null) { toast("没有选择保存位置"); return; }
        try (OutputStream output = getContentResolver().openOutputStream(target, "wt")) {
            if (output == null) { toast("无法写入所选位置"); return; }
            output.write(filteredText().getBytes(StandardCharsets.UTF_8));
            output.flush();
            toast("已导出，可在文件管理器里找到");
        } catch (Exception failure) {
            Diag.failure("DIAG", "log.write.failed", "WRITE_FAILED", failure, Diag.trace());
            toast("导出失败，请改用「复制日志」");
        }
    }

    private void confirmClear() {
        Theme.dialog(this)
            .setTitle("清空日志")
            .setMessage("清掉之后，这次故障的线索就没了。复制或导出之后再清空比较稳妥。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空", (dialog, which) -> {
                Diag.clear();
                shown = PAGE;
                refresh();
                toast("已清空");
            })
            .show();
    }

    private static String kilobytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        return (bytes / 1024) + " KB";
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
