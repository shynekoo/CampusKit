package io.qzxy.lite.preview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * UI 2.0 的动效单一来源：按压、导航胶囊、Tab 进出场的时长与位移都在这里。
 *
 * <p>{@link #shouldAnimate(Context)} 是唯一的「系统是否允许动画」判定点。Android 8.0 起用
 * {@link ValueAnimator#areAnimatorsEnabled()}（它直接反映「动画缩放=0」的无障碍设置）；更低版本
 * 读系统动画倍率。关闭时所有组件应立刻跳到终态，不播放任何过渡。</p>
 */
final class MotionSpec {
    static final long PRESS_DURATION = 140L;
    static final long NAV_DURATION = 260L;
    static final long TAB_ENTER_DURATION = 220L;
    static final long TAB_EXIT_DURATION = 180L;
    static final int TAB_OFFSET_DP = 8;
    /** 周次滑页：‹ › 松手归位与跨周跳转共用同一个时长，两处不一致就会像两块板在打架。 */
    static final long WEEK_SLIDE_DURATION = 190L;
    /** 更新日志里版本卡片的展开/收起：高度与箭头旋转同一时长，箭头不会比内容跑得快。 */
    static final long LIST_TOGGLE_DURATION = 220L;

    private static final Interpolator INDICATOR = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private MotionSpec() { }

    /** 胶囊指示器用的快出慢入曲线，全局共享同一个实例。 */
    static Interpolator indicator() { return INDICATOR; }

    /** 列表展开用的减速曲线：开头快、末尾轻，收尾不显得"顶"一下。 */
    static Interpolator settle() { return new DecelerateInterpolator(1.6f); }

    /** 系统动画被关闭（无障碍「移除动画」或动画缩放为 0）时返回 false。 */
    static boolean shouldAnimate(Context context) {
        if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
        try {
            float scale = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale > 0f;
        } catch (Exception unavailable) {
            return true;
        }
    }
}
