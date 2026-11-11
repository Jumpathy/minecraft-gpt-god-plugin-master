package net.bigyous.gptgodmc.god.vision;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.bigyous.gptgodmc.god.living.ServantBody;
import net.bigyous.gptgodmc.god.living.ServantKind;
import net.bigyous.gptgodmc.god.living.ServantSkin;
import net.bigyous.gptgodmc.god.living.ServantSkin.Shape;
import net.bigyous.gptgodmc.god.living.ServantTextures;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Face;

/**
 * A small software renderer for the servants' textured bodies, so they can be judged without a game
 * client: boxes and two-sided planes wearing the same atlas regions the resource pack maps, glows that
 * face the camera, cut-out and see-through texels (blended back to front over the solid ones), the same
 * face shading as the vessel previews, the glowing outline, and a few particles. Supersampled.
 */
final class ServantRender {
    private ServantRender() {
    }

    /** A colored mote, in blocks relative to the servant's feet. */
    record Spark(Vector3f pos, int rgb) {
    }

    /** One textured quad in view space (after the camera's turn), with its texture coordinates. */
    private record Quad(Vector3f[] c, double[][] st, String key, Face face, double light, boolean outline) {
        float depth() {
            return (c[0].z + c[1].z + c[2].z + c[3].z) / 4;
        }
    }

    private static final int SS = 2;

    /** The same picture centered on a point of the body (blocks from its feet), for close looks at a detail. */
    static BufferedImage renderAt(ServantKind kind, List<ServantBody.Part> parts, int frame, double yaw, double pitch,
            int width, int height, double ppb, Vector3f focus, int outlineRgb) {
        List<ServantBody.Part> moved = new ArrayList<>();
        for (ServantBody.Part p : parts) {
            moved.add(new ServantBody.Part(p.key(), p.shape(), new Vector3f(p.center()).sub(focus), p.size(), p.rot()));
        }
        double lift = ((height - 30) - height / 2.0) / ppb;
        return render(kind, moved, frame, yaw, pitch, width, height, ppb, lift, outlineRgb, List.of());
    }

    static BufferedImage render(ServantKind kind, List<ServantBody.Part> parts, int frame, double yaw, double pitch,
            int width, int height, double ppb, double lift, int outlineRgb, List<Spark> sparks) {
        ServantTextures.Atlas atlas = ServantTextures.atlas(kind);
        int w = width * SS, h = height * SS;
        double zoom = ppb * SS;
        double ground = h - 30.0 * SS;
        int[] rgb = new int[w * h];
        double[] depth = new double[w * h];
        boolean[] mask = new boolean[w * h];
        java.util.Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        for (int y = 0; y < h; y++) {
            int bg = y > ground ? 0x241B2E : 0x17111F;
            for (int x = 0; x < w; x++) rgb[y * w + x] = bg;
        }
        Quaternionf cam = new Quaternionf().rotationX((float) pitch).rotateY((float) -yaw);
        List<Quad> quads = new ArrayList<>();
        for (ServantBody.Part p : parts) quads(quads, kind, p, cam, lift);
        // Solid texels first (they write depth), then see-through ones from the farthest quad to the nearest.
        for (Quad q : quads) fill(q, atlas, frame, rgb, depth, mask, w, h, zoom, ground, true);
        quads.sort(Comparator.comparingDouble(Quad::depth));
        for (Quad q : quads) fill(q, atlas, frame, rgb, depth, mask, w, h, zoom, ground, false);
        // The glowing outline: a band just outside the silhouette of the solid pieces.
        if (outlineRgb >= 0) {
            int band = 2 * SS;
            int[] out = rgb.clone();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (mask[y * w + x]) continue;
                    boolean near = false;
                    for (int dy = -band; dy <= band && !near; dy++) {
                        for (int dx = -band; dx <= band && !near; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx >= 0 && ny >= 0 && nx < w && ny < h && mask[ny * w + nx]) near = true;
                        }
                    }
                    if (near) out[y * w + x] = outlineRgb;
                }
            }
            rgb = out;
        }
        for (Spark sp : sparks) {
            Vector3f c = cam.transform(new Vector3f(sp.pos()).add(0, (float) lift, 0));
            int px = (int) (w / 2.0 + c.x * zoom), py = (int) (ground - c.y * zoom);
            for (int dy = -SS; dy <= SS; dy++) {
                for (int dx = -SS; dx <= SS; dx++) {
                    int x = px + dx, y = py + dy;
                    if (x < 0 || y < 0 || x >= w || y >= h) continue;
                    if (c.z + 0.05 >= depth[y * w + x]) rgb[y * w + x] = sp.rgb();
                }
            }
        }
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 0, g = 0, b = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        int c = rgb[(y * SS + sy) * w + x * SS + sx];
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                    }
                }
                int n = SS * SS;
                img.setRGB(x, y, ((r / n) << 16) | ((g / n) << 8) | (b / n));
            }
        }
        return img;
    }

    /** The vessel previews' face shading, by the face's direction in the world. */
    private static double light(Vector3f n) {
        double shade = n.x * n.x * 0.6 + n.z * n.z * 0.8 + n.y * n.y * (n.y > 0 ? 1.0 : 0.5);
        return 0.55 + 0.45 * shade;
    }

    private static void quads(List<Quad> out, ServantKind kind, ServantBody.Part p, Quaternionf cam, double lift) {
        boolean outline = ServantSkin.def(kind, p.key()).outline();
        Vector3f center = new Vector3f(p.center()).add(0, (float) lift, 0);
        if (p.shape() == Shape.GLOW) {
            // An upright billboard: it turns about the vertical to face the camera, and stays upright.
            Vector3f right = new Quaternionf(cam).invert().transform(new Vector3f(1, 0, 0));
            right.y = 0;
            right.normalize().mul(p.size().x / 2);
            float hh = p.size().y / 2;
            Vector3f[] corners = new Vector3f[4];
            float[][] sign = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
            for (int i = 0; i < 4; i++) {
                Vector3f v = new Vector3f(center).add(right.x * sign[i][0], hh * sign[i][1], right.z * sign[i][0]);
                corners[i] = cam.transform(v);
            }
            out.add(new Quad(corners, new double[][] { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 0, 0 } }, p.key(), Face.SOUTH,
                    0.9, false));
            return;
        }
        if (p.shape() == Shape.PLANE) {
            float hw = p.size().x / 2, hh = p.size().y / 2;
            float[][] local = { { -hw, -hh }, { hw, -hh }, { hw, hh }, { -hw, hh } };
            Vector3f[] corners = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                Vector3f v = p.rot().transform(new Vector3f(local[i][0], local[i][1], 0)).add(center);
                corners[i] = cam.transform(v);
            }
            Vector3f n = p.rot().transform(new Vector3f(0, 0, 1));
            out.add(new Quad(corners, new double[][] { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 0, 0 } }, p.key(), Face.SOUTH,
                    light(n), outline));
            return;
        }
        Vector3f half = new Vector3f(p.size()).mul(0.5f);
        Vector3f[] world = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            Vector3f v = new Vector3f((i & 1) * 2 - 1, ((i >> 1) & 1) * 2 - 1, ((i >> 2) & 1) * 2 - 1).mul(half);
            world[i] = cam.transform(p.rot().transform(v).add(center));
        }
        for (int axis = 0; axis < 3; axis++) {
            for (int s = 0; s < 2; s++) {
                Vector3f n = new Vector3f(axis == 0 ? s * 2 - 1 : 0, axis == 1 ? s * 2 - 1 : 0, axis == 2 ? s * 2 - 1 : 0);
                p.rot().transform(n);
                if (cam.transform(new Vector3f(n)).z <= 0) continue;
                int[] idx = faceCorners(axis, s);
                Face face = Face.of(axis, s);
                Vector3f[] corners = new Vector3f[4];
                double[][] st = new double[4][];
                for (int i = 0; i < 4; i++) {
                    corners[i] = world[idx[i]];
                    st[i] = face.st(idx[i] & 1, (idx[i] >> 1) & 1, (idx[i] >> 2) & 1);
                }
                out.add(new Quad(corners, st, p.key(), face, light(n), outline));
            }
        }
    }

    private static int[] faceCorners(int axis, int s) {
        int[][] order = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            int a = order[i][0], b = order[i][1];
            int x = axis == 0 ? s : a, y = axis == 1 ? s : axis == 0 ? a : b, z = axis == 2 ? s : b;
            out[i] = x | (y << 1) | (z << 2);
        }
        return out;
    }

    private static void fill(Quad q, ServantTextures.Atlas atlas, int frame, int[] rgb, double[] depth, boolean[] mask,
            int w, int h, double zoom, double ground, boolean solid) {
        tri(q, 0, 1, 2, atlas, frame, rgb, depth, mask, w, h, zoom, ground, solid);
        tri(q, 0, 2, 3, atlas, frame, rgb, depth, mask, w, h, zoom, ground, solid);
    }

    private static void tri(Quad q, int ia, int ib, int ic, ServantTextures.Atlas atlas, int frame, int[] rgb,
            double[] depth, boolean[] mask, int w, int h, double zoom, double ground, boolean solid) {
        Vector3f a = q.c()[ia], b = q.c()[ib], c = q.c()[ic];
        double ax = w / 2.0 + a.x * zoom, ay = ground - a.y * zoom;
        double bx = w / 2.0 + b.x * zoom, by = ground - b.y * zoom;
        double cx = w / 2.0 + c.x * zoom, cy = ground - c.y * zoom;
        int minX = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = (int) Math.min(w - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = (int) Math.min(h - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
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
                int i = y * w + x;
                if (z <= depth[i]) continue;
                double s = w0 * q.st()[ia][0] + w1 * q.st()[ib][0] + w2 * q.st()[ic][0];
                double t = w0 * q.st()[ia][1] + w1 * q.st()[ib][1] + w2 * q.st()[ic][1];
                int argb = atlas.sample(q.key(), q.face(), s, t, frame);
                int alpha = argb >>> 24;
                if (alpha < 8) continue;
                boolean opaque = alpha >= 250;
                if (opaque != solid) continue;
                int lit = shade(argb, q.light());
                if (opaque) {
                    rgb[i] = lit;
                    depth[i] = z;
                } else {
                    rgb[i] = blend(rgb[i], lit, alpha / 255.0);
                }
                if (q.outline() && alpha > 100) mask[i] = true;
            }
        }
    }

    private static int shade(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int blend(int under, int over, double a) {
        int r = (int) Math.round(((under >> 16) & 255) * (1 - a) + ((over >> 16) & 255) * a);
        int g = (int) Math.round(((under >> 8) & 255) * (1 - a) + ((over >> 8) & 255) * a);
        int b = (int) Math.round((under & 255) * (1 - a) + (over & 255) * a);
        return (r << 16) | (g << 8) | b;
    }
}
