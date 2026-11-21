// SPDX-License-Identifier: MPL-2.0
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
 * way the native grade/room queries do. The session it already holds — signed in here a moment ago,
 * or on the grades screen — is reused as-is, and its cookie is placed in the WebView before the first
 * page loads, so there is no password field to fill in. If no account is saved, or the school asks
 * for a captcha, the ordinary login page is shown instead — never a blank screen. A login completed
 * in that page is handed back to {@link EduClient} via {@link EduClient#capture()}, so the school
 * sees one session rather than two competing ones.</p>
 *
 * <p>Navigation is pinned to the school's HTTPS hosts, no form data is saved and third-party cookies
 * are refused. Leaving the screen keeps the session the other academic screens share.</p>
 */
public final class EduPortalActivity extends Page {
    private static final String HOME = EduClient.ORIGIN + "/xtgl/index_initMenu.html";
    private static final String LOGIN = EduClient.ORIGIN + "/xtgl/login_slogin.html";
    private static final int ACCOUNT_REQUEST = 74;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WebView web;
    private TextView status;
    private Button backButton;
    private int generation;
    private boolean signedOut, askedAccount, recovered;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    /** Puts the app's session cookie into the WebView, so its next page opens already signed in. */
    private void install(String cookie, Runnable then) {
        CookieManager cookies = CookieManager.getInstance();
        for (String part : cookie.split(";")) if (part.contains("=")) cookies.setCookie(EduClient.ORIGIN, part.trim());
        cookies.flush();
        then.run();
    }

    /** A page load that carries the current session instead of starting another one. */
    private void open(String url) {
        String cookie = EduClient.browserCookie();
        if (cookie.isEmpty()) web.loadUrl(url);
        else install(cookie, () -> web.loadUrl(url));
    }

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
        backButton = action(first, WebBack.CLOSE, () -> WebBack.go(this, web));
        action(first, "教务首页", () -> open(HOME));
        action(first, "刷新", () -> open(web.getUrl() == null ? HOME : web.getUrl()));
        root.addView(first);
        LinearLayout second = new LinearLayout(this);
        action(second, "教务账号", this::openAccount);
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
                refreshBackMode();  // 历史变了，返回该不该缩卡片也跟着变
                if (url == null) return;
                if (url.contains("login_slogin")) {
                    // A saved account means the page should never have to ask: the session was either
                    // never installed or the school has ended it. One fresh sign-in per visit puts the
                    // page back on the same session the other screens use; a rejected password stops
                    // here rather than walking towards a lockout.
                    if (!recovered) {
                        recovered = true;
                        if (EduClient.hasCredentials()) { relogin(); return; }
                    }
                    status.setText("当前是登录页：可以在这里登录，或保存账号后下次自动登录。");
                } else {
                    // Whatever session this page ended up with is the app's session — including one the
                    // user just typed. Adopting it means the queries reuse it rather than signing in
                    // again and having the school drop this one.
                    EduClient.capture();
                }
                WebBack.caption(backButton, view);
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                WebBack.caption(backButton, view);
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

    /** The shared session first; the cookie is installed before the WebView ever sees a page. */
    private void autoSignIn() {
        final int ticket = ++generation;
        recovered = false;
        worker.execute(() -> {
            EduVault.Credential credential = new EduVault(getApplicationContext()).load();
            if (credential == null || !credential.complete() || credential.refused) {
                post(ticket, () -> {
                    status.setText("还没有可用的教务账号：已打开「教务账号」，保存后会自动登录；也可以直接在下面的学校页面登录。");
                    web.loadUrl(LOGIN);
                    // The school login page stays usable if they back out; ask only once per visit.
                    if (!askedAccount) {
                        askedAccount = true;
                        openAccount();
                    }
                });
                return;
            }
            try {
                EduClient.reopen();
                EduClient.remember(credential.user, credential.password, false);
                final boolean reused = EduClient.signedIn();
                if (!reused) EduClient.signIn();
                final String cookie = EduClient.browserCookie();
                if (cookie.isEmpty()) throw new EduClient.NotSignedIn();
                post(ticket, () -> {
                    status.setText(reused ? "沿用已登录的教务会话。" : "已用保存的账号自动登录。");
                    install(cookie, () -> web.loadUrl(HOME));
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

    private void openAccount() {        startActivityForResult(new Intent(this, EduAccountActivity.class), ACCOUNT_REQUEST);
    }

    /** Saving the account here signs straight into the portal, no need to reload the page by hand. */
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ACCOUNT_REQUEST && result == RESULT_OK) {
            status.setText("账号已保存，正在自动登录教务系统…");
            autoSignIn();
        }
    }

    /**
     * The school ended the session this app held: sign in once more with the saved account and load
     * the page on the new cookie. The other academic screens share that same session, so they do not
     * have to sign in again — and the school sees one login rather than one per screen.
     */
    private void relogin() {
        status.setText("登录状态已失效，正在用保存的账号重新登录…");
        final int ticket = generation;
        worker.execute(() -> {
            try {
                EduClient.forgetSession();
                EduClient.signIn();
                String cookie = EduClient.browserCookie();
                if (cookie.isEmpty()) throw new EduClient.NotSignedIn();
                post(ticket, () -> install(cookie, () -> web.loadUrl(HOME)));
            } catch (Exception failure) {
                post(ticket, () -> status.setText("重新登录没成功："
                    + EduClient.describe(failure) + " 也可以直接在这里登录。"));
            }
        });
    }

    private void signOut() {
        Theme.dialog(this).setTitle("退出教务登录")
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

    /** 网页还有上一页时返回只是后退，窗口不会关——这种时候不能让系统先缩卡片。 */
    @Override protected boolean backMayStay() { return web != null && web.canGoBack(); }

    @Override public void onBackPressed() {
        WebBack.go(this, web);
    }

    @Override protected void onDestroy() {
        generation++;
        worker.shutdown();
        if (web != null) { web.stopLoading(); web.destroy(); }
        // Same as the grade screen: leaving keeps the session the other screens reuse.
        if (!signedOut) EduClient.release();
        super.onDestroy();
    }

    private void post(int ticket, Runnable task) {
        runOnUiThread(() -> { if (!isDestroyed() && !isFinishing() && ticket == generation) task.run(); });
    }
}
