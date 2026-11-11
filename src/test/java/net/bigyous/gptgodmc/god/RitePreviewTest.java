package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * Offline previews of the particle rites and shapes: a small additive-glow particle renderer (with
 * rough Minecraft particle physics for moving dots) writes contact sheets to build/test-renders/, so
 * the choreography can be judged without a game client.
 */
class RitePreviewTest {
    static final File OUT = new File("build/test-renders");
    static final int CELL_W = 280, CELL_H = 360, TITLE_H = 34;

    // ---- Camera and canvas -------------------------------------------------------------------

    /** A perspective camera looking at a point from a raised three-quarter view. */
    static final class Camera {
        final double[] eye, right, up, fwd;
        final double focal;

        Camera(double targetY, double dist, double yaw, double pitch) {
            double[] target = { 0, targetY, 0 };
            eye = new double[] { Math.sin(yaw) * Math.cos(pitch) * dist, targetY + Math.sin(pitch) * dist,
                    Math.cos(yaw) * Math.cos(pitch) * dist };
            fwd = norm(sub(target, eye));
            right = norm(cross(fwd, new double[] { 0, 1, 0 }));
            up = cross(right, fwd);
            focal = CELL_H * 0.5 / Math.tan(Math.toRadians(25));
        }

        /** Screen x, y and depth, or null when behind the camera. */
        double[] project(double x, double y, double z) {
            double[] d = { x - eye[0], y - eye[1], z - eye[2] };
            double depth = dot(d, fwd);
            if (depth < 0.3) return null;
            return new double[] { CELL_W / 2.0 + focal * dot(d, right) / depth,
                    CELL_H / 2.0 - focal * dot(d, up) / depth, depth };
        }
    }

    static double[] sub(double[] a, double[] b) {
        return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return new double[] { a[0] / l, a[1] / l, a[2] / l };
    }

    /** One cell: a dark base (sky, grid, silhouette), additive glow and dark "ink" for black dust. */
    static final class Canvas {
        final Camera cam;
        final float[] base = new float[CELL_W * CELL_H * 3];
        final float[] glow = new float[CELL_W * CELL_H * 3];
        final float[] ink = new float[CELL_W * CELL_H];
        final double[] bodyDepth = new double[CELL_W * CELL_H];

        Canvas(Camera cam) {
            this.cam = cam;
            for (int y = 0; y < CELL_H; y++) {
                double t = y / (double) CELL_H;
                for (int x = 0; x < CELL_W; x++) {
                    int i = (y * CELL_W + x) * 3;
                    base[i] = (float) (0.045 - 0.02 * t);
                    base[i + 1] = (float) (0.035 - 0.015 * t);
                    base[i + 2] = (float) (0.085 - 0.04 * t);
                }
            }
            java.util.Arrays.fill(bodyDepth, Double.MAX_VALUE);
        }

        void grid() {
            for (int k = -7; k <= 7; k++) {
                line(k, -7, k, 7);
                line(-7, k, 7, k);
            }
        }

        private void line(double x0, double z0, double x1, double z1) {
            int n = 400;
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                double x = x0 + (x1 - x0) * t, z = z0 + (z1 - z0) * t;
                double fade = Math.max(0, 1 - Math.hypot(x, z) / 7.5);
                double[] s = cam.project(x, 0, z);
                if (s == null) continue;
                int px = (int) s[0], py = (int) s[1];
                if (px < 0 || py < 0 || px >= CELL_W || py >= CELL_H) continue;
                int j = (py * CELL_W + px) * 3;
                base[j] = (float) Math.max(base[j], 0.10 * fade);
                base[j + 1] = (float) Math.max(base[j + 1], 0.09 * fade);
                base[j + 2] = (float) Math.max(base[j + 2], 0.16 * fade);
            }
        }

        /** A player-sized figure (1.8 blocks) standing at height lift, drawn as flat shaded boxes. */
        void figure(double lift, double alpha) {
            // x half-width, y0, y1, z half-depth, x offset
            double[][] boxes = { { 0.12, 0, 0.75, 0.12, -0.12 }, { 0.12, 0, 0.75, 0.12, 0.12 },
                    { 0.25, 0.75, 1.5, 0.13, 0 }, { 0.11, 0.78, 1.48, 0.11, -0.36 }, { 0.11, 0.78, 1.48, 0.11, 0.36 },
                    { 0.24, 1.5, 1.98, 0.24, 0 } };
            for (double[] b : boxes) {
                box(b[4] - b[0], lift + b[1], -b[3], b[4] + b[0], lift + b[2], b[3], alpha);
            }
            if (lift > 0.05) {
                // its shadow on the ground
                for (int i = 0; i < 40; i++) {
                    double a = i * Math.PI * 2 / 40;
                    double[] s = cam.project(Math.cos(a) * 0.35, 0.01, Math.sin(a) * 0.35);
                    if (s != null) stamp((int) s[0], (int) s[1], 0.0f);
                }
            }
        }

        private void stamp(int x, int y, float v) {
            if (x < 0 || y < 0 || x >= CELL_W || y >= CELL_H) return;
            int j = (y * CELL_W + x) * 3;
            base[j] = base[j + 1] = base[j + 2] = v;
        }

        private void box(double x0, double y0, double z0, double x1, double y1, double z1, double alpha) {
            // Rasterize by sampling the box volume's faces densely (simple and robust).
            double step = 0.02;
            for (double x = x0; x <= x1 + 1e-9; x += step) {
                for (double y = y0; y <= y1 + 1e-9; y += step) {
                    paintBody(x, y, z0, 0.55, alpha);
                    paintBody(x, y, z1, 0.75, alpha);
                }
            }
            for (double z = z0; z <= z1 + 1e-9; z += step) {
                for (double y = y0; y <= y1 + 1e-9; y += step) {
                    paintBody(x0, y, z, 0.6, alpha);
                    paintBody(x1, y, z, 0.9, alpha);
                }
                for (double x = x0; x <= x1 + 1e-9; x += step) paintBody(x, y1, z, 1.0, alpha);
            }
        }

        private void paintBody(double x, double y, double z, double shade, double alpha) {
            double[] s = cam.project(x, y, z);
            if (s == null) return;
            int px = (int) s[0], py = (int) s[1];
            if (px < 0 || py < 0 || px >= CELL_W || py >= CELL_H) return;
            int i = py * CELL_W + px;
            if (s[2] > bodyDepth[i]) return;
            bodyDepth[i] = s[2];
            int j = i * 3;
            float r = (float) (0.16 * shade), g = (float) (0.15 * shade), b = (float) (0.22 * shade);
            base[j] = (float) (base[j] * (1 - alpha) + r * alpha);
            base[j + 1] = (float) (base[j + 1] * (1 - alpha) + g * alpha);
            base[j + 2] = (float) (base[j + 2] * (1 - alpha) + b * alpha);
        }

        /**
         * A soft glowing dot of world radius rw. Behind the figure it is mostly hidden.
         */
        void splat(double x, double y, double z, int rgb, double rw, double intensity) {
            double[] s = cam.project(x, y, z);
            if (s == null) return;
            double sigma = Math.max(0.7, cam.focal * rw / s[2]);
            double halo = sigma * 2.6;
            float cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cb = (rgb & 255) / 255f;
            double lum = 0.3 * cr + 0.55 * cg + 0.15 * cb;
            int reach = (int) Math.ceil(halo * 2.5);
            int x0 = (int) Math.max(0, s[0] - reach), x1 = (int) Math.min(CELL_W - 1, s[0] + reach);
            int y0 = (int) Math.max(0, s[1] - reach), y1 = (int) Math.min(CELL_H - 1, s[1] + reach);
            for (int py = y0; py <= y1; py++) {
                for (int px = x0; px <= x1; px++) {
                    double dx = px + 0.5 - s[0], dy = py + 0.5 - s[1];
                    double d2 = dx * dx + dy * dy;
                    int i = py * CELL_W + px;
                    double hidden = s[2] > bodyDepth[i] + 0.05 ? 0.25 : 1;
                    double core = Math.exp(-d2 / (2 * sigma * sigma));
                    double wide = 0.06 * Math.exp(-d2 / (2 * halo * halo));
                    if (lum < 0.12) {
                        // Dark dust: an inky blot with a faint violet rim.
                        ink[i] = (float) Math.min(0.9, ink[i] + core * 0.8 * intensity * hidden);
                        double rim = wide * intensity * hidden * 1.4;
                        glow[i * 3] += (float) (rim * 0.42);
                        glow[i * 3 + 1] += (float) (rim * 0.20);
                        glow[i * 3 + 2] += (float) (rim * 0.62);
                        continue;
                    }
                    double v = (core * 1.25 + wide) * intensity * hidden;
                    // Hot centers wash out toward white.
                    double white = core * core * 0.3 * intensity;
                    glow[i * 3] += (float) (v * cr + white);
                    glow[i * 3 + 1] += (float) (v * cg + white);
                    glow[i * 3 + 2] += (float) (v * cb + white);
                }
            }
        }

        void into(BufferedImage img, int ox, int oy) {
            for (int y = 0; y < CELL_H; y++) {
                for (int x = 0; x < CELL_W; x++) {
                    int i = y * CELL_W + x;
                    int[] c = new int[3];
                    // Hue-preserving tone map: scale by the brightest channel, then let only very hot
                    // spots wash out toward white.
                    double m = Math.max(glow[i * 3], Math.max(glow[i * 3 + 1], glow[i * 3 + 2])) * 1.1;
                    double scale = m < 1e-6 ? 1.1 : (1 - Math.exp(-m)) / m * 1.1;
                    double hot = Math.max(0, m - 3.0) * 0.1;
                    for (int k = 0; k < 3; k++) {
                        double g = Math.min(1, glow[i * 3 + k] * scale + hot);
                        double v = base[i * 3 + k] + g * (1 - base[i * 3 + k]);
                        v = v * (1 - ink[i]) + 0.015 * ink[i];
                        c[k] = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
                    }
                    img.setRGB(ox + x, oy + y, (c[0] << 16) | (c[1] << 8) | c[2]);
                }
            }
        }
    }

    // ---- Particle look and rough physics -----------------------------------------------------

    record Look(double radius, double intensity, double drag, double gravity, int life) {
    }

    static Look look(Rites.Tint t) {
        return switch (t) {
            case FLASH -> new Look(0.45, 1.1, 1, 0, 4);
            case STAR -> new Look(0.04, 1.4, 0.91, 0, 60);
            case SPARK -> new Look(0.032, 1.6, 0.96, 0, 10);
            case EMBER, SOUL -> new Look(0.05, 1.3, 0.96, 0, 16);
            case HEART -> new Look(0.14, 1.2, 1, -0.01, 16);
            case TOTEM -> new Look(0.045, 1.3, 0.6, 0.05, 60);
            case SMOKE -> new Look(0.28, 0.35, 0.96, -0.004, 30);
            default -> new Look(0.032 * t.size, 0.95, 0.96, 0, 24);
        };
    }

    /** How high the anchor stands at a frame (only ascension lifts its mortal). */
    static double lift(Rites.Kind kind, int f) {
        if (kind != Rites.Kind.ASCENSION) return 0;
        double tick = f * 2;
        if (tick <= 16) return 0;
        if (tick <= 68) return 2.3 * Shapes.ease((tick - 16) / 52.0 * 0.85 + 0.15 * ((tick - 16) / 52.0));
        return Math.max(0, 2.3 - (tick - 68) * 0.055);
    }

    /** Draws frame f of a rite as seen a moment after it is spawned, with the fading trail before it. */
    static void drawRite(Canvas c, Rites.Kind kind, int f) {
        double[] staticFade = { 1.0, 0.5, 0.3, 0.17, 0.08 };
        for (int back = 12; back >= 0; back--) {
            int g = f - back;
            if (g < 0) continue;
            double ly = lift(kind, g);
            for (Rites.Dot d : Rites.frame(kind, g)) {
                Look l = look(d.tint());
                double y0 = d.y() + (d.fixed() ? 0 : ly);
                if (!d.moving()) {
                    if (d.tint() == Rites.Tint.FLASH) {
                        if (back <= 1) c.splat(d.x(), y0, d.z(), d.tint().rgb, l.radius(), l.intensity() / (1 + back * 2));
                        continue;
                    }
                    if (d.tint() == Rites.Tint.HEART) {
                        if (back <= 5) heart(c, d.x(), y0 + back * 0.04, d.z(), 1 - back / 6.0);
                        continue;
                    }
                    if (back < staticFade.length) {
                        c.splat(d.x(), y0, d.z(), d.tint().rgb, l.radius(), l.intensity() * staticFade[back]);
                    }
                    continue;
                }
                // A moving dot: follow it tick by tick, drawing a short streak behind its head.
                int age = back * 2 + 1;
                if (age > l.life()) continue;
                double x = d.x(), y = y0, z = d.z(), vx = d.vx(), vy = d.vy(), vz = d.vz();
                List<double[]> path = new ArrayList<>();
                path.add(new double[] { x, y, z });
                for (int t = 0; t < age; t++) {
                    x += vx;
                    y += vy;
                    z += vz;
                    vx *= l.drag();
                    vy = vy * l.drag() - l.gravity();
                    vz *= l.drag();
                    path.add(new double[] { x, y, z });
                }
                double life = 1 - age / (double) l.life();
                int n = path.size();
                for (int k = Math.max(0, n - 4); k < n; k++) {
                    double[] q = path.get(k);
                    double w = (k == n - 1 ? 1 : 0.35) * life;
                    c.splat(q[0], q[1], q[2], d.tint().rgb, l.radius(), l.intensity() * w);
                }
            }
        }
    }

    static void heart(Canvas c, double x, double y, double z, double w) {
        // A tiny heart made of dots so it reads as one.
        double[][] pts = { { -0.06, 0.05 }, { 0.06, 0.05 }, { -0.1, 0.0 }, { 0.1, 0.0 }, { 0, 0.02 }, { -0.05, -0.05 },
                { 0.05, -0.05 }, { 0, -0.1 } };
        for (double[] p : pts) c.splat(x + p[0], y + p[1], z, 0xFF3355, 0.04, 1.3 * w);
    }

    static Camera cameraFor(Rites.Kind kind) {
        double yaw = 0.62, pitch = 0.36;
        return switch (kind) {
            case JUDGEMENT -> new Camera(3.6, 12.5, yaw, 0.3);
            case ARRIVAL -> new Camera(4.6, 16, yaw, 0.3);
            case DEPARTURE -> new Camera(3.4, 12.5, yaw, 0.3);
            case RESURRECTION -> new Camera(2.8, 11.5, yaw, 0.32);
            case SEAL -> new Camera(0.6, 10, yaw, 0.6);
            case ASCENSION -> new Camera(1.9, 8.6, yaw, pitch);
            default -> new Camera(1.3, 7.6, yaw, pitch);
        };
    }

    /** The frames shown for a rite: the build-up, the climax and two just after it, then the end. */
    static List<Integer> keyFrames(Rites.Kind kind) {
        TreeSet<Integer> s = new TreeSet<>();
        int c = kind.climax;
        int before = Math.min(4, Math.max(0, c - 1));
        for (int i = 0; i < before; i++) s.add((int) Math.round(1 + (c - 2) * i / (double) Math.max(1, before - 1)));
        s.add(c);
        if (c + 1 < kind.frames) s.add(c + 1);
        if (c + 3 < kind.frames) s.add(c + 3);
        int after = 8 - s.size();
        int from = Math.min(kind.frames - 1, c + 5);
        for (int i = 0; i < after; i++) {
            s.add((int) Math.round(from + (kind.frames - 1 - from) * i / (double) Math.max(1, after - 1)));
        }
        List<Integer> out = new ArrayList<>(s);
        while (out.size() > 8) out.remove(out.size() - 1);
        return out;
    }

    /** Whether the god's body (or the mortal) is there to draw at a frame. */
    static double figureAlpha(Rites.Kind kind, int f) {
        return switch (kind) {
            case ARRIVAL -> f < kind.climax ? 0.0 : 1.0;
            case DEPARTURE -> f < kind.climax ? 1.0 : f == kind.climax ? 0.4 : 0.0;
            case SEAL -> 0.55;
            default -> 1.0;
        };
    }

    @Test
    void writeRiteSheets() throws IOException {
        OUT.mkdirs();
        for (Rites.Kind kind : Rites.Kind.values()) {
            List<Integer> frames = keyFrames(kind);
            BufferedImage img = new BufferedImage(CELL_W * frames.size(), CELL_H + TITLE_H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(14, 11, 22));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            Camera cam = cameraFor(kind);
            for (int i = 0; i < frames.size(); i++) {
                int f = frames.get(i);
                Canvas c = new Canvas(cam);
                c.grid();
                double a = figureAlpha(kind, f);
                if (a > 0) c.figure(lift(kind, f), a);
                drawRite(c, kind, f);
                c.into(img, i * CELL_W, TITLE_H);
                label(g, String.format("f%d  %.1fs%s", f, f * 0.1, f == kind.climax ? "  * climax" : ""), i * CELL_W,
                        TITLE_H, f == kind.climax);
            }
            g.setColor(new Color(40, 34, 60));
            for (int i = 1; i < frames.size(); i++) g.drawLine(i * CELL_W, TITLE_H, i * CELL_W, img.getHeight());
            title(g, kind.id().toUpperCase() + "   " + kind.frames + " frames (" + kind.frames / 10.0
                    + " s), climax at frame " + kind.climax + "   sounds: " + soundList(kind));
            g.dispose();
            File file = new File(OUT, "rite-" + kind.id() + ".png");
            ImageIO.write(img, "png", file);
            assertTrue(file.length() > 0);
        }
    }

    static String soundList(Rites.Kind kind) {
        List<String> s = new ArrayList<>();
        for (Rites.Cue c : Rites.cues(kind)) s.add("f" + c.frame() + " " + c.sound());
        String all = String.join(", ", s);
        return all.length() > 150 ? all.substring(0, 150) + "..." : all;
    }

    // ---- Galleries ---------------------------------------------------------------------------

    record Exhibit(String name, List<Vector> points, int rgb) {
    }

    @Test
    void writeShapeGallery() throws IOException {
        OUT.mkdirs();
        List<Exhibit> ex = List.of(
                new Exhibit("ring", Shapes.ring(2, 40, 0, 0.05), Rites.Tint.GOLD.rgb),
                new Exhibit("polygon (hexagon)", Shapes.polygon(6, 2, 0, 0.05, 8), Rites.Tint.VIOLET.rgb),
                new Exhibit("pentagram", Shapes.star(5, 2, 2, 0, 0.05, 10), Rites.Tint.BLOOD.rgb),
                new Exhibit("hexagram", Shapes.star(6, 2, 2, 0, 0.05, 9), Rites.Tint.CYAN.rgb),
                new Exhibit("octagram", Shapes.star(8, 3, 2, 0, 0.05, 8), Rites.Tint.SUN.rgb),
                new Exhibit("magic circle", Shapes.magicCircle(2.4, 0.2, 0.05, 3), Rites.Tint.GOLD.rgb),
                new Exhibit("helix", Shapes.helix(2, 1.2, 0.4, 0, 2.6, 2, 40, 0), Rites.Tint.SUN.rgb),
                new Exhibit("sphere", Shapes.lift(Shapes.sphere(120, 1.2, 0), 1.3), Rites.Tint.WHITE.rgb),
                new Exhibit("spikes", Shapes.lift(Shapes.spikes(14, 0.3, 1.6, 6, 0), 1.3), Rites.Tint.STAR.rgb),
                new Exhibit("tesseract", Shapes.lift(Shapes.turn(Shapes.tesseract(0.5, 0.3, 0.2, 0.9, 7), 0.3, 0.4,
                        0), 1.4), Rites.Tint.VIOLET.rgb),
                new Exhibit("eye", Shapes.lift(Shapes.turn(Shapes.eye(1.4, 0.6, 24), 0, 0.62, 0), 1.4),
                        Rites.Tint.BLOOD.rgb),
                new Exhibit("line", Shapes.line(new Vector(-2, 0.1, 0), new Vector(1.5, 3, 0.5), 30),
                        Rites.Tint.LIFE.rgb));
        int cols = 4, rows = (ex.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(CELL_W * cols, CELL_H * rows + TITLE_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Camera cam = new Camera(1.1, 8, 0.62, 0.5);
        for (int i = 0; i < ex.size(); i++) {
            Canvas c = new Canvas(cam);
            c.grid();
            for (Vector v : ex.get(i).points()) c.splat(v.getX(), v.getY(), v.getZ(), ex.get(i).rgb(), 0.06, 1.0);
            c.into(img, (i % cols) * CELL_W, TITLE_H + (i / cols) * CELL_H);
            label(g, ex.get(i).name() + " (" + ex.get(i).points().size() + ")", (i % cols) * CELL_W,
                    TITLE_H + (i / cols) * CELL_H, false);
        }
        title(g, "Shapes: the geometry of the god's particle art");
        g.dispose();
        ImageIO.write(img, "png", new File(OUT, "particles-shapes.png"));
    }

    @Test
    void writeShowGallery() throws IOException {
        OUT.mkdirs();
        Spectacle.Shape[] shapes = Spectacle.Shape.values();
        int cols = 5, rows = (shapes.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(CELL_W * cols, CELL_H * rows + TITLE_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Camera cam = new Camera(2.2, 13, 0.62, 0.4);
        Vector beam = new Vector(4, 9, -3);
        for (int i = 0; i < shapes.length; i++) {
            Spectacle.Shape s = shapes[i];
            double lift = switch (s) {
                case BURST, SPHERE, BEAM -> 1.0;
                case RING, SHOCKWAVE -> 0.15;
                case CROWN -> 2.15;
                default -> 0;
            };
            Canvas c = new Canvas(cam);
            c.grid();
            c.figure(0, 1);
            int frame = 12;
            for (int back = 4; back >= 0; back--) {
                double w = new double[] { 1, 0.5, 0.3, 0.17, 0.08 }[back];
                for (Vector v : Spectacle.points(s, frame - back, 40, 3, beam, Spectacle.FRAME_BUDGET)) {
                    c.splat(v.getX(), v.getY() + lift, v.getZ(), 0xB24BF3, 0.08, w);
                }
            }
            c.into(img, (i % cols) * CELL_W, TITLE_H + (i / cols) * CELL_H);
            label(g, s.id() + " (size 3)", (i % cols) * CELL_W, TITLE_H + (i / cols) * CELL_H, false);
        }
        title(g, "Spectacle shapes (the particles power), violet dust, frame 12 of 40 with trails");
        g.dispose();
        ImageIO.write(img, "png", new File(OUT, "particles-shows.png"));
    }

    // ---- Labels ------------------------------------------------------------------------------

    static void label(Graphics2D g, String text, int x, int y, boolean hot) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(hot ? new Color(255, 214, 110) : new Color(220, 210, 235));
        g.drawString(text, x + 8, y + 18);
    }

    static void title(Graphics2D g, String text) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.setColor(new Color(235, 225, 250));
        g.drawString(text, 10, 22);
    }
}
