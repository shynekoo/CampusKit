package io.qzxy.lite.core;

/**
 * 事件代码的中文说法。代码是稳定的（{@code load.failed}），说法可以随时改文案——
 * 界面、故障报告和文档都从这里取词，不会再出现「同一个事件三种叫法」。
 *
 * <p>找不到的代码原样显示：那说明有人加了事件却没登记，界面上就该看得出来，而不是被兜底
 * 成一个笼统的「出了问题」。</p>
 */
public final class DiagCatalog {
    private DiagCatalog() { }

    public static String module(String code) {
        if (code == null) return "";
        switch (code) {
            case "APP": return "应用";
            case "EDU": return "教务";
            case "EVAL": return "学生评价";
            case "TIMETABLE": return "课表";
            case "NETWORK": return "网络";
            case "LIFE": return "慧生活";
            case "QZXY": return "趣智校园";
            case "AUTH": return "支付宝";
            case "UPDATE": return "更新";
            case "UI": return "界面";
            case "DIAG": return "诊断";
            default: return code;
        }
    }

    /** 稳定的模块代码，供筛选按模块聚合。 */
    public static final String[] MODULES = {"EDU", "EVAL", "TIMETABLE", "NETWORK", "LIFE", "QZXY", "UPDATE", "UI"};

    public static String title(String module, String code) {
        if (code == null) return "";
        switch (code) {
            case "session.start": return "进程启动";
            case "log.start": return "开始记录";
            case "log.stop": return "暂停记录";
            case "log.cleared": return "日志已清空";
            case "log.rotate": return "日志已轮转";
            case "log.write.failed": return "日志写入失败";
            case "log.lost": return "有记录未能写入";

            case "login.start": return "开始登录";
            case "login.success": return "登录成功，已建立会话";
            case "login.failed": return "登录失败";
            case "login.stage": return "登录进行到下一步";
            case "auth.start": return "开始唤起支付宝";
            case "auth.returned": return "支付宝已返回";
            case "auth.failed": return "支付宝授权失败";

            case "sheet.start": return "开始读取评价表单";
            case "sheet.ready": return "评价表单已读取";
            case "sheet.empty": return "评价表单没有评价项";
            case "sheet.failed": return "评价表单没能读取";
            case "sheet.unsupported": return "评价表单里有不支持的题型";
            case "sheet.shape": return "评价表单换了一种请求形态";
            case "write.sent": return "评价已提交";
            case "write.saved": return "评价已暂存";
            case "write.failed": return "学校没有接受这次评价";

            case "load.start": return "开始获取课表数据";
            case "load.success": return "课表已加载";
            case "load.failed": return "加载失败";
            case "load.empty": return "教务没有返回课表数据";
            case "parse.failed": return "课表数据解析失败";
            case "parse.success": return "课表解析完成";
            case "import.saved": return "课表已写入本机";
            case "store.unreadable": return "本机课表读取失败";

            case "request.start": return "开始请求";
            case "request.end": return "请求返回";
            case "request.timeout": return "网络请求超时";
            case "request.failed": return "请求失败";
            case "request.rejected": return "服务端拒绝请求";
            case "response.parse.failed": return "响应解析失败";

            case "sdk.call": return "SDK 调用";
            case "sdk.returned": return "SDK 已返回";
            case "sdk.failed": return "SDK 调用失败";
            case "sdk.log": return "SDK 日志";

            case "code.generate": return "领取使用码";
            case "code.failed": return "使用码操作失败";

            case "check.start": return "开始检查更新";
            case "check.failed": return "检查更新失败";
            case "check.available": return "发现新版本";
            case "check.stale": return "更新检查已作废";
            case "download.failed": return "更新包下载失败";
            case "verify.failed": return "更新包校验失败";
            case "install.start": return "开始安装更新";

            case "nav.open": return "打开页面";
            case "back.gesture": return "返回手势";
            case "render.failed": return "界面渲染失败";
            case "render.done": return "界面已渲染";
            case "crash": return "应用崩溃";
            default: return code;
        }
    }

    public static String reason(String code) {
        if (code == null || code.isEmpty()) return "";
        switch (code) {
            case "NETWORK_TIMEOUT": return "网络请求超时";
            case "NETWORK_CONNECT": return "连接不上服务器";
            case "NETWORK": return "网络异常";
            case "DNS": return "域名解析失败";
            case "TLS": return "HTTPS 证书或加密失败";
            case "HTTP_STATUS": return "服务器返回错误状态";
            case "SERVER_REJECTED": return "服务器拒绝了请求";
            case "PARSE": return "返回数据无法解析";
            case "EMPTY": return "没有返回数据";
            case "SESSION_EXPIRED": return "登录状态已失效";
            case "AUTH_REJECTED": return "账号或密码被拒绝";
            case "CAPTCHA": return "学校要求输入验证码";
            case "AUTH_CANCELLED": return "授权未完成";
            case "CRYPTO": return "加密或解密失败";
            case "INVALID_INPUT": return "输入不合法";
            case "STORE_UNREADABLE": return "本机数据无法读取";
            case "NO_CREDENTIALS": return "没有保存的账号";
            case "WRITE_FAILED": return "写入失败";
            case "VERIFY": return "安装包校验未通过";
            case "UNKNOWN": return "未知原因";
            default: return code;
        }
    }

    /**
     * 列表里那一行标题：事件说法 + 失败原因。两者都有时用「：」连接，例如
     * 「加载失败：网络请求超时」——这正是「课表为什么没出来」的答案。
     */
    public static String summary(DiagEvent event) {
        if (event == null) return "";
        String title = title(event.module, event.code);
        String reason = reason(event.field("reason"));
        if (reason.isEmpty() || title.contains(reason)) return title;
        return title + "：" + reason;
    }
}
