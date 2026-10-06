package io.qzxy.lite.preview;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.Button;
import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.PendingUse;
import io.qzxy.lite.core.UseSession;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.json.JSONObject;

/** Real Activity/Keystore, synthetic HTTP. This source is excluded from every delivery APK. */
public final class FieldDeviceTests {
    private final Instrumentation runner;
    private int checks;
    private WaterActivity activity;
    private final FakeServer server = new FakeServer();
    private FieldDeviceTests(Instrumentation runner) { this.runner = runner; }
    public static int run(Instrumentation runner) throws Exception { return new FieldDeviceTests(runner).run(); }
    private void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private Object get(String name) {
        try { Field field = WaterActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void put(String name, Object value) {
        try { Field field = WaterActivity.class.getDeclaredField(name); field.setAccessible(true); field.set(activity, value); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void call(String name) {
        runner.runOnMainSync(() -> {
            try { Method method = WaterActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity); }
            catch (Exception error) { throw new AssertionError(error); }
        });
    }
    private boolean enabled(String button) {
        boolean[] enabled = {false}; runner.runOnMainSync(() -> enabled[0] = ((Button) get(button)).isEnabled()); return enabled[0];
    }
    private String state() { return ((UseSession) get("session")).state().name(); }
    private void await(String state) {
        long until = SystemClock.elapsedRealtime() + 12000;
        while (SystemClock.elapsedRealtime() < until) {
            boolean[] ready = {false};
            runner.runOnMainSync(() -> ready[0] = state().equals(state) && !(Boolean) get("busy"));
            if (ready[0]) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + state + "; actual=" + state());
    }
    private void open() {
        Intent intent = new Intent(runner.getTargetContext(), WaterActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(WaterActivity.EXTRA_QR, "KLCXKJ-Water,G,721086402865");
        activity = (WaterActivity) runner.startActivitySync(intent);
    }
    private void close() {
        runner.runOnMainSync(() -> activity.finish()); runner.waitForIdleSync();
    }
    private int run() throws Exception {
        check(runner.getTargetContext().checkSelfPermission("android.permission.INTERNET")
            != android.content.pm.PackageManager.PERMISSION_GRANTED, "Test APK must have no internet permission");
        HttpForm.testTransport = server;
        PendingUseVault vault = new PendingUseVault(runner.getTargetContext());
        AccountVault account = new AccountVault(runner.getTargetContext());
        vault.clear(); account.save(new AccountSession("42", "synthetic-only", "9"));
        try {
            // Occupied and failed queries cannot reuse an earlier idle answer.
            server.occupied = true; open(); await("READY");
            check(!enabled("start"), "Other user's order must block start");
            server.occupied = false; call("queryOrder"); await("READY");
            check(enabled("start"), "Fresh idle enables start");
            server.failUsing = true; call("queryOrder"); await("READY");
            check(!enabled("start"), "Failed refresh must invalidate idle");
            check(server.starts == 0, "Queries cannot send start");
            server.failUsing = false; call("queryOrder"); await("READY");
            runner.runOnMainSync(() -> ((io.qzxy.lite.core.FieldStartGate) get("gate")).invalidate());
            call("sendStart"); check(server.starts == 0, "Expired confirmation cannot start");
            call("queryOrder"); await("READY");

            // HTTP start ACK has no data. Only the separate matching device/current-order result activates.
            call("sendStart"); call("sendStart"); await("ACTIVE");
            check(server.starts == 1, "Duplicate taps send exactly one start");
            check(server.startPolls > 0, "Start ACK requires a separate result query");
            check(vault.load().orderNo().equals("TEST-ORDER-1"), "Actual order must be durable");
            check(vault.load().isCommand(), "Persist a command journal, not just an observation");
            // A successful idle query from ScanActivity cannot destroy this active order.
            DeviceLookup lookup = (DeviceLookup) get("lookup");
            vault.observe(lookup.profile, io.qzxy.lite.core.WaterOrder.parse(0, null));
            check(vault.load() != null, "Idle cannot erase unresolved control journal");

            // Ambiguous close does not trigger a second close. The fallback is read-only.
            server.failClose = true; server.billReady = false;
            call("requestStop"); call("requestStop");
            long stopWait = SystemClock.elapsedRealtime() + 10000;
            while (!state().equals("SETTLING") && SystemClock.elapsedRealtime() < stopWait) SystemClock.sleep(50);
            check(state().equals("SETTLING"), "Close result query reaches settlement");
            check(server.closes == 1, "Ambiguous stop is never automatically repeated");
            check(server.closePolls > 0, "Close fallback actually runs");
            runner.runOnMainSync(() -> { put("deadline", 0L); ((Runnable) get("tick")).run(); });
            await("RECONCILE");
            check(vault.load() != null, "Missing bill retains recovery record");
            check(!enabled("start"), "Missing bill never re-enables start");
            close();

            // Process recreation uses the same order, then obtains its bill without sending controls.
            server.billReady = true; open(); await("COMPLETED");
            check(vault.load() == null, "Only verified bill clears matching journal");
            check(server.starts == 1 && server.closes == 1, "Recovery sends no controls");
            close();

            // An existing owned order is offered for manual ending, never stopped merely by viewing it.
            server.inUse = true; open(); await("ACTIVE");
            check(server.starts == 1 && server.closes == 1, "Opening recovery page is read-only");
            close(); SystemClock.sleep(200);
            check(server.closes == 1, "Leaving recovered order is read-only");
            vault.clear();

            // Force the local countdown to expire, without waiting ten wall-clock seconds.
            server.inUse = false; server.failClose = false; server.closePending = true; open(); await("READY");
            call("sendStart"); await("ACTIVE");
            int previousBills = server.billCalls;
            runner.runOnMainSync(() -> { put("stopAt", 0L); ((Runnable) get("tick")).run(); });
            await("STOPPING");
            check(server.billCalls == previousBills, "Close ACK alone must not query a bill");
            check(!(Boolean) get("closedConfirmed"), "Close ACK alone must not claim stopped");
            server.closePending = false;
            await("COMPLETED");
            check(server.starts == 2 && server.closes == 2, "Countdown dispatches exactly one stop");
            check(vault.load() == null, "Automatic stop settles same order"); close();

            // Start confirmation arriving after the page is hidden must still trigger a stop.
            server.holdResult = new java.util.concurrent.CountDownLatch(1);
            server.resultEntered = new java.util.concurrent.CountDownLatch(1);
            open(); await("READY"); call("sendStart");
            check(server.resultEntered.await(5, java.util.concurrent.TimeUnit.SECONDS), "Delayed start query entered");
            call("onStop"); server.holdResult.countDown(); await("COMPLETED");
            check(server.starts == 3 && server.closes == 3, "Late start after leaving page is stopped once");
            check(vault.load() == null, "Late confirmation remains recoverable through settlement"); close();
            server.holdResult = null;

            // A result for an old order must not activate a new session.
            server.staleResult = true; open(); await("READY"); call("sendStart");
            long queryWait = SystemClock.elapsedRealtime() + 5000;
            while (server.startPolls < 4 && SystemClock.elapsedRealtime() < queryWait) SystemClock.sleep(50);
            runner.runOnMainSync(() -> { put("deadline", 0L); ((Runnable) get("tick")).run(); });
            await("RECONCILE");
            check(server.closes == 3, "Stale result must not supply a stop order number");
            check(vault.load() != null, "Unknown start outcome remains recorded"); close(); vault.clear();
            server.staleResult = false; server.inUse = false;

            PendingUse reservation = PendingUse.command(lookup.profile, "7", "", UseSession.State.STARTING, 1789000000000L);
            vault.beginCommand(reservation);
            boolean duplicateRejected = false;
            try { new PendingUseVault(runner.getTargetContext()).beginCommand(reservation); }
            catch (IOException expected) { duplicateRejected = true; }
            check(duplicateRejected, "A second activity cannot reserve another start");
            vault.clear();

            // Missing Keystore key must not disguise an existing journal as no pending use.
            PendingUse intent = PendingUse.command(lookup.profile, "7", "TEST-ORDER-2", UseSession.State.ACTIVE, 1789000000000L);
            vault.saveCommand(intent);
            vault.clearSettled(lookup.profile, "8", "TEST-ORDER-2");
            check(vault.load() != null, "Wrong account cannot clear journal");
            vault.clearSettled(lookup.profile, "7", "WRONG-ORDER");
            check(vault.load() != null, "Wrong order cannot clear journal");
            java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
            keys.deleteEntry("qingyong-pending-use-v1");
            boolean rejected = false;
            try { vault.load(); } catch (java.security.GeneralSecurityException expected) { rejected = true; }
            check(rejected, "Missing key must be an error, not absent");
            return checks;
        } finally {
            if (activity != null) runner.runOnMainSync(() -> activity.finish());
            runner.waitForIdleSync(); vault.clear(); account.clear(); HttpForm.testTransport = null;
        }
    }

    private static final class FakeServer implements HttpForm.TestTransport {
        volatile boolean occupied, inUse, failUsing, failClose, billReady, staleResult, closePending;
        volatile java.util.concurrent.CountDownLatch holdResult, resultEntered;
        volatile int starts, closes, startPolls, closePolls, billCalls;
        @Override public JSONObject request(String path, String form, boolean post) throws IOException, org.json.JSONException {
            if (path.equals("user/info")) return new JSONObject("{\"errorCode\":0,\"data\":{\"userId\":42,\"telephone\":\"13800000000\",\"userAccount\":{\"accountId\":7,\"projectId\":9}}}");
            if (path.equals("device/info/mac")) return new JSONObject("{\"errorCode\":0,\"data\":{\"projectId\":9,\"deviceId\":7,\"deviceName\":\"离线测试水表\",\"bigTypeId\":4,\"isUse\":1,\"snCode\":\"SYNTHETIC0002\",\"chargeMode\":17,\"isOnline\":1}}");
            if (!post) throw new AssertionError("Water route must be POST");
            if (path.equals(WaterApi.ORDER_USING_PATH)) {
                if (failUsing) throw new IOException("Synthetic timeout");
                if (occupied) return new JSONObject("{\"errorCode\":307,\"data\":{\"isOwner\":false}}");
                if (inUse) return new JSONObject("{\"errorCode\":307,\"data\":{\"isOwner\":true,\"orderNo\":\"TEST-ORDER-1\",\"preDeductMoney\":10000}}");
                return new JSONObject("{\"errorCode\":0}");
            }
            if (path.equals(WaterControl.DOWN_RATE_PATH)) { starts++; inUse = true; return new JSONObject("{\"errorCode\":0}"); }
            if (path.equals(WaterControl.DOWN_RATE_RESULT_PATH)) {
                startPolls++;
                if (holdResult != null) {
                    resultEntered.countDown();
                    try { if (!holdResult.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("Synthetic timeout"); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
                }
                JSONObject response = new JSONObject("{\"errorCode\":0,\"data\":{\"accountId\":7,\"orderNo\":\"TEST-ORDER-1\",\"deviceSnCode\":\"SYNTHETIC0002\",\"preDeductMoney\":10000}}");
                if (staleResult) response.getJSONObject("data").put("orderNo", "OLD-ORDER");
                return response;
            }
            if (path.equals(WaterControl.CLOSE_ORDER_PATH)) {
                closes++; inUse = false;
                if (!form.contains("orderNo=TEST-ORDER-1")) throw new AssertionError("Wrong order closed");
                if (failClose) throw new IOException("Synthetic lost reply");
                return new JSONObject("{\"errorCode\":0}");
            }
            if (path.equals(WaterControl.CLOSE_RESULT_PATH)) { closePolls++; return new JSONObject(inUse || closePending ? "{\"errorCode\":500}" : "{\"errorCode\":0}"); }
            if (path.equals(WaterControl.CONSUME_RESULT_PATH)) {
                billCalls++;
                return new JSONObject(billReady
                    ? "{\"errorCode\":0,\"data\":{\"upMoney\":20,\"perMoney\":10000,\"upLeadMoney\":9980}}" : "{\"errorCode\":0,\"data\":{}}");
            }
            throw new AssertionError("Unexpected route: " + path);
        }
    }
}
