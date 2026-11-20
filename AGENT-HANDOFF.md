# AGENT-HANDOFF · 科信小助手（CampusKit）接手指南

供任意版本、任意 Agent 接手时快速对齐现状。原则：**以当前仓库代码和技术文档为准，不凭记忆推断**。变更内容见 [`CHANGELOG.md`](CHANGELOG.md)。

## 一、项目概况

- 河北工程大学科信学院的第三方安卓客户端（趣智校园 / 慧生活798 / 教务 / 菜鸟）。单 APK、无后端、无 Gradle，Java + 原生 View 手工构建。
- 模块：`client/android`（原生界面 / Activity）、`client/core`（不依赖 Android 的会话、二维码协议、用水状态机、解析）、`scripts`（构建、断言、核验）。
- 系统要求 Android 7.0+（API 24），targetSdk 36；第三方库 ZXing 3.5.3、PDFBox-Android 2.0.27.0、Bouncy Castle 1.72、支付宝 SDK 15.8.42。
- 权限仅 5 项；凭据 AES-256-GCM 加密存本地私有目录，密钥由 Android Keystore 保护；无推送 / 统计 / 广告 SDK。

## 二、当前版本状态（2026-11-19 核查）

| 项 | 值 | 说明 |
| :-- | :-- | :-- |
| 线上正式版 / 下载站 | **2.5.8.1** | `delivery/update/update.json`，versionCode `209`，2026-11-20 发布；修复趣智校园扫码跳转 |
| 线上 APK | `campuskit-2.5.8.1.apk` | 5,437,306 字节，SHA-256 `af7ed823…f6708f`；带 `?sharecode=BZD63HRP4B`；用水版（`--field-water`），dex 水控类命中 4/100/3 |
| 历史开发包（同名未发版） | 2.5.8.1（versionCode 199） | 提交 `6406500`；与本次线上 2.5.8.1/209 **不是同一产物** |
| 已提交（本地 `main`，合并后重建） | **2.5.8.8**（versionCode 206） | 产物 `delivery/科信小助手-2.5.8.8.apk`；主线已含少课修复，dex 反查通过，详见「同步状态」 |
| 当前分支 | **2.5.8**（versionCode 208，已发布） | `feat/diag-2.5.8`：诊断系统重构 + 把 2.5.8.1–2.5.8.9 合并成正式版本号；产物 `delivery/科信小助手-2.5.8.apk`，5 420 922 B，SHA-256 `ac40b21d…10d806` |
| 发布公告（当前版） | `delivery/release-2.5.8.1.md` | 含短版 / 长版公告、根因、测试与上线清单；`update.json` 与 App 内更新日志已同步。上版公告见 `delivery/release-2.5.8.md` |
| 上一版未发版包 | 2.5.8.7（versionCode 205） | 产物仍在 `delivery/`，**不含少课修复**，仅作历史对照 |
| 最新 Git tag | `v2.5.8.1` | 2.5.8 为 `00d7bb4`；2.5.8.x 早期开发迭代未各自建 tag |
| GitHub Release | 最新为 [v2.5.8.1](https://github.com/shynekoo/CampusKit/releases/tag/v2.5.8.1) | 附 `kexin-xiaozhushou-2.5.8.1.apk`（5,437,306 字节） |

## 二之二、同步状态（2026-11-19 22:45 合并完成）

- 本地 `main` 已 rebase 到 `origin/main`（`8fe5e6e`，含 `9c9bfbf` 少课修复），原 `4139aec` / `a8e0efe` 两个提交被重放为 `4bded0c` / `e59f1d2`，本地与远端重新回到线性、无分叉。
- rebase 冲突只有 4 个文件：`CHANGELOG.md`、`docs/agent-handoff.md`、`docs/ui-navigation.md`（`scripts/test_ui_contract.py` 自动合并）。三处均按**合并后实跑的真值**填写，没有照抄任一方：核心断言 **1278**、UI 守卫 **213**（不是分支上的 212，也不是主线的 208）。
- 冲突内容取舍：`CHANGELOG` 事实基准改为合并后视角并保留双方的版本段；`ui-navigation` 的公告守卫取主线较新的「唯一入口在 `PrivacyActivity`」措辞，同时保留本轮的学业页端点守卫。
- 关键教训：**2.5.8.7 的包是在 `16cec29` 上构建的，那时少课修复还在并行分支上**，dex 里 `cxJxzxjhxfyqFKcxx` / `planCourses` / `opensCourses` 命中数均为 0，所以装 2.5.8.7 看第二课堂仍然少课。合并后的 2.5.8.8 三项均为 1（`jdkcsx` 3 处），已确认修复真的在安装包里。
- 结论：**出包前先确认 `git log` 里有修复提交**，光看"已经合并"不等于本地工作区拿到了代码。

## 二之三、诊断系统改造（2.5.8，未提交、未合并）

- 目的：让「课表加载失败」能定位到登录 / 网络 / 解析 / 渲染中的具体一段，而不是只说「失败」。
- 结构：`core/DiagRedact`（字段白名单、脱敏、安全堆栈、原因分类）+ `core/DiagEvent`（固定行格式与解析）+
  `core/DiagCatalog`（中文文案）；`android/Diag`（开关、崩溃兜底、异步写）+ `android/DiagFiles`（256 KB × 4、7 天）
  + `DiagActivity`（筛选/展开/分页/复制/导出/清空）+ `DiagReportActivity`（故障报告）。
- 正式版也有诊断页：`DIAG_BASIC` 恒开，运行期默认关；`--diagnostics` 只多编细节事件（`Diag.DETAIL`，正式包被 javac 删除）。
- SDK 原文日志回调已彻底移除；不再依赖 `maskCode` / `maskChain` / `maskSdk` 正则，改为「字段进不来」。
- 已接入：教务登录四阶段、课表候选端点/解析/写入、网络状态与超时、慧生活、支付宝、趣智、更新、页面导航。
- 未接入：扫码/相机、课表拖拽缩放、支付 Activity 被回收的竞态、ANR / native crash。
- 权威文档：[`docs/diagnostics.md`](docs/diagnostics.md)。

## 三、开发进度

### 已提交（2.5.8.1，未发版）
- 安全整改 S1–S6：更新校验（大小 + SHA-256 + HTTPS、重定向策略、APK 身份）、诊断默认关闭并脱敏、WebView 主机白名单、敏感页面退出返回快照、支付/用水业务审计、教务 WebView 统一准入。
- 架构重构 A1–A4：导航状态机下沉 core、typed `Course` 模型、今日页真局部刷新（`RefreshDecision` / `TodayDynamic`）、会话检查去抖缓存与错误隔离。
- 新增「使用码」（8 位码可不扫码开设备）、UI 结构静态守卫（当时 33 锚点，当前工作区 46）、真机验收清单。

### 已提交（2.5.8.2–2.5.8.6，未发版）
- **新增学业情况查询**：`EduAcademic`（core 解析，绩点 / 统计 / 去重学分树）、`AcademicStatusActivity`（结果排版）；`EduClient` 增加 `/xsxy/` 只读页与 Referer 白名单；manifest 注册新 Activity，versionCode 199 → 202、versionName 2.5.8.1 → 2.5.8.4。
- 外观页圆角预览按 dp 换算修复、文案调整、删除两段过时说明。
- **2.5.8.4 修复**：学业页 `card()` 双重挂载导致闪退，改为唯一入口 `addCard()` 挂载；`screen.note()` 只在 `onCreate` 设置一次；教务卡顺序调为第一行 成绩/空教室/教务系统，第二行 学业情况/河工程官网/科信官网。
- 相应新增核心断言与 UI 守卫（46 锚点）。

**未提交文件清单**（`feat/diag-2.5.8` 的 `git status`）：

- 修改：`CHANGELOG.md`、`README.md`、`AndroidManifest.xml`、`Diag.java`、`DiagActivity.java`、`AlipayLogin.java`、
  `App.java`、`EduClient.java`、`EduTimetableImportActivity.java`、`HttpForm.java`、`LifeAuthApi.java`、
  `LifeLoginActivity.java`、`MoreActivity.java`、`Page.java`、`TimetableActivity.java`、`UpdateCheck.java`、
  `UseCodeApi.java`、`CoreTests.java`、`scripts/build_preview.py`、`check_doc_sync.py`、`test_build_config.py`、
  `test_ui_contract.py`、7 个文档。
- 新增：`DiagFiles.java`、`DiagReportActivity.java`、`DiagRedact.java`、`DiagEvent.java`、`DiagCatalog.java`、
  `docs/diagnostics.md`、`scripts/test_diag_privacy.py`。
- 未提交、未合并、未 push（按约定由用户确认）。

## 四、验证状态（如实标注）

**已执行（本环境，2026-11-19）**
- `python scripts/test_core.py` → **1228 项断言全部通过**。输出明确：Live API / Bluetooth 兼容性不在本测试范围。
- 当前：`test_core` 1228 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 46 全绿。
- 2.5.8.1 阶段：三配置构建（普通 / 用水 / 诊断）均过断言与 v2/v3 签名；只读版 DEX 控制路由 occurrence 全 0。2.5.8.4 普通版 APK 已构建并通过 v2/v3 验签（构建级，非真机）。

**未执行 / 未通过（不得标记通过）**
- 真机验收：`docs/real-device-acceptance.md` 全部待勾选（导航手势、局部刷新、支付用水、安装包配置、旧 Android / 异常 APK 等）。
- 旧版覆盖升级、水控真实出水与付款、支付宝 SDK / MobSF 扫描、签名轮换、FLAG_SECURE 真机效果——均未实测，见 `docs/rectification-acceptance.md`。
- 2.5.8.4 普通版已出包并验签，但真机覆盖安装与学业页回归未做（1228 断言 + 验签 ≠ 真机可用）。

## 五、遗留问题与风险

1. **真机验收是发布硬门槛**：未全部勾选前不得发布 2.5.8.x。
2. **工作区有未提交改动**：接手先 `git status` + `git diff` 确认；不要用 `git checkout .` / `reset --hard` 清掉 WIP。
3. **构建开关**：正式版必须用不带 `--field-water` / `--diagnostics` 的普通构建；诊断版会产出与正式版同名 APK，勿误传。
4. **签名**：`tools/keys/preview.p12` 与口令不进仓库（`QZXY_KS_PASS` 或 `tools/keys/keystore.pass`）；换签名会让已装用户无法覆盖安装，务必保留备份。
5. **README 数字已修正**：原 `1073` / `1034` 两处断言数已按实测改为 `1228`。README 的「约 1.5 万行 Java / 110 个受管文件」也是旧值（实测受管 Java 133 文件、约 7200 行），本次未改该行——如需精确请单独核对口径（含 3 个 scripts 辅助 Java）。
6. **隐私**：确认任何文档都不写入 Token / 密码 / Cookie / 密钥口令；签名证书指纹可留（公开信息）。

## 六、发版前必做（用户明确要求，缺一不可）

1. 构建 APK，跑核心断言；写 `delivery/update/update.json`（version / versionCode / notes / url / bytes / sha256）。
2. GitHub 同步：`git push`、`tag vX.Y.Z`、建 Release 上传 APK、更新 README（版本表 / 下载链接 / SHA-256 / 更新日志）。
3. 下载站同步：新 APK 复制到 `delivery/update/`，`update.json` 更新，重新发布 sites 站点。
4. 发布后核对线上页面与直链均返回 200。
5. 同步更新 `CHANGELOG.md` 与本文档的版本状态、开发进度、遗留问题；核对版本号与对应 Git Commit；验证文档链接有效。

## 七、关键文档索引

| 文档 | 用途 |
| :-- | :-- |
| [`AGENTS.md`](AGENTS.md) | 所有开发任务的强制流程与文档同步约定 |
| [`README.md`](README.md) | 项目介绍、功能、下载、构建、FAQ（含更新日志） |
| [`CHANGELOG.md`](CHANGELOG.md) | 各版本新增 / 修复 / 变更 / 废弃 |
| [`docs/agent-handoff.md`](docs/agent-handoff.md) | 完整交接文档（现状 / 进度 / 索引） |
| [`docs/architecture.md`](docs/architecture.md) | 架构分层、数据流、不变量、文档归属表 |
| [`docs/modules.md`](docs/modules.md) | core / android / fieldtest 模块清单 |
| [`docs/build-and-release.md`](docs/build-and-release.md) | 构建开关、版本号、发布清单、CI |
| [`docs/security.md`](docs/security.md) | 安全架构与约束（不含敏感值） |
| [`docs/ui-navigation.md`](docs/ui-navigation.md) | tab、返回栈、手势、刷新、主题 |
| [`docs/data-interfaces.md`](docs/data-interfaces.md) | 数据来源分类与解析约束 |
| [`docs/known-issues.md`](docs/known-issues.md) | 未验证事项与已知限制 |
| [`docs/real-device-acceptance.md`](docs/real-device-acceptance.md) | 真机验收清单（全部待勾选） |
| [`docs/rectification-acceptance.md`](docs/rectification-acceptance.md) | S1–S6 / A1–A4 整改说明与遗留风险 |
| [`docs/security-audit-network-sdk.md`](docs/security-audit-network-sdk.md) | 网络与 SDK 安全审计 |
| [`docs/security-audit-payment-water.md`](docs/security-audit-payment-water.md) | 支付与用水业务安全审计 |
| [`docs/back-gesture.md`](docs/back-gesture.md) | 返回手势行为 |
| [`client/THIRD_PARTY.md`](client/THIRD_PARTY.md) | 第三方组件许可证 |
| [`design-system/campuskit/MASTER.md`](design-system/campuskit/MASTER.md) | 界面规范 |
| [`delivery/release-*.md`](delivery/) | 历次发布说明与 notes 文案 |

## 八、文档维护制度（用户 2026-11-19 确立，每次开发任务必须遵循）

1. 阅读相关现有文档 → 2. 分析代码并修改 → 3. 检查 Git Diff → 4. 识别受影响文档 → 5. 增量更新文档 → 6. 更新 `CHANGELOG.md` → 7. 执行可用编译与测试 → 8. 检查代码与文档一致性 → 9. 汇总代码 / 文档 / 测试变更。
- 影响技术实现、架构、接口、数据流、UI 行为或功能状态 → 必须同步更新文档；纯格式改动可豁免但必须做影响检查。
- 优先增量更新，禁止无理由重写整套文档；文档内容以实际代码为依据，禁止编造。**严禁把未执行的测试标记为已通过。**

---

*最后更新：2026-11-19 · 依据 Git HEAD `6406500` + 当前工作区 diff + 实跑 1228 项断言*
