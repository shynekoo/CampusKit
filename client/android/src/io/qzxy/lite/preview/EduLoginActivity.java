package io.qzxy.lite.preview;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The school's own login page, kept apart from the query screens.
 *
 * <p>The user types their academic account into the school's form; nothing is stored by this app and
 * the password never passes through its code. As soon as the page leaves the login screen the cookies
 * are flushed and this screen closes, so the caller can read the data with the same session.</p>
 */
public final class EduLoginActivity extends Activity {
    private static final String HOST = "jwglxxfwpt.hebeu.edu.cn";
    private static final String LOGIN = "https://" + HOST + "/xtgl/login_slogin.html";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUi.BG);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(AppUi.MUTED);
        status.setText("请登录教务系统。登录成功后会自动返回，不需要再点别的。");
        status.setPadding(0, 0, 0, dp(8));
        root.addView(status);

        WebView web = new WebView(this);
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
        if (android.os.Build.VERSION.SDK_INT >= 26)
            web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null && (host.equals("hebeu.edu.cn") || host.endsWith(".hebeu.edu.cn"))) return false;
                status.setText("该跳转不属于学校网站，未在此页打开。");
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (url == null) return;
                String host = Uri.parse(url).getHost();
                if (HOST.equals(host) && !url.contains("login_slogin")) {
                    CookieManager.getInstance().flush();
                    status.setText("登录成功，正在返回…");
                    // 交还控制权给调用页（成绩、空教室、课表导入），它会带着这份 session 接着查。
                    handler.postDelayed(() -> { setResult(RESULT_OK); finish(); }, 350);
                } else {
                    status.setText("请登录教务系统。登录成功后会自动返回。");
                }
            }
            @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                status.setText("学校网站证书校验失败，已停止加载。");
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        web.loadUrl(LOGIN);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
