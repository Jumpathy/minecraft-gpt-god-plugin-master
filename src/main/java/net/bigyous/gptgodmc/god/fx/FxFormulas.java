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

    public static final List<String> TYPES = List.of("field", "solid", "reskin", "trigger");
    public static final int DEFAULT_TICKS = FxField.DEFAULT_TICKS;

    /** What the god reads about these layers in the effect language's reference. */
    public static final String HELP = "field {count (max " + FxField.MAX_COUNT + "), duration, x, y, z, size, alpha, shade, "
            + "color, sprite, glow: particles placed by your own formulas, worked out for each particle every tick, so any "
            + "shape and any motion you can write as maths; x y z are blocks from the scene's place; a formula is a number "
            + "or text, e.g. \"x\":\"cos(u*tau*4+t*0.05)*(1+u*5)\",\"y\":\"u*12\",\"z\":\"sin(u*tau*4+t*0.05)*(1+u*5)\"; "
            + "color may list up to " + FxField.MAX_STOPS + " colours and shade (0-1, default u) picks along them; texture: a "
            + "texture you painted with fx_paint, as each particle's picture; size is a particle's width in blocks "
            + "(default small and varied). Particles feel alive when they are small (0.03-0.15), no two alike (size and "
            + "shade by r or rand(k)), flickering (alpha \"0.4+0.6*sin(t*0.2+r*tau)^2\"), born and dying each on its own "
            + "clock (alpha \"sin(fract(t/50+r)*pi)\", with the same fract in its place so it starts over somewhere "
            + "new) and drifting each its own way (noise(i, t*0.03)); many small ones beat a few large ones. Large, "
            + "identical, always-lit ones read as a wall of pictures: keep a painted texture or a big size for a few "
            + "dozen at most. A field's particles may also keep their motion, like real things: give any of ax, ay, az "
            + "(the push on each every tick, blocks a tick squared, e.g. \"ay\":-0.02 is gravity), vx, vy, vz (its first "
            + "speed), drag (0-1, default 0.02), bounce (0-1: it rebounds off blocks keeping that share of its speed; "
            + "left out it passes through them), life (ticks until it is born again at x, y, z); then x, y, z are only "
            + "where it is born, and its formulas may also read px py pz (where it is now), vx vy vz (its speed), a (0-1 "
            + "through its life) and k (which life this is: rand(k) differs each time), e.g. motes that shy from whoever "
            + "walks among them: \"ax\":\"(px-ex)*0.02/max(0.3,(px-ex)^2+(pz-ez)^2)\" and the same for az}. A formula "
            + "may use: " + FxExpr.NAMES + ". solid {form: box {size [x,y,z]} | ball {radius} | tube {path [[x,y,z],...], "
            + "width or [start,end], sides} | lathe {profile [[radius,y],...] spun round the vertical, sides} | prism "
            + "{outline [[x,z],...], height, taper 0-1 (0 = a point)} | quads {quads: faces of 3 or 4 corners [x,y,z]}; "
            + "geometry you design yourself, of matter, or of light with light true; color [bottom, top]; texture: a texture "
            + "you painted with fx_paint covers it (tile n or [across, up] repeats it); count up to "
            + FxSolid.MAX_COPIES + " copies; x, y, z, scale, yaw, pitch, roll (degrees) and alpha are formulas (each copy "
            + "has its own i, u, n, r), e.g. twelve pillars in a ring: \"count\":12,\"x\":\"cos(i/n*tau)*6\",\"z\":"
            + "\"sin(i/n*tau)*6\"; box and ball sit round their own middle}. reskin {blocks: the kinds that change, e.g. [\"grass_block\",\"short_grass\"] "
            + "(left out = any solid block); as: the block they look like (e.g. coarse_dirt, dead_bush; only one as solid as "
            + "the block itself) and/or texture: one you painted and/or color with strength 0-1: a wash; radius: how far it "
            + "reaches, at most " + FxReskin.MAX_RADIUS + ", a number or a formula of t and p so it can creep outward, e.g. "
            + "\"min(5,t*0.1)\"; height: blocks above and below (default 2), or shape sphere; density 0-1: the share of the "
            + "fitting blocks that change, so 0.4 wilts some grass and not all of it; duration (default " + FxReskin.DEFAULT_TICKS
            + "): for a while some blocks round the place look like something else, to every mortal near; the world itself "
            + "never changes and they look like themselves again afterwards. Mortals without the client mod see only \"as\"}. "
            + "trigger {when: a formula that is not 0 when it should fire, watched for each mortal near (d, look, speed, ex "
            + "ey ez, t), e.g. \"d < 4\" or \"(look > 0.97) * (d < 24)\"; hold: ticks it must stay so (default 1); "
            + "then: up to " + FxTrigger.MAX_THEN + " layers that play at once, for that mortal only (colours as #RRGGBB, no "
            + "painted texture, no trigger); tell: what you are told they did, e.g. \"looked into the eye\" (you hear "
            + "\"<name> looked into the eye\"); wake true if hearing it should wake you; again: ticks before it may fire "
            + "again for the same mortal (left out = once); duration (default " + FxTrigger.DEFAULT_TICKS + "): how long it "
            + "watches. This is how a scene answers what a mortal does the instant they do it, and how you learn of it}.";

    /** Says, as layer n, what is wrong with a field or solid the god wrote. */
    public static void check(JsonObject layer, String type, int n) throws FxScenes.Invalid {
        try {
            if (type.equals("field")) FxField.check(layer);
            else if (type.equals("reskin")) FxReskin.check(layer);
            else if (type.equals("trigger")) FxTrigger.check(layer);
            else FxSolid.check(layer);
        } catch (FxExpr.Invalid e) {
            String m = e.getMessage();
            throw new FxScenes.Invalid("Layer " + n + ": " + m + (m.endsWith(".") ? "" : "."));
        }
        if (type.equals("trigger")) then(layer, n);
    }

    /** The layers a trigger plays are a small scene of their own, checked as one. */
    private static void then(JsonObject layer, int n) throws FxScenes.Invalid {
        com.google.gson.JsonElement t = layer.get("then");
        if (t == null || t.isJsonNull()) return;
        com.google.gson.JsonArray cleaned;
        try {
            cleaned = FxScenes.check(t.toString());
        } catch (FxScenes.Invalid e) {
            throw new FxScenes.Invalid("Layer " + n + ", in \"then\": " + e.getMessage());
        }
        for (com.google.gson.JsonElement e : cleaned) {
            JsonObject l = e.getAsJsonObject();
            if (l.get("type").getAsString().equals("trigger")) {
                throw new FxScenes.Invalid("Layer " + n + ": a trigger's \"then\" may not hold another trigger.");
            }
            if (l.has("texture") || l.toString().contains("\"$")) {
                throw new FxScenes.Invalid("Layer " + n + ": inside \"then\" write colours as \"#RRGGBB\" and use no painted texture.");
            }
        }
        layer.add("then", cleaned);
    }

    /**
     * Adds this layer's dots for the plain sketch at this tick of the scene: a field's particles (fewer of them),
     * or points along a solid's edges.
     *
     * @param max the most dots the whole sketch may hold
     */
    public static void plain(JsonObject layer, String type, int tick, double scale, Random rnd, int max, List<FxScenes.Dot> out) {
        int room = max - out.size();
        // A reskin is not sketched in dust: FxReskinShow shows its blocks themselves.
        if (room <= 0 || type.equals("reskin") || type.equals("trigger")) return;
        double[] vars = new double[FxExpr.VARS];
        FxExpr.nobody(vars);
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
