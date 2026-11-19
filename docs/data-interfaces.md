# 数据接口（架构级）

> 只记录数据来源分类、入口类与约束。不公开私有抓包记录、凭据、用户数据或协议密钥材料。具体请求字段、路径常量、版本头与业务码表以源码为准（如 `EduClient`、`LifeDeviceApi`、`UseCodeApi`、`HttpForm`），不从本文档复制。

## 来源总览

| 来源 | 入口类 | 用途 | 写操作边界 |
| :-- | :-- | :-- | :-- |
| 学校教务系统 | `EduClient`（android）+ `Edu*` core 解析类 | 登录、课表导入、成绩/平时分/空教室、学业情况（GPA/学分树） | 只读查询；课表导入为本地存储 |
| 慧生活798 | `LifeAuthApi`、`LifeDeviceApi`、`WaterApi` | 登录授权、设备资料/状态（只读）、订单状态查询 | 控制请求仅在 field-test source set |
| 趣智校园 | `SmsAccountApi`、`UseCodeApi` | 余额、使用码（8 位） | 使用码设置/生成；余额只读 |
| 支付宝 SDK | `AlipayLogin`、`QzxyAlipayActivity` | 授权登录、充值收银台、用水免密 | 支付凭据不经应用服务器 |
| 淘宝/菜鸟 | `PickupCodeLinks`（core） | 唤起取件身份码页面 | 仅跳转，不访问取件数据 |
| 应用内更新 | `UpdateCheck`、`UpdateManifest`、`UpdateUrls` | 读取 `delivery/update/update.json` 并下载 APK | 校验通过后交系统安装器，用户点安装 |

## 通用传输约束（`HttpForm` 等）

- HTTPS only；禁用自动重定向，逐跳受控；连接/读取超时与响应体积上限（如 15s / 256KB，按端点不同）。
- 域名/路径白名单（如设备请求 `LifeDeviceApi.allowed()`、教务 `EduClient.checkPath()`、WebView `TrustedUrlPolicy`），不在白名单的地址直接拒绝。
- 请求只带会话需要的最小字段；凭据从加密存储按使用时机读出，不写入日志或诊断输出。

## 解析与模型（core）

- `TimetablePdfParser` / `TimetableWebCourseParser`：教务网页表格与七列文字 PDF 表格；扫描版 PDF 会明确提示失败。
- `EduAcademic.parse()`：学业情况页一次 GET 服务端渲染整棵树，无分页 JSON；解析前先剥离 HTML 注释（真实页面统计行被注释切分），从 `title1` 提取已获/要求学分与通过标记，按完整 id 去重并保留首个有效节点；按脚本 `appendTo($("#ul父ID"))` 还原父子层级（实测 37 节点 5 层）；有 `MAX_HTML=4_000_000` / `MAX_NODES=120` 上限；输出 GPA、统计与学分树。
- 课程明细 `POST /xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html`：按 `xfyqjd_id` 一次取一个课组的课程列表，参数取自页面隐藏字段（`EduClient.valueOf()` 双向匹配 `name`/`id` 与 `value`，不依赖属性顺序）；`EduAcademic.parseCourses()` 解析为课程名/学分/成绩/绩点/修读状态/学期/课程性质；状态码 1 在读、3 未修、4/21 已通过、2 未过。UI 侧懒加载并缓存。
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
