package net.bigyous.gptgodmc.god.art;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.bigyous.gptgodmc.god.art.Sprite.Ink;

/**
 * Looks at a sprite the way a teacher looks over a student's shoulder and says, in a few plain notes,
 * what would make it better: too small on its canvas, a blob with no outline to read, stray texels, a
 * shape that touches every edge, nothing that shines, a held thing lying the wrong way. The notes go
 * back to the artist with the picture; none of them forbids anything. Pure.
 */
public final class SpriteLint {
    private SpriteLint() {
    }

    /** Notes for the artist, most important first (empty when there is nothing to say). */
    public static List<String> notes(Sprite s, String kind) {
        List<String> out = new ArrayList<>();
        int w = s.w(), h = s.h(), filled = 0, orphans = 0;
        int minX = w, maxX = -1, minY = h, maxY = -1;
        Set<Pigment> used = new HashSet<>();
        boolean shines = false;
        double sx = 0, sy = 0, sxy = 0, sxx = 0, syy = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Ink ink = s.at(x, y);
                if (ink == null) continue;
                filled++;
                used.add(ink.pigment());
                shines |= ink.pigment().emissive() || ink.pigment().family == Pigment.Family.GEM || s.accent(x, y) == '*';
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                sx += x;
                sy += y;
                sxy += x * y;
                sxx += x * x;
                syy += y * y;
                if (s.at(x - 1, y) == null && s.at(x + 1, y) == null && s.at(x, y - 1) == null && s.at(x, y + 1) == null
                        && s.at(x - 1, y - 1) == null && s.at(x + 1, y - 1) == null && s.at(x - 1, y + 1) == null
                        && s.at(x + 1, y + 1) == null) {
                    orphans++;
                }
            }
        }
        if (filled == 0) {
            out.add("The canvas is empty.");
            return out;
        }
        double cover = filled / (double) (w * h);
        int spanX = maxX - minX + 1, spanY = maxY - minY + 1;
        if (Math.max(spanX / (double) w, spanY / (double) h) < 0.7) {
            out.add("The thing only spans " + spanX + "x" + spanY + " of a " + w + "x" + h + " canvas. Draw it larger: it "
                    + "should reach within a texel or two of the edges along its longest side.");
        }
        if (cover > 0.8) {
            out.add("It fills " + Math.round(cover * 100) + "% of the canvas, so it has no silhouette. Carve empty space "
                    + "into it: a shape is read by its outline.");
        } else if (cover < 0.1) {
            out.add("Only " + Math.round(cover * 100) + "% of the canvas is filled; it will read as a few specks.");
        }
        if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1 && cover > 0.5) {
            out.add("It touches all four edges. Leave a margin on at least two sides.");
        }
        if (orphans > 0) {
            out.add(orphans + " texel" + (orphans == 1 ? " stands" : "s stand") + " alone, touching nothing. Join "
                    + (orphans == 1 ? "it" : "them") + " to the shape or remove " + (orphans == 1 ? "it" : "them")
                    + " (single specks read as noise).");
        }
        if (used.size() == 1 && filled > 12) {
            out.add("It is all one material. A second material for a part of it (a grip, a setting, a band) and a small "
                    + "accent make it read as a made thing.");
        } else if (used.size() > 6) {
            out.add("It uses " + used.size() + " materials. Three or four, each in a clear place, look designed; more "
                    + "look busy.");
        }
        if (!shines) {
            out.add("Nothing in it shines. One small light (a gem, a glow, a sparkle accent) gives the eye a place to land.");
        }
        java.util.Set<Character> flat = SpritePainter.flattened(s);
        if (!flat.isEmpty()) {
            StringBuilder letters = new StringBuilder();
            for (char c : flat) letters.append(letters.length() > 0 ? ", " : "").append('\'').append(c).append('\'');
            out.add(letters + (flat.size() == 1 ? " is a narrow stroke" : " are narrow strokes") + " on the diagonal, so "
                    + (flat.size() == 1 ? "it is" : "they are") + " kept at the usual thickness (" + s.usualThick()
                    + "): thick, each row would stand out from the next like a stair. Give thickness to broad parts or "
                    + "to parts that run straight.");
        }
        if (kind != null && (kind.equals("blade") || kind.equals("staff")) && filled > 8) {
            // Things held in the hand lie from bottom left to top right.
            double n = filled, cov = sxy / n - (sx / n) * (sy / n);
            double vx = sxx / n - (sx / n) * (sx / n), vy = syy / n - (sy / n) * (sy / n);
            if (cov > -0.25 * Math.max(vx, vy)) {
                out.add("A " + kind + " is held: draw it on the diagonal, grip at the bottom left and tip at the top "
                        + "right, as Minecraft's swords and tools are.");
            }
        }
        return out;
    }
}
