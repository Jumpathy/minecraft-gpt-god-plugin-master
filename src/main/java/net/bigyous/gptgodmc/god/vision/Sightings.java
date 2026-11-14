package net.bigyous.gptgodmc.god.vision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * What a perspective view shows, in words. The renderer draws every block as a cube in its map color, so a
 * campfire, a sign and a block of gold are three coloured cubes and a torch is nothing at all: the god could not
 * tell what a mortal had built. This casts the same rays as the picture (fewer of them) and names the kinds of
 * block they pass through or land on, with how many, how far and where in the frame. Pure: no world access.
 */
public final class Sightings {
    /** Rays cast across the frame. */
    static final int W = 128, H = 72;
    /** Blocks further than this are left to the picture. */
    public static final double REACH = 48;
    /** Kinds named in one legend. */
    static final int KINDS = 14;

    /** Words that mark a kind as part of the land, as in survey_area. */
    private static final List<String> NATURAL = List.of("STONE", "DIRT", "GRASS_BLOCK", "SAND", "GRAVEL", "DEEPSLATE",
            "TUFF", "ANDESITE", "DIORITE", "GRANITE", "WATER", "LAVA", "NETHERRACK", "BEDROCK", "CALCITE", "CLAY",
            "SNOW", "ICE", "SANDSTONE", "SHORT_GRASS", "TALL_GRASS", "FERN", "LEAVES", "_LOG", "SEAGRASS", "KELP",
            "COARSE_DIRT", "PODZOL", "MUD", "BASALT", "BLACKSTONE", "END_STONE", "MOSS", "VINE", "DRIPSTONE", "BUSH",
            "DANDELION", "POPPY", "CORNFLOWER", "TULIP", "DAISY", "AZURE_BLUET", "ALLIUM", "ORCHID", "LILY", "CACTUS",
            "SUGAR_CANE", "BAMBOO", "MUSHROOM", "LICHEN", "ROOTS", "SCULK");

    /** One kind of block in view. */
    public record Seen(int code, int blocks, double nearest, double side, int x, int y, int z, boolean drawn) {
    }

    private static final class Tally {
        final Set<Long> blocks = new HashSet<>();
        double nearest = Double.MAX_VALUE;
        double sideSum;
        int rays;
        int x, y, z;
        boolean drawn;
    }

    private Sightings() {
    }

    /** True for kinds that belong to the land (stone, soil, plants, tree trunks and leaves): not worth naming. */
    public static boolean natural(String materialName) {
        String name = materialName.toUpperCase(Locale.ROOT);
        if (name.endsWith("AIR")) return true; // air, cave_air, void_air: nothing at all
        if (name.contains("PLANKS") || name.contains("STAIRS") || name.contains("SLAB") || name.contains("BRICK")
                || name.contains("WALL") || name.endsWith("_ORE") || name.contains("STRIPPED")
                || name.contains("POLISHED") || name.contains("CHISELED") || name.contains("CUT_")
                || name.contains("SMOOTH") || name.contains("COBBLE") || name.contains("REDSTONE")
                || name.contains("GLOWSTONE") || name.contains("LODESTONE") || name.contains("GRINDSTONE")
                || name.contains("TILES") || name.contains("PILLAR") || name.contains("MOSAIC")) {
            return false;
        }
        for (String n : NATURAL) {
            if (name.contains(n)) return true;
        }
        return false;
    }

    /**
     * Every kind of block the view's rays meet within {@link #REACH}: all they pass through (thin things the picture
     * does not draw, glass, water) and the first solid one they stop on. Nearest first.
     */
    public static List<Seen> scan(Scene.Voxels v, Palette p, Renderer.Camera cam, double aspect,
            IntFunction<String> name) {
        // The land and the air are passed over without a tally: only what was built is counted.
        boolean[] land = new boolean[p.kind.length];
        for (int code = 0; code < land.length; code++) land[code] = natural(name.apply(code));
        double[] f = Renderer.forward(cam.yaw(), cam.pitch());
        double[] r = Renderer.right(cam.yaw());
        double[] u = Renderer.cross(r, f);
        double tanHalf = Math.tan(Math.toRadians(cam.fovDegrees()) / 2);
        Map<Integer, Tally> tallies = new HashMap<>();
        for (int py = 0; py < H; py++) {
            double sy = (1 - 2 * (py + 0.5) / H) * tanHalf;
            for (int px = 0; px < W; px++) {
                double side = 2 * (px + 0.5) / W - 1;
                double sx = side * tanHalf * aspect;
                double dx = f[0] + sx * r[0] + sy * u[0];
                double dy = f[1] + sx * r[1] + sy * u[1];
                double dz = f[2] + sx * r[2] + sy * u[2];
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                march(v, p, land, cam.x(), cam.y(), cam.z(), dx / len, dy / len, dz / len, side, tallies);
            }
        }
        List<Seen> out = new ArrayList<>();
        tallies.forEach((code, t) -> out.add(new Seen(code, t.blocks.size(), t.nearest, t.sideSum / t.rays, t.x, t.y,
                t.z, t.drawn)));
        out.sort((a, b) -> Double.compare(a.nearest(), b.nearest()));
        return out;
    }

    private static void march(Scene.Voxels v, Palette p, boolean[] land, double ox, double oy, double oz, double dx,
            double dy, double dz, double side, Map<Integer, Tally> tallies) {
        int ix = (int) Math.floor(ox), iy = (int) Math.floor(oy), iz = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tMaxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? (ix + 1 - ox) : (ox - ix)) * tDeltaX;
        double tMaxY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? (iy + 1 - oy) : (oy - iy)) * tDeltaY;
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? (iz + 1 - oz) : (oz - iz)) * tDeltaZ;
        double t = 0;
        while (t <= REACH) {
            if ((iy < v.minY() && stepY < 0) || (iy > v.maxY() && stepY > 0)) return;
            int code = v.block(ix, iy, iz);
            if (code == Scene.Voxels.UNLOADED) return;
            if (code > 0 && code < land.length && t > 0.05) {
                byte kind = p.kind[code];
                if (!land[code]) {
                    Tally tally = tallies.computeIfAbsent(code, c -> new Tally());
                    tally.blocks.add(((long) ix & 0x3FFFFFF) << 38 | ((long) iz & 0x3FFFFFF) << 12 | ((long) iy & 0xFFF));
                    tally.rays++;
                    tally.sideSum += side;
                    tally.drawn |= kind != Palette.EMPTY;
                    if (t < tally.nearest) {
                        tally.nearest = t;
                        tally.x = ix;
                        tally.y = iy;
                        tally.z = iz;
                    }
                }
                if (kind == Palette.SOLID || kind == Palette.GLOW) return;
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                t = tMaxX;
                tMaxX += tDeltaX;
                ix += stepX;
            } else if (tMaxY < tMaxZ) {
                t = tMaxY;
                tMaxY += tDeltaY;
                iy += stepY;
            } else {
                t = tMaxZ;
                tMaxZ += tDeltaZ;
                iz += stepZ;
            }
        }
    }

    /** The nearest {@link #KINDS} of what {@link #scan} found. */
    public static List<Seen> notable(List<Seen> seen) {
        return seen.size() <= KINDS ? seen : new ArrayList<>(seen.subList(0, KINDS));
    }

    /**
     * The legend's sentence, e.g. "In view, by name (nearest first): campfire, 3 blocks, centre; gold_block, 5 blocks,
     * centre; torch x4, 5 blocks (not drawn in the picture); oak_sign, 5 blocks, centre, reading "VHORAXIS / the
     * eye"." Empty when nothing built is in view. {@code signs} maps "x,y,z" of a sign to its text.
     */
    public static String describe(List<Seen> notable, IntFunction<String> name, Map<String, String> signs) {
        if (notable.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("In view, by name (nearest first): ");
        boolean first = true;
        for (Seen s : notable) {
            if (!first) sb.append("; ");
            first = false;
            sb.append(name.apply(s.code()).toLowerCase(Locale.ROOT));
            if (s.blocks() > 1) sb.append(" x").append(s.blocks());
            sb.append(", ").append(Math.max(1, Math.round(s.nearest()))).append(s.nearest() < 1.5 ? " block" : " blocks");
            sb.append(", ").append(s.side() < -0.33 ? "left" : s.side() > 0.33 ? "right" : "centre");
            if (!s.drawn()) sb.append(" (too thin to show in the picture)");
            String text = signs == null ? null : signs.get(s.x() + "," + s.y() + "," + s.z());
            if (text != null && !text.isBlank()) sb.append(", reading \"").append(text).append('"');
        }
        return sb.append('.').toString();
    }
}
