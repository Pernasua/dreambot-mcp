package com.pernasua.dreambot.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class LocalHttp implements RuntimeClient {
    private final String baseUrl;
    private final int timeoutMs;

    LocalHttp(String baseUrl, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Map<String, Object> get(String path) {
        return request("GET", path, null, timeoutMs);
    }

    @Override
    public Map<String, Object> post(String path, Map<String, Object> body) {
        return request("POST", path, body, timeoutMs);
    }

    @Override
    public Map<String, Object> post(String path, Map<String, Object> body, int timeoutMs) {
        return request("POST", path, body, timeoutMs);
    }

    private Map<String, Object> request(String method, String path, Map<String, Object> body, int requestTimeoutMs) {
        String normalizedPath = path == null || path.isEmpty() ? "/" : path;
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + normalizedPath);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(requestTimeoutMs);
            conn.setReadTimeout(requestTimeoutMs);
            conn.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] data = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(data);
                }
            }
            int status = conn.getResponseCode();
            String text = readAll(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            Object parsed = text.trim().isEmpty() ? new LinkedHashMap<String, Object>() : Json.parse(text);
            Map<String, Object> response;
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) parsed;
                response = new LinkedHashMap<String, Object>(map);
            } else {
                response = new LinkedHashMap<String, Object>();
                response.put("ok", Boolean.TRUE);
                response.put("value", parsed);
            }
            response.put("_http_status", Integer.valueOf(status));
            response.put("_path", normalizedPath);
            if (status >= 400) {
                response.putIfAbsent("ok", Boolean.FALSE);
                response.putIfAbsent("error", "HTTP " + status);
            } else {
                response.putIfAbsent("ok", Boolean.TRUE);
            }
            return response;
        } catch (IOException e) {
            throw new ToolExecutionException("local HTTP request failed for " + method + " " + baseUrl + normalizedPath + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ToolExecutionException("local HTTP response was not valid JSON for " + method + " " + baseUrl + normalizedPath + ": " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
