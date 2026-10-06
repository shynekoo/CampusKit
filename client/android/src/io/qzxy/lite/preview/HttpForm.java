package io.qzxy.lite.preview;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Shared form transport. It deliberately knows no routes: each API class keeps its own explicit
 * whitelist, so a new endpoint can never be reached by reusing another feature's list. Requests are
 * never retried, responses are size-capped, and error text is truncated before it reaches the UI.
 */
final class HttpForm {
    private static final int TIMEOUT_MS = 15000, MAX_RESPONSE_BYTES = 262144, MAX_MESSAGE = 160;
    private HttpForm() { }
    static final class SessionExpired extends IOException {
        SessionExpired(){super("登录已失效，请重新登录");}
    }
    static final class ResponseFailure extends IOException {
        private static final long serialVersionUID = 1L;
        ResponseFailure(String message) { super(message); }
    }
    interface TestTransport {
        JSONObject request(String path, String form, boolean post) throws IOException, JSONException;
    }
    static volatile TestTransport testTransport;

    static JSONObject request(String origin, String path, String form, boolean post) throws IOException, JSONException {
        // javac removes this branch from delivery builds. Test manifests also lack INTERNET.
        if (BuildFlags.DEVICE_TESTS) {
            if (testTransport == null) throw new IOException("Offline test transport not installed");
            return testTransport.request(path, form, post);
        }
        URL url = new URL(origin + path + (post ? "" : "?" + form));
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Qingyong/0.5 (Android)");
        try {
            if (post) {
                byte[] body = form.getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            }
            int status = connection.getResponseCode();
            if(status==401)throw new SessionExpired();
            if (status != 200) throw new ResponseFailure("服务器返回 HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (bytes.size() + read > MAX_RESPONSE_BYTES) throw new IOException("服务器响应过大");
                    bytes.write(buffer, 0, read);
                }
                return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }

    static int responseCode(JSONObject reply) throws JSONException {
        Object value = reply.has("errorCode") ? reply.get("errorCode") : reply.get("code");
        if (!(value instanceof Number) && !(value instanceof String)) throw new JSONException("Missing response code");
        String raw = String.valueOf(value);
        if (!raw.matches("-?[0-9]{1,9}")) throw new JSONException("Invalid response code");
        return Integer.parseInt(raw);
    }

    static boolean failed(JSONObject reply, int code) throws JSONException {
        return code != 0 || (reply.has("success") && !reply.getBoolean("success"));
    }

    /** Bounded, control-character-free rejection text; never echoes the request body. */
    static IOException rejection(JSONObject reply, int code) {
        String message = reply.optString("errorMessage", reply.optString("message", "请求未成功"));
        // Original sj.f.b recognises 8/10000. 10000 also means a migrated device channel,
        // so only classify that overloaded code when the message explicitly describes login.
        if(code==8 || code==10000 && (message.contains("登录失效")||message.contains("登录已失效")
            ||message.contains("重新登录")||message.contains("登录过期")||message.contains("登录已过期")
            ||message.contains("其他设备登录")||message.contains("异地登录")))return new SessionExpired();
        message = message.replaceAll("[\\p{Cntrl}]", " ");
        if (message.length() > MAX_MESSAGE) message = message.substring(0, MAX_MESSAGE);
        return new ResponseFailure(message + "（错误码 " + code + "）");
    }

    static JSONObject successfulData(JSONObject reply) throws IOException, JSONException {
        int code = responseCode(reply);
        if (failed(reply, code)) throw rejection(reply, code);
        return reply.getJSONObject("data");
    }
}
