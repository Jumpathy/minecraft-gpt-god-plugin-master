package net.bigyous.gptgodmc.god.vessel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.AngelFormsPreviewTest.Shot;
import net.bigyous.gptgodmc.god.vessel.TrueForm.Form;

/**
 * Shows what the client mod's full-screen shader does to each true form, pass by pass:
 * build/test-renders/trueform-shader-&lt;form&gt;.png. {@link #shade} is a line-for-line port of the mod's
 * client-mod/src/main/resources/assets/gptgod/shaders/post/divine.fsh, fed the numbers the mod's PostFx
 * would give it for the layers in {@link TrueFormScenes} at full strength. The picture it works on is the
 * offline render of the form (as with the resource pack), not a real game frame, and the scenes' solid
 * light (haloes, beams, glow particles) is not drawn: this is the shader passes alone.
 */
class ShaderPassPreviewTest {
    static final File OUT = new File("build/test-renders");
    static final int CELL = 520;

    /** The shader's uniform block (see divine.fsh): five vec4. */
    static final class Fx {
        float shockX = 0.5f, shockY = 0.5f, shockR, shockS;
        float ringWidth = 0.07f, aberration, blur, time = 3.7f;
        float saturation = 1, contrast = 1, tint, invert;
        float tr = 1, tg = 1, tb = 1, grain;
        float focusX = 0.5f, focusY = 0.5f, bloom, rays;

        Fx copy() {
            Fx f = new Fx();
            f.shockX = shockX; f.shockY = shockY; f.shockR = shockR; f.shockS = shockS;
            f.ringWidth = ringWidth; f.aberration = aberration; f.blur = blur; f.time = time;
            f.saturation = saturation; f.contrast = contrast; f.tint = tint; f.invert = invert;
            f.tr = tr; f.tg = tg; f.tb = tb; f.grain = grain;
            f.focusX = focusX; f.focusY = focusY; f.bloom = bloom; f.rays = rays;
            return f;
        }
    }

    record Pass(String label, Fx fx) {
    }

    @Test
    void passesOfEveryForm() throws IOException {
        OUT.mkdirs();
        for (Form form : Form.values()) {
            if (form == Form.HYPERCUBE) continue;
            Shot shot = new Shot("", 180, 1, 0.4, true);
            BufferedImage base = AngelFormsPreviewTest.render(form, shot, CELL, CELL);
            float[] heart = AngelFormsPreviewTest.uv(form, shot, CELL, CELL, 0, form == Form.PILLAR ? 13.5 : form
                    == Form.SERAPHIM ? 3 : 0, 0);
            List<Pass> passes = new ArrayList<>();
            Fx fx = new Fx();
            fx.focusX = heart[0];
            fx.focusY = heart[1];
            fx.shockX = heart[0];
            fx.shockY = heart[1];
            passes.add(new Pass("1. no mod (resource pack only)", fx.copy()));
            int n = 2;
            // The looping aura's shader layers, in the order TrueFormScenes lists them, at full strength.
            if (form == Form.EYE) {
                fx.saturation = 1 - 0.65f;
                passes.add(new Pass(n++ + ". + desaturate 0.65: colour drains", fx.copy()));
                fx.aberration = 0.3f;
                passes.add(new Pass(n++ + ". + aberration 0.3: colours split", fx.copy()));
                fx.grain = 0.3f;
                passes.add(new Pass(n++ + ". + grain 0.3", fx.copy()));
                fx.tint = 0.2f;
                fx.tr = 0x3A / 255f;
                fx.tg = 0x1A / 255f;
                fx.tb = 0x60 / 255f;
                passes.add(new Pass(n++ + ". + violet tint 0.2", fx.copy()));
                fx.bloom = 0.6f;
                passes.add(new Pass(n++ + ". + bloom 0.6 = the aura", fx.copy()));
            } else {
                fx.bloom = form == Form.OPHANIM ? 0.7f : form == Form.SERAPHIM ? 0.85f : 0.8f;
                passes.add(new Pass(n++ + ". + bloom " + fx.bloom + ": light spills", fx.copy()));
                fx.rays = form == Form.OPHANIM ? 0.55f : form == Form.SERAPHIM ? 0.7f : 0.75f;
                passes.add(new Pass(n++ + ". + rays " + fx.rays + " = the aura", fx.copy()));
            }
            // The moment its eyes open: the shock ring a third of the way out, colours torn apart.
            Fx open = fx.copy();
            float t = 0.3f;
            open.shockS = (1 - t);
            open.shockR = 1.6f * (1 - (1 - t) * (1 - t));
            open.aberration = Math.max(open.aberration, 0.8f);
            if (form == Form.EYE) {
                open.blur = 0.6f;
            } else {
                open.contrast = 1 + 0.6f * 0.6f;
            }
            passes.add(new Pass(n++ + ". eyes open: shock ring + aberration" + (form == Form.EYE ? " + blur" : " + contrast"),
                    open));
            if (form == Form.EYE) {
                Fx inv = fx.copy();
                inv.invert = 1;
                passes.add(new Pass(n++ + ". eyes open, first 4 ticks: inverted", inv));
            }
            // Struck by its gaze: everything smears toward it and goes grey.
            Fx struck = fx.copy();
            struck.aberration = 1;
            struck.blur = 0.7f;
            struck.saturation = Math.min(struck.saturation, 0.1f);
            passes.add(new Pass(n++ + ". struck by the gaze: blur + aberration + grey", struck));

            int cols = passes.size() <= 4 ? passes.size() : (passes.size() + 1) / 2;
            int rows = (passes.size() + cols - 1) / cols;
            BufferedImage sheet = new BufferedImage(cols * CELL, rows * CELL, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            for (int i = 0; i < passes.size(); i++) {
                int x = (i % cols) * CELL, y = (i / cols) * CELL;
                g.drawImage(i == 0 ? base : shade(base, passes.get(i).fx()), x, y, null);
                g.setColor(new Color(60, 40, 80));
                g.drawRect(x, y, CELL - 1, CELL - 1);
                TrueFormPreviewTest.label(g, passes.get(i).label(), x, y);
            }
            g.dispose();
            File f = new File(OUT, "trueform-shader-" + form.id + ".png");
            ImageIO.write(sheet, "png", f);
            assertTrue(f.length() > 10_000);
        }
    }

    // ---- divine.fsh, ported ------------------------------------------------------------------------

    /** Runs the shader over a picture. */
    static BufferedImage shade(BufferedImage in, Fx u) {
        int w = in.getWidth(), h = in.getHeight();
        float[][] src = new float[3][w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = in.getRGB(x, y);
                int i = y * w + x;
                src[0][i] = ((rgb >> 16) & 255) / 255f;
                src[1][i] = ((rgb >> 8) & 255) / 255f;
                src[2][i] = (rgb & 255) / 255f;
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        float aspect = w / (float) h;
        float[] c = new float[3], s = new float[3];
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                float uvx = (px + 0.5f) / w, uvy = 1 - (py + 0.5f) / h;
                if (u.shockS > 0.001f) {
                    float dx = (uvx - u.shockX) * aspect, dy = uvy - u.shockY;
                    float dist = (float) Math.hypot(dx, dy);
                    float ring = (float) Math.exp(-Math.pow((dist - u.shockR) / u.ringWidth, 2));
                    float len = (float) Math.hypot(dx + 1e-5f, dy + 1e-5f);
                    uvx -= (dx + 1e-5f) / len / aspect * ring * u.shockS * 0.055f;
                    uvy -= (dy + 1e-5f) / len * ring * u.shockS * 0.055f;
                }
                float tfx = uvx - u.focusX, tfy = uvy - u.focusY;
                if (u.aberration > 0.001f) {
                    float ox = (uvx - 0.5f) * u.aberration * 0.035f, oy = (uvy - 0.5f) * u.aberration * 0.035f;
                    tex(src, w, h, clampUv(uvx + ox), clampUv(uvy + oy), s);
                    c[0] = s[0];
                    tex(src, w, h, uvx, uvy, s);
                    c[1] = s[1];
                    tex(src, w, h, clampUv(uvx - ox), clampUv(uvy - oy), s);
                    c[2] = s[2];
                } else {
                    tex(src, w, h, uvx, uvy, c);
                }
                if (u.blur > 0.001f) {
                    float r = c[0], g = c[1], b = c[2];
                    for (int i = 1; i <= 10; i++) {
                        float k = u.blur * 0.22f * i / 10f;
                        tex(src, w, h, clampUv(uvx - tfx * k), clampUv(uvy - tfy * k), s);
                        r += s[0];
                        g += s[1];
                        b += s[2];
                    }
                    c[0] = r / 11;
                    c[1] = g / 11;
                    c[2] = b / 11;
                }
                if (u.bloom > 0.001f) {
                    float r = 0, g = 0, b = 0;
                    for (int i = 0; i < 18; i++) {
                        float a = i * 2.399963f;
                        float rad = (float) Math.sqrt(i + 0.5f) / 4.24f;
                        bright(src, w, h, uvx + (float) Math.cos(a) * rad * 0.035f,
                                uvy + (float) Math.sin(a) * aspect * rad * 0.035f, s);
                        r += s[0];
                        g += s[1];
                        b += s[2];
                        bright(src, w, h, uvx + (float) Math.cos(a + 1) * rad * 0.012f,
                                uvy + (float) Math.sin(a + 1) * aspect * rad * 0.012f, s);
                        r += s[0];
                        g += s[1];
                        b += s[2];
                    }
                    float k = u.bloom * 0.9f / 18f;
                    c[0] += r * k;
                    c[1] += g * k;
                    c[2] += b * k;
                }
                if (u.rays > 0.001f) {
                    float r = 0, g = 0, b = 0, decay = 1;
                    for (int i = 1; i <= 16; i++) {
                        float ax = uvx - tfx * i / 16f * 0.85f, ay = uvy - tfy * i / 16f * 0.85f;
                        float d = (float) Math.hypot((ax - u.focusX) * aspect, ay - u.focusY);
                        float near = smoothstep(0.45f, 0.05f, d);
                        bright(src, w, h, ax, ay, s);
                        r += s[0] * decay * near;
                        g += s[1] * decay * near;
                        b += s[2] * decay * near;
                        decay *= 0.9f;
                    }
                    c[0] += r / 10 * u.rays;
                    c[1] += g / 10 * u.rays;
                    c[2] += b / 10 * u.rays;
                }
                float lum = c[0] * 0.299f + c[1] * 0.587f + c[2] * 0.114f;
                float[] tint = { u.tr, u.tg, u.tb };
                for (int k = 0; k < 3; k++) {
                    float v = lum + (c[k] - lum) * u.saturation;
                    v = (v - 0.5f) * u.contrast + 0.5f;
                    v = v + (lum * tint[k] * 1.6f + tint[k] * 0.08f - v) * u.tint;
                    v = v + (1 - v - v) * u.invert;
                    c[k] = v;
                }
                if (u.grain > 0.001f) {
                    float n = (hash(uvx * w + u.time * 60, uvy * h + u.time * 60) - 0.5f) * u.grain * 0.35f;
                    c[0] += n;
                    c[1] += n;
                    c[2] += n;
                }
                int R = Math.round(clamp01(c[0]) * 255), G = Math.round(clamp01(c[1]) * 255), B = Math.round(clamp01(c[2])
                        * 255);
                out.setRGB(px, py, (R << 16) | (G << 8) | B);
            }
        }
        return out;
    }

    private static float clampUv(float v) {
        return Math.max(0.001f, Math.min(0.999f, v));
    }

    private static float clamp01(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    private static float hash(float x, float y) {
        float px = fract(x * 123.34f), py = fract(y * 456.21f);
        float d = px * (px + 45.32f) + py * (py + 45.32f);
        px += d;
        py += d;
        return fract(px * py);
    }

    /** Only what is nearly white-hot counts as light. */
    private static void bright(float[][] src, int w, int h, float u, float v, float[] out) {
        tex(src, w, h, clampUv(u), clampUv(v), out);
        float lo = Math.min(out[0], Math.min(out[1], out[2])), hi = Math.max(out[0], Math.max(out[1], out[2]));
        float k = smoothstep(0.86f, 1.0f, lo * 0.6f + hi * 0.4f);
        out[0] *= k;
        out[1] *= k;
        out[2] *= k;
    }

    /** Bilinear, clamped at the edges; v runs up, as in the shader. */
    private static void tex(float[][] src, int w, int h, float u, float v, float[] out) {
        float x = u * w - 0.5f, y = (1 - v) * h - 0.5f;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        int xa = Math.max(0, Math.min(w - 1, x0)), xb = Math.max(0, Math.min(w - 1, x0 + 1));
        int ya = Math.max(0, Math.min(h - 1, y0)), yb = Math.max(0, Math.min(h - 1, y0 + 1));
        for (int k = 0; k < 3; k++) {
            float top = src[k][ya * w + xa] * (1 - fx) + src[k][ya * w + xb] * fx;
            float bot = src[k][yb * w + xa] * (1 - fx) + src[k][yb * w + xb] * fx;
            out[k] = top * (1 - fy) + bot * fy;
        }
    }
}
