package net.bigyous.gptgodmc.god.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.GodSettings.CustomPower;

class CustomPowerToolsTest {
    private static final CustomPowerTools.Context FULL = new CustomPowerTools.Context("Steve",
            new int[] { 10, 64, -30 }, "minecraft:overworld", "Vael", "Kneel, \"mortal\" {now}\nor [else]");

    @Test
    void placeholdersFillWithOffsets() throws ToolException {
        assertEquals("tp Steve 10 104 -33",
                CustomPowerTools.fill("tp {player} {x} {y+40} {z-3}", FULL));
        assertEquals("say Vael watches minecraft:overworld",
                CustomPowerTools.fill("say {god} watches {world}", FULL));
    }

    @Test
    void nbtBracesAreLeftAlone() throws ToolException {
        String nbt = "summon minecraft:item {x} {y+6} {z} {Item:{id:\"minecraft:gold_nugget\",Count:4b},PickupDelay:20s}";
        assertEquals("summon minecraft:item 10 70 -30 {Item:{id:\"minecraft:gold_nugget\",Count:4b},PickupDelay:20s}",
                CustomPowerTools.fill(nbt, FULL));
    }

    @Test
    void godTextIsSanitized() throws ToolException {
        String filled = CustomPowerTools.fill("title {player} actionbar {\"text\":\"{text}\"}", FULL);
        assertEquals("title Steve actionbar {\"text\":\"Kneel, mortal now or else\"}", filled);
        assertEquals(120, CustomPowerTools.sanitize("x".repeat(500)).length());
        assertEquals("", CustomPowerTools.sanitize(null));
    }

    @Test
    void missingTargetsAreErrorsForTheGod() {
        CustomPowerTools.Context place = new CustomPowerTools.Context(null, new int[] { 1, 2, 3 },
                "minecraft:overworld", "Vael", "");
        CustomPowerTools.Context none = new CustomPowerTools.Context(null, null, null, "Vael", "");
        assertThrows(ToolException.class, () -> CustomPowerTools.fill("effect give {player} speed", place));
        assertThrows(ToolException.class, () -> CustomPowerTools.fill("setblock {x} {y} {z} stone", none));
    }

    @Test
    void bundledExamplePowersParseAndFill() throws ToolException {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertTrue(cfg.getBoolean("powers.custom-powers"));
        List<CustomPower> powers = GodSettings.parseCustomPowers(cfg.getConfigurationSection("custom-powers"));
        List<String> names = new ArrayList<>();
        for (CustomPower p : powers) {
            names.add(p.name());
            assertTrue(p.description().length() > 20, p.name());
            for (String c : p.commands()) {
                String filled = CustomPowerTools.fill(c, FULL);
                assertFalse(filled.matches(".*\\{(player|world|god|text|[xyz]([+-]\\d+)?)\\}.*"), filled);
                assertFalse(filled.startsWith("/"), filled);
            }
        }
        assertEquals(List.of("ascend", "firework_show", "midas_rain", "curse_of_darkness"), names);
    }

    @Test
    void disabledPowersAreSkipped() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("custom-powers.a.description", "first power, enabled by default");
        cfg.set("custom-powers.a.commands", List.of("say hi"));
        cfg.set("custom-powers.b.enabled", false);
        cfg.set("custom-powers.b.commands", List.of("say no"));
        cfg.set("custom-powers.c.description", "no commands, so skipped");
        List<CustomPower> powers = GodSettings.parseCustomPowers(cfg.getConfigurationSection("custom-powers"));
        assertEquals(1, powers.size());
        assertEquals("a", powers.get(0).name());
    }

    @Test
    void descriptionExplainsWhenNothingIsConfigured() {
        assertTrue(CustomPowerTools.description().length() > 20);
    }
}
