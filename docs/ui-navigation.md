# UI 与导航

> 原生 View + Activity，无 Compose。返回/手势细节见 [`back-gesture.md`](back-gesture.md)；视觉与运行时行为仍需真机验收，见 [`real-device-acceptance.md`](real-device-acceptance.md)。

## 主屏结构

`TimetableActivity` 是主屏，三个 tab：**今日 / 课表 / 趣智校园**（`MainNavigator` 中 tab 0/1/2）。`render()` 只做分发：

| tab | Screen | 内容 |
| :-- | :-- | :-- |
| 今日 | `HomeScreen` | 校园生活入口、今天的课、教务查询、取件信息 |
| 课表 | `ScheduleScreen` | `WeekBoard` 周视图、Pager、自适应高度、周次切换 |
| 趣智校园 | `CampusScreen` | 账户入口、记录视图（campusView 0=入口，1=记录） |

三个 Screen 是 `TimetableActivity` 的静态嵌套类，持有 Activity 引用逐字搬运原方法；不放宽任何 private 可见性。`render()` 保持纯分发，改动时须维持这一边界。

## 返回栈规则（`MainNavigator`）

`core/MainNavigator` 只管「当前是哪个视图」与返回顺序，**不持有 View、不依赖 Android**：

1. 校园记录视图 → 先退回校园入口列表；
2. 非今日 tab → 回到今日 tab；
3. 今日 tab → 关闭页面。

恢复的 tab / campusView 值做 clamp 防越界；离开校园 tab 重置其内部 scope。Activity 只持久化/恢复两个 int。返回栈顺序由 core 断言覆盖，改动必须同步补 `CoreTests`。

## 返回手势

统一由 `Page.refreshBackMode()` 选择，页面不各写一套：

| 方式 | 时机 | 执行者 |
| :-- | :-- | :-- |
| 系统预测式 | 开关开 + Android 15(API 35)+ 本页返回一定关页 | 系统整页缩卡 |
| 本地缩放 | 开关开但系统画不出（≤14）或本页返回不关页 | `page_exit` 缩小淡出 |
| 直接切换 | 开关关 | 无动画 |

`CardLayer` 负责快照/预览卡，`allowPageSnapshot()` 决定页面能否进快照。manifest 开启 `enableOnBackInvokedCallback`；主题开关存 `Theme.slide`（默认 true）。

## 刷新与滚动

- 今日 tab 同日 30s 时钟 tick 走 `updateTodayDynamic()`：就地更新摘要与每行删除线/透明度/「正在上课」标记，不再全量 `render()`。
- `RefreshDecision` 决定 NONE / TODAY_DYNAMIC / FULL；跨天、切 tab 才 FULL，保留滚动位置；数据不一致自动回退 FULL。
- 所有改变今日课程集合的操作（增删改 / 导入 / 周次 / 主题）走 `render()` 全量重建并刷新引用。
- `WeekBoard` 按屏幕计算 gutter / padding / 字号（`BoardFit`），窄屏不挤成两字一行。

## 主题

`Theme` 一处下发颜色：明暗模式（自动/浅/深）、主题色、课表配色 18 槽、文字色、圆角、不透明度、网格线（默认关）、悬浮底栏。深色是独立灰阶，不是浅色反相。`AppearanceActivity` 改动即时预览。注意圆角预览绘制需按 `dp()` 换算，与 `WeekBoard` 实际格子一致。

## 桌面组件

`ScheduleWidget` / `ScheduleWidgetMedium` / `ScheduleWidgetWide` 三种尺寸，`AppWidgetProvider` 实现，课表变动后刷新。

## 自动化守卫的边界

`scripts/test_ui_contract.py` 有 **46 个静态锚点**：底部导航三项、WeekBoard onMeasure、滚动保存恢复、CardLayer 手势与快照、S4 策略、A3 就地更新分支、三个 Screen 存在、学业情况入口与卡片单一挂载点、教务卡 tile 顺序等。**它是静态守卫，不验证视觉/手势/真机效果**；D/E 组真机清单未完成前不得发布。
