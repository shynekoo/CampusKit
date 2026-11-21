// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.app.Instrumentation;
import android.os.Bundle;
import io.qzxy.lite.core.AccountSession;
import java.nio.file.Files;
import java.io.File;
import java.util.Arrays;
import org.json.JSONObject;

/** Synthetic, offline checks in a separate test package and Android UID. */
public final class AccountDeviceTests extends Instrumentation {
    private int assertions;
    private boolean pagerOnly,releaseUiOnly;
    private void check(boolean value) { assertions++; if (!value) throw new AssertionError("Assertion " + assertions); }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); releaseUiOnly=arguments!=null&&"true".equals(arguments.getString("releaseUiOnly")); pagerOnly=arguments!=null && "true".equals(arguments.getString("pagerOnly")); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if(releaseUiOnly){result.putString("stream","PASS: "+ReleaseUiTests.run(this)+" release UI assertions\n");finish(-1,result);return;}
            if(pagerOnly){int count=WeekPagerTests.run(this)+ScheduleFeatureTests.run(this)+LifeAuthTests.run(this)+LifeDeviceTests.run()+AlipayHandoffTests.run()+ScanDecodeTests.run()+DeviceUiTests.run(this);if(BuildFlags.WATER_CONTROL)count+=(Integer)Class.forName("io.qzxy.lite.preview.LifeWaterTests").getMethod("run",android.app.Instrumentation.class).invoke(null,this);result.putString("stream","PASS: "+count+" offline assertions\n");finish(-1,result);return;}
            check("io.qzxy.lite.accounttests".equals(getTargetContext().getPackageName()));
            String qrText = "KLCXKJ-Water,A,C0123456789A";
            com.google.zxing.common.BitMatrix qrMatrix = new com.google.zxing.qrcode.QRCodeWriter().encode(qrText, com.google.zxing.BarcodeFormat.QR_CODE, 320, 320);
            android.graphics.Bitmap qrImage = android.graphics.Bitmap.createBitmap(320, 320, android.graphics.Bitmap.Config.ARGB_8888);
            byte[] cameraFrame = new byte[320 * 320 * 3 / 2];
            for (int y = 0; y < 320; y++) for (int x = 0; x < 320; x++) {
                boolean black = qrMatrix.get(x, y); qrImage.setPixel(x, y, black ? 0xff000000 : 0xffffffff);
                cameraFrame[y * 320 + x] = (byte) (black ? 0 : 255);
            }
            check(QrDecoder.bitmap(qrImage).equals(qrText)); check(QrDecoder.frame(cameraFrame, 320, 320).equals(qrText));
            android.graphics.Matrix rotation = new android.graphics.Matrix(); rotation.postRotate(90);
            android.graphics.Bitmap rotatedQr = android.graphics.Bitmap.createBitmap(qrImage, 0, 0, 320, 320, rotation, true);
            check(QrDecoder.bitmap(rotatedQr).equals(qrText)); rotatedQr.recycle(); qrImage.recycle();
            boolean noQr = false;
            try { QrDecoder.frame(new byte[320 * 320 * 3 / 2], 320, 320); } catch (Exception expected) { noQr = true; }
            check(noQr);
            // Scale pyramid: a real bathroom sticker only decoded after reduction, so large images and
            // a small code inside a large canvas must both still be found.
            com.google.zxing.common.BitMatrix baseMatrix = new com.google.zxing.qrcode.QRCodeWriter().encode(qrText, com.google.zxing.BarcodeFormat.QR_CODE, 240, 240);
            android.graphics.Bitmap base = android.graphics.Bitmap.createBitmap(240, 240, android.graphics.Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 240; y++) for (int x = 0; x < 240; x++) base.setPixel(x, y, baseMatrix.get(x, y) ? 0xff000000 : 0xffffffff);
            android.graphics.Bitmap bigQr = android.graphics.Bitmap.createBitmap(1200, 1200, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas bigCanvas = new android.graphics.Canvas(bigQr);
            bigCanvas.drawColor(0xffffffff);
            bigCanvas.drawBitmap(base, null, new android.graphics.Rect(0, 0, 1200, 1200), new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG));
            check(QrDecoder.bitmap(bigQr).equals(qrText));
            bigQr.recycle();
            android.graphics.Bitmap composed = android.graphics.Bitmap.createBitmap(2400, 1800, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas composedCanvas = new android.graphics.Canvas(composed);
            composedCanvas.drawColor(0xff6f6f6f);
            android.graphics.Paint paint = new android.graphics.Paint(); paint.setFilterBitmap(true);
            composedCanvas.drawBitmap(base, null, new android.graphics.Rect(1200, 900, 1500, 1200), paint);
            check(QrDecoder.bitmap(composed).equals(qrText));
            composed.recycle();
            // An inverted (light-on-dark) sticker must still decode. The canvas has to match the
            // matrix it is filled from, otherwise the fill itself throws before ZXing is even reached.
            android.graphics.Bitmap inverted = android.graphics.Bitmap.createBitmap(240, 240, android.graphics.Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 240; y++) for (int x = 0; x < 240; x++) inverted.setPixel(x, y, baseMatrix.get(x, y) ? 0xffffffff : 0xff000000);
            check(QrDecoder.bitmap(inverted).equals(qrText)); inverted.recycle(); base.recycle();
            JSONObject device = new JSONObject("{\"projectId\":9,\"deviceId\":7,\"deviceName\":\"synthetic\",\"bigTypeId\":4,\"isUse\":0,\"snCode\":\"SYNTHETIC0001\"}");
            check(DeviceDetails.describe(device, "9").contains("暂停使用"));
            check(DeviceDetails.describe(device, "9").contains("资料校验未通过"));
            check(DeviceDetails.describe(device, "8").contains("不属于当前账户"));
            check(!DeviceDetails.describe(device, "9").contains("SYNTHETIC0001"));
            JSONObject usable = new JSONObject("{\"PrjID\":9,\"DevID\":7,\"DevName\":\"浴室A\",\"Dsbtypeid\":4,\"IsUse\":1,"
                + "\"snCode\":\"SYNTHETIC0002\",\"chargeMode\":0,\"withholdMoney\":\"5.00\",\"isOnline\":1,\"unknownField\":\"ignored\"}");
            String usableText = DeviceDetails.describe(usable, "9");
            check(usableText.contains("浴室A")); check(usableText.contains("浴室（4）"));
            check(usableText.contains("按使用时长")); check(usableText.contains("¥ 5.00"));
            check(usableText.contains("在线")); check(usableText.contains("资料校验通过"));
            check(!usableText.contains("ignored"));
            JSONObject nulls = new JSONObject("{\"projectId\":9,\"deviceId\":7,\"deviceName\":null,\"projectName\":null,\"bigTypeId\":4,\"isUse\":1,\"snCode\":\"SYNTHETIC0003\"}");
            check(!DeviceDetails.describe(nulls, "9").contains("null"));
            JSONObject unregistered = new JSONObject("{\"projectId\":0,\"deviceId\":0}");
            boolean rejectedDevice = false;
            try { DeviceDetails.describe(unregistered, "9"); } catch (Exception expected) { rejectedDevice = true; }
            check(rejectedDevice);
            check(DeviceDetails.fields(usable).get("unknownField").equals("ignored"));
            AccountProfile profile = AccountProfile.parse(new JSONObject("{\"userId\":42,\"telephone\":\"13800000000\",\"userAccount\":{\"accountId\":7,\"projectId\":9}}"), "42");
            check(profile.hasAccount()); check(profile.description().contains("138****0000"));
            AccountProfile noName = AccountProfile.parse(new JSONObject("{\"userId\":42,\"projectName\":null,\"telephone\":null,\"accountId\":7,\"projectId\":9}"), "42");
            check(!noName.description().contains("null"));
            check(noName.description().contains("校园账户已识别"));
            check(SmsAccountApi.parseSession(new JSONObject("{\"code\":0,\"data\":{\"userId\":42,\"loginCode\":\"synthetic\",\"userAccount\":{\"projectId\":9}}}")).projectId().equals("9"));
            check(AccountProfile.yuan(12345).equals("12.35")); check(AccountProfile.yuan(0).equals("0.00"));
            check(AccountProfile.walletText(new JSONObject("{\"accountId\":7,\"projectId\":9,\"accountMoney\":12000,\"accountGivenMoney\":3000}"), profile).startsWith("余额 ¥ 15.00"));
            for (String invalid : new String[]{"{}", "{\"accountMoney\":0}", "{\"accountMoney\":1.5,\"accountGivenMoney\":0}",
                "{\"accountId\":8,\"accountMoney\":0,\"accountGivenMoney\":0}"}) {
                boolean rejected = false;
                try { AccountProfile.walletText(new JSONObject(invalid), profile); } catch (Exception expected) { rejected = true; }
                check(rejected);
            }
            boolean wrongIdentity = false;
            try { AccountProfile.parse(new JSONObject("{\"userId\":43}"), "42"); } catch (Exception expected) { wrongIdentity = true; }
            check(wrongIdentity);
            check(SmsAccountApi.parseSession(new JSONObject("{\"errorCode\":0,\"data\":{\"userId\":42,\"loginCode\":\"synthetic-only\"}}")).userId().equals("42"));
            check(SmsAccountApi.parseSession(new JSONObject("{\"code\":2,\"data\":{\"v3UserId\":42,\"loginCode\":\"synthetic-only\"}}")).userId().equals("42"));
            for (String invalid : new String[]{"{}", "{\"code\":1}", "{\"code\":0}",
                "{\"code\":0,\"success\":false}", "{\"code\":0,\"data\":{\"userId\":0,\"loginCode\":\"x\"}}",
                "{\"code\":0,\"data\":{\"userId\":42,\"loginCode\":null}}"}) {
                boolean rejected = false;
                try { SmsAccountApi.parseSession(new JSONObject(invalid)); } catch (Exception expected) { rejected = true; }
                check(rejected);
            }
            // Stage 0.5.0: the order query is reachable only through a fully verified profile, and the
            // refusal must happen before any socket is opened.
            AccountSession session = new AccountSession("42", "synthetic-only");
            AccountProfile readyAccount = AccountProfile.parse(new JSONObject("{\"userId\":42,\"telephone\":\"13800000000\","
                + "\"userAccount\":{\"accountId\":7,\"projectId\":9}}"), "42");
            DeviceLookup readyLookup = DeviceDetails.lookup(usable, "9", readyAccount);
            check(readyLookup.ready);
            DeviceLookup foreignLookup = DeviceDetails.lookup(usable, "8", readyAccount);
            check(!foreignLookup.ready);
            DeviceLookup pausedLookup = DeviceDetails.lookup(device, "9", readyAccount);
            check(!pausedLookup.ready);
            check(!readyLookup.text.contains("SYNTHETIC0002"));
            for (DeviceLookup blocked : new DeviceLookup[]{null, foreignLookup, pausedLookup}) {
                boolean refused = false;
                try { WaterApi.orderStatus(session, blocked); } catch (java.io.IOException expected) { refused = true; }
                check(refused);
            }
            check(WaterApi.isKnownRoute(WaterApi.ORDER_USING_PATH));
            check(!WaterApi.isKnownRoute("order/tcpDevice/downRate/rateOrder"));
            check(!WaterApi.isKnownRoute("order/tcpDevice/closeOrder"));
            check(!WaterApi.controlEnabled());
            io.qzxy.lite.core.WaterOrder noneOrder = io.qzxy.lite.core.WaterOrder.parse(0, null);
            check(!noneOrder.blocksStart()); check(noneOrder.describe().contains("未发现进行中的用水订单"));
            io.qzxy.lite.core.WaterOrder ownOrder = io.qzxy.lite.core.WaterOrder.parse(307,
                DeviceDetails.fields(new JSONObject("{\"isOwner\":true,\"timeIds\":\"SYNTHETIC-ORD-1\",\"perMoney\":10000}")));
            check(ownOrder.blocksStart()); check(ownOrder.prepaidYuan().equals("10.00"));
            // Only the last four characters of an order number may ever reach the screen.
            check(ownOrder.describe().contains("****RD-1")); check(!ownOrder.describe().contains("SYNTHETIC-ORD-1"));
            io.qzxy.lite.core.WaterOrder otherOrder = io.qzxy.lite.core.WaterOrder.parse(307,
                DeviceDetails.fields(new JSONObject("{\"isOwner\":false}")));
            check(otherOrder.blocksStart()); check(otherOrder.describe().contains("其他账户"));
            io.qzxy.lite.core.WaterOrder unclearOrder = io.qzxy.lite.core.WaterOrder.parse(307,
                DeviceDetails.fields(new JSONObject("{\"timeIds\":\"SYNTHETIC-ORD-2\"}")));
            check(unclearOrder.blocksStart()); check(unclearOrder.describe().contains("未说明订单归属"));
            check(!unclearOrder.describe().contains("其他账户"));
            // Both vaults refuse to run on the main looper, and instrumentation callbacks run there, so
            // every Keystore touch has to happen on a worker thread and be joined before asserting.
            final Throwable[] vaultFailure = new Throwable[1];
            Thread vaultThread = new Thread(() -> {
                try {
                    AccountVault vault = new AccountVault(getTargetContext());
                    vault.clear(); check(vault.load() == null);
                    vault.save(session);
                    check(new AccountVault(getTargetContext()).load().loginCode().equals(session.loginCode()));
                    File file = new File(getTargetContext().getNoBackupFilesDir(), "account-session.bin");
                    byte[] first = Files.readAllBytes(file.toPath());
                    check(!new String(first, java.nio.charset.StandardCharsets.ISO_8859_1).contains("synthetic-only"));
                    vault.save(session); check(!Arrays.equals(first, Files.readAllBytes(file.toPath())));
                    first[first.length - 1] ^= 1; Files.write(file.toPath(), first);
                    boolean rejected = false;
                    try { vault.load(); } catch (Exception expected) { rejected = true; }
                    check(rejected);
                    vault.clear(); check(vault.load() == null); check(!file.exists());
                    // The recovery record must survive a restart and must not sit in the clear.
                    PendingUseVault recoveryVault = new PendingUseVault(getTargetContext());
                    recoveryVault.clear(); check(recoveryVault.load() == null);
                    io.qzxy.lite.core.DeviceProfile deviceProfile =
                        io.qzxy.lite.core.DeviceProfile.parse(DeviceDetails.fields(usable));
                    io.qzxy.lite.core.PendingUse idleRecord = io.qzxy.lite.core.PendingUse.of(deviceProfile,
                        io.qzxy.lite.core.WaterOrder.parse(0, null), 1789000000000L);
                    check(idleRecord == null);
                    io.qzxy.lite.core.PendingUse record = io.qzxy.lite.core.PendingUse.of(deviceProfile,
                        io.qzxy.lite.core.WaterOrder.parse(307, DeviceDetails.fields(new JSONObject(
                            "{\"isOwner\":true,\"timeIds\":\"SYNTHETIC-ORD-3\",\"perMoney\":10000}"))),
                        1789000000000L);
                    check(record != null);
                    recoveryVault.save(record);
                    io.qzxy.lite.core.PendingUse reloaded = new PendingUseVault(getTargetContext()).load();
                    check(reloaded != null); check(reloaded.matches(deviceProfile));
                    check(reloaded.orderNo().equals("SYNTHETIC-ORD-3"));
                    check(reloaded.status().equals("MINE"));
                    File pendingFile = new File(getTargetContext().getNoBackupFilesDir(), "pending-use.bin");
                    check(pendingFile.exists());
                    check(!new String(Files.readAllBytes(pendingFile.toPath()),
                        java.nio.charset.StandardCharsets.ISO_8859_1).contains("SYNTHETIC-ORD-3"));
                    // Signing out deletes the session key but must not erase the only note that an order
                    // may still be open, because the two use separate Keystore aliases.
                    new AccountVault(getTargetContext()).clear();
                    check(new PendingUseVault(getTargetContext()).load().orderNo().equals("SYNTHETIC-ORD-3"));
                    recoveryVault.clear(); check(recoveryVault.load() == null); check(!pendingFile.exists());
                } catch (Throwable failure) { vaultFailure[0] = failure; }
            });
            vaultThread.start(); vaultThread.join();
            if (vaultFailure[0] != null) throw new AssertionError("Keystore checks: " + vaultFailure[0]);
            assertions += AlipayHandoffTests.run();
            assertions += WeekPagerTests.run(this);
            if (BuildFlags.WATER_CONTROL) {
                Class<?> tests = Class.forName("io.qzxy.lite.preview.FieldDeviceTests");
                assertions += (Integer) tests.getMethod("run", Instrumentation.class).invoke(null, this);
            }
            result.putString("stream", "PASS: " + assertions + " Android assertions; QR decode, device profile parsing and control gate, read-only order model and query gate, unverified-use record, response parser, Keystore round trip, random IV, tamper rejection, logout cleanup. No network requests.\n");
            finish(-1, result);
        } catch (Throwable failure) {
            java.io.StringWriter trace = new java.io.StringWriter();
            failure.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream", "FAIL after " + assertions + " assertions: " + failure + "\n" + trace);
            finish(0, result);
        }
    }
}
