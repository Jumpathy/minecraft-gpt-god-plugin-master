package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Typed access to a tool call's JSON input, with errors phrased for the god. */
public final class Args {
    private final JsonObject o;

    public Args(JsonObject o) {
        this.o = o == null ? new JsonObject() : o;
    }

    public JsonObject raw() {
        return o;
    }

    private JsonElement get(String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e;
    }

    public boolean has(String key) {
        JsonElement e = get(key);
        return e != null && !(e.isJsonPrimitive() && e.getAsString().isBlank());
    }

    public String str(String key) throws ToolException {
        JsonElement e = get(key);
        if (e == null || !e.isJsonPrimitive() || e.getAsString().isBlank()) {
            throw new ToolException("Missing required argument '" + key + "'.");
        }
        return e.getAsString().trim();
    }

    public String str(String key, String fallback) {
        JsonElement e = get(key);
        if (e == null || !e.isJsonPrimitive() || e.getAsString().isBlank()) {
            return fallback;
        }
        return e.getAsString().trim();
    }

    public int integer(String key, int fallback, int min, int max) throws ToolException {
        JsonElement e = get(key);
        if (e == null) {
            return Math.max(min, Math.min(max, fallback));
        }
        try {
            return Math.max(min, Math.min(max, (int) Math.round(e.getAsDouble())));
        } catch (RuntimeException ex) {
            throw new ToolException("Argument '" + key + "' must be a number.");
        }
    }

    public boolean bool(String key, boolean fallback) {
        JsonElement e = get(key);
        if (e == null) {
            return fallback;
        }
        try {
            return e.getAsBoolean();
        } catch (RuntimeException ex) {
            return "true".equalsIgnoreCase(e.getAsString());
        }
    }

    public List<String> strings(String key) {
        JsonElement e = get(key);
        List<String> out = new ArrayList<>();
        if (e == null) {
            return out;
        }
        if (e.isJsonArray()) {
            JsonArray arr = e.getAsJsonArray();
            for (JsonElement item : arr) {
                if (item != null && item.isJsonPrimitive() && !item.getAsString().isBlank()) {
                    out.add(item.getAsString().trim());
                }
            }
        } else if (e.isJsonPrimitive()) {
            for (String part : e.getAsString().split(",")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }
}
