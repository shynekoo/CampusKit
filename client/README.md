# 客户端代码

`android/` 为独立原生 Android 客户端，包含短信登录、账号验证、余额查询、相机/图片扫码与只读设备资料查询；`core/` 是不依赖 Android 的会话、二维码协议计算及用水状态模块。真实用水的分阶段实现和现场验收门槛见 `../analysis/implementation-roadmap.md`。

## 当前实现

- `NativeSigningV6528`：根据用户提供的 6.5.28 APK 原生函数，独立重建两轮 MD5 签名变换，正确处理 JNI modified UTF-8。接收已经串行化的字段文本；调用方的选字段及排序逻辑尚未恢复，不自动猜测。
- `DeviceProfile`：`device/info/mac` 返回的严格离线模型。校验校园与设备标识，解析设备类型（4=浴室）、计费方式、预扣金额与可用状态，判定是否可进入用水验证；未知字段不展示，设备编号只报“已识别”。传输类型与在线标志只按原始值展示，含义待现场确认。
- `UseSession`：区分设备就绪、待开启确认、用水中、待停止确认、结算中、完成和待核实。重复或不合状态的操作会被拒绝；发出命令后断连或恢复进入待核实状态。
- `AccountSession` 保存用户标识、登录凭据和校园编号；`LoginPersistence` 在取消补全登录时恢复原会话。`LoginCoordinator` 为独立测试模块，Activity 未调用。
- `SessionCipher` / `AccountVault`：Android Keystore AES-GCM 和私有不备份文件，支持旧两字段会话升级。已完成 Android 离线验证和 0.3 真机跨升级会话读取。
- `SmsAccountApi` / `AccountProfile` / `DeviceDetails`：固定登录、余额与只读设备资料查询路由，金额按千分之一元换算；未嵌入官方 SDK。当前仅申请 INTERNET 与 CAMERA 权限，不申请蓝牙权限。
- `QrDecoder`：本机 ZXing 解码，静态图片走尺度金字塔（1.0→0.125，含 0.2/0.16 有效区间），相机帧对 Y 平面做 1/2、1/4、1/6 降采样；启用反色与 TRY_HARDER。真实浴室实体标签只有在降采样后才能解出，屏幕码仍在第一次尝试即命中。

真实账号与余额已有 0.3 真机截图证据；真实浴室二维码已离线解码并通过 `DeviceQr` 回归测试，但尚未在线查询该水表资料或执行真实用水。当前复核与测试见 `analysis/project-review.md`，后续流程见 `analysis/implementation-roadmap.md`。

## 构建

在项目根目录运行，需可用的完整 JDK（当前使用 JDK 24.0.2，生成 Java 8 字节码）。

```powershell
python scripts/bootstrap_android.py
python scripts/test_core.py
python scripts/build_preview.py
```

SDK 仅下载到 `tools/android-sdk`，版本为平台 API 36 与 Build-Tools 36.0.0；校验来源记录在该目录下的 `sources.json`。构建直接调用官方 AAPT2、D8、zipalign 和 apksigner，不依赖全局 Android Studio 或 Gradle。

构建脚本先运行核心测试，再按 Manifest 版本生成 delivery 内的 APK 与源码哈希报告。本地开发签名文件位于忽略提交的 `tools/keys/`。

## 接入前尚需确认

1. 设备请求的认证参数及字段串行化规则；短信登录已单独验证。
2. 用户学校二维码格式，以及实际使用的设备通信方式。
3. 设备确认、服务端订单和结算响应之间的对应关系。
4. 断连与进程恢复时的订单核实方式。

预览中的延时只用于展示页面。未来真实适配器必须根据设备／服务端确认推进状态，不能复用演示定时器作为实际操作成功的依据。
