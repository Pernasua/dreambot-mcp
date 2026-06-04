package com.pernasua.dreambot.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Schemas {
    private Schemas() {
    }

    static Map<String, Object> object() {
        return object(new LinkedHashMap<String, Object>());
    }

    static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Json.list((Object[]) required));
        schema.put("additionalProperties", Boolean.FALSE);
        return schema;
    }

    static Map<String, Object> prop(String name, Object schema) {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put(name, schema);
        return props;
    }

    static Map<String, Object> string(String description) {
        return string(description, null, null);
    }

    static Map<String, Object> string(String description, String defaultValue) {
        return string(description, null, defaultValue);
    }

    static Map<String, Object> string(String description, List<String> values, String defaultValue) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "string");
        schema.put("description", description);
        if (values != null) {
            schema.put("enum", values);
        }
        if (defaultValue != null) {
            schema.put("default", defaultValue);
        }
        return schema;
    }

    static Map<String, Object> integer(String description, int minimum, int maximum) {
        return integer(description, minimum, maximum, null);
    }

    static Map<String, Object> integer(String description, int minimum, int maximum, Integer defaultValue) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "integer");
        schema.put("description", description);
        schema.put("minimum", Integer.valueOf(minimum));
        schema.put("maximum", Integer.valueOf(maximum));
        if (defaultValue != null) {
            schema.put("default", defaultValue);
        }
        return schema;
    }

    static Map<String, Object> number(String description, double minimum, Double defaultValue) {
        return number(description, minimum, null, defaultValue);
    }

    static Map<String, Object> number(String description, double minimum, Double maximum, Double defaultValue) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "number");
        schema.put("description", description);
        schema.put("minimum", Double.valueOf(minimum));
        if (maximum != null) {
            schema.put("maximum", maximum);
        }
        if (defaultValue != null) {
            schema.put("default", defaultValue);
        }
        return schema;
    }

    static Map<String, Object> bool(String description, boolean defaultValue) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "boolean");
        schema.put("description", description);
        schema.put("default", Boolean.valueOf(defaultValue));
        return schema;
    }

    static Map<String, Object> array(String description, Map<String, Object> itemSchema) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "array");
        schema.put("description", description);
        schema.put("items", itemSchema);
        return schema;
    }

    static Map<String, Object> anyObject(String description) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("description", description);
        schema.put("additionalProperties", Boolean.TRUE);
        return schema;
    }
}
