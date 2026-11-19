package io.qzxy.lite.preview;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * 手势返回时的那张卡片：底下一层是上一页的画面，上面一层是这一页自己。
 *
 * <p>手指从边缘往里滑，上面那层按进度缩小、起圆角、往旁边退，露出下面那张——这就是参考项目
 * MyCHU 的样子。系统在 Android 14 起也肯画，但换台机器、换个厂商主题就未必；所以这里不赌系统，
 * 自己按 {@code BackEvent.getProgress()} 画。上一页的画面是离开那一页时拍下来的，因为跨 Activity
 * 时真正的上一页被我们的窗口盖着，看不见。</p>
 *
 * <p>只给 {@code Page} 用：{@code Page} 把页面内容塞进 {@link #put(View)}，把手势回调转成
 * {@link #start}/{@link #progress}/{@link #cancel}/{@link #leave}。</p>
 */
final class CardLayer {
    /** 卡片最终缩到 0.90，横向让出 26% 的宽度，圆角 18dp——和参考项目的观感一致。 */
    private static final float SCALE_DROP = 0.10f;
    private static final float SLIDE = 0.26f;
    private static final float RADIUS_DP = 18f;
    /** 底图起始缩到 0.90，跟卡片一起「推近」，看着才像两层在动。 */
    private static final float PREVIEW_FROM = 0.90f;

    /** 上一页的画面，只等一张卡来取；超过几秒没人要就丢掉，免得停在一张过时的画面上。 */
    private static Bitmap pending;
    private static long pendingAt;

    private final Activity activity;
    private final FrameLayout root;
    private final FrameLayout card;
    private final ImageView preview;
    private final View scrim;
    private final float density;

    /** 当前圆角（px），只在整数 dp 变化时才通知重裁，免得每帧都重建裁剪。 */
    private float radius;
    private int radiusDp = -1;
    /** 手势从哪一边来：+1 右移，-1 左移。 */
    private float edgeSign = 1f;
    private float progress;
    private ValueAnimator animator;

    CardLayer(Activity activity) {
        this.activity = activity;
        density = activity.getResources().getDisplayMetrics().density;

        root = new FrameLayout(activity);
        root.setBackgroundColor(AppUi.BG);

        preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setAlpha(0f);
        Bitmap shot = take();
        if (shot != null) preview.setImageBitmap(shot);
        root.addView(preview, match());

        scrim = new View(activity);
        scrim.setBackgroundColor(0xff000000);
        scrim.setAlpha(0f);
        root.addView(scrim, match());

        card = new FrameLayout(activity);
        card.setOutlineProvider(OUTLINE);
        card.setClipToOutline(true);
        root.addView(card, match());
    }

    // ---- the page that sits on top ---------------------------------------------------------

    /** 铺进要显示的页面；重画时也走这里，换掉旧内容。 */
    void put(View view) {
        if (view == null) return;
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        card.removeAllViews();
        card.addView(view, match());
    }

    View root() { return root; }

    /** 有没有上一页的画面可露；没有时动画只做淡出，免得露出一块纯色再跳走。 */
    private boolean hasPreview() { return preview.getDrawable() != null; }

    // ---- the gesture -----------------------------------------------------------------------

    void start(int swipeEdge) {
        cancelAnimation();
        // BackEvent.EDGE_LEFT = 0：从左边缘滑进来，卡片往右让。
        edgeSign = swipeEdge == 1 ? -1f : 1f;
        apply(progress);
    }

    void progress(float value) {
        progress = clamp01(value);
        apply(progress);
    }

    /** 中途放手：按原路弹回去。 */
    void cancel() {
        animateTo(0f, false, 200, new DecelerateInterpolator());
    }

    /** 手势提交、或页面自己要关了：走完最后一段并淡出，结束再真的 finish。 */
    void leave(Runnable afterwards) {
        if (card.getWidth() <= 0) { afterwards.run(); return; }
        animateTo(1f, true, 170, new AccelerateInterpolator(), afterwards);
    }

    // ---- drawing ---------------------------------------------------------------------------

    private void apply(float p) {
        int width = card.getWidth();
        if (width <= 0) return;

        float scale = 1f - SCALE_DROP * p;
        card.setScaleX(scale);
        card.setScaleY(scale);
        card.setTranslationX(edgeSign * width * SLIDE * p);

        // 圆角在前 60% 的手势里长满，之后不再变——手指还没走多远就已经像个卡片了。
        setRadiusDp(Math.round(RADIUS_DP * Math.min(1f, p * 1.67f)));

        float unveiled = hasPreview() ? Math.min(1f, p * 1.8f) : 0f;
        preview.setAlpha(unveiled);
        float previewScale = PREVIEW_FROM + (1f - PREVIEW_FROM) * p;
        preview.setScaleX(previewScale);
        preview.setScaleY(previewScale);
        // 起始时把上一页压暗一点，卡片离开的瞬间才显得「翻出来」。
        scrim.setAlpha(0.42f * (1f - unveiled));
    }

    private void setRadiusDp(int value) {
        if (value == radiusDp) return;
        radiusDp = value;
        radius = value * density;
        card.invalidateOutline();
    }

    private void rest() {
        progress = 0f;
        card.setScaleX(1f);
        card.setScaleY(1f);
        card.setTranslationX(0f);
        card.setAlpha(1f);
        setRadiusDp(0);
        preview.setAlpha(0f);
        preview.setScaleX(PREVIEW_FROM);
        preview.setScaleY(PREVIEW_FROM);
        scrim.setAlpha(0f);
    }

    private void animateTo(float target, boolean fade, long duration,
                           android.view.animation.Interpolator easing) {
        animateTo(target, fade, duration, easing, null);
    }

    private void animateTo(float target, boolean fade, long duration,
                           android.view.animation.Interpolator easing, Runnable afterwards) {
        cancelAnimation();
        final float from = progress;
        final float startAlpha = card.getAlpha();
        final float endAlpha = fade ? 0f : 1f;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(duration);
        anim.setInterpolator(easing);
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            progress = from + (target - from) * t;
            apply(progress);
            if (fade) card.setAlpha(startAlpha + (endAlpha - startAlpha) * t);
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator finished) {
                // 被新的动画或取消顶掉的那一个不算数：cancel() 也会走到这里。
                if (finished != animator) return;
                animator = null;
                if (target == 0f) rest();
                if (afterwards != null) afterwards.run();
            }
        });
        animator = anim;
        anim.start();
    }

    private void cancelAnimation() {
        ValueAnimator running = animator;
        animator = null;
        if (running != null) running.cancel();
    }

    // ---- the previous page's picture --------------------------------------------------------

    /**
     * 离开这一页之前把画面拍下来，留给下一张卡当底。半分辨率就够：它只在卡片缩小后才露出来，
     * 而且总是压着一层暗色。
     */
    static void hold(Activity activity) {
        // Defence in depth: even if a caller reaches hold() directly, a page that denies snapshots
        // must never be photographed and must not leave an older bitmap behind for the next page.
        if (activity instanceof Page && !((Page) activity).allowPageSnapshot()) {
            clearPending();
            return;
        }
        View decor = activity.getWindow().getDecorView();
        int width = decor.getWidth(), height = decor.getHeight();
        if (width <= 0 || height <= 0) return;
        Bitmap shot;
        try {
            shot = Bitmap.createBitmap(width / 2, height / 2, Bitmap.Config.RGB_565);
        } catch (OutOfMemoryError noRoom) {
            return;
        }
        Canvas canvas = new Canvas(shot);
        canvas.drawColor(AppUi.BG);
        canvas.scale(0.5f, 0.5f);
        try {
            decor.draw(canvas);
        } catch (RuntimeException unsupported) {
            shot.recycle();
            return;
        }
        synchronized (CardLayer.class) {
            if (pending != null && !pending.isRecycled()) pending.recycle();
            pending = shot;
            pendingAt = System.currentTimeMillis();
        }
    }

    /** Free the backdrop bitmap when the page goes away; it is half a screen of pixels held only
     * here. Set null first so the ImageView cannot draw a recycled bitmap on a late frame. */
    void release() {
        android.graphics.drawable.Drawable drawable = preview.getDrawable();
        preview.setImageBitmap(null);
        if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    /** Drop the reusable snapshot without consuming it for a new card. Used when leaving a
     * sensitive page: nothing from that page should survive into the next back animation. */
    static void clearPending() {
        synchronized (CardLayer.class) {
            if (pending != null && !pending.isRecycled()) pending.recycle();
            pending = null;
            pendingAt = 0L;
        }
    }

    private static Bitmap take() {
        synchronized (CardLayer.class) {
            Bitmap shot = pending;
            pending = null;
            if (shot == null) return null;
            if (System.currentTimeMillis() - pendingAt > 4000 || shot.isRecycled()) {
                if (!shot.isRecycled()) shot.recycle();
                return null;
            }
            return shot;
        }
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }

    private final ViewOutlineProvider OUTLINE = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            if (radius < 1f) {
                outline.setRect(0, 0, view.getWidth(), view.getHeight());
            } else {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        }
    };
}
