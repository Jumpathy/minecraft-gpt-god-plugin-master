package net.bigyous.gptgodmc.god;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Background chores done by the cheap memory model: inventing the god's identity, condensing old
 * memories into the chronicle, and folding a mortal's old deeds into their summary. Every call fails
 * soft: a null answer means "keep going without it".
 */
public final class Chronicler {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "god-chronicler");
        t.setDaemon(true);
        return t;
    });

    /** One single-turn request to the memory model; returns the reply text or null. */
    @FunctionalInterface
    interface Model {
        String complete(String prompt, long maxTokens, String purpose);
    }

    /** The memory model (tests replace it so nothing reaches the network). */
    static volatile Model model = Chronicler::request;
    /** Where folds run (tests use a direct executor). */
    static volatile Executor executor = EXEC;

    /** What a fold of old cycle records produced: the new chronicle and a sentence per mortal. */
    record Folded(String chronicle, Map<String, String> players) {
    }

    private Chronicler() {
    }

    /** Asks the memory model to invent a god. Returns null when offline or on failure. */
    static Persona.PersonaSpec inventPersona(Persona p, Collection<String> taken) {
        String prompt = """
                Invent the god of a Minecraft world, as a character for a game.
                What kind of god it is: %s
                How it talks: %s
                It values: %s. It despises: %s.
                (Its physical form in the world is a towering figure shaped like a mortal, wreathed in a glowing \
                aura; that is just its body — its personality comes from the description above.)
                Reply with ONLY a JSON object, no prose, with these string fields:
                "name" (one invented, otherworldly name of 2 to 4 syllables that fits its personality, with the \
                feel of names like %s, but not one of those. Letters only, at most one apostrophe or hyphen, \
                at most 14 letters, easy to type in chat. Never an ordinary human name, never a god from a real \
                religion or mythology, never a famous fictional character, never a plain word like "God" or "Lord"%s),
                "epithet" (a short title that fits its personality, e.g. "the Violet Watcher", "Lord of Naps"),
                "temperament" (2-3 sentences in its own spirit that ADD to the description above instead of \
                repeating it: a piece of its history, a habit or quirk, and a signature way it shows favor or \
                displeasure. Do not restate what kind of god it is, what it values or what it despises; the god \
                already knows those).
                """.formatted(p.nature, p.speech == null || p.speech.isBlank() ? "its own way" : p.speech,
                String.join(", ", p.likes), String.join(", ", p.dislikes), String.join(", ", GodNames.EXAMPLES),
                GodNames.mythAllowed() ? ". Exception: if the description above makes it a god of ancient myth "
                        + "(Greek, Roman, Norse, Egyptian), it bears that god's own name, like Zeus, Athena or Odin"
                        : "");
        String text = model.complete(prompt, 800, "inventing a persona");
        if (text == null) {
            return null;
        }
        return parsePersona(text, taken);
    }

    /**
     * Asks the memory model to draw a newborn god's visual style from its persona. Returns the raw reply
     * (read leniently by {@link FxStyle#drafted}) or null when there is no model or key.
     */
    static String draftStyle(Persona p) {
        String prompt = """
                A god of a Minecraft world needs a visual style of its own: the colours and shapes of the light, \
                storms and signs it conjures, so it looks like no other god.
                The god: %s%s. What kind of god it is: %s
                Its temperament: %s
                Reply with ONLY a JSON object, no prose, with these fields:
                "primary", "secondary", "accent", "shadow" (colours as "#RRGGBB": its main colour, a second that \
                goes with it, a highlight, and its dark),
                "hud" (the "#RRGGBB" accent of its mark on mortals' screens; readable on a dark background),
                "sprites" (2-3 of: %s), "shapes" (2-3 of: %s),
                "motion" (how its effects move, 2-5 words, e.g. "slow and heavy", "sharp and electric"),
                "sigil" (one or two symbol characters that are its mark, e.g. "\u2726", "\u263D", "\u2020"; no emoji),
                "sky" (an object with a "#RRGGBB" sky tint for each mood: %s).
                Fit all of it to this god's personality rather than to the usual colours of gods.
                """.formatted(p.shortName(), p.epithet == null || p.epithet.isBlank() ? "" : ", " + p.epithet, p.nature,
                p.temperament == null || p.temperament.isBlank() ? "(not known)" : p.temperament,
                String.join(", ", FxScenes.SPRITES), String.join(", ", FxScenes.SHAPES), String.join(", ", FxStyle.MOODS));
        return model.complete(prompt, 500, "drawing a style");
    }

    /** Reads the invented god; an unfit name comes back as null (the caller picks one from its table). */
    static Persona.PersonaSpec parsePersona(String text, Collection<String> taken) {
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            JsonObject o = JsonParser.parseString(text.substring(start, end + 1)).getAsJsonObject();
            String name = o.has("name") ? o.get("name").getAsString().trim() : "";
            String epithet = o.has("epithet") ? o.get("epithet").getAsString().trim() : "";
            String temperament = o.has("temperament") ? o.get("temperament").getAsString().trim() : "";
            String problem = GodNames.problem(name, taken);
            if (problem != null) {
                GPTGOD.LOGGER.info("The invented name '" + name + "' was not fit for a god (" + problem
                        + "); choosing one from the table.");
                if (!name.isBlank()) temperament = temperament.replace(name, "{name}");
                name = null;
            }
            return new Persona.PersonaSpec(name, epithet, temperament);
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not parse the invented persona, using a preset god: " + text);
            return null;
        }
    }

    // ---- The chronicle -------------------------------------------------------------------------

    /** Condenses old cycle records into the chronicle off-thread; calls back with null on failure. */
    static void foldAsync(String chronicle, List<Memory.CycleRecord> records, Consumer<Folded> done) {
        // One chronicler thread serves every god, so each job carries the realm that asked for it.
        submit(() -> {
            Folded result = null;
            try {
                result = fold(chronicle, records);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Failed to condense memories", e);
            }
            done.accept(result);
        }, () -> done.accept(null));
    }

    static Folded fold(String chronicle, List<Memory.CycleRecord> records) {
        Persona persona = Persona.current();
        SimpleDateFormat clock = new SimpleDateFormat("MM-dd HH:mm");
        StringBuilder log = new StringBuilder();
        for (Memory.CycleRecord r : records) {
            log.append("[").append(clock.format(new Date(r.at))).append("] ");
            if (r.perceived != null) log.append("Noticed: ").append(Memory.clip(r.perceived, 400)).append(". ");
            if (r.acts.isEmpty()) {
                log.append("Did nothing.");
            } else {
                log.append("Did: ");
                for (String act : r.acts) log.append(Memory.clip(act, 300)).append("; ");
            }
            log.append("\n");
        }
        String prompt = """
                You keep the long-term memory of %s, the god of a Minecraft world. Update its chronicle so it \
                also covers the new log.
                Keep only what the god may act on later: promises, threats and bargains it spoke (quote them \
                and say whether they were kept), debts, grudges and alliances, unfinished or failed quests, \
                notable firsts (first death, first offering, first build), places that matter. Drop routine \
                noise: mining, walking, idle looks, repeated chatter.
                Write as the god ("I"), past tense, short bullet points, at most 300 words.
                Reply with ONLY a JSON object: {"chronicle": "<the whole new chronicle>", "players": \
                {"<mortal's name>": "<one sentence the god should remember about them from the NEW log>"}}. \
                Leave out mortals with nothing worth remembering.

                CURRENT CHRONICLE:
                %s

                NEW LOG:
                %s
                """.formatted(persona == null ? "the god" : persona.title(),
                chronicle == null || chronicle.isBlank() ? "(empty — this is the beginning)" : chronicle, log);
        String text = model.complete(prompt, 1500, "folding the chronicle");
        return parseFold(text);
    }

    /** The fold's JSON; a reply that is plain prose is taken as the chronicle alone. */
    static Folded parseFold(String text) {
        if (text == null || text.isBlank()) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0) {
            return new Folded(text.trim(), Map.of());
        }
        if (end <= start) return null;
        try {
            JsonObject o = JsonParser.parseString(text.substring(start, end + 1)).getAsJsonObject();
            if (!o.has("chronicle") || o.get("chronicle").getAsString().isBlank()) return null;
            Map<String, String> players = new LinkedHashMap<>();
            if (o.has("players") && o.get("players").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("players").entrySet()) {
                    if (e.getValue().isJsonPrimitive() && !e.getValue().getAsString().isBlank()) {
                        players.put(e.getKey(), e.getValue().getAsString().trim());
                    }
                }
            }
            return new Folded(o.get("chronicle").getAsString().trim(), players);
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not parse the folded chronicle; keeping the old one.");
            return null;
        }
    }

    // ---- A mortal's summary ----------------------------------------------------------------

    /** Folds a mortal's oldest deeds (may be empty) into their summary off-thread; null on failure. */
    static void foldDeedsAsync(String name, String summary, List<Memory.Note> deeds, Consumer<String> done) {
        submit(() -> {
            String result = null;
            try {
                result = foldDeeds(name, summary, deeds);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Failed to fold " + name + "'s deeds", e);
            }
            done.accept(result);
        }, () -> done.accept(null));
    }

    static String foldDeeds(String name, String summary, List<Memory.Note> deeds) {
        Persona persona = Persona.current();
        SimpleDateFormat clock = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        StringBuilder log = new StringBuilder();
        for (Memory.Note d : deeds) {
            log.append("- [").append(clock.format(new Date(d.at))).append("] ").append(d.text).append("\n");
        }
        String prompt = """
                You keep the memory of %s, the god of a Minecraft world, about one mortal: %s. Merge the summary \
                and the older deeds below into one new summary of at most 80 words, written as the god ("I"), \
                past tense. Keep what decides how the god treats them: promises made to or by them, debts, \
                grudges, kindness, betrayals, quests done or failed, notable firsts. Drop routine detail. \
                Reply with only the summary.

                SUMMARY SO FAR: %s

                OLDER DEEDS (oldest first):
                %s
                """.formatted(persona == null ? "the god" : persona.title(), name,
                summary == null || summary.isBlank() ? "(none)" : summary, log.length() == 0 ? "(none)\n" : log);
        String text = model.complete(prompt, 400, "folding a mortal's deeds");
        return text == null || text.isBlank() ? null : Memory.oneLine(text);
    }

    private static void submit(Runnable work, Runnable rejected) {
        try {
            executor.execute(Realm.bound(work));
        } catch (RejectedExecutionException e) {
            rejected.run();
        }
    }

    /** Single-turn request to the memory model. Returns the reply text, or null. */
    private static String request(String prompt, long maxTokens, String purpose) {
        if (GPTGOD.get() == null) {
            return null; // no server (unit tests): never reach the network
        }
        if (!Llm.anthropic()) {
            // Another provider: one plain question to its memory model (llm.memory-model).
            return Llm.complete(GodSettings.memoryModel(), prompt, maxTokens, purpose);
        }
        AnthropicClient client = GodClient.get();
        if (client == null) {
            return null;
        }
        String modelId = GodSettings.memoryModel();
        try {
            Message response = client.messages().create(MessageCreateParams.builder()
                    .model(modelId)
                    .maxTokens(maxTokens + 2000)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .addUserMessage(prompt)
                    .build());
            GodClient.recordUsage(modelId, purpose, response.usage());
            StringBuilder sb = new StringBuilder();
            for (ContentBlock block : response.content()) {
                block.text().ifPresent(t -> sb.append(t.text()));
            }
            return sb.toString();
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Memory model request failed: " + e.getMessage());
            return null;
        }
    }

    /** Writes the annals and stops the chronicler (plugin shutdown, via God.disable). */
    public static void shutdown() {
        Annals.flushAll();
        EXEC.shutdownNow();
    }
}
