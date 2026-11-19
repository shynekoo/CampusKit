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
 * revisit a URL, is capped, and must not carry the manifest's query (notably {@code sharecode}) to
 * a host that did not issue it. A legitimate CDN hop is fine; a silent downgrade to HTTP or a loop
 * is not.</p>
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
        // Do not hand the manifest's query (notably sharecode) to an unrelated host.
        if (!host(base.toString()).equals(host(target.toString())) && target.getRawQuery() != null) {
            try {
                target = new URI(target.getScheme(), target.getUserInfo(), target.getHost(),
                    target.getPort(), target.getPath(), null, target.getFragment());
            } catch (URISyntaxException impossible) {
                throw new IllegalArgumentException("bad redirect");
            }
        }
        return target.toString();
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
