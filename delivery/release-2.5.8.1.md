# 科信小助手 2.5.8.1 发布说明

**构建日期**：2026-11-20 · 版本 2.5.8.1 / 版本代码 209 ·
`delivery/科信小助手-2.5.8.1.apk`（5 437 306 字节，sha256 `af7ed82392da21863e9ac4af963a27980e3fe1718c83c4c9335f377451f6708f`）

**主题：修复趣智校园扫码无法跳转使用的 bug。**

> 状态：**已发布（2026-11-20）**。`update.json`、App 内更新日志、GitHub Release、下载站与线上核对均已完成。

---

## 一、公告正文（可直接复制）

### 短版（群公告 / 动态 / 更新推送）

> 修复趣智校园扫码无法跳转使用的bug，如果给您带来的不好的体验，非常抱歉。

### 长版（GitHub Release 正文 / 下载站公告）

> **科信小助手 2.5.8.1**
>
> 修复趣智校园扫码无法跳转使用的bug，如果给您带来的不好的体验，非常抱歉。
>
> 2.5.7 起正式包误用只读构建，趣智校园的设备码扫码后只核对资料、不跳转支付宝用水。
> 本版把用水链路编回正式包，扫码后恢复跳转支付宝，与 2.5.6 及之前一致。
>
> **升级**：覆盖安装即可，课表、账号、偏好都保留；版本代码 209，可直接覆盖 2.5.8 及更早版本。
> 真机已确认扫码可正常跳转使用。

---

## 二、根因与修复

- **根因**：2.5.7 发版（commit `2575dd8`）未传 `--field-water`，产出只读构建，`BuildFlags.WATER_CONTROL=false`；
  `ScanActivity.showCode()` 里跳支付宝的分支在编译期被整段移除。2.5.8 延续了只读构建。
- **证据**：各版本 APK 的 dex 水控类命中数——2.5.6 / 2.5.6.11 为 `WaterControl` 4、`WaterActivity` 100、
  `LifeWaterProtocol` 3；2.5.7 / 2.5.8 为 1 / 1 / 0。
- **修复**：不改业务逻辑与开关实现，用 `python scripts/build_preview.py --field-water --version-name 2.5.8.1 --version-code 209`
  重建正式包；manifest 版本真源同步为 2.5.8.1 / 209。
- **未改**：`FIELD_VERSION_NAME` 与发布逻辑保持原样，构建时显式传版本号避开 2.5.6.11 覆盖。

## 三、测试结果（本环境实际执行）

| 检查 | 结果 |
| :-- | :-- |
| `test_core.py` | 1329 断言全过 |
| `test_build_config.py` | 7 项全过 |
| `test_diag_privacy.py` | 634 项全过 |
| `test_diag_files.py` | 6650 断言全过 |
| `test_webview_policy.py` | 5 项全过 |
| `test_ui_contract.py` | 240 锚点全过 |
| 构建验签 | v2 / v3 均为 true |
| dex 水控类命中 | 4 / 100 / 3（与 2.5.6 用水版一致） |
| 真机扫码跳转 | 发布者已确认可用 |

未验证：真实出水/关水/账单、进程被杀恢复——未对生产服务测试。

## 四、发布清单（执行时勾选）

1. [x] 构建 APK + 核心断言 + `update.json`
2. [x] GitHub 同步：push `6a1808e` / tag `v2.5.8.1` / Release 上传 `kexin-xiaozhushou-2.5.8.1.apk` / README
3. [x] 下载站同步：APK 复制到 `delivery/update/`，重新发布站点（链接不变）
4. [x] 线上页面、update.json 与 APK 直链均返回 200，直链字节数 5,437,306 与清单一致
