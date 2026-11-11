package net.bigyous.gptgod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.bigyous.gptgod.client.config.ModConfig;

/**
 * Brief takeovers of the camera, as pure maths: zoom (a punch in or out), roll (the horizon tilts),
 * letterbox (cinema bars) and gaze (the view is drawn part of the way toward a point). They change only
 * how the frame is drawn, never where the player is looking or moving, and all are short and capped.
 */
public final class CameraFx {
    public static final int MAX_ACTIVE = 12;
    public static final long MAX_MS = 12_000;
    /** The most the view may be turned toward a point, in degrees. */
    public static final float MAX_GAZE = 28;

    public enum Kind {
        ZOOM, ROLL, LETTERBOX, GAZE;

        public static Kind of(String id) {
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(id)) return k;
            }
            return null;
        }
    }

    record Active(Kind kind, float amount, long start, long duration, double[] at) {
    }

    /** zoom 1 = normal (2 = twice as close); roll in degrees; bars 0..1; gaze yaw/pitch offsets in degrees. */
    public record Frame(float zoom, float roll, float bars, float gazeYaw, float gazePitch) {
        public static final Frame NONE = new Frame(1, 0, 0, 0, 0);

        public boolean idle() {
            return zoom == 1 && roll == 0 && bars == 0 && gazeYaw == 0 && gazePitch == 0;
        }
    }

    private final List<Active> active = new ArrayList<>();

    /**
     * @param amount zoom: -1..1 (positive = closer); roll: -1..1 (x 20 degrees); letterbox: 0..1; gaze: 0..1 of
     *               the way toward at
     */
    public boolean add(Kind kind, float amount, long durationMs, double[] at, long now) {
        if (kind == null || Math.abs(amount) < 0.02f) return false;
        if (kind == Kind.GAZE && at == null) return false;
        if (active.size() >= MAX_ACTIVE) active.remove(0);
        active.add(new Active(kind, Math.max(-1, Math.min(1, amount)), now, Math.max(150, Math.min(MAX_MS, durationMs)), at));
        return true;
    }

    public void clear() {
        active.clear();
    }

    public boolean busy() {
        return !active.isEmpty();
    }

    /** Eases in over the first fifth, holds, eases out over the last third. */
    static float envelope(float t) {
        float in = Math.min(1, t / 0.2f), out = Math.min(1, (1 - t) / 0.33f);
        float e = Math.max(0, Math.min(in, out));
        return e * e * (3 - 2 * e);
    }

    /**
     * @param eye       the camera's position
     * @param yaw,pitch where the player looks, in degrees (Minecraft's: yaw 0 = south, pitch + = down)
     */
    public Frame frame(long now, ModConfig cfg, double[] eye, float yaw, float pitch) {
        if (active.isEmpty()) return Frame.NONE;
        float motion = (float) cfg.shake;
        float zoom = 0, roll = 0, bars = 0, gy = 0, gp = 0;
        Iterator<Active> it = active.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (now - a.start() >= a.duration()) {
                it.remove();
                continue;
            }
            float e = envelope((now - a.start()) / (float) a.duration());
            switch (a.kind()) {
                case ZOOM -> zoom += a.amount() * e * motion;
                case ROLL -> roll += a.amount() * 20 * e * motion;
                case LETTERBOX -> bars = Math.max(bars, Math.abs(a.amount()) * e);
                case GAZE -> {
                    if (eye == null) continue;
                    double dx = a.at()[0] - eye[0], dy = a.at()[1] - eye[1], dz = a.at()[2] - eye[2];
                    double flat = Math.sqrt(dx * dx + dz * dz);
                    float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
                    float k = Math.abs(a.amount()) * e * motion;
                    gy += clamp(wrap(wantYaw - yaw) * k, MAX_GAZE);
                    gp += clamp((wantPitch - pitch) * k, MAX_GAZE);
                }
            }
        }
        if (active.isEmpty()) return Frame.NONE;
        return new Frame((float) Math.pow(2, Math.max(-1, Math.min(1, zoom))), clamp(roll, 25), Math.min(0.22f, bars * 0.22f),
                clamp(gy, MAX_GAZE), clamp(gp, MAX_GAZE));
    }

    private static float wrap(float degrees) {
        float d = degrees % 360;
        return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
    }

    private static float clamp(float v, float limit) {
        return Math.max(-limit, Math.min(limit, v));
    }
}
