package com.pernasua.dreambot.mcp;

final class McpMetrics implements McpObserver {
    private final long startedAtMs = System.currentTimeMillis();
    private long httpRequests;
    private long mcpRequests;
    private long mcpNotifications;
    private long toolCalls;
    private long toolErrors;
    private long lastMcpAtMs;
    private String lastMethod = "";
    private String lastTool = "";
    private String lastError = "";

    synchronized void onHttpRequest(RuntimeRequest request) {
        httpRequests++;
    }

    @Override
    public synchronized void onRequest(String method, boolean notification) {
        if (notification) {
            mcpNotifications++;
        } else {
            mcpRequests++;
        }
        lastMethod = method == null ? "" : method;
        lastMcpAtMs = System.currentTimeMillis();
    }

    @Override
    public synchronized void onToolStart(String name) {
        toolCalls++;
        lastTool = name == null ? "" : name;
        lastMcpAtMs = System.currentTimeMillis();
    }

    @Override
    public synchronized void onToolEnd(String name, boolean error) {
        lastTool = name == null ? "" : name;
        if (error) {
            toolErrors++;
        }
        lastMcpAtMs = System.currentTimeMillis();
    }

    @Override
    public synchronized void onError(String method, String error) {
        toolErrors++;
        lastMethod = method == null ? "" : method;
        lastError = error == null ? "" : error;
        lastMcpAtMs = System.currentTimeMillis();
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(
            startedAtMs,
            httpRequests,
            mcpRequests,
            mcpNotifications,
            toolCalls,
            toolErrors,
            lastMcpAtMs,
            lastMethod,
            lastTool,
            lastError
        );
    }

    static final class Snapshot {
        final long startedAtMs;
        final long httpRequests;
        final long mcpRequests;
        final long mcpNotifications;
        final long toolCalls;
        final long toolErrors;
        final long lastMcpAtMs;
        final String lastMethod;
        final String lastTool;
        final String lastError;

        Snapshot(long startedAtMs, long httpRequests, long mcpRequests, long mcpNotifications, long toolCalls, long toolErrors, long lastMcpAtMs, String lastMethod, String lastTool, String lastError) {
            this.startedAtMs = startedAtMs;
            this.httpRequests = httpRequests;
            this.mcpRequests = mcpRequests;
            this.mcpNotifications = mcpNotifications;
            this.toolCalls = toolCalls;
            this.toolErrors = toolErrors;
            this.lastMcpAtMs = lastMcpAtMs;
            this.lastMethod = lastMethod;
            this.lastTool = lastTool;
            this.lastError = lastError;
        }
    }
}
