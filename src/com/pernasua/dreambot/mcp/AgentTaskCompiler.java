package com.pernasua.dreambot.mcp;

import org.codehaus.janino.SimpleCompiler;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AgentTaskCompiler {
    private static final int MAX_SOURCE_LENGTH = 50000;
    private static final Pattern CLASS_NAME = Pattern.compile("\\bclass\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    CompiledTask compile(DreamBotMcpScript script, String mode, String source, String className) throws Exception {
        String normalized = mode == null ? "loop" : mode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        RuntimeJson.require(source != null && !source.trim().isEmpty(), "source is required");
        RuntimeJson.require(source.length() <= MAX_SOURCE_LENGTH, "source is too long");
        RuntimeJson.require("loop".equals(normalized) || "class".equals(normalized) || "full_class".equals(normalized), "mode must be loop or class");

        String javaSource;
        String fqcn;
        String sourceHash = JavaRuntimeSupport.sha256(normalized + "\n" + source);
        if ("loop".equals(normalized)) {
            fqcn = "com.pernasua.dreambot.mcp.generated.AgentTask_" + sourceHash.substring(0, 16);
            int dot = fqcn.lastIndexOf('.');
            String simpleName = fqcn.substring(dot + 1);
            javaSource = "package " + fqcn.substring(0, dot) + ";\n"
                + JavaRuntimeSupport.defaultImportBlock()
                + "import com.pernasua.dreambot.mcp.AgentTask;\n"
                + "public final class " + simpleName + " extends AgentTask {\n"
                + "  public int onLoop(AgentTask.Context ctx) throws Exception {\n"
                + source + "\n"
                + "  }\n"
                + "}\n";
        } else {
            String simpleName = className == null || className.trim().isEmpty() ? detectClassName(source) : className.trim();
            RuntimeJson.require(!simpleName.isEmpty(), "class_name is required when the source does not contain a class declaration");
            fqcn = source.contains("package ") ? declaredPackage(source) + "." + simpleName : "com.pernasua.dreambot.mcp.generated." + simpleName;
            javaSource = source.contains("package ")
                ? source
                : "package com.pernasua.dreambot.mcp.generated;\n"
                    + JavaRuntimeSupport.defaultImportBlock()
                    + "import com.pernasua.dreambot.mcp.AgentTask;\n"
                    + source;
        }

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(script.getClass().getClassLoader());
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.setParentClassLoader(script.getClass().getClassLoader());
            compiler.cook(javaSource);
            Class<?> type = compiler.getClassLoader().loadClass(fqcn);
            RuntimeJson.require(AgentTask.class.isAssignableFrom(type), "compiled class must extend AgentTask");
            AgentTask task = (AgentTask) type.getDeclaredConstructor().newInstance();
            return new CompiledTask(task, fqcn, sourceHash);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static String detectClassName(String source) {
        Matcher matcher = CLASS_NAME.matcher(source);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String declaredPackage(String source) {
        Matcher matcher = Pattern.compile("\\bpackage\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;").matcher(source);
        return matcher.find() ? matcher.group(1) : "";
    }

    static final class CompiledTask {
        final AgentTask task;
        final String className;
        final String sourceHash;

        CompiledTask(AgentTask task, String className, String sourceHash) {
            this.task = task;
            this.className = className;
            this.sourceHash = sourceHash;
        }
    }
}
