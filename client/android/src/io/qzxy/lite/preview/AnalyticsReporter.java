// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import io.qzxy.lite.core.AnalyticsDay;
import io.qzxy.lite.core.AnalyticsReply;
import io.qzxy.lite.core.AnalyticsRequest;

/**
 * 匿名每日活跃统计的上报器：一个安装，一天，最多一次。
 *
 * <p>它有三件事不做，这三件事比它做什么更重要：</p>
 *
 * <ul>
 *   <li><b>不打扰界面</b>：全部请求在后台线程，连不上就算了，从不弹窗、从不阻塞启动。
 *       失败也不循环重试——下一次进入前台再试一次，仅此而已。</li>
 *   <li><b>不假设成功</b>：只有服务端明确认可（200 且 {@code ok}）才把「今天报过了」写进本地；
 *       断网、限流、看不懂的应答都当作没报过。</li>
 *   <li><b>不越界</b>：请求体只有 {@code installId}（本地随机 UUID）与版本号；不读设备标识，
 *       不带任何业务数据；诊断日志只记状态码与判定，标识与完整请求内容都不进日志。</li>
 * </ul>
 *
 * <p>触发点是 {@link App} 的进程前台切换（第一个 Activity 被 started），不是每个页面的
 * {@code onResume}：一次回到前台只发生一次，页面之间来回切不会重复触发，「每个启动点各发一次」
 * 也就不存在了。</p>
 */
final class AnalyticsReporter {
    private static final int CONNECT_TIMEOUT_MS = 5000, READ_TIMEOUT_MS = 8000, MAX_REPLY_BYTES = 4096;
    /** 同一时刻只允许一个统计请求在飞：前台切换可以很快，服务端不需要看两次。 */
    private static final AtomicBoolean busy = new AtomicBoolean();

    private AnalyticsReporter() { }

    /** 编译期没给服务地址、或者这是设备测试包：一个请求都不发。 */
    static boolean usable() {
        return !BuildFlags.DEVICE_TESTS && AnalyticsPreferences.configured();
    }

    /**
     * 进入前台。顺序是「先补删除、再看要不要上报」：关闭统计之后留下的删除请求不完成，
     * 就不该再有任何新的上报。
     */
    static void onForeground(Context context) {
        final Context app = context.getApplicationContext();
        if (!usable()) {
            if (BuildFlags.DEVICE_TESTS) return;
            // 构建期没配服务地址：从来没有东西被上传过，待删除标记直接作废。
            if (AnalyticsPreferences.forgetWaiting(app)) AnalyticsPreferences.clearIdentity(app);
            return;
        }
        if (AnalyticsPreferences.forgetWaiting(app)) {
            background("analytics-forget", () -> forget(app));
            return;
        }
        if (!AnalyticsPreferences.dueToday(app)) return;
        background("analytics-report", () -> report(app));
    }

    /**
     * 读者拨动「匿名使用统计」开关。开启时当场生成标识并立刻报一次；关闭时停止上报，
     * 并把已经上传的记录删掉（删不掉就留着待删除标记，下次进前台继续删）。
     */
    static void setConsent(final Activity activity, final boolean enabled) {
        final Context app = activity.getApplicationContext();
        if (enabled) {
            if (!usable()) {
                toast(activity, "统计服务尚未配置，暂时无法开启");
                return;
            }
            AnalyticsPreferences.setConsented(app, true);
            AnalyticsPreferences.ensureInstallId(app);
            // 清掉上次的日期，让这一次开启当场生效，而不是等到明天。
            AnalyticsPreferences.setReportedDay(app, "");
            background("analytics-report", () -> report(app));
            return;
        }
        AnalyticsPreferences.setConsented(app, false);
        if (AnalyticsPreferences.installId(app) == null) {
            AnalyticsPreferences.clearIdentity(app);
            return;
        }
        AnalyticsPreferences.setForgetWaiting(app, true);
        background("analytics-forget", () -> {
            boolean removed = forget(app);
            toast(activity, removed ? "已关闭，历史匿名记录已删除" : "已关闭，历史记录会在联网后删除");
        });
    }

    /**
     * 请求服务端删掉这个安装的历史记录。阻塞，调用方负责放在后台线程。
     * 没有标识（从没同意过、或已经删干净）就返回 true——本来就没有可删的东西。
     */
    static boolean forget(Context context) {
        String id = AnalyticsPreferences.installId(context);
        if (id == null) {
            AnalyticsPreferences.clearIdentity(context);
            return true;
        }
        if (!usable()) return false;
        String trace = Diag.trace();
        long mark = Diag.mark();
        HttpURLConnection connection = null;
        try {
            connection = post(AnalyticsRequest.endpoint(BuildFlags.ANALYTICS_ENDPOINT,
                AnalyticsRequest.FORGET_PATH), AnalyticsRequest.forgetBody(id));
            int status = connection.getResponseCode();
            read(connection);
            Diag.event(status == 200 ? Diag.INFO : Diag.WARN, "ANALYTICS", "forget.done",
                "trace", trace, "http", status, "duration", Diag.since(mark));
            if (status != 200) return false;
            if (AnalyticsPreferences.consented(context)) {
                // 统计仍然开着，读者只是要求删掉历史：标识必须留着，否则统计会悄悄停掉。
                // 清掉日期，让今天从这个干净的起点重新记一次。
                AnalyticsPreferences.setForgetWaiting(context, false);
                AnalyticsPreferences.setReportedDay(context, "");
            } else {
                AnalyticsPreferences.clearIdentity(context);
            }
            return true;
        } catch (Exception failure) {
            Diag.failure("ANALYTICS", "forget.failed", Diag.reason(failure), failure, trace);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** 发这一次。只有服务端认可，才把日期写下来。 */
    private static void report(Context context) {
        String id = AnalyticsPreferences.installId(context);
        if (id == null || !AnalyticsPreferences.consented(context)) return;
        String version = versionName(context);
        if (!AnalyticsRequest.validVersion(version)) return;
        String trace = Diag.trace();
        long mark = Diag.mark();
        HttpURLConnection connection = null;
        try {
            String body = AnalyticsRequest.body(id, version);
            connection = post(AnalyticsRequest.endpoint(BuildFlags.ANALYTICS_ENDPOINT,
                AnalyticsRequest.ACTIVE_PATH), body);
            int status = connection.getResponseCode();
            String reply = read(connection);
            int verdict = AnalyticsReply.of(status, reply);
            if (AnalyticsReply.accepted(verdict)) {
                // 服务端确认过才算：断网、429、5xx 都不写，所以下次进前台还会再试。
                AnalyticsPreferences.setReportedDay(context, AnalyticsDay.today());
            }
            Diag.event(AnalyticsReply.retryable(verdict) ? Diag.WARN : Diag.INFO, "ANALYTICS",
                "report.done", "trace", trace, "http", status, "stage", stage(verdict),
                "duration", Diag.since(mark));
        } catch (Exception failure) {
            Diag.failure("ANALYTICS", "report.failed", Diag.reason(failure), failure, trace);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** 一行一个 POST，JSON，5 秒连接 / 8 秒读，不跟重定向。 */
    private static HttpURLConnection post(String address, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        return connection;
    }

    /** 应答读不完也不要紧：这里只想知道它是 200 还是别的，正文只在判定重复时看一眼。 */
    private static String read(HttpURLConnection connection) {
        InputStream input = null;
        try {
            input = connection.getResponseCode() >= 400 ? connection.getErrorStream()
                : connection.getInputStream();
            if (input == null) return "";
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int read;
            while ((read = input.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
                if (bytes.size() > MAX_REPLY_BYTES) break;
            }
            return bytes.toString("UTF-8");
        } catch (Exception unreadable) {
            return "";
        } finally {
            if (input != null) try { input.close(); } catch (Exception ignored) { }
        }
    }

    private static String stage(int verdict) {
        switch (verdict) {
            case AnalyticsReply.COUNTED: return "counted";
            case AnalyticsReply.DUPLICATE: return "duplicate";
            case AnalyticsReply.REFUSED: return "refused";
            default: return "retry";
        }
    }

    private static String versionName(Context context) {
        try {
            String name = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (Exception unknown) {
            return "";
        }
    }

    /** 一次请求一个短线程：没有连接池要维护，也不会在后台常驻一件事。 */
    private static void background(String name, Runnable task) {
        if (!busy.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try { task.run(); } catch (Throwable ignored) {
                // 统计坏了不能变成用户的问题：这里不抛、不重试、不写界面。
            } finally { busy.set(false); }
        }, name);
        worker.setDaemon(true);
        worker.start();
    }

    private static void toast(final Activity activity, final String message) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        });
    }
}
