package com.pernasua.dreambot.mcp;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RuntimeJson {
    private RuntimeJson() {
    }

    static StringBuilder field(StringBuilder sb, String name, String value) {
        return sb.append(quote(name)).append(":").append(quote(value));
    }

    static StringBuilder field(StringBuilder sb, String name, int value) {
        return sb.append(quote(name)).append(":").append(value);
    }

    static StringBuilder field(StringBuilder sb, String name, long value) {
        return sb.append(quote(name)).append(":").append(value);
    }

    static StringBuilder field(StringBuilder sb, String name, double value) {
        return sb.append(quote(name)).append(":").append(Double.isFinite(value) ? String.valueOf(value) : "null");
    }

    static StringBuilder field(StringBuilder sb, String name, boolean value) {
        return sb.append(quote(name)).append(":").append(value);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static int intField(String body, String name, int defaultValue) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(-?\\d+)").matcher(body);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : defaultValue;
    }

    static boolean boolField(String body, String name, boolean defaultValue) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE).matcher(body);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : defaultValue;
    }

    static String stringField(String body, String name, String defaultValue) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(body);
        return matcher.find() ? unescape(matcher.group(1)) : defaultValue;
    }

    static double doubleField(String body, String name, double defaultValue) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(body);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : defaultValue;
    }

    static int queryInt(Map<String, String> query, String name, int defaultValue) {
        String value = queryString(query, name, "");
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    static boolean queryBool(Map<String, String> query, String name, boolean defaultValue) {
        String value = queryString(query, name, "");
        return value.isEmpty() ? defaultValue : Boolean.parseBoolean(value);
    }

    static String queryString(Map<String, String> query, String name, String defaultValue) {
        if (query == null) {
            return defaultValue;
        }
        String value = query.get(name);
        return value == null ? defaultValue : value;
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String unescape(String value) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(c); break;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
