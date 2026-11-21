// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever a 慧生活798 device sticker encodes into something the API can use.
 *
 * <p>Stickers carry two different things and they must not be confused:</p>
 * <ul>
 *   <li>a <b>device id</b> — usable directly as {@code did};</li>
 *   <li>a <b>qr ticket</b> — an opaque token that only {@code GET /api/v1/qr/use?id=} can turn into a
 *       device id (and that also says what kind of thing the code points at).</li>
 * </ul>
 *
 * <p>The first version of this class treated every {@code ?id=} as a device id, and explicitly
 * rejected {@code https://i.ilife798.com/q/1/billing?id=a}. That link is exactly what the current
 * 慧生活 stickers and device screens produce, so scanning stopped working after the official app
 * update while nothing about our camera changed. The rules below follow the third-party clients
 * {@code ilife798/ilife798} ({@code DeviceLinkParser}) and {@code nocookies111/life-798}
 * ({@code DeviceIdParser}), both MIT, which are kept current against the live service:</p>
 *
 * <pre>
 *   https://host/q/&lt;n&gt;/{b|s|billing|vip|wallet}?id=&lt;ticket&gt;   -&gt; qr ticket
 *   https://host/q/&lt;n&gt;/bm?/&lt;ticket&gt;                          -&gt; qr ticket (prefix kept verbatim)
 *   https://host/q/&lt;n&gt;/&lt;device id&gt;                           -&gt; device id
 *   ilife798s://i/device/{nav|nfc}?id=&lt;device id&gt;             -&gt; device id (NFC tags)
 *   &lt;digits&gt;                                                  -&gt; device id
 * </pre>
 *
 * <p>Older shapes seen on real stickers ({@code did=}, {@code ?q=}, {@code /device/&lt;id&gt;},
 * {@code hnkzy.com}, JSON payloads, {@code did:} prefixes) stay supported as device ids.</p>
 */
public final class Life798Qr {
    /** What the sticker meant: exactly one of the two fields is set. */
    public static final class Scan {
        /** Ready to use as {@code did}. */
        public final String deviceId;
        /** Needs {@code GET /api/v1/qr/use?id=} before it means anything. */
        public final String qrId;

        private Scan(String deviceId, String qrId) {
            this.deviceId = deviceId;
            this.qrId = qrId;
        }

        public boolean isDeviceId() { return deviceId != null; }
    }

    /** {@code /q/<n>/<kind>?id=<ticket>}: the ticket is not a device id. */
    private static final Pattern QR_TICKET = Pattern.compile(
        "^https?://[^/?#]+(?:/[^?#]*)?/q/\\d+/(?:b|s|billing|vip|wallet)\\?id=([^&#]+)(?:[&#].*)?$",
        Pattern.CASE_INSENSITIVE);
    /** {@code /q/<n>/bm?/<ticket>}: everything after {@code q/<n>/} is the ticket. */
    private static final Pattern BM_TICKET = Pattern.compile(
        "^https?://[^/?#]+(?:/[^?#]*)?/q/\\d+/(bm\\?/.+)$", Pattern.CASE_INSENSITIVE);
    /** {@code /q/<n>/<device id>}. */
    private static final Pattern Q_PLAIN = Pattern.compile(
        "^https?://[^/?#]+(?:/[^?#]*)?/q/\\d+/([A-Za-z0-9_-]+)(?:[?#].*)?$", Pattern.CASE_INSENSITIVE);
    /** NFC deep link written by the official app. */
    private static final Pattern APP_DEEP_LINK = Pattern.compile(
        "^ilife798s?://(?:i/)?(?:device/)?(?:nav|nfc)\\?id=([^&#]+)(?:[&#].*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern DIGITS = Pattern.compile("\\d{1,32}");
    /** Path segments whose neighbour is the device id. */
    private static final String[] MARKERS = {"device", "devices", "did", "bind", "binding", "q", "qr"};
    private static final Pattern DEVICE_KEY = Pattern.compile(
        "(?:[?&#;,\\s{]|^)[\"']?(?:did|deviceId|device_id|q)[\"']?\\s*(?:=|:)\\s*[\"']?([^&#\"'\\s]+)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern TICKET_KEY = Pattern.compile(
        "(?:[?&#;,\\s{]|^)[\"']?id[\"']?\\s*(?:=|:)\\s*[\"']?([^&#\"'\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRONG = Pattern.compile("\\d{8,32}");
    private static final Pattern FLEXIBLE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{5,63}");
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    /** A ticket may arrive percent-encoded, so {@code %} and the sub-delims must survive validation. */
    private static final Pattern TICKET = Pattern.compile("[A-Za-z0-9_%+./=~?-]{1,300}");

    private Life798Qr() { }

    public static Scan parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("二维码内容为空");
        String value = raw.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("二维码内容为空");
        if (value.length() > 1024) throw new IllegalArgumentException("二维码内容过长");
        for (int i = 0; i < value.length(); i++)
            if (Character.isISOControl(value.charAt(i))) throw new IllegalArgumentException("二维码包含无效字符");

        if (DIGITS.matcher(value).matches()) return device(value);

        String deep = group(APP_DEEP_LINK, value);
        if (deep != null) return device(tidy(deep));

        String ticket = group(QR_TICKET, value);
        if (ticket != null) return asTicket(tidy(ticket));

        String bm = group(BM_TICKET, value);
        if (bm != null) return asTicket(tidy(bm));

        String plain = group(Q_PLAIN, value);
        if (plain != null) return device(tidy(plain));

        String keyed = first(DEVICE_KEY.matcher(value));
        if (keyed != null) return device(tidy(keyed));

        String keyedTicket = first(TICKET_KEY.matcher(value));
        if (keyedTicket != null) return asTicket(tidy(keyedTicket));

        String fromPath = fromPath(value);
        if (fromPath != null) return device(fromPath);

        // A bare word with a digit is how the older stickers printed the id under the code.
        return device(tidy(value));
    }

    private static Scan device(String candidate) {
        if (candidate == null || !DEVICE_ID.matcher(candidate).matches() || !hasDigit(candidate))
            throw new IllegalArgumentException("这个二维码里没有设备编号，可能不是慧生活设备码");
        return new Scan(candidate, null);
    }

    private static Scan asTicket(String candidate) {
        if (candidate == null || !TICKET.matcher(candidate).matches())
            throw new IllegalArgumentException("这个二维码里的识别码格式不受支持，可能不是慧生活设备码");
        return new Scan(null, candidate);
    }

    private static boolean hasDigit(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isDigit(value.charAt(i))) return true;
        return false;
    }

    private static String group(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static String first(Matcher matcher) {
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Path rules: hnkzy hosts, a marker segment, or a trailing numeric id. */
    private static String fromPath(String value) {
        URI uri;
        try { uri = new URI(value); } catch (Exception notUrl) { return null; }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme) || "ilife".equals(scheme)
            || "ilife798".equals(scheme) || "ilife798s".equals(scheme))) return null;
        String path = uri.getPath();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        List<String> segments = new ArrayList<>();
        // ilife://device/<id> puts the marker in the authority, so keep the host as a segment too;
        // for http(s) a hostname never looks like a marker, so this changes nothing there.
        if (!host.isEmpty()) segments.add(host);
        if (path != null) for (String part : path.split("/")) {
            String decoded = decode(part).trim();
            if (!decoded.isEmpty()) segments.add(decoded);
        }
        if (segments.size() <= 1) return null;

        if (host.equals("hnkzy.com") || host.endsWith(".hnkzy.com")) {
            for (int i = segments.size() - 1; i >= 0; i--)
                if (STRONG.matcher(segments.get(i)).matches()) return segments.get(i);
        }
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (!isMarker(segments.get(i))) continue;
            for (int j = segments.size() - 1; j > i; j--) {
                String part = segments.get(j);
                if (FLEXIBLE.matcher(part).matches() && hasDigit(part)) return part;
            }
        }
        String last = segments.get(segments.size() - 1);
        return STRONG.matcher(last).matches() ? last : null;
    }

    private static boolean isMarker(String segment) {
        String lower = segment.toLowerCase(Locale.ROOT);
        for (String marker : MARKERS) if (marker.equals(lower)) return true;
        return false;
    }

    private static String tidy(String value) {
        String text = value.trim();
        while (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\""))
            || (text.startsWith("'") && text.endsWith("'"))))
            text = text.substring(1, text.length() - 1).trim();
        if (text.toLowerCase(Locale.ROOT).startsWith("did:")) text = text.substring(4);
        return text.length() > 300 ? text.substring(0, 300) : text;
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, "UTF-8"); } catch (Exception plain) { return value; }
    }
}
