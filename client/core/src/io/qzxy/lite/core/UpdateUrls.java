// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * The network rules an update download obeys. Kept in core, with no Android imports, so the exact
 * same checks run in the JVM tests and in the app — a rule that is only tested by hand is a rule
 * that will quietly regress.
 *
 * <p>Redirects are followed one hop at a time by the caller. Every hop must stay on HTTPS, may not
 * revisit a URL, is capped, and must not carry the previous host's query parameters (notably
 * sharecode) to a new host. A query written in the Location header itself is normally the next
 * host's own parameter — GitHub signs its release CDN hop that way — and is preserved, except for
 * keys inherited from the old host. A legitimate CDN hop is fine; a silent downgrade to HTTP or a
 * loop is not.</p>
 */
public final class UpdateUrls {
    public static final int MAX_REDIRECTS = 5;

    private UpdateUrls() { }

    /** HTTPS only, with an explicit host and no embedded credentials. */
    public static void requireHttps(String address) {
        URI uri = parse(address);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("not an https url");
    }

    /**
     * Resolves one redirect hop and applies the traversal rules. Returns the absolute next URL.
     * The caller seeds {@code seen} with {@link #canonical(String)} of the first address and passes
     * the hop index, starting at zero.
     */
    public static String next(String current, String location, Set<String> seen, int hop) {
        if (hop >= MAX_REDIRECTS) throw new IllegalArgumentException("too many redirects");
        if (location == null || location.trim().isEmpty()) throw new IllegalArgumentException("empty redirect");
        URI base = parse(current);
        URI target;
        try {
            target = base.resolve(location.trim());
        } catch (IllegalArgumentException bad) {
            throw new IllegalArgumentException("bad redirect");
        }
        requireHttps(target.toString());
        if (!seen.add(canonical(target))) throw new IllegalArgumentException("redirect loop");
        // Preserve the new host's own parameters (GitHub's CDN signature), but never forward a key
        // that came from the previous host (sharecode), even if the redirect echoes it back.
        if (!host(base.toString()).equals(host(target.toString())) && base.getRawQuery() != null
                && target.getRawQuery() != null) {
            String kept = withoutKeys(target.getRawQuery(), base.getRawQuery());
            try {
                target = new URI(target.getScheme(), target.getUserInfo(), target.getHost(),
                    target.getPort(), target.getPath(), kept, target.getFragment());
            } catch (URISyntaxException impossible) {
                throw new IllegalArgumentException("bad redirect");
            }
        }
        return target.toString();
    }

    /** Removes from {@code query} every parameter whose key appears in {@code inherited}. */
    private static String withoutKeys(String query, String inherited) {
        String[] foreign = inherited.split("&");
        StringBuilder out = new StringBuilder();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            String key = pair.split("=", 2)[0];
            boolean repeated = false;
            for (String old : foreign) {
                if (!old.isEmpty() && old.split("=", 2)[0].equals(key)) { repeated = true; break; }
            }
            if (repeated) continue;
            if (out.length() > 0) out.append('&');
            out.append(pair);
        }
        return out.length() == 0 ? null : out.toString();
    }

    public static String host(String address) {
        return parse(address).getHost().toLowerCase(Locale.US);
    }

    /** Scheme + host + port + path. Query and fragment do not make a distinct hop. */
    public static String canonical(String address) {
        return canonical(parse(address));
    }

    private static String canonical(URI uri) {
        StringBuilder out = new StringBuilder(uri.getScheme().toLowerCase(Locale.US)).append("://")
            .append(uri.getHost().toLowerCase(Locale.US));
        if (uri.getPort() >= 0) out.append(':').append(uri.getPort());
        out.append(uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath());
        return out.toString();
    }

    private static URI parse(String address) {
        try {
            URI uri = new URI(address);
            if (uri.getScheme() == null || uri.getHost() == null)
                throw new IllegalArgumentException("bad url");
            return uri;
        } catch (URISyntaxException bad) {
            throw new IllegalArgumentException("bad url");
        }
    }
}
