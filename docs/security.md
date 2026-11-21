# 安全约束（架构级）

> 仅记录必要的架构与安全约束。不公开我方凭据、用户数据、私有网络调试记录与签名材料。仍然存在的第三方客户端常量单独登记在「已知残留风险」一节，不视为本项目的秘密。详细审计证据见 [`security-audit-network-sdk.md`](security-audit-network-sdk.md) 与 [`security-audit-payment-water.md`](security-audit-payment-water.md)。

## 数据与凭据

| 数据 | 处理方式 |
| :-- | :-- |
| 水控 / 教务登录凭据 | 应用私有目录，AES-256-GCM 加密，密钥由 Android Keystore 保护；只在请求对应官方接口时使用 |
| 教务查询密码 | 仅在查询过程中提交学校教务系统；不写入加密存储、不经第三方服务器 |
| 课表与设置 | 应用私有目录，`allowBackup=false`，只在用户手动导出时生成备份 |
| 诊断信息 | 运行期默认关，用户主动开启；只写白名单字段（无自由文本、无 SDK 原文、无请求正文），私有目录、不联网；导出与复制读的是同一份已脱敏内容 |
| 支付 | 交给支付宝官方 SDK 与收银台；应用不接触支付凭据 |
| 匿名活跃统计 | 默认关闭；开启后只发随机安装 UUID 与版本号，服务端 HMAC 后入库、保留 90 天；可随时关闭并请求删除（见 [`analytics.md`](analytics.md)） |

权限仅 4 项：网络访问、网络状态、相机（扫码）、安装 APK（更新，用户确认）。未申请定位 / 通讯录 / 短信 / 存储 / 蓝牙；统计功能没有新增任何权限。

## 诊断日志的安全边界

- **字段白名单**：只有 `DiagRedact` 列出的键能进文件；未知键在写入时被丢弃（不是先记录再擦除）。
  没有 `message` / `body` / `text` 一类能承载自由文本的字段，课程名、成绩、请求体没有入口。
- **异常只留类型与安全堆栈**：`类名.方法名(文件:行号)`，≤12 帧，异常消息不写入。
- **SDK 原文不再入库**：2.5.8 移除了 `Log.setupLogCallback` 接入。回调是否回来、状态码是几，
  改用结构化事件表达（`AUTH auth.returned status=9000`）。
- **不联网、不上传**：没有任何上报代码；文件在 `files/diag/`，导出由用户通过系统文件选择器决定去向。
- **默认关**：`DiagPolicy.ENABLED_BY_DEFAULT = false`，只有显式 `"on"` 才记录；关闭后连普通事件都不写。
- **发布版只带基础事件**：`DIAG_BASIC` 恒开；逐帧/逐请求的细节事件由 `--diagnostics` 编入。
- 静态守卫 `scripts/test_diag_privacy.py` 检查调用点字段键、模块/代码/原因文案、已移除入口与导出路径；
  值层面的脱敏由 `CoreTests` 断言。详见 [`diagnostics.md`](diagnostics.md)。

## 网络安全边界

- **HTTPS only**：manifest `usesCleartextTraffic=false`；应用自身连接禁用自动重定向，逐跳校验 HTTPS、跳数上限、环路与跨主机 query 清理（`UpdateUrls`）；WebView 准入统一走 `TrustedUrlPolicy`（scheme + host + port）。
- **固定依赖**：ZXing / PDFBox / Bouncy Castle / 支付宝 AAR 以 SHA-256 锁定，构建时校验；手工合并 AAR。
- **WebView**：5 处 WebView 页面全部 HTTPS-aware（由 `test_webview_policy.py` 静态守卫）；禁混合内容；拦截导航与新窗口；`addJavascriptInterface` 零命中。学生评价**不使用 WebView**：问卷片段由 `EduEvaluationForm` 当纯文本读出来，题目与选项在原生控件上渲染，因此这条链路不引入任何网页执行环境。
- **评教的写入边界**：`EduClient.checkPath()` 只按名字放行五个 `/xspjgl/` 端点（列表查询、评价情况统计、问卷片段、暂存、提交）。写端点只接收 `EduEvaluationForm.body()` 按读者实际选择拼出的请求体：应用不预填、不代选、不批量代评，提交前必须由读者在二次确认框里确认，且只提交当前这一个教学班。评语只存在内存里，不写盘、不进诊断。

## 匿名统计的安全边界

- **同意先行**：`AnalyticsPreferences.consented` 默认 false；`installId()` 只读不建，标识只在读者按下
  开启之后的 `ensureInstallId()` 里生成，升级安装不会自动开启。
- **请求体只有两样**：`installId`（本机 `UUID.randomUUID()`）与 `version`。日期由服务端按 UTC+8 现算，
  客户端不上传时间；不读 ANDROID_ID / IMEI / OAID / MAC，不带学号与任何业务数据。
- **入库不是 UUID**：服务端先 `HMAC-SHA256`（密钥走 `wrangler secret`，不入库、不进仓库），
  表里只有 `day` / `anon_id` / `app_version`，主键 `(day, anon_id)` + `INSERT OR IGNORE`。
- **密钥缺失即失败关闭**：Worker 找不到 `ANALYTICS_HMAC_SECRET` 直接 503，绝不退化成明文入库。
- **不进诊断日志**：只记状态码与判定；`scripts/test_analytics.py` 静态检查统计模块的 `Diag.*`
  调用里不出现标识或请求体。
- **删除可执行**：关闭开关或「数据与隐私 → 删除已上传的匿名记录」会请求删掉该标识的全部历史；
  删不掉则保留待删除标记，下次进前台继续（标识在删成功前不丢）。
- **故障不影响业务**：后台线程、5s/8s 超时、不跟随重定向、失败不重试不弹窗；构建期没给
  `--analytics-endpoint` 时整条链路不可用（开关置灰）。

## 支付与用水控制

- 扣费 / 设备控制请求**不自动重发**：断连或超时进入 `RECONCILE`「待核实」，恢复后先查服务端订单状态，不盲目重试（`UseSession`）。
- 支付/用水**只信服务端结果**，不采信客户端 SDK 回调作为交易成功依据。
- 命令发出前持久化待核实记录（`PendingUse`），进程被杀 / Activity 销毁后恢复进核实流程。
- 重复点击 / 并发 start 由状态机 + 单次请求 + UI 门控拦截（core 断言覆盖）。
- **只读版隔离**：正式构建不含 `client/fieldtest`，构建后 DEX 扫描 7 条控制路由 occurrence 全为 0——这是产物级检查，不是源码声明。
- 支付宝 exported 回调 Activity 属 SDK 类，有请求号校验；App 不以其回调判定成功。

## 已知残留风险（2026-11-21 公开仓库审计）

这一节记录**仍存在、且已知无法在本仓库内消除**的问题。写在这里不是「已解决」，是「不许再假装没看见」。

### R1 慧生活支付宝授权串（`AlipayLogin.AUTH_INFO`）

- **定性**：慧生活客户端里的固定常量，12 字段、620 字符，其中 `sign` 是**他们的**应用私钥对前 11 个固定字段做的 RSA2 签名。它随官方 APK 分发，任何对官方安装包做静态分析的人都拿得到，**不是本项目的秘密，也不是本项目的凭据**。
- **能力边界**：只能以「慧生活798」的名义发起一次授权，拿回**授权人自己**账号的 `auth_code`；不能换取他人身份，不能绕过用户在支付宝里的确认。危害是冒用他方 appid 的合规风险与品牌混淆，不是用户数据泄露。
- **为什么没删**：删源码不会让它失效（官方包里同样存在），却会让慧生活登录整条链路停摆，连带未登录用户无法使用用水功能。在拿到「慧生活服务端/H5 发起授权」的可行路径之前，删除只是把风险换成不可用，并不减少泄露面。
- **唯一真正的处置**：慧生活轮换应用私钥并移除客户端硬编码。本项目无法单方面吊销。
- **待验证的替代方案**：若慧生活提供 HTTPS 授权入口（由其服务端生成授权串并跳转支付宝），即可移除此常量且保留功能。该入口存在性**未验证**，不作实现。

### R2 趣智校园请求签名盐（已于 2026-11-21 删除）

- 源码侧 `RechargeSignature` / `NativeSigningV6528` 已删除：两者在应用中**没有调用方**（充值入口早在 1.4.x 移除），只被测试引用。
- 该盐值此前已随公开发布的 APK 扩散，删除源码**不能**使已泄露的副本失效；但继续把它留在仓库里没有任何收益。
- **分发面清理（2026-11-21）**：GitHub `preview` 删除 12 个、Gitee `preview` 删除 7 个带盐旧预览包，两个渠道的 `preview` 现只剩 `2.6.0-beta.18` 与清单；正式版 Release 与两个 `preview` Release 本身均保留。**这只是停止继续分发，不等于材料失效**——此前下载过的人手里仍然有可用的值。
- **仍然带盐的地方**（明列，避免误以为已清干净）：
  - 8 个正式版 Release 的 APK（`v2.5.0` … `v2.5.8.1`，以及作为当前正式源的 `v2.5.9`）——保留是版本记录与更新渠道的需要；
  - 14 个 tag 的源码归档（见 R4）；
  - Git 历史中 125 / 127 个提交；
  - 下载站目录里仍在提供的 11 个旧 APK（重发布时可剔除，`2.5.9` 必须留到正式版换代）。
- `2.6.0-beta.18`（235）起产物不再包含该值，类集合比对确认只少了这两个签名类。
- **不得重新引入**：`scripts/test_secrets.py` 对这两个盐值做硬阻断，源码或 APK 里出现即构建失败；产物扫描不通过时 `build_preview.py` 会删掉刚签好的 APK，避免「构建失败但文件还在」被误传。

### R3 会话 token 进入 `alipays://` 深链（`AlipayWaterLink`）

- 用水跳转按趣智平台的格式把 `loginCode` 放进 URI query。`alipays` 是隐式 scheme，注册同名 scheme 的应用可以截获整条 URI。
- 这是平台协议决定的，改格式功能就坏。现有约束：任何路径都不打印该 URI（`DiagRedact` 白名单 + `test_diag_privacy.py` 守卫），不放入剪贴板、不做分享。

### R4 旧 tag 的源码归档与 Git 历史（2026-11-21 决定：不重写）

- GitHub 的 `archive/refs/tags/<tag>.zip`、`…/tar.gz` 与 Gitee 的同类归档**不是我们上传的资产**，无法删除；它们按 tag 指向的提交现场打包。14 个 tag（含 `preview`，指向 `8cf8516`）全部指向删除动作之前的提交，因此归档里仍有那两行常量。
- Git 历史同样：**125 / 127 个提交**含该值，只有 `d8f7c5d`（删除）与 `85ef5d4` 干净。旧提交里的 `analysis/recharge-integration.md` 也仍可通过 raw 链接读到。
- **为什么不重写**：值早已在公开 APK 里，重写历史不能让任何一份已下载的副本失效；代价却是 127 个提交哈希全变、14 个 tag 作废、9 个 Release 与 tag 的关联需重建、Gitee 必须同步（只清一边等于没清）。收益接近零、破坏面极大，故不改写、不强制推送。
- **若将来仍要清**：先 `git bundle create --all` + `clone --mirror` 备份，再 `filter-repo --replace-text`，随后重建全部 tag、逐个 Release 重新挂载资产、Gitee 同步重推，最后全渠道复核。备份与步骤见 `tmp/security-cleanup-plan-2026-11-21.md`。

### 已检查并确认「不需要改」的项

- 学校名称、教务接口路径、趣智/支付宝的 appId 与域名：公开信息，保留。
- `sharecode`（更新清单下载码）：设计上就要公开，无凭据价值；它只是下载入口。
- `server/analytics-worker/wrangler.toml` 里的 D1 `database_id`：资源标识，不是密钥，无法据此读写。

## 应用内更新

`UpdateManifest` 要求 `version` / `versionCode` / `notes` / `url` / `bytes` / `sha256` 字段合法且 `sha256` 小写 64 位；下载必须 HTTPS、逐跳受控，落地前校验字节数与 SHA-256，并比对 APK 包名 / versionCode / 签名身份（比对逻辑存在，具体指纹值不记录在文档）。最后一步必须由用户点「安装」。

- 更新渠道（WorkBuddy / GitHub / Gitee，更多页选）只决定「从哪个主机读清单」，**不构成权限提升**：三处清单都过同一套 `UpdateManifest` 校验，GitHub 的 `sharecode` 不存在、WorkBuddy 的也不因换渠道而泄露。GitHub Release 资产会 302 到带签名 query 的 CDN，Gitee 会 302 到 `gitee.com/…/attach_files/…` 再 302 到 `foruda.gitee.com/…?token=…&ts=…`；`UpdateUrls` 保留 Location 自带的参数，但剥掉与上一跳同名（如 `sharecode`）的参数，防止查询参数跨 host 泄漏；逐跳 HTTPS、跳数上限与防环保持不变。Gitee 的 token 是服务端签发的临时下载凭据，只在那一跳内有效，不来自也不写入本地存储。
- 预览通道（`beta.json`）**不降低任何一项**：同一份 `UpdateManifest` 校验、同一套逐跳 HTTPS 策略、同一套字节数 / SHA-256 / 包名 / versionCode / 签名证书比对都在下载链路上，与渠道无关；清单缺字段、指向明文 HTTP、或包里任何一项对不上，一律删除下载而不安装。

- 版本名只用于显示与分渠道，**新旧一律由 `versionCode` 决定**（`core/UpdateChannel`）。不比较字符串，因此不存在「`2.10` 比 `2.9` 旧」这类误判。
- 渠道开关只影响「是否读取预览清单」，不构成权限提升：关闭后即使已安装预览版，也不会被推回更旧的正式版（候选必须严格大于当前 `versionCode`）。
- 构建期另有一道：`scripts/build_preview.py` 拒绝 `versionCode` 不高于已发布清单的产物，避免产出装不上的包。

## 快照与页面

敏感页面通过 `SnapshotPolicy` deny-list 不进入返回动画快照，离开时清除残留；教务密码页设置 `FLAG_SECURE`。`Page.allowPageSnapshot()` 默认读策略，新页面涉及敏感内容时须显式处理。

## 文档红线

- 不写 Token、密码、Cookie、密钥口令、签名指纹、真实账号/学号/手机号、私有网络调试记录或调试截图。
- 审计文档中只写「结论 + 证据位置（类名/行号）+ 未验证项」，不贴真实响应体。
- `docs/screenshots/` 是 README 使用的界面截图，保持原样；新增截图前确认无账号、余额、姓名等敏感信息。
