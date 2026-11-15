package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Opens the school's academic system already signed in.
 *
 * <p>The account saved in {@link EduVault} is handed to {@link EduClient}, which signs in the same
 * way the native grade/room queries do. The resulting session cookie is placed in the WebView before
 * the first page loads, so there is no password field to fill in. If no account is saved, or the
 * school asks for a captcha, the ordinary login page is shown instead — never a blank screen.</p>
 *
 * <p>Navigation is pinned to the school's HTTPS hosts, no form data is saved and third-party cookies
 * are refused. Leaving the screen keeps the five-minute grace period the other academic screens use.</p>
 */
public final class EduPortalActivity extends Activity {
    private static final String HOME = EduClient.ORIGIN + "/xtgl/index_initMenu.html";
    private static final String LOGIN = EduClient.ORIGIN + "/xtgl/login_slogin.html";
    private static final long GRACE_MS = 5 * 60 * 1000L;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WebView web;
    private TextView status;
    private int generation;
    private boolean signedOut;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private static boolean school(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return "https".equals(uri.getScheme()) && host != null
            && (host.equals("hebeu.edu.cn") || host.endsWith(".hebeu.edu.cn"))
            && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private Button action(LinearLayout row, String title, Runnable task) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setTextColor(AppUi.ACCENT);
        AppUi.press(button, 0x00ffffff, 10);
        button.setOnClickListener(v -> task.run());
        row.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
        return button;
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUi.BG);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.ime());
                v.setPadding(dp(10) + bars.left, dp(10) + bars.top, dp(10) + bars.right, dp(10) + bars.bottom);
                return insets;
            });
        }
        LinearLayout first = new LinearLayout(this);
        action(first, "‹ 返回", this::finish);
        action(first, "教务首页", () -> web.loadUrl(HOME));
        action(first, "刷新", () -> web.reload());
        root.addView(first);
        LinearLayout second = new LinearLayout(this);
        action(second, "教务账号", () -> startActivity(new Intent(this, EduAccountActivity.class)));
        action(second, "退出登录", this::signOut);
        root.addView(second);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(AppUi.MUTED);
        status.setPadding(0, dp(4), 0, dp(6));
        status.setText("正在自动登录教务系统…");
        root.addView(status);

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSaveFormData(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= 26)
            web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (school(request.getUrl().toString())) return false;
                status.setText("该跳转不属于学校 HTTPS 网站，未在此页打开。");
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (url != null && url.contains("login_slogin"))
                    status.setText("当前是登录页：可以在这里登录，或保存账号后下次自动登录。");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                status.setText("学校网站证书校验失败，已停止加载。");
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        autoSignIn();
    }

    /** Native sign-in first; the cookie is installed before the WebView ever sees a page. */
    private void autoSignIn() {
        final int ticket = ++generation;
        worker.execute(() -> {
            EduVault.Credential credential = new EduVault(getApplicationContext()).load();
            if (credential == null || !credential.complete() || credential.refused) {
                post(ticket, () -> {
                    status.setText("还没有可用的教务账号：可在此登录，或到「教务账号」保存后自动登录。");
                    web.loadUrl(LOGIN);
                });
                return;
            }
            try {
                EduClient.reopen();
                EduClient.remember(credential.user, credential.password, false);
                if (!EduClient.signedIn()) EduClient.signIn();
                final String cookie = EduClient.browserCookie();
                if (cookie.isEmpty()) throw new EduClient.NotSignedIn();
                post(ticket, () -> {
                    CookieManager cookies = CookieManager.getInstance();
                    for (String part : cookie.split(";")) if (part.contains("=")) cookies.setCookie(EduClient.ORIGIN, part.trim());
                    cookies.flush();
                    status.setText("已用保存的账号自动登录。");
                    web.loadUrl(HOME);
                });
            } catch (Exception failure) {
                final String message = EduClient.describe(failure);
                post(ticket, () -> {
                    status.setText("自动登录没成功：" + message + " 已切到学校登录页。");
                    web.loadUrl(LOGIN);
                });
            }
        });
    }

    private void signOut() {
        new AlertDialog.Builder(this).setTitle("退出教务登录")
            .setMessage("会同时结束学校那边的会话，并清除本页的网页登录状态。已保存的账号不会被删除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("退出", (d, w) -> {
                signedOut = true;
                generation++;
                CookieManager.getInstance().removeAllCookies(ok -> {
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                });
                worker.execute(EduClient::signOut);
                if (!isDestroyed()) {
                    web.clearCache(true);
                    web.clearHistory();
                    web.loadUrl(LOGIN);
                    status.setText("已退出，可在「教务账号」里保存账号后自动登录。");
                }
            }).show();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        generation++;
        worker.shutdown();
        if (web != null) { web.stopLoading(); web.destroy(); }
        // Same grace period as the grade screen: leaving does not end the session at once.
        if (!signedOut) EduClient.signOutAfter(GRACE_MS);
        super.onDestroy();
    }

    private void post(int ticket, Runnable task) {
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing() && ticket == generation) task.run(); });
    }
}
