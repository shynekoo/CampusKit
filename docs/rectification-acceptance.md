# CampusKit 整改整体验收报告（S1–S6 + A1–A4）

- 基准：本地 2.5.8.1，起点提交 `890e9d8`
- 完成日期：2026-11-19（第一轮 S1–S6+A1–A4；第二轮 A3 深化 + A1 三屏提取，见第五节）
- 原则：每项测试先行 / 最小改动 / 独立提交；保持 Java、原生 View、Python 构建、用户数据与旧版升级兼容
- 真机验收：`docs/real-device-acceptance.md`（全部待执行），未完成时不得发布

## 一、逐项修改与提交

| 项 | 提交 | 修改 | 性质 |
| --- | --- | --- | --- |
| S1 | `54e6023` | 更新清单 sha256/bytes 必填；下载逐跳 HTTPS 校验；验 APK 包名/版本/签名指纹 | 安全修复 |
| S2 | `ba2944a` | 诊断全配置默认关闭；显式开启才接 SDK 日志；脱敏扩展；构建期开关抽取 | 安全修复 |
| S3 | `c28fffc` | 新增 core `TrustedUrlPolicy`；学校站仅 HTTPS/批准域名/443；禁混合内容；导航双重载 + 新窗口兜底 | 安全修复 |
| S4 | `f89fa86` | 新增 core `SnapshotPolicy`；敏感页不产生返回快照、清除残留；快照释放；教务密码页 FLAG_SECURE | 安全修复 |
| S5 | `f7ac199` | 审计记录：支付/用水链路 8 项均已有实现与测试覆盖，未改业务逻辑 | 审计（未发现缺口） |
| S6 | `d5c5edd` | 教务登录页导航准入统一到 TrustedUrlPolicy（原仅校验 host）；新增源码守卫；审计报告 | 安全修复 + 审计 |
| A1 | `1b58c09` | core `MainNavigator`：导航状态机；Activity 状态来源改委托，UI 代码逐字不变 | 架构（状态提取） |
| A2 | `6daf036` | core `Course`/`CourseJsonMapper` 无损映射；android `CourseAdapter` 桥接；不改存储键 | 架构（模型层） |
| A3 | `c6599cf` | core `ClockRedraw`；30s 时钟仅在换日或今日 tab 重绘，滚动位置保留 | 架构（决策提取） |
| A4 | `93ffdf4` | core `SessionCheck`：会话检查去重/缓存/网络错误与登出隔离；单调时钟 | 架构（协调提取） |

## 二、测试结果（本环境实际执行）

| 领域 | 检查 | 结果 |
| --- | --- | --- |
| 核心断言 | `test_core.py` | **1196 条全过**（起点约 1110 → +86） |
| 构建配置 | `test_build_config.py` | 5/5 通过（普通/用水/显式诊断/组合/显式关） |
| WebView 守卫 | `test_webview_policy.py` | 5/5 WebView 导航 HTTPS-aware（测试先行验证过红→绿） |
| 普通版构建 | `build_preview.py` | 通过；1196 断言；v2/v3 签名 true；WATER=false DIAG=false |
| 用水版构建 | `build_preview.py --field-water` | 通过；WATER=true DIAG=false；v2/v3 签名 true |
| 显式诊断版 | `build_preview.py --diagnostics` | 通过；WATER=false DIAG=true；v2/v3 签名 true |
| DEX 路由隔离 | 只读版 `control_route_occurrences_in_dex` | 6 条控制路由 occurrence **全为 0** |
| 更新边界（S1） | 真实重定向链实测 | update.json 与 APK 直链 0 跳转、全 HTTPS；逐跳策略不破坏现有链路 |
| 产物恢复 | 三次构建后 | 已重建普通版，`delivery/科信小助手-2.5.8.1.apk`（10:28），BuildFlags 为纯发行配置 |

三配置均保留了构建验证记录（上面的开关实测值与签名结果）。

## 三、架构取舍说明（A1–A4）

用户要求"只改代码组织和数据流、不改变 UI/协议、职责清晰比行数更重要、不为重构而重构"。据此：

- `TimetableActivity` 从 1155 → **1170 行**（略增）。提取到 core 的是**可独立测试的状态/决策内核**（导航、课程模型、重绘判定、会话协调），而非搬动数百行 View 构建代码。UI 代码逐字未动，规避了此环境无法真机验证的视觉/手势回归风险。
- HomeScreen / ScheduleScreen / AccountScreen 三个 View 类**未提取**：它们是纯 UI 组装，拆分需传入 Activity 与大量可变状态，收益是文件行数下降，代价是手势返回、滚动位置、主题切换的高回归风险，与"不回退体验"的底线冲突。作为后续独立任务更合适。
- 新增 core 类均无 Android/org.json 依赖，全部纳入 core 断言，行为由测试锁定而非靠点击验证。

## 四、遗留风险与未执行项（诚实标注）

以下为本环境**无法执行或需真机/授权**的验证，未冒充通过：

1. **真机 UI/手势回归**：Tab 切换、返回手势取消、周次切换、深色模式、短屏/字体缩放、桌面组件——未实测。建议发版前在真机走一遍 A1/A3 涉及的页面。
2. **旧版覆盖升级**：旧包 → 2.5.8.1 的课表/凭据保留——未实测（遵守"旧版升级兼容"底线，需真实升级路径）。
3. **用水真机与付款**：只读版隔离有 DEX 证据，但真实出水/关水/账单、状态恢复、进程被杀——未对生产服务测试（S5 明确禁止）。
4. **支付宝 SDK / MobSF**：SDK 为固定版本 + 严格主机名校验，未做 MobSF 扫描；扫描结果也需人工复核，不等于漏洞结论。
5. **签名轮换 / 旧 Android 版本 / 异常 APK**（S1 提醒）：指纹比对兼容现有签名身份，但轮换场景未实测。
6. **FLAG_SECURE（S4）**：教务密码页已加，真机截图/最近任务缩略图效果未实测。

## 五、追加整改（第二轮，2026-11-19）

用户复核后指出 A1–A4 只是"第一阶段低风险解耦"，要求继续深化 A3（真局部刷新）与 A1（提取 Screen）。本轮追加 5 个独立提交：

| 提交 | 内容 |
| --- | --- |
| `913167f` | 自动 UI 结构守卫（26→33 锚点）+ 真机验收清单 `docs/real-device-acceptance.md`（全部标注待验收） |
| `454b44b` | **A3 深化**：新增 core `RefreshDecision`（NONE/TODAY_DYNAMIC/FULL）+ `TodayDynamic`；今日 tab 同日时钟 tick 改就地更新摘要/每行状态，不再全量 `render()`；跨天/切 tab 才重建；数据不一致自动回退 FULL；保留滚动位置、周次切换、桌面组件 |
| `84c26bc` | **A1 阶段1**：提取 `CampusScreen`（校园页组装） |
| `a440ba0` | **A1 阶段2**：提取 `HomeScreen`（今日页组装），A3 引用链保持闭合 |
| `a0954f6` | **A1 阶段3**：提取 `ScheduleScreen`（课表页组装），Pager/自适应高度/周次切换回调保持原样 |

三个 Screen 为 `TimetableActivity` 的静态嵌套类：复用其私有 UI helper，`this`→Activity 引用逐字搬运，不放宽任何可见性，行为等价。`render()` 现为纯分发（今日/课表/校园）。Activity 行数 1155→1245（增），符合"职责清晰比行数更重要"。

**A3 行为等价性**：就地更新只改摘要文本 + 每行的删除线/透明度/"正在上课"标记/contentDescription；live chip 由"条件 addView"改为"常驻 + visibility 切换"（GONE 不占空间，视觉一致），使时钟更新无需触碰 View 树。所有会改变今日课程集合的操作（增删改/导入/周次/主题）仍走 `render()` 全量重建并刷新引用。

### 追加验证结果（本环境实际执行）
- 核心断言 **1209 条全过**（起点 1196 → +13）
- 自动测试脚本全绿：`test_core` / `test_build_config`(5) / `test_webview_policy`(5) / `test_ui_contract`(33)
- 三配置构建开关实测：普通 `WATER=false DIAG=false`、用水 `WATER=true DIAG=false`、诊断 `WATER=false DIAG=true`；均 1209 断言 + v2/v3 签名通过
- 只读版 DEX 控制路由 occurrence 仍全为 0（R8/DEX 扫描随构建执行）
- 已恢复普通版产物 `delivery/科信小助手-2.5.8.1.apk`（11:01）
- 工作区干净，无未提交改动

### 仍待真机验收（未冒充通过）
A3 就地更新的视觉无闪烁、滚动位置、跨天/周次、深色模式/短屏，及 A1 三屏提取后的返回手势/卡片动画——`docs/real-device-acceptance.md` E 组与 D 组逐项列出，**必须在真机执行后才可发布**。

## 六、发布状态

- **未 push、未发版、未同步下载站**：只做整改与提交。`delivery/update/update.json` 仍为 2.5.7（线上下载站版本未动）。
- **真机验收未完成，不得发布**（`docs/real-device-acceptance.md` 全部待勾选）。
- 发版时需按项目发版清单执行：构建 → 更新 update.json（version/versionCode/notes/url/bytes/sha256）→ GitHub 同步 → 下载站重新发布 → 核对线上 200。
- 注意：显式诊断版会产出与正式版同名 APK；务必确认用不带 `--diagnostics` 的普通构建（本轮产物已恢复为普通版）。
