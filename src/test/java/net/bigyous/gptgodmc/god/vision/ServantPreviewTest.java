package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.living.HolyDays;
import net.bigyous.gptgodmc.god.living.ServantBody;
import net.bigyous.gptgodmc.god.living.ServantKind;
import net.bigyous.gptgodmc.god.living.ServantRig;
import net.bigyous.gptgodmc.god.living.ServantSkin;
import net.bigyous.gptgodmc.god.living.ServantTextures;
import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselSkin;

/**
 * The gods' servants, drawn to build/test-renders/: their own textured bodies as players with the
 * resource pack see them (servants.png, servant-&lt;kind&gt;.png, servant-atlases.png), the block rig over a
 * stand-in for the vanilla mob that everyone else sees (servants-blocks.png), and the holy-day boss bars
 * (holydays-bars.png).
 */
class ServantPreviewTest {
    static {
        Object[][] extra = { { Material.SNOW_BLOCK, 0xF2F7F7 }, { Material.NETHERITE_BLOCK, 0x3B3537 },
                { Material.NETHER_WART_BLOCK, 0x7A0A0A }, { Material.RED_NETHER_BRICKS, 0x5A0A0C },
                { Material.IRON_BLOCK, 0xD8D8D8 }, { Material.CYAN_CONCRETE, 0x157788 },
                { Material.GRAY_CONCRETE, 0x37393E }, { Material.BROWN_CONCRETE, 0x613C20 },
                { Material.GREEN_CONCRETE, 0x495B24 }, { Material.LIGHT_GRAY_CONCRETE, 0x7D7D73 },
                { Material.PURPLE_WOOL, 0x792AAC }, { Material.BIRCH_PLANKS, 0xC0AF79 } };
        for (Object[] e : extra) VesselPreviewTest.COLORS.putIfAbsent((Material) e[0], (Integer) e[1]);
    }

    /** A box in body space (center, size) as a placed part. */
    private static VesselModel.Placed box(Material m, float cx, float cy, float cz, float sx, float sy, float sz) {
        ServantRig.Piece p = new ServantRig.Piece("body", m, new Vector3f(cx, cy, cz), new Vector3f(sx, sy, sz),
                new Quaternionf());
        return placed(p);
    }

    private static VesselModel.Placed placed(ServantRig.Piece p) {
        VesselModel.Part part = new VesselModel.Part(VesselModel.Bone.BODY, VesselLook.Slot.ORNAMENT,
                VesselSkin.Piece.HORN, p.center(), p.size(), p.rot());
        return new VesselModel.Placed(p.material(), ServantRig.transform(p, 0), VesselModel.Bone.BODY, part);
    }

    /** Rough stand-ins for the vanilla mobs, at their scaled size. */
    static List<VesselModel.Placed> standIn(ServantKind k) {
        List<VesselModel.Placed> out = new ArrayList<>();
        switch (k) {
            case ANGEL -> { // an allay at 2.7x
                float s = (float) k.scale;
                out.add(box(Material.LIGHT_BLUE_CONCRETE, 0, 0.42f * s, 0, 0.31f * s, 0.31f * s, 0.31f * s));
                out.add(box(Material.CYAN_CONCRETE, 0, 0.18f * s, 0, 0.19f * s, 0.25f * s, 0.13f * s));
                out.add(box(Material.LIGHT_BLUE_CONCRETE, 0.13f * s, 0.2f * s, 0, 0.06f * s, 0.25f * s, 0.06f * s));
                out.add(box(Material.LIGHT_BLUE_CONCRETE, -0.13f * s, 0.2f * s, 0, 0.06f * s, 0.25f * s, 0.06f * s));
                out.add(box(Material.SEA_LANTERN, 0.05f * s, 0.44f * s, 0.16f * s, 0.05f * s, 0.05f * s, 0.01f * s));
                out.add(box(Material.SEA_LANTERN, -0.05f * s, 0.44f * s, 0.16f * s, 0.05f * s, 0.05f * s, 0.01f * s));
            }
            case IMP -> { // a vex at 1.5x
                float s = (float) k.scale;
                out.add(box(Material.LIGHT_GRAY_CONCRETE, 0, 0.6f * s, 0, 0.4f * s, 0.4f * s, 0.4f * s));
                out.add(box(Material.GRAY_CONCRETE, 0, 0.3f * s, 0, 0.3f * s, 0.4f * s, 0.2f * s));
                out.add(box(Material.REDSTONE_BLOCK, 0.1f * s, 0.62f * s, 0.2f * s, 0.08f * s, 0.06f * s, 0.01f * s));
                out.add(box(Material.REDSTONE_BLOCK, -0.1f * s, 0.62f * s, 0.2f * s, 0.08f * s, 0.06f * s, 0.01f * s));
            }
            case SHADE -> { // a wither skeleton
                out.add(box(Material.BLACK_CONCRETE, 0, 2.08f, 0, 0.58f, 0.58f, 0.58f));
                out.add(box(Material.POLISHED_BLACKSTONE, 0, 1.45f, 0, 0.55f, 0.75f, 0.2f));
                for (int x = -1; x <= 1; x += 2) {
                    out.add(box(Material.POLISHED_BLACKSTONE, x * 0.14f, 0.5f, 0, 0.14f, 1.0f, 0.14f));
                    out.add(box(Material.POLISHED_BLACKSTONE, x * 0.38f, 1.3f, 0.04f, 0.14f, 0.85f, 0.14f));
                }
                out.add(box(Material.NETHERITE_BLOCK, -0.38f, 1.0f, 0.42f, 0.06f, 0.06f, 0.8f));
            }
            case PRIEST -> { // a cleric villager
                out.add(box(Material.BIRCH_PLANKS, 0, 1.68f, 0, 0.5f, 0.6f, 0.5f));
                out.add(box(Material.BIRCH_PLANKS, 0, 1.55f, 0.3f, 0.12f, 0.25f, 0.12f));
                out.add(box(Material.PURPLE_WOOL, 0, 0.72f, 0, 0.56f, 1.44f, 0.42f));
                out.add(box(Material.GOLD_BLOCK, 0, 1.15f, 0.22f, 0.4f, 0.12f, 0.02f));
                out.add(box(Material.BIRCH_PLANKS, 0, 1.0f, 0.3f, 0.5f, 0.22f, 0.22f));
            }
            case GUARD -> { // an iron golem
                out.add(box(Material.IRON_BLOCK, 0, 1.95f, 0, 1.1f, 0.8f, 0.62f));
                out.add(box(Material.IRON_BLOCK, 0, 1.45f, 0, 0.7f, 0.35f, 0.45f));
                out.add(box(Material.IRON_BLOCK, 0, 2.5f, 0.08f, 0.5f, 0.6f, 0.5f));
                out.add(box(Material.BROWN_CONCRETE, 0, 2.38f, 0.36f, 0.12f, 0.24f, 0.1f));
                for (int x = -1; x <= 1; x += 2) {
                    out.add(box(Material.IRON_BLOCK, x * 0.7f, 1.3f, 0, 0.32f, 1.5f, 0.38f));
                    out.add(box(Material.IRON_BLOCK, x * 0.28f, 0.48f, 0, 0.38f, 0.96f, 0.34f));
                    out.add(box(Material.GREEN_CONCRETE, x * 0.45f, 1.7f, 0.32f, 0.12f, 0.5f, 0.02f));
                }
            }
        }
        return out;
    }

    static List<VesselModel.Placed> rig(ServantKind k, long tick, boolean moving) {
        List<VesselModel.Placed> out = new ArrayList<>();
        for (ServantRig.Piece p : ServantRig.pieces(k, tick, moving)) out.add(placed(p));
        return out;
    }

    /** A few of its ambient particles, scattered where the runner spawns them. */
    static List<ServantRender.Spark> sparks(ServantKind k, int seed) {
        int rgb = switch (k) {
            case ANGEL -> 0xFFF6D8;
            case IMP -> 0xFF9A2A;
            case SHADE -> 0x8FEFFF;
            case PRIEST -> 0xF3D9FF;
            case GUARD -> 0xBFF4FF;
        };
        java.util.Random rnd = new java.util.Random(seed * 31L + k.ordinal());
        List<ServantRender.Spark> out = new ArrayList<>();
        float top = ServantBody.top(k), wide = k == ServantKind.GUARD ? 1.1f : k == ServantKind.ANGEL ? 1.3f : 0.7f;
        for (int i = 0; i < 9; i++) {
            out.add(new ServantRender.Spark(new Vector3f((rnd.nextFloat() - 0.5f) * 2 * wide,
                    rnd.nextFloat() * top * 0.9f, (rnd.nextFloat() - 0.5f) * 2 * wide), rgb));
        }
        return out;
    }

    private record Shot(String name, double yaw, double pitch, ServantBody.Motion motion) {
    }

    private static List<Shot> shots(ServantKind k) {
        long still = k == ServantKind.IMP ? 1 : 4;
        ServantBody.Motion idle = ServantBody.Motion.idle(still);
        String act = k == ServantKind.PRIEST ? "walking" : k.flies ? "striking in flight" : "striking";
        return List.of(new Shot("front", 0, 0.12, idle), new Shot("three-quarter", 0.65, 0.22, idle),
                new Shot("side", Math.PI / 2, 0.05, idle), new Shot("back", Math.PI, 0.12, idle),
                new Shot(act, -0.6, 0.2, new ServantBody.Motion(k == ServantKind.IMP ? 3 : 12, true,
                        k == ServantKind.PRIEST ? -1 : 6)));
    }

    /** Pixels per block and how high it hovers, so each fills its cell. */
    private static double[] framing(ServantKind k, int cellH) {
        double scale = cellH / 460.0;
        return switch (k) {
            case ANGEL -> new double[] { 98 * scale, 0.75 };
            case IMP -> new double[] { 158 * scale, 0.6 };
            case SHADE -> new double[] { 145 * scale, 0 };
            case PRIEST -> new double[] { 140 * scale, 0 };
            case GUARD -> new double[] { 122 * scale, 0 };
        };
    }

    private static BufferedImage shot(ServantKind k, Shot s, int w, int h, int frame) {
        double[] f = framing(k, h);
        return ServantRender.render(k, ServantBody.parts(k, s.motion()), frame, s.yaw(), s.pitch(), w, h, f[0], f[1],
                k.glow, sparks(k, 7));
    }

    /** Its head and chest from close by, so the painting can be read. */
    private static BufferedImage closeUp(ServantKind k, int w, int h) {
        double ppb = framing(k, h)[0] * 2.3;
        double look = switch (k) {
            case ANGEL -> 1.35;
            case IMP -> 0.78;
            case SHADE -> 1.8;
            case PRIEST -> 1.72;
            case GUARD -> 1.95;
        };
        double lift = ((h - 30) - h / 2.0) / ppb - look;
        return ServantRender.render(k, ServantBody.parts(k, ServantBody.Motion.idle(k == ServantKind.IMP ? 1 : 4)), 2,
                0.42, 0.14, w, h, ppb, lift, k.glow, List.of());
    }

    /** The servants' own bodies, as players with the resource pack see them. */
    @Test
    void renderServants() throws IOException {
        ServantKind[] kinds = ServantKind.values();
        // One sheet per kind: front, three-quarter and back above; side, in action and close up below.
        int w = 540, h = 580;
        for (ServantKind k : kinds) {
            List<Shot> shots = shots(k);
            BufferedImage sheet = new BufferedImage(w * 3, h * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            int[] order = { 0, 1, 3, 2, 4 };
            for (int c = 0; c < order.length; c++) {
                int x = (c % 3) * w, y = (c / 3) * h;
                g.drawImage(shot(k, shots.get(order[c]), w, h, c), x, y, null);
                VesselPreviewTest.label(g, k.id + " / " + shots.get(order[c]).name(), x, y);
            }
            g.drawImage(closeUp(k, w, h), 2 * w, h, null);
            VesselPreviewTest.label(g, k.id + " / close up", 2 * w, h);
            g.dispose();
            RendererTest.save(sheet, "servant-" + k.id);
        }
        // ...and all five together.
        int cw = 300, ch = 380;
        BufferedImage all = new BufferedImage(cw * 4, ch * kinds.length, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = all.createGraphics();
        for (int r = 0; r < kinds.length; r++) {
            List<Shot> shots = shots(kinds[r]);
            int[] pick = { 0, 1, 3, 4 };
            for (int c = 0; c < pick.length; c++) {
                g.drawImage(shot(kinds[r], shots.get(pick[c]), cw, ch, c), c * cw, r * ch, null);
                VesselPreviewTest.label(g, kinds[r].id + " / " + shots.get(pick[c]).name(), c * cw, r * ch);
            }
        }
        g.dispose();
        RendererTest.save(all, "servants");
        assertTrue(new java.io.File(RendererTest.OUT, "servants.png").isFile());
    }

    /** Close looks at every hand that holds something, so a grip can be told from a thing floating near a hand. */
    @Test
    void renderGrips() throws IOException {
        record Grip(ServantKind kind, String what, Vector3f at, double yaw) {
        }
        List<Grip> grips = List.of(new Grip(ServantKind.ANGEL, "sword", new Vector3f(0, 0.86f, 0.27f), 0.55),
                new Grip(ServantKind.SHADE, "scythe", new Vector3f(-0.36f, 1.03f, 0.43f), -0.7),
                new Grip(ServantKind.PRIEST, "staff", new Vector3f(-0.4f, 1.02f, 0.3f), -0.7),
                new Grip(ServantKind.GUARD, "halberd", new Vector3f(-0.66f, 1.05f, 0.55f), -0.7),
                new Grip(ServantKind.GUARD, "shield", new Vector3f(0.68f, 1.15f, 0.7f), 1.1));
        int w = 380, h = 420;
        BufferedImage sheet = new BufferedImage(w * grips.size(), h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < grips.size(); i++) {
            Grip grip = grips.get(i);
            ServantKind k = grip.kind();
            g.drawImage(ServantRender.renderAt(k, ServantBody.parts(k, ServantBody.Motion.idle(4)), 2, grip.yaw(), 0.2,
                    w, h, k == ServantKind.GUARD ? 230 : 330, grip.at(), k.glow), i * w, 0, null);
            g.setColor(new Color(0x17111F));
            g.fillRect(i * w, 0, 150, 26);
            VesselPreviewTest.label(g, k.id + " / " + grip.what(), i * w, 0);
        }
        g.dispose();
        RendererTest.save(sheet, "servant-grips");
        assertTrue(new java.io.File(RendererTest.OUT, "servant-grips.png").isFile());
    }

    /** Every servant's painted atlas (first frame), enlarged on a checkerboard so alpha shows. */
    @Test
    void renderAtlases() throws IOException {
        int zoom = 3, pad = 24;
        int width = 0, height = pad;
        for (ServantKind k : ServantKind.values()) {
            width = Math.max(width, ServantSkin.layout(k).width() * zoom);
            height += ServantSkin.layout(k).height() * zoom + pad;
        }
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x17111F));
        g.fillRect(0, 0, width, height);
        int y = pad;
        for (ServantKind k : ServantKind.values()) {
            BufferedImage frame = ServantTextures.atlas(k).frames()[0];
            for (int ty = 0; ty < frame.getHeight(); ty++) {
                for (int tx = 0; tx < frame.getWidth(); tx++) {
                    int argb = frame.getRGB(tx, ty);
                    double a = (argb >>> 24) / 255.0;
                    int check = ((tx / 4 + ty / 4) & 1) == 0 ? 0x2A2233 : 0x211A29;
                    int r = (int) (((argb >> 16) & 255) * a + ((check >> 16) & 255) * (1 - a));
                    int gr = (int) (((argb >> 8) & 255) * a + ((check >> 8) & 255) * (1 - a));
                    int b = (int) ((argb & 255) * a + (check & 255) * (1 - a));
                    g.setColor(new Color(r, gr, b));
                    g.fillRect(tx * zoom, y + ty * zoom, zoom, zoom);
                }
            }
            VesselPreviewTest.label(g, k.id + " (" + frame.getWidth() + "x" + frame.getHeight() + ", "
                    + ServantSkin.FRAMES + " frames)", 0, y - pad);
            y += frame.getHeight() * zoom + pad;
        }
        g.dispose();
        RendererTest.save(sheet, "servant-atlases");
        assertTrue(new java.io.File(RendererTest.OUT, "servant-atlases.png").isFile());
    }

    /** What players without the pack (and Bedrock players) see: the vanilla mob wearing the block rig. */
    @Test
    void renderBlockFallback() throws IOException {
        List<VesselPreviewTest.View> views = List.of(VesselPreviewTest.VIEWS.get(0), VesselPreviewTest.VIEWS.get(1),
                VesselPreviewTest.VIEWS.get(3));
        int w = VesselPreviewTest.CELL_W, h = VesselPreviewTest.CELL_H;
        ServantKind[] kinds = ServantKind.values();
        int cols = views.size() + 1;
        BufferedImage sheet = new BufferedImage(w * cols, h * kinds.length, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int r = 0; r < kinds.length; r++) {
            ServantKind k = kinds[r];
            double ppb = k == ServantKind.GUARD || k == ServantKind.SHADE ? 95 : k == ServantKind.ANGEL ? 70 : 120;
            double lift = k.flies ? 0.8 : 0;
            long upstroke = ServantRig.beatTicks(k) / 4;
            List<VesselModel.Placed> parts = standIn(k);
            parts.addAll(rig(k, upstroke, false));
            for (int c = 0; c < views.size(); c++) {
                g.drawImage(VesselPreviewTest.render(parts, views.get(c), 0x17111F, List.of(), lift, ppb, null, 0, null),
                        c * w, r * h, null);
                VesselPreviewTest.label(g, k.id + " / " + views.get(c).name(), c * w, r * h);
            }
            // Mid-flight (downstroke) for fliers; the bare vanilla mob (what everyone sees under the rig) otherwise.
            List<VesselModel.Placed> last = standIn(k);
            String note;
            if (k.flies) {
                last.addAll(rig(k, 3L * ServantRig.beatTicks(k) / 4, true));
                note = k.id + " / flying, downstroke";
            } else {
                note = k.id + " / vanilla mob alone";
            }
            g.drawImage(VesselPreviewTest.render(last, VesselPreviewTest.VIEWS.get(1), 0x17111F, List.of(), lift, ppb,
                    null, 0, null), views.size() * w, r * h, null);
            VesselPreviewTest.label(g, note, views.size() * w, r * h);
        }
        g.dispose();
        RendererTest.save(sheet, "servants-blocks");
        assertTrue(new java.io.File(RendererTest.OUT, "servants-blocks.png").isFile());
    }

    @Test
    void renderHolyDayBars() throws IOException {
        long t = 1_700_000_000_000L, min = 60_000L;
        HolyDays h = new HolyDays();
        List<HolyDays.Day> days = new ArrayList<>();
        days.add(h.proclaim(new HolyDays.Request(HolyDays.Kind.FESTIVAL, "main", "Zel", "Festival of Embers",
                "Light fires on every hill", List.of("hostile_kills", "fish_caught"), "a golden apple each", "", null,
                null, null, null, false, 3, 15), t));
        HolyDays.Day war = h.proclaim(new HolyDays.Request(HolyDays.Kind.WAR, "main", "Zel", "War of Ash", "",
                List.of("hostile_kills"), "", "", null, null, "vorthak", "Vorthak", true, 0, 20), t);
        h.join(war, "Steve", "main");
        h.join(war, "Alex", "vorthak");
        h.credit("Steve", "hostile_kills", 9, null);
        h.credit("Alex", "rival_kills", 1, null);
        h.credit("Alex", "hostile_kills", 3, null);
        days.add(war);
        days.add(h.proclaim(new HolyDays.Request(HolyDays.Kind.RAID, "vorthak", "Vorthak", "Vorthak's Reckoning", "",
                List.of(), "", "", "Grief", null, null, null, false, 0, 4), t));
        HolyDays.Day hunt = h.proclaim(new HolyDays.Request(HolyDays.Kind.HUNT, "vorthak", "Vorthak", "The White Stag",
                "", List.of(), "", "", null, "ravager", null, null, false, 0, 10), t);
        h.award(hunt, "Alex", 6);
        days.add(hunt);
        days.add(h.proclaim(new HolyDays.Request(HolyDays.Kind.OMEN, "main", "Zel", "The Bleeding Moon",
                "The moon will bleed; the sea will open", List.of(), "", "", null, null, null, null, false, 0, 4320),
                t));
        int width = 820, rowH = 64;
        BufferedImage img = new BufferedImage(width, 30 + rowH * days.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x3A5A2A));
        g.fillRect(0, 0, width, img.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        int[][] colors = { { 0xF0D43A, 0x6B5A10 }, { 0xE0402A, 0x5A1A10 }, { 0xE0402A, 0x5A1A10 },
                { 0x5ED03A, 0x1F4A12 }, { 0xB04AE0, 0x3E1652 } };
        for (int i = 0; i < days.size(); i++) {
            HolyDays.Day d = days.get(i);
            long now = t + (d.kind() == HolyDays.Kind.FESTIVAL ? min : d.kind() == HolyDays.Kind.RAID ? 90_000 : 4 * min + 20_000);
            if (d.kind() != HolyDays.Kind.FESTIVAL) h.step(d, now);
            String text = h.bar(d, now);
            int y = 20 + i * rowH;
            g.setColor(Color.WHITE);
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, (width - tw) / 2, y + 14);
            int bw = 600, bx = (width - bw) / 2, by = y + 22;
            g.setColor(new Color(colors[i][1]));
            g.fillRect(bx, by, bw, 10);
            g.setColor(new Color(colors[i][0]));
            g.fillRect(bx, by, (int) (bw * HolyDays.progress(d, now)), 10);
            g.setStroke(new BasicStroke(1));
            g.setColor(Color.BLACK);
            g.drawRect(bx, by, bw, 10);
        }
        g.dispose();
        RendererTest.save(img, "holydays-bars");
        assertTrue(new java.io.File(RendererTest.OUT, "holydays-bars.png").isFile());
    }
}
