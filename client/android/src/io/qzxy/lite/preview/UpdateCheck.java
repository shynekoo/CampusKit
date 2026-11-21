// SPDX-License-Identifier: MPL-2.0
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import io.qzxy.lite.core.UpdateChannel;
import io.qzxy.lite.core.UpdateManifest;
import io.qzxy.lite.core.UpdateSources;
import io.qzxy.lite.core.UpdateUrls;

/**
 * Checks for a new release at start-up, explains it, and installs it if the reader says yes.
 *
 * <p>Three rules keep this out of the way. It never blocks the first screen — the check runs on a
 * background thread a second after the timetable is drawn. It never nags: a version the reader
 * answered 稍后 to is remembered and stays quiet on later starts, though the manual entry in
 * 账号 still offers it. And it fails silently — no network, a moved manifest or a malformed one all
 * end the same way, with nothing on screen, because a popup about a failed check is not news.</p>
 *
 * <p>Two lists feed it. {@code update.json} is the release channel; {@code beta.json} is the
 * preview channel and is read only when the reader turned 接收预览版更新 on. Which of the two wins is
 * decided by {@link UpdateChannel} from the version <em>code</em> alone, so a reader running a
 * preview is never walked back down to an older release. Either list may be missing: the other one
 * still answers, and neither lowers any of the checks below.</p>
 *
 * <p>Nothing the manifest says is taken on faith. {@link UpdateManifest} validates every field and
 * requires both a byte count and a SHA-256; this class additionally requires HTTPS on every hop,
 * follows redirects manually under the rules in {@link UpdateUrls}, and verifies the package
 * against the manifest's size and digest before checking its package name, version and signing
 * certificate against the installed app. A failed check deletes the download instead of installing
 * it. A mirrored manifest alone is not enough to forge an update: the certificate must still match.
 * The preview channel is verified exactly as strictly as the release channel.</p>
 */
final class UpdateCheck {
    /**
     * The published manifests live on three hosts. WorkBuddy is the default, GitHub and Gitee are
     * the alternatives the reader picks in 更多 → 更新渠道; Gitee is there because GitHub's release
     * CDN is slow from mainland networks. Every host carries the same two files, so switching
     * changes only where they are read from — every field still goes through the same
     * {@link UpdateManifest} checks and the download through the same signature verification.
     * The addresses are in {@link UpdateSources} so the JVM tests read the exact same list.
     */
    private static final String PREFS = "update-v1";
    /** A version the reader declined once. Kept as a code, so a newer release asks again. */
    private static final String KEY_SKIPPED = "skipped";
    /** The reader's channel choice. Absent on a fresh install, where the build itself decides. */
    private static final String KEY_BETA = "beta";
    /** The reader's channel choice. Absent on a fresh install means WorkBuddy, the default. */
    private static final String KEY_SOURCE = "source";
    private static final int CONNECT_TIMEOUT_MS = 8000, READ_TIMEOUT_MS = 15000;
    private static final int MAX_MANIFEST_BYTES = 65536, BUFFER = 16384;

    /** One check per process: a rotation or a tab switch must not start a second request. */
    private static boolean started;
    /** Downloaded and waiting for the install permission the reader was sent to grant. */
    private static File pending;
    /**
     * Bumped whenever the channel changes. A check that started before the reader flipped the
     * switch must not put its answer on screen afterwards: it was computed for the other channel.
     */
    private static int generation;

    private UpdateCheck() { }

    /**
     * The reader's saved choice. Off until the reader turns it on, in every build — including a
     * preview build. A preview channel that switches itself on because of what was installed would
     * be asking people who never opted in to run unreleased packages; the switch is opt-in only.
     */
    static boolean betaEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BETA, false);
    }

    /** Saves the choice and retires any check already in flight. */
    static void setBetaEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_BETA, enabled).apply();
        synchronized (UpdateCheck.class) { generation++; }
    }

    /** Saves the channel choice and retires any check already in flight. */
    static void setSource(Context context, String source) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SOURCE, UpdateSources.normalize(source)).apply();
        synchronized (UpdateCheck.class) { generation++; }
    }

    /** "workbuddy" (the default), "github" or "gitee"; anything unknown reads as the default. */
    static String source(Context context) {
        return UpdateSources.normalize(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SOURCE, "workbuddy"));
    }

    private static synchronized int generation() { return generation; }

    static void onStart(final Activity activity) {
        if (started) return;
        started = true;
        // The test manifest has no INTERNET permission, and an update popup would sit on top of
        // every assertion that drives the real screen.
        if (BuildFlags.DEVICE_TESTS) return;
        final View root = activity.getWindow().getDecorView();
        root.postDelayed(new Runnable() {
            @Override public void run() { tidy(activity); check(activity, false); }
        }, 1200);
    }

    /**
     * Deletes downloads older than a day. A package is otherwise only cleared when the next download
     * starts, so an update that was downloaded but never installed would keep five megabytes for
     * good; a day is long enough not to race the install prompt that may still be on screen.
     */
    private static void tidy(Activity activity) {
        File[] old = ApkProvider.directory(activity).listFiles();
        if (old == null) return;
        long staleBefore = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        for (File file : old) if (file.lastModified() < staleBefore) file.delete();
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
        // Read once, on the calling thread: the check itself runs on whichever channel was chosen
        // when it started, and the generation guard below stops a later switch from being undone.
        final boolean beta = betaEnabled(activity);
        final String source = source(activity);
        final String[] addresses = UpdateSources.manifests(source);
        final int ticket = generation();
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                String trace = Diag.trace();
                long mark = Diag.mark();
                Diag.event(Diag.INFO, "UPDATE", "check.start", "trace", trace,
                    "beta", beta ? "1" : "0");
                UpdateManifest release = readManifest(addresses[0], trace);
                // The preview list costs one request and is only spent when it can be used.
                UpdateManifest preview = beta ? readManifest(addresses[1], trace) : null;
                if (release == null && preview == null) {
                    // 检查更新失败一直是不打扰的静默失败；日志里留原因，界面仍然不弹窗。
                    if (manual) say(activity, "暂时查不到新版本", "读不到版本信息，请确认网络后重试。");
                    return;
                }
                UpdateManifest manifest = UpdateChannel.pick(installed, beta, release, preview);
                if (manifest == null) {
                    Diag.event(Diag.INFO, "UPDATE", "check.start", "trace", trace,
                        "stage", "latest", "duration", Diag.since(mark));
                    if (manual) {
                        // Say which list was quiet when one of them could not be read at all: "已是最新"
                        // while the preview list is unreachable would be a claim, not a fact.
                        String hint = beta && preview == null ? "预览版清单暂时读不到。" : "";
                        say(activity, "已是最新版本", "当前 " + AppUi.version(activity) + "，没有可安装的更新。" + hint);
                    }
                    return;
                }
                Diag.event(Diag.INFO, "UPDATE", "check.available", "trace", trace,
                    "version", manifest.version, "code", manifest.versionCode,
                    "channel", manifest.beta ? "beta" : "release", "duration", Diag.since(mark));
                // 稍后 is remembered for that exact release; asking again on every start would be
                // the nagging this check is supposed to avoid.
                if (!manual && manifest.versionCode == skipped(activity)) return;
                if (ticket != generation()) {
                    // 渠道在这期间被切换过：这次结果属于旧设置，直接丢弃，界面不能出现旧渠道的推荐。
                    Diag.event(Diag.INFO, "UPDATE", "check.stale", "trace", trace,
                        "version", manifest.version, "stage", "discarded");
                    return;
                }
                final UpdateManifest choice = manifest;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (ticket != generation()) return;
                        afterNotice(activity, choice, 0);
                    }
                });
            }
        }, "update-check");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * One manifest, or null. A missing list is a normal state — the preview file is deployed only
     * when a preview build exists — so every failure, from 404 to a malformed body, is logged and
     * turned into "this channel has nothing", never into a dialog.
     */
    private static UpdateManifest readManifest(String address, String trace) {
        try {
            return UpdateManifest.parse(read(address));
        } catch (Exception unavailable) {
            Diag.failure("UPDATE", "check.failed", Diag.reason(unavailable), unavailable, trace);
            return null;
        }
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
        // Capped like the notice dialog: eight notes on a 5-inch screen would otherwise push the
        // two buttons past the bottom edge, leaving the reader with a dialog they cannot answer.
        final int cap = Math.max(AppUi.dp(activity, 180),
            (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.52));
        ScrollView scroll = new ScrollView(activity) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
            }
        };
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(AppUi.dp(activity, 20), AppUi.dp(activity, 2), AppUi.dp(activity, 20), 0);

        body.addView(text(activity, "当前 " + AppUi.version(activity) + " → " + manifest.version
            + (manifest.beta ? "（预览版）" : ""), 12, AppUi.MUTED));
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

        AlertDialog dialog = Theme.dialog(activity)
            .setTitle((manifest.beta ? "预览版 " : "新版本 ") + manifest.version)
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

        final AlertDialog dialog = Theme.dialog(activity)
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
                    Diag.event(Diag.INFO, "UPDATE", "install.start", "version", manifest.version);
                    save(activity, manifest, target, progress, cancelled);
                } catch (Exception problem) {
                    Diag.failure("UPDATE", "download.failed", Diag.reason(problem), problem, Diag.trace());
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

    private static void save(Context context, UpdateManifest manifest, File target, Progress progress, boolean[] cancelled) throws Exception {
        HttpURLConnection connection = null;
        FileOutputStream output = null;
        try {
            connection = open(manifest.url, "application/octet-stream", null);
            progress.total = manifest.bytes;
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
                // The manifest states the size; anything beyond it is not the reviewed package.
                if (progress.done > manifest.bytes + BUFFER) throw new IOException("安装包大小与说明不符");
            }
            output.flush();
            output.close();
            output = null;
            String trace = Diag.trace();
            try {
                if (progress.done != manifest.bytes) throw new IOException("安装包大小与说明不符");
                StringBuilder hex = new StringBuilder(64);
                for (byte value : digest.digest()) hex.append(String.format(Locale.US, "%02x", value & 0xff));
                if (!hex.toString().equals(manifest.sha256)) throw new IOException("安装包校验失败，已删除");
                verifyPackage(context, target, manifest);
            } catch (IOException invalid) {
                // 校验失败要能区分「下载不完整」和「包被换过」，日志里就记这一步的类与原因。
                Diag.failure("UPDATE", "verify.failed", "VERIFY", invalid, trace);
                throw invalid;
            }
        } finally {
            if (output != null) try { output.close(); } catch (IOException ignored) { }
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Compares the downloaded package with the running app. The digest ties the bytes to the
     * manifest; this ties the manifest to the app's own identity. A forged manifest that swaps in
     * another APK cannot match our signing certificate, and a version that is not the announced one
     * is refused as well. The system installer checks the upgrade signature again at install time.
     */
    private static void verifyPackage(Context context, File apk, UpdateManifest manifest) throws IOException {
        android.content.pm.PackageManager pm = context.getPackageManager();
        android.content.pm.PackageInfo candidate = pm.getPackageArchiveInfo(
            apk.getAbsolutePath(), android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
        if (candidate == null) throw new IOException("安装包无法解析，已删除");
        if (!context.getPackageName().equals(candidate.packageName))
            throw new IOException("安装包包名不符，已删除");
        long code = Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
        if (code != manifest.versionCode) throw new IOException("安装包版本与说明不符，已删除");
        android.content.pm.SigningInfo ours = signing(context.getPackageName(), pm);
        android.content.pm.SigningInfo theirs = candidate.signingInfo;
        if (ours == null || theirs == null) throw new IOException("无法读取签名证书，已删除");
        // Compare SHA-256 of every signer certificate. getApkContentsSigners() covers both single
        // and multi-signer apps; comparing as a set makes signer order irrelevant.
        if (!fingerprints(ours).equals(fingerprints(theirs)))
            throw new IOException("安装包签名与本应用不符，已删除");
    }

    private static android.content.pm.SigningInfo signing(String packageName,
            android.content.pm.PackageManager pm) {
        try {
            return pm.getPackageInfo(packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
        } catch (Exception missing) {
            return null;
        }
    }

    private static java.util.Set<String> fingerprints(android.content.pm.SigningInfo info) {
        java.util.Set<String> out = new HashSet<String>();
        for (android.content.pm.Signature signer : info.getApkContentsSigners()) {
            out.add(hex(sha256(signer.toByteArray())));
        }
        return out;
    }

    /** SHA-256 is guaranteed on every Android device; a missing one is a broken runtime. */
    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static void installWithPermission(Activity activity, File apk) {
        if (!canInstall(activity)) {
            pending = apk;
            AlertDialog dialog = Theme.dialog(activity)
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

    /**
     * Opens an HTTPS address, following redirects one hop at a time under {@link UpdateUrls}. Java's
     * built-in automatic redirect is deliberately off: it would silently allow an HTTP downgrade,
     * an unbounded loop, or a query string (the sharecode) leaking to an unrelated host.
     */
    private static HttpURLConnection open(String address, String accept, String cache) throws IOException {
        UpdateUrls.requireHttps(address);
        Set<String> seen = new HashSet<String>();
        seen.add(UpdateUrls.canonical(address));
        String current = address;
        for (int hop = 0; ; hop++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            if (accept != null) connection.setRequestProperty("Accept", accept);
            if (cache != null) connection.setRequestProperty("Cache-Control", cache);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                current = UpdateUrls.next(current, location, seen, hop);
                continue;
            }
            if (status != 200) {
                connection.disconnect();
                throw new IOException("HTTP " + status);
            }
            return connection;
        }
    }

    private static String read(String address) throws IOException {
        HttpURLConnection connection = open(address, "application/json", "no-cache");
        try {
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
                Theme.dialog(activity).setTitle(title).setMessage(message)
                    .setPositiveButton("好", null).show();
            }
        });
    }

    /** Download progress, written by the worker thread and read by the progress dialog. */
    private static final class Progress {
        volatile long done, total;
    }
}
