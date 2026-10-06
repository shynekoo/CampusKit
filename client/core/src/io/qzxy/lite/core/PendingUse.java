package io.qzxy.lite.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Minimal record of a device whose last query did NOT come back idle.
 *
 * Purpose: an app restart, a crash or a lost network must never let the UI look "clean" while an
 * order may still be open. This record only ever says "at time T the server reported state S for
 * device D" — it is a note about a past answer, never a claim about the present. Anything that reads
 * it must re-query before showing a current state, which is why {@link #describe()} asks for a fresh
 * check rather than asserting an outcome.
 *
 * The record holds the minimum needed to re-verify: the device identity, its serial number (required
 * to re-query), the remote order number (required by a future close call) and the last state seen.
 * It is always stored encrypted; a separate AAD from the session envelope means a session blob can
 * never be replayed as a recovery record.
 */
public final class PendingUse {
    private static final byte[] AAD = "io.qzxy.lite/pending-use/v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte VERSION = 2;
    private static final long MAX_CLOCK_SKEW_MILLIS = 4102444800000L; // 2100-01-01
    private static final Set<String> STATUSES = new LinkedHashSet<>(
        Arrays.asList("MINE", "OTHER", "PENDING", "STARTING", "ACTIVE", "STOPPING", "SETTLING"));

    private final String projectId, deviceId, serial, orderNo, status;
    private final long savedAtMillis;
    private String accountId = "";

    private PendingUse(String projectId, String deviceId, String serial, String orderNo, String status, long savedAtMillis) {
        this.projectId = projectId; this.deviceId = deviceId; this.serial = serial;
        this.orderNo = orderNo; this.status = status; this.savedAtMillis = savedAtMillis;
    }

    /**
     * @return the record to keep, or null when the query confirmed the device was idle
     */
    public static PendingUse of(DeviceProfile profile, WaterOrder order, long nowMillis) {
        if (profile == null || order == null || !order.blocksStart()) return null;
        String projectId = profile.projectId(), deviceId = profile.deviceId();
        String serial = profile.requestSerial();
        if (!projectId.matches("[1-9][0-9]{0,8}") || !deviceId.matches("[1-9][0-9]{0,8}")) return null;
        if (!serial.matches("[A-Za-z0-9_-]{1,64}")) return null;
        String orderNo = order.orderNo();
        if (!orderNo.isEmpty() && !orderNo.matches("[A-Za-z0-9_-]{1,64}")) return null;
        if (!STATUSES.contains(order.status().name())) return null;
        return new PendingUse(projectId, deviceId, serial, orderNo, order.status().name(), nowMillis);
    }

    public boolean matches(DeviceProfile profile) {
        return profile != null && projectId.equals(profile.projectId()) && deviceId.equals(profile.deviceId())
            && serial.equals(profile.requestSerial());
    }

    public boolean isCommand() { return !accountId.isEmpty(); }
    public boolean belongsTo(String id) { return isCommand() && accountId.equals(id); }
    public boolean belongsToAccount(PendingUse other) { return other != null && belongsTo(other.accountId); }

    /** Persist BEFORE sending a control request. A missing order number is allowed only for start. */
    public static PendingUse command(DeviceProfile profile, String accountId, String orderNo,
                                     UseSession.State state, long now) {
        if (profile == null || accountId == null || !accountId.matches("[1-9][0-9]{0,14}")
                || orderNo == null || !(orderNo.isEmpty() || orderNo.matches("[A-Za-z0-9_-]{1,64}"))
                || now < 0 || now >= MAX_CLOCK_SKEW_MILLIS
                || !(state == UseSession.State.STARTING || state == UseSession.State.ACTIVE
                    || state == UseSession.State.STOPPING || state == UseSession.State.SETTLING)
                || (state != UseSession.State.STARTING && orderNo.isEmpty()))
            throw new IllegalArgumentException("Invalid pending command");
        PendingUse record = of(profile, WaterOrder.parse(307, null), now);
        if (record == null) throw new IllegalArgumentException("Invalid pending device");
        record = new PendingUse(record.projectId, record.deviceId, record.serial, orderNo, state.name(), now);
        record.accountId = accountId;
        return record;
    }

    public String projectId() { return projectId; }
    public String deviceId() { return deviceId; }
    /** Only for rebuilding a request; never displayed. */
    public String requestSerial() { return serial; }
    /** Only for a future close/settle call; never displayed. */
    public String orderNo() { return orderNo; }
    public String status() { return status; }
    public long savedAtMillis() { return savedAtMillis; }
    public boolean sameRecord(PendingUse other) {
        return other!=null && projectId.equals(other.projectId) && deviceId.equals(other.deviceId)
            && serial.equals(other.serial) && orderNo.equals(other.orderNo) && status.equals(other.status)
            && accountId.equals(other.accountId) && savedAtMillis==other.savedAtMillis;
    }
    public boolean canDismissUnknown(String owner) {
        return belongsTo(owner) && status.equals("STARTING") && orderNo.isEmpty();
    }
    public boolean mayRetireIdleUnknown(String owner,long now) {
        return canDismissUnknown(owner) && now>=savedAtMillis && now-savedAtMillis>=120000L;
    }

    /** Re-checking is mandatory, so the wording never states a present-tense outcome. */
    public String describe() {
        String when = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new Date(savedAtMillis));
        String state;
        if ("MINE".equals(status)) state = "当时存在本账户未结束的订单";
        else if ("OTHER".equals(status)) state = "当时设备正被其他账户使用";
        else if (isCommand()) state = "有一次用水操作尚未核实完成";
        else state = "当时设备在用，但归属未确认";
        return "本机存有一条未核实的查询记录：\n" + when + " · " + state + "。\n"
            + "该记录只代表过去的查询结果，请重新查询订单状态后再决定，不要直接开水。";
    }

    public byte[] encode(SecretKey key) throws GeneralSecurityException, IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeUTF(projectId); output.writeUTF(deviceId); output.writeUTF(serial);
        output.writeUTF(orderNo); output.writeUTF(status); output.writeLong(savedAtMillis); output.writeUTF(accountId);
        byte[] plain = bytes.toByteArray();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(AAD);
            cipher.updateAAD(new byte[]{VERSION});
            byte[] iv = cipher.getIV();
            if (iv.length != 12) throw new GeneralSecurityException("Unexpected IV length");
            byte[] encrypted = cipher.doFinal(plain);
            ByteArrayOutputStream envelope = new ByteArrayOutputStream();
            envelope.write(VERSION);
            envelope.write(iv);
            envelope.write(encrypted);
            return envelope.toByteArray();
        } finally { Arrays.fill(plain, (byte) 0); }
    }

    /**
     * @throws GeneralSecurityException when the envelope is malformed, tampered with, or carries a
     *         field outside its allowed shape. A record that cannot be trusted is not a record.
     */
    public static PendingUse decode(byte[] envelope, SecretKey key) throws GeneralSecurityException, IOException {
        if (envelope == null || envelope.length < 33 || envelope.length > 4096 || (envelope[0] != 1 && envelope[0] != VERSION)) {
            throw new GeneralSecurityException("Invalid recovery envelope");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, envelope, 1, 12));
        cipher.updateAAD(AAD);
        cipher.updateAAD(new byte[]{envelope[0]});
        byte[] plain = cipher.doFinal(envelope, 13, envelope.length - 13);
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(plain));
            String projectId = input.readUTF(), deviceId = input.readUTF(), serial = input.readUTF();
            String orderNo = input.readUTF(), status = input.readUTF();
            long savedAt = input.readLong();
            String account = envelope[0] == 1 ? "" : input.readUTF();
            if (input.available() != 0) throw new IOException("Unexpected recovery fields");
            if (!projectId.matches("[1-9][0-9]{0,8}") || !deviceId.matches("[1-9][0-9]{0,8}")) throw new IOException("Invalid device identity");
            if (!serial.matches("[A-Za-z0-9_-]{1,64}")) throw new IOException("Invalid serial");
            if (!orderNo.isEmpty() && !orderNo.matches("[A-Za-z0-9_-]{1,64}")) throw new IOException("Invalid order number");
            if (!STATUSES.contains(status)) throw new IOException("Invalid state");
            if (savedAt < 0 || savedAt >= MAX_CLOCK_SKEW_MILLIS) throw new IOException("Invalid timestamp");
            if (!account.isEmpty() && !account.matches("[1-9][0-9]{0,14}")) throw new IOException("Invalid account");
            if (Arrays.asList("STARTING", "ACTIVE", "STOPPING", "SETTLING").contains(status)
                    && (account.isEmpty() || (!status.equals("STARTING") && orderNo.isEmpty())))
                throw new IOException("Incomplete command record");
            PendingUse result = new PendingUse(projectId, deviceId, serial, orderNo, status, savedAt);
            result.accountId = account;
            return result;
        } finally { Arrays.fill(plain, (byte) 0); }
    }

    @Override public String toString() {
        return "PendingUse[device=" + deviceId + ", state=" + status + "]";
    }
}
