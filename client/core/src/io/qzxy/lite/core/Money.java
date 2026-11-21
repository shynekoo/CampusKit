// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.util.Locale;

/**
 * Money conversion for the order protocol.
 *
 * Integer amount fields in the 6.5.28 models (perMoney, upMoney, upLeadMoney) are thousandths of a
 * yuan and the original app renders them through wi.a.c(). Device-level fields such as withholdMoney
 * are already decimal-yuan strings and must never pass through here.
 */
public final class Money {
    private Money() { }

    /** @return "12.34" for 12340, or "" when the input is not a plain non-negative integer */
    public static String fromThousandths(String raw) {
        if (raw == null || !raw.matches("[0-9]{1,9}")) return "";
        return fromThousandths(Long.parseLong(raw));
    }

    /** Rounds half up to two decimals; 999 -> "1.00", 5 -> "0.01". */
    public static String fromThousandths(long value) {
        long hundredths = (value + 5) / 10;
        return String.format(Locale.ROOT, "%d.%02d", hundredths / 100, hundredths % 100);
    }
}
