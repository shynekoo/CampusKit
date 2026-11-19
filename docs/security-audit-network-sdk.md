# CampusKit 安全审计记录：全局网络与第三方 SDK（S6）

- 基准版本：本地 2.5.8.1（提交 `f7ac199` 之后）
- 审计日期：2026-11-19
- 三层范围：Java 源码 / Android 配置 / 构建产出的第三方依赖
- 方法：静态全文检索 + 构建配置核对 + 已有 SDK 反编译源复核。**未对任何真实服务做未授权安全测试。**

## 结论摘要

| 审计层 | 检查项 | 结论 |
| --- | --- | --- |
| Java 源码 | 无条件信任证书（X509TrustManager / checkServerTrusted 空实现） | 未发现 |
| Java 源码 | HostnameVerifier 绕过 / setDefault* 全局覆盖 | 未发现（应用代码） |
| Java 源码 | SSL 错误 handler.proceed() 放行 | 未发现——5 处全部 cancel() |
| Java 源码 | addJavascriptInterface（原生桥暴露） | 未发现 |
| Java 源码 | 重定向跟随 | 未发现风险——全部 setInstanceFollowRedirects(false) |
| Java 源码 | WebView 明文/域名准入 | 修复 1 处——EduLoginActivity 原仅校验 host，未校验 HTTPS |
| Android 配置 | 明文流量 / 备份 / 调试 | 未发现——usesCleartextTraffic=false、allowBackup=false、无 debuggable |
| Android 配置 | 组件导出 | 符合预期——仅启动页与 SDK 回调页 exported，其余 false |
| 构建产物 | 第三方依赖 | 已知且固定——支付宝 SDK / ZXing / PDFBox，均 SHA-256 锁定 |

## 一、Java 源码层

### 1. 危险 API 检索（应用代码：`client/core`、`client/android`、`client/fieldtest`）

全文检索以下模式，**应用自身代码零命中**：

- `X509TrustManager`、`checkServerTrusted`、`setDefaultSSLSocketFactory`
- `setDefaultHostnameVerifier`、`HostnameVerifier` 自定义实现
- `addJavascriptInterface`
- `onReceivedSslError` 中的 `handler.proceed()`

命中的安全用法：

- `onReceivedSslError`：5 个 WebView（EduLogin / EduPortal / EduQuery / SchoolSite / TimetableWeb）全部 `handler.cancel()`，证书错误一律中止。
- `setInstanceFollowRedirects(false)`：`HttpForm`、`EduClient`、`LifeAuthApi`、`LifeDeviceApi`、`UpdateCheck`、`LifeProtocolProbe` 全部关闭自动重定向；`UpdateCheck` 另经 S1 的 `UpdateUrls` 逐跳校验 HTTPS。
- `setJavaScriptEnabled(true)`：5 个 WebView 启用，但**均未** `addJavascriptInterface`，JS 无原生桥可达。

### 2. 本次修复：EduLoginActivity 导航准入（确认问题）

- 问题：其 `shouldOverrideUrlLoading` 原仅检查 host 是否 `*.hebeu.edu.cn`，未检查 scheme。manifest 的 `usesCleartextTraffic=false` 虽能兜底阻断明文，但代码层防御缺失，且这是承载教务密码的登录页。
- 修复：改用 S3 引入的 `TrustedUrlPolicy`（HTTPS + 学校域名 + 默认/443 端口），与其他四个教务 WebView 及 S1 更新链路共用同一策略。
- 测试先行：新增 `scripts/test_webview_policy.py` 源码守卫，逐一扫描所有带 `shouldOverrideUrlLoading` 的页面，断言放行判定必须 HTTPS-aware。修改前该守卫在 EduLoginActivity 上红灯，修改后 5/5 通过。已接入 `build_preview.py` 自动测试链。

## 二、Android 配置层（AndroidManifest.xml）

- `minSdkVersion=24`、`targetSdkVersion=36`。
- `usesCleartextTraffic="false"`：应用层禁用明文流量。
- `allowBackup="false"`：禁止备份，配合凭据存于 `getNoBackupFilesDir()`。
- 无 `android:debuggable`、无 `testOnly`。
- 权限最小化：`INTERNET`、`ACCESS_NETWORK_STATE`、`CAMERA`（required=false）、`REQUEST_INSTALL_PACKAGES`（仅用于向系统安装器递交用户已确认的更新包）。无短信/通讯录/定位等敏感权限。
- `ACCESS_WIFI_STATE` 已于 2.5.8.6 移除：全仓库无 `WifiManager` 调用，支付宝 SDK 也只用 `ConnectivityManager`（8 个类引用），不需要 Wi-Fi 状态。
- 组件导出：App 自有 Activity 中仅 `TimetableActivity`（启动入口）为 exported；其余全部 `exported="false"`。4 个 exported 的 `com.alipay.sdk.app.*` 回调 Activity 为支付宝 SDK manifest 要求，反编译源（`client/build/jadx-sdk/sources/com/alipay/sdk/app/PayResultActivity.java:69-103`）显示其用进程内随机请求号比对回调，本项目不以其作为交易成功依据（见 S5 审计）。

## 三、构建产物 / 第三方依赖层

`build_preview.py` 中每个外部依赖均以 SHA-256 固定校验，变更即构建失败：

- 支付宝 SDK `alipaysdk-android-15.8.42.aar`（15.8.42）
- ZXing `core-3.5.3.jar`
- PDFBox `pdfbox-android-2.0.27.0.aar` + BouncyCastle 1.72（bcpkix/bcprov/bcutil）

SDK 证书实现：反编译源 `com/alipay/android/phone/mrpc/core/b.java:235` 使用 `SSLSocketFactory.STRICT_HOSTNAME_VERIFIER`（严格主机名校验），未发现信任全部证书的自定义 TrustManager。该 SDK 为第三方产物，不在本项目修改范围；如需深入可做一次 MobSF 扫描作为补充（本次未执行，扫描结果亦不能直接当漏洞结论）。

构建期还执行 `check_route_boundary()`：扫描 dex 字面量，确认只读版不含用水控制路由字符串、用水版才含——保证 source set 隔离在最终产物中生效。

## 未发现 / 未修改项（明确记录）

- 应用代码中的证书绕过、主机名校验绕过、JS 原生桥、SSL 放行：均未发现，无需修改。
- 第三方支付宝 SDK：固定版本 + 严格主机名校验，未提供可复现的漏洞证据，不为凑数改动。
- MobSF / 真机证书错误与组件导出验证：本次未执行；列为发布前的建议补充项，而非本轮整改阻塞项。

## 测试结果

- `scripts/test_webview_policy.py`：5/5 WebView 导航守卫 HTTPS-aware（新增，测试先行验证过红灯→绿灯）。
- `scripts/test_core.py`：1136 条断言全过。
- `scripts/build_preview.py`：完整构建通过，路由边界检查通过，v2/v3 签名校验通过，且自动运行上述全部守卫。
