package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.ToolUnion;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.god.tools.GodTool;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;
import net.bigyous.gptgodmc.utils.TokenTracker;

/**
 * Measures the real pieces of a request the god sends (system prompt, tool definitions, a perception)
 * and writes ESTIMATED token counts and costs to build/test-renders/cost-estimate.md, which costs.md
 * quotes. Nothing here talks to the API: tokens are estimated from characters. The real counts are
 * printed by the plugin itself ([God usage] console lines and plugins/gptgodmc/usage.csv).
 *
 * Nothing is hard-coded about how many powers exist, so the numbers follow the tool list as it grows.
 */
class CostEstimateTest {
    /** Characters per token for English prose (system prompt, perception). */
    static final double PROSE_CHARS_PER_TOKEN = 4.0;
    /** Characters per token for JSON tool definitions (punctuation and short keys tokenize densely). */
    static final double JSON_CHARS_PER_TOKEN = 3.5;
    /** Tokens the API adds to the system prompt when tools are present (an assumption, see costs.md). */
    static final int TOOL_USE_OVERHEAD = 350;
    /** A 640x360 render from look: width x height / 750 tokens. */
    static final int IMAGE_TOKENS = 640 * 360 / 750;
    /** Fresh, uncached input per request (the bit after the last cache breakpoint). */
    static final int FRESH_INPUT = 10;

    // Assumed sizes of what the god writes (thinking + words + tool calls), at the default effort "low".
    static final int OUT_QUIET = 120;    // a look where it decides to do nothing
    static final int OUT_ACT = 400;      // the request in which it speaks and acts (2-3 powers)
    static final int OUT_FOLLOWUP = 150; // the request after the tool results, usually just ending the turn
    static final int TOOL_RESULTS = 150; // the text results of 2-3 powers sent back

    static final List<String> MODELS = List.of("claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5");

    @Test
    void writeCostEstimate() throws Exception {
        YamlConfiguration config = loadConfig();

        // ---- System prompt, for every archetype in config.yml ----
        ConfigurationSection archetypes = config.getConfigurationSection("personality.archetypes");
        assertTrue(archetypes != null && !archetypes.getKeys(false).isEmpty());
        List<String[]> promptRows = new ArrayList<>();
        int systemChars = 0;
        for (String key : archetypes.getKeys(false)) {
            int len = Prompts.system(persona(key, archetypes.getConfigurationSection(key))).length();
            promptRows.add(new String[] { key, String.valueOf(len) });
            systemChars = Math.max(systemChars, len);
        }

        // ---- Tool definitions, exactly as sent ----
        List<ToolUnion> tools = ToolRegistry.sdkTools();
        List<GodTool> godTools = new ArrayList<>(ToolRegistry.all());
        assertTrue(tools.size() == godTools.size() && !tools.isEmpty());
        List<Object[]> toolSizes = new ArrayList<>();
        int toolChars = 0;
        for (int i = 0; i < tools.size(); i++) {
            int len = ObjectMappers.jsonMapper().writeValueAsString(tools.get(i).asTool()).length();
            toolSizes.add(new Object[] { godTools.get(i).name(), len });
            toolChars += len;
        }
        // Tests have no config, so custom_power says "none configured"; on a server it lists the defaults.
        int customExtra = customPowerDescription(config).length()
                - ToolRegistry.get("custom_power").description().length();
        toolChars += Math.max(0, customExtra);
        toolSizes.sort(Comparator.comparingInt((Object[] r) -> (Integer) r[1]).reversed());

        // ---- Chronicle and perceptions ----
        int chronicleChars = Prompts.chronicle().length(); // empty chronicle (a new world)
        int chronicleFullChars = fullChronicle().length() + 60;
        String idle = idlePerception();
        String busy = busyPerception();

        // ---- Whole request as JSON, as a cross-check ----
        String body = ObjectMappers.jsonMapper().writeValueAsString(GodBrain.params("claude-sonnet-5-5",
                Prompts.system(persona("wrathful", archetypes.getConfigurationSection("wrathful"))),
                List.of(MessageParam.ofUser(busy)))._body());

        // ---- Tokens ----
        int tSystem = prose(systemChars);
        int tTools = (int) Math.round(toolChars / JSON_CHARS_PER_TOKEN) + TOOL_USE_OVERHEAD;
        int tStatic = tSystem + tTools;
        int tChronicle = prose(chronicleFullChars);
        int tIdle = prose(idle.length());
        int tBusy = prose(busy.length());

        StringBuilder md = new StringBuilder();
        md.append("<!-- Generated by CostEstimateTest. ESTIMATES from character counts, not API measurements. -->\n");
        md.append(String.format(Locale.US, "Measured from the code with %d powers. Method: prose ÷ %.1f characters "
                + "per token, JSON tool definitions ÷ %.1f, plus an assumed %d tokens the API adds when tools are "
                + "present. Images: width × height ÷ 750.%n%n", godTools.size(), PROSE_CHARS_PER_TOKEN,
                JSON_CHARS_PER_TOKEN, TOOL_USE_OVERHEAD));

        md.append("#### The pieces of one request\n\n");
        md.append("| Piece | Characters | ≈ Tokens | Cached? |\n|---|---:|---:|---|\n");
        row(md, "Standing instructions (system prompt, longest archetype)", systemChars, tSystem,
                "yes — same every thought");
        row(md, "Power definitions (" + godTools.size() + " powers, with the default custom powers) + tool-use overhead",
                toolChars, tTools, "yes — same every thought");
        row(md, "**Static prefix total**", systemChars + toolChars, tStatic, "read from cache");
        row(md, "Chronicle, new world (empty)", chronicleChars, prose(chronicleChars), "written each thought");
        row(md, "Chronicle, full (≈350 words, its cap)", chronicleFullChars, tChronicle, "written each thought");
        row(md, "Perception, quiet (1 player, few events)", idle.length(), tIdle, "written each thought");
        row(md, "Perception, busy (6 players, ~25 events, chat, prayers)", busy.length(), tBusy,
                "written each thought");
        int awareQuiet = SKY.length() + 1 + livingLand(1).length();
        int awareBusy = SKY.length() + 1 + livingLand(3).length();
        row(md, "…of which the sky line and the living land, quiet (1 group of players)", awareQuiet,
                prose(awareQuiet), "written each thought");
        row(md, "…of which the sky line and the living land, busy (3 groups and their marks)", awareBusy,
                prose(awareBusy), "written each thought");
        md.append(String.format(Locale.US, "%nWhole request JSON for a busy thought: %,d characters.%n%n",
                body.length()));

        md.append("System prompt by archetype (characters): ");
        List<String> parts = new ArrayList<>();
        for (String[] r : promptRows) parts.add(r[0] + " " + String.format(Locale.US, "%,d", Integer.parseInt(r[1])));
        md.append(String.join(", ", parts)).append(".\n\n");
        md.append("Largest power definitions (characters of JSON): ");
        parts.clear();
        for (int i = 0; i < Math.min(6, toolSizes.size()); i++) {
            parts.add("`" + toolSizes.get(i)[0] + "` " + String.format(Locale.US, "%,d", (Integer) toolSizes.get(i)[1]));
        }
        md.append(String.join(", ", parts)).append(String.format(Locale.US,
                "; average %,d.%n%n", toolChars / godTools.size()));

        // ---- Per-request token counts ("per-message counts") ----
        md.append("#### Tokens per request (one \"message\" to Claude)\n\n");
        md.append("Assumed output (thinking + words + power calls, effort `low`): ").append(OUT_QUIET)
                .append(" for a look that ends in silence, ").append(OUT_ACT).append(" for a request that speaks and acts, ")
                .append(OUT_FOLLOWUP).append(" for the follow-up after the power results (≈").append(TOOL_RESULTS)
                .append(" tokens of results). A `look` image adds ≈").append(IMAGE_TOKENS).append(" tokens.\n\n");
        md.append("| Request | Cache read | Cache write | Fresh input | Output |\n|---|---:|---:|---:|---:|\n");
        int pBusy = tChronicle + tBusy;
        int pIdle = tChronicle + tIdle;
        Req coldFirst = new Req(0, tStatic + pBusy, FRESH_INPUT, OUT_ACT);
        Req warmFirst = new Req(tStatic, pBusy, FRESH_INPUT, OUT_ACT);
        Req followUp = new Req(tStatic + pBusy, OUT_ACT + TOOL_RESULTS, FRESH_INPUT, OUT_FOLLOWUP);
        Req imageStep = new Req(tStatic + pBusy + OUT_ACT + TOOL_RESULTS, OUT_FOLLOWUP + IMAGE_TOKENS + 60,
                FRESH_INPUT, OUT_ACT);
        Req quietWarm = new Req(tStatic, pIdle, FRESH_INPUT, OUT_QUIET);
        Req quietCold = new Req(0, tStatic + pIdle, FRESH_INPUT, OUT_QUIET);
        reqRow(md, "1st request, cache warm (busy perception)", warmFirst);
        reqRow(md, "1st request, cache cold (busy perception)", coldFirst);
        reqRow(md, "Follow-up after power results", followUp);
        reqRow(md, "Extra step that looked at an image", imageStep);
        reqRow(md, "Quiet look ending in silence, warm", quietWarm);
        reqRow(md, "Quiet look ending in silence, cold", quietCold);

        // ---- Cost per thought ----
        Thought[] thoughts = {
                new Thought("Quiet look, does nothing (warm cache)", List.of(quietWarm)),
                new Thought("Quiet look, does nothing (cold cache)", List.of(quietCold)),
                new Thought("Answers a prayer: speaks + acts, then ends (warm)", List.of(warmFirst, followUp)),
                new Thought("Answers a prayer (cold cache)", List.of(coldFirst, followUp)),
                new Thought("Big moment: acts, looks at an image, acts again (warm)",
                        List.of(warmFirst, imageStep, followUp)) };
        md.append("\n#### Cost per thought (USD)\n\n| Thought |");
        for (String m : MODELS) md.append(' ').append(m).append(" |");
        md.append("\n|---|");
        for (int i = 0; i < MODELS.size(); i++) md.append("---:|");
        md.append('\n');
        for (Thought t : thoughts) {
            md.append("| ").append(t.name).append(" |");
            for (String m : MODELS) md.append(String.format(Locale.US, " $%.4f |", t.cost(m)));
            md.append('\n');
        }
        md.append("\nPrices used (USD per million tokens: input / output / cache read / cache write), from "
                + "`utils/TokenTracker`: ");
        parts.clear();
        for (String m : MODELS) {
            parts.add(String.format(Locale.US, "%s $%.2f / $%.2f / $%.2f / $%.3f", m,
                    TokenTracker.costOf(m, 1_000_000, 0, 0, 0), TokenTracker.costOf(m, 0, 1_000_000, 0, 0),
                    TokenTracker.costOf(m, 0, 0, 1_000_000, 0), TokenTracker.costOf(m, 0, 0, 0, 1_000_000)));
        }
        md.append(String.join("; ", parts)).append(".\n");

        // ---- Scenarios ----
        md.append("\n#### Scenarios (per hour of play, then per month at 3 hours a day)\n\n");
        // Players who are mining or building always leave events pending, so the god looks every
        // brain.heartbeat-seconds (40 s = 90 times an hour); prayers and its name add looks in between.
        Scenario afk = new Scenario("Players online but idle (only idle-seconds looks, every 270 s, cache warm)", 13, 0, 0, 0, 0);
        Scenario quiet = new Scenario("Quiet server, 1–2 players playing", 55, 0, 18, 2, 0);
        Scenario lively = new Scenario("Lively evening, 5–8 players", 50, 0, 55, 0, 15);
        int folds;
        md.append("| Scenario | Thoughts per hour (mix) | Memory-model folds | ");
        for (String m : MODELS) md.append(m).append(" per hour | ");
        md.append(MODELS.get(0)).append(" per month |\n|---|---|---:|");
        for (int i = 0; i < MODELS.size(); i++) md.append("---:|");
        md.append("---:|\n");
        for (Scenario s : List.of(afk, quiet, lively)) {
            folds = Math.max(0, s.total() / 6);
            md.append("| ").append(s.name).append(" | ").append(s.mix()).append(" | ").append(folds).append(" |");
            for (String m : MODELS) {
                md.append(String.format(Locale.US, " $%.2f |", s.cost(m, thoughts, folds)));
            }
            md.append(String.format(Locale.US, " $%.0f |%n", s.cost(MODELS.get(0), thoughts, folds) * 3 * 30));
        }
        md.append(String.format(Locale.US, "%nOne fold of the chronicle by the memory model (%s): ≈%,d input + "
                + "≈%,d output tokens ≈ $%.4f.%n", "claude-haiku-5-5", FOLD_IN, FOLD_OUT,
                TokenTracker.costOf("claude-haiku-5-5", FOLD_IN, FOLD_OUT, 0, 0)));

        File out = new File("build/test-renders/cost-estimate.md");
        out.getParentFile().mkdirs();
        Files.writeString(out.toPath(), md.toString(), StandardCharsets.UTF_8);

        assertTrue(tStatic > 1000, "the static prefix should be measurable");
        assertTrue(tBusy > tIdle);
        assertTrue(awareBusy <= AWARENESS_BUDGET, "the sky and the living land take " + awareBusy
                + " characters of a busy perception; the budget is " + AWARENESS_BUDGET);
    }

    /**
     * Characters the god's awareness of the sky and the land may add to a busy perception (about 450
     * tokens, written fresh every thought). Raise it only on purpose: see costs.md.
     */
    static final int AWARENESS_BUDGET = 1800;

    /** A full sky line: the moon and rain that has just stopped. */
    static final String SKY = Sky.describe(new Sky.View(13667, 11, Sky.Weather.CLEAR, 4 * 60_000L, Sky.Weather.RAIN));

    /** "## The living land around mortals" as LandSense writes it, from the real describe and render. */
    static String livingLand(int groups) {
        String[][] who = { { "Steve" }, { "Alex", "Notch_Jr", "Kaya" }, { "Bram", "Lumi" } };
        Ecology.Land[] lands = {
                new Ecology.Land("plains", new TreeMap<>(Map.of("cow", 6, "sheep", 3, "wolf", 2)), 2, 8, 1, 10, 6, 20,
                        625, 2, true),
                new Ecology.Land("dark_forest", new TreeMap<>(Map.of("pig", 4, "chicken", 7, "fox", 1)), 0, 0, 0, 0,
                        0, 300, 625, 2, true),
                new Ecology.Land("river", new TreeMap<>(Map.of("salmon", 5, "cow", 2)), 0, 2, 0, 24, 6, 40, 625, 2,
                        true) };
        Ecology.Ledger ledger = new Ecology.Ledger();
        long now = 1_700_000_000_000L, halfLife = 24 * 3_600_000L;
        List<Ecology.Spot> spots = new ArrayList<>();
        StringBuilder sb = new StringBuilder("## The living land around mortals\n");
        for (int g = 0; g < groups; g++) {
            int x = 132 + g * 200, z = -41 + g * 150;
            sb.append("- around ").append(String.join(", ", who[g])).append(" (").append(x).append(", ").append(z)
                    .append("): ").append(Ecology.describe(lands[g])).append('\n');
            spots.add(new Ecology.Spot("world", x, z));
            for (int i = 0; i < 60; i++) {
                Ecology.record(ledger, new WorldDeed(who[g][0], "fell_trees", "oak_log", Set.of("log"), "world", x, 64,
                        z, now), halfLife);
            }
            for (int i = 0; i < 14; i++) {
                Ecology.record(ledger, new WorldDeed(who[g][0], "kill_mob", "cow", Set.of("animal"), "world", x, 64, z,
                        now), halfLife);
            }
        }
        return sb.append("Their mark on it (fades over a day or two):\n")
                .append(Ecology.render(ledger, spots, now, halfLife, 4)).toString();
    }

    // ---- Model of a thought ----------------------------------------------------------------

    static final int FOLD_IN = 1_700;
    static final int FOLD_OUT = 550;

    record Req(int cacheRead, int cacheWrite, int fresh, int output) {
        double cost(String model) {
            return TokenTracker.costOf(model, fresh, output, cacheRead, cacheWrite);
        }
    }

    record Thought(String name, List<Req> requests) {
        double cost(String model) {
            return requests.stream().mapToDouble(r -> r.cost(model)).sum();
        }
    }

    /** Thoughts per hour by kind (indexes into the Thought table). */
    record Scenario(String name, int quietWarm, int quietCold, int promptWarm, int promptCold, int big) {
        int total() {
            return quietWarm + quietCold + promptWarm + promptCold + big;
        }

        String mix() {
            return String.format(Locale.US, "%d: %d silent looks (%d cold), %d answers (%d cold), %d big moments",
                    total(), quietWarm + quietCold, quietCold, promptWarm + promptCold, promptCold, big);
        }

        double cost(String model, Thought[] t, int folds) {
            return quietWarm * t[0].cost(model) + quietCold * t[1].cost(model)
                    + promptWarm * t[2].cost(model) + promptCold * t[3].cost(model) + big * t[4].cost(model)
                    + folds * TokenTracker.costOf("claude-haiku-5-5", FOLD_IN, FOLD_OUT, 0, 0);
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------

    static int prose(int chars) {
        return (int) Math.round(chars / PROSE_CHARS_PER_TOKEN);
    }

    static void row(StringBuilder md, String what, int chars, int tokens, String cached) {
        md.append(String.format(Locale.US, "| %s | %,d | %,d | %s |%n", what, chars, tokens, cached));
    }

    static void reqRow(StringBuilder md, String what, Req r) {
        md.append(String.format(Locale.US, "| %s | %,d | %,d | %,d | %,d |%n", what, r.cacheRead(), r.cacheWrite(),
                r.fresh(), r.output()));
    }

    static YamlConfiguration loadConfig() throws Exception {
        try (InputStream in = CostEstimateTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertTrue(in != null, "config.yml on the test classpath");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    static Persona persona(String archetype, ConfigurationSection a) {
        Persona p = new Persona();
        p.archetype = archetype;
        p.name = "Vaelthorn";
        p.epithet = "the Unblinking Eye";
        p.nature = a.getString("description", "");
        p.speech = a.getString("speech", "");
        p.temperament = "Proud and patient, slow to forgive but quick to notice devotion; it keeps every promise "
                + "and every grudge, and enjoys being feared a little.";
        ConfigurationSection dials = a.getConfigurationSection("dials");
        if (dials != null) {
            for (String k : dials.getKeys(false)) p.dials.put(k, dials.getInt(k));
        }
        p.likes = new ArrayList<>(a.getStringList("likes"));
        p.dislikes = new ArrayList<>(a.getStringList("dislikes"));
        while (p.likes.size() < 3) p.likes.add("slaying monsters");
        while (p.dislikes.size() < 3) p.dislikes.add("killing animals");
        return p;
    }

    /** custom_power's description as a server with the default config.yml sees it. */
    static String customPowerDescription(YamlConfiguration config) {
        StringBuilder sb = new StringBuilder("Use one of the powers the keepers of this server made for you:");
        for (GodSettings.CustomPower p : GodSettings.parseCustomPowers(config.getConfigurationSection("custom-powers"))) {
            sb.append("\n- ").append(p.name()).append(": ").append(p.description());
            if (p.cooldownSeconds() > 0) sb.append(" (once every ").append(p.cooldownSeconds()).append("s)");
        }
        return sb.toString();
    }

    static String fullChronicle() {
        String s = "- Steve built a shrine of gold by the river and prayed there every dusk; I made him my "
                + "herald and promised him a relic if he finished the north tower. ";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 2300) sb.append(s);
        return sb.toString();
    }

    // Perceptions follow Perception.build section by section; the parts Memory renders are real.

    static String idlePerception() {
        Memory m = new Memory();
        m.adjustFavor("Steve", 2, "prayed at dawn");
        StringBuilder sb = header("a quiet moment; nothing demands you, but you may act on your own whims");
        sb.append(player("Steve", 2));
        sb.append("\n").append(livingLand(1));
        sb.append("\n## Quests you have given\n(none)\n\n");
        sb.append("## Your ledger (favor and notes you keep on mortals)\n").append(m.renderLedger(List.of("Steve")))
                .append("\n## Your recent acts (oldest first)\n");
        addRecent(m, 3);
        sb.append(m.renderRecent()).append("\n## What mortals did since you last looked (oldest first)\n");
        sb.append("[1m10s ago] Steve mined 14 stone\n[40s ago] Steve crafted a furnace\n");
        sb.append("\n## Conversation (last 10 minutes, oldest first; NEW = since you last looked; \"You\" = your "
                + "own words)\n(silence)\n");
        return sb.toString();
    }

    static String busyPerception() {
        Memory m = new Memory();
        String[] names = { "Steve", "Alex", "Notch_Jr", "Kaya", "Bram", "Lumi" };
        StringBuilder sb = header("Alex prays to you; Steve said your name");
        for (int i = 0; i < names.length; i++) {
            m.adjustFavor(names[i], i - 2, "deeds noticed");
            m.remember(names[i], "wants a castle by the sea and promised me a gold offering");
            sb.append(player(names[i], i - 2));
        }
        sb.append("\n").append(livingLand(3));
        sb.append("\n## Structures mortals have built\n");
        for (int i = 0; i < 5; i++) {
            sb.append(String.format("- structure_%d: %d blocks, built by %s, centered at (%d, 70, %d)%n", i,
                    40 + i * 30, names[i], 120 + i * 9, -40 + i * 5));
        }
        m.addWork("Temple of the Violet Hour", "temple", "world", 10, 64, 20, 1800, "15x17x21");
        m.addQuest("Build a shrine of gold", "at least 9 gold blocks around a beacon", "Alex", 10);
        m.addQuest("Slay 10 zombies before dawn", "", "all", 10);
        sb.append("\n## Your works (things you built)\n").append(m.renderWorks());
        sb.append("\n## Quests you have given\n").append(m.renderQuests());
        sb.append("\n## Your ledger (favor and notes you keep on mortals)\n")
                .append(m.renderLedger(List.of(names)));
        addRecent(m, 14);
        sb.append("\n## Your recent acts (oldest first)\n").append(m.renderRecent());
        sb.append("\n## What mortals did since you last looked (oldest first)\n");
        String[] events = { "%s killed a zombie with an iron_sword", "%s placed 32 oak_planks near spawn",
                "%s died: fell from a high place", "%s tamed a wolf", "%s crafted a diamond_pickaxe",
                "%s opened a chest at (130, 65, -33)", "%s attacked %s" };
        for (int i = 0; i < 25; i++) {
            String e = events[i % events.length];
            String line = e.contains("%s attacked") ? String.format(e, names[i % 6], names[(i + 1) % 6])
                    : String.format(e, names[i % 6]);
            sb.append("[").append(EventLogger.ago(Duration.ofSeconds(40 - i))).append("] ").append(line).append('\n');
        }
        // The conversation timeline: talk from earlier looks, your own words, then what is new.
        Conversation talk = new Conversation();
        long now = 1_700_000_000_000L;
        talk.heard(now - 400_000, "Kaya said in chat: \"does anyone know where the god's temple is?\"");
        talk.spoke(now - 380_000, "You announced to everyone: \"My temple stands east of the river. Come, and "
                + "bring gold.\"");
        talk.heard(now - 370_000, "Bram said in chat: \"lol it heard you\"");
        talk.heard(now - 360_000, "Kaya prays to you: \"thank you, great one\"");
        talk.spoke(now - 355_000, "You whispered to Kaya (only they heard): \"Gratitude is a fine first offering.\"");
        talk.render(now - 300_000, true);
        for (int i = 0; i < 12; i++) {
            talk.heard(now - (30 - i * 2) * 1000L, names[i % 6]
                    + " said in chat: \"anyone got spare iron? I'm building the bridge to the island\"");
        }
        talk.heard(now - 5000, "Alex prays to you: \"Great one, bless my shrine and smite the creeper that ruined it\"");
        talk.heard(now - 2000, "Steve said in chat: \"Vaelthorn, are you watching? look at my tower\"");
        sb.append("\n## Conversation (last 10 minutes, oldest first; NEW = since you last looked; \"You\" = your "
                + "own words)\n").append(talk.render(now, true));
        sb.append("\n## Waiting for your answer (each about what THEY said; see the conversation)\n");
        sb.append("[5s ago] Alex prays to you: \"Great one, bless my shrine and smite the creeper that ruined it\"\n");
        sb.append("[2s ago] Steve said in chat: \"Vaelthorn, are you watching? look at my tower\"\n");
        return sb.toString();
    }

    static StringBuilder header(String trigger) {
        StringBuilder sb = new StringBuilder("# Perception — day 12, 19:40 (dusk)\nYou looked because: ")
                .append(trigger).append("\n\n## The world\nworld (overworld): day 12, 19:40 (dusk), weather: clear, "
                        + "difficulty normal\n" + SKY + "\n\n## Your vessel\nNot manifested — you are an invisible presence. Use "
                        + "vessel_go to appear somewhere.\n\n## Mortals present\n");
        return sb;
    }

    static String player(String name, int favor) {
        return String.format("- %s — favor %s%d (%s)%n    health 18/20, food 17/20, level 12, survival | overworld "
                + "(132, 66, -41), plains, on the surface%n    holding iron_pickaxe; armor: iron_helmet, "
                + "iron_chestplate, leather_boots; effects: speed%n    activity: standing/walking, facing north-east | "
                + "danger: 1 hostile(s) within 12 blocks | structure_1 (130, 70, -36)%n", name, favor > 0 ? "+" : "",
                favor, Memory.favorLabel(favor));
    }

    static void addRecent(Memory m, int count) {
        for (int i = 0; i < count; i++) {
            Memory.CycleRecord r = new Memory.CycleRecord();
            r.at = System.currentTimeMillis() - (count - i) * 45_000L;
            r.trigger = "your regular watch over the world";
            r.perceived = "Steve said in chat: \"is the god real?\"; 4 other event(s)";
            if (i % 3 != 0) {
                r.acts.add("whisper{\"player\":\"Steve\",\"message\":\"I am always watching, little builder.\"} → "
                        + "Whispered to Steve.");
                r.acts.add("judge{\"player\":\"Steve\",\"delta\":1,\"reason\":\"curiosity\"} → Steve's favor is now +3.");
            }
            m.recent.addLast(r);
        }
    }
}
