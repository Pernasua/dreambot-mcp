package com.pernasua.dreambot.mcp;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class RuntimeRequest {
    final String method;
    final String path;
    final Map<String, String> query;
    final Map<String, String> headers;
    final String body;

    static RuntimeRequest internal(String method, String rawPath, String body) {
        return from(method, rawPath, new HashMap<String, String>(), body);
    }

    static RuntimeRequest from(String method, String rawPath, Map<String, String> headers, String body) {
        String pathText = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
        int queryIndex = pathText.indexOf('?');
        String path = queryIndex >= 0 ? pathText.substring(0, queryIndex) : pathText;
        String query = queryIndex >= 0 ? pathText.substring(queryIndex + 1) : "";
        return new RuntimeRequest(method, path, parseQuery(query), headers, body);
    }

    RuntimeRequest(String method, String path, Map<String, String> query, Map<String, String> headers, String body) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.headers = headers;
        this.body = body;
    }

    String header(String name) {
        if (name == null || headers == null) {
            return "";
        }
        String value = headers.get(name.toLowerCase(java.util.Locale.ROOT));
        return value == null ? "" : value;
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            try {
                params.put(URLDecoder.decode(key, StandardCharsets.UTF_8.name()), URLDecoder.decode(value, StandardCharsets.UTF_8.name()));
            } catch (Exception ignored) {
                params.put(key, value);
            }
        }
        return params;
    }
}
