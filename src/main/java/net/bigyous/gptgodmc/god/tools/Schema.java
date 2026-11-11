package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny builder for a tool's JSON input schema. */
public final class Schema {
    private final Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();

    public static Schema object() {
        return new Schema();
    }

    private Schema put(String name, Map<String, Object> prop, boolean isRequired) {
        properties.put(name, prop);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    public Schema str(String name, String description, boolean isRequired) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "string");
        p.put("description", description);
        return put(name, p, isRequired);
    }

    public Schema choice(String name, String description, boolean isRequired, List<String> values) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "string");
        p.put("enum", values);
        p.put("description", description);
        return put(name, p, isRequired);
    }

    public Schema integer(String name, String description, boolean isRequired, int min, int max) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "integer");
        p.put("minimum", min);
        p.put("maximum", max);
        p.put("description", description);
        return put(name, p, isRequired);
    }

    public Schema bool(String name, String description, boolean isRequired) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "boolean");
        p.put("description", description);
        return put(name, p, isRequired);
    }

    public Schema strings(String name, String description, boolean isRequired, int maxItems) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "array");
        p.put("items", Map.of("type", "string"));
        p.put("maxItems", maxItems);
        p.put("description", description);
        return put(name, p, isRequired);
    }

    public Map<String, Map<String, Object>> properties() {
        return properties;
    }

    public List<String> required() {
        return required;
    }

    /** Full JSON-schema object, used by the debug bridge's /tools listing. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", properties);
        m.put("required", required);
        return m;
    }
}
