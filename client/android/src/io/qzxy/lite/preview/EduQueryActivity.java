package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Grades and empty-classroom lookup through the school's own academic site.
 *
 * <p>The user signs in inside this WebView; the app never sees or stores the academic password, and
 * the login cookie is the one the timetable import already uses. Queries are opened through the
 * site's own menu entries found on the page, so a route change at the school cannot silently break
 * the feature. Reading is limited to rendered table text.</p>
 */
public final class EduQueryActivity extends Activity {
    private static final String HOST = "jwglxxfwpt.hebeu.edu.cn";
    private static final String LOGIN = "https://" + HOST + "/xtgl/login_slogin.html";
    private static final String MENU = "https://" + HOST + "/xtgl/index_initMenu.html";
    /** Only used when the menu link cannot be found: the page the school itself links to. */
    private static final String GRADES = "https://" + HOST + "/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005&layout=default";
    private static final String GRADES_WORDS = "成绩查询|学生成绩|成绩";
    private static final String ROOMS_WORDS = "空闲教室|空教室|教室查询";

    private WebView web;
    private TextView status;
    private Button read;
    private Button backButton;
    private long navigation;
    private String pendingSearch, pendingFallback;
    private boolean busyReading;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private static boolean school(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return "https".equals(uri.getScheme()) && host != null
            && (host.equals("hebeu.edu.cn") || host.endsWith(".hebeu.edu.cn"))
            && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private Button action(LinearLayout row, String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setTextColor(AppUi.ACCENT);
        AppUi.press(button, 0x00ffffff, 10);
        button.setOnClickListener(v -> { if (!busyReading) action.run(); });
        row.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
        return button;
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUi.BG);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.ime());
                v.setPadding(dp(12) + bars.left, dp(12) + bars.top, dp(12) + bars.right, dp(12) + bars.bottom);
                return insets;
            });
        }

        LinearLayout first = new LinearLayout(this);
        backButton = action(first, WebBack.CLOSE, () -> WebBack.go(this, web));
        action(first, "教务首页", () -> web.loadUrl(MENU));
        action(first, "清除登录", this::clearLogin);
        root.addView(first);

        LinearLayout second = new LinearLayout(this);
        action(second, "查成绩", () -> openTarget(GRADES_WORDS, GRADES));
        action(second, "空教室", () -> openTarget(ROOMS_WORDS, null));
        root.addView(second);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(AppUi.MUTED);
        status.setPadding(dp(4), dp(4), dp(4), dp(6));
        status.setText("请先在下面登录教务系统。登录后可点“查成绩”或“空教室”，再点“读取本页”。");
        root.addView(status);

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
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
        if (Build.VERSION.SDK_INT >= 26) web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (school(request.getUrl().toString())) return false;
                status.setText("该跳转不属于学校 HTTPS 网站，未在此页打开。");
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                navigation++;
                read.setEnabled(false);
                status.setText("正在加载：" + Uri.parse(url).getHost());
            }
            @Override public void onPageFinished(WebView view, String url) {
                WebBack.caption(backButton, view);
                read.setEnabled(HOST.equals(Uri.parse(url).getHost()));
                if (pendingSearch != null) {
                    String keywords = pendingSearch, fallback = pendingFallback;
                    pendingSearch = null; pendingFallback = null;
                    search(keywords, fallback, 1);
                    return;
                }
                if (url != null && url.contains("login_slogin"))
                    status.setText("当前是登录页。请登录后点“查成绩”或“空教室”。");
                else
                    status.setText("已打开：" + Uri.parse(url).getPath() + "。进入结果页后点“读取本页”。");
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                WebBack.caption(backButton, view);
            }
            @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                status.setText("网站证书校验失败，已停止加载。");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) { read.setEnabled(false); status.setText("网页加载失败，请检查网络后重试。"); }
            }
        });

        read = new Button(this);
        read.setText("读取本页");
        read.setAllCaps(false);
        read.setTextSize(16);
        read.setTextColor(AppUi.ON_ACCENT);
        read.setBackground(AppUi.shape(this, AppUi.ACCENT, 12));
        AppUi.press(read, AppUi.ACCENT, 12);
        read.setEnabled(false);
        read.setOnClickListener(v -> extract());
        LinearLayout.LayoutParams readParams = new LinearLayout.LayoutParams(-1, dp(50));
        readParams.topMargin = dp(6);
        root.addView(read, readParams);

        setContentView(root);
        web.loadUrl(LOGIN);
    }

    private void clearLogin() {
        Theme.dialog(this)
            .setMessage("清除「科信小助手」内教务网页的登录状态？已导入的课程和其他数据不受影响。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清除", (d, w) -> CookieManager.getInstance().removeAllCookies(ok -> {
                CookieManager.getInstance().flush();
                WebStorage.getInstance().deleteAllData();
                if (!isDestroyed()) { web.clearCache(true); web.clearHistory(); web.loadUrl(LOGIN); }
            }))
            .show();
    }

    /** Opens a query by following the site's own menu link; the fallback URL is a last resort. */
    private void openTarget(String keywords, String fallback) {
        String current = web.getUrl();
        if (current == null) { web.loadUrl(LOGIN); return; }
        if (current.contains("login_slogin")) {
            status.setText("请先登录教务系统，登录后再点一次。");
            return;
        }
        search(keywords, fallback, 0);
    }

    private void search(String keywords, String fallback, int attempt) {
        final long generation = navigation;
        status.setText("正在查找“" + keywords.split("\\|")[0] + "”入口…");
        String script = read("edu_menu.js").replace("\"__CQ_KEYWORDS__\"", "\"" + keywords + "\"");
        web.evaluateJavascript(script, value -> {
            if (isDestroyed() || isFinishing() || generation != navigation) return;
            String href = unquote(value);
            if (!href.isEmpty() && school(href)) { status.setText("正在打开查询页…"); web.loadUrl(href); return; }
            if (attempt == 0) {
                pendingSearch = keywords; pendingFallback = fallback;
                status.setText("正在打开教务菜单…");
                web.loadUrl(MENU);
                return;
            }
            if (fallback != null) { status.setText("菜单里没有找到入口，正在直接打开成绩页…"); web.loadUrl(fallback); }
            else status.setText("菜单里没有找到入口。请自己在页面上点“查询 → 空闲教室”，选好周次和节次后点“读取本页”。");
        });
    }

    private void extract() {
        String url = web.getUrl();
        if (!school(url) || !HOST.equals(Uri.parse(url).getHost())) {
            status.setText("请先打开学校的教务页面。");
            return;
        }
        final long generation = navigation;
        busyReading = true;
        read.setEnabled(false);
        status.setText("正在读取页面表格…");
        web.evaluateJavascript(read("edu_tables.js"), value -> {
            busyReading = false;
            if (isDestroyed() || isFinishing()) return;
            read.setEnabled(generation == navigation);
            try {
                Object decoded = new org.json.JSONTokener(value).nextValue();
                if (!(decoded instanceof String)) throw new IllegalArgumentException("请等待页面加载完成后再读取");
                String json = (String) decoded;
                EduTables.Page page = EduTables.parse(json);
                EduTables.pending = json;
                status.setText("已读取 " + page.tables.size() + " 个表格。");
                startActivity(new android.content.Intent(this, EduResultActivity.class));
            } catch (Exception failure) {
                status.setText(failure.getMessage() == null ? "没有读到表格" : failure.getMessage());
            }
        });
    }

    private String read(String asset) {
        try (InputStream in = getResources().openRawResource(asset.equals("edu_menu.js") ? R.raw.edu_menu : R.raw.edu_tables)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        } catch (Exception failure) {
            status.setText("读取页面脚本失败，请重新进入。");
            return "";
        }
    }

    /** evaluateJavascript hands back a JSON-encoded string; an empty result arrives as "" or null. */
    private static String unquote(String value) {
        if (value == null) return "";
        String text = value.trim();
        if (text.isEmpty() || text.equals("null") || text.equals("\"\"")) return "";
        try {
            Object decoded = new org.json.JSONTokener(text).nextValue();
            return decoded instanceof String ? ((String) decoded).trim() : "";
        } catch (Exception unreadable) {
            return "";
        }
    }

    @Override public void onBackPressed() {
        WebBack.go(this, web);
    }

    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}
