# 构建与发布

## 工具链（实际）

- **无 Gradle**。Python 3 驱动，JDK 24（产出 Java 8 字节码），Android SDK 平台 36 / Build-Tools 36.0.0。
- `scripts/bootstrap_android.py`：从 Google 官方仓库下载并校验平台与 Build-Tools 到 `tools/android-sdk`。
- `scripts/build_preview.py`：编译 core + android（+ 可选 fieldtest）→ `aapt2` → `d8` → `zipalign` → `apksigner`，并写 `.build.json`（体积、SHA-256、逐文件源码哈希、权限清单）。
- 第三方依赖按 SHA-256 固定在 `tools/`：ZXing 3.5.3、PDFBox-Android 2.0.27.0、Bouncy Castle 1.72、支付宝 SDK 15.8.42（手工合并 AAR）。见 [`../client/THIRD_PARTY.md`](../client/THIRD_PARTY.md)。

## 常用命令

```bash
python scripts/bootstrap_android.py            # 首次准备 SDK
python scripts/test_core.py                    # 1418 项 core 断言
python scripts/test_build_config.py            # 构建开关 7 项
python scripts/test_diag_privacy.py            # 诊断隐私 651 项
python scripts/test_diag_files.py              # 日志轮转/上限/清空/过期清理实跑
python scripts/test_webview_policy.py          # WebView 守卫 6 项
python scripts/test_ui_contract.py             # UI 契约 291 锚点
python scripts/build_preview.py                # 普通只读版出包（默认跑前 5 个测试）
```

`build_preview.py` 默认调用 `test_core` / `test_build_config` / `test_diag_privacy` / `test_diag_files` / `test_webview_policy`；**不调用** `test_ui_contract`，需单独跑（CI 会跑全部六个）。加 `--skip-tests` 会跳过内置测试，仅用于排障。

## 构建开关

`scripts/build_preview.py` 的 `write_build_flags()` 生成 `BuildFlags`：

| 参数 | 效果 |
| :-- | :-- |
| 默认 | `WATER_CONTROL=false`、`DIAGNOSTICS=false` —— 正式只读版 |
| `--field-water` | 编入 `client/fieldtest` 用水控制；构建后校验控制路由与 `WaterActivity`/`WaterControl` 的 dex 类型描述符**全部出现** |
| `--diagnostics` | 额外编入高密度诊断事件（手势、渲染、逐次请求）；基础诊断页与白名单事件在所有构建里都有 |
| （无参数） | `DIAG_BASIC = true`：诊断页存在，运行期默认关，用户主动开启 |
| `--no-diagnostics` | 显式关闭（兼容旧参数） |
| `--device-tests` | 测试 manifest 与 instrumentation |
| `--version-name X.Y.Z[-beta.N]` | 覆盖 versionName；不传则用 manifest 的值 |
| `--version-code N` | 覆盖 versionCode；用水版不传时取 manifest+1 |
| `--beta` | 声明本次是预览版构建：名字必须是 `X.Y.Z-beta.N`，产物报告写 `"channel": "beta"` |
| `--allow-lower-version-code` | 允许 versionCode 不高于已发布清单（只用于内部构建，会打印 WARNING） |

只读版构建后，脚本验证 7 条控制路由与 field-test 类型描述符在 dex 中 occurrence **全为 0**——「不能发控制请求」是产物级可检查属性，不是口头声明。三配置（普通 / 用水 / 内部诊断）均已实测通过断言与 v2/v3 验签。

## 版本号规范（从 2.5.13 起执行）

| 用途 | 格式 | 例子 |
| :-- | :-- | :-- |
| 正式版 | `X.Y.Z` | `2.5.13` |
| 预览版 | `X.Y.Z-beta.N`，N 从 1 开始，不带前导零 | `2.5.13-beta.1` |
| 历史版（只读兼容） | `X.Y.Z.W` | `2.5.8.1`、`2.5.6.11` |

- 版本真源仍是 `client/android/AndroidManifest.xml` 的 `versionCode` / `versionName`。`build_preview.py` 的 `check_version_name()` 是唯一校验点：四段名、`beta.0`、`1.2.3-BETA.1`、`1.2.3.1-beta.1` 一律构建失败。
- **不按名字比新旧**：`2.10.0` 按文本排在 `2.9.0` 前面，`2.5.13-beta.2` 与 `2.5.13` 之间没有「自然顺序」。谁更新只由 `versionCode` 决定（`core/UpdateChannel`）。
- **版本名与版本代码各自独立编号**：`beta.N` 里的 N 只是同一版本内第几次预览，**不等于** versionCode；代码由发布者逐次加一。
- **分配规则**：每个发布包（正式或预览）占用一个唯一且比所有已发布包更大的整数；同一版本的全部 Beta 先占号，正式版取一个比这些 Beta 都大的号，因此**正式版总能覆盖安装此前的 Beta**。
- `check_version_code()` 在构建期读 `delivery/update/update.json` 与 `beta.json`，新包小于等于其中任一最大值直接失败——「装不上」的问题在出包时报，而不是在用户手机上报。
- 已核查的现状：线上正式版 `2.5.9` = 210（下载站与 `update.json` 一致）；工作区最新构建为 `2.5.13-beta.2` = 216；正式版 2.5.13 必须 ≥ 217。
- 包名 `io.qzxy.lite.preview` 与签名身份保持不变，覆盖安装不丢本地数据。签名材料只写约束，见本文末节。

## 双渠道更新清单

| 文件 | 渠道 | 现状 |
| :-- | :-- | :-- |
| `delivery/update/update.json` | 正式版 | 线上 2.5.9 / 210，字段与 URL 保持不变 |
| `delivery/update/beta.json` | 预览版 | 已按约定写在仓库里（2.5.13-beta.1 / 215），**尚未部署上线** |

- 两份清单字段相同：`version`、`versionCode`、`notes`、`url`、`bytes`、`sha256`；都必须指向真实存在、已按 SHA-256 与字节数核对过的 APK。
- 预览清单的线上路径约定与正式清单同目录（`…/beta.json?sharecode=…`）。地址常量在 `UpdateCheck.BETA_MANIFEST`，若部署位置不同改这一处。
- 部署顺序：先把 beta.json 指向的 APK 上传到下载站目录，再上传 beta.json。**在 beta.json 上线之前，App 打开预览渠道会读到 404，被当作「该渠道没有内容」而静默回退到正式渠道**——不会弹错，也不会推到任何不存在的包。
- 未发布的测试包不得写进 beta.json；`test_ui_contract.py` 会核对 beta.json 的版名格式、必填字段与 versionCode 高于正式清单。


## 发布清单（用户明确要求，每次发版必须全做）

1. 构建 APK，跑核心断言；写 `delivery/update/update.json`（`version` / `versionCode` / `notes` / `url` / `bytes` / `sha256`）。
2. GitHub 同步：`git push`、`tag vX.Y.Z`、建 Release 上传 APK、更新 README（版本表 / 下载链接 / SHA-256 / 更新日志）。
3. 下载站同步：新 APK 复制到 `delivery/update/`，`update.json` 更新为新版本，重新发布 sites 站点（目录 `delivery/update`；每次重发布需用户当次明确同意）。
4. 发布后核对线上页面与 APK 直链均返回 200。
5. **在 `AppChangelog.java` 顶部追加本版条目**（版本号、版本代码、发布日期、面向用户的 2–5 条说明），否则 `test_ui_contract.py` 会以「最新条目与 `update.json` 不一致」失败——这是「发版忘了写更新日志」的自动拦截。**预览版不进 `AppChangelog`**：那是已发布正式版的记录，Beta 的说明来自 `beta.json` 的 `notes`。
6. 同步更新 `CHANGELOG.md` 与 [`agent-handoff.md`](agent-handoff.md)；核对版本号与对应 Git Commit；检查无敏感信息写入文档。
7. **预览版另走一条**：`python scripts/build_preview.py --beta --field-water --version-code <新号>` → 把 APK 放进 `delivery/update/` → 写 `beta.json`（同字段，URL 指向该 APK）→ 上传 APK 后再上传 beta.json → 核对线上两个直链 200。`update.json` 与下载站首页的正式版信息此时**不动**。

分发链接的具体参数见 `README.md` 下载章节（去掉后会 403）。主源为 WorkBuddy 托管，备源为 GitHub Releases。网络安全约束见 [`security.md`](security.md)。

## 签名材料（只写约束，不写值）

签名密钥与口令不进仓库，通过本地文件或环境变量在构建时提供。文档不记录口令、密钥路径细节、证书指纹或任何签名材料值。换签名会让已装用户无法覆盖安装；保留备份由发布环境负责。

## CI

`.github/workflows/docs-ci.yml`：push / PR 时跑六个 Python 测试入口 + 关键文档存在性检查 + `check_doc_sync.py` 提示（不阻断）。CI 不构建 APK、不签名、不发布、不推送。
