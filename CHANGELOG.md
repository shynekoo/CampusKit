# 变更日志

本项目所有值得注意的变更记录。格式参照 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，按版本倒序。

**事实基准**（每条均以仓库代码 / Git 提交 / 实际构建为依据，不推测）：

- 当前正式版：`2.5.9`（versionCode `210`，2026-11-20，修复扫码跳转 + 四段版本号检查不到更新），见下条。
- 上一版正式版：`2.5.8`（versionCode `208`，2026-11-19）；更早为 `2.5.7`（197）。
- `2.5.8.1`（versionCode `209`）曾于 2026-11-20 短暂发布，但四段版名使旧客户端的更新清单解析失败、检查不到更新，已被 2.5.9 取代。
- 另有历史开发包 `2.5.8.1`（versionCode `199`，Git HEAD `6406500`），与上述 209 包**不是**同一产物，未发版。
- 已提交、尚未发版：`2.5.8.2`–`2.5.8.6`（最新 versionCode `204`），含学业情况查询与 UI-1/UI-2 第一轮。
- 已提交并 push 的主线：`16cec29`（UI-1 / UI-2 / UI-3 转场竞态 + 滚动恢复 + 回本周整屏滑页）。
- 已提交到主线、尚未发版：数据与隐私 / 使用公告改版 + 移除未使用的 Wi-Fi 权限；应用内更新日志；使用公告入口去重 + 课表圆角默认 20dp（versionCode `205`）；学业情况少课修复（计划内 / 计划外课组按学校 `jdkcsx` 分流取课端点）。
- 已构建、尚未发版：`2.5.8.8`（versionCode `206`，2026-11-19），在合并后的主线上重建。
- **线上正式版（2026-11-19 发布）**：`2.5.8`（versionCode `208`），把 2.5.8.1–2.5.8.9 合并成一个正式版本号；
  产物 `delivery/科信小助手-2.5.8.apk`，发布公告见 [`delivery/release-2.5.8.md`](delivery/release-2.5.8.md)。
- 核心断言：`python scripts/test_core.py` → **1584 项全过**；UI 守卫 **364 锚点**；诊断隐私守卫 **716 项**；统计守卫 **77 项** + Worker `node --test` **18 项**（2026-11-20）。
- 2.5.8 的 tag / Release / 下载站随本次发布同步；早期 2.5.8.x 开发迭代仍**未**各自建 tag。

更早版本的发布说明见 [`delivery/release-*.md`](delivery/)。

---

## [未发布] — 匿名每日活跃统计（可选，默认关闭）

分支 `feat/analytics`。**尚未提交、未发布**；`2.6.0-beta.11`（versionCode `228`）只在本地构建过，
产物 `delivery/科信小助手-2.6.0-beta.11.apk`，构建时**没有**给 `--analytics-endpoint`，所以统计
在这只包里是灰的、不会发任何请求。

**2026-11-21 更新：功能已实现，但暂停启用。** Worker 已部署到
`https://campuskit-analytics.campuskit.workers.dev`（D1 库 `campuskit-analytics`、表 `daily_active`、
HMAC 密钥、每日清理 cron 均已就位），但实测 `*.workers.dev` 在国内被按 SNI 封锁：同一 Cloudflare
边缘 IP 换 SNI 为 `www.cloudflare.com` 返回 200，换回 `*.workers.dev` 立即被重置，真实用户无法上报。
因此**不进入构建、不发布**，代码留在分支上等一个绑定到 Cloudflare 的自定义域。详见
[`docs/analytics.md`](docs/analytics.md) 的「当前状态」与 [`docs/known-issues.md`](docs/known-issues.md)。

### 新增

- **匿名每日活跃统计**：`校园 → 更多 → 匿名使用统计`，默认关闭。开启后每天最多上报一次，
  内容只有本机随机生成的 UUID 与版本号；关闭即停，并可请求删除已上传记录。
  - 安卓端：`AnalyticsPreferences`（同意状态、随机标识、上次上报日期）、`AnalyticsReporter`
    （后台线程、5s/8s 超时、不跟随重定向、失败不重试不弹窗）。
  - core：`AnalyticsDay`（UTC+8 自然日，无 `java.time`）、`AnalyticsRequest`（两字段请求体与形状
    校验）、`AnalyticsReply`（记上 / 重复 / 拒绝 / 重试四判定）。
  - 触发点：`App` 的进程前台切换（`onActivityStarted`，计数 0→1），不是页面 `onResume`。
  - 服务端：`server/analytics-worker/`（Cloudflare Workers + D1）。`POST /v1/active` 由服务器按
    UTC+8 定日期，`HMAC-SHA256` 后入库，`(day, anon_id)` 联合主键 + `INSERT OR IGNORE` 去重，
    保留 90 天由定时任务清理；`POST /v1/forget` 删除该安装的历史。无读接口，数据在 D1 控制台查。
- 构建开关 `--analytics-endpoint https://…` → `BuildFlags.ANALYTICS_ENDPOINT`（空串=未部署）。
- 诊断新增 `ANALYTICS` 模块：`report.done` / `report.failed` / `forget.done` / `forget.failed`，
  只记状态码与判定，不记标识与请求体。
- 测试入口 `scripts/test_analytics.py`（77 项静态守卫 + 跑 Worker 的 `node --test`），已并入
  `build_preview.py` 的预检与 CI。
- 文档：新增 [`docs/analytics.md`](docs/analytics.md)；`architecture` / `security` / `data-interfaces` /
  `diagnostics` / `modules` / `build-and-release` / `README` 同步；隐私说明新增第 5 节（生效日期
  2026-11-20）。

### 测试结果（实测）

| 入口 | 结果 |
| :-- | :-- |
| `python scripts/test_core.py` | 1584 项全过 |
| `python scripts/test_diag_privacy.py` | 716 项全过 |
| `python scripts/test_ui_contract.py` | 364 锚点全过 |
| `python scripts/test_analytics.py` | 77 项全过 + Worker 18 项全过 |
| `python scripts/test_build_config.py` / `test_diag_files.py` / `test_webview_policy.py` | 全过 |
| `python scripts/build_preview.py --version-name 2.6.0-beta.11 --version-code 228 --beta` | 出包成功，dex 内含 `AnalyticsReporter` / `AnalyticsPreferences` / `v1/active` |

### 未验证

- Cloudflare 侧**未部署**：没有 D1 库、没有 HMAC 密钥、没有 Worker 地址，`wrangler.toml` 的
  `database_id` 仍是占位值。因此真机上的上报、断网补报、关闭后删除、覆盖升级标识不变、卸载重装换
  新标识、服务端异常容错**全部未实测**。
- 统计功能的界面与流程只做了静态守卫（锚点）与 JVM 断言，没有真机走查。

---

## [已发 GitHub / Gitee · 2.6.0-beta.10] — 评教接口按真实抓包重做（versionCode 227）

> 这一版不再猜。用真实账号（传热学 · 孟鑫）登录学校教务，把官方页面发出的每一条请求抓下来逐项复现，
> 三条失败各有确定原因：
>
> | 请求形态 | 学校回应 |
> | :-- | :-- |
> | `POST /xspjgl/xspj_cxXspjDisplay.html?gnmkdm=N401605`，七项 id 只在请求体（**官方**） | **200，46,234 B，1 个面板 11 道题** |
> | 同上，但把 id 也拼进查询串 | 200 + 「参数异常」页（6,923 B） |
> | 不带 `gnmkdm` | HTTP **911** + 392 B 的页面模型 JSON |
> | 不带 `X-Requested-With` | 照样 200 + 完整表单（所以这个头不是必需，也就不加） |
>
> 结论：前两个版本的「双写查询串」正是学校判参数异常的原因；「少了 gnmkdm」则根本不是话，而是一段 JSON。
> 两件事都被读成「id 不对」，于是越修越偏。

### 改动

- **读表单只发一次**（`EduClient.evaluationForm`）：与官方 `load(url, {七项})` 逐字相同，删除「三种载体
  依次试」的回退矩阵与 `putIfPresent`（空值不再跳过，`pjmbmcb_id` 为空也按官方发空串）。
  HTTP 911 / 500 / 200+参数异常 / 200+无表 各自单独成句，不再混成一句。
- **解析改按兄弟边界切片**（`EduEvaluationForm.parse`）：线上片段不闭合自己的 `<div>`（63 开、73 闭），
  按嵌套计数会在第二题处收尾——**11 道题只读成 2 道**，用户看到的就是「学校没有下发评价项」。
  改为每个 `panel-pjdx` / `table-xspj` / `tr-xspj` 延伸到下一个同类开始标签。
- **请求体补齐三层身份**（`EduEvaluationForm.body`）：每行回填 `pjzbxm_id`（二级指标）、
  `pfdjdmb_id`（评分等级）、`zsmbmcb_id`（模板名称，新增读取 `data-zsmbmcb_id`），一级指标表另带自己的
  `pjzbxm_id`；并按「必填项是否全答」写 `modelList[i].pjzt`。与官方 `buildRequestMap()` 对比过 60 个字段，
  除按钮决定的 `tjzt` 外键值全同。
- **成败严格化 + 状态核对**：答复是 JSON 字符串（实测 `"暂存成功！"`，7 字节），先去引号；否定词先判，
  成功必须是学校自己的短语（「提交不成功」含「成功」，旧的 `contains("成功")` 会把它报成完成）。
  写完回读列表里这一行的 `tjzt`：只有变成 `1` 才显示「已提交，学校已记为已评」；仍是 `0`（暂存）就明说
  「学校收了但没记成已评」；查不到记 `write.unverified`。超时或断网**不重发**，先查状态。
- 诊断：`sheet.shape` 只记 `form=official` 与 `bytes`，新增 `sheet.failed stage=read|parse`、
  `write.unverified`；仍不记题目、评语、id。

### 验证状态

- **已用真实账号验证（读 + 暂存）**：官方形态 200/46,234 B/11 题；客户端自己拼的 body 直接 POST 到暂存端点，
  学校回 `"暂存成功！"`，回读时 10 个单选被选中、主观题文字保留，列表该行 `tjzt` 由 `-1` 变 `0`、
  `bfzpf=84.00`，评价情况 `{"wpgs":19,"bcgs":1,"tjgs":0}`。暂存是草稿，可继续改。
- **未验证**：真实提交（`tjzt=1`）与提交后状态同步——这需要用户本人确认后才做，本次没有代提交。
- `test_core` **1539**（+19：三层 id、`pjzt`、不闭合片段 5 题、`outcome` 八种回话）、`test_ui_contract`
  **354**、`test_diag_privacy` **691**、`test_diag_files` 6650、`test_webview_policy` 5、`test_build_config` 7，全部通过。
- **分发**：APK（5,470,074 B，`c7e5cca4…1e69c83c`）已传 GitHub `preview` 与 Gitee `preview`，两侧 `beta.json`
  同步更新为 227（Gitee 直链复验 200）。WorkBuddy 下载站因发布账号不匹配未能覆盖，其 `beta.json` 仍停在
  beta.5；装 beta.9 的手机要收到这一版，需把「更多 → 更新渠道」切到 GitHub 或 Gitee 并打开「接收预览版更新」。

## [已构建 · 2.6.0-beta.9] — Gitee 渠道接通（versionCode 226）

> 上一版把 Gitee 放进「更新渠道」，但两个地址写的是 `gitee.com/shynekoo/CampusKit`——那个
> 用户在 Gitee 上根本不存在（用户页 404），仓库自然也没有。选项在、内容没有，读者选它只会得到
> 「暂时查不到新版本」。

### 改动

- **建好真实的公开仓库**：`gitee.com/sun-heyue/CampusKit`，两个固定 tag `release` / `preview`，
  各挂清单与 APK；`UpdateSources` 的两条 Gitee 地址改到该仓库。
- **实测匿名可下**（不带任何令牌）：`beta.json` 200 / 1169 B → 226；APK 200 / 5,465,978 B，
  SHA-256 与清单一致；速度约 **0.83–0.95 MB/s**（同网络 GitHub 数十 KB/s、WorkBuddy 约 4 MB/s）。
  跳转链 `gitee.com/…/download/…` → `attach_files/…` → `foruda.gitee.com/attach_file/…?token&ts`
  与 `UpdateUrls` 的单测夹具一致。
- **踩到并记录的坑**：建仓 API 即使传 `private: false`，仓库仍落成 **私有**，此时匿名读附件回
  403（`{"status":403,"message":"403 Forbidden"}`），App 只能显示「该渠道没有内容」。要用
  `PATCH /repos/{owner}/{repo}` 带 `name` 重启可见性。这条写进 `UpdateSources` 的类注释与
  `build-and-release.md`。
- CoreTests 增加两条：Gitee 地址的 owner 必须是真实存在的仓库（防再次写成不存在的账号），
  重定向夹具改用 `UpdateSources.GITEE_RELEASE`，不再手抄一份会过期的字符串。

### 验证状态

- `test_core` **1520**（+2）、`test_ui_contract` 339、`test_diag_privacy` 679、
  `test_diag_files` 6650、`test_webview_policy` 5、`test_build_config` 7，全部通过。
- 产物：`delivery/科信小助手-2.6.0-beta.9.apk`，5,465,978 B，SHA-256
  `11cbeab8…0baf5b73`，versionName `2.6.0-beta.9`、versionCode 226；dex 内同时含
  `sun-heyue`（Gitee）与 `shynekoo`（GitHub），说明两条渠道各自指向自己的仓库。
- **未验证**：App 内实际点「检查更新」经 Gitee 下载安装——需要真机，见
  `docs/real-device-acceptance.md` 的 B0c。

## [已构建 · 2.6.0-beta.8] — 评价页：先看结构，再谈拒绝（versionCode 225）

> 2.6.0-beta.7 的真机日志里，三种请求形态（POST 仅请求体 / POST 请求体+查询串 / GET 查询串）
> 一律回 200，又一律被判成「被拒」。可第一种与学校自己页面发出的请求逐字相同，它不可能被学校
> 拒。真正的问题在判断顺序：先拿「参数异常」这四个字去认页面，再谈页面里有没有表——那句话也
> 可能出现在一张正常表单的脚本或提示模板里，于是好页面被自己挡在门外，三道都堵死。
>
> 附带一个自找的坑：`sheet.shape` 记的值是中文（「请求体」），诊断白名单只放行
> `[A-Za-z0-9_.:+-]`，于是三条全被写成 `form=OTHER`——日志看着"什么都没有"，其实信息在路上被
> 自己擦掉了。

### 改动

- **判定顺序反转**：`EduClient.evaluationForm` 先用 `looksLikeSheet` 认结构
  （`div.xspj-body` / `panel-pjdx` / `tr-xspj` / `input-xspj`，四个选择器即解析器用的那四个，
  线上 `xspj_display.js` 里分别出现 9 / 28 / 36 / 12 次），命中就把整段 HTML 交给
  `EduEvaluationForm.parse`；只有在没有任何表结构时才去读拒绝语。判断权归解析器，不归一个
  子串。
- **第一种请求逐字对齐浏览器**：新增 `EVAL_DISPLAY`（`/xspjgl/xspj_cxXspjDisplay.html`，无查询
  串），POST 七项参数只在请求体；去掉此前自加的 `gnmkdm` 与 `nd` 时间戳。后两种（带
  `gnmkdm` + 查询串、GET 查询串）作为部署差异的退路保留。
- **日志记回包大小**：`EVAL / sheet.shape` 增加 `bytes` 字段，形态值改成 ASCII
  （`body` / `body+query` / `query`），不再被脱敏改写成 `OTHER`。
- **失败提示自带证据**：无表时提示里直接写「学校回的是 X KB 的页面」，被拒时同样附上大小——
  一张截图就能区分「2 KB 的错误页」与「30 KB 的正常表」，不必再去导日志。

### 验证状态

- `test_core` **1518**、`test_build_config` 7、`test_diag_privacy` 679、`test_diag_files` 6650、
  `test_webview_policy` 5、`test_ui_contract` **339**（较上一版 +3：结构优先、请求逐字对齐、
  回包字节数入日志），全部通过。
- 产物：`delivery/科信小助手-2.6.0-beta.8.apk`，5,465,978 B，SHA-256
  `f4569dc6…8e325796`，versionName `2.6.0-beta.8`、versionCode 225；dex 内可见
  `looksLikeSheet` / `EVAL_DISPLAY` / `xspj-body` / `panel-pjdx`。
- **未验证**：真实账号下这张表到底是不是正常表单——本次改动只是不再替它下结论。真机若仍失败，
  提示语里的 KB 数会直接说明回的是什么。

## [已构建 · 2.6.0-beta.7] — 评教请求改试三种载体 + 更新渠道增加 Gitee（versionCode 224）

> 两条真机反馈在同一版里处理：① 切到 GitHub 渠道后能检查到更新，但 APK 下载慢到不可用；
> ② 2.6.0-beta.6 装了之后评价页仍显示「学校说这次请求的参数不对」——说明拒绝页分型生效了，
> 但「双写」并没有解决学校的拒绝。

### 评教：不再猜学校读哪一处

- **现象**：beta.6 已把七个 id 同时放进查询串和请求体，学校仍回「课程号ID,教学班ID,教职工ID参数异常」。
- **判断**：三种可能都成立且互斥——学校的过滤器只读请求体（网页是
  `$(panel_content).load(url, data)`，即 POST + 仅请求体）、只读查询串、或把空值参数当成
  「给了但是错的」。继续按一种形态赌下去没有意义。
- **修法**：`evaluationForm()` 改成按「最接近网页」到「最不接近」依次试三种载体——
  `POST 仅请求体` → `POST 请求体+查询串` → `GET 仅查询串`，任一次不被判拒绝就采用并返回；
  三种都被拒时，把学校那句话原样抛给读者，不再自造原因。
- **空值不发**：新增 `putIfPresent`，`xsdm / tjzt / pjmbmcb_id / sfcjlrjs` 为空时根本不进参数表。
  发一个空串等于告诉学校「这个参数给了，但是错的」。
- **可观测**：真正生效的那一种形态记进诊断（`EVAL / sheet.shape`，字段 `form`，新增词条
  「评价表单换了一种请求形态」），下次再失败能一眼看出卡在哪一步。
- **未验证**：哪一种是学校接受的，只有真机能回答；这一版就是把三种都试一遍。

### 更新渠道增加 Gitee

- `UpdateSources` 从两个渠道扩到三个：`HOSTS = {"WorkBuddy", "GitHub", "Gitee"}`，
  并列一张 `KEYS = {"workbuddy", "github", "gitee"}`；新增 `keyOf(int)` / `indexOf(String)` /
  `normalize(String)`，让页面和落盘都不再自己写渠道名。未知值仍一律回落 WorkBuddy。
- `MoreActivity.sourceSetting`：单选项从两项变三项，名称、当前值、Toast 全部取自 `UpdateSources`，
  页面里不再出现硬编码的 `"github"` / `"GitHub"` 分支。
- `UpdateCheck.setSource` / `source`：落盘与读取都过 `UpdateSources.normalize`，存进去的值一定是
  三个键之一，读出来的未知值一定回到默认。
- Gitee 侧的两个固定清单：`releases/download/release/update.json`（正式）与
  `releases/download/preview/beta.json`（预览）。Gitee 没有 `latest/download` 路由，所以正式
  清单也挂在固定 tag 上，每次发版替换 tag 内同名资产。
- 常量里的仓库暂定与 GitHub 同名：`gitee.com/shynekoo/CampusKit`。Gitee 用户名若不同，改这一处
  常量即可，两个地址都由它拼出。

### 为什么是 Gitee 而不是别的

实测（同一网络、同一时段）：WorkBuddy 托管约 **4 MB/s**，Gitee 附件约 **0.7 MB/s**，GitHub 的
release CDN 在境内常在数十 KB/s 量级甚至超时。Gitee 的价值是「境内稳定可达」，不是最快。
它的发布附件对**公开仓库匿名可下**：先 302 到 `gitee.com/<owner>/<repo>/attach_files/…`，再 302
到 `foruda.gitee.com/attach_file/…?token=…&ts=…`，最终 200，不需要登录。私有仓库不行。

### 验证状态

- `test_core` **1518**、`test_build_config` 7、`test_diag_privacy` **681**、`test_diag_files` 6650、
  `test_webview_policy` 5、`test_ui_contract` **336**，全部通过。
- 新增断言：三个渠道各给出两条 HTTPS 清单；Gitee 的两跳 302（含 `token` / `ts`）在
  `UpdateUrls.next` 下都能通过，且签名参数不被剥掉；评教三种载体的顺序、空值不发、
  生效形态记诊断各自有锚点。
- 产物：`delivery/科信小助手-2.6.0-beta.7.apk`（5,465,978 B，SHA-256
  `9001d4fb…f5ad2b8b`，224 / 2.6.0-beta.7，用水版，v2/v3 验签通过；dex 内可见 `sheet.shape` /
  `putIfPresent` / `gitee`，beta.6 里没有）。已上传 GitHub `preview`。
- **未验证**：三种载体里哪一种会被学校接受（真机 B0c / E4）；Gitee 仓库尚未创建、资产尚未上传，
  所以 App 内实际拉取一次都没跑过。

## [未发布 · 2.6.0-beta.6] — 学生评价：问卷请求的参数要双写（versionCode 223）

> 2.6.0-beta.5 的真机反馈：卡片能展开了，点「开始评价」进到原生答题页，但学校回
> 「课程号ID,教学班ID,教职工ID参数异常！」——三个 id 一定有值，因为列表就是靠它们渲染的。

### 根因

`xspj_cxXspjDisplay.html` 这组参数只放进了请求体。学校的页面用 `$(panel_content).load(url, data)`
（jQuery 把 data 编码进请求体），但它自己的过滤器**还从查询串取一份**；只给请求体时服务端读到的是
空参数表，于是回那句话。这不是新问题：2.5.10 的真机就是同一句报错，2.5.11 把同一组参数同时拼进
查询串后解决；原生重写时这一步漏掉了。

### 修法

- `EduClient.evaluationForm()`：七个 id（`jxb_id / kch_id / xsdm / jgh_id / tjzt / pjmbmcb_id /
  sfcjlrjs`）**同时**进查询串与请求体，查询串用与请求体同一个编码器（`SmsLoginProtocol.form`）。
- 新增 `refusal(html)`：学校把拒绝写成一整页 200 响应里的一句话。现在分别识别「参数异常」「模块维护」
  「已放弃」，给出对应的中文提示，不再一律显示「学校没有下发评价项」——那会让人以为是学校没题。
- 三个 id 先判空：真的缺 id 时直接说明缺哪个，读者才知道该重读列表还是去网页完成。
- `test_ui_contract.py` 增加三条守卫（双写、`refusal` 存在、三个 id 先判空），锚点 323 → **326**。

### 验证状态

- `test_core` 1505、`test_build_config` 7、`test_diag_privacy` 672、`test_diag_files` 6650、
  `test_webview_policy` 5、`test_ui_contract` **326**，全部通过；`javac --release 8` 通过。
- **仍需真机确认**：E4（题目是否出现）。提交/暂存仍未实测。

---

## [未发布 · 更新渠道] — WorkBuddy 默认 + GitHub 可选

- 「更多」页在「接收预览版更新」上方新增独立一行「更新渠道」：WorkBuddy（默认）/ GitHub，单选弹窗、确定才落盘，切换作废在飞的检查。与预览开关互不干扰。
- 新增 core `UpdateSources`：四个清单地址集中管理，JVM 测试与应用共用；未保存/未知值回落 WorkBuddy。
- `UpdateUrls` 重定向规则细化：保留 Location 自带的签名 query（GitHub CDN 必需），仅剥掉与上一跳同名的参数（防 sharecode 跨 host 泄漏）。
- GitHub Release 建立两条稳定清单地址：正式 `releases/latest/download/update.json`，预览 `releases/download/preview/beta.json`（固定 tag，因 latest 排除预发布版本）；v2.5.9 附正式清单，preview 附 beta.3 APK 与预览清单。
- 测试：core 1491 → **1505**；UI 守卫 314 → **323**；build_config 7 / diag_privacy 672 / diag_files 6650 / webview_policy 5 全过。
- 真机未验证；GitHub 直链下载在本机受代理 502 限制，仅通过 GitHub API 元数据核对资产名/大小，待网络可用时复核。

## [未发布 · 2.6.0-beta.4] — 学生评价：卡片能真正展开了（versionCode 221）

> 2.6.0-beta.3 的真机反馈：列表正常，但每张课程卡片「一点开就缩回去」，状态栏与箭头都显示已展开，
> 内容却是一片空白。这是折叠动画里的一个数值错误，不是学校接口的问题。

### 根因

`EvaluationActivity.height()` 在写卡片正文高度时做了 `Math.max(0, value)`。这个兜底对动画每帧的像素值
无害，但折叠结束时 `settle()` 传进来的是 `WRAP_CONTENT`，它的值是 **-2**；钳到 0 后正文高度变成 0，
于是「展开动画播完 → 立刻塌成 0 高度」。箭头与 `card.open` 已经置为展开，所以视觉上就是
「点一下展开、马上缩回，页面还是原样」。同一页面里 `Math.max` 是本轮唯一一处会碰到负值的高度写入。

### 修法

- `height()` 改为原样写回（负值即 `WRAP_CONTENT` / `MATCH_PARENT` 的语义），像素值不再需要钳制；
  `settle()` 的 0 与 `WRAP_CONTENT` 两条终态因此都成立。
- `test_ui_contract.py` 新增一条防回退守卫：`EvaluationActivity` 的折叠高度写入不得再出现
  `Math.max(0, …)` 形式的钳制（314 锚点）。
- 其余逻辑未动：列表、分页、筛选、答题页、暂存/提交、诊断事件都与 beta.3 相同。

### 验证状态

- `test_core` 1491、`test_build_config` 7、`test_diag_privacy` 672、`test_diag_files` 6650、
  `test_webview_policy` 5、`test_ui_contract` **314**，全部通过；`javac --release 8` 通过。
- **仍需真机确认**：展开/收起、连点两次不抢动画（`docs/real-device-acceptance.md` E2）。

---

## [未发布 · 2.6.0-beta.3] — 学生评价改成原生列表 + 原生答题页（versionCode 220）

> 上一版（2.6.0-beta.2）把学校的模块页装进菜单壳，仍然只是「把网页搬进应用」：定位、渲染、提交全都
> 依赖学校页面的脚本按预期跑起来，任何一环断了用户只看到一片空白。这一版不再打开网页。

### 改了什么

- **列表**：`EvaluationActivity` 沿用学业情况的卡片与动效——顶部三格 未评 / 已评 / 暂存，四个筛选
  chip（全部/未评/暂存/已评），每门课一张卡片，卡头显示课程名、状态与 ▸，第二行常显教师 · 教学班 · 学时类型，
  点卡头用 `MotionSpec.LIST_TOGGLE_DURATION` 展开出开课单位/分数与「开始评价」按钮。
- **分页**：`EduClient.evaluations()` 按 jqGrid 的 `queryModel.currentPage` 逐页取全（每页 100，页数取
  `totalPage`/`total`，缺失时用 `totalResult ÷ 每页数` 估算），跨页按 `jxb_id` 去重。只读第一页会漏课程。
- **答题页**：`EvaluationFormActivity` 重写为原生页面，不再有 WebView。`EduEvaluationForm.parse()` 按学校
  `xspj_display.js` 的选择器把问卷片段读成「评价模板 → 评价项 → 选项」：单选渲染成选项行、多选可多选、
  十级制的 `select[name=pjf]` 同样渲染成选项行、主观题渲染成输入框、`textarea[name=py]` 渲染成评语框；
  必填项（`data-sfbt=1`）标红星。识别不了的题型明确写「这一项要在学校页面上填」并**阻止提交**。
- **暂存 / 提交**：`EduEvaluationForm.body()` 生成与学校 `buildRequestMap()` 同构的请求体
  （`modelList[i].xspjList[j].childXspjList[k]`），未作答的题目不发字段；评语按学校做法双重编码。
  提交前校验必填项 + 二次确认（写明不可撤销），成功后自动返回列表并重读学校状态。
- **如实处理失败**：未开放（片段里 0 个评价项）、会话过期、网络失败、学校回「失败」各有中文提示；
  学校只回一句话，因此 `outcome()` 只按这句话判断成败，判不出来的原样显示，不假装成功。
- **安全边界**：`checkPath()` 按名字放行五个 `/xspjgl/` 端点（列表、统计、问卷、暂存、提交）；
  写端点只接受读者自己选出来的请求体，不预填、不代选、不批量代评；评语只在内存。
- 诊断新增 `EVAL` 事件：`sheet.start / sheet.ready / sheet.empty / sheet.failed / sheet.unsupported /
  write.sent / write.saved / write.failed`，只记阶段与题数，不记题目文本、评语、Cookie、Token。

### 验证状态

- `test_core` **1491**（新增 `EduEvaluationForm` 与分页 73 条断言）、`test_build_config` 7、
  `test_diag_privacy` **672**、`test_diag_files` 6650、`test_webview_policy` **5**（学生评价不再是 WebView 页面）、
  `test_ui_contract` **313**，全部通过；`javac --release 8` 全量编译通过。
- **未验证**：真实账号下的问卷渲染与提交（无可用账号）。提交是一次性的，必须在真实评教窗口内按
  `docs/real-device-acceptance.md` E4c/E4d 实测一次才可置信。

---

## [未发布 · 2.6.0-beta.2] — 学生评价：模块页必须在菜单壳里打开（已被 2.6.0-beta.3 取代）

> 2.6.0-beta.1 真机仍然失败：能打开这门课的页面，但状态栏一直停在「页面已打开，正在定位这门课…」，
> 面板里只有图例（`状态备注`）和「评价内容 / 0% Complete」，**一个题目都没有，连表格都没有**。
> 用户同时确认：**用「教务系统」入口从学校菜单进去，同一门课能正常评** —— 这句是分水岭。

### 根因（有脚本级证据）

学校下发的 `xspj_cxXspjIndex.html` 不是能独立打开的页面，而是**被菜单壳 `index_initMenu.html` 装进来的片段页**。
它自己的脚本裸用壳文档的全局，没有一处 `window.` 或 `parent.` 前缀：

| 证据 | 代码 |
|---|---|
| 表格数据地址 | `url: _path + '/xspjgl/xspj_cxXspjIndex.html?doType=query'` |
| 建表格 | `$.extend(true,{},BaseJqGrid,{…})` 与 `$("#tempGrid").loadJqGrid(TempGrid)` |
| 列名与文案 | `label:$.i18n.jwglxt["rlzt"]`、`$.i18n.get("wpjnr")` |
| 片段地址 | `$(panel_content).load(_path + "/xspjgl/xspj_cxXspjDisplay.html",{…})` |
| 提交逻辑 | 片段脚本里 `$("#btn_xspj_bc")` / `$("#btn_xspj_tj")`，同样无 `parent.` 前缀 |

`_path`、`BaseJqGrid`、`loadJqGrid`、`$.i18n`、`viewConfig`、`refershGrid` 只在壳文档里存在。把该 url 当**顶层文档** `loadUrl` 时，
`$(function(){…})` 在 `BaseJqGrid` 处就抛 ReferenceError → 整段中断 → 表格从未创建（所以连表头都没有）、
`onSelectRow` 从未绑定 → `#panel_content` 停在页面自带骨架。上一版补 `gnmkdm` 的 `jQuery.fn.load` 包装也因此从未被调用到。

### 改法

- **先加载 `EduClient.MENU_PAGE`（`/xtgl/index_initMenu.html`，Referer 为站点根）**，再 `jQuery(host).load('/xspjgl/xspj_cxXspjIndex.html?gnmkdm=N401605')`
  把模块装进全屏宿主 `#ckEvalHost` —— 与学校菜单装模块的方式一致，全局因此可用。
- **模块号改由 `$.ajaxPrefilter` 补齐**：壳文档的 Referer 不带 `gnmkdm`，模块自己的 `/xspjgl/` 请求（列表 query、`xspj_cxXspjDisplay.html` 片段、`xspj_cxXspjqk.html` 统计）都会缺模块号拿到 500；prefilter 只给尚未带模块号的 `/xspjgl/` url 追加 `N401605`。
- **按表格数据定位，不读行文本**：`jqGrid('getGridParam','data')` 按 `jxb_id` 精确匹配 → `setSelection(rowid, true)` 触发学校 `onSelectRow` → DOM 上补一次 `trigger('click')`。`jxb_id` 是隐藏列，jqGrid 不写进 `<td>`，读文本永远找不到。
- **成功判据改为 `.panel-pjdx` 节点数**：面板自带的骨架本来就有字，按字数判会把"没装进来"读成"已打开"。
- **失败说清是哪一种**：30 秒预算 + 各自文案 —— 课程列表没出现 / 列表里没这门课 / 学校回 0 门 / 页脚本出错 / 面板 0 项 / 学校自己拒绝（`alert-error`）。
- **提交后的「最佳教师评选」跳转**改为在同一宿主内打开，避免另一个模块页当顶层文档打开时白页。
- 诊断新增 `EVAL` 模块与 `shell.mount` / `form.ready` / `form.empty` / `form.failed` 四个事件码（只记阶段与计数，不记题目、Cookie、Token）。

### 现状

- `test_core` 1418 / `test_build_config` 7 / `test_diag_privacy` **666** / `test_diag_files` 6650 /
  `test_webview_policy` 6 / `test_ui_contract` **297**，六个入口全部通过；`javac --release 8` 全量编译通过。
- 新增守卫：禁止把模块 url 当顶层文档 `loadUrl`、禁止用面板文本长度判成功、必须有 `ajaxPrefilter` / `MENU_PAGE` / `getGridParam','data'` / `.panel-pjdx` / `BUDGET_MS`。
- 产物：`delivery/科信小助手-2.6.0-beta.2.apk`，5 457 786 B，SHA-256 `274e7e30…154d27a8`，versionCode 218 / versionName 2.6.0-beta.2，
  预览渠道（`--beta`），用水版，v2/v3 验签通过；dex 反查 `gnmkdm=N401605` 3 处。
- `delivery/update/beta.json` 已改为 2.6.0-beta.2 / 218，APK 已放进 `delivery/update/`；**线上尚未部署**（发布被 `SITES_ACCOUNT_MISMATCH` 拦住，需切回原发布账号），
  当前线上仍是 2.6.0-beta.1 / 217，`update.json` 未动（2.5.9 / 210）。
- **仍未真机验证**：真机需重新走一遍 E1–E4e。

## [未发布 · 2.6.0-beta.1] — 学生评价：让学校的片段请求带上 gnmkdm（versionCode 217）

> 2.5.13 之后真机出现新现象：状态栏停在「正在定位这门课…」，面板里只有列表页自己的占位（`状态备注` / `评价内容` / `0% Complete`），
> 一个评价项都没有 —— 也就是**片段根本没被装进来**，而不是片段里没题目。

### 根因

- 列表页脚本打开表单是 `$(#panel_content).load("/xspjgl/xspj_cxXspjDisplay.html", {…})`，**不带 `gnmkdm`**；
  而该端点没有模块号时服务端返回 500（手测：带 `gnmkdm=N401605` 同一批 id 返回含 13 个评价项的表单，不带则 500）。
  桌面能从菜单里正常评，是因为请求的 Referer 是带 `gnmkdm` 的列表页 URL，模块过滤器从那里取到模块号。
- 2.5.13 把 Referer 强行改成了菜单框架 `/xtgl/index_initMenu.html`（不含 `gnmkdm`），恰好把这条线索掐断了。

### 改法

- **Referer 改为页面自身 url**（`EduClient.EVAL_PAGE`，含 `gnmkdm=N401605`），与浏览器里真实请求一致。
- **在页面内给那一个请求补 `gnmkdm`**：包装 `jQuery.fn.load`，只对 `xspj_cxXspjDisplay.html` 且尚未带 `gnmkdm` 的 url 追加模块号，
  其余请求原样放行。这是让「页面的请求」变成「端点认的请求」的最小改动。
- **按 DOM 找行、按手指的方式打开**：`#tempGrid tr.jqgrow` 的文本里含隐藏列的教学班 id，命中后 `trigger('click')`，
  再 `jqGrid('setSelection', id, true)` 强制触发一次 `onSelectRow`（页面进来时会自己选中第一行，重复点同一行否则不会重载）。
- **失败时说清是哪一种**：状态栏改为报「共 N 个评价项；评完点「提交」」，或在 0 项/空面板时分别给出不同说明，不再只停在「正在定位…」。

### 现状

- `test_ui_contract` **291 锚点**（新增：Referer 必须是页面自身 url、必须识别 `xspj_cxXspjDisplay.html`、必须补 `gnmkdm`、必须按 DOM 找行并按点击打开）。
- 产物：`delivery/科信小助手-2.6.0-beta.1.apk`，5 453 690 B，SHA-256 `27fe5fc9…ef09976`，versionCode 217 / versionName 2.6.0-beta.1，
  预览渠道（`--beta`），用水版，v2/v3 验签通过；dex 反查含 `gnmkdm=N401605` 3 处、`jqgrow` 1 处，`source_sha256` 与工作区一致。
- `delivery/update/beta.json` 同步到 2.6.0-beta.1 / 217（**线上仍未部署**，App 会安全回退到正式渠道）。
- `test_core` 1418 / `test_build_config` 7 / `test_diag_privacy` 651 / `test_diag_files` 6650 /
  `test_webview_policy` 6 / `test_ui_contract` 291，六个入口全部通过。
- 仍在真机上未确认：本轮修复依赖「Referer 带模块号」这一判断，需装机复测。

## [未发布 · 2.5.13-beta.2] — 版本号规范与预览版更新渠道（versionCode 216）

> 第一个按新规则命名的预览包，也是「接收预览版更新」开关的首发（beta.1 / 215 未对外分发，已由 beta.2 取代）。**未发布、下载站未部署。**

### 版本号

- 正式版统一 `X.Y.Z`，预览版 `X.Y.Z-beta.N`（N 从 1 起、不带前导零）；四段 `X.Y.Z.W` 只保留**读取**兼容。
- 新增 `core/ReleaseVersion`：名字规则的唯一实现（`isWellFormed` / `isBeta` / `isRelease` / `isLegacy`）。
  `UpdateManifest` 不再自带正则，改调它，并新增 `beta` 字段（由版本名得出，用于界面文案与渠道挑选）。
- `scripts/build_preview.py` 新增 `check_version_name()`：四段名、`beta.0`、`1.2.3-BETA.1`、`1.2.3.1-beta.1` 一律构建失败；
  `--beta` 与 `X.Y.Z-beta.N` 必须同时出现或同时缺席。
- 删除写死的 `FIELD_VERSION_NAME`：`--field-water` 默认沿用 manifest 的 versionName，只认 `--version-name` / `--version-code`。
- 新增 `check_version_code()`：新包 versionCode 必须高于 `update.json` 与 `beta.json` 的最大值，否则构建失败
  （`--allow-lower-version-code` 仅供内部构建，会打印 WARNING）。
- manifest → `2.5.13-beta.2` / 216（线上正式版 2.5.9 = 210；正式版 2.5.13 必须 ≥ 217，才能覆盖此前的 Beta）。

### 双渠道

- 新增 `core/UpdateChannel.pick()`：只认 `versionCode`；候选必须**严格大于**已安装版本；预览清单仅在开关打开时参与；
  预览清单里若不是预览版名则忽略；两边同号时正式清单优先。
- `UpdateCheck` 读两条清单：任一读不到只等于「该渠道没有内容」，手动检查时如实说明预览清单读不到，自动检查照旧静默。
- 开关存 `update-v1` 的 `beta` 键：**一律默认关闭**（预览包首装也一样，不按安装包类型猜默认值）；切换时 `generation`
  自增，进行中的检查结果作废（日志 `check.stale`），不会出现旧渠道的结果覆盖新设置。
- 打开前必过一次确认框：「预览版供开发者测试使用，可能不稳定，不建议开启。」。只有「仍然开启」才落盘；
  「保持关闭」/ 点弹窗外 / 返回键都拨回关且不写偏好。关闭方向不询问。
- 下载、逐跳 HTTPS、字节数、SHA-256、包名、versionCode、签名证书、系统安装器全部复用原链路，**没有按渠道分支**。

### 界面

- 「更多」页在「更新日志」下方加一行开关：标题「接收预览版更新」，副标题「提前体验新功能，可能存在不稳定情况」，
  图标为新增的 `beaker`；整行可点、深色模式与无障碍文案齐备，页面其余结构未动。
- 打开时的确认框用 `Theme.dialog`（深色模式跟随），标题「开启预览版更新？」，按钮「仍然开启」/「保持关闭」。

### 清单与产物

- 新增 `delivery/update/beta.json`：`2.5.13-beta.2` / 216 / 5453690 bytes / sha256 `4fdbb951…78d08687`，
  与 `delivery/科信小助手-2.5.13-beta.2.apk`、`delivery/update/campuskit-2.5.13-beta.2.apk` 一致。
- **未部署**：线上 `beta.json` 仍 404，开关打开后读到的是「该渠道没有内容」，正式版更新不受影响。
- `update.json` 与下载站正式版信息**未改动**。

### 测试

- core 断言 1369 → 1418；UI 契约 259 → 289；诊断隐私 644 → 651（新增 `beta` / `channel` 字段白名单与 `check.stale` 文案）。
- 六个测试入口全过；`--beta --field-water --version-code 216` 实跑出包，aapt2 核对 `versionCode=216` / `versionName=2.5.13-beta.2`，
  v2 验签通过且证书与 2.5.9 相同；dex 内可查到确认框三段文案。
- **未做**：真机上的开关默认关、确认框取消路径、持久化、降级保护、覆盖安装 Beta → 正式版（见 `docs/real-device-acceptance.md` B5–B8）。

## [未发布 · 2.5.13] — 学生评价：改为在学校列表页里选行打开（versionCode 214）

> 2.5.12 之后真机上红字变成「系统升级维护中」。这一版不再自己拼请求，换成「打开学校的页，让学校自己开表单」。

### 根因（从学校静态脚本读出来的，不再是猜测）

- **表单是片段，不是页面**：`xspj_cxXspjDisplay.html` 返回的是插进面板里的片段；它自己的脚本
  `xspj_display.js` 绑定的 `#btn_xspj_bc` / `#btn_xspj_tj`（暂存 / 提交）**在列表页的 HTML 里**，片段里根本没有这两个按钮。
  所以之前无论请求拼得多对，独立打开的「表单」都是一堆单选按钮、没有可用的提交入口。
- 学校打开它的方式是 `xspj_index.js` 里的 `onSelectRow`：`$(#panel_content).load("/xspjgl/xspj_cxXspjDisplay.html", {…}, cb, "html")`
  —— 在**已经处于学校域名下的页面里**用 jQuery POST 取片段，顺带由同一段代码填教师名、解禁按钮。
- 2.5.12 的第一形态丢掉了 `gnmkdm`，学校直接 500「系统升级维护中」；而当时的检测只认「参数异常 / 参数错误」，
  所以四种形态根本没往下走。这两件事凑在一起，才让前几轮一直在错的方向上打转。

### 改法

- `EvaluationFormActivity` 改为加载**学校列表页** `xspj_cxXspjIndex.html?gnmkdm=N401605&doType=details`，
  并带上指向菜单框架的 `Referer`（学校的内容页正常就是这样被打开的）。
- 等 jqGrid 有数据后，用 `getRowData(ids[i]).jxb_id` 找到这门课那一行，调 `setSelection(ids[i])`
  —— 这正是学校自己的入口，`onSelectRow` 会把片段装进 `#panel_content`、填教师名、绑定提交按钮。
- **面板校验**：页面刚进来时会自己选中第一行，此时再选别的行会被 `inLoading` 挡掉。所以 1.5 秒后量一次
  `#panel_content`（文本长度 + 控件数），没内容就重新选，最多 4 次；仍空则明说「请在上面列表里自己点这一行」。
- 行状态 `fqpjzt=1`（已放弃评价）时直接说明，不再让页面摆一个红字。
- 删掉了 `evaluationForm(item)` / `evaluationFormUrl()` / `EVAL_FORM` / `EVAL_FORM_BARE`、一次性提交页与四种形态退让
  —— 它们都属于「自己拼这个请求」那条错路，留着只会误导下一个人。

### 现状

- `test_ui_contract` **259 锚点**：新增「必须打开列表页」「必须带菜单框架 Referer」「必须用 setSelection 定位」
  「必须观测 panel_content」「禁止 postUrl」「禁止自己拼表单请求」。
- 产物：`delivery/科信小助手-2.5.13.apk`，5 453 690 B，SHA-256 `68d18703…4264f858`，versionCode 214 / versionName 2.5.13，
  用水版，水控类命中 1/1，v2/v3 验签通过；dex 内已含 `panel_content` / `setSelection`，旧的一次性提交页字符串已不存在。
- `test_core` 1369 / `test_build_config` 7 / `test_diag_privacy` 644 / `test_diag_files` 6650 /
  `test_webview_policy` 6 / `test_ui_contract` 259，六个入口全部通过；`javac --release 8` 全量编译通过。
- 仍未在真机上确认表单渲染与提交：本轮没有可复测的教务账号（用户要求用后即删）。

## [未发布 · 2.5.12] — 学生评价：改为浏览器表单提交（versionCode 213）

> 2.5.11 把 id 同时放进查询串后，真机仍然是同一行红字。这一版换了请求方式本身，并把「到底发出去了什么」显示出来。

### 修复

- **请求不再是 `WebView.postUrl()`**：读到学校的 `xspj_index.js`，它打开表单用的是
  `$(panel).load("/xspjgl/xspj_cxXspjDisplay.html", {jxb_id, kch_id, xsdm, jgh_id, tjzt, pjmbmcb_id, sfcjlrjs})`
  —— jQuery 的 urlencoded POST，**不带 `gnmkdm`**。`postUrl` 不是表单提交，学校读到的是空参数表。
  现在先用一个一次性页面把参数当隐藏域，由浏览器自己 `submit()`，编码与 MIME 由平台保证。
- **四种请求形态依次退让**：①学校原样（无 `gnmkdm`，id 只在 body）→ ②带 `gnmkdm` 且 query + body 都有 id →
  ③同上的 GET → ④无 `gnmkdm` 的 GET，id 在 query。每次只在上一形态被判「参数异常」后才换下一种。
- **拒绝时说清发出了什么**：最后一次仍失败时，状态栏显示每个 id 的字符数与最终落到的路径
  （例如 `jxb_id 26 字 · kch_id 8 字 · jgh_id 0 字，落在 /xspjgl/xspj_cxXspjDisplay.html`）。
  「参数丢了」和「参数本身是空的」从外面看不出区别，这一行能分开。

### 现状

- 学校接口在未登录时一律 302 到登录页，所以「能拿到那行红字」说明会话是通的，问题只在参数。
- 产物：`delivery/科信小助手-2.5.12.apk`，5 453 690 B，SHA-256 `df3390ac…365e0d42d6`，versionCode 213 / versionName 2.5.12，
  v2/v3 验签通过，dex 水控类命中 1/1（`--field-water` 用水版），`source_sha256` 与工作区一致。
- `test_core` 1369 / `test_build_config` 7 / `test_diag_privacy` 644 / `test_diag_files` 6650 /
  `test_webview_policy` 6 / `test_ui_contract` 260，六个入口全部通过。
- 仍**未**在真机上确认表单能渲染与提交：本轮没有可复测的教务账号（上一轮按用户要求删除）。

## [未发布 · 2.5.11] — 学生评价：修复评价表单打不开（versionCode 212）

> 2.5.10 真机上点开某一项，学校页面只回一行红字「课程号ID,教学班ID,教职工ID参数异常!」。本版修掉这个问题；
> 2.5.10（211）未公开发布，直接被本版取代。

### 修复

- **评价表单参数丢失**：`EvaluationFormActivity` 原先只用 `WebView.postUrl()` 把 `jxb_id` / `kch_id` / `jgh_id`
  等放进请求体。WebView 的 POST 不是浏览器表单提交，学校侧 `getParameter` 拿到的是空参数表，于是回参数异常页。
  现在同一串参数经 `EduClient.evaluationFormUrl()` **同时拼进查询串**，请求体照旧保留——两条通道任一条被丢都能读全。
  同样的参数用普通 HTTP 客户端 POST 是能拿到表单的（2026-11-20 实测），所以问题在传输，不在字段。
- **错误页不再被当成可用表单**：页面加载后检查学校自己的「参数异常 / 参数错误」文案；命中则自动改用 GET 重开一次，
  仍失败就明说可能是教务系统改版，并指路「教务系统」网页登录，不再在红字上方显示「评完点提交」。

### 测试

- `test_ui_contract` 增至 **255 锚点**（新增「参数必须同时进查询串」「学校参数错误页必须被识别」两条守卫）；
  其余入口计数不变，全部通过。
- 产物：`delivery/科信小助手-2.5.11.apk`，5 449 594 B，SHA-256 `ade5d33f…fa5706ba`，versionCode 212 / versionName 2.5.11，
  v2/v3 验签通过，dex 水控类命中 1/1（`--field-water` 用水版）。
- 未验证：真机上表单是否正常渲染、能否提交（需要真实评教窗口）；本版没有可用于复测的教务账号。

---

## [未发布 · 2.5.10] — 学生评价（versionCode 211）

> 教务卡片新增「学生评价」：一份原生列表，说明本学期还有哪些教学班没评；点开某一项进入学校自己的评价表单。

### 新增

- **教务 · 学生评价**：`core/EduEvaluation`（列表与统计 JSON 解析：教学班、课程、教师、学时类型、
  评价状态 `-1 未评 / 0 暂存 / 1 已提交`）+ `android/EvaluationActivity`（原生列表，按状态分组，
  顶部未评数量、教务账号 / 网页登录 / 退出登录、右上角重新读取）+ `android/EvaluationFormActivity`
  （把某一教学班的表单交给学校页面，复用已登录会话的 cookie）。
- `EduClient` 增加 `evaluations()`、`evaluationCounts()`、`evaluationForm()`；只读路径白名单按名字放行
  `xspj_cxXspjIndex.html` 与 `xspj_cxXspjqk.html`，`/xspjgl/` 的 Referer 指向评价首页。
- 教务卡改为第一行四件查询（成绩 / 空教室 / 学业情况 / 学生评价），第二行三件入口与官网。
- 新增 `eval` 星形图标；manifest 注册两个新页面（`exported=false`）。

### 说明（有意为之）

- **评分不在应用里填**：学校的提交是一次性的，必填项、评语规则和模板都由学校下发；应用只读取状态，
  表单用它自己的页面提交。UI 契约测试对此有专门守卫：两个页面都不得出现 `xspj_tjXspj` / `xspj_bcXspj`。
- 从表单返回时自动重新读取，已提交的项目立刻显示为「已提交」。

### 测试

- `test_core` 1369（新增 40 项评价解析断言）/ `test_build_config` 7 / `test_diag_privacy` 644 /
  `test_diag_files` 6650 / `test_webview_policy` 6 / `test_ui_contract` 253，六个入口全部通过。
- 产物：`delivery/科信小助手-2.5.10.apk`，5 449 594 B，SHA-256 `2af8cefa…14330cfe`，versionCode 211 / versionName 2.5.10，
  v2/v3 验签通过，dex 水控类命中 1/1（`--field-water` 用水版）。
- 真实账号实测（2026-11-20）：列表 20 条（全部未评）、统计 `{"wpgs":20,"bcgs":0,"tjgs":0}`、表单片段 200 且含 13 个评价项。
- 未验证：真机上的表单页渲染与提交（需真实评教窗口）、深色主题下的列表观感。


## [2.5.9] - 2026-11-20（versionCode 210，已发布）

> 修复趣智校园扫码无法跳转使用的bug，如果给您带来的不好的体验，非常抱歉。
> 同时修复四段版本号导致检查不到更新的问题。
> 发布公告见 [`delivery/release-2.5.9.md`](delivery/release-2.5.9.md)。

### 修复

- **趣智校园扫码后不跳转支付宝**：2.5.7 发版（commit `2575dd8`）未传 `--field-water`，产出只读构建，
  `BuildFlags.WATER_CONTROL=false`，`ScanActivity.showCode()` 的支付宝跳转分支在编译期被移除；2.5.8 延续。
  本版重建为用水版，扫码后恢复跳转，与 2.5.6 及之前一致。使用码为同期独立功能，与本问题无关。
- dex 水控类命中由 1 / 1 / 0 恢复为 `WaterControl` 4、`WaterActivity` 100、`LifeWaterProtocol` 3。
- manifest 版本真源同步为 210 / 2.5.9；`FIELD_VERSION_NAME` 与发布逻辑未改，构建时显式传版本号。
- **检查不到更新**：`UpdateManifest.VERSION` 正则原只允许 2–3 段，四段版名（2.5.8.1 / 2.5.6.11）被判无效而静默跳过。
  正则放宽到 `{1,3}` 段并补 4 条断言；本版清单用三段版名 2.5.9，旧客户端也能解析并提示升级。

### 测试

- `test_core` 1333 / `test_build_config` 7 / `test_diag_privacy` 634 / `test_diag_files` 6650 /
  `test_webview_policy` 5 / `test_ui_contract` 240 全绿；APK v2 / v3 验签 true。
- 真机扫码跳转由发布者确认可用；真实出水/关水/账单未对生产服务测试。

## [2.5.8] - 2026-11-19（versionCode 208，已发布）

> 自 2.5.7（197）之后第一次正式发版，把 2.5.8.1–2.5.8.9 这一串开发迭代合并成一个版本号。
> 发布公告见 [`delivery/release-2.5.8.md`](delivery/release-2.5.8.md)，
> 线上清单 `delivery/update/update.json` 与本版同步更新，App 内更新日志已登记 2.5.8。

### 本版包含（2.5.7 之后累计，按用户能感知的顺序）

- **界面**：常驻主界面 + 会移动的底栏胶囊；Tab 切换的淡入淡出与位移；切页后回到原来的滚动位置；
  跨周跳转改为整屏滑页（落地帧就是目标周）；课表圆角默认 20dp。
- **学业情况**：每个课组可展开看课程明细（含成绩、学分、修读状态），汇总补上未通过与计划外通过；
  修复「右侧有学分、点开却说没有课程记录」（计划外课组走错端点）与父行误展开。
- **更新日志**：更多 → 更新日志，按版本卡片展示新增 / 优化 / 修复，离线可读。
- **使用公告 / 数据与隐私**：两份文本按实际行为重写（教务密码加密存储、不提供充值、四个网络端点），
  移除从未使用的 `ACCESS_WIFI_STATE`，权限由 5 项降到 4 项。
- **诊断日志**：正式版也带诊断页（默认关）；结构化事件、失败原因分类、崩溃记录、导出与故障报告。
  详见下一节。
- **安全**：2.5.8.1 的整改（见下方对应段落）。

### 运行日志与故障诊断重构

> 目标不是「把日志显示出来」，而是让「课表加载失败」能被定位到登录、网络、解析还是渲染。
> 旧的 `Diag` 是自由拼接的一句话，只能说「登录没成功」；新的是固定格式的结构化事件，能说
> 「登录成功 → 请求超时 2011ms → 加载失败：NETWORK_TIMEOUT」。

### 新增
- **结构化事件**：`core/DiagEvent` 固定行格式（`日期 时间.毫秒 等级 [模块] 事件代码 trace=… thread=… 字段=值`），
  `core/DiagCatalog` 提供事件与原因的中文文案；`Diag.event(level, module, code, key, value…)` 取代自由字符串。
- **字段白名单**（`core/DiagRedact`）：只有白名单键能进文件，未知键在写入时丢弃；没有 `message` / `text` / `body`
  一类自由文本字段。异常只留类型与安全堆栈（`类名.方法名(文件:行号)`，≤12 帧，不含异常消息）。
- **四级日志**：`INFO` / `WARN` / `ERROR` / `CRASH`；`DEBUG` / `VERBOSE` 不进文件。
- **失败原因分类**：`NETWORK_TIMEOUT`、`DNS`、`TLS`、`HTTP_STATUS`、`PARSE`、`SESSION_EXPIRED`、`AUTH_REJECTED`、
  `CAPTCHA`、`VERIFY` 等稳定代码，按异常因果链判断（`SocketTimeoutException` 被 `IOException` 包住也算超时）。
- **模块接入（只读，不改控制流）**：教务登录（读页 / 取公钥 / 提交 / 拿会话四阶段）、课表（候选端点、尝试次数、
  解析、写入、本机存储损坏）、网络（路由、HTTP 状态、耗时、超时）、慧生活（唤起 / 回调 / 服务端码）、支付宝
  （`status` 9000/6001）、趣智（接口拒绝 / 解析）、更新（检查 / 下载 / 大小 / 摘要 / 包身份校验）。
- **存储**：`DiagFiles` 轮转——当前 `diag.log` + `diag.1..3.log`，单文件 256 KB、总量约 1 MB、历史保留 7 天；
  写入走 512 行有界队列 + 单写线程，队列满时记 `DIAG log.lost`；清空同时清队列与全部文件。
- **崩溃捕获**：`App.onCreate` 即安装 `UncaughtExceptionHandler`；崩溃记录同步、有界落盘后把异常原样交回系统
  处理器；重启后日志页仍能读到 `CRASH` 与堆栈。
- **诊断页重做**（`DiagActivity`）：开关、记录状态与大小、等级/模块筛选、可展开的记录（字段 + 堆栈）、
  单条复制、按筛选复制、UTF-8 导出（系统文件选择器，不申请存储权限）、清空二次确认、每次 40 条分页。
- **故障报告**（`DiagReportActivity`）：版本、Android 版本、机型、所选模块、用户描述、最近 ≤200 条脱敏记录、
  最近一次异常；只在本机生成，用户主动导出。
- **正式版可用**：`BuildFlags.DIAG_BASIC` 恒为 `true`，诊断页对所有构建开放，运行期默认关；`--diagnostics`
  只额外编入细节事件（手势逐帧、页面打开、渲染结果）。SDK 原文日志回调**彻底移除**。

### 修复
- **超限遗留文件不再被整体归档**：上次退出时若 `diag.log` 已超过 256 KB（一次超长写入，或旧版本留下的文件），
  轮转只是把它改名为 `diag.1.log`，归档文件本身就超限，四文件「约 1 MB」的上限随之失效。现在启动时与单次写入
  超过上限时都只保留末尾 256 KB 再落盘，单文件上限在任何时刻都成立。
- 新增 `scripts/test_diag_files.py`：用 JDK 直接驱动 `DiagFiles`（不需要真机）验证轮转、1 MB 封顶、行号连续、
  超限裁剪、单次超长写入截尾、清空连历史一起删、7 天清理；上面这条问题就是它先跑出来的。

### 安全
- 旧 `maskCode` / `maskChain` / `maskSdk` 与 `Log.setupLogCallback` 接入全部删除：不再依靠正则擦除已记录的内容，
  而是让敏感值没有入口。`Diag.activity` 调用点若写错键名，值会被静默丢弃，不会落盘。
- 新增 `scripts/test_diag_privacy.py`：检查每个调用点的字段键、模块/代码/原因文案、已移除入口是否复活、
  导出必须 `ACTION_CREATE_DOCUMENT` + UTF-8、清单不得新增存储权限。

### 验证
- `test_core` **1329**（新增 51 项：格式往返、白名单、脱敏语料、堆栈、原因分类、文案）、`test_build_config` **7**、
  `test_diag_privacy` **634**、`test_diag_files`（5 组场景实跑）、`test_webview_policy` **5**、
  `test_ui_contract` **240**（新增 27 个诊断页锚点），全绿。
- 产物：`delivery/科信小助手-2.5.8.apk`，5 420 922 B，SHA-256 `ac40b21dfad29e1c2f6e985bec8762ec5244ba31564b497e87f7001f4410d806`；
  `aapt2 dump badging` 实测 `versionCode=208` / `versionName=2.5.8`，权限 4 项；`apksigner` v2 / v3 均为 true；
  只读包 dex 中 7 条水控路由与 2 个水控类名命中数全为 0。
- 编译期证据：正式构建的 `Page.class` 里 `back.gesture` 出现 **0** 次，`TimetableActivity.class` 里 `render.done`
  出现 **0** 次；用 `DIAGNOSTICS=true` 单独编译同一份源码则为 **2** 次——细节事件确实只在内部包里。
  正式构建的 `Diag.class` 不含 `com.alipay.sdk.interior.Log` 引用（SDK 自身的类仍随 AAR 存在，非本应用调用）。
- **未做**：真机验收 G6–G17（开关、复现、导出、轮转、崩溃重启、长日志滚动、深色/大字号），全部标注为未执行。

---

## [未发布 · 2.5.8.8] — versionCode 206，合并后主线重建

> 只有版本号比 2.5.8.7 变化，源码与合并后主线一致。存在的意义是把少课修复真正打进安装包，并在 dex 层面证明它在里面。

### 说明
- `2.5.8.7`（205）是在主线 `16cec29` 上构建的。当时少课修复只存在于并行分支 `agent2/parallel-dev`，尚未进入主线，所以那个包的 dex 里**不含** `cxJxzxjhxfyqFKcxx` / `planCourses` / `opensCourses` —— 这就是装 2.5.8.7 后第二课堂仍然少课的原因，与修复本身是否有效无关。
- `2.5.8.8`（206）在合并后的主线上重建，dex 反查 `cxJxzxjhxfyqFKcxx` = 1、`cxJxzxjhxfyqKcxx` = 1、`planCourses` = 1、`opensCourses` = 1、`jdkcsx` = 3 处；同一检查在 2.5.8.7 上三项均为 0。

### 验证
- 合并后四级测试实跑：`test_core` **1278**、`test_build_config` **5**、`test_webview_policy` **5**、`test_ui_contract` **213**，全绿；`check_doc_sync` exit 0。
- 产物：`delivery/科信小助手-2.5.8.8.apk`，5 404 538 B，SHA-256 `aadbe23e40aad432771c65ac08832af2db4ffa2e6bd3ac463535384420fd5e80`（以同目录 `.build.json` 为准）；`aapt2 dump badging` 实测 `versionCode=206` / `versionName=2.5.8.8`，权限 4 项，`apksigner` 验签 v2 / v3 均为 true；只读包 dex 中 7 条水控端点与 2 个水控类名命中数全为 0。
- **未做**：真机验收（第二课堂逐课组展开、覆盖安装、圆角观感）。

---

## [未发布 · 2.5.8.7] — 已提交，versionCode 205

> 两处小改：课表圆角的默认值、以及「使用公告」的重复入口。都没有动绘制逻辑、存储格式或任何业务代码。版本号 2.5.8.6/204 → **2.5.8.7/205**，仅按「生成安装包」要求递增，未发版。

### 变更
- **课表圆角默认 8dp → 20dp**（`Theme.DEFAULT_RADIUS`）。滑杆上限 `MAX_RADIUS` 本来就是 20dp，所以新默认值等于满档；`Theme.load()` 只在偏好里**没有** `radius` 时才取默认值，改过圆角的读者升级后仍是自己保存的那个数，不会被重置。`AppearanceActivity` 的 `draftRadius` 初值取自 `Theme.DEFAULT_RADIUS`，滑杆、预览与课表三处同源，没有第二份硬编码。
- **`MoreActivity` 删除「使用公告」条目**：公告已在「数据与隐私」页末挂着「阅读使用公告」，同一份文本不再开两个入口。「更多」现在只剩 数据与隐私 / 更新日志 /（诊断包才有）日志 / GitHub 仓库 / MIT 许可证。

### 验证
- 该轮构建时实测 `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 208 全绿；`check_doc_sync` exit 0。与少课修复合并后复跑为 **1278 / 213**（见下条）。
- `test_ui_contract` 新增反向锚点：`MoreActivity` 的代码行里不得出现 `NoticeActivity`（注释里提不算），防止入口再加回来。
- 构建产物：`delivery/科信小助手-2.5.8.7.apk`，5 404 538 B，SHA-256 `01d4341b8110e4c1b773f78611fc10025a6ea59f7da7cd691a28abe84a519df1`；`aapt2 dump badging` 实测 `versionCode=205` / `versionName=2.5.8.7`，权限 4 项，apksigner 验签 v2 / v3 均为 true。
- **未做**：真机未验圆角观感（0 / 20 两端、320dp 窄屏、深色主题下的格子边缘）。

---

## [未发布 · 学业情况少课修复] — 已合并到主线，未发版

> 在并行 worktree 内开发，未触碰主工作区。只改学业情况课程展示与取课端点选择，不动账号/支付/水控/升级。

### 修复
- **学业情况里"右边有学分、点开却显示这个课组还没有课程记录"**：学校把课组课程分成两个只读端点——计划内 `POST /xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html`、计划外 `POST /xsxy/xsxyqk_cxJxzxjhxfyqFKcxx.html`，并按课组标签上的 `jdkcsx == '1'` 二选一（学校脚本原样如此）。此前 App 一律请求计划内端点，于是通识选修/公共艺术这类计划外课组（人文科学 5 门、自然科学 6 门）拿到 `[]`，界面显示"还没有课程记录"。现在 `EduAcademic.Node.planCourses` 携带该判定，`EduClient.academicCourses()` 按节点选端点，计划外请求只带学校自己发的字段（`fromXh_id`/`xfyqjd_id`/`xh_id`）。
- **可点判定回归"叶子节点"**：学校只给叶子节点挂展开链接（脚本遍历 `.treeview .title a.more`），父行不该展开课程。此前按 `jdkcsx` 决定可点性，导致父行也可点、并渲染出空课程块；现在 `opensCourses(hasChildren)` 只在无子节点时可点。
- 只读路径白名单补上计划外端点；`jdkcsx` 仍从 `<p>` 与 `<div class='title' xfyqjd_id=…>` 两处读取（两种部署位置都已断言）。

### 验证
- 真实账号端到端复验（按新规则逐叶子请求）：24 个叶子节点中，人文科学 **5 门**（学校页显示"共 (5) 门"）、自然科学 **6 门**（"共 (6) 门"）、公共艺术某课组 **2 门**，其余计划内课组条数与修复前一致；`parseCourses()` 读计划外返回无丢课。抓包数据与凭据用后即删，工作区无残留。
- 合并到主线后实跑：`test_core` **1278**（含计划内/计划外分流、特殊节点 `qtkcxfyq`/`cxcyqkxfyq`、无标记页面回退）/ `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **213**（本修复新增 5 个锚点：两端点都在只读白名单、请求必须按节点选端点）全绿。
- 合并前已在 `delivery/科信小助手-2.5.8.6.apk`（5,392,250 B）上验证过 dex 含 `cxJxzxjhxfyqFKcxx`/`planCourses`/`opensCourses`；合并后的包以 2.5.8.8 重新构建为准。**真机未验证**。


## [未发布 · 应用内更新日志] — 已提交 `9a9ccad`，未发版（versionCode 仍为 204）

> 新增「更多 → 更新日志」整页，数据随包内置、离线可读。参考岱宗盒子的版本列表组织方式（`update/AppChangelog.kt` 只放数据，真正渲染在 `update/UpdateDialogs.kt` 的堆叠式 What's New 弹窗与 settings 宿主的 `ChangelogEntry` 列表里），**没有复制其代码、素材或主题**；版式、配色、动效按 UI-1/UI-2/UI-3 的 token 重做。核心 1262 断言、UI 守卫 208 锚点全绿，普通版 APK 已构建并验签。

### 新增
- **`AppChangelog.java`**：更新日志唯一数据源。`Entry(version, code, date, known[], items…)` + `Item(Kind, text)`，`Kind` 分 新增 / 优化 / 修复 / 其他；`released()` 过滤未发布版本，`current(versionName)` 决定谁挂「当前版本」。数据只收**已正式发布**的版本（2.5.7 / 2.5.6 / 2.5.5 / 2.5.0 / 2.3.10），取自 `delivery/release-*.md`、`delivery/update/update.json` 与 `CHANGELOG.md`，日期与版本代码照抄，没有推测。
- **`ChangelogActivity.java`**：`extends Page`，顶部返回 + 标题 + 「当前安装 x（code）」；每个版本一张卡片，标题 `CARD_TITLE` 粗体、日期 `BODY_SMALL` + `MUTED`、当前版本挂 `SOFT` 胶囊；每条变化前是类别图标（`plus` / `torch` / `check` / `info`，颜色只取 `AppUi`）；已知问题用 `AppUi.DANGER`。最新一版与当前安装版默认展开，历史版本点击展开/收起。

### 变更
- **`MoreActivity`**：设置卡片新增「更新日志 · 查看版本更新与历史记录」一行，复用 `entry()` 的图标/箭头/分隔线样式。
- **`MotionSpec`**：新增 `LIST_TOGGLE_DURATION`(220ms) 与 `settle()`（减速曲线），展开动画的时长与曲线只有一个来源。
- **`AndroidManifest.xml`**：注册 `.ChangelogActivity`，`exported=false`、`configChanges` 与其他子页一致；未新增任何权限。

### 交互与竞态
- 展开只动两样东西：内容高度与箭头角度，同一动画驱动；系统关动画（`MotionSpec.shouldAnimate`）时直接落到终态（展开回 `WRAP_CONTENT`）。
- 连续点击：每张卡片自带 `running` 与 `generation`，新点击先 `cancel()` 上一条并从**当前高度**继续，过期回调被 `generation` 拦下，不会两条动画抢同一个 `LayoutParams.height`。
- 开发包（2.5.8.6）匹配不到条目：**不**给任何正式版打「当前版本」，改为顶部说明「此版本尚未正式发布」。
- `Page` / `CardLayer` / 主屏与 Tab 转场 / 课表 / 教务 / 支付 / 水控 / `UpdateCheck` 均未改动。

### 自动化校验（`scripts/test_ui_contract.py`，187 → 208 锚点）
版本号唯一、版本代码严格递减、日期合法、**最新条目必须等于 `delivery/update/update.json` 的 version/versionCode**（发版忘写日志即红）、任何版本代码大于线上版本即红（开发记录不得混入）、每条版本必须有 `delivery/release-*.md` 或 `CHANGELOG.md` 记录、页面只取 `released()`、展开动画取 `MotionSpec`、关动画走终态、连点 `cancel` 与 `generation` 守卫、入口存在、`exported=false` 注册、两文件不含 WebView / 网络调用。另有一条反向守卫：`MoreActivity` 的代码行里**不得再出现** `NoticeActivity`，公告入口只留在「数据与隐私」页末。

### 未做（第二阶段）
- 升级后「本次更新」弹窗、`lastSeen` 记忆、`NoticeDialog`/`UpdateCheck` 的启动时序协调。数据结构已按 `code` 预留区间取用的可能，但**未实现任何自动弹出**，`UpdateCheck` 的下载/校验/安装流程一行未改。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **208** 全绿。
- APK 已构建并验签（`delivery/科信小助手-2.5.8.6.apk`，5 404 538 B，权限 4 项，v2/v3 通过）；该文件随后续小改重新构建过，**当前校验值以上方最新区块为准**。
- **未做**：真机 L1–L8（展开手感、连点、320dp / 大字体、深色、飞行模式、开发包不误标当前版本）。

---

## [未发布 · 数据与隐私 / 使用公告专项] — 已提交 `6b619ab`，未发版（versionCode 仍为 204）

> 按发布者提供的新版文案重写这两份文本，并逐条对照代码核对。**只改文案与展示，不改安全机制与业务逻辑**；权限表仅按发布者确认移除一个从未使用的 `ACCESS_WIFI_STATE`。文案集中在 `LegalText`，排版集中在 `LegalView`，首次启动弹窗与新增的公告页读同一份文字。核心 1262 断言、UI 守卫 187 锚点全绿，普通版 APK 已构建并验签。

### 新增
- **`LegalText.java`**：两份文本的唯一来源（隐私 8 节、公告 7 节）。隐私联系邮箱填 `shynekoo@outlook.com`（与「意见反馈」公开地址同一个，不另设邮箱），生效日期 `2026年11月19日`；守卫会拦住任何残留的 `【…】` 占位符，并校验两处邮箱一致。
- **`LegalView.java`**：两页共用排版（返回、标题、说明、可滚动正文、每节一张卡片）。字号用 sp，跟随系统字体缩放；颜色只读 `AppUi`，深浅色与自定义主题随动。
- **`NoticeActivity.java`**：使用公告整篇阅读页（离线静态文本）。**唯一入口**是「数据与隐私」页末的「阅读使用公告」卡片（早期曾在「更多」里并排一条，发布者要求去重后已删除）；首次启动仍由 `NoticeDialog` 负责「以后不再提醒」的选择，两者共用 `LegalText.NOTICE`。

### 变更
- **`PrivacyActivity`**：原先手写的「保存在这台手机上 / 不会离开这台手机 / 权限」三张卡片换成完整的隐私说明（8 节）；两个清除按钮及其行为、清除范围未变；末节入口改为「阅读使用公告」，进整篇页面（原先复用弹窗）。
- **`MoreActivity`**：文案改版时曾新增「使用公告」独立条目；发布者指出公告已在「数据与隐私」页末，同一份文本摆两条入口只会让人猜哪个是正的，**该条目已删除**。现在「更多」只剩：数据与隐私 / 更新日志 /（诊断包的日志）/ GitHub 仓库 / MIT 许可证。
- **`NoticeDialog`**：正文改读 `LegalText.NOTICE`；`VERSION` 2 → 3，勾过「不再提醒」的人在文案改版后会再看一次。
- **`AndroidManifest.xml`**：注册 `.NoticeActivity`（`exported="false"`，`configChanges` 与其他子页一致）；**移除 `ACCESS_WIFI_STATE`**（见下）。未新增任何权限。

### 文案与代码核对（据此改了 5 处声明）
1. **教务密码**：原文「不作为长期保存的密码写入本地加密存储」与 `EduVault` 不符——主动保存教务账号时，`账号\n密码` 会用 Keystore 中不可导出的密钥、以 AES-256-GCM 加密写入 `noBackupFilesDir/edu-account.bin`。已改为按实际行为描述。
2. **支付与充值**：应用没有充值、收银或代下单；`AlipayLogin` 只调 `AuthTask.authV2` 取授权码，用水是把设备交给支付宝的 URI。原文的「支付、签约和扣费」改为「不提供充值/收银/代下单，也不接收支付密码；结算在支付宝内完成，应用只读取订单状态」。
3. **权限**：清单里有 `ACCESS_WIFI_STATE`，但全仓库无 `WifiManager` 调用，支付宝 SDK 也只用 `ConnectivityManager`（8 个类引用）——该权限纯属声明负担。发布者确认后**从清单移除**，隐私文案第 5 节同步改为「网络状态：……应用自身的业务代码不读取该状态，所集成的支付宝 SDK 会用它判断网络可用性」，权限数量由 5 项降到 4 项。`ACCESS_NETWORK_STATE` 保留，因为 SDK 确实在用它。
4. **第三方服务**：把「GitHub 或软件分发服务」落到具体来源（workbuddy 托管的版本清单，安装包按其指向的 HTTPS 地址，可能是 GitHub Releases），并列出四个端点。
5. **使用公告 §4**：删掉应用并不存在的「充值」，改为余额查询与扫码用水。

### 未改（边界）
- 加密与存储（`AccountVault`/`EduVault`/`LifeSessionVault`/`PendingUseVault`）、清除逻辑与范围、更新校验、诊断日志、教务/支付/水控业务逻辑一行未动；权限只减不增（仅移除 `ACCESS_WIFI_STATE`，未新增任何权限）；未新增广告、统计、上传或追踪。
- 两份文本均为打包进 APK 的静态文字，无 WebView、无网络请求，断网可读。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **187**（+28 锚点：文案存在性、邮箱为真实公开地址且与「意见反馈」一致、无残留占位符、教务密码与 AES 表述、不得声称充值、manifest 权限逐项披露、隐私页入口与弹窗同源、`VERSION = 3`、离线可读）全绿。
- APK `delivery/科信小助手-2.5.8.6.apk`，5 396 346 B，SHA-256 `be4903d575cf47a02a1da2dfc7eb49b8bcf7ebf0194dbe44b486b39aa249605e`，验签通过；dex 内确认文案已填充（`shynekoo@outlook.com` / `2026年11月19日`），无 `【…】` 残留，权限表实测 4 项。
- **未做**：真机排版、系统大字体、320dp 换行、深浅色对比。发布信息（邮箱 / 生效日期）已填。


---

## [未发布 · UI 2.0 第一轮] — 工作区未提交（UI-1 + UI-2，未发版）

> 已提交（commit `7a8083d` / `cb3f859`），未发版。本轮只做 UI-1（设计/动效 token）与 UI-2（常驻主界面 + 动画底栏），不改视觉圆角数值、不动网络/账号/支付/水控/升级、不碰课表 JSON 与 WeekBoard。UI-3/4/5 未实施。核心 1262 断言、UI 守卫 **120 锚点**（原 60 + UI-1 的 20 + UI-2 的 40）全绿，普通版 APK 已构建并 v2/v3 验签（水控与诊断均为 false，控制路由 occurrence 全 0）。

### 新增
- **`UiTokens.java`**：尺寸/字号单一来源，逻辑 dp/sp 数值，第一阶段取现有基线（卡片 18dp、底栏 24dp、胶囊 18dp、间距 4–24dp、触控 48dp、导航图标 22dp/文字 12sp、标题 28sp、卡片标题 16sp）。页面左右边距仍走 `AppUi.side()`，未硬编码。
- **`MotionSpec.java`**：时长/位移单一来源（按压 140ms、胶囊 260ms、Tab 进 220ms/出 180ms、位移 8dp、PathInterpolator(0.2,0,0,1)），并提供 `shouldAnimate(Context)`：Android 8+ 用 `ValueAnimator.areAnimatorsEnabled()`，低版本读系统动画倍率；关闭动画时所有组件立即到终态。
- **`MainShell.java`**：一次构建的竖向 shell（headerHost / contentHost 0+weight=1 / navHolder），系统栏与挖孔 Insets 只在此落实一次；底部 Insets 不重复。课表 Pager 在 contentHost 中仍取到完整可用高度。
- **`AnimatedNavBar.java`**：单个可移动胶囊 + 一行三等分 Tab，三项只在构造时创建一次。胶囊仅用 `translationX` 移动；选中/未选中色用 `ArgbEvaluator` 插值；缓存的 `AppUi.Icon` Drawable 改色后 `invalidateSelf()`，不逐帧新建；`contentDescription` 标注选中态。

### 变更
- `TimetableActivity` 的 `render()` 拆为 `ensureShell` / `renderHeader` / `buildScreen` / `renderContent` / `selectTab`。`setContentView(shell)` 只在 `ensureShell` 调一次；Tab 切换不再重建底栏或整个 Activity。普通数据/主题/返回刷新走 `renderContent(false, 0)`，不触发 Tab 入场动画。
- 「今天的课 · 查看课表」改为先定周次再走统一 `selectTab` 流程；`onNewIntent(showToday)` 仍走非动画 `render()`。
- Tab 切换记录/恢复各 Tab 滚动位置（课表用 Pager `verticalPosition()/restoreVerticalPosition()`）；内容转场带 `transitionGeneration` 防旧回调删新页。`onDestroy` 取消动画并清引用。
- `MainNavigator` 返回顺序、`Page`/`CardLayer`/`WeekBoard` 均未改动。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 120 全绿；构建 APK v2/v3 验签通过，普通版无诊断、无水控。
- **待真机验收**：无连接设备/模拟器，底栏快速点击、320dp 窄屏、深色/自定义主题、课表高度、滚动恢复、返回手势完成与取消、系统关动画、今日页 60s 无闪烁均未实测。

---

## [未发布 · 底栏外观修正] — 已提交 `20c2ffa`，未发版（UI-2 收尾）

> 真机反馈底栏视觉比例失衡：选中胶囊铺满整格、底栏偏高、图标不可见。本轮只改底栏外观，不动 MainNavigator 状态管理、Tab 转场、课表与返回手势。核心 1262 断言、UI 守卫 131 锚点全绿，普通版 APK 已构建并 v2/v3 验签。

### 修复
- **图标 0×0 根因**：`AnimatedNavBar` 的图标 `ImageView` 原用 `wrap_content`，而 `AppUi.Icon` 没有固有尺寸，被量成 0×0——真机只显示文字。改为固定 `NAV_ICON`(22dp) 尺寸，图标正常显示。
- **选中胶囊只包图标**：从「铺满整格（slotWidth−8 × 行高−8）」收敛到 64×32dp、圆角 16dp，定位在图标槽背后、不压文字；三个 Tab 各有等宽图标槽，图标/胶囊/文字始终对齐。今日入口改用房屋图标 `home`（在现有 `AppUi.Icon` 家族内新增画法，未引入图标库）。
- **底栏尺寸**：内容高度固定 72dp（落在 68–76dp 目标区间），外框圆角 24→28dp，`navHolder` 水平/底部留白 8→4dp；宽度、左右边距仍走 `AppUi.side()` 自适应，系统栏 Insets 仍只在 MainShell 处理一次。
- 选中/未选中色仍为 `AppUi.ACCENT` / `AppUi.MUTED`，胶囊为 `AppUi.SOFT`，随浅色/深色/自定义主题刷新；胶囊 260ms `translationX` 平滑移动、快速连点从当前位置续接、首次布局/旋转/关动画直接到终态——原有动画机制不变。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 131 全绿；APK v2/v3 验签通过，普通版无诊断、无水控。
- **待真机验收**：胶囊位置/图标显示、320dp 窄屏、大字体、深色/自定义主题、快速连点无残影错位。

---

## [未发布 · UI-3 页面切换动画] — 未发版

> 底栏视觉修复后继续 UI-3。淡入淡出 + 8dp 水平位移的 Tab 内容转场在 UI-2 阶段已落地（仅作用于 contentHost，Header/底栏/Activity 不参与），本轮修复快速连点时的动画竞态。核心 1262 断言、UI 守卫 141 锚点全绿，普通版 APK 已构建并 v2/v3 验签。

### 修复
- **快速连点竞态**：`exit`(180ms) 先于 `enter`(220ms) 结束，旧实现在 exit 的 end 回调里 `contentAnimators.clear()`，导致仍在运行的 enter 脱离追踪——180–220ms 窗口内再切 Tab 时 `cancel()` 遍历不到它，旧 enter 继续写已替换页面的 alpha/translationX，造成残影/错位。改为各动画结束时只从列表移除自己，由时长更长的 enter 负责最终归位与旧页移除。
- **过期帧守卫**：enter/exit 的 update listener 与 end 回调全部加 `generation != transitionGeneration` 守卫，过期帧不再写入任何 View。
- **快照 cancel**：`for (Animator : new ArrayList<>(contentAnimators)) cancel()`，因为 end 回调会修改活跃列表，直接遍历会 ConcurrentModificationException。

### 未改（关键约束）
- 转场参数保持：新页 alpha 0.7→1、translationX ±8dp→0、220ms；旧页 alpha 1→0、180ms；系统插值器、无缩放。
- 只在用户 `selectTab` 时播放；`render()`、课程/主题刷新、A3 每 30s 局部更新均走 `renderContent(false, 0)` 不触发转场。
- `WeekBoard.Pager` 手势/周次/滚动、`Page`/`CardLayer` 返回动画与快照隔离、`MainNavigator` 状态管理均未改动。系统关动画时 `!motion` 直接切换到终态。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 141 全绿；APK v2/v3 验签通过，普通版无诊断、无水控、控制路由 occurrence 全 0。
- **待真机验收**：快速连点无残影/错位、页面切换后滚动位置、课表周次滑动与自适应高度、返回手势完成与取消、系统关动画、今日页 60s 无闪烁。

---

## [未发布 · UI-3 收尾：滚动恢复与转场残留] — 工作区未提交，未发版

> 在 `ffaf933`（转场竞态修复）之上继续 UI-3，只查页面切换动画、快速连点竞态、动画取消清理、滚动位置恢复、与课表横滑/返回手势的关系。**不改转场参数、不改视觉样式、不动 MainNavigator / Page / CardLayer**。核心 1262 断言、UI 守卫 159 锚点（原 141 + 本轮 17）全绿，普通版 APK 已构建并 v2/v3 验签。

### 修复
- **快速连点把上一页的滚动量写到新页**：`restoreScroll` 原来只读字段 `root`，而 `root` 会被后一次切换改指向新页——今日→课表→账号连点时，课表挂起的恢复会落到账号页并把它滚到课表的位置。现在恢复携带转场代号，并在执行时同时校验 `generation`、目标页仍是 `root`、tab 未变，任一不匹配即放弃。
- **滚动恢复排在了布局之前**：原实现用 `root.post(...)`，只保证「下一条消息」，不保证在首次 measure/layout 之后；未测量的 ScrollView 会把 `scrollTo` 抹掉，首次切回该 Tab 可能回到顶部。改为一次性 `OnPreDrawListener`（布局后、首帧前）恢复并自摘除监听。
- **普通重建把滚动位置重置**：`render()` 的非动画路径原来直接按 `tabScroll[nav.tab()]` 恢复，而该值只在 `selectTab` 时更新——旋转、分屏（`onConfigurationChanged` → `render()`）、换主题、数据刷新都会跳回上次切 Tab 的位置或顶部。现在 `render()` 在重建**同一屏**（`contentTab`/`contentCampus` 与当前一致）时先 `captureScroll()` 再恢复；换 Tab、校园记录↔入口列表这类真正换页的情况不搬运位移。
- **被打断的入场页带着 8dp 位移离场**：快速连点时上一页的 enter 被 `cancel()` 只停在当前数值，`exit` 只动 alpha，位移会留到淡出全程。现在被保留为离场页的残留先归位到「alpha 1 / translationX 0」。
- **转场期间两页同时可读**：离场页在交叉淡化期间标 `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`，读屏不会把正在消失的旧内容当成仍在页面上朗读。
- **「回本周」/ 选周次的换页时机**：原先直接 `applyWeek(delta)`，跨几周闪一下换页；第一次改成「先滑出一屏、落地时重建」，仍是「滑完才变成本周」，动画结束那一帧内容突变；第二次改成「先提交周次 + Tab 式淡入（alpha 0.7→1 / ±8dp / 220ms）」，内容不再突变，但整屏淡入与「横向换一周」的心理模型不符，真机观感比滑页弱。现在走第三条：`jumpWeek` **先提交周次**，再调用 `renderContent(true, ±1, true)` 的 slide 分支 —— 整屏位移一屏（`pageWidth()`），旧页 `-offset*t` 与新页 `offset*(1-t)` **同一时长同向同行、都不透明**，落地帧就是本周。周次弹窗 `selectWeek` 同样走这条路径；相邻一周的 ‹ › / 左右滑动仍是 Pager 滑页，未改。
- **滑页参数只有一个来源**：`MotionSpec.WEEK_SLIDE_DURATION`(190ms) 同时给 `Pager.settle` 与跨周 slide 使用，两处时长/曲线（`DecelerateInterpolator`）一致；`Pager.animateOutThen` 的滑出位移收敛为 ±1 屏（原先按 `-delta*width`，跨 5 周会把板面甩出 5 屏），避免以后有人拿它做跨周跳转。Tab 转场参数（alpha 0.7→1 / ±8dp / 220ms 进 180ms 出）保持不变，slide 分支不淡入、不新建任何动效数值。

### 检查结论（未改动）
- **与课表左右滑动**：转场只改 contentHost 内 `next` 的 `translationX`，`WeekBoard.Pager` 改的是自己三个子页的 `translationX`，两者作用在不同 View 上、互不拦截触摸；`Pager.onInterceptTouchEvent` 在自身 `settle` 期间返回 true，不参与内容转场。切走时 `onDetachedFromWindow` 会 `cancel` 掉周次动画，不会在已分离的页上回调 `applyWeek`。
- **与返回手势**：转场与 `Page` 的返回动画不同时发生——返回走 `nav.consumeBack()` → `render()`（非动画），会先取消内容转场；`refreshBackMode()` 在 `selectTab` 中已按新 tab 重算，系统预测式/本地缩放的选择不受转场影响。
- Tab 转场参数（alpha 0.7→1 / ±8dp / 220 进 180 出）、`UiTokens` 数值、`MainNavigator`、`Page`、`CardLayer` 均未改；`MotionSpec` 只新增 `WEEK_SLIDE_DURATION`(190ms) 这一个 token，`WeekBoard` 只动了 `Pager.animateOutThen` 的滑出位移（±1 屏）与 `settle` 取该 token（190ms 数值不变），手势判定、页高测量、周次提交逻辑未改；系统关动画时仍走 `!motion` 直接到终态。

### 验证
- `test_core` 1262 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **159** 全绿；普通版 APK（`delivery/科信小助手-2.5.8.6.apk`，5 392 250 B，SHA-256 `1f84c5efe1c56e2fd6bc59288a2c81d4cb20dbad48d22fd48af44b0c998af8b9`）构建通过、v2/v3 验签 true，构建脚本的三开关/控制路由断言全过。
- **待真机验收**：快速连点（三 Tab 连续点）无残影错位、切走再切回每页滚动位置、旋转与分屏后滚动位置、换主题后不跳顶、课表横滑与自适应高度、**回本周为整屏滑页且落地帧即为本周（不再淡入、不再突变）**、返回手势完成与取消、系统关动画、今日 60s、320dp 窄屏 —— 均未实测。

---

## [未发布 · 2.5.8.6] — 工作区未提交（versionCode 204）

> 已提交、未发版。学业情况页补上每个课组的课程明细，并改为点击展开；核心 1262 断言、UI 守卫（当时 60 锚点）全过，普通版 APK 已构建并验签。

### 新增
- **课组课程明细**：学校页面只内联学分树，每个课组的课程在 `POST /xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html` 这个只读查询里（学校自己展开箭头时调的就是它）。`EduClient.academicCourses()` 发起该查询，参数回显页面的隐藏字段 `xh_id`/`cjlrxn`/`cjlrxq` 等——这些字段在读到学分树时顺带缓存，展开一个课组只花一次小 POST，不再下整页 1.7 MB。
- **点击展开**：叶子课组行可点击，点开才取课组课程，取过的按课组 id 缓存，再点不重复请求；分类卡片头部可折叠/展开整块课组。加载中、空结果、失败重试三种状态都有明确文案。
- **课程行**：课程名 + 成绩（无成绩时显示「在读 / 未修 / 未过」）+ 学分；通过的课程用正文色与强调色，未通过/未修降为次要色。展开出来的课程块左侧加一条 2dp 细线做括注，否则课程行与下一个课组行缩进接近，会读成同一个列表。
- `EduAcademic.Course` 类型化课程模型（课程名/课程号/学分/成绩/绩点/学年/学期/必修限选/修读状态），附带 `passed()` 与 `statusLabel()`；`EduAcademic.parseCourses()` 在 core 里解析该接口的扁平 JSON 数组，不引入外部 JSON 库。
- `EduAcademic.Node` 新增 `id` 字段（课组编号，课程查询以它为键）。
- 汇总卡补全统计：原先只显示计划/通过/在读/未修，现按学校给的六个数字分两行显示，补上 **未通过** 与 **计划外通过**。

### 修复
- 隐藏字段提取改为「先定位整个标签、再读 value」：页面实际写成 `<input type="hidden" name="xh_id" value="…" id="xh_id"/>`，id 在 value 之后，按 `id="…"` 前缀取值的正则一个都取不到。

### 变更
- 去掉 2.5.8.4 里把「创新实践课 / 其他课程 / 创新创业情况」合并成紧凑卡的做法：其中「其他课程」实际挂着 6 门计划外课程，合并会让它无法展开。现在每个分类各自成卡，卡片数量与分类数一致。

### 验证
- `test_core` **1262**（新增 23 条课程解析断言，含裸数字、`null`、转义引号、全角括号、无名行丢弃、超长拒绝）/ `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **60**（新增 8 条：课程解析必须在 core、只读路径白名单显式放行、只有叶子课组可点、取回结果要缓存、汇总必须显示未通过）全绿。
- 真实账号端到端：16 个课组、94 门课程，经 Java `parseCourses` 后学分合计、通过/在读/未修分布与学校页面逐项一致，无空名或 `null` 泄漏；9 个隐藏字段全部可读（`cjzt` 页面本就没有，传空有效）。普通版 APK v2/v3 验签通过，三开关全 false、控制路由全 0。

---

## [未发布 · 2.5.8.5] — 已被 2.5.8.6 取代（versionCode 203）

> 尚未 commit、未发版。学业情况页按学校层级重排版；核心 1239 断言、UI 守卫 52 锚点全过，普通版 APK 已构建并验签。

### 变更
- **学业情况页按真实层级排版**：学校页面是「主修 → 通识教育/专业教育/第二课堂 → 课组 → 课程方向」的五层树，此前把 37 个节点平铺成 37 张同级卡片，看不出层级。现在二级分类各自成卡（分类名 + 已获/要求学分 + 进度条），三级课组作为加粗行、四级及以下作为缩进细节行（每深一层缩进 14dp，字号 13→12，学分 12→11），顶部汇总卡标题带上根分类名（如「主修 · 课程修读情况」）。页面未提供层级时自动回退为原平铺卡片，不会空白。
- 既无学分也无子分类的类别（创新实践课 / 其他课程 / 创新创业情况）合并到一张紧凑卡，避免末尾连续三张空卡。
- 学分进度条按 0.01 精度换算（原先取整会把 24.5 学分显示成 25）；要求学分为 0 时不再画一条永远空着的进度条。

### 修复
- `EduAcademic` 原正则要求 `id`→`yxxf`→`yqzdxf`→`sftg` 严格按序且都存在，真实页面存在缺 `yxxf` 的写法会被漏掉；改为先取标签再逐个读属性，顺序与缺省都不再影响结果。

### 新增
- `EduAcademic.Node` 新增 `depth` 字段、`Report.layered` 标记：深度来自页面自身的 jQuery 建树关系（`<li id='li…'>` 被 `.appendTo($("#ul父ID"))`），不是猜的；父链断裂的节点降为根节点，保证不漏分类。

### 验证
- `test_core` **1239** / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` **52** 全绿；真实账号端到端跑 Java 解析器：`layered=true, 37 节点, 最深 5 层, GPA 2.9875, 计划 118/通过 54/未修 50/在读 14`。普通版 APK v2/v3 验签通过，三开关全 false、控制路由全 0。

---

## [未发布 · 2.5.8.4] — 已被 2.5.8.5 取代（versionCode 202）

> 尚未 commit、未发版。修复包，供真机覆盖安装验证；UI 守卫 46 锚点全过，普通版 APK 已构建并验签。

### 修复
- **学业情况页闪退**：`card()` 工厂在内部就把卡片挂进 `results`，`headCard()`/`show()` 又各挂一次，同一个 View 被 addView 两次触发 `IllegalStateException: The specified child already has a parent`，且发生在主线程回调里导致闪退、错误提示来不及显示。改为 `card()` 只创建、由唯一入口 `addCard()` 挂载并设置间距——与成绩 / 空教室页「工厂只创建、调用方决定放置」的一致做法。
- 说明文字 `screen.note(...)` 原先每次 `show()` 都追加一条，重载会堆叠；改为 `onCreate` 中只设置一次。

### 变更
- 教务卡两行重排：第一行 = 成绩 / 空教室 / 教务系统；第二行 = 学业情况 / 河工程官网 / 科信官网（2.5.8.3 曾把学业情况放第一行、教务系统放第二行，本次按反馈对调）。

### 守卫
- UI 契约新增锚点：`card()` 不得自行 `results.addView`（防双重挂载回归）、`screen.note()` 仅一处、教务卡六个 tile 顺序（46 锚点全过）。

### 验证
- `test_core` 1228 / `test_build_config` 5 / `test_webview_policy` 5 / `test_ui_contract` 46 全绿；普通版 APK v2/v3 验签通过，三开关全 false、控制路由全 0。数据层 `EduAcademic`/`EduClient` 本次未改动，2.5.8.2 已用真实账号端到端验证过。

---

## [未发布 · 2.5.8.3] — 已被 2.5.8.4 取代

> 历史 WIP 记录（未 commit、未发版）。2.5.8.3 的闪退与卡片顺序问题已在 2.5.8.4 修复；下列条目仅作演进说明。

### 新增
- **学业情况查询**：教务卡新增「学业情况 · 学分·绩点」入口，直连学校「学生学业情况查询」页，展示绩点与去重后的学分树（按学分分组 + 进度条）。解析逻辑放在平台无关的 `core` 模块（`EduAcademic`），Activity 只负责排版；请求走与成绩相同的登录会话，页路径与 Referer 白名单同步收窄。
- 对应核心断言与 UI 结构守卫已补充（当前全仓 1228 项断言）。

### 变更
- 主题色彩卡文案「品牌蓝」统一改为「默认」，长按恢复提示同步调整。
- 教务卡入口重排：「学业情况」并入主功能行，「教务系统」移入网站行；「河工程官网 / 科信官网」保持原顺序。

### 修复
- 外观页课表预览的圆角绘制把 `draftRadius` 当像素用，导致预览圆角只有实际课表的约 1/3；改为按 `dp()` 换算，与 `WeekBoard` 真实格子一致。
- 外观页删去两段已经与当前行为不符的说明文字（返回手势说明、预览块说明），避免误导。

### 废弃 / 移除
- 无。

### 文档与 CI（本次变更，不改变 APK 行为）
- 新增 `AGENTS.md`：开发任务九步流程、Git Diff 文档同步检查、明确「无需更新文档」声明方式。
- 新增 `scripts/check_doc_sync.py`：按改动路径提示可能受影响的文档，默认不阻断构建。
- `docs/` 新增八类文档：架构、模块、构建发布、安全约束、UI 导航、数据接口、已知问题、Agent 交接；保留原有截图与审计文档。
- 新增 `.github/workflows/docs-ci.yml`：四个测试入口 + 关键文档存在性检查（不构建、不签名、不发布）。
- 修正 README 两处旧断言数（1073 / 1034）为实测 1228。
- 未改动任何业务代码、构建逻辑、签名、支付、用水控制或版本升级。

---

## [2.5.8.1] - 2026-11-19（已提交并 push，未发版；versionCode 199）

> Git 提交 `890e9d8`…`6406500`。`delivery/update/update.json` 仍为 `2.5.7`，下载站与 GitHub Release 未更新；真机验收清单尚未勾选完成，**不得发布**。

### 新增
- **使用码**：8 位码，可不扫码直接开启设备；扫码页自动识别设备与订单状态，「使用码」按钮与「重新扫描」并列。
- **UI 行为静态守卫**：`test_ui_contract.py` 的锚点从 26 增至 33，防止重构时悄悄改变页面结构。
- **真机验收清单**：`docs/real-device-acceptance.md`，覆盖导航 / 手势、局部刷新、支付与用水、安装包与发布配置等分组，全部标注待验收。

### 变更（安全整改 S1–S6）
- **S1** 应用内更新：清单必须包含完整大小与 SHA-256 且走 HTTPS 才接受；校验 APK 身份；重定向限制在 HTTPS、有界且无环，跨主机时丢弃 query。
- **S2** 诊断默认关闭，需显式开启，导出前脱敏。
- **S3** 学校 WebView 导航收紧，仅允许已批准的 HTTPS 主机与端口。
- **S4** 敏感页面不进入返回动画快照。
- **S5** 支付与用水业务安全审计（详见 `docs/security-audit-payment-water.md`）。
- **S6** 教务登录 WebView 统一走 `TrustedUrlPolicy` 准入。

### 变更（架构重构 A1–A4，行为等价）
- **A1** 主导航状态机下沉到 `core`；`TimetableActivity` 逐步拆出 `CampusScreen` / `HomeScreen` / `ScheduleScreen` 三个静态嵌套类（阶段 1–3）。
- **A2** 引入带类型 `Course` 模型，JSON 字段无损映射。
- **A3** 真局部刷新：新增 `RefreshDecision`（NONE / TODAY_DYNAMIC / FULL）与 `TodayDynamic`；同日时钟 tick 就地更新今日摘要与每行状态，不再整页重建；跨天 / 切 tab 才重建，数据不一致自动回退 FULL，保留滚动位置与周次切换。
- **A4** 会话检查去抖、缓存、网络错误与登出分离。

### 修复
- 时钟触发的冗余全量重绘：仅必要时重建页面（A3）。
- 会话并发检查重复触发与错误串扰（A4）。

### 废弃 / 移除
- 无。

### 验证（本环境实际执行）
- 核心断言 1196 → 1209 →（本轮工作区）1228 项全过。
- `test_core` / `test_build_config`(5) / `test_webview_policy`(5) / `test_ui_contract`(33) 全绿。
- 三配置构建开关实测：普通 `WATER=false DIAG=false`、用水 `WATER=true DIAG=false`、诊断 `WATER=false DIAG=true`；均通过断言与 v2/v3 签名。只读版 DEX 控制路由 occurrence 全为 0。
- **真机验收未完成**，详见 `docs/real-device-acceptance.md` 与 `docs/rectification-acceptance.md`。

---

## [2.5.7] - 2026-11-18（线上正式版，versionCode 197）

- 新增深色主题与返回手势动画。
- 应用与课表主题色支持自定义。
- 修复课表显示异常：课名字号与行高按屏幕自动适配，窄屏不再挤成两字一行，整块高度跟随页面剩余空间。
- 安装包 5,359,482 字节，SHA-256 `33851ee1190e98b1655489bd1c7989119fc6f16cfc4fd7883dc3beae6b5728bc`。

## [2.5.6.11] - 2026-11-18（versionCode 见对应 tag）

- 账号页新增「外观与主题」，分应用主题与课表外观两页。
- 应用主题：明暗模式（自动 / 浅色 / 深色）、主题色彩（默认 / 自定义）、悬浮底栏样式、返回手势动画。
- 课表外观：课程配色（18 个色槽逐个可调）、课程文字颜色、格子圆角、格子不透明度、网格线开关；改动即时预览。
- 深色为独立挑选的灰阶，正文、卡片、对话框、状态栏同步切换。

## [2.5.6] - 2026-11-17（versionCode 177）

- **修复**慧生活入口丢失：2.5.5 误用只读构建，用水控制未编进包；本版重新带上，支付宝免密登录不变。
- 调课多段周次（`2-6周(双),11-15周`）不再被当作坏行跳过。
- 未登录自动跳对应登录页，登录完自动回原页并续跑被打断的查询。
- 校园页新增「教务系统」入口；「数据与隐私」移入「更多」。

## [2.5.3] - 2026-11-17

- 账号页新增「意见反馈」（邮箱 / QQ 群，点一下复制）与「更多」（GitHub 仓库、开源许可证）。
- 项目按 MIT 许可证开源。
- 慧生活798 登录只保留支付宝授权，去掉手机号验证码登录。

## [2.5.0] - 2026-11-16

- 开启用水固定走支付宝免密，去掉支付方式选择；设备未提供该方式时提前说明。
- 请求层按 WaterWidget 实测修正：版本头用版本名字符串、开启参数精简、吸收业务码表。
