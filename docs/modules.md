# 模块清单

按实际 source set 与文件名整理（`rglob('*.java')` 实测：core 56、android 83、fieldtest 7、core 测试 1）。完整类清单可直接看目录，这里按职责分组。

## client/core — 平台无关规则（56 个文件，JVM 可测）

| 组 | 类 | 职责 |
| :-- | :-- | :-- |
| 导航/状态 | `MainNavigator`、`RefreshDecision`、`TodayDynamic`、`ClockRedraw`、`SessionCheck` | tab 返回栈、今日就地刷新判定、会话检查去抖与错误隔离 |
| 课程模型 | `Course`、`CourseJsonMapper`、`TimetableIndex`、`BoardFit`、`TimetablePalette`、`TermDate`、`ClassTimes`(android 侧) | 课程无损映射、周次索引、屏幕自适应、调色板、开学日期 |
| 课表解析 | `TimetablePdfParser`、`TimetableWebCourseParser`、`EduTableColumns`、`EduTimetable` | 教务网页表格与 PDF 表格解析 |
| 登录/会话 | `AccountSession`、`LoginCoordinator`、`LoginPersistence`、`SessionCipher`、`SmsLoginProtocol`、`EduLoginProtocol`、`EduSite` | 登录状态、凭据加密、协议字段与 RSA |
| 设备/用水 | `DeviceProfile`、`DeviceQr`、`Life798Qr`、`WaterChannel`、`WaterEvents`、`WaterOrder`、`UseSession`、`PendingUse`、`FieldStartGate`、`FieldTestGuard`、`Money` | 二维码、用水状态机、待核实、金额、field 边界 |
| 使用码 | `UseCode` | 8 位使用码（5+3）解析与校验 |
| 更新 | `UpdateManifest`、`UpdateUrls`、`ReleaseVersion`、`UpdateChannel` | 更新清单必填字段校验、HTTPS 逐跳重定向策略、版本名规范（`X.Y.Z` / `X.Y.Z-beta.N` / 四段兼容）与双渠道挑选（按 `versionCode` 取大） |
| 安全策略 | `TrustedUrlPolicy`、`SnapshotPolicy`、`DiagPolicy` | WebView 准入、敏感页快照、诊断默认关 |
| 诊断 | `DiagRedact`、`DiagEvent`、`DiagCatalog` | 字段白名单与脱敏、行格式与解析、事件中文文案 |
| 匿名统计 | `AnalyticsDay`、`AnalyticsRequest`、`AnalyticsReply` | 北京时间自然日（无 java.time）、两字段请求体与形状校验（UUID/版本号/上限）、应答四判定（记上/重复/拒绝/重试） |
| 教务学业 | `EduAcademic` | 学生学业情况：GPA/统计/去重学分树解析（含 `appendTo` 层级还原与 `jdkcsx` 端点分流），课程明细 JSON 解析（`parseCourses`） |
| 教务评价 | `EduEvaluation`、`EduEvaluationForm` | 列表与统计解析：教学班/课程/教师/学时类型、状态码 `-1/0/1` 归一（`stateOf`）、`withState` 分组、`counts`、分页（`pageCount`/`rowCount`/`of`）；问卷片段按学校 `xspj_display.js` 的选择器读成题目/选项（`parse`，切片按**兄弟边界**——线上片段 `<div>` 不闭合，按嵌套切会把 11 题读成 2 题），并把作答还原成学校那套 `modelList[i].xspjList[j].childXspjList[k]` 请求体（每行必带 `pjzbxm_id`/`pfdjdmb_id`/`zsmbmcb_id`，表级再带一个 `pjzbxm_id`，并按必填项是否全答写 `pjzt`）；成败先按学校回话严格分类（`outcome`），最终以回读的 `tjzt` 为准 |
| 其他 | `DailyQuote`、`AlipayWaterLink`、`PickupCodeLinks` | 文案、跳转链接、协议辅助 |

测试：`client/core/test/.../CoreTests.java` —— 当前工作区实跑 1554 项断言（含 `jdkcsx` 课程端点分流、诊断脱敏与统计的日期/请求体/应答判定），涵盖上述状态机、解析与策略。

## client/android — 原生界面与平台适配（83 个文件）

| 组 | 代表文件 | 职责 |
| :-- | :-- | :-- |
| 主屏 | `TimetableActivity`、`MainShell`、`AnimatedNavBar`、`MotionSpec`、`UiTokens`、`App`、`Page`、`CardLayer`、`AppUi`、`Theme` | 三 tab 主屏、壳层与 tab 指示器动画（含 reduce-motion 降级）、UI 尺寸 token、页面基类/返回手势、主题下发、卡片动画 |
| 课表 UI | `WeekBoard`、`CourseAdapter`、`CoursePickers`、`CourseWeeks`、`ScheduleInfo`、`ScheduleWidget*`(3 个) | 周视图、课程编辑、桌面组件 |
| 诊断 | `Diag`、`DiagFiles`、`DiagActivity`、`DiagReportActivity` | 事件 API 与开关、崩溃兜底、文件轮转、筛选/导出页、故障报告页 |
| 账号与凭据 | `AccountActivity`、`AccountVault`、`EduVault`、`LifeSessionVault`、`AccountProfile`、`AccountInfoActivity` | 登录入口、加密存储、账号资料 |
| 教务 | `EduClient`、`EduLoginActivity`、`EduQueryActivity`、`EduResultActivity`、`EduPortalActivity`、`EduTimetableImportActivity`、`EduTables`、`EduTimetableReader`、`AcademicActivity`、`AcademicStatusActivity`、`EvaluationActivity`、`EvaluationFormActivity` | 教务会话、查询、导入、学业情况页（学分树 + 点击展开课程明细）、学业页与表单页（原生列表读状态；表单页把学校模块装进菜单壳宿主，评分与提交在学校页面上完成） |
| 慧生活/水控 | `LifeLoginActivity`、`LifeDeviceActivity`、`LifeDeviceApi`、`LifeAuthApi`、`LifeDeviceProfile`、`DeviceDetails`、`DeviceLookup`、`ScanActivity`、`QrDecoder`、`WaterApi` | 登录、设备资料（只读）、扫码、订单状态 |
| 趣智校园 | `SmsAccountApi`、`UseCodeApi`、`UseCodeActivity`、`QzxyAlipayActivity`、`AlipayLogin` | 使用码、支付宝授权登录；充值/收银不在应用内，只把设备交给支付宝 |
| 匿名统计 | `AnalyticsPreferences`、`AnalyticsReporter` | 同意状态（默认关）、本机随机 UUID（只在同意后生成）、每自然日一次上报、进入前台触发、关闭时删除历史记录；不读设备标识、不进日志 |
| 更新/隐私/其他 | `UpdateCheck`、`AppChangelog`、`ChangelogActivity`、`ApkProvider`、`PrivacyActivity`、`NoticeActivity`、`LegalText`、`LegalView`、`NoticeDialog`、`FeedbackActivity`、`MoreActivity`、`AppearanceActivity`、`SchoolSiteActivity`、`TimetableWebActivity`、`WebBack`、`HttpForm`、`ColorDialog` | 应用内更新（正式 + 预览两条清单，开关在「更多」页、状态存 `update-v1` 偏好）、更新日志（`AppChangelog` 内置数据 + `ChangelogActivity` 展开式卡片页，离线、无网络）、数据与隐私页、使用公告（页面 + 首次启动弹窗，共用 `LegalText` 文案与 `LegalView` 排版）、外观、网页容器、网络与弹窗 |

## client/fieldtest — 用水控制（7 个文件，`--field-water` 时整组编入）

`WaterActivity`、`WaterControl`、`LifeWaterActivity`、`LifeWaterProtocol`、`LifeProtocolProbe`、`LifeVersion`、`LifeDiagnostics`。**正式版按 `--field-water` 构建，因此这 7 个文件全部在正式包内**（扫码跳转支付宝依赖其中的 `WaterActivity` / `LifeWaterActivity`）；不带该开关的只读构建才不含整组。构建脚本对控制路由与两个 dex 类型描述符做出现/缺失双向断言。

`LifeProtocolProbe` 是实验性入口，会向设备发一次带控制效果的开启请求，已列入待整改项，见 [`known-issues.md`](known-issues.md)。

## 测试入口

| 脚本 | 检查内容 |
| :-- | :-- |
| `scripts/test_core.py` | core 源码 JVM 编译 + `CoreTests`（当前工作区实跑 1554 项） |
| `scripts/test_build_config.py` | 7 项诊断开关检查（含 `DIAG_BASIC` 恒开） |
| `scripts/test_diag_privacy.py` | 716 项诊断隐私守卫（调用点字段/文案、已移除入口、导出路径） |
| `scripts/test_diag_files.py` | 5 组 `DiagFiles` 实跑场景（轮转、1 MB 封顶、行序连续、超限裁剪、清空、7 天清理） |
| `scripts/test_webview_policy.py` | 5 处 WebView 导航 HTTPS 守卫（学生评价改成原生后不再有第 6 处） |
| `scripts/test_ui_contract.py` | 364 个 UI 结构锚点（当前工作区实跑；静态守卫，非真机测试；含 UI-1/UI-2 守卫、两个取课端点守卫、诊断页与匿名统计入口锚点） |
| `scripts/test_analytics.py` | 77 项统计隐私守卫（请求体只有两字段、同意默认关、触发点唯一、服务端不存原始 UUID）+ 跑 `server/analytics-worker` 的 `node --test` |

## 改动时的落点建议

- 新规则 → `core` + `CoreTests`；新界面 → `android` + UI 契约锚点。
- 新接口 → 在对应 `*Api` / `*Client` 类中，复用 `HttpForm` 的超时/体积上限/重定向策略，不新开裸连接。
- 新页面 → 继承 `Page`，在 `AndroidManifest.xml` 注册（`exported=false`），涉及返回快照时覆写 `allowPageSnapshot()`。
