package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hands a signed APP-payment order to the Alipay client through the same channel its own SDK uses:
 * the {@code IAlixPay} service inside {@code com.eg.android.AlipayGphone}.
 *
 * <p>An earlier version opened {@code alipays://platformapi/startapp?appId=...&url=<order>}; that
 * entry expects a web-page URL, not an APP order string, so Alipay opened but never showed a cashier.
 * The service below is what {@code PayTask.payV2} calls: register a callback, ask Alipay to pay, and
 * let Alipay start its own cashier activity. This app never touches card numbers or passwords and
 * never reports success on its own — the balance is re-read afterwards instead.</p>
 *
 * <p>Everything here is read-only towards the order: it is passed through untouched and paid once.</p>
 */
final class AlipayHandoff {
    private static final String PACKAGE = "com.eg.android.AlipayGphone";
    private static final String SERVICE_ACTION = "com.eg.android.AlipayGphone.IAlixPay";
    private static final String ALIXPAY = "com.alipay.android.app.IAlixPay";
    private static final String CALLBACK = "com.alipay.android.app.IRemoteServiceCallback";
    private static final Pattern STATUS = Pattern.compile("resultStatus=\\{([0-9]{1,4})\\}");
    private static final int TIMEOUT_MS = 12000;

    interface Result {
        /** {@code paid} is only true when Alipay itself reports 9000. */
        void done(boolean paid, String message);
    }

    private final Activity activity;
    private final String order;
    private final Result callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Callback stub = new Callback();
    private IBinder binder;
    private boolean connected, finished, bound;

    private AlipayHandoff(Activity activity, String order, Result callback) {
        this.activity = activity;
        this.order = order;
        this.callback = callback;
    }

    static void start(Activity activity, String order, Result callback) {
        new AlipayHandoff(activity, order, callback).bind();
    }

    private void bind() {
        Intent intent = new Intent(SERVICE_ACTION).setPackage(PACKAGE);
        try {
            bound = activity.bindService(intent, connection, Activity.BIND_AUTO_CREATE);
        } catch (RuntimeException unavailable) {
            finish(false, "无法连接支付宝，请确认已安装最新版支付宝。");
            return;
        }
        if (!bound) { finish(false, "无法连接支付宝，请确认已安装最新版支付宝。"); return; }
        main.postDelayed(() -> {
            if (!connected && !finished) finish(false, "支付宝没有响应。请打开支付宝确认可以正常使用后重试。");
        }, TIMEOUT_MS);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            connected = true;
            binder = service;
            new Thread(AlipayHandoff.this::pay, "alipay-handoff").start();
        }
        @Override public void onServiceDisconnected(ComponentName name) { binder = null; }
    };

    /** Blocks until the user finishes in Alipay; always called off the main thread. */
    private void pay() {
        try {
            int version = version();
            if (version >= 3) registerCallback03(); else registerCallback();
            if (version >= 3) r03();
            String result = version >= 2 ? pay02() : payOrder();
            String text = result == null ? "" : result;
            Matcher status = STATUS.matcher(text);
            if (!status.find()) { finish(false, "支付宝没有返回支付结果。这笔订单未支付。"); return; }
            int code;
            try { code = Integer.parseInt(status.group(1)); } catch (NumberFormatException invalid) { code = -1; }
            finish(code == 9000, describe(code));
        } catch (RemoteException | RuntimeException failure) {
            finish(false, "支付宝拒绝了这笔支付请求（" + shortReason(failure) + "）。订单未支付。");
        }
    }

    private static String describe(int code) {
        switch (code) {
            case 9000: return "支付成功，正在刷新余额。";
            case 6001: return "你取消了支付，这笔订单没有付款。";
            case 6002: return "网络连接异常，这笔订单没有付款。";
            case 4000: return "支付失败，订单没有付款。";
            case 8000: return "支付结果未确认，请到支付宝账单里核对。";
            case 5000: return "订单重复提交，请到支付宝账单里核对。";
            default: return "支付宝返回结果 " + code + "，请到支付宝账单里核对。";
        }
    }

    private static String shortReason(Throwable failure) {
        String text = failure.getMessage();
        if (text == null || text.isEmpty()) return failure.getClass().getSimpleName();
        return text.length() > 60 ? text.substring(0, 60) : text;
    }

    private void finish(boolean paid, String message) {
        if (finished) return;
        finished = true;
        try { if (connected) unregister(); } catch (RemoteException | RuntimeException ignored) { }
        try { if (bound) activity.unbindService(connection); } catch (RuntimeException ignored) { }
        bound = false;
        main.post(() -> { if (!activity.isFinishing()) callback.done(paid, message); });
    }

    // ---- IAlixPay calls ------------------------------------------------------------------

    private Parcel begin() {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(ALIXPAY);
        return data;
    }

    private int version() throws RemoteException {
        Parcel data = begin(), reply = Parcel.obtain();
        try {
            binder.transact(8, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } finally { reply.recycle(); data.recycle(); }
    }

    private void registerCallback() throws RemoteException {
        Parcel data = begin(), reply = Parcel.obtain();
        try {
            data.writeStrongBinder(stub);
            binder.transact(3, data, reply, 0);
            reply.readException();
        } finally { reply.recycle(); data.recycle(); }
    }

    private void registerCallback03() throws RemoteException {
        Parcel data = begin(), reply = Parcel.obtain();
        try {
            data.writeStrongBinder(stub);
            data.writeString(order);
            data.writeMap(null);
            binder.transact(11, data, reply, 0);
            reply.readException();
        } finally { reply.recycle(); data.recycle(); }
    }

    private void r03() {
        Parcel data = begin();
        try {
            data.writeString("pay");
            data.writeString("bind_pay");
            data.writeMap(null);
            binder.transact(10, data, null, ParcelableFlags.ONE_WAY);
        } catch (RemoteException ignored) {
        } finally { data.recycle(); }
    }

    private String payOrder() throws RemoteException {
        Parcel data = begin(), reply = Parcel.obtain();
        try {
            data.writeString(order);
            binder.transact(1, data, reply, 0);
            reply.readException();
            return reply.readString();
        } finally { reply.recycle(); data.recycle(); }
    }

    private String pay02() throws RemoteException {
        Parcel data = begin(), reply = Parcel.obtain();
        try {
            Map<String, String> extra = new HashMap<>();
            extra.put("ts_bind", "0");
            data.writeString(order);
            data.writeMap(extra);
            binder.transact(9, data, reply, 0);
            reply.readException();
            return reply.readString();
        } finally { reply.recycle(); data.recycle(); }
    }

    private void unregister() throws RemoteException {
        Parcel data = begin(), reply = Parcel.obtain();
        try {
            data.writeStrongBinder(stub);
            binder.transact(4, data, reply, 0);
            reply.readException();
        } finally { reply.recycle(); data.recycle(); }
    }

    private static final class ParcelableFlags { static final int ONE_WAY = 1; }

    /** Alipay calls back here to open its cashier and to report that payment ended. */
    private final class Callback extends android.os.Binder {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == 1598968902) { reply.writeString(CALLBACK); return true; }
            switch (code) {
                case 1: {
                    data.enforceInterface(CALLBACK);
                    String pkg = data.readString(), cls = data.readString();
                    data.readInt();
                    Bundle extras = data.readInt() != 0 ? Bundle.CREATOR.createFromParcel(data) : null;
                    open(pkg, cls, extras);
                    reply.writeNoException();
                    return true;
                }
                case 2: {
                    data.enforceInterface(CALLBACK);
                    data.readInt();
                    data.readString();
                    reply.writeNoException();
                    return true;
                }
                case 3: {
                    data.enforceInterface(CALLBACK);
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }
                case 4: {
                    data.enforceInterface(CALLBACK);
                    reply.writeNoException();
                    reply.writeInt(1);
                    return true;
                }
                case 5: {
                    data.enforceInterface(CALLBACK);
                    data.readString();
                    data.readString();
                    data.readHashMap(Callback.class.getClassLoader());
                    reply.writeNoException();
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    /** The cashier is an activity inside Alipay; opening it is the actual "跳到支付宝" step. */
    private void open(String pkg, String cls, Bundle extras) {
        if (pkg == null || cls == null) return;
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(pkg, cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extras != null) intent.putExtras(extras);
        main.post(() -> {
            try { activity.startActivity(intent); }
            catch (RuntimeException blocked) { finish(false, "无法打开支付宝收银台，这笔订单未支付。"); }
        });
    }
}
