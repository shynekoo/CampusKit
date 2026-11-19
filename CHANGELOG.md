# 变更日志

本项目所有值得注意的变更记录。格式参照 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，按版本倒序。

**事实基准**（每条均以仓库代码 / Git 提交 / 实际构建为依据，不推测）：

- 线上正式版与下载站：`2.5.7`（versionCode `197`，2026-11-18，`delivery/update/update.json`）。
- 已提交并 push、尚未发版：`2.5.8.1`（versionCode `199`，Git HEAD `6406500`）。
- 已提交、尚未发版：`2.5.8.2`–`2.5.8.6`（最新 versionCode `204`），含学业情况查询与 UI-1/UI-2 第一轮。
- 已提交 HEAD：`fce95bc`（UI-1 / UI-2 / UI-3 转场竞态 + 滚动恢复 + 回本周动画）；工作区干净，**未 push、未发版**。
- 核心断言：当前工作区实跑 `python scripts/test_core.py` → **1262 项全过**（2026-11-19）；UI 守卫 **152 锚点全过**。

更早版本的发布说明见 [`delivery/release-*.md`](delivery/)。

---

## [未发布 · UI 2.0 第一轮] — 工作区未提交（UI-1 + UI-2，未发版）

> 已提交（commit `7a8083d` / `cb3f859`），未发版。本轮只做 UI-1（设计/动效 token）与 UI-2（常驻主界面 + 动画底栏），不改视觉圆角数值、不动网络/账号/支付/水控/升级、不碰课表 JSON 与 WeekBoard。UI-3/4/5 未实施。核心 1262 断言、UI 守卫 **120 锚点**（原 60 + UI-1 的 20 + UI-2 的 40）全绿，普通版 APK 已构建并 v2/v3 验签（水控与诊断均为 false，控制路由 occurrence 全 0）。

### 新增
- **`UiTokens.java`**：尺寸/字号单一来源，逻辑 dp/sp 数值，第一阶段取现有基线（卡片 18dp、底栏 24dp、胶囊 18dp、间距 4–24dp、触控 48dp、导航图标 22dp/文字 12sp、标题 28sp、卡片标题 16sp）。页面左右边距仍走 `AppUi.side()`，未硬编码。
- **`MotionSpec.java`**：时长/位移单一来源（按压 140ms、胶囊 260ms、Tab 进 220ms/出 180ms、位移 8dp、PathInterpolator(0.2,0,0,1)），并提供 `shouldAnimate(Context)`：Android 8+ 用 `ValueAnimator.areAnimatorsEnabled()`，低版本读系统动画倍率；关闭动画时所有组件立即到终态。
- **`MainShell.java`**：一次构建的竖向 shell（headerHost / contentHost 0+weight=1 / navHolder），系统栏与挖孔 Insets 只在此落实一次；底部 Insets 不重复。课表 Pager 在 contentHost 中仍取到完整可用高度。
- **`AnimatedNavBar.java`**：单个可移动胶囊 + 一行三等分 Tab，三项只在构造时创建一次。胶囊仅用 `translationX` 移动；选中/未选中色用 `ArgbEvaluator` 插值；缓存的 `AppUi.Icon` Drawable 改色后 `invalidateSelf()`，不逐帧新建；`contentDescription` 标注选中态。

### 变更
- `TimetableActivity` 的 `render()` 拆为 `ensureShell` / `renderHeader` / `buildScreen` / `renderContent` / `selectTab`。`setContentView(shell)` 只在 `ensureShell` 调一次；Tab 切换不再重建底栏或整个 Activity。普通数据/主题/返回刷新走 `renderContent(false, 0)`，不触发 Tab 入场动画。
- 「今天的课 · 查看课表」改为先定周次再走统一 `selectTab` 流程；`onNewIntent(showToday)` 仍走非动画 `render()`。
- Tab 切换记录/恢复各 Tab 滚动位置（课表用 Pager `verticalPosition()/restoreVerticalPosition()`）；内容转场带 `transitionGeneration` 防旧回调删新页。`onDestroy` 取消动画并清引用。
- `MainNavigator` 返回顺序、`Page`/`CardLayer`/`WeekBoard` 均未改动。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 120 全绿；构建 APK v2/v3 验签通过，普通版无诊断、无水控。
- **待真机验收**：无连接设备/模拟器，底栏快速点击、320dp 窄屏、深色/自定义主题、课表高度、滚动恢复、返回手势完成与取消、系统关动画、今日页 60s 无闪烁均未实测。

---

## [未发布 · 底栏外观修正] — 已提交 `20c2ffa`，未发版（UI-2 收尾）

> 真机反馈底栏视觉比例失衡：选中胶囊铺满整格、底栏偏高、图标不可见。本轮只改底栏外观，不动 MainNavigator 状态管理、Tab 转场、课表与返回手势。核心 1262 断言、UI 守卫 131 锚点全绿，普通版 APK 已构建并 v2/v3 验签。

### 修复
- **图标 0×0 根因**：`AnimatedNavBar` 的图标 `ImageView` 原用 `wrap_content`，而 `AppUi.Icon` 没有固有尺寸，被量成 0×0——真机只显示文字。改为固定 `NAV_ICON`(22dp) 尺寸，图标正常显示。
- **选中胶囊只包图标**：从「铺满整格（slotWidth−8 × 行高−8）」收敛到 64×32dp、圆角 16dp，定位在图标槽背后、不压文字；三个 Tab 各有等宽图标槽，图标/胶囊/文字始终对齐。今日入口改用房屋图标 `home`（在现有 `AppUi.Icon` 家族内新增画法，未引入图标库）。
- **底栏尺寸**：内容高度固定 72dp（落在 68–76dp 目标区间），外框圆角 24→28dp，`navHolder` 水平/底部留白 8→4dp；宽度、左右边距仍走 `AppUi.side()` 自适应，系统栏 Insets 仍只在 MainShell 处理一次。
- 选中/未选中色仍为 `AppUi.ACCENT` / `AppUi.MUTED`，胶囊为 `AppUi.SOFT`，随浅色/深色/自定义主题刷新；胶囊 260ms `translationX` 平滑移动、快速连点从当前位置续接、首次布局/旋转/关动画直接到终态——原有动画机制不变。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 131 全绿；APK v2/v3 验签通过，普通版无诊断、无水控。
- **待真机验收**：胶囊位置/图标显示、320dp 窄屏、大字体、深色/自定义主题、快速连点无残影错位。

---

## [未发布 · UI-3 页面切换动画] — 未发版

> 底栏视觉修复后继续 UI-3。淡入淡出 + 8dp 水平位移的 Tab 内容转场在 UI-2 阶段已落地（仅作用于 contentHost，Header/底栏/Activity 不参与），本轮修复快速连点时的动画竞态。核心 1262 断言、UI 守卫 141 锚点全绿，普通版 APK 已构建并 v2/v3 验签。

### 修复
- **快速连点竞态**：`exit`(180ms) 先于 `enter`(220ms) 结束，旧实现在 exit 的 end 回调里 `contentAnimators.clear()`，导致仍在运行的 enter 脱离追踪——180–220ms 窗口内再切 Tab 时 `cancel()` 遍历不到它，旧 enter 继续写已替换页面的 alpha/translationX，造成残影/错位。改为各动画结束时只从列表移除自己，由时长更长的 enter 负责最终归位与旧页移除。
- **过期帧守卫**：enter/exit 的 update listener 与 end 回调全部加 `generation != transitionGeneration` 守卫，过期帧不再写入任何 View。
- **快照 cancel**：`for (Animator : new ArrayList<>(contentAnimators)) cancel()`，因为 end 回调会修改活跃列表，直接遍历会 ConcurrentModificationException。

### 未改（关键约束）
- 转场参数保持：新页 alpha 0.7→1、translationX ±8dp→0、220ms；旧页 alpha 1→0、180ms；系统插值器、无缩放。
- 只在用户 `selectTab` 时播放；`render()`、课程/主题刷新、A3 每 30s 局部更新均走 `renderContent(false, 0)` 不触发转场。
- `WeekBoard.Pager` 手势/周次/滚动、`Page`/`CardLayer` 返回动画与快照隔离、`MainNavigator` 状态管理均未改动。系统关动画时 `!motion` 直接切换到终态。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 141 全绿；APK v2/v3 验签通过，普通版无诊断、无水控、控制路由 occurrence 全 0。
- **待真机验收**：快速连点无残影/错位、页面切换后滚动位置、课表周次滑动与自适应高度、返回手势完成与取消、系统关动画、今日页 60s 无闪烁。

---

## [未发布 · UI-3 收尾：滚动恢复与转场残留] — 工作区未提交，未发版

> 在 `ffaf933`（转场竞态修复）之上继续 UI-3，只查页面切换动画、快速连点竞态、动画取消清理、滚动位置恢复、与课表横滑/返回手势的关系。**不改转场参数、不改视觉样式、不动 MainNavigator / Page / CardLayer**。核心 1262 断言、UI 守卫 152 锚点（原 141 + 本轮 11）全绿，普通版 APK 已构建并 v2/v3 验签。

### 修复
- **快速连点把上一页的滚动量写到新页**：`restoreScroll` 原来只读字段 `root`，而 `root` 会被后一次切换改指向新页——今日→课表→账号连点时，课表挂起的恢复会落到账号页并把它滚到课表的位置。现在恢复携带转场代号，并在执行时同时校验 `generation`、目标页仍是 `root`、tab 未变，任一不匹配即放弃。
- **滚动恢复排在了布局之前**：原实现用 `root.post(...)`，只保证「下一条消息」，不保证在首次 measure/layout 之后；未测量的 ScrollView 会把 `scrollTo` 抹掉，首次切回该 Tab 可能回到顶部。改为一次性 `OnPreDrawListener`（布局后、首帧前）恢复并自摘除监听。
- **普通重建把滚动位置重置**：`render()` 的非动画路径原来直接按 `tabScroll[nav.tab()]` 恢复，而该值只在 `selectTab` 时更新——旋转、分屏（`onConfigurationChanged` → `render()`）、换主题、数据刷新都会跳回上次切 Tab 的位置或顶部。现在 `render()` 在重建**同一屏**（`contentTab`/`contentCampus` 与当前一致）时先 `captureScroll()` 再恢复；换 Tab、校园记录↔入口列表这类真正换页的情况不搬运位移。
- **被打断的入场页带着 8dp 位移离场**：快速连点时上一页的 enter 被 `cancel()` 只停在当前数值，`exit` 只动 alpha，位移会留到淡出全程。现在被保留为离场页的残留先归位到「alpha 1 / translationX 0」。
- **转场期间两页同时可读**：离场页在交叉淡化期间标 `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`，读屏不会把正在消失的旧内容当成仍在页面上朗读。
- **「回本周」/ 选周次的换页时机**：原先直接 `applyWeek(delta)`，跨几周闪一下换页；第一次改成「先滑出一屏、落地时重建」后仍然是「滑完才变成本周」——动画结束那一帧内容突变。现在改走 `jumpWeek`：**先提交周次**、更新周次标签，再播 `renderContent(true, ±1)` 内容转场，旧板面淡出、本周板面在动画里淡入到位，动画结束时画面已经是本周；周次弹窗 `selectWeek` 同样走这条路径。`Pager.animateOutThen` 的滑出位移同时收敛为 ±1 屏（原先按 `-delta*width`，跨 5 周会把板面甩出 5 屏），避免以后有人拿它做跨周跳转。

### 检查结论（未改动）
- **与课表左右滑动**：转场只改 contentHost 内 `next` 的 `translationX`，`WeekBoard.Pager` 改的是自己三个子页的 `translationX`，两者作用在不同 View 上、互不拦截触摸；`Pager.onInterceptTouchEvent` 在自身 `settle` 期间返回 true，不参与内容转场。切走时 `onDetachedFromWindow` 会 `cancel` 掉周次动画，不会在已分离的页上回调 `applyWeek`。
- **与返回手势**：转场与 `Page` 的返回动画不同时发生——返回走 `nav.consumeBack()` → `render()`（非动画），会先取消内容转场；`refreshBackMode()` 在 `selectTab` 中已按新 tab 重算，系统预测式/本地缩放的选择不受转场影响。
- 转场参数、`MotionSpec`/`UiTokens` 数值、`MainNavigator`、`Page`、`CardLayer` 均未改；`WeekBoard` 只动了 `Pager.animateOutThen` 的滑出位移（±1 屏），手势判定、页高测量、周次提交逻辑未改；系统关动画时仍走 `!motion` 直接到终态。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **152** 全绿；普通版 APK（`delivery/科信小助手-2.5.8.6.apk`，5 392 250 B，SHA-256 `8c767bce752b9beff3fd7a42b096845571e2e512af3e53c8568685229c9504d0`）构建通过、v2/v3 验签 true，构建脚本的三开关/控制路由断言全过。
- **待真机验收**：快速连点（三 Tab 连续点）无残影错位、切走再切回每页滚动位置、旋转与分屏后滚动位置、换主题后不跳顶、课表横滑与自适应高度、**回本周播内容转场且动画内即为本周**、返回手势完成与取消、系统关动画、今日 60s、320dp 窄屏 —— 均未实测。

---

## [未发布 · 2.5.8.6] — 工作区未提交（versionCode 204）

> 已提交、未发版。学业情况页补上每个课组的课程明细，并改为点击展开；核心 1262 断言、UI 守卫（当时 60 锚点）全过，普通版 APK 已构建并验签。

### 新增
- **课组课程明细**：学校页面只内联学分树，每个课组的课程在 `POST /xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html` 这个只读查询里（学校自己展开箭头时调的就是它）。`EduClient.academicCourses()` 发起该查询，参数回显页面的隐藏字段 `xh_id`/`cjlrxn`/`cjlrxq` 等——这些字段在读到学分树时顺带缓存，展开一个课组只花一次小 POST，不再下整页 1.7 MB。
- **点击展开**：叶子课组行可点击，点开才取课组课程，取过的按课组 id 缓存，再点不重复请求；分类卡片头部可折叠/展开整块课组。加载中、空结果、失败重试三种状态都有明确文案。
- **课程行**：课程名 + 成绩（无成绩时显示「在读 / 未修 / 未过」）+ 学分；通过的课程用正文色与强调色，未通过/未修降为次要色。展开出来的课程块左侧加一条 2dp 细线做括注，否则课程行与下一个课组行缩进接近，会读成同一个列表。
- `EduAcademic.Course` 类型化课程模型（课程名/课程号/学分/成绩/绩点/学年/学期/必修限选/修读状态），附带 `passed()` 与 `statusLabel()`；`EduAcademic.parseCourses()` 在 core 里解析该接口的扁平 JSON 数组，不引入外部 JSON 库。
- `EduAcademic.Node` 新增 `id` 字段（课组编号，课程查询以它为键）。
- 汇总卡补全统计：原先只显示计划/通过/在读/未修，现按学校给的六个数字分两行显示，补上 **未通过** 与 **计划外通过**。

### 修复
- 隐藏字段提取改为「先定位整个标签、再读 value」：页面实际写成 `<input type="hidden" name="xh_id" value="…" id="xh_id"/>`，id 在 value 之后，按 `id="…"` 前缀取值的正则一个都取不到。

### 变更
- 去掉 2.5.8.4 里把「创新实践课 / 其他课程 / 创新创业情况」合并成紧凑卡的做法：其中「其他课程」实际挂着 6 门计划外课程，合并会让它无法展开。现在每个分类各自成卡，卡片数量与分类数一致。

### 验证
- `test_core` **1262**（新增 23 条课程解析断言，含裸数字、`null`、转义引号、全角括号、无名行丢弃、超长拒绝）/ `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **60**（新增 8 条：课程解析必须在 core、只读路径白名单显式放行、只有叶子课组可点、取回结果要缓存、汇总必须显示未通过）全绿。
- 真实账号端到端：16 个课组、94 门课程，经 Java `parseCourses` 后学分合计、通过/在读/未修分布与学校页面逐项一致，无空名或 `null` 泄漏；9 个隐藏字段全部可读（`cjzt` 页面本就没有，传空有效）。普通版 APK v2/v3 验签通过，三开关全 false、控制路由全 0。

---

## [未发布 · 2.5.8.5] — 已被 2.5.8.6 取代（versionCode 203）

> 尚未 commit、未发版。学业情况页按学校层级重排版；核心 1239 断言、UI 守卫 52 锚点全过，普通版 APK 已构建并验签。

### 变更
- **学业情况页按真实层级排版**：学校页面是「主修 → 通识教育/专业教育/第二课堂 → 课组 → 课程方向」的五层树，此前把 37 个节点平铺成 37 张同级卡片，看不出层级。现在二级分类各自成卡（分类名 + 已获/要求学分 + 进度条），三级课组作为加粗行、四级及以下作为缩进细节行（每深一层缩进 14dp，字号 13→12，学分 12→11），顶部汇总卡标题带上根分类名（如「主修 · 课程修读情况」）。页面未提供层级时自动回退为原平铺卡片，不会空白。
- 既无学分也无子分类的类别（创新实践课 / 其他课程 / 创新创业情况）合并到一张紧凑卡，避免末尾连续三张空卡。
- 学分进度条按 0.01 精度换算（原先取整会把 24.5 学分显示成 25）；要求学分为 0 时不再画一条永远空着的进度条。

### 修复
- `EduAcademic` 原正则要求 `id`→`yxxf`→`yqzdxf`→`sftg` 严格按序且都存在，真实页面存在缺 `yxxf` 的写法会被漏掉；改为先取标签再逐个读属性，顺序与缺省都不再影响结果。

### 新增
- `EduAcademic.Node` 新增 `depth` 字段、`Report.layered` 标记：深度来自页面自身的 jQuery 建树关系（`<li id='li…'>` 被 `.appendTo($("#ul父ID"))`），不是猜的；父链断裂的节点降为根节点，保证不漏分类。

### 验证
- `test_core` **1239** / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **52** 全绿；真实账号端到端跑 Java 解析器：`layered=true, 37 节点, 最深 5 层, GPA 2.9875, 计划 118/通过 54/未修 50/在读 14`。普通版 APK v2/v3 验签通过，三开关全 false、控制路由全 0。

---

## [未发布 · 2.5.8.4] — 已被 2.5.8.5 取代（versionCode 202）

> 尚未 commit、未发版。修复包，供真机覆盖安装验证；UI 守卫 46 锚点全过，普通版 APK 已构建并验签。

### 修复
- **学业情况页闪退**：`card()` 工厂在内部就把卡片挂进 `results`，`headCard()`/`show()` 又各挂一次，同一个 View 被 addView 两次触发 `IllegalStateException: The specified child already has a parent`，且发生在主线程回调里导致闪退、错误提示来不及显示。改为 `card()` 只创建、由唯一入口 `addCard()` 挂载并设置间距——与成绩 / 空教室页「工厂只创建、调用方决定放置」的一致做法。
- 说明文字 `screen.note(...)` 原先每次 `show()` 都追加一条，重载会堆叠；改为 `onCreate` 中只设置一次。

### 变更
- 教务卡两行重排：第一行 = 成绩 / 空教室 / 教务系统；第二行 = 学业情况 / 河工程官网 / 科信官网（2.5.8.3 曾把学业情况放第一行、教务系统放第二行，本次按反馈对调）。

### 守卫
- UI 契约新增锚点：`card()` 不得自行 `results.addView`（防双重挂载回归）、`screen.note()` 仅一处、教务卡六个 tile 顺序（46 锚点全过）。

### 验证
- `test_core` 1228 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 46 全绿；普通版 APK v2/v3 验签通过，三开关全 false、控制路由全 0。数据层 `EduAcademic`/`EduClient` 本次未改动，2.5.8.2 已用真实账号端到端验证过。

---

## [未发布 · 2.5.8.3] — 已被 2.5.8.4 取代

> 历史 WIP 记录（未 commit、未发版）。2.5.8.3 的闪退与卡片顺序问题已在 2.5.8.4 修复；下列条目仅作演进说明。

### 新增
- **学业情况查询**：教务卡新增「学业情况 · 学分·绩点」入口，直连学校「学生学业情况查询」页，展示绩点与去重后的学分树（按学分分组 + 进度条）。解析逻辑放在平台无关的 `core` 模块（`EduAcademic`），Activity 只负责排版；请求走与成绩相同的登录会话，页路径与 Referer 白名单同步收窄。
- 对应核心断言与 UI 结构守卫已补充（当前全仓 1228 项断言）。

### 变更
- 主题色彩卡文案「品牌蓝」统一改为「默认」，长按恢复提示同步调整。
- 教务卡入口重排：「学业情况」并入主功能行，「教务系统」移入网站行；「河工程官网 / 科信官网」保持原顺序。

### 修复
- 外观页课表预览的圆角绘制把 `draftRadius` 当像素用，导致预览圆角只有实际课表的约 1/3；改为按 `dp()` 换算，与 `WeekBoard` 真实格子一致。
- 外观页删去两段已经与当前行为不符的说明文字（返回手势说明、预览块说明），避免误导。

### 废弃 / 移除
- 无。

### 文档与 CI（本次变更，不改变 APK 行为）
- 新增 `AGENTS.md`：开发任务九步流程、Git Diff 文档同步检查、明确「无需更新文档」声明方式。
- 新增 `scripts/check_doc_sync.py`：按改动路径提示可能受影响的文档，默认不阻断构建。
- `docs/` 新增八类文档：架构、模块、构建发布、安全约束、UI 导航、数据接口、已知问题、Agent 交接；保留原有截图与审计文档。
- 新增 `.github/workflows/docs-ci.yml`：四个测试入口 + 关键文档存在性检查（不构建、不签名、不发布）。
- 修正 README 两处旧断言数（1073 / 1034）为实测 1228。
- 未改动任何业务代码、构建逻辑、签名、支付、用水控制或版本升级。

---

## [2.5.8.1] - 2026-11-19（已提交并 push，未发版；versionCode 199）

> Git 提交 `890e9d8`…`6406500`。`delivery/update/update.json` 仍为 `2.5.7`，下载站与 GitHub Release 未更新；真机验收清单尚未勾选完成，**不得发布**。

### 新增
- **使用码**：8 位码，可不扫码直接开启设备；扫码页自动识别设备与订单状态，「使用码」按钮与「重新扫描」并列。
- **UI 行为静态守卫**：`test_ui_contract.py` 的锚点从 26 增至 33，防止重构时悄悄改变页面结构。
- **真机验收清单**：`docs/real-device-acceptance.md`，覆盖导航 / 手势、局部刷新、支付与用水、安装包与发布配置等分组，全部标注待验收。

### 变更（安全整改 S1–S6）
- **S1** 应用内更新：清单必须包含完整大小与 SHA-256 且走 HTTPS 才接受；校验 APK 身份；重定向限制在 HTTPS、有界且无环，跨主机时丢弃 query。
- **S2** 诊断默认关闭，需显式开启，导出前脱敏。
- **S3** 学校 WebView 导航收紧，仅允许已批准的 HTTPS 主机与端口。
- **S4** 敏感页面不进入返回动画快照。
- **S5** 支付与用水业务安全审计（详见 `docs/security-audit-payment-water.md`）。
- **S6** 教务登录 WebView 统一走 `TrustedUrlPolicy` 准入。

### 变更（架构重构 A1–A4，行为等价）
- **A1** 主导航状态机下沉到 `core`；`TimetableActivity` 逐步拆出 `CampusScreen` / `HomeScreen` / `ScheduleScreen` 三个静态嵌套类（阶段 1–3）。
- **A2** 引入带类型 `Course` 模型，JSON 字段无损映射。
- **A3** 真局部刷新：新增 `RefreshDecision`（NONE / TODAY_DYNAMIC / FULL）与 `TodayDynamic`；同日时钟 tick 就地更新今日摘要与每行状态，不再整页重建；跨天 / 切 tab 才重建，数据不一致自动回退 FULL，保留滚动位置与周次切换。
- **A4** 会话检查去抖、缓存、网络错误与登出分离。

### 修复
- 时钟触发的冗余全量重绘：仅必要时重建页面（A3）。
- 会话并发检查重复触发与错误串扰（A4）。

### 废弃 / 移除
- 无。

### 验证（本环境实际执行）
- 核心断言 1196 → 1209 →（本轮工作区）1228 项全过。
- `test_core` / `test_build_config`(5) / `test_webview_policy`(5) / `test_ui_contract`(33) 全绿。
- 三配置构建开关实测：普通 `WATER=false DIAG=false`、用水 `WATER=true DIAG=false`、诊断 `WATER=false DIAG=true`；均通过断言与 v2/v3 签名。只读版 DEX 控制路由 occurrence 全为 0。
- **真机验收未完成**，详见 `docs/real-device-acceptance.md` 与 `docs/rectification-acceptance.md`。

---

## [2.5.7] - 2026-11-18（线上正式版，versionCode 197）

- 新增深色主题与返回手势动画。
- 应用与课表主题色支持自定义。
- 修复课表显示异常：课名字号与行高按屏幕自动适配，窄屏不再挤成两字一行，整块高度跟随页面剩余空间。
- 安装包 5,359,482 字节，SHA-256 `33851ee1190e98b1655489bd1c7989119fc6f16cfc4fd7883dc3beae6b5728bc`。

## [2.5.6.11] - 2026-11-18（versionCode 见对应 tag）

- 账号页新增「外观与主题」，分应用主题与课表外观两页。
- 应用主题：明暗模式（自动 / 浅色 / 深色）、主题色彩（默认 / 自定义）、悬浮底栏样式、返回手势动画。
- 课表外观：课程配色（18 个色槽逐个可调）、课程文字颜色、格子圆角、格子不透明度、网格线开关；改动即时预览。
- 深色为独立挑选的灰阶，正文、卡片、对话框、状态栏同步切换。

## [2.5.6] - 2026-11-17（versionCode 177）

- **修复**慧生活入口丢失：2.5.5 误用只读构建，用水控制未编进包；本版重新带上，支付宝免密登录不变。
- 调课多段周次（`2-6周(双),11-15周`）不再被当作坏行跳过。
- 未登录自动跳对应登录页，登录完自动回原页并续跑被打断的查询。
- 校园页新增「教务系统」入口；「数据与隐私」移入「更多」。

## [2.5.3] - 2026-11-17

- 账号页新增「意见反馈」（邮箱 / QQ 群，点一下复制）与「更多」（GitHub 仓库、开源许可证）。
- 项目按 MIT 许可证开源。
- 慧生活798 登录只保留支付宝授权，去掉手机号验证码登录。

## [2.5.0] - 2026-11-16

- 开启用水固定走支付宝免密，去掉支付方式选择；设备未提供该方式时提前说明。
- 请求层按 WaterWidget 实测修正：版本头用版本名字符串、开启参数精简、吸收业务码表。
