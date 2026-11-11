package net.bigyous.gptgod.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.bigyous.gptgod.client.GodClientMod;
import net.bigyous.gptgod.client.hud.SubtitleMath.Bearing;
import net.bigyous.gptgod.client.hud.SubtitleMath.Where;
import net.bigyous.gptgod.client.net.Protocol;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * The god's spoken lines as subtitles: low on the screen, in the god's colour, revealed word by word in
 * step with the voice, with a thin bar that follows its loudness and a marker toward a speaker who is
 * off screen. At most two lines at once (two gods), a few rows each; gone a moment after the voice ends.
 */
public final class Subtitles {
    private Subtitles() {
    }

    static final int MAX_LINES = 2;
    static final int MAX_ROWS = 2;
    /** Just above the hotbar, hearts and armour. */
    static final int BOTTOM_GAP = 60;

    private static final class Line {
        final Protocol.Say say;
        final long start;
        final String[] words;
        final int[] times;
        long speakMs;
        // Layout, redone when the screen width changes.
        int laidOutFor = -1;
        Component[] parts;
        int[] widths;
        int[] rows;
        int rowCount;
        int widest;
        float level;

        Line(Protocol.Say say, long start) {
            this.say = say;
            this.start = start;
            this.words = say.text().split(" +");
            this.times = SubtitleMath.revealTimes(words, say.ms());
            this.speakMs = say.ms();
        }
    }

    private static final List<Line> LINES = new ArrayList<>();

    /** A god starts a line now. A newer line from the same god replaces its older one. */
    public static void say(Protocol.Say s, long now) {
        LINES.removeIf(l -> l.say.realm().equals(s.realm()));
        while (LINES.size() >= MAX_LINES) LINES.remove(0);
        LINES.add(new Line(s, now));
        GodClientMod.VOICE.start(s.realm(), now, s.env() != null ? s.env().length * 100 : s.ms(), s.env());
    }

    /** A further part of a god's line starts playing now: the bar follows it, and the line stays up for it. */
    public static void voice(Protocol.Voice v, long now) {
        int ms = v.env() != null ? Math.max(v.ms(), v.env().length * 100) : v.ms();
        GodClientMod.VOICE.start(v.realm(), now, ms, v.env());
        for (Line l : LINES) {
            if (l.say.realm().equals(v.realm())) l.speakMs = Math.max(l.speakMs, now - l.start + v.ms());
        }
    }

    public static void clear() {
        LINES.clear();
        GodClientMod.VOICE.clear();
    }

    public static boolean showing() {
        return !LINES.isEmpty();
    }

    // ---- Look --------------------------------------------------------------------------------------

    private static float scale(String kind) {
        return switch (kind) {
            case "whisper" -> 0.75f;
            case "announce" -> 1.25f;
            default -> 1f;
        };
    }

    private static boolean heavy(String tone) {
        return "booming".equals(tone) || "commanding".equals(tone);
    }

    private static boolean dark(String tone) {
        return "demonic".equals(tone) || "abyssal".equals(tone);
    }

    private static boolean airy(String tone) {
        return "ethereal".equals(tone) || "divine".equals(tone) || "majestic".equals(tone);
    }

    /** The words' colour: the god's, lifted toward white so it reads on any ground; darker voices run red. */
    static int textColor(Protocol.Say s) {
        int c = s.color();
        if (dark(s.tone())) c = mix(c, 0xFF2A2A, 0.35f);
        return mix(c, 0xFFFFFF, "whisper".equals(s.kind()) ? 0.6f : 0.45f);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    private static String label(Protocol.Say s) {
        String name = s.name() == null ? "The god" : s.name();
        return switch (s.kind()) {
            case "whisper" -> name + " whispers";
            case "announce" -> "✦ " + name + " ✦";
            default -> name;
        };
    }

    private static void layout(Line l, Font font, int maxWidth) {
        Protocol.Say s = l.say;
        Style style = Style.EMPTY.withItalic("whisper".equals(s.kind())).withBold(heavy(s.tone()));
        l.parts = new Component[l.words.length];
        l.widths = new int[l.words.length];
        for (int i = 0; i < l.words.length; i++) {
            l.parts[i] = Component.literal(l.words[i]).withStyle(style);
            l.widths[i] = font.width(l.parts[i]);
        }
        int space = font.width(" ");
        l.rows = SubtitleMath.wrap(l.widths, space, maxWidth);
        l.rowCount = l.rows.length == 0 ? 0 : l.rows[l.rows.length - 1] + 1;
        l.widest = 0;
        for (int r = 0; r < l.rowCount; r++) l.widest = Math.max(l.widest, rowWidth(l, r, space));
    }

    private static int rowWidth(Line l, int row, int space) {
        int w = 0;
        for (int i = 0; i < l.rows.length; i++) {
            if (l.rows[i] == row) w += (w == 0 ? 0 : space) + l.widths[i];
        }
        return w;
    }

    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (LINES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        LINES.removeIf(l -> SubtitleMath.over(now - l.start, l.speakMs, SubtitleMath.lingerMs(l.say.text().length())));
        if (mc.gui.hud.isHidden() || mc.player == null || !GodClientMod.config.subtitles) return;
        int bottom = g.guiHeight() - BOTTOM_GAP;
        // The newest line sits lowest; an older one still on screen is stacked above it.
        for (int i = LINES.size() - 1; i >= 0; i--) {
            bottom = drawLine(g, mc, LINES.get(i), now, bottom) - 6;
        }
    }

    /** Draws one line with its plate's bottom edge at bottom; returns the plate's top. */
    private static int drawLine(GuiGraphicsExtractor g, Minecraft mc, Line l, long now, int bottom) {
        Font font = mc.font;
        Protocol.Say s = l.say;
        int sw = g.guiWidth();
        long elapsed = now - l.start;
        float alpha = SubtitleMath.lineAlpha(elapsed, l.speakMs, SubtitleMath.lingerMs(s.text().length()));
        float scale = scale(s.kind());
        int maxWidth = (int) (Math.min(sw * 0.62f, 340) / scale);
        if (l.laidOutFor != maxWidth) {
            layout(l, font, maxWidth);
            l.laidOutFor = maxWidth;
        }
        int space = font.width(" ");
        int rowsShown = Math.min(MAX_ROWS, l.rowCount);
        int rowH = Math.round(10 * scale);
        int blockW = Math.round(l.widest * scale);
        String label = label(s);
        int labelW = Math.round(font.width(label) * 0.75f);
        int plateW = Math.max(blockW, labelW) + 12;
        int plateH = 9 + rowsShown * rowH + 5;
        int top = bottom - plateH;
        if (alpha <= 0.02f) return top;

        // Where the speaker is: the subtitle leans to that side, and a marker points off screen.
        Bearing bearing = null;
        if (s.from() != null) {
            Camera cam = mc.gameRenderer.mainCamera();
            var p = cam.position();
            bearing = SubtitleMath.bearing(new double[] { p.x, p.y, p.z }, cam.yRot(), cam.xRot(), s.from(), cam.getFov(),
                    sw / (float) Math.max(1, g.guiHeight()));
        }
        float lean = bearing == null ? 0 : SubtitleMath.nudge(bearing.yaw());
        int cx = Math.round(sw / 2f + lean * sw * 0.16f);
        cx = Math.max(plateW / 2 + 14, Math.min(sw - plateW / 2 - 14, cx));
        int left = cx - plateW / 2;

        int color = s.color();
        g.fill(left, top, left + plateW, bottom, Overlays.argb(0.5f * alpha, 0x0C0814));
        g.fillGradient(left, top, left + plateW, top + 8, Overlays.argb(0.22f * alpha, color), 0x00000000);

        // Who speaks.
        if (alpha > 0.16f) {
            GodHud.small(g, font, label, cx - labelW / 2, top + 2, Overlays.argb(alpha, color));
        }

        // The words, as far as the voice has come.
        int shown = SubtitleMath.revealed(l.times, elapsed);
        int currentRow = shown == 0 ? 0 : l.rows[shown - 1];
        int first = SubtitleMath.firstRow(currentRow, l.rowCount, MAX_ROWS);
        int text = textColor(s);
        boolean dark = dark(s.tone());
        boolean airy = airy(s.tone());
        int x = 0;
        int row = -1;
        for (int i = 0; i < l.words.length; i++) {
            if (l.rows[i] != row) {
                row = l.rows[i];
                x = 0;
            }
            int wordX = x;
            x += l.widths[i] + space;
            if (row < first || row >= first + rowsShown) continue;
            float a = alpha * SubtitleMath.wordAlpha(l.times[i], elapsed);
            if (a <= 0.06f) continue;
            float rise = (1 - SubtitleMath.wordAlpha(l.times[i], elapsed)) * 2f;
            float dy = 0;
            float dx = 0;
            if (dark) {
                // A low tremble while it is being said.
                if (elapsed < l.speakMs) {
                    dx = (float) Math.sin(now / 37.0 + i * 2.1) * 0.35f;
                    dy = (float) Math.cos(now / 43.0 + i * 1.3) * 0.35f;
                }
            } else if (airy) {
                dy = (float) Math.sin(now / 520.0 + i * 0.7) * 0.6f;
            }
            float rowLeft = cx - rowWidth(l, row, space) * scale / 2f;
            g.pose().pushMatrix();
            g.pose().translate(rowLeft + wordX * scale + dx, top + 10 + (row - first) * rowH + rise + dy);
            g.pose().scale(scale, scale);
            g.text(font, l.parts[i], 0, 0, Overlays.argb(a, text), true);
            g.pose().popMatrix();
        }
        // More of the line is still to come on the next page.
        if (first + rowsShown < l.rowCount && alpha > 0.16f) {
            GodHud.small(g, font, "…", left + plateW - 8, bottom - 9, Overlays.argb(alpha * 0.7f, text));
        }

        // The voice's loudness: a thin bar along the plate's bottom, growing from the middle.
        float target = GodClientMod.VOICE.measured(s.realm(), now) ? GodClientMod.VOICE.level(s.realm(), now) : 0;
        l.level += (target - l.level) * 0.35f;
        if (l.level > 0.02f) {
            int half = Math.max(1, Math.round((plateW / 2f - 2) * l.level));
            g.fill(cx - half, bottom - 1, cx + half, bottom, Overlays.argb(alpha * (0.45f + 0.5f * l.level), color));
        }

        if (bearing != null && bearing.where() != Where.AHEAD) {
            marker(g, bearing.where(), sw, cx, top, bottom, color, alpha * (0.65f + 0.35f * (float) Math.sin(now / 260.0)));
        }
        return top;
    }

    /** A small arrow toward a speaker who is off screen: at the screen's edge for left/right, at the plate for up/down. */
    private static void marker(GuiGraphicsExtractor g, Where where, int sw, int cx, int top, int bottom, int color,
            float alpha) {
        int c = Overlays.argb(alpha, mix(color, 0xFFFFFF, 0.35f));
        int mid = (top + bottom) / 2;
        int size = 5;
        int ax = switch (where) {
            case LEFT -> 6;
            case RIGHT -> sw - 7;
            default -> cx;
        };
        int ay = switch (where) {
            case ABOVE -> top - 9;
            case BELOW -> bottom + 4;
            default -> mid;
        };
        g.blit(RenderPipelines.GUI_TEXTURED, GodHud.GLOW, ax - 9, ay - 9 + (where == Where.ABOVE || where == Where.BELOW ? 2 : 0),
                0, 0, 18, 18, 16, 16, 16, 16, Overlays.argb(alpha * 0.7f, color));
        for (int i = 0; i < size; i++) {
            switch (where) {
                case LEFT -> g.fill(ax + i, ay - i, ax + i + 1, ay + i + 1, c);
                case RIGHT -> g.fill(ax - i, ay - i, ax - i + 1, ay + i + 1, c);
                case ABOVE -> g.fill(ax - i, ay + i, ax + i + 1, ay + i + 1, c);
                case BELOW -> g.fill(ax - i, ay + size - 1 - i, ax + i + 1, ay + size - i, c);
                default -> {
                }
            }
        }
    }
}
