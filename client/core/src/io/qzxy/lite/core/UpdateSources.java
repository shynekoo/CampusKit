// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/**
 * The fixed addresses of the three update hosts. Kept in core, with no Android imports, so the
 * same list is compiled into the JVM tests and the app — a source the app trusts but the tests do
 * not know about is a source nobody has checked.
 *
 * <p>Each host carries the same two manifests: the release list and the preview list. WorkBuddy is
 * the default because existing installs already trust it and it is the fastest in mainland China;
 * GitHub stays available when the WorkBuddy site's owning account changes; Gitee exists because
 * GitHub's release CDN is slow to reach from mainland networks, while a public Gitee repository
 * serves release attachments from {@code foruda.gitee.com} without a login.
 *
 * <p>Neither GitHub nor Gitee offers a "latest" download route that also covers a prerelease, so
 * both use fixed tags: GitHub keeps releases on {@code latest} and previews on a fixed
 * {@code preview} tag; Gitee keeps both on the fixed {@code release} and {@code preview} tags and
 * replaces the asset inside them. A manifest's {@code url} must therefore name the fixed tag and a
 * fixed file name, never a versioned tag. The WorkBuddy host needs its {@code sharecode} on every
 * read, and that query must never be carried across a redirect to another host — {@link UpdateUrls}
 * enforces that hop by hop. Gitee's own hop to {@code foruda.gitee.com} carries a
 * {@code token}/{@code ts} pair written by the Location header itself, so it survives unchanged.</p>
 *
 * <p>The Gitee repository must be <b>public</b>. A private one answers {@code 403} to an anonymous
 * read of the attachment, which the app can only report as "this channel has nothing" — and the
 * create-repo API has been observed to leave a repository private even when the request says
 * {@code private: false}, so the visibility has to be confirmed from the repo page, not assumed
 * from the creation call.</p>
 */
public final class UpdateSources {
    public static final String DEFAULT = "workbuddy";

    public static final String GITHUB_RELEASE =
        "https://github.com/shynekoo/CampusKit/releases/latest/download/update.json";
    public static final String GITHUB_BETA =
        "https://github.com/shynekoo/CampusKit/releases/download/preview/beta.json";
    public static final String GITEE_RELEASE =
        "https://gitee.com/sun-heyue/CampusKit/releases/download/release/update.json";
    public static final String GITEE_BETA =
        "https://gitee.com/sun-heyue/CampusKit/releases/download/preview/beta.json";
    public static final String WORKBUDDY_RELEASE =
        "https://wbapp-g8yy3klci8kmeufm6vdn0h.app.workbuddy.host/update.json?sharecode=BZD63HRP4B";
    public static final String WORKBUDDY_BETA =
        "https://wbapp-g8yy3klci8kmeufm6vdn0h.app.workbuddy.host/beta.json?sharecode=BZD63HRP4B";

    /**
     * The hosts, in the order the settings page shows them. The first one is the WorkBuddy-hosted
     * site, shown to readers as 国内 because that is what it means to them: the domestic mirror.
     * The stored key stays {@code workbuddy} so existing installs keep their choice.
     */
    public static final String[] HOSTS = {"国内", "GitHub", "Gitee"};

    /** The stored key of each host, index-aligned with {@link #HOSTS}. */
    public static final String[] KEYS = {"workbuddy", "github", "gitee"};

    private UpdateSources() { }

    /** @return the stored key for a settings-list index, falling back to the default. */
    public static String keyOf(int index) {
        return index >= 0 && index < KEYS.length ? KEYS[index] : DEFAULT;
    }

    /** @return the settings-list index of a stored key, falling back to the default host. */
    public static int indexOf(String source) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(source)) return i;
        }
        return 0;
    }

    /** An unknown or absent value resolves to the default rather than to an untested host. */
    public static String normalize(String source) {
        for (String key : KEYS) {
            if (key.equals(source)) return key;
        }
        return DEFAULT;
    }

    /** @return the two manifest addresses for a saved source name, never null. */
    public static String[] manifests(String source) {
        String key = normalize(source);
        if ("github".equals(key))
            return new String[]{GITHUB_RELEASE, GITHUB_BETA};
        if ("gitee".equals(key))
            return new String[]{GITEE_RELEASE, GITEE_BETA};
        return new String[]{WORKBUDDY_RELEASE, WORKBUDDY_BETA};
    }

    /** WorkBuddy is the default; an unknown or absent value must resolve to it. */
    public static boolean isWorkBuddy(String source) { return "workbuddy".equals(normalize(source)); }
}
