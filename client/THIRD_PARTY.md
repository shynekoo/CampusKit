# 第三方组件与许可证

本文件记录项目实际使用的第三方组件、它们的许可证与分发方式。

- 项目自身代码采用 **MPL-2.0**，见仓库根目录 [`LICENSE`](../LICENSE) 与 [`docs/licensing.md`](../docs/licensing.md)。
- 下列组件**各自适用原有许可证，不属于项目自有 MPL-2.0 的授权范围**，不得改标为 MPL。
- 组件以二进制依赖（`tools/` 下按 SHA-256 固定）或未修改的许可证原文形式进入仓库；随包附带的原文在 `client/android/res/raw/`。

| 组件 | 版本 | 许可证 | 用途 |
| :-- | :-- | :-- | :-- |
| ZXing Core | 3.5.3 | Apache-2.0 | 二维码解码 |
| PDFBox-Android | 2.0.27.0 | Apache-2.0 | 教务 PDF 课表解析 |
| Bouncy Castle（bcprov / bcpkix / bcutil） | 1.72 | MIT | PDFBox 的加密依赖 |
| 支付宝 SDK | 15.8.42 | 官方 SDK 条款（AAR 内不含 LICENSE 文件） | 慧生活登录、充值、免密扣费 |

> 支付宝 SDK 的授权条件来自支付宝开放平台的服务条款；本仓库没有取得其开源许可证文本，**既不认定为 MPL，也不按 Apache / MIT 处理**。分发含该 SDK 的 APK 前请自行核对平台的分发要求。

## ZXing Core 3.5.3

二维码解码使用 ZXing Core，Apache License 2.0。项目：https://github.com/zxing/zxing/tree/zxing-3.5.3

构建依赖路径：tools/zxing/core-3.5.3.jar

下载：https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar

SHA-256：8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82

最低 Android API 24 与本项目一致。解码只在本机进行，不通过该库上传图片。

## PDFBox-Android 2.0.27.0（0.6.1 起）

PDFBox-Android 2.0.27.0（Apache-2.0），项目 https://github.com/TomRoush/PdfBox-Android 。AAR 下载地址：https://repo.maven.apache.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.aar 。将其 classes.jar 解压至 tools/pdfbox/classes.jar；构建脚本将 AAR 内 assets 一并打包。

其声明的 Bouncy Castle 依赖 bcprov、bcpkix、bcutil 均为 jdk15to18 1.72（MIT），来自 Maven Central 的 org/bouncycastle 目录，放入 tools/pdfbox。所有依赖 SHA-256 固定于 scripts/build_preview.py，构建时校验。许可证随 APK 存放于 res/raw/pdfbox_license.txt 和 res/raw/bouncycastle_license.txt。

PDF 只在本机临时缓存并读取文字，读取结束删除临时文件；不上传 PDF，不存储教务密码。当前适配河北工程大学教务系统七列文字表格 PDF，图片扫描版和未支持的周次格式会提示失败。

## Alipay SDK 15.8.42（2.3.2 起）

支付宝官方 Android SDK（`com.alipay.sdk:alipaysdk-android`），用于慧生活登录页的「支付宝一键登录」。

下载：https://repo.maven.apache.org/maven2/com/alipay/sdk/alipaysdk-android/15.8.42/alipaysdk-android-15.8.42.aar

SHA-256：a6c301708b59a3a7cda843cf02e7b3b0b44afd96819f0a18890322e9868abf28

放在 `tools/alipay/`，构建时校验哈希。没有 Gradle，所以 AAR 里的三件事是手工合并的：`classes.jar` 进 javac 与 d8；`AndroidManifest.xml` 里的 6 个 Activity、`org.apache.http.legacy` 与两条权限抄进 `client/android/AndroidManifest.xml`；`res/values` 里那三条字符串抄进 `client/android/res/values/alipay_sdk.xml`，并用 `aapt2 link --extra-packages com.alipay.sdk` 生成 SDK 自己的 `R`。

授权串（`AlipayLogin.AUTH_INFO`）来自慧生活官方 3.1.5 的登录页，是**他们的** appid 与私钥签名，不是本项目的凭据，也不由本项目控制其有效期。它是客户端固定常量（随官方 APK 公开分发），因此无法被本项目「保管」或「吊销」；能改变它状态的只有慧生活轮换应用私钥。定性、危害边界与为什么暂时保留，见 `docs/security.md`「已知残留风险」。

## 许可归属需要单独记录的内容

- 支付宝 SDK 未附带许可证文本（见上表说明），其分发条件以支付宝开放平台条款为准。
- 协议与地址常量（`Life798Qr`、`PickupCodeLinks` 等）来自 `nocookies111/life-798`、`yimeifun/PickTool` 等第三方项目与真机实测，文件注释保留来源；这些常量不改变相关第三方项目的许可归属。
- 反编译 / 抓包得到的第三方应用代码不在本仓库中（`client/build/` 已被 `.gitignore` 排除），也不受本项目许可证覆盖。
