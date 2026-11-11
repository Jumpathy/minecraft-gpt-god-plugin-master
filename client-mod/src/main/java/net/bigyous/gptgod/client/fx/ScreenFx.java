package net.bigyous.gptgod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.net.Protocol;

/**
 * Screen effects as pure maths: the active shakes, flashes, vignettes and pulses, and what they add up to
 * at a moment in time (milliseconds). The renderer and the camera mixin only read {@link #frame}.
 */
public final class ScreenFx {
    public static final int MAX_ACTIVE = 24;
    /** The strongest shake turns the view this many degrees. */
    public static final float MAX_SHAKE_DEGREES = 3.2f;

    public enum Kind {
        SHAKE, FLASH, VIGNETTE, PULSE;

        public static Kind of(String id) {
            return switch (id) {
                case "shake" -> SHAKE;
                case "flash" -> FLASH;
                case "vignette" -> VIGNETTE;
                case "pulse" -> PULSE;
                default -> null;
            };
        }
    }

    record Active(Kind kind, float strength, long start, long duration, int color, float seed) {
        float t(long now) {
            return Math.min(1f, Math.max(0f, (now - start) / (float) duration));
        }
    }

    /** What to draw this frame. Colours are RGB; alphas 0..1; shake angles in degrees. */
    public record Frame(float yaw, float pitch, float roll, int flashColor, float flashAlpha, int vignetteColor,
            float vignetteAlpha, int pulseColor, float pulseAlpha, float pulseRadius) {
        public static final Frame NONE = new Frame(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        public boolean idle() {
            return yaw == 0 && pitch == 0 && roll == 0 && flashAlpha <= 0 && vignetteAlpha <= 0 && pulseAlpha <= 0;
        }
    }

    private final List<Active> active = new ArrayList<>();
    private int count;

    /**
     * Adds an effect from the server. you is the player's position (for falloff); null means "no falloff".
     * Returns false when it is too far away, too weak or unknown.
     */
    public boolean add(Protocol.Screen s, double[] you, long now) {
        Kind kind = Kind.of(s.kind());
        if (kind == null) return false;
        float strength = s.intensity();
        if (s.from() != null && you != null) {
            double dx = s.from()[0] - you[0], dy = s.from()[1] - you[1], dz = s.from()[2] - you[2];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            strength *= (float) falloff(d, s.radius());
        }
        return add(kind, strength, s.ticks() * 50L, s.color(), now);
    }

    public boolean add(Kind kind, float strength, long durationMs, int color, long now) {
        if (strength < 0.02f) return false;
        if (active.size() >= MAX_ACTIVE) active.remove(0);
        active.add(new Active(kind, Math.min(1f, strength), now, Math.max(50, durationMs), color & 0xFFFFFF,
                (count++ * 0.618034f) % 1f * 100f));
        return true;
    }

    /** 1 at the source, easing to 0 at radius. */
    static double falloff(double distance, double radius) {
        if (distance >= radius) return 0;
        double x = 1 - distance / radius;
        return x * Math.sqrt(x);
    }

    public void clear() {
        active.clear();
    }

    public boolean busy() {
        return !active.isEmpty();
    }

    /** Sums every active effect at this time, scaled by the player's settings; drops finished ones. */
    public Frame frame(long now, ModConfig cfg) {
        if (active.isEmpty()) return Frame.NONE;
        float yaw = 0, pitch = 0, roll = 0;
        float flashA = 0, vigA = 0, pulseA = 0, pulseR = 0;
        float fr = 0, fg = 0, fb = 0, vr = 0, vg = 0, vb = 0, pr = 0, pg = 0, pb = 0;
        Iterator<Active> it = active.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (now - a.start() >= a.duration()) {
                it.remove();
                continue;
            }
            float t = a.t(now);
            double secs = (now - a.start()) / 1000.0;
            switch (a.kind()) {
                case SHAKE -> {
                    // Fast at first, settling: layered sines with the seed as a phase, squared fade.
                    float env = (1 - t) * (1 - t) * a.strength() * MAX_SHAKE_DEGREES * (float) cfg.shake;
                    yaw += env * wave(secs, 17.0, a.seed());
                    pitch += env * 0.8f * wave(secs, 21.0, a.seed() + 3.1f);
                    roll += env * 0.5f * wave(secs, 13.0, a.seed() + 7.7f);
                }
                case FLASH -> {
                    float alpha;
                    if (cfg.noFlashing) {
                        // A slow soft swell instead of a blink, never brighter than a quarter.
                        alpha = (float) Math.sin(Math.PI * t) * 0.25f * a.strength();
                    } else {
                        float attack = Math.min(1f, t / 0.08f);
                        alpha = attack * (1 - t) * (1 - t) * 0.9f * a.strength();
                    }
                    alpha *= (float) cfg.flash;
                    if (alpha > 0) {
                        fr += alpha * r(a.color());
                        fg += alpha * g(a.color());
                        fb += alpha * b(a.color());
                        flashA += alpha;
                    }
                }
                case VIGNETTE -> {
                    float in = Math.min(1f, t / 0.12f);
                    float out = t > 0.6f ? 1 - (t - 0.6f) / 0.4f : 1f;
                    float alpha = in * out * a.strength() * 0.85f * (float) cfg.vignette;
                    vr += alpha * r(a.color());
                    vg += alpha * g(a.color());
                    vb += alpha * b(a.color());
                    vigA += alpha;
                }
                case PULSE -> {
                    float alpha = (1 - t) * a.strength() * 0.6f * (float) (cfg.noFlashing ? 0.5 * cfg.flash : cfg.flash);
                    pr += alpha * r(a.color());
                    pg += alpha * g(a.color());
                    pb += alpha * b(a.color());
                    pulseA += alpha;
                    pulseR = Math.max(pulseR, easeOut(t));
                }
            }
        }
        return new Frame(yaw, pitch, roll, rgb(fr, fg, fb, flashA), Math.min(0.95f, flashA), rgb(vr, vg, vb, vigA),
                Math.min(1f, vigA), rgb(pr, pg, pb, pulseA), Math.min(0.8f, pulseA), pulseR);
    }

    private static float wave(double secs, double hz, float seed) {
        return (float) (0.6 * Math.sin(secs * hz + seed) + 0.3 * Math.sin(secs * hz * 2.3 + seed * 1.7)
                + 0.1 * Math.sin(secs * hz * 5.1 + seed * 0.3));
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static float r(int c) {
        return ((c >> 16) & 255) / 255f;
    }

    private static float g(int c) {
        return ((c >> 8) & 255) / 255f;
    }

    private static float b(int c) {
        return (c & 255) / 255f;
    }

    /** The weighted average colour of the effects that were summed. */
    private static int rgb(float r, float g, float b, float weight) {
        if (weight <= 0) return 0;
        int ir = Math.round(Math.min(1, r / weight) * 255), ig = Math.round(Math.min(1, g / weight) * 255),
                ib = Math.round(Math.min(1, b / weight) * 255);
        return ir << 16 | ig << 8 | ib;
    }
}
