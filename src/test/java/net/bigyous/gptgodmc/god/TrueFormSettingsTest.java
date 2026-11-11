package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TrueFormSettingsTest {

    @Test
    void legacyLethalBecomesTheGaze() {
        YamlConfiguration off = new YamlConfiguration();
        off.set("vessel.true-form.lethal", false);
        assertTrue(GodSettings.migrateTrueFormGaze(off));
        assertEquals("blind", off.getString("vessel.true-form.gaze"));
        assertNull(off.get("vessel.true-form.lethal"));

        YamlConfiguration on = new YamlConfiguration();
        on.set("vessel.true-form.lethal", true);
        GodSettings.migrateTrueFormGaze(on);
        assertEquals("kill", on.getString("vessel.true-form.gaze"));

        // An explicit gaze wins over the old flag.
        YamlConfiguration both = new YamlConfiguration();
        both.set("vessel.true-form.lethal", true);
        both.set("vessel.true-form.gaze", "smite");
        GodSettings.migrateTrueFormGaze(both);
        assertEquals("smite", both.getString("vessel.true-form.gaze"));

        // Defaults alone (the jar's config) never count as an old setting.
        YamlConfiguration fresh = new YamlConfiguration();
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("vessel.true-form.gaze", "kill");
        fresh.setDefaults(defaults);
        fresh.options().copyDefaults(true);
        assertFalse(GodSettings.migrateTrueFormGaze(fresh));
    }
}
