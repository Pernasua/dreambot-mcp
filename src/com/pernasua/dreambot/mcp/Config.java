package com.pernasua.dreambot.mcp;

import java.nio.file.Path;
import java.nio.file.Paths;

final class Config {
    final String runtimeUrl;
    final boolean allowLifecycle;
    final Path scriptsDir;

    Config(String runtimeUrl, boolean allowLifecycle, Path scriptsDir) {
        this.runtimeUrl = stripTrailingSlash(runtimeUrl);
        this.allowLifecycle = allowLifecycle;
        this.scriptsDir = scriptsDir;
    }

    static Config defaults() {
        String runtime = env("DREAMBOT_MCP_URL", "http://127.0.0.1:17653");
        String scripts = env("DREAMBOT_SCRIPTS_DIR", Paths.get(System.getProperty("user.home"), "DreamBot", "Scripts").toString());
        return new Config(runtime, false, Paths.get(scripts));
    }

    Config withRuntimeUrl(String value) {
        return new Config(value, allowLifecycle, scriptsDir);
    }

    Config withAllowLifecycle(boolean value) {
        return new Config(runtimeUrl, value, scriptsDir);
    }

    Config withScriptsDir(Path value) {
        return new Config(runtimeUrl, allowLifecycle, value);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String text = value == null || value.trim().isEmpty() ? "http://127.0.0.1" : value.trim();
        while (text.endsWith("/") && text.length() > "http://x".length()) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
