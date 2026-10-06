package io.qzxy.lite.preview;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.SeekBar;
import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.DeviceQr;
import io.qzxy.lite.core.PendingUse;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Camera frames are decoded locally; identifying a device does not start it. */
@SuppressWarnings("deprecation")
public final class ScanActivity extends Activity implements SurfaceHolder.Callback {
    private static final int CAMERA_PERMISSION = 10;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean decoding = new AtomicBoolean();
    private SurfaceView preview;
    private FrameLayout viewport;
    private TextView status;
    private Button query, order, retry, torch;
    private boolean torchOn;
    private SeekBar zoom;
    private TextView zoomLabel;
    private boolean focusing;
    private boolean autoFocusMode;
    private final Runnable focusAgain = () -> focusCamera();
    private Camera camera;
    private boolean resumed, surfaceReady, scanning = true, destroyed, busy;
    private int generation;
    private long lastDecode;
    private DeviceQr recognized;
    private DeviceLookup lookup;
    private String recognizedRaw;
    private boolean life798;
    private boolean returningFromDevice;

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(AppUi.INK);
        text.setPadding(0, dp(8), 0, dp(8)); return text;
    }
    private Button button(String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setMinHeight(dp(48));
        button.setOnClickListener(v -> action.run()); return button;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        life798=getIntent().getBooleanExtra("life798",false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(AppUi.BG);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(dp(20) + bars.left, dp(16) + bars.top, dp(20) + bars.right, dp(16) + bars.bottom); return insets;
            });
        }
        TextView back = label("返回", 16); back.setOnClickListener(v -> finish()); root.addView(back);
        root.addView(label(life798?"慧生活 · 扫码":"趣智校园 · 扫码", 22));
        viewport = new FrameLayout(this); viewport.setBackgroundColor(0xff101820); viewport.setClipChildren(true);
        preview = new SurfaceView(this); preview.getHolder().addCallback(this);
        viewport.addView(preview, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        preview.setOnClickListener(v -> focusCamera());
        LinearLayout magnification = new LinearLayout(this); magnification.setGravity(Gravity.CENTER_VERTICAL);
        zoomLabel = label("1.0×", 14); magnification.addView(zoomLabel, new LinearLayout.LayoutParams(dp(52), -2));
        zoom = new SeekBar(this); zoom.setEnabled(false); magnification.addView(zoom, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(magnification);
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { focusCamera(); }
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || camera == null) return;
                try { Camera.Parameters p = camera.getParameters(); p.setZoom(progress); camera.setParameters(p);
                    zoomLabel.setText(String.format(java.util.Locale.ROOT,"%.1f×",p.getZoomRatios().get(progress)/100f));
                } catch (RuntimeException ignored) { }
            }
        });
        LinearLayout actions = new LinearLayout(this);
        retry = button("重新扫描", this::rescan);
        actions.addView(retry, new LinearLayout.LayoutParams(0, -2, 1));
        torch = button("打开手电筒", this::toggleTorch); torch.setEnabled(false);LinearLayout.LayoutParams lightParams=new LinearLayout.LayoutParams(0,-2,1);lightParams.leftMargin=dp(8);actions.addView(torch,lightParams);root.addView(actions);
        query = button("查询设备资料", this::lookup); query.setEnabled(false); root.addView(query);
        order = button("查询订单状态（只读）", this::orderLookup); order.setEnabled(false); root.addView(order);
        // Compile-time switch: in a read-only build javac removes this branch, so neither the button nor
        // the activity name exists in the dex. See scripts/build_preview.py.
        ScrollView scroll = new ScrollView(this); status = label("将设备二维码放入画面。\n"
            + (BuildFlags.WATER_CONTROL ? "识别后核对设备，再前往支付宝用水。"
                : "本版本只读取设备资料和订单状态，不会开启用水。"), 14);
        scroll.addView(status); root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(56)));
        if(life798) {
            query.setVisibility(android.view.View.GONE); order.setVisibility(android.view.View.GONE);
            status.setText("对准设备二维码，轻点画面对焦。");
        }
        if(BuildFlags.WATER_CONTROL) { query.setVisibility(android.view.View.GONE); order.setVisibility(android.view.View.GONE); }
        if(!life798&&BuildFlags.WATER_CONTROL)status.setText("对准设备二维码，轻点画面对焦。");
        setContentView(root); root.requestApplyInsets();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
    }
    @Override protected void onResume() { super.onResume(); resumed = true; if(returningFromDevice){returningFromDevice=false;rescan();}else startCamera(); }
    @Override protected void onPause() { resumed = false; stopCamera(); super.onPause(); }
    @Override protected void onDestroy() { destroyed = true; worker.shutdown(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady = true; startCamera(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { surfaceReady = false; stopCamera(); }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == CAMERA_PERMISSION) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startCamera();
            else status.setText("相机权限未开启，请授权后重新扫描。");
        }
    }
    private void startCamera() {
        if (!resumed || !surfaceReady || !scanning || busy || camera != null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            int selected = -1; Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) { Camera.getCameraInfo(i, info); if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { selected = i; break; } }
            if (selected < 0) { status.setText("未找到可用的后置相机。"); return; }
            camera = Camera.open(selected); Camera.Parameters parameters = camera.getParameters();
            Camera.Size chosen = parameters.getPreviewSize(); long bestArea = 0;
            for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
                long area = (long) size.width * size.height;
                if (area <= 1920L * 1080 && area > bestArea) { chosen = size; bestArea = area; }
            }
            parameters.setPreviewSize(chosen.width, chosen.height); parameters.setPreviewFormat(ImageFormat.NV21);
            List<String> focus = parameters.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            else if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_AUTO)) parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            autoFocusMode = Camera.Parameters.FOCUS_MODE_AUTO.equals(parameters.getFocusMode());
            if(parameters.isZoomSupported()) {
                List<Integer> ratios=parameters.getZoomRatios();int max=0;
                for(int i=0;i<ratios.size();i++)if(ratios.get(i)<=300)max=i;
                zoom.setMax(max);zoom.setProgress(0);zoom.setEnabled(max>0);parameters.setZoom(0);zoomLabel.setText("1.0×");
            }
            camera.setParameters(parameters);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
            int display = (info.orientation - degrees + 360) % 360; camera.setDisplayOrientation(display);
            int orientedWidth = display % 180 == 0 ? chosen.width : chosen.height;
            int orientedHeight = display % 180 == 0 ? chosen.height : chosen.width;
            float scale = Math.min((float) viewport.getWidth() / orientedWidth, (float) viewport.getHeight() / orientedHeight);
            if (scale > 0) preview.setLayoutParams(new FrameLayout.LayoutParams(Math.round(orientedWidth * scale), Math.round(orientedHeight * scale), Gravity.CENTER));
            camera.setPreviewDisplay(preview.getHolder());
            final int width = chosen.width, height = chosen.height, ticket = ++generation;
            camera.setPreviewCallback((bytes, source) -> {
                long now = android.os.SystemClock.elapsedRealtime();
                if (!scanning || bytes == null || now - lastDecode < 250 || !decoding.compareAndSet(false, true)) return;
                lastDecode = now;
                final byte[] frame = bytes.clone();
                worker.execute(() -> {
                    String result = null;
                    try { result = QrDecoder.frame(frame, width, height); } catch (Exception noCode) { /* Keep scanning. */ }
                    final String decoded = result;
                    main.post(() -> { decoding.set(false); if (!destroyed && resumed && ticket == generation && decoded != null) showCode(decoded); });
                });
            });
            camera.startPreview();
            if(autoFocusMode)main.postDelayed(focusAgain,300);
            List<String> flashes = parameters.getSupportedFlashModes();
            torch.setEnabled(flashes != null && flashes.contains(Camera.Parameters.FLASH_MODE_TORCH) && flashes.contains(Camera.Parameters.FLASH_MODE_OFF));
            torch.setText(torch.isEnabled()?"打开手电筒":"此相机不支持手电筒");
        } catch (Exception failure) { stopCamera(); status.setText("相机暂时无法打开，请关闭其他占用相机的应用后重试。"); }
    }
    private void focusCamera() {
        if(camera==null||!scanning||focusing)return;
        final Camera current=camera;
        try {
            String mode=current.getParameters().getFocusMode();
            if(!Camera.Parameters.FOCUS_MODE_AUTO.equals(mode)&&!Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mode)&&!Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO.equals(mode))return;
            focusing=true;current.cancelAutoFocus();
            current.autoFocus((success, source)->{
                if(camera!=source)return;
                focusing=false;
                try{source.cancelAutoFocus();}catch(RuntimeException ignored){}
                if(autoFocusMode){main.removeCallbacks(focusAgain);main.postDelayed(focusAgain,1800);}
            });
        } catch(RuntimeException ignored){focusing=false;if(autoFocusMode){main.removeCallbacks(focusAgain);main.postDelayed(focusAgain,1800);}}
    }
    private void toggleTorch() {
        if (camera == null || !scanning) return;
        try {
            Camera.Parameters parameters = camera.getParameters();
            boolean next = !torchOn;
            parameters.setFlashMode(next ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(parameters);
            torchOn = next;
            torch.setText(torchOn ? "关闭手电筒" : "打开手电筒");
        } catch (RuntimeException failure) {
            android.widget.Toast.makeText(this,"手电筒暂不可用，请重新扫描后重试。",android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    private void stopCamera() {
        generation++;
        main.removeCallbacks(focusAgain);focusing=false;
        if(zoom!=null)zoom.setEnabled(false);
        if (camera != null) {
            try { if(torchOn) { Camera.Parameters p=camera.getParameters(); p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF); camera.setParameters(p); } } catch(RuntimeException ignored) { }
            try { camera.setPreviewCallback(null); camera.stopPreview(); } catch (RuntimeException ignored) { }
            camera.release(); camera = null;
        }
        torchOn=false;
        if(torch!=null){torch.setText("打开手电筒");torch.setEnabled(false);}
    }
    private void rescan() {
        if (busy) return;
        recognized = null; recognizedRaw = null; lookup = null; query.setEnabled(false); order.setEnabled(false); scanning = true;
        status.setText("将设备二维码放入画面。");
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        else startCamera();
    }
    private void showCode(String raw) {
        if(life798) {
            scanning=false;stopCamera();
            try {
                final String id=io.qzxy.lite.core.Life798Qr.parse(raw);
                status.setText("已识别慧生活设备码，正在打开设备页。");
                returningFromDevice=true;
                if(BuildFlags.WATER_CONTROL)startActivity(new Intent().setClassName(this,"io.qzxy.lite.preview.LifeWaterActivity").putExtra("qrId",id));
                else startActivity(new Intent(this,LifeDeviceActivity.class).putExtra("qrId",id));
            } catch(IllegalArgumentException invalid){status.setText(invalid.getMessage());}
            return;
        }
        scanning = false; stopCamera(); recognized = null; recognizedRaw = null; lookup = null;
        query.setEnabled(false); order.setEnabled(false);
        try {
            recognized = DeviceQr.parse(raw); recognizedRaw = raw; query.setEnabled(true);
            if(BuildFlags.WATER_CONTROL){returningFromDevice=true;startActivity(new Intent(this,QzxyAlipayActivity.class).putExtra("qr",raw));}
            status.setText("设备二维码已识别。点击“查询设备资料”核对校园、计费方式与可用状态。\n识别二维码不会开启用水。");
        }
        catch (IllegalArgumentException invalid) { status.setText(invalid.getMessage() + "。可点击重新扫描。"); }
    }
    private void setBusy(boolean value) {
        busy = value; retry.setEnabled(!value);
        query.setEnabled(!value && recognized != null);
        order.setEnabled(!value && lookup != null && lookup.ready);
    }
    private void lookup() {
        if (busy || recognized == null) return;
        final DeviceQr qr = recognized; setBusy(true); status.setText("正在查询设备资料…");
        worker.execute(() -> {
            String message; DeviceLookup found = null;
            try {
                AccountSession session = new AccountVault(getApplicationContext()).load();
                if (session == null) message = "请先返回“我的”完成登录，再查询设备。";
                else {
                    found = SmsAccountApi.device(session, qr);
                    String note = pendingNote(found);
                    message = note.isEmpty() ? found.text : found.text + "\n\n" + note;
                }
            } catch (java.net.SocketTimeoutException timeout) { message = "查询超时，请检查网络后重试。"; }
            catch (org.json.JSONException invalid) { message = "设备资料格式与预期不符，暂不能确认设备。"; }
            catch (Exception failure) { message = "设备查询未完成，请检查网络与登录状态后重试。"; }
            final String result = message; final DeviceLookup device = found;
            main.post(() -> { if (!destroyed) { lookup = device; setBusy(false); status.setText(result); } });
        });
    }
    /**
     * Surfaces a stored record for this device. An unreadable record is reported as such rather than
     * hidden, because "cannot read" must never look the same as "nothing to worry about".
     */
    private String pendingNote(DeviceLookup device) {
        try {
            PendingUse record = new PendingUseVault(getApplicationContext()).load();
            if (record == null) return "";
            if (!record.matches(device.profile))
                return "本机另有一条未核实记录（设备 " + record.deviceId() + "），与当前设备不同。";
            return record.describe();
        } catch (Exception unreadable) {
            return "本机存在一条无法读取的用水记录，请重新查询订单状态确认后再做判断。";
        }
    }
    /** Read-only: reports the current order state and never sends a start or stop request. */
    private void orderLookup() {
        if (busy || lookup == null || !lookup.ready) return;
        final DeviceLookup device = lookup; setBusy(true); status.setText("正在查询该设备是否已有进行中的订单…");
        worker.execute(() -> {
            String message;
            try {
                AccountSession session = new AccountVault(getApplicationContext()).load();
                if (session == null) message = "请先返回“我的”完成登录，再查询订单。";
                else {
                    io.qzxy.lite.core.WaterOrder order = WaterApi.orderStatus(session, device);
                    message = order.describe() + remember(device, order);
                }
            } catch (java.net.SocketTimeoutException timeout) { message = "订单查询超时，请检查网络后重试；重试前请先确认现场水流状态。"; }
            catch (IllegalArgumentException unknown) { message = unknown.getMessage() + "。请稍后重试，不要直接开水。"; }
            catch (org.json.JSONException invalid) { message = "订单响应格式与预期不符，暂不能确认用水状态。"; }
            catch (Exception failure) { message = "订单查询未完成，请检查网络与登录状态后重试。"; }
            final String result = message;
            main.post(() -> { if (!destroyed) { setBusy(false); status.setText(result); } });
        });
    }
    /**
     * Keeps the minimum needed to re-verify a device that did not come back idle, and clears it the
     * moment a query confirms idle. A storage failure is reported to the user instead of swallowed.
     */
    private String remember(DeviceLookup device, io.qzxy.lite.core.WaterOrder order) {
        try {
            PendingUseVault vault = new PendingUseVault(getApplicationContext());
            vault.observe(device.profile, order);
            PendingUse record = vault.load();
            return record == null ? "" : "\n" + record.describe();
        } catch (Exception storageFailure) {
            return "\n（本机保存记录失败，请不要依赖本机记忆。）";
        }
    }
}
