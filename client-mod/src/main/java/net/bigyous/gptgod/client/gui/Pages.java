package net.bigyous.gptgod.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.bigyous.gptgod.client.gui.GuiProtocol.God;

/** What the pages share: god heads, state words and colours, hover cards, feedback lines, money. */
final class Pages {
    private Pages() {
    }

    static final String[] FEELINGS = { "Wrath", "Joy", "Sorrow", "Dread", "Glory" };
    static final int[] FEELING_COLORS = { 0xFF4A3A, 0xFFD24A, 0x6FA8FF, 0xA066E0, 0xFFF0A0 };

    static int moodColor(String mood) {
        return switch (mood == null ? "" : mood) {
            case "wrathful" -> 0xFF4A3A;
            case "joyful" -> 0xFFD24A;
            case "sorrowful" -> 0x8FB6FF;
            case "ominous" -> 0xA066E0;
            case "exalted" -> 0xFFF4C0;
            default -> 0x8FD6A8;
        };
    }

    static int tierColor(String tier) {
        return switch (tier == null ? "" : tier) {
            case "despised" -> 0xFFE0354A;
            case "out of favor" -> 0xFFFF8A3A;
            case "favored" -> 0xFFFFC23A;
            case "chosen" -> 0xFFFFF2B0;
            default -> 0xFFD0D0DA;
        };
    }

    static String stateWord(God g) {
        if (g.state().equals("slain")) return "slain";
        if (g.state().equals("off")) return "turned off";
        if (g.thinking()) return "thinking…";
        return g.state();
    }

    static int stateColor(God g, long now) {
        if (g.state().equals("slain")) return Ui.BAD;
        if (g.state().equals("off")) return Ui.FAINT;
        if (g.thinking()) return Ui.mix(Ui.ACCENT, 0xFFFFFFFF, 0.3f + 0.3f * (float) Math.sin(now / 250.0));
        return switch (g.state()) {
            case "awake" -> Ui.OK;
            case "paused" -> Ui.WARN;
            default -> Ui.DIM;
        };
    }

    static String bodyWord(String body) {
        return switch (body) {
            case "walks" -> "walks the world";
            case "true_form" -> "true form revealed";
            default -> "formless";
        };
    }

    static String title(String key) {
        if (key == null || key.isEmpty()) return "";
        return Character.toUpperCase(key.charAt(0)) + key.substring(1).replace('_', ' ').replace('-', ' ');
    }

    /** "claude-sonnet-5-5" → "sonnet 5.5". */
    static String model(String id) {
        if (id == null) return "?";
        return id.replace("claude-", "").replaceFirst("^([a-z]+)-(\\d+)-(\\d+)$", "$1 $2.$3");
    }

    /**
     * A god's head, `px` screen pixels per skin pixel, inside a ring in its mood's colour. Before the
     * plugin has sent the image: a plain tile in the god's colour with its initial.
     */
    static void head(ManagerScreen s, God g, int x, int y, int px) {
        Ui ui = s.ui;
        int size = s.m.headSize * px;
        boolean slain = g.state().equals("slain");
        int ring = slain ? 0xFF5A5A62 : 0xFF000000 | moodColor(g.mood());
        ui.g.fill(x - 1, y - 1, x + size + 1, y + size + 1, ring);
        int[] pixels = s.m.heads.get(g.look());
        if (pixels != null) {
            ui.image(pixels, s.m.headSize, x, y, px);
            if (slain || g.state().equals("off")) ui.g.fill(x, y, x + size, y + size, 0xA0101014);
        } else {
            ui.g.fill(x, y, x + size, y + size, Ui.mix(0xFF101014, 0xFF000000 | g.color(), 0.5f));
            String initial = g.label().substring(0, 1);
            ui.text(initial, x + (size - ui.width(initial)) / 2, y + (size - 8) / 2, 0xFFFFFFFF);
        }
    }

    /** The hover card: who the god is and how it fares right now. */
    static List<String> card(God g) {
        List<String> out = new ArrayList<>();
        out.add("§a" + g.label());
        if (g.epithet() != null) out.add("§d" + g.epithet());
        out.add((g.archetype() == null ? "No archetype yet" : title(g.archetype())) + " · " + g.power() + " god");
        out.add("State: " + stateWord(g) + (g.thinking() && !g.state().equals("awake") ? "" : ""));
        out.add("Mood: " + g.mood());
        out.add("Body: " + bodyWord(g.body()));
        StringBuilder heart = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (g.heart()[i] >= 10) heart.append(heart.length() > 0 ? ", " : "").append(FEELINGS[i].toLowerCase(Locale.ROOT))
                    .append(' ').append(Math.round(g.heart()[i]));
        }
        out.add("Heart: " + (heart.length() == 0 ? "at peace" : heart));
        if (g.pending() > 0) out.add(g.pending() + " command" + (g.pending() == 1 ? "" : "s") + " pending");
        if (g.wounded()) out.add("§bHealth " + Math.round(g.health()) + "/" + Math.round(g.max()));
        out.add(g.state().equals("slain") ? "§dClick to manage or revive it" : "§dClick to manage");
        return out;
    }

    /**
     * The answer to the last action at this slot: dots while waiting, then the server's words (green, or
     * red for a refusal). Returns the height used (0 when there is nothing to show).
     */
    static int feedback(ManagerScreen s, String slot, int x, int y, int w, int maxLines) {
        Ui ui = s.ui;
        if (s.m.busy(slot) && s.m.feedback(slot, ui.now) == null) {
            ui.small("·".repeat(1 + (int) (ui.now / 250 % 3)), x, y, Ui.DIM);
            return 8;
        }
        GuiModel.Feedback f = s.m.feedback(slot, ui.now);
        if (f == null || f.text() == null || f.text().isBlank()) return 0;
        if (f.ok() && ui.now - f.at() > 15_000) return 0;
        return ui.wrapSmall(f.text(), x, y, w, f.ok() ? Ui.OK : Ui.BAD, maxLines);
    }

    /** An error under one input, in red. Returns the height used. */
    static int fieldError(ManagerScreen s, String slot, String field, int x, int y, int w) {
        String e = s.m.error(slot, field, s.ui.now);
        return e == null ? 0 : s.ui.wrapSmall(e, x, y, w, Ui.BAD, 3);
    }

    static String usd(double v) {
        if (Double.isNaN(v)) return "?";
        if (v == 0) return "$0.00";
        return v >= 100 ? String.format(Locale.ROOT, "$%.0f", v)
                : v >= 1 ? String.format(Locale.ROOT, "$%.2f", v) : String.format(Locale.ROOT, "$%.4f", v);
    }

    static String count(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 10_000) return String.format(Locale.ROOT, "%.0fk", n / 1000.0);
        return String.format(Locale.ROOT, "%,d", n);
    }

    static String clock(long time) {
        java.time.LocalTime t = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        return String.format(Locale.ROOT, "%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
    }

    static void waiting(Ui ui, int x, int y) {
        ui.text("Waiting for the server" + ".".repeat((int) (ui.now / 400 % 4)), x, y, Ui.DIM);
    }
}
