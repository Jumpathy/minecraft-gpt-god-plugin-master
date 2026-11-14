package net.bigyous.gptgodmc.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When /god is taken back, the console says where the other plugin's command went. */
class GodLabelTest {
    @Test
    void theOtherPluginsGodModeIsNamed() {
        String e = GodLabel.note("Essentials");
        assertTrue(e.contains("/essentials:god (or /egod)"), e);
        String w = GodLabel.note("WorldGuard");
        assertTrue(w.contains("/worldguard:god") && !w.contains("egod"), w);
    }
}
