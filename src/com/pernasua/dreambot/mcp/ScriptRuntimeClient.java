package com.pernasua.dreambot.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

final class ScriptRuntimeClient implements RuntimeClient {
    interface Router {
        String route(RuntimeRequest request) throws Exception;
    }

    private final Router router;

    ScriptRuntimeClient(Router router) {
        this.router = router;
    }

    @Override
    public Map<String, Object> get(String path) {
        return request("GET", path, null);
    }

    @Override
    public Map<String, Object> post(String path, Map<String, Object> body) {
        return request("POST", path, body);
    }

    @Override
    public Map<String, Object> post(String path, Map<String, Object> body, int timeoutMs) {
        return request("POST", path, body);
    }

    private Map<String, Object> request(String method, String path, Map<String, Object> body) {
        String normalizedPath = path == null || path.isEmpty() ? "/" : path;
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        String requestBody = body == null ? "" : Json.stringify(body);
        try {
            String text = router.route(RuntimeRequest.internal(method, normalizedPath, requestBody));
            Object parsed = text == null || text.trim().isEmpty() ? new LinkedHashMap<String, Object>() : Json.parse(text);
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
            response.put("_http_status", Integer.valueOf(200));
            response.put("_path", normalizedPath);
            response.putIfAbsent("ok", Boolean.TRUE);
            return response;
        } catch (Exception e) {
            throw new ToolExecutionException("script runtime request failed for " + method + " " + normalizedPath + ": " + e.getMessage(), e);
        }
    }
}
