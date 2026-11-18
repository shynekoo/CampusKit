package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shows an official school website inside the app.
 *
 * <p>The two entry points on the home screen used to hand the URL to the system browser, which
 * meant a separate app, its own tab history and a jarring switch. A WebView is enough for a public
 * site: it needs no login, no injected cookie and no native session, so nothing here touches
 * {@link EduClient}.</p>
 *
 * <p>Only pages on {@code *.hebeu.edu.cn} are rendered here. A link that leaves the school domain
 * is handed to the system browser instead of being blocked, so an outbound link still works. Plain
 * HTTP is allowed for these hosts because the school's own pages and their images still use it;
 * mixed content is loaded in compatibility mode for the same reason.</p>
 */
public final class SchoolSiteActivity extends Page {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    private WebView web;
    private TextView status;
    private Button backButton;
    private String home = "";

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    /** Shown to the school's server as part of the user agent, so keep it truthful. */
    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception unknown) { return "unknown"; }
    }

    /** The school's own hosts; the port, if any, is irrelevant for a public site. */
    private static boolean school(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String scheme = uri.getScheme();
        if (host == null) return false;
        if (!"http".equals(scheme) && !"https".equals(scheme)) return false;
        host = host.toLowerCase(java.util.Locale.ROOT);
        return host.equals("hebeu.edu.cn") || host.endsWith(".hebeu.edu.cn");
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

    /** Anything that is not a school page goes to whichever app can handle it. */
    private void handOff(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            status.setText("该链接不属于学校网站，已用系统浏览器打开。");
        } catch (ActivityNotFoundException none) {
            status.setText("该链接不属于学校网站，本机也没有能打开它的应用。");
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Intent intent = getIntent();
        home = intent.getStringExtra(EXTRA_URL);
        if (home == null || !school(home)) home = "https://www.hebeu.edu.cn/";
        String title = intent.getStringExtra(EXTRA_TITLE);
        if (title == null) title = "学校网站";

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

        // No browser escape hatch: the pages stay here. Outbound links still leave by themselves.
        LinearLayout bar = new LinearLayout(this);
        backButton = action(bar, WebBack.CLOSE, () -> WebBack.go(this, web));
        action(bar, "首页", () -> web.loadUrl(home));
        action(bar, "刷新", () -> web.reload());
        root.addView(bar);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(13);
        heading.setTextColor(AppUi.INK);
        heading.setPadding(dp(2), dp(2), dp(2), dp(2));
        heading.setSingleLine(true);
        root.addView(heading);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(AppUi.MUTED);
        status.setPadding(dp(2), 0, dp(2), dp(6));
        status.setText("正在打开 " + Uri.parse(home).getHost() + "…");
        root.addView(status);

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        // School pages are HTTPS but still pull in plain-HTTP images and fonts.
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSaveFormData(false);
        settings.setUserAgentString(settings.getUserAgentString() + " CampusKit/" + versionName());
        if (Build.VERSION.SDK_INT >= 26)
            web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (school(url)) return false;
                handOff(url);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (url != null) status.setText("正在加载 " + Uri.parse(url).getHost() + "…");
            }
            @Override public void onPageFinished(WebView view, String url) {
                refreshBackMode();  // 历史变了，返回该不该缩卡片也跟着变
                if (url != null) status.setText(Uri.parse(url).getHost());
                WebBack.caption(backButton, view);
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                refreshBackMode();
                WebBack.caption(backButton, view);
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                status.setText("学校网站证书校验失败，已停止加载。");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String pageTitle) {
                if (pageTitle != null && pageTitle.length() > 0 && heading != null)
                    heading.setText(pageTitle.length() > 30 ? pageTitle.substring(0, 30) + "…" : pageTitle);
            }
        });
        // PDFs and attachments on school sites cannot be rendered by a bare WebView.
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> handOff(url));

        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        web.loadUrl(home);
    }

    /** 网页还有上一页时返回只是后退，窗口不会关——这种时候不能让系统先缩卡片。 */
    @Override protected boolean backMayStay() { return web != null && web.canGoBack(); }

    @Override public void onBackPressed() {
        WebBack.go(this, web);
    }

    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}
