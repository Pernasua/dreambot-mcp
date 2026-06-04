package com.pernasua.dreambot.mcp;

import java.util.Locale;

enum LoginSolverPolicy {
    AFTER_INITIAL_LOGIN("after_initial_login"),
    ENABLED("enabled"),
    DISABLED("disabled");

    final String value;

    LoginSolverPolicy(String value) {
        this.value = value;
    }

    static LoginSolverPolicy parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
        if (normalized.isEmpty() || "after_login".equals(normalized) || "after_initial_login".equals(normalized) || "initial_login_only".equals(normalized)) {
            return AFTER_INITIAL_LOGIN;
        }
        if ("enabled".equals(normalized) || "enable".equals(normalized) || "on".equals(normalized) || "true".equals(normalized)) {
            return ENABLED;
        }
        if ("disabled".equals(normalized) || "disable".equals(normalized) || "off".equals(normalized) || "false".equals(normalized)) {
            return DISABLED;
        }
        throw new IllegalArgumentException("unknown login solver policy: " + value);
    }
}
