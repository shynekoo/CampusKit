package io.qzxy.lite.core;

/**
 * The fixed addresses of the two update hosts. Kept in core, with no Android imports, so the same
 * list is compiled into the JVM tests and the app — a source the app trusts but the tests do not
 * know about is a source nobody has checked.
 *
 * <p>Each host carries the same two manifests: the release list and the preview list. WorkBuddy is
 * the default because existing installs already trust it; GitHub is the alternative that stays
 * available when the WorkBuddy site's owning account changes. The preview list on GitHub lives on
 * a fixed {@code preview} tag because GitHub excludes prereleases from {@code releases/latest}.
 * The WorkBuddy host needs its {@code sharecode} on every read, and that query must never be carried
 * across a redirect to another host — {@link UpdateUrls} enforces that hop by hop.</p>
 */
public final class UpdateSources {
    public static final String DEFAULT = "workbuddy";

    public static final String GITHUB_RELEASE =
        "https://github.com/shynekoo/CampusKit/releases/latest/download/update.json";
    public static final String GITHUB_BETA =
        "https://github.com/shynekoo/CampusKit/releases/download/preview/beta.json";
    public static final String WORKBUDDY_RELEASE =
        "https://wbapp-g8yy3klci8kmeufm6vdn0h.app.workbuddy.host/update.json?sharecode=BZD63HRP4B";
    public static final String WORKBUDDY_BETA =
        "https://wbapp-g8yy3klci8kmeufm6vdn0h.app.workbuddy.host/beta.json?sharecode=BZD63HRP4B";

    /** The two hosts, in the order the settings page shows them. */
    public static final String[] HOSTS = {"WorkBuddy", "GitHub"};

    private UpdateSources() { }

    /** @return the two manifest addresses for a saved source name, never null. */
    public static String[] manifests(String source) {
        if ("github".equals(source))
            return new String[]{GITHUB_RELEASE, GITHUB_BETA};
        return new String[]{WORKBUDDY_RELEASE, WORKBUDDY_BETA};
    }

    /** WorkBuddy is the default; an unknown or absent value must resolve to it. */
    public static boolean isWorkBuddy(String source) { return !"github".equals(source); }
}
