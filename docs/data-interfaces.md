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
| 应用内更新 | `UpdateCheck`、`UpdateSources`、`UpdateManifest`、`UpdateUrls`、`UpdateChannel` | 从所选渠道（WorkBuddy 默认 / GitHub 可选）读 `update.json`（正式）与 `beta.json`（预览，仅在开关打开时读）并下载 APK | 两条通道走同一套校验后交系统安装器，用户点安装；渠道只决定「去哪读」，不改变校验强度 |

### 更新渠道与稳定地址

- `UpdateSources` 集中保存四个清单地址（core，JVM 测试与应用共用同一份）：WorkBuddy 同目录 `update.json?sharecode=…` + `beta.json?sharecode=…`；GitHub `releases/latest/download/update.json` + `releases/download/preview/beta.json`。
- GitHub 的 `releases/latest` 排除预发布版本，因此预览清单固定在 `preview` tag；每次发预览版只更新该 tag 下的资产（同名覆盖），地址不变。
- 清单里的 APK `url` 必须指向固定 tag/固定文件名（如 `releases/download/v2.5.9/kexin-xiaozhushou-2.5.9.apk`），不能用 `latest/download/<带版本号的文件名>`——新版发布后旧文件名在 latest 下不存在。
- GitHub Release 资产下载会 302 到带签名 query 的 CDN。`UpdateUrls` 逐跳保留 Location 自带的参数（签名），同时剥掉与上一跳同名（如 `sharecode`）的参数，防止凭据跨 host 泄漏。
- 默认渠道是 WorkBuddy；未保存过或读到未知值都回落到 WorkBuddy。渠道与「接收预览版更新」两项独立设置，互不影响。GitHub 渠道不受 WorkBuddy 站点所有权限制。

## 通用传输约束（`HttpForm` 等）

- HTTPS only；禁用自动重定向，逐跳受控；连接/读取超时与响应体积上限（如 15s / 256KB，按端点不同）。
- 域名/路径白名单（如设备请求 `LifeDeviceApi.allowed()`、教务 `EduClient.checkPath()`、WebView `TrustedUrlPolicy`），不在白名单的地址直接拒绝。
- 请求只带会话需要的最小字段；凭据从加密存储按使用时机读出，不写入日志或诊断输出。

## 解析与模型（core）

- `TimetablePdfParser` / `TimetableWebCourseParser`：教务网页表格与七列文字 PDF 表格；扫描版 PDF 会明确提示失败。
- `EduAcademic.parse()`：学业情况页一次 GET 服务端渲染整棵树，无分页 JSON；解析前先剥离 HTML 注释（真实页面统计行被注释切分），从 `title1` 提取已获/要求学分与通过标记，按完整 id 去重并保留首个有效节点；按脚本 `appendTo($("#ul父ID"))` 还原父子层级（实测 37 节点 5 层）；有 `MAX_HTML=4_000_000` / `MAX_NODES=120` 上限；输出 GPA、统计与学分树。每个课组标签还带 `jdkcsx`：它的取值决定课程走哪个端点（见下一条），`Node.planCourses` 保存该判定；未带标记的页面默认走计划内端点。哪些行可点由树的父子关系决定——学校只给叶子节点挂展开链接，父行只折叠。
- 课程明细有两个只读端点，按课组标签上的 `jdkcsx` 选择（学校脚本即如此）：`POST /xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html`（`jdkcsx == '1'`，计划内课程）与 `POST /xsxy/xsxyqk_cxJxzxjhxfyqFKcxx.html`（其余取值，计划外课程，如通识选修/公共艺术）。`EduAcademic.Node.planCourses` 携带该判定；计划外请求只带 `fromXh_id`/`xfyqjd_id`/`xh_id`，计划内请求额外回显页面隐藏学年学期字段。按 `xfyqjd_id` 一次取一个课组的课程列表，参数取自页面隐藏字段（`EduClient.valueOf()` 双向匹配 `name`/`id` 与 `value`，不依赖属性顺序）；`EduAcademic.parseCourses()` 解析为课程名/学分/成绩/绩点/修读状态/学期/课程性质；状态码 1 在读、3 未修、4/21 已通过、2 未过。UI 侧懒加载并缓存。
- `EduEvaluation.parse()`：学生评价（评教）列表走学校 jqGrid 的 `doType=query` 只读接口，返回每个教学班一条扁平对象；解析出教学班/课程/教师/学时类型（理论·实验·实践）与学校自己的状态码（`tjzt`：`-1` 未评、`0` 暂存、`1` 已提交），状态词优先用服务端 `tjztmc`，缺失时按码归一；没有教学班 id 的行无法打开表单，直接丢弃；`MAX_JSON=2_000_000` / `MAX_ROWS=400` 上限。`EduEvaluation.counts()` 读评价情况接口的 `wpgs/bcgs/tjgs` 与 `pjtsxx` 提示（列表本身不带统计，两者分开取；统计失败不影响列表）。
- 学生评价的**问卷**是学校服务端渲染的 HTML 片段，端点与参数都是学校脚本里写死的那一组：`POST /xspjgl/xspj_cxXspjDisplay.html?gnmkdm=N401605`，带 `jxb_id / kch_id / xsdm / jgh_id / tjzt / pjmbmbcb_id / sfcjlrjs`（缺 `gnmkdm` 时返回 500，故模块号写进路径）。片段里没有暂存/提交按钮（`#btn_xspj_bc` / `#btn_xspj_tj` 在列表页 HTML 里），但题目、选项与全部 id 都在片段里，因此可以原生渲染，不需要 WebView。
- `EduEvaluationForm.parse()` 读这份片段，选择器与学校自己的 `xspj_display.js` 一一对应：`div.panel-pjdx` 一份评价模板（`data-pjmbmcb_id / pjmbmc / pjdxdm / fxzgf / xspfb_id`）、`table.table-xspj` 一个一级指标、`tr.tr-xspj` 一个评价项（`data-pjzbxm_id / pfdjdmb_id / dtlx / sfbt / qzz`，`dtlx` 1 主观题 / 2 客观单选 / 3 多选）；选项是 `div.input-xspj` 包着 `input.radio-pjf`（单选，值取 `data-pfdjdmxmb_id`）或 `input[type=checkbox]`（多选），标签是该 `div` 的文字；十级制行的 `select[name=pjf]` 直接回分数；主观题是 `textarea.input-zgpj`；必须 `data-sfbt="1"`。识别不了的题型记为 `UNSUPPORTED`，页面上说明「这一项要在学校页面上填」并**阻止提交**，绝不按猜的形状发参数。`data-xspj-body` 上的 `ztpjbl / jszdpjbl / xykzpjbl` 与四个 id 也一并读回，请求要原样回显。
- `EduEvaluationForm.body()` 生成的请求体与学校 `buildRequestMap()` 同构：`modelList[i].pjmbmcb_id / pjmbmc / pjdxdm / fxzgf / py / xspfb_id`，题目在 `modelList[i].xspjList[j].childXspjList[k].` 下，`j` 是一级指标表序号、`k` 是表内行序号；单选/多选写 `pfdjdmxmb_id`（多选同名字段重复出现，因此请求体直接按字符串拼，不走 `Map`），十级制写 `pjf`，主观题写 `zgpj`。未作答的题目**不发字段**（学校脚本同样跳过）。评语按学校的做法先 `encodeURIComponent` 再由表单编码，因此请求体里是**双重编码**——服务端对该字段解码两次，只编码一次会乱码。
- 暂存/提交是两个独立端点：`POST /xspjgl/xspj_bcXspj.html`（`tjzt=0`）与 `POST /xspjgl/xspj_tjXspj.html`（`tjzt=1`），同一份 `modelList` 参数。学校用**一句话**回应（`"整体提交成功"` / `"…成功"` / `"…失败"`），`EduEvaluationForm.outcome()` 只按这句话判断成败，判不出来的原样显示给用户，不假装成功。提交是一次性的：应用只在用户手动选完并二次确认后才发，且不代填任何一项。
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
