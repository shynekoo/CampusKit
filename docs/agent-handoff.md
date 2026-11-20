# Agent 交接

供任意 Agent 接手时快速对齐现状。以**当前代码、Git 记录、实测结果**为准；更细的流程见仓库根 [`../AGENTS.md`](../AGENTS.md)。

## 一、现状快照（2026-11-19）

| 项 | 值 |
| :-- | :-- |
| 技术栈 | Java + 原生 Android View + Python 构建；无 Kotlin / Compose / Gradle |
| 线上正式版 | **2.5.9 / versionCode 210**（2026-11-20 发布，`delivery/update/update.json`；修复扫码跳转 + 四段版本号检查不到更新，用水版） |
| 已提交 HEAD | `00d7bb4`（main：2.5.8 发布，已 push） |
| 当前开发分支 | `feat/diag-2.5.8`（诊断系统改造，**未提交、未合并**） |
| 最新包 | `delivery/科信小助手-2.5.9.apk`（versionCode 210，已发布，sha256 `b022159d…c82ef605`，`--field-water` 用水版）；`2.5.8.1`/209 因四段版名检查不到更新已被取代 |
| 发布公告 | `delivery/release-2.5.8.md`（短版 / 长版公告 + 上线记录 + 原始清单） |
| 最新 tag / Release | `v2.5.9`（[Release](https://github.com/shynekoo/CampusKit/releases/tag/v2.5.9)，附 ASCII 资产 `kexin-xiaozhushou-2.5.9.apk`） |
| 工作区 | `feat/diag-2.5.8` 上未提交；主线 `main` 未改 |
| 核心断言 | 实测 **1418 项全过**；UI 契约守卫 **291 锚点**；诊断隐私守卫 **651 项**；WebView 守卫 **6 处** |
| 发布状态 | **2.5.8 已发布**：清单、直链、下载站、App 内更新日志已同步；早期 2.5.8.x 开发包未单独发布 |
| 未发布开发版 | **2.6.0-beta.1 / versionCode 217**（学生评价：Referer 带模块号 + 给学校表单请求补 gnmkdm + 按 DOM 找行点击）；2.5.10 / 211 … 2.5.13-beta.2 / 216 均未公开发布，已被取代 |
| 预览渠道 | `delivery/update/beta.json` 已更新为 2.6.0-beta.1 / 217，**线上未部署**（`beta.json` 现为 404，App 会安全回退到正式渠道） |

## 二、已提交但未发布（2.5.8.1）

- **安全 S1–S6**：更新校验（bytes+sha256 必填、HTTPS 逐跳、包身份）、诊断默认关与脱敏、WebView 白名单、敏感页快照排除、支付/用水审计、教务 WebView 统一准入。
- **架构 A1–A4**：导航状态机下沉 core、typed `Course` 模型、今日真局部刷新（`RefreshDecision`/`TodayDynamic`）、会话检查去抖与错误隔离；三个 Screen 提取。
- **功能**：使用码（8 位）、UI 契约守卫、真机验收清单。

## 三、2.5.8.2–2.5.8.6 功能（已提交，未发布）

- 新增 `core/EduAcademic.java` + `android/AcademicStatusActivity.java`：教务「学业情况查询」（GPA、统计、去重学分树）；`EduClient` 放行 `/xsxy/` 只读页并复用登录会话。
- **2.5.8.3 外观**：品牌蓝改为「默认」配色、删除两段说明文案、课表预览圆角按 dp→px 换算修正。
- **2.5.8.4 修复**：学业页 `card()` 双重挂载导致闪退，改为唯一入口 `addCard()` 挂载；`screen.note()` 只在 `onCreate` 设置一次；教务卡顺序调为第一行 成绩/空教室/教务系统，第二行 学业情况/河工程官网/科信官网。
- **2.5.8.5 排版**：按 `appendTo` 还原 5 层树，一级/二级标题成卡、depth≥3 缩进 14dp×(depth-2)；进度条按 0.01 精度；空分类合并紧凑卡。
- **2.5.8.6 课程明细**：新增 `POST /xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html` 按课组取课程；`EduAcademic.parseCourses()` + `AcademicStatusActivity` 点击展开、懒加载、按课组缓存、失败可重试。
- UI 基建：新增 `MainShell`、`AnimatedNavBar`、`MotionSpec`、`UiTokens`（tab 指示器动画 + reduce-motion 降级 + 尺寸 token）。
- manifest 注册 `AcademicStatusActivity`，版本 2.5.8.1/199 → 2.5.8.6/204。

## 三之二、UI 2.0 进度（UI-1 / UI-2 已入库，UI-3 部分入库）

| 阶段 | 状态 | 位置 |
| :-- | :-- | :-- |
| UI-1 视觉/动效 token | 已完成（`7a8083d`） | `UiTokens` / `MotionSpec` |
| UI-2 常驻 shell + 动画底栏 | 已完成（`7a8083d`，外观修正 `20c2ffa`） | `MainShell` / `AnimatedNavBar` |
| UI-3 页面切换动画 | 已完成（`ffaf933` 转场 + 竞态修复；`fce95bc`+`jumpWeek` 滚动恢复/残留清理/回本周整屏滑页） | `TimetableActivity.renderContent` |
| 数据与隐私 / 使用公告 | 已完成并提交（`6b619ab`：共享文案 `LegalText` + 排版 `LegalView` + 公告页 `NoticeActivity`；邮箱 `shynekoo@outlook.com`、生效日期 `2026年11月19日`；顺带移除未使用的 `ACCESS_WIFI_STATE`）。公告入口只留「数据与隐私」页末，「更多」里的重复条目已按发布者要求删除 | `PrivacyActivity` / `NoticeActivity` |
| 应用内更新日志（第一阶段） | 已完成并提交 `9a9ccad`：`AppChangelog`（内置 5 个已发布版本）+ `ChangelogActivity`（展开式卡片）；第二阶段「升级后弹本次更新」未做 | `MoreActivity` → `ChangelogActivity` |
| UI-4 视觉圆角与卡片 | 未开始 | — |
| 课表圆角默认值 | 已提交 `4139aec`：`Theme.DEFAULT_RADIUS` 8 → 20dp（= `MAX_RADIUS` 满档）；`load()` 只对未保存过 `radius` 的设备生效，老用户不被重置 | `Theme` / `AppearanceActivity` |
| UI-5 | 未开始 | — |

UI-3 本轮修 5 处：滚动恢复带 generation 且改为布局后（pre-draw）执行；`render()` 仅在同屏重建时搬运当前滚动；被取消入场的残留页先归位再离场；离场页在交叉淡化期间移出无障碍树；跨多周（回本周 / 选周次）改走 `jumpWeek`：先落周次，再用 `renderContent(true, ±1, true)` 的 slide 分支整屏滑一屏，落地帧即为本周（不淡入）。**未改**转场参数、`UiTokens`、`MainNavigator`、`Page`、`CardLayer`；`MotionSpec` 新增 `WEEK_SLIDE_DURATION`(190ms) 供 Pager 与跨周共用，`WeekBoard` 只动 `animateOutThen` 的位移与 `settle` 取 token（数值不变），手势/测量/周次提交未改。

## 三之三、诊断系统（2.5.8，未提交）

- 分层：`core/DiagRedact`（字段白名单 + 脱敏 + 安全堆栈 + 原因分类）、`core/DiagEvent`（行格式与解析）、
  `core/DiagCatalog`（事件/模块/原因文案）；`android/Diag`（开关、崩溃兜底、快照、写线程）、`android/DiagFiles`
  （256 KB × 4 轮转、7 天保留）、`android/DiagActivity`（筛选/展开/分页/复制/导出/清空）、
  `android/DiagReportActivity`（故障报告）。
- 开关：`BuildFlags.DIAG_BASIC` 恒开（正式版也有诊断页，运行期默认关）；`BuildFlags.DIAGNOSTICS` 仅 `--diagnostics`，
  细节事件用 `Diag.DETAIL` 包住，正式包里被 javac 删除（`Page.class` 的 `back.gesture` 出现 0 次可证）。
- 已接入模块：教务登录四阶段、课表请求/解析/写入、网络状态与超时、慧生活、支付宝、趣智、更新、页面导航。
- 未接入：扫码/相机、课表拖拽缩放、Activity 被回收的支付竞态、ANR/native crash。
- 权威文档：[`diagnostics.md`](diagnostics.md)；隐私边界见 [`security.md`](security.md)「诊断日志的安全边界」。

## 三之四、学生评价（2.5.10，工作区改动、未提交）

- 新增 `core/EduEvaluation`：评价列表与统计解析（教学班/课程/教师/学时类型、状态 `-1/0/1`、`withState` 分组、`counts`）。
- 新增 `android/EvaluationActivity`（原生列表：未评/暂存/已提交分组、未评计数、教务账号/网页登录/退出登录、返回表单后自动重读）+
  `android/EvaluationFormActivity`（把某个教学班的表单 POST 给学校，复用已登录会话 cookie；HTTPS 守卫、禁止第三方 cookie）。
- `EduClient` 新增 `evaluations()` / `evaluationCounts()` / `evaluationForm()`；`checkPath()` 只放行
  `xspj_cxXspjIndex.html` 与 `xspj_cxXspjqk.html`；`/xspjgl/` 的 Referer 指向评价首页。
- 教务卡改为第一行四件查询（成绩/空教室/学业情况/学生评价）+ 第二行三件入口；新增 `eval` 星形图标；manifest 版本 211 / 2.5.10。
- **设计边界**：评分与提交只在学校的表单页面上完成，应用不调用 `xspj_tjXspj.html` / `xspj_bcXspj.html`
  （提交一次性、必填项与评语规则由学校模板决定）；`test_ui_contract.py` 有专门守卫。
- 真实账号实测（2026-11-20）：列表 20 条、统计 `{"wpgs":20,"bcgs":0,"tjgs":0}`、表单片段 200 且含 13 个评价项。
- 产物：`delivery/科信小助手-2.5.10.apk`（5 449 594 B，SHA-256 `2af8cefa…14330cfe`，211 / 2.5.10，用水版，v2/v3 验签通过，
  dex 水控类命中 1/1；`source_sha256` 与工作区一致）。
- 真机表单渲染与提交未验证（需真实评教窗口）。

## 三之五、学生评价表单参数修复（2.5.11，工作区改动、未提交）

- **现象（真机实测，用户截图）**：点开某个教学班，学校页面只回红字「课程号ID,教学班ID,教职工ID参数异常!」。
- **原因**：`EvaluationFormActivity` 只用 `WebView.postUrl()` 把 id 放在请求体里。WebView 的 POST 不是浏览器表单提交，
  学校侧 `getParameter` 读到空参数表。同样的字段用普通 HTTP 客户端 POST 能拿到含 13 个评价项的表单（2.5.10 实测），
  说明字段本身正确，问题在传输。
- **修复**：新增 `EduClient.evaluationFormUrl(body)`，把同一串已编码参数拼进查询串，请求体保留；两条通道任一条被丢都能读全。
- **兜底**：页面加载后用 JS 检查学校自己的「参数异常 / 参数错误」文案，命中则自动改用 GET 重开一次；
  再失败就明说可能改版，并指路「教务系统」网页登录，不再在错误页上显示「评完点提交」。
- **守卫**：`test_ui_contract` 增至 255 锚点，新增「参数必须同时进查询串」「学校参数错误页必须被识别」两条。
- 产物：`delivery/科信小助手-2.5.11.apk`（5 449 594 B，SHA-256 `ade5d33f…fa5706ba`，212 / 2.5.11，用水版，v2/v3 验签通过，
  dex 水控类命中 1/1；`source_sha256` 与工作区一致）。
- **仍依赖真机确认**：表单是否正常渲染、暂存/提交是否成功（需真实评教窗口；本轮无可复测的教务账号）。

## 三之六、学生评价表单：改为浏览器表单提交（2.5.12，工作区改动、未提交）

- **2.5.11 的真机结果**：id 同时进查询串后仍是同一行「参数异常」，说明问题不在「只放 body」。
- **读学校脚本得到的硬事实**（`/js/comp/jwglxt/jxpjgl/xspj/xspj_index.js`，匿名可取）：
  打开表单是 `$(panel).load("/xspjgl/xspj_cxXspjDisplay.html", {jxb_id,kch_id,xsdm,jgh_id,tjzt,pjmbmcb_id,sfcjlrjs})`
  —— jQuery 的 urlencoded POST，**不带 `gnmkdm`**；`gnmkdm` 只有列表页要。
- **改法**：请求改由一次性页面的隐藏域 + `form.submit()` 发出（编码/MIME 由平台保证），不再用 `postUrl()`；
  四种形态依次退让：①无 gnmkdm、id 只在 body ②带 gnmkdm 且 query+body 都有 ③同上 GET ④无 gnmkdm 的 GET；
  每次只在上一形态被判「参数异常」后才换下一种。
- **诊断**：全部失败时状态栏显示每个 id 的字符数 + 最终落到的路径，用一次截图即可区分「参数丢了」和「参数为空」。
- **接口事实**：未登录访问 `xspj_cxXspjDisplay.html` 一律 302 到登录页；能拿到那行红字 ⇒ 会话是通的，问题只在参数。
- 产物：`delivery/科信小助手-2.5.12.apk`（5 453 690 B，SHA-256 `df3390ac…365e0d42d6`，213 / 2.5.12，用水版，
  v2/v3 验签通过，dex 水控类 1/1；`source_sha256` 与工作区一致）。
- 守卫：`test_ui_contract` 增至 **260 锚点**（新增「必须由平台提交表单」「不得回退到 postUrl」「四种形态」三条）。

## 三之七、学生评价：改为在学校列表页里选行（2.5.13，工作区改动、未提交）

- **2.5.12 的真机结果**：红字从「参数异常」变成「系统升级维护中」——第一形态丢了 `gnmkdm` 被 500 挡住，
  而检测只认「参数异常」，四种形态没往下走。
- **决定性事实（学校静态 JS 匿名可取）**：表单 `xspj_cxXspjDisplay.html` 是**片段**；`xspj_display.js` 绑定的
  `#btn_xspj_bc` / `#btn_xspj_tj`（暂存 / 提交）在**列表页 HTML** 里，片段中没有。独立加载片段 ⇒ 没有可用的提交按钮，
  请求拼得再对也没用。学校自己打开它的路径是 `xspj_index.js` 的 `onSelectRow`：
  `$(#panel_content).load("/xspjgl/xspj_cxXspjDisplay.html", {jxb_id,kch_id,xsdm,jgh_id,tjzt,pjmbmcb_id,sfcjlrjs}, cb, "html")`。
- **改法**：加载 `xspj_cxXspjIndex.html?gnmkdm=N401605&doType=details`（带 `Referer: /xtgl/index_initMenu.html`），
  等 jqGrid 有数据 → `getRowData` 按 `jxb_id` 找行 → `setSelection(rowid)`，由学校 `onSelectRow` 装片段并绑按钮。
- **竞态**：页面进来时自己会选中第一行，此时再选别人的行会被 `inLoading` 吞掉；1.5 s 后量 `#panel_content`
  （去空白文本长度 + 控件数×8）不足 40 就重选，最多 4 次，仍空则让用户自己点。
- **删掉**：`evaluationForm(item)` / `evaluationFormUrl()` / `EVAL_FORM` / `EVAL_FORM_BARE` / handoff 提交页 / 四形态退让。
- 守卫：`test_ui_contract` **259 锚点**（列表页 + 菜单框架 Referer + setSelection + panel_content + 禁 postUrl + 禁自拼表单）。
- 产物：`delivery/科信小助手-2.5.13.apk`（5 453 690 B，SHA-256 `68d18703…4264f858`，214 / 2.5.13，用水版，
  v2/v3 验签通过，dex 水控类 1/1，`source_sha256` 与工作区一致）。
- **仍未验证**：真机上的渲染与提交（本轮无可用教务账号）。

## 三之八、版本号规范与预览版更新渠道（2.5.13-beta.2，工作区改动、未提交）

- **命名规则**：正式版 `X.Y.Z`，预览版 `X.Y.Z-beta.N`（N 从 1 起、无前导零）；四段 `X.Y.Z.W` 只保留读取兼容。
  唯一实现在 `core/ReleaseVersion`（`isWellFormed` / `isBeta` / `isRelease` / `isLegacy`），`UpdateManifest` 改调它。
- **比较规则**：只比 `versionCode`，绝不比名字。`core/UpdateChannel.pick(installed, betaEnabled, release, preview)` 是唯一选择点：
  候选必须严格大于已装版本；预览清单仅在开关打开时参与；预览清单里若写了正式版名则忽略；同号时正式清单优先。
- **构建期**：`build_preview.py` 的 `check_version_name()` 拒绝四段名 / `beta.0` / 前缀大写的 `-BETA`；
  `--beta` 必须与 `-beta.N` 名字同时出现。`check_version_code()` 要求新包高于 `update.json` 与 `beta.json` 的最大号，
  否则直接失败（`--allow-lower-version-code` 仅供内部构建）。写死的 `FIELD_VERSION_NAME` 已删除。
- **manifest**：`2.5.13-beta.2` / 216。线上正式版 210（2.5.9）；正式版 2.5.13 必须 ≥ 217 才能覆盖此前的 Beta。
- **开关**：「更多 → 更新日志」下一行「接收预览版更新」，`update-v1` 偏好 `beta` 键；**一律默认关闭**（预览包首装也一样，
  不按安装包类型猜默认值）；打开前必过确认框「预览版供开发者测试使用，可能不稳定，不建议开启。」，
  只有「仍然开启」才落盘，取消/点外部/返回键拨回关且不写偏好；切换时 `UpdateCheck` 的 `generation` 自增，在飞的结果作废
  （`check.stale`）。
- **清单**：新增 `delivery/update/beta.json`（2.5.13-beta.2 / 216 / 5 453 690 B / sha256 `4fdbb951…78d08687`），
  与 `delivery/update/campuskit-2.5.13-beta.2.apk` 一致；**未部署**，`update.json` 未动。
- **未验证**：真机开关默认关与确认框取消路径、持久化、关开关不降级、Beta → 正式覆盖安装（`real-device-acceptance.md` B5–B8）。

## 三之九、学生评价：Referer 与 gnmkdm（2.6.0-beta.1，工作区改动、未提交）

- **2.5.13 的真机现象（用户截图）**：状态栏停在「正在定位这门课…」，面板里只有列表页自己的占位
  （`状态备注` / `评价内容` / `0% Complete`），**一个 `.panel-pjdx` 都没有** —— 片段根本没被装进来。
- **根因**：`#progress_loading` / `#panel_content` 都在列表页 HTML 里，所以「0% Complete」是页面自己的骨架；
  列表页脚本 `.load("/xspjgl/xspj_cxXspjDisplay.html", {…})` **不带 `gnmkdm`**，该端点无模块号时返回 500
  （手测：带 `gnmkdm=N401605` 返回含 13 个评价项的表单）。桌面能评是因为 AJAX 的 Referer 是带 `gnmkdm` 的列表页 URL，
  2.5.13 把 Referer 改成菜单框架（不含它）恰好掐断了这条路径。
- **改法**：①Referer 改回页面自身 url；②在页面内包装 `jQuery.fn.load`，只对 `xspj_cxXspjDisplay.html` 补 `gnmkdm`；
  ③按 `#tempGrid tr.jqgrow` 的文本（含隐藏列 id）找行 → `trigger('click')` → `jqGrid('setSelection', id, true)` 强制重载；
  ④状态栏报「共 N 个评价项」，0 项 / 空面板分别提示。
- 产物：`delivery/科信小助手-2.6.0-beta.1.apk`（5 453 690 B，SHA-256 `27fe5fc9…ef09976`，217 / 2.6.0-beta.1，预览渠道，
  用水版，v2/v3 验签通过，dex 内 `gnmkdm=N401605` 3 处、`jqgrow` 1 处；`source_sha256` 与工作区一致）。
- `test_core` 1418 / `test_build_config` 7 / `test_diag_privacy` 651 / `test_diag_files` 6650 /
  `test_webview_policy` 6 / `test_ui_contract` 291，全部通过。
- **仍未验证**：真机上的定位、渲染与提交（本轮无可用教务账号）。

## 四、验证状态（如实）

**已执行**：`test_core` 1418、`test_build_config` 7、`test_diag_privacy` 651、`test_diag_files`、`test_webview_policy` 6、`test_ui_contract` 291，全部通过；`javac --release 8` 全量编译通过；2.5.8 APK 已构建（5 420 922 B，SHA-256 `ac40b21d…10d806`，v2/v3 验签通过，dex 水控路由 0）；2.5.8.6 / 2.5.8.7 / 2.5.8.8 普通版 APK 已构建并通过 v2/v3 验签（2.5.8.8 的 dex 反查含 `cxJxzxjhxfyqFKcxx` / `planCourses` / `opensCourses`，2.5.8.7 三项均为 0）；真实账号端到端拉取学业数据成功（GPA 2.9875、37 节点 5 层、16 课组 94 门课）；计划外课组按学校 `jdkcsx` 分流端点后复验（人文科学 5 门、自然科学 6 门，与学校页"共 N 门"一致）。

**未执行**：诊断页真机流程（G6–G17：开关、复现、导出、轮转、崩溃重启、长日志、深色/大字号）；真机验收清单其余全部（含 UI 2.0 的快速连点、滚动恢复与旋转/分屏、返回手势、关动画、320dp）；2.5.8.6 真机覆盖安装与学业页回归；真实支付用水、MobSF、签名轮换、旧版升级兼容。详见 [`known-issues.md`](known-issues.md)。

## 五、接手第一步

1. `git status` + `git diff` 看未提交改动；**不要** `git checkout .` / `reset --hard`。
2. 读 [`architecture.md`](architecture.md) 与 [`modules.md`](modules.md)；按任务读专题文档。
3. 按 [`../AGENTS.md`](../AGENTS.md) 九步流程工作；跑 `python scripts/check_doc_sync.py` 得到文档提示。
4. 测试六入口全跑；未做的不要写「通过」。
5. 发版按 [`build-and-release.md`](build-and-release.md) 清单；真机验收未完成不得发布。
6. **提交与推送由用户确认**，Agent 不自行 commit / push / tag / Release。

## 六、文档索引

[架构](architecture.md) · [模块](modules.md) · [诊断日志](diagnostics.md) · [构建与发布](build-and-release.md) · [安全约束](security.md) · [UI 与导航](ui-navigation.md) · [数据接口](data-interfaces.md) · [已知问题](known-issues.md) · [安全审计：网络/SDK](security-audit-network-sdk.md) · [安全审计：支付/用水](security-audit-payment-water.md) · [真机验收](real-device-acceptance.md) · [整改验收](rectification-acceptance.md) · [返回手势](back-gesture.md) · [变更日志](../CHANGELOG.md)
