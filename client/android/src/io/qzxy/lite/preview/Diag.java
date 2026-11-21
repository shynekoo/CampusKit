// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.webkit.WebView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.qzxy.lite.core.DiagEvent;
import io.qzxy.lite.core.DiagPolicy;
import io.qzxy.lite.core.DiagRedact;

/**
 * 诊断日志：回答「哪一环断了」，而不是把 Logcat 抄一遍。
 *
 * <p>一条记录长这样：</p>
 *
 * <pre>
 * 2026-11-19 21:30:04.018 ERROR [TIMETABLE] load.failed trace=7f2c1a90 thread=worker reason=NETWORK_TIMEOUT duration=2011
 *     at io.qzxy.lite.preview.EduClient.timetable(EduClient.java:801)
 * </pre>
 *
 * <p>看到它就够下结论：课表没出来，是因为网络超时，发生在请求阶段，耗时两秒。这正是自由拼接的
 * 「加载失败」给不出的信息。</p>
 *
 * <p>五条自我约束，改动时不要放松：</p>
 *
 * <ul>
 *   <li><b>默认不记录</b>：编进包里不等于开始记录。运行期默认关，只有使用者在诊断页明确点开
 *       才开始；关掉后连普通事件都不写。</li>
 *   <li><b>不联网、不上报</b>：写在应用私有目录，只有本机日志页读得到。要交出去，由使用者
 *       自己复制或导出。</li>
 *   <li><b>不记隐私</b>：字段走 {@link DiagRedact} 的白名单，没有自由文本字段。密码、Cookie、
 *       Token、学号、手机号、课程名都没有入口——不是「记下来再擦」，是「进不来」。</li>
 *   <li><b>不抛异常</b>：初始化失败、写盘失败、轮转失败都静默放弃。排查手段不能成为新的故障点。</li>
 *   <li><b>不改变业务</b>：所有调用点只读地记录，不参与分支判断，也不重试任何请求。</li>
 * </ul>
 *
 * <p>写文件而不是只留内存：卡死时使用者会杀掉进程再看，进程一死内存就空了，而那几行正是要留的。
 * 写入走单独线程，主线程只做一次入队；崩溃那条是唯一的同步写，因为有界且必须留下。</p>
 */
final class Diag {
    /** 发布版带基础诊断（用户主动开启），内部版另外带细节事件。 */
    static final boolean ON = BuildFlags.DIAG_BASIC || BuildFlags.DIAGNOSTICS;
    /** 只有 {@code --diagnostics} 的内部包才记录高密度事件（手势、渲染细节）。 */
    static final boolean DETAIL = BuildFlags.DIAGNOSTICS;

    static final int INFO = DiagEvent.INFO, WARN = DiagEvent.WARN;
    static final int ERROR = DiagEvent.ERROR, CRASH = DiagEvent.CRASH;

    private static final String PREFS = "diag";
    /** 新键：升级时旧版的 "on=true" 不被继承，正式包一律从关闭开始。 */
    private static final String KEY_MODE = "mode";
    private static final String MODE_ON = "on";

    /** 待写队列。满了就丢，并记一条「丢了多少」——日志不能反压业务线程。 */
    private static final int QUEUE = 512;
    private static final Object LOCK = new Object();
    private static final Random RANDOM = new Random();

    private static volatile DiagFiles files;
    private static volatile Context app;
    private static volatile boolean installed;
    private static volatile boolean closed;
    private static volatile Thread writer;
    private static volatile boolean snapshotDone;
    private static volatile ArrayBlockingQueue<String> queue;
    private static final AtomicInteger dropped = new AtomicInteger();
    /** 每次清空 +1：写线程手里可能还捏着清空前取出的那一批，代次变了就不能再写回去。 */
    private static final AtomicInteger generation = new AtomicInteger();

    private Diag() { }

    // ---- 开关 ---------------------------------------------------------------------------

    /** 编译期开关之外再叠一层运行期开关；默认关。 */
    static boolean enabled() {
        if (!ON) return false;
        Context context = app;
        if (context == null) return DiagPolicy.ENABLED_BY_DEFAULT;
        try {
            String mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, "");
            return DiagPolicy.defaultEnabled(mode);
        } catch (Throwable unknown) {
            return DiagPolicy.ENABLED_BY_DEFAULT;
        }
    }

    static void setEnabled(boolean value) {
        if (!ON) return;
        Context context = app;
        if (context == null) return;
        try {
            if (value) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_MODE, MODE_ON).apply();
                snapshot();
                event(INFO, "DIAG", "log.start");
            } else {
                // 先写「暂停」再关开关：这一行本身也是一条普通事件。
                event(INFO, "DIAG", "log.stop");
                flush();
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_MODE, "off").apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 开机只建文件、装崩溃兜底，不开记录。SDK 原文日志不再接：那段文字里逐字带着授权码与
     * 账户标识，而它想说明的事情（有没有回调、状态是几）用结构化事件就能说清。
     */
    static void install(Context context) {
        if (!ON) return;
        boolean first = false;
        synchronized (LOCK) {
            if (installed) return;
            installed = true;
            first = true;
            app = context.getApplicationContext();
            files = DiagFiles.open(context);
            if (files != null) {
                queue = new ArrayBlockingQueue<>(QUEUE);
                startWriter();
            }
        }
        captureCrash();
        if (first && enabled()) {
            snapshot();
            event(INFO, "DIAG", "log.start");
        }
    }

    // ---- 事件 ---------------------------------------------------------------------------

    /** 一串互不相同的随机标识，用来把一次操作里的几条记录串起来。不含任何用户信息。 */
    static String trace() {
        return String.format(Locale.US, "%08x", RANDOM.nextInt());
    }

    /** 计时起点：用纳秒差值算耗时，和系统时间被改动无关。 */
    static long mark() {
        return System.nanoTime();
    }

    /** 从 {@link #mark()} 到现在的毫秒数，直接当字段值用。 */
    static String since(long mark) {
        long ms = (System.nanoTime() - mark) / 1000000L;
        return Long.toString(Math.max(0, ms));
    }

    /**
     * 记一条结构化事件。字段按 {@code key, value, key, value} 传入，白名单之外的键会被丢掉，
     * 所以这里传错也不会泄漏，只是看不到了。
     *
     * <p>示例：{@code Diag.event(Diag.WARN, "NETWORK", "request.timeout", "duration", 2011)}</p>
     */
    static void event(int level, String module, String code, Object... pairs) {
        if (!ON || !installed || !enabled()) return;
        write(level, module, code, null, pairs);
    }

    /** 失败事件：原因用稳定代码，异常只留类型与安全堆栈。 */
    static void failure(String module, String code, String reason, Throwable cause, String trace) {
        if (!ON || !installed || !enabled()) return;
        write(ERROR, module, code, trace, new Object[] {
            "reason", reason,
            "error", cause == null ? "none" : cause.getClass().getSimpleName()
        });
        String stack = DiagRedact.stack(cause, DiagRedact.MAX_FRAMES);
        if (!stack.isEmpty()) enqueue(stack);
    }

    private static void write(int level, String module, String code, String trace, Object... pairs) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (pairs != null) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                if (pairs[i] == null || pairs[i + 1] == null) continue;
                fields.put(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
            }
        }
        // 关联 ID 不占一个字段位：调用方按 "trace", id 传进来，这里提到格式里的固定位置，
        // 免得同一件事有两种写法。
        if (trace == null) trace = fields.remove("trace");
        String thread = threadName(Thread.currentThread().getName());
        String line = DiagEvent.format(System.currentTimeMillis(), level, module, code, trace, thread, fields);
        enqueue(line);
    }

    private static void enqueue(String line) {
        if (!ON || !installed) return;
        ArrayBlockingQueue<String> target = queue;
        if (target == null) return;
        if (!target.offer(line)) dropped.incrementAndGet();
    }

    // ---- 写线程 -------------------------------------------------------------------------

    private static void startWriter() {
        if (writer != null) return;
        Thread thread = new Thread(() -> {
            StringBuilder batch = new StringBuilder(2048);
            while (!closed) {
                try {
                    String first = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (first == null) continue;
                    // 取第一批在锁外，避免空转时把 clear() 挡住 500ms；真正写盘与 flush()
                    // 用同一把锁，保证「先入队的先落盘」。
                    synchronized (LOCK) {
                        int gen = generation.get();
                        batch.setLength(0);
                        batch.append(first).append('\n');
                        String next;
                        int count = 0;
                        while (count++ < 64 && (next = queue.poll()) != null) batch.append(next).append('\n');
                        if (gen != generation.get()) continue;   // 这批是清空前的，丢掉
                        int lost = dropped.getAndSet(0);
                        DiagFiles sink = files;
                        if (sink != null) {
                            if (lost > 0) {
                                Map<String, String> fields = new LinkedHashMap<>();
                                fields.put("count", Integer.toString(lost));
                                batch.insert(0, DiagEvent.format(System.currentTimeMillis(), WARN,
                                    "DIAG", "log.lost", null, "worker", fields) + "\n");
                            }
                            sink.append(batch.toString());
                        }
                    }
                } catch (InterruptedException stop) {
                    return;
                } catch (Throwable ignored) {
                    // 写盘失败不重试、不抛出：丢一条记录比拖垮界面好。
                }
            }
        }, "diag-write");
        thread.setDaemon(true);
        thread.start();
        writer = thread;
    }

    /** 把队列里还没写的行直接写掉；读页面、导出、关开关之前调用。 */
    private static void flush() {
        ArrayBlockingQueue<String> target = queue;
        DiagFiles sink = files;
        if (target == null || sink == null) return;
        synchronized (LOCK) {
            int gen = generation.get();
            StringBuilder batch = new StringBuilder();
            String line;
            while ((line = target.poll()) != null) batch.append(line).append('\n');
            if (batch.length() > 0 && gen == generation.get()) sink.append(batch.toString());
        }
    }

    // ---- 崩溃 ---------------------------------------------------------------------------

    /**
     * 未捕获异常落盘：崩溃之后进程说没就没，而那一刻正是最需要留下堆栈的时候。这里只同步写
     * 一条有界记录，然后把异常照原样交回给系统默认处理器，不改崩溃行为。
     */
    private static void captureCrash() {
        try {
            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                try {
                    if (enabled()) crash(thread, error);
                } catch (Throwable ignored) {
                    // 记录失败也不能挡住系统处理崩溃。
                }
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                } else {
                    Process.killProcess(Process.myPid());
                }
            });
        } catch (Throwable unavailable) {
            event(WARN, "DIAG", "crash.hook.failed", "error", unavailable.getClass().getSimpleName());
        }
    }

    private static void crash(Thread thread, Throwable error) {
        DiagFiles sink = files;
        if (sink == null || error == null) return;
        String trace = trace();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("error", error.getClass().getSimpleName());
        StringBuilder block = new StringBuilder(DiagEvent.format(System.currentTimeMillis(), CRASH,
            "APP", "crash", trace, threadName(thread == null ? "" : thread.getName()), fields));
        String stack = DiagRedact.stack(error, DiagRedact.MAX_FRAMES);
        if (!stack.isEmpty()) block.append('\n').append(stack);
        sink.append(block.toString());   // 同步、有界，绕过队列
    }

    // ---- 读 -----------------------------------------------------------------------------

    /** 一条记录 + 它后面的堆栈续行。 */
    static final class Entry {
        final DiagEvent event;
        private final StringBuilder detail = new StringBuilder();

        Entry(DiagEvent event) { this.event = event; }

        void append(String line) {
            if (detail.length() > 1200) return;
            if (detail.length() > 0) detail.append('\n');
            detail.append(line);
        }

        /** 缩进续行（堆栈、明细），没有就返回空串。 */
        String detail() { return detail.toString(); }
    }

    /** 最近 {@code limit} 条；{@code limit <= 0} 表示全部。 */
    static List<Entry> records(int limit) {
        List<Entry> all = new ArrayList<>();
        if (!ON) return all;
        flush();
        DiagFiles sink = files;
        if (sink == null) return all;
        Entry current = null;
        for (String line : sink.read().split("\n")) {
            DiagEvent event = DiagEvent.parse(line);
            if (event != null) {
                current = new Entry(event);
                all.add(current);
            } else if (current != null && !line.trim().isEmpty()) {
                current.append(line.trim());
            }
        }
        if (limit > 0 && all.size() > limit) return new ArrayList<>(all.subList(all.size() - limit, all.size()));
        return all;
    }

    /** 导出用纯文本：带一段说明，粘到聊天里也知道这是什么、从哪来的。 */
    static String text() {
        if (!ON) return "";
        flush();
        DiagFiles sink = files;
        if (sink == null) return "";
        StringBuilder out = new StringBuilder();
        out.append("科信小助手 诊断日志").append('\n')
            .append("版本 ").append(versionName()).append(" (").append(versionCode()).append(")").append('\n')
            .append("导出时间 ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n')
            .append("仅保存在本机，可能包含设备环境与错误信息，请确认内容后再分享。").append('\n')
            .append("────────────").append('\n');
        String body = sink.read();
        out.append(body == null || body.isEmpty() ? "还没有记录。" : body);
        return out.toString();
    }

    static long bytes() {
        DiagFiles sink = files;
        return sink == null ? 0 : sink.bytes();
    }

    /** 清空：先清队列再清文件，避免刚清完又把内存里的旧行写回去。 */
    static void clear() {
        if (!ON) return;
        synchronized (LOCK) {
            generation.incrementAndGet();
            ArrayBlockingQueue<String> target = queue;
            if (target != null) target.clear();
            dropped.set(0);
            DiagFiles sink = files;
            if (sink != null) sink.clear();
        }
    }

    // ---- 环境快照 -----------------------------------------------------------------------

    /** 每次进程启动记一次：机型、系统、空间号、WebView 版本。这些正是「换个手机就复现」时要问的。 */
    private static void snapshot() {
        if (snapshotDone || !installed) return;
        snapshotDone = true;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("version", versionName());
        fields.put("code", Long.toString(versionCode()));
        fields.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        fields.put("sdk", Integer.toString(Build.VERSION.SDK_INT));
        fields.put("slot", Integer.toString(Process.myUid() / 100000));
        fields.put("webview", webView());
        String thread = threadName(Thread.currentThread().getName());
        enqueue(DiagEvent.format(System.currentTimeMillis(), INFO, "APP", "session.start",
            null, thread, fields));
    }

    private static String webView() {
        if (Build.VERSION.SDK_INT < 26) return "old";
        try {
            PackageInfo info = WebView.getCurrentWebViewPackage();
            return info == null ? "unknown" : info.versionName.replace(' ', '-');
        } catch (Throwable unknown) {
            return "unreadable";
        }
    }

    private static String versionName() {
        Context context = app;
        if (context == null) return "?";
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable unknown) {
            return "?";
        }
    }

    private static long versionCode() {
        Context context = app;
        if (context == null) return 0;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Throwable unknown) {
            return 0;
        }
    }

    /**
     * 线程名压短：排查只关心「主线程还是后台」。中文线程名会让字段形状不合，所以这里统一
     * 换成 ASCII 标记。
     */
    private static String threadName(String name) {
        if (name == null || name.isEmpty()) return "?";
        if ("main".equals(name)) return "main";
        if (name.startsWith("pool") || name.startsWith("Thread-") || name.startsWith("update-")
            || name.startsWith("diag-") || name.startsWith("OkHttp")) return "worker";
        String text = name.replaceAll("[^A-Za-z0-9_.-]", "");
        if (text.isEmpty()) return "worker";
        return text.length() > 16 ? text.substring(0, 16) : text;
    }

    /** 失败原因分类，调用点不必自己 import core。 */
    static String reason(Throwable error) {
        return DiagRedact.reason(error);
    }

}
