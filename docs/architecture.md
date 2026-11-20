# 架构总览

> 依据当前仓库源码整理（Java、原生 Android View、Python 构建脚本）。无 Kotlin / Compose / Gradle。

## 一句话

单 APK、**业务无后端**：界面层直接用原生 View 组装，平台无关的规则抽到 `core`，业务数据从手机直连学校与平台官方接口；Python 脚本调用官方 SDK 工具完成编译、签名与校验。仓库里唯一一处服务端代码是 `server/analytics-worker/`（可选的匿名活跃统计，默认关闭、只收随机安装标识与版本号），详见 [`analytics.md`](analytics.md)。

## 分层

```
┌─────────────────────────────────────────────────────────┐
│ client/android  (81 个受管 Java 文件)                    │
│   Activity / Page / View / 主题 / 平台 SDK 适配           │
│   TimetableActivity, EduClient, LifeDeviceApi, UpdateCheck │
└───────────────┬─────────────────────────────────────────┘
                │ 纯 Java 调用，无 Android 依赖
┌───────────────▼─────────────────────────────────────────┐
│ client/core  (51 个受管 Java 文件)                       │
│   会话 / 协议解析 / 状态机 / 导航 / 课程模型 / 决策规则     │
│   MainNavigator, Course, RefreshDecision, TrustedUrlPolicy│
│   UseSession, PendingUse, UpdateManifest, 4_000_000 上限  │
└───────────────┬─────────────────────────────────────────┘
                │ javac --release 8 → JVM 断言 (CoreTests)
┌───────────────▼─────────────────────────────────────────┐
│ scripts/*.py                                             │
│   bootstrap_android → test_* → build_preview → 出包/验签  │
└─────────────────────────────────────────────────────────┘

旁路（与业务无关，可不存在）：
server/analytics-worker  Cloudflare Worker + D1 —— 匿名活跃统计，默认关闭
                         仅在 --analytics-endpoint 构建且读者主动开启时被调用
```

- `client/core` 不 import Android 类，可单独在 JVM 上编译测试（`scripts/test_core.py`）。
- `client/android` 持有 Activity、View、平台 API；需要把规则下沉时加入 `core` 并补断言，不在 Activity 里堆业务状态。
- 诊断日志另分三层：`core/DiagRedact`（字段白名单与脱敏）、`core/DiagEvent` / `DiagCatalog`（行格式与文案）、
  `android/Diag`（开关、崩溃兜底、写盘）与 `android/DiagFiles`（轮转）。规则在 core 里由 JVM 断言锁住，
  存储与页面在 android 里。详见 [`diagnostics.md`](diagnostics.md)。
- `client/fieldtest` 是用水控制代码的独立 source set。正式（read-only）构建不包含它，构建后以 DEX 字符串扫描确认控制路由 occurrence 全为 0。

## 数据流（不公开请求细节）

```
用户 → 原生 View → core 规则/协议 → HTTPS 官方接口（教务 / 慧生活 / 趣智校园）
                              └→ 支付 SDK（支付宝收银台 / 免密）
凭据 → AccountVault / LifeSessionVault / EduVault → Android Keystore + AES-256-GCM → 应用私有目录
更新 → 更新渠道（WorkBuddy 默认 / GitHub / Gitee 可选，更多页选）→ 对应 update.json（正式）/ beta.json（预览，开关打开时才读）→ UpdateManifest 校验 → 各渠道候选按 versionCode 取大 → 下载 → 字节数+SHA-256+包身份校验 → 用户点安装
诊断 → Diag.event（白名单字段）→ 队列 → files/diag/*.log（256 KB × 4，7 天）→ 诊断页 / 导出 / 故障报告
统计 → 读者同意 → 进入前台且今天未报 → POST 统计 Worker（随机安装标识 + 版本号）→ 服务端 HMAC 入库（90 天）
       失败静默、不重试；关掉即停并可请求删除（见 analytics.md）
```

平台接口只写架构约束，具体域名/路径与请求头实现在源码里查；接口分类见 [`data-interfaces.md`](data-interfaces.md)，安全约束见 [`security.md`](security.md)。

## 关键不变量（改动时不要破坏）

- **业务无后端**：不引入业务中转服务器、第三方统计 SDK、推送、广告 SDK。唯一的自建服务是
  `server/analytics-worker/`：默认关闭、只收随机安装标识与版本号、失败不影响功能，业务请求仍然直连官方平台。
- **凭据本地加密**：密钥由 Keystore 保护；教务密码只在查询过程中提交给学校，不落盘。
- **支付不碰凭据**：充值走支付宝官方收银台；用水控制仅 field-test source set 存在。
- **不盲重试**：扣费/设备控制请求超时进「待核实」，先查服务端订单状态。
- **覆盖安装**：包名 `io.qzxy.lite.preview` 与签名身份保持稳定，versionCode 单调递增。
- **版本命名**：正式版 `X.Y.Z`，预览版 `X.Y.Z-beta.N`（N 从 1 起），四段 `X.Y.Z.W` 只对旧清单与已安装包只读兼容；新旧只比 `versionCode`，正式版必须能覆盖此前的 Beta。
- **测试先行**：core 规则改动先补 `CoreTests`；UI 结构改动先补 `scripts/test_ui_contract.py`；WebView 改动先补 `scripts/test_webview_policy.py`。

## 文档归属表（Git Diff 提示映射依据）

| 改动路径 | 需检查的文档 |
| :-- | :-- |
| `client/core/**` | `architecture.md`、`modules.md`、`data-interfaces.md` |
| `client/android/**` | `modules.md`、`ui-navigation.md`、`data-interfaces.md` |
| `**/Diag*.java`、`**/Diag*Activity.java` | `diagnostics.md`、`security.md` |
| `client/fieldtest/**`、`AndroidManifest.xml` | `modules.md`、`security.md` |
| `client/**/Analytics*.java`、`client/android/**/Analytics*` | `analytics.md`、`security.md`、`diagnostics.md` |
| `server/**` | `analytics.md`、`data-interfaces.md` |
| `scripts/build_preview.py`、`scripts/*.py` | `build-and-release.md`、`known-issues.md` |
| `delivery/update/**` | `build-and-release.md`、`CHANGELOG.md` |
| 任何行为变更 | `CHANGELOG.md`（未发布段） |

提示机制实现见 `scripts/check_doc_sync.py`；约定见仓库根 `AGENTS.md`。

## 相关文档

[模块清单](modules.md) · [匿名统计](analytics.md) · [诊断日志](diagnostics.md) · [构建与发布](build-and-release.md) · [安全约束](security.md) · [UI 与导航](ui-navigation.md) · [数据接口](data-interfaces.md) · [已知问题](known-issues.md) · [Agent 交接](agent-handoff.md)
