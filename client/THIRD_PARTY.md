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
