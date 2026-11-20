package io.qzxy.lite.core;

import java.util.regex.Pattern;

/**
 * The grammar of a release name: {@code X.Y.Z} for a release, {@code X.Y.Z-beta.N} for a preview,
 * plus the four-part {@code X.Y.Z.W} names that older builds were published under.
 *
 * <p>This class answers two questions only: "may we publish under this name" and "does this name
 * belong to the preview channel". It deliberately does not order anything. Text order is wrong for
 * versions in general (2.10.0 sorts before 2.9.0) and meaningless across channels (2.5.13-beta.2
 * versus 2.5.13), so which build is newer is decided by the integer {@code versionCode} and by
 * nothing else — see {@link UpdateChannel}.</p>
 *
 * <p>The four-part form is accepted, not recommended: 2.5.6.11 and 2.5.8.1 shipped, they are still
 * installed, and their manifests must keep parsing. New releases use three parts; a preview adds
 * {@code -beta.N} with N counting from 1 and no leading zero.</p>
 */
public final class ReleaseVersion {
    /** X.Y or X.Y.Z — a published release. Two parts survives from 2.3-era manifests. */
    private static final Pattern RELEASE = Pattern.compile("[0-9]{1,2}(\\.[0-9]{1,2}){1,2}");
    /** X.Y.Z.W — the retired naming; older manifests still carry it. */
    private static final Pattern LEGACY = Pattern.compile("[0-9]{1,2}(\\.[0-9]{1,2}){3}");
    /** X.Y.Z-beta.N — a preview build. N starts at 1; leading zeroes are not a counter. */
    private static final Pattern BETA = Pattern.compile("[0-9]{1,2}(\\.[0-9]{1,2}){2}-beta\\.[1-9][0-9]{0,2}");

    private ReleaseVersion() { }

    /** True for any name a manifest may legitimately carry, including the retired four-part form. */
    public static boolean isWellFormed(String name) {
        String value = clean(name);
        return value != null
            && (RELEASE.matcher(value).matches() || LEGACY.matcher(value).matches() || BETA.matcher(value).matches());
    }

    /** True for {@code X.Y.Z-beta.N}: the build belongs to the preview channel. */
    public static boolean isBeta(String name) {
        String value = clean(name);
        return value != null && BETA.matcher(value).matches();
    }

    /** True for a current-form release name, {@code X.Y.Z} (or the older two-part {@code X.Y}). */
    public static boolean isRelease(String name) {
        String value = clean(name);
        return value != null && RELEASE.matcher(value).matches();
    }

    /** True for the retired four-part form, which new builds must not use. */
    public static boolean isLegacy(String name) {
        String value = clean(name);
        return value != null && LEGACY.matcher(value).matches();
    }

    private static String clean(String name) {
        if (name == null) return null;
        String value = name.trim();
        return value.isEmpty() ? null : value;
    }
}
