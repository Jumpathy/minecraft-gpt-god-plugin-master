package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GodNamesTest {

    @AfterEach
    void forget() {
        Realm.primary().persona = null;
    }

    @Test
    void acceptsOtherworldlyNames() {
        for (String n : List.of("Azaeleus", "Vhal'myr", "Oruun", "Sethael", "Ithuriel", "Xa-Thul", "Ky")) {
            assertNull(GodNames.problem(n, List.of()), n);
        }
    }

    @Test
    void rejectsHumanNamesWordsDigitsAndSpaces() {
        for (String n : List.of("John", "steve", "STEVE", "god", "God", "Lord", "Zeus", "Herobrine", "Az4el",
                "Aza eleus", "", "A", "Vhal''myr", "-Oruun", "Oruun!", "x".repeat(25))) {
            assertNotNull(GodNames.problem(n, List.of(), false), "should reject '" + n + "'");
        }
        assertNotNull(GodNames.problem("Xaneth", List.of("xaneth")), "the name of a mortal online");
    }

    @Test
    void godsOfAncientMythMayBeTakenWhenTheServerAllowsIt() {
        for (String n : List.of("Zeus", "Athena", "Poseidon", "Freyja", "Anubis", "Diana", "Nyx")) {
            assertNull(GodNames.problem(n, List.of(), true), n);
        }
        assertNotNull(GodNames.problem("Zeus", List.of(), false), "off: gods invent their names");
        assertNotNull(GodNames.problem("Zeus", List.of("Zeus"), true), "a mortal online already bears it");
        for (String n : List.of("Jesus", "Allah", "Shiva", "Gandalf", "Mario", "Herobrine", "John", "God")) {
            String why = GodNames.problem(n, List.of(), true);
            assertNotNull(why, n + " is never taken");
        }
        assertTrue(GodNames.problem("Gandalf", List.of(), true).contains("Zeus"), "it says what is allowed");
        assertNull(GodNames.operatorProblem("Zeus"), "a name the operator chooses is always allowed");
    }

    @Test
    void everyFallbackNameIsValidAndTablesAreFull() {
        Set<String> archetypes = new HashSet<>(GodNames.FALLBACK.keySet());
        archetypes.add("someone-elses-archetype");
        for (String a : archetypes) {
            String[][] table = GodNames.fallbacks(a);
            assertTrue(table.length >= 8, a + " has " + table.length);
            for (String[] pick : table) {
                assertNull(GodNames.problem(pick[0], List.of()), a + ": " + pick[0]);
                assertTrue(!pick[1].isBlank());
            }
        }
    }

    @Test
    void anUnfitInventedNameFallsBackButKeepsTheRest() {
        Persona.PersonaSpec bad = Chronicler.parsePersona("{\"name\":\"John\",\"epithet\":\"the Dull\","
                + "\"temperament\":\"John likes order.\"}", List.of());
        assertNull(bad.name());
        assertEquals("{name} likes order.", bad.temperament());
        Persona.PersonaSpec good = Chronicler.parsePersona("Sure! {\"name\":\"Vhalmyr\",\"epithet\":\"the Ash\","
                + "\"temperament\":\"Stern.\"}", List.of());
        assertEquals("Vhalmyr", good.name());
    }

    @Test
    void renameSelfValidatesAndWaitsADay() {
        Persona p = new Persona();
        p.name = "Sethael";
        p.epithet = "the Pale Flame";
        Realm.primary().persona = p;
        long now = System.currentTimeMillis();
        assertNotNull(Persona.rename("Steve", null, List.of(), now));
        assertNotNull(Persona.rename("Ozzy", null, List.of("Ozzy"), now));
        assertNull(Persona.rename("Azaeleus", "the Ever-Watching", List.of(), now));
        assertEquals("Azaeleus, the Ever-Watching", p.title());
        String again = Persona.rename("Vhalmyr", null, List.of(), now + 3_600_000L);
        assertTrue(again != null && again.contains("23 hour"), again);
        assertNull(Persona.rename("Vhalmyr", null, List.of(), now + 25 * 3_600_000L));
        assertEquals("Vhalmyr, the Ever-Watching", p.title());
    }
}
