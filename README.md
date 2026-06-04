# DreamBot MCP

Standalone MCP server that runs inside a local DreamBot client as the
`DreamBot MCP` script.

Once the script is started, MCP clients connect directly to:

```text
http://127.0.0.1:17653/mcp
```

There is no auth broker, external coordinator, account database, or host
harness.

## SDN Usage

Add `DreamBot MCP` from DreamBot SDN, start it from DreamBot's Script Manager,
then configure your MCP client.

Claude Code:

```bash
claude mcp add -s user --transport http dreambot http://127.0.0.1:17653/mcp
```

JSON-style config:

```json
{
  "mcpServers": {
    "dreambot": {
      "type": "http",
      "url": "http://127.0.0.1:17653/mcp"
    }
  }
}
```

The release artifact is a single DreamBot script jar. Runtime use does not
require any files outside that jar.

## Build

The DreamBot client/API jar is required only for compiling from source.

```bash
export DREAMBOT_API_JAR=/path/to/dreambot-client.jar
./bin/build
```

Output:

```text
dist/dreambot-mcp.jar
```

The build shades Janino into the jar so Java eval and resident task compilation
remain self-contained at runtime. The jar is built as Java 11 bytecode so
DreamBot can load it.

Useful local commands:

```bash
java -jar dist/dreambot-mcp.jar self-test
java -jar dist/dreambot-mcp.jar jar-info
java -jar dist/dreambot-mcp.jar install-script --scripts-dir "$HOME/DreamBot/Scripts"
```

## Runtime Settings

Startup args:

- `--mcp-bind-host=127.0.0.1`
- `--mcp-port=17653`
- `--mcp-allow-lifecycle=true`
- `--mcp-login-solver-policy=after_initial_login`
- `--mcp-paint=false`
- `--mcp-debug=true`

Equivalent JVM/system environment settings:

- `DREAMBOT_MCP_BIND_HOST`: in-client runtime bind host, default `127.0.0.1`
- `DREAMBOT_MCP_PORT`: in-client runtime port, default `17653`
- `DREAMBOT_MCP_ALLOW_LIFECYCLE`: expose lifecycle tools, default `true`
- `DREAMBOT_MCP_LOGIN_SOLVER_POLICY`: `after_initial_login`, `enabled`, or `disabled`; default `after_initial_login`
- `DREAMBOT_MCP_PAINT`: show the runtime paint overlay, default `false`
- `DREAMBOT_MCP_DEBUG`: print startup/runtime debug lines, default `true`

`after_initial_login` lets DreamBot's own Login Handler perform the initial
configured-account login. Once the game reaches logged-in state, `DreamBot MCP`
disables the idle Login Handler. If an MCP runtime request arrives while the
account is logged out, `DreamBot MCP` attempts to log back in with the DreamBot
account configured in this client before running the requested tool.

The default loopback bind is intentional. Do not expose this port to the public
internet.

At startup and in the paint overlay, the script prints the local MCP URL and
LAN candidate URLs for the current network namespace. If the bind host is still
`127.0.0.1`, the LAN line is labeled loopback-only and shows which URLs would
work after binding to `0.0.0.0`.

## Tools

The MCP exposes runtime state, screenshots, UI, interaction, login, chat,
keyboard, mouse, camera, inventory, equipment, quest, settings, Java eval, and
resident Java task tools.

Common tools:

- `dreambot_runtime_health`
- `dreambot_login_status`
- `dreambot_client`
- `dreambot_screenshot`
- `dreambot_inventory`
- `dreambot_npcs`
- `dreambot_objects`
- `dreambot_players`
- `dreambot_ui_summary`
- `dreambot_chat_say`
- `dreambot_login_reconnect`
- `dreambot_java_eval`
- `dreambot_agent_task_start`
- `dreambot_agent_task_status`
- `dreambot_agent_task_logs`
- `dreambot_agent_task_stop`

`dreambot_screenshot` captures a PNG from inside the DreamBot client and returns
it as MCP image content. By default it captures the game canvas; pass
`target="screen"` to capture the full display instead.

`dreambot_java_eval` compiles a Java expression or block on the DreamBot script
thread. Lambdas and streams are not supported; use ordinary loops. Example
expression:

```java
Client.isLoggedIn()
```

Example block:

```java
String state = Client.getGameState().toString();
return state + " loggedIn=" + Client.isLoggedIn();
```

For looped behavior, use the resident task runner instead.

## Resident Java Tasks

`dreambot_agent_task_start` compiles Java source in memory and runs it from the
existing `DreamBot MCP` script's `onLoop`. It does not start another DreamBot
script or require files on disk.

Loop-body mode. Use `ctx.log(...)` for messages returned by
`dreambot_agent_task_logs`. Return a delay in milliseconds, call
`ctx.stop(reason)`, or return a negative delay to stop the task.

```java
ctx.log("loop " + ctx.loopCount() + " loggedIn=" + Client.isLoggedIn());
if (ctx.loopCount() >= 3) {
    ctx.stop("demo complete");
}
return 600;
```

Full-class mode:

```java
public final class DemoTask extends AgentTask {
    public void onStart(AgentTask.Context ctx) {
        ctx.log("started");
    }

    public int onLoop(AgentTask.Context ctx) {
        ctx.log("tile=" + Players.getLocal().getTile());
        ctx.stop("one tick");
        return 1000;
    }
}
```

The task context supports `ctx.log(...)`, `ctx.stop(...)`, `ctx.loopCount()`,
`ctx.elapsedMs()`, and `ctx.script()`. DreamBot API classes are imported by
default for generated loop-body tasks. DreamBot `Logger.log(...)` still writes
to the client log, but it is not returned by `dreambot_agent_task_logs`; use
`ctx.log(...)` when the AI agent should read the task log back through MCP.

## Verification

```bash
./bin/build
java -jar dist/dreambot-mcp.jar self-test
curl -s http://127.0.0.1:17653/health
curl -s \
  -H 'Content-Type: application/json' \
  --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}' \
  http://127.0.0.1:17653/mcp
```
