package net.bigyous.gptgod.client.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Decodes the GPTGOD plugin's messages (JSON on gptgod:main) into records. Pure. Never throws: bad or
 * unknown messages become {@link Unknown}; every number is clamped and every string clipped, so a broken
 * or hostile server cannot crash the client or draw absurd things.
 */
public final class Protocol {
    public static final int VERSION = 1;
    public static final int MAX_BYTES = 64 * 1024;

    private Protocol() {
    }

    public sealed interface Message permits Welcome, Ping, God, Standing, Quest, Screen, Play, Stop, Tone, Say, Voice, Unknown {
    }

    public record Welcome(int protocol, String plugin, List<String> features) implements Message {
        public boolean accepted() {
            return protocol == VERSION && !features.isEmpty();
        }
    }

    public record Ping(int protocol) implements Message {
    }

    /** The five feelings of the god's heart, 0..100 each. */
    public record Heart(float wrath, float joy, float sorrow, float dread, float glory) {
        public static final Heart NONE = new Heart(0, 0, 0, 0, 0);

        public float[] values() {
            return new float[] { wrath, joy, sorrow, dread, glory };
        }
    }

    /** pos is null when the god has no body in your world; health/max are NaN unless it is wounded. */
    public record God(String realm, String name, String epithet, String archetype, int color, String mood,
            Heart heart, String state, boolean thinking, float health, float max, double[] pos, boolean here)
            implements Message {
        public boolean wounded() {
            return !Float.isNaN(health) && !Float.isNaN(max) && max > 0;
        }
    }

    public record Standing(String realm, String tier, String title, String titleColor, String mark) implements Message {
    }

    /** title null = no mission. */
    public record Quest(String realm, String god, String title, int progress) implements Message {
    }

    /** from null = right on you (no falloff). */
    public record Screen(String kind, float intensity, int ticks, int color, double[] from, float radius)
            implements Message {
    }

    /** Play a scene (the effect language; see fx/Scene). */
    public record Play(net.bigyous.gptgod.client.fx.Scene scene) implements Message {
    }

    /** Stop the running scene with this id ("" = all). */
    public record Stop(String id) implements Message {
    }

    /**
     * A line the god speaks, shown as a subtitle. kind: whisper, announce or vessel. from: where the speaker
     * stands, or null for a voice in the mind. ms: how long saying it should take. env: loudness 0..1, ten
     * values a second, of the audio starting now, or null when there is none to follow.
     */
    public record Say(String realm, String name, int color, String text, String tone, String kind, double[] from,
            int ms, float[] env) implements Message {
    }

    /** A further part of the line being spoken starts playing now. env may be null. */
    public record Voice(String realm, int ms, float[] env) implements Message {
    }

    /** Play one of the mod's own synthesized sounds (see fx/SoundBank) at a place. */
    public record Tone(String id, double[] at, float volume, float pitch) implements Message {
    }

    public record Unknown(String type) implements Message {
    }

    public static Message decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) return new Unknown("");
        try {
            JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return new Unknown("");
            return decode(root.getAsJsonObject());
        } catch (RuntimeException e) {
            return new Unknown("");
        }
    }

    static Message decode(JsonObject o) {
        String t = str(o, "t", "", 24);
        return switch (t) {
            case "welcome" -> new Welcome(integer(o, "protocol", -1, -1, 1000), str(o, "plugin", "?", 32),
                    strings(o, "features", 32));
            case "ping" -> new Ping(integer(o, "protocol", -1, -1, 1000));
            case "god" -> god(o);
            case "standing" -> new Standing(str(o, "realm", "main", 64), str(o, "tier", "unproven", 32),
                    str(o, "title", null, 40), str(o, "title_color", null, 24), str(o, "mark", null, 24));
            case "quest" -> new Quest(str(o, "realm", "main", 64), str(o, "god", null, 48),
                    bool(o, "none") ? null : str(o, "title", null, 120), integer(o, "progress", 0, 0, 100));
            case "screen" -> new Screen(str(o, "kind", "", 24).toLowerCase(Locale.ROOT),
                    (float) num(o, "intensity", 0, 0, 1), integer(o, "ticks", 20, 1, 400), color(o, "color", 0xFFFFFF),
                    vec(o, "from"), (float) num(o, "radius", 64, 1, 256));
            case "scene" -> new Play(net.bigyous.gptgod.client.fx.Scene.decode(o));
            case "scene_stop" -> new Stop(str(o, "id", "", 48));
            case "sound" -> new Tone(str(o, "id", "", 80), vec(o, "at"), (float) num(o, "volume", 1, 0, 4),
                    (float) num(o, "pitch", 1, 0.5, 2));
            case "say" -> say(o);
            case "voice" -> new Voice(str(o, "realm", "main", 64), integer(o, "ms", 1000, 100, 60_000), levels(o, "env"));
            default -> new Unknown(t);
        };
    }

    private static Message say(JsonObject o) {
        String text = str(o, "text", "", 300).trim();
        if (text.isEmpty()) return new Unknown("say");
        String kind = str(o, "kind", "announce", 16).toLowerCase(Locale.ROOT);
        if (!kind.equals("whisper") && !kind.equals("vessel")) kind = "announce";
        String tone = str(o, "tone", null, 24);
        return new Say(str(o, "realm", "main", 64), str(o, "name", null, 48), color(o, "color", 0xC77DFF), text,
                tone == null ? null : tone.toLowerCase(Locale.ROOT), kind, vec(o, "from"),
                integer(o, "ms", 2000, 300, 60_000), levels(o, "env"));
    }

    /** A loudness envelope: at most 600 values, each 0..1 (anything else counts as silence), or null. */
    private static float[] levels(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().isEmpty()) return null;
        JsonArray a = e.getAsJsonArray();
        float[] out = new float[Math.min(600, a.size())];
        for (int i = 0; i < out.length; i++) {
            JsonElement v = a.get(i);
            if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) continue;
            double d = v.getAsDouble();
            out[i] = Double.isNaN(d) ? 0 : (float) Math.max(0, Math.min(1, d));
        }
        return out;
    }

    private static God god(JsonObject o) {
        Heart heart = Heart.NONE;
        if (o.has("heart") && o.get("heart").isJsonObject()) {
            JsonObject h = o.getAsJsonObject("heart");
            heart = new Heart((float) num(h, "wrath", 0, 0, 100), (float) num(h, "joy", 0, 0, 100),
                    (float) num(h, "sorrow", 0, 0, 100), (float) num(h, "dread", 0, 0, 100),
                    (float) num(h, "glory", 0, 0, 100));
        }
        float health = Float.NaN;
        float max = Float.NaN;
        if (o.has("health") && o.has("max")) {
            max = (float) num(o, "max", 100, 1, 1_000_000);
            health = (float) num(o, "health", 0, 0, max);
        }
        return new God(str(o, "realm", "main", 64), str(o, "name", null, 48), str(o, "epithet", null, 80),
                str(o, "archetype", null, 32), color(o, "color", 0xB24BF3), str(o, "mood", "calm", 16).toLowerCase(Locale.ROOT),
                heart, str(o, "state", "watches", 16).toLowerCase(Locale.ROOT), bool(o, "thinking"), health, max,
                vec(o, "pos"), bool(o, "here"));
    }

    // ---- Reading fields defensively ------------------------------------------------------------

    private static String str(JsonObject o, String key, String fallback, int max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        String s = e.getAsString();
        StringBuilder b = new StringBuilder(Math.min(s.length(), max));
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7F && c != '§') b.append(c); // no control chars or legacy colour codes
        }
        return b.toString();
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private static double num(JsonObject o, String key, double fallback, double lo, double hi) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        double v = e.getAsDouble();
        if (Double.isNaN(v)) return fallback;
        return Math.max(lo, Math.min(hi, v));
    }

    private static int integer(JsonObject o, String key, int fallback, int lo, int hi) {
        return (int) Math.round(num(o, key, fallback, lo, hi));
    }

    private static int color(JsonObject o, String key, int fallback) {
        String s = str(o, key, null, 8);
        if (s == null || !s.matches("#[0-9a-fA-F]{6}")) return fallback;
        return Integer.parseInt(s.substring(1), 16);
    }

    /** A world position, or null. Coordinates beyond the world border are refused. */
    private static double[] vec(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) return null;
        JsonArray a = e.getAsJsonArray();
        if (a.size() != 3) return null;
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            JsonElement c = a.get(i);
            if (!c.isJsonPrimitive() || !c.getAsJsonPrimitive().isNumber()) return null;
            v[i] = c.getAsDouble();
            if (Double.isNaN(v[i]) || Math.abs(v[i]) > 30_000_000) return null;
        }
        return v;
    }

    private static List<String> strings(JsonObject o, String key, int max) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) return out;
        for (JsonElement x : e.getAsJsonArray()) {
            if (x.isJsonPrimitive() && out.size() < max) out.add(x.getAsString());
        }
        return List.copyOf(out);
    }

    /** The hello this mod sends. features: what the player has turned on. */
    public static byte[] hello(String modVersion, List<String> features) {
        JsonObject o = new JsonObject();
        o.addProperty("protocol", VERSION);
        o.addProperty("mod", modVersion);
        JsonArray fs = new JsonArray();
        features.forEach(fs::add);
        o.add("features", fs);
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }
}
