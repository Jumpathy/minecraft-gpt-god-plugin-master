package net.bigyous.gptgod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import net.bigyous.gptgod.client.config.ModConfig;

/**
 * Full-screen shader effects as pure maths: what is active, and the numbers the divine post shader gets
 * this frame (see assets/gptgod/shaders/post/divine.fsh). Kinds: shock (a ring that bends the world as it
 * expands from a point), aberration (colours split apart), blur (radial, toward a point), bloom, rays
 * (light streaming from a point), desaturate, tint, contrast, invert, grain.
 */
public final class PostFx {
    public static final int MAX_ACTIVE = 24;
    /** Floats in the uniform block: five vec4. */
    public static final int FLOATS = 20;

    public enum Kind {
        SHOCK, ABERRATION, BLUR, BLOOM, RAYS, DESATURATE, TINT, CONTRAST, INVERT, GRAIN;

        public static Kind of(String id) {
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(id)) return k;
            }
            return null;
        }
    }

    record Active(Kind kind, float strength, long start, long duration, int color, double[] at) {
    }

    private final List<Active> active = new ArrayList<>();

    /** at: the world point it comes from (shock, blur, rays), or null for the screen's centre. */
    public boolean add(Kind kind, float strength, long durationMs, int color, double[] at, long now) {
        if (kind == null || strength < 0.02f) return false;
        if (active.size() >= MAX_ACTIVE) active.remove(0);
        active.add(new Active(kind, Math.min(1, strength), now, Math.max(100, durationMs), color & 0xFFFFFF, at));
        return true;
    }

    public void clear() {
        active.clear();
    }

    public boolean busy() {
        return !active.isEmpty();
    }

    /** Rises quickly, holds, falls slowly. */
    static float envelope(float t) {
        float in = Math.min(1f, t / 0.12f);
        float out = t > 0.6f ? 1 - (t - 0.6f) / 0.4f : 1f;
        return Math.max(0, in * out);
    }

    /**
     * The uniform values for this frame, or null when nothing shows.
     *
     * @param project world point → {u, v} on screen (0..1), or null when it is behind the camera
     */
    public float[] frame(long now, ModConfig cfg, Function<double[], float[]> project) {
        if (active.isEmpty()) return null;
        float power = (float) cfg.bloom;
        float shockR = 0, shockS = 0, shockX = 0.5f, shockY = 0.5f;
        float aberration = 0, blur = 0, bloom = 0, rays = 0, desat = 0, tint = 0, contrast = 0, invert = 0, grain = 0;
        float tr = 0, tg = 0, tb = 0;
        float fx = 0.5f, fy = 0.5f, focusWeight = 0;
        Iterator<Active> it = active.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (now - a.start() >= a.duration()) {
                it.remove();
                continue;
            }
            float t = (now - a.start()) / (float) a.duration();
            float e = envelope(t) * a.strength() * power;
            float[] uv = a.at() == null ? new float[] { 0.5f, 0.5f } : project.apply(a.at());
            switch (a.kind()) {
                case SHOCK -> {
                    if (uv == null) continue;
                    // The strongest ring wins; it expands past the screen's corners and thins as it goes.
                    float s = (1 - t) * a.strength() * power * (cfg.noFlashing ? 0.4f : 1f);
                    if (s > shockS) {
                        shockS = s;
                        shockR = 1.6f * (1 - (1 - t) * (1 - t));
                        shockX = uv[0];
                        shockY = uv[1];
                    }
                }
                case ABERRATION -> aberration = Math.max(aberration, e);
                case BLUR, RAYS -> {
                    if (a.kind() == Kind.BLUR) blur = Math.max(blur, e);
                    else rays = Math.max(rays, e);
                    if (uv != null && e > focusWeight) {
                        focusWeight = e;
                        fx = uv[0];
                        fy = uv[1];
                    }
                }
                case BLOOM -> bloom = Math.max(bloom, e);
                case DESATURATE -> desat = Math.max(desat, e);
                case TINT -> {
                    tr += ((a.color() >> 16) & 255) / 255f * e;
                    tg += ((a.color() >> 8) & 255) / 255f * e;
                    tb += (a.color() & 255) / 255f * e;
                    tint += e;
                }
                case CONTRAST -> contrast = Math.max(contrast, e);
                case INVERT -> invert = Math.max(invert, cfg.noFlashing ? 0 : e);
                case GRAIN -> grain = Math.max(grain, e);
            }
        }
        if (active.isEmpty()) return null;
        if (tint > 0) {
            tr /= tint;
            tg /= tint;
            tb /= tint;
        }
        float time = (now % 100_000L) / 1000f;
        return new float[] {
                shockX, shockY, shockR, shockS,
                0.07f, aberration, blur, time,
                1 - Math.min(1, desat), 1 + contrast * 0.6f, Math.min(0.85f, tint), Math.min(1, invert),
                tr, tg, tb, grain,
                fx, fy, bloom, rays };
    }

    /** Whether these values change the picture at all. */
    public static boolean visible(float[] u) {
        return u != null && (u[3] > 0.003f || u[5] > 0.003f || u[6] > 0.003f || u[8] < 0.997f || u[9] > 1.003f
                || u[10] > 0.003f || u[11] > 0.003f || u[15] > 0.003f || u[18] > 0.003f || u[19] > 0.003f);
    }
}
