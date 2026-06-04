package com.pernasua.dreambot.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

final class Tool {
    final String name;
    final String title;
    final String description;
    final Map<String, Object> inputSchema;
    final ToolHandler handler;
    final boolean destructive;

    Tool(String name, String title, String description, Map<String, Object> inputSchema, ToolHandler handler) {
        this(name, title, description, inputSchema, handler, false);
    }

    Tool(String name, String title, String description, Map<String, Object> inputSchema, ToolHandler handler, boolean destructive) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.inputSchema = inputSchema;
        this.handler = handler;
        this.destructive = destructive;
    }

    Map<String, Object> toMcp() {
        Map<String, Object> tool = new LinkedHashMap<String, Object>();
        tool.put("name", name);
        tool.put("title", title);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        if (destructive) {
            tool.put("annotations", Json.obj("destructiveHint", Boolean.TRUE));
        }
        return tool;
    }
}
