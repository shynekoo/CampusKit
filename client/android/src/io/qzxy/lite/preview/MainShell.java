package io.qzxy.lite.preview;

import android.app.Activity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * 主屏外壳：header / content / nav 三段竖向排列，整个 Activity 的生命周期里只建一次。
 *
 * <p>切 Tab 只换 contentHost 里的 Screen 根，header 与 navHolder 原封不动——底栏因此不会消失
 * 再出现。系统状态栏、导航栏与挖孔的 Insets 只在这里处理一次，contentHost 拿 0+weight=1 的
 * 完整可用高度，课表 Pager 仍然量到真实高度，不会退化成 UNSPECIFIED。</p>
 *
 * <p>Activity 的 {@code root} 仍是 LinearLayout，但它现在是「当前 Screen 的内容根」，被塞进
 * contentHost；三个 Screen 的 {@code a.root.addView(...)} 逻辑无需改动。</p>
 */
final class MainShell extends LinearLayout {
    final FrameLayout headerHost;
    final FrameLayout contentHost;
    final AnimatedNavBar navBar;

    MainShell(Activity activity, AnimatedNavBar.Listener navListener) {
        super(activity);
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundColor(AppUi.BG);

        headerHost = new FrameLayout(activity);
        contentHost = new FrameLayout(activity);
        navBar = new AnimatedNavBar(activity, navListener);

        LinearLayout navHolder = new LinearLayout(activity);
        navHolder.setOrientation(LinearLayout.VERTICAL);
        // 外观修正：水平/底部留白从 8dp 收敛到 4dp，缩小无意义留白；顶部仍留 2dp 与内容分隔。
        // 左右边距仍叠加在 shell 的 AppUi.side() 自适应之上，系统导航栏 Insets 仍只在 shell 处理一次。
        navHolder.setPadding(AppUi.dp(activity, 4), AppUi.dp(activity, 2), AppUi.dp(activity, 4), AppUi.dp(activity, 4));
        navHolder.addView(navBar, new LinearLayout.LayoutParams(-1, -2));

        addView(headerHost, new LinearLayout.LayoutParams(-1, -2));
        addView(contentHost, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(navHolder, new LinearLayout.LayoutParams(-1, -2));

        // 旧版 root 的水平边距是 AppUi.side()（窄屏收紧、宽屏居中），垂直方向另有 8dp 顶距。
        // 现在这些连同系统 Insets 一起只在外壳落实一次：横向 = side + 挖孔/系统栏，顶部 =
        // 8dp + 状态栏，底部 = 导航栏。三段内部不再各自加边距，避免重复。
        final int side = AppUi.side(activity, 8);
        final int topGap = AppUi.dp(activity, 8);
        activity.getWindow().setDecorFitsSystemWindows(false);
        setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            setPadding(side + bars.left, topGap + bars.top, side + bars.right, bars.bottom);
            return insets;
        });
        setPadding(side, topGap, side, 0);
    }
}
