package com.pernasua.dreambot.mcp;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DreamBotTools {
    private static final double MAX_SLEEP_SECONDS = 7.0 * 24.0 * 60.0 * 60.0;
    private static final int DEFAULT_UI_TEXT_LIMIT = 25;
    private static final int DEFAULT_UI_TEXT_MESSAGE_LIMIT = 10;
    private static final int DEFAULT_UI_SUMMARY_MESSAGE_LIMIT = 10;
    private static final int DEFAULT_WIDGET_LIMIT = 15;
    private static final int DEFAULT_AGENT_LOG_LIMIT = 20;

    private final Config config;
    private final RuntimeClient runtime;
    private final SelfInstaller installer;
    private final boolean includeInstallTools;

    DreamBotTools(Config config) {
        this(config, new LocalHttp(config.runtimeUrl, 45000), true);
    }

    DreamBotTools(Config config, RuntimeClient runtime, boolean includeInstallTools) {
        this.config = config;
        this.runtime = runtime;
        this.installer = new SelfInstaller();
        this.includeInstallTools = includeInstallTools;
    }

    static List<Tool> runtimeTools(RuntimeClient runtime, boolean allowLifecycle) {
        Config config = new Config("http://127.0.0.1:17653", allowLifecycle, java.nio.file.Paths.get("."));
        return new DreamBotTools(config, runtime, false).build();
    }

    List<Tool> build() {
        List<Tool> tools = new ArrayList<Tool>();
        runtimeUtilityTools(tools);
        runtimeTelemetryTools(tools);
        runtimeReadTools(tools);
        runtimeActionTools(tools);
        if (includeInstallTools) {
            installTools(tools);
        }
        return compactResponseTools(tools);
    }

    private void runtimeUtilityTools(List<Tool> tools) {
        Map<String, Object> sleepProps = props();
        sleepProps.put("seconds", Schemas.number("Seconds to sleep before returning. Maximum is 604800 seconds.", 0.0, Double.valueOf(MAX_SLEEP_SECONDS), null));
        tools.add(new Tool("dreambot_sleep", "Sleep", "Sleep for the requested number of seconds before returning.", Schemas.object(sleepProps, "seconds"), args -> sleep(args)));
    }

    private void runtimeTelemetryTools(List<Tool> tools) {
        tools.add(new Tool("dreambot_runtime_health", "Runtime Health", "Check the in-client DreamBot MCP runtime.", Schemas.object(), args -> runtime.get("/health")));
        tools.add(new Tool("dreambot_state", "Account State", "Return the in-client DreamBot MCP account snapshot.", Schemas.object(), args -> runtime.get("/state")));
        tools.add(new Tool("dreambot_account", "Account", "Return current DreamBot account metadata.", Schemas.object(), args -> runtime.get("/account")));
        tools.add(new Tool("dreambot_login_status", "Login Status", "Return current login-screen and logged-in status.", Schemas.object(), args -> runtime.get("/login/status")));
        tools.add(new Tool("dreambot_client", "Client", "Return current client/login/world/player metadata.", Schemas.object(), args -> runtime.get("/client")));
        tools.add(new Tool("dreambot_skills", "Skills", "Return current skill levels and XP.", Schemas.object(), args -> runtime.get("/skills")));
        tools.add(new Tool("dreambot_inventory", "Inventory", "Return current inventory items.", Schemas.object(), args -> runtime.get("/inventory")));
        tools.add(new Tool("dreambot_equipment", "Equipment", "Return current equipment items.", Schemas.object(), args -> runtime.get("/equipment")));
        tools.add(new Tool("dreambot_bank", "Bank", "Return the retained bank cache.", Schemas.object(), args -> runtime.get("/bank")));
        tools.add(new Tool("dreambot_quests", "Quest Summary", "Return quest summary from the in-client DreamBot MCP runtime.", Schemas.object(), args -> runtime.get("/quests?all=false")));
    }

    private void runtimeReadTools(List<Tool> tools) {
        Map<String, Object> screenshotProps = props();
        screenshotProps.put("target", Schemas.string("Capture target.", list("canvas", "screen"), "canvas"));
        tools.add(new Tool("dreambot_screenshot", "Screenshot", "Capture a PNG screenshot of the DreamBot game canvas or full display.", Schemas.object(screenshotProps), args -> runtime.get(query("/screenshot", Validators.queryParams(args, "target")))));

        tools.add(new Tool("dreambot_dialogue_state", "Dialogue State", "Return current dialogue state.", Schemas.object(), args -> runtime.get("/dialogue")));
        tools.add(new Tool("dreambot_ground_items", "Ground Items", "Return nearby ground items.", Schemas.object(), args -> runtime.get("/ground-items")));
        tools.add(new Tool("dreambot_npcs", "NPCs", "Return nearby NPCs.", Schemas.object(), args -> runtime.get("/npcs")));
        tools.add(new Tool("dreambot_objects", "Objects", "Return nearby game objects.", Schemas.object(), args -> runtime.get("/objects")));
        tools.add(new Tool("dreambot_players", "Players", "Return nearby players.", Schemas.object(), args -> runtime.get("/players")));
        tools.add(new Tool("dreambot_camera", "Camera", "Return current camera pitch/yaw/zoom.", Schemas.object(), args -> runtime.get("/camera")));

        Map<String, Object> uiTextProps = props();
        uiTextProps.put("visible_only", Schemas.bool("Only include visible widgets.", true));
        uiTextProps.put("limit", Schemas.integer("Maximum widget rows.", 1, 500, Integer.valueOf(DEFAULT_UI_TEXT_LIMIT)));
        uiTextProps.put("message_limit", Schemas.integer("Maximum recent messages.", 0, 200, Integer.valueOf(DEFAULT_UI_TEXT_MESSAGE_LIMIT)));
        tools.add(new Tool("dreambot_ui_text", "UI Text", "Return visible widget text, dialogue text, and recent chat messages.", Schemas.object(uiTextProps), args -> runtime.get(query("/ui-text", uiTextQuery(args)))));

        Map<String, Object> uiSummaryProps = props();
        uiSummaryProps.put("message_limit", Schemas.integer("Maximum recent messages.", 0, 200, Integer.valueOf(DEFAULT_UI_SUMMARY_MESSAGE_LIMIT)));
        tools.add(new Tool("dreambot_ui_summary", "UI Summary", "Return compact structured UI state for automation decisions.", Schemas.object(uiSummaryProps), args -> runtime.get(query("/ui-summary", uiSummaryQuery(args)))));

        Map<String, Object> widgetProps = widgetQueryProps();
        tools.add(new Tool("dreambot_widgets", "Widgets", "Query visible widgets by id, text, action, and bounds.", Schemas.object(widgetProps), args -> runtime.get(query("/widgets", widgetReadQuery(args)))));

        Map<String, Object> settingsProps = props();
        settingsProps.put("configs", Schemas.array("Config/varp ids to read.", Schemas.integer("Config id.", 0, 200000)));
        settingsProps.put("varps", Schemas.array("Varp ids to read.", Schemas.integer("Varp id.", 0, 200000)));
        settingsProps.put("varbits", Schemas.array("Varbit ids to read.", Schemas.integer("Varbit id.", 0, 200000)));
        tools.add(new Tool("dreambot_settings", "Settings", "Read selected config/varp and varbit values.", Schemas.object(settingsProps), args -> runtime.get(query("/settings", Validators.queryParams(args, "configs", "varps", "varbits")))));

        Map<String, Object> questProps = props();
        questProps.put("name", Schemas.string("Quest name or enum.", ""));
        questProps.put("all", Schemas.bool("Return all quest states when name is empty.", false));
        tools.add(new Tool("dreambot_quest_state", "Quest State", "Return quest state by DreamBot quest enum/name, or all states.", Schemas.object(questProps), args -> runtime.get(query("/quests", questQuery(args)))));

        Map<String, Object> projectionProps = props();
        projectionProps.put("x", Schemas.integer("World tile X.", 0, 16383));
        projectionProps.put("y", Schemas.integer("World tile Y.", 0, 16383));
        projectionProps.put("z", Schemas.integer("Plane.", 0, 3, Integer.valueOf(0)));
        tools.add(new Tool("dreambot_projection_tile", "Tile Projection", "Project a world tile to game-screen and minimap coordinates.", Schemas.object(projectionProps, "x", "y"), args -> runtime.get(query("/projection/tile", Validators.queryParams(args, "x", "y", "z")))));
    }

    private void runtimeActionTools(List<Tool> tools) {
        Map<String, Object> tileProps = tileProps();
        tools.add(new Tool("dreambot_walk", "Walk", "Queue a DreamBot Walking.walk(Tile) action.", Schemas.object(tileProps, "x", "y"), args -> runtime.post("/action/walk", Validators.tile(args)), true));

        Map<String, Object> tileClickProps = tileProps();
        tileClickProps.put("minimap", Schemas.bool("Click through minimap destination.", false));
        tileClickProps.put("right", Schemas.bool("Right-click instead of left-click.", false));
        tools.add(new Tool("dreambot_tile_click", "Tile Click", "Click a world tile through DreamBot mouse input.", Schemas.object(tileClickProps, "x", "y"), args -> {
            Map<String, Object> body = Validators.tile(args);
            body.put("minimap", Boolean.valueOf(Validators.optionalBool(args, "minimap", false)));
            body.put("right", Boolean.valueOf(Validators.optionalBool(args, "right", false)));
            return runtime.post("/action/tile/click", body);
        }, true));

        Map<String, Object> namedProps = props();
        namedProps.put("name", Schemas.string("Entity name."));
        namedProps.put("action", Schemas.string("Interaction action."));
        tools.add(new Tool("dreambot_npc_interact", "NPC Interact", "Interact with the nearest matching NPC.", Schemas.object(namedProps, "name", "action"), args -> runtime.post("/action/npc/interact", Validators.namedInteract(args)), true));
        tools.add(new Tool("dreambot_object_interact", "Object Interact", "Interact with the nearest matching game object.", Schemas.object(namedProps, "name", "action"), args -> runtime.post("/action/object/interact", Validators.namedInteract(args)), true));
        tools.add(new Tool("dreambot_player_interact", "Player Interact", "Interact with the nearest matching player.", Schemas.object(namedProps, "name", "action"), args -> runtime.post("/action/player/interact", Validators.namedInteract(args)), true));

        tools.add(new Tool("dreambot_ground_item_interact", "Ground Item Interact", "Interact with a nearby ground item by name/id/tile.", Schemas.object(targetProps("Ground item"), "action"), args -> runtime.post("/action/ground-item/interact", Validators.target(args, true)), true));
        tools.add(new Tool("dreambot_inventory_interact", "Inventory Interact", "Interact with an inventory slot or named item.", Schemas.object(inventoryProps()), args -> runtime.post("/action/inventory/interact", Validators.inventoryInteract(args)), true));

        Map<String, Object> equipmentProps = props();
        equipmentProps.put("slot", Schemas.string("Equipment slot.", Validators.EQUIPMENT_SLOTS, null));
        equipmentProps.put("action", Schemas.string("Equipment action."));
        tools.add(new Tool("dreambot_equipment_interact", "Equipment Interact", "Interact with an equipped item by slot.", Schemas.object(equipmentProps, "slot", "action"), args -> {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("slot", Validators.enumValue(args, "slot", Validators.EQUIPMENT_SLOTS));
            body.put("action", Validators.requireString(args, "action", 64));
            return runtime.post("/action/equipment/interact", body);
        }, true));

        tools.add(new Tool("dreambot_item_on_object", "Item On Object", "Use an inventory item on a target game object.", Schemas.object(itemOnProps("Object")), args -> runtime.post("/action/item/on-object", itemOnBody(args)), true));
        tools.add(new Tool("dreambot_item_on_npc", "Item On NPC", "Use an inventory item on a target NPC.", Schemas.object(itemOnProps("NPC")), args -> runtime.post("/action/item/on-npc", itemOnBody(args)), true));
        tools.add(new Tool("dreambot_item_on_item", "Item On Item", "Use one inventory item on another.", Schemas.object(itemOnItemProps()), args -> runtime.post("/action/item/on-item", Validators.itemOnItem(args)), true));

        Map<String, Object> spellProps = props();
        spellProps.put("spell", Schemas.string("Normal spell name, such as WIND_STRIKE or Wind Strike."));
        tools.add(new Tool("dreambot_spell_cast", "Spell Cast", "Cast a normal spell.", Schemas.object(spellProps, "spell"), args -> runtime.post("/action/spell/cast", Validators.spell(args)), true));
        Map<String, Object> spellTargetProps = targetProps("NPC");
        spellTargetProps.put("spell", Schemas.string("Normal spell name, such as WIND_STRIKE or Wind Strike."));
        tools.add(new Tool("dreambot_spell_on_npc", "Spell On NPC", "Cast a normal spell on a target NPC.", Schemas.object(spellTargetProps, "spell"), args -> runtime.post("/action/spell/on-npc", Validators.spellTarget(args)), true));

        Map<String, Object> tabProps = props();
        tabProps.put("tab", Schemas.string("Tab name.", Validators.TABS, null));
        tools.add(new Tool("dreambot_tab_open", "Open Tab", "Open a DreamBot tab by enum name.", Schemas.object(tabProps, "tab"), args -> runtime.post("/action/tab/open", Json.obj("tab", Validators.enumValue(args, "tab", Validators.TABS))), true));

        Map<String, Object> continueProps = props();
        continueProps.put("method", Schemas.string("continue, space, or click.", list("continue", "space", "click"), "continue"));
        tools.add(new Tool("dreambot_dialogue_continue", "Dialogue Continue", "Continue the active dialogue.", Schemas.object(continueProps), args -> runtime.post("/action/dialogue/continue", Json.obj("method", Validators.optionalString(args, "method", "continue", 16, true))), true));

        Map<String, Object> chooseProps = props();
        chooseProps.put("text", Schemas.string("Dialogue option text.", ""));
        chooseProps.put("index", Schemas.integer("Dialogue option index.", 0, 20));
        chooseProps.put("contains", Schemas.bool("Use substring matching.", false));
        tools.add(new Tool("dreambot_dialogue_choose", "Dialogue Choose", "Choose a dialogue option by text or index.", Schemas.object(chooseProps), args -> {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            String text = Validators.optionalString(args, "text", "", 256, true);
            int index = Validators.optionalInt(args, "index", -1, -1, 20);
            if (text.isEmpty() && index < 0) {
                throw new ToolExecutionException("dialogue choice requires text or index");
            }
            if (!text.isEmpty()) {
                body.put("text", text);
            }
            if (index >= 0) {
                body.put("index", Integer.valueOf(index));
            }
            body.put("contains", Boolean.valueOf(Validators.optionalBool(args, "contains", false)));
            return runtime.post("/action/dialogue/choose", body);
        }, true));

        tools.add(new Tool("dreambot_widget_click", "Widget Click", "Click/interact with a widget by id/text/action.", Schemas.object(widgetActionProps()), args -> runtime.post("/action/widget/click", Validators.widget(args)), true));
        Map<String, Object> widgetTypeProps = widgetActionProps();
        widgetTypeProps.put("input", Schemas.string("Text to type into the widget."));
        tools.add(new Tool("dreambot_widget_type", "Widget Type", "Optionally focus a widget and type text.", Schemas.object(widgetTypeProps, "input"), args -> {
            Map<String, Object> body = Validators.widget(args);
            body.put("input", Validators.requireString(args, "input", 2048));
            return runtime.post("/action/widget/type", body);
        }, true));

        Map<String, Object> waitProps = tileProps();
        waitProps.put("type", Schemas.string("Wait condition type.", list("tile", "logged_in", "dialogue", "widget", "inventory", "idle", "varp", "varbit", "quest"), "tile"));
        waitProps.put("timeout_ms", Schemas.integer("Maximum wait time.", 1, 60000, Integer.valueOf(5000)));
        waitProps.put("name", Schemas.string("Optional target name.", ""));
        waitProps.put("distance", Schemas.integer("Maximum tile distance for tile waits.", 0, 50, Integer.valueOf(0)));
        waitProps.put("value", Schemas.bool("Expected boolean value for logged_in waits.", true));
        tools.add(new Tool("dreambot_wait_until", "Wait Until", "Wait for a DreamBot MCP runtime condition.", Schemas.object(waitProps, "type"), args -> {
            Map<String, Object> body = new LinkedHashMap<String, Object>(args);
            int timeoutMs = Validators.optionalInt(args, "timeout_ms", 5000, 1, 60000);
            body.put("timeout_ms", Integer.valueOf(timeoutMs));
            return runtime.post("/action/wait-until", body, Math.min(100000, timeoutMs + 35000));
        }, true));

        tools.add(new Tool("dreambot_camera_set", "Camera Set", "Set camera yaw, pitch, and/or zoom.", Schemas.object(cameraProps()), args -> runtime.post("/action/camera/set", cameraBody(args)), true));
        Map<String, Object> rotateProps = tileProps();
        rotateProps.put("target", Schemas.string("Camera target type.", list("tile", "player"), "tile"));
        tools.add(new Tool("dreambot_camera_rotate_to", "Camera Rotate To", "Rotate camera toward a tile/player.", Schemas.object(rotateProps, "target"), args -> runtime.post("/action/camera/rotate-to", new LinkedHashMap<String, Object>(args)), true));

        tools.add(new Tool("dreambot_client_focus", "Client Focus", "Focus the game client.", Schemas.object(), args -> runtime.post("/action/client/focus", Json.obj()), true));
        tools.add(new Tool("dreambot_mouse_click", "Mouse Click", "Click a viewport pixel.", Schemas.object(pixelProps(true), "x", "y"), args -> runtime.post("/action/mouse/click", mouseBody(args)), true));
        tools.add(new Tool("dreambot_mouse_move", "Mouse Move", "Move to a viewport pixel.", Schemas.object(pixelProps(false), "x", "y"), args -> runtime.post("/action/mouse/move", mouseBody(args)), true));
        tools.add(new Tool("dreambot_mouse_drag", "Mouse Drag", "Drag between viewport pixels.", Schemas.object(dragProps(), "x", "y", "to_x", "to_y"), args -> runtime.post("/action/mouse/drag", dragBody(args)), true));
        tools.add(new Tool("dreambot_mouse_wheel", "Mouse Wheel", "Scroll the mouse wheel at a viewport pixel.", Schemas.object(wheelProps(), "rotation"), args -> runtime.post("/action/mouse/wheel", wheelBody(args)), true));

        Map<String, Object> keyboardTypeProps = props();
        keyboardTypeProps.put("text", Schemas.string("Text to type."));
        tools.add(new Tool("dreambot_keyboard_type", "Keyboard Type", "Type text through DreamBot keyboard input.", Schemas.object(keyboardTypeProps, "text"), args -> runtime.post("/action/keyboard/type", Json.obj("text", Validators.requireString(args, "text", 4096))), true));
        Map<String, Object> keyboardKeyProps = props();
        keyboardKeyProps.put("key", Schemas.string("Key name."));
        tools.add(new Tool("dreambot_keyboard_key", "Keyboard Key", "Send one keyboard key.", Schemas.object(keyboardKeyProps, "key"), args -> runtime.post("/action/keyboard/key", Json.obj("key", Validators.requireString(args, "key", 64))), true));

        Map<String, Object> chatProps = props();
        chatProps.put("message", Schemas.string("Public chat message to send."));
        chatProps.put("press_enter", Schemas.bool("Submit the message after typing it.", true));
        tools.add(new Tool("dreambot_chat_say", "Chat Say", "Open chat, type a message, and optionally submit it.", Schemas.object(chatProps, "message"), args -> {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("message", Validators.requireString(args, "message", 256));
            body.put("press_enter", Boolean.valueOf(Validators.optionalBool(args, "press_enter", true)));
            return runtime.post("/action/chat/say", body);
        }, true));

        Map<String, Object> loginProps = props();
        loginProps.put("username", Schemas.string("Account username or email."));
        loginProps.put("password", Schemas.string("Account password."));
        loginProps.put("submit", Schemas.bool("Press enter after typing credentials.", true));
        tools.add(new Tool("dreambot_login_type_credentials", "Login Type Credentials", "Type account credentials into the DreamBot login screen without any external account database.", Schemas.object(loginProps, "username", "password"), args -> {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("username", Validators.requireString(args, "username", 320));
            body.put("password", Validators.requireString(args, "password", 320));
            body.put("submit", Boolean.valueOf(Validators.optionalBool(args, "submit", true)));
            return runtime.post("/action/login/type-credentials", body);
        }, true));

        Map<String, Object> reconnectProps = props();
        reconnectProps.put("timeout_ms", Schemas.integer("Maximum login wait time.", 1000, 90000, Integer.valueOf(30000)));
        tools.add(new Tool("dreambot_login_reconnect", "Login Reconnect", "Manually log back in with the DreamBot account configured in this client.", Schemas.object(reconnectProps), args -> {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            int timeoutMs = Validators.optionalInt(args, "timeout_ms", 30000, 1000, 90000);
            body.put("timeout_ms", Integer.valueOf(timeoutMs));
            return runtime.post("/action/login/reconnect", body, Math.min(95000, timeoutMs + 5000));
        }, true));

        Map<String, Object> javaProps = props();
        javaProps.put("code", Schemas.string("Java expression or block to compile and run on the DreamBot script thread."));
        javaProps.put("mode", Schemas.string("Java snippet mode.", list("expression", "block"), "expression"));
        tools.add(new Tool("dreambot_java_eval", "Java Eval", "Compile and run a Java expression or block inside the DreamBot MCP script. Lambdas and streams are not supported; use ordinary loops.", Schemas.object(javaProps, "code"), args -> {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("code", Validators.requireString(args, "code", 20000));
            body.put("mode", Validators.optionalString(args, "mode", "expression", 32, true));
            return runtime.post("/action/java/eval", body);
        }, true));

        Map<String, Object> taskStartProps = props();
        taskStartProps.put("name", Schemas.string("Task display name.", "agent-task"));
        taskStartProps.put("mode", Schemas.string("Task source mode.", list("loop", "class", "full_class"), "loop"));
        taskStartProps.put("source", Schemas.string("Loop body or full AgentTask class source. In loop mode, use ctx.log(...) for captured logs, ctx.stop(reason) to stop, and return a delay in milliseconds. A negative returned delay stops the task."));
        taskStartProps.put("class_name", Schemas.string("Class name for class mode when it cannot be inferred.", ""));
        tools.add(new Tool("dreambot_agent_task_start", "Agent Task Start", "Compile and start a resident Java task that runs from DreamBot MCP onLoop. In loop mode, source is the body of onLoop(AgentTask.Context ctx). Use ctx.log(...) for captured task logs, ctx.stop(reason) to stop, and return a delay in milliseconds. Returning a negative delay also stops the task.", Schemas.object(taskStartProps, "source"), args -> {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("name", Validators.optionalString(args, "name", "agent-task", 128, true));
            body.put("mode", Validators.optionalString(args, "mode", "loop", 32, true));
            body.put("source", Validators.requireString(args, "source", 50000));
            body.put("class_name", Validators.optionalString(args, "class_name", "", 128, true));
            return runtime.post("/action/agent-task/start", body, 65000);
        }, true));

        tools.add(new Tool("dreambot_agent_task_status", "Agent Task Status", "Return the resident Java agent task status.", Schemas.object(), args -> runtime.get("/agent-task/status")));

        Map<String, Object> taskLogsProps = props();
        taskLogsProps.put("limit", Schemas.integer("Maximum task log rows.", 1, 200, Integer.valueOf(DEFAULT_AGENT_LOG_LIMIT)));
        tools.add(new Tool("dreambot_agent_task_logs", "Agent Task Logs", "Return resident Java agent task lifecycle logs and messages written with ctx.log(...).", Schemas.object(taskLogsProps), args -> runtime.get(query("/agent-task/logs", agentTaskLogsQuery(args)))));

        Map<String, Object> taskStopProps = props();
        taskStopProps.put("reason", Schemas.string("Stop reason.", "requested"));
        tools.add(new Tool("dreambot_agent_task_stop", "Agent Task Stop", "Stop the resident Java agent task.", Schemas.object(taskStopProps), args -> {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("reason", Validators.optionalString(args, "reason", "requested", 256, true));
            return runtime.post("/action/agent-task/stop", body);
        }, true));

        if (config.allowLifecycle) {
            tools.add(new Tool("dreambot_client_logout", "Client Logout", "Logout the active game session.", Schemas.object(), args -> runtime.post("/action/client/logout", Json.obj()), true));
            Map<String, Object> specialProps = props();
            specialProps.put("enabled", Schemas.bool("Whether special attack should be enabled.", true));
            tools.add(new Tool("dreambot_combat_special", "Combat Special", "Toggle special attack.", Schemas.object(specialProps), args -> runtime.post("/action/combat/special", Json.obj("enabled", Boolean.valueOf(Validators.optionalBool(args, "enabled", true)))), true));
        }
    }

    private void installTools(List<Tool> tools) {
        tools.add(new Tool("dreambot_jar_info", "Jar Info", "Return the executable jar path used by this MCP process.", Schemas.object(), args -> installer.info()));
        Map<String, Object> installProps = props();
        installProps.put("scripts_dir", Schemas.string("DreamBot Scripts directory. Defaults to this MCP process config.", ""));
        tools.add(new Tool("dreambot_install_script", "Install Script", "Copy this standalone MCP jar into a DreamBot Scripts directory as DreamBotMCP.jar.", Schemas.object(installProps), args -> {
            String scriptsDir = Validators.optionalString(args, "scripts_dir", config.scriptsDir.toString(), 4096, true);
            return installer.install(java.nio.file.Paths.get(scriptsDir));
        }, true));
    }

    private List<Tool> compactResponseTools(List<Tool> tools) {
        List<Tool> wrapped = new ArrayList<Tool>();
        for (Tool tool : tools) {
            final Tool source = tool;
            wrapped.add(new Tool(
                source.name,
                source.title,
                source.description,
                withVerboseProperty(source.inputSchema),
                args -> {
                    boolean verbose = Validators.optionalBool(args, "verbose", false);
                    Map<String, Object> cleanArgs = new LinkedHashMap<String, Object>(args);
                    cleanArgs.remove("verbose");
                    Map<String, Object> payload = source.handler.call(cleanArgs);
                    return compactPayload(source.name, payload, verbose);
                },
                source.destructive
            ));
        }
        return wrapped;
    }

    private Map<String, Object> withVerboseProperty(Map<String, Object> schema) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>(schema);
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        Object existing = copy.get("properties");
        if (existing instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existingProperties = (Map<String, Object>) existing;
            properties.putAll(existingProperties);
        }
        properties.put("verbose", Schemas.bool("Return the full runtime payload, including wrapper and diagnostic fields.", false));
        copy.put("properties", properties);
        return copy;
    }

    private Map<String, Object> compactPayload(String toolName, Map<String, Object> payload, boolean verbose) {
        if (verbose || payload == null) {
            return payload;
        }

        Map<String, Object> cleaned = stripTransport(payload);
        if (!truthy(payload.get("ok"), true)) {
            cleaned.put("ok", Boolean.FALSE);
            return cleaned;
        }

        cleaned.remove("ok");
        if ("dreambot_runtime_health".equals(toolName)) {
            return compactHealth(cleaned);
        }
        if ("dreambot_java_eval".equals(toolName) && cleaned.containsKey("result")) {
            return Json.obj("result", cleaned.get("result"));
        }
        if (cleaned.containsKey("action") && cleaned.containsKey("result")) {
            return compactAction(cleaned);
        }
        if ("dreambot_state".equals(toolName)) {
            return compactState(cleaned);
        }
        if ("dreambot_account".equals(toolName) && cleaned.containsKey("account")) {
            return compactAccount(asMap(cleaned.get("account")));
        }
        if ("dreambot_login_status".equals(toolName) && cleaned.containsKey("login")) {
            return compactLogin(asMap(cleaned.get("login")));
        }
        if ("dreambot_client".equals(toolName) && cleaned.containsKey("client")) {
            return compactClient(asMap(cleaned.get("client")));
        }
        if ("dreambot_inventory".equals(toolName) && cleaned.containsKey("inventory")) {
            return Json.obj("inventory", compactItems(cleaned.get("inventory")));
        }
        if ("dreambot_equipment".equals(toolName) && cleaned.containsKey("equipment")) {
            return Json.obj("equipment", compactItems(cleaned.get("equipment")));
        }
        if ("dreambot_bank".equals(toolName) && cleaned.containsKey("bank")) {
            return compactBank(asMap(cleaned.get("bank")));
        }
        if ("dreambot_quests".equals(toolName) || "dreambot_quest_state".equals(toolName)) {
            return compactQuestPayload(cleaned);
        }
        if ("dreambot_dialogue_state".equals(toolName) && cleaned.containsKey("dialogue")) {
            return compactDialogue(asMap(cleaned.get("dialogue")));
        }
        if ("dreambot_ground_items".equals(toolName) && cleaned.containsKey("ground_items")) {
            return Json.obj("ground_items", compactEntities(cleaned.get("ground_items"), "ground_item"));
        }
        if ("dreambot_npcs".equals(toolName) && cleaned.containsKey("npcs")) {
            return Json.obj("npcs", compactEntities(cleaned.get("npcs"), "npc"));
        }
        if ("dreambot_objects".equals(toolName) && cleaned.containsKey("objects")) {
            return Json.obj("objects", compactEntities(cleaned.get("objects"), "object"));
        }
        if ("dreambot_players".equals(toolName) && cleaned.containsKey("players")) {
            return Json.obj("players", compactEntities(cleaned.get("players"), "player"));
        }
        if ("dreambot_camera".equals(toolName) && cleaned.containsKey("camera")) {
            return compactCamera(asMap(cleaned.get("camera")));
        }
        if ("dreambot_ui_text".equals(toolName)) {
            return compactUiText(cleaned);
        }
        if ("dreambot_ui_summary".equals(toolName)) {
            return compactUiSummary(cleaned);
        }
        if ("dreambot_widgets".equals(toolName) && cleaned.containsKey("widgets")) {
            return Json.obj("widgets", compactWidgets(cleaned.get("widgets")));
        }
        if ("dreambot_agent_task_status".equals(toolName) || "dreambot_agent_task_start".equals(toolName) || "dreambot_agent_task_stop".equals(toolName)) {
            return Json.obj("agent_task", compactAgentTask(asMap(cleaned.get("agent_task"))));
        }
        if ("dreambot_agent_task_logs".equals(toolName)) {
            return compactAgentTaskLogs(cleaned);
        }
        if ("dreambot_jar_info".equals(toolName) || "dreambot_install_script".equals(toolName)) {
            return compactJarPayload(cleaned);
        }

        String primaryKey = compactPrimaryKey(toolName);
        if (primaryKey != null && cleaned.containsKey(primaryKey)) {
            Object value = cleaned.get(primaryKey);
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> valueMap = (Map<String, Object>) value;
                return stripTransport(valueMap);
            }
            return Json.obj(primaryKey, value);
        }

        return cleaned.isEmpty() ? Json.obj("ok", Boolean.TRUE) : cleaned;
    }

    private Map<String, Object> compactHealth(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(payload, out, "service");
        copyIfPresent(payload, out, "port");
        copyIfPresent(payload, out, "tools");
        copyIfPresent(payload, out, "uptime_ms");
        copyIfPresent(payload, out, "queue_size");
        copyIfPresent(payload, out, "last_tick_age_ms");
        return out;
    }

    private Map<String, Object> compactAction(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(payload, out, "action");
        copyIfPresent(payload, out, "result");
        copyIfPresent(payload, out, "type");
        copyIfPresent(payload, out, "elapsed_ms");
        if (payload.containsKey("detail")) {
            out.put("detail", compactDetail(payload.get("detail")));
        }
        if (payload.containsKey("target")) {
            out.put("target", compactTarget(payload.get("target")));
        }
        if (payload.containsKey("item")) {
            out.put("item", compactItem(asMap(payload.get("item"))));
        }
        if (payload.containsKey("spell")) {
            out.put("spell", compactSpell(asMap(payload.get("spell"))));
        }
        if (payload.containsKey("camera")) {
            out.put("camera", compactCamera(asMap(payload.get("camera"))));
        }
        if (payload.containsKey("dialogue")) {
            out.put("dialogue", compactDialogue(asMap(payload.get("dialogue"))));
        }
        copyIfPresent(payload, out, "submitted");
        copyIfPresent(payload, out, "logged_in");
        copyIfPresent(payload, out, "moved");
        copyIfPresent(payload, out, "dragged");
        copyIfPresent(payload, out, "script_stop_requested");
        return out;
    }

    private Map<String, Object> compactState(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (payload.containsKey("account")) {
            out.put("account", compactAccount(asMap(payload.get("account"))));
        }
        if (payload.containsKey("client")) {
            out.put("client", compactClient(asMap(payload.get("client"))));
        }
        if (payload.containsKey("local_player")) {
            out.put("local_player", compactEntity(asMap(payload.get("local_player")), "player"));
        }
        return out;
    }

    private Map<String, Object> compactAccount(Map<String, Object> account) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyUseful(account, out, "username");
        copyUseful(account, out, "account_identifier");
        copyUseful(account, out, "local_player_name");
        copyIfPresent(account, out, "logged_in");
        copyIfPresent(account, out, "members");
        copyIfPresent(account, out, "has_members_access");
        copyIfPresent(account, out, "membership_left");
        copyUseful(account, out, "account_status");
        copyIfPresent(account, out, "ironman");
        copyIfPresent(account, out, "group_ironman");
        copyIfPresent(account, out, "ultimate_ironman");
        return out;
    }

    private Map<String, Object> compactLogin(Map<String, Object> login) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(login, out, "logged_in");
        copyIfPresent(login, out, "login_index");
        copyUseful(login, out, "game_state");
        copyIfPresent(login, out, "game_state_id");
        copyIfPresent(login, out, "has_focus");
        copyUseful(login, out, "local_player_name");
        copyUseful(login, out, "login_response");
        return out;
    }

    private Map<String, Object> compactClient(Map<String, Object> client) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(client, out, "logged_in");
        copyUseful(client, out, "game_state");
        copyIfPresent(client, out, "game_state_id");
        copyIfPresent(client, out, "game_tick");
        copyIfPresent(client, out, "plane");
        copyIfPresent(client, out, "world");
        copyIfPresent(client, out, "destination");
        if (client.containsKey("camera")) {
            out.put("camera", compactCamera(asMap(client.get("camera"))));
        }
        if (client.containsKey("combat")) {
            out.put("combat", compactCombat(asMap(client.get("combat"))));
        }
        return out;
    }

    private Map<String, Object> compactCombat(Map<String, Object> combat) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(combat, out, "level");
        copyIfPresent(combat, out, "health_percent");
        copyIfPresent(combat, out, "special_percent");
        copyIfPresent(combat, out, "special_active");
        copyIfPresent(combat, out, "in_wilderness");
        copyIfPresent(combat, out, "wilderness_level");
        copyIfPresent(combat, out, "in_multi");
        return out;
    }

    private Map<String, Object> compactBank(Map<String, Object> bank) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(bank, out, "open");
        copyIfPresent(bank, out, "loaded");
        copyIfPresent(bank, out, "cached");
        copyIfPresent(bank, out, "capacity");
        copyIfPresent(bank, out, "full_slots");
        if (bank.containsKey("items")) {
            out.put("items", compactItems(bank.get("items")));
        }
        return out;
    }

    private Map<String, Object> compactQuestPayload(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(payload, out, "quest_points");
        if (payload.containsKey("quest")) {
            out.put("quest", compactQuest(asMap(payload.get("quest"))));
        }
        if (payload.containsKey("quests")) {
            List<Object> quests = new ArrayList<Object>();
            for (Object item : asIterable(payload.get("quests"))) {
                quests.add(compactQuest(asMap(item)));
            }
            out.put("quests", quests);
        }
        return out;
    }

    private Map<String, Object> compactQuest(Map<String, Object> quest) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyUseful(quest, out, "name");
        copyUseful(quest, out, "type");
        copyUseful(quest, out, "state");
        copyIfPresent(quest, out, "started");
        copyIfPresent(quest, out, "finished");
        return out;
    }

    private Map<String, Object> compactDialogue(Map<String, Object> dialogue) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(dialogue, out, "in_dialogue");
        copyIfPresent(dialogue, out, "can_continue");
        copyIfPresent(dialogue, out, "can_enter_input");
        copyIfPresent(dialogue, out, "processing");
        copyUseful(dialogue, out, "npc_dialogue");
        copyIfPresent(dialogue, out, "options_available");
        copyIfPresent(dialogue, out, "options");
        copyIfPresent(dialogue, out, "widget_selected");
        return out;
    }

    private Map<String, Object> compactUiText(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (payload.containsKey("dialogue_api")) {
            out.put("dialogue", compactDialogue(asMap(payload.get("dialogue_api"))));
        }
        Map<String, Object> screenText = asMap(payload.get("screen_text"));
        if (screenText.containsKey("combined")) {
            out.put("screen_text", screenText.get("combined"));
        }
        if (payload.containsKey("recent_messages")) {
            out.put("recent_messages", compactMessages(payload.get("recent_messages")));
        }
        return out;
    }

    private Map<String, Object> compactUiSummary(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (payload.containsKey("dialogue")) {
            out.put("dialogue", compactDialogue(asMap(payload.get("dialogue"))));
        }
        if (payload.containsKey("message_box")) {
            out.put("message_box", compactMessageBox(asMap(payload.get("message_box"))));
        }
        if (payload.containsKey("client_messages")) {
            out.put("client_messages", compactClientMessages(asMap(payload.get("client_messages"))));
        }
        if (payload.containsKey("hint_arrow")) {
            out.put("hint_arrow", compactHintArrow(asMap(payload.get("hint_arrow"))));
        }
        if (payload.containsKey("menu")) {
            out.put("menu", compactMenu(asMap(payload.get("menu"))));
        }
        if (payload.containsKey("selected_widget")) {
            out.put("selected_widget", compactSelectedWidget(asMap(payload.get("selected_widget"))));
        }
        if (payload.containsKey("recent_messages")) {
            out.put("recent_messages", compactMessages(payload.get("recent_messages")));
        }
        return out;
    }

    private Map<String, Object> compactMessageBox(Map<String, Object> box) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(box, out, "visible");
        copyUseful(box, out, "text");
        copyUseful(box, out, "continue_text");
        copyIfPresent(box, out, "can_continue");
        copyIfPresent(box, out, "waiting");
        return out;
    }

    private Map<String, Object> compactClientMessages(Map<String, Object> messages) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyUseful(messages, out, "message0");
        copyUseful(messages, out, "message1");
        copyUseful(messages, out, "message2");
        copyIfPresent(messages, out, "login_screen_text");
        return out;
    }

    private Map<String, Object> compactHintArrow(Map<String, Object> hint) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(hint, out, "exists");
        if (truthy(hint.get("exists"), false)) {
            copyUseful(hint, out, "type");
            copyIfPresent(hint, out, "tile");
        }
        return out;
    }

    private Map<String, Object> compactMenu(Map<String, Object> menu) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(menu, out, "visible");
        copyIfPresent(menu, out, "count");
        copyUseful(menu, out, "default_action");
        copyIfPresent(menu, out, "bounds");
        if (truthy(menu.get("visible"), false) && menu.containsKey("rows")) {
            List<Object> rows = new ArrayList<Object>();
            int count = 0;
            for (Object item : asIterable(menu.get("rows"))) {
                Map<String, Object> row = asMap(item);
                Map<String, Object> compact = new LinkedHashMap<String, Object>();
                copyIfPresent(row, compact, "index");
                copyUseful(row, compact, "action");
                copyUseful(row, compact, "object");
                rows.add(compact);
                count++;
                if (count >= 10) {
                    break;
                }
            }
            out.put("rows", rows);
        }
        return out;
    }

    private Map<String, Object> compactSelectedWidget(Map<String, Object> selected) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(selected, out, "selected");
        copyIfPresent(selected, out, "selected_widget_id");
        copyIfPresent(selected, out, "selected_widget_index");
        if (selected.containsKey("widget")) {
            out.put("widget", compactWidget(asMap(selected.get("widget"))));
        }
        return out;
    }

    private List<Object> compactMessages(Object value) {
        List<Object> out = new ArrayList<Object>();
        for (Object item : asIterable(value)) {
            Map<String, Object> message = asMap(item);
            Map<String, Object> compact = new LinkedHashMap<String, Object>();
            copyUseful(message, compact, "type");
            copyUseful(message, compact, "username");
            copyUseful(message, compact, "clean");
            copyIfPresent(message, compact, "time");
            out.add(compact);
        }
        return out;
    }

    private List<Object> compactItems(Object value) {
        List<Object> out = new ArrayList<Object>();
        for (Object item : asIterable(value)) {
            out.add(compactItem(asMap(item)));
        }
        return out;
    }

    private Map<String, Object> compactItem(Map<String, Object> item) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyUseful(item, out, "name");
        copyIfPresent(item, out, "id");
        copyIfPresent(item, out, "amount");
        copyIfPresent(item, out, "slot");
        return out;
    }

    private List<Object> compactEntities(Object value, String type) {
        List<Object> out = new ArrayList<Object>();
        for (Object item : asIterable(value)) {
            out.add(compactEntity(asMap(item), type));
        }
        return out;
    }

    private Map<String, Object> compactTarget(Object value) {
        Map<String, Object> target = asMap(value);
        if (target.isEmpty()) {
            return target;
        }
        if (target.containsKey("amount") || target.containsKey("ownership")) {
            return compactEntity(target, "ground_item");
        }
        if (target.containsKey("level") || target.containsKey("skulled")) {
            return compactEntity(target, target.containsKey("skulled") ? "player" : "npc");
        }
        if (target.containsKey("widget_id") || target.containsKey("child_id")) {
            return compactWidget(target);
        }
        if (target.containsKey("slot") || target.containsKey("stackable")) {
            return compactItem(target);
        }
        return compactEntity(target, "object");
    }

    private Map<String, Object> compactEntity(Map<String, Object> entity, String type) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyUseful(entity, out, "name");
        copyIfPresent(entity, out, "id");
        if ("player".equals(type) || "npc".equals(type)) {
            copyIfPresent(entity, out, "level");
            copyIfPresent(entity, out, "health_percent");
        }
        if ("ground_item".equals(type)) {
            copyIfPresent(entity, out, "amount");
        }
        if ("player".equals(type)) {
            copyIfPresent(entity, out, "in_combat");
        }
        copyIfPresent(entity, out, "distance");
        copyIfPresent(entity, out, "on_screen");
        copyIfPresent(entity, out, "tile");
        copyIfPresent(entity, out, "actions");
        return out;
    }

    private List<Object> compactWidgets(Object value) {
        List<Object> out = new ArrayList<Object>();
        for (Object item : asIterable(value)) {
            out.add(compactWidget(asMap(item)));
        }
        return out;
    }

    private Map<String, Object> compactWidget(Map<String, Object> widget) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(widget, out, "widget_id");
        copyIfPresent(widget, out, "child_id");
        copyIfPresent(widget, out, "grandchild_id");
        copyUseful(widget, out, "text");
        copyUseful(widget, out, "name");
        copyUseful(widget, out, "tooltip");
        copyUseful(widget, out, "selected_action");
        copyUseful(widget, out, "spell");
        copyIfPresent(widget, out, "item_id");
        copyIfPresent(widget, out, "item_stack");
        copyIfPresent(widget, out, "visible");
        copyIfPresent(widget, out, "bounds");
        copyIfPresent(widget, out, "actions");
        return out;
    }

    private Map<String, Object> compactCamera(Map<String, Object> camera) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyIfPresent(camera, out, "pitch");
        copyIfPresent(camera, out, "yaw");
        copyIfPresent(camera, out, "zoom");
        copyUseful(camera, out, "mode");
        return out;
    }

    private Map<String, Object> compactSpell(Map<String, Object> spell) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyUseful(spell, out, "name");
        copyIfPresent(spell, out, "level");
        copyIfPresent(spell, out, "can_cast");
        return out;
    }

    private Object compactDetail(Object value) {
        Map<String, Object> detail = asMap(value);
        if (detail.isEmpty()) {
            return value;
        }
        if (detail.containsKey("dialogue")) {
            return Json.obj("dialogue", compactDialogue(asMap(detail.get("dialogue"))));
        }
        if (detail.containsKey("widget")) {
            return Json.obj("widget", compactWidget(asMap(detail.get("widget"))));
        }
        if (detail.containsKey("quest")) {
            return Json.obj("quest", compactQuest(asMap(detail.get("quest"))));
        }
        return detail;
    }

    private Map<String, Object> compactAgentTaskLogs(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (payload.containsKey("logs")) {
            out.put("logs", payload.get("logs"));
        }
        if (payload.containsKey("agent_task")) {
            out.put("agent_task", compactAgentTask(asMap(payload.get("agent_task"))));
        }
        return out;
    }

    private Map<String, Object> compactAgentTask(Map<String, Object> task) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyUseful(task, out, "status");
        copyUseful(task, out, "task_id");
        copyUseful(task, out, "name");
        copyIfPresent(task, out, "loop_count");
        copyIfPresent(task, out, "running");
        copyIfPresent(task, out, "stop_requested");
        copyUseful(task, out, "stop_reason");
        copyUseful(task, out, "last_error");
        copyIfPresent(task, out, "last_tick_age_ms");
        copyIfPresent(task, out, "next_tick_in_ms");
        return out;
    }

    private Map<String, Object> compactJarPayload(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        copyUseful(payload, out, "jar");
        copyUseful(payload, out, "target");
        copyUseful(payload, out, "script_jar_name");
        copyUseful(payload, out, "sha256");
        copyIfPresent(payload, out, "size_bytes");
        return out;
    }

    private String compactPrimaryKey(String toolName) {
        if ("dreambot_account".equals(toolName)) {
            return "account";
        }
        if ("dreambot_login_status".equals(toolName)) {
            return "login";
        }
        if ("dreambot_client".equals(toolName)) {
            return "client";
        }
        if ("dreambot_skills".equals(toolName)) {
            return "skills";
        }
        if ("dreambot_inventory".equals(toolName)) {
            return "inventory";
        }
        if ("dreambot_equipment".equals(toolName)) {
            return "equipment";
        }
        if ("dreambot_bank".equals(toolName)) {
            return "bank";
        }
        if ("dreambot_dialogue_state".equals(toolName)) {
            return "dialogue";
        }
        if ("dreambot_ground_items".equals(toolName)) {
            return "ground_items";
        }
        if ("dreambot_npcs".equals(toolName)) {
            return "npcs";
        }
        if ("dreambot_objects".equals(toolName)) {
            return "objects";
        }
        if ("dreambot_players".equals(toolName)) {
            return "players";
        }
        if ("dreambot_camera".equals(toolName)) {
            return "camera";
        }
        if ("dreambot_widgets".equals(toolName)) {
            return "widgets";
        }
        if ("dreambot_agent_task_status".equals(toolName) || "dreambot_agent_task_start".equals(toolName) || "dreambot_agent_task_stop".equals(toolName)) {
            return "agent_task";
        }
        return null;
    }

    private Map<String, Object> stripTransport(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<String, Object>(payload);
        out.remove("_http_status");
        out.remove("_path");
        return out;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private void copyUseful(Map<String, Object> source, Map<String, Object> target, String key) {
        if (!source.containsKey(key)) {
            return;
        }
        Object value = source.get(key);
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).isEmpty()) {
            return;
        }
        target.put(key, value);
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map)) {
            return new LinkedHashMap<String, Object>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private Iterable<?> asIterable(Object value) {
        if (value instanceof Iterable) {
            return (Iterable<?>) value;
        }
        return new ArrayList<Object>();
    }

    private boolean truthy(Object value, boolean fallback) {
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private Map<String, Object> sleep(Map<String, Object> args) {
        Object raw = args.get("seconds");
        if (!(raw instanceof Number)) {
            throw new ToolExecutionException("seconds must be a number");
        }
        double seconds = ((Number) raw).doubleValue();
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0.0 || seconds > MAX_SLEEP_SECONDS) {
            throw new ToolExecutionException("seconds must be between 0 and 604800");
        }
        long sleepMs = Math.round(seconds * 1000.0);
        long started = System.nanoTime();
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException("sleep interrupted", e);
        }
        long sleptMs = Math.max(0L, Math.round((System.nanoTime() - started) / 1000000.0));
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("ok", Boolean.TRUE);
        out.put("seconds", Double.valueOf(seconds));
        out.put("slept_ms", Long.valueOf(sleptMs));
        return out;
    }

    private Map<String, Object> itemOnBody(Map<String, Object> args) {
        Map<String, Object> body = Validators.target(args, false);
        int slot = Validators.optionalInt(args, "slot", -1, -1, 27);
        int id = Validators.optionalInt(args, "id", -1, -1, 1000000);
        String name = Validators.optionalString(args, "item_name", Validators.optionalString(args, "inventory_name", "", 128, true), 128, true);
        if (slot < 0 && id < 0 && name.isEmpty()) {
            throw new ToolExecutionException("source item requires slot, id, item_name, or inventory_name");
        }
        if (slot >= 0) {
            body.put("slot", Integer.valueOf(slot));
        }
        if (id >= 0) {
            body.put("id", Integer.valueOf(id));
        }
        if (!name.isEmpty()) {
            body.put("item_name", name);
        }
        return body;
    }

    private Map<String, Object> cameraBody(Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        if (args.containsKey("yaw")) {
            body.put("yaw", Integer.valueOf(Validators.optionalInt(args, "yaw", 0, 0, 2047)));
        }
        if (args.containsKey("pitch")) {
            body.put("pitch", Integer.valueOf(Validators.optionalInt(args, "pitch", 0, 0, 383)));
        }
        if (args.containsKey("zoom")) {
            body.put("zoom", Integer.valueOf(Validators.optionalInt(args, "zoom", 0, 0, 2000)));
        }
        if (body.isEmpty()) {
            throw new ToolExecutionException("camera_set requires yaw, pitch, or zoom");
        }
        return body;
    }

    private Map<String, Object> mouseBody(Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("x", Integer.valueOf(Validators.requireInt(args, "x", 0, 10000)));
        body.put("y", Integer.valueOf(Validators.requireInt(args, "y", 0, 10000)));
        body.put("button", Validators.optionalString(args, "button", "left", 32, true));
        return body;
    }

    private Map<String, Object> dragBody(Map<String, Object> args) {
        Map<String, Object> body = mouseBody(args);
        body.put("to_x", Integer.valueOf(Validators.requireInt(args, "to_x", 0, 10000)));
        body.put("to_y", Integer.valueOf(Validators.requireInt(args, "to_y", 0, 10000)));
        body.put("duration_ms", Integer.valueOf(Validators.optionalInt(args, "duration_ms", 600, 0, 10000)));
        return body;
    }

    private Map<String, Object> wheelBody(Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("rotation", Integer.valueOf(Validators.requireInt(args, "rotation", -100, 100)));
        if (args.containsKey("x")) {
            body.put("x", Integer.valueOf(Validators.optionalInt(args, "x", 0, 0, 10000)));
        }
        if (args.containsKey("y")) {
            body.put("y", Integer.valueOf(Validators.optionalInt(args, "y", 0, 0, 10000)));
        }
        return body;
    }

    private static String query(String path, Map<String, Object> query) {
        if (query.isEmpty()) {
            return path;
        }
        StringBuilder out = new StringBuilder(path).append("?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            if (!first) {
                out.append("&");
            }
            first = false;
            out.append(url(entry.getKey())).append("=").append(url(String.valueOf(entry.getValue())));
        }
        return out.toString();
    }

    private static Map<String, Object> uiTextQuery(Map<String, Object> args) {
        Map<String, Object> query = props();
        query.put("visible_only", Boolean.valueOf(Validators.optionalBool(args, "visible_only", true)));
        query.put("limit", Integer.valueOf(Validators.optionalInt(args, "limit", DEFAULT_UI_TEXT_LIMIT, 1, 500)));
        query.put("message_limit", Integer.valueOf(Validators.optionalInt(args, "message_limit", DEFAULT_UI_TEXT_MESSAGE_LIMIT, 0, 200)));
        return query;
    }

    private static Map<String, Object> uiSummaryQuery(Map<String, Object> args) {
        Map<String, Object> query = props();
        query.put("message_limit", Integer.valueOf(Validators.optionalInt(args, "message_limit", DEFAULT_UI_SUMMARY_MESSAGE_LIMIT, 0, 200)));
        return query;
    }

    private static Map<String, Object> widgetReadQuery(Map<String, Object> args) {
        Map<String, Object> query = Validators.queryParams(args, "widget", "child", "grandchild", "text", "action", "contains", "visible_only");
        query.put("limit", Integer.valueOf(Validators.optionalInt(args, "limit", DEFAULT_WIDGET_LIMIT, 1, 500)));
        return query;
    }

    private static Map<String, Object> questQuery(Map<String, Object> args) {
        Map<String, Object> query = props();
        String name = Validators.optionalString(args, "name", "", 128, true);
        if (!name.isEmpty()) {
            query.put("name", name);
        }
        query.put("all", Boolean.valueOf(Validators.optionalBool(args, "all", false)));
        return query;
    }

    private static Map<String, Object> agentTaskLogsQuery(Map<String, Object> args) {
        Map<String, Object> query = props();
        query.put("limit", Integer.valueOf(Validators.optionalInt(args, "limit", DEFAULT_AGENT_LOG_LIMIT, 1, 200)));
        return query;
    }

    private static String url(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> props() {
        return new LinkedHashMap<String, Object>();
    }

    private static Map<String, Object> tileProps() {
        Map<String, Object> props = props();
        props.put("x", Schemas.integer("World tile X.", 0, 16383));
        props.put("y", Schemas.integer("World tile Y.", 0, 16383));
        props.put("z", Schemas.integer("Plane.", 0, 3, Integer.valueOf(0)));
        return props;
    }

    private static Map<String, Object> targetProps(String label) {
        Map<String, Object> props = props();
        props.put("name", Schemas.string(label + " name.", ""));
        props.put("id", Schemas.integer(label + " id.", 0, 1000000));
        props.put("x", Schemas.integer("Optional world tile X.", 0, 16383));
        props.put("y", Schemas.integer("Optional world tile Y.", 0, 16383));
        props.put("z", Schemas.integer("Optional plane.", 0, 3, Integer.valueOf(0)));
        props.put("radius", Schemas.integer("Search radius.", 0, 104, Integer.valueOf(20)));
        props.put("action", Schemas.string("Interaction action.", ""));
        return props;
    }

    private static Map<String, Object> inventoryProps() {
        Map<String, Object> props = props();
        props.put("slot", Schemas.integer("Inventory slot.", 0, 27));
        props.put("name", Schemas.string("Inventory item name.", ""));
        props.put("action", Schemas.string("Item action.", ""));
        return props;
    }

    private static Map<String, Object> itemOnProps(String label) {
        Map<String, Object> props = targetProps(label);
        props.put("slot", Schemas.integer("Source inventory slot.", 0, 27));
        props.put("id", Schemas.integer("Source inventory item id.", 0, 1000000));
        props.put("item_name", Schemas.string("Source inventory item name.", ""));
        props.put("inventory_name", Schemas.string("Alias for source inventory item name.", ""));
        return props;
    }

    private static Map<String, Object> itemOnItemProps() {
        Map<String, Object> props = inventoryProps();
        props.put("id", Schemas.integer("Source inventory item id.", 0, 1000000));
        props.put("target_slot", Schemas.integer("Target inventory slot.", 0, 27));
        props.put("target_name", Schemas.string("Target inventory item name.", ""));
        props.put("target_id", Schemas.integer("Target inventory item id.", 0, 1000000));
        return props;
    }

    private static Map<String, Object> widgetQueryProps() {
        Map<String, Object> props = props();
        props.put("widget", Schemas.integer("Optional root widget id.", 0, 100000));
        props.put("child", Schemas.integer("Optional child id/index.", 0, 100000));
        props.put("grandchild", Schemas.integer("Optional grandchild id.", 0, 100000));
        props.put("text", Schemas.string("Optional text/name/tooltip filter.", ""));
        props.put("action", Schemas.string("Optional widget action filter.", ""));
        props.put("contains", Schemas.bool("Use substring matching.", true));
        props.put("visible_only", Schemas.bool("Only return visible widgets.", true));
        props.put("limit", Schemas.integer("Maximum widgets.", 1, 500, Integer.valueOf(DEFAULT_WIDGET_LIMIT)));
        return props;
    }

    private static Map<String, Object> widgetActionProps() {
        Map<String, Object> props = widgetQueryProps();
        props.remove("limit");
        return props;
    }

    private static Map<String, Object> cameraProps() {
        Map<String, Object> props = props();
        props.put("yaw", Schemas.integer("Camera yaw.", 0, 2047));
        props.put("pitch", Schemas.integer("Camera pitch.", 0, 383));
        props.put("zoom", Schemas.integer("Camera zoom.", 0, 2000));
        return props;
    }

    private static Map<String, Object> pixelProps(boolean includeButton) {
        Map<String, Object> props = props();
        props.put("x", Schemas.integer("Viewport pixel X.", 0, 10000));
        props.put("y", Schemas.integer("Viewport pixel Y.", 0, 10000));
        if (includeButton) {
            props.put("button", Schemas.string("Mouse button.", "left"));
        }
        return props;
    }

    private static Map<String, Object> dragProps() {
        Map<String, Object> props = pixelProps(false);
        props.put("to_x", Schemas.integer("Destination viewport pixel X.", 0, 10000));
        props.put("to_y", Schemas.integer("Destination viewport pixel Y.", 0, 10000));
        props.put("duration_ms", Schemas.integer("Drag duration.", 0, 10000, Integer.valueOf(600)));
        return props;
    }

    private static Map<String, Object> wheelProps() {
        Map<String, Object> props = props();
        props.put("x", Schemas.integer("Optional viewport pixel X.", 0, 10000));
        props.put("y", Schemas.integer("Optional viewport pixel Y.", 0, 10000));
        props.put("rotation", Schemas.integer("Wheel rotation.", -100, 100));
        return props;
    }

    private static List<String> list(String... values) {
        List<String> list = new ArrayList<String>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }
}
