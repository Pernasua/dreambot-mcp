package com.pernasua.dreambot.mcp;

interface McpObserver {
    McpObserver NONE = new McpObserver() {
    };

    default void onRequest(String method, boolean notification) {
    }

    default void onToolStart(String name) {
    }

    default void onToolEnd(String name, boolean error) {
    }

    default void onError(String method, String error) {
    }
}
