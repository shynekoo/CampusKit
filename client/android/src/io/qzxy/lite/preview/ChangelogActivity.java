package io.qzxy.lite.preview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 更新日志：这台手机上装的是哪一版，以及每个正式版本改了什么。
 *
 * <p>内容全部来自 {@link AppChangelog}，随安装包一起打包，断网也能读。数据里只登记正式发布的
 * 版本，所以开发包（如 2.5.8.6）打开时**不会**给任何一条打「当前版本」——那会让人以为它是线上版；
 * 页面改为在顶部说明「此版本尚未正式发布」。</p>
 *
 * <p>交互只有一件事：点版本行展开/收起。动画只动两样东西——卡片内容的高度和箭头的旋转角度，
 * 都走 {@link MotionSpec#LIST_TOGGLE_DURATION}，系统关动画时直接落到终态。连续点击时先取消
 * 正在跑的那条动画并**以当前高度为起点**继续，不会两条动画抢同一个布局参数。</p>
 */
public final class ChangelogActivity extends Page {

    /** 两种字重各建一次，避免每行都 setTypeface 出新对象。 */
    private static final Typeface BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

    private int dp(int n) { return AppUi.dp(this, n); }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(4), 1f);
        return view;
    }

    /** 一张卡片能展开也能收起，动画状态挂在卡片自己身上，页面不另存一份。 */
    private final class Card {
        final LinearLayout root;
        final LinearLayout head;
        final LinearLayout body;
        final ImageView arrow;
        final TextView title;
        final AppChangelog.Entry entry;
        boolean open;
        ValueAnimator running;
        /** 每次点击自增；回调带的是点击发生时的值，过期回调不再改状态。 */
        int generation;

        Card(LinearLayout root, AppChangelog.Entry entry, LinearLayout head, LinearLayout body,
             ImageView arrow, TextView title) {
            this.root = root;
            this.entry = entry;
            this.head = head;
            this.body = body;
            this.arrow = arrow;
            this.title = title;
        }
    }

    private ScrollView scroll;

    // ---- 卡片 ------------------------------------------------------------------------------

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, UiTokens.CARD_RADIUS, AppUi.LINE));
        return card;
    }

    private void gap(LinearLayout parent, int size) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size)));
    }

    /** 一条变化：类别图标 + 一句话。图标只做分类提示，读屏读的是文字。 */
    private View item(AppChangelog.Item item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(UiTokens.SPACE_4), 0, dp(UiTokens.SPACE_4));

        ImageView mark = new ImageView(this);
        mark.setImageDrawable(AppUi.icon(this, iconFor(item.kind), colorFor(item.kind), 16));
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.topMargin = dp(1);
        row.addView(mark, iconParams);

        TextView label = text(item.text, UiTokens.BODY, AppUi.INK);
        label.setPadding(dp(UiTokens.SPACE_8), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    /**
     * 四类各挑一个现成的画法，不新造字形：新增是加号、优化是闪电、修复是勾、其他是信息圈。
     * 只有「其他」用次要色——它本来就不该抢眼。
     */
    private String iconFor(AppChangelog.Kind kind) {
        switch (kind) {
            case ADDED: return "plus";
            case IMPROVED: return "torch";
            case FIXED: return "check";
            default: return "info";
        }
    }

    private int colorFor(AppChangelog.Kind kind) {
        return kind == AppChangelog.Kind.OTHER ? AppUi.MUTED : AppUi.ACCENT;
    }

    private void knownIssues(LinearLayout body, String[] known) {
        if (known.length == 0) return;
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(UiTokens.SPACE_8), 0, 0);
        TextView heading = text("仍存在的问题", UiTokens.BODY_SMALL, AppUi.DANGER);
        heading.setTypeface(BOLD);
        block.addView(heading);
        for (String issue : known) {
            TextView line = text("· " + issue, UiTokens.BODY, AppUi.MUTED);
            line.setPadding(0, dp(2), 0, 0);
            block.addView(line);
        }
        body.addView(block);
    }

    private Card buildCard(AppChangelog.Entry entry, boolean expanded, boolean current) {
        LinearLayout card = card();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(UiTokens.MIN_TOUCH));
        head.setPadding(dp(UiTokens.SPACE_16), dp(UiTokens.SPACE_12),
            dp(UiTokens.SPACE_16), dp(UiTokens.SPACE_12));
        head.setFocusable(true);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("v" + entry.version, UiTokens.CARD_TITLE, AppUi.INK);
        title.setTypeface(BOLD);
        titleRow.addView(title);
        if (current) {
            TextView badge = text("当前版本", 11, AppUi.ACCENT);
            badge.setBackground(AppUi.shape(this, AppUi.SOFT, 8));
            badge.setPadding(dp(6), dp(2), dp(6), dp(2));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.leftMargin = dp(UiTokens.SPACE_8);
            titleRow.addView(badge, badgeParams);
        }
        labels.addView(titleRow);

        TextView date = text(entry.date, UiTokens.BODY_SMALL, AppUi.MUTED);
        date.setPadding(0, dp(2), 0, 0);
        labels.addView(date);
        head.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView arrow = new ImageView(this);
        arrow.setImageDrawable(AppUi.icon(this, "caret", openedColor(expanded), 18));
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        arrow.setRotation(expanded ? 180f : 0f);
        head.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(18)));

        card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(UiTokens.SPACE_16), 0, dp(UiTokens.SPACE_16), dp(UiTokens.SPACE_16));
        for (AppChangelog.Item entryItem : entry.items) body.addView(item(entryItem));
        knownIssues(body, entry.known);

        final Card state = new Card(card, entry, head, body, arrow, title);
        // 先按终态挂上去：展开的用自适应高度，收起的从 0 高度开始，避免首帧闪一下全文。
        body.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            expanded ? ViewGroup.LayoutParams.WRAP_CONTENT : 0));
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        card.addView(body);
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        state.open = expanded;
        applyHeadState(state);

        AppUi.press(head, AppUi.SURFACE, UiTokens.CARD_RADIUS);
        head.setOnClickListener(v -> toggle(state));
        return state;
    }

    private int openedColor(boolean expanded) {
        return expanded ? AppUi.ACCENT : AppUi.MUTED;
    }

    /** 行标题与箭头的视觉状态跟着展开与否走：收起的版本弱化成次要色，形成层级。 */
    private void applyHeadState(Card card) {
        card.title.setTextColor(card.open ? AppUi.INK : AppUi.MUTED);
        card.arrow.setImageDrawable(AppUi.icon(this, "caret", openedColor(card.open), 18));
        card.head.setContentDescription("版本 " + card.entry.version + "，" + card.entry.date
            + "，" + (card.open ? "已展开" : "已收起"));
    }

    // ---- 展开 / 收起 ------------------------------------------------------------------------

    private void toggle(Card card) {
        card.open = !card.open;
        final int generation = ++card.generation;
        if (card.running != null) {
            // 正在跑的动画先停：它的 end 回调已经过期，不会再改状态。
            card.running.cancel();
            card.running = null;
        }
        applyHeadState(card);
        int from = card.body.getHeight();
        int to = card.open ? measureBody(card.body) : 0;

        if (!MotionSpec.shouldAnimate(this) || from == to) {
            // 关动画（或本来就到位）：直接给终态，展开交回自适应高度，旋转屏幕后不会留一截空的。
            settle(card, card.open ? ViewGroup.LayoutParams.WRAP_CONTENT : 0);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(MotionSpec.LIST_TOGGLE_DURATION);
        animator.setInterpolator(MotionSpec.settle());
        animator.addUpdateListener(a -> {
            if (generation != card.generation) return;
            height(card, (Integer) a.getAnimatedValue());
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (generation != card.generation) return;   // 被下一次点击接管，别落终态
                card.running = null;
                settle(card, card.open ? ViewGroup.LayoutParams.WRAP_CONTENT : 0);
            }
        });
        card.running = animator;
        animator.start();
    }

    /** 卡片刚从 0 高度展开时量不出自身高度，得先按卡片宽度量一遍内容。 */
    private int measureBody(View body) {
        int width = body.getWidth();
        if (width <= 0) {
            int screen = getResources().getDisplayMetrics().widthPixels;
            // 屏幕宽 − 左右页边距 − 卡片左右内边距，正好是正文那一列的宽度。
            width = screen - 2 * AppUi.side(this, 14) - 2 * dp(UiTokens.SPACE_16);
        }
        body.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, width), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return body.getMeasuredHeight();
    }

    private void height(Card card, int pixels) {
        ViewGroup.LayoutParams params = card.body.getLayoutParams();
        params.height = pixels;
        card.body.setLayoutParams(params);
        card.body.setVisibility(pixels > 0 ? View.VISIBLE : View.GONE);
    }

    /** 落到终态：展开交回自适应高度，收起留 0 并隐藏，省掉一次无谓的绘制。 */
    private void settle(Card card, int pixels) {
        ViewGroup.LayoutParams params = card.body.getLayoutParams();
        params.height = pixels;
        card.body.setLayoutParams(params);
        card.body.setVisibility(pixels == 0 ? View.GONE : View.VISIBLE);
    }

    // ---- 页面 ------------------------------------------------------------------------------

    private int installedCode() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) info.getLongVersionCode() : info.versionCode;
        } catch (Exception unknown) {
            return -1;
        }
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
        back.setMinHeight(dp(UiTokens.MIN_TOUCH));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView heading = text("更新日志", UiTokens.PAGE_TITLE, AppUi.INK);
        heading.setTypeface(BOLD);
        root.addView(heading);

        String installed = AppUi.version(this);
        int code = installedCode();
        TextView caption = text(installed.isEmpty()
            ? "每个正式版本改了什么"
            : "当前安装 " + installed + (code > 0 ? "（" + code + "）" : "")
                + " · 按版本从新到旧",
            UiTokens.BODY_SMALL, AppUi.MUTED);
        caption.setPadding(0, dp(2), 0, dp(UiTokens.SPACE_12));
        root.addView(caption);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(UiTokens.SPACE_16));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        AppChangelog.Entry current = AppChangelog.current(installed);
        AppChangelog.Entry[] entries = AppChangelog.released();
        for (int i = 0; i < entries.length; i++) {
            AppChangelog.Entry entry = entries[i];
            // 最新一版默认展开；正是当前安装的版本也展开——读者最先想看的是自己这一版。
            boolean expanded = i == 0 || entry == current;
            content.addView(buildCard(entry, expanded, entry == current).root);
            gap(content, UiTokens.SPACE_12);
        }

        // 开发包（或刚装上还没发布的版本）不该被误当成线上版：说清楚，而不是给谁打个「当前」。
        if (current == null && !installed.isEmpty() && entries.length > 0) {
            TextView note = text("此版本尚未正式发布，下面是已发布版本中最新的一版（"
                + entries[0].version + "）。", UiTokens.BODY_SMALL, AppUi.MUTED);
            note.setPadding(dp(UiTokens.SPACE_4), dp(UiTokens.SPACE_16), dp(UiTokens.SPACE_4), 0);
            content.addView(note);
        }

        TextView footer = text("更新日志随安装包一起保存，不联网也能看。更早的版本没有留下带日期的"
            + "发布记录，暂未收录。", UiTokens.BODY_SMALL, AppUi.MUTED);
        footer.setPadding(dp(UiTokens.SPACE_4), dp(UiTokens.SPACE_16), dp(UiTokens.SPACE_4), 0);
        content.addView(footer);

        setContentView(root);
        root.requestApplyInsets();
    }
}
