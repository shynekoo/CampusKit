package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

/**
 * 每个页面的共同外壳：进出动画，以及「返回」该由谁来画。
 *
 * <p>返回一共两种方式，都从 {@link #refreshBackMode()} 出去，页面自己不各写一套：</p>
 *
 * <ul>
 *   <li><b>跟手缩卡</b>（开关开、Android 14 起、且本页返回就一定关页）：页面被塞进
 *       {@link CardLayer}，跟着手势缩小、起圆角、退到一边，露出底下一张「上一页」的画面。
 *       这是唯一能保证跟手的做法——系统那套在不同版本、不同厂商主题上未必画得出来。</li>
 *   <li><b>轻过渡</b>（其余情况：开关关掉、系统太旧、或者这一页返回不一定关页面）：注册普通
 *       回调自己接管，用 {@code page_exit} 播一段缩小淡出。快、自然，不需要整页跟手。</li>
 * </ul>
 *
 * <p>为什么不再指望系统：{@code overrideActivityTransition(CLOSE, …)} 一旦设过，手势返回播的
 * 就是它；不清掉，系统的跨页面动画被顶掉，滑到一半什么都看不见——上一版「滑了没反应」就是这么
 * 来的。而清掉以后，能画成什么样又完全取决于系统和厂商。所以这里只把系统当作「手势进度的事件
 * 源」：位置、缩放、圆角、底图都自己算。</p>
 */
public class Page extends Activity {
    /** 自己画缩卡，手势进度由系统的 {@code OnBackAnimationCallback} 给。 */
    private static final int BACK_CARD = 0;
    /** 开关关掉（或系统画不了跟手）时的那段轻过渡：缩小淡出，不跟手。 */
    private static final int BACK_MILD = 1;

    /**
     * 只在「自己画」的方式下注册，用来接管返回。
     *
     * <p>声明成 {@code Object} 是故意的：minSdk 24，直接写 {@code OnBackInvokedCallback} 类型
     * 会让低版本设备在加载这个类时去找一个不存在的接口。</p>
     */
    private Object backCallback;

    /** 上一次落实的方式；-1 = 还没配过。每次 {@code onPause} 都清回 -1，回来必然重配。 */
    private int backMode = -1;

    /** 页面内容外面那层卡片；只有 34+ 且开关打开时才有。 */
    private CardLayer cardLayer;
    /** 页面自己给的根视图，重画时会换掉，所以要留着。 */
    private View content;
    /** 已经在走「关掉」的动画，别被第二次 finish() 打断成直切。 */
    private boolean closing;

    // ---- lifecycle -------------------------------------------------------------------------

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // 进出动画不在这里写死：统一由 refreshBackMode() 决定，避免两处各写一套互相顶掉。
        if (Diag.ON) Diag.step("返回手势", "SDK=" + Build.VERSION.SDK_INT
            + " 动画=" + (Theme.slide() ? "开" : "关") + " 类型=" + (backMayStay() ? "页内返回" : "关闭页面"));
    }

    @Override public void setContentView(View view) {
        content = view;
        install();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshBackMode();
    }

    @Override protected void onPause() {
        unregisterBackCallback();
        backMode = -1;
        super.onPause();
    }

    // ---- the layers ------------------------------------------------------------------------

    /** 把页面内容铺进卡片；重画时换掉旧内容，层本身不重建。 */
    private void install() {
        if (content == null) return;
        CardLayer layer = layer();
        if (layer == null) {
            if (content.getParent() instanceof ViewGroup) ((ViewGroup) content.getParent()).removeView(content);
            super.setContentView(content);
            return;
        }
        layer.put(content);
        super.setContentView(layer.root());
    }

    private CardLayer layer() {
        if (Build.VERSION.SDK_INT < 34 || !Theme.slide()) return null;
        if (cardLayer == null) cardLayer = new CardLayer(this);
        return cardLayer;
    }

    // ---- leaving ---------------------------------------------------------------------------

    @Override public void startActivity(Intent intent) {
        holdPreview();
        super.startActivity(intent);
        if (Build.VERSION.SDK_INT < 34) overridePendingTransition(R.anim.page_enter, 0);
    }

    @Override public void startActivity(Intent intent, Bundle options) {
        holdPreview();
        super.startActivity(intent, options);
        if (Build.VERSION.SDK_INT < 34) overridePendingTransition(R.anim.page_enter, 0);
    }

    @Override public void startActivityForResult(Intent intent, int request) {
        holdPreview();
        super.startActivityForResult(intent, request);
        if (Build.VERSION.SDK_INT < 34) overridePendingTransition(R.anim.page_enter, 0);
    }

    /** 下一页要拿这张画面当底：跨 Activity 时真正的上一页被新窗口盖着，只能自己拍一张。 */
    private void holdPreview() {
        if (Build.VERSION.SDK_INT >= 34 && Theme.slide()) CardLayer.hold(this);
    }

    /**
     * 应用里每个「返回」按钮都调 {@code finish()}，所以这里也是关闭动画唯一要说的地方。
     * 卡片模式下先播完那一段再真的关窗，否则会「啪」一下直切。
     */
    @Override public void finish() {
        if (!closing && cardLayer != null && backMode == BACK_CARD) {
            closing = true;
            cardLayer.leave(this::finishNow);
            return;
        }
        finishNow();
    }

    private void finishNow() {
        closing = true;
        super.finish();
        // 官方要求 overridePendingTransition 跟在 finish() 之后，否则可能被忽略。
        if (Build.VERSION.SDK_INT < 34) overridePendingTransition(0, R.anim.page_exit);
    }

    // ---- choosing how 返回 looks -----------------------------------------------------------

    /**
     * True for pages whose 返回 might not leave the page at all — a web view going one page back,
     * a tab bar returning to 今日. The system assumes back closes the activity, so it would shrink
     * the whole page before handing the press over; on these pages that reads as a bounce.
     *
     * <p>子类按当前状态作答（网页还有历史吗、还在别的 tab 上吗），状态变了再调一次
     * {@link #refreshBackMode()}，返回方式会跟着换。</p>
     */
    protected boolean backMayStay() { return false; }

    /**
     * 按「开关 + 系统版本 + 本页返回时会不会留在原地」选出方式并当场落实。开关刚被改过时也调
     * 这里，所以不必退出页面再看效果。
     */
    protected final void refreshBackMode() {
        int mode = pickBackMode();
        if (mode == BACK_CARD && cardLayer == null) install();
        if (mode == backMode) return;
        applyTransition(mode);
        applyCallback(mode);
        backMode = mode;
        if (Diag.ON) Diag.step("返回手势", "方式=" + (mode == BACK_CARD ? "跟手缩卡" : "轻过渡"));
    }

    /**
     * 跟手要两个条件：开关开着、系统肯把手势进度一帧一帧报过来（14 起才有
     * {@code OnBackAnimationCallback}）、以及这一页按返回一定关掉。差一个就用上一版那段
     * 轻过渡——缩小淡出，快、自然，不跟手。开关关掉时也是它。
     */
    private int pickBackMode() {
        if (Theme.slide() && Build.VERSION.SDK_INT >= 34 && !backMayStay()) return BACK_CARD;
        return BACK_MILD;
    }

    private void applyTransition(int mode) {
        if (Build.VERSION.SDK_INT < 34) return;
        if (mode == BACK_MILD) {
            // 上一版那段：窗口自己缩小淡出，不跟手，但胜在稳。
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.page_exit);
        } else {
            // 卡片自己画，窗口一点都别动，否则会和卡片叠成两层缩放。
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        }
    }

    private void applyCallback(int mode) {
        unregisterBackCallback();
        if (Build.VERSION.SDK_INT < 33) return;
        if (mode == BACK_CARD && cardLayer != null) {
            registerCardBack();
            return;
        }
        // 轻过渡：注册普通回调，系统就不再缩卡片，页面按 onBackPressed() 的说法走。
        android.window.OnBackInvokedCallback plain = () -> {
            if (Diag.ON) Diag.step("返回手势", "回调命中");
            onBackPressed();
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, plain);
        backCallback = plain;
    }

    /** 34+：拿到逐帧进度，交给卡片自己画。 */
    private void registerCardBack() {
        android.window.OnBackAnimationCallback animated = new android.window.OnBackAnimationCallback() {
            @Override public void onBackStarted(android.window.BackEvent event) {
                if (Diag.ON) Diag.step("返回手势", "开始 边缘=" + event.getSwipeEdge());
                cardLayer.start(event.getSwipeEdge());
            }

            @Override public void onBackProgressed(android.window.BackEvent event) {
                cardLayer.progress(event.getProgress());
            }

            @Override public void onBackCancelled() {
                if (Diag.ON) Diag.step("返回手势", "中途放弃，弹回");
                cardLayer.cancel();
            }

            @Override public void onBackInvoked() {
                if (Diag.ON) Diag.step("返回手势", "松手，关窗");
                closing = true;
                cardLayer.leave(Page.this::finishNow);
            }
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, animated);
        backCallback = animated;
    }

    private void unregisterBackCallback() {
        if (backCallback == null) return;
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                (android.window.OnBackInvokedCallback) backCallback);
        }
        backCallback = null;
    }
}
