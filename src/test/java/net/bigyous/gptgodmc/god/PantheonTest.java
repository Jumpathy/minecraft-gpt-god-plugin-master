package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

/** Several gods in one world: names, who is addressed, who answers, what the others see. */
class PantheonTest {

    @Test
    void namesMatchWholeWordsOnly() {
        assertTrue(Pantheon.names("O Vorthak, hear me", "Vorthak"));
        assertTrue(Pantheon.names("vorthak's wrath", "Vorthak"));
        assertFalse(Pantheon.names("Vorthak hears", "Vor"), "a name inside another god's name does not count");
        assertFalse(Pantheon.names("anything", ""));
        assertTrue(Pantheon.names("praise Ysmere the Bright", "Ysmere the Bright"));
    }

    @Test
    void aPrayerMayNameItsGod() {
        List<String> gods = List.of("Vorthak", "Ysmere");
        assertEquals(new Pantheon.Address(1, "please heal me"), Pantheon.addressed(gods, "Ysmere, please heal me"));
        assertEquals(new Pantheon.Address(0, "smite them"), Pantheon.addressed(gods, "@vorthak smite them"));
        assertEquals(new Pantheon.Address(1, "bless me"), Pantheon.addressed(gods, "ysmere: bless me"));
        assertEquals(-1, Pantheon.addressed(gods, "please help me").index(), "no god named: an open prayer");
        assertEquals(-1, Pantheon.addressed(gods, "Vorthaklike words").index(), "the name must end at a boundary");
        // Just the name: still that god, with the words left as they were.
        assertEquals(0, Pantheon.addressed(gods, "@Vorthak").index());
    }

    @Test
    void theLongestMatchingNameWins() {
        List<String> gods = List.of("Ash", "Ash the Pale");
        assertEquals(1, Pantheon.addressed(gods, "Ash the Pale, hear me").index());
        assertEquals(0, Pantheon.addressed(gods, "Ash, hear me").index());
    }

    @Test
    void openWordsGoToTheGodWhoFavorsTheMortalMost() {
        assertEquals(1, Pantheon.responder(new int[] { 2, 7, -3 }));
        assertEquals(0, Pantheon.responder(new int[] { 4, 4 }), "a tie goes to the world's first god");
        assertEquals(-1, Pantheon.responder(new int[0]));
    }

    @Test
    void otherGodsSeePublicActsHearWordsAndMissPrivateOnes() {
        JsonObject say = new JsonObject();
        say.addProperty("message", "Kneel before me");
        assertEquals("Vorthak proclaimed to every mortal: \"Kneel before me\"",
                Pantheon.witnessed("Vorthak", "announce", say, "ok"));
        JsonObject whisper = new JsonObject();
        whisper.addProperty("player", "Steve");
        whisper.addProperty("message", "secret");
        String seen = Pantheon.witnessed("Vorthak", "whisper", whisper, "ok");
        assertTrue(seen.contains("Steve") && !seen.contains("secret"), seen);
        assertNull(Pantheon.witnessed("Vorthak", "recall", new JsonObject(), "memories"));
        assertNull(Pantheon.witnessed("Vorthak", "commune", new JsonObject(), "sent"));
        assertTrue(Pantheon.witnessed("Vorthak", "smite", new JsonObject(), "Lightning struck Alex.")
                .contains("Lightning struck Alex"));
    }

    @Test
    void slugsAreFolderSafe() {
        assertEquals("vorthak_the_red", Pantheon.slug("Vorthak the Red"));
        assertEquals("ysmre", Pantheon.slug("Ysmëre"));
        assertEquals("", Pantheon.slug("!!!"));
    }

    @Test
    void createdGodsSurviveARestart(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("pantheon.json");
        Pantheon.load(file);
        Pantheon.Entry e = new Pantheon.Entry();
        e.id = "vorthak";
        e.domain = "main";
        e.archetype = "wrathful";
        e.name = "Vorthak";
        Pantheon.add(e);
        assertTrue(Files.exists(file));
        List<Pantheon.Entry> read = Pantheon.load(file);
        assertEquals(1, read.size());
        assertEquals("wrathful", read.get(0).archetype);
        assertTrue(Pantheon.created("VORTHAK"));
        Pantheon.setArchetype("vorthak", "chill");
        assertEquals("chill", Pantheon.load(file).get(0).archetype);
        Pantheon.remove("vorthak");
        assertFalse(Pantheon.created("vorthak"));
        assertTrue(Pantheon.load(file).isEmpty());
        Pantheon.load(null);
    }

    @Test
    void godsShareTheirFirstGodsWorlds() {
        Realm main = Realm.primary();
        Realm second = Realm.detachedPeer(main, "ysmere");
        try {
            assertTrue(main.isHost());
            assertFalse(second.isHost());
            assertEquals(main, second.host());
            assertEquals(List.of(main, second), main.domainGods());
            assertEquals(List.of(second), main.peers());
            assertTrue(main.shared() && second.shared());
            assertEquals(second, Realm.byName("@ysm"));
            // The first god is supreme unless told otherwise; a new one takes pantheon.default-power.
            assertEquals(Power.SUPREME, main.power());
            assertEquals(Power.GREATER, second.power());
            second.profile().power = "lesser";
            assertEquals(Power.LESSER, second.power());
            assertNotNull(Prompts.pantheon(second));
            assertTrue(Prompts.pantheon(second).contains("lesser god"));
            assertTrue(Prompts.pantheon(main).contains("not the only god"));
            // Its own model and voice, while it is the god at work.
            second.profile().model = "claude-haiku-5-5";
            second.profile().voice = "bm_george";
            assertEquals("claude-haiku-5-5", second.get(GodSettings::model));
            assertEquals("bm_george", second.get(GodSettings::ttsVoice));
            assertEquals(GodSettings.configuredModel(), main.get(GodSettings::model));
        } finally {
            Realm.dropDetached(second);
        }
        assertFalse(main.shared());
        assertEquals("", Prompts.pantheon(main), "a lone supreme god's prompt is unchanged");
    }

    @Test
    void aSlainGodIsDeadToItsPeers() {
        Realm main = Realm.primary();
        Realm second = Realm.detachedPeer(main, "vorrath");
        try {
            second.memory().mortality.wound(1000, "Steve", 100, System.currentTimeMillis());
            assertTrue(Godslayer.isSlain(second));
            String seen = Perception.describePeers(main);
            assertTrue(seen.contains("DEAD: slain by Steve"), seen);
            assertFalse(seen.contains("watches unseen"), seen);
            // Unnamed prayers and offerings go to the god still alive.
            second.memory().adjustFavor("Alex", 9, "loved");
            assertEquals(main, Addressing.responder(List.of(main, second), "Alex"));
            second.memory().mortality.revive(System.currentTimeMillis());
            assertEquals(second, Addressing.responder(List.of(main, second), "Alex"), "alive again, it answers");
        } finally {
            Realm.dropDetached(second);
        }
    }
}
