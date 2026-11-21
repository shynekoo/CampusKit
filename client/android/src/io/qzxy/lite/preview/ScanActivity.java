// SPDX-License-Identifier: MPL-2.0
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
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import io.qzxy.lite.core.AccountSession;
import io.qzxy.lite.core.DeviceQr;
import io.qzxy.lite.core.PendingUse;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Camera frames are decoded locally; identifying a device does not start it. */
@SuppressWarnings("deprecation")
public final class ScanActivity extends Page implements SurfaceHolder.Callback {
    private static final int CAMERA_PERMISSION = 10;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean decoding = new AtomicBoolean();
    private SurfaceView preview;
    private FrameLayout viewport;
    private TextView status, hint;
    private Button retry, useCode;
    private ImageView torch;
    private LinearLayout zoomRow;
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
    private boolean returningFromDevice, askedLogin;
    /** Restored whenever the camera restarts, so the panel never keeps a stale result. */
    private String idleStatus = "";

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(AppUi.INK);
        text.setPadding(0, dp(8), 0, dp(8)); return text;
    }
    private TextView heading(String value) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(20); text.setTextColor(AppUi.INK);
        text.setTypeface(null, android.graphics.Typeface.BOLD); return text;
    }
    private Button button(String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setMinHeight(dp(48));
        button.setOnClickListener(v -> action.run()); return button;
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        life798=getIntent().getBooleanExtra("life798",false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(AppUi.BG);
        final int side = AppUi.side(this, 16);
        root.setPadding(side, dp(8), side, dp(12));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(side + bars.left, dp(8) + bars.top, side + bars.right, dp(12) + bars.bottom); return insets;
            });
        }

        // Header: one back link and one line naming the campus app this camera is aimed at.
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = label("‹ 返回", 15); back.setTextColor(AppUi.ACCENT);
        back.setPadding(dp(6), dp(12), dp(8), dp(12)); back.setContentDescription("返回");
        back.setOnClickListener(v -> finish()); header.addView(back, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(0, dp(2), 0, dp(6));
        titles.addView(heading("扫一扫"));
        TextView mode = new TextView(this);
        mode.setText(life798 ? "慧生活798 · 对准设备上的二维码" : "趣智校园 · 对准设备上的二维码");
        mode.setTextSize(12); mode.setTextColor(AppUi.MUTED); mode.setPadding(0, dp(1), 0, 0);
        titles.addView(mode); header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);

        // Camera: a dark rounded stage, the preview letterboxed inside it, and the target on top.
        viewport = new FrameLayout(this);
        viewport.setBackground(AppUi.shape(this, 0xff101820, 20));
        viewport.setClipToOutline(true);
        preview = new SurfaceView(this); preview.getHolder().addCallback(this);
        viewport.addView(preview, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        viewport.addView(new ScanFrame(this), new FrameLayout.LayoutParams(-1, -1));
        preview.setOnClickListener(v -> focusCamera());

        hint = new TextView(this);
        hint.setText(life798 ? "把设备码放进框内" : "把趣智校园设备码放进框内");
        hint.setTextSize(12); hint.setTextColor(0xffffffff);
        hint.setBackground(AppUi.shape(this, 0x59000000, 14));
        hint.setPadding(dp(12), dp(6), dp(12), dp(7));
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintParams.bottomMargin = dp(14); viewport.addView(hint, hintParams);

        // The torch used to be a full-width button; inside the stage it is where the thumb already is.
        torch = new ImageView(this);
        torch.setPadding(dp(11), dp(11), dp(11), dp(11));
        torch.setVisibility(View.GONE);
        torch.setOnClickListener(v -> toggleTorch());
        paintTorch();
        FrameLayout.LayoutParams torchParams = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END);
        torchParams.topMargin = dp(12); torchParams.rightMargin = dp(12);
        viewport.addView(torch, torchParams);
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        // A long failure message must not squeeze the camera out of existence.
        viewport.setMinimumHeight(dp(200));

        // Only shown when the sensor actually zooms; a dead slider was pure clutter.
        zoomRow = new LinearLayout(this); zoomRow.setGravity(Gravity.CENTER_VERTICAL);
        zoomRow.setPadding(dp(2), dp(6), dp(2), 0); zoomRow.setVisibility(View.GONE);
        zoomLabel = label("1.0×", 12); zoomLabel.setTextColor(AppUi.MUTED);
        zoomRow.addView(zoomLabel, new LinearLayout.LayoutParams(dp(44), -2));
        zoom = new SeekBar(this); zoom.setEnabled(false);
        zoomRow.addView(zoom, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(zoomRow);
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

        idleStatus = life798
            ? "对准设备二维码，轻点画面可对焦。识别后自动打开设备页。"
            : BuildFlags.WATER_CONTROL
                ? "对准设备二维码，轻点画面可对焦。识别后进入支付宝用水。"
                : "将设备二维码放入画面。识别后核对设备，再前往支付宝用水。";
        // Selectable, because the failure text is what gets reported back verbatim.
        ScrollView panel = new ScrollView(this);
        panel.setBackground(AppUi.outlined(this, AppUi.SURFACE, 12, AppUi.LINE));
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        status = label(idleStatus, 13); status.setTextColor(AppUi.INK);
        status.setTextIsSelectable(true); status.setLineSpacing(dp(2), 1f);
        panel.addView(status);
        root.addView(panel, new LinearLayout.LayoutParams(-1, -2));

        // Two equal halves: scan again, or open the keypad code when the sticker is out of reach.
        LinearLayout actionRow = new LinearLayout(this);
        retry = button("重新扫描", this::rescan);
        retry.setTextSize(15);
        actionRow.addView(retry, new LinearLayout.LayoutParams(0, -2, 1));
        useCode = button("使用码", () -> startActivity(new Intent(this, UseCodeActivity.class)));
        useCode.setTextSize(15);
        useCode.setVisibility(life798 ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(0, -2, 1);
        codeParams.leftMargin = dp(8); actionRow.addView(useCode, codeParams);
        root.addView(actionRow);

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
                zoomRow.setVisibility(max>0?View.VISIBLE:View.GONE);
            } else zoomRow.setVisibility(View.GONE);
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
            boolean torchReady = flashes != null && flashes.contains(Camera.Parameters.FLASH_MODE_TORCH) && flashes.contains(Camera.Parameters.FLASH_MODE_OFF);
            torch.setEnabled(torchReady);
            torch.setVisibility(torchReady ? View.VISIBLE : View.GONE);
            paintTorch();
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
    /** Lit looks like a filled lens, unlit like a dark one, so the state needs no text. */
    private void paintTorch() {
        if (torch == null) return;
        torch.setImageDrawable(AppUi.icon(this, "torch", torchOn ? 0xff275dad : 0xffffffff, 22));
        torch.setBackground(AppUi.shape(this, torchOn ? 0xffffffff : 0x59000000, 22));
        torch.setContentDescription(torchOn ? "关闭手电筒" : "打开手电筒");
    }
    private void toggleTorch() {
        if (camera == null || !scanning) return;
        try {
            Camera.Parameters parameters = camera.getParameters();
            boolean next = !torchOn;
            parameters.setFlashMode(next ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(parameters);
            torchOn = next;
            paintTorch();
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
        if(torch!=null){torch.setEnabled(false);torch.setVisibility(View.GONE);paintTorch();}
    }
    private void rescan() {
        if (busy) return;
        recognized = null; recognizedRaw = null; lookup = null; scanning = true;
        status.setText(idleStatus);
        hint.setVisibility(View.VISIBLE);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        else startCamera();
    }
    private void scanFeedback() {
        status.announceForAccessibility("二维码已识别，正在读取设备");
    }
    private void showCode(String raw) {
        if (!scanning) return;
        if(life798) {
            scanning=false;stopCamera();
            hint.setVisibility(View.GONE);
            try {
                openLife(io.qzxy.lite.core.Life798Qr.parse(raw));
            } catch(IllegalArgumentException invalid){
                // Show what the sticker actually said: without it "扫不出来" is undebuggable, and the
                // raw text is what tells us whether this is an unknown URL shape or a foreign code.
                status.setText(invalid.getMessage()+"\n\n识别到的内容："+clipCode(raw)
                    +"\n\n点「重新扫描」再对准一次。");
            }
            return;
        }
        scanning = false; stopCamera(); recognized = null; recognizedRaw = null; lookup = null;
        hint.setVisibility(View.GONE);
        try {
            recognized = DeviceQr.parse(raw); recognizedRaw = raw; scanFeedback();
            if(BuildFlags.WATER_CONTROL){returningFromDevice=true;startActivity(new Intent(this,QzxyAlipayActivity.class).putExtra("qr",raw));}
            status.setText("设备二维码已识别，正在核对设备与订单状态…\n识别二维码不会开启用水。");
            verify();
        }
        catch (IllegalArgumentException invalid) { status.setText(invalid.getMessage() + "。可点击重新扫描。"); }
    }
    /**
     * Hands the parsed code to the device page.
     *
     * <p>Current 慧生活 stickers carry a server ticket instead of a device id, so the two cases are
     * handed over with different keys: {@code did} skips the exchange, {@code qrId} means the device
     * page must call {@code /qr/use} first. Guessing wrong here is exactly what made scanning look
     * broken while the API was fine.</p>
     */
    private void openLife(io.qzxy.lite.core.Life798Qr.Scan scan) {
        scanFeedback();
        boolean direct = scan.isDeviceId();
        String value = direct ? scan.deviceId : scan.qrId;
        status.setText(direct ? "已识别设备编号，正在打开设备页。"
            : "已识别设备码，正在向服务器换取设备编号。");
        returningFromDevice = true;
        Intent target;
        if (BuildFlags.WATER_CONTROL) target = new Intent().setClassName(this, "io.qzxy.lite.preview.LifeWaterActivity");
        else target = new Intent(this, LifeDeviceActivity.class);
        target.putExtra(direct ? "did" : "qrId", value);
        startActivity(target);
    }

    private static String clipCode(String raw) {
        String text = raw == null ? "" : raw.trim();
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }

    /** A missing session is a reason to sign in, not just another line of status text. */
    private void askLogin() {
        if (askedLogin) return;
        askedLogin = true;
        Intent target = life798
            ? new Intent(this, LifeLoginActivity.class)
            : new Intent(this, AccountActivity.class).putExtra("returnToAccountInfo", true);
        startActivity(target);
    }
    private void setBusy(boolean value) {
        busy = value; retry.setEnabled(!value); useCode.setEnabled(!value);
    }
    /**
     * Runs both lookups the moment a code is recognized, because the two buttons that used to start
     * them are gone: the page now answers by itself instead of waiting to be asked.
     */
    private void verify() {
        if (busy || recognized == null) return;
        final DeviceQr qr = recognized; setBusy(true);
        worker.execute(() -> {
            String message; DeviceLookup found = null; boolean needLogin = false;
            try {
                AccountSession session = new AccountVault(getApplicationContext()).load();
                if (session == null) { message = "设备已识别。登录后才能核对设备与订单状态。"; needLogin = true; }
                else { found = SmsAccountApi.device(session, qr); message = describe(session, found); }
            } catch (java.net.SocketTimeoutException timeout) { message = "核对超时，请检查网络后重试。"; }
            catch (org.json.JSONException invalid) { message = "设备资料格式与预期不符，暂不能确认设备。"; }
            catch (HttpForm.SessionExpired expired) { message = "登录已失效，请重新登录后再扫码。"; needLogin = true; }
            catch (Exception failure) { message = "核对未完成，请检查网络与登录状态后重试。"; }
            final String result = message; final DeviceLookup device = found; final boolean login = needLogin;
            main.post(() -> {
                if (destroyed) return;
                lookup = device; setBusy(false); status.setText(result);
                if (login) askLogin();
            });
        });
    }
    /** Device profile plus, when the profile is trustworthy, the live order state for that device. */
    private String describe(AccountSession session, DeviceLookup device) {
        StringBuilder text = new StringBuilder(device.text);
        String note = pendingNote(device);
        if (!note.isEmpty()) text.append("\n\n").append(note);
        if (!device.ready) return text.toString();
        try {
            io.qzxy.lite.core.WaterOrder order = WaterApi.orderStatus(session, device);
            return text.append("\n\n").append(order.describe()).append(remember(device, order)).toString();
        } catch (java.net.SocketTimeoutException timeout) { return text.append("\n\n订单状态查询超时，请检查网络后重试；重试前请先确认现场水流状态。").toString(); }
        catch (IllegalArgumentException unknown) { return text.append("\n\n").append(unknown.getMessage()).append("。请稍后重试，不要直接开水。").toString(); }
        catch (org.json.JSONException invalid) { return text.append("\n\n订单响应格式与预期不符，暂不能确认用水状态。").toString(); }
        catch (HttpForm.SessionExpired expired) { return text.append("\n\n登录已失效，请重新登录后再查订单。").toString(); }
        catch (Exception failure) { return text.append("\n\n订单状态查询未完成，请检查网络后重试。").toString(); }
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
            return "本机存在一条无法读取的用水记录，请重新扫码确认后再做判断。";
        }
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
