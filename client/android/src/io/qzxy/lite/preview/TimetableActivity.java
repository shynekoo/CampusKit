package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import io.qzxy.lite.core.MainNavigator;
import io.qzxy.lite.core.PendingUse;
import io.qzxy.lite.core.TimetableIndex;
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
public final class TimetableActivity extends Page implements App.Themed {
    private static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private final List<JSONObject> courses = new ArrayList<>();
    private SharedPreferences store;
    private int week = 1;
    private String lastDay="";
    private android.graphics.Bitmap backgroundImage;
    private final android.os.Handler clockHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private io.qzxy.lite.core.RefreshDecision refreshDecision=new io.qzxy.lite.core.RefreshDecision();
    /** Live references onto 今日's dynamic views, so a clock tick updates text in place instead of
     *  rebuilding the whole tree. Only populated while the 今日 content is on screen. */
    private TextView todaySummary;
    private final java.util.List<TodayRow> todayRows=new java.util.ArrayList<TodayRow>();
    /** One 今日 class row's mutable bits: the strike-through name, the row alpha and the live chip. */
    private static final class TodayRow {
        final JSONObject course; final TextView name; final View row; final TextView live; final String label;
        TodayRow(JSONObject course,TextView name,View row,TextView live,String label){this.course=course;this.name=name;this.row=row;this.live=live;this.label=label;}
    }
    private final Runnable clockTick=new Runnable(){public void run(){if(hasWindowFocus())refreshClock();clockHandler.postDelayed(this,30000);}};
    private final SharedPreferences.OnSharedPreferenceChangeListener timetableChanged=(prefs,key)->ScheduleWidget.updateAll(this);
    private void refreshClock(){
        String today=todayIso();
        boolean dayChanged=!today.equals(lastDay);lastDay=today;
        if(dayChanged)week=Math.max(1,Math.min(CoursePickers.LAST_WEEK,currentWeek()));
        if(root==null)return;
        io.qzxy.lite.core.RefreshDecision.Level level=refreshDecision.forTick(today,nav.tab());
        if(level==io.qzxy.lite.core.RefreshDecision.Level.TODAY_DYNAMIC){
            // Normally an in-place label update suffices. If the course set changed underneath us
            // without a render (e.g. a widget edit), the cached row handles no longer match — fall
            // back to a full rebuild so no stale/mismatched rows are left on screen.
            if(!updateTodayDynamic())level=io.qzxy.lite.core.RefreshDecision.Level.FULL;
        }
        if(level==io.qzxy.lite.core.RefreshDecision.Level.FULL){
            int scroll=0;for(int i=0;i<root.getChildCount();i++)if(root.getChildAt(i) instanceof ScrollView)scroll=root.getChildAt(i).getScrollY();
            render();final int y=scroll;for(int i=0;i<root.getChildCount();i++)if(root.getChildAt(i) instanceof ScrollView){View v=root.getChildAt(i);v.post(()->v.scrollTo(0,y));}
        }
        // NONE: nothing on screen depends on the clock.
    }
    /** Recompute 今日's summary and per-row live state from the existing course data and write them
     *  onto the views kept by todayContent(). No view creation, no setContentView, no scroll reset. */
    private boolean updateTodayDynamic(){
        if(todaySummary==null)return true;            // 今日 content not built (e.g. empty state): nothing to update
        int now=ScheduleInfo.minutes();
        int todayWeek=currentWeek();
        java.util.List<JSONObject> todayList=new java.util.ArrayList<JSONObject>();
        for(JSONObject course:courses)if(course.optInt("day")==todayIndex()&&appears(course,todayWeek))todayList.add(course);
        todayList.sort((a,b)->Integer.compare(a.optInt("first"),b.optInt("first")));
        // If the underlying data no longer matches the handles we kept, a label-only update would
        // show the wrong rows; signal the caller to rebuild instead.
        if(todayList.size()!=todayRows.size())return false;
        for(int i=0;i<todayList.size();i++)
            if(!todayList.get(i).optString("name").equals(todayRows.get(i).course.optString("name"))
               || todayList.get(i).optInt("first")!=todayRows.get(i).course.optInt("first"))return false;
        todaySummary.setText(ScheduleInfo.summary(todayList,now));
        for(TodayRow handle:todayRows){
            String status=ScheduleInfo.status(handle.course,now);
            boolean ended=status.equals("已结束");
            boolean live=status.equals("正在上课");
            int flags=handle.name.getPaintFlags();
            handle.name.setPaintFlags(ended?(flags|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG):(flags&~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
            handle.row.setAlpha(ended?.55f:1f);
            handle.live.setVisibility(live?View.VISIBLE:View.GONE);
            handle.row.setContentDescription(handle.label+(ended?"，已结束":""));
        }
        return true;
    }
    @Override protected void onPause(){accountVisible=false;clockHandler.removeCallbacks(clockTick);super.onPause();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("showToday",false)){nav.selectTab(0);render();}}

    private String monday = "";
    private boolean unreadable;
    private LinearLayout root;
    private boolean importing;
    /** The persistent UI-2 shell; built once in ensureShell, reused for every tab switch. */
    private MainShell shell;
    private AnimatedNavBar navBar;
    /** Scroll position remembered per tab so switching back restores where the reader was. */
    private final int[] tabScroll = new int[3];
    /** Bumped on every content transition so a late callback from an old one cannot remove the new page. */
    private int transitionGeneration;
    private final java.util.List<android.animation.Animator> contentAnimators = new ArrayList<>();
    /** Which side the next board should animate in from: +1 from the right, -1 from the left, 0 none. */
    private WeekBoard.Pager pager;
    /** Navigation state lives in core so the back order is unit-tested; views read it via nav. */
    private MainNavigator nav = new MainNavigator();
    private String pendingNote;
    private boolean signedIn;
    private boolean accountVisible;
    /** De-dupes onResume checks, caches a good result briefly, and keeps network errors distinct
     * from a real logout. Time source is monotonic elapsedRealtime, not the wall clock. */
    private final io.qzxy.lite.core.SessionCheck sessionCheck = new io.qzxy.lite.core.SessionCheck(30000L);
    private static final String CAINIAO_PACKAGE = "com.cainiao.wireless";
    private static final String CAINIAO_HOME_ACTIVITY = "com.cainiao.wireless.homepage.view.activity.HomePageActivity";
    /** 菜鸟裹裹's router path for the same page; only its exported host can be started with it. */
    private static final String CAINIAO_IDENTITY_CODE = "guoguo://go/station_code";
    private static final String TAOBAO_PACKAGE = "com.taobao.taobao";
    /** Opened in this order when the identity code is tapped; 淘宝 owns the page now. */
    private static final String[] PICKUP_FALLBACK_PACKAGES = {TAOBAO_PACKAGE, CAINIAO_PACKAGE, "com.eg.android.AlipayGphone"};
    private final java.util.concurrent.ExecutorService backgroundWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override protected void onDestroy() {
        backgroundWorker.shutdown(); clockHandler.removeCallbacks(clockTick);
        for (Animator anim : contentAnimators) anim.cancel(); contentAnimators.clear();
        if (navBar != null) navBar.cancelAnimations();
        navBar = null; shell = null; pager = null; root = null;
        if(store!=null)store.unregisterOnSharedPreferenceChangeListener(timetableChanged);
        super.onDestroy();
    }

    /** 外观与主题 changed while this page was behind it: draw the same tab again in the new look. */
    @Override public void onThemeChanged() { render(); }

    @Override protected void onResume() {
        super.onResume();
        accountVisible=true;checkQzxySession();
        refreshClock();clockHandler.removeCallbacks(clockTick);clockHandler.postDelayed(clockTick,30000);
        // Coming back from「允许安装未知应用」finishes the install that was waiting on it.
        UpdateCheck.resume(this);

    }

    /** Session expiry is not a question: take them to the 趣智校园 login page itself. */
    private void showExpiredNotice(){if(!accountVisible||isDestroyed()||isFinishing())return;if(!sessionCheck.consumeExpiryNotice())return;startActivity(new Intent(this,AccountActivity.class).putExtra("returnToAccountInfo",true));}
    private void checkQzxySession(){
        showExpiredNotice();
        long now=android.os.SystemClock.elapsedRealtime();
        if(!sessionCheck.shouldStart(now))return;
        backgroundWorker.execute(()->{io.qzxy.lite.core.AccountSession checked=null;boolean expired=false;boolean networkError=false;
            try{checked=new AccountVault(this).load();if(checked!=null)SmsAccountApi.profile(checked);}
            catch(HttpForm.SessionExpired e){try{expired=AccountVault.clearExpired(this,checked);}catch(Exception ignored){}}
            catch(Exception unavailable){networkError=true;/* A network or parsing error is not proof of logout. */}
            final boolean invalid=expired, failed=networkError;
            runOnUiThread(()->{
                long at=android.os.SystemClock.elapsedRealtime();
                if(invalid)sessionCheck.finishExpired(at);
                else if(failed)sessionCheck.finishNetworkError(at);
                else sessionCheck.finishValid(at);
                if(isDestroyed()||isFinishing())return;
                if(invalid){signedIn=false;showExpiredNotice();}
            });
        });
    }

    /**
     * 返回先收内部状态：校区页 → 校区列表 → 今日，之后才是关页面。按当前状态作答，回到今日
     * 首页时返回就真的关页面，那一页才用得上系统的跟手缩小。
     */
    @Override protected boolean backMayStay() { return nav.tab() != 0 || nav.campusView() == 1; }

    @Override public void onBackPressed() {
        if (nav.consumeBack()) { render(); return; }
        super.onBackPressed();
    }

    /**
     * Reads the two local facts 趣智校园 shows: whether an account session is stored, and whether a past
     * query left an unverified record. A record that cannot be read is reported as unreadable rather
     * than hidden, and the view is only rebuilt when something actually changed.
     */
    private void loadLocalState() {
        backgroundWorker.execute(() -> {
            boolean signed = false;
            try { signed = new AccountVault(getApplicationContext()).load() != null; }
            catch (Exception unreadableSession) { /* Shown as "not signed in"; the account screen says more. */ }
            String note = "";
            try {
                PendingUse record = new PendingUseVault(getApplicationContext()).load();
                if (record != null) note = record.describe();
            } catch (Exception unreadableRecord) {
                note = "本机存在一条无法读取的用水记录。请重新扫码核对设备与订单，不要直接开水。";
            }
            final boolean loadedSignedIn = signed; final String loadedNote = note;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                boolean changed = signedIn != loadedSignedIn || !loadedNote.equals(pendingNote == null ? "" : pendingNote);
                signedIn = loadedSignedIn; pendingNote = loadedNote;
                if (changed && (nav.tab() == 2 || nav.tab() == 0)) render();
            });
        });
    }

    /**
     * Opens 菜鸟驿站's 身份码 as 淘宝 → 菜鸟驿站 → 身份码.
     *
     * <p>That page is Taobao's own H5, and Taobao only renders its own pages inside the {@code tbopen}
     * bridge, so the address travels as a percent-encoded {@code h5Url} instead of a plain https intent.
     * Both bridge schemes are tried because older builds answer only {@code tbopen}. 菜鸟裹裹's own
     * 身份码 page is kept as the second choice for phones that still have the app, since the station
     * accepts either code. If no page can be reached, the installed app opens with instructions and
     * the browser is the last resort.
     */
    private void openPickupCode() {
        for (String link : io.qzxy.lite.core.PickupCodeLinks.taobaoBridges()) {
            Intent bridge = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            bridge.setPackage(TAOBAO_PACKAGE);
            if (tryStart(bridge)) return;
        }
        // 菜鸟裹裹, in case a future build exports the page for a direct intent.
        if (tryStart(new Intent(Intent.ACTION_VIEW,
            Uri.parse(io.qzxy.lite.core.PickupCodeLinks.CAINIAO_IDENTITY)).setPackage(CAINIAO_PACKAGE))) return;
        // The route 菜鸟裹裹's own shortcut uses: its exported host plus the router path.
        Intent routed = new Intent(Intent.ACTION_VIEW);
        routed.setClassName(CAINIAO_PACKAGE, CAINIAO_HOME_ACTIVITY);
        routed.putExtra("jumpPath", CAINIAO_IDENTITY_CODE);
        routed.putExtra("entrance", "campuskit");
        if (tryStart(routed)) return;
        for (String name : PICKUP_FALLBACK_PACKAGES) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(name);
            if (launch != null && tryStart(launch)) {
                android.widget.Toast.makeText(this, name.equals(TAOBAO_PACKAGE)
                    ? "已打开淘宝，请依次进入 我的 → 菜鸟驿站 → 身份码。"
                    : "已打开应用，请在里面找到菜鸟驿站的身份码。", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
        }
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(io.qzxy.lite.core.PickupCodeLinks.TAOBAO_IDENTITY_H5));
        if (tryStart(web)) {
            android.widget.Toast.makeText(this, "没有找到淘宝，已在浏览器打开身份码网页，可能需要先登录。", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        android.widget.Toast.makeText(this, "没有找到淘宝或菜鸟裹裹，无法打开身份码。", android.widget.Toast.LENGTH_LONG).show();
    }

    /** @return true when something opened; a refused target (not exported) or a missing one returns false. */
    private boolean tryStart(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); return true; }
        catch (android.content.ActivityNotFoundException missing) { return false; }
        catch (SecurityException refused) { return false; }
        catch (IllegalArgumentException malformed) { return false; }
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
        Theme.dialog(this).setTitle("未找到易校园").setMessage("请先安装易校园并登录，再回来打开付款码。")
            .setPositiveButton("知道了",null).show();
    }

    /** The installed consumer app exports its launcher but keeps the camera Activity private. */
    private void openLife798() {
        startActivity(new Intent(this,ScanActivity.class).putExtra("life798",true));
    }

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if(request>=80&&request<=83){handleDocument(request,result,data);return;}
        if (request == 42 && result == RESULT_OK && data != null && data.hasExtra("courses")) {
            if (data.getBooleanExtra("auto", false)) autoImport(data);
            else {
                try { previewPdf(TimetableWebReader.parse(data.getStringExtra("courses")), "核对教务课表"); }
                catch(Exception invalid) { Theme.dialog(this).setMessage("网页课表未能完整识别，已有课表未修改。"+invalid.getMessage()).setPositiveButton("知道了",null).show(); }
            }
            return;
        }
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
            AlertDialog dialog = Theme.dialog(this).setTitle(title).setView(scroll).setNegativeButton("取消",null).setPositiveButton("确认导入",null).create();
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
            Theme.dialog(this).setMessage("课表信息未通过校验，未修改已有课程。"+(invalid.getMessage()==null?"":invalid.getMessage())).setPositiveButton("知道了",null).show();
        }
    }
    /** A chosen term replaces the timetable in one step; one undo copy is kept for that import. */
    private void autoImport(Intent data) {
        try {
            io.qzxy.lite.core.TimetablePdfParser.Result parsed =
                TimetableWebReader.parse(data.getStringExtra("courses"));
            List<JSONObject> replacement = new ArrayList<>();
            for (io.qzxy.lite.core.TimetablePdfParser.Course c : parsed.courses) {
                JSONObject item = new JSONObject();
                item.put("name", c.name); item.put("room", c.room); item.put("teacher", c.teacher);
                item.put("day", c.day); item.put("first", c.first); item.put("last", c.last);
                item.put("from", c.from); item.put("to", c.to); item.put("parity", c.parity);
                validate(item);
                boolean duplicate = false;
                for (JSONObject old : replacement) {
                    boolean equal = true;
                    for (String key : new String[]{"name","room","teacher","day","first","last","from","to","parity"})
                        if (!old.get(key).equals(item.get(key))) equal = false;
                    if (equal) { duplicate = true; break; }
                }
                if (!duplicate) replacement.add(item);
            }
            if (replacement.isEmpty() || replacement.size() > 500)
                throw new IllegalArgumentException("课程数量异常");
            JSONArray array = new JSONArray(); for (JSONObject c : replacement) array.put(c);
            String term = data.getStringExtra("term");
            if (term == null || term.isEmpty()) term = "教务课表";
            int skipped = data.getIntExtra("skipped", 0);
            String notes = term + "：从教务系统自动导入 " + replacement.size() + " 门课"
                + (skipped > 0 ? "（跳过 " + skipped + " 条字段不完整的记录）" : "");
            String previous = store.getString("courses", "[]");
            if (!store.edit().putString("courses", array.toString()).putString("undo_courses", previous)
                .putString("import_notes", notes).putString("import_term", term).commit()) {
                notice("课表保存失败，原课表未修改。"); return;
            }
            courses.clear(); courses.addAll(replacement); render();
            String message = "已写入 " + replacement.size() + " 门课（" + term + "）到「"
                + TimetableStore.currentName(this) + "」。"
                + (skipped > 0 ? "\n另有 " + skipped + " 条记录字段不完整，已跳过。" : "")
                + (monday.isEmpty() ? "\n记得在「开学日期」里设置本学期第一周的周一。" : "")
                + "\n点「撤销」可恢复导入前的课表。";
            Theme.dialog(this).setTitle("课表已导入").setMessage(message)
                .setNegativeButton("撤销",(d,w)->undoImport()).setPositiveButton("知道了",null).show();
        } catch (Exception invalid) {
            notice("自动导入的课表未通过校验，原课表未修改。"
                + (invalid.getMessage() == null ? "" : invalid.getMessage()));
        }
    }

    private void undoImport() {
        String previous = store.getString("undo_courses", "");
        if (previous.isEmpty()) { notice("没有可恢复的课表。"); return; }
        try {
            JSONArray array = new JSONArray(previous);
            List<JSONObject> restored = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) restored.add(array.getJSONObject(i));
            store.edit().remove("undo_courses").commit();
            courses.clear(); courses.addAll(restored); render();
            notice("已恢复导入前的课表。");
        } catch (Exception invalid) { notice("恢复失败，请从备份文件恢复。"); }
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
    /** Re-reads everything the current table owns; runs at start and whenever the table changes. */
    private void reload() {
        courses.clear();
        unreadable = false;
        monday = store.getString("monday", "");
        backgroundImage = TimetableBackup.decode(TimetableStore.background(this));
        try {
            JSONArray data = new JSONArray(store.getString("courses", "[]"));
            if (data.length() > 500) throw new IllegalArgumentException();
            for (int i = 0; i < data.length(); i++) {
                JSONObject course = data.getJSONObject(i); validate(course); courses.add(course);
            }
            if (!monday.isEmpty()) parseMonday(monday);
        } catch (Exception invalid) { unreadable = true; }
        week = Math.max(1, Math.min(CoursePickers.LAST_WEEK, currentWeek()));
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Diag.install(this);
        store = TimetableStore.open(this);
        // 0.7.7 and earlier stored a hand-typed pickup code here; the feature is gone, so drop it.
        getSharedPreferences("campus-v1", MODE_PRIVATE).edit().remove("pickup_code").apply();
        lastDay=saved==null?todayIso():saved.getString("lastDay",todayIso());
        refreshDecision=new io.qzxy.lite.core.RefreshDecision(lastDay);
        store.registerOnSharedPreferenceChangeListener(timetableChanged);
        reload();
        int current = currentWeek();
        week = saved == null ? Math.max(1, Math.min(CoursePickers.LAST_WEEK, current)) : Math.max(1, Math.min(CoursePickers.LAST_WEEK, saved.getInt("week", 1)));
        if (saved != null) {
            nav = new MainNavigator(saved.getInt("tab"), saved.getInt("campusView"));
            pendingNote = saved.getString("pendingNote");
        }
        render();
        // A fresh process shows the notice; a restored one does not pop it again mid-session.
        if (saved == null) {
            NoticeDialog.showIfNeeded(this);
            // Checked once per process, on a background thread, and only after this screen exists.
            UpdateCheck.onStart(this);
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("lastDay",lastDay);
        out.putInt("week", week); out.putInt("tab", nav.tab()); out.putInt("campusView", nav.campusView());
        out.putString("pendingNote", pendingNote);
        super.onSaveInstanceState(out);
    }
    /**
     * Declared with orientation|screenSize, so the framework does not recreate the activity on a
     * rotation or a split-screen resize. Every margin here is computed for a width, so the view is
     * rebuilt for the new one instead of keeping the old one stretched.
     */
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        render();
    }
    private static SimpleDateFormat dateFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false); format.setTimeZone(TimeZone.getTimeZone("UTC")); return format;
    }
    /**
     * Reads a stored or typed term start. {@link io.qzxy.lite.core.TermDate} owns the rules — it
     * accepts 2026-8-31 as well as 2026-08-31 and still insists on a real Monday — and hands back
     * the canonical text this class then parses.
     */
    static Date parseMonday(String value) throws Exception {
        return dateFormat().parse(io.qzxy.lite.core.TermDate.normalize(value));
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

    private void notice(String message){Theme.dialog(this).setMessage(message).setPositiveButton("知道了",null).show();}
    private void document(String action,String type,int code){
        Intent intent=new Intent(action).setType(type).addCategory(Intent.CATEGORY_OPENABLE);
        if(action.equals(Intent.ACTION_CREATE_DOCUMENT))intent.putExtra(Intent.EXTRA_TITLE,"科信小助手-课表-"+todayIso()+".json");
        try{startActivityForResult(intent,code);}catch(Exception e){notice("无法打开系统文件选择器");}
    }
    private void backgroundMenu(){Theme.dialog(this).setTitle("课表背景").setItems(new String[]{"选择背景图片","恢复默认背景"},(d,index)->{
        if(index==0)document(Intent.ACTION_OPEN_DOCUMENT,"image/*",80);
        else if(TimetableStore.background(this,"")){backgroundImage=null;render();}else notice("保存失败，请重试");
    }).setNegativeButton("取消",null).show();}
    private void widgetHelp(){Theme.dialog(this).setTitle("添加桌面课表")
        .setMessage("如果没有出现添加窗口，请在系统设置中允许「科信小助手」的桌面快捷方式权限，再返回重试。红米路径：权限管理 → 其他权限 → 桌面快捷方式 → 始终允许。也可在桌面小部件列表的安卓小部件中查找「科信小助手」。")
        .setPositiveButton("打开应用设置",(d,w)->{try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception e){notice("请在系统设置中找到「科信小助手」的权限管理。");}}).setNegativeButton("关闭",null).show();}
    private void addScheduleWidget(){Theme.dialog(this).setTitle("桌面课表尺寸").setItems(new String[]{"2 × 2 · 当前 / 下一节","3 × 2 · 今日课程","4 × 2 · 宽版课表"},(d,index)->pinScheduleWidget(new Class<?>[]{ScheduleWidget.class,ScheduleWidgetMedium.class,ScheduleWidgetWide.class}[index])).setNegativeButton("取消",null).show();}
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
        backgroundWorker.execute(()->{
            try{
                if(request==81){JSONObject backup=TimetableBackup.exportData(store);backup.put("background",TimetableStore.background(this));try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new java.io.IOException();out.write(backup.toString().getBytes("UTF-8"));}
                    runOnUiThread(()->{if(isDestroyed())return;importing=false;render();notice("备份已导出，包含课表、配色、开学日期和背景图片。");});
                }else if(request==80){String encoded=TimetableBackup.image(TimetableBackup.read(getContentResolver().openInputStream(uri),20*1024*1024));
                    if(!TimetableStore.background(this,encoded))throw new java.io.IOException();
                    final android.graphics.Bitmap bitmap=TimetableBackup.decode(encoded);
                    runOnUiThread(()->{if(isDestroyed())return;importing=false;backgroundImage=bitmap;render();});
                }else{JSONObject backup=TimetableBackup.parse(TimetableBackup.read(getContentResolver().openInputStream(uri),6*1024*1024));int count=new JSONArray(backup.getString("courses")).length();
                    runOnUiThread(()->{if(isDestroyed())return;importing=false;render();Theme.dialog(this).setTitle("恢复课表备份")
                        .setMessage("备份含 "+count+" 条课程，开学日期："+backup.optString("monday")+"。恢复会替换当前课表「"+TimetableStore.currentName(this)+"」的课程、配色和备注，并换上备份里的背景图。")
                        .setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->{try{if(!TimetableBackup.restore(store,backup))throw new java.io.IOException();TimetableStore.background(this,backup.optString("background",""));recreate();}catch(Exception e){notice("恢复失败，未能保存备份。");}}).show();});}
            }catch(Exception failure){runOnUiThread(()->{if(isDestroyed())return;importing=false;render();notice(request==80?"图片无法读取，请选择20 MB以内的图片。":request==81?"备份写入失败，请重新选择保存位置。":"备份无法读取或格式无效，原课表未修改。");});}
        });
    }

    /**
     * The table picker behind the heading: the name of every table, the current one marked, and
     * 新建课表 at the bottom. Tapping anywhere on a row switches to that table — the name used to be
     * a rename target, which made the obvious gesture (tap the table you want) do something else.
     * Renaming is now a labelled button beside 删除, next to the row it belongs to.
     */
    private void chooseTable() {
        List<TimetableIndex.Table> tables = TimetableStore.tables(this);
        String current = TimetableStore.currentId(this);
        final AlertDialog[] holder = new AlertDialog[1];
        LinearLayout list = column();
        list.setPadding(dp(4), dp(4), dp(4), dp(6));
        TextView hint = text("点一下课表即可切换，右侧可重命名或删除。", 12);
        hint.setTextColor(AppUi.MUTED);
        hint.setPadding(dp(14), dp(2), dp(14), dp(10));
        list.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        for (final TimetableIndex.Table table : tables) {
            boolean active = table.id.equals(current);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(8), dp(8), dp(8));
            AppUi.press(row, AppUi.SURFACE, 10);
            TextView name = text(table.name, 16);
            name.setTextColor(active ? AppUi.ACCENT : AppUi.INK);
            if (active) name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setPadding(0, dp(3), dp(8), dp(3));
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView rename = text("重命名", 13);
            rename.setTextColor(AppUi.ACCENT);
            rename.setPadding(dp(10), dp(8), dp(10), dp(8));
            AppUi.press(rename, 0x00ffffff, 8);
            rename.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                renameTable(table);
            });
            rename.setContentDescription("重命名" + table.name);
            row.addView(rename);
            TextView remove = text("删除", 13);
            remove.setTextColor(AppUi.DANGER);
            remove.setPadding(dp(10), dp(8), dp(10), dp(8));
            AppUi.press(remove, 0x00ffffff, 8);
            remove.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                removeTable(table);
            });
            remove.setContentDescription("删除" + table.name);
            row.addView(remove);
            // The current table needs no switch button, but saying so keeps the list readable.
            if (active) {
                TextView mark = text("当前", 12);
                mark.setTextColor(AppUi.ACCENT);
                mark.setBackground(AppUi.shape(this, AppUi.SOFT, 8));
                mark.setPadding(dp(7), dp(2), dp(7), dp(2));
                LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(-2, -2);
                markParams.rightMargin = dp(6);
                row.addView(mark, 1, markParams);
                row.setContentDescription(table.name + "，当前课表");
            } else {
                row.setContentDescription(table.name + "，点按切换");
                row.setOnClickListener(v -> {
                    if (holder[0] != null) holder[0].dismiss();
                    switchTable(table.id);
                });
            }
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
            View rule = new View(this); rule.setBackgroundColor(AppUi.LINE);
            list.addView(rule, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
        }
        Button add = button("＋ 新建课表", () -> {
            if (holder[0] != null) holder[0].dismiss();
            createTable();
        });
        add.setTextColor(AppUi.ACCENT);
        list.addView(add, new LinearLayout.LayoutParams(-1, dp(50)));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        AlertDialog dialog = Theme.dialog(this).setTitle("选择课表").setView(scroll)
            .setNegativeButton("关闭", null).create();
        holder[0] = dialog;
        dialog.show();
    }

    /** Points the screen and the desktop widget at whichever table is current now. */
    private void bind() {
        if (store != null) store.unregisterOnSharedPreferenceChangeListener(timetableChanged);
        store = TimetableStore.open(this);
        store.registerOnSharedPreferenceChangeListener(timetableChanged);
        reload();
        render();
        ScheduleWidget.updateAll(this);
    }

    private void switchTable(String id) {
        if (id.equals(TimetableStore.currentId(this))) return;
        TimetableStore.select(this, id);
        bind();
        notice("已切换到「" + TimetableStore.currentName(this) + "」。");
    }

    private void createTable() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("例如 2026 秋、辅修课表");
        Theme.dialog(this).setTitle("新建课表").setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建", (d, w) -> {
                TimetableStore.create(this, input.getText().toString());
                bind();
                Theme.dialog(this).setTitle("已新建空课表")
                    .setMessage("「" + TimetableStore.currentName(this) + "」里还没有课程。"
                        + "可以在「管理」里从教务系统导入，或手动添加课程。")
                    .setPositiveButton("知道了", null).show();
            }).show();
    }

    private void renameTable(final TimetableIndex.Table table) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(table.name);
        input.setSelection(table.name.length());
        Theme.dialog(this).setTitle("重命名课表").setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", (d, w) -> {
                TimetableStore.rename(this, table.id, input.getText().toString());
                if (table.id.equals(TimetableStore.currentId(this))) render();
                notice("已改名为「" + TimetableStore.name(this, table.id) + "」。");
            }).show();
    }

    private void removeTable(final TimetableIndex.Table table) {
        if (TimetableStore.tables(this).size() <= 1) { notice("至少要保留一个课表。"); return; }
        boolean wasCurrent = table.id.equals(TimetableStore.currentId(this));
        // The board picture is not part of a timetable, so it is not part of the question either.
        String message = "这个课表的课程和导入记录会一起删除，无法恢复。";
        if (wasCurrent) {
            // Say which table the screen will land on, so deleting the one you are looking at is not
            // a jump into the unknown.
            for (TimetableIndex.Table other : TimetableStore.tables(this)) {
                if (!other.id.equals(table.id)) { message = "删除后会切到「" + other.name + "」。" + message; break; }
            }
        }
        Theme.dialog(this).setTitle("删除「" + table.name + "」？")
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("删除", (d, w) -> {
                if (!TimetableStore.remove(this, table.id)) { notice("删除失败，至少要保留一个课表。"); return; }
                if (wasCurrent) bind(); else render();
                notice("已删除「" + table.name + "」。");
            }).show();
    }

    private void manage() {
        if(unreadable || importing)return;
        Theme.dialog(this).setTitle(TimetableStore.currentName(this)).setItems(new String[]{"从教务系统导入课表", "开学日期", "添加课程", "全部课程（"+courses.size()+"）", "自定义背景图片", "添加桌面课表组件", "导出备份", "恢复备份"}, (d,index)-> {
            if(index==0)startActivityForResult(new Intent(this,EduTimetableImportActivity.class),42);
            else if(index==1)semester();else if(index==2)edit(null);else if(index==3)allCourses();
            else if(index==4)backgroundMenu();else if(index==5)addScheduleWidget();else if(index==6)document(Intent.ACTION_CREATE_DOCUMENT,"application/json",81);else if(index==7)document(Intent.ACTION_OPEN_DOCUMENT,"*/*",82);
        }).setNegativeButton("关闭",null).show();
    }
    /**
     * Button-driven week change. The board slides out the same way a swipe does, then the new week
     * animates in from the opposite side, so buttons and gestures feel identical.
     */
    private void shiftWeek(int delta) {
        int next=Math.max(1,Math.min(CoursePickers.LAST_WEEK,week+delta));if(next==week)return;
        if(pager==null){applyWeek(delta);return;}
        pager.animateOutThen(delta,()->applyWeek(delta));
    }
    /** Commits the adjacent week while preserving the vertical reading position. */
    private void applyWeek(int delta) {
        int next=Math.max(1,Math.min(CoursePickers.LAST_WEEK,week+delta));if(next==week)return;
        int scroll=pager==null?0:pager.verticalPosition();
        week=next;render();
        if(pager!=null)pager.restoreVerticalPosition(scroll);
        root.announceForAccessibility("第 "+week+" 周");
    }
    private void selectWeek() {
        CoursePickers.selectWeek(this,week,selected->{int delta=selected-week;if(delta!=0)applyWeek(delta);});
    }
    private void goCurrentWeek() {
        if(currentWeek()<1 || currentWeek()>CoursePickers.LAST_WEEK){semester();return;}
        int delta=currentWeek()-week;
        if(delta==0){android.widget.Toast.makeText(this,"已经是本周",android.widget.Toast.LENGTH_SHORT).show();return;}
        applyWeek(delta);
    }
    private void showCourses(List<JSONObject> group) {
        if(group.size()>1) {
            String[] names=new String[group.size()];for(int i=0;i<names.length;i++)names[i]=group.get(i).optString("name");
            Theme.dialog(this).setTitle("此时段有重叠课程").setItems(names,(d,n)->showCourses(java.util.Collections.singletonList(group.get(n)))).setNegativeButton("关闭",null).show();return;
        }
        JSONObject c=group.get(0);
        String detail=DAYS[c.optInt("day")]+"  ·  第 "+c.optInt("first")+"—"+c.optInt("last")+" 节\n\n"+c.optString("room")+"\n"+c.optString("teacher")+"\n\n"+CourseWeeks.label(c);
        Theme.dialog(this).setTitle(c.optString("name")).setMessage(detail).setPositiveButton("编辑课程",(d,w)->edit(c)).setNegativeButton("关闭",null).show();
    }
    private android.graphics.drawable.GradientDrawable rounded(int color,int radius) {
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;
    }
    private void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private TextView bold(String value,int size){TextView view=text(value,size);view.setTypeface(null,android.graphics.Typeface.BOLD);return view;}

    // ---- UI-2: persistent shell -----------------------------------------------------------

    /** Build the one outer shell and hand it to Page exactly once. Never called from selectTab. */
    private void ensureShell() {
        if (shell != null) return;
        shell = new MainShell(this, this::selectTab);
        navBar = shell.navBar;
        setContentView(shell);
        shell.requestApplyInsets();
    }

    /** The Three screens always write into {@link #root}; buildScreen gives them a fresh root inside
     *  contentHost. Clearing the live 今日 handles here is what keeps A3 updates from touching a
     *  view that no longer exists after a full rebuild. */
    private LinearLayout buildScreen(LinearLayout target) {
        root = target;
        pager = null;
        todaySummary = null;
        todayRows.clear();
        if (nav.tab()==0) new HomeScreen(this).build();
        else if (nav.tab()==1) new ScheduleScreen(this).build();
        else new CampusScreen(this).build();
        return target;
    }

    /** Rebuild only the header into its host; no setContentView, no nav re-inflation. */
    private void renderHeader() {
        shell.headerHost.removeAllViews();
        // One header shape for all three tabs, so 今日 / 课表 / 账号 read as views of the same app.
        LinearLayout header=new LinearLayout(this);header.setGravity(android.view.Gravity.CENTER_VERTICAL);header.setPadding(dp(8),0,dp(8),0);
        LinearLayout heading=column();
        if(nav.tab()==1){
            // Only the name and its caret are the button. The header row behind them stays inert, so a
            // tap on the empty space to the right no longer opens the picker by accident.
            LinearLayout picker=new LinearLayout(this);
            picker.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView pickerName=bold(TimetableStore.currentName(this),20);
            pickerName.setSingleLine(true);
            pickerName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            // Whatever the header has left after the 管理 button, so a long table name ellipsizes
            // instead of pushing the button off a narrow screen.
            pickerName.setMaxWidth(Math.max(dp(120), getResources().getDisplayMetrics().widthPixels-2*AppUi.side(this,8)-dp(76)-dp(28)));
            picker.addView(pickerName,new LinearLayout.LayoutParams(-2,-2));
            android.widget.ImageView caret=new android.widget.ImageView(this);
            caret.setImageDrawable(AppUi.icon(this,"caret",AppUi.INK,17));
            caret.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams caretParams=new LinearLayout.LayoutParams(dp(17),dp(17));
            caretParams.setMargins(dp(5),dp(4),0,0);
            picker.addView(caret,caretParams);
            picker.setPadding(dp(2),dp(2),dp(6),dp(2));
            picker.setOnClickListener(v->chooseTable());
            AppUi.press(picker,0x00ffffff,8);
            picker.setContentDescription("当前课表 "+TimetableStore.currentName(this)+"，点按切换或重命名");
            heading.addView(picker);
        } else heading.addView(bold(nav.tab()==0?"今日":"账号",UiTokens.PAGE_TITLE));
        String subtitle;
        if(nav.tab()==0)subtitle=DAYS[todayIndex()]+" · "+todayIso()+(currentWeek()>=1&&currentWeek()<=CoursePickers.LAST_WEEK?" · 第 "+currentWeek()+" 周":"");
        else if(nav.tab()==1)subtitle=monday.isEmpty()?"设置开学日期，自动定位本周":(dayDate(0).replace(" · ",""))+" — "+dayDate(6).replace(" · ","");
        else subtitle="校园服务账号";
        TextView caption=text(subtitle,UiTokens.BODY_SMALL);caption.setTextColor(AppUi.MUTED);caption.setPadding(0,0,0,dp(6));heading.addView(caption);
        if(nav.tab()==1&&monday.isEmpty()&&!unreadable)caption.setOnClickListener(v->semester());
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if(nav.tab()==1){Button settings=weekAction(importing?"识别中…":"管理",this::manage);settings.setEnabled(!unreadable&&!importing);header.addView(settings,new LinearLayout.LayoutParams(dp(76),dp(48)));}
        else if(nav.tab()==2&&nav.campusView()==1){TextView back=text("‹ 返回",14);back.setTextColor(AppUi.ACCENT);back.setPadding(dp(10),dp(10),0,dp(10));back.setOnClickListener(v->{nav.openCampusList();render();});header.addView(back);}
        shell.headerHost.addView(header,new FrameLayout.LayoutParams(-1,-2));
    }

    /** Capture the current screen's scroll so a later switch back can restore it. */
    private int captureScroll() {
        if (pager != null) return pager.verticalPosition();
        if (root == null) return 0;
        for (int i=0;i<root.getChildCount();i++) {
            View child = root.getChildAt(i);
            if (child instanceof ScrollView) return child.getScrollY();
        }
        return 0;
    }

    /** Restore a remembered scroll once the new screen has been laid out. */
    private void restoreScroll(int tab, int y) {
        if (y <= 0) return;
        final int value = y;
        root.post(() -> {
            if (tab == 1 && pager != null) { pager.restoreVerticalPosition(value); return; }
            if (root == null) return;
            for (int i=0;i<root.getChildCount();i++) {
                View child = root.getChildAt(i);
                if (child instanceof ScrollView) { child.scrollTo(0, value); break; }
            }
        });
    }

    /** Replace the contentHost child. With animate=false (ordinary render) the swap is immediate;
     *  selectTab passes animate=true to play the isolated content crossfade. */
    private void renderContent(boolean animate, int direction) {
        final int generation = ++transitionGeneration;
        for (Animator running : contentAnimators) running.cancel();
        contentAnimators.clear();
        // 连续快速切 Tab 时，上一次转场可能还留着「旧页 + 未完成的新页」两页。只保留最上层
        // 那一页作为本次 outgoing，其余过渡残留全部移除，保证 contentHost 不会叠出三页。
        while (shell.contentHost.getChildCount() > 1) shell.contentHost.removeViewAt(0);
        View outgoing = shell.contentHost.getChildCount() > 0 ? shell.contentHost.getChildAt(0) : null;

        LinearLayout next = column();
        next.setBackgroundColor(AppUi.BG);
        FrameLayout.LayoutParams full = new FrameLayout.LayoutParams(-1,-1);
        buildScreen(next);
        shell.contentHost.addView(next, full);

        boolean motion = animate && outgoing != null
            && MotionSpec.shouldAnimate(this) && navBar != null;
        if (!motion) {
            if (outgoing != null) shell.contentHost.removeView(outgoing);
            next.setAlpha(1f); next.setTranslationX(0f);
            restoreScroll(nav.tab(), tabScroll[nav.tab()]);
            return;
        }

        final View old = outgoing;
        final float offset = dp(MotionSpec.TAB_OFFSET_DP) * direction;
        next.setAlpha(.7f);
        next.setTranslationX(offset);
        ValueAnimator enter = ValueAnimator.ofFloat(0f,1f);
        enter.setDuration(MotionSpec.TAB_ENTER_DURATION);
        enter.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            next.setAlpha(.7f + .3f*t);
            next.setTranslationX(offset * (1f - t));
        });
        ValueAnimator exit = ValueAnimator.ofFloat(1f,0f);
        exit.setDuration(MotionSpec.TAB_EXIT_DURATION);
        exit.addUpdateListener(a -> old.setAlpha((Float) a.getAnimatedValue()));
        exit.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator anim) {
                if (generation != transitionGeneration) return;   // stale callback: a newer tab won
                if (old.getParent() == shell.contentHost) shell.contentHost.removeView(old);
                next.setAlpha(1f); next.setTranslationX(0f);
                contentAnimators.clear();
            }
        });
        contentAnimators.add(enter); contentAnimators.add(exit);
        enter.start(); exit.start();
        restoreScroll(nav.tab(), tabScroll[nav.tab()]);
    }

    /** Ordinary full rebuild (data / theme / course change / back). Never animates a tab entrance. */
    private void render() {
        // tab / campusView 刚变过，返回该不该缩卡片也跟着变；refreshBackMode 内部有记忆，
        // 方式没变时什么也不做。
        refreshBackMode();
        ensureShell();
        renderHeader();
        renderContent(false, 0);
        navBar.refreshPalette();           // 主题换色后胶囊底色/文字取新的 AppUi 调色板
        navBar.select(nav.tab(), false);   // keep capsule in sync without animating
    }

    /** The only user-driven tab switch path. Header, content and capsule update in a fixed order. */
    private void selectTab(int target) {
        if (target == nav.tab()) return;
        int from = nav.tab();
        tabScroll[from] = captureScroll();          // save the page we are leaving
        nav.selectTab(target);
        refreshBackMode();
        renderHeader();
        int direction = target > from ? 1 : -1;
        renderContent(true, direction);
        navBar.select(target, true);                // animate the capsule after content is in place
    }

    private TextView cardTitle(String value){return bold(value,16);}
    private LinearLayout whiteCard(){LinearLayout card=column();card.setBackground(AppUi.outlined(this,AppUi.SURFACE,UiTokens.CARD_RADIUS,AppUi.LINE));card.setPadding(dp(18),dp(16),dp(18),dp(16));return card;}
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

    private TextView body(String value) {
        TextView view=text(value,12);view.setTextColor(AppUi.MUTED);return view;
    }

    /**
     * Every icon on 今日 and 校园 is drawn in the one accent colour. Colour is not carrying meaning
     * here — the label and the icon already say which service this is — so a second hue only made
     * one row look like it belonged to something else.
     */

    private Button weekAction(String label,Runnable action) {
        Button view=button(label,action);view.setTextColor(AppUi.INK);view.setTextSize(14);
        view.setMinWidth(0);view.setMinimumWidth(0);view.setPadding(dp(8),0,dp(8),0);
        AppUi.press(view,0x00ffffff,8);return view;
    }
    private void openQzxy(){
        Intent intent=getPackageManager().getLaunchIntentForPackage("com.klcxkj.zqxy");
        if(intent==null){notice("请先安装趣智校园原版应用。");return;}
        try{startActivity(intent);}catch(Exception e){notice("无法打开趣智校园，请检查原版是否已安装并启用。");}
    }
    private void records(LinearLayout parent) {
        if(pendingNote!=null&&!pendingNote.isEmpty()) {
            // A warning keeps its warm tint in both themes, but the tint itself has to be the dark
            // theme's idea of warm — the light one is a cream box that glows on a dark page.
            boolean dark=Theme.dark();
            LinearLayout panel=column();panel.setPadding(dp(16),dp(14),dp(16),dp(14));
            panel.setBackground(rounded(dark?0xff2a2318:0xfffff7ed,14));
            TextView headline=bold("待核实的用水记录",16);
            if(dark)headline.setTextColor(0xffe8c793);
            panel.addView(headline);gap(panel,8);
            TextView detail=text("这条记录来自上一次没有查清的订单。它描述的是过去，不是现在，必须重新扫码联网核对才能判断。",12);
            detail.setTextColor(dark?0xffcfae7c:0xff7c4a12);panel.addView(detail);
            parent.addView(panel,new LinearLayout.LayoutParams(-1,-2));gap(parent,14);
        }
        LinearLayout panel=column();panel.setPadding(dp(16),dp(14),dp(16),dp(14));panel.setBackground(rounded(AppUi.SURFACE,14));
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
        AlertDialog dialog = Theme.dialog(this).setTitle(original == null ? "添加课程" : "编辑课程")
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
        Theme.dialog(this).setTitle("删除这条课程？").setMessage(course.optString("name"))
            .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
                List<JSONObject> replacement = new ArrayList<>(courses); replacement.remove(course);
                if (save(replacement, monday)) render();
                else Theme.dialog(this).setMessage("删除未能保存，请重试。").setPositiveButton("知道了", null).show();
            }).show();
    }
    private void allCourses() {
        if (courses.isEmpty()) { edit(null); return; }
        Map<String,Integer> auto = WeekBoard.autoColorsFor(courses);
        List<JSONObject> ordered=new ArrayList<>(courses);
        ordered.sort((a,b)->{int day=Integer.compare(a.optInt("day"),b.optInt("day"));return day!=0?day:Integer.compare(a.optInt("first"),b.optInt("first"));});
        LinearLayout list=column();list.setPadding(dp(16),dp(8),dp(16),dp(16));
        ScrollView scroll=new ScrollView(this);scroll.addView(list);
        AlertDialog dialog=Theme.dialog(this).setTitle("全部课程 · "+courses.size()+" 条").setView(scroll).setNegativeButton("关闭",null).create();
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
        EditText date = input(form, "第一周的周一（例如 2026-08-31）", monday, false);
        TextView error = text("", 14); form.addView(error);
        AlertDialog dialog = Theme.dialog(this).setTitle("学期设置").setView(form)
            .setPositiveButton("保存", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                // Stored canonical whatever was typed, so every reader keeps one format.
                String value = io.qzxy.lite.core.TermDate.normalize(date.getText().toString());
                if (!save(new ArrayList<>(courses), value)) { error.setText("保存失败，请重试。"); return; }
                week = Math.max(1, Math.min(CoursePickers.LAST_WEEK, currentWeek())); dialog.dismiss(); render();
            } catch (Exception invalid) { error.setText("请输入有效的周一日期，格式为 年-月-日。"); }
        })); dialog.show();
    }

    /** 趣智校园 tab: app-level services, settings and about entries. Extracted as a screen so the
     *  activity no longer owns its view assembly; kept as a nested class to reuse the activity's
     *  private UI helpers with identical behaviour (no visibility widening, no Context confusion). */
    static final class CampusScreen {
        private final TimetableActivity a;
        CampusScreen(TimetableActivity activity) { this.a = activity; }

        void build() {
            ScrollView scroll=new ScrollView(a);LinearLayout content=a.column();scroll.addView(content);a.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout card=a.whiteCard();
            entry(card,"scan","趣智校园","浴室、吹风机",()->a.startActivity(new Intent(a,AccountInfoActivity.class)),true);
            entry(card,"water","慧生活798","饮水",()->a.startActivity(new Intent(a,AccountInfoActivity.class).putExtra("life798",true)),true);
            // 教务系统指向本应用自己的教务页（自动登录 + 网页登录两条路都在里面），不是学校的空白登录表单：
            // 没存账号时那一页会直接把教务账号页拉起来，等于先看到登录，再看到系统。
            entry(card,"cap","教务系统","成绩、空教室与自动登录",()->a.startActivity(new Intent(a,EduPortalActivity.class)),true);
            // 数据与隐私 已移到「更多」：它讲的是这个应用本身存了什么，不是一项校园服务。
            // Feedback sits above the update check: both are about the app itself rather than about an
            // account, and a reader who came here to complain should not have to scroll past 检查更新.
            entry(card,"mail","意见反馈","邮箱与 QQ 群",()->a.startActivity(new Intent(a,FeedbackActivity.class)),true);
            // The start-up check is silent by design, so the manual one sits in the list next to the
            // other things that answer a question about the app itself, rather than on a footer line.
            // 外观与主题 和 意见反馈、检查更新 同类：它讲的是这个应用该长什么样，不是一项校园服务。
            entry(card,"theme","外观与主题","深色模式、主题色与课表配色",()->a.startActivity(new Intent(a,AppearanceActivity.class)),true);
            entry(card,"refresh","检查更新","当前 "+AppUi.version(a)+"，点按查询新版本",()->UpdateCheck.checkManually(a),true);
            entry(card,"more","更多","数据与隐私、源码与许可证",()->a.startActivity(new Intent(a,MoreActivity.class)),false);
            a.gap(card,10);card.addView(a.body("科信小助手 "+AppUi.version(a)+" · 第三方校园客户端"));content.addView(card);
        }

        private void entry(LinearLayout parent,String icon,String title,String subtitle,Runnable action,boolean divider) {
            LinearLayout line=new LinearLayout(a);line.setGravity(android.view.Gravity.CENTER_VERTICAL);
            line.setPadding(a.dp(16),a.dp(14),a.dp(14),a.dp(14));
            line.setOnClickListener(v->action.run());line.setFocusable(true);
            android.widget.ImageView mark=new android.widget.ImageView(a);mark.setImageDrawable(AppUi.icon(a,icon,AppUi.ACCENT,24));mark.setBackground(AppUi.shape(a,AppUi.SOFT,12));mark.setPadding(a.dp(8),a.dp(8),a.dp(8),a.dp(8));mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);line.addView(mark,new LinearLayout.LayoutParams(a.dp(40),a.dp(40)));AppUi.press(line,AppUi.SURFACE,8);
            LinearLayout labels=a.column();labels.setPadding(a.dp(14),0,0,0);
            labels.addView(a.bold(title,16));
            TextView caption=a.text(subtitle,12);caption.setTextColor(AppUi.MUTED);caption.setPadding(0,a.dp(2),0,0);labels.addView(caption);
            line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            TextView chevron=a.text("›",20);chevron.setTextColor(0xffb3bcc9);line.addView(chevron);
            line.setContentDescription(title+"，"+subtitle);
            parent.addView(line,new LinearLayout.LayoutParams(-1,-2));
            if(divider){View rule=new View(a);rule.setBackgroundColor(AppUi.LINE);
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,Math.max(1,a.dp(1)));
                params.setMargins(a.dp(70),0,0,0);parent.addView(rule,params);}
        }
    }


    /** 今日 tab: shortcuts, today's classes and academic entries. Extracted as a screen so the
     *  activity no longer owns this page's view assembly. Nested so it reuses the activity's
     *  private helpers byte-for-byte; the A3 in-place update handles are written back to the
     *  activity's own fields, so refreshClock() keeps working untouched. */
    static final class HomeScreen {
        private final TimetableActivity a;
        HomeScreen(TimetableActivity activity) { this.a = activity; }

        void build() {
            ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);
            LinearLayout content=a.column();scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
            a.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            shortcutsCard(content);a.gap(content,14);
            todayCourses(content);a.gap(content,14);
            academicCard(content);
        }

        private void todayCourses(LinearLayout parent) {
            LinearLayout card=a.whiteCard();
            int todayWeek=a.currentWeek();
            // 先定周次，再走统一的 selectTab 流程（含胶囊动画与滚动恢复），不再自己 nav+render。
            a.cardHeader(card,"今天的课","查看课表",()->{a.week=Math.max(1,Math.min(CoursePickers.LAST_WEEK,a.currentWeek()));a.selectTab(1);});a.gap(card,10);
            if(a.unreadable) {
                card.addView(a.body("本机课表无法完整读取，已保留原数据。"));
            } else if(a.monday.isEmpty()) {
                card.addView(a.body("还没设置开学日期，无法判断今天属于第几周。"));
                a.gap(card,10);card.addView(a.cardAction("去设置开学日期 ›",a::semester));
            } else if(todayWeek<1||todayWeek>CoursePickers.LAST_WEEK) {
                card.addView(a.body("今天不在课表范围内（第 "+todayWeek+" 周，仅支持 1–"+CoursePickers.LAST_WEEK+" 周）。"));
            } else {
                List<JSONObject> todayList=new ArrayList<>();
                for(JSONObject course:a.courses)if(course.optInt("day")==a.todayIndex()&&a.appears(course,todayWeek))todayList.add(course);
                todayList.sort((x,y)->Integer.compare(x.optInt("first"),y.optInt("first")));
                if(todayList.isEmpty()) {
                    card.addView(a.body("今天没有课程"));
                } else {
                    TextView next=a.bold(ScheduleInfo.summary(todayList,ScheduleInfo.minutes()),14);next.setTextColor(AppUi.ACCENT);card.addView(next);a.gap(card,12);
                    a.todaySummary=next;   // kept so a clock tick can update the text in place
                    Map<String,Integer> auto=WeekBoard.autoColorsFor(a.courses);
                    for(int i=0;i<todayList.size();i++) {
                        JSONObject course=todayList.get(i);
                        if(i>0)a.gap(card,10);
                        card.addView(courseRow(course,auto),new LinearLayout.LayoutParams(-1,-2));
                    }
                }
            }
            parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
        }

        /** One class on the 今日 tab: name plus section range, room and teacher, tinted with its own colour. */
        private View courseRow(JSONObject course,Map<String,Integer> auto) {
            int index=WeekBoard.colorIndexOf(course,auto),ink=WeekBoard.inkAt(index);
            LinearLayout row=new LinearLayout(a);row.setGravity(android.view.Gravity.TOP);row.setPadding(a.dp(14),a.dp(12),a.dp(14),a.dp(12));row.setBackground(AppUi.shape(a,WeekBoard.fillAt(index),14));
            String status=ScheduleInfo.status(course,ScheduleInfo.minutes());boolean ended=status.equals("已结束");
            String time=ClassTimes.range(course.optInt("first"),course.optInt("last"));String[] span=time.split("[–—-]");
            LinearLayout times=a.column();TextView first=a.bold(span.length>0?span[0]:"",14);first.setTextColor(ink);times.addView(first);
            if(span.length>1){TextView last=a.text(span[1],12);last.setTextColor(ink);last.setAlpha(.72f);times.addView(last);}
            row.addView(times,new LinearLayout.LayoutParams(AppUi.compact(a)?a.dp(48):a.dp(56),-2));
            View marker=new View(a);marker.setBackground(AppUi.shape(a,ink,2));LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(a.dp(3),a.dp(40));ml.setMargins(0,a.dp(2),a.dp(12),0);row.addView(marker,ml);
            LinearLayout labels=a.column();TextView name=a.bold(course.optString("name"),16);name.setTextColor(ink);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);if(ended)name.setPaintFlags(name.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);labels.addView(name);
            String detail=course.optString("room")+" · 第 "+course.optInt("first")+"–"+course.optInt("last")+" 节";TextView room=a.text(detail,12);room.setTextColor(ink);room.setAlpha(.8f);room.setSingleLine(true);room.setEllipsize(android.text.TextUtils.TruncateAt.END);room.setPadding(0,a.dp(2),0,0);labels.addView(room);
            // The live chip is always attached and toggled with visibility instead of being added/
            // removed on the fly. GONE takes no space, so the visual output is identical to the old
            // conditional addView, but a clock tick can now flip it without touching the view tree.
            TextView current=a.bold("● 正在上课",12);current.setTextColor(ink);current.setPadding(0,a.dp(4),0,0);
            current.setVisibility((!ended&&status.equals("正在上课"))?View.VISIBLE:View.GONE);labels.addView(current);
            row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));if(ended)row.setAlpha(.55f);
            String label=course.optString("name")+"，"+time+"，"+detail;
            row.setContentDescription(label+(ended?"，已结束":""));
            a.todayRows.add(new TodayRow(course,name,row,current,label));   // handle for in-place clock updates
            return row;
        }

        private void shortcutsCard(LinearLayout parent) {
            LinearLayout card=a.whiteCard();a.cardHeader(card,"校园生活",null,null);a.gap(card,8);
            LinearLayout row=new LinearLayout(a);
            row.addView(shortcutTile("water","慧生活","用水",a::openLife798),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(shortcutTile("scan","趣智校园","扫码",()->a.startActivity(new Intent(a,ScanActivity.class))),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(shortcutTile("pay","易校园","付款码",a::openCampusPayCode),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(shortcutTile("parcel","身份码","淘宝·菜鸟驿站",a::openPickupCode),new LinearLayout.LayoutParams(0,-2,1));
            card.addView(row);parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
        }
        private void academicCard(LinearLayout parent) {
            LinearLayout card=a.whiteCard();a.cardHeader(card,"教务",null,null);a.gap(card,8);
            LinearLayout row=new LinearLayout(a);
            row.addView(shortcutTile("score","成绩","查询",()->openEdu("grades")),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(shortcutTile("room","空教室","查询",()->openEdu("rooms")),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(shortcutTile("cap","教务系统","自动登录",()->a.startActivity(new Intent(a,EduPortalActivity.class))),new LinearLayout.LayoutParams(0,-2,1));
            card.addView(row);
            LinearLayout sites=new LinearLayout(a);
            sites.addView(shortcutTile("check","学业情况","学分·绩点",()->openAcademicStatus()),new LinearLayout.LayoutParams(0,-2,1));
            sites.addView(shortcutTile("school","河工程官网","hebeu.edu.cn",()->openSchoolSite("河北工程大学官网","https://www.hebeu.edu.cn/")),new LinearLayout.LayoutParams(0,-2,1));
            sites.addView(shortcutTile("globe","科信官网","kexin.hebeu.edu.cn",()->openSchoolSite("科信学院官网","https://kexin.hebeu.edu.cn/")),new LinearLayout.LayoutParams(0,-2,1));
            card.addView(sites);parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
        }
        private void openEdu(String mode){
            try{a.startActivity(new Intent(a,AcademicActivity.class).putExtra("mode",mode));}
            catch(RuntimeException unavailable){a.notice("无法打开教务页面。");}
        }
        private void openAcademicStatus(){
            try{a.startActivity(new Intent(a,AcademicStatusActivity.class));}
            catch(RuntimeException unavailable){a.notice("无法打开学业情况页面。");}
        }
        private void openSchoolSite(String title,String url){
            try{a.startActivity(new Intent(a,SchoolSiteActivity.class)
                .putExtra(SchoolSiteActivity.EXTRA_URL,url)
                .putExtra(SchoolSiteActivity.EXTRA_TITLE,title));}
            catch(RuntimeException unavailable){a.notice("无法打开学校网站。");}
        }
        private View shortcutTile(String icon,String title,String subtitle,Runnable action) {
            boolean compact=AppUi.compact(a);int size=compact?a.dp(40):a.dp(44);
            LinearLayout tile=a.column();tile.setGravity(android.view.Gravity.CENTER);tile.setPadding(a.dp(3),a.dp(10),a.dp(3),a.dp(10));tile.setMinimumHeight(a.dp(96));AppUi.press(tile,AppUi.SURFACE,10);tile.setOnClickListener(v->action.run());
            android.widget.ImageView mark=new android.widget.ImageView(a);mark.setImageDrawable(AppUi.icon(a,icon,AppUi.ACCENT,24));mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);mark.setBackground(AppUi.shape(a,AppUi.SOFT,22));mark.setPadding(a.dp(9),a.dp(9),a.dp(9),a.dp(9));tile.addView(mark,new LinearLayout.LayoutParams(size,size));
            TextView name=a.bold(title,compact?12:13);name.setGravity(android.view.Gravity.CENTER);name.setPadding(0,a.dp(8),0,0);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(name);
            TextView hint=a.text(subtitle,compact?11:12);hint.setTextColor(AppUi.MUTED);hint.setGravity(android.view.Gravity.CENTER);hint.setMaxLines(2);hint.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(hint);tile.setContentDescription(title+"，"+subtitle);return tile;
        }
    }


    /** 课表 tab: week navigation and the paging WeekBoard. Extracted as a screen; every callback
     *  still writes back to the activity's own state (week, pager, courses), so swipe/week switching
     *  and the adaptive board height behave exactly as before. */
    static final class ScheduleScreen {
        private final TimetableActivity a;
        ScheduleScreen(TimetableActivity activity) { this.a = activity; }

        void build() {
            boolean compact=AppUi.compact(a);int arrow=compact?a.dp(44):a.dp(48);
            LinearLayout navigation=new LinearLayout(a);navigation.setGravity(android.view.Gravity.CENTER_VERTICAL);navigation.setPadding(a.dp(4),0,a.dp(4),a.dp(4));
            Button selected=a.weekAction("第 "+a.week+" 周  ▾",a::selectWeek);selected.setTextSize(compact?17:18);selected.setGravity(android.view.Gravity.LEFT|android.view.Gravity.CENTER_VERTICAL);selected.setMaxLines(1);selected.setEllipsize(android.text.TextUtils.TruncateAt.END);selected.setContentDescription("选择周次，当前第"+a.week+"周");
            navigation.addView(selected,new LinearLayout.LayoutParams(0,a.dp(48),1));
            Button previous=a.weekAction("‹",()->a.shiftWeek(-1));previous.setTextSize(26);previous.setContentDescription("上一周");previous.setEnabled(a.week>1);
            Button next=a.weekAction("›",()->a.shiftWeek(1));next.setTextSize(26);next.setContentDescription("下一周");next.setEnabled(a.week<CoursePickers.LAST_WEEK);
            navigation.addView(previous,new LinearLayout.LayoutParams(arrow,a.dp(48)));
            navigation.addView(next,new LinearLayout.LayoutParams(arrow,a.dp(48)));
            if(a.week!=a.currentWeek()&&a.currentWeek()>=1&&a.currentWeek()<=CoursePickers.LAST_WEEK)navigation.addView(a.weekAction("回本周",a::goCurrentWeek),new LinearLayout.LayoutParams(compact?a.dp(64):a.dp(76),a.dp(48)));
            a.root.addView(navigation);
            List<JSONObject> visible=new ArrayList<>();for(JSONObject c:a.courses)if(a.appears(c))visible.add(c);
            WeekBoard.Listener listener=new WeekBoard.Listener(){
                public void open(List<JSONObject> group){a.showCourses(group);}
                public void shift(int delta){a.shiftWeek(delta);}
                public void swipe(int delta){a.applyWeek(delta);}
                public boolean canShift(int delta){int next=a.week+delta;return next>=1&&next<=CoursePickers.LAST_WEEK;}
                public void addAt(int day,int section){a.edit(null,day,section);}
            };
            if(a.unreadable)a.root.addView(a.text("课表读取失败，原数据已保留。可从管理中恢复备份。",14));
            else if(a.courses.isEmpty()) {TextView hint=a.text("还没有课程 · 点击右上角管理，导入教务课表",12);hint.setGravity(android.view.Gravity.CENTER);a.root.addView(hint);}
            else if(visible.isEmpty()) {TextView hint=a.text("这一周没有课程",12);hint.setGravity(android.view.Gravity.CENTER);a.root.addView(hint);}
            // Colours are resolved from the whole course list, not just this week, so a course keeps its
            // colour while paging through weeks.
            final int shownWeek=a.week;
            a.pager=new WeekBoard.Pager(a,delta->{
                int targetWeek=shownWeek+delta;
                List<JSONObject> adjacent=new ArrayList<>();
                if(!a.unreadable)for(JSONObject c:a.courses)if(a.appears(c,targetWeek))adjacent.add(c);
                String[] adjacentDates=new String[7];
                for(int d=0;d<7;d++) {
                    adjacentDates[d]="";
                    if(!a.monday.isEmpty())try {
                        Date date=new Date(a.parseMonday(a.monday).getTime()+((targetWeek-1L)*7+d)*86400000L);
                        adjacentDates[d]=a.dateFormat().format(date).substring(5);
                    }catch(Exception invalid){}
                }
                WeekBoard board=new WeekBoard(a,adjacent,adjacentDates,targetWeek==a.currentWeek()?(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)+5)%7:-1,
                    WeekBoard.autoColorsFor(a.unreadable?new ArrayList<>():a.courses),listener);
                board.setPhoto(a.backgroundImage);return board;
            },listener);
            a.root.addView(a.pager,new LinearLayout.LayoutParams(-1,0,1));
        }
    }

}
