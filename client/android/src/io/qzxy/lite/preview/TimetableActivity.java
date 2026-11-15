package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import io.qzxy.lite.core.PendingUse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local timetable, independent of the water account and network. Times are section numbers. */
public final class TimetableActivity extends Activity {
    private static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private final List<JSONObject> courses = new ArrayList<>();
    private SharedPreferences store;
    private PickupWidget pickupWidget;
    private int week = 1;
    private String lastDay="";
    private android.graphics.Bitmap backgroundImage;
    private final android.os.Handler clockHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable clockTick=new Runnable(){public void run(){if(hasWindowFocus())refreshClock();clockHandler.postDelayed(this,30000);}};
    private final SharedPreferences.OnSharedPreferenceChangeListener timetableChanged=(prefs,key)->ScheduleWidget.updateAll(this);
    private void refreshClock(){
        boolean changed=!todayIso().equals(lastDay);lastDay=todayIso();
        if(changed)week=Math.max(1,Math.min(30,currentWeek()));
        if(root!=null&&(changed||tab==0)){
            int scroll=0;for(int i=0;i<root.getChildCount();i++)if(root.getChildAt(i) instanceof ScrollView)scroll=root.getChildAt(i).getScrollY();
            render();final int y=scroll;for(int i=0;i<root.getChildCount();i++)if(root.getChildAt(i) instanceof ScrollView){View v=root.getChildAt(i);v.post(()->v.scrollTo(0,y));}
        }
    }
    @Override protected void onPause(){accountVisible=false;clockHandler.removeCallbacks(clockTick);super.onPause();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("showToday",false)){tab=0;render();}}

    private String monday = "";
    private boolean unreadable;
    private LinearLayout root;
    private boolean importing;
    /** Which side the next board should animate in from: +1 from the right, -1 from the left, 0 none. */
    private WeekBoard.Pager pager;
    /** 0 = 今日, 1 = 课表, 2 = 趣智校园. Parallel tabs of this activity, not separate screens. */
    private int tab;
    /** Inside 趣智校园: 0 = the entry list, 1 = the records view. */
    private int campusView;
    private String pendingNote;
    private boolean signedIn;
    private boolean accountVisible,sessionChecking,expiredNotice;
    private static final String CAINIAO_PACKAGE = "com.cainiao.wireless";
    private static final String CAINIAO_HOME_ACTIVITY = "com.cainiao.wireless.homepage.view.activity.HomePageActivity";
    /** 菜鸟裹裹 calls this page 出库码 / 身份码; both names are used in its own text. */
    private static final String CAINIAO_IDENTITY_CODE = "guoguo://go/station_code";
    /** Tried in order when the pickup code is tapped. */
    private static final String[] CAINIAO_FALLBACK_PACKAGES = {CAINIAO_PACKAGE, "com.taobao.taobao", "com.eg.android.AlipayGphone"};
    private final java.util.concurrent.ExecutorService pdfWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override protected void onDestroy() { pdfWorker.shutdown(); clockHandler.removeCallbacks(clockTick);if(store!=null)store.unregisterOnSharedPreferenceChangeListener(timetableChanged); super.onDestroy(); }

    @Override protected void onStart() { super.onStart(); if (pickupWidget != null) pickupWidget.start(); }
    @Override protected void onStop() { if (pickupWidget != null) pickupWidget.stop(); super.onStop(); }

    @Override protected void onResume() {
        super.onResume();
        accountVisible=true;checkQzxySession();
        refreshClock();clockHandler.removeCallbacks(clockTick);clockHandler.postDelayed(clockTick,30000);

    }

    private void showExpiredNotice(){if(!accountVisible||!expiredNotice||isDestroyed()||isFinishing())return;expiredNotice=false;new android.app.AlertDialog.Builder(this).setTitle("趣智校园登录已失效").setMessage("请重新登录后使用趣智校园设备。").setNegativeButton("稍后",null).setPositiveButton("重新登录",(d,w)->startActivity(new Intent(this,AccountActivity.class).putExtra("returnToAccountInfo",true))).show();}
    private void checkQzxySession(){
        showExpiredNotice();if(sessionChecking)return;sessionChecking=true;
        pdfWorker.execute(()->{io.qzxy.lite.core.AccountSession checked=null;boolean expired=false;
            try{checked=new AccountVault(this).load();if(checked!=null)SmsAccountApi.profile(checked);}
            catch(HttpForm.SessionExpired e){try{expired=AccountVault.clearExpired(this,checked);}catch(Exception ignored){}}
            catch(Exception unavailable){/* A network or parsing error is not proof of logout. */}
            final boolean invalid=expired;runOnUiThread(()->{sessionChecking=false;if(isDestroyed()||isFinishing())return;if(invalid){signedIn=false;expiredNotice=true;showExpiredNotice();}});
        });
    }

    @Override public void onBackPressed() {
        if (tab == 2 && campusView == 1) { campusView = 0; render(); return; }
        if (tab != 0) { tab = 0; campusView = 0; render(); return; }
        super.onBackPressed();
    }

    /**
     * Reads the two local facts 趣智校园 shows: whether an account session is stored, and whether a past
     * query left an unverified record. A record that cannot be read is reported as unreadable rather
     * than hidden, and the view is only rebuilt when something actually changed.
     */
    private void loadLocalState() {
        pdfWorker.execute(() -> {
            boolean signed = false;
            try { signed = new AccountVault(getApplicationContext()).load() != null; }
            catch (Exception unreadableSession) { /* Shown as "not signed in"; the account screen says more. */ }
            String note = "";
            try {
                PendingUse record = new PendingUseVault(getApplicationContext()).load();
                if (record != null) note = record.describe();
            } catch (Exception unreadableRecord) {
                note = "本机存在一条无法读取的用水记录。请在扫码后重新查询订单状态，不要直接开水。";
            }
            final boolean loadedSignedIn = signed; final String loadedNote = note;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                boolean changed = signedIn != loadedSignedIn || !loadedNote.equals(pendingNote == null ? "" : pendingNote);
                signedIn = loadedSignedIn; pendingNote = loadedNote;
                if (changed && (tab == 2 || tab == 0)) render();
            });
        });
    }

    /**
     * Opens 菜鸟裹裹's 出库码 (identity code) page.
     *
     * The page's own activity is {@code exported="false"}, so an implicit {@code guoguo://go/station_code}
     * intent is refused by the system. The app's own launcher shortcut therefore starts HomePageActivity
     * (exported) with a "jumpPath" extra that its internal router resolves. That contract is not a guess:
     * in the installed 菜鸟裹裹 dex, {@code HomePageActivity.handleShortcuts()} reads
     * {@code extras.getString("jumpPath")} and {@code extras.getString("entrance")}, then calls
     * {@code Router.from(this).toUri(jumpPath)} — and both keys must be non-empty or it returns silently.
     * It is also called from onNewIntent, so it works whether 菜鸟裹裹 is cold or already running.
     */
    private void openCainiao() {
        // The direct route first, in case a future build exports the page.
        if (tryStart(new Intent(Intent.ACTION_VIEW, Uri.parse(CAINIAO_IDENTITY_CODE)).setPackage(CAINIAO_PACKAGE))) return;
        // The route the app's own shortcut uses: an exported host plus the router path.
        Intent routed = new Intent(Intent.ACTION_VIEW);
        routed.setClassName(CAINIAO_PACKAGE, CAINIAO_HOME_ACTIVITY);
        routed.putExtra("jumpPath", CAINIAO_IDENTITY_CODE);
        routed.putExtra("entrance", "campuskit");
        if (tryStart(routed)) return;
        for (String name : CAINIAO_FALLBACK_PACKAGES) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(name);
            if (launch != null && tryStart(launch)) return;
        }
        android.widget.Toast.makeText(this, "没有找到菜鸟裹裹或淘宝，无法跳转。", android.widget.Toast.LENGTH_LONG).show();
    }

    /** @return true when something opened; a refused target (not exported) or a missing one returns false. */
    private boolean tryStart(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); return true; }
        catch (android.content.ActivityNotFoundException missing) { return false; }
        catch (SecurityException refused) { return false; }
    }

    /** Uses the installed YiXiaoYuan app's own res/xml/shortcuts payCode contract. */
    private void openCampusPayCode() {
        String target="cn.com.yunma.school.app";
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("pay.xiaofubao.com://school?pageId=payCode"));
        intent.setClassName(target,"com.yunma.app.ui.main.MainActivity");
        if(tryStart(intent))return;
        Intent fallback=getPackageManager().getLaunchIntentForPackage(target);
        if(fallback!=null && tryStart(fallback)) {
            android.widget.Toast.makeText(this,"已打开易校园，请点击付款码。",android.widget.Toast.LENGTH_LONG).show();return;
        }
        new AlertDialog.Builder(this).setTitle("未找到易校园").setMessage("请先安装易校园并登录，再回来打开付款码。")
            .setPositiveButton("知道了",null).show();
    }

    /** The installed consumer app exports its launcher but keeps the camera Activity private. */
    private void openLife798() {
        startActivity(new Intent(this,ScanActivity.class).putExtra("life798",true));
    }

    private void choosePdf() {
        if (importing) return;
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf"); intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 40);
    }
    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if(request>=80&&request<=83){handleDocument(request,result,data);return;}
        if (pickupWidget != null && pickupWidget.result(request, result)) { render(); return; }
        if (request == 41 && result == RESULT_OK && data != null) {
            try { previewPdf(TimetableWebReader.parse(data.getStringExtra("courses")), "核对教务课表"); }
            catch(Exception invalid) { new AlertDialog.Builder(this).setMessage("网页课表未能完整识别，已有课表未修改。"+invalid.getMessage()).setPositiveButton("知道了",null).show(); }
            return;
        }
        if (request != 40 || result != RESULT_OK || data == null || data.getData() == null || importing) return;
        importing = true; render(); final android.net.Uri uri = data.getData();
        pdfWorker.execute(() -> {
            try {
                io.qzxy.lite.core.TimetablePdfParser.Result parsed = TimetablePdfReader.read(getApplicationContext(), uri);
                runOnUiThread(() -> { if (isDestroyed() || isFinishing()) return; importing=false; render(); previewPdf(parsed); });
            } catch (Exception failure) {
                final String message = failure instanceof IllegalArgumentException ? failure.getMessage() : "无法读取此 PDF，请选择教务系统导出的未加密课表（10 MB、10 页以内）。";
                runOnUiThread(() -> { if (isDestroyed() || isFinishing()) return; importing=false; render();
                    new AlertDialog.Builder(this).setTitle("未导入课表").setMessage(message).setPositiveButton("知道了", null).show(); });
            }
        });
    }
    private void previewPdf(io.qzxy.lite.core.TimetablePdfParser.Result parsed) {
        previewPdf(parsed, "核对 PDF 课表");
    }
    private void previewPdf(io.qzxy.lite.core.TimetablePdfParser.Result parsed, String title) {
        try {
            List<JSONObject> replacement = new ArrayList<>(courses);
            StringBuilder summary = new StringBuilder(parsed.term).append("\n识别到 ").append(parsed.courses.size()).append(" 条安排。\n\n");
            int added = 0;
            for (io.qzxy.lite.core.TimetablePdfParser.Course c : parsed.courses) {
                JSONObject item = new JSONObject(); item.put("name", c.name); item.put("room", c.room); item.put("teacher", c.teacher);
                item.put("day", c.day); item.put("first", c.first); item.put("last", c.last); item.put("from", c.from); item.put("to", c.to); item.put("parity", c.parity); validate(item);
                boolean duplicate = false;
                for (JSONObject old : replacement) {
                    boolean equal = true;
                    for (String key : new String[]{"name","room","teacher","day","first","last","from","to","parity"})
                        if (!old.get(key).equals(item.get(key))) equal = false;
                    if (equal) { duplicate = true; break; }
                }
                if (!duplicate) { replacement.add(item); added++; }
                summary.append(DAYS[c.day]).append(" · ").append(c.first).append("–").append(c.last).append("节 · ").append(c.from).append("–").append(c.to).append("周")
                    .append(new String[]{"", "（单周）", "（双周）"}[c.parity]).append("\n").append(c.name).append("\n").append(c.room).append(" · ").append(c.teacher).append("\n\n");
            }
            if (replacement.size()>500) throw new IllegalArgumentException("导入后超过 500 条课程，请先整理已有课程。");
            summary.append(parsed.notes).append("\n将新增 ").append(added).append(" 条，跳过相同课程。现有课程保留。\n开学日期需要按学校安排设置。");
            ScrollView scroll = new ScrollView(this); TextView body=text(summary.toString(), 15); body.setPadding(dp(20),dp(8),dp(20),dp(8)); scroll.addView(body);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("取消",null).setPositiveButton("确认导入",null).create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                JSONArray array = new JSONArray(); for(JSONObject c:replacement) array.put(c);
                String notes=store.getString("import_notes", "");
                if (!parsed.notes.isEmpty() && !notes.contains(parsed.notes)) notes += (notes.isEmpty()?"":"\n")+parsed.notes;
                if (!store.edit().putString("courses",array.toString()).putString("import_notes",notes).putString("import_term",parsed.term).commit()) {
                    android.widget.Toast.makeText(this,"保存失败，请重试",android.widget.Toast.LENGTH_LONG).show(); return;
                }
                courses.clear(); courses.addAll(replacement); dialog.dismiss(); render();
                if(monday.isEmpty()) semester();
            })); dialog.show();
        } catch(Exception invalid) {
            new AlertDialog.Builder(this).setMessage("课表信息未通过校验，未修改已有课程。"+(invalid.getMessage()==null?"":invalid.getMessage())).setPositiveButton("知道了",null).show();
        }
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(AppUi.INK); view.setPadding(0, dp(8), 0, dp(8)); return view;
    }
    private Button button(String title, Runnable action) {
        Button view = new Button(this); view.setText(title); view.setAllCaps(false);
        view.setMinHeight(dp(48)); view.setOnClickListener(v -> action.run()); return view;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        pickupWidget = new PickupWidget(this);
        pickupWidget.onChanged(()->{if(root!=null && tab==0)render();});
        store = getSharedPreferences("timetable-v1", MODE_PRIVATE);
        // 0.7.7 and earlier stored a hand-typed pickup code here; the feature is gone, so drop it.
        getSharedPreferences("campus-v1", MODE_PRIVATE).edit().remove("pickup_code").apply();
        lastDay=saved==null?todayIso():saved.getString("lastDay",todayIso());
        backgroundImage=TimetableBackup.decode(store.getString("background",""));
        store.registerOnSharedPreferenceChangeListener(timetableChanged);
        monday = store.getString("monday", "");
        try {
            JSONArray data = new JSONArray(store.getString("courses", "[]"));
            if (data.length() > 500) throw new IllegalArgumentException();
            for (int i = 0; i < data.length(); i++) {
                JSONObject course = data.getJSONObject(i); validate(course); courses.add(course);
            }
            if (!monday.isEmpty()) parseMonday(monday);
        } catch (Exception invalid) { unreadable = true; }
        int current = currentWeek();
        week = saved == null ? Math.max(1, Math.min(30, current)) : Math.max(1, Math.min(30, saved.getInt("week", 1)));
        if (saved != null) {
            tab = Math.max(0, Math.min(2, saved.getInt("tab")));
            campusView = Math.max(0, Math.min(1, saved.getInt("campusView")));
            pendingNote = saved.getString("pendingNote");
        }
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("lastDay",lastDay);
        out.putInt("week", week); out.putInt("tab", tab); out.putInt("campusView", campusView);
        out.putString("pendingNote", pendingNote);
        super.onSaveInstanceState(out);
    }
    private static SimpleDateFormat dateFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false); format.setTimeZone(TimeZone.getTimeZone("UTC")); return format;
    }
    static Date parseMonday(String value) throws Exception {
        if (!value.matches("20[0-9]{2}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
        Date date = dateFormat().parse(value);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")); calendar.setTime(date);
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) throw new IllegalArgumentException();
        return date;
    }
    private int currentWeek() {
        if (monday.isEmpty()) return 0;
        try {
            // Compare local calendar dates at UTC midnight, unaffected by daylight-saving shifts.
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
            long days = (dateFormat().parse(today).getTime() - parseMonday(monday).getTime()) / 86400000L;
            return (int) Math.floorDiv(days, 7L) + 1;
        } catch (Exception invalid) { return 0; }
    }
    private String dayDate(int day) {
        if (monday.isEmpty()) return "";
        try {
            Date date = new Date(parseMonday(monday).getTime() + ((week - 1L) * 7L + day) * 86400000L);
            return " · " + dateFormat().format(date).substring(5);
        } catch (Exception invalid) { return ""; }
    }
    static void validate(JSONObject course) throws Exception {
        String name = course.getString("name").trim();
        if (name.isEmpty() || name.length() > 80 || course.getString("room").length() > 100
                || course.getString("teacher").length() > 60) throw new IllegalArgumentException();
        int day = course.getInt("day"), first = course.getInt("first"), last = course.getInt("last");
        int from = course.getInt("from"), to = course.getInt("to"), parity = course.getInt("parity");
        if (day < 0 || day > 6 || first < 1 || last < first || last > 16
                || from < 1 || to < from || to > 30 || parity < 0 || parity > 2) throw new IllegalArgumentException();
        if (from == to && parity != 0 && from % 2 != (parity == 1 ? 1 : 0)) throw new IllegalArgumentException();
        CourseWeeks.validate(course);
        // 0 means "pick a colour from the course name"; 1..PALETTE is an explicit choice.
        int color = course.optInt("color", 0);
        if (color < 0 || color > WeekBoard.PALETTE) throw new IllegalArgumentException();
    }
    private boolean appears(JSONObject course) { return appears(course, week); }
    /** Whether a course runs in a given week. Split out because the 今日 tab asks about this week. */
    private static boolean appears(JSONObject course, int targetWeek) {
        return CourseWeeks.includes(course, targetWeek);
    }
    /** Today as a column index: Monday 0 … Sunday 6. */
    private static int todayIndex() { return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7; }
    private static String todayIso() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }

    private void notice(String message){new AlertDialog.Builder(this).setMessage(message).setPositiveButton("知道了",null).show();}
    private void document(String action,String type,int code){
        Intent intent=new Intent(action).setType(type).addCategory(Intent.CATEGORY_OPENABLE);
        if(action.equals(Intent.ACTION_CREATE_DOCUMENT))intent.putExtra(Intent.EXTRA_TITLE,"CampusKit-课表-"+todayIso()+".json");
        try{startActivityForResult(intent,code);}catch(Exception e){notice("无法打开系统文件选择器");}
    }
    private void backgroundMenu(){new AlertDialog.Builder(this).setTitle("课表背景").setItems(new String[]{"选择背景图片","恢复默认背景"},(d,index)->{
        if(index==0)document(Intent.ACTION_OPEN_DOCUMENT,"image/*",80);
        else if(store.edit().remove("background").commit()){backgroundImage=null;render();}else notice("保存失败，请重试");
    }).setNegativeButton("取消",null).show();}
    private void widgetHelp(){new AlertDialog.Builder(this).setTitle("添加桌面课表")
        .setMessage("如果没有出现添加窗口，请在系统设置中允许 CampusKit 的桌面快捷方式权限，再返回重试。红米路径：权限管理 → 其他权限 → 桌面快捷方式 → 始终允许。也可在桌面小部件列表的安卓小部件中查找 CampusKit。")
        .setPositiveButton("打开应用设置",(d,w)->{try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception e){notice("请在系统设置中找到 CampusKit 的权限管理。");}}).setNegativeButton("关闭",null).show();}
    private void addScheduleWidget(){new AlertDialog.Builder(this).setTitle("桌面课表尺寸").setItems(new String[]{"2 × 2 · 当前 / 下一节","3 × 2 · 今日课程","4 × 2 · 宽版课表"},(d,index)->pinScheduleWidget(new Class<?>[]{ScheduleWidget.class,ScheduleWidgetMedium.class,ScheduleWidgetWide.class}[index])).setNegativeButton("取消",null).show();}
    private void pinScheduleWidget(Class<?> widgetType){
        android.appwidget.AppWidgetManager manager=android.appwidget.AppWidgetManager.getInstance(this);
        android.content.ComponentName provider=new android.content.ComponentName(this,widgetType);
        try{
            if(android.os.Build.VERSION.SDK_INT>=26&&manager.isRequestPinAppWidgetSupported()){
                int before=manager.getAppWidgetIds(provider).length;
                boolean accepted=manager.requestPinAppWidget(provider,null,null);
                if(!accepted){widgetHelp();return;}
                android.widget.Toast.makeText(this,"已请求添加课表组件",android.widget.Toast.LENGTH_SHORT).show();
                clockHandler.postDelayed(()->{if(!isDestroyed()&&!isFinishing()&&hasWindowFocus()&&manager.getAppWidgetIds(provider).length==before)widgetHelp();},1800);
            }else widgetHelp();
        }catch(Exception failure){widgetHelp();}
    }
    private void handleDocument(int request,int result,Intent data){
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();importing=true;render();
        pdfWorker.execute(()->{
            try{
                if(request==81){JSONObject backup=TimetableBackup.exportData(store);try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new java.io.IOException();out.write(backup.toString().getBytes("UTF-8"));}
                    runOnUiThread(()->{if(isDestroyed())return;importing=false;render();notice("备份已导出，包含课表、配色、开学日期和背景图片。");});
                }else if(request==80){String encoded=TimetableBackup.image(TimetableBackup.read(getContentResolver().openInputStream(uri),20*1024*1024));
                    if(!store.edit().putString("background",encoded).commit())throw new java.io.IOException();
                    final android.graphics.Bitmap bitmap=TimetableBackup.decode(encoded);
                    runOnUiThread(()->{if(isDestroyed())return;importing=false;backgroundImage=bitmap;render();});
                }else{JSONObject backup=TimetableBackup.parse(TimetableBackup.read(getContentResolver().openInputStream(uri),6*1024*1024));int count=new JSONArray(backup.getString("courses")).length();
                    runOnUiThread(()->{if(isDestroyed())return;importing=false;render();new AlertDialog.Builder(this).setTitle("恢复课表备份")
                        .setMessage("备份含 "+count+" 条课程，开学日期："+backup.optString("monday")+"。恢复将替换现有课表、配色、备注和背景。")
                        .setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->{try{if(!TimetableBackup.restore(store,backup))throw new java.io.IOException();recreate();}catch(Exception e){notice("恢复失败，未能保存备份。");}}).show();});}
            }catch(Exception failure){runOnUiThread(()->{if(isDestroyed())return;importing=false;render();notice(request==80?"图片无法读取，请选择20 MB以内的图片。":request==81?"备份写入失败，请重新选择保存位置。":"备份无法读取或格式无效，原课表未修改。");});}
        });
    }

    private void manage() {
        if(unreadable || importing)return;
        new AlertDialog.Builder(this).setTitle("我的课表").setItems(new String[]{"从教务系统导入", "开学日期", "添加课程", "全部课程（"+courses.size()+"）", "自定义背景图片", "添加桌面课表组件", "导出备份", "恢复备份", "查成绩", "查空教室"}, (d,index)-> {
            if(index==0)startActivityForResult(new Intent(this,TimetableWebActivity.class),41);
            else if(index==1)semester();else if(index==2)edit(null);else if(index==3)allCourses();
            else if(index==4)backgroundMenu();else if(index==5)addScheduleWidget();else if(index==6)document(Intent.ACTION_CREATE_DOCUMENT,"application/json",81);else if(index==7)document(Intent.ACTION_OPEN_DOCUMENT,"*/*",82);
            else if(index==8)openEdu("grades");else if(index==9)openEdu("rooms");
        }).setNegativeButton("关闭",null).show();
    }
    /**
     * Button-driven week change. The board slides out the same way a swipe does, then the new week
     * animates in from the opposite side, so buttons and gestures feel identical.
     */
    private void shiftWeek(int delta) {
        int next=Math.max(1,Math.min(30,week+delta));if(next==week)return;
        if(pager==null){applyWeek(delta);return;}
        pager.animateOutThen(delta,()->applyWeek(delta));
    }
    /** Commits the adjacent week while preserving the vertical reading position. */
    private void applyWeek(int delta) {
        int next=Math.max(1,Math.min(30,week+delta));if(next==week)return;
        int scroll=pager==null?0:pager.verticalPosition();
        week=next;render();
        if(pager!=null)pager.restoreVerticalPosition(scroll);
        root.announceForAccessibility("第 "+week+" 周");
    }
    private void selectWeek() {
        CoursePickers.selectWeek(this,week,selected->{int delta=selected-week;if(delta!=0)applyWeek(delta);});
    }
    private void goCurrentWeek() {
        if(currentWeek()<1 || currentWeek()>30){semester();return;}
        int delta=currentWeek()-week;
        if(delta==0){android.widget.Toast.makeText(this,"已经是本周",android.widget.Toast.LENGTH_SHORT).show();return;}
        applyWeek(delta);
    }
    private void showCourses(List<JSONObject> group) {
        if(group.size()>1) {
            String[] names=new String[group.size()];for(int i=0;i<names.length;i++)names[i]=group.get(i).optString("name");
            new AlertDialog.Builder(this).setTitle("此时段有重叠课程").setItems(names,(d,n)->showCourses(java.util.Collections.singletonList(group.get(n)))).setNegativeButton("关闭",null).show();return;
        }
        JSONObject c=group.get(0);
        String detail=DAYS[c.optInt("day")]+"  ·  第 "+c.optInt("first")+"—"+c.optInt("last")+" 节\n\n"+c.optString("room")+"\n"+c.optString("teacher")+"\n\n"+CourseWeeks.label(c);
        new AlertDialog.Builder(this).setTitle(c.optString("name")).setMessage(detail).setPositiveButton("编辑课程",(d,w)->edit(c)).setNegativeButton("关闭",null).show();
    }
    private android.graphics.drawable.GradientDrawable rounded(int color,int radius) {
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;
    }
    private void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private TextView bold(String value,int size){TextView view=text(value,size);view.setTypeface(null,android.graphics.Typeface.BOLD);return view;}

    /** One row of the 趣智校园 list: badge, title, description, chevron. */
    private void campusEntry(LinearLayout parent,String badge,int tint,String title,String subtitle,Runnable action,boolean divider) {
        LinearLayout line=new LinearLayout(this);line.setGravity(android.view.Gravity.CENTER_VERTICAL);
        line.setPadding(dp(16),dp(14),dp(14),dp(14));
        line.setOnClickListener(v->action.run());line.setFocusable(true);
        android.widget.ImageView mark=new android.widget.ImageView(this);mark.setImageDrawable(AppUi.icon(this,badge.equals("慧")?"water":"user",tint,24));mark.setBackground(AppUi.shape(this,AppUi.SOFT,12));mark.setPadding(dp(8),dp(8),dp(8),dp(8));mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);line.addView(mark,new LinearLayout.LayoutParams(dp(40),dp(40)));AppUi.press(line,0xffffffff,8);
        LinearLayout labels=column();labels.setPadding(dp(14),0,0,0);
        labels.addView(bold(title,16));
        TextView caption=text(subtitle,12);caption.setTextColor(AppUi.MUTED);caption.setPadding(0,dp(2),0,0);labels.addView(caption);
        line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView chevron=text("›",20);chevron.setTextColor(0xffb3bcc9);line.addView(chevron);
        line.setContentDescription(title+"，"+subtitle);
        parent.addView(line,new LinearLayout.LayoutParams(-1,-2));
        if(divider){View rule=new View(this);rule.setBackgroundColor(AppUi.LINE);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,Math.max(1,dp(1)));
            params.setMargins(dp(70),0,0,0);parent.addView(rule,params);}
    }
    private TextView tabItem(String label,boolean selected,Runnable action) {
        TextView item=bold(label,12);
        item.setTextColor(selected?AppUi.ACCENT:AppUi.MUTED);
        item.setGravity(android.view.Gravity.CENTER);item.setSelected(selected);item.setCompoundDrawables(null,AppUi.icon(this,label.equals("今日")?"today":label.equals("课表")?"calendar":"user",selected?AppUi.ACCENT:AppUi.MUTED,22),null,null);item.setCompoundDrawablePadding(dp(4));item.setPadding(0,dp(6),0,dp(6));AppUi.press(item,selected?AppUi.SOFT:0x00ffffff,18);
        
        item.setOnClickListener(v->action.run());
        item.setContentDescription(selected?label+"，已选中":label);
        return item;
    }
    private void render() {
        pager=null;
        root=column();root.setBackgroundColor(AppUi.BG);root.setPadding(dp(8),dp(8),dp(8),0);
        if(android.os.Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(8)+bars.left,dp(8)+bars.top,dp(8)+bars.right,bars.bottom);return insets;});
        }
        // One header shape for all three tabs, so 今日 / 课表 / 趣智校园 read as views of the same app.
        LinearLayout header=new LinearLayout(this);header.setGravity(android.view.Gravity.CENTER_VERTICAL);header.setPadding(dp(8),0,dp(8),0);
        LinearLayout heading=column();
        heading.addView(bold(tab==0?"今日":tab==1?"课表":"账号",tab==1?20:28));
        String subtitle;
        if(tab==0)subtitle=DAYS[todayIndex()]+" · "+todayIso()+(currentWeek()>=1&&currentWeek()<=30?" · 第 "+currentWeek()+" 周":"");
        else if(tab==1)subtitle=monday.isEmpty()?"设置开学日期，自动定位本周":(dayDate(0).replace(" · ",""))+" — "+dayDate(6).replace(" · ","");
        else subtitle="校园服务账号";
        TextView caption=text(subtitle,12);caption.setTextColor(AppUi.MUTED);caption.setPadding(0,0,0,dp(6));heading.addView(caption);
        if(tab==1&&monday.isEmpty()&&!unreadable)caption.setOnClickListener(v->semester());
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if(tab==1){Button settings=weekAction(importing?"识别中…":"管理",this::manage);settings.setEnabled(!unreadable&&!importing);header.addView(settings,new LinearLayout.LayoutParams(dp(76),dp(48)));}
        else if(tab==2&&campusView==1){TextView back=text("‹ 返回",14);back.setTextColor(AppUi.ACCENT);back.setPadding(dp(10),dp(10),0,dp(10));back.setOnClickListener(v->{campusView=0;render();});header.addView(back);}
        root.addView(header);
        if(tab==0)todayContent();else if(tab==1)timetableContent();else campusContent();
        LinearLayout holder=column();holder.setPadding(dp(8),dp(2),dp(8),dp(8));
        LinearLayout bar=new LinearLayout(this);bar.setBackground(AppUi.outlined(this,0xffffffff,24,AppUi.LINE));bar.setElevation(dp(3));bar.setPadding(dp(5),dp(5),dp(5),dp(5));
        bar.addView(tabItem("今日",tab==0,()->{if(tab!=0){tab=0;campusView=0;render();}}),new LinearLayout.LayoutParams(0,dp(60),1));
        bar.addView(tabItem("课表",tab==1,()->{if(tab!=1){tab=1;campusView=0;render();}}),new LinearLayout.LayoutParams(0,dp(60),1));
        bar.addView(tabItem("账号",tab==2,()->{if(tab!=2){tab=2;render();}}),new LinearLayout.LayoutParams(0,dp(60),1));
        holder.addView(bar,new LinearLayout.LayoutParams(-1,-2));root.addView(holder,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);root.requestApplyInsets();
    }

    private TextView cardTitle(String value){return bold(value,16);}
    private LinearLayout whiteCard(){LinearLayout card=column();card.setBackground(AppUi.outlined(this,0xffffffff,18,AppUi.LINE));card.setPadding(dp(18),dp(16),dp(18),dp(16));return card;}
    private TextView cardAction(String label,Runnable action) {
        TextView view=text(label,13);view.setTextColor(AppUi.ACCENT);view.setPadding(dp(10),dp(6),0,dp(6));
        view.setMinHeight(dp(48));view.setGravity(android.view.Gravity.CENTER_VERTICAL);AppUi.press(view,0x00ffffff,8);view.setOnClickListener(v->action.run());return view;
    }
    /** A card heading with an optional action link on the right. */
    private LinearLayout cardHeader(LinearLayout card,String title,String action,Runnable onClick) {
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(cardTitle(title),new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null)row.addView(cardAction(action,onClick));
        card.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;
    }

    private void todayContent() {
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content=column();scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        shortcutsCard(content);gap(content,14);
        academicCard(content);gap(content,14);
        if(pickupWidget.hasPickup()){pickupCard(content);gap(content,14);}
        todayCourses(content);gap(content,18);
        if(!pickupWidget.hasPickup()){pickupCard(content);gap(content,14);}

    }

    /** Today's actual classes: the current week, the current weekday, in section order. */
    private void pickupCard(LinearLayout parent) {
        LinearLayout card=whiteCard();
        View widget = null;
        try { widget = pickupWidget.view(); } catch (RuntimeException unavailable) { /* Offer recovery below. */ }
        boolean added = pickupWidget.added();
        cardHeader(card,"取件信息",added?"设置":"添加菜鸟组件",()->{
            if(pickupWidget.added()) {
                pickupWidget.settings();
            } else { pickupWidget.add(); render(); }
        });gap(card,8);
        if(widget!=null) card.addView(widget,new LinearLayout.LayoutParams(-1,dp(180)));
        else card.addView(body(added?"菜鸟组件暂不可用，可移除后重新添加。":"添加菜鸟组件，查看待取包裹。"));
        parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
    }

    private void todayCourses(LinearLayout parent) {
        LinearLayout card=whiteCard();
        int todayWeek=currentWeek();
        cardHeader(card,"今天的课","查看课表",()->{tab=1;week=Math.max(1,Math.min(30,currentWeek()));render();});gap(card,10);
        if(unreadable) {
            card.addView(body("本机课表无法完整读取，已保留原数据。"));
        } else if(monday.isEmpty()) {
            card.addView(body("还没设置开学日期，无法判断今天属于第几周。"));
            gap(card,10);card.addView(cardAction("去设置开学日期 ›",this::semester));
        } else if(todayWeek<1||todayWeek>30) {
            card.addView(body("今天不在课表范围内（第 "+todayWeek+" 周，仅支持 1–30 周）。"));
        } else {
            List<JSONObject> todayList=new ArrayList<>();
            for(JSONObject course:courses)if(course.optInt("day")==todayIndex()&&appears(course,todayWeek))todayList.add(course);
            todayList.sort((a,b)->Integer.compare(a.optInt("first"),b.optInt("first")));
            if(todayList.isEmpty()) {
                card.addView(body("今天没有课程"));
            } else {
                TextView next=bold(ScheduleInfo.summary(todayList,ScheduleInfo.minutes()),14);next.setTextColor(AppUi.ACCENT);card.addView(next);gap(card,12);
                Map<String,Integer> auto=WeekBoard.autoColorsFor(courses);
                for(int i=0;i<todayList.size();i++) {
                    JSONObject course=todayList.get(i);
                    if(i>0)gap(card,10);
                    card.addView(courseRow(course,auto),new LinearLayout.LayoutParams(-1,-2));
                }
            }
        }
        parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
    }

    /** One class on the 今日 tab: name plus section range, room and teacher, tinted with its own colour. */
    private View courseRow(JSONObject course,Map<String,Integer> auto) {
        int index=WeekBoard.colorIndexOf(course,auto),ink=io.qzxy.lite.core.TimetablePalette.ink(index+1);
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.TOP);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setBackground(AppUi.shape(this,WeekBoard.FILLS[index],14));
        String status=ScheduleInfo.status(course,ScheduleInfo.minutes());boolean ended=status.equals("已结束");
        String time=ClassTimes.range(course.optInt("first"),course.optInt("last"));String[] span=time.split("[–—-]");
        LinearLayout times=column();TextView first=bold(span.length>0?span[0]:"",14);times.addView(first);
        if(span.length>1){TextView last=text(span[1],12);last.setTextColor(AppUi.MUTED);times.addView(last);}
        row.addView(times,new LinearLayout.LayoutParams(dp(56),-2));
        View marker=new View(this);marker.setBackground(AppUi.shape(this,ink,2));LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(dp(3),dp(40));ml.setMargins(0,dp(2),dp(12),0);row.addView(marker,ml);
        LinearLayout labels=column();TextView name=bold(course.optString("name"),16);name.setTextColor(ink);if(ended)name.setPaintFlags(name.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);labels.addView(name);
        String detail=course.optString("room")+" · 第 "+course.optInt("first")+"–"+course.optInt("last")+" 节";TextView room=text(detail,12);room.setTextColor(ink);room.setAlpha(.8f);room.setPadding(0,dp(2),0,0);labels.addView(room);
        if(!ended&&status.equals("正在上课")){TextView current=bold("● 正在上课",12);current.setTextColor(ink);current.setPadding(0,dp(4),0,0);labels.addView(current);}
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));if(ended)row.setAlpha(.55f);row.setContentDescription(course.optString("name")+"，"+time+"，"+detail+(ended?"，已结束":""));return row;
    }

    private TextView body(String value) {
        TextView view=text(value,12);view.setTextColor(AppUi.MUTED);return view;
    }

    /** Four compact daily actions, paired by purpose. */
    private void shortcutsCard(LinearLayout parent) {
        LinearLayout card=whiteCard();cardHeader(card,"校园生活",null,null);gap(card,8);
        LinearLayout row=new LinearLayout(this);
        row.addView(shortcutTile("water","慧生活","用水",this::openLife798),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(shortcutTile("scan","趣智校园","扫码",()->startActivity(new Intent(this,ScanActivity.class))),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(shortcutTile("pay","易校园","付款码",this::openCampusPayCode),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(shortcutTile("parcel","菜鸟","出库码",this::openCainiao),new LinearLayout.LayoutParams(0,-2,1));
        card.addView(row);parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
    }
    /** Grades and empty classrooms, read through the school's own academic site. */
    private void academicCard(LinearLayout parent) {
        LinearLayout card=whiteCard();cardHeader(card,"教务",null,null);gap(card,8);
        LinearLayout row=new LinearLayout(this);
        row.addView(shortcutTile("score","成绩","查询",()->openEdu("grades")),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(shortcutTile("room","空教室","查询",()->openEdu("rooms")),new LinearLayout.LayoutParams(0,-2,1));
        card.addView(row);parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
    }
    private void openEdu(String mode){
        try{startActivity(new Intent(this,AcademicActivity.class).putExtra("mode",mode));}
        catch(RuntimeException unavailable){notice("无法打开教务页面。");}
    }
    private View shortcutTile(String icon,String title,String subtitle,Runnable action) {        LinearLayout tile=column();tile.setGravity(android.view.Gravity.CENTER);tile.setPadding(dp(4),dp(10),dp(4),dp(10));tile.setMinimumHeight(dp(96));AppUi.press(tile,0xffffffff,10);tile.setOnClickListener(v->action.run());
        android.widget.ImageView mark=new android.widget.ImageView(this);mark.setImageDrawable(AppUi.icon(this,icon,AppUi.ACCENT,24));mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);mark.setBackground(AppUi.shape(this,AppUi.SOFT,22));mark.setPadding(dp(10),dp(10),dp(10),dp(10));tile.addView(mark,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView name=bold(title,13);name.setGravity(android.view.Gravity.CENTER);name.setPadding(0,dp(8),0,0);tile.addView(name);
        TextView hint=text(subtitle,12);hint.setTextColor(AppUi.MUTED);hint.setGravity(android.view.Gravity.CENTER);tile.addView(hint);tile.setContentDescription(title+"，"+subtitle);return tile;
    }

    private Button weekAction(String label,Runnable action) {
        Button view=button(label,action);view.setTextColor(AppUi.INK);view.setTextSize(14);
        view.setMinWidth(0);view.setMinimumWidth(0);view.setPadding(dp(8),0,dp(8),0);
        AppUi.press(view,0x00ffffff,8);return view;
    }
    private void timetableContent() {
        LinearLayout navigation=new LinearLayout(this);navigation.setGravity(android.view.Gravity.CENTER_VERTICAL);navigation.setPadding(dp(4),0,dp(4),dp(4));
        Button selected=weekAction("第 "+week+" 周  ▾",this::selectWeek);selected.setTextSize(18);selected.setGravity(android.view.Gravity.LEFT|android.view.Gravity.CENTER_VERTICAL);selected.setContentDescription("选择周次，当前第"+week+"周");
        navigation.addView(selected,new LinearLayout.LayoutParams(0,dp(48),1));
        Button previous=weekAction("‹",()->shiftWeek(-1));previous.setTextSize(26);previous.setContentDescription("上一周");previous.setEnabled(week>1);
        Button next=weekAction("›",()->shiftWeek(1));next.setTextSize(26);next.setContentDescription("下一周");next.setEnabled(week<30);
        navigation.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));
        navigation.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));
        if(week!=currentWeek()&&currentWeek()>=1&&currentWeek()<=30)navigation.addView(weekAction("回本周",this::goCurrentWeek),new LinearLayout.LayoutParams(dp(76),dp(48)));
        root.addView(navigation);
        List<JSONObject> visible=new ArrayList<>();for(JSONObject c:courses)if(appears(c))visible.add(c);
        WeekBoard.Listener listener=new WeekBoard.Listener(){
            public void open(List<JSONObject> group){showCourses(group);}
            public void shift(int delta){shiftWeek(delta);}
            public void swipe(int delta){applyWeek(delta);}
            public boolean canShift(int delta){int next=week+delta;return next>=1&&next<=30;}
            public void addAt(int day,int section){edit(null,day,section);}
        };
        if(unreadable)root.addView(text("课表读取失败，原数据已保留。可从管理中恢复备份。",14));
        else if(courses.isEmpty()) {TextView hint=text("还没有课程 · 点击右上角管理，导入教务课表",12);hint.setGravity(android.view.Gravity.CENTER);root.addView(hint);}
        else if(visible.isEmpty()) {TextView hint=text("这一周没有课程",12);hint.setGravity(android.view.Gravity.CENTER);root.addView(hint);}
        // Colours are resolved from the whole course list, not just this week, so a course keeps its
        // colour while paging through weeks.
        final int shownWeek=week;
        pager=new WeekBoard.Pager(this,delta->{
            int targetWeek=shownWeek+delta;
            List<JSONObject> adjacent=new ArrayList<>();
            if(!unreadable)for(JSONObject c:courses)if(appears(c,targetWeek))adjacent.add(c);
            String[] adjacentDates=new String[7];
            for(int d=0;d<7;d++) {
                adjacentDates[d]="";
                if(!monday.isEmpty())try {
                    Date date=new Date(parseMonday(monday).getTime()+((targetWeek-1L)*7+d)*86400000L);
                    adjacentDates[d]=dateFormat().format(date).substring(5);
                }catch(Exception invalid){}
            }
            WeekBoard board=new WeekBoard(this,adjacent,adjacentDates,targetWeek==currentWeek()?(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)+5)%7:-1,
                WeekBoard.autoColorsFor(unreadable?new ArrayList<>():courses),listener);
            board.setPhoto(backgroundImage);return board;
        },listener);
        root.addView(pager,new LinearLayout.LayoutParams(-1,0,1));
        
    }

    private void openQzxy(){
        Intent intent=getPackageManager().getLaunchIntentForPackage("com.klcxkj.zqxy");
        if(intent==null){notice("请先安装趣智校园原版应用。");return;}
        try{startActivity(intent);}catch(Exception e){notice("无法打开趣智校园，请检查原版是否已安装并启用。");}
    }
    private void campusContent() {
        ScrollView scroll=new ScrollView(this);LinearLayout content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout card=whiteCard();
        campusEntry(card,"账",AppUi.ACCENT,"趣智校园","浴室、吹风机",()->startActivity(new Intent(this,AccountInfoActivity.class)),true);
        campusEntry(card,"慧",0xff37668b,"慧生活798","饮水",()->startActivity(new Intent(this,AccountInfoActivity.class).putExtra("life798",true)),false);
        gap(card,10);card.addView(body("CampusKit 1.0.9 · 第三方校园客户端"));content.addView(card);
    }

    private void records(LinearLayout parent) {
        if(pendingNote!=null&&!pendingNote.isEmpty()) {
            LinearLayout panel=column();panel.setPadding(dp(16),dp(14),dp(16),dp(14));panel.setBackground(rounded(0xfffff7ed,14));
            panel.addView(bold("待核实的用水记录",16));gap(panel,8);
            TextView detail=text("这条记录来自上一次没有查清的订单。它描述的是过去，不是现在，必须重新联网查询订单状态才能判断。",12);
            detail.setTextColor(0xff7c4a12);panel.addView(detail);
            parent.addView(panel,new LinearLayout.LayoutParams(-1,-2));gap(parent,14);
        }
        LinearLayout panel=column();panel.setPadding(dp(16),dp(14),dp(16),dp(14));panel.setBackground(rounded(0xffffffff,14));
        panel.addView(bold("消费记录尚未接入",16));gap(panel,8);
        TextView body=text("读取趣智校园账单需要先确认账单接口的真实返回结构。接入后这里会按时间列出每次用水的时长与扣费。",12);
        body.setTextColor(AppUi.MUTED);panel.addView(body);gap(panel,10);
        TextView more=text("当前可用的是扫码页的只读订单查询，它会告诉你设备是否空闲、是否是你的订单。",12);
        more.setTextColor(AppUi.MUTED);panel.addView(more);
        parent.addView(panel,new LinearLayout.LayoutParams(-1,-2));
    }
    /**
     * A row of colour swatches for the course editor. Index 0 is "自动", which keeps the colour derived
     * from the course name; 1..PALETTE pin an explicit colour. Each swatch previews its own ink, so a
     * choice can be judged by readability, not just hue. The returned array holds the live selection.
     */
    private int[] colorPicker(LinearLayout parent, int selected) {
        parent.addView(text("课程颜色", 14));
        final int[] choice = {selected};
        final List<TextView> marks = new ArrayList<>();
        LinearLayout row = new LinearLayout(this);
        int columns=Math.max(3,Math.min(6,(getResources().getDisplayMetrics().widthPixels-dp(88))/dp(56)));
        int size = dp(48);
        for (int index = 0; index <= WeekBoard.PALETTE; index++) {
            if(index%columns==0){row=new LinearLayout(this);parent.addView(row);}
            final int value = index;
            TextView swatch = new TextView(this);
            swatch.setGravity(android.view.Gravity.CENTER); swatch.setTextSize(13);
            swatch.setTypeface(null, android.graphics.Typeface.BOLD); swatch.setIncludeFontPadding(false);
            swatch.setText(index == 0 ? "自" : "");
            swatch.setContentDescription(index == 0 ? "自动配色" : io.qzxy.lite.core.TimetablePalette.name(index));
            swatch.setOnClickListener(v -> { choice[0] = value; paint(marks, value); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(swatch, params); marks.add(swatch);
        }

        paint(marks, selected);
        return choice;
    }
    /** Re-strokes the swatches so the current selection is obvious without relying on colour alone. */
    private void paint(List<TextView> marks, int selected) {
        for (int index = 0; index < marks.size(); index++) {
            TextView swatch = marks.get(index);
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setColor(WeekBoard.fill(index)); shape.setCornerRadius(dp(13));
            if (index == selected) shape.setStroke(dp(2), AppUi.INK);
            else shape.setStroke(dp(1), 0x22000000);
            swatch.setBackground(shape); swatch.setTextColor(WeekBoard.ink(index));
            if (index > 0) swatch.setText(index == selected ? "✓" : "");
        }
    }
    private EditText input(LinearLayout parent, String title, String value, boolean number) {
        parent.addView(text(title, 14)); EditText field = new EditText(this); field.setSingleLine(true); field.setText(value);
        field.setInputType(number ? android.text.InputType.TYPE_CLASS_NUMBER : android.text.InputType.TYPE_CLASS_TEXT);
        parent.addView(field); return field;
    }
    private Spinner choices(LinearLayout parent, String[] values, int selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(selected); parent.addView(spinner); return spinner;
    }
    private void edit(JSONObject original) { edit(original, -1, -1); }
    /**
     * @param defaultDay     weekday to pre-select for a new course, or -1 to keep the old default
     * @param defaultSection starting section to pre-fill for a new course, or -1
     */
    private void edit(JSONObject original, int defaultDay, int defaultSection) {
        LinearLayout form = column(); form.setPadding(dp(20), dp(8), dp(20), dp(8));
        JSONObject old = original == null ? new JSONObject() : original;
        int dayDefault = defaultDay >= 0 ? defaultDay : 0;
        int firstDefault = defaultSection >= 0 ? defaultSection : 1;
        int lastDefault = defaultSection >= 0 ? Math.min(defaultSection + 1, 16) : 2;
        EditText name = input(form, "课程名称（必填）", old.optString("name"), false);
        EditText room = input(form, "教室（可选）", old.optString("room"), false);
        EditText teacher = input(form, "教师（可选）", old.optString("teacher"), false);
        int[] time = {old.optInt("day", dayDefault), old.optInt("first", firstDefault), old.optInt("last", lastDefault)};
        boolean[][] selectedWeeks = {CourseWeeks.selection(old)};
        form.addView(text("上课时间", 14));
        Button timeButton = CoursePickers.button(this, "", false); form.addView(timeButton);
        Runnable showTime = () -> timeButton.setText(DAYS[time[0]] + "  " + time[1] + "–" + time[2] + "节（" + (time[2]-time[1]+1) + "节）"); showTime.run();
        timeButton.setOnClickListener(v -> CoursePickers.time(this,time[0],time[1],time[2],(day,first,last)->{time[0]=day;time[1]=first;time[2]=last;showTime.run();}));
        form.addView(text("上课周次", 14));
        Button weeksButton = CoursePickers.button(this, CourseWeeks.label(selectedWeeks[0]), false); form.addView(weeksButton);
        weeksButton.setOnClickListener(v -> CoursePickers.weeks(this,selectedWeeks[0],value->{selectedWeeks[0]=value;weeksButton.setText(CourseWeeks.label(value));}));
        int[] color = colorPicker(form, old.optInt("color", 0));
        TextView error = text("", 14); error.setTextColor(0xffb3261e); form.addView(error);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(original == null ? "添加课程" : "编辑课程")
            .setView(scroll).setPositiveButton("保存", null).setNegativeButton("取消", null)
            .setNeutralButton(original == null ? null : "删除课程", (d, w) -> delete(original)).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                JSONObject course = new JSONObject();
                course.put("name", name.getText().toString().trim()); course.put("room", room.getText().toString().trim());
                course.put("teacher", teacher.getText().toString().trim()); course.put("day", time[0]);
                course.put("first", time[1]); course.put("last", time[2]);
                CourseWeeks.put(course, selectedWeeks[0]); course.put("color", color[0]);
                validate(course);
                List<JSONObject> replacement = new ArrayList<>(courses);
                if (original == null) replacement.add(course); else replacement.set(courses.indexOf(original), course);
                if (replacement.size() > 500) { error.setText("最多保存 500 条课程。"); return; }
                if (!save(replacement, monday)) { error.setText("保存失败，请重试。"); return; }
                dialog.dismiss(); render();
            } catch (Exception invalid) { error.setText("请填写课程名和有效节次、周次；结束不能早于开始，单双周须包含实际上课周。"); }
        })); dialog.show();
    }
    private boolean save(List<JSONObject> replacement, String start) {
        JSONArray data = new JSONArray(); for (JSONObject course : replacement) data.put(course);
        if (!store.edit().putString("courses", data.toString()).putString("monday", start).commit()) return false;
        courses.clear(); courses.addAll(replacement); monday = start; return true;
    }
    private void delete(JSONObject course) {
        new AlertDialog.Builder(this).setTitle("删除这条课程？").setMessage(course.optString("name"))
            .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
                List<JSONObject> replacement = new ArrayList<>(courses); replacement.remove(course);
                if (save(replacement, monday)) render();
                else new AlertDialog.Builder(this).setMessage("删除未能保存，请重试。").setPositiveButton("知道了", null).show();
            }).show();
    }
    private void allCourses() {
        if (courses.isEmpty()) { edit(null); return; }
        Map<String,Integer> auto = WeekBoard.autoColorsFor(courses);
        List<JSONObject> ordered=new ArrayList<>(courses);
        ordered.sort((a,b)->{int day=Integer.compare(a.optInt("day"),b.optInt("day"));return day!=0?day:Integer.compare(a.optInt("first"),b.optInt("first"));});
        LinearLayout list=column();list.setPadding(dp(16),dp(8),dp(16),dp(16));
        ScrollView scroll=new ScrollView(this);scroll.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("全部课程 · "+courses.size()+" 条").setView(scroll).setNegativeButton("关闭",null).create();
        int previous=-1;
        for(JSONObject c:ordered){
            int day=c.optInt("day");if(day!=previous){TextView heading=text(DAYS[day],14);heading.setTextColor(AppUi.MUTED);list.addView(heading);previous=day;}
            int explicit=c.optInt("color");Integer derived=auto.get(c.optString("name"));int index=explicit>0?explicit:(derived==null?1:derived+1);
            LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));AppUi.press(row,AppUi.BG,12);
            View stripe=new View(this);stripe.setBackground(AppUi.shape(this,WeekBoard.ink(index),2));row.addView(stripe,new LinearLayout.LayoutParams(dp(4),dp(48)));
            LinearLayout content=column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(12);row.addView(content,cp);
            TextView title=text(c.optString("name"),16);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setPadding(0,0,0,dp(5));content.addView(title);
            TextView time=text("第 "+c.optInt("first")+"–"+c.optInt("last")+" 节  ·  "+ClassTimes.range(c.optInt("first"),c.optInt("last")),13);time.setTextColor(AppUi.MUTED);time.setPadding(0,0,0,dp(4));content.addView(time);
            String room=c.optString("room"),teacher=c.optString("teacher");String location=room+(room.isEmpty()||teacher.isEmpty()?"":" · ")+teacher;if(!location.isEmpty()){TextView detail=text(location,13);detail.setTextColor(AppUi.MUTED);detail.setPadding(0,0,0,dp(4));content.addView(detail);}
            TextView weeks=text(CourseWeeks.label(c),12);weeks.setTextColor(AppUi.MUTED);weeks.setPadding(0,0,0,0);content.addView(weeks);
            row.setContentDescription(c.optString("name")+"，点击编辑");row.setOnClickListener(v->{dialog.dismiss();edit(c);});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);list.addView(row,rp);
        }
        dialog.show();
    }
    private void semester() {
        LinearLayout form = column(); form.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText date = input(form, "第一周的周一（例如 2026-09-07，以学校安排为准）", monday, false);
        TextView error = text("", 14); form.addView(error);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("学期设置").setView(form)
            .setPositiveButton("保存", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String value = date.getText().toString().trim(); parseMonday(value);
                if (!save(new ArrayList<>(courses), value)) { error.setText("保存失败，请重试。"); return; }
                week = Math.max(1, Math.min(30, currentWeek())); dialog.dismiss(); render();
            } catch (Exception invalid) { error.setText("请输入有效的周一日期，格式为 年-月-日。"); }
        })); dialog.show();
    }
}
