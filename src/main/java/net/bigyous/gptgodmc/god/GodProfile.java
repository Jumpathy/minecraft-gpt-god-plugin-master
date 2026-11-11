package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * One god's own settings, kept in profile.json in its folder: its power, and overrides for the model,
 * effort, voice, voice effect and vessel look. A null field means "use config.yml". Set from the God
 * Manager and /god; read through {@link GodSettings} while the god is bound.
 */
public final class GodProfile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** lesser | greater | supreme; null = the default for this god (supreme for the first god of a world). */
    public String power;
    public String model;
    public String effort;
    public String voice;
    public String effect;
    public String look;
    /** Turned off from the God Manager: no thoughts, no body, until turned on again. */
    public Boolean dormant;

    private transient Path file;

    /** Reads the profile in this file, or a blank one (in memory only when file is null). */
    static GodProfile open(Path file) {
        GodProfile p = null;
        if (file != null && Files.exists(file)) {
            try {
                p = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), GodProfile.class);
            } catch (IOException | RuntimeException e) {
                GPTGOD.LOGGER.warn("Could not read " + file + ", using the server-wide settings", e);
            }
        }
        if (p == null) p = new GodProfile();
        p.file = file;
        return p;
    }

    public synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save " + file, e);
        }
    }

    /** No longer saved anywhere (the god was removed). */
    void detach() {
        file = null;
    }

    /** The value, or null when blank or "default" (for callers outside this package). */
    public static String blankOrNull(String s) {
        return blankToNull(s);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() || s.equalsIgnoreCase("default") ? null : s.trim();
    }
}
