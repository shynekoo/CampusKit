# 模块清单

按实际 source set 与文件名整理（`git ls-files '*.java'`：core 44、android 71、fieldtest 7、测试 11）。完整类清单可直接看目录，这里按职责分组。

## client/core — 平台无关规则（45 个文件，JVM 可测）

| 组 | 类 | 职责 |
| :-- | :-- | :-- |
| 导航/状态 | `MainNavigator`、`RefreshDecision`、`TodayDynamic`、`ClockRedraw`、`SessionCheck` | tab 返回栈、今日就地刷新判定、会话检查去抖与错误隔离 |
| 课程模型 | `Course`、`CourseJsonMapper`、`TimetableIndex`、`BoardFit`、`TimetablePalette`、`TermDate`、`ClassTimes`(android 侧) | 课程无损映射、周次索引、屏幕自适应、调色板、开学日期 |
| 课表解析 | `TimetablePdfParser`、`TimetableWebCourseParser`、`EduTableColumns`、`EduTimetable` | 教务网页表格与 PDF 表格解析 |
| 登录/会话 | `AccountSession`、`LoginCoordinator`、`LoginPersistence`、`SessionCipher`、`SmsLoginProtocol`、`EduLoginProtocol`、`EduSite` | 登录状态、凭据加密、协议字段与 RSA |
| 设备/用水 | `DeviceProfile`、`DeviceQr`、`Life798Qr`、`WaterChannel`、`WaterEvents`、`WaterOrder`、`UseSession`、`PendingUse`、`FieldStartGate`、`FieldTestGuard`、`Money` | 二维码、用水状态机、待核实、金额、field 边界 |
| 使用码 | `UseCode` | 8 位使用码（5+3）解析与校验 |
| 更新 | `UpdateManifest`、`UpdateUrls` | 更新清单必填字段校验、HTTPS 逐跳重定向策略 |
| 安全策略 | `TrustedUrlPolicy`、`SnapshotPolicy`、`DiagPolicy` | WebView 准入、敏感页快照、诊断默认关 |
| 教务学业 | `EduAcademic` | 学生学业情况：GPA/统计/去重学分树解析（含 `appendTo` 层级还原与 `jdkcsx` 端点分流），课程明细 JSON 解析（`parseCourses`） |
| 其他 | `DailyQuote`、`AlipayWaterLink`、`PickupCodeLinks`、`RechargeSignature`、`NativeSigningV6528` | 文案、跳转链接、签名/协议辅助 |

测试：`client/core/test/.../CoreTests.java` —— 当前工作区实跑 1278 项断言（含 `jdkcsx` 课程端点分流），涵盖上述状态机、解析与策略。

## client/android — 原生界面与平台适配（77 个文件）

| 组 | 代表文件 | 职责 |
| :-- | :-- | :-- |
| 主屏 | `TimetableActivity`、`MainShell`、`AnimatedNavBar`、`MotionSpec`、`UiTokens`、`App`、`Page`、`CardLayer`、`AppUi`、`Theme` | 三 tab 主屏、壳层与 tab 指示器动画（含 reduce-motion 降级）、UI 尺寸 token、页面基类/返回手势、主题下发、卡片动画 |
| 课表 UI | `WeekBoard`、`CourseAdapter`、`CoursePickers`、`CourseWeeks`、`ScheduleInfo`、`ScheduleWidget*`(3 个) | 周视图、课程编辑、桌面组件 |
| 账号与凭据 | `AccountActivity`、`AccountVault`、`EduVault`、`LifeSessionVault`、`AccountProfile`、`AccountInfoActivity` | 登录入口、加密存储、账号资料 |
| 教务 | `EduClient`、`EduLoginActivity`、`EduQueryActivity`、`EduResultActivity`、`EduPortalActivity`、`EduTimetableImportActivity`、`EduTables`、`EduTimetableReader`、`AcademicActivity`、`AcademicStatusActivity` | 教务会话、查询、导入、学业情况页（学分树 + 点击展开课程明细） |
| 慧生活/水控 | `LifeLoginActivity`、`LifeDeviceActivity`、`LifeDeviceApi`、`LifeAuthApi`、`LifeDeviceProfile`、`DeviceDetails`、`DeviceLookup`、`ScanActivity`、`QrDecoder`、`WaterApi` | 登录、设备资料（只读）、扫码、订单状态 |
| 趣智校园 | `SmsAccountApi`、`UseCodeApi`、`UseCodeActivity`、`QzxyAlipayActivity`、`AlipayLogin` | 使用码、支付宝授权登录；充值/收银不在应用内，只把设备交给支付宝 |
| 更新/隐私/其他 | `UpdateCheck`、`AppChangelog`、`ChangelogActivity`、`ApkProvider`、`PrivacyActivity`、`NoticeActivity`、`LegalText`、`LegalView`、`NoticeDialog`、`FeedbackActivity`、`MoreActivity`、`AppearanceActivity`、`DiagActivity`、`SchoolSiteActivity`、`TimetableWebActivity`、`WebBack`、`Diag`、`HttpForm`、`ColorDialog` | 应用内更新、更新日志（`AppChangelog` 内置数据 + `ChangelogActivity` 展开式卡片页，离线、无网络）、数据与隐私页、使用公告（页面 + 首次启动弹窗，共用 `LegalText` 文案与 `LegalView` 排版）、外观、网页容器、网络与弹窗 |

## client/fieldtest — 用水控制（7 个文件，正式包不编入）

`WaterActivity`、`WaterControl`、`LifeWaterActivity`、`LifeWaterProtocol`、`LifeProtocolProbe`、`LifeVersion`、`LifeDiagnostics`。仅在 `--field-water` 构建下进入 dex；构建脚本对控制路由与两个 dex 类型描述符做出现/缺失双向断言。

## 测试入口

| 脚本 | 检查内容 |
| :-- | :-- |
| `scripts/test_core.py` | core 源码 JVM 编译 + `CoreTests`（当前工作区实跑 1278 项） |
| `scripts/test_build_config.py` | 5 种诊断开关组合 |
| `scripts/test_webview_policy.py` | 5 处 WebView 导航 HTTPS 守卫 |
| `scripts/test_ui_contract.py` | 146 个 UI 结构锚点（当前工作区实跑；静态守卫，非真机测试；含 UI-1/UI-2 守卫与两个取课端点守卫） |

## 改动时的落点建议

- 新规则 → `core` + `CoreTests`；新界面 → `android` + UI 契约锚点。
- 新接口 → 在对应 `*Api` / `*Client` 类中，复用 `HttpForm` 的超时/体积上限/重定向策略，不新开裸连接。
- 新页面 → 继承 `Page`，在 `AndroidManifest.xml` 注册（`exported=false`），涉及返回快照时覆写 `allowPageSnapshot()`。
