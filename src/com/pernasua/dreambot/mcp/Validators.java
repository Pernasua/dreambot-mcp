package com.pernasua.dreambot.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Validators {
    static final List<String> TABS = list(
        "ACCOUNT_MANAGEMENT", "CLAN", "COMBAT", "EMOTES", "EQUIPMENT", "FRIENDS",
        "INVENTORY", "LOGOUT", "MAGIC", "MUSIC", "OPTIONS", "PRAYER", "QUEST", "SKILLS"
    );

    static final List<String> EQUIPMENT_SLOTS = list(
        "AMULET", "ARROWS", "CAPE", "CHEST", "FEET", "HANDS", "HAT", "LEGS", "RING", "SHIELD", "WEAPON"
    );

    private Validators() {
    }

    static int requireInt(Map<String, Object> args, String name, int minimum, int maximum) {
        Object value = args.get(name);
        if (!(value instanceof Number)) {
            throw new ToolExecutionException("missing integer field: " + name);
        }
        int intValue = ((Number) value).intValue();
        if (intValue < minimum) {
            throw new ToolExecutionException(name + " must be >= " + minimum);
        }
        if (intValue > maximum) {
            throw new ToolExecutionException(name + " must be <= " + maximum);
        }
        return intValue;
    }

    static int optionalInt(Map<String, Object> args, String name, int fallback, int minimum, int maximum) {
        Object value = args.get(name);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number)) {
            throw new ToolExecutionException(name + " must be an integer");
        }
        int intValue = ((Number) value).intValue();
        if (intValue < minimum) {
            throw new ToolExecutionException(name + " must be >= " + minimum);
        }
        if (intValue > maximum) {
            throw new ToolExecutionException(name + " must be <= " + maximum);
        }
        return intValue;
    }

    static String requireString(Map<String, Object> args, String name, int maxLength) {
        String value = optionalString(args, name, "", maxLength, false);
        if (value.isEmpty()) {
            throw new ToolExecutionException("missing string field: " + name);
        }
        return value;
    }

    static String optionalString(Map<String, Object> args, String name, String fallback, int maxLength, boolean allowEmpty) {
        Object value = args.get(name);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String)) {
            throw new ToolExecutionException(name + " must be a string");
        }
        String text = ((String) value).trim();
        if (!allowEmpty && text.isEmpty()) {
            throw new ToolExecutionException(name + " must not be empty");
        }
        if (text.length() > maxLength) {
            throw new ToolExecutionException(name + " is too long");
        }
        return text;
    }

    static boolean optionalBool(Map<String, Object> args, String name, boolean fallback) {
        Object value = args.get(name);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean)) {
            throw new ToolExecutionException(name + " must be a boolean");
        }
        return ((Boolean) value).booleanValue();
    }

    static List<Integer> optionalIntList(Map<String, Object> args, String name, int minimum, int maximum) {
        Object value = args.get(name);
        List<Integer> result = new ArrayList<Integer>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof Iterable)) {
            throw new ToolExecutionException(name + " must be an array");
        }
        for (Object item : (Iterable<?>) value) {
            if (!(item instanceof Number)) {
                throw new ToolExecutionException(name + " must contain integers");
            }
            int intValue = ((Number) item).intValue();
            if (intValue < minimum || intValue > maximum) {
                throw new ToolExecutionException(name + " values must be between " + minimum + " and " + maximum);
            }
            result.add(Integer.valueOf(intValue));
        }
        return result;
    }

    static Map<String, Object> tile(Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("x", Integer.valueOf(requireInt(args, "x", 0, 16383)));
        body.put("y", Integer.valueOf(requireInt(args, "y", 0, 16383)));
        body.put("z", Integer.valueOf(optionalInt(args, "z", 0, 0, 3)));
        return body;
    }

    static Map<String, Object> namedInteract(Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", requireString(args, "name", 128));
        body.put("action", requireString(args, "action", 64));
        return body;
    }

    static Map<String, Object> target(Map<String, Object> args, boolean requireAction) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        String name = optionalString(args, "name", "", 128, true);
        int id = optionalInt(args, "id", -1, -1, 1000000);
        int x = optionalInt(args, "x", -1, -1, 16383);
        int y = optionalInt(args, "y", -1, -1, 16383);
        if (name.isEmpty() && id < 0 && (x < 0 || y < 0)) {
            throw new ToolExecutionException("target action requires name, id, or x/y tile");
        }
        if (!name.isEmpty()) {
            body.put("name", name);
        }
        if (id >= 0) {
            body.put("id", Integer.valueOf(id));
        }
        if (x >= 0 && y >= 0) {
            body.put("x", Integer.valueOf(x));
            body.put("y", Integer.valueOf(y));
            body.put("z", Integer.valueOf(optionalInt(args, "z", 0, 0, 3)));
        }
        String action = optionalString(args, "action", "", 64, !requireAction);
        if (requireAction || !action.isEmpty()) {
            body.put("action", action);
        }
        int radius = optionalInt(args, "radius", 20, 0, 104);
        body.put("radius", Integer.valueOf(radius));
        return body;
    }

    static Map<String, Object> widget(Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        int widget = optionalInt(args, "widget", -1, -1, 100000);
        int child = optionalInt(args, "child", -1, -1, 100000);
        int grandchild = optionalInt(args, "grandchild", -1, -1, 100000);
        String text = optionalString(args, "text", "", 256, true);
        String action = optionalString(args, "action", "", 128, true);
        if (widget < 0 && text.isEmpty() && action.isEmpty()) {
            throw new ToolExecutionException("widget action requires widget id, text, or action");
        }
        if (widget >= 0) {
            body.put("widget", Integer.valueOf(widget));
        }
        if (child >= 0) {
            body.put("child", Integer.valueOf(child));
        }
        if (grandchild >= 0) {
            body.put("grandchild", Integer.valueOf(grandchild));
        }
        if (!text.isEmpty()) {
            body.put("text", text);
        }
        if (!action.isEmpty()) {
            body.put("action", action);
        }
        body.put("contains", Boolean.valueOf(optionalBool(args, "contains", true)));
        body.put("visible_only", Boolean.valueOf(optionalBool(args, "visible_only", true)));
        return body;
    }

    static Map<String, Object> inventoryInteract(Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        int slot = optionalInt(args, "slot", -1, -1, 27);
        String name = optionalString(args, "name", "", 128, true);
        if (slot < 0 && name.isEmpty()) {
            throw new ToolExecutionException("inventory interaction requires slot or name");
        }
        if (slot >= 0) {
            body.put("slot", Integer.valueOf(slot));
        }
        if (!name.isEmpty()) {
            body.put("name", name);
        }
        body.put("action", optionalString(args, "action", "", 64, true));
        return body;
    }

    static Map<String, Object> itemOnItem(Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        copyItemSelector(args, body, "", false);
        copyItemSelector(args, body, "target_", true);
        return body;
    }

    static Map<String, Object> spell(Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("spell", requireString(args, "spell", 128));
        return body;
    }

    static Map<String, Object> spellTarget(Map<String, Object> args) {
        Map<String, Object> body = target(args, false);
        body.put("spell", requireString(args, "spell", 128));
        return body;
    }

    static Map<String, Object> queryParams(Map<String, Object> args, String... names) {
        Map<String, Object> query = new LinkedHashMap<String, Object>();
        for (String name : names) {
            Object value = args.get(name);
            if (value == null) {
                continue;
            }
            if (value instanceof Iterable) {
                List<String> values = new ArrayList<String>();
                for (Object item : (Iterable<?>) value) {
                    values.add(String.valueOf(item));
                }
                query.put(name, join(values, ","));
            } else {
                query.put(name, String.valueOf(value));
            }
        }
        return query;
    }

    static String enumValue(Map<String, Object> args, String name, List<String> values) {
        String value = requireString(args, name, 128).toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!values.contains(value)) {
            throw new ToolExecutionException(name + " must be one of " + values);
        }
        return value;
    }

    private static void copyItemSelector(Map<String, Object> args, Map<String, Object> body, String prefix, boolean target) {
        int slot = optionalInt(args, prefix + "slot", -1, -1, 27);
        int id = optionalInt(args, prefix + "id", -1, -1, 1000000);
        String name = optionalString(args, prefix + "name", "", 128, true);
        if (slot < 0 && id < 0 && name.isEmpty()) {
            throw new ToolExecutionException((target ? "target " : "") + "item selector requires slot, id, or name");
        }
        if (slot >= 0) {
            body.put(prefix + "slot", Integer.valueOf(slot));
        }
        if (id >= 0) {
            body.put(prefix + "id", Integer.valueOf(id));
        }
        if (!name.isEmpty()) {
            body.put(prefix + "name", name);
        }
    }

    private static List<String> list(String... values) {
        List<String> list = new ArrayList<String>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }

    private static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(separator);
            }
            out.append(values.get(i));
        }
        return out.toString();
    }
}
