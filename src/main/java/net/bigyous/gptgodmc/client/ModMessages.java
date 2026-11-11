package net.bigyous.gptgodmc.client;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * The JSON messages sent to the GPTGOD client mod (channel gptgod:main). Pure: built from plain values
 * so the exact output is unit tested against the golden files in client-mod/src/test/resources/protocol.
 * Every message is an object with "t" (its type). Absent optional fields are left out.
 */
public final class ModMessages {
    private ModMessages() {
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** A god as a mod player sees it. heart values are 0..100; health/max only while wounded or slain. */
    public record GodView(String realm, String name, String epithet, String archetype, int color, String mood,
            double wrath, double joy, double sorrow, double dread, double glory, String state, boolean thinking,
            Double health, Double maxHealth, Integer vessel, double[] pos, boolean here) {
    }

    public static JsonObject god(GodView g) {
        JsonObject o = type("god");
        o.addProperty("realm", g.realm());
        if (g.name() != null) o.addProperty("name", g.name());
        if (g.epithet() != null) o.addProperty("epithet", g.epithet());
        if (g.archetype() != null) o.addProperty("archetype", g.archetype());
        o.addProperty("color", hex(g.color()));
        o.addProperty("mood", g.mood());
        JsonObject heart = new JsonObject();
        heart.addProperty("wrath", round(g.wrath()));
        heart.addProperty("joy", round(g.joy()));
        heart.addProperty("sorrow", round(g.sorrow()));
        heart.addProperty("dread", round(g.dread()));
        heart.addProperty("glory", round(g.glory()));
        o.add("heart", heart);
        o.addProperty("state", g.state());
        if (g.thinking()) o.addProperty("thinking", true);
        if (g.health() != null && g.maxHealth() != null) {
            o.addProperty("health", round(g.health()));
            o.addProperty("max", round(g.maxHealth()));
        }
        if (g.vessel() != null) o.addProperty("vessel", g.vessel());
        if (g.pos() != null) o.add("pos", vec(g.pos()));
        o.addProperty("here", g.here());
        return o;
    }

    /** The player's standing with a god: a vague favor tier, never the number. */
    public static JsonObject standing(String realm, String tier, String title, String titleColor, String mark) {
        JsonObject o = type("standing");
        o.addProperty("realm", realm);
        o.addProperty("tier", tier);
        if (title != null) o.addProperty("title", title);
        if (title != null && titleColor != null) o.addProperty("title_color", titleColor);
        if (mark != null) o.addProperty("mark", mark);
        return o;
    }

    /** The god's announced mission (the boss bar), or none when title is null. */
    public static JsonObject quest(String realm, String god, String title, int progress) {
        JsonObject o = type("quest");
        o.addProperty("realm", realm);
        if (title == null) {
            o.addProperty("none", true);
            return o;
        }
        if (god != null) o.addProperty("god", god);
        o.addProperty("title", title);
        o.addProperty("progress", Math.max(0, Math.min(100, progress)));
        return o;
    }

    /** Screen effect kinds the mod knows. Unknown kinds are ignored by the mod. */
    public enum Screen {
        SHAKE, FLASH, VIGNETTE, PULSE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A screen effect. intensity 0..1, ticks it lasts, colour (for flash/vignette/pulse), and the world
     * position it comes from (the mod fades it with distance), or null for "right on you".
     */
    public static JsonObject screen(Screen kind, double intensity, int ticks, int color, double[] from, double radius) {
        JsonObject o = type("screen");
        o.addProperty("kind", kind.id());
        o.addProperty("intensity", round2(Math.max(0, Math.min(1, intensity))));
        o.addProperty("ticks", Math.max(1, Math.min(400, ticks)));
        o.addProperty("color", hex(color));
        if (from != null) {
            o.add("from", vec(from));
            o.addProperty("radius", round(Math.max(1, Math.min(256, radius))));
        }
        return o;
    }

    public static final int SAY_MAX_ENV = 600;

    /**
     * A line the god speaks, sent the moment its text is shown. kind: whisper, announce or vessel. tone:
     * the voice effect (ethereal, demonic...), or null. from: where the speaker stands, or null for a
     * voice in the mind. ms: how long saying it is expected to take. env: loudness 0..1, 10 values a
     * second, of the audio that starts now (it may cover only the line's first part), or null.
     */
    public static JsonObject say(String realm, String name, int color, String text, String tone, String kind,
            double[] from, int ms, float[] env) {
        JsonObject o = type("say");
        o.addProperty("realm", realm);
        if (name != null) o.addProperty("name", name);
        o.addProperty("color", hex(color));
        o.addProperty("text", text);
        if (tone != null) o.addProperty("tone", tone);
        o.addProperty("kind", kind);
        if (from != null) o.add("from", vec(from));
        o.addProperty("ms", Math.max(300, Math.min(60_000, ms)));
        if (env != null && env.length > 0) o.add("env", env(env));
        return o;
    }

    /** A further part of the line being spoken starts playing now: its length and loudness. */
    public static JsonObject voice(String realm, int ms, float[] env) {
        JsonObject o = type("voice");
        o.addProperty("realm", realm);
        o.addProperty("ms", Math.max(100, Math.min(60_000, ms)));
        if (env != null && env.length > 0) o.add("env", env(env));
        return o;
    }

    private static com.google.gson.JsonArray env(float[] env) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (int i = 0; i < env.length && i < SAY_MAX_ENV; i++) {
            float v = Float.isNaN(env[i]) ? 0 : env[i];
            a.add(round2(Math.max(0, Math.min(1, v))));
        }
        return a;
    }

    /**
     * The god message with the god's own visual style (FxStyle.wire: primary, secondary, accent, shadow, hud,
     * sigil, motion, sprites, shapes, sky {mood: colour}) under "style". Mods that do not know it ignore it.
     */
    public static JsonObject god(GodView g, JsonObject style) {
        JsonObject o = god(g);
        if (style != null && style.size() > 0) o.add("style", style);
        return o;
    }

    /**
     * A sign for one player's HUD panel: a short line, the god's sigil, or both, shown for ticks in colour.
     * Sent only to mod players who asked for the "omen" feature; everyone else gets an action-bar line.
     */
    public static JsonObject omen(String realm, String god, String text, String sigil, int color, int ticks) {
        JsonObject o = type("omen");
        o.addProperty("realm", realm);
        if (god != null) o.addProperty("god", god);
        if (text != null && !text.isBlank()) o.addProperty("text", text);
        if (sigil != null && !sigil.isBlank()) o.addProperty("sigil", sigil);
        o.addProperty("color", hex(color));
        o.addProperty("ticks", Math.max(20, Math.min(400, ticks)));
        return o;
    }

    /** One of the mod's own synthesized sounds (ids in the mod's SoundBank), played at a place. */
    public static JsonObject sound(String id, double[] at, double volume, double pitch) {
        JsonObject o = type("sound");
        o.addProperty("id", id);
        o.add("at", vec(at));
        o.addProperty("volume", round2(Math.max(0, Math.min(4, volume))));
        o.addProperty("pitch", round2(Math.max(0.5, Math.min(2, pitch))));
        return o;
    }

    public static byte[] bytes(JsonObject o) {
        return GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
    }

    public static String json(JsonObject o) {
        return GSON.toJson(o);
    }

    private static JsonObject type(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        return o;
    }

    private static com.google.gson.JsonArray vec(double[] p) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (int i = 0; i < 3; i++) a.add(round2(p[i]));
        return a;
    }

    static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Whole numbers as ints (so 30.0 is written 30), else one decimal. */
    private static Number round(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.rint(r) ? (Number) (long) r : (Number) r;
    }

    private static Number round2(double v) {
        double r = Math.round(v * 100) / 100.0;
        return r == Math.rint(r) ? (Number) (long) r : (Number) r;
    }
}
