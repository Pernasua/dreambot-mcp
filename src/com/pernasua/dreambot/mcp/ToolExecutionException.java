package com.pernasua.dreambot.mcp;

final class ToolExecutionException extends RuntimeException {
    ToolExecutionException(String message) {
        super(message);
    }

    ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
