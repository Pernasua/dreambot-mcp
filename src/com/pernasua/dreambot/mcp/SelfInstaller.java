package com.pernasua.dreambot.mcp;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

final class SelfInstaller {
    private static final String SCRIPT_JAR_NAME = "DreamBotMCP.jar";

    Map<String, Object> info() {
        Path jar = currentJar();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", Boolean.TRUE);
        result.put("version", BuildInfo.VERSION);
        result.put("jar", jar == null ? "" : jar.toString());
        result.put("script_jar_name", SCRIPT_JAR_NAME);
        result.put("release_jar_name", "dreambot-mcp-" + BuildInfo.VERSION + ".jar");
        if (jar != null && Files.isRegularFile(jar)) {
            result.put("sha256", sha256(jar));
            try {
                result.put("size_bytes", Long.valueOf(Files.size(jar)));
            } catch (IOException e) {
                result.put("size_bytes", Long.valueOf(-1L));
            }
        }
        return result;
    }

    Map<String, Object> install(Path scriptsDir) {
        Path source = currentJar();
        if (source == null || !Files.isRegularFile(source)) {
            throw new ToolExecutionException("cannot locate current executable jar; run this MCP from dreambot-mcp.jar");
        }
        Path target = scriptsDir.resolve(SCRIPT_JAR_NAME);
        try {
            Files.createDirectories(scriptsDir);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ToolExecutionException("could not install DreamBot MCP script jar: " + e.getMessage(), e);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", Boolean.TRUE);
        result.put("source", source.toString());
        result.put("target", target.toString());
        result.put("sha256", sha256(target));
        try {
            result.put("size_bytes", Long.valueOf(Files.size(target)));
        } catch (IOException e) {
            result.put("size_bytes", Long.valueOf(-1L));
        }
        return result;
    }

    private static Path currentJar() {
        CodeSource source = SelfInstaller.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return null;
        }
        try {
            return Paths.get(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder();
            for (byte b : hash) {
                out.append(String.format("%02x", Integer.valueOf(b & 0xff)));
            }
            return out.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            return "";
        }
    }
}
