# 科信小助手 2.5.6 发布说明

**构建日期**：2026-11-17 · 版本 2.5.6 / 版本代码 177 · `delivery/科信小助手-2.5.6.apk`（5346972 字节，sha256 `5fac88d0…413c83`）

**主题：修复慧生活无法使用**。2.5.5 的设备页只剩「在慧生活原版使用」，没有「进入独立用水」入口。

## 问题与根因

- 现象：装 2.5.5 后，慧生活 798 的设备页只有「刷新设备状态 / 账户信息 / 在慧生活原版使用」，独立用水入口整个消失。
- 根因：**已发布的 2.5.5(176) 是 `scripts/build_preview.py` 不带 `--field-water` 打出来的**。read-only 构建把 fieldtest 源根整体排除 → `BuildFlags.WATER_CONTROL=false` → 设备页按钮 `setVisibility(GONE)`，`LifeWaterActivity` 类根本没编进 dex。与支付宝 ptpye=21 的协议改动无关。
- 佐证：2.5.5 的 `.build.json` 里 `field_water_build=false`、`control_route_occurrences_in_dex` 全 0；线上 `delivery/update/campuskit-2.5.5.apk` 的 sha256 `91f69f89…` 正是这个 read-only 包。

## 修复

- `FIELD_VERSION_NAME` 置 `"2.5.6"`，用 `python scripts/build_preview.py --field-water` 重新出包。
- 本包 `field_water_build=true`，`control_route_occurrences_in_dex` 中 7 条用水控制路由与 `FieldWaterActivity` 均非 0，dex 可检索。
- 用户侧无需任何操作，覆盖安装即恢复「进入独立用水」，支付宝免密登录链路未改。

## 本次用户能看到的其它变化（2.5.5 已发，2.5.3 用户直升时一并生效）

- 调课多段周次（`2-6周(双),11-15周`）不再被当作坏行跳过。
- 未登录自动跳对应登录页，登录完自动回原页并续跑被打断的查询。
- 校园页新增「教务系统」入口；「数据与隐私」移入「更多」。
- 「清除登录状态」现在连内存会话、WebView cookie、WebStorage 一起清。

## 升级注意

- versionCode 177，可直接覆盖 2.5.3 / 2.5.5 安装，数据保留。
- 签名证书未变：`CN=QZXY Local Preview`。
- **不要**把 `2.5.6.a`（诊断版，178）发到更新通道：`UpdateManifest` 的 version 正则 `[0-9]{1,2}(\.[0-9]{1,2}){1,2}` 不收字母。

## 质量

- `python scripts/test_core.py` 1067 项断言通过。
- `python scripts/build_preview.py --field-water` 通过，v2/v3 签名通过。
- **未验证**：沙箱内 adb 起不来，设备页按钮恢复只做到构建级确认（dex 中存在路由与 Activity）。

## 上线清单

1. `delivery/update/update.json` → `2.5.6` / `177`，`bytes` `5346972`，`sha256` `5fac88d080fa99121f863e271403b6cecb0c5510e134ea3742263dbcc5413c83`，`notes` 用下面 5 条。
2. 安装包为 `delivery/update/campuskit-2.5.6.apk`，sites 重部署 `delivery/update`（appId `wbapp_g8yY3kLcI8KmEUFM6vdn0h`，domainPrefix `kexin-update`）。
3. 线上验证 `update.json` 与 apk 均 200、`Content-Length` 5346972。
4. GitHub Release `v2.5.6` 与 push 未做，等用户确认。

## notes（应用内更新弹窗，≤8 条、每条 ≤160 字）

```json
"notes": [
  "修好了慧生活无法使用的问题：上一版设备页的「进入独立用水」入口丢了，只剩「在慧生活原版使用」；现在已恢复，支付宝免密登录照旧",
  "调课那类多段周次的课（如「2-6周(双),11-15周」）以前导入会被跳过，现在能正常进课表",
  "登录问题做了优化：没登录会自动跳登录页，登完自动回原页",
  "校园页新加了「教务系统」入口；「数据与隐私」挪进「更多」",
  "第一次用请先在账号页登录趣智校园、慧生活798及教务系统；账号密码加密存在本机，无后端，不上传、不汇总、不分享"
]
```

措辞取舍：

- 首条讲修复，符合「这版是修 bug 的」定位；不写「打包漏了」这种内部原因，改成用户能对上的现象（按钮没了）。
- 其余 4 条沿用 2.5.5 已发布文案，保证从 2.5.3 直升的用户也看得到。
