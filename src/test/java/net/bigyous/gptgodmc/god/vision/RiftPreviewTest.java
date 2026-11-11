package net.bigyous.gptgodmc.god.vision;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.Rift;
import net.bigyous.gptgodmc.god.vessel.Rift.Palette;
import net.bigyous.gptgodmc.god.vessel.RiftTextures;
import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselTextures;
import net.bigyous.gptgodmc.god.vision.VesselPreviewTest.Spark;
import net.bigyous.gptgodmc.god.vision.VesselPreviewTest.View;

/**
 * Offline pictures of a god tearing its way in: build/test-renders/rift-textures.png (each palette's
 * painted rift through its animation, on night and on day), rift-arrival.png (the arrival frame by frame,
 * as resource-pack players and as particle-only players see it) and rift-palettes.png (five gods stepping
 * out of their own rifts).
 */
class RiftPreviewTest {
    static final int[] MOMENTS = { 6, 22, 40, 52, 60, 70, 80, 90 };
    static final View VIEW = new View("three-quarter", 0.45, 0.14);
    static final double PPB = 62;

    @Test
    void renderTextures() throws IOException {
        int zoom = 3, frames = 4, gap = 12;
        int cw = RiftTextures.W * zoom + gap, ch = RiftTextures.H * zoom + gap;
        Palette[] all = Palette.values();
        BufferedImage sheet = new BufferedImage(cw * (frames + 1), ch * all.length + 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new java.awt.Color(0x17111F));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int r = 0; r < all.length; r++) {
            BufferedImage[] f = RiftTextures.frames(all[r]);
            for (int c = 0; c <= frames; c++) {
                boolean day = c == frames;
                int bg = day ? 0x8DB6E8 : 0x17111F;
                BufferedImage img = f[day ? 0 : c * 2];
                int ox = c * cw + gap / 2, oy = 30 + r * ch + gap / 2;
                for (int y = 0; y < RiftTextures.H * zoom; y++) {
                    for (int x = 0; x < RiftTextures.W * zoom; x++) {
                        int px = img.getRGB(x / zoom, y / zoom);
                        sheet.setRGB(ox + x, oy + y, over(bg, px));
                    }
                }
            }
            VesselPreviewTest.label(g, all[r].id(), 0, 30 + r * ch);
        }
        VesselPreviewTest.label(g, "frames 0, 2, 4, 6 at night, and frame 0 by day", 0, 0);
        g.dispose();
        RendererTest.save(sheet, "rift-textures");
    }

    @Test
    void renderArrival() throws IOException {
        VesselLook look = VesselLook.shade();
        BufferedImage sheet = new BufferedImage(VesselPreviewTest.CELL_W * MOMENTS.length, VesselPreviewTest.CELL_H * 2,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int row = 0; row < 2; row++) {
            boolean painted = row == 0;
            for (int c = 0; c < MOMENTS.length; c++) {
                int t = MOMENTS[c];
                g.drawImage(moment(look, VesselModel.DEFAULT_SIZE, t, painted), c * VesselPreviewTest.CELL_W,
                        row * VesselPreviewTest.CELL_H, null);
                VesselPreviewTest.label(g, (painted ? "pack" : "particles") + "  tick " + t + "  " + phase(t),
                        c * VesselPreviewTest.CELL_W, row * VesselPreviewTest.CELL_H);
            }
        }
        g.dispose();
        RendererTest.save(sheet, "rift-arrival");
    }

    /** Leaving: the rift opens behind it, it turns and walks in, the far side takes it, the rift seals. */
    @Test
    void renderDeparture() throws IOException {
        VesselLook look = VesselLook.voidLook();
        double size = VesselModel.DEFAULT_SIZE, scale = VesselModel.scaleForSize(size), body = size * VesselModel.PLAYER_BLOCKS;
        double d = Rift.stepBack(body);
        double step = 0.26 * scale * 0.35;
        int walk = (int) Math.ceil(d / step) * 2;
        Rift.Timeline when = Rift.departure(walk);
        int[] moments = { 6, 18, 26, Rift.DEPART_WALK + walk / 2, Rift.DEPART_WALK + walk, when.climax() + 4, when.seal() + 4,
                when.shut() - 3 };
        BufferedImage sheet = new BufferedImage(VesselPreviewTest.CELL_W * moments.length, VesselPreviewTest.CELL_H,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        Palette pal = Palette.of(look.outline().asRGB());
        Quaternionf away = new Quaternionf().rotationY((float) Math.PI);
        for (int c = 0; c < moments.length; c++) {
            int t = moments[c];
            double z = -Math.min(d, Math.max(0, (t - Rift.DEPART_WALK) * step / 2));
            boolean walking = t >= Rift.DEPART_WALK && z > -d;
            double grow = t < when.climax() ? 1 : Math.max(0, 1 - (t - when.climax()) / 8.0);
            List<VesselModel.Placed> parts = new ArrayList<>();
            if (grow > 0) {
                VesselModel.Pose pose = VesselModel.pose("idle", t, walking ? 1 : 0, walking ? t * 0.35f : 0, null, null);
                double lift = body * 0.5 * (1 - grow);
                for (VesselModel.Placed p : VesselModel.place(look, pose, scale * Math.max(0.02, grow))) {
                    Transformation tr = p.transformation();
                    // It has turned its back on us, toward the rift.
                    Vector3f at = away.transform(new Vector3f(tr.getTranslation())).add(0, (float) lift, (float) z);
                    parts.add(new VesselModel.Placed(p.material(), new Transformation(at,
                            new Quaternionf(away).mul(tr.getLeftRotation()), tr.getScale(), tr.getRightRotation()), p.bone(),
                            p.part()));
                }
            }
            VesselPreviewTest.Prepass pre = (img, depth, cam) -> plane(img, depth, cam, pal, body, -d, t, when);
            g.drawImage(VesselPreviewTest.render(parts, VIEW, look.outline().asRGB(), List.of(), 0, PPB,
                    VesselTextures.atlas(look.name()), (t / 4) % 8, null, pre), c * VesselPreviewTest.CELL_W, 0, null);
            String what = t < when.open() ? "opening" : t < Rift.DEPART_WALK ? "open" : t < when.climax() ? "walking in"
                    : t < when.seal() ? "swallowed" : "sealing";
            VesselPreviewTest.label(g, "tick " + t + "  " + what, c * VesselPreviewTest.CELL_W, 0);
        }
        g.dispose();
        RendererTest.save(sheet, "rift-departure");
    }

    @Test
    void renderPalettes() throws IOException {
        String[] looks = { "shade", "radiant", "infernal", "tide", "grove" };
        BufferedImage sheet = new BufferedImage(VesselPreviewTest.CELL_W * looks.length, VesselPreviewTest.CELL_H,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int c = 0; c < looks.length; c++) {
            VesselLook look = VesselLook.byName(looks[c]);
            g.drawImage(moment(look, VesselModel.DEFAULT_SIZE, 70, true), c * VesselPreviewTest.CELL_W, 0, null);
            VesselPreviewTest.label(g, looks[c] + " / " + Palette.of(look.outline().asRGB()).id(), c * VesselPreviewTest.CELL_W, 0);
        }
        g.dispose();
        RendererTest.save(sheet, "rift-palettes");
    }

    private static String phase(int t) {
        if (t < Rift.CRACK) return "seam";
        if (t < Rift.OPEN) return "tearing";
        if (t < Rift.CLIMAX) return "open";
        if (t < Rift.CLIMAX + Rift.STEP_DELAY) return "forming";
        if (t < Rift.SEAL) return "stepping out";
        return "sealing";
    }

    /** One moment of the arrival: the rift (painted, or its particles) and the body where it is then. */
    static BufferedImage moment(VesselLook look, double size, int t, boolean painted) {
        double scale = VesselModel.scaleForSize(size);
        double body = size * VesselModel.PLAYER_BLOCKS;
        double d = Rift.stepBack(body);
        Palette pal = Palette.of(look.outline().asRGB());
        // Where the body is: forming in the rift, then walking out at the vessel's emerging pace.
        double stride = 0.26 * scale * 0.35 / 2; // blocks a tick
        int walkFrom = Rift.CLIMAX + Rift.STEP_DELAY;
        double z = -d + Math.max(0, (t - walkFrom) * stride);
        z = Math.min(0, z);
        boolean walking = t >= walkFrom && z < 0;
        double grow = t < Rift.CLIMAX ? 0 : Math.min(1, (t - Rift.CLIMAX) / 14.0);
        List<VesselModel.Placed> parts = new ArrayList<>();
        if (grow > 0) {
            VesselModel.Pose pose = VesselModel.pose("idle", t, walking ? 1 : 0, walking ? t * 0.35f : 0,
                    new Vector3f(0.2f, 0, 1), null);
            double lift = body * 0.5 * (1 - grow);
            for (VesselModel.Placed p : VesselModel.place(look, pose, scale * Math.max(0.02, grow))) {
                Transformation tr = p.transformation();
                Vector3f at = new Vector3f(tr.getTranslation()).add(0, (float) lift, (float) z);
                parts.add(new VesselModel.Placed(p.material(), new Transformation(at, tr.getLeftRotation(), tr.getScale(),
                        tr.getRightRotation()), p.bone(), p.part()));
            }
        }
        List<Spark> sparks = new ArrayList<>();
        VesselPreviewTest.Prepass pre = null;
        if (painted) {
            pre = (img, depth, cam) -> plane(img, depth, cam, pal, body, -d, t);
        } else {
            for (Rift.Row r : Rift.rim(body, t, RiftTextures.SEED, 14)) {
                double up = Rift.bottom(body) + r.up();
                if (up < 0) continue;
                sparks.add(new Spark(new Vector3f((float) r.left(), (float) up, (float) -d), pal.rim));
                sparks.add(new Spark(new Vector3f((float) r.right(), (float) up, (float) -d), pal.rim));
            }
        }
        return VesselPreviewTest.render(parts, VIEW, look.outline().asRGB(), sparks, 0, PPB, VesselTextures.atlas(look.name()),
                (t / 4) % 8, null, pre);
    }

    /** The painted plane, stretched as {@link Rift#open} says, standing at z across x, depth-tested where solid. */
    private static void plane(BufferedImage img, double[] depth, Quaternionf cam, Palette pal, double body, double z, int t) {
        plane(img, depth, cam, pal, body, z, t, Rift.ARRIVAL);
    }

    private static void plane(BufferedImage img, double[] depth, Quaternionf cam, Palette pal, double body, double z, int t,
            Rift.Timeline when) {
        double[] o = when.open(t);
        if (o[0] <= 0) return;
        double w = Rift.width(body) / RiftTextures.TEAR * o[1], h = Rift.height(body) / RiftTextures.TALL * o[0];
        double cy = Rift.centre(body);
        BufferedImage tex = RiftTextures.frames(pal)[(t / RiftTextures.FRAME_TICKS) % RiftTextures.FRAMES];
        Vector3f[] c = { new Vector3f((float) (-w / 2), (float) (cy + h / 2), (float) z),
                new Vector3f((float) (w / 2), (float) (cy + h / 2), (float) z),
                new Vector3f((float) (w / 2), (float) (cy - h / 2), (float) z),
                new Vector3f((float) (-w / 2), (float) (cy - h / 2), (float) z) };
        double[][] st = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
        for (Vector3f v : c) cam.transform(v);
        tri(img, depth, c, st, 0, 1, 2, tex);
        tri(img, depth, c, st, 0, 2, 3, tex);
    }

    private static void tri(BufferedImage img, double[] depth, Vector3f[] c, double[][] st, int ia, int ib, int ic,
            BufferedImage tex) {
        int W = VesselPreviewTest.CELL_W, H = VesselPreviewTest.CELL_H;
        double ax = VesselPreviewTest.sx(c[ia]), ay = VesselPreviewTest.sy(c[ia]);
        double bx = VesselPreviewTest.sx(c[ib]), by = VesselPreviewTest.sy(c[ib]);
        double cx = VesselPreviewTest.sx(c[ic]), cy = VesselPreviewTest.sy(c[ic]);
        double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1e-9) return;
        int minX = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = (int) Math.min(W - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = (int) Math.min(H - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
        int ground = VesselPreviewTest.groundY();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area;
                double w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                double s = w0 * st[ia][0] + w1 * st[ib][0] + w2 * st[ic][0];
                double tt = w0 * st[ia][1] + w1 * st[ib][1] + w2 * st[ic][1];
                double zz = w0 * c[ia].z + w1 * c[ib].z + w2 * c[ic].z;
                // Below the ground line the rift is buried, as it is in the world.
                double worldY = (ground - py) / VesselPreviewTest.zoom;
                if (worldY < -0.05 && y > ground) continue;
                int argb = tex.getRGB(Math.min(tex.getWidth() - 1, (int) (s * tex.getWidth())),
                        Math.min(tex.getHeight() - 1, (int) (tt * tex.getHeight())));
                int a = argb >>> 24;
                if (a == 0) continue;
                int i = y * W + x;
                if (zz <= depth[i]) continue;
                img.setRGB(x, y, over(img.getRGB(x, y), argb));
                if (a >= 250) depth[i] = zz;
            }
        }
    }

    /** argb laid over an opaque rgb. */
    private static int over(int bg, int argb) {
        double a = (argb >>> 24) / 255.0;
        int r = (int) Math.round(((bg >> 16) & 255) * (1 - a) + ((argb >> 16) & 255) * a);
        int g = (int) Math.round(((bg >> 8) & 255) * (1 - a) + ((argb >> 8) & 255) * a);
        int b = (int) Math.round((bg & 255) * (1 - a) + (argb & 255) * a);
        return (r << 16) | (g << 8) | b;
    }
}
