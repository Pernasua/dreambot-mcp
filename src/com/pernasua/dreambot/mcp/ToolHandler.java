package com.pernasua.dreambot.mcp;

import java.util.Map;

interface ToolHandler {
    Map<String, Object> call(Map<String, Object> args);
}
