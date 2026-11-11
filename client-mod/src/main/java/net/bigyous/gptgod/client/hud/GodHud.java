package net.bigyous.gptgod.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.bigyous.gptgod.client.GodClientMod;
import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.net.Protocol;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The god panel: the god of your world (name, epithet, mood, heart, wounds, state), your standing with it
 * (favor tier, title, mark) and its announced mission. H cycles full / compact / hidden.
 */
public final class GodHud {
    private GodHud() {
    }

    static final Identifier GLOW = GodClientMod.id("textures/gui/glow.png");
    static final int WIDTH = 158;
    static final String[] FEELINGS = { "Wrath", "Joy", "Sorrow", "Dread", "Glory" };
    static final int[] FEELING_COLORS = { 0xFF4A3A, 0xFFD24A, 0x6FA8FF, 0xA066E0, 0xFFF0A0 };

    public static int moodColor(String mood) {
        return switch (mood == null ? "" : mood) {
            case "wrathful" -> 0xFF4A3A;
            case "joyful" -> 0xFFD24A;
            case "sorrowful" -> 0x8FB6FF;
            case "ominous" -> 0xA066E0;
            case "exalted" -> 0xFFF4C0;
            default -> 0xC9B8FF;
        };
    }

    public static int tierColor(String tier) {
        return switch (tier == null ? "" : tier) {
            case "despised" -> 0xC0162A;
            case "out of favor" -> 0xFF8A3A;
            case "favored" -> 0xFFC23A;
            case "chosen" -> 0xFFF2B0;
            default -> 0xB8B0C8;
        };
    }

    static String stateWord(String state) {
        return switch (state == null ? "" : state) {
            case "walks" -> "walks among you";
            case "watches" -> "watches";
            case "true_form" -> "TRUE FORM";
            case "slain" -> "SLAIN";
            case "sleeps" -> "sleeps";
            case "silent" -> "is silent";
            case "unborn" -> "unborn";
            default -> state;
        };
    }

    static String cap(String s) {
        return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.player == null) return;
        ModConfig cfg = GodClientMod.config;
        if (!GodClientMod.STATE.has("hud")) return;
        Protocol.God god = GodClientMod.STATE.main();
        if (god == null) return;
        long now = System.currentTimeMillis();
        Protocol.Standing standing = GodClientMod.STATE.standing(god.realm());
        Protocol.Quest quest = GodClientMod.STATE.quest(god.realm());
        // What is worth a glance: when any of this changes, the chip shows for a few seconds.
        String news = god.realm() + '|' + god.mood() + '|' + god.state() + '|' + (god.wounded() ? Math.round(god.health()) : -1)
                + '|' + (standing == null ? "" : standing.tier() + standing.title() + standing.mark()) + '|'
                + (quest == null ? "" : quest.title());
        if (!news.equals(lastNews)) {
            lastNews = news;
            chipUntil = now + CHIP_MS;
        }
        boolean held = GodClientMod.hudKey != null && GodClientMod.hudKey.isDown() && mc.gui.screen() == null;
        if (held && cfg.hud != ModConfig.HudMode.HIDDEN || cfg.hud == ModConfig.HudMode.FULL) {
            drawPanel(g, mc.font, god, standing, quest, false, cfg.corner, now);
        } else if (cfg.hud == ModConfig.HudMode.COMPACT) {
            drawChip(g, mc.font, god, quest, cfg.corner, now, 1f);
        } else if (cfg.hud == ModConfig.HudMode.AUTO && now < chipUntil) {
            long left = chipUntil - now;
            drawChip(g, mc.font, god, quest, cfg.corner, now, Math.min(1f, Math.min(left, CHIP_MS - left + 200) / 400f));
        }
    }

    static final long CHIP_MS = 7000;
    private static String lastNews = "";
    private static long chipUntil;

    /** One small line: the god's name, its mood, and its mission's progress. About 14 pixels tall. */
    public static void drawChip(GuiGraphicsExtractor g, Font font, Protocol.God god, Protocol.Quest quest,
            ModConfig.Corner corner, long now, float fade) {
        if (fade <= 0.02f) return;
        boolean slain = "slain".equals(god.state());
        int mood = moodColor(god.mood());
        String name = god.name() == null ? "The god" : god.name();
        String word = slain ? "slain" : god.thinking() ? "thinking" : god.mood();
        String tail = quest != null && quest.title() != null ? "  " + quest.progress() + "%" : "";
        int w = 8 + font.width("✦ " + name) + 6 + (int) ((font.width(word) + font.width(tail)) * 0.75f) + 6;
        int h = 13;
        int x = corner == ModConfig.Corner.TOP_LEFT ? 4 : g.guiWidth() - w - 4;
        int y = corner == ModConfig.Corner.BOTTOM_RIGHT ? g.guiHeight() - h - 52 : 4;
        int a = Math.round(255 * Math.max(0, Math.min(1, fade)));
        g.fill(x, y, x + w, y + h, (a * 0xA0 / 255) << 24 | 0x100A18);
        g.fill(x, y, x + 2, y + h, a << 24 | mood);
        g.text(font, "✦ " + name, x + 5, y + 3, a << 24 | (slain ? 0x8A8A90 : god.color()), true);
        if (a > 40) small(g, font, word + tail, x + 5 + font.width("✦ " + name) + 6, y + 4, a << 24 | (slain ? 0xFF4040 : mood));
    }

    /** Draws the panel for this god (also used by the demo command and the preview tests). */
    public static void drawPanel(GuiGraphicsExtractor g, Font font, Protocol.God god, Protocol.Standing standing,
            Protocol.Quest quest, boolean compact, ModConfig.Corner corner, long now) {
        int pad = 6;
        int inner = WIDTH - pad * 2;
        int godColor = god.color();
        int mood = moodColor(god.mood());
        boolean slain = "slain".equals(god.state());

        // Measure first so the panel can sit in any corner.
        List<FormattedCharSequence> questLines = new ArrayList<>();
        if (quest != null && quest.title() != null) {
            questLines = font.split(Component.literal(quest.title()), inner);
            if (questLines.size() > 3) questLines = questLines.subList(0, 3);
        }
        int h = pad + 10;
        if (!compact && god.epithet() != null) h += 8;
        h += 11; // mood line
        if (!compact) h += 5 * 7 + 3;
        if (god.wounded()) h += 12;
        if (standing != null) h += 12 + (!compact && standing.title() != null ? 0 : 0);
        if (!questLines.isEmpty()) h += 3 + 9 + questLines.size() * 9 + (compact ? 0 : 0) + 6;
        h += pad - 2;

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int x = switch (corner) {
            case TOP_LEFT -> 4;
            case TOP_RIGHT, BOTTOM_RIGHT -> sw - WIDTH - 4;
        };
        int y = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> 4;
            case BOTTOM_RIGHT -> sh - h - 52;
        };

        // Backdrop: dark glass, a border in the god's colour, an accent bar that breathes with its mood.
        float breath = 0.55f + 0.45f * (float) Math.sin(now / 700.0);
        g.fill(x, y, x + WIDTH, y + h, 0xC0100A18);
        g.fillGradient(x, y, x + WIDTH, y + 14, Overlays.argb(0.35f, godColor), 0x00000000);
        g.outline(x, y, WIDTH, h, Overlays.argb(0.55f, godColor));
        g.fill(x, y + 1, x + 2, y + h - 1, Overlays.argb(0.5f + 0.5f * breath, mood));

        int cx = x + pad;
        int cy = y + pad;

        // Name and state.
        String name = god.name() == null ? "The god" : god.name();
        g.text(font, "✦ " + name, cx, cy, 0xFF000000 | (slain ? 0x8A8A90 : godColor), true);
        String state = stateWord(god.state());
        if (god.thinking() && !slain) {
            int dots = (int) (now / 400 % 4);
            state = "thinking" + ".".repeat(dots);
        }
        small(g, font, state, x + WIDTH - pad - (int) (font.width(state) * 0.75f), cy + 2,
                slain ? 0xFFFF4040 : 0xFF9C94B0);
        cy += 10;
        if (!compact && god.epithet() != null) {
            small(g, font, god.epithet(), cx + 8, cy, 0xFFB8AEC8);
            cy += 8;
        }

        // Mood.
        String moodWord = slain ? "Fallen" : cap(god.mood());
        g.blit(RenderPipelines.GUI_TEXTURED, GLOW, cx - 1, cy - 1, 0, 0, 10, 10, 16, 16, 16, 16,
                Overlays.argb(0.6f + 0.4f * breath, mood));
        g.text(font, "◆", cx + 1, cy, 0xFF000000 | mood, false);
        g.text(font, moodWord, cx + 11, cy, 0xFF000000 | mood, true);
        cy += 11;

        // Heart.
        if (!compact) {
            float[] v = god.heart().values();
            for (int i = 0; i < 5; i++) {
                small(g, font, FEELINGS[i], cx, cy, 0xFFA89EBA);
                bar(g, cx + 34, cy + 1, inner - 34, 4, v[i] / 100f, FEELING_COLORS[i], now);
                cy += 7;
            }
            cy += 3;
        }

        // Wounds from a Godslayer.
        if (god.wounded()) {
            String hp = "♥ " + Math.round(god.health()) + "/" + Math.round(god.max());
            small(g, font, hp, cx, cy, 0xFFFF6A6A);
            bar(g, cx + 44, cy + 1, inner - 44, 5, god.health() / god.max(), 0xE0203A, now);
            cy += 12;
        }

        // Your standing.
        if (standing != null) {
            int tc = tierColor(standing.tier());
            String you = "You: " + cap(standing.tier());
            g.text(font, you, cx, cy, 0xFF000000 | tc, true);
            int tx = cx + font.width(you) + 4;
            if (standing.title() != null) {
                String t = "“" + standing.title() + "”";
                int room = x + WIDTH - pad - tx;
                if (font.width(t) * 0.75f > room) t = font.plainSubstrByWidth(t, (int) (room / 0.75f));
                small(g, font, t, tx, cy + 2, 0xFFFFD88A);
            }
            if (standing.mark() != null) {
                String m = "halo".equals(standing.mark()) ? "☀" : "☠";
                int mc = "halo".equals(standing.mark()) ? 0xFFFFF0A0 : 0xFFB040FF;
                g.text(font, m, x + WIDTH - pad - font.width(m), cy, mc, true);
            }
            cy += 12;
        }

        // The mission.
        if (!questLines.isEmpty()) {
            g.fill(cx, cy, x + WIDTH - pad, cy + 1, Overlays.argb(0.35f, godColor));
            cy += 3;
            small(g, font, name + " wills:", cx, cy, 0xFFD9A6FF);
            cy += 9;
            for (FormattedCharSequence line : questLines) {
                g.text(font, line, cx, cy, 0xFFF0E6FF, true);
                cy += 9;
            }
            bar(g, cx, cy + 1, inner, 4, quest.progress() / 100f, 0xB070FF, now);
        }
    }

    static void small(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.75f, 0.75f);
        g.text(font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** A thin bar with a dark track and a glowing tip. */
    static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float value, int color, long now) {
        value = Math.max(0, Math.min(1, value));
        g.fill(x, y, x + w, y + h, 0x80000000);
        int fw = Math.round(w * value);
        if (fw <= 0) return;
        g.fillGradient(x, y, x + fw, y + h, 0xFF000000 | color, Overlays.argb(1f, darken(color)));
        float shine = 0.6f + 0.4f * (float) Math.sin(now / 350.0 + x * 0.1);
        g.blit(RenderPipelines.GUI_TEXTURED, GLOW, x + fw - h - 1, y - h / 2 - 1, 0, 0, h * 2 + 2, h * 2 + 2, 16, 16, 16,
                16, Overlays.argb(shine, color));
    }

    static int darken(int c) {
        return (((c >> 16) & 255) * 6 / 10) << 16 | (((c >> 8) & 255) * 6 / 10) << 8 | ((c & 255) * 6 / 10);
    }
}
