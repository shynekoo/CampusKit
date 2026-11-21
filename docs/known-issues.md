# 已知问题与风险

> 只记录有据可查的限制与未验证项。未执行 ≠ 通过。

## 发布状态

- **当前正式版为 2.6.0**（versionCode 236，2026-11-21 发布）：`delivery/update/update.json` 与三渠道清单已同步。上一版为 2.5.9（210）。
  - 正式包为 `--field-water` 用水版：2.5.7 起曾误用只读构建，趣智校园扫码跳转支付宝的分支被编译期移除；2.5.9 重建恢复。以后发版必须显式确认构建开关，勿依赖默认值。
  - `2.5.8.1`（209）曾短暂发布，但四段版名被旧客户端的 `UpdateManifest.VERSION` 正则判为无效，检查不到更新；2.5.9 起清单改用三段版名并把正则放宽到 4 段。发布清单的版名字段必须是 2–4 段纯数字。
  - 用水真机验证：发布者已确认扫码可跳转；真实出水/关水/账单仍未对生产服务测试。
- 2.5.8 把 2.5.8.1–2.5.8.9 合并成一个正式版本号，含诊断日志重构（结构化事件、正式版带诊断页）。
  **2.5.8 发版时未经真机验收**：底栏/转场/跨周滑页、诊断页的开关与导出、学业情况展开均只有静态守卫与
  构建级证据；发布公告的「已知问题」已如实写明。真机验收清单见 [`real-device-acceptance.md`](real-device-acceptance.md)。
- 2.5.8.2–2.5.8.6 已提交（学业情况查询、外观修复、闪退修复、层级排版、课程明细展开）。普通版 APK 已构建并验签，但**未建 tag / Release、未发版、未同步下载站**。
- **2.6.1-beta.1（237）预览已发 GitHub `preview` 与国内下载站，Gitee `preview` 未上传**（本机没有可用的 Gitee 令牌）：Gitee 渠道线上清单仍是 beta.18，选该渠道的用户暂时收不到这次预览；仓库内 `beta-gitee.json` 已按 237 写好，拿到令牌后直接传即可。正式版 2.6.1 未发布，等真机确认正常用水后再执行。
- 真机验收 [`real-device-acceptance.md`](real-device-acceptance.md) 全部待勾选；未完成前不得发布。
- **匿名使用统计：服务已部署，但地址在国内不可达，功能暂停启用**（2026-11-21）。代码已在
  `main`（`AnalyticsReporter` / `AnalyticsPreferences` / `core/Analytics*` / `server/analytics-worker/`），
  Worker 已上线到 `campuskit-analytics.campuskit.workers.dev`，D1 库、表、HMAC 密钥与每日清理 `cron`
  均就位，`wrangler.toml` 里是真实 `database_id`。但 `*.workers.dev` 在国内按 SNI 被封，用户侧连不上，
  所以出包不传 `--analytics-endpoint`：设置页显示「统计服务尚未配置」，开关置灰，一个请求都不发。
  重启条件与验证方法见 [`analytics.md`](analytics.md)。

## 已整改：实验性协议探测入口（2.3.6 – 2.6.0 的发布包内）

**2.6.1-beta.1 起，正式与预览包都不再包含该入口；旧版本仍在分发，风险按其存在，见下。**

- **原位置**：`client/fieldtest/src/io/qzxy/lite/preview/LifeProtocolProbe.java`，入口为「慧生活 · 用水」设备详情 ⓘ → 诊断… →「协议探测（会尝试开启一次）」。
- **原行为**：按顺序尝试多种请求形态，逐个记录服务端回答，命中即停。**其中包含带实际控制效果的开启请求**（`cnt=1`），并非纯读操作。
- **影响**：在服务端接受该请求形态的设备上，用户点一次即可能真的开启一次用水，并在平台侧产生计费；不点则不会自发发出请求。
- **整改（2.6.1-beta.1）**：传输拆成 `LifeDeviceHttp`（正常用水只依赖它）；探测类与探测菜单移入 `client/fieldtest/experimental`，默认不编译，仅 `--experimental-probe` 的本地隔离构建才编入；`build_preview.py` 对产物做双向断言（发布包必须没有探测类与 `dev/mode/start`，实验包必须有）。静态守卫 `scripts/test_probe_boundary.py` 已并入 CI 与出包预检。
- **旧版本仍受影响**：`2.3.6` 至 `2.6.0` 的已发布 APK 仍在三渠道可下载，其入口照旧可用。源码不删除，按 MPL-2.0 由各 tag 归档提供；下载页与发布说明已提示不要使用该入口。

## 未验证事项（本环境无法执行）

| 项 | 状态 |
| :-- | :-- |
| 真机 UI / 手势 / A3 就地刷新视觉、滚动位置、跨天与周次 | 未实测（静态守卫 240 锚点已过，不等于真机通过） |
| 「数据与隐私」「使用公告」的排版、系统大字体、320dp 换行、深浅色对比 | 未实测（只做了构建级与静态守卫） |
| 旧版覆盖升级（凭据/课表保留） | 未实测 |
| 匿名统计的真实上报、断网补报、关闭删除、覆盖升级标识不变、卸载重装、服务端异常 | 未实测（服务地址国内不可达，功能暂停，见下） |
| 学业情况页在真机的排版、闪退修复、登录态复用、课程明细展开 | 未实测 |
| 用水真实出水/关水/账单、进程被杀恢复 | 未对生产服务测试（安全约束禁止） |
| 支付宝 SDK 与 MobSF 扫描 | 未做；扫描结果也需人工复核 |
| 签名轮换、旧 Android 版本、异常 APK、FLAG_SECURE 真机效果 | 未实测 |

## 隐私文案的发布信息（已定）

- `LegalText.CONTACT_EMAIL` = `shynekoo@outlook.com`，与「意见反馈」里公开的地址一致（`FeedbackActivity.EMAIL`）；`LegalText.EFFECTIVE_DATE` = `2026年11月19日`。`test_ui_contract.py` 会拦住残留的 `【…】` 占位符，并校验两处邮箱相同。改文案时记得同时改生效日期。
- 支付宝 SDK 内含 `com.alipay.apmobilesecuritysdk` 等自有 APM 组件（随 AAR 一起编入），其数据行为受支付宝自身规则约束；隐私文案以「第三方组件以当前版本实际集成为准」概括，**按发布者决定不点名**。若日后监管或上架要求逐项披露，需重新核对这一节。
- `ACCESS_WIFI_STATE` 已于 2.5.8.6 从清单移除（无任何 `WifiManager` 调用），隐私文案第 5 节同步改写；`ACCESS_NETWORK_STATE` 保留，因为支付宝 SDK 有 8 个类引用 `ConnectivityManager`。

## 已知技术限制

- `FIELD_VERSION_NAME` 已随版本号规范化**移除**：`--field-water` 不再套一个写死的旧版本名，默认沿用 manifest 的 `versionName`，只有 `--version-name` / `--version-code` 能覆盖，且都要过 `check_version_name()` 的格式校验（`X.Y.Z` 或 `X.Y.Z-beta.N`）。详见 [`build-and-release.md`](build-and-release.md)。
- 教务导入只适配河北工程大学（网页表格 + 七列文字 PDF）；扫描版 PDF 明确失败，其他学校需改解析规则。
- 无 Gradle：IDE 一键运行不可用；加依赖需手工合并 AAR 并校验 SHA-256。
- 沙箱内 adb 起不来：按钮/页面恢复只能做到构建级确认（dex 中存在路由与 Activity），不是真机确认。
- 显式 `--diagnostics` 构建产出与正式版同名 APK，误传会污染更新通道；发版务必用不带该参数的普通构建。
- 诊断只覆盖**未捕获的 Java 异常**：ANR、native crash、系统杀进程、厂商 ROM 直接杀进程都不会留下 `CRASH` 记录。
- 关闭诊断记录时**不写任何内容**（包括崩溃）。要留下崩溃现场，必须在复现前先打开开关。
- 日志不包含课程内容、账号、请求体，因此「某门课导入后名字错了」这类问题无法只靠日志判断，仍需用户描述。
- 诊断页的筛选/展开/导出只做了静态守卫；长日志滚动、真机导出与另一台设备读取尚未实测。
- 日志轮转与保留规则（256 KB/文件、1 MB 总量、7 天清理、清空）已由 `scripts/test_diag_files.py` 在 JVM 上实跑，
  但「应用被杀时正在写」这类时序仍只有真机能验；`prepare()` 期间对超限文件做裁剪，是唯一会重写整个文件的路径。
- 构建测试循环里不要用 `| head` 截断管道：SIGPIPE 会中断 Python 导致 BuildFlags 读到旧值；重定向到文件即可。

- **匿名统计的服务地址国内不可达，功能暂停启用**（2026-11-21 实测）：`server/analytics-worker/` 已部署到
  `https://campuskit-analytics.campuskit.workers.dev`（D1、表、HMAC 密钥、每日清理 cron 均就位），但
  `*.workers.dev` 在国内被**按域名（SNI）封锁**——同一 Cloudflare 边缘 IP `104.21.8.221` 换 SNI 为
  `www.cloudflare.com` 返回 `200`，换回 `*.workers.dev` 立即被重置；本机 DNS 解析不到，Chrome 直连
  `/health` 报 `Failed to fetch`。因此代码虽已在 `main`，出包**不传** `--analytics-endpoint`
  （设置页显示「统计服务尚未配置」，一个请求都不发）。重新启用前必须先有绑定到 Cloudflare 的自定义域，
  并**先验可达再出包**，方法见 [`analytics.md`](analytics.md)。**不要把带代理的 `curl` 通当作用户能通。**

## 文档维护注意

- 历史段落（本文件、`docs/agent-handoff.md`、`CHANGELOG.md` 的旧版本记录）里的断言数是当时提交的实际值，**不是过时错误**，不要为了统一而改成当前值；只有 README 的「当前值」需与实测一致。
- `docs/back-gesture.md` 标题带 v2.5.6.14，为当时的实现说明；行为若有变更需增量更新，不要整篇重写。

## 发现后如何处理

确认属于文档缺陷 → 按 `AGENTS.md` 工作流增量修复；属于未验证事项 → 保留在本文件，等真机实测后再更新状态；属于业务缺陷 → 开 Issue，不在文档里写临时结论。
