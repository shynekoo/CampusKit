package io.qzxy.lite.preview;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The school's own 学生评价 page, opened on one 教学班 and left to the school to drive.
 *
 * <p>Earlier versions asked for the evaluation form directly, as a URL with ids attached. That can
 * never work: the form is a <em>fragment</em> — {@code xspj_cxXspjDisplay.html} — and the 暂存 /
 * 提交 buttons it binds ({@code #btn_xspj_bc}, {@code #btn_xspj_tj}) live in the list page's own
 * markup, not in the fragment. Fetched on its own it is a form of radio buttons with no way to
 * submit, whatever shape the request takes; the school's list page asks for it with
 * {@code $(panel).load(url, data)}, a jQuery POST whose headers and encoding only exist inside a
 * page that is already on the school's origin.</p>
 *
 * <p>So this screen loads {@link EduClient#EVAL_PAGE} itself and then selects the row for the 教学班
 * the student tapped, through the grid's own {@code setSelection}. From there the school's
 * {@code onSelectRow} loads the fragment into its panel, fills the teacher line, wires the buttons
 * and decides what 暂存 and 提交 send. Nothing about the evaluation — the ids, the fields, the
 * required items, the submit — is built on this side.</p>
 *
 * <p>Navigation stays on the school's HTTPS hosts, no form data is saved, and the browser session
 * is the one the list already holds: leaving keeps it, so returning reloads without signing in.</p>
 */
public final class EvaluationFormActivity extends Page {
    /** The 教学班 to land on; the only thing this screen needs from the list. */
    static final String EXTRA_CLASS = "jxb_id";
    static final String EXTRA_TITLE = "title";

    /** How many times to look for the row before saying so: the grid answers over two round trips. */
    private static final int MAX_POLLS = 24;

    private WebView web;
    private TextView status;
    private TextView heading;
    private Button backButton;
    private String classId = "";
    private int polls;
    /** Row selections that produced nothing; the grid drops one made while it is still loading. */
    private int misses;
    private final Handler main = new Handler(Looper.getMainLooper());

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private static boolean school(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return "https".equals(uri.getScheme()) && host != null
            && (host.equals("hebeu.edu.cn") || host.endsWith(".hebeu.edu.cn"))
            && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    /** A jqGrid id comes from the school, but it is spliced into script: keep it to id characters. */
    private String safeId() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < classId.length(); i++) {
            char c = classId.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || c == '-' || c == '_') out.append(c);
        }
        return out.toString();
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
        classId = text(getIntent().getStringExtra(EXTRA_CLASS));
        String title = text(getIntent().getStringExtra(EXTRA_TITLE));

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

        LinearLayout bar = new LinearLayout(this);
        backButton = action(bar, WebBack.CLOSE, () -> WebBack.go(this, web));
        action(bar, "重新打开", this::load);
        root.addView(bar);

        heading = new TextView(this);
        heading.setText(title.isEmpty() ? "学生评价" : title);
        heading.setTextSize(14);
        heading.setTextColor(AppUi.INK);
        heading.setSingleLine(true);
        heading.setPadding(dp(2), dp(2), dp(2), dp(2));
        root.addView(heading);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(AppUi.MUTED);
        status.setPadding(dp(2), 0, dp(2), dp(6));
        status.setText("正在打开学校的评价页面…");
        root.addView(status);

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSaveFormData(false);
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
                WebBack.caption(backButton, view);
                if (url == null) return;
                if (url.contains("login_slogin")) {
                    status.setText("登录状态已失效：返回上一页会重新登录，再进来就能评。");
                    return;
                }
                if (!url.contains("/xspjgl/")) return;  // 学校把页面带到了别处，等它停下
                status.setText("页面已打开，正在定位这门课…");
                polls = 0;
                pick();
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                WebBack.caption(backButton, view);
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                                     android.net.http.SslError error) {
                handler.cancel();
                status.setText("学校网站证书校验失败，已停止加载。");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) status.setText("网页加载失败，请检查网络后点「重新打开」。");
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        load();
    }

    /**
     * Opens the list page with the app's session cookie already installed.
     *
     * <p>The Referer is the page's own url, which is what a browser sends once the page is up: it
     * carries {@code gnmkdm}, and the module's own filter reads that. A Referer pointing at the menu
     * frame loads the page but leaves the fragment request without it.</p>
     */
    private void load() {
        CookieManager cookies = CookieManager.getInstance();
        for (String part : EduClient.browserCookie().split(";"))
            if (part.contains("=")) cookies.setCookie(EduClient.ORIGIN, part.trim());
        cookies.flush();
        if (safeId().isEmpty()) {
            status.setText("没有拿到这门课的教学班编号，请返回上一页重新点一次。");
            return;
        }
        polls = 0;
        misses = 0;
        status.setText("正在打开学校的评价页面…");
        java.util.HashMap<String, String> headers = new java.util.HashMap<>();
        headers.put("Referer", EduClient.ORIGIN + EduClient.EVAL_PAGE);
        web.loadUrl(EduClient.ORIGIN + EduClient.EVAL_PAGE, headers);
    }

    /**
     * Makes the page's own fragment request carry the module id.
     *
     * <p>The page calls {@code $(#panel_content).load("/xspjgl/xspj_cxXspjDisplay.html", {…})} without
     * {@code gnmkdm}; reached from the menu that is harmless, but a page opened by url gets nothing
     * back for it — the panel stays at its own placeholder. Appending the id to that one url is the
     * smallest change that makes the request the one the endpoint answers (verified by hand: with
     * {@code gnmkdm} the same ids return a form with 13 items, without it 500).</p>
     */
    private static String patch() {
        return "if(j.fn&&j.fn.load&&!j.fn.__ckEvalPatched){"
            + "var L=j.fn.load,P=function(u){"
            + "if(typeof u==='string'&&u.indexOf('xspj_cxXspjDisplay.html')>=0"
            + "&&u.indexOf('gnmkdm')<0){u+=(u.indexOf('?')>=0?'&':'?')+'gnmkdm=N401605';arguments[0]=u;}"
            + "return L.apply(this,arguments);};"
            + "P.__ckEvalPatched=true;j.fn.load=P;}";
    }

    /**
     * Looks for the row, and clicks it once the table has one.
     *
     * <p>Matched in the DOM rather than through the grid's API: the 教学班 id sits in a hidden cell,
     * so the row's text carries it even though nothing of it is on screen. A click is what a finger
     * does, and the page already wires that to the same handler that loads the form — so this
     * cannot drift from the way the school means it to work.</p>
     */
    private void pick() {
        if (polls++ > MAX_POLLS) {
            status.setText("没能定位到这门课，请在上面的列表里自己点那一行。");
            return;
        }
        web.evaluateJavascript("(function(){"
            + "var j=window.jQuery;if(!j)return 'wait';"
            + "var g=j('#tempGrid');if(!g.length)return 'wait';"
            + "var rows=g.find('tr.jqgrow');if(!rows.length)return 'wait';"
            + patch()
            + "var t='" + safeId() + "',hit=null;"
            + "rows.each(function(){"
            + "if((this.textContent||'').indexOf(t)>=0){hit=this;return false;}});"
            + "if(!hit)return 'missing'+rows.length;"
            + "j(hit).trigger('click');"
            + "try{if(g.jqGrid)g.jqGrid('setSelection',hit.id,true);}catch(e){}"
            + "return 'open';"
            + "})()", value -> {
            String token = value == null ? "" : value;
            if (token.contains("open")) {
                main.postDelayed(this::verify, 1600);
            } else if (token.contains("missing")) {
                String rows = token.replaceAll("[^0-9]", "");
                status.setText("列表里没找到这门课（这一页 " + (rows.isEmpty() ? "?" : rows)
                    + " 行），请在上面的列表里自己点那一行。");
            } else {
                main.postDelayed(this::pick, 500);
            }
        });
    }

    /**
     * Did the form actually land in the panel, and did the school send items with it?
     *
     * <p>Two numbers, because they fail differently: an empty panel means the row click was dropped
     * (the page ignores one while it is still loading the row it selected on arrival), while a panel
     * with a header and no 评价项 means the school answered a form with nothing to score — the
     * student can do nothing about that from here, so it is said plainly instead of leaving a blank
     * screen under a cheerful hint.</p>
     */
    private void verify() {
        web.evaluateJavascript("(function(){var p=document.getElementById('panel_content');"
            + "if(!p)return '0,0';"
            + "var t=(p.textContent||'').replace(/\\s+/g,'').length;"
            + "var n=p.querySelectorAll('.panel-pjdx').length;"
            + "return t+','+n;})()", value -> {
            String raw = value == null ? "" : value.replace("\"", "");
            String[] parts = raw.split(",");
            int length = number(parts.length > 0 ? parts[0] : "");
            int items = number(parts.length > 1 ? parts[1] : "");
            if (items > 0) {
                status.setText("表单已打开：共 " + items + " 个评价项，评完点页面里的「提交」，返回上一页会刷新状态。");
                reveal();
                return;
            }
            if (length >= 40) {
                status.setText("学校页面打开了，但没有下发评价项（0 项）：请在上面的列表里点这门课那一行。"
                    + "若仍是 0 项，说明这门课的评价模板没有开放。");
                return;
            }
            if (misses++ < 3) {
                polls = 0;
                pick();
            } else {
                status.setText("表单没有加载出来，请在上面的列表里点这门课所在的那一行。");
            }
        });
    }

    private static int number(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try { return Integer.parseInt(digits); } catch (NumberFormatException tooBig) { return 0; }
    }

    /** Brings the panel into view: the list above it is the school's, not what was asked for. */
    private void reveal() {
        web.evaluateJavascript("(function(){var p=document.getElementById('panel_content');"
            + "if(p&&p.scrollIntoView)p.scrollIntoView({block:'start'});})()", null);
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }

    /** 网页还有上一页时返回只是后退，窗口不会关——这种时候不能让系统先缩卡片。 */
    @Override protected boolean backMayStay() { return web != null && web.canGoBack(); }

    @Override public void onBackPressed() {
        WebBack.go(this, web);
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (web != null) { web.stopLoading(); web.destroy(); }
        // Same as the other academic screens: leaving keeps the session the list reuses.
        EduClient.release();
        super.onDestroy();
    }
}
