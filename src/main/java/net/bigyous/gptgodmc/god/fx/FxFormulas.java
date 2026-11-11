package net.bigyous.gptgodmc.god.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.FxScenes;

/**
 * The plugin's side of the two scene layers the god writes with formulas: "field" ({@link FxField}) and "solid"
 * ({@link FxSolid}). Pure. Checks what the god wrote, and sketches both in dust for players without the mod.
 */
public final class FxFormulas {
    private FxFormulas() {
    }

    public static final List<String> TYPES = List.of("field", "solid");
    public static final int DEFAULT_TICKS = FxField.DEFAULT_TICKS;

    /** What the god reads about these layers in the effect language's reference. */
    public static final String HELP = "field {count (max " + FxField.MAX_COUNT + "), duration, x, y, z, size, alpha, shade, "
            + "color, sprite, glow: particles placed by your own formulas, worked out for each particle every tick, so any "
            + "shape and any motion you can write as maths; x y z are blocks from the scene's place; a formula is a number "
            + "or text, e.g. \"x\":\"cos(u*tau*4+t*0.05)*(1+u*5)\",\"y\":\"u*12\",\"z\":\"sin(u*tau*4+t*0.05)*(1+u*5)\"; "
            + "color may list up to " + FxField.MAX_STOPS + " colours and shade (0-1, default u) picks along them; texture: a "
            + "texture you painted with fx_paint, as each particle's picture}. A formula "
            + "may use: " + FxExpr.NAMES + ". solid {form: box {size [x,y,z]} | ball {radius} | tube {path [[x,y,z],...], "
            + "width or [start,end], sides} | lathe {profile [[radius,y],...] spun round the vertical, sides} | prism "
            + "{outline [[x,z],...], height, taper 0-1 (0 = a point)} | quads {quads: faces of 3 or 4 corners [x,y,z]}; "
            + "geometry you design yourself, of matter, or of light with light true; color [bottom, top]; texture: a texture "
            + "you painted with fx_paint covers it (tile n or [across, up] repeats it); count up to "
            + FxSolid.MAX_COPIES + " copies; x, y, z, scale, yaw, pitch, roll (degrees) and alpha are formulas (each copy "
            + "has its own i, u, n, r), e.g. twelve pillars in a ring: \"count\":12,\"x\":\"cos(i/n*tau)*6\",\"z\":"
            + "\"sin(i/n*tau)*6\"; box and ball sit round their own middle}.";

    /** Says, as layer n, what is wrong with a field or solid the god wrote. */
    public static void check(JsonObject layer, String type, int n) throws FxScenes.Invalid {
        try {
            if (type.equals("field")) FxField.check(layer);
            else FxSolid.check(layer);
        } catch (FxExpr.Invalid e) {
            String m = e.getMessage();
            throw new FxScenes.Invalid("Layer " + n + ": " + m + (m.endsWith(".") ? "" : "."));
        }
    }

    /**
     * Adds this layer's dots for the plain sketch at this tick of the scene: a field's particles (fewer of them),
     * or points along a solid's edges.
     *
     * @param max the most dots the whole sketch may hold
     */
    public static void plain(JsonObject layer, String type, int tick, double scale, Random rnd, int max, List<FxScenes.Dot> out) {
        int room = max - out.size();
        if (room <= 0) return;
        double[] vars = new double[FxExpr.VARS];
        if (type.equals("field")) {
            FxField f = FxField.parse(layer);
            int t = tick - f.delay;
            if (t < 0 || t > f.duration) return;
            int want = Math.min(room, Math.max(12, Math.min(90, f.count / 6)));
            double[] o = new double[FxField.OUT];
            for (int k = 0; k < want; k++) {
                // Spread over the whole field, so its shape shows however few dots there are.
                int i = Math.min(f.count - 1, (int) ((k + rnd.nextDouble()) * f.count / want));
                f.at(i, f.count, t, 0, vars, o);
                if (o[FxField.ALPHA] < 0.05) continue;
                out.add(new FxScenes.Dot((f.offset[0] + o[FxField.X]) * scale, (f.offset[1] + o[FxField.Y]) * scale,
                        (f.offset[2] + o[FxField.Z]) * scale, f.color(o[FxField.SHADE]),
                        (float) Math.max(0.6, Math.min(3.5, o[FxField.SIZE] * 4 * scale))));
            }
            return;
        }
        FxSolid s = FxSolid.parse(layer);
        if (s == null) return;
        float[] built = s.build(tick - s.delay, 0, scale, 1, vars);
        List<double[]> points = new ArrayList<>();
        FxSolid.sketch(built, Math.min(room, Math.max(16, Math.min(80, built.length / FxSolid.FLOATS))), rnd, points);
        for (double[] p : points) out.add(new FxScenes.Dot(p[0], p[1], p[2], (int) p[3], 1f));
    }
}
