package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.tools.ToolException;

/**
 * Changes the server owner makes from the mind viewer: a mortal's favor, notes, deeds, summary and
 * title; the chronicle, commandments, goals, heart and persona (which shapes the system prompt);
 * binding commands; or a god's whole memory or persona as raw JSON. Every change runs on the server
 * thread as that god, is saved, and is written to the journal.
 */
public final class MindEdits {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private MindEdits() {
    }

    /** Applies one edit; returns what was done, or throws with why not. */
    public static String apply(Realm r, JsonObject e) throws ToolException {
        String op = str(e, "op");
        if (op == null) throw new ToolException("op is required");
        String done = Sync.call(() -> r.call(() -> {
            try {
                return run(r, op, e);
            } catch (IllegalArgumentException | IllegalStateException | Purpose.Refusal | com.google.gson.JsonParseException x) {
                throw new ToolException(x.getMessage() == null ? x.toString() : x.getMessage());
            }
        }), 8000);
        Journal.add(Journal.Kind.INFO, "Mind viewer: " + done);
        return done;
    }

    private static String run(Realm r, String op, JsonObject e) {
        long now = System.currentTimeMillis();
        Memory m = r.memory();
        String who = str(e, "name");
        String done = switch (op) {
            // ---- One mortal ----
            case "player.favor" -> {
                int f = m.setFavor(need(who, "name"), num(e, "favor"), null);
                yield who + "'s favor set to " + f;
            }
            case "player.note.add" -> {
                m.remember(need(who, "name"), need(str(e, "text"), "text"));
                yield "note added on " + who;
            }
            case "player.note.edit" -> {
                Memory.Note n = findNote(m, who, "notes", e);
                synchronized (m) {
                    n.text = need(str(e, "text"), "text");
                }
                yield "note edited on " + who;
            }
            case "player.note.remove" -> {
                if (!m.removeNote(need(who, "name"), lng(e, "at"), str(e, "old"))) throw new IllegalArgumentException("no such note");
                yield "note removed from " + who;
            }
            case "player.notes.clear" -> "cleared " + m.clearNotes(need(who, "name")) + " note(s) on " + who;
            case "player.deed.add" -> {
                m.addDeed(need(who, "name"), need(str(e, "text"), "text"), now);
                yield "deed added for " + who;
            }
            case "player.deed.edit" -> {
                Memory.Note n = findNote(m, who, "deeds", e);
                synchronized (m) {
                    n.text = need(str(e, "text"), "text");
                }
                yield "deed edited for " + who;
            }
            case "player.deed.remove" -> {
                if (!m.removeDeed(need(who, "name"), lng(e, "at"), str(e, "old"))) throw new IllegalArgumentException("no such deed");
                yield "deed removed from " + who;
            }
            case "player.deeds.clear" -> "cleared " + m.clearDeeds(need(who, "name")) + " deed(s) of " + who;
            case "player.summary" -> {
                Memory.Subject s = m.subject(need(who, "name"));
                synchronized (m) {
                    String t = str(e, "text");
                    s.summary = t == null || t.isBlank() ? null : t.trim();
                }
                yield "summary of " + who + " rewritten";
            }
            case "player.title" -> {
                String t = str(e, "title");
                m.setTitle(need(who, "name"), t == null || t.isBlank() ? null : t.trim(), str(e, "color"));
                try {
                    Devotion.refreshAll();
                } catch (RuntimeException ex) {
                    // titles show on next join
                }
                yield who + "'s title set to " + (t == null || t.isBlank() ? "none" : t);
            }
            case "player.forget" -> {
                if (!m.forget(need(who, "name"))) throw new IllegalArgumentException("no record of " + who);
                yield "forgot " + who + " entirely";
            }
            // ---- The god's own memory ----
            case "chronicle" -> {
                synchronized (m) {
                    String t = str(e, "text");
                    m.chronicle = t == null ? "" : t.trim();
                }
                yield "chronicle rewritten";
            }
            case "commandments" -> {
                List<String> laws = new ArrayList<>();
                JsonArray a = e.has("laws") && e.get("laws").isJsonArray() ? e.getAsJsonArray("laws") : new JsonArray();
                for (JsonElement l : a) {
                    if (l.isJsonPrimitive() && !l.getAsString().isBlank()) laws.add(l.getAsString());
                }
                m.replaceCommandments(laws);
                try {
                    Devotion.refreshAll();
                } catch (RuntimeException ex) {
                    // shown on next refresh
                }
                yield laws.size() + " commandment(s) set";
            }
            case "goal.add" -> {
                Purpose.Goal g = Purpose.set(m, need(str(e, "title"), "title"), str(e, "details"), str(e, "reward"),
                        bool(e, "mission"), 30, now);
                yield "goal " + g.id + " added";
            }
            case "goal.edit" -> {
                Purpose.Goal g = goal(m, need(str(e, "id"), "id"));
                synchronized (m) {
                    if (e.has("title")) g.title = str(e, "title");
                    if (e.has("details")) g.details = str(e, "details");
                    if (e.has("reward")) g.reward = blankNull(str(e, "reward"));
                    if (e.has("note")) g.note = blankNull(str(e, "note"));
                    if (e.has("progress")) g.progress = Math.max(0, Math.min(100, num(e, "progress")));
                    if (e.has("mission")) g.mission = bool(e, "mission");
                    if (e.has("status")) {
                        String st = str(e, "status").toLowerCase(Locale.ROOT);
                        if (!List.of("active", "achieved", "abandoned").contains(st)) {
                            throw new IllegalArgumentException("status is active, achieved or abandoned");
                        }
                        g.status = st;
                        if (!"active".equals(st)) g.closedAt = now;
                    }
                    g.touchedAt = now;
                }
                yield "goal " + g.id + " edited";
            }
            case "goal.remove" -> {
                Purpose.Goal g = goal(m, need(str(e, "id"), "id"));
                synchronized (m) {
                    m.goals.remove(g);
                }
                yield "goal " + g.id + " removed";
            }
            case "heart.set" -> {
                Heart.Feeling f = Heart.parse(need(str(e, "feeling"), "feeling"));
                if (f == null) throw new IllegalArgumentException("unknown feeling");
                double level = Math.max(0, Math.min(100, num(e, "level")));
                Heart h = m.heart;
                h.feel(f, level - h.level(f), "set by the server owner", now);
                yield f.id() + " set to " + Math.round(h.level(f));
            }
            case "heart.clear" -> {
                m.heart.clear(now);
                yield "heart calmed";
            }
            case "memory.raw" -> {
                String json = need(str(e, "json"), "json");
                Memory fresh = Memory.replacing(m, json);
                r.setMemory(fresh);
                m = fresh;
                yield "memory replaced from raw JSON";
            }
            // ---- Who it is (shapes the system prompt) ----
            case "persona.edit" -> {
                Persona p = persona(r);
                JsonObject f = e.has("fields") && e.get("fields").isJsonObject() ? e.getAsJsonObject("fields")
                        : new JsonObject();
                List<String> changed = new ArrayList<>();
                for (String k : f.keySet()) {
                    JsonElement v = f.get(k);
                    switch (k) {
                        case "name" -> p.name = v.getAsString();
                        case "epithet" -> p.epithet = v.getAsString();
                        case "nature" -> p.nature = v.getAsString();
                        case "speech" -> p.speech = v.getAsString();
                        case "temperament" -> p.temperament = v.getAsString();
                        case "voiceEffect" -> p.voiceEffect = blankNull(v.getAsString());
                        case "likes" -> p.likes = lines(v);
                        case "dislikes" -> p.dislikes = lines(v);
                        case "dials" -> {
                            Map<String, Integer> next = new LinkedHashMap<>(p.dials);
                            for (String d : v.getAsJsonObject().keySet()) {
                                if (!Persona.DIALS.contains(d)) throw new IllegalArgumentException("unknown dial " + d);
                                next.put(d, Math.max(0, Math.min(10, v.getAsJsonObject().get(d).getAsInt())));
                            }
                            p.dials = next;
                        }
                        default -> throw new IllegalArgumentException("persona has no editable field " + k);
                    }
                    changed.add(k);
                }
                Persona.save(p);
                TabList.refreshSoon();
                yield "persona edited (" + String.join(", ", changed) + "); the system prompt changes from the next thought";
            }
            case "persona.raw" -> {
                Persona p = GSON.fromJson(need(str(e, "json"), "json"), Persona.class);
                if (p == null || p.name == null || p.name.isBlank()) throw new IllegalArgumentException("a persona needs a name");
                Persona.fillMissingDials(p);
                r.persona = p;
                Persona.save(p);
                TabList.refreshSoon();
                yield "persona replaced from raw JSON";
            }
            case "briefing" -> {
                String t = str(e, "text");
                GPTGOD.get().getConfig().set("personality.briefing", t == null ? "" : t);
                GPTGOD.get().saveConfig();
                yield "briefing (every god's prompt) rewritten";
            }
            // ---- Commands and control ----
            case "edict.issue" -> {
                int minutes = e.has("minutes") ? num(e, "minutes") : 0;
                Edicts.Edict ed = Edicts.issue(need(str(e, "text"), "text"), Bukkit.getConsoleSender(), "mind viewer",
                        minutes, null);
                yield "binding command #" + ed.number + " issued";
            }
            case "edict.cancel" -> {
                int n = num(e, "number");
                yield Edicts.cancel(n).size() + " command(s) cancelled";
            }
            case "wake" -> {
                God.wake(blankNull(str(e, "directive")));
                yield "woken" + (str(e, "directive") == null ? "" : " with a directive");
            }
            case "pause" -> {
                God.setPaused(bool(e, "on"));
                yield bool(e, "on") ? "paused" : "resumed";
            }
            default -> throw new IllegalArgumentException("unknown op " + op);
        };
        m.save();
        return done + " [" + r.id + "]";
    }

    private static Memory.Note findNote(Memory m, String who, String list, JsonObject e) {
        Memory.Subject s = m.subjectIfAny(need(who, "name"));
        if (s == null) throw new IllegalArgumentException("no record of " + who);
        long at = lng(e, "at");
        String old = str(e, "old");
        synchronized (m) {
            for (Memory.Note n : "notes".equals(list) ? s.notes : s.deeds) {
                if (n.at == at && (old == null || old.equals(n.text))) return n;
            }
        }
        throw new IllegalArgumentException("that entry changed or is gone; reload");
    }

    private static Purpose.Goal goal(Memory m, String id) {
        synchronized (m) {
            for (Purpose.Goal g : m.goals) {
                if (id.equalsIgnoreCase(g.id)) return g;
            }
        }
        throw new IllegalArgumentException("no goal " + id);
    }

    private static Persona persona(Realm r) {
        Persona p = r.persona();
        if (p == null) throw new IllegalArgumentException("the god is not born yet");
        return p;
    }

    private static List<String> lines(JsonElement v) {
        List<String> out = new ArrayList<>();
        if (v.isJsonArray()) {
            for (JsonElement x : v.getAsJsonArray()) {
                if (!x.getAsString().isBlank()) out.add(x.getAsString().trim());
            }
        } else {
            for (String s : v.getAsString().split("\n")) {
                if (!s.isBlank()) out.add(s.trim());
            }
        }
        return out;
    }

    private static String need(String s, String what) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(what + " is required");
        return s;
    }

    private static String blankNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    private static int num(JsonObject o, String k) {
        try {
            return (int) Math.round(o.get(k).getAsDouble());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(k + " must be a number");
        }
    }

    private static long lng(JsonObject o, String k) {
        try {
            return o.get(k).getAsLong();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(k + " is required");
        }
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
    }
}
