package com.pernasua.dreambot.mcp;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        ParsedArgs parsed = ParsedArgs.parse(args);
        String command = parsed.command;
        Config config = parsed.config;

        if ("self-test".equals(command)) {
            SelfTest.run();
            return;
        }
        if ("health".equals(command)) {
            LocalHttp runtime = new LocalHttp(config.runtimeUrl, 5000);
            System.out.println(Json.pretty(runtime.get("/health")));
            return;
        }
        if ("install-script".equals(command)) {
            Map<String, Object> result = new SelfInstaller().install(config.scriptsDir);
            System.out.println(Json.pretty(result));
            return;
        }
        if ("jar-info".equals(command)) {
            System.out.println(Json.pretty(new SelfInstaller().info()));
            return;
        }
        if (!"mcp".equals(command)) {
            throw new IllegalArgumentException("unknown command: " + command);
        }
        new McpServer(config).serveStdio();
    }

    private static final class ParsedArgs {
        final String command;
        final Config config;

        ParsedArgs(String command, Config config) {
            this.command = command;
            this.config = config;
        }

        static ParsedArgs parse(String[] args) throws IOException {
            String command = "mcp";
            Config config = Config.defaults();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("mcp".equals(arg) || "self-test".equals(arg) || "health".equals(arg) || "install-script".equals(arg) || "jar-info".equals(arg)) {
                    command = arg;
                    continue;
                }
                if ("--allow-lifecycle".equals(arg)) {
                    config = config.withAllowLifecycle(true);
                    continue;
                }
                if ("--runtime-url".equals(arg) || "--mcp-url".equals(arg)) {
                    config = config.withRuntimeUrl(requireValue(args, ++i, arg));
                    continue;
                }
                if (arg.startsWith("--runtime-url=")) {
                    config = config.withRuntimeUrl(arg.substring("--runtime-url=".length()));
                    continue;
                }
                if (arg.startsWith("--mcp-url=")) {
                    config = config.withRuntimeUrl(arg.substring("--mcp-url=".length()));
                    continue;
                }
                if ("--scripts-dir".equals(arg)) {
                    config = config.withScriptsDir(Paths.get(requireValue(args, ++i, arg)));
                    continue;
                }
                if (arg.startsWith("--scripts-dir=")) {
                    config = config.withScriptsDir(Paths.get(arg.substring("--scripts-dir=".length())));
                    continue;
                }
                throw new IllegalArgumentException("unknown argument: " + arg);
            }
            return new ParsedArgs(command, config);
        }

        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length || args[index].trim().isEmpty()) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[index];
        }
    }
}
