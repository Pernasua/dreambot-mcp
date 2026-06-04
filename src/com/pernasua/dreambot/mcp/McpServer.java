package com.pernasua.dreambot.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class McpServer {
    static final String PROTOCOL_VERSION = "2025-06-18";
    static final String SERVER_NAME = "dreambot-mcp";
    static final String SERVER_VERSION = "2.4.1";

    private final List<Tool> tools;
    private final Map<String, Tool> toolsByName;
    private final McpObserver observer;

    McpServer(Config config) {
        this(new DreamBotTools(config).build(), McpObserver.NONE);
    }

    McpServer(List<Tool> tools) {
        this(tools, McpObserver.NONE);
    }

    McpServer(List<Tool> tools, McpObserver observer) {
        this.tools = tools;
        this.observer = observer == null ? McpObserver.NONE : observer;
        this.toolsByName = new LinkedHashMap<String, Tool>();
        for (Tool tool : tools) {
            toolsByName.put(tool.name, tool);
        }
    }

    void serveStdio() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            Map<String, Object> response = handleLine(line);
            if (response != null) {
                System.out.println(Json.stringify(response));
                System.out.flush();
            }
        }
    }

    Map<String, Object> handleLine(String line) {
        try {
            Object parsed = Json.parse(line);
            if (!(parsed instanceof Map)) {
                return error(null, -32600, "invalid request", null);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> request = (Map<String, Object>) parsed;
            return handleMessage(request);
        } catch (RuntimeException e) {
            return error(null, -32700, "parse error", Json.obj("error", e.getMessage()));
        }
    }

    Map<String, Object> handleMessage(Map<String, Object> request) {
        Object id = request.get("id");
        String method = string(request.get("method"));
        observer.onRequest(method, !request.containsKey("id"));
        if (!request.containsKey("id")) {
            return null;
        }
        try {
            Map<String, Object> result = handleRequest(request);
            return Json.obj("jsonrpc", "2.0", "id", id, "result", result);
        } catch (McpException e) {
            observer.onError(method, e.getMessage());
            return error(id, e.code, e.getMessage(), e.data);
        } catch (ToolExecutionException e) {
            observer.onError(method, e.getMessage());
            return Json.obj(
                "jsonrpc", "2.0",
                "id", id,
                "result", textResult(Json.obj("ok", Boolean.FALSE, "error", e.getMessage()), true)
            );
        } catch (RuntimeException e) {
            observer.onError(method, e.getMessage());
            return error(id, -32603, "internal error", Json.obj("error", e.getMessage()));
        }
    }

    private Map<String, Object> handleRequest(Map<String, Object> request) {
        String method = string(request.get("method"));
        Map<String, Object> params = object(request.get("params"));
        if ("initialize".equals(method)) {
            String clientVersion = string(params.get("protocolVersion"));
            return Json.obj(
                "protocolVersion", clientVersion.isEmpty() ? PROTOCOL_VERSION : clientVersion,
                "capabilities", Json.obj(
                    "tools", Json.obj("listChanged", Boolean.FALSE),
                    "resources", Json.obj("subscribe", Boolean.FALSE, "listChanged", Boolean.FALSE),
                    "prompts", Json.obj("listChanged", Boolean.FALSE)
                ),
                "serverInfo", Json.obj("name", SERVER_NAME, "version", SERVER_VERSION)
            );
        }
        if ("ping".equals(method)) {
            return Json.obj();
        }
        if ("tools/list".equals(method)) {
            List<Object> items = new ArrayList<Object>();
            for (Tool tool : tools) {
                items.add(tool.toMcp());
            }
            return Json.obj("tools", items);
        }
        if ("tools/call".equals(method)) {
            return callTool(params);
        }
        if ("resources/list".equals(method)) {
            return Json.obj("resources", Json.list());
        }
        if ("resources/read".equals(method)) {
            throw new McpException(-32602, "unknown resource");
        }
        if ("prompts/list".equals(method)) {
            return Json.obj("prompts", Json.list());
        }
        throw new McpException(-32601, "method not found: " + method);
    }

    private Map<String, Object> callTool(Map<String, Object> params) {
        String name = string(params.get("name"));
        Map<String, Object> args = object(params.get("arguments"));
        if (name.isEmpty()) {
            throw new McpException(-32602, "tool name must be a string");
        }
        Tool tool = toolsByName.get(name);
        if (tool == null) {
            throw new McpException(-32602, "unknown tool: " + name);
        }
        observer.onToolStart(name);
        Map<String, Object> payload = tool.handler.call(args);
        boolean isError = !truthy(payload.get("ok"), true);
        observer.onToolEnd(name, isError);
        return toolResult(payload, isError);
    }

    private Map<String, Object> toolResult(Map<String, Object> payload, boolean isError) {
        if (!isError && isImagePayload(payload)) {
            return imageResult(payload);
        }
        return textResult(payload, isError);
    }

    private Map<String, Object> imageResult(Map<String, Object> payload) {
        String data = string(payload.get("data_base64"));
        String mimeType = string(payload.get("mime_type"));
        Map<String, Object> summary = new LinkedHashMap<String, Object>(payload);
        summary.remove("data_base64");
        summary.put("has_image", Boolean.TRUE);
        return Json.obj(
            "content", Json.list(
                Json.obj("type", "text", "text", Json.stringify(summary)),
                Json.obj("type", "image", "mimeType", mimeType, "data", data)
            ),
            "structuredContent", summary,
            "isError", Boolean.FALSE
        );
    }

    private boolean isImagePayload(Map<String, Object> payload) {
        String data = string(payload.get("data_base64"));
        String mimeType = string(payload.get("mime_type"));
        return !data.isEmpty() && mimeType.startsWith("image/");
    }

    private Map<String, Object> textResult(Map<String, Object> payload, boolean isError) {
        return Json.obj(
            "content", Json.list(Json.obj("type", "text", "text", Json.stringify(payload))),
            "structuredContent", payload,
            "isError", Boolean.valueOf(isError)
        );
    }

    private static Map<String, Object> error(Object id, int code, String message, Object data) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("code", Integer.valueOf(code));
        error.put("message", message);
        if (data != null) {
            error.put("data", data);
        }
        return Json.obj("jsonrpc", "2.0", "id", id, "error", error);
    }

    private static String string(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static Map<String, Object> object(Object value) {
        if (value == null) {
            return new LinkedHashMap<String, Object>();
        }
        if (!(value instanceof Map)) {
            throw new McpException(-32602, "params must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private static boolean truthy(Object value, boolean fallback) {
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }
}
