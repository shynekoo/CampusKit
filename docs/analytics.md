# 匿名使用统计（可选，默认关闭）

> 依据当前代码：`client/core/Analytics*`、`client/android/AnalyticsPreferences`、`AnalyticsReporter`、
> `server/analytics-worker/`。本文只写设计与约束，具体字段以源码为准。

## 一句话

读者自己打开开关后，应用每天最多向开发者自建的统计服务发一次「这个安装今天活跃过」，内容是
**一个本机随机生成的 UUID + 当前版本号**；服务端只用 HMAC 后的值入库，保留 90 天。关掉即停，
并可请求删除已上传记录。

这是本项目**唯一**一处向外发送与业务无关的数据的服务。教务、支付宝、慧生活、趣智校园的请求
仍然由手机直连各自官方平台，不经过这个 Worker。

## 当前状态：已实现，未启用（地址不可达）

2026-11-21 实测：Worker 已部署，Cloudflare 侧一切正常（脚本、`DB` 绑定、`cron` 清理、HMAC 密钥都在），
但 `*.workers.dev` 在国内**按域名（SNI）封锁**：

| 测试 | 结果 |
| :-- | :-- |
| 同一边缘 IP `104.21.8.221`，SNI = `www.cloudflare.com` | `200` |
| 同一 IP，SNI = `campuskit-analytics.campuskit.workers.dev` | TLS 被重置（`curl` 退出码 35 / 56） |
| 本机 DNS 解析 `*.workers.dev` | 超时 |
| Chrome 直连 `/health` | `Failed to fetch` |

结论：**Cloudflare 的边缘是通的，被封的是 `workers.dev` 这个域名本身。** 换一个自定义域即可，
Worker 代码与 D1 一行都不用改。

所以本功能当前**暂停启用**：代码在 `feat/analytics` 分支，出包不传 `--analytics-endpoint`，设置页显示
「统计服务尚未配置」，一个请求都不发。重启步骤：

1. 准备一个 NS 托管在 Cloudflare 的域名（自有域名，或 `eu.org` / DigitalPlat 这类免费域名）
2. 在 `wrangler.toml` 加自定义域路由：
   ```toml
   routes = [{ pattern = "stats.<你的域名>", custom_domain = true }]
   ```
   然后重新 `deploy`
3. **先验可达再出包**：用 DoH 取真实 IP，`curl --resolve "stats.<域名>:443:<IP>"` 打通（`200`）才继续
4. 带 `--analytics-endpoint https://stats.<域名>` 构建，再跑 [`real-device-acceptance.md`](real-device-acceptance.md) N1–N12

红线：**不要用「代理下 curl 能通」判定地址可用**——那只证明代理通，不证明用户通。当前构建里
`ANALYTICS_ENDPOINT` 是空串，统计链路整体不可用，这是刻意保持的状态。

## 为什么是一个 Worker 而不是第三方 SDK

| 方案 | 不采用的原因 |
| :-- | :-- |
| Firebase / 友盟等统计 SDK | 会额外申请权限、自带设备标识与广告标识采集、行为不可审计；与「无广告无推送」的既有边界冲突 |
| 学校或平台侧计数 | 学校接口没有公开的活跃统计，绕过去等于越权访问 |
| 开发者自建 Worker + D1 | 请求体可枚举、入库字段可枚举、保留期可控、删除可执行；没有 SDK 的黑盒部分 |

## 数据边界

**上报（仅一处，`AnalyticsReporter.report`）**

```
POST {ANALYTICS_ENDPOINT}/v1/active
{"installId":"<本机 UUID>","version":"2.6.0"}
```

- `installId`：`UUID.randomUUID()`，只在本机生成；不读 ANDROID_ID / IMEI / OAID / MAC，不含学号。
- `version`：`PackageInfo.versionName`，字符集 `[A-Za-z0-9._+-]`，≤32 字符。
- 没有日期、时间、时区、机型、系统版本、IP（IP 只在 Worker 内存中做限流，不落库）、
  课程、成绩、余额、用水、账号或任何凭据。

**入库（`server/analytics-worker/schema.sql`）**

| 列 | 内容 |
| :-- | :-- |
| `day` | 服务器按 UTC+8 现算的日期，客户端传什么都无关 |
| `anon_id` | `HMAC-SHA256(ANALYTICS_HMAC_SECRET, installId)` 的 hex，64 字符 |
| `app_version` | 上报的版本号 |

主键 `(day, anon_id)` + `INSERT OR IGNORE`，所以同一安装一天最多一行。

## 客户端行为

| 场景 | 行为 | 实现点 |
| :-- | :-- | :-- |
| 从没同意 / 升级安装 | 不发请求、不生成标识 | `AnalyticsPreferences.consented` 默认 false；`installId()` 只读不建 |
| 同意后首次 | 当场生成 UUID 并报一次 | `AnalyticsReporter.setConsent(true)` → `ensureInstallId` |
| 同一天再开若干次 | 只有第一次发请求，之后按本地日期直接返回 | `AnalyticsPreferences.dueToday` |
| 第二天 | 日期不同 → 再报一次 | 同上 |
| 断网 / 429 / 5xx / 应答读不懂 | 不写「今天报过了」，不重试、不提示；下次进前台再试 | `AnalyticsReply.TRANSIENT` |
| 服务端明确拒绝（4xx） | 同样不记日期，下次再试；不弹窗 | `AnalyticsReply.REFUSED` |
| 关闭开关 | 立即停止上报，并请求删除已上传记录 | `setConsent(false)` → `forget()` |
| 删除失败 | 留「待删除」标记，下次进前台继续删；标识在删成功前不丢 | `AnalyticsPreferences.forgetWaiting` |
| 卸载重装 | 偏好随应用数据删除，得到新的 UUID（`allowBackup=false`，不进系统备份） | `AndroidManifest.xml` |
| 覆盖升级 | 偏好保留，UUID 不变 | 同上 |

触发点是 **进程进入前台**（`App` 的 `ActivityLifecycleCallbacks`，`started` 0→1），不是
`TimetableActivity.onResume`：一次回到前台只触发一次，页面之间切换不会重复请求，也就没有
「多个启动点各发一次」的问题。

### 故障隔离

- 全部请求在 daemon 线程，5 秒连接 / 8 秒读，不跟随重定向，应答上限 4 KB。
- 任何异常都被 `catch` 住并只写一条诊断事件；统计坏了不会影响课表、教务、支付、用水、更新。
- 构建期没有 `--analytics-endpoint` 时，`BuildFlags.ANALYTICS_ENDPOINT` 为空，`usable()` 恒 false：
  开关是灰的、副标题写「统计服务尚未配置」，一个请求都不发。

## 诊断日志

只记状态码与判定，不记标识、不记请求体：

```
INFO  [ANALYTICS] report.done   http=200 stage=counted  duration=312
WARN  [ANALYTICS] report.done   http=503 stage=retry    duration=118
ERROR [ANALYTICS] report.failed reason=NETWORK_TIMEOUT  error=SocketTimeoutException
INFO  [ANALYTICS] forget.done   http=200 duration=204
```

字段全部在 `DiagRedact` 白名单内，`scripts/test_analytics.py` 另外静态检查统计模块的
`Diag.*` 调用里不出现标识或请求体。

## 服务端

接口、部署步骤与查数 SQL 见 [`server/analytics-worker/README.md`](../server/analytics-worker/README.md)。
第一阶段不提供读接口：DAU / MAU / 版本分布 / 趋势在 D1 控制台跑 SQL。

服务端自身的测试（日期算术、字段校验、HMAC、去重、删除、限流、清理）用 `node --test`：

```bash
cd server/analytics-worker && npm test
```

## 测试与验收

```bash
python scripts/test_analytics.py     # 静态守卫 + 跑上面的 node --test
python scripts/test_core.py          # 日期/请求体/应答判定的 JVM 断言
python scripts/test_ui_contract.py   # 开关、删除入口、隐私文案锚点
```

真机上尚未验证的项目见 [`real-device-acceptance.md`](real-device-acceptance.md) 的统计一节：
真实网络下的上报、断网补报、关闭后删除、覆盖升级标识不变、卸载重装换新标识。这些必须在有了
部署好的 Worker 地址与真实设备之后才能勾选。
