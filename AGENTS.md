# AGENTS.md · 科信小助手（CampusKit）开发约定

适用于所有在本仓库工作的 Coding Agent。**本文件是开发流程约定，不是架构说明书**。架构见 [`docs/architecture.md`](docs/architecture.md)，交接见 [`docs/agent-handoff.md`](docs/agent-handoff.md)。

## 0. 技术事实（禁止套用其他栈）

- **Java** + **原生 Android View**；**没有 Kotlin、没有 Compose、没有 Gradle**。
- 构建是 Python 脚本 + 官方 SDK 工具（`aapt2` / `d8` / `zipalign` / `apksigner`），入口 `scripts/build_preview.py`。
- 业务无后端、无 Gradle 插件；依赖按 SHA-256 固定在 `tools/`，第三方清单见 [`client/THIRD_PARTY.md`](client/THIRD_PARTY.md)。
- 仓库里唯一的服务端代码是 `server/analytics-worker/`（Cloudflare Workers + D1，可选的匿名活跃统计，默认关闭）。它只处理「随机安装标识 + 版本号」，不参与任何业务请求。
- 不要引入上述不存在的技术栈，也不要假设有 `gradlew` / `build.gradle` / `*.kt`。

## 1. 每次开发任务的工作流（强制）

1. **先读文档**：`docs/architecture.md`、`docs/modules.md` 及相关专题文档。
2. 分析代码，实施最小化修改；不碰无关业务（签名 / 支付 / 用水控制 / 版本升级）。
3. `git diff` 检查实际改动。
4. 运行文档同步检查（见第 2 节），识别受影响文档。
5. **增量更新**受影响文档：只改相关段落，禁止无理由重写整套文档。
6. 更新 [`CHANGELOG.md`](CHANGELOG.md) 的「未发布」段。
7. 执行可用测试：`python scripts/test_core.py`、`test_build_config.py`、`test_diag_privacy.py`、`test_diag_files.py`、`test_webview_policy.py`、`test_ui_contract.py`、`test_analytics.py`；出包才跑 `build_preview.py`。
8. 检查代码与文档一致性（命令、路径、版本、测试数量是否与实测一致）。
9. 汇总：代码改动 / 文档改动 / 测试结果 / 未验证事项。

**严禁把未执行的测试写成「已通过」。** 真机、支付、用水、升级等未实测项必须明确标注为未验证。

## 2. 基于 Git Diff 的文档同步提示

改动代码后运行：

```bash
python scripts/check_doc_sync.py                 # 检查工作区（含未跟踪文件）
python scripts/check_doc_sync.py --base origin/main   # 检查一个提交范围（CI 用法）
```

脚本按路径映射列出**可能需要更新**的文档，默认只提示、不改文件、不阻断构建。映射表在脚本顶部 `DOC_MAP`，文档归属总表在 [`docs/architecture.md`](docs/architecture.md) 末节。

**明确无需更新文档的情况**（纯格式、不改变行为、不改接口/架构/UI/安全边界）：

- 在提交信息中加入 trailer：`Docs-Not-Needed: 原因`（如 `Docs-Not-Needed: 仅调整缩进，行为不变`）；或
- 在仓库根目录创建 `.doc-sync-waiver`，内容为一行原因（用于尚未提交的工作区检查）。

脚本检测到上述声明会打印「已声明无需更新文档」及原因。**原因必须具体**，不接受空值。声明只免除文档更新，不免除测试和一致性检查。

## 3. 文档编写红线

- 内容必须以当前代码 / 实测 / Git 记录为依据，**禁止编造**。
- 安全敏感实现（支付、用水控制、登录凭据、会话、更新校验）**只写架构与安全约束**。
- **禁止写入**凭据、Token、密码、Cookie、真实用户数据、私有抓包记录、签名密钥/口令/指纹等敏感信息。现有截图资源 `docs/screenshots/` 保持原样，不要删除或替换。
- 版本号、断言数、路径、命令必须现场核实；README 中的数字与实测冲突时以实测为准并修正 README。

## 4. 测试与 CI

```bash
python scripts/test_core.py            # core JVM 断言（当前 1594 项）
python scripts/test_build_config.py    # 诊断构建开关 7 项
python scripts/test_diag_privacy.py    # 诊断隐私守卫 717 项
python scripts/test_diag_files.py      # 日志轮转/上限/清空/过期清理实跑
python scripts/test_webview_policy.py  # WebView 导航守卫 5 项
python scripts/test_ui_contract.py     # UI 行为静态守卫 388 锚点
python scripts/test_analytics.py       # 统计隐私守卫 77 项 + server/analytics-worker 的 node --test
```

CI 在 `.github/workflows/docs-ci.yml`：推送 / PR 时自动跑以上七个入口（构建脚本内置的六个 + UI 守卫），并检查关键文档存在。**CI 不构建、不签名、不发布、不推送任何内容。**

## 5. 发布与提交

- 发版按项目发版清单执行（构建 → `delivery/update/update.json` → GitHub 同步 → 下载站 → 线上核对）；详见 [`docs/build-and-release.md`](docs/build-and-release.md)。
- **所有 Git 提交与推送必须由用户明确确认**；Agent 不自行 `git commit` / `git push` / 建 tag / 建 Release。
- 真机验收清单 [`docs/real-device-acceptance.md`](docs/real-device-acceptance.md) 未全部勾选前不得发布。

## 6. 许可证维护

项目自有代码统一采用 **MPL-2.0**；例外与归属说明见 [`docs/licensing.md`](docs/licensing.md)，第三方组件见 [`client/THIRD_PARTY.md`](client/THIRD_PARTY.md)。

- 新增自有源码文件首行加 SPDX 标识：Java / JavaScript 用 `// SPDX-License-Identifier: MPL-2.0`，Python 用 `# SPDX-License-Identifier: MPL-2.0`（放在 shebang 与编码声明之后）。
- **不得**给第三方源码、二进制依赖、随包许可证文本添加 MPL 标识，也不得改写 `client/android/res/raw/*_license.txt` 的原文。
- 修改根目录 `LICENSE` 时同步三处：`client/android/res/raw/mpl_license.txt`（App 内全文）、`docs/licensing.md`、README 的「开源协议」章节。
- 引入新的第三方组件必须在 `client/THIRD_PARTY.md` 登记版本、许可证与分发形式；许可证不明的组件单独记录，不擅自认定。
- **每次发布都要提供该版本的源码**（MPL 3.2 的义务）：正式版打与版本号一致的 `vX.Y.Z` tag、预览版用 `preview` tag，Release 说明与下载站发布项写明源码地址；App 内「更多 → 开源许可」弹窗已显示本机版本号与源码地址，改这段文案时不得删掉。
- 历史 MIT 存档固定放在 `docs/legacy/LICENSE-MIT.txt`，**不要**移到仓库根目录或改名成 `LICENSE`，否则会被 GitHub 误判为当前主许可证。
