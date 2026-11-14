package net.bigyous.gptgodmc.god.build;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.structure.StructureRotation;

/**
 * Hand-made designs, generated in any {@link Style}. Each faces south (+z) before rotation.
 */
public final class Designs {
    /** centerOnTarget: built around the target (a prison); otherwise built beside it, facing it. */
    public record Design(String id, String description, boolean centerOnTarget, Function<Style, Plan> generator) {
    }

    public static final Map<String, Design> ALL = new LinkedHashMap<>();

    static {
        add(new Design("temple", "A grand colonnaded temple (15x21) with steps, a gabled roof and an altar inside.",
                false, Designs::temple));
        add(new Design("shrine", "A small open shrine (7x7) with four pillars, a roof and a lit pedestal.", false,
                Designs::shrine));
        add(new Design("cottage", "A cozy house (9x11) with a door, windows, bed, chest, furnace, chimney and "
                + "a gabled roof.", false, Designs::cottage));
        add(new Design("prison", "A sealed cell built AROUND the target (traps a mortal inside behind bars and a "
                + "locked iron door; has a bed).", true, Designs::prison));
        add(new Design("tower", "A 15-block watchtower with windows, a ladder inside and battlements on top.", false,
                Designs::tower));
        add(new Design("obelisk", "A tall tapering monument with a glowing tip.", false, Designs::obelisk));
        add(new Design("arena", "A circular combat arena (21 wide) with four gates and lanterns.", true,
                Designs::arena));
        add(new Design("statue", "A 14-block statue of you (your horned, violet likeness) on a pedestal.", false,
                Designs::statue));
        add(new Design("town", "A small town: eight cottages around a plaza with a shrine and paths (~60x60).",
                true, Designs::town));
    }

    private Designs() {
    }

    private static void add(Design d) {
        ALL.put(d.id(), d);
    }

    static Plan temple(Style s) {
        Plan p = new Plan();
        p.box(-7, 0, -10, 7, 0, 10, s.base());
        p.box(-6, 1, -9, 6, 1, 9, s.floor());
        for (int x = -3; x <= 3; x++) {
            p.stairs(x, 0, 11, s.roof(), BlockFace.NORTH, false);
            p.stairs(x, 1, 10, s.roof(), BlockFace.NORTH, false);
        }
        // Clear the hall and set the colonnade.
        p.box(-6, 2, -9, 6, 7, 9, Material.AIR);
        for (int z = -8; z <= 8; z += 2) {
            p.box(-6, 2, z, -6, 7, z, s.pillar());
            p.box(6, 2, z, 6, 7, z, s.pillar());
        }
        for (int x = -4; x <= 4; x += 2) {
            p.box(x, 2, -8, x, 7, -8, s.pillar());
            if (Math.abs(x) > 1) p.box(x, 2, 8, x, 7, 8, s.pillar());
        }
        p.box(-7, 8, -10, 7, 8, 10, s.trim());
        p.box(-6, 8, -9, 6, 8, 9, s.wall());
        // Gabled roof running front to back.
        for (int i = 0; i <= 7; i++) {
            int y = 9 + i;
            for (int z = -10; z <= 10; z++) {
                if (i == 7) {
                    p.set(0, y, z, s.roofSlab());
                } else {
                    p.stairs(-7 + i, y, z, s.roof(), BlockFace.EAST, false);
                    p.stairs(7 - i, y, z, s.roof(), BlockFace.WEST, false);
                }
            }
            for (int x = -6 + i; x <= 6 - i; x++) {
                p.set(x, y, -10, s.wall());
                p.set(x, y, 10, s.wall());
            }
        }
        p.set(0, 13, 10, s.accent());
        // Inner sanctum.
        p.walls(-3, 2, -7, 3, 7, 1, s.wall());
        p.box(-1, 2, 1, 1, 4, 1, Material.AIR);
        for (int z : new int[] { -5, -2 }) {
            p.box(-3, 4, z, -3, 5, z, s.glass());
            p.box(3, 4, z, 3, 5, z, s.glass());
        }
        p.box(-1, 2, -6, 1, 2, -6, s.trim());
        p.set(0, 3, -6, s.accent());
        p.lantern(-1, 3, -6, s.light(), false);
        p.lantern(1, 3, -6, s.light(), false);
        p.lantern(0, 7, -3, s.light(), true);
        p.lantern(-4, 7, 4, s.light(), true);
        p.lantern(4, 7, 4, s.light(), true);
        return p;
    }

    static Plan shrine(Style s) {
        Plan p = new Plan();
        p.box(-3, 0, -3, 3, 0, 3, s.base());
        p.box(-2, 1, -2, 2, 4, 2, Material.AIR);
        for (int[] c : new int[][] { { -2, -2 }, { 2, -2 }, { -2, 2 }, { 2, 2 } }) {
            p.box(c[0], 1, c[1], c[0], 3, c[1], s.pillar());
        }
        p.box(-3, 4, -3, 3, 4, 3, s.roofSlab());
        p.box(-1, 5, -1, 1, 5, 1, s.trim());
        p.set(0, 6, 0, s.accent());
        p.set(0, 1, 0, s.trim());
        p.lantern(0, 2, 0, s.light(), false);
        for (int x = -1; x <= 1; x++) p.stairs(x, 0, 4, s.roof(), BlockFace.NORTH, false);
        return p;
    }

    static Plan cottage(Style s) {
        Plan p = new Plan();
        p.box(-4, 0, -5, 4, 0, 5, s.base());
        p.box(-3, 0, -4, 3, 0, 4, s.floor());
        p.box(-3, 1, -4, 3, 4, 4, Material.AIR);
        p.walls(-4, 1, -5, 4, 4, 5, s.wall());
        for (int[] c : new int[][] { { -4, -5 }, { 4, -5 }, { -4, 5 }, { 4, 5 } }) {
            p.box(c[0], 1, c[1], c[0], 4, c[1], s.pillar());
        }
        p.door(0, 1, 5, s.door(), BlockFace.NORTH);
        for (int x : new int[] { -2, 2 }) p.box(x, 2, 5, x, 3, 5, s.glass());
        for (int z : new int[] { -2, 2 }) {
            p.box(-4, 2, z, -4, 3, z, s.glass());
            p.box(4, 2, z, 4, 3, z, s.glass());
        }
        p.box(0, 2, -5, 0, 3, -5, s.glass());
        // Roof, ridge running front to back, with a one-block overhang.
        for (int i = 0; i <= 5; i++) {
            int y = 5 + i;
            for (int z = -6; z <= 6; z++) {
                if (i == 5) {
                    p.set(0, y, z, s.roofSlab());
                } else {
                    p.stairs(-5 + i, y, z, s.roof(), BlockFace.EAST, false);
                    p.stairs(5 - i, y, z, s.roof(), BlockFace.WEST, false);
                }
            }
            for (int x = -4 + i; x <= 4 - i; x++) {
                if (i < 5) {
                    p.set(x, y, -5, s.wall());
                    p.set(x, y, 5, s.wall());
                }
            }
        }
        p.box(-3, 5, -4, 3, 5, 4, Material.AIR);
        p.set(0, 7, 5, s.glass());
        // Chimney with smoke.
        p.box(3, 0, -6, 3, 10, -6, s.accent());
        p.set(3, 11, -6, Material.CAMPFIRE);
        // Furniture.
        p.bed(-2, 1, -3, Material.RED_BED, BlockFace.NORTH);
        p.set(2, 1, -4, Material.CRAFTING_TABLE);
        p.facing(1, 1, -4, Material.FURNACE, BlockFace.SOUTH);
        p.facing(3, 1, 0, Material.CHEST, BlockFace.WEST);
        p.lantern(0, 4, 0, s.light(), true);
        p.set(-3, 1, 3, Material.POTTED_POPPY);
        for (int z = 6; z <= 9; z++) p.set(0, -1, z, s.path());
        return p;
    }

    static Plan prison(Style s) {
        Plan p = new Plan();
        Material wall = s.id().equals("rustic") ? Material.COBBLESTONE : s.wall();
        p.box(-2, -1, -2, 2, -1, 2, s.base());
        p.box(-2, 3, -2, 2, 3, 2, s.base());
        p.walls(-2, 0, -2, 2, 2, 2, wall);
        p.box(-1, 0, -1, 1, 2, 1, Material.AIR);
        p.bars(-1, 1, 2, Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST);
        p.bars(1, 1, 2, Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST);
        p.bars(-2, 1, 0, Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH);
        p.bars(2, 1, 0, Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH);
        p.door(0, 0, 2, Material.IRON_DOOR, BlockFace.NORTH);
        p.bed(-1, 0, -1, Material.GRAY_BED, BlockFace.NORTH);
        p.set(1, 0, -1, Material.CAULDRON);
        p.lantern(1, 2, 1, Material.LANTERN, true);
        return p;
    }

    static Plan tower(Style s) {
        Plan p = new Plan();
        int r = 3;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                boolean corner = Math.abs(x) == r && Math.abs(z) == r;
                if (corner) continue;
                p.set(x, 0, z, s.base());
                p.set(x, 7, z, s.floor());
                p.set(x, 13, z, s.floor());
                boolean edge = Math.abs(x) == r || Math.abs(z) == r
                        || (Math.abs(x) == r - 1 && Math.abs(z) == r - 1);
                for (int y = 1; y <= 12; y++) {
                    if (y == 7) continue;
                    p.set(x, y, z, edge && !(Math.abs(x) == r - 1 && Math.abs(z) == r - 1) ? s.wall()
                            : (Math.abs(x) == r - 1 && Math.abs(z) == r - 1 ? s.pillar() : Material.AIR));
                }
                if (edge && (x + z) % 2 == 0 && !(Math.abs(x) == r - 1 && Math.abs(z) == r - 1)) {
                    p.set(x, 14, z, s.wall());
                }
            }
        }
        for (int y = 1; y <= 13; y++) p.facing(0, y, -2, Material.LADDER, BlockFace.SOUTH);
        p.door(0, 1, 3, s.door(), BlockFace.NORTH);
        for (int y : new int[] { 4, 10 }) {
            p.set(-3, y, 0, s.glass());
            p.set(3, y, 0, s.glass());
            p.set(0, y, -3, s.glass());
        }
        p.set(0, 10, 3, s.glass());
        p.lantern(1, 6, 1, s.light(), true);
        p.lantern(2, 14, 2, s.light(), false);
        p.lantern(-2, 14, -2, s.light(), false);
        return p;
    }

    static Plan obelisk(Style s) {
        Plan p = new Plan();
        p.box(-2, 0, -2, 2, 0, 2, s.base());
        p.box(-1, 1, -1, 1, 1, 1, s.trim());
        p.box(-1, 2, -1, 1, 8, -1 + 2, s.wall());
        p.box(-1, 5, -1, 1, 5, 1, s.accent());
        p.box(0, 9, 0, 0, 14, 0, s.pillar());
        p.set(0, 15, 0, Material.SEA_LANTERN);
        p.facing(0, 16, 0, Material.END_ROD, BlockFace.UP);
        for (int[] c : new int[][] { { -2, -2 }, { 2, -2 }, { -2, 2 }, { 2, 2 } }) {
            p.lantern(c[0], 1, c[1], s.light(), false);
        }
        return p;
    }

    static Plan arena(Style s) {
        Plan p = new Plan();
        int r = 10;
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < r - 0.5) {
                    p.set(x, -1, z, Material.SAND);
                    for (int y = 0; y <= 4; y++) p.air(x, y, z);
                } else if (d <= r + 0.5) {
                    boolean gate = Math.abs(x) <= 1 || Math.abs(z) <= 1;
                    for (int y = 0; y <= 2; y++) {
                        if (gate && y <= 1) p.air(x, y, z);
                        else p.set(x, y, z, s.wall());
                    }
                    p.set(x, 3, z, (x + z) % 5 == 0 ? s.light() : s.roofSlab());
                }
            }
        }
        p.set(0, -1, 0, s.accent());
        return p;
    }

    static Plan statue(Style s) {
        Plan p = new Plan();
        Material body = s.id().equals("violet") || s.id().equals("divine") ? Material.PURPUR_BLOCK : s.wall();
        Material horn = Material.AMETHYST_BLOCK;
        p.box(-2, 0, -2, 2, 1, 2, s.base());
        p.box(-1, 2, -1, 1, 2, 1, s.trim());
        p.set(0, 3, 0, horn);
        p.box(-1, 4, -1, 1, 4, 0, Material.PURPLE_STAINED_GLASS);
        p.box(-1, 5, -1, 1, 8, 0, body);
        p.set(0, 6, 0, Material.CRYING_OBSIDIAN);
        p.box(-2, 6, -1, -2, 8, -1, body);
        p.box(2, 6, -1, 2, 8, -1, body);
        p.set(-2, 5, -1, Material.PEARLESCENT_FROGLIGHT);
        p.set(2, 5, -1, Material.PEARLESCENT_FROGLIGHT);
        p.set(0, 9, -1, body);
        p.box(-1, 10, -1, 1, 11, 0, body);
        p.set(-1, 11, 0, Material.PEARLESCENT_FROGLIGHT);
        p.set(1, 11, 0, Material.PEARLESCENT_FROGLIGHT);
        for (int side : new int[] { -1, 1 }) {
            p.set(2 * side, 11, -1, horn);
            p.set(2 * side, 12, -1, horn);
            p.set(3 * side, 12, 0, horn);
            p.set(3 * side, 11, 0, horn);
            p.set(3 * side, 10, 1, horn);
        }
        p.lantern(-2, 2, 2, s.light(), false);
        p.lantern(2, 2, 2, s.light(), false);
        return p;
    }

    static Plan town(Style s) {
        Plan p = new Plan();
        Style homes = Style.STYLES.get(s.id().equals("dark") ? "dark" : "rustic");
        // Plaza with a shrine in the middle.
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++)
                p.set(x, -1, z, (x + z) % 2 == 0 ? s.path() : s.base());
        p.merge(shrine(s), 0, 0, 0, StructureRotation.NONE);
        Plan house = cottage(homes);
        int d = 22;
        int[][] spots = { { 0, d }, { 0, -d }, { d, 0 }, { -d, 0 }, { 14, d }, { -14, d }, { 14, -d }, { -14, -d } };
        for (int[] spot : spots) {
            int x = spot[0];
            int z = spot[1];
            StructureRotation rot;
            if (Math.abs(z) >= Math.abs(x)) {
                rot = z > 0 ? StructureRotation.CLOCKWISE_180 : StructureRotation.NONE;
            } else {
                rot = x > 0 ? StructureRotation.CLOCKWISE_90 : StructureRotation.COUNTERCLOCKWISE_90;
            }
            p.merge(house, x, 0, z, rot);
        }
        // Paths from the plaza to each front door.
        for (int i = 9; i <= d - 6; i++) {
            p.set(0, -1, i, homes.path());
            p.set(0, -1, -i, homes.path());
            p.set(i, -1, 0, homes.path());
            p.set(-i, -1, 0, homes.path());
        }
        for (int i = 1; i <= 14; i++) {
            p.set(i, -1, d - 8, homes.path());
            p.set(-i, -1, d - 8, homes.path());
            p.set(i, -1, -(d - 8), homes.path());
            p.set(-i, -1, -(d - 8), homes.path());
        }
        return p;
    }
}
