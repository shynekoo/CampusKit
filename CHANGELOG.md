# 变更日志

本项目所有值得注意的变更记录。格式参照 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，按版本倒序。

**事实基准**（每条均以仓库代码 / Git 提交 / 实际构建为依据，不推测）：

- 当前正式版：`2.5.9`（versionCode `210`，2026-11-20，修复扫码跳转 + 四段版本号检查不到更新），见下条。
- 上一版正式版：`2.5.8`（versionCode `208`，2026-11-19）；更早为 `2.5.7`（197）。
- `2.5.8.1`（versionCode `209`）曾于 2026-11-20 短暂发布，但四段版名使旧客户端的更新清单解析失败、检查不到更新，已被 2.5.9 取代。
- 另有历史开发包 `2.5.8.1`（versionCode `199`，Git HEAD `6406500`），与上述 209 包**不是**同一产物，未发版。
- 已提交、尚未发版：`2.5.8.2`–`2.5.8.6`（最新 versionCode `204`），含学业情况查询与 UI-1/UI-2 第一轮。
- 已提交并 push 的主线：`16cec29`（UI-1 / UI-2 / UI-3 转场竞态 + 滚动恢复 + 回本周整屏滑页）。
- 已提交到主线、尚未发版：数据与隐私 / 使用公告改版 + 移除未使用的 Wi-Fi 权限；应用内更新日志；使用公告入口去重 + 课表圆角默认 20dp（versionCode `205`）；学业情况少课修复（计划内 / 计划外课组按学校 `jdkcsx` 分流取课端点）。
- 已构建、尚未发版：`2.5.8.8`（versionCode `206`，2026-11-19），在合并后的主线上重建。
- **线上正式版（2026-11-19 发布）**：`2.5.8`（versionCode `208`），把 2.5.8.1–2.5.8.9 合并成一个正式版本号；
  产物 `delivery/科信小助手-2.5.8.apk`，发布公告见 [`delivery/release-2.5.8.md`](delivery/release-2.5.8.md)。
- 核心断言：`python scripts/test_core.py` → **1333 项全过**；UI 守卫 **240 锚点**；诊断隐私守卫 **634 项**（2026-11-20）。
- 2.5.8 的 tag / Release / 下载站随本次发布同步；早期 2.5.8.x 开发迭代仍**未**各自建 tag。

更早版本的发布说明见 [`delivery/release-*.md`](delivery/)。

---

## [2.5.9] - 2026-11-20（versionCode 210，已发布）

> 修复趣智校园扫码无法跳转使用的bug，如果给您带来的不好的体验，非常抱歉。
> 同时修复四段版本号导致检查不到更新的问题。
> 发布公告见 [`delivery/release-2.5.9.md`](delivery/release-2.5.9.md)。

### 修复

- **趣智校园扫码后不跳转支付宝**：2.5.7 发版（commit `2575dd8`）未传 `--field-water`，产出只读构建，
  `BuildFlags.WATER_CONTROL=false`，`ScanActivity.showCode()` 的支付宝跳转分支在编译期被移除；2.5.8 延续。
  本版重建为用水版，扫码后恢复跳转，与 2.5.6 及之前一致。使用码为同期独立功能，与本问题无关。
- dex 水控类命中由 1 / 1 / 0 恢复为 `WaterControl` 4、`WaterActivity` 100、`LifeWaterProtocol` 3。
- manifest 版本真源同步为 210 / 2.5.9；`FIELD_VERSION_NAME` 与发布逻辑未改，构建时显式传版本号。
- **检查不到更新**：`UpdateManifest.VERSION` 正则原只允许 2–3 段，四段版名（2.5.8.1 / 2.5.6.11）被判无效而静默跳过。
  正则放宽到 `{1,3}` 段并补 4 条断言；本版清单用三段版名 2.5.9，旧客户端也能解析并提示升级。

### 测试

- `test_core` 1333 / `test_build_config` 7 / `test_diag_privacy` 634 / `test_diag_files` 6650 /
  `test_webview_policy` 5 / `test_ui_contract` 240 全绿；APK v2 / v3 验签 true。
- 真机扫码跳转由发布者确认可用；真实出水/关水/账单未对生产服务测试。

## [2.5.8] - 2026-11-19（versionCode 208，已发布）

> 自 2.5.7（197）之后第一次正式发版，把 2.5.8.1–2.5.8.9 这一串开发迭代合并成一个版本号。
> 发布公告见 [`delivery/release-2.5.8.md`](delivery/release-2.5.8.md)，
> 线上清单 `delivery/update/update.json` 与本版同步更新，App 内更新日志已登记 2.5.8。

### 本版包含（2.5.7 之后累计，按用户能感知的顺序）

- **界面**：常驻主界面 + 会移动的底栏胶囊；Tab 切换的淡入淡出与位移；切页后回到原来的滚动位置；
  跨周跳转改为整屏滑页（落地帧就是目标周）；课表圆角默认 20dp。
- **学业情况**：每个课组可展开看课程明细（含成绩、学分、修读状态），汇总补上未通过与计划外通过；
  修复「右侧有学分、点开却说没有课程记录」（计划外课组走错端点）与父行误展开。
- **更新日志**：更多 → 更新日志，按版本卡片展示新增 / 优化 / 修复，离线可读。
- **使用公告 / 数据与隐私**：两份文本按实际行为重写（教务密码加密存储、不提供充值、四个网络端点），
  移除从未使用的 `ACCESS_WIFI_STATE`，权限由 5 项降到 4 项。
- **诊断日志**：正式版也带诊断页（默认关）；结构化事件、失败原因分类、崩溃记录、导出与故障报告。
  详见下一节。
- **安全**：2.5.8.1 的整改（见下方对应段落）。

### 运行日志与故障诊断重构

> 目标不是「把日志显示出来」，而是让「课表加载失败」能被定位到登录、网络、解析还是渲染。
> 旧的 `Diag` 是自由拼接的一句话，只能说「登录没成功」；新的是固定格式的结构化事件，能说
> 「登录成功 → 请求超时 2011ms → 加载失败：NETWORK_TIMEOUT」。

### 新增
- **结构化事件**：`core/DiagEvent` 固定行格式（`日期 时间.毫秒 等级 [模块] 事件代码 trace=… thread=… 字段=值`），
  `core/DiagCatalog` 提供事件与原因的中文文案；`Diag.event(level, module, code, key, value…)` 取代自由字符串。
- **字段白名单**（`core/DiagRedact`）：只有白名单键能进文件，未知键在写入时丢弃；没有 `message` / `text` / `body`
  一类自由文本字段。异常只留类型与安全堆栈（`类名.方法名(文件:行号)`，≤12 帧，不含异常消息）。
- **四级日志**：`INFO` / `WARN` / `ERROR` / `CRASH`；`DEBUG` / `VERBOSE` 不进文件。
- **失败原因分类**：`NETWORK_TIMEOUT`、`DNS`、`TLS`、`HTTP_STATUS`、`PARSE`、`SESSION_EXPIRED`、`AUTH_REJECTED`、
  `CAPTCHA`、`VERIFY` 等稳定代码，按异常因果链判断（`SocketTimeoutException` 被 `IOException` 包住也算超时）。
- **模块接入（只读，不改控制流）**：教务登录（读页 / 取公钥 / 提交 / 拿会话四阶段）、课表（候选端点、尝试次数、
  解析、写入、本机存储损坏）、网络（路由、HTTP 状态、耗时、超时）、慧生活（唤起 / 回调 / 服务端码）、支付宝
  （`status` 9000/6001）、趣智（接口拒绝 / 解析）、更新（检查 / 下载 / 大小 / 摘要 / 包身份校验）。
- **存储**：`DiagFiles` 轮转——当前 `diag.log` + `diag.1..3.log`，单文件 256 KB、总量约 1 MB、历史保留 7 天；
  写入走 512 行有界队列 + 单写线程，队列满时记 `DIAG log.lost`；清空同时清队列与全部文件。
- **崩溃捕获**：`App.onCreate` 即安装 `UncaughtExceptionHandler`；崩溃记录同步、有界落盘后把异常原样交回系统
  处理器；重启后日志页仍能读到 `CRASH` 与堆栈。
- **诊断页重做**（`DiagActivity`）：开关、记录状态与大小、等级/模块筛选、可展开的记录（字段 + 堆栈）、
  单条复制、按筛选复制、UTF-8 导出（系统文件选择器，不申请存储权限）、清空二次确认、每次 40 条分页。
- **故障报告**（`DiagReportActivity`）：版本、Android 版本、机型、所选模块、用户描述、最近 ≤200 条脱敏记录、
  最近一次异常；只在本机生成，用户主动导出。
- **正式版可用**：`BuildFlags.DIAG_BASIC` 恒为 `true`，诊断页对所有构建开放，运行期默认关；`--diagnostics`
  只额外编入细节事件（手势逐帧、页面打开、渲染结果）。SDK 原文日志回调**彻底移除**。

### 修复
- **超限遗留文件不再被整体归档**：上次退出时若 `diag.log` 已超过 256 KB（一次超长写入，或旧版本留下的文件），
  轮转只是把它改名为 `diag.1.log`，归档文件本身就超限，四文件「约 1 MB」的上限随之失效。现在启动时与单次写入
  超过上限时都只保留末尾 256 KB 再落盘，单文件上限在任何时刻都成立。
- 新增 `scripts/test_diag_files.py`：用 JDK 直接驱动 `DiagFiles`（不需要真机）验证轮转、1 MB 封顶、行号连续、
  超限裁剪、单次超长写入截尾、清空连历史一起删、7 天清理；上面这条问题就是它先跑出来的。

### 安全
- 旧 `maskCode` / `maskChain` / `maskSdk` 与 `Log.setupLogCallback` 接入全部删除：不再依靠正则擦除已记录的内容，
  而是让敏感值没有入口。`Diag.activity` 调用点若写错键名，值会被静默丢弃，不会落盘。
- 新增 `scripts/test_diag_privacy.py`：检查每个调用点的字段键、模块/代码/原因文案、已移除入口是否复活、
  导出必须 `ACTION_CREATE_DOCUMENT` + UTF-8、清单不得新增存储权限。

### 验证
- `test_core` **1329**（新增 51 项：格式往返、白名单、脱敏语料、堆栈、原因分类、文案）、`test_build_config` **7**、
  `test_diag_privacy` **634**、`test_diag_files`（5 组场景实跑）、`test_webview_policy` **5**、
  `test_ui_contract` **240**（新增 27 个诊断页锚点），全绿。
- 产物：`delivery/科信小助手-2.5.8.apk`，5 420 922 B，SHA-256 `ac40b21dfad29e1c2f6e985bec8762ec5244ba31564b497e87f7001f4410d806`；
  `aapt2 dump badging` 实测 `versionCode=208` / `versionName=2.5.8`，权限 4 项；`apksigner` v2 / v3 均为 true；
  只读包 dex 中 7 条水控路由与 2 个水控类名命中数全为 0。
- 编译期证据：正式构建的 `Page.class` 里 `back.gesture` 出现 **0** 次，`TimetableActivity.class` 里 `render.done`
  出现 **0** 次；用 `DIAGNOSTICS=true` 单独编译同一份源码则为 **2** 次——细节事件确实只在内部包里。
  正式构建的 `Diag.class` 不含 `com.alipay.sdk.interior.Log` 引用（SDK 自身的类仍随 AAR 存在，非本应用调用）。
- **未做**：真机验收 G6–G17（开关、复现、导出、轮转、崩溃重启、长日志滚动、深色/大字号），全部标注为未执行。

---

## [未发布 · 2.5.8.8] — versionCode 206，合并后主线重建

> 只有版本号比 2.5.8.7 变化，源码与合并后主线一致。存在的意义是把少课修复真正打进安装包，并在 dex 层面证明它在里面。

### 说明
- `2.5.8.7`（205）是在主线 `16cec29` 上构建的。当时少课修复只存在于并行分支 `agent2/parallel-dev`，尚未进入主线，所以那个包的 dex 里**不含** `cxJxzxjhxfyqFKcxx` / `planCourses` / `opensCourses` —— 这就是装 2.5.8.7 后第二课堂仍然少课的原因，与修复本身是否有效无关。
- `2.5.8.8`（206）在合并后的主线上重建，dex 反查 `cxJxzxjhxfyqFKcxx` = 1、`cxJxzxjhxfyqKcxx` = 1、`planCourses` = 1、`opensCourses` = 1、`jdkcsx` = 3 处；同一检查在 2.5.8.7 上三项均为 0。

### 验证
- 合并后四级测试实跑：`test_core` **1278**、`test_build_config` **5**、`test_webview_policy` **5**、`test_ui_contract` **213**，全绿；`check_doc_sync` exit 0。
- 产物：`delivery/科信小助手-2.5.8.8.apk`，5 404 538 B，SHA-256 `aadbe23e40aad432771c65ac08832af2db4ffa2e6bd3ac463535384420fd5e80`（以同目录 `.build.json` 为准）；`aapt2 dump badging` 实测 `versionCode=206` / `versionName=2.5.8.8`，权限 4 项，`apksigner` 验签 v2 / v3 均为 true；只读包 dex 中 7 条水控端点与 2 个水控类名命中数全为 0。
- **未做**：真机验收（第二课堂逐课组展开、覆盖安装、圆角观感）。

---

## [未发布 · 2.5.8.7] — 已提交，versionCode 205

> 两处小改：课表圆角的默认值、以及「使用公告」的重复入口。都没有动绘制逻辑、存储格式或任何业务代码。版本号 2.5.8.6/204 → **2.5.8.7/205**，仅按「生成安装包」要求递增，未发版。

### 变更
- **课表圆角默认 8dp → 20dp**（`Theme.DEFAULT_RADIUS`）。滑杆上限 `MAX_RADIUS` 本来就是 20dp，所以新默认值等于满档；`Theme.load()` 只在偏好里**没有** `radius` 时才取默认值，改过圆角的读者升级后仍是自己保存的那个数，不会被重置。`AppearanceActivity` 的 `draftRadius` 初值取自 `Theme.DEFAULT_RADIUS`，滑杆、预览与课表三处同源，没有第二份硬编码。
- **`MoreActivity` 删除「使用公告」条目**：公告已在「数据与隐私」页末挂着「阅读使用公告」，同一份文本不再开两个入口。「更多」现在只剩 数据与隐私 / 更新日志 /（诊断包才有）日志 / GitHub 仓库 / MIT 许可证。

### 验证
- 该轮构建时实测 `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 208 全绿；`check_doc_sync` exit 0。与少课修复合并后复跑为 **1278 / 213**（见下条）。
- `test_ui_contract` 新增反向锚点：`MoreActivity` 的代码行里不得出现 `NoticeActivity`（注释里提不算），防止入口再加回来。
- 构建产物：`delivery/科信小助手-2.5.8.7.apk`，5 404 538 B，SHA-256 `01d4341b8110e4c1b773f78611fc10025a6ea59f7da7cd691a28abe84a519df1`；`aapt2 dump badging` 实测 `versionCode=205` / `versionName=2.5.8.7`，权限 4 项，apksigner 验签 v2 / v3 均为 true。
- **未做**：真机未验圆角观感（0 / 20 两端、320dp 窄屏、深色主题下的格子边缘）。

---

## [未发布 · 学业情况少课修复] — 已合并到主线，未发版

> 在并行 worktree 内开发，未触碰主工作区。只改学业情况课程展示与取课端点选择，不动账号/支付/水控/升级。

### 修复
- **学业情况里"右边有学分、点开却显示这个课组还没有课程记录"**：学校把课组课程分成两个只读端点——计划内 `POST /xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html`、计划外 `POST /xsxy/xsxyqk_cxJxzxjhxfyqFKcxx.html`，并按课组标签上的 `jdkcsx == '1'` 二选一（学校脚本原样如此）。此前 App 一律请求计划内端点，于是通识选修/公共艺术这类计划外课组（人文科学 5 门、自然科学 6 门）拿到 `[]`，界面显示"还没有课程记录"。现在 `EduAcademic.Node.planCourses` 携带该判定，`EduClient.academicCourses()` 按节点选端点，计划外请求只带学校自己发的字段（`fromXh_id`/`xfyqjd_id`/`xh_id`）。
- **可点判定回归"叶子节点"**：学校只给叶子节点挂展开链接（脚本遍历 `.treeview .title a.more`），父行不该展开课程。此前按 `jdkcsx` 决定可点性，导致父行也可点、并渲染出空课程块；现在 `opensCourses(hasChildren)` 只在无子节点时可点。
- 只读路径白名单补上计划外端点；`jdkcsx` 仍从 `<p>` 与 `<div class='title' xfyqjd_id=…>` 两处读取（两种部署位置都已断言）。

### 验证
- 真实账号端到端复验（按新规则逐叶子请求）：24 个叶子节点中，人文科学 **5 门**（学校页显示"共 (5) 门"）、自然科学 **6 门**（"共 (6) 门"）、公共艺术某课组 **2 门**，其余计划内课组条数与修复前一致；`parseCourses()` 读计划外返回无丢课。抓包数据与凭据用后即删，工作区无残留。
- 合并到主线后实跑：`test_core` **1278**（含计划内/计划外分流、特殊节点 `qtkcxfyq`/`cxcyqkxfyq`、无标记页面回退）/ `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **213**（本修复新增 5 个锚点：两端点都在只读白名单、请求必须按节点选端点）全绿。
- 合并前已在 `delivery/科信小助手-2.5.8.6.apk`（5,392,250 B）上验证过 dex 含 `cxJxzxjhxfyqFKcxx`/`planCourses`/`opensCourses`；合并后的包以 2.5.8.8 重新构建为准。**真机未验证**。


## [未发布 · 应用内更新日志] — 已提交 `9a9ccad`，未发版（versionCode 仍为 204）

> 新增「更多 → 更新日志」整页，数据随包内置、离线可读。参考岱宗盒子的版本列表组织方式（`update/AppChangelog.kt` 只放数据，真正渲染在 `update/UpdateDialogs.kt` 的堆叠式 What's New 弹窗与 settings 宿主的 `ChangelogEntry` 列表里），**没有复制其代码、素材或主题**；版式、配色、动效按 UI-1/UI-2/UI-3 的 token 重做。核心 1262 断言、UI 守卫 208 锚点全绿，普通版 APK 已构建并验签。

### 新增
- **`AppChangelog.java`**：更新日志唯一数据源。`Entry(version, code, date, known[], items…)` + `Item(Kind, text)`，`Kind` 分 新增 / 优化 / 修复 / 其他；`released()` 过滤未发布版本，`current(versionName)` 决定谁挂「当前版本」。数据只收**已正式发布**的版本（2.5.7 / 2.5.6 / 2.5.5 / 2.5.0 / 2.3.10），取自 `delivery/release-*.md`、`delivery/update/update.json` 与 `CHANGELOG.md`，日期与版本代码照抄，没有推测。
- **`ChangelogActivity.java`**：`extends Page`，顶部返回 + 标题 + 「当前安装 x（code）」；每个版本一张卡片，标题 `CARD_TITLE` 粗体、日期 `BODY_SMALL` + `MUTED`、当前版本挂 `SOFT` 胶囊；每条变化前是类别图标（`plus` / `torch` / `check` / `info`，颜色只取 `AppUi`）；已知问题用 `AppUi.DANGER`。最新一版与当前安装版默认展开，历史版本点击展开/收起。

### 变更
- **`MoreActivity`**：设置卡片新增「更新日志 · 查看版本更新与历史记录」一行，复用 `entry()` 的图标/箭头/分隔线样式。
- **`MotionSpec`**：新增 `LIST_TOGGLE_DURATION`(220ms) 与 `settle()`（减速曲线），展开动画的时长与曲线只有一个来源。
- **`AndroidManifest.xml`**：注册 `.ChangelogActivity`，`exported=false`、`configChanges` 与其他子页一致；未新增任何权限。

### 交互与竞态
- 展开只动两样东西：内容高度与箭头角度，同一动画驱动；系统关动画（`MotionSpec.shouldAnimate`）时直接落到终态（展开回 `WRAP_CONTENT`）。
- 连续点击：每张卡片自带 `running` 与 `generation`，新点击先 `cancel()` 上一条并从**当前高度**继续，过期回调被 `generation` 拦下，不会两条动画抢同一个 `LayoutParams.height`。
- 开发包（2.5.8.6）匹配不到条目：**不**给任何正式版打「当前版本」，改为顶部说明「此版本尚未正式发布」。
- `Page` / `CardLayer` / 主屏与 Tab 转场 / 课表 / 教务 / 支付 / 水控 / `UpdateCheck` 均未改动。

### 自动化校验（`scripts/test_ui_contract.py`，187 → 208 锚点）
版本号唯一、版本代码严格递减、日期合法、**最新条目必须等于 `delivery/update/update.json` 的 version/versionCode**（发版忘写日志即红）、任何版本代码大于线上版本即红（开发记录不得混入）、每条版本必须有 `delivery/release-*.md` 或 `CHANGELOG.md` 记录、页面只取 `released()`、展开动画取 `MotionSpec`、关动画走终态、连点 `cancel` 与 `generation` 守卫、入口存在、`exported=false` 注册、两文件不含 WebView / 网络调用。另有一条反向守卫：`MoreActivity` 的代码行里**不得再出现** `NoticeActivity`，公告入口只留在「数据与隐私」页末。

### 未做（第二阶段）
- 升级后「本次更新」弹窗、`lastSeen` 记忆、`NoticeDialog`/`UpdateCheck` 的启动时序协调。数据结构已按 `code` 预留区间取用的可能，但**未实现任何自动弹出**，`UpdateCheck` 的下载/校验/安装流程一行未改。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **208** 全绿。
- APK 已构建并验签（`delivery/科信小助手-2.5.8.6.apk`，5 404 538 B，权限 4 项，v2/v3 通过）；该文件随后续小改重新构建过，**当前校验值以上方最新区块为准**。
- **未做**：真机 L1–L8（展开手感、连点、320dp / 大字体、深色、飞行模式、开发包不误标当前版本）。

---

## [未发布 · 数据与隐私 / 使用公告专项] — 已提交 `6b619ab`，未发版（versionCode 仍为 204）

> 按发布者提供的新版文案重写这两份文本，并逐条对照代码核对。**只改文案与展示，不改安全机制与业务逻辑**；权限表仅按发布者确认移除一个从未使用的 `ACCESS_WIFI_STATE`。文案集中在 `LegalText`，排版集中在 `LegalView`，首次启动弹窗与新增的公告页读同一份文字。核心 1262 断言、UI 守卫 187 锚点全绿，普通版 APK 已构建并验签。

### 新增
- **`LegalText.java`**：两份文本的唯一来源（隐私 8 节、公告 7 节）。隐私联系邮箱填 `shynekoo@outlook.com`（与「意见反馈」公开地址同一个，不另设邮箱），生效日期 `2026年11月19日`；守卫会拦住任何残留的 `【…】` 占位符，并校验两处邮箱一致。
- **`LegalView.java`**：两页共用排版（返回、标题、说明、可滚动正文、每节一张卡片）。字号用 sp，跟随系统字体缩放；颜色只读 `AppUi`，深浅色与自定义主题随动。
- **`NoticeActivity.java`**：使用公告整篇阅读页（离线静态文本）。**唯一入口**是「数据与隐私」页末的「阅读使用公告」卡片（早期曾在「更多」里并排一条，发布者要求去重后已删除）；首次启动仍由 `NoticeDialog` 负责「以后不再提醒」的选择，两者共用 `LegalText.NOTICE`。

### 变更
- **`PrivacyActivity`**：原先手写的「保存在这台手机上 / 不会离开这台手机 / 权限」三张卡片换成完整的隐私说明（8 节）；两个清除按钮及其行为、清除范围未变；末节入口改为「阅读使用公告」，进整篇页面（原先复用弹窗）。
- **`MoreActivity`**：文案改版时曾新增「使用公告」独立条目；发布者指出公告已在「数据与隐私」页末，同一份文本摆两条入口只会让人猜哪个是正的，**该条目已删除**。现在「更多」只剩：数据与隐私 / 更新日志 /（诊断包的日志）/ GitHub 仓库 / MIT 许可证。
- **`NoticeDialog`**：正文改读 `LegalText.NOTICE`；`VERSION` 2 → 3，勾过「不再提醒」的人在文案改版后会再看一次。
- **`AndroidManifest.xml`**：注册 `.NoticeActivity`（`exported="false"`，`configChanges` 与其他子页一致）；**移除 `ACCESS_WIFI_STATE`**（见下）。未新增任何权限。

### 文案与代码核对（据此改了 5 处声明）
1. **教务密码**：原文「不作为长期保存的密码写入本地加密存储」与 `EduVault` 不符——主动保存教务账号时，`账号\n密码` 会用 Keystore 中不可导出的密钥、以 AES-256-GCM 加密写入 `noBackupFilesDir/edu-account.bin`。已改为按实际行为描述。
2. **支付与充值**：应用没有充值、收银或代下单；`AlipayLogin` 只调 `AuthTask.authV2` 取授权码，用水是把设备交给支付宝的 URI。原文的「支付、签约和扣费」改为「不提供充值/收银/代下单，也不接收支付密码；结算在支付宝内完成，应用只读取订单状态」。
3. **权限**：清单里有 `ACCESS_WIFI_STATE`，但全仓库无 `WifiManager` 调用，支付宝 SDK 也只用 `ConnectivityManager`（8 个类引用）——该权限纯属声明负担。发布者确认后**从清单移除**，隐私文案第 5 节同步改为「网络状态：……应用自身的业务代码不读取该状态，所集成的支付宝 SDK 会用它判断网络可用性」，权限数量由 5 项降到 4 项。`ACCESS_NETWORK_STATE` 保留，因为 SDK 确实在用它。
4. **第三方服务**：把「GitHub 或软件分发服务」落到具体来源（workbuddy 托管的版本清单，安装包按其指向的 HTTPS 地址，可能是 GitHub Releases），并列出四个端点。
5. **使用公告 §4**：删掉应用并不存在的「充值」，改为余额查询与扫码用水。

### 未改（边界）
- 加密与存储（`AccountVault`/`EduVault`/`LifeSessionVault`/`PendingUseVault`）、清除逻辑与范围、更新校验、诊断日志、教务/支付/水控业务逻辑一行未动；权限只减不增（仅移除 `ACCESS_WIFI_STATE`，未新增任何权限）；未新增广告、统计、上传或追踪。
- 两份文本均为打包进 APK 的静态文字，无 WebView、无网络请求，断网可读。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **187**（+28 锚点：文案存在性、邮箱为真实公开地址且与「意见反馈」一致、无残留占位符、教务密码与 AES 表述、不得声称充值、manifest 权限逐项披露、隐私页入口与弹窗同源、`VERSION = 3`、离线可读）全绿。
- APK `delivery/科信小助手-2.5.8.6.apk`，5 396 346 B，SHA-256 `be4903d575cf47a02a1da2dfc7eb49b8bcf7ebf0194dbe44b486b39aa249605e`，验签通过；dex 内确认文案已填充（`shynekoo@outlook.com` / `2026年11月19日`），无 `【…】` 残留，权限表实测 4 项。
- **未做**：真机排版、系统大字体、320dp 换行、深浅色对比。发布信息（邮箱 / 生效日期）已填。


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

> 在 `ffaf933`（转场竞态修复）之上继续 UI-3，只查页面切换动画、快速连点竞态、动画取消清理、滚动位置恢复、与课表横滑/返回手势的关系。**不改转场参数、不改视觉样式、不动 MainNavigator / Page / CardLayer**。核心 1262 断言、UI 守卫 159 锚点（原 141 + 本轮 17）全绿，普通版 APK 已构建并 v2/v3 验签。

### 修复
- **快速连点把上一页的滚动量写到新页**：`restoreScroll` 原来只读字段 `root`，而 `root` 会被后一次切换改指向新页——今日→课表→账号连点时，课表挂起的恢复会落到账号页并把它滚到课表的位置。现在恢复携带转场代号，并在执行时同时校验 `generation`、目标页仍是 `root`、tab 未变，任一不匹配即放弃。
- **滚动恢复排在了布局之前**：原实现用 `root.post(...)`，只保证「下一条消息」，不保证在首次 measure/layout 之后；未测量的 ScrollView 会把 `scrollTo` 抹掉，首次切回该 Tab 可能回到顶部。改为一次性 `OnPreDrawListener`（布局后、首帧前）恢复并自摘除监听。
- **普通重建把滚动位置重置**：`render()` 的非动画路径原来直接按 `tabScroll[nav.tab()]` 恢复，而该值只在 `selectTab` 时更新——旋转、分屏（`onConfigurationChanged` → `render()`）、换主题、数据刷新都会跳回上次切 Tab 的位置或顶部。现在 `render()` 在重建**同一屏**（`contentTab`/`contentCampus` 与当前一致）时先 `captureScroll()` 再恢复；换 Tab、校园记录↔入口列表这类真正换页的情况不搬运位移。
- **被打断的入场页带着 8dp 位移离场**：快速连点时上一页的 enter 被 `cancel()` 只停在当前数值，`exit` 只动 alpha，位移会留到淡出全程。现在被保留为离场页的残留先归位到「alpha 1 / translationX 0」。
- **转场期间两页同时可读**：离场页在交叉淡化期间标 `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`，读屏不会把正在消失的旧内容当成仍在页面上朗读。
- **「回本周」/ 选周次的换页时机**：原先直接 `applyWeek(delta)`，跨几周闪一下换页；第一次改成「先滑出一屏、落地时重建」，仍是「滑完才变成本周」，动画结束那一帧内容突变；第二次改成「先提交周次 + Tab 式淡入（alpha 0.7→1 / ±8dp / 220ms）」，内容不再突变，但整屏淡入与「横向换一周」的心理模型不符，真机观感比滑页弱。现在走第三条：`jumpWeek` **先提交周次**，再调用 `renderContent(true, ±1, true)` 的 slide 分支 —— 整屏位移一屏（`pageWidth()`），旧页 `-offset*t` 与新页 `offset*(1-t)` **同一时长同向同行、都不透明**，落地帧就是本周。周次弹窗 `selectWeek` 同样走这条路径；相邻一周的 ‹ › / 左右滑动仍是 Pager 滑页，未改。
- **滑页参数只有一个来源**：`MotionSpec.WEEK_SLIDE_DURATION`(190ms) 同时给 `Pager.settle` 与跨周 slide 使用，两处时长/曲线（`DecelerateInterpolator`）一致；`Pager.animateOutThen` 的滑出位移收敛为 ±1 屏（原先按 `-delta*width`，跨 5 周会把板面甩出 5 屏），避免以后有人拿它做跨周跳转。Tab 转场参数（alpha 0.7→1 / ±8dp / 220ms 进 180ms 出）保持不变，slide 分支不淡入、不新建任何动效数值。

### 检查结论（未改动）
- **与课表左右滑动**：转场只改 contentHost 内 `next` 的 `translationX`，`WeekBoard.Pager` 改的是自己三个子页的 `translationX`，两者作用在不同 View 上、互不拦截触摸；`Pager.onInterceptTouchEvent` 在自身 `settle` 期间返回 true，不参与内容转场。切走时 `onDetachedFromWindow` 会 `cancel` 掉周次动画，不会在已分离的页上回调 `applyWeek`。
- **与返回手势**：转场与 `Page` 的返回动画不同时发生——返回走 `nav.consumeBack()` → `render()`（非动画），会先取消内容转场；`refreshBackMode()` 在 `selectTab` 中已按新 tab 重算，系统预测式/本地缩放的选择不受转场影响。
- Tab 转场参数（alpha 0.7→1 / ±8dp / 220 进 180 出）、`UiTokens` 数值、`MainNavigator`、`Page`、`CardLayer` 均未改；`MotionSpec` 只新增 `WEEK_SLIDE_DURATION`(190ms) 这一个 token，`WeekBoard` 只动了 `Pager.animateOutThen` 的滑出位移（±1 屏）与 `settle` 取该 token（190ms 数值不变），手势判定、页高测量、周次提交逻辑未改；系统关动画时仍走 `!motion` 直接到终态。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **159** 全绿；普通版 APK（`delivery/科信小助手-2.5.8.6.apk`，5 392 250 B，SHA-256 `1f84c5efe1c56e2fd6bc59288a2c81d4cb20dbad48d22fd48af44b0c998af8b9`）构建通过、v2/v3 验签 true，构建脚本的三开关/控制路由断言全过。
- **待真机验收**：快速连点（三 Tab 连续点）无残影错位、切走再切回每页滚动位置、旋转与分屏后滚动位置、换主题后不跳顶、课表横滑与自适应高度、**回本周为整屏滑页且落地帧即为本周（不再淡入、不再突变）**、返回手势完成与取消、系统关动画、今日 60s、320dp 窄屏 —— 均未实测。

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
