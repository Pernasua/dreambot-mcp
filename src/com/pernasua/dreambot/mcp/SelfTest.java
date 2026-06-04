package com.pernasua.dreambot.mcp;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SelfTest {
    private SelfTest() {
    }

    static void run() throws Exception {
        StubRuntime stub = new StubRuntime();
        stub.start();
        try {
            Config config = Config.defaults().withRuntimeUrl(stub.url());
            McpServer server = new McpServer(config);
            assertInitialize(server);
            assertToolList(server);
            assertSleepTool(server, stub);
            assertState(server);
            assertVerboseState(server);
            assertCompactDefaults(server, stub);
            assertScreenshot(server, stub);
            assertValidationBlocksBadWalk(server, stub);
            assertRuntimeActionRouting(server, stub);
            assertNoAuthHeaders(stub);
            assertRuntimeOnlyToolScope();
            assertHttpMcpEndpoint();
            assertAgentTaskRunnerStopsOnNegativeDelay();
            assertScriptSettings();
            System.out.println("ok: dreambot-mcp self-test passed");
        } finally {
            stub.stop();
        }
    }

    private static void assertInitialize(McpServer server) {
        Map<String, Object> response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}");
        Map<String, Object> result = object(response.get("result"));
        Map<String, Object> info = object(result.get("serverInfo"));
        require("dreambot-mcp".equals(info.get("name")), "initialize returned wrong server name");
        require(McpServer.SERVER_VERSION.equals(info.get("version")), "initialize returned wrong server version");
    }

    private static void assertToolList(McpServer server) {
        Map<String, Object> response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
        List<?> tools = list(object(response.get("result")).get("tools"));
        assertUniqueToolNames(tools);
        assertDreamBotToolNames(tools);
        Map<String, Object> sleepTool = findTool(tools, "dreambot_sleep");
        require(sleepTool != null, "sleep tool missing");
        Map<String, Object> sleepSchema = object(object(object(sleepTool.get("inputSchema")).get("properties")).get("seconds"));
        require(Double.valueOf(604800.0).equals(sleepSchema.get("maximum")), "sleep schema should cap seconds at one week");
        require(containsTool(tools, "dreambot_runtime_health"), "runtime health tool missing");
        require(containsTool(tools, "dreambot_state"), "state tool missing");
        require(containsTool(tools, "dreambot_screenshot"), "screenshot tool missing");
        require(containsTool(tools, "dreambot_login_status"), "login status tool missing");
        require(containsTool(tools, "dreambot_login_type_credentials"), "login credentials tool missing");
        require(containsTool(tools, "dreambot_chat_say"), "chat tool missing");
        require(containsTool(tools, "dreambot_java_eval"), "java eval tool missing");
        require(containsTool(tools, "dreambot_agent_task_start"), "agent task start tool missing");
        require(containsTool(tools, "dreambot_agent_task_status"), "agent task status tool missing");
        require(containsTool(tools, "dreambot_agent_task_logs"), "agent task logs tool missing");
        require(containsTool(tools, "dreambot_agent_task_stop"), "agent task stop tool missing");
        require(containsTool(tools, "dreambot_install_script"), "install script tool missing");
        assertAllToolsHaveVerbose(tools);
    }

    private static void assertSleepTool(McpServer server, StubRuntime stub) {
        int before = stub.requests.size();
        Map<String, Object> response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_sleep\",\"arguments\":{\"seconds\":0}}}");
        Map<String, Object> result = object(response.get("result"));
        require(Boolean.FALSE.equals(result.get("isError")), "sleep tool returned error");
        require(stub.requests.size() == before, "sleep tool should not reach standalone runtime");
        Map<String, Object> structured = object(result.get("structuredContent"));
        require(!structured.containsKey("ok"), "compact sleep should not include ok wrapper");
        require(structured.get("seconds") instanceof Number, "sleep compact response missing seconds");
        require(structured.get("slept_ms") instanceof Number, "sleep compact response missing slept_ms");

        response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_sleep\",\"arguments\":{\"seconds\":604800.001}}}");
        result = object(response.get("result"));
        require(Boolean.TRUE.equals(result.get("isError")), "sleep above one week should be rejected");
    }

    private static void assertState(McpServer server) {
        Map<String, Object> response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_state\",\"arguments\":{}}}");
        Map<String, Object> result = object(response.get("result"));
        require(Boolean.FALSE.equals(result.get("isError")), "state tool returned error");
        Map<String, Object> structured = object(result.get("structuredContent"));
        require("stub-player".equals(object(structured.get("local_player")).get("name")), "state did not come from standalone runtime");
        require(!structured.containsKey("inventory"), "compact state should not include inventory");
        require(!structured.containsKey("skills"), "compact state should not include skills");
        require(!structured.containsKey("quests"), "compact state should not include quests");
        require(!structured.containsKey("bank"), "compact state should not include bank");
        require(!structured.containsKey("ok"), "compact state should not include ok wrapper");
        require(!structured.containsKey("_http_status"), "compact state should not include HTTP status");
        require(!structured.containsKey("_path"), "compact state should not include runtime path");
    }

    private static void assertVerboseState(McpServer server) {
        Map<String, Object> response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":30,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_state\",\"arguments\":{\"verbose\":true}}}");
        Map<String, Object> result = object(response.get("result"));
        require(Boolean.FALSE.equals(result.get("isError")), "verbose state tool returned error");
        Map<String, Object> structured = object(result.get("structuredContent"));
        require(Boolean.TRUE.equals(structured.get("ok")), "verbose state should include ok wrapper");
        require(Integer.valueOf(200).equals(structured.get("_http_status")), "verbose state should include HTTP status");
        require("/state".equals(structured.get("_path")), "verbose state should include runtime path");
        require("stub-player".equals(object(structured.get("local_player")).get("name")), "verbose state did not include runtime payload");
        require(structured.containsKey("inventory"), "verbose state should include full inventory");
    }

    private static void assertCompactDefaults(McpServer server, StubRuntime stub) {
        Map<String, Object> response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_quests\",\"arguments\":{}}}");
        Map<String, Object> result = object(response.get("result"));
        require(Boolean.FALSE.equals(result.get("isError")), "compact quests returned error");
        require(stub.requests.get(stub.requests.size() - 1).contains("GET /quests?all=false"), "quests default should request summary only");
        Map<String, Object> structured = object(result.get("structuredContent"));
        require(Integer.valueOf(7).equals(structured.get("quest_points")), "compact quests should include quest points");
        require(!structured.containsKey("quests"), "compact quests default should not include all quest rows");

        response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":32,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_quest_state\",\"arguments\":{\"all\":true}}}");
        result = object(response.get("result"));
        structured = object(result.get("structuredContent"));
        Map<String, Object> quest = object(list(structured.get("quests")).get(0));
        require(!quest.containsKey("settings"), "compact quest rows should omit settings");
        require(!quest.containsKey("config_id"), "compact quest rows should omit config internals");

        response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":33,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_dialogue_state\",\"arguments\":{}}}");
        result = object(response.get("result"));
        structured = object(result.get("structuredContent"));
        require(!structured.containsKey("screen_text"), "compact dialogue should omit screen text scan");
        require(!structured.containsKey("recent_messages"), "compact dialogue should omit recent messages");

        response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":34,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_objects\",\"arguments\":{}}}");
        result = object(response.get("result"));
        Map<String, Object> object = object(list(object(result.get("structuredContent")).get("objects")).get(0));
        require(!object.containsKey("bounds"), "compact object rows should omit bounds");
        require(!object.containsKey("clickable_point"), "compact object rows should omit clickable point");

        response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":35,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_widgets\",\"arguments\":{}}}");
        result = object(response.get("result"));
        require(stub.requests.get(stub.requests.size() - 1).contains("limit=15"), "widgets default should send compact limit");
        Map<String, Object> widget = object(list(object(result.get("structuredContent")).get("widgets")).get(0));
        require(!widget.containsKey("raw_id"), "compact widget rows should omit raw id");
        require(widget.containsKey("bounds"), "compact widget rows should keep bounds");

        response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":36,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_ui_text\",\"arguments\":{}}}");
        result = object(response.get("result"));
        require(stub.requests.get(stub.requests.size() - 1).contains("limit=25"), "ui_text default should send compact text limit");
        structured = object(result.get("structuredContent"));
        require(structured.get("screen_text") instanceof List, "compact ui_text should return combined screen text list");

        response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":40,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_camera\",\"arguments\":{}}}");
        result = object(response.get("result"));
        structured = object(result.get("structuredContent"));
        require(Integer.valueOf(100).equals(structured.get("pitch")), "compact camera should include pitch");
        require(Integer.valueOf(200).equals(structured.get("yaw")), "compact camera should include yaw");
        require(Integer.valueOf(300).equals(structured.get("zoom")), "compact camera should include zoom");
        require(!structured.containsKey("x"), "compact camera should omit x");
        require(!structured.containsKey("min_zoom"), "compact camera should omit min_zoom");

        response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":37,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_agent_task_logs\",\"arguments\":{}}}");
        result = object(response.get("result"));
        require(stub.requests.get(stub.requests.size() - 1).contains("limit=20"), "agent logs default should send compact limit");
        Map<String, Object> task = object(object(result.get("structuredContent")).get("agent_task"));
        require(!task.containsKey("source_hash"), "compact agent task should omit source hash");
        require(!task.containsKey("class_name"), "compact agent task should omit class name");

        response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":38,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_object_interact\",\"arguments\":{\"name\":\"Tree\",\"action\":\"Chop down\"}}}");
        result = object(response.get("result"));
        Map<String, Object> target = object(object(result.get("structuredContent")).get("target"));
        require(!target.containsKey("bounds"), "compact action target should omit bounds");

        String text = textContent(result);
        require(!text.contains("\n"), "text content should use compact JSON");

        response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":39,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_dialogue_state\",\"arguments\":{\"verbose\":true}}}");
        result = object(response.get("result"));
        structured = object(result.get("structuredContent"));
        require(object(structured.get("dialogue")).containsKey("screen_text"), "verbose dialogue should include full screen text");
    }

    private static void assertScreenshot(McpServer server, StubRuntime stub) {
        Map<String, Object> response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_screenshot\",\"arguments\":{}}}");
        Map<String, Object> result = object(response.get("result"));
        require(Boolean.FALSE.equals(result.get("isError")), "screenshot tool returned error");
        require(stub.requests.get(stub.requests.size() - 1).contains("GET /screenshot"), "screenshot did not use standalone runtime");

        Map<String, Object> structured = object(result.get("structuredContent"));
        require(Boolean.TRUE.equals(structured.get("has_image")), "screenshot metadata did not flag image content");
        require(!structured.containsKey("data_base64"), "screenshot structuredContent should not contain image base64");

        List<?> content = list(result.get("content"));
        boolean sawImage = false;
        for (Object item : content) {
            if (item instanceof Map && "image".equals(((Map<?, ?>) item).get("type"))) {
                sawImage = true;
                require("image/png".equals(((Map<?, ?>) item).get("mimeType")), "screenshot image content had wrong MIME type");
                require("iVBORw0KGgo=".equals(((Map<?, ?>) item).get("data")), "screenshot image content did not include base64 data");
            }
        }
        require(sawImage, "screenshot result did not include MCP image content");
    }

    private static void assertValidationBlocksBadWalk(McpServer server, StubRuntime stub) {
        int before = stub.requests.size();
        Map<String, Object> response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_walk\",\"arguments\":{\"x\":-1,\"y\":3200}}}");
        Map<String, Object> result = object(response.get("result"));
        require(Boolean.TRUE.equals(result.get("isError")), "bad walk should be rejected");
        require(stub.requests.size() == before, "bad walk reached runtime");
    }

    private static void assertRuntimeActionRouting(McpServer server, StubRuntime stub) {
        assertToolRoutes(server, stub, "dreambot_walk", "{\"x\":3221,\"y\":3218}", "POST /action/walk");
        assertToolRoutes(server, stub, "dreambot_login_status", "{}", "GET /login/status");
        assertToolRoutes(server, stub, "dreambot_chat_say", "{\"message\":\"hello\",\"press_enter\":false}", "POST /action/chat/say");

        Map<String, Object> credentials = assertToolRoutes(server, stub, "dreambot_login_type_credentials", "{\"username\":\"agent@example.com\",\"password\":\"secret\",\"submit\":false}", "POST /action/login/type-credentials");
        require(!String.valueOf(credentials).contains("secret"), "password leaked into tool response");

        assertToolRoutes(server, stub, "dreambot_java_eval", "{\"code\":\"1 + 1\"}", "POST /action/java/eval");
        assertToolRoutes(server, stub, "dreambot_agent_task_start", "{\"source\":\"ctx.log(\\\"tick\\\"); return 100;\"}", "POST /action/agent-task/start");
        assertToolRoutes(server, stub, "dreambot_agent_task_status", "{}", "GET /agent-task/status");
        assertToolRoutes(server, stub, "dreambot_agent_task_stop", "{\"reason\":\"test\"}", "POST /action/agent-task/stop");
    }

    private static Map<String, Object> assertToolRoutes(McpServer server, StubRuntime stub, String tool, String argsJson, String expectedRequest) {
        int before = stub.requests.size();
        Map<String, Object> response = server.handleLine("{\"jsonrpc\":\"2.0\",\"id\":" + (100 + before) + ",\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool + "\",\"arguments\":" + argsJson + "}}");
        Map<String, Object> result = object(response.get("result"));
        require(Boolean.FALSE.equals(result.get("isError")), tool + " returned error");
        require(stub.requests.size() == before + 1, tool + " did not reach standalone runtime");
        require(stub.requests.get(before).contains(expectedRequest), tool + " routed to wrong runtime endpoint");
        return response;
    }

    private static void assertRuntimeOnlyToolScope() {
        List<Tool> lifecycleTools = DreamBotTools.runtimeTools(new FakeRuntime(), true);
        require(containsToolObject(lifecycleTools, "dreambot_client_logout"), "runtime-only lifecycle tool missing when enabled");
        require(!containsToolObject(lifecycleTools, "dreambot_install_script"), "runtime-only tools should not expose install_script");
        require(!containsToolObject(lifecycleTools, "dreambot_jar_info"), "runtime-only tools should not expose jar_info");

        List<Tool> nonLifecycleTools = DreamBotTools.runtimeTools(new FakeRuntime(), false);
        require(!containsToolObject(nonLifecycleTools, "dreambot_client_logout"), "runtime-only lifecycle tool exposed when disabled");
        require(!containsToolObject(nonLifecycleTools, "dreambot_combat_special"), "runtime-only combat lifecycle tool exposed when disabled");
    }

    private static void assertHttpMcpEndpoint() {
        McpMetrics metrics = new McpMetrics();
        McpHttpEndpoint endpoint = new McpHttpEndpoint(new McpServer(DreamBotTools.runtimeTools(new FakeRuntime(), true), metrics), metrics);

        RuntimeResponse get = endpoint.handle(RuntimeRequest.internal("GET", "/mcp", ""));
        require(get.status == 405, "HTTP MCP GET should return 405 when SSE is not supported");

        RuntimeResponse notification = endpoint.handle(RuntimeRequest.internal("POST", "/mcp", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}"));
        require(notification.status == 202, "HTTP MCP notification should return 202");
        require(notification.body.isEmpty(), "HTTP MCP notification should not return a body");

        RuntimeResponse initialize = endpoint.handle(RuntimeRequest.internal("POST", "/mcp", "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}"));
        require(initialize.status == 200, "HTTP MCP initialize should return 200");
        Map<String, Object> initializeResponse = Json.parseObject(initialize.body);
        require("2.0".equals(initializeResponse.get("jsonrpc")), "HTTP MCP initialize response is not JSON-RPC");

        RuntimeResponse toolCall = endpoint.handle(RuntimeRequest.internal("POST", "/mcp", "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\",\"params\":{\"name\":\"dreambot_runtime_health\",\"arguments\":{}}}"));
        require(toolCall.status == 200, "HTTP MCP tool call should return 200");
        Map<String, Object> toolResponse = Json.parseObject(toolCall.body);
        Map<String, Object> result = object(toolResponse.get("result"));
        require(Boolean.FALSE.equals(result.get("isError")), "HTTP MCP health tool returned an error");
    }

    private static void assertAgentTaskRunnerStopsOnNegativeDelay() throws Exception {
        AgentTaskRunner runner = new AgentTaskRunner();
        AgentTask task = new AgentTask() {
            public int onLoop(AgentTask.Context ctx) {
                ctx.log("negative delay tick");
                return -1;
            }
        };
        runner.start(null, "negative-delay-test", new AgentTaskCompiler.CompiledTask(task, "NegativeDelayTest", "hash"));
        runner.tick();
        Map<String, Object> status = object(object(Json.parse(runner.statusJson())).get("agent_task"));
        require("stopped".equals(status.get("status")), "negative task delay should stop the task");
        require("negative delay".equals(status.get("stop_reason")), "negative task delay should set a clear stop reason");
        require(Integer.valueOf(1).equals(status.get("loop_count")), "negative task delay should stop after one loop");
    }

    private static void assertScriptSettings() {
        ScriptSettings defaults = ScriptSettings.from();
        require(defaults.loginSolverPolicy == LoginSolverPolicy.AFTER_INITIAL_LOGIN, "login solver should default to after_initial_login");

        ScriptSettings enabled = ScriptSettings.from("--mcp-login-solver-policy=enabled");
        require(enabled.loginSolverPolicy == LoginSolverPolicy.ENABLED, "enabled login solver policy should allow DreamBot auto-login");

        ScriptSettings disabled = ScriptSettings.from("--mcp-login-solver-policy=enabled", "--mcp-login-solver-policy=disabled");
        require(disabled.loginSolverPolicy == LoginSolverPolicy.DISABLED, "later login solver policy should win");

        ScriptSettings explicit = ScriptSettings.from("--mcp-login-solver=after_initial_login");
        require(explicit.loginSolverPolicy == LoginSolverPolicy.AFTER_INITIAL_LOGIN, "explicit login solver policy was not parsed");
    }

    private static void assertNoAuthHeaders(StubRuntime stub) {
        for (String request : stub.requests) {
            require(!request.contains("Authorization="), "unexpected Authorization header");
        }
    }

    private static boolean containsTool(List<?> tools, String name) {
        return findTool(tools, name) != null;
    }

    private static Map<String, Object> findTool(List<?> tools, String name) {
        for (Object item : tools) {
            if (item instanceof Map && name.equals(((Map<?, ?>) item).get("name"))) {
                return object(item);
            }
        }
        return null;
    }

    private static boolean containsToolObject(List<Tool> tools, String name) {
        for (Tool tool : tools) {
            if (name.equals(tool.name)) {
                return true;
            }
        }
        return false;
    }

    private static void assertUniqueToolNames(List<?> tools) {
        Set<String> names = new LinkedHashSet<String>();
        for (Object item : tools) {
            if (!(item instanceof Map)) {
                throw new IllegalStateException("tool is not an object: " + item);
            }
            Object name = ((Map<?, ?>) item).get("name");
            require(name instanceof String, "tool name is missing or non-string");
            require(names.add((String) name), "duplicate tool name: " + name);
        }
    }

    private static void assertDreamBotToolNames(List<?> tools) {
        for (Object item : tools) {
            String name = (String) ((Map<?, ?>) item).get("name");
            require(name.startsWith("dreambot_"), "unexpected tool namespace: " + name);
        }
    }

    private static void assertAllToolsHaveVerbose(List<?> tools) {
        for (Object item : tools) {
            Map<String, Object> tool = object(item);
            Map<String, Object> schema = object(tool.get("inputSchema"));
            Map<String, Object> properties = object(schema.get("properties"));
            Map<String, Object> verbose = object(properties.get("verbose"));
            require("boolean".equals(verbose.get("type")), "verbose must be a boolean on " + tool.get("name"));
            require(Boolean.FALSE.equals(verbose.get("default")), "verbose must default false on " + tool.get("name"));
        }
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalStateException("expected object: " + value);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private static List<?> list(Object value) {
        if (!(value instanceof List)) {
            throw new IllegalStateException("expected list: " + value);
        }
        return (List<?>) value;
    }

    private static String textContent(Map<String, Object> result) {
        StringBuilder out = new StringBuilder();
        for (Object item : list(result.get("content"))) {
            Map<String, Object> content = object(item);
            if ("text".equals(content.get("type"))) {
                out.append(String.valueOf(content.get("text")));
            }
        }
        return out.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class StubRuntime implements HttpHandler {
        private final HttpServer server;
        private final List<String> requests = new ArrayList<String>();

        StubRuntime() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this);
        }

        void start() {
            server.start();
        }

        void stop() {
            server.stop(0);
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getRequestHeaders();
            StringBuilder request = new StringBuilder();
            request.append(exchange.getRequestMethod()).append(" ").append(exchange.getRequestURI().getPath());
            if (exchange.getRequestURI().getRawQuery() != null) {
                request.append("?").append(exchange.getRequestURI().getRawQuery());
            }
            if (headers.containsKey("Authorization")) {
                request.append(" Authorization=").append(headers.getFirst("Authorization"));
            }
            requests.add(request.toString());

            String path = exchange.getRequestURI().getPath();
            String rawQuery = exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery();
            String response;
            if ("/health".equals(path)) {
                response = "{\"ok\":true,\"service\":\"dreambot-mcp\",\"auth\":\"none\"}";
            } else if ("/state".equals(path)) {
                response = "{\"ok\":true"
                    + ",\"account\":{\"username\":\"agent@example.com\",\"account_identifier\":\"agent-id\",\"local_player_name\":\"stub-player\",\"logged_in\":true,\"members\":false,\"has_members_access\":false,\"membership_left\":0,\"account_status\":\"NORMAL\",\"ironman\":false,\"group_ironman\":false,\"ultimate_ironman\":false}"
                    + ",\"client\":{\"logged_in\":true,\"game_state\":\"LOGGED_IN\",\"game_state_id\":30,\"game_tick\":10,\"fps\":50,\"world\":301,\"plane\":0,\"destination\":{\"x\":1,\"y\":2,\"z\":0},\"camera\":{\"pitch\":100,\"yaw\":200,\"x\":1,\"y\":2,\"z\":3,\"zoom\":300,\"mode\":\"FIXED\"},\"combat\":{\"level\":3,\"health_percent\":100,\"special_percent\":0,\"special_active\":false,\"in_wilderness\":false,\"wilderness_level\":0,\"in_multi\":false}}"
                    + ",\"local_player\":{\"name\":\"stub-player\",\"level\":3,\"health_percent\":100,\"distance\":0,\"on_screen\":true,\"tile\":{\"x\":3200,\"y\":3200,\"z\":0},\"bounds\":{\"x\":1,\"y\":2,\"width\":3,\"height\":4},\"actions\":[\"Follow\",\"Trade\"]}"
                    + ",\"skills\":{\"ATTACK\":{\"level\":1,\"boosted\":1,\"xp\":0}}"
                    + ",\"quests\":{\"quest_points\":7,\"quests\":[{\"name\":\"Cook's Assistant\",\"type\":\"FREE\",\"state\":\"NOT_STARTED\",\"started\":false,\"finished\":false,\"settings\":[0]}]}"
                    + ",\"inventory\":[{\"name\":\"Coins\",\"id\":995,\"amount\":1,\"slot\":0,\"actions\":[\"Use\"]}]"
                    + ",\"equipment\":[]"
                    + ",\"bank\":{\"open\":false,\"loaded\":false,\"cached\":false,\"items\":[]}"
                    + "}";
            } else if ("/screenshot".equals(path)) {
                response = "{\"ok\":true,\"target\":\"canvas\",\"mime_type\":\"image/png\",\"width\":1,\"height\":1,\"data_base64\":\"iVBORw0KGgo=\"}";
            } else if ("/quests".equals(path)) {
                if (rawQuery.contains("all=false")) {
                    response = "{\"ok\":true,\"quest_points\":7}";
                } else {
                    response = "{\"ok\":true,\"quest_points\":7,\"quests\":[{\"name\":\"Cook's Assistant\",\"type\":\"FREE\",\"state\":\"NOT_STARTED\",\"started\":false,\"finished\":false,\"config_id\":29,\"settings\":[0,1,2]}]}";
                }
            } else if ("/login/status".equals(path)) {
                response = "{\"ok\":true,\"login\":{\"logged_in\":true,\"login_index\":0,\"game_state\":\"LOGGED_IN\",\"game_state_id\":30,\"has_focus\":true,\"username\":\"agent@example.com\",\"account_identifier\":\"agent-id\",\"local_player_name\":\"stub-player\",\"login_response\":\"null\",\"recent_messages\":[{\"clean\":\"hello\"}]}}";
            } else if ("/dialogue".equals(path)) {
                response = "{\"ok\":true,\"dialogue\":{\"in_dialogue\":true,\"can_continue\":true,\"can_enter_input\":false,\"processing\":false,\"npc_dialogue\":\"Hi\",\"options_available\":false,\"options\":[],\"screen_text\":{\"combined\":[\"Hi\"],\"widgets\":[{\"clean\":\"Hi\",\"bounds\":{\"x\":1,\"y\":2,\"width\":3,\"height\":4}}]},\"recent_messages\":[{\"clean\":\"hello\"}]}}";
            } else if ("/camera".equals(path)) {
                response = "{\"ok\":true,\"camera\":{\"pitch\":100,\"yaw\":200,\"x\":1,\"y\":2,\"z\":3,\"zoom\":300,\"mode\":\"FIXED\",\"min_zoom\":0,\"max_zoom\":2000,\"lowest_pitch\":128}}";
            } else if ("/objects".equals(path)) {
                response = "{\"ok\":true,\"objects\":[{\"name\":\"Tree\",\"id\":1276,\"real_id\":1276,\"index\":1,\"distance\":3,\"on_screen\":true,\"tile\":{\"x\":3201,\"y\":3200,\"z\":0},\"clickable_point\":{\"x\":10,\"y\":20},\"center_point\":{\"x\":11,\"y\":21},\"bounds\":{\"x\":1,\"y\":2,\"width\":3,\"height\":4},\"actions\":[\"Chop down\"]}]}";
            } else if ("/widgets".equals(path)) {
                response = "{\"ok\":true,\"widgets\":[{\"widget_id\":162,\"id\":1,\"raw_id\":2,\"real_id\":3,\"parent_id\":4,\"child_id\":5,\"grandchild_id\":-1,\"text\":\"Run\",\"name\":\"\",\"tooltip\":\"\",\"item_id\":-1,\"item_stack\":0,\"visible\":true,\"bounds\":{\"x\":1,\"y\":2,\"width\":3,\"height\":4},\"actions\":[\"Toggle\"]}]}";
            } else if ("/ui-text".equals(path)) {
                response = "{\"ok\":true,\"dialogue_api\":{\"in_dialogue\":false,\"can_continue\":false,\"can_enter_input\":false,\"processing\":false,\"npc_dialogue\":\"\",\"options_available\":false,\"options\":[]},\"screen_text\":{\"combined\":[\"Run\",\"Inventory\"],\"widgets\":[{\"clean\":\"Run\",\"bounds\":{\"x\":1,\"y\":2,\"width\":3,\"height\":4}}]},\"recent_messages\":[{\"type\":\"GAME\",\"username\":\"\",\"message\":\"Welcome\",\"clean\":\"Welcome\",\"clan\":\"\",\"time\":1,\"seen_at_ms\":2}]}";
            } else if ("/ui-summary".equals(path)) {
                response = "{\"ok\":true,\"dialogue\":{\"in_dialogue\":false,\"can_continue\":false,\"can_enter_input\":false,\"processing\":false,\"npc_dialogue\":\"\",\"options_available\":false,\"options\":[]},\"message_box\":{\"visible\":true,\"text\":\"Hi\",\"can_continue\":true,\"waiting\":false,\"widgets\":[{\"clean\":\"Hi\"}]},\"client_messages\":{\"message0\":\"\",\"message1\":\"\",\"message2\":\"\",\"login_screen_text\":[]},\"hint_arrow\":{\"exists\":false,\"type\":\"NONE\",\"tile\":null},\"menu\":{\"visible\":false,\"count\":0,\"rows\":[]},\"selected_widget\":{\"selected\":false,\"widget\":null},\"recent_messages\":[]}";
            } else if ("/agent-task/logs".equals(path)) {
                response = "{\"ok\":true,\"logs\":[{\"at_ms\":1,\"message\":\"started\"}],\"agent_task\":{\"status\":\"running\",\"task_id\":\"task-1\",\"name\":\"agent-task\",\"class_name\":\"Task\",\"source_hash\":\"abc\",\"loop_count\":1,\"running\":true,\"stop_requested\":false,\"stop_reason\":\"\",\"last_error\":\"\",\"last_tick_age_ms\":10,\"next_tick_in_ms\":20}}";
            } else if ("/agent-task/status".equals(path)) {
                response = "{\"ok\":true,\"agent_task\":{\"status\":\"running\",\"task_id\":\"task-1\",\"name\":\"agent-task\",\"class_name\":\"Task\",\"source_hash\":\"abc\",\"loop_count\":1,\"running\":true,\"stop_requested\":false,\"stop_reason\":\"\",\"last_error\":\"\",\"last_tick_age_ms\":10,\"next_tick_in_ms\":20}}";
            } else if ("/action/walk".equals(path)) {
                response = "{\"ok\":true,\"action\":\"walk\",\"result\":true}";
            } else if ("/action/object/interact".equals(path)) {
                response = "{\"ok\":true,\"action\":\"object.interact\",\"result\":true,\"target\":{\"name\":\"Tree\",\"id\":1276,\"distance\":3,\"on_screen\":true,\"tile\":{\"x\":3201,\"y\":3200,\"z\":0},\"bounds\":{\"x\":1,\"y\":2,\"width\":3,\"height\":4},\"actions\":[\"Chop down\"]}}";
            } else {
                response = "{\"ok\":true,\"path\":\"" + path + "\"}";
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private static final class FakeRuntime implements RuntimeClient {
        @Override
        public Map<String, Object> get(String path) {
            return response(path);
        }

        @Override
        public Map<String, Object> post(String path, Map<String, Object> body) {
            return response(path);
        }

        @Override
        public Map<String, Object> post(String path, Map<String, Object> body, int timeoutMs) {
            return response(path);
        }

        private Map<String, Object> response(String path) {
            return Json.obj("ok", Boolean.TRUE, "path", path);
        }
    }
}
