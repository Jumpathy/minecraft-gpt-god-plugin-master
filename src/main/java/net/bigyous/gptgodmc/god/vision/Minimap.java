package net.bigyous.gptgodmc.god.vision;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The god's minimap: a small top-down picture of where mortals are, cut at their own level (the cave
 * or room they stand in, not the roof of the world above them), with each mortal's name, coordinates
 * and facing drawn on it. Everything here is pure (no server), so the level choice, the grouping into
 * panels, the projection and the label placement are unit tested and rendered offline.
 */
public final class Minimap {
    private Minimap() {
    }

    // ---- Which level a mortal is on -----------------------------------------------------------------

    public enum Level {
        SURFACE, INDOORS, UNDERGROUND;

        /** Drawn as a slice at the mortal's height rather than as the top of the world. */
        public boolean slice() {
            return this != SURFACE;
        }

        public String word() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Mortals this far apart in height (or more) are on different floors of a slice. */
    public static final int SAME_LEVEL = 6;
    /** Under this much ground a covered mortal is underground, not merely indoors. */
    public static final int DEEP = 8;
    private static final int MARGIN = 10;

    /**
     * @param covered  something solid is over their head (no open sky straight up)
     * @param y        their feet
     * @param surfaceY the top of the world in their column
     */
    public static Level level(boolean covered, int y, int surfaceY) {
        if (!covered) return Level.SURFACE;
        return surfaceY - y >= DEEP ? Level.UNDERGROUND : Level.INDOORS;
    }

    // ---- What is drawn ------------------------------------------------------------------------------

    public record Mortal(String name, double x, double y, double z, float yaw, Level level, int rgb) {
    }

    /** A hostile creature. */
    public record Foe(double x, double y, double z, Level level) {
    }

    public enum PlaceKind {
        STRUCTURE, WORK, OWN_VESSEL, OTHER_VESSEL
    }

    public record Place(String name, double x, double z, PlaceKind kind) {
    }

    /** One picture: a square of the world around a centre, at the surface or sliced at a height. */
    public record Panel(int cx, int cz, int radius, boolean slice, int sliceY, Level level, List<Mortal> members) {
        public String title() {
            return slice ? (level == Level.INDOORS ? "INDOORS" : "UNDERGROUND") + " Y=" + sliceY : "SURFACE";
        }
    }

    public record Grouping(List<Panel> panels, List<Mortal> leftOut) {
    }

    /**
     * Sorts mortals into panels: those on the surface together, those under cover with the others on
     * their own floor, and only while they fit in one picture. The first mortal of each panel sets its
     * level, so put the one that matters first.
     */
    public static Grouping group(List<Mortal> mortals, int minRadius, int maxRadius, int maxPanels) {
        List<List<Mortal>> sets = new ArrayList<>();
        List<Mortal> leftOut = new ArrayList<>();
        for (Mortal m : mortals) {
            List<Mortal> home = null;
            for (List<Mortal> set : sets) {
                Mortal first = set.get(0);
                if (first.level().slice() != m.level().slice()) continue;
                if (m.level().slice() && Math.abs(first.y() - m.y()) > SAME_LEVEL) continue;
                List<Mortal> with = new ArrayList<>(set);
                with.add(m);
                if (halfExtent(with) + MARGIN <= maxRadius) {
                    home = set;
                    break;
                }
            }
            if (home != null) {
                home.add(m);
            } else if (sets.size() < maxPanels) {
                sets.add(new ArrayList<>(List.of(m)));
            } else {
                leftOut.add(m);
            }
        }
        List<Panel> panels = new ArrayList<>();
        for (List<Mortal> set : sets) {
            double[] b = bounds(set);
            int radius = Math.max(minRadius, Math.min(maxRadius, (int) Math.ceil(halfExtent(set)) + MARGIN));
            Mortal first = set.get(0);
            panels.add(new Panel((int) Math.floor((b[0] + b[1]) / 2), (int) Math.floor((b[2] + b[3]) / 2), radius,
                    first.level().slice(), (int) Math.floor(first.y()), first.level(), List.copyOf(set)));
        }
        return new Grouping(panels, leftOut);
    }

    private static double[] bounds(List<Mortal> set) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Mortal m : set) {
            minX = Math.min(minX, m.x());
            maxX = Math.max(maxX, m.x());
            minZ = Math.min(minZ, m.z());
            maxZ = Math.max(maxZ, m.z());
        }
        return new double[] { minX, maxX, minZ, maxZ };
    }

    private static double halfExtent(List<Mortal> set) {
        double[] b = bounds(set);
        return Math.max(b[1] - b[0], b[3] - b[2]) / 2;
    }

    /** True when a mortal shown in a panel is not on the level the panel is cut at. */
    public static boolean offLevel(Panel p, Mortal m) {
        return offLevel(p, m.level(), m.y());
    }

    /** The same for anything at a height: under cover on a surface map, or on another floor of a slice. */
    public static boolean offLevel(Panel p, Level level, double y) {
        if (!p.slice()) return level.slice();
        return Math.abs(y - p.sliceY()) > SAME_LEVEL;
    }

    /** "ABOVE", "BELOW" or (under a roof on a surface map) "INSIDE" for a mortal off the panel's level. */
    public static String offWord(Panel p, Mortal m) {
        if (!p.slice()) return m.level() == Level.INDOORS ? "INSIDE" : "BELOW";
        return m.y() > p.sliceY() ? "ABOVE" : "BELOW";
    }

    // ---- Projection ---------------------------------------------------------------------------------

    /** World to pixel, north up, the same mapping as {@link Renderer#terrain}. */
    public record Frame(int cx, int cz, int radius, int size) {
        public double blocksPerPx() {
            return 2.0 * radius / size;
        }

        public int px(double x) {
            return (int) Math.floor((x - (cx - radius)) / blocksPerPx());
        }

        public int py(double z) {
            return (int) Math.floor((z - (cz - radius)) / blocksPerPx());
        }

        public boolean inside(double x, double z) {
            int px = px(x), py = py(z);
            return px >= 0 && py >= 0 && px < size && py < size;
        }
    }

    /** Pixel offset of the tip of a facing arrow (Minecraft yaw: 0 south, 90 west, 180 north). */
    public static int[] arrow(float yaw, double length) {
        double r = Math.toRadians(yaw);
        return new int[] { (int) Math.round(-Math.sin(r) * length), (int) Math.round(Math.cos(r) * length) };
    }

    /** A round number of blocks for the scale bar, at most a quarter of the picture wide. */
    public static int scaleBlocks(double blocksPerPx, int size) {
        int best = 1;
        for (int n : new int[] { 2, 5, 10, 20, 50, 100 }) {
            if (n / blocksPerPx <= size / 4.0) best = n;
        }
        return best;
    }

    /** Roughly what an image of this size costs the model to read, in tokens (width x height / 750). */
    public static int tokens(int width, int height) {
        return (int) Math.ceil(width * height / 750.0);
    }

    // ---- Label placement ----------------------------------------------------------------------------

    public record Box(int x, int y, int w, int h) {
        public boolean hits(Box o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }

        boolean within(int width, int height) {
            return x >= 0 && y >= 0 && x + w <= width && y + h <= height;
        }
    }

    /**
     * Finds a spot for each label beside its marker: inside the picture, off the other markers, off the
     * labels placed before it and off the reserved corners. Earlier labels win, so list the important
     * ones first. A label with no free spot is squeezed inside the picture next to its marker.
     *
     * @param anchors the markers' boxes
     * @param sizes   {width, height} of each label
     */
    public static List<Box> place(List<Box> anchors, List<int[]> sizes, int width, int height, List<Box> reserved) {
        List<Box> placed = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            Box a = anchors.get(i);
            int w = sizes.get(i)[0], h = sizes.get(i)[1];
            Box chosen = null;
            search: for (int ring = 0; ring < 4; ring++) {
                int gap = 3 + ring * (h + 2);
                for (Box c : candidates(a, w, h, gap)) {
                    if (!c.within(width, height) || blocked(c, i, anchors, placed, reserved)) continue;
                    chosen = c;
                    break search;
                }
            }
            if (chosen == null) {
                int x = Math.max(0, Math.min(width - w, a.x() + a.w() + 3));
                int y = Math.max(0, Math.min(height - h, a.y() + a.h() / 2 - h / 2));
                chosen = new Box(x, y, w, h);
            }
            placed.add(chosen);
        }
        return placed;
    }

    private static List<Box> candidates(Box a, int w, int h, int gap) {
        int midY = a.y() + a.h() / 2 - h / 2, midX = a.x() + a.w() / 2 - w / 2;
        int right = a.x() + a.w() + gap, left = a.x() - gap - w, below = a.y() + a.h() + gap, above = a.y() - gap - h;
        return List.of(new Box(right, midY, w, h), new Box(left, midY, w, h), new Box(midX, below, w, h),
                new Box(midX, above, w, h), new Box(right, below, w, h), new Box(right, above, w, h),
                new Box(left, below, w, h), new Box(left, above, w, h));
    }

    private static boolean blocked(Box c, int self, List<Box> anchors, List<Box> placed, List<Box> reserved) {
        for (int j = 0; j < anchors.size(); j++) {
            if (j != self && c.hits(anchors.get(j))) return true;
        }
        for (Box p : placed) {
            if (c.hits(p)) return true;
        }
        for (Box r : reserved) {
            if (c.hits(r)) return true;
        }
        return false;
    }

    // ---- When a map is worth sending unasked --------------------------------------------------------

    private static final Pattern PLACE_WORDS = Pattern.compile("\\b(build|built|place|put|here|there|where|near|"
            + "nearby|next to|behind|around|beside|under|above|come|go to|move|follow|find|smite|strike|protect|"
            + "guard|save|rescue|trap|wall|bridge|map)\\b|-?\\d+[ ,]+-?\\d+", Pattern.CASE_INSENSITIVE);

    /**
     * Whether to attach a minimap to a perception without being asked.
     *
     * @param mode     off, when-useful or always
     * @param happened what the god is about to read: events, prayers and directives
     * @param commands its open binding commands ("" when none)
     * @param sinceMs  time since the last unasked map
     * @param gapMs    the least time between unasked maps in when-useful mode
     */
    public static boolean useful(String mode, List<String> happened, String commands, long sinceMs, long gapMs) {
        String m = mode == null ? "off" : mode.trim().toLowerCase(Locale.ROOT);
        if (m.equals("always")) return true;
        if (!m.equals("when-useful") || sinceMs < gapMs) return false;
        // Only the commands themselves ("#3 (given 2 min ago, attempt 1 of 3): build ..."), not the heading.
        for (String line : (commands == null ? "" : commands).split("\n")) {
            if (!line.startsWith("#")) continue;
            int cut = line.indexOf("): ");
            if (PLACE_WORDS.matcher(cut < 0 ? line : line.substring(cut + 3)).find()) return true;
        }
        for (String line : happened) {
            String l = line.toLowerCase(Locale.ROOT);
            if (l.contains("in danger they")) return true;
            if ((l.contains("pray") || l.contains("[operator")) && PLACE_WORDS.matcher(l).find()) return true;
        }
        return false;
    }

    // ---- Drawing ------------------------------------------------------------------------------------

    private static final int PLATE = 0x000000;
    private static final int FOE = 0xFF2A2A;
    private static final int OWN = 0xB24BF3;
    private static final int OTHER = 0xFF9A3C;
    private static final int MAX_PLACE_LABELS = 6;

    /** Draws one panel. `mortals` is everyone in that world; those outside the square are skipped. */
    public static BufferedImage render(Scene.Voxels voxels, Palette palette, Panel panel, List<Mortal> mortals,
            List<Foe> foes, List<Place> places, int size) {
        Frame f = new Frame(panel.cx(), panel.cz(), panel.radius(), size);
        BufferedImage img = Renderer.terrain(voxels, palette, panel.cx(), panel.sliceY(), panel.cz(), panel.radius(),
                size, panel.slice() ? Renderer.OverheadMode.CAVE : Renderer.OverheadMode.SURFACE);
        Graphics2D g = img.createGraphics();
        List<Box> reserved = new ArrayList<>();

        // The frame of reference: what level, which way is north, how big, and where the corners are.
        String title = panel.title();
        String nw = "NW " + (panel.cx() - panel.radius()) + "," + (panel.cz() - panel.radius());
        String se = "SE " + (panel.cx() + panel.radius()) + "," + (panel.cz() + panel.radius());
        Box titleBox = new Box(0, 0, Math.max(PixelFont.width(title, 2), PixelFont.width(nw, 1)) + 8, 31);
        plate(img, titleBox);
        PixelFont.draw(img, title, 4, 4, panel.slice() ? 0xFFD98A : 0xFFFFFF, 2);
        PixelFont.draw(img, nw, 4, 21, 0xD8D8D8, 1);
        reserved.add(titleBox);
        Box north = new Box(size - 24, 0, 24, 36);
        plate(img, north);
        PixelFont.draw(img, "N", size - 17, 18, 0xFFFFFF, 2);
        g.setColor(Color.WHITE);
        g.fillPolygon(new int[] { size - 12, size - 18, size - 6 }, new int[] { 3, 14, 14 }, 3);
        reserved.add(north);
        int blocks = scaleBlocks(f.blocksPerPx(), size);
        int barPx = (int) Math.round(blocks / f.blocksPerPx());
        String barText = blocks + " BLOCKS";
        Box bar = new Box(0, size - 24, Math.max(barPx, PixelFont.width(barText, 1)) + 10, 24);
        plate(img, bar);
        g.fillRect(5, size - 9, barPx, 2);
        g.fillRect(5, size - 13, 1, 9);
        g.fillRect(4 + barPx, size - 13, 1, 9);
        PixelFont.draw(img, barText, 5, size - 22, 0xFFFFFF, 1);
        reserved.add(bar);
        Box seBox = new Box(size - PixelFont.width(se, 1) - 8, size - 13, PixelFont.width(se, 1) + 8, 13);
        plate(img, seBox);
        PixelFont.draw(img, se, seBox.x() + 4, size - 10, 0xD8D8D8, 1);
        reserved.add(seBox);

        // Markers first, so no label is put on top of one; then the labels, mortals before places.
        List<Box> anchors = new ArrayList<>();
        List<int[]> sizes = new ArrayList<>();
        List<Runnable> writers = new ArrayList<>();
        List<List<Box>> spots = new ArrayList<>();

        for (Foe foe : foes) {
            if (!f.inside(foe.x(), foe.z()) || offLevel(panel, foe.level(), foe.y())) continue;
            int px = f.px(foe.x()), py = f.py(foe.z());
            g.setColor(new Color(0x101010));
            g.fillRect(px - 3, py - 3, 7, 7);
            g.setColor(new Color(FOE));
            g.fillRect(px - 2, py - 2, 5, 5);
        }

        List<Mortal> shown = new ArrayList<>();
        for (Mortal m : panel.members()) {
            if (f.inside(m.x(), m.z())) shown.add(m);
        }
        for (Mortal m : mortals) {
            if (f.inside(m.x(), m.z()) && !shown.contains(m)) shown.add(m);
        }
        int nameScale = shown.size() <= 4 && size >= 352 ? 2 : 1;
        for (Mortal m : shown) {
            int px = f.px(m.x()), py = f.py(m.z());
            boolean off = offLevel(panel, m);
            pointer(g, px, py, m.yaw(), m.rgb(), off);
            String name = m.name();
            String coords = Math.round(Math.floor(m.x())) + " " + Math.round(Math.floor(m.y())) + " "
                    + Math.round(Math.floor(m.z())) + (off ? " " + offWord(panel, m) : "");
            int w = Math.max(PixelFont.width(name, nameScale), PixelFont.width(coords, 1)) + 4;
            int h = PixelFont.height(nameScale) + 9 + 5;
            int index = anchors.size();
            anchors.add(new Box(px - 10, py - 10, 21, 21));
            sizes.add(new int[] { w, h });
            int scale = nameScale;
            writers.add(() -> {
                Box b = spots.get(0).get(index);
                plate(img, b);
                PixelFont.draw(img, name, b.x() + 2, b.y() + 2, m.rgb(), scale);
                PixelFont.draw(img, coords, b.x() + 2, b.y() + PixelFont.height(scale) + 5, 0xFFFFFF, 1);
            });
        }

        List<Place> near = new ArrayList<>();
        for (Place p : places) {
            if (f.inside(p.x(), p.z())) near.add(p);
        }
        near.sort((a, b) -> {
            boolean av = a.kind() == PlaceKind.OWN_VESSEL || a.kind() == PlaceKind.OTHER_VESSEL;
            boolean bv = b.kind() == PlaceKind.OWN_VESSEL || b.kind() == PlaceKind.OTHER_VESSEL;
            if (av != bv) return av ? -1 : 1;
            return Double.compare(Math.hypot(a.x() - panel.cx(), a.z() - panel.cz()),
                    Math.hypot(b.x() - panel.cx(), b.z() - panel.cz()));
        });
        int labelled = 0;
        for (Place p : near) {
            int px = f.px(p.x()), py = f.py(p.z());
            boolean vessel = p.kind() == PlaceKind.OWN_VESSEL || p.kind() == PlaceKind.OTHER_VESSEL;
            int rgb = switch (p.kind()) {
                case OWN_VESSEL -> OWN;
                case OTHER_VESSEL -> OTHER;
                case WORK -> 0xD9A8FF;
                default -> 0xFFFFFF;
            };
            if (vessel) {
                g.setColor(Color.WHITE);
                g.fillPolygon(new int[] { px, px + 7, px, px - 7 }, new int[] { py - 7, py, py + 7, py }, 4);
                g.setColor(new Color(rgb));
                g.fillPolygon(new int[] { px, px + 5, px, px - 5 }, new int[] { py - 5, py, py + 5, py }, 4);
            } else {
                g.setColor(new Color(0x101010));
                g.drawRect(px - 4, py - 4, 8, 8);
                g.setColor(new Color(rgb));
                g.drawRect(px - 3, py - 3, 6, 6);
            }
            if (!vessel && labelled >= MAX_PLACE_LABELS) continue;
            if (!vessel) labelled++;
            String text = p.name().length() > 18 ? p.name().substring(0, 18) : p.name();
            int index = anchors.size();
            anchors.add(new Box(px - 7, py - 7, 15, 15));
            sizes.add(new int[] { PixelFont.width(text, 1) + 4, 11 });
            writers.add(() -> {
                Box b = spots.get(0).get(index);
                plate(img, b);
                PixelFont.draw(img, text, b.x() + 2, b.y() + 2, rgb, 1);
            });
        }
        g.dispose();

        spots.add(place(anchors, sizes, size, size, reserved));
        writers.forEach(Runnable::run);
        return img;
    }

    /** A dart pointing where the mortal faces; hollow when they are on another level. */
    private static void pointer(Graphics2D g, int px, int py, float yaw, int rgb, boolean hollow) {
        int[] tip = arrow(yaw, 10), l = arrow(yaw + 140, 8), r = arrow(yaw - 140, 8), back = arrow(yaw + 180, 3);
        int[] xs = { px + tip[0], px + l[0], px + back[0], px + r[0] };
        int[] ys = { py + tip[1], py + l[1], py + back[1], py + r[1] };
        g.setColor(new Color(0x101010));
        g.setStroke(new java.awt.BasicStroke(3));
        g.drawPolygon(xs, ys, 4);
        g.setStroke(new java.awt.BasicStroke(1));
        if (hollow) {
            g.fillPolygon(xs, ys, 4);
            g.setColor(new Color(rgb));
            g.drawPolygon(xs, ys, 4);
        } else {
            g.setColor(new Color(rgb));
            g.fillPolygon(xs, ys, 4);
            g.setColor(Color.WHITE);
            g.drawLine(px, py, px + tip[0], py + tip[1]);
        }
    }

    /** Darkens a rectangle so the text on it reads on any ground. */
    private static void plate(BufferedImage img, Box b) {
        for (int y = Math.max(0, b.y()); y < Math.min(img.getHeight(), b.y() + b.h()); y++) {
            for (int x = Math.max(0, b.x()); x < Math.min(img.getWidth(), b.x() + b.w()); x++) {
                img.setRGB(x, y, Renderer.mix(img.getRGB(x, y) & 0xFFFFFF, PLATE, 0.62));
            }
        }
    }

    /** Several panels with a thin gap: up to three in a row, four as a square. */
    public static BufferedImage compose(List<BufferedImage> panels) {
        if (panels.size() == 1) return panels.get(0);
        int size = panels.get(0).getWidth(), gap = 4;
        int cols = panels.size() == 4 ? 2 : panels.size(), rows = (panels.size() + cols - 1) / cols;
        BufferedImage out = new BufferedImage(cols * size + (cols - 1) * gap, rows * size + (rows - 1) * gap,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x202024));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int i = 0; i < panels.size(); i++) {
            g.drawImage(panels.get(i), (i % cols) * (size + gap), (i / cols) * (size + gap), null);
        }
        g.dispose();
        return out;
    }

    /** The side of each panel when there are several: a little smaller, so the whole stays modest. */
    public static int panelSize(int size, int panels) {
        return panels <= 1 ? size : Math.max(320, (int) Math.round(size * 0.8));
    }
}
