package io.qzxy.lite.preview;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 「数据与隐私」和「使用公告」共用的排版：顶部返回 + 标题 + 说明，下面一整块可滚动的内容，
 * 每节一张卡片。样式全部取自 {@link AppUi} / {@link UiTokens}，与 UI-1/UI-2 的既有页面一致，
 * 不另做一套视觉。
 *
 * <p>三条硬要求，改这里时别丢：文字用 {@code sp}（跟着系统字体缩放大），内容放在 ScrollView 里
 * （长文本可滚），配色只读 {@link AppUi}（深浅色与自定义主题跟着变）。</p>
 *
 * <p>文案格式约定见 {@link LegalText}：段落以 {@code "#"} 开头是小标题，以 {@code "- "} 开头是
 * 列表项，标题为空字符串时只排正文、不画卡片。</p>
 */
final class LegalView {

    /** 一页的骨架：{@code root} 交给 setContentView，正文追加到 {@code content}。 */
    static final class Screen {
        final LinearLayout root;
        final LinearLayout content;

        Screen(LinearLayout root, LinearLayout content) {
            this.root = root;
            this.content = content;
        }
    }

    private LegalView() {}

    static Screen screen(Activity activity, String title, String caption) {
        final int dp = AppUi.dp(activity, 1);
        final int side = AppUi.side(activity, 14);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUi.BG);
        root.setPadding(side, 8 * dp, side, 0);
        if (Build.VERSION.SDK_INT >= 30) {
            activity.getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(side + bars.left, 8 * dp + bars.top, side + bars.right, bars.bottom);
                return insets;
            });
        }

        TextView back = new TextView(activity);
        back.setText("‹ 返回");
        back.setTextSize(15);
        back.setTextColor(AppUi.ACCENT);
        back.setPadding(2 * dp, 10 * dp, 2 * dp, 10 * dp);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setMinHeight(48 * dp);
        back.setOnClickListener(v -> activity.finish());
        root.addView(back);

        TextView heading = new TextView(activity);
        heading.setText(title);
        // 26sp：与「更多」「外观与主题」等子页标题一致，不因为换 token 把已有子页的标题改大。
        heading.setTextSize(26);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(AppUi.INK);
        root.addView(heading);

        TextView subhead = new TextView(activity);
        subhead.setText(caption);
        subhead.setTextSize(UiTokens.BODY_SMALL);
        subhead.setTextColor(AppUi.MUTED);
        subhead.setPadding(0, 2 * dp, 0, 12 * dp);
        root.addView(subhead);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, 16 * dp);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        return new Screen(root, content);
    }

    /** 追加一节；返回它占用的视图，方便调用方在同屏继续加按钮。 */
    static View section(Activity activity, LinearLayout content, String[] section) {
        View view = sectionView(activity, section);
        content.addView(view);
        View gap = new View(activity);
        content.addView(gap, new LinearLayout.LayoutParams(1, AppUi.dp(activity, 12)));
        return view;
    }

    private static View sectionView(Activity activity, String[] section) {
        final int dp = AppUi.dp(activity, 1);
        String heading = section.length > 0 ? section[0] : "";

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        boolean card = !heading.isEmpty();
        if (card) {
            box.setPadding(18 * dp, 16 * dp, 18 * dp, 16 * dp);
            box.setBackground(AppUi.outlined(activity, AppUi.SURFACE, 18, AppUi.LINE));
        }

        if (card) {
            TextView title = new TextView(activity);
            title.setText(heading);
            title.setTextSize(UiTokens.CARD_TITLE);
            title.setTypeface(null, Typeface.BOLD);
            title.setTextColor(AppUi.INK);
            title.setPadding(0, 0, 0, 2 * dp);
            box.addView(title);
        }

        for (int i = card ? 1 : 0; i < section.length; i++) {
            String line = section[i];
            if (line == null || line.isEmpty()) continue;
            if (line.startsWith("#")) {
                TextView sub = new TextView(activity);
                sub.setText(line.substring(1));
                sub.setTextSize(UiTokens.BODY_SMALL + 1);
                sub.setTypeface(null, Typeface.BOLD);
                sub.setTextColor(AppUi.INK);
                sub.setPadding(0, 10 * dp, 0, 3 * dp);
                box.addView(sub);
                continue;
            }
            boolean bullet = line.startsWith("- ");
            TextView body = new TextView(activity);
            body.setText(bullet ? "· " + line.substring(2) : line);
            body.setTextSize(UiTokens.BODY);
            body.setTextColor(AppUi.MUTED);
            body.setLineSpacing(4 * dp, 1f);
            if (bullet) body.setPadding(6 * dp, 3 * dp, 0, 3 * dp);
            else body.setPadding(0, 0, 0, 6 * dp);
            box.addView(body);
        }
        return box;
    }
}
