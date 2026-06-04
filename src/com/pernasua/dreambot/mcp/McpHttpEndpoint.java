package com.pernasua.dreambot.mcp;

import java.util.Locale;
import java.util.Map;

final class McpHttpEndpoint {
    static final String PATH = "/mcp";

    private final McpServer server;
    private final McpMetrics metrics;

    McpHttpEndpoint(McpServer server, McpMetrics metrics) {
        this.server = server;
        this.metrics = metrics;
    }

    RuntimeResponse handle(RuntimeRequest request) {
        metrics.onHttpRequest(request);
        String method = request.method == null ? "" : request.method.toUpperCase(Locale.ROOT);
        if ("GET".equals(method) || "DELETE".equals(method)) {
            return RuntimeResponse.methodNotAllowed();
        }
        if (!"POST".equals(method)) {
            return RuntimeResponse.methodNotAllowed();
        }
        if (!validOrigin(request.header("origin"))) {
            metrics.onError("http", "rejected Origin header");
            return RuntimeResponse.badRequest("unsupported Origin header");
        }
        if (request.body == null || request.body.trim().isEmpty()) {
            return RuntimeResponse.badRequest("empty MCP request body");
        }
        Map<String, Object> response = server.handleLine(request.body);
        if (response == null) {
            return RuntimeResponse.accepted();
        }
        return RuntimeResponse.json(Json.stringify(response));
    }

    private boolean validOrigin(String origin) {
        if (origin == null || origin.trim().isEmpty()) {
            return true;
        }
        String value = origin.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("http://127.0.0.1")
            || value.startsWith("http://localhost")
            || value.startsWith("http://[::1]")
            || value.startsWith("https://127.0.0.1")
            || value.startsWith("https://localhost")
            || value.startsWith("https://[::1]");
    }
}
