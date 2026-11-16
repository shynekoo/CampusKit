# 科信小助手 2.3.10

版本码 169 · 2026-11-16

## 这一版的依据：同类开源客户端 WaterWidget

按用户建议不再闭门造车，找到了同类项目 **[WaterWidget](https://github.com/nocookies111/life-798)**（`nocookies111/life-798`，Kotlin，146 星，2026-10 仍在维护），直接读它的请求层 `IlifeApi.java`，把我们猜了很多版的问题一次性对清：

| 项目 | 我们此前 | WaterWidget（实测可用） |
| --- | --- | --- |
| `VersionCode` 头 | 数字 `31005` / `31010` | **版本名字符串 `"3.1.9"`** |
| `User-Agent` | `CampusKit/…` | **`Android_ilife798_3.1.9`** |
| `Accept-Language` | 无 | `zh-Hans-CN;q=1` |
| 开启路径 | `dev/mode/start`（绕路） | **`dev/start`** |
| 开启参数 | `did&upgrade&ptype&args&rcp&cnt` | **`did&upgrade&ptype&rcp`**（无 `args`、无 `cnt`） |
| 签名 | 怀疑需要 | **设备控制不需要**（`sign` 只用于积分任务） |

谜底：**「请升级最新版APP」从来不是新协议，而是版本头格式错了**。服务端要的是版本名字符串，我们一直发数字，它解析不出可比的版本，把请求当成极旧客户端退回。之前 `dev/mode/start` 能进业务逻辑只是路径白名单碰巧不含版本检查，不是正确的路。

另外从它的源码确认了业务码表（实测）：`-19` 设备准备中、`-20` 未绑定一卡通、`-21` 设备登录信息未接受、`-52` 账户欠费、`-82` 需要支付、`-87` 签约过期、`-88` 未签约代扣协议。这张表已吸收进 `LifeDeviceFailure`：服务端 `msg` 缺失时也有解释。

## 改了什么

- `LifeVersion` 新增 `DEVICE_VERSION="3.1.9"`、`DEVICE_UA="Android_ilife798_3.1.9"`、`ACCEPT_LANGUAGE`；`LifeProtocolProbe.request()`（所有设备控制请求的出口）改用这三个头，并带 `Content-Type: application/json`。
- `startPath` 回到 `dev/start`，参数精简为 `did、upgrade、ptype、rcp`；`dev/mode/start` 与 `mode()` 读取删除。
- 保留 2.3.9 的支付方式修正：21（支付宝免密）默认，商家钱包余额 0 时排后。
- 诊断 JSON 的版本字段改为 `deviceVersionHeader`（记录实际发送的头值）。

## 请试

更新 → 扫码 → 开启用水（默认「支付宝免密」）。若这次仍有错误码，提示会带具体含义（欠费/未签约/未绑一卡通等），按提示处理或把诊断发我。

1034 项核心断言通过（本版改路径断言、删 mode 断言、加三个头断言），v2/v3 签名通过。**未经真机验证。**
