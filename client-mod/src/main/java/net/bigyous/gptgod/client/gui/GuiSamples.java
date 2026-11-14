package net.bigyous.gptgod.client.gui;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

/**
 * Sample data for the God Manager screen with no server (/godfx manager, the screenshot test): the
 * protocol's golden files, shipped in the jar, decoded exactly like real messages. Actions are answered
 * here the way the plugin would, including a few refusals so the inline errors can be seen.
 */
public final class GuiSamples {
    private GuiSamples() {
    }

    static final List<String> FILES = List.of("gui-sample-catalog", "gui-pantheon", "gui-sample-heads", "gui-sample-heads-2", "gui-god",
            "gui-commands", "gui-mortals", "gui-mortal", "gui-journal", "gui-sample-switches", "gui-costs");

    /** Fills the model as if the plugin had just opened the screen on a server with four gods. */
    public static void fill(GuiModel model) {
        long now = System.currentTimeMillis();
        model.accept(new GuiProtocol.Open("main", true, true, true), now);
        for (String file : FILES) load(model, file, now);
        for (String tab : new ArrayList<>(model.catalog.tabs())) load(model, "gui-sample-config-" + tab, now);
        model.openMortal.put("main", "Korvath");
    }

    private static void load(GuiModel model, String name, long now) {
        try (InputStream in = GuiSamples.class.getResourceAsStream("/gptgod-samples/" + name + ".json")) {
            if (in == null) return;
            Object m = GuiProtocol.decode(in.readAllBytes());
            if (m != null) model.accept(m, now);
        } catch (IOException e) {
            // a missing sample only leaves that page empty
        }
    }

    /** What the plugin would answer, near enough to try every kind of feedback. */
    static GuiProtocol.Result answer(String id, String action, Map<String, String> args, String confirm) {
        String name = args.getOrDefault("name", "");
        return switch (action) {
            case "create_god" -> name.equalsIgnoreCase("Zeus")
                    ? fail(id, "name", "\"Zeus\" is the name of a famous god; choose an invented one.")
                    : name.contains(" ") ? fail(id, "name", "The name must be one word.")
                            : new GuiProtocol.Result(id, true, null, null, "A new god comes into being.", null, "ysmere");
            case "rebirth", "remove_god" -> "Vaelor".equalsIgnoreCase(confirm)
                    ? ok(id, "(sample) It would be done now.")
                    : new GuiProtocol.Result(id, false, null, "Type its name (Vaelor) to confirm.", null, "name", null);
            case "command" -> args.getOrDefault("text", "").isBlank() ? fail(id, "text", "Type something first.")
                    : ok(id, "Command #14 delivered: the god must obey. You will hear when it is done.");
            case "config_set" -> {
                String v = args.getOrDefault("value", "");
                yield v.matches("-?\\d+(\\.\\d+)?|true|false|[a-z._-]+") && !v.equals("9999")
                        ? ok(id, "Saved to config.yml.")
                        : fail(id, args.get("key"), "Must be between 5 and 600 s.");
            }
            case "revive" -> ok(id, "The veil tears open before you: Morthuun is being called back.");
            case "mortal_open" -> new GuiProtocol.Result(id, true, null, null, null, null, args.get("player"));
            default -> ok(id, "(sample) " + action.replace('_', ' ') + ": done.");
        };
    }

    private static GuiProtocol.Result ok(String id, String message) {
        return new GuiProtocol.Result(id, true, null, null, message, null, null);
    }

    private static GuiProtocol.Result fail(String id, String field, String error) {
        return new GuiProtocol.Result(id, false, field, error, null, null, null);
    }

    /** Only so tests can check a sample decodes. */
    static Object decode(JsonObject o) {
        return GuiProtocol.decode(o);
    }
}
