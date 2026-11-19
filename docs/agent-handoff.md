# Agent 交接

供任意 Agent 接手时快速对齐现状。以**当前代码、Git 记录、实测结果**为准；更细的流程见仓库根 [`../AGENTS.md`](../AGENTS.md)。

## 一、现状快照（2026-11-19）

| 项 | 值 |
| :-- | :-- |
| 技术栈 | Java + 原生 Android View + Python 构建；无 Kotlin / Compose / Gradle |
| 线上正式版 | 2.5.7 / versionCode 197（`delivery/update/update.json`） |
| 已提交 HEAD | `f0e4232`（2.5.8.4 / 202 及文档制度提交），与 `origin/main` 同步 |
| 最新 tag / Release | `v2.5.7`；2.5.8.x 未建 tag / Release |
| 工作区 | 2.5.8.5 / 2.5.8.6 已随本次提交入库（学业页层级排版 + 课程明细点击展开）；普通版 APK 已构建验签 |
| 核心断言 | 实测 **1262 项全过**；UI 契约守卫 **141 锚点全过** |
| 发布状态 | 2.5.8.x 代码已推送，**未建 tag / Release、未发版、未同步下载站** |

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

## 四、验证状态（如实）

**已执行**：`test_core` 1262、`test_build_config` 5、`test_webview_policy` 5、`test_ui_contract` 141，全部通过；2.5.8.6 普通版 APK 已构建并通过 v2/v3 验签；真实账号端到端拉取学业数据成功（GPA 2.9875、37 节点 5 层、16 课组 94 门课）。

**未执行**：真机验收清单全部；2.5.8.6 真机覆盖安装与学业页回归（含点击展开、滚动、返回手势）；真实支付用水、MobSF、签名轮换、旧版升级兼容。详见 [`known-issues.md`](known-issues.md)。

## 五、接手第一步

1. `git status` + `git diff` 看未提交改动；**不要** `git checkout .` / `reset --hard`。
2. 读 [`architecture.md`](architecture.md) 与 [`modules.md`](modules.md)；按任务读专题文档。
3. 按 [`../AGENTS.md`](../AGENTS.md) 九步流程工作；跑 `python scripts/check_doc_sync.py` 得到文档提示。
4. 测试四入口全跑；未做的不要写「通过」。
5. 发版按 [`build-and-release.md`](build-and-release.md) 清单；真机验收未完成不得发布。
6. **提交与推送由用户确认**，Agent 不自行 commit / push / tag / Release。

## 六、文档索引

[架构](architecture.md) · [模块](modules.md) · [构建与发布](build-and-release.md) · [安全约束](security.md) · [UI 与导航](ui-navigation.md) · [数据接口](data-interfaces.md) · [已知问题](known-issues.md) · [安全审计：网络/SDK](security-audit-network-sdk.md) · [安全审计：支付/用水](security-audit-payment-water.md) · [真机验收](real-device-acceptance.md) · [整改验收](rectification-acceptance.md) · [返回手势](back-gesture.md) · [变更日志](../CHANGELOG.md)
