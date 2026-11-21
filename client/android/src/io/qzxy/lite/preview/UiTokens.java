// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

/**
 * UI 2.0 的尺寸 / 字号单一来源。全部是逻辑 dp / sp 数值，调用点自行用 {@link AppUi#dp} 换算成
 * 像素——这里不提前乘密度，免得同一份数值在两处被换算两次。
 *
 * <p>第一阶段（UI-1）的数值与原实现逐字相同：普通卡片 18dp、底栏 24dp、胶囊 18dp。底栏在随后的
 * 外观修正里单独放大到 28dp、胶囊收敛到只包图标的 16dp（普通卡片 18dp 保持不变，避免全局视觉
 * 跳变）。页面左右边距仍归 {@link AppUi#side} 管，窄屏自适应规则不在这里重复。</p>
 */
final class UiTokens {
    // 圆角
    static final int CARD_RADIUS = 18;
    static final int NAV_RADIUS = 28;
    static final int PILL_RADIUS = 16;

    // 基础间距
    static final int SPACE_4 = 4;
    static final int SPACE_8 = 8;
    static final int SPACE_12 = 12;
    static final int SPACE_16 = 16;
    static final int SPACE_20 = 20;
    static final int SPACE_24 = 24;

    // 触控与导航
    static final int MIN_TOUCH = 48;
    static final int NAV_ICON = 22;
    static final int NAV_TEXT = 12;
    /** 底栏内容高度，落在 68–76dp 目标区间内；宽度/左右边距仍走 MainShell 的 AppUi.side()。 */
    static final int NAV_CONTENT_HEIGHT = 72;
    /** 选中胶囊只覆盖图标：64×32dp，圆角 PILL_RADIUS。 */
    static final int PILL_W = 64;
    static final int PILL_H = 32;

    // 字号
    static final int PAGE_TITLE = 28;
    static final int CARD_TITLE = 16;
    static final int BODY = 13;
    static final int BODY_SMALL = 12;

    private UiTokens() { }
}
