package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** The god sees how much it has already given each mortal, so it can say "wait". */
class GiftsTest {
    static final long T = 1_700_000_000_000L;

    @Test
    void goodThingsCountAsGiftsAndHarmDoesNot() {
        assertTrue(Memory.isGift("give_item", Map.of("item", "cod")));
        assertTrue(Memory.isGift("spawn_entity", Map.of("entity", "ocelot", "near", "Tono")));
        assertTrue(Memory.isGift("spawn_entity", Map.of("entity", "minecraft:cat", "near", "Tono")));
        assertFalse(Memory.isGift("spawn_entity", Map.of("entity", "zombie", "near", "Tono")));
        assertFalse(Memory.isGift("spawn_entity", Map.of("entity", "wolf", "near", "Tono", "hunt", "Tono")));
        assertTrue(Memory.isGift("apply_effect", Map.of("effect", "regeneration")));
        assertFalse(Memory.isGift("apply_effect", Map.of("effect", "poison")));
        assertFalse(Memory.isGift("smite", Map.of()));
    }

    @Test
    void theDossierShowsRecentGifts() {
        Memory m = new Memory();
        m.addGift("TonoCatMeow", T);                 // the ocelot
        m.addGift("TonoCatMeow", T + 20_000);        // same thought: not counted again
        m.addGift("TonoCatMeow", T + 55_000 + 60_000); // the cat and cod, a minute later
        Memory.Subject s = m.subjectIfAny("TonoCatMeow");
        assertEquals(2, s.gifts.size());
        assertEquals("2 in the last hour, latest 1m ago", Memory.giftsLine(s, T + 175_000));
        assertEquals("2 today, latest 2h ago", Memory.giftsLine(s, T + 115_000 + 7_200_000));
        String dossier = m.renderDossier("TonoCatMeow", true, true, T + 175_000);
        assertTrue(dossier.contains("your gifts to them: 2 in the last hour"), dossier);
        assertNull(Memory.giftsLine(m.subject("Nobody"), T));
    }

    @Test
    void oldGiftsAgeOut() {
        Memory m = new Memory();
        m.addGift("Pip", T);
        m.addGift("Pip", T + 8 * 86_400_000L);
        assertEquals(1, m.subjectIfAny("Pip").gifts.size());
    }
}
