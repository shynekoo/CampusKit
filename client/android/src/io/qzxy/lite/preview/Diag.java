package io.qzxy.lite.preview;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 运行记录：只在测试版存在，回答「哪一环断了」这一个问题。
 *
 * <p>它记的不是全量日志，而是能自己算结论的字段：一次开机的机器快照（机型、ROM、空间号、
 * 支付宝版本、WebView 版本、时区），加上授权与登录两步的时间点和返回码。看日志的人不需要
 * 知道代码在哪，只要看最后一行停在哪，就知道断在唤起、回调、还是服务端。</p>
 *
 * <p>三条自我约束。**不联网、不上报**：写进应用私有目录，只有本机这个页面读得到，交出去靠
 * 使用者自己复制。**不记隐私**：手机号、token、授权码原文、设备标识一律不进文件，授权码只留
 * 前六位用于对齐服务器日志。**不抛异常**：日志写失败就静默放弃，绝不能让排查手段本身成为
 * 新的故障点。</p>
 *
 * <p>写在文件而不是内存里是有原因的：卡死时使用者会杀掉进程再看日志，进程一死，内存里的记录
 * 就没了——而那正是最需要留下的几行。文件保留上一次会话的尾部，加上一条「进程启动」分隔线，
 * 断在哪一行一眼可见。</p>
 */
final class Diag {
    /** 编译期开关：只读发布版里这些分支会被 javac 删掉，类本身也不承载任何控制路由。 */
    static final boolean ON = BuildFlags.DIAGNOSTICS;

    private static final String CURRENT = "diag.log";
    private static final String PREVIOUS = "diag.1.log";
    /** 一个文件装两三次登录的记录绰绰有余；再多就转存成上一份。 */
    private static final long MAX_BYTES = 96 * 1024L;
    private static final int MAX_READ = 256 * 1024;
    private static final int MAX_TEXT = 60000;

    private static final Object LOCK = new Object();
    private static File file;
    private static boolean installed;

    private Diag() { }

    /**
     * 开机记一次快照，并把支付宝 SDK 的内部日志接过来。重复调用无副作用：多个入口都会叫它，
     * 谁先到谁初始化。
     */
    static void install(Context context) {
        if (!ON) return;
        synchronized (LOCK) {
            if (installed) return;
            installed = true;
            try {
                File dir = new File(context.getFilesDir(), "diag");
                if (!dir.isDirectory() && !dir.mkdirs()) return;
                File current = new File(dir, CURRENT);
                File previous = new File(dir, PREVIOUS);
                if (current.length() > MAX_BYTES) {
                    if (previous.exists()) previous.delete();
                    current.renameTo(previous);
                }
                file = current;
                write(snapshot(context));
            } catch (Throwable ignored) {
                // A log that cannot be written is not a reason to fail a login.
            }
        }
        captureSdk();
    }

    /** 应用自己的事件：一句话说清发生了什么，不带变量名和堆栈。 */
    static void step(String area, String detail) {
        if (!ON || !installed) return;
        append(clock() + " [" + thread() + "] " + area + " " + detail);
    }

    /** 写文件，调用方负责内容已经脱敏。 */
    static void raw(String line) {
        if (!ON || !installed) return;
        append(line);
    }

    /** 全部记录，最新在最后。超过上限时保留尾部——排查要的正是最后几步。 */
    static String dump() {
        if (!ON) return "";
        synchronized (LOCK) {
            if (file == null || !file.isFile()) return "还没有记录。";
            try (FileInputStream input = new FileInputStream(file)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, read);
                    if (bytes.size() > MAX_READ) break;
                }
                String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                if (text.length() <= MAX_TEXT) return text;
                return "…（前段已省略）\n" + text.substring(text.length() - MAX_TEXT);
            } catch (Throwable unreadable) {
                return "日志读取失败。";
            }
        }
    }

    /** 复制出去之后清空，免得下一次排查时旧记录混在新记录前面。 */
    static void clear() {
        if (!ON) return;
        synchronized (LOCK) {
            if (file == null) return;
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(new byte[0]);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 授权码只留前六位：够和服务端日志对齐，又不构成可重放的凭据。 */
    static String maskCode(String code) {
        if (code == null) return "null";
        if (code.length() <= 6) return code + "(len=" + code.length() + ")";
        return code.substring(0, 6) + "…(len=" + code.length() + ")";
    }

    /**
     * 把 SDK 返回的 a=b&amp;c=d 里的 auth_code 摘掉值。整串照抄会把一次有效授权写进日志文件，
     * 而这个文件是要被复制、发出去的。
     */
    static String maskChain(String chain) {
        if (chain == null) return "null";
        // 替换串里没法按捕获组做截断，手工拆一遍比写两条正则更清楚。
        StringBuilder out = new StringBuilder();
        String[] pairs = chain.split("&");
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            if (pair.startsWith("auth_code=")) {
                out.append("auth_code=").append(maskCode(pair.substring("auth_code=".length())));
            } else {
                out.append(pair);
            }
            if (i < pairs.length - 1) out.append('&');
        }
        String text = out.length() == 0 ? "空" : out.toString();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    private static void captureSdk() {
        try {
            com.alipay.sdk.interior.Log.setupLogCallback(line -> step("sdk", line));
        } catch (Throwable unavailable) {
            // 老版本 SDK 没有这个入口，应用自己的事件仍然有效。
            step("sdk", "无法接入 SDK 日志：" + unavailable.getClass().getSimpleName());
        }
    }

    private static String snapshot(Context context) {
        StringBuilder text = new StringBuilder();
        text.append("=== 进程启动 ").append(fullClock()).append(" ===\n");
        text.append("App ").append(name(context)).append(" (").append(code(context)).append(")\n");
        text.append("机型 ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        text.append("系统 Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n');
        text.append("ROM ").append(Build.DISPLAY).append('\n');
        text.append("空间 ").append(space()).append('\n');
        text.append("时区 ").append(TimeZone.getDefault().getID()).append('\n');
        text.append("支付宝 ").append(alipay(context)).append('\n');
        text.append("WebView ").append(webView()).append('\n');
        return text.toString();
    }

    /**
     * Android 的应用双开/手机分身跑在另一个用户空间里，UID 的十万位就是空间号。双开环境下的
     * 跨空间 IPC 会被系统隔断，支付宝回调因此送不回来——这一行是判断该原因的直接证据。
     */
    private static String space() {
        try {
            int slot = Process.myUid() / 100000;
            return slot == 0 ? "主空间" : "空间 " + slot + "（可能是双开或手机分身）";
        } catch (Throwable unknown) {
            return "未知";
        }
    }

    private static String alipay(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo("com.eg.android.AlipayGphone", 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " (" + code + ")";
        } catch (PackageManager.NameNotFoundException absent) {
            return "未安装";
        } catch (Throwable unknown) {
            return "无法读取";
        }
    }

    /** API 26 起可以直接问系统当前 WebView 是谁；更早的系统读不到，也不值得为它加一条 queries。 */
    private static String webView() {
        if (Build.VERSION.SDK_INT < 26) return "Android " + Build.VERSION.RELEASE + "（旧版本读不到）";
        try {
            PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info == null) return "未知";
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " (" + code + ")";
        } catch (Throwable unknown) {
            return "无法读取";
        }
    }

    private static String name(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable unknown) {
            return "?";
        }
    }

    private static long code(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Throwable unknown) {
            return 0;
        }
    }

    private static void append(String line) {
        synchronized (LOCK) {
            write(line);
        }
    }

    private static void write(String text) {
        if (file == null || text == null) return;
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.write('\n');
        } catch (Throwable ignored) {
            // 写不进去就放弃，不让日志影响任何正常流程。
        }
    }

    private static String clock() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String fullClock() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    /** 线程名压短：排查时只关心「主线程还是后台」，pool-1-thread-3 这种名字没有信息量。 */
    private static String thread() {
        String name = Thread.currentThread().getName();
        if (name == null || name.isEmpty()) return "?";
        if ("main".equals(name)) return "主";
        if (name.startsWith("pool") || name.startsWith("Thread-")) return "后台";
        return name.length() > 12 ? name.substring(0, 12) : name;
    }
}
