package io.qzxy.lite.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The release manifest read at start-up: which version is current, what changed, and where the
 * package lives.
 *
 * <p>The manifest is an ordinary public file that anybody can edit, so nothing in it is trusted.
 * Every field is checked here — the version must look like a version, the download must be HTTPS,
 * the note list must be short enough to read in a dialog — and a manifest that fails any check is
 * treated exactly like a missing one: the app skips the update quietly rather than showing a
 * half-filled announcement. {@link #parse} is the only way in, and it is deliberately strict about
 * trailing commas, unquoted keys and junk after the object, because a manifest written by hand
 * across several releases is exactly where those mistakes appear.</p>
 *
 * <p>Comparison uses the version <em>code</em>, never the version name: "2.10" sorts before "2.9"
 * as text, and a downgrade install is refused by the system anyway, so an integer is the only thing
 * that answers "is this newer than what is installed".</p>
 */
public final class UpdateManifest {
    /** Enough for a release summary; more than this is patch notes nobody reads in a dialog. */
    private static final int MAX_NOTES = 8, MAX_NOTE = 160, MAX_URL = 512, MAX_NAME = 24;
    private static final Pattern VERSION = Pattern.compile("[0-9]{1,2}(\\.[0-9]{1,2}){1,2}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    /**
     * HTTPS only, and no credentials in the URL: the APK is installed with the app's own authority,
     * so a plain-HTTP download would let anyone on the same Wi-Fi substitute a package.
     */
    private static final Pattern URL_PATTERN =
        Pattern.compile("https://[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?(:[0-9]{1,5})?(/[A-Za-z0-9._~%!$&'()*+,;=:@/-]*)?(\\?[A-Za-z0-9._~%!$&'()*+,;=:@/?-]*)?");

    public final String version;
    public final int versionCode;
    public final List<String> notes;
    public final String url;
    /** Package size in bytes. Required so an oversized download can be stopped mid-stream. */
    public final long bytes;
    /** Lower-case hex digest of the package. Required so it is verified before install. */
    public final String sha256;

    private UpdateManifest(String version, int versionCode, List<String> notes, String url, long bytes, String sha256) {
        this.version = version;
        this.versionCode = versionCode;
        this.notes = Collections.unmodifiableList(notes);
        this.url = url;
        this.bytes = bytes;
        this.sha256 = sha256;
    }

    /** True when this release is a genuine upgrade over what the calling install reports. */
    public boolean newerThan(int installedVersionCode) {
        return versionCode > installedVersionCode;
    }

    public static UpdateManifest parse(String json) {
        if (json == null) throw new IllegalArgumentException("empty manifest");
        Object root = new Reader(json).document();
        if (!(root instanceof Map)) throw new IllegalArgumentException("manifest is not an object");
        @SuppressWarnings("unchecked") Map<String, Object> object = (Map<String, Object>) root;

        String version = text(object, "version", MAX_NAME);
        if (!VERSION.matcher(version).matches()) throw new IllegalArgumentException("bad version");
        int code = count(object, "versionCode");
        if (code < 1 || code > 2000000000) throw new IllegalArgumentException("bad versionCode");
        String url = text(object, "url", MAX_URL);
        if (!URL_PATTERN.matcher(url).matches() || url.contains("..")) throw new IllegalArgumentException("bad url");

        List<String> notes = new ArrayList<String>();
        Object listed = object.get("notes");
        if (!(listed instanceof List)) throw new IllegalArgumentException("notes is not an array");
        @SuppressWarnings("unchecked") List<Object> raw = (List<Object>) listed;
        for (Object entry : raw) {
            if (!(entry instanceof String)) throw new IllegalArgumentException("note is not a string");
            String note = ((String) entry).trim();
            // A blank line is a formatting choice, not an error: the writer may space the list out.
            if (note.isEmpty()) continue;
            if (note.length() > MAX_NOTE || note.indexOf('\n') >= 0) throw new IllegalArgumentException("long note");
            notes.add(note);
        }
        if (notes.isEmpty() || notes.size() > MAX_NOTES) throw new IllegalArgumentException("bad notes");

        // A manifest without a digest or a size cannot be verified before install. Require both:
        // an attacker who can swap the file can otherwise point at any package and skip the check.
        long bytes = count(object, "bytes");
        if (bytes < 1) throw new IllegalArgumentException("bad bytes");
        String digest = text(object, "sha256", 64);
        digest = digest.toLowerCase(Locale.US);
        if (!SHA256.matcher(digest).matches()) throw new IllegalArgumentException("bad sha256");
        return new UpdateManifest(version, code, notes, url, bytes, digest);
    }

    private static String text(Map<String, Object> object, String key, int max) {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException("missing " + key);
        String text = ((String) value).trim();
        if (text.isEmpty() || text.length() > max) throw new IllegalArgumentException("bad " + key);
        return text;
    }

    private static int count(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Double)) throw new IllegalArgumentException("missing " + key);
        double number = (Double) value;
        if (number != Math.floor(number) || number < 0) throw new IllegalArgumentException("bad " + key);
        return (int) number;
    }

    /**
     * A JSON reader for exactly what a manifest can contain: an object of strings, integers, string
     * arrays and nulls. Keeping it here rather than pulling in a parser is the same trade the rest
     * of the core makes — small, testable, no dependency to audit — and it lets the reader reject
     * the sloppy forms (trailing commas, single quotes, comments) instead of silently repairing them.
     */
    private static final class Reader {
        private final String text;
        private int at;

        Reader(String text) { this.text = text; }

        Object document() {
            Object value = value();
            space();
            if (at != text.length()) throw new IllegalArgumentException("trailing content");
            return value;
        }

        private Object value() {
            space();
            if (at >= text.length()) throw new IllegalArgumentException("unexpected end");
            char c = text.charAt(at);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't') { literal("true"); return Boolean.TRUE; }
            if (c == 'f') { literal("false"); return Boolean.FALSE; }
            if (c == 'n') { literal("null"); return null; }
            return number();
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            at++; space();
            if (peek() == '}') { at++; return map; }
            while (true) {
                space();
                if (peek() != '"') throw new IllegalArgumentException("unquoted key");
                String key = string();
                space();
                if (peek() != ':') throw new IllegalArgumentException("missing colon");
                at++;
                map.put(key, value());
                space();
                char c = peek();
                if (c == ',') { at++; continue; }
                if (c == '}') { at++; return map; }
                throw new IllegalArgumentException("bad object");
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<Object>();
            at++; space();
            if (peek() == ']') { at++; return list; }
            while (true) {
                list.add(value());
                space();
                char c = peek();
                if (c == ',') { at++; continue; }
                if (c == ']') { at++; return list; }
                throw new IllegalArgumentException("bad array");
            }
        }

        private String string() {
            at++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (at >= text.length()) throw new IllegalArgumentException("unterminated string");
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (at >= text.length()) throw new IllegalArgumentException("dangling escape");
                char escape = text.charAt(at++);
                switch (escape) {
                    case '"': case '\\': case '/': out.append(escape); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'u':
                        if (at + 4 > text.length()) throw new IllegalArgumentException("short escape");
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        at += 4; break;
                    default: throw new IllegalArgumentException("bad escape");
                }
            }
        }

        private Double number() {
            int start = at;
            if (peek() == '-') at++;
            while (at < text.length() && "0123456789.eE+-".indexOf(text.charAt(at)) >= 0) at++;
            if (start == at) throw new IllegalArgumentException("not a value");
            return Double.valueOf(text.substring(start, at));
        }

        private void literal(String word) {
            if (!text.startsWith(word, at)) throw new IllegalArgumentException("bad literal");
            at += word.length();
        }

        private char peek() {
            if (at >= text.length()) throw new IllegalArgumentException("unexpected end");
            return text.charAt(at);
        }

        private void space() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
        }
    }
}
