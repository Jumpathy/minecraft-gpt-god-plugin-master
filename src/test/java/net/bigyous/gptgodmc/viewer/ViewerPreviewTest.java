package net.bigyous.gptgodmc.viewer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.anthropic.core.ObjectMappers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.Heart;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Prompts;
import net.bigyous.gptgodmc.god.Purpose;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;

/**
 * Renders the mind viewer page against sample data: build/test-renders/viewer.html opens in any browser
 * (no server) with two gods, live and finished thoughts, a look image, memory, players and a prompt.
 */
class ViewerPreviewTest {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeSpecialFloatingPointValues()
            .create();

    @AfterEach
    void off() {
        MindTrace.setEnabled(false);
        MindTrace.clear();
    }

    @Test
    void rendersThePageWithSampleData() throws Exception {
        MindTrace.clear();
        MindTrace.setEnabled(true);
        long now = System.currentTimeMillis();
        Persona p = persona();
        String system = Prompts.system(p);
        String tools = ObjectMappers.jsonMapper().writerWithDefaultPrettyPrinter()
                .writeValueAsString(ToolRegistry.sdkTools());
        JsonObject images = new JsonObject();
        Path pov = Path.of("previews/vision/pov.png");

        // An older thought: a prayer answered with a look and a blessing.
        MindTrace.Thought a = MindTrace.begin("main", 41, "Ashwyn prays: grant me light in these caves", "claude-sonnet-5-5",
                "medium", "a prayer from a favored mortal", perception("Ashwyn prays: grant me light in these caves"));
        a.prompts(system, "## Your chronicle\nThe mortals raised a temple of deepslate by the river.", tools);
        MindTrace.Request r1 = a.request(1, "claude-sonnet-5-5", "medium");
        a.firstToken(r1);
        stream(a, "thinking", 1, "Ashwyn has been faithful — three offerings this week. Let me look at where they are "
                + "before deciding how to answer.");
        MindTrace.Item look = a.tool(1, "look", "{\"target\":\"Ashwyn\",\"view\":\"pov\"}");
        byte[] png = Files.exists(pov) ? Files.readAllBytes(pov) : null;
        a.result(look, "Ashwyn's view: a narrow cave, lava to the east, two zombies 12 blocks north.", false, png);
        if (png != null) images.addProperty(String.valueOf(look.id), "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
        a.usage(r1, 2310, 412, 18_940, 0, 0, 0.0187, "tool_use", true, null);
        MindTrace.Request r2 = a.request(2, "claude-sonnet-5-5", "medium");
        a.firstToken(r2);
        stream(a, "thinking", 2, "Dark and dangerous. Night vision and a few torches, and a warning about the lava.");
        a.result(a.tool(2, "bless", "{\"player\":\"Ashwyn\",\"effect\":\"night_vision\",\"seconds\":600}"),
                "Ashwyn has night vision for 10 minutes.", false, null);
        a.result(a.tool(2, "give", "{\"player\":\"Ashwyn\",\"item\":\"torch\",\"amount\":16}"), "Gave 16 torch to Ashwyn.", false, null);
        a.note("interject", 3, null, "[While you were acting, mortals addressed you. Answer each of them now in words, "
                + "even briefly.]\nPrayer: [2s ago] Bram: what about me??");
        a.result(a.tool(2, "whisper", "{\"player\":\"Ashwyn\",\"message\":\"Walk in my light, child. Mind the fire to the east.\"}"),
                "Whispered to Ashwyn.", false, null);
        a.usage(r2, 640, 233, 21_250, 1_410, 0, 0.0121, "tool_use", true, null);
        MindTrace.Request r3 = a.request(3, "claude-sonnet-5-5", "medium");
        stream(a, "text", 3, "");
        a.result(a.tool(3, "vessel_say", "{\"text\":\"Patience, Bram. The impatient are heard last.\",\"tone\":\"stern\"}"),
                "The vessel spoke.", false, null);
        a.usage(r3, 220, 96, 22_660, 0, 0, 0.0081, "end_turn", true, null);
        a.end("done: 4 act(s), first after 2140 ms, 9820 ms");
        String chron = "## Your chronicle\nThe mortals raised a temple of deepslate by the river.";
        String powers = GSON.toJson(com.google.gson.JsonParser.parseString(
                ObjectMappers.jsonMapper().writeValueAsString(ToolRegistry.sdkTools())));
        String b64 = png == null ? "iVBORw0KGgo" + "A".repeat(4000) : Base64.getEncoder().encodeToString(png);
        JsonArray conv = new JsonArray();
        conv.add(msg("user", text(a.perception)));
        a.requestBody(r1, body("claude-sonnet-5-5", "medium", system, chron, powers, conv), List.of("server-side-fallback-2026-07-01"));
        JsonArray turn1 = arr(thinking("Ashwyn has been faithful — three offerings this week. Let me look at where they are "
                + "before deciding how to answer."), toolUse("toolu_01", "look", "{\"target\":\"Ashwyn\",\"view\":\"pov\"}"));
        a.responseBody(r1, reply(turn1, "tool_use", 2310, 412, 18_940, 0));
        conv.add(msg("assistant", turn1));
        JsonObject img = new JsonObject();
        img.addProperty("type", "image");
        JsonObject src = new JsonObject();
        src.addProperty("type", "base64");
        src.addProperty("media_type", "image/png");
        src.addProperty("data", b64);
        img.add("source", src);
        conv.add(msg("user", arr(toolResult("toolu_01", arr(text("Ashwyn's view: a narrow cave, lava to the east, two zombies 12 blocks north.").get(0), img)))));
        a.requestBody(r2, body("claude-sonnet-5-5", "medium", system, chron, powers, conv), List.of("server-side-fallback-2026-07-01"));
        JsonArray turn2 = arr(thinking("Dark and dangerous. Night vision and a few torches, and a warning about the lava."),
                toolUse("toolu_02", "bless", "{\"player\":\"Ashwyn\",\"effect\":\"night_vision\",\"seconds\":600}"),
                toolUse("toolu_03", "give", "{\"player\":\"Ashwyn\",\"item\":\"torch\",\"amount\":16}"),
                toolUse("toolu_04", "whisper", "{\"player\":\"Ashwyn\",\"message\":\"Walk in my light, child. Mind the fire to the east.\"}"));
        a.responseBody(r2, reply(turn2, "tool_use", 640, 233, 21_250, 1_410));
        conv.add(msg("assistant", turn2));
        JsonArray results = arr(toolResult("toolu_02", text("Ashwyn has night vision for 10 minutes.")),
                toolResult("toolu_03", text("Gave 16 torch to Ashwyn.")), toolResult("toolu_04", text("Whispered to Ashwyn.")));
        results.add(text("[While you were acting, mortals addressed you. Answer each of them now in words, even briefly.]\n"
                + "Prayer: [2s ago] Bram: what about me??").get(0));
        conv.add(msg("user", results));
        a.requestBody(r3, body("claude-sonnet-5-5", "medium", system, chron, powers, conv), List.of("server-side-fallback-2026-07-01"));
        a.responseBody(r3, reply(arr(toolUse("toolu_05", "vessel_say", "{\"text\":\"Patience, Bram. The impatient are heard last.\",\"tone\":\"stern\"}")),
                "end_turn", 220, 96, 22_660, 0));

        // A failed tool and a pressed command.
        MindTrace.Thought b = MindTrace.begin("main", 42, "the server operator gave you binding command #7", "claude-sonnet-5-5",
                "high", "a binding command is open", perception("(no new events)"));
        b.prompts(system, "## Your chronicle\nThe mortals raised a temple of deepslate by the river. Ashwyn was "
                + "given light in the caves.", tools);
        MindTrace.Request b1 = b.request(1, "claude-sonnet-5-5", "high");
        stream(b, "thinking", 1, "Command #7: build a lighthouse at the spawn harbour. The build power needs a clear site.");
        b.result(b.tool(1, "build", "{\"design\":\"lighthouse\",\"at\":\"spawn\",\"size\":\"large\"}"),
                "Refused: the site overlaps Bram's house. Pick another place or a smaller size.", true, null);
        b.usage(b1, 1880, 301, 19_102, 2_024, 0, 0.0221, "end_turn", true, null);
        b.note("note", 2, "pressed to finish", "Pressed to finish binding command #7");
        MindTrace.Request b2 = b.request(2, "claude-sonnet-5-5", "high");
        b.result(b.tool(2, "build", "{\"design\":\"lighthouse\",\"at\":\"-120 64 310\",\"size\":\"medium\"}"),
                "Building lighthouse (1,284 blocks) at -120 64 310; done in about 30 s.", false, null);
        b.result(b.tool(2, "command_done", "{\"number\":7,\"report\":\"A lighthouse stands on the cliff west of the harbour.\"}"),
                "Command #7 closed; the operator was told.", false, null);
        b.usage(b2, 410, 188, 21_400, 0, 0, 0.0098, "end_turn", true, null);
        b.end("done: 3 act(s), first after 3010 ms, 14200 ms");

        // The live one: still thinking.
        MindTrace.Thought c = MindTrace.begin("main", 43, "Bram said your name in chat", "claude-sonnet-5-5", "low",
                "speech naming the god", perception("[1s ago] Bram: Ixthariel is a fraud"));
        c.prompts(system, "## Your chronicle\nThe mortals raised a temple of deepslate by the river. Ashwyn was "
                + "given light in the caves. A lighthouse was raised west of the harbour.", tools);
        MindTrace.Request c1 = c.request(1, "claude-sonnet-5-5", "low");
        c.firstToken(c1);
        MindTrace.Item live = c.open("thinking", 1);
        c.append(live, "A fraud, he says. Bram's favor is already -4. A lightning strike beside him — not on him — "
                + "and a word he will remember");

        // The second god.
        MindTrace.Thought d = MindTrace.begin("quorrun", 17, "a quiet moment; nothing demands you, but you may act on your own whims",
                "claude-haiku-5-5", "low", "idle musing", perception("(no new events)"));
        d.prompts("You are Quorrun, the Drowned Bell...", "## Your chronicle\nThe sea took the old pier.", tools);
        MindTrace.Request d1 = d.request(1, "claude-haiku-5-5", "low");
        stream(d, "thinking", 1, "The tide is low. A good night for the bell to ring once, far out at sea.");
        d.result(d.tool(1, "particles", "{\"shape\":\"ring\",\"at\":\"vessel\",\"particle\":\"bubble\"}"), "A ring of bubbles rose.", false, null);
        d.usage(d1, 900, 120, 8_100, 0, 0, 0.0021, "end_turn", true, null);
        d.end("done: 1 act(s), first after 1400 ms, 3100 ms");

        JsonObject thoughts = new JsonObject();
        thoughts.addProperty("rev", MindTrace.revision());
        JsonArray all = new JsonArray();
        all.addAll(TraceJson.thoughts("main", MindTrace.god("main").thoughts()));
        all.addAll(TraceJson.thoughts("quorrun", MindTrace.god("quorrun").thoughts()));
        thoughts.add("thoughts", all);

        JsonObject sample = new JsonObject();
        sample.add("/api/gods", gods(now));
        sample.add("/api/thoughts", thoughts);
        JsonObject blobs = new JsonObject();
        for (MindTrace.Thought t : List.of(a, b, c, d)) {
            List<String> hashes = new java.util.ArrayList<>(List.of(t.system, t.chronicle, t.tools));
            for (MindTrace.Request q : t.requests()) {
                hashes.addAll(List.of(q.params, q.system, q.tools, q.messages, q.response));
            }
            for (String h : hashes) {
                if (h == null || h.isEmpty()) continue;
                JsonObject o = new JsonObject();
                o.addProperty("text", MindTrace.blobText(h));
                sample.add("/api/blob?h=" + h, o);
            }
        }
        sample.add("/api/memory", memory(p, now));
        JsonObject prompt = new JsonObject();
        prompt.addProperty("system", system);
        prompt.addProperty("chronicle", "## Your chronicle\nThe mortals raised a temple of deepslate by the river.");
        prompt.addProperty("briefing", "This is a survival server for friends. Be dramatic but fair.");
        prompt.addProperty("born", true);
        sample.add("/api/prompt", prompt);
        sample.add("/api/upcoming", upcoming(now));
        sample.add("/api/events", events(now));
        sample.add("images", images);

        String page = new String(MindViewer.loadPage(), StandardCharsets.UTF_8).replace("__CSRF__", "sample")
                .replace("<!--SAMPLE-->", "<script>window.__SAMPLE__=" + GSON.toJson(sample).replace("</", "<\\/")
                        + ";</script>");
        Path out = Path.of("build/test-renders/viewer.html");
        Files.createDirectories(out.getParent());
        Files.writeString(out, page, StandardCharsets.UTF_8);
        assertTrue(Files.size(out) > 10_000);
    }

    /** A request body shaped like GodBrain.params(): cached rules, chronicle, tools, messages. */
    private static String body(String model, String effort, String system, String chronicle, String tools, JsonArray messages) {
        JsonObject o = new JsonObject();
        o.addProperty("model", model);
        o.addProperty("max_tokens", 16000);
        JsonArray sys = new JsonArray();
        JsonObject s0 = text(system).get(0).getAsJsonObject();
        JsonObject cc = new JsonObject();
        cc.addProperty("type", "ephemeral");
        s0.add("cache_control", cc);
        sys.add(s0);
        sys.add(text(chronicle).get(0));
        o.add("system", sys);
        o.add("tools", com.google.gson.JsonParser.parseString(tools));
        o.add("messages", messages);
        JsonObject th = new JsonObject();
        th.addProperty("type", "adaptive");
        th.addProperty("display", "summarized");
        o.add("thinking", th);
        JsonObject oc = new JsonObject();
        oc.addProperty("effort", effort);
        o.add("output_config", oc);
        o.add("cache_control", cc.deepCopy());
        o.addProperty("fallbacks", "default");
        return o.toString();
    }

    private static String reply(JsonArray content, String stop, long in, long out, long cr, long cw) {
        JsonObject o = new JsonObject();
        o.addProperty("id", "msg_sample");
        o.addProperty("type", "message");
        o.addProperty("role", "assistant");
        o.addProperty("model", "claude-sonnet-5-5");
        o.add("content", content);
        o.addProperty("stop_reason", stop);
        JsonObject u = new JsonObject();
        u.addProperty("input_tokens", in);
        u.addProperty("output_tokens", out);
        u.addProperty("cache_read_input_tokens", cr);
        u.addProperty("cache_creation_input_tokens", cw);
        o.add("usage", u);
        return o.toString();
    }

    private static JsonObject msg(String role, JsonArray content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.add("content", content);
        return o;
    }

    private static JsonArray arr(com.google.gson.JsonElement... items) {
        JsonArray a = new JsonArray();
        for (com.google.gson.JsonElement e : items) a.add(e);
        return a;
    }

    private static JsonArray text(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "text");
        o.addProperty("text", t);
        return arr(o);
    }

    private static JsonObject thinking(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "thinking");
        o.addProperty("thinking", t);
        o.addProperty("signature", "EqQBCkgIBRABGAIiQL" + "x".repeat(300));
        return o;
    }

    private static JsonObject toolUse(String id, String name, String input) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "tool_use");
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.add("input", com.google.gson.JsonParser.parseString(input));
        return o;
    }

    private static JsonObject toolResult(String id, JsonArray content) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "tool_result");
        o.addProperty("tool_use_id", id);
        o.add("content", content);
        return o;
    }

    /** World events as the feed would record them: who got each one and which thought took it. */
    private static JsonObject events(long now) {
        EventFeed.clear();
        Object[][] rows = {
                { "event", "Corin harvested 32 wheat", null, 400_000L, new String[] { "main:seen:41", "quorrun:seen:17" } },
                { "event", "A zombie killed a villager near the harbour", null, 380_000L, new String[] { "main:seen:41", "quorrun:seen:17" } },
                { "prayer", "Ashwyn prays: grant me light in these caves", "Ashwyn prays: grant me light in these caves", 300_000L, new String[] { "main:seen:41" } },
                { "prayer", "Bram: what about me??", "Bram prays", 290_000L, new String[] { "main:heard:41" } },
                { "directive", "Binding command #7 from the console: build a lighthouse at the spawn harbour", null, 200_000L, new String[] { "main:seen:42" } },
                { "event", "Bram broke 4 cobblestone", null, 150_000L, new String[] { "main:merged:0", "quorrun:merged:0" } },
                { "event", "Sylvaeth's vessel was struck by the Godslayer", null, 120_000L, new String[] { "emberwake:dropped:0" } },
                { "speech", "Bram: Ixthariel is a fraud", "Bram said your name in chat", 2_000L, new String[] { "main:seen:43", "quorrun:pending:0" } },
                { "event", "A creeper exploded near Corin's farm", null, 4_000L, new String[] { "main:pending:0", "quorrun:pending:0" } },
                { "speech", "Bram: told you, nothing happens", null, 2_000L, new String[] { "main:pending:0", "quorrun:pending:0" } } };
        net.bigyous.gptgodmc.loggables.Loggable firstStone = null;
        for (Object[] r : rows) {
            Object group = EventFeed.openGroup();
            String cat = (String) r[0];
            for (String spec : (String[]) r[4]) {
                String[] g = spec.split(":");
                EventFeedTest.Ev e = new EventFeedTest.Ev((String) r[1],
                        net.bigyous.gptgodmc.loggables.Loggable.Category.valueOf(cat.toUpperCase()), (String) r[2]);
                boolean merged = g[1].equals("merged");
                if (merged && firstStone == null) {
                    firstStone = new EventFeedTest.Ev("Bram broke 2 cobblestone", e.cat(), null);
                }
                EventFeed.logged(g[0], e, merged ? firstStone : null, e.text(), now - (Long) r[3]);
                switch (g[1]) {
                    case "seen" -> EventFeed.seen(List.of(e), Integer.parseInt(g[2]), 0, false);
                    case "heard" -> EventFeed.seen(List.of(e), Integer.parseInt(g[2]), 0, true);
                    case "dropped" -> EventFeed.dropped(e);
                    default -> { }
                }
            }
            EventFeed.closeGroup(group);
        }
        return EventFeed.since(0, 100);
    }

    private static void stream(MindTrace.Thought t, String kind, int step, String text) {
        MindTrace.Item it = t.open(kind, step);
        for (String w : text.split("(?<= )")) t.append(it, w);
        t.close(it);
    }

    private static String perception(String news) {
        return "# Perception — day 14, dusk (18:40)\nYou looked because: " + news + "\n\n## The world\nOverworld, clear, "
                + "3 mortals online. Spawn harbour at -80 64 300.\n\n## Your vessel\nFormed (radiant, lordly) hovering "
                + "over the temple at -42 72 288, mood calm.\n\n## Mortals here\n- Ashwyn (favor +6, \"the Faithful\"): "
                + "in a cave at -12 31 340, health 14/20, holding a stone pickaxe.\n- Bram (favor -4): at the harbour, "
                + "fishing.\n- Corin (favor 0): building a farm north of spawn.\n\n## Since you last looked\n" + news
                + "\n[40s ago] Corin harvested 32 wheat\n[1m ago] A zombie killed a villager near the harbour\n\n"
                + "## Your heart\nmild joy (22), faint wrath (9)\n\n## Your purpose\n- g3 \"A lighthouse for the harbour\" "
                + "40%: untouched 12 min";
    }

    private static Persona persona() {
        Persona p = new Persona();
        p.name = "Ixthariel";
        p.epithet = "the Ember-Crowned";
        p.archetype = "stern";
        p.temperament = "proud and watchful";
        p.nature = "An ancient fire-god who rewards devotion and punishes insolence, slow to anger but terrible in it.";
        p.speech = "Formal and archaic, short sentences, never jokes about itself.";
        p.likes = List.of("offerings of gold", "builders", "courage");
        p.dislikes = List.of("mockery", "cowardice", "waste");
        for (String d : Persona.DIALS) p.dials.put(d, 5);
        p.dials.put("wrath", 7);
        p.dials.put("pride", 8);
        p.dials.put("humor", 2);
        return p;
    }

    private static JsonObject gods(long now) {
        JsonArray arr = new JsonArray();
        arr.add(god("main", "Ixthariel", "Ixthariel", "the Ember-Crowned", "stern", "supreme", "claude-sonnet-5-5", "auto",
                "thinking", 43, 3, "Bram said your name in chat", true, 1, now));
        arr.add(god("quorrun", "Quorrun", "Quorrun", "the Drowned Bell", "mystic", "greater", "claude-haiku-5-5", "low",
                "awake", 17, 0, null, false, 0, now));
        arr.add(god("emberwake", "Sylvaeth", "Sylvaeth", "Who Walks the Ash", "trickster", "lesser", "claude-haiku-5-5", "low",
                "slain", 5, 2, null, false, 0, now));
        JsonObject o = new JsonObject();
        o.add("gods", arr);
        o.addProperty("rev", MindTrace.revision());
        o.addProperty("now", now);
        o.addProperty("mode", "live");
        o.addProperty("brainEnabled", true);
        o.addProperty("keyConfigured", true);
        return o;
    }

    private static JsonObject god(String id, String name, String full, String epithet, String archetype, String power,
            String model, String effort, String state, int thoughts, int pending, String why, boolean urgent, int cmds,
            long now) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("fullName", full);
        o.addProperty("epithet", epithet);
        o.addProperty("archetype", archetype);
        o.addProperty("temperament", "proud and watchful");
        o.addProperty("host", "main".equals(id));
        o.addProperty("model", model);
        o.addProperty("effort", effort);
        o.addProperty("power", power);
        o.addProperty("state", state);
        o.addProperty("awake", !"asleep".equals(state));
        o.addProperty("paused", false);
        o.addProperty("slain", "slain".equals(state));
        o.addProperty("thinking", "thinking".equals(state));
        o.addProperty("thoughts", thoughts);
        o.addProperty("pendingEvents", pending);
        o.addProperty("players", 3);
        o.addProperty("vessel", "main".equals(id) ? "manifest" : "formless");
        o.addProperty("openCommands", cmds);
        o.addProperty("lastThoughtEnded", now - 40_000);
        if (why != null) {
            JsonObject a = new JsonObject();
            a.addProperty("at", now - 2000);
            a.addProperty("reason", why);
            a.addProperty("urgent", urgent);
            o.add("attention", a);
        }
        return o;
    }

    private static JsonObject memory(Persona p, long now) {
        Memory m = new Memory();
        m.chronicle = "In the first days the god woke above a river valley. The mortals raised a temple of deepslate by "
                + "the river and brought it gold. Bram mocked it at the harbour and was struck by lightning; Ashwyn has "
                + "been its most faithful servant.";
        m.setFavor("Ashwyn", 6, null);
        m.remember("Ashwyn", "Faithful; three offerings this week. Deserves a boon soon.");
        m.remember("Ashwyn", "Afraid of the deep caves.");
        m.addDeed("Ashwyn", "offered 12 gold ingots at the temple", now - 3 * 86_400_000L);
        m.addDeed("Ashwyn", "prayed for light in the caves", now - 60_000);
        m.subject("Ashwyn").title = "the Faithful";
        m.subject("Ashwyn").titleColor = "gold";
        m.subject("Ashwyn").lastSeen = now - 30_000;
        m.subject("Ashwyn").summary = "A quiet miner who built the first altar and has never once asked for riches.";
        m.setFavor("Bram", -4, null);
        m.remember("Bram", "Insolent. Called me a fraud twice.");
        m.addDeed("Bram", "mocked the god in chat", now - 7_200_000L);
        m.addDeed("Bram", "was struck by lightning for insolence", now - 7_100_000L);
        m.subject("Bram").lastSeen = now - 5_000;
        m.setFavor("Corin", 1, null);
        m.addDeed("Corin", "built a wheat farm north of spawn", now - 900_000);
        m.subject("Corin").lastSeen = now - 60_000;
        m.commandments.add("Thou shalt not waste what the earth gives.");
        m.commandments.add("Bring gold to the temple at each full moon.");
        Purpose.set(m, "A lighthouse for the harbour", "Light the way for the fishers.", null, false, 5, now - 3_600_000L);
        Purpose.set(m, "Gather 64 gold at the temple", "Mortals bring gold; the god rewards the most generous.",
                "a crown of fire for the most generous", true, 5, now - 7_000_000L);
        m.goals.get(0).progress = 40;
        m.goals.get(1).progress = 65;
        m.heart.feel(Heart.Feeling.JOY, 22, "Ashwyn's offering", now - 600_000);
        m.heart.feel(Heart.Feeling.WRATH, 9, "Bram called it a fraud", now - 10_000);
        m.addQuest("Light the old mine", "Place 20 torches in the abandoned mine.", "Ashwyn", 60);
        for (int i = 0; i < 4; i++) {
            Memory.CycleRecord r = new Memory.CycleRecord();
            r.at = now - (4 - i) * 300_000L;
            r.trigger = i % 2 == 0 ? "a prayer" : "your regular watch over the world";
            r.thought = "Considered the mortals; rewarded the faithful.";
            r.acts.add("whisper Ashwyn");
            m.recent.add(r);
        }
        JsonObject o = new JsonObject();
        o.add("memory", GSON.toJsonTree(m));
        o.add("heartSummary", GSON.toJsonTree(m.heart.summary(now)));
        o.addProperty("heartText", m.heart.describe(now));
        JsonArray edicts = new JsonArray();
        JsonObject e = new JsonObject();
        e.addProperty("number", 8);
        e.addProperty("text", "Give every player a loaf of bread at dawn.");
        e.addProperty("issuer", "Jumpathy (God Manager)");
        e.addProperty("issuedAt", now - 90_000);
        e.addProperty("thoughts", 1);
        e.addProperty("standing", false);
        edicts.add(e);
        o.add("edicts", edicts);
        o.addProperty("retries", 3);
        o.add("persona", GSON.toJsonTree(p));
        return o;
    }

    private static JsonObject upcoming(long now) {
        JsonObject o = new JsonObject();
        o.add("events", GSON.toJsonTree(List.of("[12s ago] Corin placed 14 hay bales", "[4s ago] A creeper exploded near Corin's farm")));
        o.add("speech", GSON.toJsonTree(List.of("[2s ago] Bram: told you, nothing happens")));
        o.add("prayers", GSON.toJsonTree(List.of()));
        o.add("directives", GSON.toJsonTree(List.of()));
        o.addProperty("omitted", 0);
        JsonArray journal = new JsonArray();
        String[][] rows = { { "cycle", "Thinking (Bram said your name in chat) · effort low: speech naming the god" },
                { "thought", "A fraud, he says." }, { "tool", "smite{\"player\":\"Bram\",\"near\":true}" },
                { "speech", "Ixthariel → Bram: Mind your tongue, fisherman." }, { "error", "Could not reach Claude (timeout); retrying in 30s." } };
        for (int i = 0; i < rows.length; i++) {
            JsonObject j = new JsonObject();
            j.addProperty("seq", i);
            j.addProperty("at", now - (rows.length - i) * 20_000L);
            j.addProperty("kind", rows[i][0]);
            j.addProperty("text", rows[i][1]);
            journal.add(j);
        }
        o.add("journal", journal);
        JsonArray annals = new JsonArray();
        JsonObject an = new JsonObject();
        an.addProperty("at", now - 60_000);
        an.addProperty("kind", "prayer");
        an.addProperty("text", "Ashwyn prayed for light in the caves");
        annals.add(an);
        o.add("annals", annals);
        return o;
    }
}
