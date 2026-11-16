# CampusKit 1.5.0

2026-11-16。版本代码 145，`delivery/CampusKit-1.5.0.apk`，
sha256 `ff7c9bbccf46b2e1f93409ca72cb7038f526ef9cda6e0e52b4d12df1042bd847`。
签名密钥与 1.4.9 相同，可直接覆盖安装；课表、趣智登录状态、慧生活/教务账号都保留。

这一版只改一个入口：首页「校园生活」里的 **菜鸟 / 出库码** 换成
**身份码 / 淘宝·菜鸟驿站**，点下去走 淘宝 → 菜鸟驿站 → 身份码，不再走菜鸟裹裹的出库码页。

## 为什么整条路要换

菜鸟把这张码换地方了。「出库码」这个名字已经不用，现在驿站扫的是 **身份码**，
而这一页现在住在淘宝里：它的地址是淘宝自己的 H5

```
https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code?x-ssr=true
```

这页要靠淘宝登录态才渲染得出来，所以不能当普通网址发给浏览器，也不能直接
`ACTION_VIEW` 一个 https——淘宝只在自己的 `tbopen` 桥里打开自家 H5，地址必须
作为 `h5Url` 参数传进去：

```
taobao://m.taobao.com/tbopen/index.html?h5Url=<整段 percent-encoded 的上面的地址>
tbopen://m.taobao.com/tbopen/index.html?h5Url=<同上>
```

两个 scheme 都试，因为旧版淘宝只认 `tbopen`。

## 参考的 GitHub 项目

先把 GitHub 翻了一遍，找的是「怎么在安卓上打开取件码/身份码」而不是自己猜 scheme：

- [`yimeifun/PickTool`](https://github.com/yimeifun/PickTool)（MIT，2026-10 仍在更新）
  —— 这是最对症的一个，`Destination.java` 里逐个平台的深链、包名、网页兜底都列好了，
  `Destination.TAOBAO` 就是上面的 `pages-fast…identity-code` 地址，
  `Destination.CAINIAO` 是 `cainiao://desktop/station_code`。它的 `DeepLinkLauncher`
  还给了兜底顺序：App 深链 → 打开官方 App → 网页。
- [`qinyuanxin132/ling_QuickShortcut`](https://github.com/qinyuanxin132/ling_QuickShortcut)
  ——「一键生成快递身份码桌面快捷方式」，但它仓库里只有默认的 Compose 模板，没有实际逻辑，仅供佐证。
- 另外两份 URL Scheme 收集表（`JamesHopbourn/Apple-Automation`、NIKO798 的 gist）
  里的淘宝身份码还是 2022 年的老地址 `market.m.taobao.com/app/cn-yz/multi-activity/authCode.html`，
  已经过期——照它写就会打不开，所以最终以 PickTool 的地址为准。

## 改动

1. 入口改名：`菜鸟 / 出库码` → `身份码 / 淘宝·菜鸟驿站`。
2. 点击顺序（`openPickupCode`）：
   - 淘宝桥 `taobao://…h5Url=` → `tbopen://…h5Url=`（包名限定 `com.taobao.taobao`）；
   - 菜鸟裹裹自己的身份码页 `cainiao://desktop/station_code`（老客户端仍在，驿站也认）；
   - 菜鸟裹裹启动器快捷方式那条路由（`HomePageActivity` + `jumpPath`）；
   - 打开已装的淘宝并提示「我的 → 菜鸟驿站 → 身份码」；
   - 最后才用浏览器打开网页版，并提示可能需要登录；
   - 都没有才提示「没有找到淘宝或菜鸟裹裹」。
3. 地址与编码放进核心库 `PickupCodeLinks`（`taobaoBridges()` / `CAINIAO_IDENTITY`），
   在真实 JVM 上有断言：编码只保留 unreserved 字符、不出现 `+`（淘宝桥会把 `+` 当空格）、
   两个 scheme 只差前缀、`h5Url` 之外没有裸露的 `?`。
4. `tryStart` 多兜一种 `IllegalArgumentException`（畸形 URI 不该让点击崩掉）。

## 验证到哪一步

- 核心断言 **886 项通过**（1.4.9 是 876）。
- `aapt2` 读到 versionCode 145 / versionName 1.5.0，v2/v3 签名校验通过。
- 已核对 APK 内的 dex 确实带 `pages-fast.m.taobao.com`、`tbopen`、`end-collect-platform`。
- **没做到的**：仍无真机。淘宝的 `tbopen` 桥、以及这个 H5 在手机上的实际渲染，
  只能装上点一次。若打不开，请告诉我卡在哪一步（是直接跳到淘宝首页、提示没有淘宝、
  还是打开了网页），这三种对应三种不同的兜底分支。
