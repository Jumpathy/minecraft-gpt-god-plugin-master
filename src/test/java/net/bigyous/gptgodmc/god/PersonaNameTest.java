package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A god the operator named calls itself by that name, not the one the helper model invented. */
class PersonaNameTest {
    static final String WRITTEN = "I am Thulmarak, and I weigh every hand. Obey Thulmarak's commandments.";

    @Test
    void theInventedNameInItsSelfDescriptionIsFound() {
        assertEquals("Thulmarak", Persona.strayName(WRITTEN, "diddy"));
        assertNull(Persona.strayName(WRITTEN, "Thulmarak"), "its own name is not stray");
        assertNull(Persona.strayName("It loves naps.", "diddy"));
    }

    @Test
    void theChosenNameReplacesItEverywhere() {
        Persona p = new Persona();
        p.name = "diddy";
        p.temperament = WRITTEN;
        p.inventedName = "Thulmarak";
        assertTrue(Persona.reconcileName(p));
        assertEquals("I am diddy, and I weigh every hand. Obey diddy's commandments.", p.temperament);
        assertNull(p.inventedName);
        assertFalse(Persona.reconcileName(p), "nothing left to change");
        assertEquals("Thulmarakian stays", Persona.swapName("Thulmarakian stays", "Thulmarak", "diddy"),
                "only whole words");
    }
}
