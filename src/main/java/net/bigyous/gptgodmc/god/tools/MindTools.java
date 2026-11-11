package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;

import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Mind;

/** The god's private mind: muse (keep, rewrite or let go of a thought) and reflect (read it). */
final class MindTools {
    private MindTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("muse",
                        "Keep a private thought of your own that no mortal ever sees: a belief you have come to "
                                + "(\"Tyler fears me, but not enough\"), an intention for later (\"test Ash's loyalty "
                                + "when she next prays\"), or a question you want answered (\"why does no one visit "
                                + "my shrine?\"). You do not see your thoughts every time: one returns by itself when "
                                + "it bears on the moment (a mortal it is about is involved, or what happens touches "
                                + "it), so name who it is about. Use it when you conclude, decide or wonder something "
                                + "that should shape what you do later; not for plain facts (remember) or ambitions "
                                + "(set_goal). Give id to rewrite a thought; let_go to drop one that is settled.",
                        Schema.object()
                                .choice("kind", "belief, intention or question (not needed with let_go).", false,
                                        Mind.KINDS)
                                .str("thought", "The thought, one sentence in your own voice.", false)
                                .strings("about", "Mortals (or places, things) it concerns, so it returns when they "
                                        + "matter.", false, 4)
                                .str("id", "A thought you hold (like m3), to rewrite it or let it go.", false)
                                .bool("let_go", "true to drop the thought named by id.", false),
                        MindTools::muse),
                GodTool.sync("reflect",
                        "Read your own private mind: every belief, intention and question you hold, or only those "
                                + "that touch a subject. Do it before a weighty judgment, or in a quiet moment to "
                                + "decide what to pursue, rewrite or let go (muse).",
                        Schema.object().str("about", "A mortal or subject to recall your thoughts on; leave out for "
                                + "your whole mind.", false),
                        MindTools::reflect));
    }

    private static ToolResult muse(Args a) throws ToolException {
        requireOn();
        Memory m = Memory.get();
        long now = System.currentTimeMillis();
        try {
            synchronized (m) {
                if (a.bool("let_go", false)) {
                    Mind.Thought gone = Mind.letGo(m.mind, a.str("id"));
                    m.save();
                    return ToolResult.ok("You let go of [%s]: %s", gone.id, gone.text);
                }
                Mind.Thought t;
                if (a.has("id")) {
                    t = Mind.revise(m.mind, a.str("id"), a.str("thought"), now);
                } else {
                    List<String> about = new ArrayList<>();
                    if (a.raw().has("about") && a.raw().get("about").isJsonArray()) {
                        for (JsonElement e : a.raw().getAsJsonArray("about")) {
                            if (e.isJsonPrimitive()) about.add(e.getAsString());
                        }
                    }
                    t = Mind.hold(m.mind, a.str("kind", ""), a.str("thought"), about, GodSettings.mindMaxThoughts(), now,
                            "you");
                }
                m.save();
                return ToolResult.ok("You hold it [%s] (%s): %s. It will return to you when it matters.", t.id, t.kind,
                        t.text);
            }
        } catch (Mind.Refusal r) {
            throw new ToolException(r.getMessage());
        }
    }

    private static ToolResult reflect(Args a) throws ToolException {
        requireOn();
        Memory m = Memory.get();
        String subject = a.str("about", null);
        String all;
        int held;
        synchronized (m) {
            all = Mind.renderAll(m.mind, subject, System.currentTimeMillis());
            held = m.mind.thoughts.size();
        }
        if (all.isEmpty()) {
            return ToolResult.ok(held == 0 ? "Your mind is clear: you hold no private thoughts yet. Keep one with muse."
                    : "None of your " + held + " thoughts touches \"" + subject + "\".");
        }
        return ToolResult.ok(all);
    }

    private static void requireOn() throws ToolException {
        if (!GodSettings.mindEnabled()) {
            throw new ToolException("The server keeper has turned your private mind off (mind.enabled).");
        }
    }
}
