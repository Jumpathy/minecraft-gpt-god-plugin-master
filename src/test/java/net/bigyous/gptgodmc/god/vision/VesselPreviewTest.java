package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.bigyous.gptgodmc.god.vessel.VesselSkin;
import net.bigyous.gptgodmc.god.vessel.VesselTextures;

/**
 * Draws the vessel's display entities as shaded boxes (block-model face shading plus the glowing
 * outline) to build/test-renders/, so the model can be judged without a game client.
 */
class VesselPreviewTest {
    static final int CELL_W = 300, CELL_H = 400;
    static final double PX_PER_BLOCK = 96;
    static double zoom = PX_PER_BLOCK;
    static final Map<Material, Integer> COLORS = new EnumMap<>(Material.class);

    static {
        COLORS.put(Material.PURPLE_CONCRETE, 0x641F9C);
        COLORS.put(Material.WHITE_CONCRETE, 0xCFD5D6);
        COLORS.put(Material.BLACK_CONCRETE, 0x080A0F);
        COLORS.put(Material.OBSIDIAN, 0x0F0A18);
        COLORS.put(Material.AMETHYST_BLOCK, 0x8561BF);
        COLORS.put(Material.PEARLESCENT_FROGLIGHT, 0xECDCE4);
        COLORS.put(Material.PURPLE_WOOL, 0x792AAC);
        COLORS.put(Material.SMOOTH_SANDSTONE, 0xDFD6AA);
        COLORS.put(Material.LIGHT_BLUE_CONCRETE, 0x2389C6);
        COLORS.put(Material.GOLD_BLOCK, 0xF6D03D);
        COLORS.put(Material.WHITE_WOOL, 0xE9ECEC);
        COLORS.put(Material.YELLOW_CONCRETE, 0xF0AF15);
        COLORS.put(Material.RED_CONCRETE, 0x8E2020);
        COLORS.put(Material.OCHRE_FROGLIGHT, 0xF0D278);
        COLORS.put(Material.POLISHED_BLACKSTONE, 0x353038);
        COLORS.put(Material.MAGMA_BLOCK, 0xC2541C);
        COLORS.put(Material.RED_WOOL, 0xA02722);
        COLORS.put(Material.CRYING_OBSIDIAN, 0x200A3C);
        COLORS.put(Material.REDSTONE_BLOCK, 0xD01A0A);
        COLORS.put(Material.SEA_LANTERN, 0xD8ECE6);
        // The mythic looks' blocks (approximate map colors).
        Object[][] extra = { { Material.BLUE_CONCRETE, 0x2C2E8F }, { Material.BIRCH_PLANKS, 0xC0AF79 },
                { Material.LIGHT_GRAY_CONCRETE, 0x7D7D73 }, { Material.LIGHT_GRAY_WOOL, 0x8E8E86 },
                { Material.GRAY_CONCRETE, 0x36393D }, { Material.BROWN_CONCRETE, 0x603C20 },
                { Material.IRON_BLOCK, 0xDCDCDC }, { Material.GRAY_WOOL, 0x3E4447 }, { Material.BLACK_WOOL, 0x141519 },
                { Material.PRISMARINE_BRICKS, 0x63AB9E }, { Material.WARPED_WART_BLOCK, 0x167E86 },
                { Material.DARK_PRISMARINE, 0x335B4C }, { Material.BRAIN_CORAL_BLOCK, 0xCF5B9F },
                { Material.CYAN_CONCRETE, 0x157788 }, { Material.LIGHT_BLUE_WOOL, 0x3AAFD9 },
                { Material.SHROOMLIGHT, 0xF09245 }, { Material.NETHERITE_BLOCK, 0x433E40 },
                { Material.NETHER_WART_BLOCK, 0x720B0B }, { Material.COPPER_BLOCK, 0xC06C50 },
                { Material.CUT_COPPER, 0xBF6B51 }, { Material.BROWN_TERRACOTTA, 0x4D3324 },
                { Material.TERRACOTTA, 0x985E43 }, { Material.GLOWSTONE, 0xD8B060 }, { Material.LAPIS_BLOCK, 0x1F438C },
                { Material.ORANGE_CONCRETE, 0xE06100 }, { Material.SPRUCE_WOOD, 0x3A2A15 },
                { Material.VERDANT_FROGLIGHT, 0xD4E8C8 }, { Material.AZALEA_LEAVES, 0x5A7426 },
                { Material.MOSS_BLOCK, 0x596E2D }, { Material.DARK_OAK_WOOD, 0x3C2E1A }, { Material.ROOTED_DIRT, 0x90684D },
                { Material.MUSHROOM_STEM, 0xCBC4B9 }, { Material.FLOWERING_AZALEA_LEAVES, 0x6A7A44 },
                { Material.PACKED_ICE, 0x8DB4FE }, { Material.SNOW_BLOCK, 0xF9FEFE }, { Material.BLUE_ICE, 0x74A8FD },
                { Material.CYAN_TERRACOTTA, 0x575B5B }, { Material.POLISHED_DEEPSLATE, 0x484849 },
                { Material.BONE_BLOCK, 0xD1CDB2 }, { Material.GREEN_TERRACOTTA, 0x4C532A },
                { Material.GRAY_TERRACOTTA, 0x392A23 }, { Material.BLACK_TERRACOTTA, 0x251610 },
                { Material.BLUE_TERRACOTTA, 0x4A3B5B }, { Material.QUARTZ_BLOCK, 0xEBE5DE },
                { Material.RAW_GOLD_BLOCK, 0xDDA92E }, { Material.SMOOTH_BASALT, 0x48484E },
                { Material.EXPOSED_COPPER, 0xA17E68 }, { Material.GREEN_CONCRETE, 0x495B24 },
                { Material.YELLOW_WOOL, 0xF8C527 }, { Material.CALCITE, 0xDFE0DC },
                { Material.LIGHT_BLUE_TERRACOTTA, 0x716C89 }, { Material.BLACKSTONE, 0x2A2329 }, { Material.SCULK, 0x0C1E24 },
                { Material.DEEPSLATE, 0x505052 }, { Material.OXIDIZED_COPPER, 0x52A284 },
                { Material.SMOOTH_QUARTZ, 0xECE6DF }, { Material.DIAMOND_BLOCK, 0x62EDE4 },
                { Material.MAGENTA_CONCRETE, 0xA9309F }, { Material.MAGENTA_WOOL, 0xBD44B3 },
                { Material.WHITE_TERRACOTTA, 0xD1B2A1 }, { Material.PINK_WOOL, 0xED8DAC },
                { Material.PINK_CONCRETE, 0xD5658E }, { Material.LIME_CONCRETE, 0x5EA818 },
                { Material.CHERRY_LEAVES, 0xE5ADC2 }, { Material.BLUE_WOOL, 0x35399D } };
        for (Object[] e : extra) COLORS.put((Material) e[0], (Integer) e[1]);
    }

    record View(String name, double yaw, double pitch) {
    }

    static final List<View> VIEWS = List.of(new View("front", 0, 0.12), new View("three-quarter", 0.65, 0.22),
            new View("side", Math.PI / 2, 0.05), new View("back", Math.PI, 0.12));

    @Test
    void everyMaterialHasAPreviewColor() {
        for (String name : VesselLook.NAMES) {
            VesselLook look = VesselLook.resolve(name, null);
            for (Material m : look.materials().values()) {
                assertTrue(COLORS.containsKey(m), "no preview color for " + m + " in " + name);
            }
        }
    }

    @Test
    void lookResolution() {
        assertEquals("radiant", VesselLook.resolve("auto", "benevolent").name());
        assertEquals("infernal", VesselLook.resolve("auto", "evil").name());
        assertEquals("void", VesselLook.resolve("auto", "cosmic").name());
        assertEquals("shade", VesselLook.resolve("auto", "chill").name());
        assertEquals("shade", VesselLook.resolve("nonsense", null).name());
    }

    @Test
    void sizesAreMultiplesOfAPlayer() {
        assertEquals(1.8, VesselModel.HEIGHT_PX * VesselModel.unit(VesselModel.scaleForSize(1.0)), 1e-4);
        assertEquals(2.7, VesselModel.HEIGHT_PX * VesselModel.unit(VesselModel.scaleForSize(VesselModel.DEFAULT_SIZE)),
                1e-4);
        assertEquals(1.5, VesselModel.SIZES.get("lordly").doubleValue());
        assertEquals(3.0, VesselModel.parseSize(" Towering ").doubleValue());
        assertEquals(2.5, VesselModel.parseSize("2.5x").doubleValue());
        assertEquals(4.0, VesselModel.parseSize("4").doubleValue());
        assertNull(VesselModel.parseSize("huge"));
        assertNull(VesselModel.parseSize("-2"));
        assertNull(VesselModel.parseSize(null));
        assertEquals("towering (3x a mortal's height, 5.4 blocks tall)", VesselModel.describeSize(3));
        assertEquals("2.5x a mortal's height, 4.5 blocks tall", VesselModel.describeSize(2.5));
        double last = 0;
        for (double v : VesselModel.SIZES.values()) {
            assertTrue(v > last, "sizes ascend");
            last = v;
        }
    }

    /** Every named size at one scale, each beside a grey silhouette as tall as a player. */
    @Test
    void renderSizes() throws IOException {
        VesselLook look = VesselLook.shade();
        VesselModel.Pose pose = VesselModel.pose("idle", 0, 0, 0, null, null);
        List<String> names = new ArrayList<>(VesselModel.SIZES.keySet());
        double ppb = 28;
        BufferedImage sheet = new BufferedImage(CELL_W * names.size(), CELL_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int c = 0; c < names.size(); c++) {
            double size = VesselModel.SIZES.get(names.get(c));
            List<VesselModel.Placed> parts = VesselModel.place(look, pose, VesselModel.scaleForSize(size));
            BufferedImage cell = render(parts, VIEWS.get(1), look.outline().asRGB(), List.of(), 0.3, ppb,
                    VesselTextures.atlas(look.name()), 0, null);
            g.drawImage(cell, c * CELL_W, 0, null);
            int ground = CELL_H - 30;
            int w = (int) Math.round(0.6 * ppb), h = (int) Math.round(VesselModel.PLAYER_BLOCKS * ppb);
            int head = (int) Math.round(0.5 * ppb);
            g.setColor(new java.awt.Color(0x9A9AA8));
            g.fillRect(c * CELL_W + 22, ground - h + head, w, h - head);
            g.fillRect(c * CELL_W + 22 + (w - head) / 2, ground - h, head, head);
            label(g, names.get(c) + "  " + String.format(java.util.Locale.ROOT, "%.1fx player, %.1f blocks", size,
                    size * VesselModel.PLAYER_BLOCKS),
                    c * CELL_W, 0);
        }
        g.dispose();
        RendererTest.save(sheet, "vessel-sizes");
    }

    @Test
    void renderPoses() throws IOException {
        VesselLook look = VesselLook.shade();
        record Row(String label, VesselModel.Pose pose) {
        }
        List<Row> rows = List.of(
                new Row("idle", VesselModel.pose("idle", 0, 0, 0, null, null)),
                new Row("walk", VesselModel.pose("idle", 0, 1, 0, null, null)),
                new Row("look", VesselModel.pose("idle", 0, 0, 0, new Vector3f(0.8f, 0.35f, 1), null)),
                new Row("raise_arms", VesselModel.pose("raise_arms", 0, 0, 0, null, null)),
                new Row("spread_arms", VesselModel.pose("spread_arms", 0, 0, 0, null, null)),
                new Row("point", VesselModel.pose("point", 0, 0, 0, null, new Vector3f(-0.4f, 0.1f, 1))),
                new Row("bow", VesselModel.pose("bow", 0, 0, 0, null, null)));
        BufferedImage sheet = new BufferedImage(CELL_W * VIEWS.size(), CELL_H * rows.size(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int r = 0; r < rows.size(); r++) {
            List<VesselModel.Placed> parts = VesselModel.place(look, rows.get(r).pose(), 1.2);
            assertTrue(parts.size() > 20 && parts.size() < 60, "part count " + parts.size());
            for (int c = 0; c < VIEWS.size(); c++) {
                BufferedImage cell = render(parts, VIEWS.get(c), look.outline().asRGB(), List.of(), 0, PX_PER_BLOCK,
                        VesselTextures.atlas(look.name()), 0, null);
                g.drawImage(cell, c * CELL_W, r * CELL_H, null);
                label(g, rows.get(r).label() + " / " + VIEWS.get(c).name(), c * CELL_W, r * CELL_H);
            }
        }
        g.dispose();
        RendererTest.save(sheet, "vessel-poses");
    }

    @Test
    void renderLooks() throws IOException {
        List<View> views = List.of(VIEWS.get(0), VIEWS.get(1));
        BufferedImage sheet = new BufferedImage(CELL_W * VesselLook.NAMES.size(), CELL_H * views.size(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int c = 0; c < VesselLook.NAMES.size(); c++) {
            VesselLook look = VesselLook.resolve(VesselLook.NAMES.get(c), null);
            List<VesselModel.Placed> parts = VesselModel.place(look,
                    VesselModel.pose("speak", 0, 0, 0, new Vector3f(0.3f, 0, 1), null), 1.2);
            for (int r = 0; r < views.size(); r++) {
                g.drawImage(render(parts, views.get(r), look.outline().asRGB()), c * CELL_W, r * CELL_H, null);
                label(g, look.name() + " (blocks) / " + views.get(r).name(), c * CELL_W, r * CELL_H);
            }
        }
        g.dispose();
        RendererTest.save(sheet, "vessel-looks");
    }

    /** Every look in its own skin (the resource pack's textures): front, three-quarter, back and a close-up. */
    @Test
    void renderSkins() throws IOException {
        List<View> views = List.of(VIEWS.get(0), VIEWS.get(1), VIEWS.get(3), new View("close-up", 0.45, 0.1));
        BufferedImage sheet = new BufferedImage(CELL_W * views.size(), CELL_H * VesselLook.NAMES.size(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int r = 0; r < VesselLook.NAMES.size(); r++) {
            VesselLook look = VesselLook.resolve(VesselLook.NAMES.get(r), null);
            VesselTextures.Atlas atlas = VesselTextures.atlas(look.name());
            List<VesselModel.Placed> parts = VesselModel.place(look,
                    VesselModel.pose("speak", 0, 0, 0, new Vector3f(0.3f, 0, 1), null), 1.2);
            for (int c = 0; c < views.size(); c++) {
                boolean close = c == 3;
                raise = close ? 330 : 0;
                BufferedImage cell = render(parts, views.get(c), look.outline().asRGB(), List.of(), 0,
                        close ? 210 : PX_PER_BLOCK, atlas, 0, null);
                raise = 0;
                g.drawImage(cell, c * CELL_W, r * CELL_H, null);
                label(g, look.name() + " / " + views.get(c).name(), c * CELL_W, r * CELL_H);
            }
        }
        g.dispose();
        RendererTest.save(sheet, "vessel-skins");
    }

    /** The animation: one look's three-quarter view through every frame of its atlas. */
    @Test
    void renderSkinFrames() throws IOException {
        int n = VesselSkin.FRAMES / 2;
        BufferedImage sheet = new BufferedImage(CELL_W * n, CELL_H * VesselLook.NAMES.size(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int r = 0; r < VesselLook.NAMES.size(); r++) {
            VesselLook look = VesselLook.resolve(VesselLook.NAMES.get(r), null);
            VesselTextures.Atlas atlas = VesselTextures.atlas(look.name());
            List<VesselModel.Placed> parts = VesselModel.place(look,
                    VesselModel.pose("idle", 0, 0, 0, new Vector3f(0.2f, 0, 1), null), 1.2);
            for (int c = 0; c < n; c++) {
                raise = 330;
                g.drawImage(render(parts, new View("f", 0.3, 0.08), look.outline().asRGB(), List.of(), 0, 210,
                        atlas, c * 2, null), c * CELL_W, r * CELL_H, null);
                raise = 0;
                label(g, look.name() + " / frame " + (c * 2), c * CELL_W, r * CELL_H);
            }
        }
        g.dispose();
        RendererTest.save(sheet, "vessel-skin-frames");
    }

    /** The raw atlases (first frame), scaled up, as they go into the resource pack. */
    @Test
    void renderAtlases() throws IOException {
        VesselSkin.Layout layout = VesselSkin.LAYOUT;
        int k = 3;
        BufferedImage sheet = new BufferedImage(layout.width() * k, layout.height() * k * VesselLook.NAMES.size(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x2A2233));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < VesselLook.NAMES.size(); i++) {
            BufferedImage f = VesselTextures.atlas(VesselLook.NAMES.get(i)).frames()[0];
            g.drawImage(f, 0, i * layout.height() * k, layout.width() * k, layout.height() * k, null);
        }
        g.dispose();
        RendererTest.save(sheet, "vessel-atlases");
    }

    /** The look with a mood's eyes and glow. */
    static VesselLook moodLook(VesselLook l, VesselMood m) {
        Map<VesselLook.Slot, Material> mats = new EnumMap<>(l.materials());
        if (m.eyes != null) mats.put(VesselLook.Slot.EYES, m.eyes);
        return new VesselLook(l.name(), mats, l.ornament(), l.cape(), m.outline != null ? m.outline : l.outline(),
                l.mote(), l.smokeFrom(), l.smokeTo(), l.sparkle());
    }

    static List<Spark> wingSparks(VesselMood.Wings style, VesselModel.Pose pose, float time, double scale) {
        boolean angel = style == VesselMood.Wings.ANGEL;
        float unit = VesselModel.unit(scale);
        List<Spark> out = new ArrayList<>();
        for (VesselModel.Dot d : VesselModel.wings(style, time, VesselModel.frames(pose))) {
            int rgb = d.edge() ? (angel ? 0xFFD54A : 0xD41A1A) : (angel ? 0xFFFFFF : 0x4A0808);
            out.add(new Spark(new Vector3f(d.pos()).mul(unit), rgb));
        }
        return out;
    }

    @Test
    void renderMoods() throws IOException {
        List<View> views = List.of(VIEWS.get(1), VIEWS.get(3));
        VesselMood[] moods = VesselMood.values();
        BufferedImage sheet = new BufferedImage(CELL_W * moods.length, CELL_H * views.size(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int c = 0; c < moods.length; c++) {
            VesselMood mood = moods[c];
            VesselLook look = moodLook(VesselLook.shade(), mood);
            float air = mood.rise > 0.5 ? 1 : 0;
            VesselModel.Pose pose = VesselModel.pose(new VesselModel.Motion("idle", 20, 37, 0, 0, air, 0, mood,
                    new Vector3f(0.2f, -0.4f, 1), null));
            List<VesselModel.Placed> parts = VesselModel.place(look, pose, 1.2);
            List<Spark> sparks = wingSparks(mood.wings, pose, 37, 1.2);
            for (int r = 0; r < views.size(); r++) {
                String eyes = mood.eyes != null ? VesselSkin.eyeKey(mood.id()) : null;
                g.drawImage(render(parts, views.get(r), look.outline().asRGB(), sparks, air * 0.5, 62,
                        VesselTextures.atlas(look.name()), 0, eyes), c * CELL_W, r * CELL_H, null);
                label(g, mood.id() + " / " + views.get(r).name(), c * CELL_W, r * CELL_H);
            }
        }
        g.dispose();
        RendererTest.save(sheet, "vessel-moods");
    }

    @Test
    void renderMoves() throws IOException {
        record Move(String label, VesselModel.Motion motion) {
        }
        Vector3f ahead = new Vector3f(0.3f, -0.2f, 1);
        List<Move> moves = List.of(
                new Move("levitate", new VesselModel.Motion("idle", 0, 37, 0, 0, 1, 0, VesselMood.CALM, ahead, null)),
                new Move("fly", new VesselModel.Motion("idle", 0, 37, 0, 0, 1, 1, VesselMood.CALM, null, null)),
                new Move("meditate", new VesselModel.Motion("meditate", 40, 37, 0, 0, 1, 0, VesselMood.CALM, null,
                        null)),
                new Move("slam (rise)", new VesselModel.Motion("slam", 24, 37, 0, 0, 0, 0, VesselMood.CALM, null,
                        null)),
                new Move("slam (hit)", new VesselModel.Motion("slam", 34, 37, 0, 0, 0, 0, VesselMood.CALM, null,
                        null)),
                new Move("clap", new VesselModel.Motion("clap", 20, 37, 0, 0, 0, 0, VesselMood.CALM, null, null)),
                new Move("roar", new VesselModel.Motion("roar", 20, 37, 0, 0, 0, 0, VesselMood.CALM, null, null)),
                new Move("summon", new VesselModel.Motion("summon", 60, 37, 0, 0, 0, 0, VesselMood.CALM, null, null)),
                new Move("wave", new VesselModel.Motion("wave", 12, 37, 0, 0, 0, 0, VesselMood.CALM, ahead, null)),
                new Move("fold_arms", new VesselModel.Motion("fold_arms", 20, 37, 0, 0, 0, 0, VesselMood.CALM, ahead,
                        null)),
                new Move("laugh", new VesselModel.Motion("laugh", 20, 37, 0, 0, 0, 0, VesselMood.CALM, null, null)));
        List<View> views = List.of(VIEWS.get(1), VIEWS.get(2));
        BufferedImage sheet = new BufferedImage(CELL_W * moves.size(), CELL_H * views.size(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        VesselLook look = VesselLook.shade();
        for (int c = 0; c < moves.size(); c++) {
            VesselModel.Motion m = moves.get(c).motion();
            List<VesselModel.Placed> parts = VesselModel.place(look, VesselModel.pose(m), 1.2);
            for (int r = 0; r < views.size(); r++) {
                g.drawImage(render(parts, views.get(r), look.outline().asRGB(), List.of(), m.air() * 0.5, 80,
                        VesselTextures.atlas(look.name()), 0, null), c * CELL_W, r * CELL_H, null);
                label(g, moves.get(c).label() + " / " + views.get(r).name(), c * CELL_W, r * CELL_H);
            }
        }
        g.dispose();
        RendererTest.save(sheet, "vessel-moves");
    }

    @Test
    void wingsSpreadFromTheBack() {
        VesselModel.Pose pose = VesselModel.pose("idle", 0, 0, 0, null, null);
        for (VesselMood.Wings style : List.of(VesselMood.Wings.ANGEL, VesselMood.Wings.DEMON)) {
            List<VesselModel.Dot> dots = VesselModel.wings(style, 10, VesselModel.frames(pose));
            assertTrue(dots.size() > 40 && dots.size() < 260, style + " dot count " + dots.size());
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            for (VesselModel.Dot d : dots) {
                assertTrue(d.pos().z < 0, style + " wing in front of the back: " + d.pos());
                minX = Math.min(minX, d.pos().x);
                maxX = Math.max(maxX, d.pos().x);
            }
            assertTrue(maxX > 10 && minX < -10, style + " wings too narrow: " + minX + ".." + maxX);
            assertEquals(maxX, -minX, 1e-3);
        }
        assertTrue(VesselModel.wings(VesselMood.Wings.NONE, 0, VesselModel.frames(pose)).isEmpty());
    }

    @Test
    void everyGestureHasALength() {
        for (String g : VesselModel.GESTURES) {
            VesselModel.Pose p = VesselModel.pose(new VesselModel.Motion(g, 10, 5, 0, 0, 0, 0, VesselMood.CALM,
                    null, null));
            assertTrue(Float.isFinite(p.headPitch) && Float.isFinite(p.leftArm.w), g);
        }
        assertEquals(VesselMood.WRATHFUL, VesselMood.parse("angry"));
        assertEquals(VesselMood.EXALTED, VesselMood.parse("Exalted"));
    }

    @Test
    void hornsStayAttachedAndSymmetric() {
        Vector3f root = VesselModel.hornPoint(0, 1);
        assertTrue(root.y < 8.0f && Math.abs(root.x) < 4f, "horn root must sit in the crown: " + root);
        for (float t = 0; t <= 1; t += 0.125f) {
            Vector3f a = VesselModel.hornPoint(t, 1), b = VesselModel.hornPoint(t, -1);
            assertEquals(a.x, -b.x, 1e-5);
            assertEquals(a.y, b.y, 1e-5);
        }
    }

    // ---- A tiny box rasterizer ---------------------------------------------------------------

    static BufferedImage render(List<VesselModel.Placed> parts, View view, int outlineRgb) {
        return render(parts, view, outlineRgb, List.of(), 0, PX_PER_BLOCK);
    }

    /** A colored particle, in blocks relative to the vessel's feet. */
    record Spark(Vector3f pos, int rgb) {
    }

    static BufferedImage render(List<VesselModel.Placed> parts, View view, int outlineRgb, List<Spark> sparks,
            double lift, double ppb) {
        return render(parts, view, outlineRgb, sparks, lift, ppb, null, 0, null);
    }

    /**
     * Draws the parts; with an atlas, every textured part is drawn with its custom skin the way the
     * resource pack maps it (horns keep their block color). eyeKey swaps in a mood's eyes.
     */
    static BufferedImage render(List<VesselModel.Placed> parts, View view, int outlineRgb, List<Spark> sparks,
            double lift, double ppb, VesselTextures.Atlas atlas, int frame, String eyeKey) {
        return render(parts, view, outlineRgb, sparks, lift, ppb, atlas, frame, eyeKey, null);
    }

    /** Something drawn before the body (into the picture and its depth), so the body is hidden behind it. */
    interface Prepass {
        void draw(BufferedImage img, double[] depth, Quaternionf cam);
    }

    static BufferedImage render(List<VesselModel.Placed> parts, View view, int outlineRgb, List<Spark> sparks,
            double lift, double ppb, VesselTextures.Atlas atlas, int frame, String eyeKey, Prepass pre) {
        zoom = ppb;
        BufferedImage img = new BufferedImage(CELL_W, CELL_H, BufferedImage.TYPE_INT_RGB);
        double[] depth = new double[CELL_W * CELL_H];
        boolean[] mask = new boolean[CELL_W * CELL_H];
        java.util.Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        int bg = 0x17111F;
        for (int y = 0; y < CELL_H; y++) {
            for (int x = 0; x < CELL_W; x++) {
                img.setRGB(x, y, y > groundY() ? 0x241B2E : bg);
            }
        }
        Quaternionf cam = new Quaternionf().rotationX((float) view.pitch()).rotateY((float) -view.yaw());
        if (pre != null) pre.draw(img, depth, cam);
        for (VesselModel.Placed p : parts) {
            int base = COLORS.getOrDefault(p.material(), 0xFF00FF);
            Transformation t = p.transformation();
            VesselSkin.Piece piece = p.part().piece();
            boolean textured = atlas != null && piece.textured();
            String key = piece == VesselSkin.Piece.EYE && eyeKey != null ? eyeKey : piece.id();
            boolean flat = piece == VesselSkin.Piece.EYE || piece == VesselSkin.Piece.GEM
                    || piece == VesselSkin.Piece.HALO;
            Vector3f[] corners = new Vector3f[8];
            for (int i = 0; i < 8; i++) {
                Vector3f c = new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1).mul(t.getScale());
                t.getLeftRotation().transform(c).add(t.getTranslation()).add(0, (float) lift, 0);
                cam.transform(c);
                corners[i] = c;
            }
            // axis, sign -> the four corner indices and the block-model shade of that face
            for (int axis = 0; axis < 3; axis++) {
                for (int s = 0; s < 2; s++) {
                    int[] idx = faceCorners(axis, s);
                    Vector3f n = new Vector3f(axis == 0 ? (s * 2 - 1) : 0, axis == 1 ? (s * 2 - 1) : 0,
                            axis == 2 ? (s * 2 - 1) : 0);
                    t.getLeftRotation().transform(n);
                    cam.transform(n);
                    if (n.z <= 0) continue;
                    double shade = axis == 1 ? (s == 1 ? 1.0 : 0.5) : axis == 2 ? 0.8 : 0.6;
                    if (!textured) {
                        int rgb = shadeRgb(base, shade);
                        Sampler flatColor = (u, v) -> rgb;
                        fillTri(img, depth, mask, corners, idx[0], idx[1], idx[2], null, flatColor);
                        fillTri(img, depth, mask, corners, idx[0], idx[2], idx[3], null, flatColor);
                        continue;
                    }
                    VesselSkin.Face face = VesselSkin.Face.of(axis, s);
                    double[][] st = new double[8][];
                    for (int i : idx) st[i] = face.st(i & 1, (i >> 1) & 1, (i >> 2) & 1);
                    double lit = flat ? 1.0 : 0.55 + 0.45 * shade;
                    Sampler skin = (u, v) -> shadeRgb(atlas.sample(key, face, u, v, frame), lit);
                    fillTri(img, depth, mask, corners, idx[0], idx[1], idx[2], st, skin);
                    fillTri(img, depth, mask, corners, idx[0], idx[2], idx[3], st, skin);
                }
            }
        }
        // Glowing outline: a two-pixel band just outside the silhouette.
        for (int y = 0; y < CELL_H; y++) {
            for (int x = 0; x < CELL_W; x++) {
                if (mask[y * CELL_W + x]) continue;
                boolean near = false;
                for (int dy = -2; dy <= 2 && !near; dy++) {
                    for (int dx = -2; dx <= 2 && !near; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < CELL_W && ny < CELL_H && mask[ny * CELL_W + nx]) near = true;
                    }
                }
                if (near) img.setRGB(x, y, outlineRgb);
            }
        }
        for (Spark sp : sparks) {
            Vector3f c = cam.transform(new Vector3f(sp.pos()).add(0, (float) lift, 0));
            int px = (int) sx(c), py = (int) sy(c);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = px + dx, y = py + dy;
                    if (x < 0 || y < 0 || x >= CELL_W || y >= CELL_H) continue;
                    if (c.z + 0.05 >= depth[y * CELL_W + x]) img.setRGB(x, y, sp.rgb());
                }
            }
        }
        return img;
    }

    static int groundY() {
        return (int) (CELL_H - 30);
    }

    private static int[] faceCorners(int axis, int s) {
        List<Integer> out = new ArrayList<>();
        int[][] order = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
        for (int[] o : order) {
            int x, y, z;
            if (axis == 0) {
                x = s;
                y = o[0];
                z = o[1];
            } else if (axis == 1) {
                x = o[0];
                y = s;
                z = o[1];
            } else {
                x = o[0];
                y = o[1];
                z = s;
            }
            out.add(x | (y << 1) | (z << 2));
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int shadeRgb(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static double sx(Vector3f v) {
        return CELL_W / 2.0 + v.x * zoom;
    }

    /** Pixels the picture is shifted down, for close-ups of the upper body. */
    static double raise = 0;

    static double sy(Vector3f v) {
        return groundY() - v.y * zoom + raise;
    }

    interface Sampler {
        int at(double s, double t);
    }

    private static void fillTri(BufferedImage img, double[] depth, boolean[] mask, Vector3f[] corners, int ia,
            int ib, int ic, double[][] st, Sampler color) {
        Vector3f a = corners[ia], b = corners[ib], c = corners[ic];
        double ax = sx(a), ay = sy(a), bx = sx(b), by = sy(b), cx = sx(c), cy = sy(c);
        int minX = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = (int) Math.min(CELL_W - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = (int) Math.min(CELL_H - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
        double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1e-9) return;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area;
                double w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                double z = w0 * a.z + w1 * b.z + w2 * c.z;
                int i = y * CELL_W + x;
                if (z > depth[i]) {
                    depth[i] = z;
                    double u = 0, v = 0;
                    if (st != null) {
                        u = w0 * st[ia][0] + w1 * st[ib][0] + w2 * st[ic][0];
                        v = w0 * st[ia][1] + w1 * st[ib][1] + w2 * st[ic][1];
                    }
                    img.setRGB(x, y, color.at(u, v));
                    mask[i] = true;
                }
            }
        }
    }

    static void label(Graphics2D g, String text, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(new Color(220, 210, 235));
        g.drawString(text, x + 8, y + 18);
    }
}
