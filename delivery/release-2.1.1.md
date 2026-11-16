# 科信小助手 2.1.1

- 版本代码：155
- 安装包：`delivery/科信小助手-2.1.1.apk`（4.92 MB）
- sha256：`2156548a9bbab87bdc410a96e030d5d36bc681faaf505f23493441d41dcef478`
- 包名不变（`io.qzxy.lite.preview`），可直接覆盖安装，数据保留

## 先纠正上一版的判断

2.1 的说明里写着「`LifeWaterActivity` 在源码里不存在，点『进入独立用水』会抛 `ActivityNotFoundException`」。**这是错的**，是只查了 `client/android/src` 就下的结论。

这个类在 `client/fieldtest/src/`，而 `--field-water` 构建会把整个 `client/fieldtest/src` 加进编译（`build_preview.py` 第 167 行），清单里也声明了它。解包 2.1 的 dex 能看到 `Lio/qzxy/lite/preview/LifeWaterActivity;`、`LifeWaterProtocol`、`LifeWaterRecord` 全都在，`WaterActivity` 也在。

所以扫码进独立用水这条路是通的，**入口没有删除**。

## 真正没用的东西：取件组件

「取件信息」卡片在 1.5.3 之前就从首页去掉了，但实现一直留着，而且留下的是一整条**已经断掉的引用链**：

| 残留 | 状态 |
|---|---|
| `PickupWidget`（159 行，菜鸟 RemoteViews 宿主） | 只被 `pickupCard()` 调用 |
| `pickupCard()` | 没有任何地方调用它（首页早就只剩快捷入口、今天的课、教务卡） |
| `onStart` / `onStop` / `onActivityResult` 里的取件分支 | 只服务 `PickupWidget` |
| `PickupPresence`（core，判断「有没有待取件」） | 只被 `PickupWidget` 调用 |

整条删掉。`pickup-widget` 那份偏好仍留在「清除全部本机数据」的清理列表里——旧安装里还有菜鸟组件的 id，删列表就等于删不掉了。

## 顺手清掉另一条断链：PDF 导入

「网页手动导入课表」删掉之后，同一批还有三段没人走得通的路：

- `choosePdf()` —— 打开 PDF 选择器的唯一入口，**没有任何调用方**；
- `onActivityResult` 里的 `request == 40` 分支（PDF 解析）与 `request == 41` 分支（网页课表回传）—— 两个请求码都不再有发起方；
- `TimetablePdfReader`（pdfbox 封装）—— 只被 `request == 40` 分支调用。

一起去掉。`TimetablePdfParser` 留着：教务系统导入仍然用它解析课程，`TimetableWebReader` 也还依赖它。pdfbox 的运行库仍随包打包（构建脚本没动，省不了体积），删掉的只是死代码。

另外把 `pdfWorker` 改名 `backgroundWorker`——它早就不只跑 PDF 了，现在负责登录校验和备份导出。

## 验证

- 核心断言 **908** 项通过（915 − 7 条取件识别断言，随 `PickupPresence` 一起删除）。
- `aapt2` 读到 `versionCode='155' versionName='2.1.1'`、`application-label:'科信小助手'`。
- 解包 dex 确认 `PickupWidget` / `PickupPresence` / `TimetablePdfReader` 已不在，`LifeWaterActivity` / `LifeWaterProtocol` 仍在。
- v2/v3 签名通过，与 2.1 同密钥。

## 未在真机验证

删掉取件组件后首页布局少了一个隐藏分支，视觉上不应有变化；「进入独立用水」按钮是否真能打开页面（需要已登录慧生活并扫到设备码），仍只能在实际设备上确认。
