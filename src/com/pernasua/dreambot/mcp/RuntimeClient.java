package com.pernasua.dreambot.mcp;

import java.util.Map;

interface RuntimeClient {
    Map<String, Object> get(String path);

    Map<String, Object> post(String path, Map<String, Object> body);

    Map<String, Object> post(String path, Map<String, Object> body, int timeoutMs);
}
