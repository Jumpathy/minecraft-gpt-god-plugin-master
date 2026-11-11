package net.bigyous.gptgod.client.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** The player's settings, kept in config/gptgod-client.json. Strengths are 0..1. Pure (no Minecraft). */
public final class ModConfig {
    public enum HudMode {
        /** Out of the way: a small chip for a few seconds when something changes; hold the key for the panel. */
        AUTO,
        /** The whole panel, always. */
        FULL,
        /** The small chip, always. */
        COMPACT, HIDDEN;

        public HudMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT;

        public Corner next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public double shake = 1.0;
    public double flash = 1.0;
    public double vignette = 1.0;
    public double sky = 1.0;
    public double particles = 1.0;
    public double bloom = 1.0;
    /** Flashes become slow soft fades, never a bright blink. */
    public boolean noFlashing = false;
    public HudMode hud = HudMode.AUTO;
    public Corner corner = Corner.TOP_LEFT;
    public boolean subtitles = true;
    /** Tell the server we are vanilla: it then sends nothing and the game looks as without the mod. */
    public boolean vanilla = false;

    /** Commands typed in the God Manager screen, oldest first (Up/Down recalls them). */
    public List<String> commandHistory = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** What the hello asks the server for. */
    public List<String> features() {
        List<String> out = new ArrayList<>();
        if (vanilla) return out;
        out.add("fx");
        out.add("hud");
        if (sky > 0) out.add("sky");
        if (subtitles) out.add("subs");
        out.add("gui"); // the God Manager screen (the server opens it only for operators)
        out.add("relics"); // relic art is drawn from the plugin's messages, with no resource pack
        out.add("guide"); // the guide to the gods in its own screen instead of the written book
        return out;
    }

    public ModConfig clamped() {
        shake = clamp(shake);
        flash = clamp(flash);
        vignette = clamp(vignette);
        sky = clamp(sky);
        particles = clamp(particles);
        bloom = clamp(bloom);
        if (hud == null) hud = HudMode.AUTO;
        if (corner == null) corner = Corner.TOP_LEFT;
        if (commandHistory == null) commandHistory = new ArrayList<>();
        while (commandHistory.size() > 50) commandHistory.remove(0);
        return this;
    }

    private static double clamp(double v) {
        return Double.isNaN(v) ? 1 : Math.max(0, Math.min(1, v));
    }

    public static ModConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                ModConfig c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ModConfig.class);
                if (c != null) return c.clamped();
            }
        } catch (IOException | RuntimeException e) {
            // fall through to defaults; a broken file is replaced on the next save
        }
        return new ModConfig();
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // settings are a convenience; the game goes on with them in memory
        }
    }
}
