package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The god's character is in its system prompt once, clearly, and fully. */
class PromptsTest {
    static Persona thyvoru() {
        Persona p = new Persona();
        p.name = "Thyvoru";
        p.epithet = "the Easygoing Canopy";
        p.archetype = "chill";
        p.nature = "A laid-back, easygoing god who treats divinity like a long vacation. It mostly lets mortals do "
                + "their thing, vibes with good builds and good banter, and only gets involved when something is "
                + "genuinely cool or genuinely uncool. Not pompous at all.";
        p.temperament = "Thyvoru once slept through an entire war and woke up only for the fireworks. It keeps a "
                + "favourite oak at the edge of the world and drops rain on anyone who is cruel near it.";
        p.speech = "Casual and relaxed, dry humor, modern phrasing, short sentences. Never speaks in thee/thou.";
        p.likes = List.of("using friendly language", "growing plants", "connecting with animals");
        p.dislikes = List.of("materialistic wants", "using hostile language", "using fire and explosions");
        for (String d : Persona.DIALS) p.dials.put(d, 5);
        p.dials.put("wrath", 1);
        p.dials.put("patience", 9);
        p.dials.put("pride", 1);
        p.dials.put("vanity", 1);
        p.dials.put("humor", 8);
        return p;
    }

    @Test
    void theCharacterIsSaidOnceAndAllOfItIsThere() throws Exception {
        Persona p = thyvoru();
        String prompt = Prompts.system(p);
        Files.createDirectories(Path.of("build/test-renders"));
        Files.writeString(Path.of("build/test-renders/system-prompt-sample.txt"), prompt, StandardCharsets.UTF_8);

        assertTrue(prompt.startsWith("You are Thyvoru, the Easygoing Canopy, the god of this Minecraft world."));
        // Rules that came out of a live test: a god that only whispered, spoke in one motif, claimed acts it had
        // not done and took "watching" for a goal.
        for (String rule : new String[] { "Choose who hears you.", "Speak like a living mind, not a motif.",
                "Mortals see only what your powers do.", "never a goal", "locate (the nearest village" }) {
            assertEquals(1, count(prompt, rule), rule);
        }
        assertEquals(1, count(prompt, p.nature), "the nature appears once");
        assertEquals(1, count(prompt, "once slept through an entire war"), "the history appears once");
        assertEquals(1, count(prompt, "growing plants"), "likes appear once");
        assertEquals(1, count(prompt, "using fire and explosions"), "dislikes appear once");
        assertEquals(1, count(prompt, "Casual and relaxed"));
        assertEquals(0, count(prompt, "This is central to who you are"), "no sentence repeated per dial");
        assertTrue(prompt.contains("At your core you are slow to anger, endlessly patient, humble and indifferent "
                + "to worship"), prompt);
        for (String dial : Persona.DIALS) assertTrue(prompt.contains("- " + dial + " "), dial);
        assertTrue(prompt.contains("Stay this god in every reply"));
        assertFalse(prompt.contains("# From the keepers"), "no briefing section when there is no briefing");
        assertFalse(prompt.contains("%s"));
    }

    @Test
    void aGodWithNoExtremesOrInventedHistoryStillReadsWell() {
        Persona p = new Persona();
        p.name = "Ombrel";
        p.nature = "A watchful god.";
        for (String d : Persona.DIALS) p.dials.put(d, 5);
        String c = Prompts.character(p);
        assertFalse(c.contains("At your core"));
        assertFalse(c.contains("history and quirks"));
        assertFalse(c.contains("Mortals are never told"), "nothing to keep secret without likes or dislikes");
        assertTrue(c.contains("Your nature: A watchful god."));
        assertTrue(c.contains("How you speak: in your own way."));
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
    }
}
