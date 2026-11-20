# 运行日志与故障诊断

> 依据 2.5.8 的实际代码整理：`client/core/src/io/qzxy/lite/core/DiagRedact.java`、`DiagEvent.java`、`DiagCatalog.java`，
> `client/android/src/io/qzxy/lite/preview/Diag.java`、`DiagFiles.java`、`DiagActivity.java`、`DiagReportActivity.java`。

## 1. 它解决什么问题

用户说「课表加载不出来」，这句话本身无法定位。可能断在四步中的任何一步：

```
登录教务 → 请求课表接口 → 解析返回 → 映射成本机课程 → 画到屏幕上
```

诊断日志要能指出断在哪一步、为什么断、花了多久。它**不是**更新日志、不是 Git 记录，
也**不是**把 Logcat 抄一遍——Logcat 有全量输出，但没有结论。

## 2. 一条记录长什么样

```
2026-11-19 21:30:01.120 INFO [EDU] login.start trace=3f9a1c02 thread=worker stage=page
2026-11-19 21:30:01.650 INFO [EDU] login.success trace=3f9a1c02 thread=worker duration=530
2026-11-19 21:30:02.001 INFO [TIMETABLE] load.start trace=8841de20 thread=worker stage=request
2026-11-19 21:30:04.012 WARN [NETWORK] request.timeout trace=8841de20 thread=worker route=/kbcx/xskbcx_cxXsKb.html duration=2011
2026-11-19 21:30:04.018 ERROR [TIMETABLE] load.failed trace=8841de20 thread=worker reason=NETWORK_TIMEOUT error=SocketTimeoutException
    at io.qzxy.lite.preview.EduClient.request(EduClient.java:1001)
    at io.qzxy.lite.preview.EduClient.fetch(EduClient.java:821)
```

固定格式：

```
日期 时间.毫秒 等级 [模块] 事件代码 trace=关联ID thread=线程 [字段=值 ...]
```

- **日期时间**：本地时区，毫秒精度。
- **等级**：`INFO` / `WARN` / `ERROR` / `CRASH`。
- **模块**：`APP`、`EDU`、`TIMETABLE`、`NETWORK`、`LIFE`、`QZXY`、`AUTH`、`UPDATE`、`UI`、`DIAG`。
- **事件代码**：稳定标识（`load.failed`），文案改动不影响它。
- **关联 ID**：一次操作内几条记录共用，随机生成，与用户身份无关。
- **线程**：`main` / `worker`（其余压成 ASCII 短名）。
- **字段**：只允许白名单内的键（见 §4）。

等级约定：

| 等级 | 什么时候用 | 例子 |
| :-- | :-- | :-- |
| `INFO` | 关键操作成功、阶段推进 | 登录成功、课表已加载、请求返回 200 |
| `WARN` | 可恢复异常、超时、异常返回 | 请求超时、候选端点没数据、授权被取消 |
| `ERROR` | 请求失败、解析失败、业务异常 | 课表加载失败、登录被拒、校验不通过 |
| `CRASH` | 未捕获的 Java 异常 | `APP crash` + 安全堆栈 |

`DEBUG` / `VERBOSE` 不进入文件：正式包没有这两级；内部排障用 `--diagnostics` 增加事件密度，
而不是增加等级。

## 3. 两种构建模式

| | 发布版（默认） | 内部版 `--diagnostics` |
| :-- | :-- | :-- |
| 诊断页入口 | **有**（更多 → 诊断日志） | 有 |
| 运行期默认 | **关**，用户手动开启 | 关，需手动开启 |
| 基础事件 | 登录、课表、网络、慧生活、趣智、更新、失败原因 | 同左 |
| 细节事件 | 无 | 返回手势逐帧、页面打开、渲染结果 |
| SDK 原文 | **不接** | **也不接**（2.5.8 起彻底移除） |
| 字段白名单 | 相同 | 相同 |

`BuildFlags.DIAG_BASIC` 恒为 `true`；`BuildFlags.DIAGNOSTICS` 只在 `--diagnostics` 下为 `true`
（代码里由 `Diag.DETAIL` 引用）。两者都由 `scripts/build_preview.py` 生成，
`scripts/test_build_config.py` 断言这个组合。

> 为什么发布版也带：早期版本把整套诊断代码从正式包里删掉，结果是普通用户遇到问题时
> 手上没有任何证据，只能复述「打不开」。现在带进正式包的是**经过审计的基础事件**——
> 没有自由文本字段、没有 SDK 原文、不联网、默认不记录——细节事件仍然只给内部构建。

## 4. 字段白名单（安全边界）

字段不是「先记下来再脱敏」，而是**只有这些键能进文件**；键名不在表里，值直接被丢掉。

| 类别 | 键 | 允许的形状 |
| :-- | :-- | :-- |
| 阶段 | `stage` `step` `method` `kind` `result` `reason` `slot` `error` `webview` `page` `version` | `[A-Za-z0-9_.:+-]{1,72}`，不合形状写成 `OTHER` |
| 数字 | `duration` `attempts` `count` `bytes` `size` `ms` `http` `code` `status` `week` `term` `line` `lost` `courses` `frames` | `-?[0-9]{1,9}`，不是数字就丢掉 |
| 路径 | `route` | `[A-Za-z0-9_./-]{1,96}`，查询串在写入前被截掉 |
| 机型 | `device` | ASCII 字母数字与空格括号，≤48 |
| 关联 | `trace` | 格式位，不占字段；8 位十六进制 |

没有 `message`、`text`、`body`、`payload` 这一类字段——**日志里不存在能承载自由文本的位置**，
所以课程名、成绩、请求体、服务器原话都没有入口。异常只留类型和安全堆栈。

堆栈只保留 `类名.方法名(文件:行号)`，最多 12 帧，跨 `Caused by` 也计数；异常消息一个字都不写。

### 禁止记录（硬性）

- 密码、Cookie、Session、Token、授权码及其可利用片段
- 身份证号、手机号、学号、账户标识
- 支付凭据、签名材料、二维码内容
- 完整请求体 / 响应体 / HTTP Header
- 含凭据的 URL、WebView Cookie、页面内容
- 课程明细、成绩等与定位无关的个人数据

### 运行时复查

`scripts/test_diag_privacy.py` 静态检查每个调用点：

- 字段键必须在白名单里（否则运行时会被静默丢掉）；
- 模块、事件代码、失败原因必须有 `DiagCatalog` 文案；
- `Diag.step` / `Diag.raw` / `maskChain` / `maskSdk` / `setupLogCallback` 不得复活；
- 导出必须走 `ACTION_CREATE_DOCUMENT` + UTF-8，清单里不得出现存储权限。

值层面的脱敏由 `CoreTests` 断言（手机号、学号、token、Cookie、URL 查询串、邮箱、长十六进制串、
JWT、超长文本、堆栈不含异常消息）。

## 5. 存储与轮转

- 目录：`files/diag/`（应用私有，不参与备份，不申请任何存储权限）。
- 当前文件：`diag.log`；历史：`diag.1.log` ~ `diag.3.log`。
- 单文件上限 **256 KB**，写满整体改名为上一代，第四代删除；总量约 **1 MB**。
- 历史文件超过 **7 天**在启动时删除。
- 写入走 `ArrayBlockingQueue`（512 行）+ 单写线程，主线程只入队；队列满时丢弃并记一条
  `DIAG log.lost count=N`，不对业务线程施加反压。
- 轮转用 `renameTo` 整文件改名，**不截断**——用户可能正打开日志页准备复制。
- 单文件上限在任何时刻都成立：启动时若发现上次退出留下的 `diag.log` 已超限，先只保留末尾
  256 KB 再归档；一次写入本身超过 256 KB 时也只保留尾部。否则「单文件 ≤ 256 KB、总量约 1 MB」
  会被一次异常写入或旧版本遗留文件悄悄破掉。
- 读的顺序固定为 `diag.3` → `diag.1` → `diag.log`，解析器逐行匹配，截断点上的半行被忽略。
- 清空：先清队列再清文件（含历史），避免刚清完又把内存里的旧行写回去。
- 关闭开关后不再采集任何普通事件；关闭动作本身会先写一行 `DIAG log.stop` 再落盘。

## 6. 崩溃捕获

`Thread.setDefaultUncaughtExceptionHandler` 在 `App.onCreate` 时安装（比任何页面都早）。

- 触发时**同步**写入一条 `CRASH [APP] crash` + 安全堆栈，绕过队列，有界（≤12 帧）。
- 写完把异常**原样交回**系统处理器（`previous.uncaughtException`），不吞异常、不改崩溃行为；
  没有前一个处理器时才 `killProcess` 保底。
- 记录在文件里，重启后日志页仍能看到——崩溃发生在 `session.start` 之后，位置一眼可见。
- 注册失败、写入失败都只吞掉自己，不影响业务。

**能力边界（不要夸大）**：它只覆盖未捕获的 **Java 异常**。ANR、系统杀进程、native crash、
被厂商 ROM 直接杀掉的场景不会留下这条记录。这些场景需要 `tombstone` / `dumpsys`，不在本应用范围内。

## 7. 各模块记录了什么

| 模块 | 事件 | 能定位的问题 |
| :-- | :-- | :-- |
| 教务 `EDU` | `login.start` / `login.stage` / `login.success` / `login.failed` | 停在读登录页、取公钥、提交还是拿会话；密码被拒 / 验证码 / 会话失效 |
| 课表 `TIMETABLE` | `load.start` / `request.end` / `load.empty` / `request.failed` / `load.failed` / `parse.failed` / `parse.success` / `import.saved` / `store.unreadable` | 哪个候选端点、第几次尝试、失败原因、解析是否通过、本机存储是否损坏 |
| 网络 `NETWORK` | `request.end` / `request.timeout` / `response.parse.failed` | HTTP 状态码、路由、耗时；超时与解析失败分开 |
| 慧生活 `LIFE` | `login.start` / `auth.returned` / `request.end` / `login.success` / `login.failed` | 唤起支付宝、回调是否回来、服务端业务码 |
| 支付宝 `AUTH` | `auth.start` / `auth.returned` / `auth.failed` | SDK 是否返回、状态码（9000/6001…）、是否给了授权码 |
| 趣智 `QZXY` | `code.failed` / `request.rejected` | 接口不可用、业务码拒绝、解析失败 |
| 更新 `UPDATE` | `check.start` / `check.available` / `check.failed` / `install.start` / `download.failed` / `verify.failed` | 清单读不到、下载中断、大小 / 摘要 / 包身份校验失败 |
| 界面 `UI` | `nav.open` / `back.gesture` / `render.done`（仅内部版） | 页面是否打开、返回手势走到哪一步、渲染结果 |
| 诊断 `DIAG` | `log.start` / `log.stop` / `log.lost` / `log.write.failed` | 诊断系统自身的状态 |

业务逻辑不因记录而改变：所有调用点只读，不参与分支，不重试，不改异常类型（`EduClient.json()`
只是补了 `cause`，消息文本不变）。

## 8. 使用：用户怎么复现、怎么导出

**正式版（用户主动诊断）**

1. 更多 → **诊断日志**。
2. 打开「诊断记录」开关（默认关）。
3. 回到出问题的页面，按平时的步骤复现一次。
4. 回到诊断日志：顶部能看到「记录状态：正在记录」「日志大小：36 KB」。
5. 按等级（错误 / 警告 / 信息）或模块（课表 / 登录 / 网络…）筛选，点条目展开看字段与堆栈。
6. **复制日志**（当前筛选结果）或 **导出文件**（UTF-8 `.txt`，系统文件选择器，不申请存储权限）。
7. 需要更完整的上下文时，进 **故障报告**：选模块、写问题描述，导出成一份可直接发出去的文本。

**内部版**

```bash
python scripts/build_preview.py --diagnostics --version-name 2.5.13-beta.1 --version-code 215
```

安装后流程相同；区别只是多出 `UI` 的细节事件。

## 9. 故障报告的内容

- App 版本与 versionCode
- Android 版本（API）
- 设备厂商 + 型号
- 用户选择的故障模块
- 用户填写的问题描述（≤1500 字）
- 最近最多 200 条脱敏记录（按模块过滤）
- 最近一次错误级记录及其堆栈

不包含账号、Cookie、Token、学号、课程明细、请求正文。报告只在本机生成，导出位置由用户选择。

## 10. 测试与验证

| 入口 | 覆盖 |
| :-- | :-- |
| `python scripts/test_core.py` | 行格式 / 解析往返、字段白名单、脱敏（手机号、学号、token、Cookie、URL、邮箱、JWT、长串）、堆栈、原因分类、文案映射 |
| `python scripts/test_diag_privacy.py` | 调用点字段键 / 模块 / 代码 / 原因的可查性，已移除的入口不得复活，导出路径与权限 |
| `python scripts/test_build_config.py` | `DIAG_BASIC` 恒开、`DIAGNOSTICS` 只在显式参数下开 |
| `python scripts/test_diag_files.py` | 在 JVM 上直接驱动 `DiagFiles`：写满轮转、四文件封顶、读出的行号连续、超限遗留文件先裁剪、单次超长写入被截尾、清空连历史一起删、7 天前的历史被清理 |
| `python scripts/test_ui_contract.py` | 诊断页筛选 / 分页 / 导出 / 清空确认 / 入口在正式版可见；不得引入 RecyclerView |

**真机才能验的**（静态测试不能替代）：

- 开关打开 → 复现 → 杀进程 → 重开，日志仍在且顺序正确；
- 崩溃后重启能看到 `CRASH` 记录与堆栈；
- 导出文件在另一台设备 / 电脑上能正常打开，中文不乱码；
- 长日志（接近 1 MB）滚动、筛选、展开不卡顿；
- 深色主题、大字号下开关与筛选胶囊可读、可点。

清单见 [`real-device-acceptance.md`](real-device-acceptance.md) 的「诊断」一节。

## 11. 加一个新事件

1. 在 `DiagCatalog.title()` 里登记事件代码的中文说法（不登记会被守卫脚本拦下）。
2. 选一个已存在的模块代码；新模块要同时改 `DiagRedact` 的模块约束与 `DiagCatalog.module()`。
3. 只传白名单字段。需要新字段时先问「这个值会不会带出用户数据」，然后同时改
   `DiagRedact` 与 `scripts/test_diag_privacy.py` 的期望。
4. 高密度事件用 `if (Diag.DETAIL)` 包住，别让正式版记录逐帧数据。
5. 跑四个测试入口。

## 12. 仍缺诊断的模块

- **扫码 / 相机**（`ScanActivity`、`QrDecoder`）：权限拒绝、对焦失败、解码失败目前只在界面提示。
- **课表界面交互**（拖拽、缩放、桌面组件刷新）：没有事件；只有内部版的 `render.done`。
- **支付回调的竞态**（`QzxyAlipayActivity` 生命周期）：只记录了 SDK 返回，没有记录 Activity 被回收。
- **ANR / native crash**：不在能力范围内，见 §6。
- **自动上传 / 服务端聚合**：按设计不做。

## 相关文档

[架构](architecture.md) · [模块](modules.md) · [安全约束](security.md) · [UI 与导航](ui-navigation.md) · [构建与发布](build-and-release.md) · [真机验收](real-device-acceptance.md) · [已知问题](known-issues.md)
