package com.pernasua.dreambot.mcp;

public abstract class AgentTask {
    public void onStart(Context ctx) throws Exception {
    }

    public abstract int onLoop(Context ctx) throws Exception;

    public void onStop(Context ctx) throws Exception {
    }

    public static final class Context {
        private final DreamBotMcpScript script;
        private final AgentTaskRunner runner;
        private int loopCount;
        private long startedAtMs;

        Context(DreamBotMcpScript script, AgentTaskRunner runner) {
            this.script = script;
            this.runner = runner;
            this.startedAtMs = System.currentTimeMillis();
        }

        public DreamBotMcpScript script() {
            return script;
        }

        public int loopCount() {
            return loopCount;
        }

        public long elapsedMs() {
            return System.currentTimeMillis() - startedAtMs;
        }

        public boolean stopRequested() {
            return runner.stopRequested();
        }

        public void log(String message) {
            runner.log(message);
        }

        public void stop(String reason) {
            runner.requestStop(reason);
        }

        void reset() {
            this.loopCount = 0;
            this.startedAtMs = System.currentTimeMillis();
        }

        void incrementLoopCount() {
            loopCount++;
        }
    }
}
