# 科信小助手 2.5.0 发布说明

**发布日期**：2026-11-16 · 版本代码 170 · `delivery/科信小助手-2.5.0.apk`（5342876 字节，sha256 `8782e26c…62f131`）

## 变更

- **开启用水不再弹支付方式选择**。此前按 `payItems` 的 `seq` 排序弹出单选列表，默认项可能落在余额为 0 的商家钱包上（正是 2.3.6–2.3.8 反复撞上业务码 `-3` 的原因）。现在固定使用**支付宝免密（type 21）**——本机账单里每一笔成功水费都是它。点「开启用水」→ 确认弹窗（固定文案「支付方式：支付宝免密」）→ 直接发送 `dev/start`。
- 开启前的最终核对仍会重新拉设备详情：若服务端这次没有提供 21，提前报「设备未提供支付宝免密（可用：…）」并列出可用方式，不盲发请求。
- 业务码 `-3` 的提示改为「可能是设备刚被他人占用或支付宝免密代扣未生效」，不再引导换支付方式。

## 继承自 2.3.10（未改动）

- 版本头三件套：`VersionCode: 3.1.9`（版本名字符串）、`User-Agent: Android_ilife798_3.1.9`、`Accept-Language: zh-Hans-CN;q=1`——参考开源项目 [WaterWidget](https://github.com/nocookies111/life-798) 实测形状。
- 开启走 `/api/v1/dev/start?did=&upgrade=true&ptype=&rcp=false`（无 `args`、无 `cnt`）。
- WaterWidget 实测业务码表（-19/-20/-52/-82/-87/-88）仍用于失败解释。

## 质量

- 1034 项核心断言通过（新增 `DEFAULT_PAYMENT==21` 断言），v2/v3 签名通过。

## 升级

应用内「设置—数据管理—应用」或直接下载：<https://wbapp-g8yy3klci8kmeufm6vdn0h.app.workbuddy.host/campuskit-2.5.0.apk?sharecode=BZD63HRP4B>
