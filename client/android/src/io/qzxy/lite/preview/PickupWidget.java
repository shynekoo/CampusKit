package io.qzxy.lite.preview;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

/** Hosts Cainiao's own RemoteViews. Does not scrape parcel data or store pickup codes. */
final class PickupWidget {
    private static final int BIND = 71, CONFIGURE = 72;
    private static final String PACKAGE = "com.cainiao.wireless";
    private final Activity activity;
    private final AppWidgetHost host;
    private final AppWidgetManager manager;
    private final SharedPreferences prefs;
    private Runnable changed = () -> {};
    private boolean hasPickup;
    void onChanged(Runnable action) { changed = action; }
    boolean hasPickup() { return hasPickup; }
    void openCainiao() {
        try { activity.startActivity(new Intent().setClassName(PACKAGE,
            "com.cainiao.wireless.homepage.view.activity.HomePageActivity")); }
        catch (RuntimeException failure) { message("暂时无法打开菜鸟，请确认已安装。"); }
    }
    void settings() {
        new android.app.AlertDialog.Builder(activity).setTitle("取件信息设置")
            .setItems(new String[]{"打开菜鸟", "重新添加组件", "移除组件"}, (d, which) -> {
                if(which == 0) openCainiao();
                else if(which == 1) add();
                else { remove(); hasPickup=false; changed.run(); }
            }).setNegativeButton("取消", null).show();
    }
    private void inspect(View root) {
        StringBuilder text = new StringBuilder();
        wire(root, text);
        boolean next = io.qzxy.lite.core.PickupPresence.hasPickup(text.toString());
        if(next != hasPickup) { hasPickup=next; changed.run(); }
    }
    private void wire(View view, StringBuilder text) {
        if(view.getVisibility()!=View.VISIBLE) return;
        if(view instanceof android.widget.TextView) text.append(((android.widget.TextView)view).getText()).append('\n');
        String name="";
        try { if(view.getId()!=View.NO_ID) name=activity.getPackageManager().getResourcesForApplication(PACKAGE).getResourceEntryName(view.getId()); }
        catch(Exception missing) { }
        if(name.equals("ll_setting_layout") || name.equals("iv_setting")) view.setOnClickListener(v->settings());
        if(name.equals("tv_action_one") && view instanceof android.widget.TextView
            && ((android.widget.TextView)view).getText().toString().startsWith("打开菜鸟")) {
            view.setOnClickListener(v->openCainiao());
            if(view.getParent() instanceof View) ((View)view.getParent()).setOnClickListener(v->openCainiao());
        }
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)wire(group.getChildAt(i),text);
        }
    }
    PickupWidget(Activity activity) {
        this.activity = activity;
        host = new AppWidgetHost(activity, 7982) {
            @Override protected AppWidgetHostView onCreateView(android.content.Context context, int id, AppWidgetProviderInfo info) {
                return new AppWidgetHostView(context) {
                    @Override public void updateAppWidget(android.widget.RemoteViews views) {
                        super.updateAppWidget(views);
                        post(() -> inspect(this));
                    }
                };
            }
        };
        manager = AppWidgetManager.getInstance(activity);
        prefs = activity.getSharedPreferences("pickup-widget", 0);
    }
    void start() { host.startListening(); }
    void stop() { host.stopListening(); }
    boolean added() { return prefs.getInt("id", -1) >= 0; }
    private void message(String value) { Toast.makeText(activity, value, Toast.LENGTH_LONG).show(); }
    private Bundle options() {
        int width = Math.max(240, (int)(activity.getResources().getDisplayMetrics().widthPixels
            / activity.getResources().getDisplayMetrics().density) - 64);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
        return options;
    }
    void add() {
        AppWidgetProviderInfo selected = null;
        for (AppWidgetProviderInfo provider : manager.getInstalledProviders()) {
            if (!PACKAGE.equals(provider.provider.getPackageName())) continue;
            String name = provider.provider.getClassName();
            if (name.endsWith(".NewLogisticsWidgetLargest")) { selected = provider; break; }
            if (name.endsWith(".LogisticsWidgetLargest")) selected = provider;
        }
        if (selected == null) { message("未找到菜鸟物流组件，请先安装或更新菜鸟。"); return; }
        clearPending();
        int id = host.allocateAppWidgetId();
        prefs.edit().putInt("pending", id).apply();
        try {
            if (manager.bindAppWidgetIdIfAllowed(id, selected.provider, options())) configure(id);
            else activity.startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, selected.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options()), BIND);
        } catch (RuntimeException failure) { clearPending(); message("系统暂不允许添加菜鸟组件。"); }
    }
    private void configure(int id) {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        if (info == null || !PACKAGE.equals(info.provider.getPackageName())) { clearPending(); return; }
        if (info.configure != null) {
            activity.startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), CONFIGURE);
        } else save(id);
    }
    private void save(int id) {
        int old = prefs.getInt("id", -1);
        if (old >= 0 && old != id) host.deleteAppWidgetId(old);
        prefs.edit().putInt("id", id).remove("pending").apply();
    }
    private void clearPending() {
        int id = prefs.getInt("pending", -1);
        if (id >= 0) host.deleteAppWidgetId(id);
        prefs.edit().remove("pending").apply();
    }
    boolean result(int request, int result) {
        if (request != BIND && request != CONFIGURE) return false;
        int id = prefs.getInt("pending", -1);
        if (id < 0) return true;
        try {
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
            if (result != Activity.RESULT_OK || info == null || !PACKAGE.equals(info.provider.getPackageName())) clearPending();
            else if (request == BIND) configure(id);
            else save(id);
        } catch (RuntimeException failure) { clearPending(); message("组件未能添加，请重试。"); }
        return true;
    }
    View view() {
        int id = prefs.getInt("id", -1);
        if (id < 0) return null;
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        if (info == null || !PACKAGE.equals(info.provider.getPackageName())) { remove(); return null; }
        AppWidgetHostView view = host.createView(activity, id, info);
        view.updateAppWidgetOptions(options());
        view.post(() -> inspect(view));
        view.postDelayed(() -> inspect(view),500);
        return view;
    }
    void remove() {
        int id = prefs.getInt("id", -1);
        if (id >= 0) host.deleteAppWidgetId(id);
        prefs.edit().remove("id").apply();
        clearPending();
    }
}
