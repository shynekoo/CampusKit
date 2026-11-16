package io.qzxy.lite.core;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import io.qzxy.lite.core.UseSession.State;

public final class CoreTests {
    private static int assertions;
    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void rejects(Runnable action) {
        assertions++;
        try { action.run(); } catch (IllegalStateException | IllegalArgumentException | NullPointerException expected) { return; }
        throw new AssertionError("Invalid operation was accepted");
    }
    private static UseSession ready() {
        UseSession s = new UseSession(); s.resolve(); s.deviceResolved(); return s;
    }
    private static UseSession active() {
        UseSession s = ready(); s.requestStart(); s.startAcknowledged(); return s;
    }
    private static java.util.Map<String, String> fields(String... pairs) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }
    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        equal(true, WaterChannel.requiresAlipay(10000,"当前项目不支持该应用，请使用“趣智校园”支付宝小程序，账号、余额不变"));
        equal(false, WaterChannel.requiresAlipay(10000,"请求失败"));
        equal(false, WaterChannel.requiresAlipay(1,"当前项目不支持该应用，请使用支付宝小程序"));
        equal(false, WaterChannel.requiresAlipay(10000,null));
        equal(false, WaterChannel.requiresAlipay(10000,"当前项目不支持该应用"));
        // 慧生活 stickers. Current ones carry a server ticket, not a device id: sending the ticket
        // as "did" is why scanning looked broken after the official app update. Reference logic:
        // ilife798/ilife798 DeviceLinkParser and nocookies111/life-798 DeviceIdParser.
        equal("123456",Life798Qr.parse("123456").deviceId);
        equal("Abc123",Life798Qr.parse("https://i.ilife798.com/nfc.html?q=Abc123").deviceId);
        // 1.4.8: a number in a key is the device id whatever host carries it; stickers have been
        // seen on hosts outside the old whitelist, and the official app reads those too.
        equal("Abc123",Life798Qr.parse("https://evil.example/nfc.html?q=Abc123").deviceId);
        equal("2000123456",Life798Qr.parse("https://i.ilife798.com/bind?did=2000123456").deviceId);
        equal("2000123456",Life798Qr.parse("{\"deviceId\":\"2000123456\",\"type\":2}").deviceId);
        equal("2000123456",Life798Qr.parse("ilife://device/2000123456/detail").deviceId);
        equal("2000123456",Life798Qr.parse("did:2000123456").deviceId);
        equal("A1B2C3D4",Life798Qr.parse("A1B2C3D4").deviceId);
        equal("863000000000001",Life798Qr.parse("https://i.hnkzy.com/q/1/863000000000001").deviceId);
        equal("abc-123",Life798Qr.parse("https://i.ilife798.com/q/2/abc-123").deviceId);
        // /q/<n>/<kind>?id= is a ticket for /qr/use, on every kind the stickers use.
        for (String kind : new String[]{"b", "s", "billing", "vip", "wallet"}) {
            Life798Qr.Scan ticket = Life798Qr.parse("https://i.ilife798.com/q/1/" + kind + "?id=a1b2c3d4");
            equal("a1b2c3d4", ticket.qrId);
            equal(false, ticket.isDeviceId());
        }
        // bm codes keep everything after q/<n>/ verbatim, question mark and slash included.
        equal("bm?/tok-1", Life798Qr.parse("https://i.ilife798.com/q/1/bm?/tok-1").qrId);
        // NFC tags written by the official app carry the device id itself.
        equal("863781051223338", Life798Qr.parse("ilife798s://i/device/nav?id=863781051223338").deviceId);
        equal("dev123abc", Life798Qr.parse("ilife798s://i/device/nfc?id=dev123abc&from=nfc").deviceId);
        rejects(()->Life798Qr.parse("https://i.ilife798.com/about"));
        rejects(()->Life798Qr.parse("hello"));
        rejects(()->Life798Qr.parse("just-some-text"));
        rejects(()->Life798Qr.parse("https://i.ilife798.com/nfc.html?q=a&q=b"));
        rejects(()->Life798Qr.parse(null));
        rejects(()->Life798Qr.parse("https://i.hnkzy.com/q/1/abc?other=1"));
        rejects(()->Life798Qr.parse("https://i.hnkzy.com/q/1/abc#fragment"));
        rejects(()->Life798Qr.parse("https://i.hnkzy.com/q/1/a/123"));
        System.out.println("PASS: 慧生活 codes split into device ids and /qr/use tickets");
        AccountSession handoff = new AccountSession("12", "test+token/value=", "34");
        String handoffLink = AlipayWaterLink.bathroom("TEST_233", handoff, "56", "34", "13800000000", "1");
        java.net.URI handoffUri = java.net.URI.create(handoffLink);
        equal("alipays", handoffUri.getScheme());
        equal("platformapi", handoffUri.getHost());
        equal("/startapp", handoffUri.getPath());
        String decodedQuery = java.net.URLDecoder.decode(handoffLink.substring(handoffLink.indexOf("&query=") + 7), "UTF-8");
        equal("moduleId=1&accountId=56&userId=12&token=test+token/value=&telPhone=13800000000&projectId=34&isMigrated=1&snCode=TEST_233", decodedQuery);
        rejects(() -> AlipayWaterLink.bathroom(null, handoff,"56","34","13800000000","1"));
        rejects(() -> AlipayWaterLink.bathroom("233&moduleId=5", handoff,"56","34","13800000000","1"));
        rejects(() -> AlipayWaterLink.bathroom("233", handoff,"56","35","13800000000","1"));
        rejects(() -> AlipayWaterLink.bathroom("233", handoff,"56","34","","1"));
        rejects(() -> AlipayWaterLink.bathroom("233", handoff,"56","34","13800000000",""));
        rejects(() -> AlipayWaterLink.bathroom("233", null,"56","34","13800000000","1"));
        rejects(() -> AlipayWaterLink.bathroom(repeat('a',65), handoff,"56","34","13800000000","1"));
        rejects(() -> AlipayWaterLink.bathroom("233", new AccountSession("12","a&projectId=999","34"),"56","34","13800000000","1"));
        DeviceQr qr = DeviceQr.parse("KLCXKJ-Water,A,C0123456789A");
        equal("C0:12:34:56:78:9A", qr.bluetoothAddress());
        equal("00123456789A", qr.queryAddress()); equal(true, qr.bleHint());
        DeviceQr classic = DeviceQr.parse("https://qzxy.ink/example,A,00:12:34:56:78:9A");
        equal(false, classic.bleHint()); equal("00:12:34:56:78:9A", classic.queryAddress());
        equal("00:12:34:56:78:9A", DeviceQr.parse("KLCXKJ-Water,A,c0:12:34:56:78:9a,extra").queryAddress());
        // Real bathroom device code photographed 2026-09-16; see analysis/real-device-qr.md.
        DeviceQr real = DeviceQr.parse("KLCXKJ-Water,G,721086402865");
        equal("G", real.deviceType());
        equal("72:10:86:40:28:65", real.bluetoothAddress());
        equal("721086402865", real.queryAddress());
        equal(true, real.bleHint());
        for (String invalid : new String[]{null, "", "KLCXKJ-Recharge,123", "https://example.com/,A,C0123456789A",
            "https://qzxy.ink.evil/,A,C0123456789A", "KLCXKJ-Water,A", "KLCXKJ-Water,,C0123456789A",
            "KLCXKJ-Water,A,C0", "KLCXKJ-Water,A,C0123456789G", "KLCXKJ-Water,A,C0:1234:56789A",
            "KLCXKJ-Water,T,C0123456789A", "KLCXKJ-Water,A,C0123456789A\n"})
            rejects(() -> DeviceQr.parse(invalid));
        DeviceProfile bath = DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "DevName", "浴室1号",
            "PrjName", "科信学院", "bigTypeId", "4", "bigTypeName", "浴室", "isUse", "1",
            "snCode", "KLCX20260001", "chargeMode", "0", "withholdMoney", "5.00",
            "LDName", "宿舍楼A", "LCName", "2层", "FJName", "201", "isOnline", "1",
            "communicationTypeId", "4", "deviceOrderPagePromptText", "请先放好物品"));
        equal(true, bath.bathroom()); equal(true, bath.usable()); equal(true, bath.hasSerial());
        equal(true, bath.inProject("9")); equal(true, bath.readyForWaterControl("9"));
        equal("9", bath.projectId()); equal("7", bath.deviceId());
        equal(4, bath.transportHint()); equal(1, bath.online());
        String bathText = bath.describe("9");
        for (String expected : new String[]{"浴室1号", "浴室", "宿舍楼A 2层 201", "科信学院", "属于当前账户",
            "浴室（4）", "按使用时长", "¥ 5.00", "允许使用", "在线", "已识别", "请先放好物品", "资料校验通过"})
            equal(true, bathText.contains(expected));
        equal(false, bathText.contains("KLCX20260001"));
        equal(false, bath.toString().contains("KLCX20260001"));
        equal(true, bath.describe("8").contains("不属于当前账户"));
        equal(false, bath.readyForWaterControl("8"));
        // Legacy-only spelling must resolve through the alternate names.
        DeviceProfile legacy = DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "Dsbtypeid", "4",
            "DsbName", "浴室", "IsUse", "1", "snCode", "KLCX20260002", "chargeMode", "17"));
        equal(true, legacy.bathroom()); equal(true, legacy.usable());
        equal(true, legacy.describe("9").contains("按使用量"));
        java.util.List<java.util.Map<String, String>> unregistered = new java.util.ArrayList<>();
        unregistered.add(fields()); unregistered.add(fields("PrjID", "9")); unregistered.add(fields("DevID", "7"));
        unregistered.add(fields("PrjID", "9", "DevID", "0")); unregistered.add(fields("PrjID", "0", "DevID", "7"));
        unregistered.add(fields("PrjID", "09", "DevID", "7"));
        for (java.util.Map<String, String> invalid : unregistered) rejects(() -> DeviceProfile.parse(invalid));
        rejects(() -> DeviceProfile.parse(null));
        DeviceProfile paused = DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "bigTypeId", "4",
            "isUse", "0", "disableTip", "设备维修中", "snCode", "KLCX20260003"));
        equal(false, paused.usable()); equal(false, paused.readyForWaterControl("9"));
        equal(true, paused.describe("9").contains("暂停使用（设备维修中）"));
        equal(true, paused.describe("9").contains("资料校验未通过"));
        DeviceProfile drink = DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "bigTypeId", "5",
            "isUse", "1", "snCode", "KLCX20260004"));
        equal(false, drink.bathroom()); equal(false, drink.readyForWaterControl("9"));
        equal(true, drink.describe("9").contains("其他（5）"));
        DeviceProfile unknownType = DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "isUse", "1",
            "snCode", "KLCX20260005"));
        equal(-1, unknownType.dsbTypeId()); equal(-1, unknownType.chargeMode()); equal(-1, unknownType.online());
        equal(-1, unknownType.transportHint()); equal(false, unknownType.bathroom());
        equal(true, unknownType.describe("9").contains("未提供"));
        DeviceProfile noSerial = DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "bigTypeId", "4", "isUse", "1"));
        equal(false, noSerial.hasSerial()); equal(false, noSerial.readyForWaterControl("9"));
        for (String badSerial : new String[]{"", " ", "bad\ncode", "bad code", "带中文", repeat('a', 65), "sn;drop"})
            equal(false, DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "bigTypeId", "4", "isUse", "1",
                "snCode", badSerial)).hasSerial());
        for (String badMoney : new String[]{"", " ", "abc", "5.0.0", "-1", "1,5", "5.0000", "12元"})
            equal("", DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "bigTypeId", "4", "isUse", "1",
                "withholdMoney", badMoney)).withholdMoney());
        equal("12.5", DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "bigTypeId", "4", "isUse", "1",
            "withholdMoney", "12.5")).withholdMoney());
        equal(false, DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "bigTypeId", "4", "isUse", "1",
            "withholdMoney", "abc")).describe("9").contains("预扣金额"));
        DeviceProfile noisy = DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "bigTypeId", "4",
            "isUse", "1", "DevName", "浴室\u0000\u0007一号", "snCode", "KLCX20260006", "isOnline", "0"));
        equal(true, noisy.describe("9").contains("浴室一号"));
        equal(true, noisy.describe("9").contains("离线"));
        equal(false, noisy.describe("9").contains("\u0000"));
        DeviceProfile verbose = DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "bigTypeId", "4",
            "isUse", "1", "DevName", repeat('长', 200), "snCode", "KLCX20260007"));
        equal(true, verbose.describe("9").length() < 400);
        System.out.println("PASS: strict device profile parsing, control gate and display boundaries");
        // order/tcpDevice/query/rateOrder/using is read-only: it must never unlock a start command.
        WaterOrder idle = WaterOrder.parse(0, null);
        equal(WaterOrder.Status.NONE, idle.status());
        equal(false, idle.blocksStart());
        equal(true, idle.describe().contains("未发现进行中的用水订单"));
        equal("", idle.maskedOrderNo()); equal("", idle.prepaidYuan());
        WaterOrder mine = WaterOrder.parse(307, fields("isOwner", "true", "timeIds", "ORD12345678",
            "perMoney", "10500"));
        equal(WaterOrder.Status.MINE, mine.status());
        equal(true, mine.blocksStart());
        equal("ORD12345678", mine.orderNo());
        equal("10.50", mine.prepaidYuan());
        equal("5678", mine.maskedOrderNo());
        equal(true, mine.describe().contains("订单号：****5678"));
        equal(false, mine.describe().contains("ORD12345678"));
        equal(false, mine.toString().contains("ORD12345678"));
        equal(true, mine.describe().contains("¥ 10.50"));
        // Legacy/alternate key spellings from the same response model must resolve identically.
        WaterOrder alternate = WaterOrder.parse(307, fields("isOwner", "TRUE", "orderNo", "AB-9",
            "preDeductMoney", "5"));
        equal(WaterOrder.Status.MINE, alternate.status());
        equal("AB-9", alternate.orderNo()); equal("0.01", alternate.prepaidYuan());
        equal(true, alternate.describe().contains("订单号：****AB-9"));
        WaterOrder others = WaterOrder.parse(307, fields("isOwner", "false", "timeIds", "ORD12345678"));
        equal(WaterOrder.Status.OTHER, others.status());
        equal(true, others.blocksStart()); equal("", others.orderNo());
        equal(true, others.describe().contains("其他账户"));
        equal(false, others.describe().contains("ORD12345678"));
        // An in-use reply that does not say whose order it is must not be reported as someone else's.
        for (java.util.Map<String, String> unclear : java.util.Arrays.asList(null,
                fields(), fields("isOwner", "yes"), fields("isOwner", ""),
                fields("timeIds", "ORD12345678"), fields("isOwner", "true"),
                fields("isOwner", "true", "timeIds", ""), fields("isOwner", "true", "timeIds", "bad order"),
                fields("isOwner", "true", "timeIds", "sn;drop"), fields("isOwner", "true", "timeIds", repeat('a', 65)))) {
            WaterOrder pending = WaterOrder.parse(307, unclear);
            equal(WaterOrder.Status.PENDING, pending.status());
            equal(true, pending.blocksStart()); equal("", pending.orderNo()); equal("", pending.maskedOrderNo());
            equal(true, pending.describe().contains("未说明订单归属"));
            equal(false, pending.describe().contains("其他账户"));
        }
        for (int code : new int[]{1, 2, 308, -1, 500})
            rejects(() -> WaterOrder.parse(code, fields("isOwner", "true", "timeIds", "ORD12345678")));
        // Thousandths-of-a-yuan rounding, including the carry boundary.
        equal("1.00", WaterOrder.parse(307, fields("isOwner", "true", "timeIds", "X1", "perMoney", "999")).prepaidYuan());
        equal("0.00", WaterOrder.parse(307, fields("isOwner", "true", "timeIds", "X2", "perMoney", "0")).prepaidYuan());
        equal("123456.79", WaterOrder.parse(307, fields("isOwner", "true", "timeIds", "X3",
            "perMoney", "123456789")).prepaidYuan());
        for (String badMoney : new String[]{"abc", "-1", "1.5", "1,5", "", "9999999999"})
            equal("", WaterOrder.parse(307, fields("isOwner", "true", "timeIds", "X4",
                "perMoney", badMoney)).prepaidYuan());
        equal(true, WaterOrder.parse(307, fields("isOwner", "true", "timeIds", "X5",
            "perMoney", "abc")).describe().contains("未提供"));
        System.out.println("PASS: read-only order model fails closed and never unlocks a start command");
        javax.crypto.KeyGenerator recoveryKeys = javax.crypto.KeyGenerator.getInstance("AES");
        recoveryKeys.init(256);
        javax.crypto.SecretKey recoveryKey = recoveryKeys.generateKey();
        DeviceProfile control = DeviceProfile.parse(fields("PrjID", "9", "DevID", "7", "bigTypeId", "4",
            "isUse", "1", "snCode", "KLCX20260009"));
        long now = 1789000000000L;
        // An idle device leaves nothing behind; anything else must survive a restart.
        equal(true, PendingUse.of(control, WaterOrder.parse(0, null), now) == null);
        equal(true, PendingUse.of(null, WaterOrder.parse(307, fields("isOwner", "true", "timeIds", "O1")), now) == null);
        equal(true, PendingUse.of(control, null, now) == null);
        PendingUse mineRecord = PendingUse.of(control, WaterOrder.parse(307,
            fields("isOwner", "true", "timeIds", "ORD12345678", "perMoney", "10000")), now);
        equal("9", mineRecord.projectId()); equal("7", mineRecord.deviceId());
        equal("KLCX20260009", mineRecord.requestSerial());
        equal("ORD12345678", mineRecord.orderNo()); equal("MINE", mineRecord.status());
        equal(now, mineRecord.savedAtMillis());
        equal(true, mineRecord.matches(control));
        equal(false, mineRecord.matches(DeviceProfile.parse(fields("PrjID", "9", "DevID", "8",
            "bigTypeId", "4", "isUse", "1", "snCode", "KLCX20260010"))));
        equal(false, mineRecord.matches(null));
        equal(true, mineRecord.describe().contains("重新查询订单状态"));
        equal(false, mineRecord.describe().contains("ORD12345678"));
        equal(false, mineRecord.describe().contains("KLCX20260009"));
        equal(true, PendingUse.of(control, WaterOrder.parse(307, fields("isOwner", "false")), now)
            .describe().contains("被其他账户使用"));
        equal(true, PendingUse.of(control, WaterOrder.parse(307, fields()), now)
            .describe().contains("归属未确认"));
        // The serial must round-trip exactly, since it is what a later re-query is built from.
        byte[] recovery = mineRecord.encode(recoveryKey);
        PendingUse reopened = PendingUse.decode(recovery, recoveryKey);
        equal(mineRecord.requestSerial(), reopened.requestSerial());
        equal(mineRecord.orderNo(), reopened.orderNo());
        equal(mineRecord.status(), reopened.status());
        equal(mineRecord.savedAtMillis(), reopened.savedAtMillis());
        equal(true, reopened.matches(control));
        equal(false, Arrays.equals(recovery, mineRecord.encode(recoveryKey)));
        // A device without a valid serial cannot be re-verified, so it must not be remembered at all.
        DeviceProfile noSerialProfile = DeviceProfile.parse(fields("PrjID", "9", "DevID", "7",
            "bigTypeId", "4", "isUse", "1"));
        equal(true, PendingUse.of(noSerialProfile, WaterOrder.parse(307,
            fields("isOwner", "true", "timeIds", "O2")), now) == null);
        for (int pos : new int[]{0, 1, 12, 13, recovery.length - 1}) {
            byte[] tampered = recovery.clone(); tampered[pos] ^= 1;
            assertions++;
            try { PendingUse.decode(tampered, recoveryKey); throw new AssertionError("Tampered record accepted"); }
            catch (java.security.GeneralSecurityException expected) { /* Authentication rejected. */ }
        }
        assertions++;
        try { PendingUse.decode(recovery, recoveryKeys.generateKey()); throw new AssertionError("Wrong key accepted"); }
        catch (java.security.GeneralSecurityException expected) { /* Wrong key rejected. */ }
        // The session envelope and the recovery envelope must not be interchangeable.
        byte[] sessionBlob = SessionCipher.seal(new AccountSession("42", "synthetic-only"), recoveryKey);
        assertions++;
        try { PendingUse.decode(sessionBlob, recoveryKey); throw new AssertionError("Session blob accepted as recovery"); }
        catch (java.security.GeneralSecurityException expected) { /* Domain separation held. */ }
        assertions++;
        try { SessionCipher.open(recovery, recoveryKey); throw new AssertionError("Recovery blob accepted as session"); }
        catch (java.security.GeneralSecurityException | java.io.IOException expected) { /* Domain separation held. */ }
        System.out.println("PASS: unverified-use record round trip, domain separation and tamper rejection");
        equal("10.00", Money.fromThousandths("10000")); equal("1.00", Money.fromThousandths("999"));
        equal("0.01", Money.fromThousandths("5")); equal("0.00", Money.fromThousandths("0"));
        equal("", Money.fromThousandths("abc")); equal("", Money.fromThousandths("-1"));
        equal("", Money.fromThousandths(null)); equal("", Money.fromThousandths("9999999999"));
        // Start confirmation: the payload is the same whether it arrives by push or by fallback query.
        WaterEvents.Started started = WaterEvents.Started.parse(fields("result", "0", "accountId", "42",
            "accountType", "1", "perMoney", "10000", "timeIds", "ORD99887766", "deviceSncode", "KLCX20260009"));
        equal(true, started.accepted()); equal(false, started.noBalance());
        equal(42L, started.accountId()); equal("10.00", started.prepaidYuan());
        equal("7766", started.maskedOrderNo()); equal("ORD99887766", started.orderNo());
        equal(true, started.belongsTo(42)); equal(false, started.belongsTo(43)); equal(false, started.belongsTo(0));
        equal(true, started.describe().contains("设备已确认开启"));
        equal(false, started.describe().contains("ORD99887766"));
        equal(false, started.toString().contains("ORD99887766"));
        equal(true, WaterEvents.Started.parse(fields("result", "36", "accountId", "42")).accepted());
        equal(true, WaterEvents.Started.parse(fields("result", "2", "accountId", "42")).noBalance());
        equal(false, WaterEvents.Started.parse(fields("result", "2", "accountId", "42")).accepted());
        equal(true, WaterEvents.Started.parse(fields("result", "2", "accountId", "42")).describe().contains("余额不足"));
        // An unrecognised result code is refused, but it still names our own account: ownership is an
        // identity test, acceptance is checked separately, and the caller needs both to be true.
        WaterEvents.Started unknownResult = WaterEvents.Started.parse(fields("result", "7", "accountId", "42"));
        equal(false, unknownResult.accepted());
        equal(true, unknownResult.belongsTo(42)); equal(false, unknownResult.belongsTo(43));
        equal(true, unknownResult.describe().contains("状态码 7"));
        equal(false, unknownResult.describe().contains("设备已确认开启"));
        // An unclear confirmation must never be treated as a confirmed one.
        for (java.util.Map<String, String> broken : java.util.Arrays.asList(null, fields(),
                fields("accountId", "42"), fields("result", "0"), fields("result", "0", "accountId", "0"),
                fields("result", "0", "accountId", "abc"), fields("result", "0", "accountId", "42", "timeIds", "bad order"),
                fields("result", "0", "accountId", "42", "timeIds", repeat('a', 65)),
                fields("result", "x", "accountId", "42")))
            rejects(() -> WaterEvents.Started.parse(broken));
        // The confirmation may carry no order number; that is a valid reply, just an unusable one.
        WaterEvents.Started noOrder = WaterEvents.Started.parse(fields("result", "0", "accountId", "42"));
        equal(true, noOrder.accepted()); equal("", noOrder.orderNo());
        WaterEvents.Settled settled = WaterEvents.Settled.parse(fields("fishTime", "12分30秒",
            "perMoney", "10000", "upMoney", "2380", "upLeadMoney", "7620"));
        equal("12分30秒", settled.duration());
        equal("2.38", settled.chargedYuan()); equal("7.62", settled.refundedYuan());
        equal("10.00", settled.prepaidYuan());
        equal(true, settled.describe().contains("实际扣费：¥ 2.38"));
        equal(true, settled.describe().contains("退还预扣：¥ 7.62"));
        equal(true, settled.describe().contains("以趣智校园账单为准"));
        for (java.util.Map<String, String> broken : java.util.Arrays.asList(null, fields(),
                fields("fishTime", "1分"), fields("upMoney", "abc", "upLeadMoney", "abc")))
            rejects(() -> WaterEvents.Settled.parse(broken));
        WaterEvents.Settled partial = WaterEvents.Settled.parse(fields("upMoney", "500"));
        equal("0.50", partial.chargedYuan()); equal("", partial.refundedYuan());
        equal(false, partial.describe().contains("退还预扣"));
        // Stop results: the reply code *is* the answer, and "already closed" satisfies the goal.
        equal(true, WaterEvents.Stopped.of(0).accepted());
        equal(true, WaterEvents.Stopped.of(0).satisfied());
        equal(false, WaterEvents.Stopped.of(0).alreadyClosed());
        equal(true, WaterEvents.Stopped.of(308).satisfied());
        equal(true, WaterEvents.Stopped.of(308).alreadyClosed());
        equal(false, WaterEvents.Stopped.of(308).accepted());
        equal(true, WaterEvents.Stopped.of(309).badOrderNo());
        equal(false, WaterEvents.Stopped.of(309).satisfied());
        equal(true, WaterEvents.Stopped.of(309).describe().contains("订单号不正确"));
        for (int refused : new int[]{1, 500, 1001, -1})
            equal(false, WaterEvents.Stopped.of(refused).satisfied());
        equal(false, WaterEvents.Stopped.of(500).describe().contains("已确认关水"));
        equal(true, WaterEvents.Stopped.of(500).describe().contains("不要直接重复关水"));
        equal(true, WaterEvents.Stopped.of(308).describe().contains("无需重复关水"));
        equal(true, WaterEvents.Stopped.of(0).describe().contains("已确认关水"));
        equal(false, WaterEvents.Stopped.of(0).toString().contains("308"));
        equal(308, WaterEvents.Stopped.of(308).code());
        // Timing policy: every limit must be bounded, and a backwards clock must not shorten one.
        FieldTestGuard guard = new FieldTestGuard();
        equal(false, guard.awaitingConfirmation()); equal(false, guard.autoStopDue(0));
        equal(0L, guard.remainingAutoStopSeconds(0));
        guard.startRequested(1000L);
        equal(true, guard.awaitingConfirmation());
        equal(false, guard.confirmationOverdue(1000L + FieldTestGuard.CONFIRM_DEADLINE_MILLIS - 1));
        equal(true, guard.confirmationOverdue(1000L + FieldTestGuard.CONFIRM_DEADLINE_MILLIS));
        equal(true, guard.pollDue(1000L));
        guard.polled(1000L);
        equal(false, guard.pollDue(1000L));
        guard.polled(2000L);
        equal(false, guard.pollDue(2000L + FieldTestGuard.POLL_INTERVAL_MILLIS - 1));
        equal(true, guard.pollDue(2000L + FieldTestGuard.POLL_INTERVAL_MILLIS));
        equal(false, guard.autoStopDue(99999L));
        // A clock that jumps backwards counts as zero elapsed, so it can neither trip a deadline early
        // nor stretch the auto-stop cap.
        equal(false, guard.confirmationOverdue(500L));
        guard.confirmedActive(30000L);
        equal(false, guard.awaitingConfirmation()); equal(false, guard.confirmationOverdue(999999L));
        equal(true, guard.active());
        equal(false, guard.autoStopDue(30000L + FieldTestGuard.AUTO_STOP_MILLIS - 1));
        equal(true, guard.autoStopDue(30000L + FieldTestGuard.AUTO_STOP_MILLIS));
        equal(10L, guard.remainingAutoStopSeconds(30000L));
        equal(1L, guard.remainingAutoStopSeconds(30000L + FieldTestGuard.AUTO_STOP_MILLIS - 1));
        equal(0L, guard.remainingAutoStopSeconds(30000L + FieldTestGuard.AUTO_STOP_MILLIS + 5000L));
        guard.reset();
        equal(false, guard.active()); equal(false, guard.awaitingConfirmation());
        equal(false, guard.confirmationOverdue(Long.MAX_VALUE));
        System.out.println("PASS: start confirmation, settlement model and field-test timing limits");
        // Regression: profile-ready alone, stale idle, failed re-query and duplicate taps cannot start.
        FieldStartGate startGate = new FieldStartGate();
        equal(false, startGate.permits(1000)); rejects(() -> startGate.consume(1000));
        startGate.observed(WaterOrder.parse(0, null), 1000);
        equal(true, startGate.permits(1000)); equal(false, startGate.permits(999));
        equal(true, startGate.permits(15999)); equal(false, startGate.permits(16000));
        startGate.invalidate(); equal(false, startGate.permits(2000));
        startGate.observed(WaterOrder.parse(307, fields("isOwner", "false")), 2000);
        equal(false, startGate.permits(2000));
        startGate.observed(WaterOrder.parse(307, fields("isOwner", "true", "orderNo", "O1")), 2000);
        equal(false, startGate.permits(2000));
        startGate.observed(null, 2000); equal(false, startGate.permits(2000));
        startGate.observed(WaterOrder.parse(0, null), 2000); startGate.consume(2001);
        equal(false, startGate.permits(2002)); rejects(() -> startGate.consume(2002));
        startGate.observed(WaterOrder.parse(0, null), 3000); equal(false, startGate.permits(3001));
        equal(true, started.usableFor(42L, "KLCX20260009"));
        equal(false, started.usableFor(42L, "OTHER")); equal(false, started.usableFor(43L, "KLCX20260009"));
        equal(false, noOrder.usableFor(42L, "KLCX20260009"));
        WaterEvents.Started httpStart = WaterEvents.Started.fromHttpConfirmation(fields("accountId", "42", "orderNo", "O1", "deviceSnCode", "S1"));
        equal(true, httpStart.usableFor(42L, "S1")); equal(false, httpStart.usableFor(42L, "S2"));
        rejects(() -> WaterEvents.Started.fromHttpConfirmation(fields()));
        rejects(() -> WaterEvents.Started.fromHttpConfirmation(fields("accountId", "42")));
        for (State stage : new State[]{State.STARTING, State.ACTIVE, State.STOPPING, State.SETTLING}) {
            PendingUse intent = PendingUse.command(control, "42", stage == State.STARTING ? "" : "O1", stage, now);
            PendingUse decoded = PendingUse.decode(intent.encode(recoveryKey), recoveryKey);
            equal(true, decoded.isCommand()); equal(true, decoded.belongsTo("42")); equal(false, decoded.belongsTo("43"));
            equal(stage.name(), decoded.status()); equal(true, decoded.matches(control));
            equal(true, decoded.describe().contains("尚未核实完成"));
            equal(true, decoded.sameRecord(intent));
            equal(false, decoded.sameRecord(null));
            equal(stage==State.STARTING, decoded.canDismissUnknown("42"));
            equal(false, decoded.canDismissUnknown("43"));
            equal(false, decoded.mayRetireIdleUnknown("42",now+119999));
            equal(stage==State.STARTING, decoded.mayRetireIdleUnknown("42",now+120000));
            equal(false, decoded.mayRetireIdleUnknown("42",now-1));
            equal(false, decoded.mayRetireIdleUnknown("43",now+120000));
            equal(false, decoded.sameRecord(PendingUse.command(control,"42",stage==State.STARTING?"":"O1",stage,now+1)));
        }
        rejects(() -> PendingUse.command(control, "", "", State.STARTING, now));
        equal(false, PendingUse.command(control,"42","O1",State.STARTING,now).canDismissUnknown("42"));
        rejects(() -> PendingUse.command(control, "42", "", State.ACTIVE, now));
        rejects(() -> PendingUse.command(control, "42", "O1", State.COMPLETED, now));
        // An authenticated legacy v1 record remains readable after an in-place upgrade.
        ByteArrayOutputStream legacyRecoveryPlain = new ByteArrayOutputStream();
        DataOutputStream legacyOutput = new DataOutputStream(legacyRecoveryPlain);
        legacyOutput.writeUTF("9"); legacyOutput.writeUTF("7"); legacyOutput.writeUTF(control.requestSerial());
        legacyOutput.writeUTF("O1"); legacyOutput.writeUTF("MINE"); legacyOutput.writeLong(now);
        javax.crypto.Cipher legacyRecoveryCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        legacyRecoveryCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, recoveryKey);
        legacyRecoveryCipher.updateAAD("io.qzxy.lite/pending-use/v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        legacyRecoveryCipher.updateAAD(new byte[]{1});
        ByteArrayOutputStream legacyRecoveryEnvelope = new ByteArrayOutputStream();
        legacyRecoveryEnvelope.write(1); legacyRecoveryEnvelope.write(legacyRecoveryCipher.getIV());
        legacyRecoveryEnvelope.write(legacyRecoveryCipher.doFinal(legacyRecoveryPlain.toByteArray()));
        PendingUse legacyRecovery = PendingUse.decode(legacyRecoveryEnvelope.toByteArray(), recoveryKey);
        equal("O1", legacyRecovery.orderNo()); equal(false, legacyRecovery.isCommand()); equal(true, legacyRecovery.matches(control));
        System.out.println("PASS: fresh idle gate, exact confirmation identity and durable command recovery (v1/v2)");
        java.util.Map<String, String> sms = SmsLoginProtocol.sendFields("13800000000");
        equal("18cd336d35b00ab76974f7052d6732a6", sms.get("secret"));
        equal("3", sms.get("typeId"));
        equal("1", sms.get("platform"));
        equal("android", sms.get("phoneSystem"));
        equal("6.5.28", sms.get("version"));
        equal("5", SmsLoginProtocol.loginFields("13800000000", "012345").get("type"));
        equal("012345", SmsLoginProtocol.loginFields("13800000000", "012345").get("smsCode"));
        equal("a%26b=%2B+%3D", SmsLoginProtocol.form(java.util.Collections.singletonMap("a&b", "+ =")));
        // The Config-Project header is taken from the body, so only a whole projectId field counts:
        // a number that happens to sit inside another value must never become the campus.
        equal("9", SmsLoginProtocol.projectIdIn("loginCode=x&projectId=9&version=6.5.28"));
        equal("9", SmsLoginProtocol.projectIdIn("projectId=9"));
        equal("", SmsLoginProtocol.projectIdIn(null));
        equal("", SmsLoginProtocol.projectIdIn(""));
        equal("", SmsLoginProtocol.projectIdIn("projectId="));
        equal("", SmsLoginProtocol.projectIdIn("projectId=0"));
        equal("", SmsLoginProtocol.projectIdIn("myprojectId=9"));
        equal("", SmsLoginProtocol.projectIdIn("projectId=9a"));
        equal("", SmsLoginProtocol.projectIdIn("telephone=projectId%3D9"));
        equal("12", SmsLoginProtocol.projectIdIn("signature=abc&projectId=12&accountId=3"));
        for (String invalid : new String[]{null, "", "1380000000", "23800000000", "1380000000a"})
            rejects(() -> SmsLoginProtocol.sendFields(invalid));
        for (String invalid : new String[]{null, "", "123", "123456789", "12a4"})
            rejects(() -> SmsLoginProtocol.loginFields("13800000000", invalid));
        System.out.println("PASS: SMS protocol fixtures, form escaping and invalid input rejection");
        AccountSession synthetic = new AccountSession("42", "synthetic-session-only");
        final AccountSession[] stored = {synthetic};
        LoginPersistence.Store memory = new LoginPersistence.Store() {
            public AccountSession load() { return stored[0]; }
            public void save(AccountSession value) { stored[0] = value; }
            public void clear() { stored[0] = null; }
        };
        LoginPersistence replacement = new LoginPersistence(memory);
        replacement.cancel(); equal(synthetic, stored[0]);
        AccountSession newer = new AccountSession("42", "new-synthetic", "9");
        replacement.stage(newer); equal(newer, stored[0]);
        replacement.cancel(); equal(synthetic, stored[0]);
        replacement.cancel(); equal(synthetic, stored[0]);
        replacement.stage(newer); replacement.accept(); replacement.cancel(); equal(newer, stored[0]);
        stored[0] = null; replacement.stage(synthetic); replacement.cancel(); equal(true, stored[0] == null);
        equal("42", synthetic.userId());
        equal(false, synthetic.toString().contains(synthetic.loginCode()));
        rejects(() -> new AccountSession("0", "x"));
        rejects(() -> new AccountSession("", "x"));
        rejects(() -> new AccountSession("42", " "));
        LoginCoordinator login = new LoginCoordinator();
        long first = login.begin();
        rejects(login::begin);
        login.signOut();
        equal(false, login.succeed(first, synthetic));
        equal(LoginCoordinator.State.SIGNED_OUT, login.state());
        long second = login.begin();
        equal(false, login.fail(first));
        equal(false, login.succeed(first, synthetic));
        equal(true, login.succeed(second, synthetic));
        equal(synthetic, login.session());
        rejects(login::begin);
        equal(false, login.fail(second));
        login.signOut();
        equal(true, login.session() == null);
        long third = login.begin();
        equal(true, login.fail(third));
        equal(LoginCoordinator.State.SIGNED_OUT, login.state());
        System.out.println("PASS: login deduplication, cancellation and stale-response rejection");
        javax.crypto.KeyGenerator keyGenerator = javax.crypto.KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        javax.crypto.SecretKey storageKey = keyGenerator.generateKey();
        byte[] sealed = SessionCipher.seal(synthetic, storageKey);
        AccountSession withProject = new AccountSession("42", "synthetic", "9");
        equal("9", SessionCipher.open(SessionCipher.seal(withProject, storageKey), storageKey).projectId());
        // Produce the old two-field envelope independently to verify upgrade compatibility.
        ByteArrayOutputStream oldPlain = new ByteArrayOutputStream();
        DataOutputStream oldOut = new DataOutputStream(oldPlain); oldOut.writeUTF("42"); oldOut.writeUTF("legacy-synthetic");
        javax.crypto.Cipher oldCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        oldCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, storageKey);
        oldCipher.updateAAD("io.qzxy.lite/session/v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        ByteArrayOutputStream oldEnvelope = new ByteArrayOutputStream(); oldEnvelope.write(1); oldEnvelope.write(oldCipher.getIV()); oldEnvelope.write(oldCipher.doFinal(oldPlain.toByteArray()));
        equal("legacy-synthetic", SessionCipher.open(oldEnvelope.toByteArray(), storageKey).loginCode());
        equal("", SessionCipher.open(oldEnvelope.toByteArray(), storageKey).projectId());
        equal(synthetic.loginCode(), SessionCipher.open(sealed, storageKey).loginCode());
        equal(synthetic.userId(), SessionCipher.open(sealed, storageKey).userId());
        equal(false, Arrays.equals(sealed, SessionCipher.seal(synthetic, storageKey)));
        for (int pos : new int[]{0, 1, 12, 13, sealed.length - 1}) {
            byte[] tampered = sealed.clone(); tampered[pos] ^= 1;
            assertions++;
            try { SessionCipher.open(tampered, storageKey); throw new AssertionError("Tampered session accepted"); }
            catch (java.security.GeneralSecurityException expected) { /* Authentication rejected. */ }
        }
        assertions++;
        try { SessionCipher.open(sealed, keyGenerator.generateKey()); throw new AssertionError("Wrong key accepted"); }
        catch (java.security.GeneralSecurityException expected) { /* Wrong key rejected. */ }
        System.out.println("PASS: encrypted session round trip, random IV, tampering and wrong-key rejection");
        // Known-answer fixtures independently calculated with Python hashlib.
        equal("3e4842da101bb5bc351cd54181619d76", NativeSigningV6528.signSerialized("", ""));
        equal("ebb52189a9553b6cee324ee09817e3b7", NativeSigningV6528.signSerialized("loginCodeexampletelephone13800000000", "example"));
        equal("b58a68b225b43a4de2bb13cc7d370970", NativeSigningV6528.signSerialized("deviceId42projectId7", "synthetic-key"));
        equal("7499be138265844d65f1219d5aadff6f", NativeSigningV6528.signSerialized("学校科信", "example"));
        rejects(() -> NativeSigningV6528.signSerialized(null, "example"));
        rejects(() -> NativeSigningV6528.signSerialized("x", null));
        // JDK's writeUTF is an independent implementation of JNI's modified UTF-8 encoding.
        for (String input : new String[]{"ascii", "\u0000", "学校", "\uD83D\uDCA7", "\uD800", "a\u0000\u07ff\u0800"}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new DataOutputStream(bytes).writeUTF(input);
            byte[] reference = Arrays.copyOfRange(bytes.toByteArray(), 2, bytes.size());
            equal(true, Arrays.equals(reference, NativeSigningV6528.modifiedUtf8(input)));
        }
        System.out.println("PASS: signing fixtures and JNI encoding boundaries");

        UseSession session = new UseSession();
        rejects(session::requestStart); rejects(session::settlementAcknowledged);
        session.resolve(); session.deviceResolved(); session.requestStart();
        rejects(session::requestStart); rejects(session::requestStop);
        session.startAcknowledged(); session.requestStop();
        rejects(session::requestStop); rejects(session::cancelBeforeStart);
        equal(State.STOPPING, session.state());
        session.stopAcknowledged(); equal(State.SETTLING, session.state());
        session.settlementAcknowledged(); equal(State.COMPLETED, session.state());
        rejects(session::settlementAcknowledged);
        System.out.println("PASS: acknowledgements required; duplicate start/stop rejected");

        for (State stage : new State[]{State.STARTING, State.ACTIVE, State.STOPPING, State.SETTLING}) {
            UseSession s = ready(); s.requestStart();
            if (stage != State.STARTING) s.startAcknowledged();
            if (stage == State.STOPPING || stage == State.SETTLING) s.requestStop();
            if (stage == State.SETTLING) s.stopAcknowledged();
            s.connectionLost(); equal(State.RECONCILE, s.state());
            rejects(s::requestStart); rejects(s::resolve); rejects(s::settlementAcknowledged);
            rejects(() -> s.reconcile(State.IDLE));
        }
        UseSession resolved = active(); resolved.connectionLost(); resolved.reconcile(State.ACTIVE);
        equal(State.ACTIVE, resolved.state());
        System.out.println("PASS: disconnect never implies stopped or settled; reconciliation required");

        for (State stage : new State[]{State.STARTING, State.ACTIVE, State.STOPPING, State.SETTLING, State.RECONCILE}) {
            UseSession restored = new UseSession(); restored.restore(stage);
            equal(State.RECONCILE, restored.state()); rejects(restored::requestStart);
        }
        UseSession cancelled = ready(); cancelled.cancelBeforeStart(); equal(State.IDLE, cancelled.state());
        System.out.println("PASS: interrupted sessions restore into reconciliation, not idle");

        UseSession raced = ready(); CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger(); Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try { start.await(); raced.requestStart(); accepted.incrementAndGet(); }
                catch (IllegalStateException expected) { /* another request already won */ }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            threads[i].start();
        }
        start.countDown(); for (Thread t : threads) t.join();
        equal(1, accepted.get()); equal(State.STARTING, raced.state());
        System.out.println("PASS: concurrent start requests yield exactly one accepted transition");

        // Timetable colours: different courses must not look the same, and a course must keep its colour.
        equal(18, TimetablePalette.SIZE);
        equal(0xffeef1ef, TimetablePalette.fill(0)); equal(0xff6b778c, TimetablePalette.ink(0));
        equal(0xffeef1ef, TimetablePalette.fill(-3)); equal(0xff6b778c, TimetablePalette.ink(99));
        for (int i = 1; i <= TimetablePalette.SIZE; i++) {
            for (int j = i + 1; j <= TimetablePalette.SIZE; j++)
                if (TimetablePalette.fill(i) == TimetablePalette.fill(j)) throw new AssertionError("Palette fills must be distinct");
            if (TimetablePalette.ink(i) == TimetablePalette.fill(i)) throw new AssertionError("Ink must differ from its fill");
        }
        assertions++;
        java.util.List<String> eight = Arrays.asList("自动控制原理", "发电厂电气设备", "汽轮机原理", "工程伦理",
            "大学生职业生涯规划(2)", "电子技术基础", "传热学", "习近平新时代中国特色社会主");
        java.util.List<String> distinct = TimetablePalette.distinctNames(eight);
        equal(8, distinct.size());
        int[] assigned = TimetablePalette.assign(distinct);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int index : assigned) { equal(true, index >= 0 && index < TimetablePalette.SIZE); seen.add(index); }
        equal(8, seen.size());   // Eight different courses, eight different colours.
        // The assignment must not depend on the order the courses happen to be stored in.
        java.util.List<String> shuffled = Arrays.asList("传热学", "电子技术基础", "工程伦理", "自动控制原理",
            "习近平新时代中国特色社会主", "汽轮机原理", "大学生职业生涯规划(2)", "发电厂电气设备");
        java.util.List<String> shuffledDistinct = TimetablePalette.distinctNames(shuffled);
        equal(distinct, shuffledDistinct);
        int[] again = TimetablePalette.assign(shuffledDistinct);
        for (int i = 0; i < assigned.length; i++) equal(assigned[i], again[i]);
        // Duplicates collapse, so the same course always maps to one colour.
        equal(Arrays.asList("传热学", "工程伦理"),
            TimetablePalette.distinctNames(Arrays.asList("传热学", "传热学", "工程伦理", "传热学")));
        equal(0, TimetablePalette.distinctNames(new ArrayList<String>()).size());
        equal(0, TimetablePalette.distinctNames(Arrays.asList("", null)).size());
        equal(0, TimetablePalette.assign(new ArrayList<String>()).length);
        // More distinct courses than colours: reuse is unavoidable, but every index stays in range.
        java.util.List<String> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) many.add("课程" + i);
        java.util.List<String> manyDistinct = TimetablePalette.distinctNames(many);
        equal(40, manyDistinct.size());
        int[] crowded = TimetablePalette.assign(manyDistinct);
        java.util.Set<Integer> usedByCrowd = new java.util.HashSet<>();
        for (int index : crowded) { equal(true, index >= 0 && index < TimetablePalette.SIZE); usedByCrowd.add(index); }
        equal(TimetablePalette.SIZE, usedByCrowd.size());   // All twelve colours get used before any repeats.
        System.out.println("PASS: timetable palette assignment is distinct, stable and bounded");

        // Daily quote: same day, same line — and the day count must not depend on a time zone.
        equal(0L, DailyQuote.daysFromCivil("1970-01-01"));
        equal(10957L, DailyQuote.daysFromCivil("2000-01-01"));
        equal(19782L, DailyQuote.daysFromCivil("2024-02-29"));   // Leap day.
        equal(20718L, DailyQuote.daysFromCivil("2026-09-22"));
        equal(20719L, DailyQuote.daysFromCivil("2026-09-23"));
        equal(47541L, DailyQuote.daysFromCivil("2100-03-01"));   // 2100 is not a leap year.
        equal(1L, DailyQuote.daysFromCivil("1970-01-02") - DailyQuote.daysFromCivil("1970-01-01"));
        equal(1L, DailyQuote.daysFromCivil("2026-03-01") - DailyQuote.daysFromCivil("2026-02-28"));
        equal(true, DailyQuote.count() >= 20);
        equal(DailyQuote.forDate("2026-09-22"), DailyQuote.forDate("2026-09-22"));
        equal(true, DailyQuote.forDate("2026-09-22").length() > 0);
        // Consecutive days must move through the list rather than repeat one line for a week.
        java.util.Set<String> week = new java.util.HashSet<>();
        for (int day = 1; day <= 7; day++) week.add(DailyQuote.forDate("2026-09-" + (day < 10 ? "0" : "") + day));
        equal(7, week.size());
        // The list wraps rather than running out: 2026-10-16 is exactly count() days after 2026-09-22,
        // so it lands on the same line. If the list length changes, update this date to match.
        equal(DailyQuote.forDate("2026-09-22"), DailyQuote.forDate("2026-10-16"));
        for (String broken : new String[]{null, "", "2026-9-22", "20260922", "2026-13-01", "2026-00-10", "2026-09-32"})
            rejects(() -> DailyQuote.forDate(broken));
        System.out.println("PASS: daily quote is stable per date and date arithmetic is zone-independent");

        // Recharge signature: fixed salt, sorted field names, lowercase MD5 (vectors from Python).
        equal("0767b8783fe2ce35b7510dbc0b1c3b17",
            RechargeSignature.alipay("abc123", "50", "13800138000"));
        equal("d3fd54b3f63bb2c52b4bcd1145bbd106",
            RechargeSignature.alipayFallback("abc123", "50", "13800138000"));
        // Order of insertion must not matter: only sorted names enter the digest.
        equal(RechargeSignature.alipay("abc123", "50", "13800138000"),
            RechargeSignature.sign(fields("telPhone", "13800138000", "rechargeMoney", "50",
                "payWay", "2", "loginCode", "abc123"), "sign-kailu="));
        equal(32, RechargeSignature.alipay("abc123", "50", "13800138000").length());
        equal(true, RechargeSignature.alipay("abc123", "50", "13800138000").matches("[0-9a-f]{32}"));
        rejects(() -> RechargeSignature.sign(new java.util.LinkedHashMap<String, String>(), "sign-kailu="));
        rejects(() -> RechargeSignature.sign(null, "sign-kailu="));
        // Vector checked against the live charge route: sending this digest with a deliberately bogus
        // loginCode moved the reply from 参数错误 (rejected at validation) to 登录失效 (rejected only
        // at the session check), which is as far as an unauthenticated request can go. If the sort
        // order or the salt ever drifts, every real order starts failing again.
        equal("51edfe981729391720d2fe3ca22d358d",
            RechargeSignature.alipay("probe", "1", "13800000000"));
        System.out.println("PASS: recharge signature is deterministic and salt-scoped");

        // Settlement tiers are JSON numbers: the original client hands Gson's "50.0" straight to
        // the order. Rewriting it to "50" changes both the signed digest and the amount the server
        // compares against its configured tiers, which is what 157 充值金额错误 looks like.
        equal("50.0", RechargeSignature.normalizeAmount("50.0"));
        equal("0.01", RechargeSignature.normalizeAmount("0.01"));
        equal("1000", RechargeSignature.normalizeAmount("1000"));
        equal("50", RechargeSignature.normalizeAmount(" 50 "));
        equal("50", RechargeSignature.displayAmount("50.0"));
        equal("0.01", RechargeSignature.displayAmount("0.01"));
        equal("300", RechargeSignature.displayAmount("300"));
        equal(true, !RechargeSignature.alipay("abc123", "50.0", "13800138000")
            .equals(RechargeSignature.alipay("abc123", "50", "13800138000")));
        equal(RechargeSignature.alipay("abc123", "50.0", "13800138000"),
            RechargeSignature.sign(fields("telPhone", "13800138000", "rechargeMoney", "50.0",
                "payWay", "2", "loginCode", "abc123"), "sign-kailu="));
        for (String bad : new String[]{null, "", "0", "0.00", "1001", "50.123", "50.", "-50", "1e3", "abc", "１２"})
            rejects(() -> RechargeSignature.normalizeAmount(bad));
        System.out.println("PASS: recharge amounts keep the server tier spelling and stay in range");

        // Automatic timetable import: 正方 field shapes -> text the shared course parser already reads.
        equal(0, EduTimetable.day("1", ""));
        equal(6, EduTimetable.day("", "星期日"));
        equal(6, EduTimetable.day("", "星期天"));
        rejects(() -> EduTimetable.day("8", ""));
        rejects(() -> EduTimetable.day("", "星期八"));
        equal("1-2", EduTimetable.sections("1-2"));
        equal("1-2", EduTimetable.sections("01-02"));
        equal("1-2", EduTimetable.sections("0102"));
        equal("10-11", EduTimetable.sections("1011"));
        equal("3", EduTimetable.sections("3"));
        rejects(() -> EduTimetable.sections("17-18"));
        rejects(() -> EduTimetable.sections("abc"));
        equal("1-16周", EduTimetable.weeks("1-16周", ""));
        equal("1-16周(单周)", EduTimetable.weeks("1-16周(单周)", ""));
        equal("1-16周(双周)", EduTimetable.weeks("1-16", "双"));
        equal("5周", EduTimetable.weeks("5周", ""));
        rejects(() -> EduTimetable.weeks("1,3,5-16周", ""));
        rejects(() -> EduTimetable.weeks("20-1周", ""));
        equal("A B", EduTimetable.clean("A&nbsp;<br/>B"));
        equal("(1-2节)1-16周/场地:教101/教师:张三/教学班:高等数学",
            EduTimetable.courseText("1-2", "1-16周", "教101", "张三", "高等数学"));
        TimetablePdfParser.Course auto = TimetableWebCourseParser.parse(0, "高等数学",
            EduTimetable.courseText("1-2", "1-16周(单周)", "教101", "张三", "高等数学"));
        equal("高等数学", auto.name);
        equal("教101", auto.room);
        equal("张三", auto.teacher);
        equal(0, auto.day);
        equal(1, auto.first);
        equal(2, auto.last);
        equal(1, auto.from);
        equal(16, auto.to);
        equal(1, auto.parity);
        System.out.println("PASS: academic timetable fields become valid course rows");
        java.util.List<String> found = EduSite.timetableQueries(
            "<script>url:'/kbcx/xskbcx_cxXsKbcx.html?gnmkdm=N2151';var a='xskbcx_cxXsKb.html';</script>"
            + "<a href='/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151'>index</a>");
        equal(2, found.size());
        equal("/kbcx/xskbcx_cxXsKbcx.html?gnmkdm=N2151", found.get(0));
        equal("/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151", found.get(1));
        equal(true, EduSite.timetableQueries(null).isEmpty());
        equal(true, EduSite.timetableQueries("<a href='/kbcx/xskbcx_cxXskbcxIndex.html'>index</a>").isEmpty());
        System.out.println("PASS: timetable query endpoints are discovered from the school page");

        // Grade columns: identified by label, never by position, and 学分 must not be read as 成绩.
        int[] graded = EduTableColumns.grade(Arrays.asList("序号", "课程名称", "学分", "成绩", "绩点"));
        equal(1, graded[EduTableColumns.NAME]);
        equal(3, graded[EduTableColumns.SCORE]);
        equal(2, graded[EduTableColumns.CREDIT]);
        equal(4, graded[EduTableColumns.GPA]);
        equal(true, EduTableColumns.usable(graded));
        int[] renamed = EduTableColumns.grade(Arrays.asList("科目", "总成绩", "学分"));
        equal(0, renamed[EduTableColumns.NAME]);
        equal(1, renamed[EduTableColumns.SCORE]);
        equal(-1, renamed[EduTableColumns.GPA]);
        int[] noScore = EduTableColumns.grade(Arrays.asList("课程", "学分", "绩点"));
        equal(-1, noScore[EduTableColumns.SCORE]);
        equal(false, EduTableColumns.usable(noScore));      // A credit count is not a score.
        equal(false, EduTableColumns.usable(EduTableColumns.grade(null)));
        equal(false, EduTableColumns.usable(EduTableColumns.grade(new ArrayList<String>())));
        equal(false, EduTableColumns.usable(null));
        System.out.println("PASS: grade columns are matched by label and refuse ambiguous tables");

        // Academic page values and query masks: read from the school's own markup, never hard-coded.
        String page = "<input type=\"hidden\" name=\"xnm\" value=\"2026\" id=\"xnm\">"
            + "<input type=\"hidden\" name=\"xqm\" value=\"3\" id=\"xqm\">"
            + "<select name=\"xqh_id\" id=\"xqh_id\" class=\"form-control\">"
            + "<option value=\"103\" selected=\"selected\">丛台校区(临床医学院)</option>"
            + "<option value=\"108\">新校区</option><option value=\"106\">东校区(科信学院)</option></select>";
        equal("2026", EduSite.field(page, "xnm"));
        equal("3", EduSite.field(page, "xqm"));
        equal("103", EduSite.field(page, "xqh_id"));
        equal(3, EduSite.options(page, "xqh_id").size());
        equal("106", EduSite.options(page, "xqh_id").get(2)[0]);
        equal("东校区(科信学院)", EduSite.options(page, "xqh_id").get(2)[1]);
        equal("", EduSite.field(page, "yhm"));
        equal(0, EduSite.options(page, "yhm").size());
        equal("1", EduSite.weekMask(1));
        equal("2", EduSite.weekMask(2));
        equal("256", EduSite.weekMask(9));
        rejects(() -> EduSite.weekMask(0));
        rejects(() -> EduSite.weekMask(63));
        rejects(() -> EduSite.periodMask(3, 2));
        equal("3", EduSite.periodMask(1, 2));
        equal("12", EduSite.periodMask(3, 4));
        equal(1, EduSite.weekOn("2026-08-31", "2026-08-31"));
        equal(3, EduSite.weekOn("2026-08-31", "2026-09-14"));
        equal(0, EduSite.weekOn("2026-08-31", "2026-08-30"));
        equal(0, EduSite.weekOn("", "2026-08-31"));
        equal(0, EduSite.weekOn("2026-13-01", "2026-08-31"));
        java.util.List<int[]> pairs = EduSite.pairs(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        equal(5, pairs.size());
        equal(3, pairs.get(1)[0]);
        equal(4, pairs.get(1)[1]);
        java.util.List<int[]> odd = EduSite.pairs(Arrays.asList(1, 2, 3));
        equal(2, odd.size());
        equal(3, odd.get(1)[1]);   // A lone period stays alone; it is never widened.
        equal(0, EduSite.pairs(null).size());
        // Multi-period selection: gaps stay gaps in both the mask and the label.
        equal("19", EduSite.periodMask(new int[]{1, 2, 5}));
        equal("12", EduSite.periodMask(new int[]{3, 4}));
        rejects(() -> EduSite.periodMask(new int[]{}));
        rejects(() -> EduSite.periodMask(new int[]{0}));
        rejects(() -> EduSite.periodMask(new int[]{63}));
        equal(3, EduSite.sections("1,2,5").length);
        equal(5, EduSite.sections("1,2,5")[2]);
        equal(4, EduSite.sections("1-4").length);
        equal(4, EduSite.sections("1-4,2")[3]);   // duplicates collapse
        equal(0, EduSite.sections("").length);
        equal(0, EduSite.sections(null).length);
        equal(0, EduSite.sections("0,99,abc").length);
        equal(0, EduSite.sections("3-2").length);   // reversed range is dropped
        equal("第 1-2、5 节", EduSite.sectionLabel(new int[]{1, 2, 5}));
        equal("第 3 节", EduSite.sectionLabel(new int[]{3}));
        equal("第 1-3 节", EduSite.sectionLabel(new int[]{1, 2, 3}));
        equal("", EduSite.sectionLabel(null));
        // Grade page found in the school's own menu, so a renamed module still resolves.
        String menu = "<ul><li><a href=\"/xsxk/zzz.html?gnmkdm=N253508\">选课</a></li>"
            + "<li><a href=\"/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005\"><span>成绩查询</span></a></li>"
            + "<li><a href=\"javascript:void(0)\">成绩说明</a></li></ul>";
        equal("/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005", EduSite.gradeLink(menu));
        equal("", EduSite.gradeLink("<a href=\"/xsxk/xk.html\">选课</a>"));
        equal("", EduSite.gradeLink(null));
        // 正方 builds its own menu in script; the anchor-only parse missed it and the app then fell
        // back to a compiled-in route this school answers with its 404 page.
        String scriptMenu = "<script>openTab('成绩查询','/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005','N305005');</script>";
        equal("/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005", EduSite.gradeLink(scriptMenu));
        // The function code is what marks the grade module, even in a folder of its own.
        equal("/xscjgl/xscjgl_cxXs.html?gnmkdm=N305021", EduSite.gradeLink(
            "<script>openTab('学生成绩','/xscjgl/xscjgl_cxXs.html?gnmkdm=N305021','N305021')</script>"));
        equal("", EduSite.gradeLink("<script>openTab('选课','/xsxk/xk.html?gnmkdm=N253508')</script>"));
        equal("/cjcx/cjcx_cxXsgrcj.html", EduSite.gradeLink("<script>openTab('X','/cjcx/cjcx_cxXsgrcj.html')</script>"));
        // 正方's script escapes every Chinese character; without decoding, 成绩查询 is invisible.
        equal("成绩查询", EduSite.unescape("\\u6210\\u7ee9\\u67e5\\u8be2"));
        equal("/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005", EduSite.gradeLink(
            "<script>openTab('\\u6210\\u7ee9\\u67e5\\u8be2','/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005')</script>"));
        equal("/a/b.html", EduSite.unescape("\\/a\\/b.html"));
        // A path next to the word 成绩 in someone else's text must not become "the grade page".
        equal("", EduSite.gradeLink(
            "<script>openTab('选课','/xsxk/xk.html?gnmkdm=N253508')</script><p>成绩说明</p>"));
        // Two script entries: each name binds to its own path, never to its neighbour's.
        equal("/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005", EduSite.gradeLink(
            "<script>openTab('选课','/xsxk/xk.html?gnmkdm=N253508');"
                + "openTab('成绩查询','/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005');</script>"));
        // The JSON menu 正方's /xtgl/index_initMenu.html returns, with every Chinese char escaped.
        String jsonMenu = "[{\"text\":\"\\u9009\\u8bfe\",\"url\":\"/xsxk/xk.html?gnmkdm=N253508\"},"
            + "{\"text\":\"\\u6210\\u7ee9\\u67e5\\u8be2\",\"url\":\"/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005\"}]";
        equal("/cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005", EduSite.gradeLink(jsonMenu));
        // Term picker: the school renders xnm/xqm as hidden inputs, so the app must offer years.
        java.util.List<String[]> years = EduSite.yearOptions("2026", 6);
        equal(7, years.size());
        equal("2026", years.get(0)[0]);
        equal("2026-2027 学年", years.get(0)[1]);
        equal("2021", years.get(5)[0]);
        equal("ALL", years.get(6)[0]);
        equal("全部学年", years.get(6)[1]);
        equal(2, EduSite.yearOptions("2026", 0).size());    // one year, plus 全部学年
        equal(13, EduSite.yearOptions("2026", 99).size());   // bounded, not an endless list
        equal(0, EduSite.yearOptions("abc", 6).size());
        equal(0, EduSite.yearOptions(null, 6).size());
        equal(0, EduSite.yearOptions("16", 6).size());
        java.util.List<String[]> terms = EduSite.termOptions("3");
        equal(4, terms.size());
        equal("3", terms.get(0)[0]);
        equal("第一学期", terms.get(0)[1]);
        equal("12", terms.get(1)[0]);
        equal("16", terms.get(2)[0]);
        equal("ALL", terms.get(3)[0]);
        equal("全部学期", terms.get(3)[1]);
        java.util.List<String[]> simple = EduSite.termOptions("1");
        equal(3, simple.size());
        equal("2", simple.get(1)[0]);
        equal(2, EduSite.termOptions("7").size());          // unknown code is kept, not guessed
        equal(0, EduSite.termOptions("").size());
        equal(0, EduSite.termOptions(null).size());
        System.out.println("PASS: academic page values, grade menu decoding, term choices and empty-room query masks");

        // Room type the screen opens on: 多媒体教室一, then any 多媒体 room, else every room.
        java.util.List<String[]> types = Arrays.asList(
            new String[]{"", "全部"},
            new String[]{"01", "普通教室"},
            new String[]{"02", "多媒体教室二"},
            new String[]{"03", "多媒体教室一"});
        equal("03", EduSite.defaultType(types));
        equal("02", EduSite.defaultType(java.util.Collections.singletonList(new String[]{"02", "多媒体教室二"})));
        equal("", EduSite.defaultType(java.util.Collections.singletonList(new String[]{"01", "普通教室"})));
        equal("", EduSite.defaultType(new ArrayList<String[]>()));
        equal("", EduSite.defaultType(null));
        // A class is two periods here: the picker offers 1-2, 3-4 and stores the whole classes chosen.
        java.util.List<int[]> classes = EduSite.pairs(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8));
        equal("1-2", EduSite.pairText(classes.get(0)));
        equal("7-8", EduSite.pairText(classes.get(3)));
        equal("7", EduSite.pairText(new int[]{7, 7}));      // a lone period is not written as a range
        equal("", EduSite.pairText(null));
        boolean[] chosen = EduSite.chosenPairs(classes, EduSite.sections("1-2,5-6"));
        equal(true, chosen[0]);
        equal(false, chosen[1]);
        equal(true, chosen[2]);                             // any period of a class selects it
        equal(false, chosen[3]);
        equal("1-2,5-6", EduSite.pairValue(classes, chosen));
        equal("", EduSite.pairValue(classes, new boolean[classes.size()]));
        equal("", EduSite.pairValue(null, chosen));
        equal(4, EduSite.periodsOf(classes, new boolean[]{true, false, true, false}).length);
        equal(5, EduSite.periodsOf(classes, new boolean[]{true, false, true, false})[2]);
        equal("51", EduSite.periodMask(EduSite.periodsOf(classes, new boolean[]{true, false, true, false})));
        equal(0, EduSite.periodsOf(classes, new boolean[0]).length);

        // ---- 正方 login form: csrf token, hidden fields, RSA password ----
        String form = "'<form action=\"/xtgl/login_slogin.html\" method=\"post\">"
            + "<input type=\"hidden\" id=\"csrftoken\" name=\"csrftoken\" value=\"65095068-185b,65095068185b\"/>"
            + "<input type=\"hidden\" id=\"language\" name=\"language\" value=\"zh_CN\"/>"
            + "<input type=\"hidden\" name=\"xxdm\" id=\"xxdm\" value= 10076>"
            + "<input type=\"hidden\" name=\"ydType\" id=\"ydType\" value= >"
            + "<input type=\"text\" class=\"form-control\" name=\"yhm\" id=\"yhm\" value=\"\">"
            + "<input type=\"text\" name=\"mm\" id=\"mm\" value=\"\">"
            + "</form>'";
        java.util.Map<String, String> loginFields = EduLoginProtocol.fields(form.replace("'", ""));
        equal(6, loginFields.size());
        equal("65095068-185b,65095068185b", EduLoginProtocol.csrf(form));
        equal("zh_CN", loginFields.get("language"));
        equal("10076", loginFields.get("xxdm"));
        equal("", loginFields.get("ydType"));
        equal(true, EduLoginProtocol.isLoginForm(form));
        equal(false, EduLoginProtocol.isLoginForm("{\"items\":[],\"totalPage\":1}"));
        equal(false, EduLoginProtocol.csrf("<html></html>").length() > 0);
        equal(true, EduLoginProtocol.wrongPassword("<div id=\"tips\">用户名或密码不正确，请重新输入！</div>"));
        equal(false, EduLoginProtocol.wrongPassword("{\"items\":[{\"kcmc\":\"高等数学\"}]}"));
        // A hidden modal that merely mentions 验证码 must not be mistaken for a captcha demand.
        equal(false, EduLoginProtocol.captchaRequired("<div id=\"yzmModal\" style=\"display:none\">验证码登录</div>"));
        equal(true, EduLoginProtocol.captchaRequired("<input type=\"text\" name=\"yzm\" id=\"yzm\"/>"));
        equal("MIIBIjAN", EduLoginProtocol.jsonValue("{\"modulus\":\"MIIBIjAN\",\"exponent\":\"AQAB\"}", "modulus"));
        equal("AQAB", EduLoginProtocol.jsonValue("{\"modulus\":\"MIIBIjAN\",\"exponent\":\"AQAB\"}", "exponent"));
        equal("", EduLoginProtocol.jsonValue("{\"modulus\":\"x\"}", "exponent"));
        equal("YWJj", EduLoginProtocol.base64Encode("abc".getBytes("UTF-8")));
        equal("YWI=", EduLoginProtocol.base64Encode("ab".getBytes("UTF-8")));
        equal("YQ==", EduLoginProtocol.base64Encode("a".getBytes("UTF-8")));
        equal("AQAB", EduLoginProtocol.base64Encode(new byte[]{1, 0, 1}));
        equal(3, EduLoginProtocol.base64Decode("AQAB").length);
        equal(1, (int) EduLoginProtocol.base64Decode("AQAB")[0]);
        equal(0, (int) EduLoginProtocol.base64Decode("AQAB")[1]);
        equal("", new String(EduLoginProtocol.base64Decode(""), "UTF-8"));
        equal("abc", new String(EduLoginProtocol.base64Decode("YWJj"), "UTF-8"));

        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        java.security.KeyPair pair = generator.generateKeyPair();
        java.security.interfaces.RSAPublicKey publicKey = (java.security.interfaces.RSAPublicKey) pair.getPublic();
        String modulus = EduLoginProtocol.base64Encode(unsigned(publicKey.getModulus()));
        String exponent = EduLoginProtocol.base64Encode(unsigned(publicKey.getPublicExponent()));
        equal("AQAB", exponent);
        String cipherText = EduLoginProtocol.encryptPassword(modulus, exponent, "s3cret密码");
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, pair.getPrivate());
        equal("s3cret密码", new String(cipher.doFinal(EduLoginProtocol.base64Decode(cipherText)), "UTF-8"));
        equal(128, EduLoginProtocol.base64Decode(cipherText).length);
        rejects(() -> {
            try { EduLoginProtocol.encryptPassword("AAAA", "AQAB", "x"); }
            catch (java.security.GeneralSecurityException expected) { throw new IllegalStateException(expected); }
        });
        System.out.println("PASS: school login form fields and RSA password");

        // 身份码 travels to 淘宝 as an h5Url query value, so everything but unreserved chars is escaped
        // and '+' never appears: Taobao's bridge reads '+' as a space and would break the URL.
        equal("https%3A%2F%2Fpages-fast.m.taobao.com%2Fwow%2Fz%2Funiapp%2F1011717%2Flast-mile-fe%2Fend-collect-platform%2Fidentity-code%3Fx-ssr%3Dtrue",
            PickupCodeLinks.encode(PickupCodeLinks.TAOBAO_IDENTITY_H5));
        equal("a%20b%2Bc", PickupCodeLinks.encode("a b+c"));
        equal("%E8%BA%AB%E4%BB%BD", PickupCodeLinks.encode("身份"));
        equal("-._~AZaz09", PickupCodeLinks.encode("-._~AZaz09"));
        String[] bridges = PickupCodeLinks.taobaoBridges();
        equal(2, bridges.length);
        equal(true, bridges[0].startsWith("taobao://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2F"));
        equal(true, bridges[1].startsWith("tbopen://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2F"));
        equal(1, bridges[0].split("\\?", -1).length - 1);
        equal(false, bridges[0].contains("+"));
        equal("cainiao://desktop/station_code", PickupCodeLinks.CAINIAO_IDENTITY);
        System.out.println("PASS: identity-code bridge links keep the Taobao page address intact");

        // Term start date. The stored text is one canonical format because the week counter parses
        // it; what is typed may drop the zero in front of a month or a day, which is the whole point.
        equal("2026-08-31", TermDate.normalize("2026-08-31"));
        equal("2026-08-31", TermDate.normalize("2026-8-31"));
        equal("2026-08-31", TermDate.normalize("  2026-8-31  "));
        equal("2026-09-07", TermDate.normalize("2026-9-7"));
        equal("2026-12-28", TermDate.normalize("2026-12-28"));
        equal("2026-01-05", TermDate.normalize("2026-1-5"));
        // Not a Monday, not a real day, not this shape: refused rather than guessed.
        rejects(() -> TermDate.normalize("2026-8-30"));
        rejects(() -> TermDate.normalize("2026-2-30"));
        rejects(() -> TermDate.normalize("2026-13-07"));
        rejects(() -> TermDate.normalize("2026/8/31"));
        rejects(() -> TermDate.normalize("2026-08-31 08:00"));
        rejects(() -> TermDate.normalize("26-8-31"));
        rejects(() -> TermDate.normalize("2026-08-32"));
        rejects(() -> TermDate.normalize(""));
        rejects(() -> TermDate.normalize(null));
        System.out.println("PASS: a term date may omit the leading zero, and still has to be a real Monday");

        // Timetables. The index is the only place that knows the list and the pointer; an install
        // from before multiple timetables has no index at all, so empty input has to produce the one
        // "main" table whose storage the old code already wrote to — no migration, no lost timetable.
        TimetableIndex fresh = TimetableIndex.parse(null);
        equal(1, fresh.size());
        equal(TimetableIndex.FIRST, fresh.current());
        equal("我的课表", fresh.currentName());
        equal(1, TimetableIndex.parse("").size());
        equal(1, TimetableIndex.parse("{broken").size());
        equal(1, TimetableIndex.parse("CampusKit.timetables/1\ncurrent=main\n").size());
        String created = fresh.create("2026 秋", 1000L);
        equal(2, fresh.size());
        equal(created, fresh.current());
        equal("2026 秋", fresh.currentName());
        String secondTable = fresh.create("辅修", 1000L);
        equal(false, secondTable.equals(created));
        equal(3, fresh.size());

        // Round trip keeps order, ids and the pointer.
        TimetableIndex reloaded = TimetableIndex.parse(fresh.toText());
        equal(3, reloaded.size());
        equal(secondTable, reloaded.current());
        equal(created, reloaded.tables().get(1).id);

        // Choosing something that is not in the list must not move the pointer.
        equal(false, reloaded.select("nope"));
        equal(secondTable, reloaded.current());

        // Renaming trims, caps and never leaves an empty label.
        equal(true, reloaded.rename(created, "  秋季课表  "));
        equal("秋季课表", reloaded.tables().get(1).name);
        equal(true, reloaded.rename(created, "   "));
        equal("我的课表", reloaded.tables().get(1).name);
        equal(true, reloaded.rename(created, "一二三四五六七八九十一二三四五六七八九十一二三四五"));
        equal(TimetableIndex.NAME_LIMIT, reloaded.tables().get(1).name.length());

        // Deleting the current table falls back to the first one; the last table never goes.
        equal(true, reloaded.remove(secondTable));
        equal(TimetableIndex.FIRST, reloaded.current());
        equal(true, reloaded.remove(created));
        equal(TimetableIndex.FIRST, reloaded.current());
        equal(false, reloaded.remove(TimetableIndex.FIRST));
        equal(1, reloaded.size());
        equal(false, reloaded.remove("missing"));
        System.out.println("PASS: several timetables share one index without losing the original");

        // Board geometry. The board is the one screen with seven fixed columns, so its type sizes
        // cannot be constants: a 320dp phone and a 480dp phone both have to show a readable name.
        // 320dp is the narrowest phone Android still ships; the other widths are the common ones.
        int[] boards = {288, 304, 328, 344, 400, 464, 560, 900};
        for (int board : boards) {
            int axis = BoardFit.axis(board), column = BoardFit.column(board);
            equal(true, axis >= BoardFit.MIN_AXIS && axis <= BoardFit.MAX_AXIS);
            // Seven columns and the gutter never ask for more width than the board has.
            equal(true, axis + 7 * column <= board);
            equal(true, column >= 30);
            int title = BoardFit.titleSize(column), pad = BoardFit.cellPad(column);
            equal(true, title >= BoardFit.MIN_TITLE && title <= BoardFit.MAX_TITLE);
            // At least two characters still fit on one line in the narrowest case.
            equal(true, (column - 2 * pad) / title >= 2);
            // A wider column never gets smaller type than a narrower one.
            equal(true, title >= BoardFit.titleSize(Math.max(1, column - 1)));
            equal(true, BoardFit.roomSize(title) < title);
        }
        // Concrete widths: the gutter shrinks on a narrow phone and stops growing on a wide one.
        equal(33, BoardFit.axis(288));
        equal(38, BoardFit.axis(328));
        equal(46, BoardFit.axis(400));
        equal(46, BoardFit.axis(900));
        equal(36, BoardFit.column(288));
        equal(12, BoardFit.titleSize(43));
        equal(10, BoardFit.titleSize(36));
        equal(13, BoardFit.titleSize(60));
        equal(4, BoardFit.titleCap(43));
        equal(3, BoardFit.titleCap(36));
        equal(4, BoardFit.titleCap(60));
        // A one-section block on a short screen drops the room line rather than squeezing the name.
        equal(0, BoardFit.roomLines(30, 36));
        equal(1, BoardFit.roomLines(53, 43));
        equal(2, BoardFit.roomLines(110, 43));
        equal(0, BoardFit.roomLines(10, 43));
        // Degenerate input never produces a zero or negative size.
        equal(BoardFit.MIN_AXIS, BoardFit.axis(0));
        equal(1, BoardFit.column(0));
        System.out.println("PASS: the timetable board scales its gutter, padding and type to the screen");

        // Update manifest. The file is public and hand-written, so every malformed shape must be
        // turned away here rather than reaching the download code with half its fields missing.
        UpdateManifest release = UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,"
            + "\"notes\":[\"课表右侧的三角形放大了\",\"   \",\"其它页面按屏幕宽度留白\"],"
            + "\"url\":\"https://example.edu/app/campuskit-2.3.0.apk\",\"bytes\":5242880,"
            + "\"sha256\":\"" + repeat('a', 64) + "\"}");
        equal("2.3.0", release.version);
        equal(158, release.versionCode);
        // A blank entry is spacing in the source file, not a bullet point in the dialog.
        equal(2, release.notes.size());
        equal("课表右侧的三角形放大了", release.notes.get(0));
        equal(5242880L, release.bytes);
        equal(repeat('a', 64), release.sha256);
        equal(true, release.newerThan(157));
        equal(false, release.newerThan(158));
        equal(false, release.newerThan(159));

        // The name only decides what the dialog says about itself; the code decides that it appears.
        equal(false, UpdateManifest.parse("{\"version\":\"9.9.9\",\"versionCode\":3,"
            + "\"notes\":[\"x\"],\"url\":\"https://example.edu/a.apk\"}").newerThan(158));

        // Optional fields stay absent instead of being invented.
        UpdateManifest bare = UpdateManifest.parse("{\"version\":\"2.3\",\"versionCode\":158,"
            + "\"notes\":[\"x\"],\"url\":\"https://example.edu/a.apk\"}");
        equal(0L, bare.bytes);
        equal("", bare.sha256);
        // An unknown key is left for a future field and does not invalidate the release.
        UpdateManifest extended = UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,"
            + "\"notes\":[\"x\"],\"url\":\"https://example.edu/a.apk\",\"channel\":\"beta\"}");
        equal(158, extended.versionCode);

        // Escapes survive, because a note may quote itself. A newline does not: every note is one
        // bullet in the dialog, so a two-line entry is rejected rather than drawn half-height.
        equal("他说" + '"' + "好了" + '"',
            UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,"
                + "\"notes\":[\"他说\\\"好了\\\"\"],\"url\":\"https://example.edu/a.apk\"}").notes.get(0));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,"
            + "\"notes\":[\"第一行\\n第二行\"],\"url\":\"https://example.edu/a.apk\"}"));

        String ok = "\"version\":\"2.3.0\",\"versionCode\":158,\"notes\":[\"x\"]";
        rejects(() -> UpdateManifest.parse(""));
        rejects(() -> UpdateManifest.parse("null"));
        rejects(() -> UpdateManifest.parse("[]"));
        rejects(() -> UpdateManifest.parse("{" + ok + "} trailing"));
        rejects(() -> UpdateManifest.parse("{" + ok + ",\"url\":\"http://example.edu/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{" + ok + ",\"url\":\"file:///sdcard/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{" + ok + ",\"url\":\"https://example.edu/../a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{" + ok + ",\"url\":\"\"}"));
        // Missing pieces of every kind.
        rejects(() -> UpdateManifest.parse("{\"versionCode\":158,\"notes\":[\"x\"],\"url\":\"https://e.cn/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"notes\":[\"x\"],\"url\":\"https://e.cn/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,\"url\":\"https://e.cn/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,\"notes\":[\"x\"]}"));
        // Values that would break the dialog or the download.
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0-beta\",\"versionCode\":158,\"notes\":[\"x\"],\"url\":\"https://e.cn/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":0,\"notes\":[\"x\"],\"url\":\"https://e.cn/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":15.5,\"notes\":[\"x\"],\"url\":\"https://e.cn/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,\"notes\":[],\"url\":\"https://e.cn/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,\"notes\":[\"x\"],\"url\":\"https://e.cn/a.apk\",\"bytes\":-1}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,\"notes\":[\"x\"],\"url\":\"https://e.cn/a.apk\",\"sha256\":\"zz\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,\"notes\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\"],\"url\":\"https://e.cn/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,\"notes\":[\"x\"],\"url\":\"https://e.cn/a.apk\",}"));
        rejects(() -> UpdateManifest.parse("{version:\"2.3.0\",\"versionCode\":158,\"notes\":[\"x\"],\"url\":\"https://e.cn/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,\"notes\":\"x\",\"url\":\"https://e.cn/a.apk\"}"));
        rejects(() -> UpdateManifest.parse("{\"version\":\"2.3.0\",\"versionCode\":158,\"notes\":[\"x\",1],\"url\":\"https://e.cn/a.apk\"}"));
        System.out.println("PASS: an update manifest is accepted only when every field it will use is sound");

        System.out.println("All " + assertions + " assertions passed. Live API/Bluetooth compatibility is not tested here.");
    }

    /** BigInteger.toByteArray adds a sign byte; the base64 in the school's JSON has none. */
    private static byte[] unsigned(java.math.BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length > 1 && raw[0] == 0) return Arrays.copyOfRange(raw, 1, raw.length);
        return raw;
    }
}
