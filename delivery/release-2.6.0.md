# 科信小助手 2.6.0 发布说明

**构建日期**：2026-11-21 · 版本 `2.6.0` / 版本代码 `236` ·
`delivery/科信小助手-2.6.0.apk`（5 486 527 字节，sha256 `8658a047ab3a5c31a38b7c73d4ed1a5e5a5c357d401ba84c3fa0314478f589b7`）

**主题：学生评价搬进原生界面，更新渠道自己做主，版本号与开源协议一次理顺。**

> 状态：**已发布（2026-11-21）**。构建、签名、清单、GitHub / Gitee / 下载站与线上核对见第六节。

---

## 一、更新内容

### 新增

| 功能 | 说明 |
| :-- | :-- |
| **学生评价** | 教务里新增「学生评价」。原生列表显示**未评 / 已评 / 暂存**三种状态，可按状态筛选，翻页取全本学期教学班 |
| | 原生答题页支持**单选、多选、十级制、主观题**，可**暂存**、**提交**；已评可**回看答案** |
| **更新渠道** | 「更多」页可选 **国内 / GitHub / Gitee**；新增 **「接收预览版更新」** 开关，**默认关** |

### 改进

| 变更 | 说明 |
| :-- | :-- |
| **版本号规则** | 统一为 `X.Y.Z` 与 `X.Y.Z-beta.N`，**构建期强制校验**；四段号（如 `2.5.8.1`）在构建时就失败，不会再出现「清单解析不了、收不到更新」 |
| **开源协议** | MIT → **MPL-2.0**；「开源许可」弹窗显示**本机版本号**与**对应源码地址** |
| **仓库与产物** | 移除早已无调用方的旧签名常量；新增发布前敏感信息扫描，源码或安装包命中禁用常量就不出包。功能边界未变 |

---

## 二、公告正文（可直接复制）

### 短版（群公告 / 动态 / 更新推送）

> 2.6.0 来了：教务新增「学生评价」，未评、已评、暂存一眼看清，答题页支持单选、多选、十级评分和主观题，可先暂存再提交，评过的还能回看自己的答案。
> 「更多」里可以自己选更新渠道（国内 / GitHub / Gitee），「接收预览版更新」默认关闭。版本号与开源协议也一并理顺。

### 长版（GitHub Release 正文 / 下载站公告）

> **科信小助手 2.6.0**
>
> **新增 · 学生评价**
> 教务里新增「学生评价」。原生列表直接标出每个教学班是未评、已评还是只存了草稿，可以按状态筛选，翻页能把本学期的都取全。
> 答题页是原生的：单选、多选、十级评分、主观题都能填，随手暂存，确认后提交；已经评过的可以回看自己填了什么。
>
> **新增 · 更新渠道**
> 「更多」页可以自己选更新渠道：国内（默认）、GitHub、Gitee。另加一个「接收预览版更新」开关，默认关闭——想尝鲜再开。
>
> **改进 · 版本号**
> 版本号统一成 `2.6.0` 与 `2.6.0-beta.1` 两种写法，并在构建时就强制校验。以前出现过四段号（如 `2.5.8.1`）导致部分手机检查不到更新的问题，这类包以后发不出来。
>
> **改进 · 开源协议**
> 项目由 MIT 改为 MPL-2.0。「更多 → 开源许可」里能看到本机版本号和这一版对应的源码地址。
>
> **升级**：覆盖安装即可，课表、账号与偏好都保留；版本代码 236，可直接覆盖 2.5.9（210）与全部 2.6.0 预览版（≤235）。
> **源码**：https://github.com/shynekoo/CampusKit/tree/v2.6.0

---

## 三、技术变更（面向开发者）

| 项 | 内容 |
| :-- | :-- |
| 构建命令 | `python scripts/build_preview.py --field-water --version-code 236`（manifest 真源 `2.6.0` / `236`） |
| 版本校验 | `check_version_name()`：`X.Y.Z` / `X.Y.Z-beta.N` 之外一律构建失败；`check_version_code()` 拒绝不高于线上清单的号 |
| 学生评价 | 原生列表 + 原生答题页（`EvalListActivity` / `EvalFormActivity` 一脉）；状态由学校返回的行标记决定，答案按学校字段顺序回填 |
| 更新渠道 | `UpdateSources` 三渠道六地址；渠道与预览开关存 `update-v1` 的 `source` / 接收预览键 |
| 清单分渠道 | `update.json`（WorkBuddy）/ `update-github.json` / `update-gitee.json` 仅 `url` 不同，`url` 固定 tag + ASCII 文件名 |
| 协议 | 自有源码 SPDX `MPL-2.0`；历史 MIT 存档留在 `docs/legacy/LICENSE-MIT.txt` |
| 安全 | `scripts/test_secrets.py` 作为发布闸门：打包前扫源码、打包后扫 dex / assets / 资源表 / native |

---

## 四、测试结果（本环境实际执行）

| 检查 | 结果 |
| :-- | :-- |
| `test_core.py` | 1554 断言全过 |
| `test_build_config.py` | 7 项全过 |
| `test_diag_privacy.py` | 717 项全过 |
| `test_diag_files.py` | 6650 断言全过（实跑轮转 / 上限 / 清空 / 过期） |
| `test_webview_policy.py` | 5 项全过 |
| `test_analytics.py` | 77 项 + Worker `node --test` 18 项全过 |
| `test_ui_contract.py` | 388 锚点全过 |
| `test_secrets.py` | 源码与产物均无命中 |
| 签名 | v2 / v3 均 `true`，证书 `13b96892…d4209`（与 2.5.9 一致） |
| manifest | `versionCode 236` / `versionName 2.6.0`；`WaterActivity`、`LifeWaterActivity` 均在列 |

**未验证**：真机覆盖安装、慧生活登录与扫码用水、教务登录与课表 / 成绩实查、三渠道在线检查更新——由发布者自行测试。

---

## 五、渠道与链接

| 渠道 | 正式清单 | 本版 APK |
| :-- | :-- | :-- |
| 国内（WorkBuddy，默认） | `update.json?sharecode=BZD63HRP4B` | `campuskit-2.6.0.apk?sharecode=BZD63HRP4B` |
| GitHub | `releases/latest/download/update.json` | `releases/download/v2.6.0/kexin-xiaozhushou-2.6.0.apk` |
| Gitee | `releases/download/release/update.json` | `releases/download/release/campuskit-2.6.0.apk` |

---

## 六、发布清单（执行时勾选）

1. [x] 构建 APK（`--field-water`，236），签名 / 对齐 / manifest / 产物体检通过
2. [x] `test_ui_contract` 388 / `test_secrets` 通过；`AppChangelog` 追加 2.6.0
3. [x] 三份 `update*.json` 写好，APK 复制进 `delivery/update/`
4. [x] GitHub：push `main`（`8dc2541`）、tag `v2.6.0`、Release 附 APK + `update.json`（Latest）、README 同步
5. [x] Gitee：`release` tag 内替换 APK + `update.json`（先传新、再删旧，删除均 `204`）
6. [x] 下载站重发布；三个渠道清单与 APK 直链均 200，字节数与 SHA-256 与产物一致

### 线上核对结果（2026-11-21 15:05）

| 渠道 | 清单 | APK 直链 | 字节 / SHA-256 |
| :-- | :-- | :-- | :-- |
| 国内 WorkBuddy | 200，`2.6.0` / 236 | 200 | 5,486,527 B / 一致 |
| GitHub | 200（`gh release download` 回读） | 200 | 5,486,527 B / 一致 |
| Gitee | 200 | 200 | 5,486,527 B / 一致 |

> 国内站前三次上传失败是 `delivery/update/` 里堆了 146 MB 历史 APK 导致上传超时；归档到
> `delivery/_apk-archive/` 后站点降到 11 MB，重发布成功。回滚点：`campuskit-2.5.9.apk` 仍留在站点目录。
