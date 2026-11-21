# 开源许可说明（MPL-2.0）

本文记录项目自身的许可证、MPL 的适用范围、第三方组件的例外，以及 2026-11-21 从 MIT 迁移到 MPL-2.0 的处理方式。第三方组件的详细清单见 [`../client/THIRD_PARTY.md`](../client/THIRD_PARTY.md)。

## 1. 当前许可证

- 项目自有代码采用 **Mozilla Public License 2.0（MPL-2.0）**，全文见仓库根目录 [`LICENSE`](../LICENSE)（未经修改的官方英文原文，取自 <https://www.mozilla.org/en-US/MPL/2.0/>）。
- App 内「更多 → 开源许可」展示同一份全文：编译进 `client/android/src/io/qzxy/lite/preview/MoreActivity.java` 读取的 `client/android/res/raw/mpl_license.txt`，离线可读，不依赖浏览器。
- 源码文件按 SPDX 标识声明许可：Java / JavaScript 用 `// SPDX-License-Identifier: MPL-2.0`，Python 用 `# SPDX-License-Identifier: MPL-2.0`。

## 2. MPL-2.0 是文件级 Copyleft

MPL 的传染范围以**文件**为单位，但「文件级」只说明**边界**，不说明**可以选择闭源**：

| 你的行为 | 义务 |
| :-- | :-- |
| 修改了带 MPL 声明的文件 | 该文件（及其衍生）**必须继续以 MPL 发布源码**，对外分发时可执行形式的同时提供对应源码 |
| 把 MPL 文件与自己的代码合并进同一个文件 | 合并后的整个文件都受 MPL 约束，不得闭源 |
| 自己新写、与 MPL 文件之间没有衍生关系的独立文件 | 可以自选许可（含专有），MPL 不要求你公开它 |
| 只调用本项目提供的库 / 接口，未改动任何 MPL 文件 | 同上：你的独立文件可保持闭源，但随分发必须满足 MPL 文件的源码提供义务 |
| 分发二进制（含改过的 APK） | 必须告知接收者 MPL 覆盖文件的源码获取方式 |
| 保留版权与许可声明 | 不得删除 MPL 声明与 SPDX 标识 |

**要点：不存在「把 MPL 文件改完闭源、只发二进制」这条路。** 只要对外分发基于本项目的修改版，被改动或被合并的那些 MPL 文件就必须继续以 MPL 公开源码；文件级只意味着 Copyleft 不跨越到你自己独立编写的文件。

MPL 允许商用、改名分发、二次开发，不额外附加「禁止商用」「禁止改名」之类限制。**项目没有附加任何此类条款**；若在别处看到这类说法，以 `LICENSE` 与本文为准。

## 2.1 每个发布版本都要能拿到对应源码

- 仓库 `client/android/src/io/qzxy/lite/preview/MoreActivity.java` 的「开源许可」弹窗在协议正文之上写明：本机安装的版本号、源码地址、以及「本版本对应源码见同版本号的 tag / 预览版见 `preview` tag」。
- 发布流程要求（见 `AGENTS.md` 第 6 节与 `docs/build-and-release.md`）：正式版打与版本号一致的 tag（`vX.Y.Z`），Release 说明与下载站都必须写出该版本的源码地址；预览版对应 `preview` tag，并在 `beta.json` 与 Release 说明中保留源码地址。
- 因此任何一只 APK 都能回到同版本号的提交，不需要等到「最后一个版本」才有源码。

## 3. 不在项目自有 MPL 授权范围内的内容

| 类别 | 例子 | 说明 |
| :-- | :-- | :-- |
| 第三方库（二进制） | ZXing、PDFBox-Android、Bouncy Castle、支付宝 SDK | 各自许可证，见 `client/THIRD_PARTY.md` |
| 随包存放的第三方许可证文本 | `client/android/res/raw/{zxing,pdfbox,bouncycastle}_license.txt` | 原文照转，不改写 |
| 平台接口与数据 | 趣智校园、慧生活798、教务系统、菜鸟 | 不属于本项目，也不随本项目授权 |
| 学校 / 平台的名称与标识 | 校名、平台 logo、二维码样式 | 项目开源不等于获得商标或平台接口的授权 |
| 协议知识来源 | `nocookies111/life-798`、`yimeifun/PickTool` 等实测与地址 | 代码里的地址与业务码表来自这些项目与实测；相关文件的注释保留来源说明，不改变其原有许可归属 |
| 反编译产物（不入库） | `client/build/jadx-*/` | 已被 `.gitignore` 排除，不属于仓库内容，也不受本项目许可覆盖 |

## 4. 历史 MIT 版本的授权继续有效

- **边界**：本次迁移提交（`docs: migrate project license to MPL-2.0`）之前构建的任何 APK 都按 MIT 授权 —— 包括正式版（最后一只 `2.5.9` / versionCode `210`）、`2.6.0-beta.1`–`2.6.0-beta.15` 预览版（已发 GitHub / Gitee `preview`），以及 `delivery/` 下留存的更早构建产物与历史上短暂发布过的版本。
- **不可撤回**：已经拿到上述版本的人，其 MIT 授权不因本次迁移而被收回或被 MPL 取代，仍可按 MIT 的条款使用、修改、分发那些版本。
- **边界之后**：本提交及之后的版本（源码与二进制）适用 MPL-2.0。
- MIT 原文存档在 [`docs/legacy/LICENSE-MIT.txt`](legacy/LICENSE-MIT.txt)。该文件带「历史资料，非当前许可证」抬头，且不在仓库根目录，GitHub 不会把它识别为当前主许可证。
- 历史更新日志中「项目按 MIT 许可证开源」等记载属于事实记录，保留不改。

## 5. 迁移时的版权归属与例外

- 仓库的 Git 贡献者只有项目作者（`shynekoo`，含同人不同邮箱的提交），自有代码的作者拥有重新授权的权利。
- 版权审核未发现「作者无权重新授权」的文件；第三方代码均以二进制依赖形式引入，未经改写后混入自有源码。
- 需注意的两类**归属不确定**内容（均未擅自标为 MPL）：
  1. **支付宝 SDK（`tools/alipay/alipaysdk-android-15.8.42.aar`）**：AAR 内没有 LICENSE 文件，官方以开放平台服务条款分发；本项目只按官方方式打包调用，未声明其许可证类型。
  2. **协议与地址常量**：`Life798Qr`、`PickupCodeLinks` 等文件中的设备协议、取件地址来自第三方开源项目与真机实测，文件内的注释保留了来源；常量本身不构成可版权作品的主张对象，但来源说明保留。
- 若后续引入第三方源码（复制文件而非二进制依赖），必须在该文件保留原许可证头，并在 `client/THIRD_PARTY.md` 登记；**不得**给它加 MPL 标识。

## 6. 外部开发者分发修改版时的基本合规要求

1. 保留 `LICENSE` 与所有 `SPDX-License-Identifier: MPL-2.0` 声明；改过的 MPL 文件继续用 MPL 发布。
2. 分发 APK 等可执行形式时，同时提供对应源码（原始仓库链接或你自己公开的仓库）与获取方式说明。
3. **换签名/改包名必须自行处理升级与数据迁移**：本项目签名固定，换签名会让老用户无法覆盖安装（详见 README「升级请覆盖安装」）。
4. 不得删除或改写第三方组件的版权与许可声明；分发时仍需满足 Apache-2.0（ZXing、PDFBox）、Bouncy Castle、支付宝 SDK 各自的条件。
5. 不得用本项目名义暗示学校、趣智校园、慧生活798 或作者的背书；免责声明中的非官方身份说明应一并保留。
6. 涉及登录、支付、水控的改动由改动者自行承担合规与安全责任，本项目不提供任何绕过计费的支持。

## 7. 相关文件

- [`LICENSE`](../LICENSE)：MPL-2.0 全文
- [`docs/legacy/LICENSE-MIT.txt`](legacy/LICENSE-MIT.txt)：历史 MIT 存档
- [`client/THIRD_PARTY.md`](../client/THIRD_PARTY.md)：第三方组件与许可证
- [`CHANGELOG.md`](../CHANGELOG.md)：「未发布」段的迁移记录
