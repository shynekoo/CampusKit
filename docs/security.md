# 安全约束（架构级）

> 仅记录必要的架构与安全约束。不公开凭据、用户数据、私有抓包记录、签名材料或具体密钥值。详细审计证据见 [`security-audit-network-sdk.md`](security-audit-network-sdk.md) 与 [`security-audit-payment-water.md`](security-audit-payment-water.md)。

## 数据与凭据

| 数据 | 处理方式 |
| :-- | :-- |
| 水控 / 教务登录凭据 | 应用私有目录，AES-256-GCM 加密，密钥由 Android Keystore 保护；只在请求对应官方接口时使用 |
| 教务查询密码 | 仅在查询过程中提交学校教务系统；不写入加密存储、不经第三方服务器 |
| 课表与设置 | 应用私有目录，`allowBackup=false`，只在用户手动导出时生成备份 |
| 诊断信息 | 运行期默认关，用户主动开启；只写白名单字段（无自由文本、无 SDK 原文、无请求正文），私有目录、不联网；导出与复制读的是同一份已脱敏内容 |
| 支付 | 交给支付宝官方 SDK 与收银台；应用不接触支付凭据 |

权限仅 5 项：网络×3、相机（扫码）、安装 APK（更新，用户确认）。未申请定位 / 通讯录 / 短信 / 存储 / 蓝牙。

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
- **WebView**：6 处 WebView 页面全部 HTTPS-aware（由 `test_webview_policy.py` 静态守卫）；禁混合内容；拦截导航与新窗口；`addJavascriptInterface` 零命中。学生评价的表单页先把学校的菜单壳（`index_initMenu.html`）作为顶层文档加载，再把模块装进壳内的全屏宿主 —— 宿主仍是学校 HTTPS 文档，不引入新导航来源；评分与提交都在学校页面上完成，应用自身不调用评教的保存/提交端点。

## 支付与用水控制

- 扣费 / 设备控制请求**不自动重发**：断连或超时进入 `RECONCILE`「待核实」，恢复后先查服务端订单状态，不盲目重试（`UseSession`）。
- 支付/用水**只信服务端结果**，不采信客户端 SDK 回调作为交易成功依据。
- 命令发出前持久化待核实记录（`PendingUse`），进程被杀 / Activity 销毁后恢复进核实流程。
- 重复点击 / 并发 start 由状态机 + 单次请求 + UI 门控拦截（core 断言覆盖）。
- **只读版隔离**：正式构建不含 `client/fieldtest`，构建后 DEX 扫描 7 条控制路由 occurrence 全为 0——这是产物级检查，不是源码声明。
- 支付宝 exported 回调 Activity 属 SDK 类，有请求号校验；App 不以其回调判定成功。

## 应用内更新

`UpdateManifest` 要求 `version` / `versionCode` / `notes` / `url` / `bytes` / `sha256` 字段合法且 `sha256` 小写 64 位；下载必须 HTTPS、逐跳受控，落地前校验字节数与 SHA-256，并比对 APK 包名 / versionCode / 签名身份（比对逻辑存在，具体指纹值不记录在文档）。最后一步必须由用户点「安装」。

预览渠道（`beta.json`）**不降低任何一项**：同一份 `UpdateManifest` 校验、同一套逐跳 HTTPS 策略、同一套字节数 / SHA-256 / 包名 / versionCode / 签名证书比对都在下载链路上，与渠道无关；清单缺字段、指向明文 HTTP、或包里任何一项对不上，一律删除下载而不安装。

- 版本名只用于显示与分渠道，**新旧一律由 `versionCode` 决定**（`core/UpdateChannel`）。不比较字符串，因此不存在「`2.10` 比 `2.9` 旧」这类误判。
- 渠道开关只影响「是否读取预览清单」，不构成权限提升：关闭后即使已安装预览版，也不会被推回更旧的正式版（候选必须严格大于当前 `versionCode`）。
- 构建期另有一道：`scripts/build_preview.py` 拒绝 `versionCode` 不高于已发布清单的产物，避免产出装不上的包。

## 快照与页面

敏感页面通过 `SnapshotPolicy` deny-list 不进入返回动画快照，离开时清除残留；教务密码页设置 `FLAG_SECURE`。`Page.allowPageSnapshot()` 默认读策略，新页面涉及敏感内容时须显式处理。

## 文档红线

- 不写 Token、密码、Cookie、密钥口令、签名指纹、真实账号/学号/手机号、私有抓包记录或抓包截图。
- 审计文档中只写「结论 + 证据位置（类名/行号）+ 未验证项」，不贴真实响应体。
- `docs/screenshots/` 是 README 使用的界面截图，保持原样；新增截图前确认无账号、余额、姓名等敏感信息。
