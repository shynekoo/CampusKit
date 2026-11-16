# CampusKit 1.5.3

2026-11-16。版本代码 148，`delivery/CampusKit-1.5.3.apk`，
sha256 `d33a8e4f75ca40a6af7c5b22ac67342ef69299737a49ecf00c19817cc5508c6f`。
签名密钥与 1.5.2 相同，可直接覆盖安装；课表、账号、登录态都保留。

这一版只换图标：原来那个蓝色底 + 白色水滴的矢量图，换成你给的「书本 + 钟楼 + 星」那张。

## 图标怎么落地的

源图 1254×1254，四周是白边、中间是圆角方块（实测圆角半径 276px，约 22%）。
没有直接缩一张图塞进去，而是按 Android 的两条路径各出一份：

| 用途 | 文件 | 处理 |
|---|---|---|
| 自适应图标（Android 8 及以上，桌面实际显示的就是它） | `res/mipmap-*/ic_app_background.png`，尺寸 108/162/216/324/432 | 原图**不去圆角、四角不留白**：四角的白边用紧邻的浅蓝渐变填平，这样无论桌面用圆形还是方圆角遮罩，边缘都不会露出白角 |
| 传统图标（Android 7、以及忽略自适应图标的桌面） | `res/mipmap-*/ic_app.png`，48/72/96/144/192 | 按原图自身的圆角裁成透明圆角方块，四角 alpha=0 |
| 自适应图标声明 | `res/mipmap-anydpi-v26/ic_app.xml` | 整图放 background 层，foreground 留空（图本身已经画满，再缩放一次会缩小主体） |

清单从 `@drawable/ic_app` 改为 `@mipmap/ic_app`，并补了 `android:roundIcon`；
旧的矢量 `res/drawable/ic_app.xml` 删掉，避免两份图标并存。

生成脚本留在 `scripts/MakeAppIcon.java`（JDK 单文件，`java scripts/MakeAppIcon.java <图片> client/android/res`），
以后换图直接重跑，不用再手算密度。

## 验证到哪一步

- 核心断言 886 项通过（与 1.5.2 相同，这一版没有改核心逻辑）。
- `aapt2` 读到 versionCode 148 / versionName 1.5.3，`icon='res/mipmap-anydpi-v26/ic_app.xml'`，v2/v3 签名校验通过。
- APK 内 11 个图标资源齐全（5 档 × 2 变体 + 1 个 adaptive 声明）；从 APK 里解出的 192px 图标与源图一致，四角 alpha=0。
- 用圆形和 30% 圆角矩形两种遮罩预览过：主体（楼、书、星）完整，边缘无白边、无缝。
- **没做到的**：没真机。桌面图标可能因为启动器缓存而不立刻变——装上后如果还是旧图标，
  把桌面重启一次或卸载重装即可，不是包的问题。
