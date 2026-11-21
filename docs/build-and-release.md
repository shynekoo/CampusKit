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
| `--analytics-endpoint https://…` | 把匿名统计服务地址写进 `BuildFlags.ANALYTICS_ENDPOINT`；不给则为空串（开关置灰、一个请求都不发）。只接受 `https://`，结尾斜杠会被去掉 |

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

## 双通道 × 三渠道更新清单

每个渠道各有一份正式清单和一份预览清单，字段相同：`version`、`versionCode`、`notes`、`url`、`bytes`、`sha256`；都必须指向真实存在、已按 SHA-256 与字节数核对过的 APK。

| 渠道 | 正式清单 | 预览清单 |
| :-- | :-- | :-- |
| WorkBuddy（默认） | `update.json?sharecode=…` | `beta.json?sharecode=…` |
| GitHub（可选） | `releases/latest/download/update.json` | `releases/download/preview/beta.json`（固定 `preview` tag，因 latest 排除预发布版本） |
| Gitee（可选，`gitee.com/sun-heyue/CampusKit`） | `releases/download/release/update.json`（固定 `release` tag，Gitee 没有 latest/download） | `releases/download/preview/beta.json` |

- 六个地址集中在 core 的 `UpdateSources`，测试与应用读同一份。地址常量不再放在 `UpdateCheck`。
- 清单里的 APK `url` 必须指向固定 tag/固定文件名（例：`releases/download/v2.5.9/kexin-xiaozhushou-2.5.9.apk`）；**不要**用 `releases/latest/download/<带版本号的文件名>`——发新版后旧文件名在 latest 下不存在，链接会 404。
- GitHub Release 资产下载 302 到带签名 query 的 CDN；Gitee 先 302 到 `attach_files/…`、再 302 到 `foruda.gitee.com/…?token=…&ts=…`。`UpdateUrls` 保留 Location 自带的参数、剥掉与上一跳同名（如 `sharecode`）的参数。改动这个文件后必须跑 `test_core.py`。
- 部署顺序：先上传 APK 资产，再用同名覆盖上传清单。清单未上线期间该渠道返回 404，被当作「该渠道没有内容」静默回退，**不影响另一渠道**。
- Gitee 侧：公开仓库、固定 `release` / `preview` 两个 tag，每次发版替换 tag 内同名资产（`update.json` / `beta.json` / APK）。公开仓库的附件匿名可下（实测 302 到 `foruda.gitee.com` 后 200，无需登录），私有仓库不行。
- 未发布的测试包不得写进任何清单；`test_ui_contract.py` 会核对 beta.json 的版名格式、必填字段与 versionCode 高于正式清单。
- 已安装旧版 App 不会自动识别 GitHub / Gitee 渠道（旧包只有 WorkBuddy 地址）。若 WorkBuddy 站点不可发布，用户需手动安装一次带「更新渠道」设置的新版本。


## 发布清单（用户明确要求，每次发版必须全做）

1. 构建 APK，跑核心断言；写 `delivery/update/update.json`（`version` / `versionCode` / `notes` / `url` / `bytes` / `sha256`）。
2. GitHub 同步：`git push`、`tag vX.Y.Z`、建 Release 上传 APK、更新 README（版本表 / 下载链接 / SHA-256 / 更新日志）。
3. 下载站同步：新 APK 复制到 `delivery/update/`，`update.json` 更新为新版本，重新发布 sites 站点（目录 `delivery/update`；每次重发布需用户当次明确同意）。
4. 发布后核对线上页面与 APK 直链均返回 200。
5. **在 `AppChangelog.java` 顶部追加本版条目**（版本号、版本代码、发布日期、面向用户的 2–5 条说明），否则 `test_ui_contract.py` 会以「最新条目与 `update.json` 不一致」失败——这是「发版忘了写更新日志」的自动拦截。**预览版不进 `AppChangelog`**：那是已发布正式版的记录，Beta 的说明来自 `beta.json` 的 `notes`。
6. 同步更新 `CHANGELOG.md` 与 [`agent-handoff.md`](agent-handoff.md)；核对版本号与对应 Git Commit；检查无敏感信息写入文档。
7. **预览版另走一条**：`python scripts/build_preview.py --beta --field-water --version-code <新号>` → 把 APK 放进 `delivery/update/` → 写清单 → 上传 APK 后再上传清单 → 核对线上两个直链 200。`update.json` 与下载站首页的正式版信息此时**不动**。
   - **一份 `beta.json` 服务不了三个渠道**：清单里的 `url` 必须指向该渠道自己的下载地址，所以按渠道各写一份——`delivery/update/beta.json`（WorkBuddy，带 `sharecode`）、`beta-github.json`（`github.com/shynekoo/CampusKit/releases/download/preview/<ASCII 名>.apk`）、`beta-gitee.json`（`gitee.com/sun-heyue/CampusKit/releases/download/preview/<ASCII 名>.apk`）。三者除 `url` 外字段完全相同，`bytes` / `sha256` 取自同一次构建的 `.build.json`。
   - 上传时文件必须**叫 `beta.json`**（GitHub / Gitee 的固定资产名），所以在临时目录里把对应变体复制成 `beta.json` 再传：GitHub 用 `gh release upload preview … --clobber`；Gitee 先 `DELETE …/releases/{release_id}/attach_files/{attach_file_id}` 删旧附件（`204` 才算删掉，不要用不带 `release_id` 的路径，那会 404），再 `POST …/releases/{release_id}/attach_files` 传 APK 和 `beta.json`。
   - 只传 APK 不传清单 = 用户端永远看不到更新：客户端读的是清单里的 `versionCode`，线上清单停在旧版就等于「已是最新」。
   - WorkBuddy 站点若因发布账号不匹配（`SITES_ACCOUNT_MISMATCH`）发不出去，预览版就先只发 GitHub + Gitee，并告知用户把「更多 → 更新渠道」切到这两个之一、同时打开「接收预览版更新」；等切回原发布账号再补发 WorkBuddy 的 `beta.json`。

分发链接的具体参数见 `README.md` 下载章节（去掉后会 403）。WorkBuddy 是默认渠道，GitHub / Gitee 是可切换的备选：换 WorkBuddy 账号后仍可切到 GitHub 继续收更新，不被站点所有权卡住；境内下载慢时切 Gitee。网络安全约束见 [`security.md`](security.md)。

## 签名材料（只写约束，不写值）

签名密钥与口令不进仓库，通过本地文件或环境变量在构建时提供。文档不记录口令、密钥路径细节、证书指纹或任何签名材料值。换签名会让已装用户无法覆盖安装；保留备份由发布环境负责。

## CI

`.github/workflows/docs-ci.yml`：push / PR 时跑六个 Python 测试入口 + 关键文档存在性检查 + `check_doc_sync.py` 提示（不阻断）。CI 不构建 APK、不签名、不发布、不推送。
