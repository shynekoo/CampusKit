# 趣智校园充值链路（1.1.0）

2026-11-15。本次仅做离线反编译与离线请求构造，没有用真实账号发起充值，也没有产生任何订单。

## 来源

原版 6.5.28 的代码在内存里，直接反编译 APK 拿不到业务类。本次从已有的
`analysis/private/lab-dex/*.dex`（MuMu 实验机上 `read_lab_dex.py` 抓取的内存 dex）中按类名抽取：

```
jadx --single-class com.klcxkj.zqxy.ui.recharge.RechargeActivity
jadx --single-class ui.h          # 路由表
jadx --single-class sj.e0         # 签名
jadx --single-class com.klcxkj.zqxy.response.RechargeAliPayResponse
```

## 路由

`ui.h` 里每个方法都按 `MyApp.e` 二选一，`MyApp.e` 来自 `ProjectInfo.getIsMigrated()`：

| 支付方式 | 未迁移（wfwapi） | 已迁移（v3-api） |
| --- | --- | --- |
| 支付宝 2 | `wallet/recharge/aliPay` | `settlement/alipay/recharge/balance` |
| 微信 5 | `wallet/recharge/weChat` | `settlement/weChat/recharge/app/balance` |
| 银联 6 | `wallet/recharge/union` | `settlement/union/recharge/balance` |
| 招行 23 | `wallet/recharge/cmb` | `settlement/cmb/recharge/balance` |
| 聚合 26 | `wallet/recharge/juhePay` | `settlement/juhe/recharge/balance` |
| 校园卡 36 | — | `settlement/campus/jump/address` |

基址：`wi.a.a = https://wfwapi.china-qzxy.cn/`，`wi.a.b = https://v3-api.china-qzxy.cn/`。

## 请求

`RechargeActivity.U2()`（支付宝）：先算签名，再 `M0(ui.h.n(), {payWay, rechargeMoney, signature}, …, false)`。

`M0` → `t0(map, MyApp.e)` 补齐公共字段，其中 `z=false` 时**不加 userId**，但加
`projectId`、`accountId`、`telephone`、`telPhone`、`loginCode`、`phoneSystem`、`version`，
随后 `X0` 以 POST 发送。

金额：自填框 `Integer.valueOf` 后限制 300–1000；套餐金额来自 `wallet/recharge/package` 的
`package_account` 字符串。本版只做整数元，1–1000，由 `RechargeApi.normalize` 收口。

响应：`RechargeAliPayResponse.data` 是字符串，即交给支付宝的 APP 支付订单串。原版用
`sj.b.a().d(ctx, data, true, cb)`，即 `new PayTask(activity).payV2(orderInfo, true)`。

2026-11-15 修正：1.1.0 用 `alipays://platformapi/startapp?appId=20000067&url=<订单串>` 是错误的——
该 `url` 参数要的是网页地址，订单串是 `app_id=…&biz_content=…&sign=…` 的键值串，支付宝打开后
无法识别，表现为「跳不过去」。1.2.0 改为直接绑定支付宝客户端支付服务（`AlipayHandoff`）：
service action `com.eg.android.AlipayGphone.IAlixPay`，descriptor `com.alipay.android.app.IAlixPay`，
取版本号后 `registerCallback03`（版本 ≥3）并 `r03("pay","bind_pay")`、`pay02(orderInfo, map)`
（版本 ≥2，否则 `Pay`）；同时向支付宝注册 `IRemoteServiceCallback` 的 Binder 桩，支付宝通过其
`startActivity(pkg, cls, pid, bundle)` 事务拉起自己的收银台。付款结果从返回串的
`resultStatus={9000}` 解析，余额随后重读。全部 Parcel 序号抄自 alipaySdk 15.8.42 的反编译
`IAlixPay` / `IRemoteServiceCallback`，未引入 SDK jar。

仍未确认：订单是开给趣智官方 app_id 的，第三方包代发是否被支付宝接受只能实机验证。

## 签名

`sj.e0.e(Map)`：取 key 排序 → 依次拼接「key + value」→ 追加 `JniUtils.getSk()` → MD5 小写。
只对 `loginCode / payWay / rechargeMoney / telPhone` 四个字段签名；公共字段在签名之后才加。

`getSk()` 在 `libklcxkjencry.so` 里只有两条返回路径：

```
0xa2133 "keep&1!"      ← 标志位 0xcc0e8 == 0
0xa213b "sign-kailu="  ← 标志位 != 0
```

`Java_com_klcxkj_jni_JniUtils_checkSignature` 在把 `PackageInfo.signatures[0].toCharsString()` 与常量
比对成功后写入 `strb w9(1), [x8,#0xe8]`。也就是说原版自己跑的时候标志为 1，返回 `sign-kailu=`。
本版默认用这个值；另一个值只在服务器明确报签名错误时重试一次（签名不过时服务器不建单）。

## 仍未确认

1. **迁移标志与 token 的对应关系**：`y0.A(z)` 在 `z=true` 时返回 `v3LoginCode`，否则返回
   `loginCode`；而签名用的是 `z() = A(MyApp.e)`，请求体用的却是 `A(false)`。原版在已迁移账号上
   这两个值可能不同。本版两处统一用会话里的同一个 `loginCode`，保证服务端按收到的字段重算签名时
   自洽；若某校已迁移且必须用 `v3LoginCode`，会表现为登录类错误而不是静默错账。
2. **服务器是否真的校验 signature**：没有任何真实请求证据。若校验，错误会带明确消息。
3. **套餐 / 赠送**：本版不读 `wallet/recharge/package`，界面只给整数金额，赠送以官方为准。
4. **支付宝能否唤起**：`alipays://` 方案未在本机验证过订单串。
