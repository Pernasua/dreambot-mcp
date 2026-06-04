package com.pernasua.dreambot.mcp;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class JavaRuntimeSupport {
    static final String[] DEFAULT_IMPORTS = {
        "java.util.*",
        "org.dreambot.api.Client",
        "org.dreambot.api.input.Keyboard",
        "org.dreambot.api.input.Mouse",
        "org.dreambot.api.methods.interactive.Players",
        "org.dreambot.api.methods.interactive.NPCs",
        "org.dreambot.api.methods.interactive.GameObjects",
        "org.dreambot.api.methods.container.impl.Inventory",
        "org.dreambot.api.methods.container.impl.bank.Bank",
        "org.dreambot.api.methods.walking.impl.Walking",
        "org.dreambot.api.methods.map.Tile",
        "org.dreambot.api.methods.tabs.Tabs",
        "org.dreambot.api.methods.dialogues.Dialogues",
        "org.dreambot.api.methods.skills.Skills",
        "org.dreambot.api.methods.quest.Quests",
        "org.dreambot.api.methods.input.Camera"
    };

    private JavaRuntimeSupport() {
    }

    static String defaultImportBlock() {
        StringBuilder out = new StringBuilder();
        for (String item : DEFAULT_IMPORTS) {
            out.append("import ").append(item).append(";\n");
        }
        return out.toString();
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) {
                out.append(String.format("%02x", Integer.valueOf(b & 0xff)));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String valueJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) ? String.valueOf(value) : RuntimeJson.quote(String.valueOf(value));
        }
        return RuntimeJson.quote(String.valueOf(value));
    }
}
