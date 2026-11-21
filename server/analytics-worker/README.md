# 匿名每日活跃统计 · 后端（Cloudflare Workers + D1）

一个安装一天最多一条记录，落库的标识是服务端 HMAC 后的值，保留 90 天。没有读接口，
第一阶段的数据查询在 D1 控制台跑 SQL。

## 接口

| 方法 | 路径 | 请求体 | 说明 |
| :-- | :-- | :-- | :-- |
| `POST` | `/v1/active` | `{"installId":"<uuid>","version":"2.6.0"}` | 记一次当日活跃。日期由服务器按 UTC+8 现算 |
| `POST` | `/v1/forget` | `{"installId":"<uuid>"}` | 删除该安装的全部历史记录（读者关闭统计时） |
| `GET` | `/health` | — | 部署存活检查，无业务数据 |

应答：

```json
{ "ok": true, "counted": true,  "day": "2026-11-20" }   // 首次：新增一条
{ "ok": true, "counted": false, "day": "2026-11-20" }   // 当天重复：未新增
{ "ok": true, "removed": 2 }                             // 删除：删掉几条
{ "ok": false, "error": "..." }                          // 400 / 405 / 413 / 429 / 503
```

失败的四种含义：`400` 请求不合规、`429` 频率超限、`503` 密钥缺失或 D1 不可用、
`413` 请求体过大。客户端把这几种一律当作「下次进前台再试」，不会影响任何业务功能。

## 部署步骤（需要你自己在 Cloudflare 账号里执行）

以下命令都在 `server/analytics-worker/` 下执行。**没有可用的 Cloudflare 凭据时不要执行
`wrangler deploy`；本仓库里 `wrangler.toml` 的 `database_id` 是占位值，未替换前部署会失败。**

1. 安装依赖

   ```bash
   npm install
   ```

2. 登录（浏览器授权，凭据不会写进仓库）

   ```bash
   npx wrangler login
   ```

3. 创建 D1 数据库，把输出的 `database_id` 填进 `wrangler.toml`

   ```bash
   npx wrangler d1 create campuskit-analytics
   ```

4. 建表

   ```bash
   npx wrangler d1 execute campuskit-analytics --file=./schema.sql --remote
   ```

5. 设置 HMAC 密钥（**只走 secret，不要写进任何文件**）

   ```bash
   npx wrangler secret put ANALYTICS_HMAC_SECRET
   ```

   - 建议 32 字节以上随机值，例如本机生成：`openssl rand -hex 32`
   - 换密钥等于换一套匿名标识：换之前已经入库的记录，与之后的记录对不上同一个人。
     这只会让 MAU 在某一天出现一个断点，不产生错误数据。

6. 部署

   ```bash
   npx wrangler deploy
   ```

   输出里的 `https://campuskit-analytics.<你的子域>.workers.dev` 就是服务地址。

7. 冒烟验证（把地址换成上一步的实际域名）

   ```bash
   curl -s https://<你的地址>/health
   curl -s -X POST https://<你的地址>/v1/active \
     -H 'content-type: application/json' \
     -d '{"installId":"0f8fad5b-d9cb-469f-a165-70867728950e","version":"2.6.0"}'
   curl -s -X POST https://<你的地址>/v1/active \
     -H 'content-type: application/json' \
     -d '{"installId":"0f8fad5b-d9cb-469f-a165-70867728950e","version":"2.6.0"}'
   ```

   第二次必须是 `"counted": false`——这就是「一天一次」在生效。

8. 让安卓端用上这个地址：构建时传 `--analytics-endpoint`

   ```bash
   python scripts/build_preview.py --version-name 2.6.0 --version-code 230 \
     --beta --analytics-endpoint https://<你的地址>
   ```

   不带这个参数时生成的包 `ANALYTICS_ENDPOINT` 为空：设置页仍会显示这一项，
   但状态是「统计服务尚未配置」，不会发出任何请求。

9. 打开面板上的频率限制（可选但建议）

   Cloudflare 控制台 → 该 Worker → Security / Rate limiting → 新建规则：
   匹配 `http.request.method eq "POST"`，阈值 20 次 / 分钟 / IP，动作 Block。
   Worker 自己还有一层同样阈值的内存限流（isolate 内有效），面板规则是跨 isolate 的那一层。

10. 定时清理（已写在 `wrangler.toml`）

    每天 03:10 UTC（北京时间 11:10）跑一次 `scheduled`，删除 `day < 今天-89` 的行，
    于是表里正好留 90 个自然日。

## 查数（D1 控制台）

```sql
-- 每日 DAU，最近 30 天
SELECT day, COUNT(*) AS dau FROM daily_active
 WHERE day >= date('now', '+8 hours', '-29 days')
 GROUP BY day ORDER BY day DESC;

-- 最近 30 天 MAU
SELECT COUNT(DISTINCT anon_id) AS mau FROM daily_active
 WHERE day >= date('now', '+8 hours', '-29 days');

-- 今天的版本分布
SELECT app_version, COUNT(DISTINCT anon_id) AS active FROM daily_active
 WHERE day = date('now', '+8 hours') GROUP BY app_version ORDER BY active DESC;

-- 最近 90 天的趋势
SELECT day, COUNT(*) AS dau FROM daily_active GROUP BY day ORDER BY day;
```

## 数据边界（改动前先读）

- 表里只有 `day` / `anon_id` / `app_version` 三列，没有学号、姓名、手机号、设备标识、
  IP、User-Agent、课程或任何业务数据。
- `anon_id` 是 `HMAC-SHA256(secret, installId)` 的十六进制，**不是客户端 UUID**；没有密钥
  拿不回 UUID，换密钥后旧值也对不上任何客户端。
- 日志（`console.log`）只写保留期清理的日期与条数，不写请求体、不写标识。
- 这个 Worker 不接触教务、支付宝、慧生活、趣智校园的任何请求；那些业务仍然由手机直连
  各自官方平台。

## 测试

```bash
npm test        # node --test：日期算术、字段校验、HMAC、去重、删除、限流、清理
```

测试不需要 Cloudflare 账号，也不联网：D1 用内存表模拟，密钥用测试值。
