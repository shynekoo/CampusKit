<div align="center">
<img src="docs/screenshots/icon.png" width="96" alt="科信小助手">

<h1>科信小助手</h1>

<p><b>CampusKit</b></p>

<p>面向河北工程大学科信学院学生的第三方开源 Android 校园助手，整合教务查询、课表管理和校园生活服务，让日常校园事务更方便。</p>

<p>
<a href="https://github.com/shynekoo/CampusKit/releases/latest"><img src="https://img.shields.io/github/v/release/shynekoo/CampusKit?label=release&style=flat-square" alt="Release"></a>
<a href="LICENSE"><img src="https://img.shields.io/github/license/shynekoo/CampusKit?style=flat-square" alt="License"></a>
<img src="https://img.shields.io/badge/Android-7.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white" alt="Android 7.0+">
<img src="https://img.shields.io/badge/Java-8-3b82f6?style=flat-square&logo=openjdk&logoColor=white" alt="Java">
</p>
</div>

## 下载与更新

支持 Android 7.0（API 24）及以上。正式版与预览版使用同一个签名，可以互相覆盖安装。

| 通道 | 说明 | 下载 |
| :-- | :-- | :-- |
| **正式版 Stable** | 适合日常使用，只在功能验证充分后才更新 | [GitHub Releases 最新正式版](https://github.com/shynekoo/CampusKit/releases/latest) |
| **预览版 Beta** | 更新频率更高，可能存在尚未修复的问题 | [预览版 Releases](https://github.com/shynekoo/CampusKit/releases/tag/preview) |

访问 GitHub 较慢时，可以用[国内下载站](https://wbapp-g8yy3klci8kmeufm6vdn0h.app.workbuddy.host/?sharecode=BZD63HRP4B)。

两条更新通道彼此独立：正式版只跟随正式清单；装好预览版后，可以在「更多」里选定下载渠道（国内 / GitHub / Gitee）并打开「接收预览版更新」，之后按预览清单自动更新。

预览版的版本号通常高于正式版，想退回正式版需要等正式版号追上来，或卸载重装；重装会清掉本地课表与登录状态，动手前先在「课表 → 管理」里导出备份。安装包大小、SHA-256 与版本号只在 Release 页面维护，出包流程与版本号规则见[构建与发布](docs/build-and-release.md)。

## 主要功能

功能以仓库当前实现为准。正式版与预览版代码同一份，差别只在清单与版本号。

### 教务与学习

- **今日**：一页汇总当天课程、校园生活入口、教务查询与取件入口；还没设开学日期时，直接在页面上引导设置
- **课表**：从教务系统导入（网页表格或 PDF 表格），支持多课表、逐门调整周次、自定义背景、导出与恢复备份；导入后完全离线可用
- **成绩与平时分**：按学期查询
- **学业情况**：GPA、学分统计与课程明细
- **空教室**：按周次、节次、教学楼查询
- **学生评价**：教务里的教学班评价，原生列表区分未评 / 已评 / 暂存并可筛选；答题页支持单选、多选、十级评分与主观题，可暂存、提交，已评可回看自己填过的答案

### 校园生活

- **慧生活798**：扫码开启用水，结束时结算；覆盖在线水控、淋浴与饮水机，需要已开通支付宝免密
- **趣智校园**：余额查询、支付宝授权登录，以及 8 位使用码（在浴室或吹风机的键盘上输入即可开启，不必扫码）
- **校园快捷入口**：易校园付款码、淘宝 · 菜鸟驿站身份码，以及河工程官网、科信官网，一键跳到对应应用或页面；这些平台里的数据仍由它们自己的应用处理

应用内不提供充值、下单或收银，相关操作仍在各平台自己的应用里完成。

### 实用工具

- **桌面课表组件**：小、中、宽三种尺寸
- **应用内更新**：下载后校验字节数与 SHA-256，最后一步必须由你确认安装；下载渠道可选，预览更新默认关闭
- **外观与主题**：深色模式（自动 / 浅色 / 深色）、主题色、胶囊式三项底栏、返回手势动画，以及课表配色、文字颜色、圆角、格子不透明度与网格线开关
- **数据备份**：课表、配色与背景导出成一份文件，换机或重装后恢复
- **诊断日志**：默认关闭，只记录白名单字段，可导出脱敏后的故障报告
- **更多与账号页**：数据与隐私（内含使用公告全文）、更新日志、开源许可（离线全文）、意见反馈

## 项目特色

- **原生实现**：Java + Android 原生 View，没有 Kotlin、Compose，也没有第三方 UI 框架，安装包保持在几 MB
- **业务直连**：教务、用水、余额等请求由应用与对应平台直接通信，不经过开发者自建的业务中转服务器
- **凭据本机加密**：登录凭据在本机加密保存，密钥由 Android Keystore 保护
- **双通道更新**：正式版与预览版各自独立，想尝鲜和想稳定可以各取所需；下载渠道可在国内 / GitHub / Gitee 之间切换
- **权限克制**：只申请网络、网络状态、相机与安装未知应用四项，不申请定位、通讯录、短信、存储与蓝牙
- **个性外观**：深色模式、主题色与课表格子外观都能单独调，改动只影响这台手机
- **持续开源**：MPL-2.0 授权，每个发布版本都能拿到对应源码

## 开发与构建

| 项 | 情况 |
| :-- | :-- |
| 语言与 UI | Java（生成 Java 8 字节码）+ 原生 Android View |
| 构建 | Python 脚本调用官方 `aapt2` / `d8` / `zipalign` / `apksigner`，不使用 Gradle |
| 系统要求 | 最低 Android 7.0（API 24），targetSdk 36 |
| 环境 | JDK 24、Python 3，目前仅在 Windows 上验证 |

```bash
python scripts/bootstrap_android.py            # 下载 API 36 平台与 Build-Tools
python scripts/test_core.py                    # 平台无关规则的 JVM 断言
python scripts/build_preview.py --field-water  # 出包（必须带 --field-water，否则缺少用水功能）
```

目录结构：

```
client/android   原生 Android 客户端（Activity / View / 主题 / 平台适配）
client/core      不依赖 Android 的规则层：会话、解析、课表、用水状态机、更新校验
client/fieldtest 用水控制的独立 source set，仅在 --field-water 构建下编入
scripts/         构建、断言与发布辅助脚本
docs/            架构、模块、构建、安全、诊断等文档
delivery/        构建产物与历次发布说明
```

没有 Gradle 是刻意的：下载什么、校验什么、打进包里的每一份文件都写在脚本里，依赖按 SHA-256 固定，测试与出包是同一步。代价是不能用 IDE 一键运行，新增依赖也需要手工合并 AAR。

技术文档：

- [架构总览](docs/architecture.md) · [模块清单](docs/modules.md) · [数据接口](docs/data-interfaces.md)
- [构建与发布](docs/build-and-release.md) · [安全约束](docs/security.md) · [运行日志与诊断](docs/diagnostics.md)
- [开发约定](AGENTS.md) · [开源许可说明](docs/licensing.md) · [变更日志](CHANGELOG.md)

## 使用须知

- 科信小助手是面向河北工程大学科信学院学生的第三方开源校园助手，由个人独立开发维护，**不是学校或相关服务平台的官方应用，也不代表已获得其授权或认可**。
- 本项目用于方便个人学习与校园生活，请遵守学校及相关服务平台的使用规定。
- 部分功能依赖学校及第三方平台提供的在线服务，可能因系统维护、服务调整或接口变动而失效，可用性以实际使用为准。
- 教务导入目前只适配河北工程大学的教务系统（网页表格与七列文字 PDF），其他学校需要修改解析规则；扫描版 PDF 不支持。
- **涉及费用时以平台账单为准**：开启用水、结算等操作会在对应平台产生真实扣费，应用内不提供充值、下单或收银。
- **学生评价提交后会写入学校系统**：应用不会代填、代选或批量代评，提交前请确认内容；能否修改或撤回取决于学校系统。
- 涉及成绩、费用及其他重要信息时，请以学校及相关平台的官方记录为准。
- 请通过本页列出的 Release 或下载站获取安装包，并妥善保管个人账号信息；从其他渠道下载的包无法确认来源。

### 关于数据

登录凭据在本机加密保存（密钥由 Android Keystore 保护），登录时按平台要求把认证信息提交给对应平台；业务请求同样由应用与平台直接通信，不经过开发者自建的业务中转服务器。诊断日志默认关闭、不联网，导出的是脱敏内容。

仓库里唯一的自建服务是一个**可选的匿名使用统计** Worker：需要你自己在「更多」里打开开关，开启后每天最多发送一次「本机随机标识 + 版本号」，不含学号、成绩、课表或任何业务数据；关掉即停，也可以请求删除已上传记录。当前发布版本没有配置统计服务地址，该功能不可用。完整的数据处理方式见 [`docs/analytics.md`](docs/analytics.md) 与 [`docs/security.md`](docs/security.md)。

应用内「更多 → 数据与隐私」有使用公告全文。已知残留风险与具体技术限制集中记录在 [`docs/security.md`](docs/security.md)，包括第三方客户端常量的合规风险、真机验收状态等，本页不重复。

## 反馈与贡献

- **问题与建议**：[GitHub Issues](https://github.com/shynekoo/CampusKit/issues)
- **代码改进**：[Pull Requests](https://github.com/shynekoo/CampusKit/pulls)，动手前建议先开 Issue 说明要改什么
- **开发规范**：[AGENTS.md](AGENTS.md) 记录了构建方式、测试入口与文档同步要求；[构建与发布](docs/build-and-release.md) 说明出包流程

提 Issue 时请附上 App 版本、Android 版本与机型、复现步骤；涉及崩溃或请求失败时，可以在应用内导出脱敏诊断日志一并附上。**不要在公开 Issue 中粘贴密码、Token、Cookie、学号或任何账号凭据。**

## 开源协议与致谢

项目自有代码采用 [Mozilla Public License 2.0](LICENSE)（MPL-2.0）开源。修改 MPL 覆盖文件并对外分发时，需遵守对应的源码提供与许可声明义务。

历史上的 MIT 授权版本继续有效，原文存档见 [`docs/legacy/LICENSE-MIT.txt`](docs/legacy/LICENSE-MIT.txt)；迁移说明见 [`docs/licensing.md`](docs/licensing.md)。

第三方组件（ZXing、PDFBox-Android、Bouncy Castle、支付宝 SDK）各自适用原有许可证，清单见 [`client/THIRD_PARTY.md`](client/THIRD_PARTY.md)。

正式版对应同版本号的 `vX.Y.Z` tag，预览版对应 `preview` tag，任何一只安装包都能回到生成它的源码。

感谢这些项目与同学：

- [nocookies111/life-798](https://github.com/nocookies111/life-798)：慧生活用水接口的公开实测记录
- [ZXing](https://github.com/zxing/zxing)、[PdfBox-Android](https://github.com/TomRoush/PdfBox-Android)、[Bouncy Castle](https://www.bouncycastle.org/)、支付宝 SDK：扫码、PDF 解析、加密与支付
- 在真机上帮忙试用水控、教务导入与各项功能，以及提交 Issue 与 PR 的同学

界面与交互设计参考过以下同类型校园开源项目，仅为设计交流，它们与科信小助手没有开发或隶属关系：

- [不高山上（四川大学）](https://github.com/The-Brotherhood-of-SCU/Bugaoshan)
- [岱宗盒子（西安交通大学）](https://github.com/yeliqin666/xjtu-toolbox-android)
- [MyCHU（长安大学）](https://github.com/RinnMoe/my_chu)
