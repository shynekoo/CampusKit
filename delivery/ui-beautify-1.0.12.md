# CampusKit 1.0.12（界面美化）

2026-10-14，版本代码 129。用户提供 `CampusKit-ui-beautified.zip`，按其内容覆盖界面层后重建。

## 合入的文件

| 文件 | 变化 |
| --- | --- |
| `AppUi.java` | 新增 `outlined(fill, radius, stroke)` 描边形状工具 |
| `TimetableActivity.java` | 今日课程改为按课程配色的圆角填充卡片，课程名/教室用课程色，正在上课加“● 正在上课”，已结束整行淡化；底部标签栏改描边圆角 24dp 加阴影、选中态浅底；白色卡片 18dp 描边；快捷入口图标加浅底圆形；标题字号与分隔线颜色对齐设计系统 |
| `WeekBoard.java` | 课程块内边距与圆角（4dp → 8dp）；今日列表头改为蓝色圆角块、日期文字转白；移除纵向网格线 |
| `res/values/styles.xml` | 新增 `AppDialog` 主题，弹窗统一 `@drawable/dialog_background`，强调色 `#275DAD` |
| `res/drawable/button_surface.xml` | 圆角 10dp → 12dp |
| `res/drawable/dialog_background.xml` | 新增，24dp 圆角白色面板，四周 12dp 内缩 |

覆盖前的原文件备份在 `tmp/ui-backup-20261014/`。

## 验证

- 644 项核心断言通过。
- 构建、v2/v3 签名验证通过；APK 内确认含 `res/drawable/dialog_background.xml`，dex 含 `AppUi.outlined`，无易校园支付类残留。
- 安装包：`delivery/CampusKit-1.0.12.apk`。
- 用户明确表示不需要模拟器验证、不需要安装到手机；本版只交付到 APK，未做视觉验收。

SHA256：`4920cc4c9c84af10e148f7d80a7bf0aacca49d23d3efc1730574a0132a5e6f58`。
