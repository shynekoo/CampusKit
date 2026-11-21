// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/**
 * Deep links for 菜鸟驿站's 身份码, the code a station scans when you pick a parcel up.
 *
 * <p>菜鸟裹裹 used to own that page ({@code cainiao://desktop/station_code}, which it still answers).
 * The page the station now accepts lives on Taobao's side: 淘宝 → 菜鸟驿站 → 身份码, an H5 page that
 * only Taobao can render because it needs the Taobao session. Taobao opens its own pages through the
 * {@code tbopen} bridge, not through a plain https intent, so the H5 address has to be percent-encoded
 * into {@code h5Url}. The addresses here follow the MIT-licensed {@code yimeifun/PickTool}
 * ({@code Destination.TAOBAO}, checked 2026-10), which is maintained against the current apps.
 */
public final class PickupCodeLinks {
    private PickupCodeLinks() { }

    /** 淘宝's 身份码 page. The 1011717 app id and the last-mile path are Taobao's own, not ours. */
    public static final String TAOBAO_IDENTITY_H5 =
        "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/"
            + "end-collect-platform/identity-code?x-ssr=true";

    /** 菜鸟裹裹's own 身份码 page; kept for phones where the station still accepts its code. */
    public static final String CAINIAO_IDENTITY = "cainiao://desktop/station_code";

    /** Both schemes are registered by Taobao; older builds only answer the second one. */
    public static String[] taobaoBridges() {
        String encoded = encode(TAOBAO_IDENTITY_H5);
        return new String[] {
            "taobao://m.taobao.com/tbopen/index.html?h5Url=" + encoded,
            "tbopen://m.taobao.com/tbopen/index.html?h5Url=" + encoded
        };
    }

    /**
     * RFC 3986 percent encoding over UTF-8, leaving only the unreserved characters alone. Taobao's
     * bridge decodes {@code h5Url} once, so {@code : / ? =} must arrive encoded; a {@code +} must not
     * appear, because the bridge reads it as a space rather than as part of the URL.
     */
    static String encode(String value) {
        StringBuilder out = new StringBuilder(value.length() * 3);
        for (byte b : value.getBytes(java.nio.charset.Charset.forName("UTF-8"))) {
            int c = b & 0xff;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~';
            if (unreserved) {
                out.append((char) c);
            } else {
                out.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0f]);
            }
        }
        return out.toString();
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
}
