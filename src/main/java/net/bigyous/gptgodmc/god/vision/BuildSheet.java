package net.bigyous.gptgodmc.god.vision;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;

/**
 * One build seen from all sides in a single picture: only its own blocks (the ground around it is a
 * faint ghost), from four corners at a three-quarter angle, from straight above, and a tile of facts.
 * Pure: which blocks belong to the build, where each camera stands and how the sheet is laid out are
 * unit tested and rendered offline.
 */
public final class BuildSheet {
    private BuildSheet() {
    }

    // ---- Blocks -------------------------------------------------------------------------------------

    /** One block position packed into a long (x and z within ±33 million, y within ±2047). */
    public static long key(int x, int y, int z) {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    public static int x(long k) {
        return (int) (k >> 38);
    }

    public static int z(long k) {
        return (int) (k << 26 >> 38);
    }

    public static int y(long k) {
        return (int) (k << 52 >> 52);
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Bounds of(Collection<Long> blocks) {
            int ax = Integer.MAX_VALUE, ay = Integer.MAX_VALUE, az = Integer.MAX_VALUE;
            int bx = Integer.MIN_VALUE, by = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
            for (long k : blocks) {
                ax = Math.min(ax, x(k));
                bx = Math.max(bx, x(k));
                ay = Math.min(ay, y(k));
                by = Math.max(by, y(k));
                az = Math.min(az, z(k));
                bz = Math.max(bz, z(k));
            }
            return new Bounds(ax, ay, az, bx, by, bz);
        }

        /** East to west. */
        public int width() {
            return maxX - minX + 1;
        }

        /** North to south. */
        public int length() {
            return maxZ - minZ + 1;
        }

        public int height() {
            return maxY - minY + 1;
        }

        public double cx() {
            return (minX + maxX + 1) / 2.0;
        }

        public double cy() {
            return (minY + maxY + 1) / 2.0;
        }

        public double cz() {
            return (minZ + maxZ + 1) / 2.0;
        }

        /** The radius of the sphere around the whole build. */
        public double radius() {
            return Math.sqrt(width() * width() + length() * length() + height() * height()) / 2.0;
        }

        boolean holds(int x, int y, int z, int margin) {
            return x >= minX - margin && x <= maxX + margin && y >= minY - margin && y <= maxY + margin
                    && z >= minZ - margin && z <= maxZ + margin;
        }
    }

    /** True for a block a mortal would have put there: not air, not plain terrain, water, leaves or ore. */
    public static boolean built(int code, Palette p) {
        if (code <= 0 || code >= p.kind.length) return false;
        if (p.kind[code] == Palette.EMPTY || p.kind[code] == Palette.WATER || p.xrayHidden[code]) return false;
        String n = Material.values()[code].name();
        return !n.endsWith("_LEAVES") && !n.endsWith("_ORE") && !n.equals("BEDROCK") && !n.equals("LAVA");
    }

    /**
     * Grows a few known blocks (what a mortal just placed) into the whole build: every built block
     * touching them, face, edge or corner, no further than `reach` blocks outside the seeds' own box.
     * Seeds that are gone (air now) are dropped; seeds of terrain blocks (a dirt hut) stay.
     */
    public static Set<Long> grow(Collection<Long> seeds, Scene.Voxels v, Palette p, int reach, int limit) {
        Set<Long> out = new HashSet<>();
        Deque<Long> open = new ArrayDeque<>();
        for (long s : seeds) {
            int code = v.block(x(s), y(s), z(s));
            if (code > 0 && p.kind[code] != Palette.EMPTY && out.add(s)) open.add(s);
        }
        if (out.isEmpty()) return out;
        Bounds b = Bounds.of(out);
        while (!open.isEmpty() && out.size() < limit) {
            long k = open.poll();
            int x = x(k), y = y(k), z = z(k);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int nx = x + dx, ny = y + dy, nz = z + dz;
                        if (!b.holds(nx, ny, nz, reach)) continue;
                        long nk = key(nx, ny, nz);
                        if (out.contains(nk) || !built(v.block(nx, ny, nz), p)) continue;
                        if (out.size() >= limit) break;
                        out.add(nk);
                        open.add(nk);
                    }
                }
            }
        }
        return out;
    }

    /** Drops positions that hold nothing now (broken, burnt) so the box fits what still stands. */
    public static Set<Long> standing(Collection<Long> blocks, Scene.Voxels v, Palette p) {
        Set<Long> out = new HashSet<>();
        for (long k : blocks) {
            int code = v.block(x(k), y(k), z(k));
            if (code > 0 && p.kind[code] != Palette.EMPTY) out.add(k);
        }
        return out;
    }

    /** What it is made of, most used first: block name to count. */
    public static Map<String, Integer> materials(Collection<Long> blocks, Scene.Voxels v) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (long k : blocks) {
            int code = v.block(x(k), y(k), z(k));
            if (code > 0) counts.merge(Material.values()[code].name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, c) -> c.getValue() != a.getValue().intValue() ? c.getValue() - a.getValue()
                : a.getKey().compareTo(c.getKey()));
        Map<String, Integer> out = new LinkedHashMap<>();
        sorted.forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    /** "120 cobblestone, 64 oak_planks, 12 glass_pane" (the first `most`, then "+N other kinds"). */
    public static String materialsLine(Map<String, Integer> materials, int most) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Integer> e : materials.entrySet()) {
            if (i++ >= most) break;
            parts.add(e.getValue() + " " + e.getKey());
        }
        if (materials.size() > most) parts.add("+" + (materials.size() - most) + " other kinds");
        return String.join(", ", parts);
    }

    /** The voxels of the build alone, fully lit; everything else is air. */
    static final class Only implements Scene.Voxels {
        private final Scene.Voxels world;
        private final Set<Long> blocks;
        private final Bounds b;
        private final boolean ghost;

        /** @param ghost false: the build's blocks; true: what surrounds it (near its box, not above it) */
        Only(Scene.Voxels world, Set<Long> blocks, Bounds b, boolean ghost) {
            this.world = world;
            this.blocks = blocks;
            this.b = b;
            this.ghost = ghost;
        }

        @Override
        public int block(int x, int y, int z) {
            if (ghost) {
                if (!b.holds(x, y, z, GROUND) || y > b.maxY() || blocks.contains(key(x, y, z))) return 0;
                int code = world.block(x, y, z);
                return code == UNLOADED ? 0 : code;
            }
            if (!b.holds(x, y, z, 0) || !blocks.contains(key(x, y, z))) return 0;
            int code = world.block(x, y, z);
            return code == UNLOADED ? 0 : code;
        }

        @Override
        public int light(int x, int y, int z, double daylight) {
            return 15;
        }

        @Override
        public int highestY(int x, int z) {
            return b.maxY();
        }

        @Override
        public int minY() {
            return b.minY() - GROUND - 1;
        }

        @Override
        public int maxY() {
            return b.maxY() + 2;
        }
    }

    // ---- Cameras ------------------------------------------------------------------------------------

    /** Blocks of ground shown around the build. */
    static final int GROUND = 3;
    private static final double FOV = 38, PITCH = 32;

    /** A side the build is seen from: where the camera stands, as a compass bearing from the build. */
    public enum Side {
        SOUTH_EAST("FROM SOUTH-EAST", 1, 1), SOUTH_WEST("FROM SOUTH-WEST", -1, 1), NORTH_WEST("FROM NORTH-WEST", -1,
                -1), NORTH_EAST("FROM NORTH-EAST", 1, -1);

        public final String label;
        final int dx, dz;

        Side(String label, int dx, int dz) {
            this.label = label;
            this.dx = dx;
            this.dz = dz;
        }
    }

    /** A camera on that side, looking down at the build's middle, far enough that all of it is in frame. */
    public static Renderer.Camera camera(Bounds b, Side side) {
        double dist = (b.radius() + 0.5) / Math.sin(Math.toRadians(FOV) / 2);
        double flat = dist * Math.cos(Math.toRadians(PITCH)) / Math.sqrt(2);
        double x = b.cx() + side.dx * flat, z = b.cz() + side.dz * flat;
        double y = b.cy() + dist * Math.sin(Math.toRadians(PITCH));
        double[] yp = Renderer.lookAt(x, y, z, b.cx(), b.cy(), b.cz());
        return new Renderer.Camera(x, y, z, yp[0], yp[1], FOV);
    }

    /**
     * Where a point lands in a square picture taken by the camera: {across, down} from -1 to 1 (0 the
     * middle, down positive towards the bottom), and the distance in front of the camera.
     */
    public static double[] project(Renderer.Camera cam, double x, double y, double z) {
        double[] f = Renderer.forward(cam.yaw(), cam.pitch());
        double[] r = Renderer.right(cam.yaw());
        double[] u = Renderer.cross(r, f);
        double vx = x - cam.x(), vy = y - cam.y(), vz = z - cam.z();
        double depth = vx * f[0] + vy * f[1] + vz * f[2];
        double tan = Math.tan(Math.toRadians(cam.fovDegrees()) / 2);
        return new double[] { (vx * r[0] + vy * r[1] + vz * r[2]) / (depth * tan),
                -(vx * u[0] + vy * u[1] + vz * u[2]) / (depth * tan), depth };
    }

    /** Pixels per block in the top view, so the whole footprint plus a rim of ground fits the tile. */
    public static double topScale(Bounds b, int tile) {
        return tile / (double) (Math.max(b.width(), b.length()) + 2 * GROUND);
    }

    // ---- The sheet ----------------------------------------------------------------------------------

    public record Tile(String label, int x, int y, int size) {
    }

    /** Three across, two down: two corners and the top view above, two corners and the facts below. */
    public static List<Tile> tiles(int tile) {
        return List.of(new Tile(Side.SOUTH_EAST.label, 0, 0, tile), new Tile(Side.SOUTH_WEST.label, tile, 0, tile),
                new Tile("FROM ABOVE", 2 * tile, 0, tile), new Tile(Side.NORTH_WEST.label, 0, tile, tile),
                new Tile(Side.NORTH_EAST.label, tile, tile, tile), new Tile("ABOUT", 2 * tile, tile, tile));
    }

    /** What the facts tile says. */
    public record About(String name, String builder, String where) {
    }

    private static final int BACK = 0x1B1D24;
    private static final double GHOST = 0.72;

    public static BufferedImage render(Scene.Voxels world, Palette palette, Set<Long> blocks, About about, int tile) {
        Bounds b = Bounds.of(blocks);
        Scene build = new Scene(new Only(world, blocks, b, false), palette, List.of(), List.of(),
                Scene.Dimension.OVERWORLD, 1.0, 0, false);
        Scene ground = new Scene(new Only(world, blocks, b, true), palette, List.of(), List.of(),
                Scene.Dimension.OVERWORLD, 1.0, 0, false);
        List<Tile> tiles = tiles(tile);
        BufferedImage img = new BufferedImage(3 * tile, 2 * tile, BufferedImage.TYPE_INT_RGB);
        Side[] sides = { Side.SOUTH_EAST, Side.SOUTH_WEST, null, Side.NORTH_WEST, Side.NORTH_EAST };
        for (int i = 0; i < 5; i++) {
            Tile t = tiles.get(i);
            if (sides[i] != null) corner(img, t, build, ground, camera(b, sides[i]), b);
            else top(img, t, build, ground, b);
        }
        facts(img, tiles.get(5), b, blocks.size(), materials(blocks, world), about);
        for (Tile t : tiles) {
            if (!t.label().equals("ABOUT")) {
                plate(img, t.x(), t.y(), PixelFont.width(t.label(), 1) + 7, 13);
                PixelFont.draw(img, t.label(), t.x() + 4, t.y() + 3, 0xFFFFFF, 1);
            }
            for (int k = 0; k < t.size(); k++) {
                if (t.x() > 0) img.setRGB(t.x(), t.y() + k, 0x50545E);
                if (t.y() > 0) img.setRGB(t.x() + k, t.y(), 0x50545E);
            }
        }
        return img;
    }

    private static void corner(BufferedImage img, Tile t, Scene build, Scene ground, Renderer.Camera cam, Bounds b) {
        double[] f = Renderer.forward(cam.yaw(), cam.pitch());
        double[] r = Renderer.right(cam.yaw());
        double[] u = Renderer.cross(r, f);
        double tan = Math.tan(Math.toRadians(cam.fovDegrees()) / 2);
        // A far limit well past the build keeps the tracer's distance haze off it.
        double far = (b.radius() + 0.5) / Math.sin(Math.toRadians(FOV) / 2) * 8;
        double[] out = new double[1];
        int n = t.size();
        for (int py = 0; py < n; py++) {
            double sy = (1 - 2 * (py + 0.5) / n) * tan;
            for (int px = 0; px < n; px++) {
                double sx = (2 * (px + 0.5) / n - 1) * tan;
                double dx = f[0] + sx * r[0] + sy * u[0], dy = f[1] + sx * r[1] + sy * u[1],
                        dz = f[2] + sx * r[2] + sy * u[2];
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                img.setRGB(t.x() + px, t.y() + py, pixel(build, ground, cam.x(), cam.y(), cam.z(), dx / len, dy / len,
                        dz / len, far, out));
            }
        }
    }

    private static int pixel(Scene build, Scene ground, double ox, double oy, double oz, double dx, double dy,
            double dz, double far, double[] out) {
        int c = Renderer.trace(build, ox, oy, oz, dx, dy, dz, far, false, out);
        if (out[0] < far) return c;
        c = Renderer.trace(ground, ox, oy, oz, dx, dy, dz, far, false, out);
        return out[0] < far ? Renderer.mix(c, BACK, GHOST) : BACK;
    }

    /** Straight down, north up; higher parts are brighter. */
    private static void top(BufferedImage img, Tile t, Scene build, Scene ground, Bounds b) {
        int n = t.size();
        double scale = topScale(b, n);
        double span = n / scale, x0 = b.cx() - span / 2, z0 = b.cz() - span / 2;
        // A far limit well past the ground keeps the tracer's distance haze off the picture.
        double from = b.maxY() + 3, far = (b.height() + GROUND + 8) * 8.0;
        double[] out = new double[1];
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double wx = x0 + (px + 0.5) / scale, wz = z0 + (py + 0.5) / scale;
                int c = Renderer.trace(build, wx, from, wz, 0, -1, 0, far, false, out);
                if (out[0] < far) {
                    double depth = Math.max(0, out[0] - 2) / Math.max(1, b.height());
                    c = Renderer.scale(c, 1.0 - 0.45 * Math.min(1, depth));
                } else {
                    c = Renderer.trace(ground, wx, from, wz, 0, -1, 0, far, false, out);
                    c = out[0] < far ? Renderer.mix(c, BACK, GHOST) : BACK;
                }
                img.setRGB(t.x() + px, t.y() + py, c);
            }
        }
        // North, and a bar of round length.
        PixelFont.draw(img, "N", t.x() + n - 12, t.y() + 14, 0xFFFFFF, 1);
        for (int k = 0; k < 5; k++) {
            for (int j = -k; j <= k; j++) img.setRGB(t.x() + n - 10 + j, t.y() + 5 + k, 0xFFFFFF);
        }
        int bar = Minimap.scaleBlocks(1 / scale, n);
        int barPx = (int) Math.round(bar * scale);
        plate(img, t.x(), t.y() + n - 22, Math.max(barPx, PixelFont.width(bar + " BLOCKS", 1)) + 9, 22);
        for (int k = 0; k < barPx; k++) {
            img.setRGB(t.x() + 4 + k, t.y() + n - 6, 0xFFFFFF);
            img.setRGB(t.x() + 4 + k, t.y() + n - 5, 0xFFFFFF);
        }
        PixelFont.draw(img, bar + (bar == 1 ? " BLOCK" : " BLOCKS"), t.x() + 4, t.y() + n - 19, 0xFFFFFF, 1);
    }

    private static void facts(BufferedImage img, Tile t, Bounds b, int count, Map<String, Integer> materials,
            About about) {
        for (int py = 0; py < t.size(); py++) {
            for (int px = 0; px < t.size(); px++) img.setRGB(t.x() + px, t.y() + py, BACK);
        }
        int max = (t.size() - 12) / 6;
        int y = t.y() + 8;
        y = line(img, t, y, about.name(), 0xFFD98A, max);
        if (about.builder() != null) y = line(img, t, y, "BY " + about.builder(), 0xFFFFFF, max);
        y += 5;
        y = line(img, t, y, b.width() + " X " + b.length() + " X " + b.height() + " BLOCKS", 0xFFFFFF, max);
        y = line(img, t, y, "(EAST-WEST X NORTH-SOUTH X HIGH)", 0xA8ACB8, max);
        y = line(img, t, y, count + " BLOCKS IN ALL", 0xFFFFFF, max);
        if (about.where() != null) y = line(img, t, y, about.where(), 0xA8ACB8, max);
        y += 5;
        y = line(img, t, y, "MADE OF", 0xFFD98A, max);
        int shown = 0;
        for (Map.Entry<String, Integer> e : materials.entrySet()) {
            if (y > t.y() + t.size() - 24 || shown++ >= 8) break;
            y = line(img, t, y, e.getValue() + " " + e.getKey().replace('_', ' '), 0xFFFFFF, max);
        }
        if (materials.size() > shown) line(img, t, y, "+" + (materials.size() - shown) + " OTHER KINDS", 0xA8ACB8, max);
    }

    private static int line(BufferedImage img, Tile t, int y, String text, int rgb, int max) {
        PixelFont.draw(img, text.length() > max ? text.substring(0, max) : text, t.x() + 7, y, rgb, 1);
        return y + 11;
    }

    private static void plate(BufferedImage img, int x, int y, int w, int h) {
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                if (px >= 0 && py >= 0 && px < img.getWidth() && py < img.getHeight()) {
                    img.setRGB(px, py, Renderer.mix(img.getRGB(px, py) & 0xFFFFFF, 0, 0.6));
                }
            }
        }
    }
}
