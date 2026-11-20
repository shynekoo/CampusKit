package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 更多：the things about this project itself — what it stores on the phone, where the source is,
 * and under what terms it may be used.
 *
 * <p>All are one tap from the account page and none needs the network to be understood, so the
 * licence is shown here in full rather than as a link that fails on a phone with no browser. The
 * repository is a link, because a copy of GitHub inside this app would be a second browser nobody
 * asked for; the page hands the address to whatever app already handles web pages.</p>
 */
import io.qzxy.lite.core.UpdateSources;

public final class MoreActivity extends Page {
    private static final String REPO = "https://github.com/shynekoo/CampusKit";

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
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 18, AppUi.LINE));
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
        if (divider) rule(parent);
    }

    /**
     * 更新渠道：WorkBuddy（默认）、GitHub 或 Gitee。三处清单同名同字段，切换只改变「去哪读」，
     * 不改变校验强度。Gitee 提供是因为 GitHub 的发布 CDN 在国内常常只有几十 KB/s。
     * 选择落盘（{@link UpdateCheck#setSource}），重启后保持，并作废正在飞的检查。
     * 副标题会跟着当前选择变，读者不必点开才知道现在用的是哪一处。
     */
    private void sourceSetting(LinearLayout parent, boolean divider) {
        final String current = UpdateCheck.source(this);
        final int currentIndex = UpdateSources.indexOf(current);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        row.setMinimumHeight(dp(56));

        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageDrawable(AppUi.icon(this, "globe", AppUi.ACCENT, 22));
        mark.setBackground(AppUi.shape(this, AppUi.SOFT, 11));
        mark.setPadding(dp(8), dp(8), dp(8), dp(8));
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, dp(8), 0);
        TextView name = text("更新渠道", 16, AppUi.INK);
        name.setTypeface(null, Typeface.BOLD);
        labels.addView(name);
        final TextView caption = text(sourceCaption(currentIndex), 12, AppUi.MUTED);
        caption.setPadding(0, dp(2), 0, 0);
        labels.addView(caption);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView value = text(UpdateSources.HOSTS[currentIndex], 13, AppUi.MUTED);
        row.addView(value);

        final String[] names = UpdateSources.HOSTS;
        final int[] picked = {currentIndex};
        row.setFocusable(true);
        row.setContentDescription("更新渠道，" + value.getText());
        AppUi.press(row, AppUi.SURFACE, 12);
        row.setOnClickListener(v -> Theme.dialog(this)
            .setTitle("更新渠道")
            .setSingleChoiceItems(names, picked[0], (dialog, which) -> picked[0] = which)
            .setPositiveButton("确定", (dialog, which) -> {
                UpdateCheck.setSource(this, UpdateSources.keyOf(picked[0]));
                caption.setText(sourceCaption(picked[0]));
                value.setText(UpdateSources.HOSTS[picked[0]]);
                row.setContentDescription("更新渠道，" + value.getText());
                Toast.makeText(this, "已切换到 " + UpdateSources.HOSTS[picked[0]], Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show());
        parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        if (divider) rule(parent);
    }

    /** 列表里第一项是默认渠道，副标题要点明这一点；其余直接写名字。 */
    private static String sourceCaption(int index) {
        return index == 0 ? UpdateSources.HOSTS[0] + "（默认）" : UpdateSources.HOSTS[index];
    }

    /**
     * 接收预览版更新：一条可以随手关掉的渠道开关，就挨在更新日志下面。
     *
     * <p>放在这里而不是另开一页：读者是顺着「这版改了什么 → 想不想早点看到下一版」走到它的。
     * 开关自己落盘（{@link UpdateCheck#setBetaEnabled}），重启后保持；同时会作废正在飞的那次
     * 检查，免得旧渠道的结果盖掉新选择。整行可点，因为系统开关的触点只有那颗拇指那么宽。</p>
     *
     * <p>默认关，任何安装包都一样——包括预览版的第一次安装。打开前要先过一次确认框，
     * 说清预览版是给开发者测试用的、可能不稳定；取消或返回键都回到关的位置，<b>并且不落盘</b>。</p>
     */
    private void previewChannel(LinearLayout parent, boolean divider) {
        final boolean on = UpdateCheck.betaEnabled(this);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        row.setMinimumHeight(dp(56));

        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageDrawable(AppUi.icon(this, "beaker", AppUi.ACCENT, 22));
        mark.setBackground(AppUi.shape(this, AppUi.SOFT, 11));
        mark.setPadding(dp(8), dp(8), dp(8), dp(8));
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, dp(8), 0);
        TextView name = text("接收预览版更新", 16, AppUi.INK);
        name.setTypeface(null, Typeface.BOLD);
        labels.addView(name);
        TextView caption = text("提前体验新功能，可能存在不稳定情况", 12, AppUi.MUTED);
        caption.setPadding(0, dp(2), 0, 0);
        labels.addView(caption);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // 打开要先过一次确认框：预览版是给开发者和愿意担风险的人用的，默认就该关着。
        final boolean[] settling = {false};
        Switch toggle = tick(on, (moved, checked) -> {
            if (settling[0]) return;
            if (!checked) {
                UpdateCheck.setBetaEnabled(this, false);
                row.setContentDescription("接收预览版更新，已关闭");
                Toast.makeText(this, "已关闭，只检查正式版", Toast.LENGTH_SHORT).show();
                return;
            }
            Theme.dialog(this)
                .setTitle("开启预览版更新？")
                .setMessage("预览版供开发者测试使用，可能不稳定，不建议开启。")
                .setPositiveButton("仍然开启", (dialog, which) -> {
                    UpdateCheck.setBetaEnabled(this, true);
                    row.setContentDescription("接收预览版更新，已开启");
                    Toast.makeText(this, "已开启，会一并检查预览版", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("保持关闭", (dialog, which) -> revert(moved, settling))
                .setOnCancelListener(dialog -> revert(moved, settling))
                .show();
        });
        toggle.setContentDescription("接收预览版更新");
        row.addView(toggle);

        row.setFocusable(true);
        row.setContentDescription("接收预览版更新，" + (on ? "已开启" : "已关闭"));
        AppUi.press(row, AppUi.SURFACE, 12);
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        if (divider) rule(parent);
    }

    /** 取消确认后把开关拨回去，但不要再走一遍「已关闭」的提示与落盘。 */
    private void revert(Switch toggle, boolean[] settling) {
        settling[0] = true;
        toggle.setChecked(false);
        settling[0] = false;
    }

    /** 开关本体：轨道与拇指都上实色，深色下也分得清开与关（同「外观与主题」页）。 */
    private Switch tick(boolean value, java.util.function.BiConsumer<Switch, Boolean> change) {
        Switch toggle = new Switch(this);
        toggle.setChecked(value);
        toggle.setShowText(false);
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states,
            new int[]{AppUi.ACCENT, Theme.mix(AppUi.MUTED, AppUi.SURFACE, .45f)}));
        toggle.setTrackTintList(new ColorStateList(states,
            new int[]{Theme.mix(AppUi.ACCENT, AppUi.SURFACE, .25f), 0xffc3cad6}));
        toggle.setOnCheckedChangeListener((button, checked) -> change.accept((Switch) button, checked));
        return toggle;
    }

    private void rule(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(AppUi.LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            Math.max(1, dp(1)));
        params.setMargins(dp(52), 0, 0, 0);
        parent.addView(line, params);
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
        Theme.dialog(this)
            .setTitle("MIT License")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show();
    }

    /** 数据与隐私 lives behind this page now, but it is still one tap from 校园. */
    private void openPrivacy() {
        startActivity(new Intent(this, PrivacyActivity.class));
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
        TextView caption = text("数据、源码与许可证", 12, AppUi.MUTED);
        caption.setPadding(0, dp(2), 0, dp(12));
        root.addView(caption);

        LinearLayout card = card();
        // 数据与隐私 从校园页挪到这里：它回答的是「这个应用本身动了什么」，与源码、许可证同类，
        // 而不是像趣智校园/慧生活那样的一项校园服务。
        entry(card, "info", "数据与隐私", "本机存了什么、发到哪里、怎么清除", true, this::openPrivacy);
        // 使用公告不在这里另开一条：它就是《数据与隐私》里那份文本的另一面，从那一页末尾
        // 进去即可，同一份东西摆两个入口只会让人猜哪个才是正的。
        // 更新日志挨着「检查更新」的入口放：想知道这版改了什么的人，先找的通常是这里。
        entry(card, "refresh", "更新日志", "查看版本更新与历史记录", true,
            () -> startActivity(new Intent(this, ChangelogActivity.class)));
        // 更新渠道挨着更新日志：先看这版改了什么，再决定从 WorkBuddy 还是 GitHub 取下一版。
        sourceSetting(card, true);
        // 渠道开关紧跟在更新日志后面：先看这版改了什么，再决定要不要提前拿到下一版。
        previewChannel(card, true);
        // 诊断页从 2.5.8 起对所有构建开放：正文只含白名单字段，运行期默认关，由使用者
        // 遇到问题时自己打开。内部构建（--diagnostics）只是多了更细的事件，不是另一个开关。
        if (BuildFlags.DIAG_BASIC || BuildFlags.DIAGNOSTICS) {
            entry(card, "today", "诊断日志", "遇到问题时记录，可导出给开发者", true,
                () -> startActivity(new Intent(this, DiagActivity.class)));
        }
        entry(card, "globe", "本项目 GitHub 仓库", "github.com/shynekoo/CampusKit", true, this::openRepository);
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
