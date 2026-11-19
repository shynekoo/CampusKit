package io.qzxy.lite.preview;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
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
 */
final class AnimatedNavBar extends FrameLayout {
    private static final String[] LABELS = {"今日", "课表", "账号"};
    private static final String[] ICONS = {"today", "calendar", "user"};

    private final View indicator;
    private final LinearLayout tabRow;
    private final LinearLayout[] tabs = new LinearLayout[3];
    private final TextView[] captions = new TextView[3];
    private final AppUi.Icon[] icons = new AppUi.Icon[3];

    private final ArgbEvaluator evaluator = new ArgbEvaluator();
    private ValueAnimator capsuleAnim;
    private ValueAnimator[] colorAnims = new ValueAnimator[3];
    private float[] colorState = new float[3];   // 0 = MUTED, 1 = ACCENT
    private int selected = 0;
    private int slotWidth, inset, indicatorW, indicatorH;
    private boolean layoutReady;

    interface Listener { void onTab(int index); }
    private Listener listener;

    AnimatedNavBar(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        this.inset = AppUi.dp(context, MotionSpec.TAB_OFFSET_DP);
        // 旧版的细描边胶囊外框：hairline 四角均匀，不加 elevation 投影（偏下会在两个下角留黑印）。
        setBackground(AppUi.outlined(context, AppUi.SURFACE, UiTokens.NAV_RADIUS, AppUi.LINE));
        setPadding(AppUi.dp(context, 5), AppUi.dp(context, 5), AppUi.dp(context, 5), AppUi.dp(context, 5));

        indicator = new View(context);
        indicator.setBackground(AppUi.shape(context, AppUi.SOFT, UiTokens.PILL_RADIUS));
        addView(indicator, new FrameLayout.LayoutParams(0, 0, Gravity.TOP));

        tabRow = new LinearLayout(context);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(AppUi.dp(context, 5), AppUi.dp(context, 5), AppUi.dp(context, 5), AppUi.dp(context, 5));
        for (int i = 0; i < 3; i++) {
            LinearLayout tab = new LinearLayout(context);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setMinimumHeight(AppUi.dp(context, 56));
            tab.setPadding(0, AppUi.dp(context, 6), 0, AppUi.dp(context, 6));
            AppUi.press(tab, 0x00ffffff, UiTokens.PILL_RADIUS);

            ImageView iconView = new ImageView(context);
            // 用现有 icon 工厂拿图标，并把它当 AppUi.Icon 缓存下来：换色只改字段 + 重绘，
            // 不在每一帧重新创建 Drawable。
            AppUi.Icon icon = (AppUi.Icon) AppUi.icon(context, ICONS[i], AppUi.MUTED, UiTokens.NAV_ICON);
            iconView.setImageDrawable(icon);
            iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tab.addView(iconView, new LinearLayout.LayoutParams(-2, -2));

            TextView caption = new TextView(context);
            caption.setText(LABELS[i]);
            caption.setTextSize(UiTokens.NAV_TEXT);
            caption.setGravity(Gravity.CENTER);
            caption.setPadding(0, AppUi.dp(context, 4), 0, 0);
            tab.addView(caption, new LinearLayout.LayoutParams(-2, -2));

            final int index = i;
            tab.setOnClickListener(v -> { if (listener != null) listener.onTab(index); });
            tabRow.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
            tabs[i] = tab;
            captions[i] = caption;
            icons[i] = icon;
            colorState[i] = 0f;
        }
        addView(tabRow, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int rowWidth = tabRow.getWidth() - tabRow.getPaddingLeft() - tabRow.getPaddingRight();
        int rowHeight = tabRow.getHeight() - tabRow.getPaddingTop() - tabRow.getPaddingBottom();
        if (rowWidth <= 0 || rowHeight <= 0) return;
        slotWidth = rowWidth / 3;
        indicatorW = slotWidth - inset;
        indicatorH = rowHeight - inset;
        // 胶囊的父容器就是这个 FrameLayout，tabRow 也在同一个父容器里；起点取 tabRow 的 padding 内沿，
        // 全程只用同一层坐标 + translationX，不混用 getX/getLeft 或跨父容器换算。
        int originX = tabRow.getLeft() + tabRow.getPaddingLeft();
        int originY = tabRow.getTop() + tabRow.getPaddingTop();
        if (indicator.getWidth() != indicatorW || indicator.getHeight() != indicatorH) {
            indicator.measure(View.MeasureSpec.makeMeasureSpec(indicatorW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(indicatorH, View.MeasureSpec.EXACTLY));
        }
        indicator.layout(originX, originY, originX + indicatorW, originY + indicatorH);
        layoutReady = true;
        // 首次布局 / 旋转 / 分区变化：直接放到终态，绝不播放从左滑来的假动画。
        indicator.setTranslationX(targetX(selected));
    }

    private float targetX(int index) {
        if (slotWidth <= 0) return 0f;
        return (float) (index * slotWidth + (slotWidth - indicatorW) / 2);
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
