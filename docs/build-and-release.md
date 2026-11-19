# 构建与发布

## 工具链（实际）

- **无 Gradle**。Python 3 驱动，JDK 24（产出 Java 8 字节码），Android SDK 平台 36 / Build-Tools 36.0.0。
- `scripts/bootstrap_android.py`：从 Google 官方仓库下载并校验平台与 Build-Tools 到 `tools/android-sdk`。
- `scripts/build_preview.py`：编译 core + android（+ 可选 fieldtest）→ `aapt2` → `d8` → `zipalign` → `apksigner`，并写 `.build.json`（体积、SHA-256、逐文件源码哈希、权限清单）。
- 第三方依赖按 SHA-256 固定在 `tools/`：ZXing 3.5.3、PDFBox-Android 2.0.27.0、Bouncy Castle 1.72、支付宝 SDK 15.8.42（手工合并 AAR）。见 [`../client/THIRD_PARTY.md`](../client/THIRD_PARTY.md)。

## 常用命令

```bash
python scripts/bootstrap_android.py            # 首次准备 SDK
python scripts/test_core.py                    # 1228 项 core 断言
python scripts/test_build_config.py            # 构建开关 5 项
python scripts/test_webview_policy.py          # WebView 守卫 5 项
python scripts/test_ui_contract.py             # UI 契约 46 锚点
python scripts/build_preview.py                # 普通只读版出包（默认跑前 3 个测试）
```

`build_preview.py` 默认调用 `test_core` / `test_build_config` / `test_webview_policy`；**不调用** `test_ui_contract`，需单独跑（CI 会跑全四个）。加 `--skip-tests` 会跳过内置测试，仅用于排障。

## 构建开关

`scripts/build_preview.py` 的 `write_build_flags()` 生成 `BuildFlags`：

| 参数 | 效果 |
| :-- | :-- |
| 默认 | `WATER_CONTROL=false`、`DIAGNOSTICS=false` —— 正式只读版 |
| `--field-water` | 编入 `client/fieldtest` 用水控制；构建后校验控制路由与 `WaterActivity`/`WaterControl` 的 dex 类型描述符**全部出现** |
| `--diagnostics` | 编入诊断日志（内部构建）；默认永远关闭 |
| `--no-diagnostics` | 显式关闭（兼容旧参数） |
| `--device-tests` | 测试 manifest 与 instrumentation |

只读版构建后，脚本验证 7 条控制路由与 field-test 类型描述符在 dex 中 occurrence **全为 0**——「不能发控制请求」是产物级可检查属性，不是口头声明。三配置（普通 / 用水 / 诊断）均已实测通过断言与 v2/v3 验签。

## 版本号处理与 `FIELD_VERSION_NAME` 的影响（只说明，不改）

- 版本真源是 `client/android/AndroidManifest.xml` 的 `versionCode` / `versionName`。当前工作区：`202` / `2.5.8.4`（未提交，由 2.5.8.3 递增用于真机覆盖安装）；已提交 HEAD：`199` / `2.5.8.1`；线上下载站：`197` / `2.5.7`。
- `scripts/build_preview.py` 顶部 `FIELD_VERSION_NAME = "2.5.6.11"` **只在 `--field-water` 且未传 `--version-name` 时**用于覆盖 manifest 的 versionName；versionCode 仍取 manifest+1（或 `--version-code`）。
- 影响：不带参数跑 `--field-water` 会产出 versionName 停留在 2.5.6.11 的**内部用水版**。它不会被当作正式版发布（正式版用不带 `--field-water` 的普通构建，直接读 manifest），但**若有人把用水版误传更新通道**，应用内更新/用户看到的版本号会出现显著倒退与混淆。规避方式：始终显式传 `--version-name` / `--version-code`，或用普通构建发版。**本次不修改该常量与发布逻辑**。

## 发布清单（用户明确要求，每次发版必须全做）

1. 构建 APK，跑核心断言；写 `delivery/update/update.json`（`version` / `versionCode` / `notes` / `url` / `bytes` / `sha256`）。
2. GitHub 同步：`git push`、`tag vX.Y.Z`、建 Release 上传 APK、更新 README（版本表 / 下载链接 / SHA-256 / 更新日志）。
3. 下载站同步：新 APK 复制到 `delivery/update/`，`update.json` 更新为新版本，重新发布 sites 站点（目录 `delivery/update`；每次重发布需用户当次明确同意）。
4. 发布后核对线上页面与 APK 直链均返回 200。
5. 同步更新 `CHANGELOG.md` 与 [`agent-handoff.md`](agent-handoff.md)；核对版本号与对应 Git Commit；检查无敏感信息写入文档。

分发链接的具体参数见 `README.md` 下载章节（去掉后会 403）。主源为 WorkBuddy 托管，备源为 GitHub Releases。网络安全约束见 [`security.md`](security.md)。

## 签名材料（只写约束，不写值）

签名密钥与口令不进仓库，通过本地文件或环境变量在构建时提供。文档不记录口令、密钥路径细节、证书指纹或任何签名材料值。换签名会让已装用户无法覆盖安装；保留备份由发布环境负责。

## CI

`.github/workflows/docs-ci.yml`：push / PR 时跑四个 Python 测试入口 + 关键文档存在性检查 + `check_doc_sync.py` 提示（不阻断）。CI 不构建 APK、不签名、不发布、不推送。
