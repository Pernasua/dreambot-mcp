package com.pernasua.dreambot.mcp;

import static com.pernasua.dreambot.mcp.RuntimeJson.boolField;
import static com.pernasua.dreambot.mcp.RuntimeJson.doubleField;
import static com.pernasua.dreambot.mcp.RuntimeJson.field;
import static com.pernasua.dreambot.mcp.RuntimeJson.intField;
import static com.pernasua.dreambot.mcp.RuntimeJson.queryBool;
import static com.pernasua.dreambot.mcp.RuntimeJson.queryInt;
import static com.pernasua.dreambot.mcp.RuntimeJson.queryString;
import static com.pernasua.dreambot.mcp.RuntimeJson.quote;
import static com.pernasua.dreambot.mcp.RuntimeJson.require;
import static com.pernasua.dreambot.mcp.RuntimeJson.stringField;
import static com.pernasua.dreambot.mcp.RuntimeText.cleanText;

import org.dreambot.api.Client;
import org.dreambot.api.methods.RSLoginResponse;
import org.dreambot.api.input.Keyboard;
import org.dreambot.api.input.Mouse;
import org.dreambot.api.input.mouse.destination.AbstractMouseDestination;
import org.dreambot.api.input.mouse.destination.impl.MiniMapTileDestination;
import org.dreambot.api.input.mouse.destination.impl.TileDestination;
import org.dreambot.api.methods.ViewportTools;
import org.dreambot.api.methods.combat.Combat;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.container.impl.equipment.EquipmentSlot;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.filter.Filter;
import org.dreambot.api.methods.hint.HintArrow;
import org.dreambot.api.methods.input.Camera;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.login.LoginUtility;
import org.dreambot.api.methods.magic.Magic;
import org.dreambot.api.methods.magic.Normal;
import org.dreambot.api.methods.magic.Spell;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.FreeQuest;
import org.dreambot.api.methods.quest.book.PaidQuest;
import org.dreambot.api.methods.quest.book.Quest;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.methods.widget.Widget;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.randoms.RandomEvent;
import org.dreambot.api.randoms.RandomManager;
import org.dreambot.api.randoms.RandomSolver;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.ChatListener;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.Entity;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.items.GroundItem;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.wrappers.widgets.Menu;
import org.dreambot.api.wrappers.widgets.MenuRow;
import org.dreambot.api.wrappers.widgets.WidgetChild;
import org.dreambot.api.wrappers.widgets.message.Message;

import java.awt.Color;
import java.awt.Canvas;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.geom.Area;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

@ScriptManifest(
    category = Category.UTILITY,
    name = "DreamBot MCP",
    description = "Standalone MCP runtime for this DreamBot client",
    author = "Pernasua",
    version = 2.41,
    image = "",
    _key = ""
)
public class DreamBotMcpScript extends AbstractScript implements ChatListener {
    private static final int MAX_ENTITIES = 40;
    private static final long AUTO_LOGIN_TIMEOUT_MS = 30000L;
    private static final long AUTO_LOGIN_ATTEMPT_INTERVAL_MS = 1200L;
    private static final long AUTO_LOGIN_POLL_MS = 150L;
    private static final String MCP_BOOTSTRAP_PROPERTY = "pernasua.mcp.bootstrap.active";
    private static final Object ACTIVE_LOCK = new Object();
    private static DreamBotMcpScript activeRuntime;

    private final BlockingQueue<RuntimeTask> queue = new LinkedBlockingQueue<>(64);
    private final RecentMessages recentMessages = new RecentMessages(200);
    private final JavaSnippetEvaluator javaSnippetEvaluator = new JavaSnippetEvaluator();
    private final AgentTaskCompiler agentTaskCompiler = new AgentTaskCompiler();
    private final AgentTaskRunner agentTaskRunner = new AgentTaskRunner();
    private final McpMetrics mcpMetrics = new McpMetrics();
    private final DreamBotMcpJson json = new DreamBotMcpJson(MAX_ENTITIES);
    private volatile ScriptSettings settings = ScriptSettings.from();
    private McpHttpEndpoint mcpEndpoint;
    private ScriptHttpServer httpServer;
    private long startedAt;
    private long lastLoopAt;
    private int mcpToolCount;
    private volatile boolean started;
    private volatile boolean initialLoginObserved;
    private volatile boolean loginSolverDisabled;
    private volatile boolean runtimeRunning;
    private volatile boolean releaseRequested;
    private volatile boolean scriptStopRequested;
    private volatile long scriptStopNotBeforeMs;
    private volatile String scriptStopReason = "";
    private volatile String mcpLocalUrlSummary = "";
    private volatile String mcpLanUrlSummary = "";
    private volatile String mcpBindSummary = "";
    private Thread runtimeThread;
    private DreamBotMcpMenu mcpMenu;

    @Override
    public void onStart() {
        start(new String[0]);
    }

    @Override
    public void onStart(String... args) {
        start(args);
    }

    private synchronized void start(String... args) {
        ScriptSettings requested = ScriptSettings.from(args);
        DreamBotMcpScript runtime;
        synchronized (ACTIVE_LOCK) {
            if (activeRuntime != null && activeRuntime.runtimeActive()) {
                runtime = activeRuntime;
            } else {
                activeRuntime = this;
                runtime = this;
            }
        }
        if (runtime != this) {
            debug("DreamBotMcpScript runtime already active at " + runtime.settings.mcpUrl());
            runtime.publishBootstrapProperties();
            runtime.ensureMenuAttached();
            if (requested.releaseScriptSlot) {
                requestScriptSlotRelease("runtime already active");
            }
            return;
        }
        startRuntime(requested);
        if (requested.releaseScriptSlot) {
            requestScriptSlotRelease("runtime started");
        }
    }

    private synchronized void startRuntime(ScriptSettings requested) {
        if (started) {
            return;
        }
        started = true;
        settings = requested == null ? ScriptSettings.from() : requested;
        refreshMcpAddressSummary();
        startedAt = System.currentTimeMillis();
        ScriptRuntimeClient runtimeClient = new ScriptRuntimeClient(this::route);
        List<Tool> tools = DreamBotTools.runtimeTools(runtimeClient, settings.allowLifecycle);
        mcpToolCount = tools.size();
        mcpEndpoint = new McpHttpEndpoint(new McpServer(tools, mcpMetrics), mcpMetrics);

        debug("DreamBotMcpScript starting: " + settings.summary());
        debug("DreamBotMcpScript HTTP runtime: " + settings.httpBaseUrl());
        debug("DreamBotMcpScript HTTP MCP endpoint: " + settings.mcpUrl() + " tools=" + mcpToolCount);
        debug("DreamBotMcpScript MCP local URL: " + mcpLocalUrlSummary);
        debug("DreamBotMcpScript MCP LAN URLs: " + mcpLanUrlSummary);
        if (settings.loginSolverPolicy == LoginSolverPolicy.DISABLED) {
            disableLoginSolver();
        } else if (settings.loginSolverPolicy == LoginSolverPolicy.AFTER_INITIAL_LOGIN) {
            debug("DreamBotMcpScript leaving Login Handler enabled for initial login only");
        } else {
            debug("DreamBotMcpScript leaving Login Handler random solver enabled");
        }
        startHttpServer();
        startRuntimeLoop();
        publishBootstrapProperties();
        ensureMenuAttached();
    }

    private void publishBootstrapProperties() {
        System.setProperty(MCP_BOOTSTRAP_PROPERTY, "true");
        System.setProperty("pernasua.mcp.port", String.valueOf(settings.port));
        System.setProperty("pernasua.mcp.url", settings.mcpUrl());
    }

    private synchronized void ensureMenuAttached() {
        if (mcpMenu == null) {
            mcpMenu = DreamBotMcpMenu.attach(this);
        } else {
            mcpMenu.ensureAttached();
            mcpMenu.refresh();
        }
    }

    @Override
    public boolean onSolverStart(RandomSolver solver) {
        if (shouldBlockLoginSolver() && isLoginSolver(solver)) {
            debug("DreamBotMcpScript blocked random solver: " + solver.getEventString());
            return false;
        }
        return true;
    }

    @Override
    public int onLoop() {
        return settings != null && settings.releaseScriptSlot ? 600 : 100;
    }

    private void startRuntimeLoop() {
        runtimeRunning = true;
        runtimeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runtimeLoop();
            }
        }, "dreambot-mcp-runtime-loop");
        runtimeThread.setDaemon(true);
        runtimeThread.start();
    }

    private void runtimeLoop() {
        while (runtimeRunning) {
            int waitMs = runtimeTick();
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private int runtimeTick() {
        long now = System.currentTimeMillis();
        lastLoopAt = now;
        updateLoginSolverPolicy();
        agentTaskRunner.tick();

        int processed = 0;
        RuntimeTask task;
        while ((task = queue.poll()) != null && processed < 5) {
            try {
                task.future.complete(task.callable.call());
            } catch (Throwable t) {
                task.future.complete("{\"ok\":false,\"error\":" + quote(t.toString()) + "}");
            }
            processed++;
        }
        stopScriptIfRequested();
        return processed > 0 ? 25 : 100;
    }

    @Override
    public void onExit() {
        debug("DreamBotMcpScript script slot released; MCP runtime remains active.");
    }

    @Override
    public void onMessage(Message message) {
        recentMessages.record(message);
    }

    @Override
    public void onGameMessage(Message message) {
        recentMessages.record(message);
    }

    @Override
    public void onPrivateInfoMessage(Message message) {
        recentMessages.record(message);
    }

    @Override
    public void onPaint(Graphics graphics) {
        ScriptSettings active = settings;
        if (active == null || !active.paintEnabled || !(graphics instanceof Graphics2D)) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics;
        McpMetrics.Snapshot snapshot = mcpMetrics.snapshot();
        long now = System.currentTimeMillis();
        int x = 12;
        int y = 34;
        int width = 720;
        int height = 180;

        Color previousColor = g.getColor();
        Font previousFont = g.getFont();
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(x - 8, y - 22, width, height, 8, 8);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(230, 245, 255));
        drawPaintLine(g, "DreamBot MCP " + McpServer.SERVER_VERSION + "  " + mcpBindSummary, x, y);
        drawPaintLine(g, "MCP local " + blank(mcpLocalUrlSummary), x, y + 18);
        drawPaintLine(g, "MCP LAN " + truncate(blank(mcpLanUrlSummary), 96), x, y + 36);
        drawPaintLine(g, "Tools " + mcpToolCount + "  MCP req " + snapshot.mcpRequests + "  notify " + snapshot.mcpNotifications + "  calls " + snapshot.toolCalls + "  errors " + snapshot.toolErrors, x, y + 54);
        drawPaintLine(g, "Last method " + blank(snapshot.lastMethod) + "  last tool " + blank(snapshot.lastTool) + "  age " + age(now, snapshot.lastMcpAtMs), x, y + 72);
        drawPaintLine(g, "Queue " + queue.size() + "  tick age " + (lastLoopAt == 0 ? "n/a" : (now - lastLoopAt) + "ms") + "  HTTP bind " + active.bindHost + ":" + active.port, x, y + 90);
        drawPaintLine(g, "Game " + safeGameState() + "  logged in " + safeLoggedIn() + "  lifecycle " + active.allowLifecycle, x, y + 108);
        drawPaintLine(g, "Login solver " + active.loginSolverPolicy.value + "  disabled " + loginSolverDisabled + "  initial login " + initialLoginObserved, x, y + 126);
        if (!snapshot.lastError.isEmpty()) {
            g.setColor(new Color(255, 205, 170));
            drawPaintLine(g, "Last error " + truncate(snapshot.lastError, 96), x, y + 144);
        }
        g.setColor(previousColor);
        g.setFont(previousFont);
    }

    private void drawPaintLine(Graphics2D g, String text, int x, int y) {
        g.drawString(text == null ? "" : text, x, y);
    }

    private String safeGameState() {
        try {
            return String.valueOf(Client.getGameState());
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private boolean safeLoggedIn() {
        try {
            return Client.isLoggedIn();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String age(long now, long timestamp) {
        return timestamp <= 0 ? "n/a" : (now - timestamp) + "ms";
    }

    private String blank(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max - 3) + "...";
    }

    private boolean runtimeActive() {
        return runtimeRunning && httpServer != null;
    }

    String runtimeMenuStatus() {
        return "Runtime: " + (runtimeActive() ? "Running" : "Stopped");
    }

    String runtimeMenuUrl() {
        String url = runtimeMcpUrl();
        return url == null || url.isEmpty() ? "MCP URL: unavailable" : "MCP URL: " + url;
    }

    String runtimeMcpUrl() {
        ScriptSettings active = settings;
        return active == null ? "" : active.mcpUrl();
    }

    void logRuntimeStatus() {
        log("DreamBot MCP status: " + healthJson());
    }

    private void debug(String message) {
        ScriptSettings active = settings;
        if (active == null || active.debugEnabled) {
            System.out.println(message);
        }
    }

    private void refreshMcpAddressSummary() {
        ScriptSettings active = settings;
        if (active == null) {
            return;
        }
        String local = "http://127.0.0.1:" + active.port + McpHttpEndpoint.PATH;
        List<String> urls = mcpNetworkUrls(active.port);
        boolean loopbackOnly = isLoopbackBind(active.bindHost);
        boolean wildcard = isWildcardBind(active.bindHost);
        mcpLocalUrlSummary = local;
        if (loopbackOnly) {
            mcpBindSummary = "bind loopback-only " + active.bindHost + ":" + active.port;
            mcpLanUrlSummary = urls.isEmpty()
                ? "loopback-only; no non-loopback IPv4 found"
                : "loopback-only; bind 0.0.0.0 for " + joinLimited(urls, 3);
        } else if (wildcard) {
            mcpBindSummary = "bind all interfaces :" + active.port;
            mcpLanUrlSummary = urls.isEmpty() ? "no non-loopback IPv4 found" : joinLimited(urls, 3);
        } else {
            mcpBindSummary = "bind " + active.bindHost + ":" + active.port;
            mcpLanUrlSummary = "http://" + active.bindHost + ":" + active.port + McpHttpEndpoint.PATH;
        }
    }

    private List<String> mcpNetworkUrls(int port) {
        List<String> urls = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface == null || !iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        urls.add("http://" + address.getHostAddress() + ":" + port + McpHttpEndpoint.PATH);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return urls;
    }

    private boolean isLoopbackBind(String host) {
        String value = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() || "127.0.0.1".equals(value) || "localhost".equals(value) || "::1".equals(value) || "[::1]".equals(value);
    }

    private boolean isWildcardBind(String host) {
        String value = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        return "0.0.0.0".equals(value) || "::".equals(value) || "[::]".equals(value) || "*".equals(value);
    }

    private String joinLimited(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = Math.min(Math.max(limit, 1), values.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        if (values.size() > count) {
            sb.append(" +").append(values.size() - count).append(" more");
        }
        return sb.toString();
    }

    private void updateLoginSolverPolicy() {
        ScriptSettings active = settings;
        if (active == null || active.loginSolverPolicy != LoginSolverPolicy.AFTER_INITIAL_LOGIN || loginSolverDisabled) {
            return;
        }
        boolean loggedIn;
        try {
            loggedIn = Client.isLoggedIn();
        } catch (Throwable ignored) {
            return;
        }
        if (!loggedIn) {
            return;
        }
        initialLoginObserved = true;
        debug("DreamBotMcpScript observed initial login; disabling Login Handler random solver");
        disableLoginSolver();
    }

    private boolean shouldBlockLoginSolver() {
        ScriptSettings active = settings;
        return loginSolverDisabled || (active != null && active.loginSolverPolicy == LoginSolverPolicy.DISABLED);
    }

    private void disableLoginSolver() {
        loginSolverDisabled = true;
        try {
            RandomManager randomManager = getRandomManager();
            if (randomManager == null) {
                debug("DreamBotMcpScript could not disable login solver: random manager unavailable");
                return;
            }
            randomManager.disableSolver(RandomEvent.LOGIN);
            randomManager.disableSolver("Login Handler");
            debug("DreamBotMcpScript disabled Login Handler random solver");
        } catch (Throwable t) {
            debug("DreamBotMcpScript could not disable login solver: " + t);
        }
    }

    private boolean isLoginSolver(RandomSolver solver) {
        if (solver == null) {
            return false;
        }
        try {
            String event = solver.getEventString();
            return event != null && event.toLowerCase(Locale.ROOT).contains("login");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void startHttpServer() {
        httpServer = new ScriptHttpServer(settings.bindHost, settings.port, this::handleHttp);
        httpServer.start();
        debug("DreamBotMcpScript listening on " + settings.httpBaseUrl());
    }

    private RuntimeResponse handleHttp(RuntimeRequest request) throws Exception {
        if (McpHttpEndpoint.PATH.equals(request.path)) {
            return mcpEndpoint.handle(request);
        }
        return RuntimeResponse.json(route(request));
    }

    String route(RuntimeRequest request) throws Exception {
        String method = request.method.toUpperCase(Locale.ROOT);
        String path = request.path;
        if ("GET".equals(method) && "/health".equals(path)) {
            return healthJson();
        }
        if ("GET".equals(method) && "/state".equals(path)) {
            return runMcpRequest(request, () -> stateJson());
        }
        if ("GET".equals(method) && "/account".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"account\":" + safeJson(() -> accountJson()) + "}");
        }
        if ("GET".equals(method) && "/login/status".equals(path)) {
            return runMcpRequest(request, () -> loginStatusJson());
        }
        if ("GET".equals(method) && "/client".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"client\":" + safeJson(() -> clientJson()) + "}");
        }
        if ("GET".equals(method) && "/screenshot".equals(path)) {
            return runMcpRequest(request, () -> screenshotJson(request.query), 10000L);
        }
        if ("GET".equals(method) && "/skills".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"skills\":" + skillsJson() + "}");
        }
        if ("GET".equals(method) && "/inventory".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"inventory\":" + itemsJson(Inventory.all()) + "}");
        }
        if ("GET".equals(method) && "/equipment".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"equipment\":" + itemsJson(Equipment.all()) + "}");
        }
        if ("GET".equals(method) && "/bank".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"bank\":" + bankJson() + "}");
        }
        if ("GET".equals(method) && "/dialogue".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"dialogue\":" + dialogueJson() + "}");
        }
        if ("GET".equals(method) && "/ui-text".equals(path)) {
            return runMcpRequest(request, () -> uiTextResponseJson(request.query));
        }
        if ("GET".equals(method) && "/ui-summary".equals(path)) {
            return runMcpRequest(request, () -> uiSummaryResponseJson(request.query));
        }
        if ("GET".equals(method) && "/widgets".equals(path)) {
            return runMcpRequest(request, () -> widgetsJson(request.query));
        }
        if ("GET".equals(method) && "/ground-items".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"ground_items\":" + json.groundItemsJson(GroundItems.all()) + "}");
        }
        if ("GET".equals(method) && "/npcs".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"npcs\":" + json.npcsJson(NPCs.all()) + "}");
        }
        if ("GET".equals(method) && "/objects".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"objects\":" + json.objectsJson(GameObjects.all()) + "}");
        }
        if ("GET".equals(method) && "/players".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"players\":" + json.playersJson(Players.all()) + "}");
        }
        if ("GET".equals(method) && "/settings".equals(path)) {
            return runMcpRequest(request, () -> settingsJson(request.query));
        }
        if ("GET".equals(method) && "/quests".equals(path)) {
            return runMcpRequest(request, () -> questsJson(request.query));
        }
        if ("GET".equals(method) && "/camera".equals(path)) {
            return runMcpRequest(request, () -> "{\"ok\":true,\"camera\":" + cameraJson() + "}");
        }
        if ("GET".equals(method) && "/projection/tile".equals(path)) {
            return runMcpRequest(request, () -> projectionTileJson(request.query));
        }
        if ("GET".equals(method) && "/agent-task/status".equals(path)) {
            return runMcpRequest(request, () -> agentTaskRunner.statusJson());
        }
        if ("GET".equals(method) && "/agent-task/logs".equals(path)) {
            return runMcpRequest(request, () -> agentTaskRunner.logsJson(queryInt(request.query, "limit", 50)));
        }
        if ("POST".equals(method) && "/action/login/reconnect".equals(path)) {
            return loginReconnectJson(request.body);
        }
        if ("POST".equals(method) && path.startsWith("/action/")) {
            long timeoutMs = "/action/wait-until".equals(path)
                ? Math.min(Math.max(intField(request.body, "timeout_ms", 5000), 1) + 2000L, 65000L)
                : Math.min(Math.max(intField(request.body, "action_timeout_ms", 45000), 1000), 90000);
            return runMcpRequest(request, () -> actionJson(path, request.body), timeoutMs);
        }
        return "{\"ok\":false,\"error\":\"not found\",\"path\":" + quote(path) + "}";
    }

    private String healthJson() {
        long now = System.currentTimeMillis();
        return "{\"ok\":true,\"service\":\"dreambot-mcp\",\"auth\":\"none\",\"bind_host\":" + quote(settings.bindHost)
            + ",\"port\":" + settings.port
            + ",\"mcp_path\":" + quote(McpHttpEndpoint.PATH)
            + ",\"mcp_url\":" + quote(settings.mcpUrl())
            + ",\"allow_lifecycle\":" + settings.allowLifecycle
            + ",\"release_script_slot\":" + settings.releaseScriptSlot
            + ",\"runtime_running\":" + runtimeRunning
            + ",\"background_runtime\":true"
            + ",\"login_solver_policy\":" + quote(settings.loginSolverPolicy.value)
            + ",\"login_solver_disabled\":" + loginSolverDisabled
            + ",\"initial_login_observed\":" + initialLoginObserved
            + ",\"tools\":" + mcpToolCount
            + ",\"uptime_ms\":" + (now - startedAt)
            + ",\"queue_size\":" + queue.size()
            + ",\"last_tick_age_ms\":" + (lastLoopAt == 0 ? -1 : now - lastLoopAt) + "}";
    }

    private String stateJson() {
        StringBuilder sb = new StringBuilder("{\"ok\":true,");
        sb.append("\"account\":").append(safeJson(() -> accountJson())).append(",");
        sb.append("\"client\":").append(safeJson(() -> clientJson())).append(",");
        sb.append("\"local_player\":").append(localPlayerJson()).append(",");
        sb.append("\"skills\":").append(safeJson(() -> skillsJson())).append(",");
        sb.append("\"quests\":").append(safeJson(() -> questsSummaryJson())).append(",");
        sb.append("\"inventory\":").append(safeJson(() -> itemsJson(Inventory.all()))).append(",");
        sb.append("\"equipment\":").append(safeJson(() -> itemsJson(Equipment.all()))).append(",");
        sb.append("\"bank\":").append(safeJson(() -> bankJson()));
        sb.append("}");
        return sb.toString();
    }

    private String safeJson(Callable<String> supplier) {
        try {
            String value = supplier.call();
            return value == null ? "null" : value;
        } catch (Throwable t) {
            return "{\"ok\":false,\"error\":" + quote(t.toString()) + "}";
        }
    }

    private String localPlayerJson() {
        try {
            Player player = Players.getLocal();
            return player == null ? "null" : json.playerJson(player);
        } catch (Throwable ignored) {
            return "null";
        }
    }

    private Player localPlayerSafe() {
        try {
            return Players.getLocal();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String accountJson() {
        Player local = Players.getLocal();
        StringBuilder sb = new StringBuilder("{");
        field(sb, "username", Client.getUsername()).append(",");
        field(sb, "account_identifier", Client.getAccountIdentifier()).append(",");
        field(sb, "local_player_name", local == null ? "" : local.getName()).append(",");
        field(sb, "logged_in", Client.isLoggedIn()).append(",");
        field(sb, "members", Client.isMembers()).append(",");
        field(sb, "has_members_access", Client.hasMembersAccess()).append(",");
        field(sb, "membership_left", Client.getMembershipLeft()).append(",");
        field(sb, "account_status", String.valueOf(Client.getAccountStatus())).append(",");
        field(sb, "ironman", Client.isIronman()).append(",");
        field(sb, "group_ironman", Client.isGroupIronman()).append(",");
        field(sb, "ultimate_ironman", Client.isUltimateIronman());
        sb.append("}");
        return sb.toString();
    }

    private String clientJson() {
        Tile destination = Client.getDestination();
        StringBuilder sb = new StringBuilder("{");
        field(sb, "logged_in", Client.isLoggedIn()).append(",");
        field(sb, "game_state", String.valueOf(Client.getGameState())).append(",");
        field(sb, "game_state_id", Client.getGameStateId()).append(",");
        field(sb, "game_tick", Client.getGameTick()).append(",");
        field(sb, "fps", Client.getFPS()).append(",");
        field(sb, "runescape_fps", Client.getRunescapeFps()).append(",");
        field(sb, "idle_time", Client.getIdleTime()).append(",");
        field(sb, "idle_logout", Client.getIdleLogout()).append(",");
        field(sb, "plane", Client.getPlane()).append(",");
        field(sb, "base_x", Client.getBaseX()).append(",");
        field(sb, "base_y", Client.getBaseY()).append(",");
        field(sb, "map_angle", Client.getMapAngle()).append(",");
        field(sb, "viewport_width", Client.getViewportWidth()).append(",");
        field(sb, "viewport_height", Client.getViewportHeight()).append(",");
        field(sb, "host", safeClientHost()).append(",");
        field(sb, "world", safeCurrentWorld()).append(",");
        sb.append("\"destination\":").append(json.tileJson(destination)).append(",");
        sb.append("\"camera\":").append(cameraJson()).append(",");
        sb.append("\"combat\":{");
        field(sb, "level", Combat.getCombatLevel()).append(",");
        field(sb, "health_percent", Combat.getHealthPercent()).append(",");
        field(sb, "special_percent", Combat.getSpecialPercentage()).append(",");
        field(sb, "special_active", Combat.isSpecialActive()).append(",");
        field(sb, "in_wilderness", Combat.isInWild()).append(",");
        field(sb, "wilderness_level", Combat.getWildernessLevel()).append(",");
        field(sb, "in_multi", Combat.isInMultiCombat());
        sb.append("}}");
        return sb.toString();
    }

    private String screenshotJson(Map<String, String> query) throws Exception {
        String target = queryString(query, "target", "canvas").trim().toLowerCase(Locale.ROOT);
        if (target.isEmpty()) {
            target = "canvas";
        }
        require("canvas".equals(target) || "screen".equals(target), "target must be canvas or screen");

        BufferedImage image = "screen".equals(target) ? captureScreenImage() : captureCanvasImage();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        String data = Base64.getEncoder().encodeToString(out.toByteArray());

        return "{\"ok\":true"
            + ",\"target\":" + quote(target)
            + ",\"mime_type\":\"image/png\""
            + ",\"width\":" + image.getWidth()
            + ",\"height\":" + image.getHeight()
            + ",\"data_base64\":" + quote(data)
            + "}";
    }

    private BufferedImage captureCanvasImage() throws Exception {
        Canvas canvas = Client.getCanvas();
        require(canvas != null, "DreamBot canvas is not available");
        int width = Math.max(1, canvas.getWidth());
        int height = Math.max(1, canvas.getHeight());
        try {
            Point location = canvas.getLocationOnScreen();
            return new Robot().createScreenCapture(new Rectangle(location.x, location.y, width, height));
        } catch (IllegalComponentStateException e) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                canvas.paint(graphics);
            } finally {
                graphics.dispose();
            }
            return image;
        }
    }

    private BufferedImage captureScreenImage() throws Exception {
        Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        require(bounds.width > 0 && bounds.height > 0, "screen bounds are unavailable");
        return new Robot().createScreenCapture(bounds);
    }

    private String loginStatusJson() {
        Player local = localPlayerSafe();
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"login\":{");
        field(sb, "logged_in", Client.isLoggedIn()).append(",");
        field(sb, "login_index", Client.getLoginIndex()).append(",");
        field(sb, "game_state", String.valueOf(Client.getGameState())).append(",");
        field(sb, "game_state_id", Client.getGameStateId()).append(",");
        field(sb, "has_focus", Client.hasFocus()).append(",");
        field(sb, "username", Client.getUsername()).append(",");
        field(sb, "account_identifier", Client.getAccountIdentifier()).append(",");
        field(sb, "local_player_name", local == null ? "" : local.getName()).append(",");
        field(sb, "login_response", String.valueOf(Client.getLoginResponse())).append(",");
        sb.append("\"messages\":").append(clientMessagesJson()).append(",");
        sb.append("\"recent_messages\":").append(recentMessages.json(10));
        sb.append("}}");
        return sb.toString();
    }

    private int safeCurrentWorld() {
        try {
            return Worlds.getCurrentWorld();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private String safeClientHost() {
        // Client.getHost() can trigger RuneLite world lookup from telemetry and block the client thread.
        return "";
    }

    private String skillsJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Skill skill : Skill.values()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(quote(skill.name())).append(":{");
            field(sb, "level", Skills.getRealLevel(skill)).append(",");
            field(sb, "boosted", Skills.getBoostedLevel(skill)).append(",");
            field(sb, "xp", Skills.getExperience(skill));
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    private String questsSummaryJson() {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "quest_points", Quests.getQuestPoints()).append(",");
        sb.append("\"quests\":[");
        boolean first = true;
        for (FreeQuest quest : FreeQuest.values()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(questJson(quest));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String bankJson() {
        List<Item> items = json.safe(Bank.all());
        boolean cached = Bank.isCached();
        if ((items == null || items.isEmpty()) && cached) {
            items = json.safe(Bank.getBankHistoryCache());
        }
        StringBuilder sb = new StringBuilder("{");
        field(sb, "open", Bank.isOpen()).append(",");
        field(sb, "loaded", Bank.isLoaded()).append(",");
        field(sb, "cached", cached).append(",");
        field(sb, "last_updated_tick", Bank.getLastUpdatedTick()).append(",");
        field(sb, "capacity", Bank.capacity()).append(",");
        field(sb, "full_slots", Bank.fullSlotCount()).append(",");
        sb.append("\"items\":").append(itemsJson(items));
        sb.append("}");
        return sb.toString();
    }

    private String itemsJson(List<Item> items) {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (Item item : json.safe(items)) {
            if (item == null || item.getId() <= 0) {
                continue;
            }
            if (count > 0) {
                sb.append(",");
            }
            sb.append(json.itemJson(item));
            count++;
        }
        sb.append("]");
        return sb.toString();
    }

    private String runOnScriptThread(Callable<String> callable) throws Exception {
        return runOnScriptThread(callable, 6000L);
    }

    private String runOnScriptThread(Callable<String> callable, long timeoutMs) throws Exception {
        return callOnScriptThread(callable, timeoutMs);
    }

    private <T> T callOnScriptThread(Callable<T> callable, long timeoutMs) throws Exception {
        RuntimeTask task = new RuntimeTask(callable);
        if (!queue.offer(task, 2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("runtime queue full");
        }
        @SuppressWarnings("unchecked")
        T value = (T) task.future.get(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
        return value;
    }

    private String runMcpRequest(RuntimeRequest request, Callable<String> callable) throws Exception {
        return runMcpRequest(request, callable, 6000L);
    }

    private String runMcpRequest(RuntimeRequest request, Callable<String> callable, long timeoutMs) throws Exception {
        LoginAttempt login = ensureLoggedInForRequest(request);
        if (!login.ok) {
            return login.failureJson(request.path);
        }
        return runOnScriptThread(callable, timeoutMs);
    }

    private LoginAttempt ensureLoggedInForRequest(RuntimeRequest request) throws Exception {
        if (request == null || shouldSkipAutoLogin(request.path)) {
            return LoginAttempt.success(true, "skipped", 0, 0L, safeLoginStage(), safeLoginResponse(), "");
        }
        return loginNow("auto", AUTO_LOGIN_TIMEOUT_MS);
    }

    private boolean shouldSkipAutoLogin(String path) {
        return "/action/login/reconnect".equals(path)
            || "/action/login/type-credentials".equals(path)
            || "/action/client/logout".equals(path);
    }

    private LoginAttempt loginNow(String trigger, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        LoginAttempt current = loginStatusAttempt(trigger, true, 0, start, "");
        if (current.ok) {
            return current;
        }

        ScriptSettings active = settings;
        if (active != null && active.loginSolverPolicy == LoginSolverPolicy.DISABLED) {
            return LoginAttempt.failure(trigger, "login solver disabled by settings", 0, 0L, safeLoginStage(), safeLoginResponse(), "");
        }

        enableLoginSolverForAttempt();
        configureLoginUtility();

        long deadline = start + Math.max(1L, timeoutMs);
        long nextAttemptAt = 0L;
        int attempts = 0;
        String error = "";

        while (System.currentTimeMillis() <= deadline) {
            final int statusAttempts = attempts;
            final String statusError = error;
            current = loginStatusAttempt(trigger, false, statusAttempts, start, statusError);
            if (current.ok) {
                return current;
            }
            long now = System.currentTimeMillis();
            if (now >= nextAttemptAt) {
                attempts++;
                final int tickAttempts = attempts;
                current = loginTickAttempt(trigger, tickAttempts, start);
                error = current.detail;
                if (current.ok) {
                    return current;
                }
                nextAttemptAt = now + AUTO_LOGIN_ATTEMPT_INTERVAL_MS;
            }
            sleepQuietly(AUTO_LOGIN_POLL_MS);
        }

        if (active != null && active.loginSolverPolicy == LoginSolverPolicy.AFTER_INITIAL_LOGIN) {
            disableLoginSolver();
        }
        final int finalAttempts = attempts;
        final String finalError = error;
        return LoginAttempt.failure(trigger, "auto-login timed out", finalAttempts, System.currentTimeMillis() - start, safeLoginStage(), safeLoginResponse(), finalError);
    }

    private LoginAttempt loginStatusAttempt(String trigger, boolean alreadyLoggedIn, int attempts, long start, String detail) {
        if (safeLoggedIn()) {
            return loggedInAttempt(trigger, alreadyLoggedIn, attempts, start, detail);
        }
        return LoginAttempt.failure(trigger, "not logged in", attempts, System.currentTimeMillis() - start, safeLoginStage(), safeLoginResponse(), detail);
    }

    private LoginAttempt loginTickAttempt(String trigger, int attempts, long start) {
        String response = safeLoginResponse();
        String stage = safeLoginStage();
        String error = "";
        try {
            RSLoginResponse loginResponse = LoginUtility.login();
            response = String.valueOf(loginResponse);
            stage = safeLoginStage();
        } catch (Throwable t) {
            error = t.toString();
        }
        if (safeLoggedIn()) {
            return loggedInAttempt(trigger, false, attempts, start, error);
        }
        return LoginAttempt.failure(trigger, "not logged in", attempts, System.currentTimeMillis() - start, stage, response, error);
    }

    private LoginAttempt loggedInAttempt(String trigger, boolean alreadyLoggedIn, int attempts, long start, String detail) {
        ScriptSettings active = settings;
        if (active != null && active.loginSolverPolicy == LoginSolverPolicy.AFTER_INITIAL_LOGIN) {
            initialLoginObserved = true;
            disableLoginSolver();
        }
        return LoginAttempt.success(alreadyLoggedIn, trigger, attempts, System.currentTimeMillis() - start, safeLoginStage(), safeLoginResponse(), detail);
    }

    private void configureLoginUtility() {
        try {
            LoginUtility.setEnterToLogin(true);
        } catch (Throwable t) {
            debug("DreamBotMcpScript could not configure LoginUtility: " + t);
        }
    }

    private void enableLoginSolverForAttempt() {
        try {
            RandomManager randomManager = getRandomManager();
            if (randomManager == null) {
                debug("DreamBotMcpScript could not enable login solver: random manager unavailable");
                return;
            }
            randomManager.enableSolver(RandomEvent.LOGIN);
            randomManager.enableSolver("Login Handler");
            loginSolverDisabled = false;
        } catch (Throwable t) {
            debug("DreamBotMcpScript could not enable login solver: " + t);
        }
    }

    private String safeLoginStage() {
        try {
            return String.valueOf(LoginUtility.getStage());
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private String safeLoginResponse() {
        try {
            return String.valueOf(LoginUtility.getResponse());
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private String actionJson(String path, String body) {
        try {
            if ("/action/walk".equals(path)) {
                int x = intField(body, "x", Integer.MIN_VALUE);
                int y = intField(body, "y", Integer.MIN_VALUE);
                int z = intField(body, "z", Client.getPlane());
                require(x != Integer.MIN_VALUE && y != Integer.MIN_VALUE, "x and y are required");
                boolean result = Walking.walk(new Tile(x, y, z));
                return okResult("walk", result);
            }
            if ("/action/inventory/interact".equals(path)) {
                String action = stringField(body, "action", "");
                int slot = intField(body, "slot", -1);
                String name = stringField(body, "name", "");
                boolean result;
                if (slot >= 0) {
                    result = action.isEmpty() ? Inventory.slotInteract(slot) : Inventory.slotInteract(slot, action);
                } else {
                    require(!name.isEmpty(), "slot or name is required");
                    result = action.isEmpty() ? Inventory.interact(name) : Inventory.interact(name, action);
                }
                return okResult("inventory.interact", result);
            }
            if ("/action/equipment/interact".equals(path)) {
                String slotName = stringField(body, "slot", "");
                String action = stringField(body, "action", "");
                require(!slotName.isEmpty() && !action.isEmpty(), "slot and action are required");
                EquipmentSlot slot = EquipmentSlot.valueOf(slotName.toUpperCase(Locale.ROOT));
                return okResult("equipment.interact", Equipment.interact(slot, action));
            }
            if ("/action/npc/interact".equals(path)) {
                String action = stringField(body, "action", "");
                require(!action.isEmpty(), "action is required");
                NPC npc = targetNpc(body);
                boolean result = npc != null && npc.interact(action);
                return interactionResult("npc.interact", result, npc == null ? "null" : json.npcJson(npc));
            }
            if ("/action/object/interact".equals(path)) {
                String action = stringField(body, "action", "");
                require(!action.isEmpty(), "action is required");
                GameObject object = targetObject(body);
                boolean result = object != null && object.interact(action);
                return interactionResult("object.interact", result, object == null ? "null" : json.objectJson(object));
            }
            if ("/action/tile/click".equals(path)) {
                int x = intField(body, "x", Integer.MIN_VALUE);
                int y = intField(body, "y", Integer.MIN_VALUE);
                int z = intField(body, "z", Client.getPlane());
                boolean minimap = boolField(body, "minimap", false);
                boolean right = boolField(body, "right", false);
                require(x != Integer.MIN_VALUE && y != Integer.MIN_VALUE, "x and y are required");
                AbstractMouseDestination<Tile> destination = minimap
                    ? new MiniMapTileDestination(new Tile(x, y, z))
                    : new TileDestination(new Tile(x, y, z));
                boolean result = right ? Mouse.click(destination, true) : Mouse.click(destination);
                return okResult(minimap ? "tile.click.minimap" : "tile.click", result);
            }
            if ("/action/player/interact".equals(path)) {
                String action = stringField(body, "action", "");
                require(!action.isEmpty(), "action is required");
                Player player = targetPlayer(body);
                boolean result = player != null && player.interact(action);
                return interactionResult("player.interact", result, player == null ? "null" : json.playerJson(player));
            }
            if ("/action/ground-item/interact".equals(path)) {
                String action = stringField(body, "action", "");
                require(!action.isEmpty(), "action is required");
                GroundItem item = targetGroundItem(body);
                boolean result = item != null && item.interact(action);
                return interactionResult("ground_item.interact", result, item == null ? "null" : json.groundItemJson(item));
            }
            if ("/action/item/on-object".equals(path)) {
                Item item = targetInventoryItem(body, "");
                GameObject object = targetObject(body, "target_");
                boolean result = item != null && object != null && item.useOn(object);
                return itemOnResult("item.on_object", result, item, object == null ? "null" : json.objectJson(object));
            }
            if ("/action/item/on-npc".equals(path)) {
                Item item = targetInventoryItem(body, "");
                NPC npc = targetNpc(body, "target_");
                boolean result = item != null && npc != null && item.useOn(npc);
                return itemOnResult("item.on_npc", result, item, npc == null ? "null" : json.npcJson(npc));
            }
            if ("/action/item/on-item".equals(path)) {
                Item item = targetInventoryItem(body, "");
                Item target = targetInventoryItem(body, "target_");
                boolean result = item != null && target != null && item.useOn(target);
                return itemOnResult("item.on_item", result, item, target == null ? "null" : json.itemJson(target));
            }
            if ("/action/spell/cast".equals(path)) {
                Spell spell = targetSpell(body);
                boolean result = spell != null && Magic.castSpell(spell);
                return spellResult("spell.cast", result, spell, "null");
            }
            if ("/action/spell/on-npc".equals(path)) {
                Spell spell = targetSpell(body);
                NPC npc = targetNpc(body);
                boolean result = spell != null && npc != null && Magic.castSpellOn(spell, npc);
                return spellResult("spell.on_npc", result, spell, npc == null ? "null" : json.npcJson(npc));
            }
            if ("/action/tab/open".equals(path)) {
                String tabName = stringField(body, "tab", "");
                require(!tabName.isEmpty(), "tab is required");
                Tab tab = Tab.valueOf(tabName.toUpperCase(Locale.ROOT));
                return okResult("tab.open", Tabs.open(tab));
            }
            if ("/action/dialogue/continue".equals(path)) {
                String method = stringField(body, "method", "continue").trim().toLowerCase(Locale.ROOT);
                boolean result;
                if ("space".equals(method)) {
                    result = Dialogues.spaceToContinue();
                } else if ("click".equals(method)) {
                    result = Dialogues.clickContinue();
                } else {
                    result = Dialogues.continueDialogue();
                }
                return "{\"ok\":true,\"action\":\"dialogue.continue\",\"result\":" + result + ",\"dialogue\":" + dialogueJson() + "}";
            }
            if ("/action/dialogue/choose".equals(path)) {
                String text = stringField(body, "text", stringField(body, "option_text", ""));
                boolean result;
                if (!text.isEmpty()) {
                    boolean contains = boolField(body, "contains", false);
                    result = contains ? Dialogues.chooseFirstOptionContaining(text) : Dialogues.chooseOption(text);
                } else {
                    int index = intField(body, "index", Integer.MIN_VALUE);
                    require(index != Integer.MIN_VALUE, "text or index is required");
                    if (index <= 0) {
                        index = 1;
                    }
                    result = Dialogues.chooseOption(index);
                }
                return "{\"ok\":true,\"action\":\"dialogue.choose\",\"result\":" + result + ",\"dialogue\":" + dialogueJson() + "}";
            }
            if ("/action/widget/click".equals(path)) {
                String action = stringField(body, "action", "");
                WidgetChild widget = targetWidget(body);
                boolean result = widget != null && (action.isEmpty() ? widget.interact() : widget.interact(action));
                return interactionResult("widget.click", result, widget == null ? "null" : json.widgetJson(widget));
            }
            if ("/action/widget/type".equals(path)) {
                String text = stringField(body, "text", "");
                require(!text.isEmpty(), "text is required");
                WidgetChild widget = targetWidget(body);
                boolean focused = widget == null || widget.interact();
                if (widget != null) {
                    sleepQuietly(100L);
                }
                boolean result = focused && Keyboard.type(text);
                return interactionResult("widget.type", result, widget == null ? "null" : json.widgetJson(widget));
            }
            if ("/action/wait-until".equals(path)) {
                return waitUntilJson(body);
            }
            if ("/action/camera/set".equals(path)) {
                boolean result = setCameraFromBody(body);
                return "{\"ok\":true,\"action\":\"camera.set\",\"result\":" + result + ",\"camera\":" + cameraJson() + "}";
            }
            if ("/action/camera/rotate-to".equals(path)) {
                boolean result = rotateCameraToBody(body);
                return "{\"ok\":true,\"action\":\"camera.rotate_to\",\"result\":" + result + ",\"camera\":" + cameraJson() + "}";
            }
            if ("/action/login/type-credentials".equals(path)) {
                return typeLoginCredentialsJson(body);
            }
            if ("/action/chat/say".equals(path)) {
                return chatSayJson(body);
            }
            if ("/action/java/eval".equals(path)) {
                return javaEvalJson(body);
            }
            if ("/action/agent-task/start".equals(path)) {
                return agentTaskStartJson(body);
            }
            if ("/action/agent-task/stop".equals(path)) {
                return agentTaskRunner.stop(stringField(body, "reason", "requested"));
            }
            if ("/action/mouse/click".equals(path)) {
                int x = intField(body, "x", Integer.MIN_VALUE);
                int y = intField(body, "y", Integer.MIN_VALUE);
                boolean right = isRightClick(body);
                require(x != Integer.MIN_VALUE && y != Integer.MIN_VALUE, "x and y are required");
                boolean result = right ? Mouse.click(new Point(x, y), true) : Mouse.click(new Point(x, y));
                return okResult("mouse.click", result);
            }
            if ("/action/mouse/move".equals(path)) {
                int x = intField(body, "x", Integer.MIN_VALUE);
                int y = intField(body, "y", Integer.MIN_VALUE);
                require(x != Integer.MIN_VALUE && y != Integer.MIN_VALUE, "x and y are required");
                return okResult("mouse.move", Mouse.move(new Point(x, y)));
            }
            if ("/action/mouse/drag".equals(path)) {
                int x = intField(body, "x", Integer.MIN_VALUE);
                int y = intField(body, "y", Integer.MIN_VALUE);
                int toX = intField(body, "to_x", Integer.MIN_VALUE);
                int toY = intField(body, "to_y", Integer.MIN_VALUE);
                require(x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && toX != Integer.MIN_VALUE && toY != Integer.MIN_VALUE, "x, y, to_x, and to_y are required");
                boolean moved = Mouse.move(new Point(x, y));
                int durationMs = Math.min(Math.max(intField(body, "duration_ms", 0), 0), 10000);
                if (durationMs > 0) {
                    sleepQuietly(Math.min(durationMs, 500L));
                }
                boolean dragged = Mouse.drag(new Point(toX, toY));
                return "{\"ok\":true,\"action\":\"mouse.drag\",\"result\":" + (moved && dragged) + ",\"moved\":" + moved + ",\"dragged\":" + dragged + "}";
            }
            if ("/action/mouse/wheel".equals(path)) {
                int x = intField(body, "x", Integer.MIN_VALUE);
                int y = intField(body, "y", Integer.MIN_VALUE);
                if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
                    Mouse.move(new Point(x, y));
                }
                int rotation = intField(body, "rotation", intField(body, "amount", 1));
                if (boolField(body, "up", false)) {
                    rotation = -Math.abs(rotation);
                } else if (boolField(body, "down", false)) {
                    rotation = Math.abs(rotation);
                }
                require(rotation != 0, "rotation must not be zero");
                int steps = Math.min(Math.abs(rotation), 100);
                if (rotation > 0) {
                    Mouse.scrollDown(steps, () -> false);
                } else {
                    Mouse.scrollUp(steps, () -> false);
                }
                return okResult("mouse.wheel", true);
            }
            if ("/action/keyboard/type".equals(path)) {
                String text = stringField(body, "text", "");
                require(text.length() <= 4096, "text is too long");
                return okResult("keyboard.type", Keyboard.type(text));
            }
            if ("/action/keyboard/key".equals(path)) {
                int keyCode = keyCodeField(body);
                return okResult("keyboard.key", Keyboard.typeKey(keyCode));
            }
            if ("/action/combat/special".equals(path)) {
                boolean enabled = boolField(body, "enabled", true);
                return okResult("combat.special", Combat.toggleSpecialAttack(enabled));
            }
            if ("/action/client/logout".equals(path)) {
                disableLoginSolver();
                boolean result = Client.logout();
                return "{\"ok\":true,\"action\":\"client.logout\",\"result\":" + result + ",\"script_stop_requested\":false}";
            }
            if ("/action/client/focus".equals(path)) {
                Client.gainFocus();
                return okResult("client.focus", true);
            }
            return "{\"ok\":false,\"error\":\"unknown action\",\"path\":" + quote(path) + "}";
        } catch (Throwable t) {
            return "{\"ok\":false,\"error\":" + quote(t.toString()) + "}";
        }
    }

    private String typeLoginCredentialsJson(String body) {
        String username = stringField(body, "username", "");
        String password = stringField(body, "password", "");
        boolean submit = boolField(body, "submit", true);
        require(!username.trim().isEmpty(), "username is required");
        require(!password.isEmpty(), "password is required");
        require(username.length() <= 320, "username is too long");
        require(password.length() <= 320, "password is too long");

        focusClientInput();
        boolean usernameTyped = Keyboard.type(username);
        sleepQuietly(75L);
        boolean tabbed = Keyboard.typeKey(KeyEvent.VK_TAB);
        sleepQuietly(75L);
        boolean passwordTyped = Keyboard.type(password);
        sleepQuietly(75L);
        boolean submitted = submit && Keyboard.typeKey(KeyEvent.VK_ENTER);

        StringBuilder sb = new StringBuilder("{\"ok\":true,\"action\":\"login.type_credentials\",");
        field(sb, "result", usernameTyped && tabbed && passwordTyped && (!submit || submitted)).append(",");
        field(sb, "username", username).append(",");
        field(sb, "username_typed", usernameTyped).append(",");
        field(sb, "password_typed", passwordTyped).append(",");
        field(sb, "submitted", submitted).append(",");
        field(sb, "logged_in", Client.isLoggedIn()).append(",");
        field(sb, "game_state", String.valueOf(Client.getGameState())).append(",");
        field(sb, "game_state_id", Client.getGameStateId());
        sb.append("}");
        return sb.toString();
    }

    private String loginReconnectJson(String body) throws Exception {
        long timeoutMs = Math.min(Math.max(intField(body, "timeout_ms", (int) AUTO_LOGIN_TIMEOUT_MS), 1000), 90000);
        LoginAttempt login = loginNow("manual", timeoutMs);
        return login.resultJson("login.reconnect");
    }

    private String chatSayJson(String body) {
        String message = stringField(body, "message", "");
        boolean pressEnter = boolField(body, "press_enter", true);
        require(Client.isLoggedIn(), "chat requires a logged-in game session");
        require(!message.trim().isEmpty(), "message is required");
        require(message.length() <= 256, "message is too long");

        focusClientInput();
        boolean opened = Keyboard.typeKey(KeyEvent.VK_ENTER);
        sleepQuietly(75L);
        boolean typed = Keyboard.type(message);
        sleepQuietly(75L);
        boolean submitted = pressEnter && Keyboard.typeKey(KeyEvent.VK_ENTER);

        StringBuilder sb = new StringBuilder("{\"ok\":true,\"action\":\"chat.say\",");
        field(sb, "result", opened && typed && (!pressEnter || submitted)).append(",");
        field(sb, "opened", opened).append(",");
        field(sb, "typed", typed).append(",");
        field(sb, "submitted", submitted).append(",");
        field(sb, "message_length", message.length());
        sb.append("}");
        return sb.toString();
    }

    private void focusClientInput() {
        try {
            Client.gainFocus();
        } catch (Throwable ignored) {
        }
        try {
            Keyboard.gainFocus();
        } catch (Throwable ignored) {
        }
        sleepQuietly(100L);
    }

    private String javaEvalJson(String body) throws Exception {
        String mode = stringField(body, "mode", "expression");
        String code = stringField(body, "code", "");
        return javaSnippetEvaluator.eval(this, mode, code);
    }

    private String agentTaskStartJson(String body) throws Exception {
        String name = stringField(body, "name", "agent-task");
        String mode = stringField(body, "mode", "loop");
        String source = stringField(body, "source", stringField(body, "code", stringField(body, "loop", "")));
        String className = stringField(body, "class_name", "");
        AgentTaskCompiler.CompiledTask compiled = agentTaskCompiler.compile(this, mode, source, className);
        return agentTaskRunner.start(this, name, compiled);
    }

    private String dialogueJson() {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "in_dialogue", Dialogues.inDialogue()).append(",");
        field(sb, "can_continue", Dialogues.canContinue()).append(",");
        field(sb, "can_enter_input", Dialogues.canEnterInput()).append(",");
        field(sb, "processing", Dialogues.isProcessing()).append(",");
        field(sb, "npc_dialogue", Dialogues.getNPCDialogue()).append(",");
        field(sb, "options_available", Dialogues.areOptionsAvailable()).append(",");
        sb.append("\"options\":").append(json.stringArrayJson(Dialogues.getOptions())).append(",");
        field(sb, "widget_selected", Widgets.isWidgetSelected()).append(",");
        field(sb, "selected_widget_id", Widgets.getSelectedWidgetId()).append(",");
        field(sb, "selected_widget_index", Widgets.getSelectedWidgetIndex()).append(",");
        sb.append("\"screen_text\":").append(screenTextJson(true, 40)).append(",");
        sb.append("\"recent_messages\":").append(recentMessages.json(20));
        sb.append("}");
        return sb.toString();
    }

    private String uiTextResponseJson(Map<String, String> query) {
        boolean visibleOnly = queryBool(query, "visible_only", true);
        int limit = Math.min(Math.max(queryInt(query, "limit", 80), 1), 500);
        int messageLimit = Math.min(Math.max(queryInt(query, "message_limit", 30), 0), 200);
        StringBuilder sb = new StringBuilder("{\"ok\":true");
        sb.append(",\"dialogue_api\":{");
        field(sb, "in_dialogue", Dialogues.inDialogue()).append(",");
        field(sb, "can_continue", Dialogues.canContinue()).append(",");
        field(sb, "can_enter_input", Dialogues.canEnterInput()).append(",");
        field(sb, "processing", Dialogues.isProcessing()).append(",");
        field(sb, "npc_dialogue", Dialogues.getNPCDialogue()).append(",");
        field(sb, "options_available", Dialogues.areOptionsAvailable()).append(",");
        sb.append("\"options\":").append(json.stringArrayJson(Dialogues.getOptions()));
        sb.append("}");
        sb.append(",\"screen_text\":").append(screenTextJson(visibleOnly, limit));
        sb.append(",\"recent_messages\":").append(recentMessages.json(messageLimit));
        sb.append("}");
        return sb.toString();
    }

    private String uiSummaryResponseJson(Map<String, String> query) {
        int messageLimit = Math.min(Math.max(queryInt(query, "message_limit", 20), 0), 200);
        StringBuilder sb = new StringBuilder("{\"ok\":true");
        sb.append(",\"dialogue\":").append(dialogueApiJson());
        sb.append(",\"message_box\":").append(messageBoxJson());
        sb.append(",\"client_messages\":").append(clientMessagesJson());
        sb.append(",\"hint_arrow\":").append(hintArrowJson());
        sb.append(",\"menu\":").append(menuJson());
        sb.append(",\"selected_widget\":").append(selectedWidgetJson());
        sb.append(",\"recent_messages\":").append(recentMessages.json(messageLimit));
        sb.append("}");
        return sb.toString();
    }

    private String dialogueApiJson() {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "in_dialogue", Dialogues.inDialogue()).append(",");
        field(sb, "can_continue", Dialogues.canContinue()).append(",");
        field(sb, "can_enter_input", Dialogues.canEnterInput()).append(",");
        field(sb, "processing", Dialogues.isProcessing()).append(",");
        field(sb, "npc_dialogue", Dialogues.getNPCDialogue()).append(",");
        field(sb, "options_available", Dialogues.areOptionsAvailable()).append(",");
        sb.append("\"options\":").append(json.stringArrayJson(Dialogues.getOptions()));
        sb.append("}");
        return sb.toString();
    }

    private String messageBoxJson() {
        List<WidgetTextRecord> records = widgetTextRecords(11, true, 20, false);
        String text = joinWidgetTexts(records, true);
        String continueText = "";
        boolean waiting = false;
        for (WidgetTextRecord record : records) {
            if ("Click here to continue".equalsIgnoreCase(record.clean)) {
                continueText = record.clean;
            } else if ("Please wait...".equalsIgnoreCase(record.clean)) {
                waiting = true;
            }
        }
        StringBuilder sb = new StringBuilder("{");
        field(sb, "visible", !records.isEmpty()).append(",");
        field(sb, "text", text).append(",");
        field(sb, "continue_text", continueText).append(",");
        field(sb, "can_continue", Dialogues.canContinue() || !continueText.isEmpty()).append(",");
        field(sb, "waiting", waiting).append(",");
        sb.append("\"widgets\":").append(widgetTextRecordsJson(records));
        sb.append("}");
        return sb.toString();
    }

    private String clientMessagesJson() {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "message0", Client.getMessage0()).append(",");
        field(sb, "message1", Client.getMessage1()).append(",");
        field(sb, "message2", Client.getMessage2()).append(",");
        sb.append("\"login_screen_text\":").append(json.stringArrayJson(Client.getLoginScreenText()));
        sb.append("}");
        return sb.toString();
    }

    private String hintArrowJson() {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "exists", HintArrow.exists()).append(",");
        field(sb, "type", String.valueOf(HintArrow.getType())).append(",");
        field(sb, "type_value", HintArrow.getTypeValue()).append(",");
        field(sb, "npc_index", HintArrow.getNpcIndex()).append(",");
        field(sb, "player_index", HintArrow.getPlayerIndex()).append(",");
        field(sb, "x", HintArrow.getX()).append(",");
        field(sb, "y", HintArrow.getY()).append(",");
        field(sb, "height", HintArrow.getHeight()).append(",");
        sb.append("\"tile\":").append(json.tileJson(HintArrow.getTile()));
        sb.append("}");
        return sb.toString();
    }

    private String menuJson() {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "visible", Menu.isVisible()).append(",");
        field(sb, "count", Menu.getCount()).append(",");
        field(sb, "x", Menu.getX()).append(",");
        field(sb, "y", Menu.getY()).append(",");
        field(sb, "width", Menu.getWidth()).append(",");
        field(sb, "height", Menu.getHeight()).append(",");
        field(sb, "default_action", Menu.getDefaultAction()).append(",");
        sb.append("\"bounds\":").append(json.rectangleJson(Menu.getBounds())).append(",");
        sb.append("\"rows\":[");
        boolean first = true;
        for (MenuRow row : json.safe(Menu.getMenuRows())) {
            if (row == null) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{");
            field(sb, "index", row.getIndex()).append(",");
            field(sb, "action", row.getAction()).append(",");
            field(sb, "raw_action", row.getRawAction()).append(",");
            field(sb, "object", row.getObject()).append(",");
            field(sb, "raw_object", row.getRawObject()).append(",");
            field(sb, "id", row.getID()).append(",");
            field(sb, "opcode", row.getOpCode()).append(",");
            field(sb, "item_id", row.getItemIdCode()).append(",");
            field(sb, "x_code", row.getXCode()).append(",");
            field(sb, "y_code", row.getYCode()).append(",");
            field(sb, "target_region_index", row.getTargetRegionIndex()).append(",");
            field(sb, "shift", row.isShift());
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String selectedWidgetJson() {
        WidgetChild selected = null;
        try {
            selected = Widgets.getSelected();
        } catch (Throwable ignored) {
        }
        StringBuilder sb = new StringBuilder("{");
        field(sb, "selected", Widgets.isWidgetSelected()).append(",");
        field(sb, "selected_widget_id", Widgets.getSelectedWidgetId()).append(",");
        field(sb, "selected_widget_index", Widgets.getSelectedWidgetIndex()).append(",");
        sb.append("\"widget\":").append(selected == null ? "null" : json.widgetJson(selected));
        sb.append("}");
        return sb.toString();
    }

    private String screenTextJson(boolean visibleOnly, int limit) {
        List<String> combined = new ArrayList<>();
        StringBuilder widgets = new StringBuilder("[");
        int count = 0;
        boolean first = true;
        for (Widget widget : json.safe(Widgets.getAllWidgets())) {
            if (widget == null) {
                continue;
            }
            count = appendWidgetText(widget.getChildrenCollection(), visibleOnly, limit, widgets, combined, count, first);
            first = count == 0;
            if (count >= limit) {
                break;
            }
        }
        widgets.append("]");
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"combined\":").append(json.stringListJson(combined)).append(",");
        sb.append("\"widgets\":").append(widgets);
        sb.append("}");
        return sb.toString();
    }

    private int appendWidgetText(Collection<WidgetChild> children, boolean visibleOnly, int limit, StringBuilder out, List<String> combined, int count, boolean first) {
        if (children == null || count >= limit) {
            return count;
        }
        for (WidgetChild child : children) {
            if (child == null) {
                continue;
            }
            if ((!visibleOnly || child.isVisible()) && widgetHasReadableText(child)) {
                if (!first || count > 0) {
                    out.append(",");
                }
                String clean = cleanText(firstReadableText(child));
                combined.add(clean);
                out.append("{");
                field(out, "widget_id", child.getWidgetId()).append(",");
                field(out, "child_id", child.getChildId()).append(",");
                field(out, "grandchild_id", child.getGrandChildId()).append(",");
                field(out, "visible", child.isVisible()).append(",");
                out.append("\"bounds\":").append(json.rectangleJson(child.getRectangle())).append(",");
                field(out, "text", child.getText()).append(",");
                field(out, "name", child.getName()).append(",");
                field(out, "tooltip", child.getTooltip()).append(",");
                field(out, "clean", clean);
                out.append("}");
                count++;
                first = false;
                if (count >= limit) {
                    return count;
                }
            }
            WidgetChild[] nested = child.getChildren();
            if (nested != null && nested.length > 0) {
                List<WidgetChild> list = new ArrayList<>();
                for (WidgetChild nestedChild : nested) {
                    if (nestedChild != null) {
                        list.add(nestedChild);
                    }
                }
                count = appendWidgetText(list, visibleOnly, limit, out, combined, count, first && count == 0);
                first = count == 0;
                if (count >= limit) {
                    return count;
                }
            }
        }
        return count;
    }

    private boolean widgetHasReadableText(WidgetChild widget) {
        return !cleanText(widget.getText()).isEmpty()
            || !cleanText(widget.getName()).isEmpty()
            || !cleanText(widget.getTooltip()).isEmpty();
    }

    private String firstReadableText(WidgetChild widget) {
        String text = cleanText(widget.getText());
        if (!text.isEmpty()) {
            return widget.getText();
        }
        text = cleanText(widget.getName());
        if (!text.isEmpty()) {
            return widget.getName();
        }
        return widget.getTooltip();
    }

    private List<WidgetTextRecord> widgetTextRecords(int rootWidgetId, boolean visibleOnly, int limit, boolean includeTooltip) {
        List<WidgetTextRecord> result = new ArrayList<>();
        if (limit <= 0) {
            return result;
        }
        if (rootWidgetId >= 0) {
            Widget widget = Widgets.getWidget(rootWidgetId);
            if (widget != null) {
                collectWidgetTextRecords(widget.getChildrenCollection(), visibleOnly, limit, includeTooltip, result);
            }
            return result;
        }
        for (Widget widget : json.safe(Widgets.getAllWidgets())) {
            if (widget == null) {
                continue;
            }
            collectWidgetTextRecords(widget.getChildrenCollection(), visibleOnly, limit, includeTooltip, result);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private void collectWidgetTextRecords(Collection<WidgetChild> children, boolean visibleOnly, int limit, boolean includeTooltip, List<WidgetTextRecord> out) {
        if (children == null || out.size() >= limit) {
            return;
        }
        for (WidgetChild child : children) {
            if (child == null) {
                continue;
            }
            String clean = widgetCleanText(child, includeTooltip);
            if ((!visibleOnly || child.isVisible()) && !clean.isEmpty()) {
                out.add(new WidgetTextRecord(child, clean));
                if (out.size() >= limit) {
                    return;
                }
            }
            WidgetChild[] nested = child.getChildren();
            if (nested != null && nested.length > 0) {
                List<WidgetChild> list = new ArrayList<>();
                for (WidgetChild nestedChild : nested) {
                    if (nestedChild != null) {
                        list.add(nestedChild);
                    }
                }
                collectWidgetTextRecords(list, visibleOnly, limit, includeTooltip, out);
                if (out.size() >= limit) {
                    return;
                }
            }
        }
    }

    private String widgetCleanText(WidgetChild widget, boolean includeTooltip) {
        String text = cleanText(widget.getText());
        if (!text.isEmpty()) {
            return text;
        }
        text = cleanText(widget.getName());
        if (!text.isEmpty()) {
            return text;
        }
        return includeTooltip ? cleanText(widget.getTooltip()) : "";
    }

    private String widgetTextRecordsJson(List<WidgetTextRecord> records) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (WidgetTextRecord record : json.safe(records)) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{");
            field(sb, "widget_id", record.widgetId).append(",");
            field(sb, "child_id", record.childId).append(",");
            field(sb, "grandchild_id", record.grandchildId).append(",");
            field(sb, "visible", record.visible).append(",");
            sb.append("\"bounds\":").append(json.rectangleJson(record.bounds)).append(",");
            field(sb, "clean", record.clean);
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String joinWidgetTexts(List<WidgetTextRecord> records, boolean skipControls) {
        List<String> values = new ArrayList<>();
        for (WidgetTextRecord record : json.safe(records)) {
            if (record.clean == null || record.clean.isEmpty()) {
                continue;
            }
            if (skipControls && isControlText(record.clean)) {
                continue;
            }
            values.add(record.clean);
        }
        return String.join(" ", values).trim();
    }

    private boolean isControlText(String text) {
        return "Ok".equalsIgnoreCase(text)
            || "Click here to continue".equalsIgnoreCase(text)
            || "Please wait...".equalsIgnoreCase(text);
    }

    private String cameraJson() {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "pitch", Camera.getPitch()).append(",");
        field(sb, "yaw", Camera.getYaw()).append(",");
        field(sb, "x", Camera.getX()).append(",");
        field(sb, "y", Camera.getY()).append(",");
        field(sb, "z", Camera.getZ()).append(",");
        field(sb, "zoom", Camera.getZoom()).append(",");
        field(sb, "min_zoom", Camera.getMinZoom()).append(",");
        field(sb, "max_zoom", Camera.getMaxZoom()).append(",");
        field(sb, "lowest_pitch", Camera.getLowestPitch()).append(",");
        field(sb, "mode", String.valueOf(Camera.getCameraMode()));
        sb.append("}");
        return sb.toString();
    }

    private String projectionTileJson(Map<String, String> query) {
        int x = queryInt(query, "x", Integer.MIN_VALUE);
        int y = queryInt(query, "y", Integer.MIN_VALUE);
        int z = queryInt(query, "z", Client.getPlane());
        require(x != Integer.MIN_VALUE && y != Integer.MIN_VALUE, "x and y are required");
        ViewportTools viewport = new ViewportTools();
        Point screen = viewport.tileToScreen(new Tile(x, y, z));
        Point minimap = viewport.getMiniMapCoordinate(x, y);
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"tile\":");
        sb.append(json.tileJson(new Tile(x, y, z))).append(",");
        sb.append("\"screen\":").append(json.pointJson(screen)).append(",");
        sb.append("\"minimap\":").append(json.pointJson(minimap)).append(",");
        field(sb, "on_game_screen", screen != null && viewport.isOnGameScreen(screen)).append(",");
        field(sb, "on_main_screen", screen != null && viewport.isVisibleOnMainScreen(screen));
        sb.append("}");
        return sb.toString();
    }

    private String settingsJson(Map<String, String> query) {
        StringBuilder sb = new StringBuilder("{\"ok\":true");
        int[] configs = PlayerSettings.getConfigs();
        int[] varbits = PlayerSettings.getVarBits();
        sb.append(",\"config_count\":").append(configs == null ? 0 : configs.length);
        sb.append(",\"varbit_count\":").append(varbits == null ? 0 : varbits.length);
        sb.append(",\"configs\":").append(settingsValuesJson(configs, idsFromQuery(query, "configs", "varps")));
        sb.append(",\"varps\":").append(settingsValuesJson(configs, idsFromQuery(query, "varps", "configs")));
        sb.append(",\"varbits\":").append(settingsValuesJson(varbits, idsFromQuery(query, "varbits", "bits")));
        sb.append("}");
        return sb.toString();
    }

    private String questsJson(Map<String, String> query) {
        String name = queryString(query, "name", queryString(query, "quest", ""));
        boolean all = queryBool(query, "all", name.isEmpty());
        String stateFilter = normalizeName(queryString(query, "state", ""));
        StringBuilder sb = new StringBuilder("{\"ok\":true");
        field(sb.append(","), "quest_points", Quests.getQuestPoints());
        if (!name.isEmpty()) {
            Quest quest = findQuest(name);
            sb.append(",\"quest\":").append(quest == null ? "null" : questJson(quest));
        } else if (all) {
            sb.append(",\"quests\":[");
            // Quest.values() exposes the same underlying quest under more than one
            // enum alias (e.g. CORSAIR_CURSE and THE_CORSAIR_CURSE share varbit
            // 6071), so dedupe by the (config_id, varbit_id) signature that uniquely
            // identifies a quest.
            Set<String> seen = new HashSet<>();
            boolean first = true;
            for (Quest quest : Quest.values()) {
                if (quest == null || !questStateMatches(quest, stateFilter)) {
                    continue;
                }
                int configId = quest.getConfigId();
                int varBitId = quest.getVarBitId();
                // Real quests are keyed by exactly one of config/varbit; aliases of
                // the same quest share that key. Fall back to the enum name when a
                // quest has neither id so two id-less quests are not merged.
                String signature = (configId == -1 && varBitId == -1) ? questName(quest) : configId + ":" + varBitId;
                if (!seen.add(signature)) {
                    continue;
                }
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(questJson(quest));
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    // Optional ?state= filter. Empty (default) returns every quest so existing
    // callers are unaffected; in_progress is the common case for an agent
    // tracking what it can act on right now.
    private boolean questStateMatches(Quest quest, String normalizedFilter) {
        if (normalizedFilter == null || normalizedFilter.isEmpty()) {
            return true;
        }
        boolean started = quest.isStarted();
        boolean finished = quest.isFinished();
        if ("notstarted".equals(normalizedFilter)) {
            return !started;
        }
        if ("started".equals(normalizedFilter) || "inprogress".equals(normalizedFilter)) {
            return started && !finished;
        }
        if ("finished".equals(normalizedFilter) || "complete".equals(normalizedFilter) || "completed".equals(normalizedFilter)) {
            return finished;
        }
        return true;
    }

    private String questJson(Quest quest) {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "name", questName(quest)).append(",");
        field(sb, "type", String.valueOf(quest.getType())).append(",");
        field(sb, "state", String.valueOf(quest.getState())).append(",");
        field(sb, "started", quest.isStarted()).append(",");
        field(sb, "finished", quest.isFinished()).append(",");
        field(sb, "config_id", quest.getConfigId()).append(",");
        field(sb, "varbit_id", quest.getVarBitId()).append(",");
        field(sb, "config_value", quest.getConfigValue()).append(",");
        field(sb, "started_setting", quest.getStartedSetting()).append(",");
        field(sb, "finished_setting", quest.getFinishedSetting()).append(",");
        sb.append("\"settings\":").append(json.intArrayJson(quest.getSettings()));
        sb.append("}");
        return sb.toString();
    }

    private String widgetsJson(Map<String, String> query) {
        WidgetQuery widgetQuery = widgetQueryFrom(query);
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"widgets\":[");
        int count = 0;
        boolean first = true;
        for (WidgetChild child : matchingWidgets(widgetQuery)) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(json.widgetJson(child));
            count++;
            if (count >= widgetQuery.limit) {
                break;
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    // Existing-and-sorted by relevance: on-screen first, then nearest. The
    // MAX_ENTITIES cap below truncates *after* this ordering, so the entities
    // the agent is most likely to act on survive instead of an arbitrary 40.
    private NPC targetNpc(String body) {
        return targetNpc(body, "");
    }

    private NPC targetNpc(String body, String prefix) {
        TargetSpec spec = targetSpecFrom(body, prefix);
        require(spec.hasIdentity(), "npc target requires name, id, or tile");
        NPC best = null;
        double bestDistance = Double.MAX_VALUE;
        for (NPC npc : json.safe(NPCs.all())) {
            if (npc == null || !npc.exists() || !spec.matches(npc, npc.getName(), npc.getId(), npc.getActions())) {
                continue;
            }
            double distance = npc.distance();
            if (distance < bestDistance) {
                best = npc;
                bestDistance = distance;
            }
        }
        return best;
    }

    private GameObject targetObject(String body) {
        return targetObject(body, "");
    }

    private GameObject targetObject(String body, String prefix) {
        TargetSpec spec = targetSpecFrom(body, prefix);
        require(spec.hasIdentity(), "object target requires name, id, or tile");
        GameObject best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GameObject object : json.safe(GameObjects.all())) {
            if (object == null || !object.exists() || !spec.matches(object, object.getName(), object.getId(), object.getActions())) {
                continue;
            }
            double distance = object.distance();
            if (distance < bestDistance) {
                best = object;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Player targetPlayer(String body) {
        TargetSpec spec = targetSpecFrom(body, "");
        require(spec.hasIdentity(), "player target requires name, id, or tile");
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : json.safe(Players.all())) {
            if (player == null || !player.exists() || !spec.matches(player, player.getName(), player.getIndex(), player.getActions())) {
                continue;
            }
            double distance = player.distance();
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private GroundItem targetGroundItem(String body) {
        TargetSpec spec = targetSpecFrom(body, "");
        require(spec.hasIdentity(), "ground item target requires name, id, or tile");
        GroundItem best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GroundItem item : json.safe(GroundItems.all())) {
            if (item == null || !item.exists() || !spec.matches(item, item.getName(), item.getId(), item.getActions())) {
                continue;
            }
            double distance = item.distance();
            if (distance < bestDistance) {
                best = item;
                bestDistance = distance;
            }
        }
        return best;
    }

    private TargetSpec targetSpecFrom(String body, String prefix) {
        String name = stringField(body, prefix + "name", "");
        boolean contains = boolField(body, prefix + "contains", false);
        int id = intField(body, prefix + "id", Integer.MIN_VALUE);
        int x = intField(body, prefix + "x", Integer.MIN_VALUE);
        int y = intField(body, prefix + "y", Integer.MIN_VALUE);
        int z = intField(body, prefix + "z", Client.getPlane());
        Tile tile = x == Integer.MIN_VALUE || y == Integer.MIN_VALUE ? null : new Tile(x, y, z);
        int tileDistance = intField(body, prefix + "tile_distance", intField(body, prefix + "radius", 0));
        double maxDistance = doubleField(body, prefix + "max_distance", Double.POSITIVE_INFINITY);
        String action = stringField(body, prefix + "action", "");
        return new TargetSpec(name, contains, id, tile, tileDistance, maxDistance, action);
    }

    private Item targetInventoryItem(String body, String prefix) {
        int slot = intField(body, prefix + "slot", Integer.MIN_VALUE);
        int id = intField(body, prefix + "id", intField(body, prefix + "item_id", Integer.MIN_VALUE));
        String name = stringField(body, prefix + "name", stringField(body, prefix + "item_name", ""));
        boolean contains = boolField(body, prefix + "contains", false);
        require(slot != Integer.MIN_VALUE || id != Integer.MIN_VALUE || !name.isEmpty(), prefix + "inventory item requires slot, id, or name");
        for (Item item : json.safe(Inventory.all())) {
            if (item == null || item.getId() <= 0) {
                continue;
            }
            if (slot != Integer.MIN_VALUE && item.getSlot() != slot) {
                continue;
            }
            if (id != Integer.MIN_VALUE && item.getId() != id) {
                continue;
            }
            if (!name.isEmpty() && !stringMatches(item.getName(), name, contains)) {
                continue;
            }
            return item;
        }
        return null;
    }

    private Spell targetSpell(String body) {
        String name = stringField(body, "spell", stringField(body, "spell_name", ""));
        require(!name.isEmpty(), "spell is required");
        String normalized = name.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        for (Normal spell : Normal.values()) {
            if (spell.name().equals(normalized) || spell.toString().equalsIgnoreCase(name.trim())) {
                return spell;
            }
        }
        throw new IllegalArgumentException("unsupported normal spell: " + name);
    }

    private WidgetChild targetWidget(String body) {
        WidgetQuery query = widgetQueryFrom(body);
        List<WidgetChild> matches = matchingWidgets(query);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private WidgetQuery widgetQueryFrom(Map<String, String> query) {
        return new WidgetQuery(
            queryInt(query, "widget", Integer.MIN_VALUE),
            queryInt(query, "child", Integer.MIN_VALUE),
            queryInt(query, "grandchild", Integer.MIN_VALUE),
            queryString(query, "text", ""),
            queryString(query, "action", ""),
            queryBool(query, "contains", true),
            queryBool(query, "visible_only", true),
            Math.min(Math.max(queryInt(query, "limit", 50), 1), 500)
        );
    }

    private WidgetQuery widgetQueryFrom(String body) {
        return new WidgetQuery(
            intField(body, "widget", Integer.MIN_VALUE),
            intField(body, "child", Integer.MIN_VALUE),
            intField(body, "grandchild", Integer.MIN_VALUE),
            stringField(body, "text", ""),
            stringField(body, "action", ""),
            boolField(body, "contains", true),
            boolField(body, "visible_only", true),
            Math.min(Math.max(intField(body, "limit", 50), 1), 500)
        );
    }

    private List<WidgetChild> matchingWidgets(WidgetQuery query) {
        List<WidgetChild> result = new ArrayList<>();
        WidgetChild direct = query.getDirect();
        if (direct != null) {
            if (query.matches(direct)) {
                result.add(direct);
            }
            return result;
        }
        for (Widget widget : json.safe(Widgets.getAllWidgets())) {
            if (widget == null) {
                continue;
            }
            collectMatchingWidgets(widget.getChildrenCollection(), query, result);
            if (result.size() >= query.limit) {
                break;
            }
        }
        return result;
    }

    private void collectMatchingWidgets(Collection<WidgetChild> widgets, WidgetQuery query, List<WidgetChild> result) {
        if (widgets == null || result.size() >= query.limit) {
            return;
        }
        for (WidgetChild child : widgets) {
            if (child == null) {
                continue;
            }
            if (query.matches(child)) {
                result.add(child);
                if (result.size() >= query.limit) {
                    return;
                }
            }
            WidgetChild[] children = child.getChildren();
            if (children != null && children.length > 0) {
                List<WidgetChild> nested = new ArrayList<>();
                for (WidgetChild nestedChild : children) {
                    if (nestedChild != null) {
                        nested.add(nestedChild);
                    }
                }
                collectMatchingWidgets(nested, query, result);
                if (result.size() >= query.limit) {
                    return;
                }
            }
        }
    }

    private String waitUntilJson(String body) {
        String type = stringField(body, "type", "");
        require(!type.isEmpty(), "type is required");
        long timeoutMs = Math.min(Math.max(intField(body, "timeout_ms", 5000), 1), 60000);
        long pollMs = Math.min(Math.max(intField(body, "poll_ms", 200), 25), 5000);
        long start = System.currentTimeMillis();
        boolean met = false;
        String detail = "{}";
        while (System.currentTimeMillis() - start <= timeoutMs) {
            WaitResult result = evaluateWait(type, body);
            detail = result.detailJson;
            if (result.met) {
                met = true;
                break;
            }
            sleepQuietly(pollMs);
        }
        long elapsed = System.currentTimeMillis() - start;
        return "{\"ok\":true,\"action\":\"wait_until\",\"type\":" + quote(type) + ",\"result\":" + met + ",\"elapsed_ms\":" + elapsed + ",\"detail\":" + detail + "}";
    }

    private WaitResult evaluateWait(String type, String body) {
        String normalized = type.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if ("login".equals(normalized)) {
            normalized = "logged_in";
        }
        if ("tile".equals(normalized)) {
            Player local = Players.getLocal();
            int x = intField(body, "x", Integer.MIN_VALUE);
            int y = intField(body, "y", Integer.MIN_VALUE);
            int z = intField(body, "z", Client.getPlane());
            int distance = Math.max(0, intField(body, "distance", 0));
            require(x != Integer.MIN_VALUE && y != Integer.MIN_VALUE, "x and y are required");
            boolean met = local != null && local.getTile() != null && local.getTile().distance(new Tile(x, y, z)) <= distance;
            return new WaitResult(met, "{\"tile\":" + json.tileJson(local == null ? null : local.getTile()) + "}");
        }
        if ("inventory_contains".equals(normalized) || "inventory".equals(normalized)) {
            int id = intField(body, "id", Integer.MIN_VALUE);
            String name = stringField(body, "name", "");
            int amount = Math.max(1, intField(body, "amount", 1));
            int found = inventoryAmount(id, name);
            return new WaitResult(found >= amount, "{\"amount\":" + found + "}");
        }
        if ("dialogue_text".equals(normalized) || "dialogue".equals(normalized)) {
            String text = stringField(body, "text", "");
            String state = stringField(body, "state", "").trim().toLowerCase(Locale.ROOT).replace('-', '_');
            String dialogue = Dialogues.getNPCDialogue();
            boolean hasDialogueText = dialogue != null && !dialogue.trim().isEmpty();
            boolean met;
            if ("can_continue".equals(state)) {
                met = Dialogues.canContinue();
            } else if ("options".equals(state) || "options_available".equals(state)) {
                met = Dialogues.areOptionsAvailable();
            } else if ("input".equals(state) || "can_enter_input".equals(state)) {
                met = Dialogues.canEnterInput();
            } else if ("active".equals(state) || "in_dialogue".equals(state)) {
                met = Dialogues.canContinue() || Dialogues.areOptionsAvailable() || hasDialogueText;
            } else if ("inactive".equals(state) || "not_in_dialogue".equals(state)) {
                met = !Dialogues.canContinue() && !Dialogues.areOptionsAvailable() && !hasDialogueText;
            } else {
                met = text.isEmpty() ? (Dialogues.canContinue() || Dialogues.areOptionsAvailable() || hasDialogueText) : containsIgnoreCase(dialogue, text);
            }
            return new WaitResult(met, "{\"dialogue\":" + dialogueJson() + "}");
        }
        if ("dialogue_continue".equals(normalized)) {
            return new WaitResult(Dialogues.canContinue(), "{\"dialogue\":" + dialogueJson() + "}");
        }
        if ("dialogue_options".equals(normalized)) {
            return new WaitResult(Dialogues.areOptionsAvailable(), "{\"dialogue\":" + dialogueJson() + "}");
        }
        if ("widget_visible".equals(normalized) || "widget".equals(normalized)) {
            WidgetChild widget = targetWidget(body);
            return new WaitResult(widget != null && widget.isVisible(), "{\"widget\":" + json.widgetJson(widget) + "}");
        }
        if ("animation_done".equals(normalized) || "idle".equals(normalized)) {
            Player local = Players.getLocal();
            boolean moving = local != null && local.isMoving();
            int animation = local == null ? -1 : local.getAnimation();
            boolean requireNotMoving = boolField(body, "not_moving", true);
            boolean met = animation == -1 && (!requireNotMoving || !moving);
            return new WaitResult(met, "{\"animation\":" + animation + ",\"moving\":" + moving + "}");
        }
        if ("varp".equals(normalized) || "config".equals(normalized)) {
            int id = intField(body, "id", Integer.MIN_VALUE);
            require(id != Integer.MIN_VALUE, "id is required");
            int value = PlayerSettings.getConfig(id);
            return new WaitResult(settingValueMatches(body, value), "{\"id\":" + id + ",\"value\":" + value + "}");
        }
        if ("varbit".equals(normalized)) {
            int id = intField(body, "id", Integer.MIN_VALUE);
            require(id != Integer.MIN_VALUE, "id is required");
            int value = PlayerSettings.getBitValue(id);
            return new WaitResult(settingValueMatches(body, value), "{\"id\":" + id + ",\"value\":" + value + "}");
        }
        if ("quest_state".equals(normalized) || "quest".equals(normalized)) {
            String name = stringField(body, "name", stringField(body, "quest", ""));
            require(!name.isEmpty(), "name is required");
            Quest quest = findQuest(name);
            String expected = stringField(body, "state", "");
            boolean met = quest != null && (expected.isEmpty() || String.valueOf(quest.getState()).equalsIgnoreCase(expected));
            return new WaitResult(met, "{\"quest\":" + (quest == null ? "null" : questJson(quest)) + "}");
        }
        if ("logged_in".equals(normalized)) {
            boolean expected = boolField(body, "value", true);
            return new WaitResult(Client.isLoggedIn() == expected, "{\"logged_in\":" + Client.isLoggedIn() + "}");
        }
        throw new IllegalArgumentException("unsupported wait type: " + type);
    }

    private boolean setCameraFromBody(String body) {
        boolean result = true;
        int yaw = intField(body, "yaw", Integer.MIN_VALUE);
        int pitch = intField(body, "pitch", Integer.MIN_VALUE);
        int zoom = intField(body, "zoom", Integer.MIN_VALUE);
        if (yaw != Integer.MIN_VALUE && pitch != Integer.MIN_VALUE) {
            result = Camera.rotateTo(yaw, pitch) && result;
        } else if (yaw != Integer.MIN_VALUE) {
            result = Camera.rotateToYaw(yaw) && result;
        } else if (pitch != Integer.MIN_VALUE) {
            result = Camera.rotateToPitch(pitch) && result;
        }
        if (zoom != Integer.MIN_VALUE) {
            result = Camera.setZoom(zoom) && result;
        }
        return result;
    }

    private boolean rotateCameraToBody(String body) {
        String target = stringField(body, "target", "tile").trim().toLowerCase(Locale.ROOT);
        if ("tile".equals(target)) {
            int x = intField(body, "x", Integer.MIN_VALUE);
            int y = intField(body, "y", Integer.MIN_VALUE);
            int z = intField(body, "z", Client.getPlane());
            require(x != Integer.MIN_VALUE && y != Integer.MIN_VALUE, "x and y are required");
            return Camera.rotateToTile(new Tile(x, y, z));
        }
        if ("npc".equals(target)) {
            NPC npc = targetNpc(body);
            return npc != null && Camera.rotateToEntity(npc);
        }
        if ("object".equals(target)) {
            GameObject object = targetObject(body);
            return object != null && Camera.rotateToEntity(object);
        }
        if ("player".equals(target)) {
            Player player = targetPlayer(body);
            return player != null && Camera.rotateToEntity(player);
        }
        if ("ground_item".equals(target) || "ground-item".equals(target)) {
            GroundItem item = targetGroundItem(body);
            return item != null && Camera.rotateToEntity(item);
        }
        throw new IllegalArgumentException("unsupported camera target: " + target);
    }

    private void requestScriptStop(final String reason) {
        scriptStopReason = reason;
        scriptStopNotBeforeMs = System.currentTimeMillis() + 600L;
        scriptStopRequested = true;
    }

    private void requestScriptSlotRelease(final String reason) {
        if (releaseRequested) {
            return;
        }
        releaseRequested = true;
        try {
            stop();
        } catch (Throwable ignored) {
        }

        Thread release = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 8; i++) {
                    try {
                        Thread.sleep(i == 0 ? 100L : 250L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        ScriptManager manager = ScriptManager.getScriptManager();
                        if (manager == null) {
                            return;
                        }
                        Object current = manager.getCurrentScript();
                        if (current != DreamBotMcpScript.this) {
                            return;
                        }
                        if (manager.isRunning()) {
                            debug("DreamBotMcpScript releasing script slot after " + reason);
                            manager.stop();
                        }
                        try {
                            DreamBotMcpScript.this.stop();
                        } catch (Throwable ignored) {
                        }
                        if (!manager.isRunning() || manager.getCurrentScript() != DreamBotMcpScript.this) {
                            return;
                        }
                    } catch (Throwable t) {
                        System.out.println("DreamBotMcpScript script slot release failed after " + reason + ": " + t);
                    }
                }
                System.out.println("DreamBotMcpScript script slot release request did not finish after " + reason);
            }
        }, "dreambot-mcp-slot-release");
        release.setDaemon(true);
        release.start();
    }

    private void stopScriptIfRequested() {
        if (!scriptStopRequested || System.currentTimeMillis() < scriptStopNotBeforeMs) {
            return;
        }
        scriptStopRequested = false;
        Thread release = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 8; i++) {
                    try {
                        Thread.sleep(i == 0 ? 50L : 250L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        ScriptManager manager = ScriptManager.getScriptManager();
                        if (manager != null && manager.isRunning() && manager.getCurrentScript() == DreamBotMcpScript.this) {
                            System.out.println("DreamBotMcpScript stopping current script after " + scriptStopReason);
                            manager.stop();
                        }
                        try {
                            DreamBotMcpScript.this.stop();
                        } catch (Throwable ignored) {
                        }
                        if (manager == null || !manager.isRunning() || manager.getCurrentScript() != DreamBotMcpScript.this) {
                            return;
                        }
                    } catch (Throwable t) {
                        System.out.println("DreamBotMcpScript script stop failed after " + scriptStopReason + ": " + t);
                    }
                }
                System.out.println("DreamBotMcpScript script stop request did not finish after " + scriptStopReason);
            }
        }, "dreambot-mcp-stop-after-action");
        release.setDaemon(true);
        release.start();
    }

    private int inventoryAmount(int id, String name) {
        int amount = 0;
        for (Item item : json.safe(Inventory.all())) {
            if (item == null || item.getId() <= 0) {
                continue;
            }
            if (id != Integer.MIN_VALUE && item.getId() != id) {
                continue;
            }
            if (!name.isEmpty() && !stringMatches(item.getName(), name, false)) {
                continue;
            }
            amount += Math.max(1, item.getAmount());
        }
        return amount;
    }

    private boolean settingValueMatches(String body, int value) {
        int expected = intField(body, "value", Integer.MIN_VALUE);
        if (expected != Integer.MIN_VALUE) {
            return value == expected;
        }
        int changedFrom = intField(body, "changed_from", Integer.MIN_VALUE);
        return changedFrom == Integer.MIN_VALUE || value != changedFrom;
    }

    private String okResult(String action, boolean result) {
        return "{\"ok\":true,\"action\":" + quote(action) + ",\"result\":" + result + "}";
    }

    private String interactionResult(String action, boolean result, String targetJson) {
        return "{\"ok\":true,\"action\":" + quote(action) + ",\"result\":" + result + ",\"target\":" + targetJson + "}";
    }

    private String itemOnResult(String action, boolean result, Item item, String targetJson) {
        return "{\"ok\":true,\"action\":" + quote(action) + ",\"result\":" + result + ",\"item\":" + json.itemJson(item) + ",\"target\":" + targetJson + "}";
    }

    private String spellResult(String action, boolean result, Spell spell, String targetJson) {
        return "{\"ok\":true,\"action\":" + quote(action) + ",\"result\":" + result + ",\"spell\":" + json.spellJson(spell) + ",\"target\":" + targetJson + "}";
    }
    private boolean isRightClick(String body) {
        String button = stringField(body, "button", "").trim().toLowerCase(Locale.ROOT);
        if (button.isEmpty()) {
            return boolField(body, "right", false);
        }
        if ("right".equals(button) || "right_click".equals(button) || "button3".equals(button)) {
            return true;
        }
        if ("left".equals(button) || "left_click".equals(button) || "button1".equals(button)) {
            return false;
        }
        throw new IllegalArgumentException("unsupported mouse button: " + button);
    }

    private int keyCodeField(String body) {
        int keyCode = intField(body, "keyCode", Integer.MIN_VALUE);
        if (keyCode != Integer.MIN_VALUE) {
            return keyCode;
        }
        String key = stringField(body, "key", "").trim();
        require(!key.isEmpty(), "key or keyCode is required");
        int mapped = keyCodeForName(key);
        require(mapped != Integer.MIN_VALUE, "unsupported key: " + key);
        return mapped;
    }

    private int keyCodeForName(String key) {
        String normalized = key.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.length() == 1) {
            char c = normalized.charAt(0);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                return KeyEvent.getExtendedKeyCodeForChar(c);
            }
        }
        if (normalized.matches("F\\d{1,2}")) {
            int number = Integer.parseInt(normalized.substring(1));
            if (number >= 1 && number <= 12) {
                return KeyEvent.VK_F1 + number - 1;
            }
        }
        if ("ENTER".equals(normalized) || "RETURN".equals(normalized)) {
            return KeyEvent.VK_ENTER;
        }
        if ("TAB".equals(normalized)) {
            return KeyEvent.VK_TAB;
        }
        if ("ESC".equals(normalized) || "ESCAPE".equals(normalized)) {
            return KeyEvent.VK_ESCAPE;
        }
        if ("SPACE".equals(normalized) || "SPACEBAR".equals(normalized)) {
            return KeyEvent.VK_SPACE;
        }
        if ("BACKSPACE".equals(normalized) || "BACK_SPACE".equals(normalized)) {
            return KeyEvent.VK_BACK_SPACE;
        }
        if ("DELETE".equals(normalized) || "DEL".equals(normalized)) {
            return KeyEvent.VK_DELETE;
        }
        if ("UP".equals(normalized) || "ARROW_UP".equals(normalized)) {
            return KeyEvent.VK_UP;
        }
        if ("DOWN".equals(normalized) || "ARROW_DOWN".equals(normalized)) {
            return KeyEvent.VK_DOWN;
        }
        if ("LEFT".equals(normalized) || "ARROW_LEFT".equals(normalized)) {
            return KeyEvent.VK_LEFT;
        }
        if ("RIGHT".equals(normalized) || "ARROW_RIGHT".equals(normalized)) {
            return KeyEvent.VK_RIGHT;
        }
        if ("HOME".equals(normalized)) {
            return KeyEvent.VK_HOME;
        }
        if ("END".equals(normalized)) {
            return KeyEvent.VK_END;
        }
        if ("PAGE_UP".equals(normalized) || "PGUP".equals(normalized)) {
            return KeyEvent.VK_PAGE_UP;
        }
        if ("PAGE_DOWN".equals(normalized) || "PGDN".equals(normalized)) {
            return KeyEvent.VK_PAGE_DOWN;
        }
        if ("SHIFT".equals(normalized)) {
            return KeyEvent.VK_SHIFT;
        }
        if ("CTRL".equals(normalized) || "CONTROL".equals(normalized)) {
            return KeyEvent.VK_CONTROL;
        }
        if ("ALT".equals(normalized)) {
            return KeyEvent.VK_ALT;
        }
        return Integer.MIN_VALUE;
    }

    private int[] idsFromQuery(Map<String, String> query, String primary, String secondary) {
        String value = queryString(query, primary, "");
        if (value.isEmpty()) {
            value = queryString(query, secondary, "");
        }
        if (value.isEmpty()) {
            return new int[0];
        }
        String[] parts = value.split(",");
        int[] ids = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            try {
                ids[count++] = Integer.parseInt(part.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        int[] result = new int[count];
        System.arraycopy(ids, 0, result, 0, count);
        return result;
    }

    private String settingsValuesJson(int[] values, int[] ids) {
        StringBuilder sb = new StringBuilder("{");
        if (values != null && ids != null) {
            boolean first = true;
            for (int id : ids) {
                if (id < 0 || id >= values.length) {
                    continue;
                }
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(quote(String.valueOf(id))).append(":").append(values[id]);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private Quest findQuest(String name) {
        String target = normalizeName(name);
        for (Quest quest : Quest.values()) {
            if (normalizeName(questName(quest)).equals(target)) {
                return quest;
            }
        }
        for (FreeQuest quest : FreeQuest.values()) {
            if (normalizeName(quest.name()).equals(target)) {
                return quest;
            }
        }
        for (PaidQuest quest : PaidQuest.values()) {
            if (normalizeName(quest.name()).equals(target)) {
                return quest;
            }
        }
        return null;
    }

    private String questName(Quest quest) {
        return quest instanceof Enum ? ((Enum<?>) quest).name() : String.valueOf(quest);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private boolean stringMatches(String actual, String expected, boolean contains) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return contains ? containsIgnoreCase(actual, expected) : actual.equalsIgnoreCase(expected);
    }

    private boolean containsIgnoreCase(String actual, String expected) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        return actual != null && actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private boolean actionMatches(String[] actions, String action) {
        if (action == null || action.isEmpty()) {
            return true;
        }
        if (actions == null) {
            return false;
        }
        for (String available : actions) {
            if (available != null && available.equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }

    private boolean tileMatches(Tile actual, Tile expected, int distance) {
        if (expected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (actual.getZ() != expected.getZ()) {
            return false;
        }
        int dx = actual.getX() - expected.getX();
        int dy = actual.getY() - expected.getY();
        return Math.sqrt(dx * dx + dy * dy) <= Math.max(0, distance);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private class TargetSpec {
        final String name;
        final boolean contains;
        final int id;
        final Tile tile;
        final int tileDistance;
        final double maxDistance;
        final String action;

        TargetSpec(String name, boolean contains, int id, Tile tile, int tileDistance, double maxDistance, String action) {
            this.name = name;
            this.contains = contains;
            this.id = id;
            this.tile = tile;
            this.tileDistance = tileDistance;
            this.maxDistance = maxDistance;
            this.action = action;
        }

        boolean hasIdentity() {
            return !name.isEmpty() || id != Integer.MIN_VALUE || tile != null;
        }

        boolean matches(Entity entity, String actualName, int actualId, String[] actions) {
            return (id == Integer.MIN_VALUE || actualId == id)
                && stringMatches(actualName, name, contains)
                && tileMatches(entity.getTile(), tile, tileDistance)
                && (Double.isInfinite(maxDistance) || entity.distance() <= maxDistance)
                && actionMatches(actions, action);
        }
    }

    private class WidgetQuery {
        final int widget;
        final int child;
        final int grandchild;
        final String text;
        final String action;
        final boolean contains;
        final boolean visibleOnly;
        final int limit;

        WidgetQuery(int widget, int child, int grandchild, String text, String action, boolean contains, boolean visibleOnly, int limit) {
            this.widget = widget;
            this.child = child;
            this.grandchild = grandchild;
            this.text = text;
            this.action = action;
            this.contains = contains;
            this.visibleOnly = visibleOnly;
            this.limit = limit;
        }

        WidgetChild getDirect() {
            if (widget == Integer.MIN_VALUE) {
                return null;
            }
            if (child == Integer.MIN_VALUE) {
                Widget root = Widgets.getWidget(widget);
                if (root == null) {
                    return null;
                }
                List<WidgetChild> children = root.getChildren();
                return children == null || children.isEmpty() ? null : children.get(0);
            }
            if (grandchild == Integer.MIN_VALUE) {
                return Widgets.get(widget, child);
            }
            return Widgets.get(widget, child, grandchild);
        }

        boolean matches(WidgetChild widgetChild) {
            if (widgetChild == null) {
                return false;
            }
            if (visibleOnly && !widgetChild.isVisible()) {
                return false;
            }
            if (widget != Integer.MIN_VALUE && widgetChild.getWidgetId() != widget && widgetChild.getID() != widget) {
                return false;
            }
            if (child != Integer.MIN_VALUE && widgetChild.getChildId() != child && widgetChild.getIndex() != child) {
                return false;
            }
            if (grandchild != Integer.MIN_VALUE && widgetChild.getGrandChildId() != grandchild) {
                return false;
            }
            if (!text.isEmpty()) {
                boolean textMatches = stringMatches(widgetChild.getText(), text, contains)
                    || stringMatches(widgetChild.getName(), text, contains)
                    || stringMatches(widgetChild.getTooltip(), text, contains);
                if (!textMatches) {
                    return false;
                }
            }
            return actionMatches(widgetChild.getActions(), action);
        }
    }
    private static class WaitResult {
        final boolean met;
        final String detailJson;

        WaitResult(boolean met, String detailJson) {
            this.met = met;
            this.detailJson = detailJson;
        }
    }

    private static class LoginAttempt {
        final boolean ok;
        final boolean alreadyLoggedIn;
        final String trigger;
        final String error;
        final int attempts;
        final long elapsedMs;
        final String stage;
        final String response;
        final String detail;

        LoginAttempt(boolean ok, boolean alreadyLoggedIn, String trigger, String error, int attempts, long elapsedMs, String stage, String response, String detail) {
            this.ok = ok;
            this.alreadyLoggedIn = alreadyLoggedIn;
            this.trigger = trigger == null ? "" : trigger;
            this.error = error == null ? "" : error;
            this.attempts = attempts;
            this.elapsedMs = elapsedMs;
            this.stage = stage == null ? "" : stage;
            this.response = response == null ? "" : response;
            this.detail = detail == null ? "" : detail;
        }

        static LoginAttempt success(boolean alreadyLoggedIn, String trigger, int attempts, long elapsedMs, String stage, String response, String detail) {
            return new LoginAttempt(true, alreadyLoggedIn, trigger, "", attempts, elapsedMs, stage, response, detail);
        }

        static LoginAttempt failure(String trigger, String error, int attempts, long elapsedMs, String stage, String response, String detail) {
            return new LoginAttempt(false, false, trigger, error, attempts, elapsedMs, stage, response, detail);
        }

        String failureJson(String path) {
            StringBuilder sb = new StringBuilder("{\"ok\":false");
            field(sb.append(","), "error", error).append(",");
            field(sb, "path", path).append(",");
            appendFields(sb);
            sb.append("}");
            return sb.toString();
        }

        String resultJson(String action) {
            StringBuilder sb = new StringBuilder("{\"ok\":").append(ok);
            field(sb.append(","), "action", action).append(",");
            field(sb, "result", ok).append(",");
            if (!ok) {
                field(sb, "error", error).append(",");
            }
            appendFields(sb);
            sb.append("}");
            return sb.toString();
        }

        private void appendFields(StringBuilder sb) {
            field(sb, "trigger", trigger).append(",");
            field(sb, "already_logged_in", alreadyLoggedIn).append(",");
            field(sb, "attempts", attempts).append(",");
            field(sb, "elapsed_ms", elapsedMs).append(",");
            field(sb, "stage", stage).append(",");
            field(sb, "response", response).append(",");
            field(sb, "detail", detail);
        }
    }

    private static class WidgetTextRecord {
        final int widgetId;
        final int childId;
        final int grandchildId;
        final boolean visible;
        final Rectangle bounds;
        final String clean;

        WidgetTextRecord(WidgetChild widget, String clean) {
            this.widgetId = widget.getWidgetId();
            this.childId = widget.getChildId();
            this.grandchildId = widget.getGrandChildId();
            this.visible = widget.isVisible();
            this.bounds = widget.getRectangle();
            this.clean = clean == null ? "" : clean;
        }
    }

    private static class RuntimeTask {
        final Callable<?> callable;
        final CompletableFuture<Object> future = new CompletableFuture<>();

        RuntimeTask(Callable<?> callable) {
            this.callable = callable;
        }
    }

}
