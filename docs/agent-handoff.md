# Agent 交接

供任意 Agent 接手时快速对齐现状。以**当前代码、Git 记录、实测结果**为准；流程见仓库根 [`../AGENTS.md`](../AGENTS.md)。

> 本文档只记录**现状与结论**。逐轮排查过程、请求形态试验与调试细节属开发期材料，不入公开仓库；需要时从 Git 历史查对应提交。

## 一、现状快照（2026-11-21，2.6.0 发版后）

| 项 | 值 |
| :-- | :-- |
| 技术栈 | Java + 原生 Android View + Python 构建；无 Kotlin / Compose / Gradle |
| 线上正式版 | **2.6.0 / versionCode 236**（tag `v2.6.0`）；GitHub Latest、国内下载站、Gitee `release` 三渠道均已上线并核对 200 |
| 已提交 HEAD | `main` 与 `origin/main` 同步，工作区干净 |
| 当前开发分支 | 无。`feat/diag-2.5.8.9` 与 `feat/analytics` 均已并入 `main` |
| 最新包 | `delivery/科信小助手-2.6.0.apk`（236，5,486,527 B，`--field-water`，v2+v3 签名）；说明见 `delivery/release-2.6.0.md` |
| 线上预览 | **2.6.0-beta.18 / 235**（GitHub 与 Gitee 的 `preview`）；国内 WorkBuddy 站点仍停在 beta.5 / 222 |
| 核心断言 | 实测见本文第四节；UI 契约 388 锚点、诊断隐私 717 项、WebView 5 处 |
| GitHub 登录 | `gh auth login` 已登录 `shynekoo`（ADMIN，repo scope）；按 AGENTS.md，push / tag / Release 仍需用户逐次确认 |

### 分发现状的三处已知限制

1. **WorkBuddy 下载站**：发布回落 `SITES_ACCOUNT_MISMATCH`——`delivery/update/` 绑定的应用属另一个账号。切回原发布账号后重发布即可；在此之前国内站点的清单停在旧版本。
2. **预览清单按渠道各一份**：`beta.json`（WorkBuddy）/ `beta-github.json` / `beta-gitee.json`，仅 `url` 不同。客户端比的是清单里的 `versionCode`，**只传包不传清单等于没发**。
3. **预览通道默认关闭**：用户必须在「更多 → 更新渠道」选定来源并打开「接收预览版更新」，否则读不到预览清单。

## 二、已解决问题的结论（不再复述过程）

| 问题 | 结论 |
| :-- | :-- |
| 学生评价页面在 WebView 里装不起来 | 学校那个页面是**片段页**，依赖菜单壳文档的全局变量与脚本。改为原生列表 + 原生答题页，不再加载网页 |
| 问卷片段读出的题目数远少于实际 | 片段不闭合自身 `<div>`。切片改为按「同类开始标签」的平铺边界，不按嵌套计数 |
| 写端点回「参数异常」 | 请求体必须与学校页面同构：三层身份字段与表级字段都要回填，未作答的题目不发答案字段，多选重复同名字段故走字符串拼装而非 `Map` |
| 提交后界面无反应 | 回话明确成功即生效；回读列表只作旁证与诊断，`UNKNOWN` 不吞掉已确认的成功 |
| 已评条目点开没有当时选的答案 | 已提交行的 HTML 没有表单控件，答案在只读展示节点里。解析器新增只读题型 `SHOWN`，`body()` 永不写它的答案字段 |
| 学生评价卡片点开即缩回 | 折叠结束时写入 `WRAP_CONTENT`（-2）被 `Math.max(0, …)` 钳成 0。高度原样写回，勿再加钳制 |
| 评价列表漏课 | 列表接口会分页，`evaluations()` 按 `totalPage` 循环取页并跨页去重 |
| 更新渠道缺境内分发点 | 增加 Gitee 渠道，公开仓库 `gitee.com/sun-heyue/CampusKit`，两个固定 tag `release` / `preview`；默认仍是 WorkBuddy |
| 同名附件重传后清单不更新 | 必须先传新件、再删旧件；Gitee 删除接口要带 `release_id`，用 `access_token` 查询参数而非 `Authorization` 头 |
| 预览包安装报「解析软件包时出现问题」 | 上传了签名**前**的中间产物。只发 `delivery/科信小助手-<version>.apk`，上传前 `apksigner verify -v` 要看到 `Verifies` |
| 出包缺少用水功能 | 构建必须带 `--field-water`；中间产物已改名 `unsigned-aligned.apk` 防误传 |

架构与安全相关的结论分别在 [`architecture.md`](architecture.md)、[`security.md`](security.md)，不在本文重复。

## 三、验证状态（如实）

**已执行**：见下方命令与计数；`2.6.0` APK 已构建并通过 v2+v3 验签；产物级 DEX 路由边界扫描通过；Gitee 与 GitHub 的清单与 APK 已用匿名链路回读核对。

**未执行（不得标记通过）**：

- 真机验收清单 [`real-device-acceptance.md`](real-device-acceptance.md) 全部待勾选；
- 真实支付 / 用水出水、支付宝 SDK 真机回调、MobSF 扫描、签名轮换、旧版覆盖升级；
- **学生评价的提交成功路径**目前只有代码路径与静态证据，未代用户真实提交。

## 四、接手第一步

1. `git status` + `git diff` 看工作区；**不要** `git checkout .` / `reset --hard`。
2. 读 [`architecture.md`](architecture.md) 与 [`modules.md`](modules.md)，按任务读专题文档。
3. 按 [`../AGENTS.md`](../AGENTS.md) 九步流程工作；跑 `python scripts/check_doc_sync.py` 拿文档提示。
4. 测试入口全跑，未做的不要写「通过」：

```bash
python scripts/test_core.py && python scripts/test_build_config.py \
  && python scripts/test_diag_privacy.py && python scripts/test_diag_files.py \
  && python scripts/test_webview_policy.py && python scripts/test_ui_contract.py \
  && python scripts/test_analytics.py && python scripts/test_secrets.py
```

5. 发版按 [`build-and-release.md`](build-and-release.md)；真机验收未完成不得发布。
6. **提交与推送由用户确认**，Agent 不自行 commit / push / tag / Release。

## 五、文档索引

[架构](architecture.md) · [模块](modules.md) · [诊断日志](diagnostics.md) · [构建与发布](build-and-release.md) · [安全约束](security.md) · [UI 与导航](ui-navigation.md) · [数据接口](data-interfaces.md) · [开源许可](licensing.md) · [已知问题](known-issues.md) · [安全审计：网络/SDK](security-audit-network-sdk.md) · [安全审计：支付/用水](security-audit-payment-water.md) · [真机验收](real-device-acceptance.md) · [返回手势](back-gesture.md) · [变更日志](../CHANGELOG.md)
