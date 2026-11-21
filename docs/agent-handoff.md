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
| 最新 tag / Release | `v2.5.9`（[Release](https://github.com/shynekoo/CampusKit/releases/tag/v2.5.9)，附 ASCII 资产 `kexin-xiaozhushou-2.5.9.apk` 与 `update.json`）；`preview` 预发布现为 **2.6.0-beta.17 / 234**（APK + `beta.json`）；Gitee 侧同版本（`gitee.com/sun-heyue/CampusKit`） |
| GitHub 登录 | `gh auth login` 已登录 `shynekoo`（ADMIN，repo scope）；按 AGENTS.md，push/tag/Release 仍需用户逐次确认 |
| 工作区 | `main` 干净；许可证迁移 `0392cea` 已推送，本次「公开仓库安全整改 + 2.6.0-beta.17」随该分支入库 |
| 核心断言 | 实测 **1554 项全过**（2026-11-21 删除充值签名器的 40 项断言）；UI 契约守卫 **388 锚点**；诊断隐私守卫 **717 项**；WebView 守卫 **5 处** |
| 发布状态 | **2.5.8 已发布**：清单、直链、下载站、App 内更新日志已同步；早期 2.5.8.x 开发包未单独发布 |
| 未发布开发版 | 无。**2.6.0-beta.17 / 234** 是当前预览包（`--beta --field-water`；许可迁移 MPL-2.0 + 修复 beta.16 上传未签名中间产物导致装不上） |
| 预览渠道 | `delivery/update/beta.json` 为 2.6.0-beta.17 / 234；**WorkBuddy 站点仍是 beta.5 / 222**（发布被 `SITES_ACCOUNT_MISMATCH` 拒绝，appId `wbapp_g8yY3kLcI8KmEUFM6vdn0h` 属另一账号）；GitHub 与 Gitee 的 `preview` 均已是 beta.17 / 234 |

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

## 三之十、学生评价：模块页必须在菜单壳里打开（2.6.0-beta.2，工作区改动、未构建未发版）

- **2.6.0-beta.1 的真机现象（用户截图 + 用户确认）**：能打开这门课的页面，状态栏一直停在「页面已打开，正在定位这门课…」，
  面板只有图例与「评价内容」「0% Complete」，**连表格都没有**；同时用户确认「用教务系统入口从学校菜单进去能正常评」。
- **根因**：`xspj_cxXspjIndex.html` 是**片段页**，被 `index_initMenu.html` 装进主文档运行。它自己与它加载的片段都裸用壳文档的全局：
  `_path`（拼每个请求地址）、`BaseJqGrid` / `loadJqGrid`（建表格）、`$.i18n`（每个列名）、`viewConfig` / `refershGrid`，
  没有一处 `window.` 或 `parent.` 前缀。作为顶层文档打开时 `$(function(){…})` 在 `BaseJqGrid` 处抛 ReferenceError → 整段中断 →
  表格不建、`onSelectRow` 不绑、面板停在骨架。beta.1 补 `gnmkdm` 的 `jQuery.fn.load` 包装因此从未被执行到。
- **改法**：①先 `loadUrl(EduClient.MENU_PAGE)`（Referer 站点根），再 `jQuery(host).load('/xspjgl/xspj_cxXspjIndex.html?gnmkdm=N401605')` 装进全屏宿主；
  ②`$.ajaxPrefilter` 给尚未带模块号的 `/xspjgl/` 请求补 `N401605`；③按 `getGridParam','data'` 的 `jxb_id` 匹配 →
  `setSelection(rowid, true)` → 补 `trigger('click')`；④成功判据改为 `.panel-pjdx` 节点数；⑤30 秒预算 + 六种失败文案；
  ⑥「最佳教师评选」跳转改在同一宿主内打开；⑦诊断新增 `EVAL` 模块与 4 个事件码。
- 测试：`test_core` 1418 / `test_build_config` 7 / `test_diag_privacy` 666 / `test_diag_files` 6650 /
  `test_webview_policy` 6 / `test_ui_contract` 297，六个入口全过；`javac --release 8` 全量编译通过。
- 产物：`delivery/科信小助手-2.6.0-beta.2.apk`（5 457 786 B，SHA-256 `274e7e30…154d27a8`，218 / 2.6.0-beta.2，预览渠道、用水版、v2/v3 验签通过）。
  `delivery/update/beta.json` 已指向它；**线上未部署**（`SITES_ACCOUNT_MISMATCH`，需切回原发布账号）。
- **仍未验证**：真机 E1–E4e（尤其：菜单壳是否被学校重定向、模块装进宿主后表格能否建起来、片段能否装载）。
- **证据留存**：`tmp/idx.js`（列表页脚本）、`tmp/disp.js`（片段脚本）是从学校页面抓下的原始脚本，`tmp/` 已被 gitignore，不随仓库分发。

## 三之十一、学生评价改成原生列表 + 原生答题页（2.6.0-beta.3，工作区改动）

- **为什么换做法**：前三版（2.5.10–2.6.0-beta.2）都是「把学校页面搬进 WebView」，定位、渲染、提交全依赖
  学校脚本按预期跑起来；真机三次失败（参数异常 → 系统升级维护 → 仍是 0 个评价项）。这一版不再打开网页。
- **核实过的事实**（不是推断）：
  - `xspj_cxXspjDisplay.html?gnmkdm=N401605` 返回的是**服务端渲染的 HTML 片段**，题目/选项/全部 id 都在里面；
    未登录访问 302 到登录页（端点存在），学校脚本 230 270 B 的 `xspj_display.js` 可匿名取到，与本地 `tmp/disp.js`
    **字节一致**，因此下面的选择器是当前线上版本，不是猜测。
  - 学校脚本读的 DOM：`div.panel-pjdx`（评价模板）→ `table.table-xspj`（一级指标）→ `tr.tr-xspj`（评价项，
    `data-pjzbxm_id / pfdjdmb_id / dtlx / sfbt / qzz`）→ 选项 `input.radio-pjf`（`data-pfdjdmxmb_id`，
    标签是外层 `div.input-xspj` 的文字）/ `input[type=checkbox]` / `select[name=pjf]`（十级制）；
    主观题 `textarea.input-zgpj`，评语 `textarea[name=py]`。
  - 提交参数由 `buildRequestMap()` 拼：`modelList[i].pjmbmcb_id / pjmbmc / pjdxdm / fxzgf / py / xspfb_id`，
    题目在 `modelList[i].xspjList[j].childXspjList[k].` 下（单选 `pfdjdmxmb_id`、十级制 `pjf`、主观题 `zgpj`），
    评语先 `encodeURIComponent` 再被表单编码（**双重编码**）。
  - 列表是 jqGrid，**会分页**；旧代码固定 `currentPage=1`，只读一页就会漏课（截图里 20 门未评）。
- **改法**：
  - 新增 `core/EduEvaluationForm`：`parse()` 读片段 → 模板/题目/选项/必填/已选；`body()` 按学校同构拼请求体
    （未作答不发字段，多选重复同名字段，故走字符串拼装而非 `Map`）；`outcome()`/`note()` 只按学校回话判断成败；
    识别不了的题型记 `UNSUPPORTED`。
  - `EduEvaluation.pages/rowCount/of`：分页与跨页去重。
  - `EduClient`：`evaluations()` 循环取页；新增 `evaluationForm(...)`（七个 id）、`evaluationWrite(body, submit)`、
    `fetchEncoded()`（已编码请求体，支持重复字段）；`checkPath()` 放行五个 `/xspjgl/` 端点（含暂存/提交）。
  - `EvaluationActivity` 重写：三格计数 + 四个筛选 chip + 卡片折叠动画（`MotionSpec.LIST_TOGGLE_DURATION`，
    连点先 cancel，关动画直接落终态）；打开答题页时把六个 id 一起带过去。
  - `EvaluationFormActivity` 重写为原生页：选项行/多选/输入框/评语框，必填 `*`、暂存/提交按钮、
    提交前必填校验 + `Theme.dialog` 二次确认、成功后返回列表重读；已提交只读；暂存过的回填本人上次选择。
  - 诊断：`EVAL` 事件改为 `sheet.start / sheet.ready / sheet.empty / sheet.failed / sheet.unsupported /
    write.sent / write.saved / write.failed`（只记阶段与题数）。
- 测试：`test_core` **1491** / `test_build_config` 7 / `test_diag_privacy` **672** / `test_diag_files` 6650 /
  `test_webview_policy` **5** / `test_ui_contract` **313**，全过；`javac --release 8` 通过；manifest `220 / 2.6.0-beta.3`。
- **仍未验证**：真实账号下的问卷渲染与暂存/提交（无可用账号）。提交一次性，必须按
  `docs/real-device-acceptance.md` E4c/E4d 在评教窗口内实测一次。

## 三之十二、学生评价卡片「点开就缩回」：高度写入把 WRAP_CONTENT 钳成 0（2.6.0-beta.4）

- **现象**（真机截图）：列表与分页正常（17 门全在），但点任一课程卡片的头部，卡片只是闪一下展开又立刻
  合上；右侧箭头停在 ▾（已展开），正文却一片空白。
- **根因**：`EvaluationActivity.height(View, int)` 里写的是 `params.height = Math.max(0, value)`。动画每帧
  传的是像素值，钳制无害；但折叠结束调 `settle(body, WRAP_CONTENT)`，`WRAP_CONTENT` 的值是 **-2**，
  被钳成 0 → 正文高度 0。于是「展开动画走完立刻塌回 0」，而 `card.open` 与箭头已经置为展开态。
  这也解释了为什么不是所有卡片都收不起来：只有被点过、跑完一次动画的那张会停在「箭头已展开、高度 0」。
- **修法**：`height()` 原样写回（负值就是 `WRAP_CONTENT` / `MATCH_PARENT` 的语义，像素值无需钳制）；
  `settle()` 的两条终态（0 / `WRAP_CONTENT`）因此都成立。`test_ui_contract.py` 增加防回退守卫，
  禁止折叠高度写入再出现 `Math.max(0, …)` 形式（锚点 313 → 314）。
- **对照**：`ChangelogActivity` 的同类折叠一直正常，它的 `height(Card, int)` 就是直接 `params.height = pixels`，
  没有钳制——这也是本次判断的旁证。
- 测试：`test_core` 1491 / `test_build_config` 7 / `test_diag_privacy` 672 / `test_diag_files` 6650 /
  `test_webview_policy` 5 / `test_ui_contract` **314**，全过；`javac --release 8` 通过。
- **仍需真机**：E2（展开/收起、连点不抢动画）；其余链路（答题、暂存、提交）与 beta.3 相同，未实测。

## 三之十三、问卷请求被回「参数异常」：这组 id 必须同时进查询串（2.6.0-beta.6）

- **现象**（真机）：2.6.0-beta.5 上卡片已能展开，点「开始评价」进入原生答题页，页面显示
  「学校没有下发评价项：课程号ID,教学班ID,教职工ID参数异常！」。
- **判断依据**：列表就是靠 `jxb_id / kch_id / jgh_id` 渲染出来的，这三项不可能是空的；学校这句
  提示的真实含义是「请求里的参数表是空的」。
- **根因**：原生版本只把参数放进了请求体。学校页面自己用 `$(panel_content).load(url, data)`
  （data 进请求体），但它的过滤器仍从**查询串**取一份；只给请求体就读到空参数表。2.5.10 的真机
  是同一句报错，2.5.11 的修法正是把同一组参数也拼进查询串——原生重写时漏掉了这条经验。
- **修法**：`evaluationForm()` 里 `SmsLoginProtocol.form(form)` 生成查询串，URL 变成
  `EVAL_FORM + "&" + query`，请求体照旧；新增 `refusal(html)` 识别「参数异常 / 模块维护 / 已放弃」
  三种拒绝页并给对应提示（此前一律显示「没有下发评价项」，把责任推给了学校）；三个 id 先判空。
- **守卫**：ui_contract 增三条防回退锚点（双写、`refusal` 必须存在、三个 id 先判空），323 → 326。
- 测试：core 1505 / build_config 7 / diag_privacy 672 / diag_files 6650 / webview_policy 5 /
  ui_contract **326**，全过；`javac --release 8` 通过。
- **未验证**：题目是否真的渲染出来（E4）；提交/暂存仍无真机记录。

## 三之十四、更新渠道增加 Gitee（2.6.0-beta.7，工作区改动、未构建未发版）

- **起因**：真机把更新渠道切到 GitHub 后能检查到新版本，但 APK 下载慢到不可用。GitHub 的
  release CDN 在境内本来就慢，换配置解决不了，只能增加一个境内分发点。
- **实测（同一网络、同一时段）**：WorkBuddy 托管 ≈ 4 MB/s；Gitee 发布附件 ≈ 0.7 MB/s（9.1 MB
  文件 7.2 s）；GitHub release CDN 在境内常为数十 KB/s 或直接超时。结论：Gitee 是「稳定可达」，
  不是最快，所以 WorkBuddy 仍是默认渠道。
- **Gitee 能否免登录下载**（关键前提，已实测）：公开仓库的发布附件可以。链路是
  `gitee.com/<owner>/<repo>/releases/download/<tag>/<file>` → 302 →
  `gitee.com/<owner>/<repo>/attach_files/<id>/download/<file>` → 302 →
  `foruda.gitee.com/attach_file/<id>/<file>?token=…&ts=…` → 200，全程无登录态。私有仓库不行。
- **代码**：`UpdateSources` 扩成三渠道，新增 `KEYS` / `keyOf(int)` / `indexOf(String)` /
  `normalize(String)`；`manifests()` 多一个 `gitee` 分支；`MoreActivity.sourceSetting` 的三项名称、
  当前值、Toast 全取自 `UpdateSources`，页面不再有硬编码渠道分支；`UpdateCheck.setSource/source`
  落盘与读取都过 `normalize`，未知值回落 WorkBuddy。
- **两个固定清单**：正式 `releases/download/release/update.json`、预览
  `releases/download/preview/beta.json`。Gitee 没有 `latest/download` 路由，正式清单也只能挂固定
  tag，每次发版替换 tag 内同名资产（与 GitHub 的 `preview` tag 做法一致）。
- **仓库常量**：暂定 `gitee.com/shynekoo/CampusKit`（与 GitHub 同名）。Gitee 用户名不同就只改这两行常量。
- **守卫**：ui_contract 增「三渠道顺序 / 每个渠道有落盘键 / Gitee 固定 tag 地址 / 页面渠道名取自
  core / 未知值归一」，326 → 331；core 增三渠道清单与 Gitee 两跳 302 的断言，1505 → 1518。
- **未验证**：Gitee 仓库还没建、资产还没上传，所以 App 内实际拉取一次都没跑过。真机步骤见
  `docs/real-device-acceptance.md` 的 B0c。

## 三之十五、评教仍回「参数异常」：三种载体依次试（2.6.0-beta.7）

- **现象**：2.6.0-beta.6 已经双写（查询串 + 请求体），真机仍显示「学校说这次请求的参数不对」。
  这句来自本项目自己的 `refusal()`，说明**拒绝页分型生效了、双写没有解决问题**——两者要分开看，
  不能因为页面上出现了新文案就当修好了。
- **三个互斥的假设**：① 学校过滤器只读请求体（网页是 `$(panel_content).load(url, data)`，
  即 POST + 仅请求体）；② 只读查询串（beta.6 的假设）；③ 空值参数被当成「给了但错」。
  没有账号无法逐条验证，所以一次把三种都试掉。
- **修法**：`evaluationForm()` 依次试 `POST 仅请求体` → `POST 请求体+查询串` → `GET 仅查询串`，
  第一次不被判拒绝就采用并返回；三种都被拒时抛学校原话。
- **空值不发**：新增 `putIfPresent()`，`xsdm / tjzt / pjmbmcb_id / sfcjlrjs` 为空时根本不进参数表。
- **可观测**：生效的形态写 `EVAL / sheet.shape`（字段 `form`，词条「评价表单换了一种请求形态」）。
  这条是本轮唯一能自我定位的手段：下次再失败，诊断里能看出三种都试过且都被拒。
- **守卫**：ui_contract 的「双写」断言改成「三形态顺序 + 空值不发 + 生效形态记诊断」，331 → 336；
  diag_privacy 新增 `form` 字段与 `sheet.shape` 词条，672 → 681。
- **未验证**：哪一种是学校接受的（E4 仍挂在真机上）。

## 三之十四、评价页被自己判成「被拒」：先把顺序倒过来（2.6.0-beta.8）

- **现场**：2.6.0-beta.7 真机日志里，`xspj_cxXspjDisplay.html` 连发三次（POST 仅请求体 / POST
  请求体+查询串 / GET 查询串），三次都是 **200**，`duration` 都在 160–175 ms，三次都被记成
  「被拒」，随后 `sheet.failed`。界面显示「学校说这次请求的参数不对」。
- **推断的关键**：第一种形态与学校自己页面发出的请求逐字相同
  （`load("/xspjgl/xspj_cxXspjDisplay.html", {jxb_id, kch_id, xsdm, jgh_id, tjzt, pjmbmcb_id,
  sfcjlrjs})`，见线上 `xspj_index.js` 的 `onSelectRow`）。它不可能被学校拒。所以要么回包不是
  拒绝，要么判定本身错了。
- **真正的错**：`evaluationForm` 先调 `refusal(html)`（`html.contains("参数异常")`），命中就丢弃
  整个回包，再去试下一种形态。判断权交给了一个子串——那句话只要出现在正常表单的脚本或提示模板
  里，好页面就被自己挡在门外，三条路一次堵死。
- **修法**：`looksLikeSheet(html)` 先认结构，四个标记即解析器所用的四个选择器
  （`div.xspj-body` / `panel-pjdx` / `tr-xspj` / `input-xspj`；线上 `xspj_display.js` 里分别出现
  9 / 28 / 36 / 12 次）。命中即 `return html`，由 `EduEvaluationForm.parse` 去判断有没有题目；
  只有在**没有任何表结构**时才去读拒绝语，并把回包大小附在同一句提示里。
- **顺带修的两个自伤**：
  1. 第一种请求改成与浏览器逐字一致——新增 `EVAL_DISPLAY`
     （`/xspjgl/xspj_cxXspjDisplay.html`，无查询串），去掉自加的 `gnmkdm` 与 `nd` 时间戳。
  2. `sheet.shape` 的形态值原先是中文（「请求体」），诊断白名单只放行 `[A-Za-z0-9_.:+-]`，于是
     三条全被脱敏写成 `form=OTHER`——日志看着「什么都没有」，信息其实是在路上被自己擦掉的。
     现改为 `body` / `body+query` / `query`，并加 `bytes` 记录回包字节数。
- **验证**：`test_ui_contract` 336 → **339**（结构优先、逐字对齐、`bytes` 入日志、失败提示带大小）；
  `test_core` 1518 不变；其余守卫不变。产物 2.6.0-beta.8 / 225，SHA-256
  `f4569dc6…8e325796`，dex 内可见 `looksLikeSheet` / `EVAL_DISPLAY` / `xspj-body`。
- **未验证**：这张表到底是正常表单还是真的拒绝页。本次只做到「不再替它下结论」；真机若仍失败，
  提示语里的 KB 数会直接说明回的是什么（2 KB 量级＝提示页，数十 KB＝表单）。

## 三之十六、Gitee 渠道接通：仓库不存在，选项等于空（2.6.0-beta.9）

- **现象**：beta.7/beta.8 里「更新渠道」有 Gitee 一项，选中后手动检查只得到「暂时查不到新版本」。
- **原因**：两个地址写的是 `gitee.com/shynekoo/CampusKit`，当时是照 GitHub 账号名猜的。实测
  `gitee.com/shynekoo` 本身 **404**（对照 `gitee.com/droidvm` 200），仓库自然也不存在，
  `update.json` / `beta.json` 全 404；`UpdateCheck` 把读不到当成「该渠道没有内容」静默跳过。
- **落地**：真实公开仓库 **`gitee.com/sun-heyue/CampusKit`**，两个固定 tag `release` / `preview`；
  `UpdateSources.GITEE_RELEASE` / `GITEE_BETA` 改到该仓库；README 说明两个 tag 的用途与清单字段。
- **建仓踩的坑**：`POST /user/repos` 传 `private: false`，仓库仍落成 **私有** → 匿名读附件
  403（`{"status":403,"data":null,"message":"403 Forbidden"}`）。改可见性要
  `PATCH /repos/{owner}/{repo}` **同时带 `name`**（只传 `private` 会回 `name is missing`）。
  这条写进了 `UpdateSources` 类注释与 `build-and-release.md`：发布前从仓库页确认 public，
  不能相信建仓接口的回显。
- **上传附件两个坑**（与 GitHub 同源）：① 附件名取**本地文件名**，`update-gitee.json`
  会原样上传，必须先把文件命名成 `update.json` 再传；② 同名重传要先 `DELETE
  /releases/{id}/attach_files/{attach_id}`（204），否则会留两份。
- **实测（不带令牌）**：`beta.json` 200 / 1169 B → 226；APK 200 / 5,465,978 B，SHA-256
  `11cbeab8…0baf5b73` 与清单一致；速度 0.83–0.95 MB/s。跳转链
  `download/…` → `attach_files/…` → `foruda.gitee.com/attach_file/…?token&ts`，与
  `CoreTests` 的 Gitee 两跳夹具一致。
- **守卫**：`test_core` 1518 → **1520**（新增「Gitee 地址的 owner 必须是真实仓库」两条）；
  重定向夹具改用 `UpdateSources.GITEE_RELEASE` 常量，不再手抄地址。
- **未验证**：App 内真机走 Gitee 下载安装（B0c）。

## 三之十七、评教接口按真实抓包重做（2.6.0-beta.10）

- **做法变了**：这次没有改文案、也没有再加一种请求组合。用实测账号（某专业课 · 任课教师）登录学校教务，
  把官方 `xspj_index.js` 的 `$(panel_content).load("/xspjgl/xspj_cxXspjDisplay.html", {七项})` 逐项复现，
  一次一种形态，记录状态码、字节数与页面种类。
- **实测矩阵**：

  | 形态 | 学校回应 |
  | :-- | :-- |
  | `?gnmkdm=N401605` + 七项只在请求体（官方） | **200 / 46,234 B / 1 面板 11 题** |
  | 同上 + 查询串再拼一份 id | 200 / 6,923 B，页面写「参数异常」 |
  | 不带 `gnmkdm` | HTTP **911** / 392 B，正文是一段页面模型 JSON |
  | 不带 `X-Requested-With` | 200 / 46,234 B（这个头不是必需的） |
  | 截断 `jgh_id` / 空 `jxb_id` | 200 / 6,923 B，「参数异常」 |

- **两个真正的 bug**，都不是「id 不对」：
  1. `EduClient.evaluationForm` 的第三种形态把同一组 id 也拼进了查询串——学校把这当成参数异常。
     beta.6 的「双写」修复、beta.7 的「三形态」修复都在这一点上打转。
  2. `EduEvaluationForm.parse` 按标签嵌套切面板，而线上片段不闭合 `<div>`（63 开 / 73 闭），
     面板在第二题处就被判结束：**11 道题读成 2 道**，界面于是说「学校没有下发评价项」。
- **请求体对不上**：`body()` 只发答案字段，缺 `childXspjList[k].pjzbxm_id / pfdjdmb_id / zsmbmcb_id`、
  缺表级 `xspjList[j].pjzbxm_id`、缺 `modelList[i].pjzt`。补齐后与官方 `buildRequestMap()` 对比 60 个字段，
  除按钮决定的 `tjzt` 外键值全同。
- **写操作实测（草稿）**：把客户端自己拼的 body POST 到 `xspj_bcXspj.html`，学校回 `"暂存成功！"`（7 字节
  JSON 字符串）；回读片段时 10 个单选被选中、主观题文字保留；列表该行 `tjzt` 由 `-1` 变 `0`、
  `bfzpf=84.00`，评价情况 `{"wpgs":19,"bcgs":1,"tjgs":0}`。
- **成败判定与核对**：`outcome()` 先去 JSON 引号；否定词先判（「提交不成功」含「成功」）；只认学校自己的
  短语；「操作成功」按不清楚处理。写完回读 `tjzt`，`1` 才算已提交，`0` 明说「收了但没记成已评」，
  读不到记 `write.unverified`。超时/断网不重发。
- **守卫**：`test_core` 1520 → **1539**（三层 id、`pjzt`、不闭合片段 5 题、`outcome` 八种回话）；
  `test_ui_contract` 339 → **354 → 371 → 374 → 377**（只发一次、禁止回退矩阵、平铺切片、三层 id、状态核对）；
  `test_diag_privacy` 679 → **691**。
- **未验证**：真实提交（`tjzt=1`）与提交后状态同步——按约定要用户本人确认后才做，本轮没有代提交。

## 三之十八、beta.10 的分发：一份清单服务不了三个渠道

- **症状**：APK 已经放在 `delivery/update/`、`beta.json` 也写好了，装上 beta.9 的手机点「检查更新」仍说已是最新。
- **原因**：客户端只比较清单里的 `versionCode`，而三条线上清单都没更新——WorkBuddy 站点还停在 `beta.5` / 222
  （`SITES_ACCOUNT_MISMATCH`，`delivery/update` 绑定的应用 `wbapp_g8yY3kLcI8KmEUFM6vdn0h` 属于另一个账号），
  GitHub `preview` 与 Gitee `preview` 仍是 `beta.9` / 226（只建了包，没上传资产）。
  另一层：预览清单只在用户打开「接收预览版更新」后才读，开关关着时连发布正确的 beta 也看不到。
- **清单按渠道各写一份**，只有 `url` 不同（三份同 `bytes` / `sha256` / `notes`）：
  `beta.json`（WorkBuddy，带 `sharecode`）、`beta-github.json`、`beta-gitee.json`。上传前把对应变体复制成
  名为 `beta.json` 的文件，因为 GitHub / Gitee 侧的固定资产名就是 `beta.json`。
- **Gitee 附件必须带 `release_id` 删除**：`DELETE /releases/{release_id}/attach_files/{id}` 返回 `204`；
  漏掉 `release_id` 的路径返回 `404`，会让人误以为「附件删不掉、只能改名」，那又会把清单地址打歪。
- **已发**：GitHub `preview`（`beta.json` 1,352 B，`campuskit-2.6.0-beta.10.apk` 5,470,074 B）与 Gitee `preview`
  （同两件，直链实测 `200` / 5,470,074 B / SHA-256 `c7e5cca4…1e69c83c`）。**WorkBuddy 站点仍未发布**，等切回原发布账号补 `beta.json`。

## 三之十九、答题页反馈改成飘字 + 写成功自动返回（2.6.0-beta.13）

- 之前「没填完」「已暂存」「已提交」都是 `status` 状态栏文字，用户要**飘字**：新增 `toast(String)`
  （`Toast.makeText(this, …, LENGTH_SHORT).show()`）；「一个选项都没选」与「必填项没答全」统一成
  `toast("请完成所有题目后再提交")`，`blockers()` 从此只管「不支持题型」这一种硬阻断。
- 写成功后 `toastAndLeave(submit ? "已完成提交" : "已暂存")`：先飘字，600ms 后 `finish()`，
  且只在 `!isFinishing() && !isDestroyed()` 时返回。Toast 是系统窗口，`finish()` 后仍显示完，
  列表在 `onResume` 重读，刚写下的状态立刻可见。**失败 / UNCLEAR 不返回**，保留页面内说明供重试。
- 承接 beta.12 的 id 修复：列表解析里显示用的 `text()` 会把 `jgh_id` 截到 80 字符，线上实测 256 字符，
  截断即触发学校 200/6,923 B「参数异常」；改成 id 字段整串保留。真机 A/B 三轮已验证。
- 产物 `2.6.0-beta.13`（versionCode 230，5,478,266 B，`a41078b9…f78220e2fca5`）已发 GitHub + Gitee
  `preview`；WorkBuddy 站点仍 `SITES_ACCOUNT_MISMATCH`，未发布。测试：core 1587 / ui_contract 371 /
  diag_privacy 716 / analytics 77+18，全过。

## 三之二十、提交后回读 id 与已评回显（2.6.0-beta.14）

- **真机症状**：确认提交后没有飘字「已完成提交」、也没返回列表；在「已评」里点开这份评价，题目在但没有当时选的答案。
- **根因一（提交判定）**：`EduClient.evaluationState()` 拿表单 HTML 的 `data-jgh_id` 去列表里比对，
  而学校给列表行的 `jgh_id` 是另一套 256 字符值——同一会话里两者 239 位不同，长度都是 256。
  于是提交成功后回读仍 `UNKNOWN`，被判「状态未变」，不飘字不返回。改为从 intent 传来的列表
  `EXTRA_CLASS` / `EXTRA_TEACHER_ID` 回读。
- **根因二（已评回显）**：已提交行的 HTML 不含 radio / checked / textarea，答案是
  `<td><span>良好&nbsp;</span></td>`（选择）或 `<div class="input-xspj">主观文字</div>`。
  parser 新增 `SHOWN`（只读展示题）：从第二个 `td` 取文本，标 `readOnly`，UI 回显成一行文字；
  `supported()` 为真以免被误报「不支持题型」，但 `body()` 永不写它的答案字段。
- **实测**：真实账号已评行 4 条，各回 200/约 22 KB、1 面板 11 题；修复前 parser 读出 11 题全部
  `kind=0` / `options=0`，修复后 11 题全部 `SHOWN` / 1 个选项（10 项「良好」+ 主观题「挺好」）。
- 测试：core 1594 / ui_contract 374 / diag_privacy 716 / analytics 77+18 全过。

## 三之二十一、提交成功判定与界面两处调整（2.6.0-beta.15）

- **真机症状**：确认提交后依旧没有「已完成提交」飘字，也没有返回列表——beta.14 修的回读 id
  不是这一条的根因。
- **根因（代码路径确定）**：`send()` 里先从学校回话判 `outcome = OK`（「提交成功」），随后
  `evaluationState()` 回读列表；老代码在回读不是 SUBMITTED 时执行
  `else if (outcome == OK) outcome = UNCLEAR;`。列表 tjzt 是异步写的，多页查询/服务端延迟/
  网络抖动都会让这一下读到 UNKNOWN，成功就被吞成 UNCLEAR，`done()` 只在 OK 时
  `toastAndLeave()`，于是真机上什么也不发生。
- **修复**：回话明确成功即生效；回读只作旁证和诊断——只有 `state == SAVED`（回话说成功但学校
  只记了暂存）才降为 UNCLEAR，`SUBMITTED` 仍确认 OK，`UNKNOWN` 保留回话判定。诊断加
  `status=<回读状态>`（白名单字段），下次真机可直接看出卡在哪一步。
- 「更多」页移除匿名使用统计开关的调用；`analyticsSetting` 方法与隐私页说明保留，能力未删。
- 更新渠道首个显示名 WorkBuddy → **国内**；`KEYS[0]` 仍是 `workbuddy`，存量偏好不变。
- 测试：core 1594 / ui_contract 377 / diag_privacy 717 / analytics 77+18 全过。
  **提交成功路径仍是静态 + 代码路径证据，未代用户真实提交**。

## 三之二十二、开源协议迁移 MIT → MPL-2.0（2026-11-21，工作区改动、未提交）

- 根目录 `LICENSE` 换为 MPL-2.0 官方全文（16,726 B，未改动）；GitHub 徽章随之变为 MPL-2.0。
- 自有源码首次加 `SPDX-License-Identifier: MPL-2.0`：191 个文件（`client/android/src`、`client/core/src`、
  `client/core/test`、`client/androidTest/src`、`client/fieldtest/src`、`client/fieldtestTest/src`、
  `client/android/res/raw/*.js`、`scripts/*.{py,java,js}`、`server/analytics-worker/{src,test}`）各加一行注释，无逻辑改动。
- App 内：「更多 → 查看开源许可证」改为「**开源许可**」（副标题 `MPL-2.0`），弹窗读
  `client/android/res/raw/mpl_license.txt`（与根目录 `LICENSE` 同一份），仍离线、可选中、可滚动；
  读取失败回落为协议名 + 官方地址。第三方组件提示仍指向 `client/THIRD_PARTY.md`，不改标为 MPL。
- 弹窗正文之上先给「本机版本号 + 源码地址 + 本版本对应源码见同版本号 tag（预览版 `preview`）」，取自
  `AppUi.version(this)` 与 `REPO` 常量；`AGENTS.md` 第 6 节与 `build-and-release.md` 发布清单同步要求
  Release 说明写明该版本源码地址（MPL 3.2 的可执行形式源码告知义务）。
- 文档：新增 `docs/licensing.md`；`README.md` 开源协议与免责声明重写（删掉与 MPL 冲突的「仅供学习、不得商用」）；
  `client/THIRD_PARTY.md` 重排为组件总表；`AGENTS.md` 新增第 6 节「许可证维护」；历史 MIT 原文存档
  `docs/legacy/LICENSE-MIT.txt`（不在根目录，不会被 GitHub 误判为当前许可证）。
- 未做：**未提交、未推送、未出包、未真机验证**弹窗滚动与深色模式；`delivery/` 里历史 release 说明保持原样。

## 三之二十三、预览包 2.6.0-beta.17 与 beta.16 装不上的事故（2026-11-21）

- **事故**：beta.16 / 233 上传的是 `client/build/preview/aligned.apk`——签名**前**的中间产物，用户安装报
  「解析软件包时出现问题」。同一次构建还漏了 `--field-water`，包里没有 `WaterActivity` / `LifeWaterActivity`。
- **处置**：坏包从 GitHub（`gh release delete-asset`）与 Gitee（`DELETE …/attach_files/{id}`）撤下，
  `delivery/update/` 内副本删除；233 作废不复用。
- **重出**：`python scripts/build_preview.py --beta --field-water --version-code 234` → `2.6.0-beta.17` /
  234，`delivery/科信小助手-2.6.0-beta.17.apk` 5,486,527 B，sha256 `cab737846bc59da4…101723`。
  签名 v2+v3（证书 CN=QZXY Local Preview，`13b96892…`，与历史各版一致）、zipalign OK、两个用水 Activity 在、
  `res/raw/mpl_license.txt` 16,726 B 在。GitHub 与 Gitee 都已更新，线上 `beta.json` 读到 234；
  Gitee 下载回来复核 sha256 且 `apksigner verify` 通过。
- **防复发**：构建脚本中间产物改名 `unsigned-aligned.apk`；`AGENTS.md` §5 与 `build-and-release.md` 第 7 步
  写明「只签发签名产物 / 预览必带 `--field-water` / 上传前 `apksigner verify` 见 `Verifies`」。
- **仍未验证**：真机安装与「开源许可」弹窗的滚动 / 深色模式 / 小屏。

## 四、验证状态（如实）

**已执行**：`test_core` **1539**、`test_build_config` 7、`test_diag_privacy` **691**、`test_diag_files`、`test_webview_policy` 5、`test_ui_contract` **371**，全部通过；2.6.0-beta.10 APK 已构建（5,470,074 B，`versionCode` 227，SHA-256 `c7e5cca4…1e69c83c`）并已发到 GitHub / Gitee 的 `preview`（Gitee 直链复验 200）；2.6.0-beta.9 APK（5,465,978 B，SHA-256 `11cbeab8…0baf5b73`）；Gitee 两个固定 tag 的清单与 APK 已匿名复验；`javac --release 8` 全量编译通过；2.5.8 APK 已构建（5 420 922 B，SHA-256 `ac40b21d…10d806`，v2/v3 验签通过，dex 水控路由 0）；2.5.8.6 / 2.5.8.7 / 2.5.8.8 普通版 APK 已构建并通过 v2/v3 验签（2.5.8.8 的 dex 反查含 `cxJxzxjhxfyqFKcxx` / `planCourses` / `opensCourses`，2.5.8.7 三项均为 0）；实测账号端到端拉取学业数据成功（GPA 与学分树完整解析；37 节点 5 层、16 课组 94 门课，具体成绩不记录）；计划外课组按学校 `jdkcsx` 分流端点后复验（人文科学 5 门、自然科学 6 门，与学校页"共 N 门"一致）。

**未执行**：诊断页真机流程（G6–G17：开关、复现、导出、轮转、崩溃重启、长日志、深色/大字号）；真机验收清单其余全部（含 UI 2.0 的快速连点、滚动恢复与旋转/分屏、返回手势、关动画、320dp）；2.5.8.6 真机覆盖安装与学业页回归；真实支付用水、MobSF、签名轮换、旧版升级兼容。详见 [`known-issues.md`](known-issues.md)。

## 五、接手第一步

1. `git status` + `git diff` 看未提交改动；**不要** `git checkout .` / `reset --hard`。
2. 读 [`architecture.md`](architecture.md) 与 [`modules.md`](modules.md)；按任务读专题文档。
3. 按 [`../AGENTS.md`](../AGENTS.md) 九步流程工作；跑 `python scripts/check_doc_sync.py` 得到文档提示。
4. 测试六入口全跑；未做的不要写「通过」。
5. 发版按 [`build-and-release.md`](build-and-release.md) 清单；真机验收未完成不得发布。
6. **提交与推送由用户确认**，Agent 不自行 commit / push / tag / Release。

## 六、文档索引

[架构](architecture.md) · [模块](modules.md) · [诊断日志](diagnostics.md) · [构建与发布](build-and-release.md) · [安全约束](security.md) · [UI 与导航](ui-navigation.md) · [数据接口](data-interfaces.md) · [开源许可](licensing.md) · [已知问题](known-issues.md) · [安全审计：网络/SDK](security-audit-network-sdk.md) · [安全审计：支付/用水](security-audit-payment-water.md) · [真机验收](real-device-acceptance.md) · [整改验收](rectification-acceptance.md) · [返回手势](back-gesture.md) · [变更日志](../CHANGELOG.md)
