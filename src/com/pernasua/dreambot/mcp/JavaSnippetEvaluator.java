package com.pernasua.dreambot.mcp;

import org.codehaus.janino.ScriptEvaluator;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

final class JavaSnippetEvaluator {
    private static final int MAX_CODE_LENGTH = 20000;
    private long evalCount;

    String eval(DreamBotMcpScript script, String mode, String code) throws Exception {
        String normalized = mode == null ? "expression" : mode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        RuntimeJson.require(code != null && !code.trim().isEmpty(), "code is required");
        RuntimeJson.require(code.length() <= MAX_CODE_LENGTH, "code is too long");
        RuntimeJson.require("expression".equals(normalized) || "block".equals(normalized), "mode must be expression or block");

        String source = "expression".equals(normalized) ? "return " + code + ";" : code;
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(script.getClass().getClassLoader());
        Object result;
        try {
            ScriptEvaluator evaluator = new ScriptEvaluator();
            evaluator.setParentClassLoader(script.getClass().getClassLoader());
            evaluator.setDefaultImports(JavaRuntimeSupport.DEFAULT_IMPORTS);
            evaluator.setReturnType(Object.class);
            evaluator.setParameters(new String[] {"script"}, new Class<?>[] {DreamBotMcpScript.class});
            evaluator.cook(source);
            result = evaluator.evaluate(new Object[] {script});
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof Exception) {
                throw (Exception) target;
            }
            throw e;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        evalCount++;

        StringBuilder out = new StringBuilder("{\"ok\":true,\"action\":\"java.eval\",");
        RuntimeJson.field(out, "language", "java").append(",");
        RuntimeJson.field(out, "mode", normalized).append(",");
        RuntimeJson.field(out, "eval_count", evalCount).append(",");
        RuntimeJson.field(out, "result_class", result == null ? "null" : result.getClass().getName()).append(",");
        out.append("\"result\":").append(JavaRuntimeSupport.valueJson(result));
        out.append("}");
        return out.toString();
    }
}
