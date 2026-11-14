package net.bigyous.gptgodmc.god.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A "reskin" layer of a scene: for a while, some of the blocks round a place look like something else (the
 * world itself never changes). Which blocks: those of the named kinds, inside a disc or ball whose radius is
 * a formula of time (so it can creep outward), and of those only a share, picked block by block so the same
 * ones are chosen for every viewer. What they look like: another block ("as"), a texture the god painted,
 * or a wash of colour. Pure. {@link #parse} never throws and clamps everything; {@link #check} is the strict
 * reading, with messages meant for the god.
 *
 * This file is word for word the same in the plugin and in the client mod, apart from the package line.
 */
public final class FxReskin {
    public static final int MAX_RADIUS = 16;
    public static final int MAX_HEIGHT = 8;
    public static final int MAX_BLOCKS = 1500;
    public static final int MAX_KINDS = 12;
    public static final int MAX_TICKS = 20 * 60;
    public static final int DEFAULT_TICKS = 200;
    public static final String BLOCK_NAME = "[a-z0-9_]{1,48}";

    public final int delay, duration, limit;
    public final double[] offset;
    /** The kinds of block that change (ids without "minecraft:"); empty means any solid block. */
    public final List<String> blocks;
    /** The block they look like, or "". */
    public final String as;
    /** The painted texture they wear, or "". */
    public final String texture;
    /** The colour they are washed with (RGB), or -1; strength 0..1 is how strongly. */
    public final int color;
    public final float strength;
    /** How far it reaches, in blocks, as time passes. */
    public final FxExpr radius;
    /** How far above and below the place it reaches (a disc); a ball ignores it. */
    public final int height;
    public final boolean ball;
    /** The share of the fitting blocks that change, 0..1. */
    public final double density;

    private FxReskin(int delay, int duration, int limit, double[] offset, List<String> blocks, String as, String texture, int color,
            float strength, FxExpr radius, int height, boolean ball, double density) {
        this.delay = delay;
        this.duration = duration;
        this.limit = limit;
        this.offset = offset;
        this.blocks = blocks;
        this.as = as;
        this.texture = texture;
        this.color = color;
        this.strength = strength;
        this.radius = radius;
        this.height = height;
        this.ball = ball;
        this.density = density;
    }

    /** Reads a layer leniently. Null when it would change nothing. */
    public static FxReskin parse(JsonObject o) {
        List<String> blocks = new ArrayList<>();
        JsonElement b = o.get("blocks");
        if (b != null && b.isJsonArray()) {
            for (JsonElement e : b.getAsJsonArray()) {
                String id = name(e);
                if (!id.isEmpty() && blocks.size() < MAX_KINDS) blocks.add(id);
            }
        } else if (!name(b).isEmpty()) {
            blocks.add(name(b));
        }
        String as = name(o.get("as")), texture = word(o.get("texture"));
        if (!texture.matches("[a-z0-9_.-]{1,80}")) texture = "";
        int color = -1;
        JsonElement c = o.get("color");
        if (c != null && c.isJsonPrimitive() && c.getAsString().trim().matches("#[0-9a-fA-F]{6}")) {
            color = Integer.parseInt(c.getAsString().trim().substring(1), 16);
        }
        if (as.isEmpty() && texture.isEmpty() && color < 0) return null;
        FxExpr radius;
        try {
            radius = FxField.formula(o.get("radius"), FxExpr.of(4));
        } catch (FxExpr.Invalid e) {
            radius = FxExpr.of(4);
        }
        return new FxReskin((int) FxField.num(o.get("delay"), 0, 0, MAX_TICKS), (int) FxField.num(o.get("duration"), DEFAULT_TICKS, 1, MAX_TICKS),
                (int) FxField.num(o.get("limit"), 600, 1, MAX_BLOCKS), FxField.vec(o.get("offset"), 64), List.copyOf(blocks), as, texture, color,
                (float) FxField.num(o.get("strength"), 0.6, 0, 1), radius, (int) FxField.num(o.get("height"), 2, 0, MAX_HEIGHT),
                word(o.get("shape")).equals("ball") || word(o.get("shape")).equals("sphere"), FxField.num(o.get("density"), 1, 0, 1));
    }

    /** The strict reading. */
    public static void check(JsonObject o) throws FxExpr.Invalid {
        boolean look = false;
        for (String key : new String[] { "as", "texture", "color" }) look |= o.has(key) && !o.get(key).isJsonNull();
        if (!look) {
            throw new FxExpr.Invalid("a reskin needs what the blocks should look like: \"as\": another block (e.g. \"coarse_dirt\"), "
                    + "\"texture\": one you painted, or \"color\": a wash of \"#RRGGBB\"");
        }
        if (o.has("as") && name(o.get("as")).isEmpty()) throw new FxExpr.Invalid("\"as\" is a block id such as coarse_dirt or dead_bush");
        JsonElement b = o.get("blocks");
        if (b != null && b.isJsonArray()) {
            JsonArray a = b.getAsJsonArray();
            if (a.size() > MAX_KINDS) throw new FxExpr.Invalid("at most " + MAX_KINDS + " kinds of block in \"blocks\"");
            for (JsonElement e : a) {
                if (name(e).isEmpty()) throw new FxExpr.Invalid("\"blocks\" lists block ids such as grass_block (you wrote " + e + ")");
            }
        } else if (b != null && name(b).isEmpty()) {
            throw new FxExpr.Invalid("\"blocks\" lists block ids such as [\"grass_block\",\"short_grass\"]");
        }
        try {
            FxField.formula(o.get("radius"), null);
        } catch (FxExpr.Invalid bad) {
            throw new FxExpr.Invalid("radius: " + bad.getMessage());
        }
    }

    /** How far it reaches at tick t, 0..MAX_RADIUS. */
    public double reach(double t, double[] vars) {
        vars[FxExpr.T] = t;
        vars[FxExpr.P] = Math.max(0, Math.min(1, t / duration));
        vars[FxExpr.I] = 0;
        vars[FxExpr.U] = 0;
        vars[FxExpr.N] = 1;
        vars[FxExpr.SEED] = 0;
        return radius.eval(vars, 0, MAX_RADIUS, 4);
    }

    /** Whether a block this far from the place (in blocks) is within reach r. */
    public boolean inside(double dx, double dy, double dz, double r) {
        if (ball) return dx * dx + dy * dy + dz * dz <= r * r;
        return dx * dx + dz * dz <= r * r && Math.abs(dy) <= height + 0.5;
    }

    /** Whether this block is one of the share that changes: the same answer for everyone, every time. */
    public boolean picks(int x, int y, int z) {
        return density >= 1 || FxExpr.hash(x, y, z, 7) < density;
    }

    /** Whether a block of this kind (an id with or without "minecraft:") is one that changes. */
    public boolean changes(String id) {
        if (blocks.isEmpty()) return true;
        String plain = id.startsWith("minecraft:") ? id.substring(10) : id;
        return blocks.contains(plain);
    }

    private static String word(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsString().trim().toLowerCase(Locale.ROOT) : "";
    }

    /** A block id without its namespace, or "" when it is not one. */
    private static String name(JsonElement e) {
        String s = word(e).replace(' ', '_');
        if (s.startsWith("minecraft:")) s = s.substring(10);
        return s.matches(BLOCK_NAME) ? s : "";
    }
}
