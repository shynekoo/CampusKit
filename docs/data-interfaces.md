# 数据接口（架构级）

> 只记录数据来源分类、入口类与约束。不公开私有抓包记录、凭据、用户数据或协议密钥材料。具体请求字段、路径常量、版本头与业务码表以源码为准（如 `EduClient`、`LifeDeviceApi`、`UseCodeApi`、`HttpForm`），不从本文档复制。

## 来源总览

| 来源 | 入口类 | 用途 | 写操作边界 |
| :-- | :-- | :-- | :-- |
| 学校教务系统 | `EduClient`（android）+ `Edu*` core 解析类 | 登录、课表导入、成绩/平时分/空教室、学业情况（GPA/学分树）、学生评价状态 | 只读查询；课表导入为本地存储；评价的填写与提交在学校自己的页面上完成 |
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
- `EduAcademic.parse()`：学业情况页一次 GET 服务端渲染整棵树，无分页 JSON；解析前先剥离 HTML 注释（真实页面统计行被注释切分），从 `title1` 提取已获/要求学分与通过标记，按完整 id 去重并保留首个有效节点；按脚本 `appendTo($("#ul父ID"))` 还原父子层级（实测 37 节点 5 层）；有 `MAX_HTML=4_000_000` / `MAX_NODES=120` 上限；输出 GPA、统计与学分树。每个课组标签还带 `jdkcsx`：它的取值决定课程走哪个端点（见下一条），`Node.planCourses` 保存该判定；未带标记的页面默认走计划内端点。哪些行可点由树的父子关系决定——学校只给叶子节点挂展开链接，父行只折叠。
- 课程明细有两个只读端点，按课组标签上的 `jdkcsx` 选择（学校脚本即如此）：`POST /xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html`（`jdkcsx == '1'`，计划内课程）与 `POST /xsxy/xsxyqk_cxJxzxjhxfyqFKcxx.html`（其余取值，计划外课程，如通识选修/公共艺术）。`EduAcademic.Node.planCourses` 携带该判定；计划外请求只带 `fromXh_id`/`xfyqjd_id`/`xh_id`，计划内请求额外回显页面隐藏学年学期字段。按 `xfyqjd_id` 一次取一个课组的课程列表，参数取自页面隐藏字段（`EduClient.valueOf()` 双向匹配 `name`/`id` 与 `value`，不依赖属性顺序）；`EduAcademic.parseCourses()` 解析为课程名/学分/成绩/绩点/修读状态/学期/课程性质；状态码 1 在读、3 未修、4/21 已通过、2 未过。UI 侧懒加载并缓存。
- `EduEvaluation.parse()`：学生评价（评教）列表走学校 jqGrid 的 `doType=query` 只读接口，返回每个教学班一条扁平对象；解析出教学班/课程/教师/学时类型（理论·实验·实践）与学校自己的状态码（`tjzt`：`-1` 未评、`0` 暂存、`1` 已提交），状态词优先用服务端 `tjztmc`，缺失时按码归一；没有教学班 id 的行无法打开表单，直接丢弃；`MAX_JSON=2_000_000` / `MAX_ROWS=400` 上限。`EduEvaluation.counts()` 读评价情况接口的 `wpgs/bcgs/tjgs` 与 `pjtsxx` 提示（列表本身不带统计，两者分开取；统计失败不影响列表）。
- 学生评价的**问卷**是学校服务端渲染的 HTML 片段，端点与参数就是列表页 `xspj_index.js` 里那一句 `$(panel_content).load("/xspjgl/xspj_cxXspjDisplay.html", {七项})`：**POST，七项只在请求体**。2026-11-20 用真实账号（传热学 · 孟鑫）逐项实测，三种失败各有确定原因，因此现在**只发这一种形态**：
  | 形态 | 结果 |
  | :-- | :-- |
  | `?gnmkdm=N401605` + 七项在请求体（官方） | **200，46,234 B，1 个面板 11 道题** |
  | 同上，但查询串里再拼一份 id | 200 + 「参数异常」页（6,923 B） |
  | 不带 `gnmkdm` | HTTP **911** + 392 B 的页面模型 JSON |
  | 不带 `X-Requested-With` | **照样 200 + 完整表单**（因此不加这个头，少一处无依据的差异） |
  片段里没有暂存/提交按钮（`#btn_xspj_bc` / `#btn_xspj_tj` 在列表页 HTML 里），但题目、选项与全部 id 都在片段里，因此可以原生渲染，不需要 WebView。查询串重复 id 这一条尤其重要：两版「双写」修复都在这里被学校判成参数异常，却被读成「id 不对」。
- `EduEvaluationForm.parse()` 读这份片段。切片方式是**平铺的兄弟边界**（每个 `panel-pjdx` / `table-xspj` / `tr-xspj` 一直延伸到下一个同类开始标签），不是按标签嵌套计数：学校这份片段不闭合自己的 `<div>`（线上传热学那张表 63 个 `<div>`、73 个 `</div>`），按嵌套切会在第二题处收尾，把 11 道题读成 2 道，用户看到的就是「学校没有下发评价项」。选择器与学校自己的 `xspj_display.js` 一一对应：`div.panel-pjdx` 一份评价模板（`data-pjmbmcb_id / pjmbmc / pjdxdm / fxzgf / xspfb_id`）、`table.table-xspj` 一个一级指标、`tr.tr-xspj` 一个评价项（`data-pjzbxm_id / pfdjdmb_id / dtlx / sfbt / qzz`，`dtlx` 1 主观题 / 2 客观单选 / 3 多选）；选项是 `div.input-xspj` 包着 `input.radio-pjf`（单选，值取 `data-pfdjdmxmb_id`）或 `input[type=checkbox]`（多选），标签是该 `div` 的文字；十级制行的 `select[name=pjf]` 直接回分数；主观题是 `textarea.input-zgpj`；必须 `data-sfbt="1"`。识别不了的题型记为 `UNSUPPORTED`，页面上说明「这一项要在学校页面上填」并**阻止提交**，绝不按猜的形状发参数。`data-xspj-body` 上的 `ztpjbl / jszdpjbl / xykzpjbl` 与四个 id 也一并读回，请求要原样回显。
- `EduEvaluationForm.body()` 生成的请求体与学校 `buildRequestMap()` 逐字段同构（用真实片段对比过 60 个字段，除 `tjzt` 由按钮决定外键值全同）：`modelList[i].pjmbmcb_id / pjmbmc / pjdxdm / fxzgf / py / xspfb_id`，题目在 `modelList[i].xspjList[j].childXspjList[k].` 下，`j` 是一级指标表序号、`k` 是表内行序号。每一行必须回填**三个身份字段**：`pjzbxm_id`（二级指标）、`pfdjdmb_id`（评分等级）、`zsmbmcb_id`（模板名称，取 `tr` 的 `data-zsmbmcb_id`）——答与不答都发，它们是身份不是答案；一级指标表自己还有一个不同的 `pjzbxm_id` 写在 `modelList[i].xspjList[j].pjzbxm_id`。此外 `modelList[i].pjzt` 由「必填项是否全答」算出（1 全答 / 0 未答完），与学校脚本一致。答案字段：单选/多选写 `pfdjdmxmb_id`（多选同名字段重复出现，因此请求体按字符串拼，不走 `Map`），十级制写 `pjf`，主观题写 `zgpj`；**未作答的题目不发答案字段**。评语按学校做法先 `encodeURIComponent` 再由表单编码，请求体里是**双重编码**——服务端对该字段解码两次，只编码一次会乱码。实测：客户端自己拼的这份 body 直接 POST 到暂存端点，学校回 `"暂存成功！"`，回读时 10 个单选与主观题文字都在。
- 暂存/提交是两个独立端点：`POST /xspjgl/xspj_bcXspj.html`（`tjzt=0`）与 `POST /xspjgl/xspj_tjXspj.html`（`tjzt=1`），同一份 `modelList` 参数。学校用**一句话**回应，而且是 **JSON 字符串**（实测 `"暂存成功！"`，7 字节），所以 `outcome()` 先去掉引号再判定：否定词（不成功 / 失败 / 不能 / 请先 / 错误 / 异常 / 已放弃 / 维护）**先判**，成功必须是学校自己的短语（整体提交成功 / 暂存成功 / 提交成功 / 保存成功）；只说「操作成功」不算数，因为「提交不成功」也含「成功」。判不出来就如实说「还不能确认」。**写完必须回读状态**：`EduClient.evaluationState()` 重新查列表里这一行的 `tjzt`，提交只有变成 `1` 才显示「已提交，学校已记为已评」；仍是 `0`（暂存）就明说「学校收了但没有记成已评」；查询失败则记 `write.unverified` 事件。超时或断网时**不重发**，先去查状态。提交是一次性的：只在用户手动选完并二次确认后才发，不代填任何一项。
- 列表接口 `POST /xspjgl/xspj_cxXspjIndex.html?gnmkdm=N401605&doType=query` 是 jqGrid，**会分页**：`EduClient.evaluations()` 按 `queryModel.currentPage` 循环取页（每页 100），页数取回复里的 `totalPage` / `total`，缺失时用 `totalResult ÷ 每页条数` 估算；某页没有新行即停止，跨页按 `jxb_id` 去重。只读一页会漏课程，而漏掉的课看起来就像「应用弄丢了」。
- `EduClient.checkPath()` 只放行五个按名字指定的 `/xspjgl/` 端点：列表查询、评价情况统计、问卷片段、暂存、提交。写端点只接受 `EduEvaluationForm.body()` 拼好的请求体，会话、HTTPS 与 Referer 仍由同一套传输处理。
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
