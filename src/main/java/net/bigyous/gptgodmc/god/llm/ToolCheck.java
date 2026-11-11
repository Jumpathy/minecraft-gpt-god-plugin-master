package net.bigyous.gptgodmc.god.llm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Strict handling of tool calls from small models: the arguments must be one JSON object that fits the
 * tool's schema. Only harmless slips are mended here (a code fence around the JSON, arguments sent as
 * a JSON string, a trailing comma, "5" for 5); anything else is reported so the model can be asked once
 * to send the call again. A call that was cut off is never completed by guessing.
 */
public final class ToolCheck {
    private static final Pattern FENCE = Pattern.compile("(?s)^```[a-zA-Z]*\\s*(.*?)\\s*```$");
    private static final Pattern TRAILING_COMMA = Pattern.compile(",\\s*([}\\]])");
    private static final Pattern TAGGED = Pattern.compile("(?s)<tool_call>\\s*(.*?)\\s*</tool_call>");
    private static final Pattern FENCED = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?\\})\\s*```");

    private ToolCheck() {
    }

    /** The arguments as an object, or null when they are not one. No arguments at all is an empty object. */
    public static JsonObject parse(String raw) {
        if (raw == null || raw.isBlank()) return new JsonObject();
        String s = raw.trim();
        Matcher fence = FENCE.matcher(s);
        if (fence.matches()) s = fence.group(1).trim();
        JsonObject o = object(s);
        if (o != null) return o;
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        return object(TRAILING_COMMA.matcher(s.substring(a, b + 1)).replaceAll("$1"));
    }

    private static JsonObject object(String s) {
        try {
            JsonElement e = JsonParser.parseString(s);
            // Some servers hand the arguments over as a JSON string holding the object.
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                e = JsonParser.parseString(e.getAsString());
            }
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Checks the arguments against the tool's schema, mending type slips in place. Returns what is wrong
     * in words the model can act on, or null when the call is fit to run.
     */
    public static String problem(LlmTool tool, JsonObject input) {
        for (String key : new ArrayList<>(input.keySet())) {
            if (input.get(key).isJsonNull()) input.remove(key);
        }
        for (String need : tool.required()) {
            if (!input.has(need)) return "missing required argument \"" + need + "\"";
        }
        for (String key : new ArrayList<>(input.keySet())) {
            Map<String, Object> prop = tool.properties().get(key);
            if (prop == null) {
                return "unknown argument \"" + key + "\" (allowed: " + String.join(", ", tool.properties().keySet())
                        + ")";
            }
            String wrong = fit(input, key, prop);
            if (wrong != null) return "argument \"" + key + "\" " + wrong;
        }
        return null;
    }

    private static String fit(JsonObject input, String key, Map<String, Object> prop) {
        JsonElement v = input.get(key);
        String type = String.valueOf(prop.get("type"));
        switch (type) {
            case "string" -> {
                if (!v.isJsonPrimitive()) return "must be a string";
                String s = v.getAsString();
                if (prop.get("enum") instanceof Collection<?> allowed) {
                    for (Object a : allowed) {
                        if (String.valueOf(a).equalsIgnoreCase(s.trim())) {
                            input.addProperty(key, String.valueOf(a));
                            return null;
                        }
                    }
                    return "must be one of " + allowed;
                }
                if (!v.getAsJsonPrimitive().isString()) input.addProperty(key, s);
            }
            case "integer", "number" -> {
                if (!v.isJsonPrimitive() || v.getAsJsonPrimitive().isBoolean()) return "must be a number";
                double d;
                try {
                    d = Double.parseDouble(v.getAsString().trim());
                } catch (NumberFormatException e) {
                    return "must be a number";
                }
                if (Double.isNaN(d) || Double.isInfinite(d)) return "must be a number";
                if (type.equals("integer")) {
                    if (d != Math.rint(d)) return "must be a whole number";
                    input.addProperty(key, (long) d);
                } else if (!v.getAsJsonPrimitive().isNumber()) {
                    input.addProperty(key, d);
                }
            }
            case "boolean" -> {
                if (!v.isJsonPrimitive()) return "must be true or false";
                JsonPrimitive p = v.getAsJsonPrimitive();
                if (p.isBoolean()) return null;
                String s = p.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
                if (s.equals("true") || s.equals("false")) input.addProperty(key, s.equals("true"));
                else return "must be true or false";
            }
            case "array" -> {
                if (v.isJsonPrimitive()) {
                    JsonArray one = new JsonArray();
                    one.add(v);
                    input.add(key, one);
                } else if (!v.isJsonArray()) {
                    return "must be a list";
                }
            }
            default -> {
                // a type this check does not know: leave it to the tool
            }
        }
        return null;
    }

    /** The arguments a tool takes, in one line, to remind a model that got them wrong. */
    public static String hint(LlmTool tool) {
        if (tool.properties().isEmpty()) return "{} (no arguments)";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Map<String, Object>> e : tool.properties().entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append('"').append(e.getKey()).append("\": ");
            Object options = e.getValue().get("enum");
            sb.append(options != null ? "one of " + options : String.valueOf(e.getValue().get("type")));
            if (tool.required().contains(e.getKey())) sb.append(" (required)");
        }
        return sb.append('}').toString();
    }

    /** A tool call a model wrote into its prose instead of calling the tool properly. */
    public record TextCall(String name, String json) {
    }

    /**
     * Tool calls written out as text: {@code <tool_call>{"name": ..., "arguments": {...}}</tool_call>},
     * a fenced JSON block, or a reply that is nothing but such an object (or a list of them). Only calls
     * to a known tool count.
     */
    public static List<TextCall> fromText(String text, Collection<String> names) {
        List<TextCall> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        Matcher tagged = TAGGED.matcher(text);
        while (tagged.find()) add(out, tagged.group(1), names);
        if (!out.isEmpty()) return out;
        Matcher fenced = FENCED.matcher(text);
        while (fenced.find()) add(out, fenced.group(1), names);
        if (!out.isEmpty()) return out;
        String t = text.trim();
        if (t.startsWith("{") || t.startsWith("[")) add(out, t, names);
        return out;
    }

    private static void add(List<TextCall> out, String json, Collection<String> names) {
        JsonElement e;
        try {
            e = JsonParser.parseString(json);
        } catch (RuntimeException ex) {
            return;
        }
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonObject()) add(out, x.getAsJsonObject(), names);
            }
        } else if (e.isJsonObject()) {
            add(out, e.getAsJsonObject(), names);
        }
    }

    private static void add(List<TextCall> out, JsonObject o, Collection<String> names) {
        if (o.has("function") && o.get("function").isJsonObject()) o = o.getAsJsonObject("function");
        JsonElement name = o.has("name") ? o.get("name") : o.get("tool");
        if (name == null || !name.isJsonPrimitive() || !names.contains(name.getAsString())) return;
        JsonElement args = o.has("arguments") ? o.get("arguments")
                : o.has("parameters") ? o.get("parameters") : o.get("args");
        String json = args == null || args.isJsonNull() ? "{}"
                : args.isJsonPrimitive() ? args.getAsString() : args.toString();
        out.add(new TextCall(name.getAsString(), json));
    }
}
