# 数据接口（架构级）

> 只记录数据来源分类、入口类与约束。不公开私有网络调试记录、凭据、用户数据或协议密钥材料。具体请求字段、路径常量、版本头与业务码表以源码为准（如 `EduClient`、`LifeDeviceApi`、`UseCodeApi`、`HttpForm`），不从本文档复制。

## 来源总览

| 来源 | 入口类 | 用途 | 写操作边界 |
| :-- | :-- | :-- | :-- |
| 学校教务系统 | `EduClient`（android）+ `Edu*` core 解析类 | 登录、课表导入、成绩/平时分/空教室、学业情况（GPA/学分树）、学生评价状态 | 只读查询；课表导入为本地存储；评价在原生答题页填写后提交到学校系统（不预填、不代选、不批量） |
| 慧生活798 | `LifeAuthApi`、`LifeDeviceApi`、`WaterApi` | 登录授权、设备资料/状态（只读）、订单状态查询 | 控制请求仅在 field-test source set |
| 趣智校园 | `SmsAccountApi`、`UseCodeApi` | 余额、使用码（8 位） | 使用码设置/生成；余额只读 |
| 支付宝 SDK | `AlipayLogin`、`QzxyAlipayActivity` | 授权登录、充值收银台、用水免密 | 支付凭据不经应用服务器 |
| 淘宝/菜鸟 | `PickupCodeLinks`（core） | 唤起取件身份码页面 | 仅跳转，不访问取件数据 |
| 应用内更新 | `UpdateCheck`、`UpdateSources`、`UpdateManifest`、`UpdateUrls`、`UpdateChannel` | 从所选渠道（WorkBuddy 默认 / GitHub / Gitee 可选）读 `update.json`（正式）与 `beta.json`（预览，仅在开关打开时读）并下载 APK | 三条通道走同一套校验后交系统安装器，用户点安装；渠道只决定「去哪读」，不改变校验强度 |
| 匿名活跃统计（唯一自建服务） | `AnalyticsReporter`（android）+ `AnalyticsRequest`/`AnalyticsReply`（core）；服务端 `server/analytics-worker` | 读者主动开启后每天最多一次：`POST /v1/active`（随机安装标识 + 版本号），关闭时 `POST /v1/forget` | 只写统计库（HMAC 后的标识、日期、版本），不接触任何业务数据；失败静默、不影响业务 |

### 更新渠道与稳定地址

- `UpdateSources` 集中保存六个清单地址（core，JVM 测试与应用共用同一份）：WorkBuddy 同目录 `update.json?sharecode=…` + `beta.json?sharecode=…`；GitHub `releases/latest/download/update.json` + `releases/download/preview/beta.json`；Gitee（**公开**仓库 `gitee.com/sun-heyue/CampusKit`）`releases/download/release/update.json` + `releases/download/preview/beta.json`。Gitee 侧必须 public：私有仓库对匿名读附件回 403，而建仓 API 即使传了 `private: false` 也可能落成私有，发布前要在仓库页确认可见性。
- GitHub 的 `releases/latest` 排除预发布版本，因此预览清单固定在 `preview` tag；Gitee 没有可用的 `latest/download` 路由，正式清单固定在 `release` tag：每次发版只替换该 tag 下的同名资产，地址不变。
- 清单里的 APK `url` 必须指向固定 tag/固定文件名（如 `releases/download/v2.5.9/kexin-xiaozhushou-2.5.9.apk`），不能用 `latest/download/<带版本号的文件名>`——新版发布后旧文件名在 latest 下不存在。
- GitHub Release 资产下载会 302 到带签名 query 的 CDN；Gitee 会先 302 到 `gitee.com/<owner>/<repo>/attach_files/…`，再 302 到 `foruda.gitee.com/attach_file/…?token=…&ts=…`。`UpdateUrls` 逐跳保留 Location 自带的参数（签名 / token+ts），同时剥掉与上一跳同名（如 `sharecode`）的参数，防止凭据跨 host 泄漏。Gitee 链路是两跳且不需要登录（公开仓库）。
- 默认渠道是 WorkBuddy；未保存过或读到未知值都回落到 WorkBuddy。渠道与「接收预览版更新」两项独立设置，互不影响。GitHub / Gitee 渠道不受 WorkBuddy 站点所有权限制。
- 实测参考（同一网络、同一时段）：WorkBuddy 托管约 4 MB/s，Gitee 附件约 0.7 MB/s，GitHub Release 在境内常在数十 KB/s 量级。Gitee 的意义是「境内稳定可达」，不是最快。

### 匿名统计（Cloudflare Worker + D1）

- 地址由构建期 `--analytics-endpoint` 写死进 `BuildFlags.ANALYTICS_ENDPOINT`，运行期没有任何入口能改它；空串=未部署，整条链路不工作。
- `POST /v1/active` 请求体固定两个字段：`installId`（本机随机 UUID，36 字符）与 `version`（≤32 字符，字符集 `[A-Za-z0-9._+-]`）。服务端拒绝多余字段（如 `studentId`、`day`），整包上限 512 字节。
- 日期由服务端按 UTC+8 自行确定，请求里没有日期/时间/时区字段。
- 应答：`200 {"ok":true,"counted":…}`=已记；`400`=请求不合规；`413`=包过大；`429`=限流；`503`=密钥缺失或 D1 不可用（失败关闭）。客户端把 `400` 之外的失败与断网一律当作「下次进前台再试」，只有 `200 + ok` 才在本地记下「今天报过了」。
- `POST /v1/forget` 用同一个标识删除该安装的全部历史记录；没有读接口，统计结果在 D1 控制台查（SQL 见 `server/analytics-worker/README.md`）。
- 传输约束与更新链路一致：HTTPS、`setInstanceFollowRedirects(false)`、5s 连接 / 8s 读、应答上限 4KB。

## 通用传输约束（`HttpForm` 等）

- HTTPS only；禁用自动重定向，逐跳受控；连接/读取超时与响应体积上限（如 15s / 256KB，按端点不同）。
- 域名/路径白名单（如设备请求 `LifeDeviceApi.allowed()`、教务 `EduClient.checkPath()`、WebView `TrustedUrlPolicy`），不在白名单的地址直接拒绝。
- 请求只带会话需要的最小字段；凭据从加密存储按使用时机读出，不写入日志或诊断输出。

## 解析与模型（core）

- `TimetablePdfParser` / `TimetableWebCourseParser`：教务网页表格与七列文字 PDF 表格；扫描版 PDF 会明确提示失败。
- `EduAcademic.parse()`：学业情况页一次 GET 拿到服务端渲染的整棵树，没有分页 JSON，因此按 id 去重、按页面脚本的 `appendTo` 还原父子层级；有 `MAX_HTML` / `MAX_NODES` 上限；输出 GPA、学分统计与学分树。
- 课程明细是两个只读端点，按课组标签上的 `jdkcsx` 选择，与学校页面自身的分流一致；参数取自页面隐藏字段，按 `name` / `id` 双向匹配取值，不依赖属性顺序。字段名与拼装方式以 `EduClient`、`EduAcademic.parseCourses()` 为准，本文档不复制。
- `EduEvaluation.parse()`：学生评价列表走学校 jqGrid 的 `doType=query` 只读接口，一个教学班一条；状态优先取服务端返回的状态词，缺失时按学校状态码归一；有 `MAX_JSON` / `MAX_ROWS` 上限。列表本身不带统计，统计另取，失败不影响列表。列表会分页，`EduClient.evaluations()` 按 `totalPage` 循环取页并跨页去重——只读一页会漏课。
- 学生评价的**问卷**是学校服务端渲染的 HTML 片段，由 `EduEvaluationForm.parse()` 读成题目与选项，再在原生控件上渲染，因此这条链路不引入任何网页执行环境。片段不闭合自身标签，切片按「同类开始标签」的平铺边界做，不按嵌套计数。请求形态、参数字段与回填方式以 `EduEvaluationForm` 为准：只发学校页面自身下发的那一种形态，本文档不抄写字段表；识别不了的题型记为 `UNSUPPORTED`，页面说明并阻止提交，不按猜测的接口形状发参数。
- 暂存与提交是两个独立写端点，只接受 `EduEvaluationForm.body()` 拼好的请求体。学校以一句话回应，`outcome()` 先判否定词再判成功短语，判不出来就如实说「还不能确认」。**写完必须回读状态**：重新查列表确认已评才显示「已提交」；回话成功但学校只记了暂存时明说；超时或断网不重发，先查状态。提交只在用户手动选完并二次确认后才发，不代填、不批量。
- `EduClient.checkPath()` 只按名字放行指定的读端点与两个写端点，其余教务路径同样走白名单。
- `Course` / `CourseJsonMapper`：课程无损映射到 JSON，不改既有存储键。
- `UseCode`：8 位码按 5+3 分段供键盘显示。

## 用水状态机（core）

`UseSession` 状态：STARTING / ACTIVE / STOPPING / SETTLING / RECONCILE / COMPLETED。断连一律进 RECONCILE，不回落 IDLE；`reconcile()` 只接受已核实的远端结果；`restore()` 把持久化的中间态映射回 RECONCILE。`PendingUse` 在命令发出前落盘，`PendingUseVault` 加密保存，登出清会话但保留待核实记录。core 断言覆盖重复点击、8 线程并发仅一个 start 通过、各中间态恢复。

## 修改接口时的检查清单

1. 新路径/域名是否加入白名单？是否 HTTPS？
2. 是否复用 `HttpForm`（超时、体积上限、重定向策略）？
3. 解析是否有体积/节点上限，避免异常页面卡 UI？
4. 是否补 core 断言（解析/状态机）与必要的源码守卫？
5. 是否同步本文档分类与 [`security.md`](security.md) 的约束（若边界改变）？
6. 是否更新 [`CHANGELOG.md`](../CHANGELOG.md) 未发布段？
