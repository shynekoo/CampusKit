# ZXing Core 3.5.3

## PDF 导入依赖（0.6.1 起）

PDFBox-Android 2.0.27.0（Apache-2.0），项目 https://github.com/TomRoush/PdfBox-Android 。AAR 下载地址：https://repo.maven.apache.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.aar 。将其 classes.jar 解压至 tools/pdfbox/classes.jar；构建脚本将 AAR 内 assets 一并打包。

其声明的 Bouncy Castle 依赖 bcprov、bcpkix、bcutil 均为 jdk15to18 1.72（MIT），来自 Maven Central 的 org/bouncycastle 目录，放入 tools/pdfbox。所有依赖 SHA-256 固定于 scripts/build_preview.py，构建时校验。许可证随 APK 存放于 res/raw/pdfbox_license.txt 和 res/raw/bouncycastle_license.txt。

PDF 只在本机临时缓存并读取文字，读取结束删除临时文件；不上传 PDF，不存储教务密码。当前适配河北工程大学教务系统七列文字表格 PDF，图片扫描版和未支持的周次格式会提示失败。

二维码解码使用 ZXing Core，Apache License 2.0。项目：https://github.com/zxing/zxing/tree/zxing-3.5.3

构建依赖路径：tools/zxing/core-3.5.3.jar

下载：https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar

SHA-256：8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82

最低 Android API 24 与本项目一致。解码只在本机进行，不通过该库上传图片。

# Alipay SDK 15.8.42（2.3.2 起）

支付宝官方 Android SDK（`com.alipay.sdk:alipaysdk-android`），用于慧生活登录页的「支付宝一键登录」。

下载：https://repo.maven.apache.org/maven2/com/alipay/sdk/alipaysdk-android/15.8.42/alipaysdk-android-15.8.42.aar

SHA-256：a6c301708b59a3a7cda843cf02e7b3b0b44afd96819f0a18890322e9868abf28

放在 `tools/alipay/`，构建时校验哈希。没有 Gradle，所以 AAR 里的三件事是手工合并的：`classes.jar` 进 javac 与 d8；`AndroidManifest.xml` 里的 6 个 Activity、`org.apache.http.legacy` 与两条权限抄进 `client/android/AndroidManifest.xml`；`res/values` 里那三条字符串抄进 `client/android/res/values/alipay_sdk.xml`，并用 `aapt2 link --extra-packages com.alipay.sdk` 生成 SDK 自己的 `R`。

授权串（`AlipayLogin.AUTH_INFO`）来自慧生活官方 3.1.5 的登录页，是**他们的** appid 与签名，不是本项目的。用它的后果写在 `delivery/release-2.3.2.md` 里。
