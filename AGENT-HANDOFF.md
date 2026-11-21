# AGENT-HANDOFF · 科信小助手（CampusKit）接手指南

供任意版本、任意 Agent 接手时快速对齐现状。原则：**以当前仓库代码和技术文档为准，不凭记忆推断**。
本文只写现状与硬约束；逐轮排查过程不入库，需要时查 Git 历史与 [`CHANGELOG.md`](CHANGELOG.md)。

## 一、项目概况

- 河北工程大学科信学院学生的第三方 Android 校园助手：教务（课表 / 成绩 / 学业情况 / 空教室 / 学生评价）+ 校园生活（慧生活798 用水、趣智校园余额与使用码、取件与官网快捷入口）。
- 单 APK、业务无后端、无 Gradle：Java + 原生 View，Python 脚本调官方 `aapt2` / `d8` / `zipalign` / `apksigner` 出包。
- 仓库里唯一的服务端代码是 `server/analytics-worker/`（Cloudflare Workers + D1，可选的匿名活跃统计，默认关闭且当前未配置地址）。架构文档写「业务无后端」，不要写成绝对「无后端」。
- 模块：`client/android`（界面）、`client/core`（不依赖 Android 的规则层）、`client/fieldtest`（用水控制 source set，`--field-water` 时整组编入，**正式版即带该开关，故这组文件在正式包内**）、`scripts`（构建 / 断言 / 发布辅助）、`docs`（技术文档）、`delivery`（发布说明与更新清单）。
- Android 7.0+（API 24），targetSdk 36。**权限 4 项**：网络、网络状态、相机（扫码）、安装 APK。
- 第三方：ZXing 3.5.3、PDFBox-Android 2.0.27.0、Bouncy Castle 1.72、支付宝 SDK 15.8.42，版本与 SHA-256 固定在构建脚本。

## 二、当前状态

| 项 | 值 |
| :-- | :-- |
| 线上正式版 | **2.6.0 / 236**（tag `v2.6.0`） |
| 线上预览 | **2.6.0-beta.18 / 235**（GitHub 与 Gitee 的 `preview`） |
| 当前分支 | `main`，与 `origin/main` 同步 |
| 断言 | core 1554 · build_config 7 · diag_privacy 717 · diag_files 6650 · webview 5 · ui_contract 388 · analytics 77 |

三处发布限制（WorkBuddy 站点账号不匹配、预览清单按渠道各一份、预览通道默认关闭）与各问题的历史结论见
[`docs/agent-handoff.md`](docs/agent-handoff.md)。安全边界与已知残留风险见 [`docs/security.md`](docs/security.md)。

## 三、发版前必做（用户明确要求，缺一不可）

1. 构建 APK 并跑断言；写 `delivery/update/update.json`（`version` / `versionCode` / `notes` / `url` / `bytes` / `sha256`）。
2. GitHub 同步：`git push`、tag `vX.Y.Z`、建 Release 上传 APK、更新 README 的版本信息。
3. 下载站同步：新 APK 放进 `delivery/update/`，`update.json` 更新，重新发布站点。
4. 发布后核对线上页面与直链均返回 200。
5. 同步更新 `CHANGELOG.md` 与本文档的状态、遗留问题；核对版本号与 Git 提交；验证文档链接。

预览版按 `docs/build-and-release.md` 构建，清单按渠道各写一份，**只传包不传清单等于没发**。

## 四、遗留与风险

1. **真机验收是发布硬门槛**：`docs/real-device-acceptance.md` 未全部勾选前不得发布。
2. **预览通道默认关闭**：用户须自选渠道并打开「接收预览版更新」。
3. **实验性协议探测已移出发布包（2.6.1-beta.1 起）**：探测类与入口只在 `client/fieldtest/experimental`，默认不编译；发布包由构建期断言保证不含该类与 `dev/mode/start`，静态守卫见 `scripts/test_probe_boundary.py`。2.6.0 及更早的 APK 仍带该入口，下载页已提示勿用；源码按 tag 保留。
4. **构建开关**：**正式版与预览版都必须带 `--field-water`**（不带会静默产出缺用水控制的只读包，2.5.7 就是这样丢过扫码跳转）；`--diagnostics` 只用于内部排障，产出与正式版同名，误传会污染更新通道。
5. **签名**：`tools/keys/` 下的密钥与口令不进仓库；换签名会让已装用户无法覆盖安装。
6. **不要用 `git checkout .` / `reset --hard` 清工作区**，先确认有没有 WIP。

## 五、文档索引

| 文档 | 用途 |
| :-- | :-- |
| [`AGENTS.md`](AGENTS.md) | 开发流程、测试入口、文档红线、许可证维护（**权威**） |
| [`docs/agent-handoff.md`](docs/agent-handoff.md) | 现状快照、已解决问题的结论、分发现状 |
| [`docs/architecture.md`](docs/architecture.md) | 分层、数据流、不变量、文档归属 |
| [`docs/modules.md`](docs/modules.md) | core / android / fieldtest 模块清单 |
| [`docs/data-interfaces.md`](docs/data-interfaces.md) | 数据来源分类、入口类、写操作边界 |
| [`docs/security.md`](docs/security.md) | 安全架构、残留风险登记（R1–R4） |
| [`docs/build-and-release.md`](docs/build-and-release.md) | 构建开关、版本号规则、发布清单、CI |
| [`docs/real-device-acceptance.md`](docs/real-device-acceptance.md) | 真机验收清单（全部待勾选） |
| [`client/THIRD_PARTY.md`](client/THIRD_PARTY.md) | 第三方组件与许可证 |

---

*最后更新：2026-11-21 · 依据 `main` 当前提交与实跑断言*
