package com.pernasua.dreambot.mcp;

import java.util.Locale;

final class ScriptSettings {
    final String bindHost;
    final int port;
    final LoginSolverPolicy loginSolverPolicy;
    final boolean allowLifecycle;
    final boolean paintEnabled;
    final boolean debugEnabled;
    final boolean releaseScriptSlot;

    private ScriptSettings(String bindHost, int port, LoginSolverPolicy loginSolverPolicy, boolean allowLifecycle, boolean paintEnabled, boolean debugEnabled, boolean releaseScriptSlot) {
        this.bindHost = bindHost;
        this.port = port;
        this.loginSolverPolicy = loginSolverPolicy;
        this.allowLifecycle = allowLifecycle;
        this.paintEnabled = paintEnabled;
        this.debugEnabled = debugEnabled;
        this.releaseScriptSlot = releaseScriptSlot;
    }

    static ScriptSettings from(String... args) {
        Builder builder = new Builder();
        builder.bindHost = setting("dreambot.mcp.bindHost", "DREAMBOT_MCP_BIND_HOST", "127.0.0.1");
        builder.port = parsePort(setting("dreambot.mcp.port", "DREAMBOT_MCP_PORT", "17653"));
        builder.loginSolverPolicy = LoginSolverPolicy.parse(setting("dreambot.mcp.loginSolverPolicy", "DREAMBOT_MCP_LOGIN_SOLVER_POLICY", "after_initial_login"));
        builder.allowLifecycle = parseBool(setting("dreambot.mcp.allowLifecycle", "DREAMBOT_MCP_ALLOW_LIFECYCLE", "true"));
        builder.paintEnabled = parseBool(setting("dreambot.mcp.paint", "DREAMBOT_MCP_PAINT", "false"));
        builder.debugEnabled = parseBool(setting("dreambot.mcp.debug", "DREAMBOT_MCP_DEBUG", "true"));
        builder.releaseScriptSlot = parseBool(setting("dreambot.mcp.releaseScriptSlot", "DREAMBOT_MCP_RELEASE_SLOT", "true"));
        builder.applyArgs(args == null ? new String[0] : args);
        return new ScriptSettings(
            builder.bindHost,
            builder.port,
            builder.loginSolverPolicy,
            builder.allowLifecycle,
            builder.paintEnabled,
            builder.debugEnabled,
            builder.releaseScriptSlot
        );
    }

    String httpBaseUrl() {
        return "http://" + bindHost + ":" + port;
    }

    String mcpUrl() {
        return httpBaseUrl() + McpHttpEndpoint.PATH;
    }

    String summary() {
        return "bindHost=" + bindHost
            + " port=" + port
            + " allowLifecycle=" + allowLifecycle
            + " loginSolverPolicy=" + loginSolverPolicy.value
            + " paint=" + paintEnabled
            + " debug=" + debugEnabled
            + " releaseScriptSlot=" + releaseScriptSlot;
    }

    private static String setting(String property, String env, String defaultValue) {
        String value = settingOrNull(property, env);
        return value == null ? defaultValue : value;
    }

    private static String settingOrNull(String property, String env) {
        String value = System.getProperty(property);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        value = System.getenv(env);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return null;
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port > 0 && port <= 65535) {
                return port;
            }
        } catch (RuntimeException ignored) {
        }
        throw new IllegalArgumentException("MCP port must be between 1 and 65535");
    }

    private static boolean parseBool(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.isEmpty()
            && !"0".equals(normalized)
            && !"false".equals(normalized)
            && !"no".equals(normalized)
            && !"off".equals(normalized)
            && !"disabled".equals(normalized);
    }

    private static final class Builder {
        String bindHost;
        int port;
        LoginSolverPolicy loginSolverPolicy;
        boolean allowLifecycle;
        boolean paintEnabled;
        boolean debugEnabled;
        boolean releaseScriptSlot;

        void applyArgs(String[] args) {
            for (int i = 0; i < args.length; i++) {
                String raw = args[i];
                if (raw == null || raw.trim().isEmpty()) {
                    continue;
                }
                String arg = raw.trim();
                String key;
                String value = null;
                if (arg.startsWith("--no-")) {
                    key = arg.substring(5);
                    value = "false";
                } else {
                    if (arg.startsWith("--")) {
                        arg = arg.substring(2);
                    }
                    int equals = arg.indexOf('=');
                    if (equals >= 0) {
                        key = arg.substring(0, equals);
                        value = arg.substring(equals + 1);
                    } else {
                        key = arg;
                        if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].trim().startsWith("--")) {
                            value = args[++i].trim();
                        }
                    }
                }
                apply(normalizeKey(key), value == null ? "true" : value);
            }
        }

        private void apply(String key, String value) {
            if ("mcp_bind_host".equals(key) || "bind_host".equals(key)) {
                bindHost = value.trim();
            } else if ("mcp_port".equals(key) || "port".equals(key)) {
                port = parsePort(value);
            } else if ("mcp_login_solver_policy".equals(key) || "login_solver_policy".equals(key) || "mcp_login_solver".equals(key) || "login_solver".equals(key)) {
                loginSolverPolicy = LoginSolverPolicy.parse(value);
            } else if ("mcp_allow_lifecycle".equals(key) || "allow_lifecycle".equals(key)) {
                allowLifecycle = parseBool(value);
            } else if ("mcp_paint".equals(key) || "paint".equals(key)) {
                paintEnabled = parseBool(value);
            } else if ("mcp_debug".equals(key) || "debug".equals(key)) {
                debugEnabled = parseBool(value);
            } else if ("mcp_release_slot".equals(key) || "release_slot".equals(key) || "release_script_slot".equals(key)) {
                releaseScriptSlot = parseBool(value);
            } else if ("mcp_keep_script_slot".equals(key) || "keep_script_slot".equals(key)) {
                releaseScriptSlot = !parseBool(value);
            }
        }

        private String normalizeKey(String key) {
            return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
        }
    }
}
