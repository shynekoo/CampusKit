package io.qzxy.lite.preview;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import java.io.*;

/** User-operated university login. No native JS bridge and no credential extraction. */
public final class TimetableWebActivity extends Page {
    private static final String HOST="jwglxxfwpt.hebeu.edu.cn";
    private static final String LOGIN="https://"+HOST+"/xtgl/login_slogin.html";
    private static final String COURSES="https://"+HOST+"/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default";
    private WebView web; private TextView status; private Button read; private long navigation;
    private Button button(String title,Runnable action) { Button b=new Button(this); b.setText(title); b.setOnClickListener(v->action.run()); return b; }
    private static boolean school(String url) {
        Uri uri=Uri.parse(url); String host=uri.getHost();
        return "https".equals(uri.getScheme()) && host!=null && (host.equals("hebeu.edu.cn") || host.endsWith(".hebeu.edu.cn")) && (uri.getPort()==-1 || uri.getPort()==443);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12,12,12,12);
        if(android.os.Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)-> { android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime()); v.setPadding(12+bars.left,12+bars.top,12+bars.right,12+bars.bottom);return insets; });
        }
        LinearLayout actions=new LinearLayout(this);
        actions.addView(button("返回课表",this::finish),new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(button("打开课表页",()->web.loadUrl(COURSES)),new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(button("清除登录",()->Theme.dialog(this).setMessage("清除「科信小助手」内教务网页的登录状态？已导入课程会保留。")
            .setNegativeButton("取消",null).setPositiveButton("清除",(d,w)-> {
                CookieManager.getInstance().removeAllCookies(ok->{ CookieManager.getInstance().flush(); WebStorage.getInstance().deleteAllData(); if(!isDestroyed()){web.clearCache(true);web.clearHistory();web.loadUrl(LOGIN);} });
            }).show()),new LinearLayout.LayoutParams(0,-2,1));
        root.addView(actions);
        TextView help=new TextView(this); help.setText("河北工程大学教务系统\n请自行登录、选择学期并显示表格式课表。点击下方按钮读取后，还需核对确认。网页登录状态可能保留，可点“清除登录”。");root.addView(help);
        status=new TextView(this);root.addView(status);
        web=new WebView(this); root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
        settings.setSaveFormData(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if(android.os.Build.VERSION.SDK_INT>=26) web.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                if(school(request.getUrl().toString())) return false;
                status.setText("该跳转不属于学校 HTTPS 网站，未在此页打开。");return true;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) { navigation++; read.setEnabled(false);status.setText("正在加载："+Uri.parse(url).getHost()); }
            @Override public void onPageFinished(WebView view,String url) { refreshBackMode(); read.setEnabled(HOST.equals(Uri.parse(url).getHost()));status.setText("当前网站："+Uri.parse(url).getHost()); }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,android.net.http.SslError error) { handler.cancel();status.setText("网站证书校验失败，已停止加载。"); }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) { if(request.isForMainFrame()) { read.setEnabled(false);status.setText("网页加载失败，请检查网络后重新进入。"); } }
        });
        read=button("读取当前课表",this::extract);read.setEnabled(false);root.addView(read);
        setContentView(root);web.loadUrl(LOGIN);
    }
    private void extract() {
        String url=web.getUrl(); if(url==null || !school(url) || !HOST.equals(Uri.parse(url).getHost()) || !Uri.parse(url).getPath().startsWith("/kbcx/")) {
            status.setText("请先打开学生课表查询页面。");return;
        }
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=getResources().openRawResource(R.raw.timetable_extract)) { byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n); }
            final long generation=navigation;read.setEnabled(false);
            web.evaluateJavascript(out.toString("UTF-8"), value->{
                if(isDestroyed() || isFinishing() || generation!=navigation)return;
                read.setEnabled(true);
                try {
                    Object decoded=new org.json.JSONTokener(value).nextValue();
                    if(!(decoded instanceof String))throw new IllegalArgumentException("请等待课表加载完成后重试");
                    String json=(String)decoded;
                    TimetableWebReader.parse(json);
                    setResult(RESULT_OK,new Intent().putExtra("courses",json));finish();
                } catch(Exception failure) { Theme.dialog(this).setTitle("暂未导入").setMessage(failure.getMessage()).setPositiveButton("继续查看",null).show(); }
            });
        } catch(Exception failure) { status.setText("读取失败，请重新打开页面。");read.setEnabled(true); }
    }
    /** 网页还有上一页时返回只是后退，窗口不会关——这种时候不能让系统先缩卡片。 */
    @Override protected boolean backMayStay() { return web != null && web.canGoBack(); }

    @Override public void onBackPressed() { if(web.canGoBack())web.goBack();else super.onBackPressed(); }
    @Override protected void onDestroy() { if(web!=null){web.stopLoading();web.destroy();}super.onDestroy(); }
}
