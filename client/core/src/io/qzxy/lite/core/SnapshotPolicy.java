package io.qzxy.lite.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which pages may contribute a back-animation snapshot.
 *
 * <p>Leaving a page draws its decor into a Bitmap that the <em>next</em> page shows as the backdrop
 * while the card slides away. That is fine for a timetable, but an account, password or payment page
 * must never end up photographed into the next screen. The list is keyed by simple class name so the
 * rule lives in core where it can be unit-tested; adding a sensitive page is one line here.</p>
 *
 * <p>Unknown and blank names are refused. A name that is not on the list is ordinary content and is
 * allowed; this is a deny-list, because almost every page is safe to snapshot.</p>
 */
public final class SnapshotPolicy {
    private static final Set<String> EXCLUDED = Collections.unmodifiableSet(new HashSet<String>(
        Arrays.asList(
            "QzxyAlipayActivity",   // Alipay handoff / payment entry
            "EduLoginActivity",     // academic password field
            "LifeLoginActivity",    // campus-service login
            "AccountInfoActivity",  // phone / identity
            "EduAccountActivity",   // academic account
            "AccountActivity"       // login entry
        )));

    private SnapshotPolicy() { }

    /** True when a page of this class may be photographed for the back animation. */
    public static boolean allows(String simpleName) {
        if (simpleName == null) return false;
        String name = simpleName.trim().toLowerCase(Locale.US);
        if (name.length() == 0) return false;
        for (String excluded : EXCLUDED) {
            if (name.equals(excluded.toLowerCase(Locale.US))) return false;
        }
        return true;
    }
}
