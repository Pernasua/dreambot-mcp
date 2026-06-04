package com.pernasua.dreambot.mcp;

final class McpException extends RuntimeException {
    final int code;
    final Object data;

    McpException(int code, String message) {
        this(code, message, null);
    }

    McpException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }
}
