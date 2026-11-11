package net.bigyous.gptgod.client.gui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The guide to the gods (feature "guide"): the plugin sends the whole guide in one "guide" message, and
 * the mod shows it in {@link GuideScreen} in place of the written book. Pure. Never throws: strings are
 * clipped, lists capped, and a guide with no chapters is no guide.
 */
public final class GuideProtocol {
    private GuideProtocol() {
    }

    public static final int MAX_BYTES = 64 * 1024;
    static final int MAX_CHAPTERS = 32, MAX_BLOCKS = 64, MAX_GODS = 8;

    /** kind: "p" (paragraph), "h" (heading) or "cmd" (cmd, then what it does in text). */
    public record Block(String kind, String cmd, String text) {
    }

    public record Chapter(String id, String title, boolean op, List<Block> blocks) {
    }

    public record God(String name, String epithet, int color, boolean slain) {
    }

    /** open = show it now (the player used the book or typed /guide). */
    public record Guide(boolean open, String title, boolean operator, List<God> gods, List<Chapter> chapters) {
        public List<Chapter> playerChapters() {
            return chapters.stream().filter(c -> !c.op()).toList();
        }

        public List<Chapter> operatorChapters() {
            return chapters.stream().filter(Chapter::op).toList();
        }
    }

    /** Whether these bytes are a guide message (cheap check before parsing). */
    public static boolean mine(byte[] data) {
        if (data == null || data.length < 12 || data.length > MAX_BYTES) return false;
        String head = new String(data, 0, Math.min(data.length, 48), StandardCharsets.UTF_8).replaceAll("\\s", "");
        return head.startsWith("{\"t\":\"guide\"");
    }

    /** The guide, or null when the bytes are not a usable one. */
    public static Guide decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) return null;
        try {
            JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            return root.isJsonObject() ? decode(root.getAsJsonObject()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Guide decode(JsonObject o) {
        try {
            if (!"guide".equals(GuiProtocol.str(o, "t", "", 16))) return null;
            List<God> gods = new ArrayList<>();
            for (JsonObject g : GuiProtocol.objects(o, "gods", MAX_GODS)) {
                String name = GuiProtocol.str(g, "name", "", 48);
                if (name.isBlank()) continue;
                gods.add(new God(name, GuiProtocol.str(g, "epithet", null, 64), GuiProtocol.color(g, "color", 0xB070FF),
                        GuiProtocol.bool(g, "slain")));
            }
            List<Chapter> chapters = new ArrayList<>();
            for (JsonObject c : GuiProtocol.objects(o, "chapters", MAX_CHAPTERS)) {
                List<Block> blocks = new ArrayList<>();
                for (JsonObject b : GuiProtocol.objects(c, "blocks", MAX_BLOCKS)) {
                    if (b.has("p")) blocks.add(new Block("p", null, GuiProtocol.str(b, "p", "", 800)));
                    else if (b.has("h")) blocks.add(new Block("h", null, GuiProtocol.str(b, "h", "", 80)));
                    else if (b.has("cmd")) {
                        blocks.add(new Block("cmd", GuiProtocol.str(b, "cmd", "", 120), GuiProtocol.str(b, "text", "", 400)));
                    }
                }
                String title = GuiProtocol.str(c, "title", "", 60);
                if (title.isBlank()) continue;
                chapters.add(new Chapter(GuiProtocol.str(c, "id", "", 32), title, GuiProtocol.bool(c, "op"),
                        List.copyOf(blocks)));
            }
            if (chapters.isEmpty()) return null;
            return new Guide(GuiProtocol.bool(o, "open"), GuiProtocol.str(o, "title", "The Book of the Gods", 48),
                    GuiProtocol.bool(o, "operator"), List.copyOf(gods), List.copyOf(chapters));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** What a command's click types into chat: up to its first placeholder ("/pray <words>" types "/pray "). */
    public static String typed(String cmd) {
        int cut = cmd.length();
        for (String mark : List.of(" <", " [", " |")) {
            int i = cmd.indexOf(mark);
            if (i >= 0) cut = Math.min(cut, i + 1);
        }
        String s = cmd.substring(0, cut);
        return s.endsWith(" ") ? s : s + " ";
    }
}
