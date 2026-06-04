package com.pernasua.dreambot.mcp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class AgentTaskRunner {
    private static final int MAX_LOGS = 200;

    private final Deque<LogEntry> logs = new ArrayDeque<LogEntry>();
    private AgentTask task;
    private AgentTask.Context context;
    private String taskId = "";
    private String name = "";
    private String className = "";
    private String sourceHash = "";
    private String status = "idle";
    private String stopReason = "";
    private String lastError = "";
    private long startedAtMs;
    private long stoppedAtMs;
    private long lastTickAtMs;
    private long nextTickNotBeforeMs;
    private int loopCount;
    private volatile boolean stopRequested;

    synchronized String start(DreamBotMcpScript script, String requestedName, AgentTaskCompiler.CompiledTask compiled) throws Exception {
        if (task != null) {
            stopCurrent("replaced");
        }
        this.task = compiled.task;
        this.context = new AgentTask.Context(script, this);
        this.context.reset();
        this.taskId = "task-" + Long.toString(System.currentTimeMillis(), 36);
        this.name = requestedName == null || requestedName.trim().isEmpty() ? "agent-task" : requestedName.trim();
        this.className = compiled.className;
        this.sourceHash = compiled.sourceHash;
        this.status = "starting";
        this.stopReason = "";
        this.lastError = "";
        this.startedAtMs = System.currentTimeMillis();
        this.stoppedAtMs = 0L;
        this.lastTickAtMs = 0L;
        this.nextTickNotBeforeMs = 0L;
        this.loopCount = 0;
        this.stopRequested = false;
        this.logs.clear();
        log("starting " + name + " (" + className + ")");
        task.onStart(context);
        this.status = "running";
        log("started");
        return statusJson();
    }

    synchronized void tick() {
        if (task == null || !"running".equals(status)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextTickNotBeforeMs) {
            return;
        }
        try {
            context.incrementLoopCount();
            loopCount++;
            lastTickAtMs = now;
            int delayMs = task.onLoop(context);
            if (stopRequested) {
                stopCurrent(stopReason.isEmpty() ? "requested" : stopReason);
                return;
            }
            if (delayMs < 0) {
                stopCurrent("negative delay");
                return;
            }
            nextTickNotBeforeMs = System.currentTimeMillis() + Math.max(0, delayMs);
        } catch (Throwable t) {
            lastError = t.toString();
            log("error: " + lastError);
            stopCurrent("error");
        }
    }

    synchronized String stop(String reason) {
        if (task == null) {
            return statusJson();
        }
        stopCurrent(reason == null || reason.trim().isEmpty() ? "requested" : reason.trim());
        return statusJson();
    }

    synchronized String statusJson() {
        return "{\"ok\":true,\"agent_task\":" + statusBodyJson() + "}";
    }

    private String statusBodyJson() {
        long now = System.currentTimeMillis();
        StringBuilder out = new StringBuilder("{");
        RuntimeJson.field(out, "status", status).append(",");
        RuntimeJson.field(out, "task_id", taskId).append(",");
        RuntimeJson.field(out, "name", name).append(",");
        RuntimeJson.field(out, "class_name", className).append(",");
        RuntimeJson.field(out, "source_hash", sourceHash).append(",");
        RuntimeJson.field(out, "loop_count", loopCount).append(",");
        RuntimeJson.field(out, "running", task != null && "running".equals(status)).append(",");
        RuntimeJson.field(out, "stop_requested", stopRequested).append(",");
        RuntimeJson.field(out, "stop_reason", stopReason).append(",");
        RuntimeJson.field(out, "last_error", lastError).append(",");
        RuntimeJson.field(out, "started_at_ms", startedAtMs).append(",");
        RuntimeJson.field(out, "stopped_at_ms", stoppedAtMs).append(",");
        RuntimeJson.field(out, "last_tick_age_ms", lastTickAtMs == 0 ? -1 : now - lastTickAtMs).append(",");
        RuntimeJson.field(out, "next_tick_in_ms", Math.max(0L, nextTickNotBeforeMs - now));
        out.append("}");
        return out.toString();
    }

    synchronized String logsJson(int limit) {
        int max = Math.min(Math.max(limit, 1), MAX_LOGS);
        List<LogEntry> copy = new ArrayList<LogEntry>(logs);
        int start = Math.max(0, copy.size() - max);
        StringBuilder out = new StringBuilder("{\"ok\":true,\"logs\":[");
        for (int i = start; i < copy.size(); i++) {
            if (i > start) {
                out.append(",");
            }
            LogEntry entry = copy.get(i);
            out.append("{");
            RuntimeJson.field(out, "at_ms", entry.atMs).append(",");
            RuntimeJson.field(out, "message", entry.message);
            out.append("}");
        }
        out.append("],\"agent_task\":").append(statusBodyJson()).append("}");
        return out.toString();
    }

    boolean stopRequested() {
        return stopRequested;
    }

    void requestStop(String reason) {
        this.stopRequested = true;
        this.stopReason = reason == null ? "requested" : reason;
    }

    synchronized void log(String message) {
        while (logs.size() >= MAX_LOGS) {
            logs.removeFirst();
        }
        logs.addLast(new LogEntry(System.currentTimeMillis(), message == null ? "" : message));
    }

    private void stopCurrent(String reason) {
        AgentTask active = task;
        if (active == null) {
            return;
        }
        status = "stopping";
        stopReason = reason == null || reason.trim().isEmpty() ? "requested" : reason.trim();
        try {
            active.onStop(context);
        } catch (Throwable t) {
            lastError = t.toString();
            log("stop error: " + lastError);
        }
        task = null;
        context = null;
        status = "stopped";
        stoppedAtMs = System.currentTimeMillis();
        stopRequested = false;
        log("stopped: " + stopReason);
    }

    private static final class LogEntry {
        final long atMs;
        final String message;

        LogEntry(long atMs, String message) {
            this.atMs = atMs;
            this.message = message;
        }
    }
}
