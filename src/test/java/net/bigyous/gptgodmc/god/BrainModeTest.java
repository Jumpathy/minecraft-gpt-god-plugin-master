package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** brain.mode: live | external | off, where YAML reads a bare off as the boolean false. */
class BrainModeTest {

    @Test
    void yamlBooleansMeanOffAndLive() {
        assertEquals("off", GodSettings.brainMode("false"));
        assertEquals("off", GodSettings.brainMode("no"));
        assertEquals("off", GodSettings.brainMode(" OFF "));
        assertEquals("live", GodSettings.brainMode("true"));
        assertEquals("live", GodSettings.brainMode("yes"));
        assertEquals("live", GodSettings.brainMode("on"));
        assertEquals("live", GodSettings.brainMode("Live"));
        assertEquals("external", GodSettings.brainMode("EXTERNAL"));
        assertEquals("live", GodSettings.brainMode(null), "unset: the default");
        assertEquals("live", GodSettings.brainMode("  "));
        assertEquals("sideways", GodSettings.brainMode("sideways"), "an unknown word is shown as written (and does not think)");
    }

    @Test
    void aBareOffStaysTheWordThroughASave() throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("brain:\n  mode: off\n");
        assertTrue(c.isBoolean("brain.mode"), "the trap: YAML 1.1 reads off as false");
        assertEquals("off", GodSettings.brainMode(c.getString("brain.mode")), "read as off all the same");

        assertTrue(GodSettings.keepModeWord(c));
        assertEquals("off", c.getString("brain.mode"));
        String saved = c.saveToString();
        assertFalse(saved.contains("false"), saved);

        YamlConfiguration again = new YamlConfiguration();
        again.loadFromString(saved);
        assertTrue(again.isString("brain.mode"), saved);
        assertEquals("off", again.getString("brain.mode"));
        assertFalse(GodSettings.keepModeWord(again), "nothing left to repair");

        // migrateLegacy runs at start, before the plugin saves the file.
        YamlConfiguration start = new YamlConfiguration();
        start.loadFromString("brain:\n  mode: off\n");
        GodSettings.migrateLegacy(start);
        assertEquals("off", start.get("brain.mode"));

        YamlConfiguration on = new YamlConfiguration();
        on.loadFromString("brain:\n  mode: on\n");
        assertTrue(GodSettings.keepModeWord(on));
        assertEquals("live", on.get("brain.mode"));

        YamlConfiguration plain = new YamlConfiguration();
        plain.loadFromString("brain:\n  mode: external\n");
        assertFalse(GodSettings.keepModeWord(plain));
        assertFalse(GodSettings.keepModeWord(new YamlConfiguration()), "unset stays unset");
    }
}
