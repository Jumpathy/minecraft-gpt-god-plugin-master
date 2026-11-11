package net.bigyous.gptgodmc.god.tools;

import java.util.List;

import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Purpose;

/** The god's own purpose: ambitions and missions it pursues across many thoughts. */
final class PurposeTools {
    private PurposeTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("set_goal",
                        "Take up a purpose of your own: a long ambition you pursue across many thoughts (a temple "
                                + "raised in your name, the dragon slain, a rival humbled, the land made beautiful, "
                                + "the faithful multiplied). mission=true announces it to mortals as a mission "
                                + "shown on a bar, with an optional promised reward; then speak it to them. Break it "
                                + "into quests (set_quest) as steps.",
                        Schema.object()
                                .str("title", "The goal in a few words, e.g. 'Raise my temple on the hill'.", true)
                                .str("details", "What exactly would satisfy you, and why you want it.", false)
                                .bool("mission", "true = a mission for mortals, shown to them; false = a private "
                                        + "ambition. Default true.", false)
                                .str("reward", "What you promise mortals when it is achieved (missions).", false),
                        a -> {
                            requireOn();
                            Purpose.Goal g;
                            try {
                                g = Purpose.set(Memory.get(), a.str("title"), a.str("details", null),
                                        a.str("reward", null), a.bool("mission", true), GodSettings.purposeMaxGoals(),
                                        System.currentTimeMillis());
                            } catch (Purpose.Refusal e) {
                                throw new ToolException(e.getMessage());
                            }
                            Memory.get().save();
                            return ToolResult.ok("Your purpose [%s] \"%s\" is set%s. %s", g.id, g.title,
                                    g.mission ? " as a mission mortals can see" : " (private)",
                                    g.mission ? "Tell the mortals what you want of them." : "");
                        }),
                GodTool.sync("update_goal",
                        "Judge how far one of your goals has come (0-100) after looking at the world, with a short "
                                + "note. Do it whenever you see progress or neglect.",
                        Schema.object()
                                .str("goal", "Goal id (like g2) or its title.", true)
                                .integer("progress", "How complete it is now, 0-100.", true, 0, 100)
                                .str("note", "What you saw: who helped, what is missing.", false),
                        a -> {
                            requireOn();
                            Purpose.Goal g;
                            try {
                                g = Purpose.update(Memory.get(), a.str("goal"), a.integer("progress", 0, 0, 100),
                                        a.str("note", null), System.currentTimeMillis());
                            } catch (Purpose.Refusal e) {
                                throw new ToolException(e.getMessage());
                            }
                            Memory.get().save();
                            return ToolResult.ok("[%s] \"%s\" is now %d%% complete.", g.id, g.title, g.progress);
                        }),
                GodTool.sync("close_goal",
                        "Close one of your goals: achieved (celebrate it, keep any promise, then choose a new "
                                + "purpose) or abandoned (say why).",
                        Schema.object()
                                .str("goal", "Goal id (like g2) or its title.", true)
                                .choice("outcome", "achieved or abandoned.", true, List.of("achieved", "abandoned"))
                                .str("note", "What came of it.", false),
                        a -> {
                            requireOn();
                            boolean won = a.str("outcome").equalsIgnoreCase("achieved");
                            Purpose.Goal g;
                            try {
                                g = Purpose.close(Memory.get(), a.str("goal"), won, a.str("note", null),
                                        System.currentTimeMillis());
                            } catch (Purpose.Refusal e) {
                                throw new ToolException(e.getMessage());
                            }
                            Memory.get().save();
                            return ToolResult.ok("[%s] \"%s\" is %s.%s", g.id, g.title, g.status,
                                    won && g.reward != null ? " You promised: " + g.reward + ". Keep your word." : "");
                        }));
    }

    private static void requireOn() throws ToolException {
        if (!GodSettings.purposeEnabled()) {
            throw new ToolException("The server keeper has turned goals off (purpose.enabled).");
        }
    }
}
