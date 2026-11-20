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
| 应用内更新 | `UpdateCheck`、`UpdateManifest`、`UpdateUrls`、`UpdateChannel` | 读取 `delivery/update/update.json`（正式）与 `delivery/update/beta.json`（预览，仅在开关打开时读）并下载 APK | 两条渠道走同一套校验后交系统安装器，用户点安装 |

## 通用传输约束（`HttpForm` 等）

- HTTPS only；禁用自动重定向，逐跳受控；连接/读取超时与响应体积上限（如 15s / 256KB，按端点不同）。
- 域名/路径白名单（如设备请求 `LifeDeviceApi.allowed()`、教务 `EduClient.checkPath()`、WebView `TrustedUrlPolicy`），不在白名单的地址直接拒绝。
- 请求只带会话需要的最小字段；凭据从加密存储按使用时机读出，不写入日志或诊断输出。

## 解析与模型（core）

- `TimetablePdfParser` / `TimetableWebCourseParser`：教务网页表格与七列文字 PDF 表格；扫描版 PDF 会明确提示失败。
- `EduAcademic.parse()`：学业情况页一次 GET 服务端渲染整棵树，无分页 JSON；解析前先剥离 HTML 注释（真实页面统计行被注释切分），从 `title1` 提取已获/要求学分与通过标记，按完整 id 去重并保留首个有效节点；按脚本 `appendTo($("#ul父ID"))` 还原父子层级（实测 37 节点 5 层）；有 `MAX_HTML=4_000_000` / `MAX_NODES=120` 上限；输出 GPA、统计与学分树。每个课组标签还带 `jdkcsx`：它的取值决定课程走哪个端点（见下一条），`Node.planCourses` 保存该判定；未带标记的页面默认走计划内端点。哪些行可点由树的父子关系决定——学校只给叶子节点挂展开链接，父行只折叠。
- 课程明细有两个只读端点，按课组标签上的 `jdkcsx` 选择（学校脚本即如此）：`POST /xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html`（`jdkcsx == '1'`，计划内课程）与 `POST /xsxy/xsxyqk_cxJxzxjhxfyqFKcxx.html`（其余取值，计划外课程，如通识选修/公共艺术）。`EduAcademic.Node.planCourses` 携带该判定；计划外请求只带 `fromXh_id`/`xfyqjd_id`/`xh_id`，计划内请求额外回显页面隐藏学年学期字段。按 `xfyqjd_id` 一次取一个课组的课程列表，参数取自页面隐藏字段（`EduClient.valueOf()` 双向匹配 `name`/`id` 与 `value`，不依赖属性顺序）；`EduAcademic.parseCourses()` 解析为课程名/学分/成绩/绩点/修读状态/学期/课程性质；状态码 1 在读、3 未修、4/21 已通过、2 未过。UI 侧懒加载并缓存。
- `EduEvaluation.parse()`：学生评价（评教）列表走学校 jqGrid 的 `doType=query` 只读接口，返回每个教学班一条扁平对象；解析出教学班/课程/教师/学时类型（理论·实验·实践）与学校自己的状态码（`tjzt`：`-1` 未评、`0` 暂存、`1` 已提交），状态词优先用服务端 `tjztmc`，缺失时按码归一；没有教学班 id 的行无法打开表单，直接丢弃；`MAX_JSON=2_000_000` / `MAX_ROWS=400` 上限。`EduEvaluation.counts()` 读评价情况接口的 `wpgs/bcgs/tjgs` 与 `pjtsxx` 提示（列表本身不带统计，两者分开取；统计失败不影响列表）。
- 学生评价的表单是学校**服务端渲染**的片段，由学校**列表页自己**加载。表单片段 `xspj_cxXspjDisplay.html` 内不含暂存/提交按钮（`#btn_xspj_bc` / `#btn_xspj_tj` 在列表页 HTML 里），因此应用**不能**独立请求这个片段。
- **模块页必须在菜单壳文档里运行**：`xspj_cxXspjIndex.html` 及其片段是按「被 `index_initMenu.html` 装进来」写的片段页——脚本裸用 `_path`（拼每个请求地址）、`BaseJqGrid` / `loadJqGrid`（建表格）、`$.i18n`（每个列名）、`viewConfig` / `refershGrid`，这些全局只在壳文档里存在。作为顶层文档 `loadUrl` 时第一个 `BaseJqGrid` 就抛 ReferenceError，`$(function(){…})` 整段中断：表格不建、`onSelectRow` 不绑、`#panel_content` 停在页面自带骨架（状态备注图例 + 「评价内容」「0% Complete」）。因此 `EvaluationFormActivity` 先加载 `EduClient.MENU_PAGE`（`/xtgl/index_initMenu.html`，Referer 为站点根），再把模块 `jQuery(host).load('/xspjgl/xspj_cxXspjIndex.html?gnmkdm=N401605')` 装进一个全屏宿主 `#ckEvalHost`——与学校菜单的做法一致，全局因此可用。
- 模块 id 由 `$.ajaxPrefilter` 补齐：壳文档的 Referer 不带 `gnmkdm`，模块自己的 `/xspjgl/` 请求（`doType=query` 列表、`xspj_cxXspjDisplay.html` 片段、`xspj_cxXspjqk.html` 统计）都会缺模块号而拿到 500；prefilter 只给尚未带 `gnmkdm` 的 `/xspjgl/` url 追加 `N401605`，其余请求原样放行。
- 课程定位走表格自己的数据：`jqGrid('getGridParam','data')` 按 `jxb_id` 精确匹配，再 `jqGrid('setSelection', rowid, true)` 触发学校的 `onSelectRow`（它才发起 `$(#panel_content).load(…)`），并在 DOM 上补一次 `trigger('click')`。**不能**读行的文本——`jxb_id` 是隐藏列，jqGrid 不把它写进 `<td>`。
- 成功判据是 `#panel_content .panel-pjdx` 的**节点数**，不是面板文本长度：面板自带的骨架本来就有字，按字数判会把「没装进来」读成「已打开」。
- 应用不调用 `xspj_tjXspj.html` / `xspj_bcXspj.html`（提交是一次性的，必填项与评语规则由学校模板决定）。`EduClient.checkPath()` 只放行列表与统计两个只读端点，UI 契约测试另有守卫禁止这两个提交端点出现在应用代码里。
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
