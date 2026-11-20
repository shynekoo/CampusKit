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

### UI 2.0 常驻外壳（UI-1 / UI-2）

`render()` 已拆为 `ensureShell` / `renderHeader` / `buildScreen` / `renderContent` / `selectTab`：

- **`MainShell`** 竖向三段：headerHost、contentHost（0+weight=1）、navHolder。整个 Activity 只构建一次、`setContentView(shell)` 只调一次；系统栏/挖孔 Insets 只在此落实，底部不重复。课表 Pager 在 contentHost 内取到完整高度，不受 FrameLayout 测量退化影响。
- **`AnimatedNavBar`** 三项底栏只在构造时 new 一次，切 Tab 不再重建。单个胶囊背景只用 `translationX` 移动（260ms，PathInterpolator(0.2,0,0,1)），选中/未选中色由 `ArgbEvaluator` 插值，图标 Drawable 缓存后改色重绘。系统关动画时立即到终态。
- **Tab 切换**统一走 `selectTab(target)`：保存离开页滚动 → 更新 nav → 换 Header → 转场内容 → 胶囊动画 → 恢复目标页滚动。数据/主题/返回等普通刷新走 `renderContent(false,0)`，无 Tab 入场动画；转场有 `transitionGeneration` 防止旧回调删新页。
- **UI-3 内容转场**：新页 alpha 0.7→1、translationX ±8dp→0、220ms；旧页 alpha 1→0、180ms，只作用于 contentHost 内的页面，Header / 底栏 / Activity 不参与。转场只在用户点底栏时播放，`render()`、数据/主题刷新、A3 每 30s 就地更新都不触发。
- **转场竞态与清理**（UI-3 收尾）：`exit`(180ms) 先结束，只把自己从活跃列表移除并删旧页，由更长的 `enter`(220ms) 负责最终归位；取消时按快照遍历 `cancel()`，四个回调（两条 update + 两条 end）都带 `generation` 守卫。被取消的入场页会先归位到「不透明 + 零位移」再作为本次离场页，离场页在交叉淡化期间标 `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`，读屏不会同时朗读两页。
- **滚动位置恢复**：`restoreScroll(tab, y, generation)` 用一次性 `OnPreDrawListener` 在**布局之后**恢复（`post()` 不保证排在首次 measure/layout 之后），并校验「转场代号 + 目标页仍是 `root` + tab 未变」，快速连点（今日→课表→账号）时上一页的延迟恢复不会落到新页上。`render()` 只在重建**同一屏**（`contentTab`/`contentCampus` 与当前一致：旋转、分屏、换主题、数据刷新）时先 `captureScroll()` 再恢复，换 tab / 换校园层级不搬运上一屏的位移。
- `UiTokens` / `MotionSpec` 是尺寸与动效的单一来源；页面左右边距仍由 `AppUi.side()` 决定。视觉圆角本轮不变（卡片 18dp、底栏 24dp），UI-4 才调整。

## 返回栈规则（`MainNavigator`）

`core/MainNavigator` 只管「当前是哪个视图」与返回顺序，**不持有 View、不依赖 Android**：

1. 校园记录视图 → 先退回校园入口列表；
2. 非今日 tab → 回到今日 tab；
3. 今日 tab → 关闭页面。

恢复的 tab / campusView 值做 clamp 防越界；离开校园 tab 重置其内部 scope。Activity 只持久化/恢复两个 int。返回栈顺序由 core 断言覆盖，改动必须同步补 `CoreTests`。

## 诊断与故障报告（2.5.8）

两页都在「更多」之后，入口从 `MoreActivity` 进；两者都继承 `Page`，返回手势与主题跟随全局，不单独实现。

| 页面 | 入口 | 内容 |
| :-- | :-- | :-- |
| `DiagActivity` | 更多 → 诊断日志 | 开关、记录状态与大小、等级/模块筛选、可展开的记录、复制 / 导出 / 清空 |
| `DiagReportActivity` | 诊断日志 → 故障报告 | 模块选择、问题描述、将包含什么、导出 / 复制 |

页面结构固定为：返回与「故障报告」在顶部一行，标题下是开关卡，再往下两行筛选胶囊（等级 + 模块），
中间是分页列表（每次 40 条，顶部「显示更早的」继续加载），底部是「复制日志 / 导出文件 / 清空」。
没有底部导航、没有悬浮按钮——这里是排查工具，不是第四个 tab。

交互约定：

- 开关默认关；打开后写一行 `DIAG log.start`，关闭前写 `DIAG log.stop` 再落盘。
- 筛选不改变数据，只改变视图；复制与导出都遵循当前筛选，两者文案一致。
- 导出走 `ACTION_CREATE_DOCUMENT`（系统文件选择器），UTF-8，不申请存储权限；取消不报错。
- 清空必须二次确认；清空后队列与文件同时清掉，页面立即显示空状态。
- 记录行默认三行（标题），点开显示原始记录、字段与堆栈；堆栈可长按选择。

视觉与运行时限制：列表用 `LinearLayout` + 分页，不引入 AndroidX/RecyclerView；深色主题下等级颜色
（错误/警告）单独取值以保证对比度。

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
- **周次切换动画**：两类都表现为「同一块板横向移动」，不是淡入。
  - 相邻一周（‹ › 的 `shiftWeek`、左右滑动）：`Pager.animateOutThen` → 旧板面滑出一屏、相邻页滑进来，位移固定 ±1 屏。
  - **跨多周**（回本周 `goCurrentWeek`、选周次 `selectWeek`）：Pager 只持有相邻三周，没有相邻页可滑，因此走 `jumpWeek` —— **先落周次**（`week=next` + 周次标签）→ `renderContent(true, ±1, true)` 的 slide 分支：整屏位移一屏（`pageWidth()`），旧页 `-offset*t` 与新页 `offset*(1-t)` 同一时长同向同行，两页都不透明。落地帧就是本周，**不会**出现「滑空一屏再突然变成本周」，也不会有「先滑完再突变」。
- 滑页时长统一取 `MotionSpec.WEEK_SLIDE_DURATION`（190ms + `DecelerateInterpolator`），Pager 松手归位与跨周跳转共用同一个 token，避免两处时长/曲线不一致像两块板在打架。Tab 转场的 alpha 0.7→1 / ±8dp / 220ms 进 180ms 出保持不变，slide 分支不淡入。
- 所有改变今日课程集合的操作（增删改 / 导入 / 周次 / 主题）走 `render()` 全量重建并刷新引用。
- `WeekBoard` 按屏幕计算 gutter / padding / 字号（`BoardFit`），窄屏不挤成两字一行。

## 主题

`Theme` 一处下发颜色：明暗模式（自动/浅/深）、主题色、课表配色 18 槽、文字色、圆角、不透明度、网格线（默认关）、悬浮底栏。深色是独立灰阶，不是浅色反相。`AppearanceActivity` 改动即时预览。课表圆角默认 `Theme.DEFAULT_RADIUS = 20dp`，与滑杆上限（`MAX_RADIUS`）同值；改过的读者仍按自己保存的值走，升级不会被覆盖。注意圆角预览绘制需按 `dp()` 换算，与 `WeekBoard` 实际格子一致。

## 桌面组件

`ScheduleWidget` / `ScheduleWidgetMedium` / `ScheduleWidgetWide` 三种尺寸，`AppWidgetProvider` 实现，课表变动后刷新。

## 更新设置（`MoreActivity`）

「更多」页卡片里紧跟「更新日志」的两行，不新开页面、不新开卡片、不动页面其余结构。两项彼此独立：**更新渠道**决定从哪个主机读清单，**接收预览版更新**决定读正式版还是预览版。

### 更新渠道

- 位置：更新日志下一行，紧挨「接收预览版更新」上方。图标 `AppUi` 的 `globe`。
- 文案：标题「更新渠道」；副标题随选择显示「WorkBuddy（默认）」或「GitHub」；右侧显示当前值。整行可点，行高 ≥ 56dp，带 `contentDescription`。
- 交互：单选弹窗，「WorkBuddy」/「GitHub」两项，「确定」才落盘，「取消」不动。选择后更新副标题、读屏文案并给短 Toast。
- 默认 WorkBuddy；`update-v1` 偏好的 `source` 键，未保存或未知值都按 WorkBuddy 处理。切换会作废正在进行的检查（generation 计数），下一手动检查按新渠道执行。

### 接收预览版更新
- 文案：标题「接收预览版更新」，副标题「提前体验新功能，可能存在不稳定情况」。
- 控件：系统 `Switch`，轨道与拇指都上实色（同「外观与主题」页），深色模式下开/关都分得清；整行可点（行高 ≥ 56dp），行与开关各自带 `contentDescription`，切换后更新读屏文案并给一条短 Toast 反馈。
- 默认值：**一律默认关闭，任何安装包都一样**（含 `X.Y.Z-beta.N` 的首次安装）。预览开关只能由读者自己打开，不按安装包类型猜默认值。
- 开启前必过一次确认框：标题「开启预览版更新？」，正文「预览版供开发者测试使用，可能不稳定，不建议开启。」，按钮「仍然开启」/「保持关闭」。
  **只有点「仍然开启」才落盘**；点「保持关闭」、点弹窗外、按返回键都把开关拨回关的位置，且不写偏好、不弹 Toast。关闭方向不再询问（那是安全方向）。
- 存储：`update-v1` 偏好里的 `beta` 键（与「稍后再说」的 `skipped` 同在一个偏好文件，清除全部本机数据时一并清掉）。
- 切换会作废正在进行的那次检查（`UpdateCheck` 的 generation 计数），因此不会出现「关了开关却弹出预览版」的结果；下一次手动「检查更新」立刻按新设置执行。
- 主题/无障碍全部复用 `AppUi` 与 `Theme`，无新增色值，也没有独立页面。

## 更新日志页（`ChangelogActivity`）

- 入口：校园 → 账号 → 更多 → 更新日志（`MoreActivity` 卡片里，位于「数据与隐私」之后）。**没有**底部 Tab，主屏结构不变。
- 数据：`AppChangelog.ENTRIES` 随包内置，新到旧；页面只取 `released()`，`current(versionName)` 决定谁挂「当前版本」。开发包（如 `2.5.8.6`）匹配不到条目，因此**不会**给任何正式版误打标记，改为在顶部说明「此版本尚未正式发布」。
- 版式：每版一张卡片（`UiTokens.CARD_RADIUS`），标题 `v版本号` 用 `CARD_TITLE` + 粗体，日期用 `BODY_SMALL` + `MUTED`，当前版本另挂一枚 `SOFT` 底的小胶囊；每条变化前是类别图标（新增 `plus` / 优化 `torch` / 修复 `check` / 其他 `info`），图标色只取 `AppUi.ACCENT` 或 `MUTED`；已知问题用 `AppUi.DANGER`。配色不写死任何色值。
- 交互：只做展开/收起。最新一版与当前安装版默认展开，历史版本收起；展开时标题由 `MUTED` 转 `INK`、箭头旋转 180°，动画只改「内容高度 + 箭头角度」，时长取 `MotionSpec.LIST_TOGGLE_DURATION`(220ms) + `MotionSpec.settle()`，系统关动画时直接给终态（展开回 `WRAP_CONTENT`）。
- 连点：每张卡片自带 `generation` 与 `running`，新点击先 `cancel()` 上一条动画并从**当前高度**接着走，过期回调直接被 `generation` 拦下，不会两条动画抢同一个 `LayoutParams.height`。
- 返回走 `Page` 既有机制，未改 `Page` / `CardLayer`。

## 自动化守卫的边界

`scripts/test_ui_contract.py` 当前有 **314 个静态锚点**：底部导航三项、WeekBoard onMeasure、滚动保存恢复、CardLayer 手势与快照、S4 策略、A3 就地更新分支、三个 Screen 存在、学业情况入口与卡片单一挂载点、教务卡 tile 顺序，以及 UI-1 的 token 常量、UI-2 的 shell/底栏不重建与转场防旧回调守卫、底栏外观修正的房屋图标/固定尺寸图标图标槽/胶囊只包图标的 64×32dp 守卫、UI-3 的转场参数（alpha 0.7→1 / ±8dp / 220ms 进 180ms 出）、快速连点竞态守卫（快照 cancel、各动画自带 end 清理、4 处 generation 守卫），以及 UI-3 收尾的滚动恢复守卫（恢复带 generation、同屏重建才搬运滚动、`OnPreDrawListener` 布局后恢复、stale 恢复不得落到别的 tab、离场页移出无障碍树）与跨周跳转守卫（回本周走 jumpWeek、先落周次再整页滑入、保留滚动位置；两处共用 WEEK_SLIDE_DURATION；Pager 滑出位移只取一屏），「数据与隐私 / 使用公告」守卫（文案只在 `LegalText`、待填项必须存在、教务密码与 AES 表述、不得声称充值、manifest 权限逐项披露、公告唯一入口在 `PrivacyActivity` 页末（`MoreActivity` 不得再挂一条）、页面与弹窗同源、`NoticeDialog.VERSION = 3`、两页不得加载网页或发请求），、双渠道更新守卫（`beta.json` 独立清单、`UpdateChannel.pick` 选版、开关落盘与 generation 作废、预览渠道六道校验原样保留、manifest 版名格式与 versionCode 高于线上、`beta.json` 字段与版名格式）以及更新日志守卫（版本号唯一、版本代码严格递减、日期合法、最新条目必须等于 `delivery/update/update.json`、不得出现尚未发布的版本、每版都要有发布记录可查、页面只取 `released()`、展开动画取 `MotionSpec`、连点 cancel 与 generation 守卫、入口与 exported=false 注册、离线可读），学业情况只让叶子课组可点、计划内与计划外两个取课端点都在只读白名单内且请求必须按节点 `jdkcsx` 选端点，以及学生评价（列表走 `EduClient.evaluations()` 并**按教务分页逐页取全**、头部三格是 未评/已评/暂存、有 全部/未评/暂存/已评 四个筛选 chip、卡片折叠用 `MotionSpec.LIST_TOGGLE_DURATION` 且关动画时直接落终态、再次点击先 cancel 前一条动画、打开答题页要带上全部六个 id、返回后必须重读；答题页**不得出现 WebView 或 loadUrl**、不得出现写死的「优秀/良好/及格」、必须 `EduEvaluationForm.parse()` 读学校片段、必须 `EduEvaluationForm.body()` 组参数、必须 `Theme.dialog` 二次确认并写明提交不可撤销、必填项未答完不得提交、不支持的题型必须阻止提交、成败只按学校自己的回话判断、暂存过的问卷要回填本人上次的选择、已提交的只读；核心侧 `EduEvaluationForm` 必须按 `panel-pjdx` / `tr-xspj` / `input-xspj` 与 `modelList[i].xspjList[j].childXspjList[k]` 处理，`EduEvaluation.pageCount` 必须存在））与教务卡第一行四件查询的 tile 顺序。**它是静态守卫，不验证视觉/手势/真机效果**；UI 2.0 的真机清单（快速切换、320dp、主题、滚动恢复、返回手势、关动画、今日 60s）本轮未实测，D/E 组真机清单未完成前不得发布。
