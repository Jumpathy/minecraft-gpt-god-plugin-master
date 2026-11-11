package net.bigyous.gptgodmc.client;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Which players run the optional GPTGOD client mod, and what they may be sent. Pure (no Bukkit), so the
 * handshake rules are unit tested. A player is a mod user only after a valid hello with our protocol
 * major version; everyone else (vanilla Java, Bedrock through Geyser) is never sent anything.
 */
public final class ModSessions {
    /** Bumped only for changes old mods cannot ignore; new optional fields do not bump it. */
    public static final int PROTOCOL = 1;

    public static final String FX = "fx";
    public static final String HUD = "hud";
    public static final String SKY = "sky";
    /** The God Manager screen (operators only; see ModGui). */
    public static final String GUI = "gui";
    /** What this plugin can send today, in a stable order. */
    /** The god's spoken lines as subtitles (see ModVoice). */
    public static final String SUBS = "subs";
    /** A line or sigil on one player's HUD (the omen message); others get it in the action bar. */
    public static final String OMEN = "omen";
    /** The art of god-made relics, so the mod draws them itself (see ModRelics). */
    public static final String RELICS = "relics";
    /** The guide to the gods in the mod's own screen instead of the written book (see ModGuide). */
    public static final String GUIDE = "guide";
    public static final List<String> SERVER_FEATURES = List.of(FX, HUD, SKY, GUI, SUBS, OMEN, RELICS, GUIDE);

    public static final int MAX_BYTES = 32 * 1024;
    /** Messages a second per player (a bucket that refills continuously). */
    public static final double RATE = 40;

    public record Session(int protocol, String modVersion, Set<String> features) {
        public boolean has(String feature) {
            return features.contains(feature);
        }
    }

    /** What the plugin answers a hello with, and whether the player now counts as a mod user. */
    public record Reply(JsonObject welcome, Session session) {
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> buckets = new ConcurrentHashMap<>();

    /**
     * Handles a hello. Bad JSON or a different protocol leaves the player a vanilla player (the welcome
     * then lists no features, so the mod can say it is out of date). Never throws.
     */
    public Reply hello(UUID player, byte[] payload, String pluginVersion) {
        int protocol = -1;
        String mod = "?";
        Set<String> wanted = new LinkedHashSet<>();
        try {
            JsonElement root = JsonParser.parseString(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
            if (root.isJsonObject()) {
                JsonObject o = root.getAsJsonObject();
                if (o.has("protocol") && o.get("protocol").isJsonPrimitive()) protocol = o.get("protocol").getAsInt();
                if (o.has("mod") && o.get("mod").isJsonPrimitive()) mod = clip(o.get("mod").getAsString(), 32);
                if (o.has("features") && o.get("features").isJsonArray()) {
                    for (JsonElement f : o.getAsJsonArray("features")) {
                        if (f.isJsonPrimitive() && wanted.size() < 32) wanted.add(clip(f.getAsString(), 32));
                    }
                }
            }
        } catch (RuntimeException e) {
            protocol = -1;
        }
        Set<String> granted = new LinkedHashSet<>();
        if (protocol == PROTOCOL) {
            for (String f : SERVER_FEATURES) {
                if (wanted.contains(f)) granted.add(f);
            }
        }
        JsonObject welcome = new JsonObject();
        welcome.addProperty("t", "welcome");
        welcome.addProperty("protocol", PROTOCOL);
        welcome.addProperty("plugin", pluginVersion);
        JsonArray fs = new JsonArray();
        granted.forEach(fs::add);
        welcome.add("features", fs);
        if (protocol != PROTOCOL || granted.isEmpty()) {
            forget(player);
            return new Reply(welcome, null);
        }
        Session s = new Session(protocol, mod, Collections.unmodifiableSet(granted));
        sessions.put(player, s);
        buckets.put(player, new double[] { RATE, -1 });
        return new Reply(welcome, s);
    }

    public Session get(UUID player) {
        return player == null ? null : sessions.get(player);
    }

    public boolean has(UUID player, String feature) {
        Session s = get(player);
        return s != null && s.has(feature);
    }

    public Set<UUID> players() {
        return Collections.unmodifiableSet(sessions.keySet());
    }

    public void forget(UUID player) {
        sessions.remove(player);
        buckets.remove(player);
    }

    /**
     * Whether a message of this size may go to this player now: only mod users with the feature, under
     * the size cap and the rate limit. Takes one token when it says yes.
     */
    public boolean allow(UUID player, String feature, int bytes, long nowMs) {
        if (bytes > MAX_BYTES || !has(player, feature)) return false;
        double[] b = buckets.get(player);
        if (b == null) return false;
        synchronized (b) {
            if (b[1] >= 0) b[0] = Math.min(RATE, b[0] + (nowMs - b[1]) / 1000.0 * RATE);
            b[1] = nowMs;
            if (b[0] < 1) return false;
            b[0] -= 1;
            return true;
        }
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
