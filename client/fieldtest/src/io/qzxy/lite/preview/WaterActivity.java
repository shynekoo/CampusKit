package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.DeviceQr;
import io.qzxy.lite.core.FieldStartGate;
import io.qzxy.lite.core.PendingUse;
import io.qzxy.lite.core.UseSession;
import io.qzxy.lite.core.UseSession.State;
import io.qzxy.lite.core.WaterEvents;
import io.qzxy.lite.core.WaterOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Single-flight HTTP flow. A command is persisted before dispatch and never retried automatically. */
public final class WaterActivity extends Activity {
    static final String EXTRA_QR = "io.qzxy.lite.preview.qr";
    private static final long CONFIRM_MS = 20000L, BILL_MS = 30000L, POLL_MS = 2500L, STOP_MS = 10000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final UseSession session = new UseSession();
    private final FieldStartGate gate = new FieldStartGate();
    private TextView deviceText, stateText, countText, logText;
    private Button verify, query, start, stop, reconcile, dismissUnknown;
    private DeviceQr qr;
    private DeviceLookup lookup;
    private AccountSession account;
    private PendingUse pending;
    private String orderNo = "", note = "";
    private boolean busy, destroyed, visible, storageBlocked, stopSent, closedConfirmed, managedActive;
    private long deadline, stopAt, nextPoll;
    private boolean idleVerified;
    private String lastFailure = "";
    private boolean unsupportedChannel;
    private Button alipay;
    private static final String CHANNEL_NOTICE = "服务器拒绝了当前应用的开水请求（10000）。请点“前往支付宝”，在趣智校园小程序确认设备并用水；首次可能需要登录。";

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size);
        text.setTextColor(0xff172236); text.setPadding(0, dp(8), 0, dp(8)); return text;
    }
    private Button button(String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false);
        button.setMinHeight(dp(48)); button.setOnClickListener(v -> action.run()); return button;
    }
    private void log(String value) {
        String text = logText.getText() + "\n" + value;
        logText.setText(text.length() > 4000 ? text.substring(text.length() - 4000) : text);
    }
    private long now() { return SystemClock.elapsedRealtime(); }
    private PendingUseVault vault() { return new PendingUseVault(getApplicationContext()); }
    private long ownerId() {
        try { return Long.parseLong(lookup.account.accountId); }
        catch (RuntimeException invalid) { return 0L; }
    }
    private boolean usable() { return lookup != null && lookup.ready && account != null && ownerId() > 0; }
    private boolean unresolved() {
        State state = session.state();
        return state == State.STARTING || state == State.ACTIVE || state == State.STOPPING
            || state == State.SETTLING || state == State.RECONCILE;
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f7f2); root.setPadding(dp(20), dp(16), dp(20), dp(16));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        root.addView(label("扫码用水 · 现场测试", 24));
        root.addView(label("当前为 10 秒用水测试，会真实扣费。请在水表旁操作，并确认现场可手动停水。", 14));
        deviceText = label("正在核对设备…", 15); root.addView(deviceText);
        stateText = label("", 17); root.addView(stateText);
        countText = label("", 22); root.addView(countText);
        
        verify = button("重新连接", this::verifyDevice); root.addView(verify);
        query = button("刷新状态", this::refreshStatus); root.addView(query);
        start = button("开启用水 · 10 秒短测", this::confirmStart); root.addView(start);
        stop = button("立即结束用水", this::confirmStop); root.addView(stop);
        alipay = button("前往支付宝 · 此水表用水", this::openAlipayWater); root.addView(alipay);
        reconcile = button("核实未完成订单 / 查询账单", this::queryOrder); 
        dismissUnknown = button("已核实未出水，解除失败记录", ()->{}); 
        root.addView(button("返回", this::attemptExit));
        root.addView(button("查看详情", () -> new AlertDialog.Builder(this).setTitle("设备与请求详情").setMessage((lookup==null?deviceText.getText():lookup.text)+"\n\n"+logText.getText()).setPositiveButton("关闭",null).show()));
        logText = label("", 13);
        ScrollView page = new ScrollView(this); page.setFillViewport(true); page.addView(root);
        if(android.os.Build.VERSION.SDK_INT>=30)page.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        setContentView(page); page.requestApplyInsets();
        try { qr = DeviceQr.parse(getIntent().getStringExtra(EXTRA_QR)); }
        catch (RuntimeException invalid) { note = "二维码无法识别，请返回重新扫描。"; render(); return; }
        verifyDevice();
    }

    @Override protected void onStart() { super.onStart(); visible = true; }
    @Override protected void onStop() {
        visible = false; gate.invalidate();
        if (session.state() == State.ACTIVE && managedActive) requestStop();
        // A late start confirmation checks visible and immediately requests stop as well.
        super.onStop();
    }
    @Override protected void onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null); worker.shutdown(); super.onDestroy();
    }
    @Override public void onBackPressed() { attemptExit(); }
    private void attemptExit() {
        if (!unresolved()) { finish(); return; }
        new AlertDialog.Builder(this).setTitle("订单还没有核实完成")
            .setMessage("请先确认现场已停水。退出后会保留未完成记录，下次进入继续核实；"
                + "退出不代表停水或结算成功。")
            .setPositiveButton("现场已停水，返回", (dialog, which) -> finish())
            .setNegativeButton("留在此页", null).show();
    }

    private void render() {
        State state = session.state();
        boolean waiting = state == State.STARTING || state == State.STOPPING || state == State.SETTLING;
        boolean canQuery = usable() && !busy && !waiting && state != State.ACTIVE && !storageBlocked;
        verify.setEnabled(qr != null && !busy && !unresolved());
        query.setEnabled(canQuery); reconcile.setEnabled(canQuery);
        start.setEnabled(!unsupportedChannel && usable() && visible && !busy && !storageBlocked && pending == null
            && state == State.READY && gate.permits(now()));
        stop.setEnabled(state == State.ACTIVE && !stopSent);
        dismissUnknown.setEnabled(state == State.RECONCILE && idleVerified && !busy && !storageBlocked
            && orderNo.isEmpty() && pending != null && pending.isCommand()
            && pending.status().equals("STARTING") && pending.matches(lookup.profile)
            && pending.belongsTo(lookup.account.accountId));
        verify.setVisibility(!usable()?android.view.View.VISIBLE:android.view.View.GONE);
        query.setEnabled(!busy && state!=State.ACTIVE && !waiting);
        stop.setVisibility(state==State.ACTIVE || state==State.STOPPING?android.view.View.VISIBLE:android.view.View.GONE);
        start.setVisibility(state==State.ACTIVE || state==State.STOPPING?android.view.View.GONE:android.view.View.VISIBLE);
        for(Button b:new Button[]{verify,query,start,stop})b.setAlpha(b.isEnabled()?1f:.38f);
        if (state == State.STARTING || state == State.ACTIVE || state == State.STOPPING)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (state == State.STARTING) stateText.setText("开启请求处理中，等待设备确认…");
        else if (state == State.ACTIVE) stateText.setText("设备已确认本账户订单。可立即结束用水。");
        else if (state == State.STOPPING) stateText.setText("正在确认关水，请留意现场水流…");
        else if (state == State.SETTLING) stateText.setText("已确认关水，正在等待账单…");
        else if (state == State.COMPLETED) stateText.setText("本次账单已取得，用水结束。");
        else if (state == State.RECONCILE) stateText.setText(closedConfirmed
            ? "关水已确认，账单仍待核实。点击下方查询账单。"
            : "状态待确认。请先检查现场水流，必要时使用现场停止方式，再核实订单。");
        else if (state == State.READY && pending == null && !storageBlocked && !gate.permits(now()))
            stateText.setText(note.isEmpty() ? "请先查询设备是否空闲。" : note + "\n开启前需重新查询；空闲结果有效 15 秒。");
        else stateText.setText(note.isEmpty() ? "请先查询设备是否空闲。" : note);
        // Also provide an exit route for records created by older builds, which did not persist the refusal.
        alipay.setVisibility(android.view.View.VISIBLE);
        alipay.setEnabled(usable() && !busy && state != State.STARTING && state != State.ACTIVE
            && state != State.STOPPING && state != State.SETTLING);
        if (unsupportedChannel && state != State.ACTIVE && state != State.STOPPING && state != State.SETTLING)
            stateText.setText(CHANNEL_NOTICE + (pending == null ? "" : "\n历史用水记录仍保留，可刷新核实。"));
        countText.setText(state == State.ACTIVE && managedActive
            ? Math.max(0, (stopAt - now() + 999) / 1000) + " 秒后尝试关水" : "");
    }

    private void openAlipayWater() {
        if (!usable() || busy || session.state() == State.STARTING || session.state() == State.ACTIVE
            || session.state() == State.STOPPING || session.state() == State.SETTLING) return;
        final AccountSession login = account;
        final DeviceLookup device = lookup;
        busy = true; render();
        worker.execute(() -> {
            String link = null;
            try { link = SmsAccountApi.alipayWaterLink(login, device); }
            catch (Exception failure) { /* Never include credentials, server content or URI in an error. */ }
            final String destination = link;
            main.post(() -> {
                if (destroyed) return;
                busy = false; render();
                if (!visible) return;
                if (destination == null) {
                    new AlertDialog.Builder(this).setMessage("未取得完整的账号与校园跳转信息。请检查网络；仍失败时重新登录并扫码。")
                        .setPositiveButton("知道了",null).show(); return;
                }
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(destination)).setPackage("com.eg.android.AlipayGphone");
                try { startActivity(intent); }
                catch (android.content.ActivityNotFoundException | SecurityException failure) {
                    new AlertDialog.Builder(this).setMessage("未能打开支付宝，请确认已安装支付宝。")
                        .setPositiveButton("知道了",null).show();
                }
            });
        });
    }

    private void verifyDevice() {
        if (busy || qr == null || unresolved()) return;
        gate.invalidate(); idleVerified = false; busy = true; render();
        worker.execute(() -> {
            DeviceLookup found = null; AccountSession active = null; PendingUse record = null;
            boolean blocked = false; String message = "";
            try {
                active = new AccountVault(getApplicationContext()).load();
                if (active == null) message = "请先到“我的”登录。";
                else { found = SmsAccountApi.device(active, qr); message = found.text; }
                record = vault().load();
                if(record!=null && found!=null && found.ready && record.mayRetireIdleUnknown(found.account.accountId,System.currentTimeMillis())) {
                    WaterOrder old=WaterApi.recordedOrderStatus(active,found.account,record);
                    if(old.status()==WaterOrder.Status.NONE) {
                        vault().retireIdleUnknown(record,found.account.accountId);
                        record=vault().load();
                    }
                }
            } catch (Exception failure) { blocked = true; message = "设备或用水记录读取失败，暂不能开启：" + failureText(failure); }
            final DeviceLookup device = found; final AccountSession login = active;
            final PendingUse saved = record; final boolean failed = blocked; final String text = message;
            main.post(() -> {
                if (destroyed) return;
                lookup = device; account = login; pending = saved; storageBlocked = failed;
                unsupportedChannel = device != null && device.account != null && getSharedPreferences("water-channel", MODE_PRIVATE)
                    .getBoolean("alipay-" + device.account.projectId, false);
                deviceText.setText(device==null?text:device.profile.deviceName()+"\n"+device.profile.location()); busy = false;
                if (usable()) {
                    if (session.state() == State.READY) session.cancelBeforeStart();
                    if (session.state() == State.IDLE || session.state() == State.COMPLETED) {
                        session.resolve(); session.deviceResolved();
                    }
                    if (saved != null) {
                        log(saved.describe());
                        if (!saved.matches(device.profile) || (saved.isCommand() && !saved.belongsTo(device.account.accountId))) {
                            storageBlocked = true; note = "旧设备状态还未核实，请刷新重试；仍在用水的订单请在原版处理。"; log(note);
                        } else if (saved.isCommand()) {
                            orderNo = saved.orderNo();
                            session.cancelBeforeStart(); session.restore(State.RECONCILE);
                        }
                    }
                }
                render();
                if (usable() && !storageBlocked) queryOrder();
            });
        });
    }

    private void refreshStatus() {
        if(busy)return;
        if(unresolved())queryOrder();else verifyDevice();
    }

    private void queryOrder() {
        State state = session.state();
        if (busy || !usable() || storageBlocked || state == State.ACTIVE || state == State.STARTING
                || state == State.STOPPING || state == State.SETTLING) return;
        gate.invalidate(); busy = true; render();
        if (state == State.RECONCILE && !orderNo.isEmpty()) {
            // Reconcile the known order itself; idle is not proof that the old bill was settled.
            worker.execute(() -> {
                WaterEvents.Stopped result = null;
                try { result = WaterControl.closeResult(account, lookup, orderNo); } catch (Exception ignored) { }
                final WaterEvents.Stopped value = result;
                main.post(() -> {
                    if (destroyed) return;
                    busy = false;
                    if (value != null && value.satisfied()) beginSettlement(); else queryUsing();
                });
            });
        } else queryUsing();
    }

    private void queryUsing() {
        idleVerified = false; busy = true; render();
        worker.execute(() -> {
            WaterOrder result = null; PendingUse saved = null; String error = "";
            try {
                result = WaterApi.orderStatus(account, lookup);
                PendingUse previous=vault().load();
                if(result.status()==WaterOrder.Status.NONE && previous!=null && previous.matches(lookup.profile)
                    && previous.mayRetireIdleUnknown(lookup.account.accountId,System.currentTimeMillis()))
                    vault().retireIdleUnknown(previous,lookup.account.accountId);
                vault().observe(lookup.profile, result); saved = vault().load();
            } catch (Exception failure) { error = "订单查询失败：" + failureText(failure); }
            final WaterOrder order = result; final PendingUse record = saved; final String message = error;
            main.post(() -> {
                if (destroyed) return;
                busy = false;
                if (!message.isEmpty() || order == null) { note = message; log(message); render(); return; }
                pending = record; idleVerified = order.status() == WaterOrder.Status.NONE; log(order.describe());
                if(record==null && idleVerified && session.state()==State.RECONCILE && orderNo.isEmpty()){recreate();return;}
                if (order.status() == WaterOrder.Status.MINE) {
                    if (!orderNo.isEmpty() && !orderNo.equals(order.orderNo())) {
                        log("当前订单与本机记录不同，保留原记录，请在官方客户端核实。"); render(); return;
                    }
                    orderNo = order.orderNo();
                    if (session.state() == State.READY) { session.cancelBeforeStart(); session.restore(State.RECONCILE); }
                    if (session.state() == State.RECONCILE) {
                        session.reconcile(State.ACTIVE); stopSent = false; managedActive = false;
                        log("已找回本账户订单。请确认现场设备后点击结束；查询和返回不会自动关掉找回的订单。");
                    }
                } else if (session.state() == State.READY && record == null) {
                    gate.observed(order, now()); note = order.describe(); scheduleTick();
                } else if (session.state() == State.RECONCILE) {
                    log("未取得原订单的完成结果，继续保留记录；空闲不等于原订单已结算。");
                } else note = order.describe();
                render();
            });
        });
    }

    private void confirmStart() {
        if (!start.isEnabled()) return;
        new AlertDialog.Builder(this).setTitle("确认在这台水表前开始？")
            .setMessage("本次会真实出水并扣费，约 10 秒后尝试结束。请确认上方设备位置正确，"
                + "并能使用现场停止方式。离开本页也会尝试关水。")
            .setPositiveButton("开始短时出水", (dialog, which) -> sendStart())
            .setNegativeButton("取消", null).show();
    }

    private void sendStart() {
        if (unsupportedChannel || busy || !visible || !usable() || storageBlocked || pending != null || session.state() != State.READY) return;
        try { gate.consume(now()); }
        catch (IllegalStateException stale) { note = "空闲查询已过期，请重新查询。"; render(); return; }
        session.requestStart(); busy = true; stopSent = false; closedConfirmed = false; managedActive = true;
        stopAt = now() + STOP_MS; deadline = now() + CONFIRM_MS; nextPoll = now();
        render(); scheduleTick();
        worker.execute(() -> {
            try { vault().beginCommand(command(State.STARTING, "")); }
            catch (Exception failure) {
                main.post(() -> { if (!destroyed) { busy = false; fail("记录保存失败，未发送开启指令。请返回核实本机记录。"); } }); return;
            }
            String message = "开启请求已受理，正在查询设备结果。";
            boolean rejectedChannel = false;
            try { WaterControl.start(account, lookup); }
            catch (WaterControl.UnsupportedChannel failure) {
                rejectedChannel = true; message = CHANNEL_NOTICE;
                getSharedPreferences("water-channel", MODE_PRIVATE).edit()
                    .putBoolean("alipay-" + lookup.account.projectId, true).commit();
            }
            catch (Exception failure) { message = "开启请求失败：" + failureText(failure)
                + "。继续只读核实，不会重发开启指令。"; }
            final String text = message;
            final boolean channelRefused = rejectedChannel;
            main.post(() -> {
                if (destroyed) return;
                busy = false; log(text);
                if (channelRefused) {
                    unsupportedChannel = true; managedActive = false; gate.invalidate();
                    main.removeCallbacks(tick); session.connectionLost(); note = CHANNEL_NOTICE;
                    render(); return;
                }
                if (session.state() == State.STARTING) { poll(); scheduleTick(); }
                render();
            });
        });
    }

    private PendingUse command(State state, String number) {
        return PendingUse.command(lookup.profile, lookup.account.accountId, number, state, System.currentTimeMillis());
    }
    private void fail(String message) {
        log(message); session.connectionLost(); gate.invalidate();
        main.removeCallbacks(tick); render();
    }
    private void scheduleTick() { main.removeCallbacks(tick); main.postDelayed(tick, 250); }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            State state = session.state();
            if (state == State.ACTIVE && managedActive && now() >= stopAt) { requestStop(); return; }
            if (state == State.STARTING || state == State.STOPPING || state == State.SETTLING) {
                if (now() >= deadline) { fail("结果确认超时。请检查现场水流，再点击核实订单；未重发控制指令。"); return; }
                if (!busy && now() >= nextPoll) poll();
            }
            render();
            if (state == State.STARTING || state == State.STOPPING || state == State.SETTLING
                    || (state == State.ACTIVE && managedActive) || (state == State.READY && gate.permits(now()))) scheduleTick();
        }
    };

    /** Only one result query may be in flight. Queued polls cannot delay a stop. */
    private void poll() {
        if (busy) return;
        final State phase = session.state();
        if (phase != State.STARTING && phase != State.STOPPING && phase != State.SETTLING) return;
        busy = true; nextPoll = now() + POLL_MS; render();
        worker.execute(() -> {
            WaterEvents.Started opened = null; WaterEvents.Stopped stopped = null; WaterEvents.Settled bill = null;
            String storageNote = "";
            try {
                if (phase == State.STARTING) {
                    opened = WaterControl.startResult(account, lookup);
                    if (opened.usableFor(ownerId(), lookup.profile.requestSerial())) {
                        // The result endpoint can describe a previous start. Match the current owned order.
                        WaterOrder current = WaterApi.orderStatus(account, lookup);
                        if (current.status() != WaterOrder.Status.MINE || !current.orderNo().equals(opened.orderNo())) opened = null;
                        else {
                            try { vault().saveCommand(command(State.ACTIVE, opened.orderNo())); }
                            catch (Exception failure) { storageNote = "订单号保存失败，本页仍会尝试关水，请留意现场。"; }
                        }
                    }
                } else if (phase == State.STOPPING) stopped = WaterControl.closeResult(account, lookup, orderNo);
                else {
                    bill = WaterControl.settle(account, lookup, orderNo);
                    try { vault().clearSettled(lookup.profile, lookup.account.accountId, orderNo); }
                    catch (Exception failure) { storageNote = "账单已取得，但本机记录清理失败，下次进入需再核实。"; }
                }
            } catch (Exception pendingReply) {
                // A parsed start result is insufficient if the current-order check itself failed.
                if (phase == State.STARTING) opened = null;
                storageNote = (phase == State.STARTING ? "开启确认" : phase == State.STOPPING ? "停止确认" : "账单查询")
                    + "失败：" + failureText(pendingReply);
            }
            final WaterEvents.Started startResult = opened; final WaterEvents.Stopped stopResult = stopped;
            final WaterEvents.Settled settlement = bill; final String warning = storageNote;
            main.post(() -> {
                if (destroyed) return;
                busy = false;
                if (session.state() != phase) {
                    if (session.state() == State.RECONCILE && startResult != null
                            && startResult.usableFor(ownerId(), lookup.profile.requestSerial())) {
                        orderNo = startResult.orderNo(); log("已收到延迟的订单号，请核实订单并结束用水。");
                    }
                    render(); return;
                }
                if (!warning.isEmpty() && !warning.equals(lastFailure)) { log(warning); lastFailure = warning; }
                if (phase == State.STARTING && startResult != null) {
                    if (!startResult.usableFor(ownerId(), lookup.profile.requestSerial())) {
                        fail("开启结果与当前设备、账户或订单不匹配。请核实现场水流。"); return;
                    }
                    orderNo = startResult.orderNo(); session.startAcknowledged(); log(startResult.describe());
                    if (!visible || now() >= stopAt || !warning.isEmpty()) { requestStop(); return; }
                } else if (phase == State.STOPPING && stopResult != null && stopResult.satisfied()) {
                    beginSettlement(); return;
                } else if (phase == State.SETTLING && settlement != null) {
                    session.settlementAcknowledged(); log(settlement.describe()); main.removeCallbacks(tick);
                }
                render();
            });
        });
    }

    private void requestStop() {
        if (stopSent || session.state() != State.ACTIVE || !usable() || orderNo.isEmpty()) return;
        stopSent = true; session.requestStop(); busy = true; gate.invalidate();
        deadline = now() + CONFIRM_MS; nextPoll = now(); render(); scheduleTick();
        worker.execute(() -> {
            // Failure to persist must not prevent a stop; the earlier journal remains available.
            try { vault().saveCommand(command(State.STOPPING, orderNo)); } catch (Exception ignored) { }
            WaterEvents.Stopped result = null;
            try { result = WaterControl.closeOrder(account, lookup, orderNo); } catch (Exception ignored) { }
            final WaterEvents.Stopped value = result;
            main.post(() -> {
                if (destroyed) return;
                busy = false;
                if (session.state() != State.STOPPING) return;
                if (value != null && value.alreadyClosed()) { beginSettlement(); return; }
                // BaseDeviceActivity.s -> BathingNetActivity.r3 waits for O4/close-result.
                // A successful dispatch alone does not prove the device stopped.
                log(value != null && value.accepted() ? "关水请求已受理，正在查询设备停止结果。"
                    : "关水请求未取得明确结果，正在只读查询，不会自动重发关水指令。");
                poll(); render();
            });
        });
    }

    private void confirmStop() {
        if (managedActive) { requestStop(); return; }
        if (session.state() != State.ACTIVE || stopSent) return;
        new AlertDialog.Builder(this).setTitle("结束找回的订单？")
            .setMessage("请确认这是你要停止的水表和订单。结束后会扣取已使用的费用。")
            .setPositiveButton("确认结束", (dialog, which) -> requestStop())
            .setNegativeButton("取消", null).show();
    }

    private String failureText(Exception failure) {
        if (failure instanceof java.net.SocketTimeoutException) return "网络超时";
        if (failure instanceof java.net.UnknownHostException) return "无法解析服务器地址";
        if (failure instanceof javax.net.ssl.SSLException) return "安全连接失败";
        if (failure instanceof org.json.JSONException) return "服务器返回格式不符合预期";
        if (failure instanceof HttpForm.ResponseFailure || failure instanceof IllegalArgumentException) {
            String text = failure.getMessage() == null ? "响应无效" : failure.getMessage();
            if (account != null && !account.loginCode().isEmpty()) text = text.replace(account.loginCode(), "[已隐藏]");
            if (lookup != null) {
                if (!lookup.account.telephone.isEmpty()) text = text.replace(lookup.account.telephone, "[已隐藏]");
                if (!lookup.profile.requestSerial().isEmpty()) text = text.replace(lookup.profile.requestSerial(), "[已隐藏]");
            }
            text = text.replaceAll("[\\p{Cntrl}]", " ").replaceAll("[A-Za-z0-9_-]{20,}", "[已隐藏]");
            return text.length() > 180 ? text.substring(0, 180) : text;
        }
        return "请求未完成（" + failure.getClass().getSimpleName() + "）";
    }

    private void beginSettlement() {
        if (session.state() == State.STOPPING) session.stopAcknowledged();
        else if (session.state() == State.RECONCILE) session.reconcile(State.SETTLING);
        else return;
        closedConfirmed = true; deadline = now() + BILL_MS; nextPoll = now(); busy = true;
        render(); scheduleTick();
        worker.execute(() -> {
            try { vault().saveCommand(command(State.SETTLING, orderNo)); } catch (Exception ignored) { }
            main.post(() -> {
                if (destroyed) return;
                busy = false;
                if (session.state() == State.SETTLING) poll();
                render();
            });
        });
    }

    static { if (!BuildFlags.WATER_CONTROL) throw new IllegalStateException("Field-only activity"); }
}
