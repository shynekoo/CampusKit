// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 常驻的三项底栏：一层可移动的胶囊背景，上面压着一行平均分布的 Tab。
 *
 * <p>三项只在构造时各 new 一次，切 Tab 走 {@link #select(int, boolean)} 只更新颜色与胶囊位置，
 * 不再重建视图——这是「底栏不消失再出现」的关键。胶囊只用 {@code translationX} 移动，坐标全程
 * 留在 tabRow 父容器这一层，不混用 getX/getLeft。图标 Drawable 也只建一次，之后改色 + 重绘。</p>
 *
 * <p>外观修正：胶囊只覆盖图标那一行（64×32dp、圆角 16dp），不再铺满整格；每个 Tab 是一个固定宽度
 * 的图标槽 + 12sp 文字，图标槽与胶囊同宽，保证三个入口始终对齐。图标 ImageView 用固定 dp 尺寸
 * ——AppUi.Icon 没有固有尺寸，wrap_content 会把它量成 0×0，这正是真机「只有文字」的根因。</p>
 */
final class AnimatedNavBar extends FrameLayout {
    private static final String[] LABELS = {"今日", "课表", "账号"};
    private static final String[] ICONS = {"home", "calendar", "user"};

    private final View indicator;
    private final LinearLayout tabRow;
    private final LinearLayout[] tabs = new LinearLayout[3];
    private final FrameLayout[] iconSlots = new FrameLayout[3];
    private final TextView[] captions = new TextView[3];
    private final AppUi.Icon[] icons = new AppUi.Icon[3];

    private final ArgbEvaluator evaluator = new ArgbEvaluator();
    private ValueAnimator capsuleAnim;
    private ValueAnimator[] colorAnims = new ValueAnimator[3];
    private float[] colorState = new float[3];   // 0 = MUTED, 1 = ACCENT
    private int selected = 0;
    private int slotWidth, indicatorW, indicatorH;
    private boolean layoutReady;

    interface Listener { void onTab(int index); }
    private Listener listener;

    AnimatedNavBar(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        indicatorW = AppUi.dp(context, UiTokens.PILL_W);
        indicatorH = AppUi.dp(context, UiTokens.PILL_H);
        // 外框：细描边四角均匀，不加 elevation 投影（偏下会在两个下角留黑印）。圆角走 token 28dp。
        setBackground(AppUi.outlined(context, AppUi.SURFACE, UiTokens.NAV_RADIUS, AppUi.LINE));
        // 缩小无意义留白：左右 4dp、上下 4dp；宽度/左右边距仍由 MainShell 的 AppUi.side() 决定。
        setPadding(AppUi.dp(context, 4), AppUi.dp(context, 4), AppUi.dp(context, 4), AppUi.dp(context, 4));

        indicator = new View(context);
        indicator.setBackground(AppUi.shape(context, AppUi.SOFT, UiTokens.PILL_RADIUS));
        addView(indicator, new FrameLayout.LayoutParams(indicatorW, indicatorH, Gravity.TOP));

        tabRow = new LinearLayout(context);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            LinearLayout tab = new LinearLayout(context);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setMinimumHeight(AppUi.dp(context, UiTokens.MIN_TOUCH));
            AppUi.press(tab, 0x00ffffff, UiTokens.PILL_RADIUS);

            // 图标槽与胶囊同宽同高：胶囊盖住的就是这一格，不会压到下面的文字。
            FrameLayout iconSlot = new FrameLayout(context);
            ImageView iconView = new ImageView(context);
            // 用现有 icon 工厂拿图标，并把它当 AppUi.Icon 缓存下来：换色只改字段 + 重绘，
            // 不在每一帧重新创建 Drawable。
            AppUi.Icon icon = (AppUi.Icon) AppUi.icon(context, ICONS[i], AppUi.MUTED, UiTokens.NAV_ICON);
            // 固定 dp 尺寸而非 wrap_content：自定义 Drawable 无固有尺寸，wrap_content 会量成 0×0。
            iconView.setImageDrawable(icon);
            iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            iconSlot.addView(iconView, new FrameLayout.LayoutParams(AppUi.dp(context, UiTokens.NAV_ICON),AppUi.dp(context, UiTokens.NAV_ICON), Gravity.CENTER));
            iconSlot.setClickable(false);
            tab.addView(iconSlot, new LinearLayout.LayoutParams(indicatorW, indicatorH));

            TextView caption = new TextView(context);
            caption.setText(LABELS[i]);
            caption.setTextSize(UiTokens.NAV_TEXT);
            caption.setGravity(Gravity.CENTER);
            caption.setPadding(0, AppUi.dp(context, 2), 0, 0);
            tab.addView(caption, new LinearLayout.LayoutParams(-2, -2));

            final int index = i;
            tab.setOnClickListener(v -> { if (listener != null) listener.onTab(index); });
            // 高度 MATCH_PARENT 撑满 tabRow，tabRow 固定 NAV_CONTENT_HEIGHT - 外框上下留白。
            tabRow.addView(tab, new LinearLayout.LayoutParams(0, -1, 1));
            tabs[i] = tab;
            iconSlots[i] = iconSlot;
            captions[i] = caption;
            icons[i] = icon;
            colorState[i] = 0f;
        }
        int rowHeight = AppUi.dp(context, UiTokens.NAV_CONTENT_HEIGHT) - AppUi.dp(context, 4) * 2;
        addView(tabRow, new FrameLayout.LayoutParams(-1, rowHeight, Gravity.TOP));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int rowWidth = tabRow.getWidth() - tabRow.getPaddingLeft() - tabRow.getPaddingRight();
        if (rowWidth <= 0 || iconSlots[0].getHeight() <= 0) return;
        slotWidth = rowWidth / 3;
        // 胶囊的父容器就是这个 FrameLayout，tabRow 也在同一父容器里。图标槽是 tab 的子、tab 是
        // tabRow 的子，逐层 getLeft/getTop 累加得到图标槽在胶囊父容器坐标系的落点；全程只用
        // 同一层坐标 + translationX，不混用 getX 或跨父容器换算。
        int originX = tabRow.getLeft() + tabRow.getPaddingLeft()
            + tabs[0].getLeft() + iconSlots[0].getLeft();
        int iconTop = tabRow.getTop() + tabs[0].getTop() + iconSlots[0].getTop();
        indicator.layout(originX, iconTop, originX + indicatorW, iconTop + indicatorH);
        layoutReady = true;
        // 首次布局 / 旋转 / 分区变化：直接放到终态，绝不播放从左滑来的假动画。
        indicator.setTranslationX(targetX(selected));
    }

    private float targetX(int index) {
        if (slotWidth <= 0) return 0f;
        // 三项等宽、图标槽在每个 tab 内水平居中，相邻槽的间距正好是一个 slotWidth。
        return (float) index * slotWidth;
    }

    /** 更新选中项。animate=false 用于初次显示、旋转、数据刷新后的同步。 */
    void select(int target, boolean animate) {
        if (target < 0 || target > 2) return;
        int index = target;
        int previous = selected;
        selected = index;
        updateAccessibility();
        boolean motion = animate && layoutReady && MotionSpec.shouldAnimate(getContext()) && slotWidth > 0;
        moveCapsule(previous, index, motion);
        for (int i = 0; i < 3; i++) animateColor(i, i == index ? 1f : 0f, motion);
    }

    private void updateAccessibility() {
        for (int i = 0; i < 3; i++) {
            tabs[i].setSelected(i == selected);
            tabs[i].setContentDescription(LABELS[i] + (i == selected ? "，已选中" : ""));
        }
    }

    private void moveCapsule(int from, int to, boolean animate) {
        if (capsuleAnim != null) { capsuleAnim.cancel(); capsuleAnim = null; }
        float end = targetX(to);
        if (!animate) { indicator.setTranslationX(end); return; }
        // 从「当前位置」动画到新目标：快速连点时先 cancel 旧动画，translationX 停在中间值，
        // 新动画从该值接着走，不会跳回起点重播。
        ValueAnimator anim = ValueAnimator.ofFloat(indicator.getTranslationX(), end);
        anim.setDuration(MotionSpec.NAV_DURATION);
        anim.setInterpolator(MotionSpec.indicator());
        anim.addUpdateListener(a -> indicator.setTranslationX((Float) a.getAnimatedValue()));
        capsuleAnim = anim;
        anim.start();
    }

    private void animateColor(int index, float end, boolean animate) {
        if (colorAnims[index] != null) { colorAnims[index].cancel(); colorAnims[index] = null; }
        float start = colorState[index];
        if (!animate || Math.abs(end - start) < 0.001f) {
            colorState[index] = end;
            paint(index, end);
            return;
        }
        ValueAnimator anim = ValueAnimator.ofFloat(start, end);
        anim.setDuration(MotionSpec.NAV_DURATION);
        anim.addUpdateListener(a -> {
            float value = (Float) a.getAnimatedValue();
            colorState[index] = value;
            paint(index, value);
        });
        colorAnims[index] = anim;
        anim.start();
    }

    /** 已缓存的 Drawable 直接改色 + 重绘，不每帧新建。 */
    private void paint(int index, float selectedFraction) {
        int color = (Integer) evaluator.evaluate(selectedFraction, AppUi.MUTED, AppUi.ACCENT);
        captions[index].setTextColor(color);
        AppUi.Icon icon = icons[index];
        icon.color = color;
        icon.invalidateSelf();
    }

    /** 主题变了以后重取 AppUi 调色板，按当前选中状态立即重画。 */
    void refreshPalette() {
        indicator.setBackground(AppUi.shape(getContext(), AppUi.SOFT, UiTokens.PILL_RADIUS));
        for (int i = 0; i < 3; i++) paint(i, colorState[i]);
    }

    void cancelAnimations() {
        if (capsuleAnim != null) { capsuleAnim.cancel(); capsuleAnim = null; }
        for (int i = 0; i < 3; i++) if (colorAnims[i] != null) { colorAnims[i].cancel(); colorAnims[i] = null; }
    }
}
