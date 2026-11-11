package net.bigyous.gptgodmc.god;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.god.tools.ToolException;
import net.bigyous.gptgodmc.viewer.MindTrace;

/**
 * Reads each god's live state for the mind viewer: who it is, whether it is thinking or why it is
 * not, what it remembers and what it is about to see. Read-only; never changes a god.
 */
public final class MindProbe {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeSpecialFloatingPointValues()
            .create();

    private MindProbe() {
    }

    /** Every god with its identity and state. Server thread (through Sync). */
    public static JsonObject gods() throws ToolException {
        return Sync.call(() -> {
            JsonArray arr = new JsonArray();
            long now = System.currentTimeMillis();
            for (Realm r : Realm.all()) {
                arr.add(r.get(() -> god(r, now)));
            }
            JsonObject o = new JsonObject();
            o.add("gods", arr);
            o.addProperty("rev", MindTrace.revision());
            o.addProperty("now", now);
            o.addProperty("mode", GodSettings.brainMode());
            o.addProperty("brainEnabled", GodSettings.brainEnabled());
            o.addProperty("keyConfigured", GodClient.hasCredentials());
            return o;
        }, 5000);
    }

    private static JsonObject god(Realm r, long now) {
        JsonObject o = new JsonObject();
        o.addProperty("id", r.id);
        o.addProperty("name", r.displayName());
        Persona p = r.persona();
        if (p != null) {
            o.addProperty("fullName", p.name);
            o.addProperty("epithet", p.epithet);
            o.addProperty("archetype", p.archetype);
            o.addProperty("temperament", p.temperament);
        } else if (r.archetype != null) {
            o.addProperty("archetype", r.archetype);
        }
        o.addProperty("host", r.isHost());
        o.addProperty("domain", r.domain);
        o.addProperty("model", GodSettings.model());
        o.addProperty("effort", GodSettings.effort());
        o.addProperty("power", r.power().id);
        o.addProperty("awake", r.awake);
        o.addProperty("paused", r.paused);
        boolean slain = Godslayer.isSlain(r);
        o.addProperty("slain", slain);
        GodBrain b = r.brainIfStarted();
        boolean thinking = b != null && b.isThinking();
        boolean backoff = b != null && b.isBackingOff();
        o.addProperty("thinking", thinking);
        o.addProperty("backingOff", backoff);
        boolean weary = Spending.weary(r.id, GodSettings.pantheonBudgetPerGod(), now);
        o.addProperty("weary", weary);
        if (b != null) {
            o.addProperty("lastError", b.lastError());
            if (b.lastThoughtEnded() > 0) o.addProperty("lastThoughtEnded", b.lastThoughtEnded());
        }
        String state = slain ? "slain" : !r.awake ? "asleep" : r.paused ? "paused" : thinking ? "thinking"
                : backoff ? "backing off" : weary ? "weary" : "awake";
        o.addProperty("state", state);
        o.addProperty("thoughts", r.thoughts.get());
        o.addProperty("pendingEvents", r.events().pendingCount());
        o.addProperty("eventsWaiting", r.events().hasPending());
        if (r.attentionAt != 0) {
            JsonObject a = new JsonObject();
            a.addProperty("at", r.attentionAt);
            a.addProperty("reason", r.attentionReason);
            a.addProperty("urgent", r.attentionUrgent);
            o.add("attention", a);
        }
        o.addProperty("divineStreak", r.divineStreak);
        o.addProperty("players", r.players().size());
        try {
            o.addProperty("vessel", r.vessel().location() != null ? "manifest" : "formless");
        } catch (RuntimeException e) {
            o.addProperty("vessel", "formless");
        }
        o.addProperty("openCommands", Edicts.book().pending().size());
        MindTrace.GodTrace trace = MindTrace.godIfAny(r.id);
        o.addProperty("traced", trace == null ? 0 : trace.thoughts().size());
        return o;
    }

    /** Everything the god remembers, as stored, plus its commands and heart. */
    public static JsonObject memory(Realm r) throws ToolException {
        return Sync.call(() -> r.get(() -> {
            long now = System.currentTimeMillis();
            Memory m = r.memory();
            JsonObject o = new JsonObject();
            synchronized (m) {
                o.add("memory", GSON.toJsonTree(m));
            }
            Heart h = m.heart;
            o.add("heartSummary", GSON.toJsonTree(h.summary(now)));
            o.addProperty("heartText", h.describe(now));
            // The sky over its world and the memory that last came back to it (what moved it is in the heart).
            String sky = GodSettings.awarenessSky() ? Sky.now(r.world()) : null;
            if (sky != null) o.addProperty("sky", sky);
            String[] surfaced = Reminiscence.last(r.id);
            if (surfaced != null) {
                o.addProperty("surfaced", surfaced[0]);
                o.addProperty("surfacedAt", Long.parseLong(surfaced[1]));
            }
            JsonArray edicts = new JsonArray();
            for (Edicts.Edict e : Edicts.book().pending()) {
                JsonObject j = GSON.toJsonTree(e).getAsJsonObject();
                j.addProperty("standing", e.standing());
                edicts.add(j);
            }
            o.add("edicts", edicts);
            o.addProperty("retries", Edicts.retries());
            Persona p = r.persona();
            if (p != null) o.add("persona", GSON.toJsonTree(p));
            // The god's own visual style and its library of effects and routines (read-only in the viewer).
            o.add("style", GSON.toJsonTree(FxStyle.of(r)));
            o.add("library", GSON.toJsonTree(FxLibrary.of(r).all()));
            o.addProperty("chronicleText", m.renderChronicle());
            return o;
        }), 5000);
    }

    /** The system prompt and chronicle block its next thought will be sent, and the shared briefing. */
    public static JsonObject prompt(Realm r) throws ToolException {
        return Sync.call(() -> r.get(() -> {
            JsonObject o = new JsonObject();
            Persona p = r.persona();
            o.addProperty("system", p == null ? "" : Prompts.system(p));
            o.addProperty("chronicle", Prompts.chronicle());
            o.addProperty("briefing", GodSettings.briefing());
            o.addProperty("born", p != null);
            return o;
        }), 5000);
    }

    /** What the god is about to see: pending events, a preview of its next perception, its journal. */
    public static JsonObject upcoming(Realm r, boolean withPerception) throws ToolException {
        JsonObject o = Sync.call(() -> r.get(() -> {
            JsonObject j = new JsonObject();
            EventLogger.Drained d = EventLogger.peek();
            j.add("events", GSON.toJsonTree(d.events()));
            j.add("speech", GSON.toJsonTree(d.speech()));
            j.add("prayers", GSON.toJsonTree(d.prayers()));
            j.add("directives", GSON.toJsonTree(d.directives()));
            j.addProperty("omitted", d.omitted());
            if (withPerception) {
                j.addProperty("perception", Perception.build("preview (events not consumed)", d, true));
            }
            JsonArray journal = new JsonArray();
            for (Journal.Entry e : Journal.last(120)) {
                JsonObject je = new JsonObject();
                je.addProperty("seq", e.seq());
                je.addProperty("at", e.time());
                je.addProperty("kind", e.kind().id());
                je.addProperty("text", e.text());
                journal.add(je);
            }
            j.add("journal", journal);
            return j;
        }), 8000);
        // The annals are files: read them off the server thread.
        try {
            List<Annals.Entry> recent = r.memory().annals().search("", null, null, 0, 60).entries();
            o.add("annals", GSON.toJsonTree(recent));
        } catch (RuntimeException e) {
            o.add("annals", new JsonArray());
        }
        return o;
    }
}
