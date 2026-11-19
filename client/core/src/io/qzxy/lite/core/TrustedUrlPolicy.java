package io.qzxy.lite.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a URL may be opened inside an in-app WebView.
 *
 * <p>Pure Java on purpose. The WebView itself is Android code, but "is this really our school over
 * HTTPS" is a security rule, and security rules need tests that run without a device. Each caller
 * supplies its own approved host list, so the public school site does not silently inherit the
 * trust of the academic portal (or vice versa).</p>
 *
 * <p>Host matching happens on label boundaries: {@code hebeu.edu.cn.evil.test} is not a subdomain
 * of {@code hebeu.edu.cn}. Backslashes and userinfo are rejected because that is where a browser's
 * idea of the host and the parser's idea of the host tend to part company.</p>
 */
public final class TrustedUrlPolicy {
    private final Set<String> hosts;
    private final Set<Integer> ports;

    /** @param hosts approved host suffixes, lower-case comparison; @param ports approved explicit ports. */
    public TrustedUrlPolicy(Collection<String> hosts, Collection<Integer> ports) {
        Set<String> approvedHosts = new HashSet<String>();
        if (hosts != null) {
            for (String host : hosts) {
                if (host != null && host.length() > 0) approvedHosts.add(host.toLowerCase(Locale.US));
            }
        }
        Set<Integer> approvedPorts = new HashSet<Integer>();
        if (ports != null) {
            for (Integer port : ports) if (port != null && port.intValue() > 0) approvedPorts.add(port);
        }
        this.hosts = Collections.unmodifiableSet(approvedHosts);
        this.ports = Collections.unmodifiableSet(approvedPorts);
    }

    /** True only for an HTTPS URL on an approved host and port, with no userinfo or parser tricks. */
    public boolean allowsInApp(String url) {
        URI uri = parse(url);
        if (uri == null) return false;
        if (!"https".equals(lower(uri.getScheme()))) return false;
        if (!trusted(lower(uri.getHost()))) return false;
        int port = uri.getPort();
        return port == -1 || ports.contains(Integer.valueOf(port));
    }

    /** Host-level check regardless of scheme: whether a link is still "our school" at all. */
    public boolean isSchoolUrl(String url) {
        URI uri = parse(url);
        return uri != null && trusted(lower(uri.getHost()));
    }

    private boolean trusted(String host) {
        if (host == null || host.length() == 0) return false;
        for (String suffix : hosts) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
        }
        return false;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.US);
    }

    private static URI parse(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.length() == 0 || text.indexOf('\\') >= 0) return null;
        try {
            URI uri = new URI(text);
            if (uri.getRawUserInfo() != null || uri.getHost() == null) return null;
            return uri;
        } catch (URISyntaxException invalid) {
            return null;
        }
    }
}
