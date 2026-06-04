package com.pernasua.dreambot.mcp;

final class RuntimeResponse {
    final int status;
    final String contentType;
    final String body;

    RuntimeResponse(int status, String contentType, String body) {
        this.status = status;
        this.contentType = contentType == null || contentType.isEmpty() ? "application/json" : contentType;
        this.body = body == null ? "" : body;
    }

    static RuntimeResponse json(String body) {
        return new RuntimeResponse(200, "application/json", body);
    }

    static RuntimeResponse accepted() {
        return new RuntimeResponse(202, "text/plain", "");
    }

    static RuntimeResponse badRequest(String message) {
        return new RuntimeResponse(400, "application/json", "{\"ok\":false,\"error\":" + RuntimeJson.quote(message) + "}");
    }

    static RuntimeResponse methodNotAllowed() {
        return new RuntimeResponse(405, "text/plain", "");
    }

    static RuntimeResponse serverError(Throwable error) {
        return new RuntimeResponse(500, "application/json", "{\"ok\":false,\"error\":" + RuntimeJson.quote(String.valueOf(error)) + "}");
    }
}
