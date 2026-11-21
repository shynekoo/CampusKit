# CampusKit 安全审计记录：支付与用水业务链路（S5）

- 基准版本：本地 2.5.8.1（提交 `f89fa86` 之后）
- 审计日期：2026-11-19
- 范围：`UseSession` / `PendingUse` / `WaterEvents` / `WaterControl` / `WaterActivity` / `QzxyAlipayActivity` / `PendingUseVault` / Manifest 中支付宝回调组件
- 方法：源码静态审计 + 现有单元测试复核 + 构建产物 source set 隔离验证。**未向学校或支付平台生产服务发送任何真实交易或重复请求。**

## 结论摘要

| 审计项 | 风险级别 | 结论 |
| --- | --- | --- |
| 重复点击导致重复下发控制指令 | 中 | 未发现缺口——状态机 + 单次请求 + UI 门控三重防护 |
| 网络超时自动重发扣费/设备指令 | 高 | 未发现缺口——代码明确不重发，进入核实而非重试 |
| 支付/用水仅凭客户端 SDK 返回值判成功 | 高 | 未发现缺口——一律以服务端结果核实后才落地 |
| `PendingUse` 结果不明状态的保存与恢复 | 高 | 未发现缺口——命令前持久化，恢复进 RECONCILE |
| 进程被杀 / Activity 销毁后状态丢失 | 中 | 未发现缺口——加密记录 + 状态映射 |
| 登出清理敏感会话但保留待核实记录 | 中 | 未发现缺口——记录按账户归属隔离 |
| 只读版发出设备控制请求 | 高 | 未发现缺口——构建期 source set 隔离 + DEX 扫描 |
| 支付宝 exported 回调 Activity 被滥用为"假成功" | 中 | 未发现缺口——SDK 自带类，有请求号校验；App 不采信其回调作为交易成功 |

## 逐项证据

### 1. 状态机禁止盲重试（`UseSession.java`）

- `connectionLost()` 对 `STARTING/ACTIVE/STOPPING/SETTLING` 一律进 `RECONCILE`（`:45-52`），不回落 IDLE。
- `reconcile(State)` 只接受 `ACTIVE/SETTLING/COMPLETED` 三个"已核实"结果，传入 `IDLE` 抛异常（`:59-66`）。注释明确写 "caller must first verify the remote outcome; blind retries are invalid"。
- `restore()` 把持久化的中间态全部映射成 `RECONCILE`（`:68-82`），恢复后无法直接 `requestStart()`。
- 测试 `CoreTests.java:552-590`：重复 start/stop 被拒、断网不误判停止/结算、8 线程并发仅 1 个 start 通过、各中间态恢复均进 RECONCILE。

### 2. 控制请求单次发送（`WaterControl.java`，field-water source set）

- 类注释与每个方法明确 "one request, one route, no retry"（`:21-23, 62-71, 85-92`）。
- `requireUsable()` 双门控：构建开关 `WATER_CONTROL` + 运行时设备校验，且在开 socket 前执行（`:56-60`）。
- 路由白名单 `ROUTES`（`:41-42`），`control()` 只接受白名单内路径（`:137`）。
- `settle()` 校验返回订单号与请求一致（`:127`），账单不匹配直接拒绝。
- 只读版不含此类：`WaterControl.java` 位于 `client/fieldtest/`，`--field-water` 才编译；产物级 DEX 扫描由 `build_preview.py` 的 `check_route_boundary()` 执行。

### 3. 客户端返回值不作为成功依据

- `WaterActivity.poll()`（`:393-409`）：start 结果必须 `usableFor(ownerId, serial)`（账户 ID + 设备序列号匹配），**且**再次调用 `WaterApi.orderStatus()` 确认当前订单号一致（`:397-398`），否则丢弃该结果。
- 关水：`closeOrder()` 返回 0 只是"受理"，`describe()` 与 `satisfied()` 明确要求 `closeResult` 再确认（`:119-121`）；代码注释 "a successful dispatch alone does not prove the device stopped"（`:460-461`）。
- 结算：`Settled.parse()` 无金额即抛异常，不允许展示"金额为空"的完成账单（`WaterEvents.java:146-153`）。
-  Quid pro quo：支付宝跳转让用户去小程序完成（`QzxyAlipayActivity`/`WaterActivity.openAlipayWater`），App 不解析任何支付成功回调作为本地交易成功。

### 4. `PendingUse` 命令前持久化与恢复

- `PendingUse.command()` 只允许 STARTING/ACTIVE/STOPPING/SETTLING 四态，非 STARTING 必须有订单号（`:74-88`）。
- `WaterActivity.sendStart()` 在 `WaterControl.start()` **之前**调用 `vault().beginCommand(...)`（`:330` 先于 `:336`）；持久化失败则不发送指令（`:331-333`）。
- `WaterActivity.requestStop()` 同样先持久化再发送（`:451` 先于 `:453`），且注释说明持久化失败仍尝试停止但保留旧 journal（`:450`）。
- 记录加密存储、独立 AAD 与会话信封隔离（`PendingUse.java:34, 129-176`）；测试含篡改/错密钥/会话信封重放均被拒（`CoreTests.java:268-282`）。

### 5. 登出与账户归属

- `PendingUse.belongsTo(accountId)` / `belongsToAccount()`（`:69-71`）按账户隔离；跨账户的旧记录在 `verifyDevice()` 中被识别为 `storageBlocked`，不会误用（`:235-236`）。
- `mayRetireIdleUnknown()` 仅在"本账户 + STARTING + 无订单号 + 超过 2 分钟"时允许清除"已核实未出水"的失败记录（`:103-108`），不误删需人工核对的订单。

### 6. 支付宝 exported 回调组件

- Manifest 中 `PayResultActivity` / `AlipayResultActivity` 为 `exported="true"`（`:67-72`），系支付宝 SDK 回调所需；其余所有 App Activity 均 `exported="false"`。
- 对该 SDK 的静态分析显示其用进程内随机请求号比对 `intExtra`，不符则走失败分支；这是 SDK 自身职责，非本项目代码，分析产物不入库。
- 本项目不读取这两个 Activity 的回调来确认交易；用水成功以服务端状态为准（见第 3 项）。故不存在"外部 Intent 伪造支付成功"的本地链路。

## 未发现 / 未修改项（明确记录，不为凑数而改）

- **未修改任何业务逻辑代码**：上述 8 项控制均有实现与测试证据支撑，不存在需止损的缺口。
- `dismissUnknown`（`WaterActivity.java:94`）是现场测试工具中一个未接线的 UI 按钮，属功能完整性问题而非安全缺口，且修改涉及 UI，按"控制修改范围"要求不在本次处理。
- 第三方 SDK 的 TLS / 重定向 / 组件导出行为属于 S6 审计范围；本记录不对其下结论。

## 测试结果

- 核心断言 `python scripts/test_core.py`：**1136 条全过**（含上述状态机、结果核实、持久化加解密、只读隔离相关断言）。
- 构建 `python scripts/build_preview.py`：普通版构建通过，v2/v3 签名校验通过。
- 真实交易/重复请求测试：**未执行**（避免对生产服务造成影响）；所有高风险路径均以 Mock/解析层单元测试覆盖。
