package io.qzxy.lite.preview;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
 * <p>A 正方 module page is not a page: it is a fragment written against globals the menu shell
 * declares — {@code _path} for every url it builds, {@code BaseJqGrid} and {@code loadJqGrid} for
 * its grid, {@code $.i18n} for every label. Opened as a top-level document it throws on the first
 * of those ({@code BaseJqGrid is not defined}) inside its own {@code $(function(){…})}, so the grid
 * is never created, {@code onSelectRow} is never bound, and the panel keeps the static skeleton it
 * shipped with — the 状态备注 legend and "评价内容 / 0% Complete" the user saw. That is why the
 * school's own menu entry works while a direct url did not: the menu loads the module <em>into the
 * shell document</em>, where those globals exist.</p>
 *
 * <p>So this screen loads the shell first, then mounts the module into a full-screen host inside
 * it, exactly as the menu does. Everything after that — the grid, the row, the fragment the row
 * loads, the 暂存 / 提交 buttons the fragment wires — is the school's own code running on its own
 * terms. Nothing about the evaluation is built on this side: the app never scores and never
 * submits.</p>
 *
 * <p>Two things are added to that document, both inert to the school's logic: a
 * {@code $.ajaxPrefilter} that appends {@code gnmkdm=N401605} to its {@code /xspjgl/} requests
 * (the shell's own referer does not carry the module id, and the endpoint answers 500 without it),
 * and an error listener, so a page that throws says so in the status line instead of waiting.</p>
 */
public final class EvaluationFormActivity extends Page {
    /** The 教学班 to land on; the only thing this screen needs from the list. */
    static final String EXTRA_CLASS = "jxb_id";
    static final String EXTRA_TITLE = "title";

    /** How many times to look for the row: the grid answers over two round trips. */
    private static final int MAX_POLLS = 30;
    /** Whole-screen budget. Past this the screen says what is wrong rather than waiting. */
    private static final long BUDGET_MS = 30_000;
    /** How many row selections may produce nothing before the panel counts as not loading. */
    private static final int MAX_MISSES = 3;

    private WebView web;
    private TextView status;
    private TextView heading;
    private Button backButton;
    private String classId = "";
    private int polls;
    private int misses;
    private boolean mounted;
    private long startedAt;
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
        status.setText("正在打开学校的教务菜单…");
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
                String target = request.getUrl().toString();
                if (!school(target)) {
                    status.setText("该跳转不属于学校 HTTPS 网站，未在此页打开。");
                    return true;
                }
                // 提交后学校会问「要不要评选最佳教师」并跳到另一个模块页。作为顶层文档打开它同样
                // 缺 _path / BaseJqGrid，只剩白页——所以改成就地装进壳里，和进来时一样。
                if (mounted && target.contains("/zjjspxgl/")) {
                    view.evaluateJavascript(hostLoad(target), null);
                    status.setText("已在评价页内打开学校的后续页面。");
                    return true;
                }
                return false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                refreshBackMode();  // 历史变了，返回该不该缩卡片也跟着变
                WebBack.caption(backButton, view);
                if (url == null) return;
                if (url.contains("login_slogin")) {
                    status.setText("登录状态已失效：返回上一页会重新登录，再进来就能评。");
                    return;
                }
                if (url.contains("/xspjgl/")) {   // 学校自己跳到了模块页，就当已经装好
                    status.setText("页面已打开，正在定位这门课…");
                    located();
                    return;
                }
                // Anything else is the shell, or where the shell redirected to — the document the
                // module's globals live in either way, so mount into it. The flag keeps a redirect
                // chain from mounting twice.
                mount();
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
     * Opens the menu shell with the app's session cookie already installed.
     *
     * <p>The shell is the whole point: it is the document that declares {@code _path},
     * {@code BaseJqGrid}, {@code $.i18n} and {@code loadJqGrid}. Loading the module url instead is
     * what left the screen with a legend and "0% Complete".</p>
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
        mounted = false;
        startedAt = SystemClock.elapsedRealtime();
        status.setText("正在打开学校的教务菜单…");
        java.util.HashMap<String, String> headers = new java.util.HashMap<>();
        headers.put("Referer", EduClient.ORIGIN + "/");
        web.loadUrl(EduClient.ORIGIN + EduClient.MENU_PAGE, headers);
    }

    /**
     * Mounts the module into the shell document and starts looking for the row.
     *
     * <p>Done the way the menu does it — {@code $(host).load(url)} — so the module's own scripts run
     * with the shell's globals in scope. The host covers the shell so the student sees the
     * evaluation, not a menu.</p>
     */
    private void mount() {
        if (mounted) return;
        mounted = true;
        web.evaluateJavascript(shellScript(), value -> {
            String token = value == null ? "" : value.replace("\"", "");
            if (token.contains("ok")) {
                status.setText("页面已打开，正在定位这门课…");
                located();
            } else {
                status.setText(token.contains("nojquery")
                    ? "学校的教务菜单没有加载完整（缺少页面脚本），请点「重新打开」再试。"
                    : "学校的教务菜单没有加载完整，请点「重新打开」再试。");
                Diag.event(Diag.WARN, "EVAL", "shell.mount", "stage", "menu", "result", token);
            }
        });
    }

    /**
     * The shell-side script: prefilter, error listener, host, mount.
     *
     * <p>The prefilter is the one thing the school's own code cannot know it needs: inside the menu
     * the module is opened from a link carrying {@code gnmkdm}, while this one is loaded by url, so
     * every {@code /xspjgl/} request the module makes would arrive without the module id and the
     * endpoint answers 500. The error listener writes the first message into the page so a failure
     * can be read back without a debugger.</p>
     */
    private static String shellScript() {
        return "(function(){try{"
            + "var j=window.jQuery;"
            + "if(!j||!j.fn||typeof j.fn.load!=='function')return 'nojquery';"
            + "if(!window.__ckEvalFix){window.__ckEvalFix=true;"
            + "try{j.ajaxPrefilter(function(o){"
            + "if(!o||typeof o.url!=='string')return;"
            + "if(o.url.indexOf('/xspjgl/')<0)return;"
            + "if(o.url.indexOf('gnmkdm')>=0)return;"
            + "o.url+=(o.url.indexOf('?')>=0?'&':'?')+'gnmkdm=N401605';});}catch(e){}"
            + "window.__ckEvalErr=[];"
            + "try{window.addEventListener('error',function(e){"
            + "if(window.__ckEvalErr.length<3)"
            + "window.__ckEvalErr.push(String((e&&e.message)||'').slice(0,80));});}catch(e){}"
            + "}"
            + "var host=document.getElementById('ckEvalHost');"
            + "if(!host){"
            + "host=document.createElement('div');host.id='ckEvalHost';"
            + "host.style.cssText='position:fixed;left:0;top:0;right:0;bottom:0;z-index:2147480000;"
            + "background:#fff;overflow:auto;-webkit-overflow-scrolling:touch;';"
            + "(document.body||document.documentElement).appendChild(host);"
            + "j(host).load('/xspjgl/xspj_cxXspjIndex.html?gnmkdm=N401605',function(r,s){"
            + "window.__ckEvalMount=s;});"
            + "}"
            + "return 'ok';}catch(e){return 'fail';}})()";
    }

    /** Loads one more module page into the same host, so the shell's globals stay in scope. */
    private static String hostLoad(String url) {
        String safe = url == null ? "" : url.replace("\\", "").replace("'", "%27");
        return "(function(){try{var h=document.getElementById('ckEvalHost');"
            + "if(!h||!window.jQuery)return;"
            + "window.jQuery(h).load('" + safe + "');}catch(e){}})()";
    }

    /** Resets the look-for-the-row counter after a fresh mount or a retry. */
    private void located() {
        polls = 0;
        pick();
    }

    /**
     * Looks for the row, and selects it once the grid has one.
     *
     * <p>Matched through the grid's own data rather than the rendered cells: {@code jxb_id} is a
     * hidden column, so jqGrid never writes it into a {@code <td>} and no amount of reading the row's
     * text would find it. {@code getGridParam('data')} returns the rows as the school sent them, and
     * {@code setSelection(rowid, true)} fires {@code onSelectRow} — the same handler a tap fires,
     * which is what loads the fragment.</p>
     */
    private void pick() {
        if (SystemClock.elapsedRealtime() - startedAt > BUDGET_MS) {
            fail("页面打开了，但课程列表一直没有出现。可以点「重新打开」，或返回上一页用「教务系统」里的菜单进入。",
                "timeout");
            return;
        }
        if (polls++ > MAX_POLLS) {
            fail("没能定位到这门课，请返回上一页重新点一次，或在「教务系统」的菜单里打开学生评价。",
                "nocourse");
            return;
        }
        web.evaluateJavascript("(function(){"
            + "try{"
            + "var j=window.jQuery;if(!j)return 'wait';"
            + "var g=j('#tempGrid');if(!g.length)return 'wait';"
            + "var t='" + safeId() + "';"
            + "var rows=null;try{rows=g.jqGrid('getGridParam','data');}catch(e){return 'wait';}"
            + "if(!rows||!rows.length){"
            + "if(g.find('tr.jqgrow').length)return 'null';"
            + "return 'wait';}"
            + "var hit='';"
            + "for(var i=0;i<rows.length;i++){var r=rows[i]||{};"
            + "if(String(r.jxb_id)===t){hit=(r._id_!=null?String(r._id_):String(r.jxb_id));break;}}"
            + "if(!hit)return 'missing'+rows.length;"
            + "try{g.jqGrid('setSelection',hit,true);}catch(e){}"
            + "var node=document.getElementById(hit);if(node){j(node).trigger('click');}"
            + "return 'open';"
            + "}catch(e){return 'fail';}})()", value -> {
            String token = value == null ? "" : value.replace("\"", "");
            if (token.contains("open")) {
                main.postDelayed(this::verify, 1600);
            } else if (token.contains("missing")) {
                String rows = token.replaceAll("[^0-9]", "");
                fail("列表里没找到这门课（这一页 " + (rows.isEmpty() ? "?" : rows)
                    + " 行），请返回上一页重新点一次。", "missing");
            } else if (token.contains("null")) {
                fail("学校没有返回这门课的评价列表（0 门），可以返回上一页看看这门课的状态。", "empty");
            } else if (token.contains("fail")) {
                fail("学校的评价页脚本出错了，没能读出课程列表。", "script");
            } else {
                main.postDelayed(this::pick, 500);
            }
        });
    }

    /**
     * Did the form actually land in the panel, and did the school send items with it?
     *
     * <p>Three numbers, because they fail differently: an empty panel means the row selection was
     * dropped (the page ignores one while it is still loading the row it selected on arrival), an
     * {@code .alert-error} in the panel means the school itself refused the course (已放弃 / 无评价
     * 内容) and no retry will change that, and a panel with items is the only success. A panel that
     * merely has text is <em>not</em> success — the skeleton ships with text, which is exactly what
     * made an earlier version report a loaded form over an empty one.</p>
     */
    private void verify() {
        web.evaluateJavascript("(function(){var p=document.getElementById('panel_content');"
            + "if(!p)return '0|0|0';"
            + "var t=(p.textContent||'').replace(/\\s+/g,'').length;"
            + "var n=p.querySelectorAll('.panel-pjdx').length;"
            + "var e=p.querySelector('.alert-error')?1:0;"
            + "return t+'|'+n+'|'+e;})()", value -> {
            String raw = value == null ? "" : value.replace("\"", "");
            String[] parts = raw.split("\\|");
            int length = number(parts.length > 0 ? parts[0] : "");
            int items = number(parts.length > 1 ? parts[1] : "");
            int refused = number(parts.length > 2 ? parts[2] : "");
            if (items > 0) {
                status.setText("表单已打开：共 " + items + " 个评价项，评完点页面里的「提交」，返回上一页会刷新状态。");
                Diag.event(Diag.INFO, "EVAL", "form.ready", "stage", "form", "count", items);
                reveal();
                return;
            }
            if (refused > 0) {
                fail("这门课现在不能评价（学校页面已给出提示），返回上一页可以看到它的状态。", "refused");
                return;
            }
            if (misses++ < MAX_MISSES) {
                polls = 0;
                pick();
                return;
            }
            Diag.event(Diag.WARN, "EVAL", "form.empty", "stage", "form", "count", length);
            fail("评价表单没有加载出来（面板 " + length + " 字，0 个评价项）。点「重新打开」再试一次；"
                + "如果一直这样，请用「教务系统」里的菜单进入。", "formempty");
        });
    }

    /** Says what went wrong and stops polling: a status line that never settles is not an answer. */
    private void fail(String message, String code) {
        status.setText(message);
        Diag.event(Diag.WARN, "EVAL", "form.failed", "stage", code);
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
