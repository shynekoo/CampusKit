# 科信小助手

趣智校园 / 慧生活798 的第三方安卓客户端。原生实现，无广告、无账号系统，凭据只保存在本机。

当前版本：**v2.5.3**（版本代码 173）

## 功能

- **课表**：从教务系统自动导入（选学年学期即可），多课表管理、编辑周次、桌面组件
- **成绩 / 空教室**：直连学校教务接口，按学期、周次、节次即查即出
- **充值**：走支付宝官方通道，钱只在支付宝里扣，返回后重新读余额
- **洗澡打水**：扫设备上的码 → 点「开启用水」→ 走**支付宝免密**扣费 → 用完点「结束用水」。在线水控、淋浴、饮水机均可，失败时有具体原因提示（欠费 / 被占用 / 未签约等）
- **应用内更新**：启动时检查版本清单，下载后校验字节数与 SHA-256 才安装

## 下载

- GitHub Releases：<https://github.com/shynekoo/kexin-xiaozhushou/releases/latest>
- 直链（国内更稳）：<https://wbapp-g8yy3klci8kmeufm6vdn0h.app.workbuddy.host/campuskit-2.5.3.apk?sharecode=BZD63HRP4B>

## 隐私

- 登录凭据 AES-256-GCM 加密存于应用私有目录，不上传
- 诊断信息导出时已剔除 token、uid、手机号等敏感字段
- 水控只与慧生活官方服务器通信，无中间服务器

## 构建

无 Gradle，手工工具链：

```bash
python scripts/build_preview.py --field-water
```

产出 `delivery/科信小助手-2.5.3.apk`（约 5.3 MB），构建即跑 1034 项核心断言，通过才出包。

## 更新日志

**v2.5.3**（2026-11-17）
- 账号页新增「意见反馈」（邮箱 / QQ 群，点一下复制）与「更多」（GitHub 仓库、开源许可证）
- 项目按 MIT 许可证开源
- 慧生活798 登录只保留支付宝授权，去掉手机号验证码登录

**v2.5.0**（2026-11-16）
- 开启用水固定走支付宝免密，去掉支付方式选择；设备未提供该方式时提前说明，不盲发请求
- 请求层按开源项目 [WaterWidget](https://github.com/nocookies111/life-798) 的实测修正：版本头用版本名字符串、开启参数精简、吸收实测业务码表

更早版本的明细见 [`delivery/release-*.md`](delivery/)。

## 致谢

- [nocookies111/life-798（WaterWidget）](https://github.com/nocookies111/life-798) —— 慧生活协议的请求头与业务码表

## 许可证

[MIT License](LICENSE) © 2026 shynekoo

第三方组件（ZXing、PDFBox、支付宝 SDK 等）各自的许可证见 [`client/THIRD_PARTY.md`](client/THIRD_PARTY.md)。
