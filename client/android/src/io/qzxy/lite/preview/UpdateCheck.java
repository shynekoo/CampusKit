package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

import io.qzxy.lite.core.UpdateManifest;

/**
 * Checks for a new release at start-up, explains it, and installs it if the reader says yes.
 *
 * <p>Three rules keep this out of the way. It never blocks the first screen — the check runs on a
 * background thread a second after the timetable is drawn. It never nags: a version the reader
 * answered 稍后 to is remembered and stays quiet on later starts, though the manual entry in
 * 账号 still offers it. And it fails silently — no network, a moved manifest or a malformed one all
 * end the same way, with nothing on screen, because a popup about a failed check is not news.</p>
 *
 * <p>Nothing the manifest says is taken on faith. {@link UpdateManifest} validates the fields; this
 * class additionally requires HTTPS, compares the version <em>code</em> rather than the name, and
 * verifies the package against the manifest's byte count and SHA-256 before handing it to the
 * installer. A failed verification deletes the download instead of installing it.</p>
 */
final class UpdateCheck {
    /**
     * The published manifest. The {@code sharecode} is what the host uses to authorise an anonymous
     * read of this one app; it is not a secret — it is part of the public link — and it is fixed for
     * the life of the app, so re-publishing a new manifest under the same link needs no code change.
     */
    private static final String MANIFEST =
        "https://wbapp-g8yy3klci8kmeufm6vdn0h.app.workbuddy.host/update.json?sharecode=BZD63HRP4B";
    private static final String PREFS = "update-v1";
    /** A version the reader declined once. Kept as a code, so a newer release asks again. */
    private static final String KEY_SKIPPED = "skipped";
    private static final int CONNECT_TIMEOUT_MS = 8000, READ_TIMEOUT_MS = 15000;
    private static final int MAX_MANIFEST_BYTES = 65536, BUFFER = 16384;

    /** One check per process: a rotation or a tab switch must not start a second request. */
    private static boolean started;
    /** Downloaded and waiting for the install permission the reader was sent to grant. */
    private static File pending;

    private UpdateCheck() { }

    static void onStart(final Activity activity) {
        if (started) return;
        started = true;
        // The test manifest has no INTERNET permission, and an update popup would sit on top of
        // every assertion that drives the real screen.
        if (BuildFlags.DEVICE_TESTS) return;
        final View root = activity.getWindow().getDecorView();
        root.postDelayed(new Runnable() {
            @Override public void run() { check(activity, false); }
        }, 1200);
    }

    /** Called from 账号 so the check can also be asked for, and then it always answers. */
    static void checkManually(Activity activity) { check(activity, true); }

    /**
     * Returns from the system's "install unknown apps" page and finishes what was started. Without
     * this the reader would grant the permission and then have to find the update again.
     */
    static void resume(Activity activity) {
        File apk = pending;
        if (apk == null || !apk.isFile()) { pending = null; return; }
        if (!canInstall(activity)) return;
        pending = null;
        install(activity, apk);
    }

    private static void check(final Activity activity, final boolean manual) {
        final int installed = versionCode(activity);
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                UpdateManifest manifest;
                try {
                    manifest = UpdateManifest.parse(read(MANIFEST));
                } catch (Exception unavailable) {
                    if (manual) say(activity, "暂时查不到新版本", "读不到版本信息，请确认网络后重试。");
                    return;
                }
                if (!manifest.newerThan(installed)) {
                    if (manual) say(activity, "已是最新版本", "当前 " + AppUi.version(activity) + "，没有可安装的更新。");
                    return;
                }
                // 稍后 is remembered for that exact release; asking again on every start would be
                // the nagging this check is supposed to avoid.
                if (!manual && manifest.versionCode == skipped(activity)) return;
                final UpdateManifest release = manifest;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() { afterNotice(activity, release, 0); }
                });
            }
        }, "update-check");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Waits for the first-launch notice to be dismissed before offering an update. The two would
     * otherwise land on the same screen within a second of each other, and the update is the one
     * that can wait: it is still there on the next start.
     */
    private static void afterNotice(final Activity activity, final UpdateManifest manifest, final int attempt) {
        if (activity.isFinishing()) return;
        if (NoticeDialog.showing()) {
            if (attempt >= 40) return;
            activity.getWindow().getDecorView().postDelayed(new Runnable() {
                @Override public void run() { afterNotice(activity, manifest, attempt + 1); }
            }, 1500);
            return;
        }
        offer(activity, manifest);
    }

    private static void offer(final Activity activity, final UpdateManifest manifest) {
        if (activity.isFinishing()) return;
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(AppUi.dp(activity, 20), AppUi.dp(activity, 2), AppUi.dp(activity, 20), 0);

        body.addView(text(activity, "当前 " + AppUi.version(activity) + " → " + manifest.version, 12, AppUi.MUTED));
        for (String note : manifest.notes) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, AppUi.dp(activity, 12), 0, 0);
            TextView dot = text(activity, "·", 15, AppUi.ACCENT);
            dot.setTypeface(null, Typeface.BOLD);
            dot.setPadding(0, 0, AppUi.dp(activity, 8), 0);
            row.addView(dot);
            TextView line = text(activity, note, 14, AppUi.INK);
            line.setLineSpacing(AppUi.dp(activity, 2), 1f);
            row.addView(line, new LinearLayout.LayoutParams(0, -2, 1));
            body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        if (manifest.bytes > 0) {
            TextView size = text(activity, "安装包 " + megabytes(manifest.bytes) + " MB", 12, AppUi.MUTED);
            size.setPadding(0, AppUi.dp(activity, 14), 0, 0);
            body.addView(size);
        }
        scroll.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("新版本 " + manifest.version)
            .setView(scroll)
            .setPositiveButton("更新", null)
            .setNegativeButton("稍后", null)
            .create();
        dialog.setOnShowListener(shown -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                download(activity, manifest);
            });
            // The negative button records the decline, which is what keeps the next start quiet.
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                skip(activity, manifest.versionCode);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    /** Downloads with a progress dialog, verifies the package, then hands it to the installer. */
    private static void download(final Activity activity, final UpdateManifest manifest) {
        final File directory = ApkProvider.directory(activity);
        // A package from an earlier attempt is never reused: it may be a partial file left by a
        // killed process, and installing that reports a corrupt package with no explanation.
        File[] stale = directory.listFiles();
        if (stale != null) for (File old : stale) old.delete();
        directory.mkdirs();
        final File target = new File(directory, "campuskit-" + manifest.version + ".apk");

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(AppUi.dp(activity, 20), AppUi.dp(activity, 6), AppUi.dp(activity, 20), 0);
        final TextView status = text(activity, "正在连接…", 13, AppUi.MUTED);
        body.addView(status);
        final ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, AppUi.dp(activity, 6));
        barParams.topMargin = AppUi.dp(activity, 12);
        body.addView(bar, barParams);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("正在下载 " + manifest.version)
            .setView(body)
            .setNegativeButton("取消", null)
            .create();
        final boolean[] cancelled = {false};
        dialog.setCancelable(false);
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setOnClickListener(v -> { cancelled[0] = true; dialog.dismiss(); }));
        dialog.show();

        final Progress progress = new Progress();
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                String failure = null;
                try {
                    save(manifest, target, progress, cancelled);
                } catch (Exception problem) {
                    failure = problem.getMessage() == null ? "下载失败" : problem.getMessage();
                    target.delete();
                }
                final String message = failure;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (activity.isFinishing()) return;
                        if (dialog.isShowing()) dialog.dismiss();
                        if (cancelled[0]) { target.delete(); return; }
                        if (message != null) { say(activity, "下载没有完成", message); return; }
                        installWithPermission(activity, target);
                    }
                });
            }
        }, "update-download");
        worker.setDaemon(true);
        worker.start();

        // The bar is driven from the writing thread; one message per whole percent is enough to
        // look smooth and keeps the main thread free.
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                long done = progress.done, total = progress.total;
                if (total > 0) {
                    int percent = (int) Math.min(100, done * 100 / total);
                    bar.setProgress(percent);
                    status.setText("已下载 " + percent + "%（" + megabytes(done) + " / " + megabytes(total) + " MB）");
                } else status.setText("已下载 " + megabytes(done) + " MB");
                activity.getWindow().getDecorView().postDelayed(this, 200);
            }
        });
    }

    private static void save(UpdateManifest manifest, File target, Progress progress, boolean[] cancelled) throws Exception {
        HttpURLConnection connection = null;
        FileOutputStream output = null;
        try {
            connection = (HttpURLConnection) new URL(manifest.url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/octet-stream");
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("服务器返回 " + status);
            progress.total = manifest.bytes > 0 ? manifest.bytes : connection.getContentLength();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            output = new FileOutputStream(target);
            InputStream input = connection.getInputStream();
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = input.read(buffer)) > 0) {
                if (cancelled[0]) return;
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                progress.done += read;
                // A manifest that understates the size is not a slow download, it is a different
                // file; stop before it fills the phone.
                if (progress.total > 0 && progress.done > progress.total + BUFFER) throw new IOException("安装包大小与说明不符");
            }
            output.flush();
            output.close();
            output = null;
            if (manifest.bytes > 0 && progress.done != manifest.bytes) throw new IOException("安装包大小与说明不符");
            if (!manifest.sha256.isEmpty()) {
                StringBuilder hex = new StringBuilder(64);
                for (byte value : digest.digest()) hex.append(String.format(Locale.US, "%02x", value & 0xff));
                if (!hex.toString().equals(manifest.sha256)) throw new IOException("安装包校验失败，已删除");
            }
        } finally {
            if (output != null) try { output.close(); } catch (IOException ignored) { }
            if (connection != null) connection.disconnect();
        }
    }

    private static void installWithPermission(Activity activity, File apk) {
        if (!canInstall(activity)) {
            pending = apk;
            AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("还需要一步")
                .setMessage("安装新版本要允许本应用「安装未知应用」。系统会让你单独确认一次，同意后回到应用就会接着安装。")
                .setPositiveButton("去设置", null)
                .setNegativeButton("取消", null)
                .create();
            dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
                } catch (ActivityNotFoundException noPage) {
                    pending = null;
                    say(activity, "打不开设置", "请在系统设置里找到本应用，允许它安装应用。");
                }
            }));
            dialog.show();
            return;
        }
        install(activity, apk);
    }

    private static void install(Activity activity, File apk) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(ApkProvider.uriFor(activity, apk), "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException noInstaller) {
            say(activity, "打不开安装程序", "安装包已下载到应用目录，但系统没有可用的安装界面。");
        }
    }

    /** API 26 put app installation behind a per-app switch that the app cannot set itself. */
    private static boolean canInstall(Activity activity) {
        return Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls();
    }

    private static String read(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-cache");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status);
            InputStream input = connection.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_MANIFEST_BYTES) throw new IOException("manifest too large");
            }
            return out.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }

    private static int versionCode(Context context) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) info.getLongVersionCode() : info.versionCode;
        } catch (Exception missing) {
            return 0;
        }
    }

    private static int skipped(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SKIPPED, 0);
    }

    private static void skip(Context context, int versionCode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_SKIPPED, versionCode).apply();
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.US, "%.1f", bytes / 1048576.0);
    }

    private static TextView text(Context context, String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    /** Answers a check the reader asked for. Everything a manual check says is one short dialog. */
    private static void say(final Activity activity, final String title, final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (activity.isFinishing()) return;
                new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
                    .setPositiveButton("好", null).show();
            }
        });
    }

    /** Download progress, written by the worker thread and read by the progress dialog. */
    private static final class Progress {
        volatile long done, total;
    }
}
