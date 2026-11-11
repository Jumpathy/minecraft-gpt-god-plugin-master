package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class TabListTest {
    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void headerShowsNameEpithetAndState() {
        TabList.Text t = TabList.compose("Vaelor", "the Ember Judge", "wrathful", VesselMood.CALM, TabList.State.WALKS);
        assertEquals("✦ Vaelor ✦\nthe Ember Judge\nwalks among you", plain(t.header()));
        assertEquals("/pray to be heard", plain(t.footer()));

        TabList.Text hidden = TabList.compose("Vaelor", "", "wrathful", null, TabList.State.WATCHES);
        assertEquals("✦ Vaelor ✦\nwatches unseen", plain(hidden.header()));
        assertEquals("✦ The God ✦\nhas not yet awoken",
                plain(TabList.compose(null, null, null, null, TabList.State.UNBORN).header()));
        assertTrue(plain(TabList.compose("V", null, "evil", null, TabList.State.SLEEPS).header()).endsWith("sleeps"));
    }

    @Test
    void moodShowsInTheLineAndTheColour() {
        TabList.Text angry = TabList.compose("Vaelor", null, "benevolent", VesselMood.WRATHFUL, TabList.State.WALKS);
        assertTrue(plain(angry.header()).endsWith("walks among you · wrathful"), plain(angry.header()));
        assertEquals(TextColor.color(VesselMood.WRATHFUL.outline.asRGB()),
                TabList.nameColor("benevolent", VesselMood.WRATHFUL));
        assertEquals(TabList.archetypeColor("benevolent"), TabList.nameColor("benevolent", VesselMood.CALM));
        assertNotEquals(TabList.archetypeColor("evil"), TabList.archetypeColor("chill"));
        assertEquals(Speech.VIOLET, TabList.archetypeColor("my_custom_god"));
        // A sleeping god shows no mood.
        assertEquals("✦ V ✦\nsleeps",
                plain(TabList.compose("V", null, "evil", VesselMood.OMINOUS, TabList.State.SLEEPS).header()));
    }

    @Test
    void sameTextComparesEqualSoTheTimerSendsNothing() {
        TabList.Text a = TabList.compose("Vaelor", "x", "evil", VesselMood.OMINOUS, TabList.State.WATCHES);
        TabList.Text b = TabList.compose("Vaelor", "x", "evil", VesselMood.OMINOUS, TabList.State.WATCHES);
        assertEquals(a.key(), b.key());
        assertNotEquals(a.key(), TabList.compose("Vaelor", "x", "evil", null, TabList.State.WATCHES).key());
    }
}
