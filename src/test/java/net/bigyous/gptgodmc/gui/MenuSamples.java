package net.bigyous.gptgodmc.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.bigyous.gptgodmc.gui.MenuModel.Archetype;
import net.bigyous.gptgodmc.gui.MenuModel.Entry;
import net.bigyous.gptgodmc.gui.MenuModel.Mortal;
import net.bigyous.gptgodmc.gui.MenuModel.Pending;
import net.bigyous.gptgodmc.gui.MenuModel.State;

/** A believable god for menu tests and previews. */
public final class MenuSamples {
    private MenuSamples() {
    }

    public static State sample() {
        Map<String, Boolean> settings = new LinkedHashMap<>();
        settings.put(MenuLayout.gazeKey("smite"), true);
        settings.put("vessel.manifest-on-wake", false);
        return new State("Vaelor", "the Ember Judge", "wrathful", true, false, true, false, VesselMood.WRATHFUL,
                List.of(new Archetype("benevolent", "A warm guardian deity who delights in mortals."),
                        new Archetype("chill", "A laid-back, easygoing god."),
                        new Archetype("trickster", "A mischievous chaos god who loves pranks."),
                        new Archetype("evil", "A malevolent dark god who sees mortals as playthings."),
                        new Archetype("wrathful", "A stern, jealous old god of law and judgment."),
                        new Archetype("cosmic", "An ancient, alien intelligence from beyond the stars."),
                        new Archetype("gardener", "A god of growing things (written by the owner).")),
                List.of(new Pending(3, "Reveal your true form to all players", false, "Seen in 1 of 3 thoughts"),
                        new Pending(4, "For the next 10 minutes you are WRATHFUL: your patience is spent.", true,
                                "9 min left")),
                settings, null, null).withPlayers(mortals(), mortals().get(1), 0)
                .withSoul("radiant", List.of("strong wrath (64)", "faint sorrow (12)"))
                .withVoice(new MenuModel.VoiceNow("kokoro", "bm_george", "ethereal"))
                .withDials(List.of(new MenuModel.Dial("generosity", 3, 4), new MenuModel.Dial("wrath", 10, 9),
                        new MenuModel.Dial("chattiness", 4, 4), new MenuModel.Dial("humor", 0, 1),
                        new MenuModel.Dial("meddling", 6, 6), new MenuModel.Dial("patience", 2, 2),
                        new MenuModel.Dial("mercy", 2, 2), new MenuModel.Dial("pride", 9, 9),
                        new MenuModel.Dial("curiosity", 5, 3), new MenuModel.Dial("vanity", 8, 8)))
                .withGods(gods());
    }

    /** Three gods sharing the world: the sample god (being edited), a created one and an unborn one. */
    public static MenuModel.Gods gods() {
        return new MenuModel.Gods(List.of(
                new MenuModel.Deity("main", "Vaelor", "the Ember Judge", "wrathful", "supreme", "awake", true, false),
                new MenuModel.Deity("ysmere", "Ysmere", "Keeper of Green Hollows", "benevolent", "greater", "awake",
                        false, true),
                new MenuModel.Deity("god3", null, null, "trickster", "lesser", "asleep", false, true)),
                "supreme", "claude-opus-5-5", null, "claude-sonnet-5-5", "auto", false)
                .withPeers(List.of(new MenuModel.Peer("ysmere", "Ysmere", "greater", -42, "rival", "none",
                        "She blessed Korvath, who burned your chapel."),
                        new MenuModel.Peer("god3", "An unborn god (@god3)", "lesser", 0, "neutral", "none", null)));
    }

    /** A few remembered mortals: one chosen, one despised (the one opened), one it barely knows. */
    public static List<Mortal> mortals() {
        long t = 1_700_000_000_000L;
        Mortal ash = new Mortal("Ashwyn", 8, true, "the Faithful",
                "Built the first chapel and has never missed a prayer.",
                List.of(new Entry(t + 3, "Promised her a crown of gold if the tower is finished.", "2h")),
                List.of(new Entry(t + 2, "made an offering of 12 gold ingots", "3h"),
                        new Entry(t + 1, "prayed: \"Guide my hand, great one\"", "1d")),
                5, null);
        Mortal korv = new Mortal("Korvath", -7, true, null, "Burned the chapel and mocks every prayer.",
                List.of(new Entry(t + 6, "-3 favor: burned your chapel", "40m"),
                        new Entry(t + 5, "He swore he would tear down your statue.", "2h")),
                List.of(new Entry(t + 9, "killed Ashwyn (Ashwyn was slain by Korvath)", "35m"),
                        new Entry(t + 8, "struck your vessel", "50m"),
                        new Entry(t + 7, "first entered your world", "2d")),
                0, null);
        Mortal pip = new Mortal("Pip", 0, false, null, null, List.of(), List.of(), 0, "3d");
        return List.of(ash, korv, pip);
    }
}
