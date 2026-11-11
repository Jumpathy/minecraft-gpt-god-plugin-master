package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.joml.Quaternionf;

import net.bigyous.gptgodmc.god.vessel.VesselModel.Pose;

/**
 * Animations the god writes itself: keyframes of bone angles and body offsets over time, plus cues
 * (particles and sounds on given moments), played once or looped, blended over the vessel's own idle
 * pose. Pure (no server), so parsing, clamping and interpolation are unit tested.
 *
 * <p>A keyframe is one line of tokens, angles in degrees:
 * {@code t=0.5 body=pitch,turn,roll torso=bend head=turn,tilt larm=fwd,out[,twist] rarm=… lleg=fwd,out
 * rleg=… cape=flare rise=h slide=x,z ease=smooth|linear|snap}. A channel left out of a keyframe follows
 * the idle pose until a keyframe sets it. A cue is {@code t=1 fx=heart at=rhand n=8 sound=entity.player.levelup
 * pitch=1.2 vol=1}.
 */
public final class Choreo {
    private Choreo() {
    }

    public static final int MAX_KEYS = 16;
    public static final int MAX_CUES = 16;
    /** Longest single pass, in ticks (20 s). */
    public static final int MAX_TICKS = 400;
    public static final int MAX_LOOP_SECONDS = 300;
    public static final int MAX_SAVED = 24;
    /** Ticks to blend in from idle, and back out at the end. */
    public static final int FADE_IN = 6;
    public static final int FADE_OUT = 10;

    /** Every animated channel, with its clamp range (degrees, or figure heights for rise/slide). */
    public enum Ch {
        BODY_PITCH(-90, 90), BODY_YAW(-720, 720), BODY_ROLL(-45, 45), TORSO(-45, 90), HEAD_YAW(-80, 80),
        HEAD_PITCH(-50, 55), LARM_FWD(-60, 200), LARM_OUT(-60, 150), LARM_TWIST(-90, 90), RARM_FWD(-60, 200),
        RARM_OUT(-60, 150), RARM_TWIST(-90, 90), LLEG_FWD(-60, 120), LLEG_OUT(-15, 50), RLEG_FWD(-60, 120),
        RLEG_OUT(-15, 50), CAPE(-20, 110), RISE(-0.6f, 1.5f), SLIDE_X(-0.5f, 0.5f), SLIDE_Z(-0.5f, 0.5f);

        public final float min, max;

        Ch(float min, float max) {
            this.min = min;
            this.max = max;
        }
    }

    private static final Ch[] CH = Ch.values();

    /** Groups set together by one token. */
    private static final Map<String, Ch[]> TOKENS = new LinkedHashMap<>();

    static {
        TOKENS.put("body", new Ch[] { Ch.BODY_PITCH, Ch.BODY_YAW, Ch.BODY_ROLL });
        TOKENS.put("torso", new Ch[] { Ch.TORSO });
        TOKENS.put("head", new Ch[] { Ch.HEAD_YAW, Ch.HEAD_PITCH });
        TOKENS.put("larm", new Ch[] { Ch.LARM_FWD, Ch.LARM_OUT, Ch.LARM_TWIST });
        TOKENS.put("rarm", new Ch[] { Ch.RARM_FWD, Ch.RARM_OUT, Ch.RARM_TWIST });
        TOKENS.put("lleg", new Ch[] { Ch.LLEG_FWD, Ch.LLEG_OUT });
        TOKENS.put("rleg", new Ch[] { Ch.RLEG_FWD, Ch.RLEG_OUT });
        TOKENS.put("cape", new Ch[] { Ch.CAPE });
        TOKENS.put("rise", new Ch[] { Ch.RISE });
        TOKENS.put("slide", new Ch[] { Ch.SLIDE_X, Ch.SLIDE_Z });
    }

    private static final Map<String, String> ALIASES = Map.ofEntries(Map.entry("left_arm", "larm"),
            Map.entry("right_arm", "rarm"), Map.entry("left_leg", "lleg"), Map.entry("right_leg", "rleg"),
            Map.entry("l_arm", "larm"), Map.entry("r_arm", "rarm"), Map.entry("l_leg", "lleg"),
            Map.entry("r_leg", "rleg"), Map.entry("chest", "torso"), Map.entry("lift", "rise"),
            Map.entry("time", "t"));

    public enum Ease {
        SMOOTH, LINEAR, SNAP
    }

    /** Where a cue's particles appear. */
    public static final List<String> SPOTS = List.of("rhand", "lhand", "hands", "head", "body", "feet", "above");

    /** One keyframe: its time (ticks) and a value per channel (NaN = not set here). */
    public record Key(int tick, float[] values, Ease ease) {
        public boolean has(Ch c) {
            return !Float.isNaN(values[c.ordinal()]);
        }

        public float get(Ch c) {
            return values[c.ordinal()];
        }
    }

    /** A moment's particles and/or sound. */
    public record Cue(int tick, String particle, String at, int count, String sound, float pitch, float volume) {
    }

    /** A parsed, clamped animation. warnings lists every value that was clamped or ignored. */
    public record Anim(String name, List<Key> keys, List<Cue> cues, int length, boolean loop, List<String> warnings) {
        /** How long one pass plays before it fades back to idle, in ticks. */
        public int passTicks() {
            return loop ? length : length + FADE_OUT;
        }

        /** The text that parses back to this animation (saved in memory). */
        public String text() {
            StringBuilder sb = new StringBuilder();
            sb.append(loop ? "loop" : "once").append(" length=").append(fmt(length / 20f)).append('\n');
            for (Key k : keys) sb.append(keyText(k)).append('\n');
            for (Cue c : cues) sb.append(cueText(c)).append('\n');
            return sb.toString().trim();
        }

        public String summary() {
            return name + " (" + fmt(length / 20f) + "s, " + keys.size() + " keys" + (cues.isEmpty() ? ""
                    : ", " + cues.size() + " cues") + (loop ? ", looping" : "") + ")";
        }
    }

    /** Thrown with a message phrased for the god. */
    public static final class Invalid extends RuntimeException {
        public Invalid(String message) {
            super(message);
        }
    }

    // ---- Parsing ------------------------------------------------------------------------------

    /**
     * Parses keyframes and cues into an animation.
     *
     * @param particles valid particle names (null accepts any well-formed name)
     */
    public static Anim parse(String name, List<String> keyLines, List<String> cueLines, boolean loop,
            Collection<String> particles) {
        List<String> warn = new ArrayList<>();
        if (keyLines == null || keyLines.isEmpty()) throw new Invalid("An animation needs at least one keyframe.");
        List<String> kl = new ArrayList<>(keyLines);
        if (kl.size() > MAX_KEYS) {
            warn.add("only the first " + MAX_KEYS + " keyframes were kept");
            kl = kl.subList(0, MAX_KEYS);
        }
        List<Key> keys = new ArrayList<>();
        int auto = 0;
        for (String line : kl) {
            Key k = parseKey(line, auto, warn);
            keys.add(k);
            auto = k.tick + 10;
        }
        keys.sort((a, b) -> Integer.compare(a.tick, b.tick));
        // Two keys at the same moment: the later line wins.
        List<Key> unique = new ArrayList<>();
        for (Key k : keys) {
            if (!unique.isEmpty() && unique.get(unique.size() - 1).tick == k.tick) unique.remove(unique.size() - 1);
            unique.add(k);
        }
        int length = Math.max(1, unique.get(unique.size() - 1).tick);
        List<Cue> cues = new ArrayList<>();
        if (cueLines != null) {
            List<String> cl = cueLines;
            if (cl.size() > MAX_CUES) {
                warn.add("only the first " + MAX_CUES + " cues were kept");
                cl = cl.subList(0, MAX_CUES);
            }
            for (String line : cl) {
                Cue c = parseCue(line, warn, particles);
                if (c == null) continue;
                if (c.tick > length) {
                    warn.add("a cue at " + fmt(c.tick / 20f) + "s came after the last keyframe; moved to the end");
                    c = new Cue(length, c.particle, c.at, c.count, c.sound, c.pitch, c.volume);
                }
                cues.add(c);
            }
            cues.sort((a, b) -> Integer.compare(a.tick, b.tick));
        }
        boolean anything = false;
        for (Key k : unique) {
            for (float v : k.values) anything |= !Float.isNaN(v);
        }
        if (!anything && cues.isEmpty()) throw new Invalid("No keyframe sets any bone (e.g. 't=0 torso=30').");
        return new Anim(clean(name), List.copyOf(unique), List.copyOf(cues), length, loop, List.copyOf(warn));
    }

    /** Parses the saved text form (see {@link Anim#text()}). */
    public static Anim fromText(String name, String text) {
        List<String> keys = new ArrayList<>();
        List<String> cues = new ArrayList<>();
        boolean loop = false;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("loop") || line.startsWith("once")) {
                loop = line.startsWith("loop");
            } else if (line.contains("fx=") || line.contains("sound=")) {
                cues.add(line);
            } else {
                keys.add(line);
            }
        }
        return parse(name, keys, cues, loop, null);
    }

    static Key parseKey(String line, int autoTick, List<String> warn) {
        float[] v = new float[CH.length];
        Arrays.fill(v, Float.NaN);
        int tick = autoTick;
        Ease ease = Ease.SMOOTH;
        for (String tok : tokens(line)) {
            int eq = tok.indexOf('=');
            if (eq <= 0) {
                warn.add("ignored '" + tok + "' (write name=value)");
                continue;
            }
            String k = tok.substring(0, eq).toLowerCase(Locale.ROOT);
            k = ALIASES.getOrDefault(k, k);
            String val = tok.substring(eq + 1);
            if (k.equals("t")) {
                tick = seconds(val, warn, "t");
                continue;
            }
            if (k.equals("ease")) {
                try {
                    ease = Ease.valueOf(val.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    warn.add("unknown ease '" + val + "' (smooth, linear, snap)");
                }
                continue;
            }
            Ch[] group = TOKENS.get(k);
            if (group == null) {
                warn.add("unknown bone '" + k + "' (body, torso, head, larm, rarm, lleg, rleg, cape, rise, slide)");
                continue;
            }
            String[] parts = val.split(",");
            for (int i = 0; i < group.length; i++) {
                float x = 0;
                if (i < parts.length && !parts[i].isBlank()) {
                    try {
                        x = Float.parseFloat(parts[i].trim());
                    } catch (NumberFormatException e) {
                        warn.add(k + ": '" + parts[i] + "' is not a number");
                    }
                }
                if (!Float.isFinite(x)) x = 0;
                Ch c = group[i];
                float clamped = Math.max(c.min, Math.min(c.max, x));
                if (clamped != x) warn.add(k + " " + fmt(x) + " clamped to " + fmt(clamped));
                v[c.ordinal()] = clamped;
            }
            if (parts.length > group.length) warn.add(k + " takes " + group.length + " values; extras ignored");
        }
        return new Key(tick, v, ease);
    }

    static Cue parseCue(String line, List<String> warn, Collection<String> particles) {
        int tick = 0;
        String fx = null, at = "body", sound = null;
        int n = 8;
        float pitch = 1, vol = 1;
        for (String tok : tokens(line)) {
            int eq = tok.indexOf('=');
            if (eq <= 0) continue;
            String k = tok.substring(0, eq).toLowerCase(Locale.ROOT);
            String val = tok.substring(eq + 1).trim();
            switch (k) {
                case "t", "time" -> tick = seconds(val, warn, "cue t");
                case "fx", "particle" -> {
                    String p = val.toLowerCase(Locale.ROOT).replace("minecraft:", "");
                    if (!p.matches("[a-z_]{2,40}") || (particles != null && !particles.contains(p))) {
                        warn.add("unknown particle '" + val + "' in a cue");
                    } else {
                        fx = p;
                    }
                }
                case "at" -> {
                    String s = val.toLowerCase(Locale.ROOT);
                    if (SPOTS.contains(s)) at = s;
                    else warn.add("cue at '" + val + "' unknown (" + String.join(", ", SPOTS) + ")");
                }
                case "n", "count" -> n = Math.max(1, Math.min(40, (int) num(val, 8)));
                case "sound" -> {
                    String s = val.toLowerCase(Locale.ROOT).replace("minecraft:", "");
                    if (s.matches("[a-z0-9_.]{3,80}")) sound = s;
                    else warn.add("bad sound name '" + val + "'");
                }
                case "pitch" -> pitch = Math.max(0.5f, Math.min(2f, num(val, 1)));
                case "vol", "volume" -> vol = Math.max(0.1f, Math.min(2f, num(val, 1)));
                default -> warn.add("unknown cue field '" + k + "'");
            }
        }
        if (fx == null && sound == null) return null;
        return new Cue(tick, fx, at, n, sound, pitch, vol);
    }

    private static int seconds(String val, List<String> warn, String what) {
        float s = num(val.replace("s", ""), 0);
        float c = Math.max(0, Math.min(MAX_TICKS / 20f, s));
        if (c != s) warn.add(what + "=" + fmt(s) + " clamped to " + fmt(c) + "s (at most " + (MAX_TICKS / 20) + "s)");
        return Math.round(c * 20);
    }

    private static float num(String s, float fallback) {
        try {
            float f = Float.parseFloat(s.trim());
            return Float.isFinite(f) ? f : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static List<String> tokens(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        for (String t : line.trim().split("[\\s;]+")) {
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** A saved animation's name: lowercase letters, digits and underscores, at most 24. */
    public static String clean(String name) {
        if (name == null) return "dance";
        String n = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (n.isEmpty()) n = "dance";
        return n.length() > 24 ? n.substring(0, 24) : n;
    }

    static String keyText(Key k) {
        StringBuilder sb = new StringBuilder("t=").append(fmt(k.tick / 20f));
        for (Map.Entry<String, Ch[]> e : TOKENS.entrySet()) {
            Ch[] g = e.getValue();
            if (!k.has(g[0])) continue;
            sb.append(' ').append(e.getKey()).append('=');
            for (int i = 0; i < g.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(fmt(k.get(g[i])));
            }
        }
        if (k.ease != Ease.SMOOTH) sb.append(" ease=").append(k.ease.name().toLowerCase(Locale.ROOT));
        return sb.toString();
    }

    static String cueText(Cue c) {
        StringBuilder sb = new StringBuilder("t=").append(fmt(c.tick / 20f));
        if (c.particle != null) sb.append(" fx=").append(c.particle).append(" at=").append(c.at).append(" n=")
                .append(c.count);
        if (c.sound != null) sb.append(" sound=").append(c.sound).append(" pitch=").append(fmt(c.pitch))
                .append(" vol=").append(fmt(c.volume));
        return sb.toString();
    }

    static String fmt(float f) {
        if (f == Math.rint(f)) return String.valueOf((int) f);
        String s = String.format(Locale.ROOT, "%.2f", f);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // ---- Sampling -----------------------------------------------------------------------------

    /** A channel's value at a moment and how strongly it overrides idle (0..1). */
    public record Sample(float[] values, float[] weights) {
    }

    /**
     * Each channel's value at a tick of one pass. Between the keyframes that set a channel it eases
     * from one to the next; before the first it blends in from idle; after the last it holds (or, looping,
     * eases back to the first).
     */
    public static Sample sample(Anim a, float tick) {
        float[] val = new float[CH.length];
        float[] w = new float[CH.length];
        float t = a.loop ? floorMod(tick, a.length) : Math.max(0, Math.min(tick, a.length));
        for (Ch c : CH) {
            Key prev = null, next = null;
            Key first = null, last = null;
            for (Key k : a.keys) {
                if (!k.has(c)) continue;
                if (first == null) first = k;
                last = k;
                if (k.tick <= t) prev = k;
                if (k.tick >= t && next == null) next = k;
            }
            int i = c.ordinal();
            if (first == null) {
                val[i] = Float.NaN;
                continue;
            }
            float pt, nt, pv, nv;
            Ease ease;
            if (prev == null) {
                if (a.loop && last != first) {
                    // Wraps around from the last key of the previous pass.
                    pt = last.tick - a.length;
                    pv = last.get(c);
                    nt = next.tick;
                    nv = next.get(c);
                    ease = next.ease;
                } else {
                    // Blending in from idle until the first key that sets it.
                    val[i] = next.get(c);
                    w[i] = next.tick == 0 ? 1 : ease(next.ease, t / next.tick);
                    continue;
                }
            } else if (next == null) {
                if (a.loop && last != first) {
                    pt = prev.tick;
                    pv = prev.get(c);
                    nt = first.tick + a.length;
                    nv = first.get(c);
                    ease = first.ease;
                } else {
                    val[i] = prev.get(c);
                    w[i] = 1;
                    continue;
                }
            } else {
                pt = prev.tick;
                pv = prev.get(c);
                nt = next.tick;
                nv = next.get(c);
                ease = next.ease;
            }
            float f = nt <= pt ? 1 : ease(ease, (t - pt) / (nt - pt));
            val[i] = pv + (nv - pv) * f;
            w[i] = 1;
        }
        return new Sample(val, w);
    }

    /**
     * How strongly the whole animation shows at an age (ticks since it began), blending in from idle and
     * back out at the end. total = ticks it plays in all (a pass, or a loop's whole run).
     */
    public static float weight(float age, int total) {
        if (age < 0 || age >= total) return 0;
        float in = Math.min(1, (age + 2) / FADE_IN);
        float out = Math.min(1, (total - age) / FADE_OUT);
        return smooth(Math.max(0, Math.min(in, out)));
    }

    /**
     * Lays the animation over a pose (the idle pose the vessel would otherwise take), by weight.
     * Arms and legs keep their idle orientation for any group the keyframes never set.
     */
    public static void apply(Pose p, Anim a, float age, float weight) {
        if (weight <= 0) return;
        Sample s = sample(a, age);
        float[] v = s.values();
        float[] w = s.weights();
        p.bodyPitch = mix(p.bodyPitch, rad(v, Ch.BODY_PITCH), w, Ch.BODY_PITCH, weight);
        // Turns wrap to half a turn either way, so a full spin blends back to idle without unwinding.
        float yaw = rad(v, Ch.BODY_YAW);
        if (!Float.isNaN(yaw)) yaw = (float) Math.IEEEremainder(yaw, Math.PI * 2);
        p.bodyYaw = mix(p.bodyYaw, yaw, w, Ch.BODY_YAW, weight);
        p.bodyRoll = mix(p.bodyRoll, rad(v, Ch.BODY_ROLL), w, Ch.BODY_ROLL, weight);
        p.torsoPitch = mix(p.torsoPitch, rad(v, Ch.TORSO), w, Ch.TORSO, weight);
        p.headYaw = mix(p.headYaw, rad(v, Ch.HEAD_YAW), w, Ch.HEAD_YAW, weight);
        p.headPitch = mix(p.headPitch, rad(v, Ch.HEAD_PITCH), w, Ch.HEAD_PITCH, weight);
        p.capePitch = mix(p.capePitch, rad(v, Ch.CAPE), w, Ch.CAPE, weight);
        float h = VesselModel.HEIGHT_PX;
        p.offset.y = mix(p.offset.y, v[Ch.RISE.ordinal()] * h, w, Ch.RISE, weight);
        p.offset.x = mix(p.offset.x, v[Ch.SLIDE_X.ordinal()] * h, w, Ch.SLIDE_X, weight);
        p.offset.z = mix(p.offset.z, v[Ch.SLIDE_Z.ordinal()] * h, w, Ch.SLIDE_Z, weight);
        limb(p.leftArm, v, w, Ch.LARM_FWD, Ch.LARM_OUT, Ch.LARM_TWIST, 1, weight);
        limb(p.rightArm, v, w, Ch.RARM_FWD, Ch.RARM_OUT, Ch.RARM_TWIST, -1, weight);
        limb(p.leftLeg, v, w, Ch.LLEG_FWD, Ch.LLEG_OUT, null, 1, weight);
        limb(p.rightLeg, v, w, Ch.RLEG_FWD, Ch.RLEG_OUT, null, -1, weight);
    }

    /** The pose of an animation alone at a tick (over the calm idle pose), for previews and tests. */
    public static Pose pose(Anim a, float tick) {
        Pose p = VesselModel.pose("idle", 0, 0, 0, null, null);
        apply(p, a, tick, 1);
        return p;
    }

    private static void limb(Quaternionf q, float[] v, float[] w, Ch fwd, Ch out, Ch twist, int side, float weight) {
        if (Float.isNaN(v[fwd.ordinal()])) return;
        float f = (float) Math.toRadians(v[fwd.ordinal()]);
        float o = (float) Math.toRadians(v[out.ordinal()]) * side;
        Quaternionf target = new Quaternionf().rotationX(-f).rotateZ(o);
        if (twist != null && !Float.isNaN(v[twist.ordinal()])) {
            target.rotateY((float) Math.toRadians(v[twist.ordinal()]) * side);
        }
        q.slerp(target, w[fwd.ordinal()] * weight);
    }

    private static float rad(float[] v, Ch c) {
        float x = v[c.ordinal()];
        return Float.isNaN(x) ? Float.NaN : (float) Math.toRadians(x);
    }

    private static float mix(float base, float target, float[] w, Ch c, float weight) {
        if (Float.isNaN(target)) return base;
        return base + (target - base) * w[c.ordinal()] * weight;
    }

    static float ease(Ease e, float f) {
        f = Math.max(0, Math.min(1, f));
        return switch (e) {
            case LINEAR -> f;
            case SNAP -> f < 0.5f ? 0 : 1;
            case SMOOTH -> smooth(f);
        };
    }

    private static float smooth(float f) {
        return f * f * (3 - 2 * f);
    }

    private static float floorMod(float t, int len) {
        float m = t % len;
        return m < 0 ? m + len : m;
    }

    /** Cues whose moment falls in (from, to] of one pass (looping wraps). */
    public static List<Cue> cuesBetween(Anim a, float from, float to) {
        List<Cue> out = new ArrayList<>();
        if (a.cues.isEmpty() || to <= from) return out;
        if (!a.loop) {
            for (Cue c : a.cues) {
                if ((c.tick > from || (c.tick == 0 && from < 0)) && c.tick <= to) out.add(c);
            }
            return out;
        }
        for (float pass = (float) Math.floor(Math.max(from, 0) / a.length) * a.length; pass <= to; pass += a.length) {
            for (Cue c : a.cues) {
                float at = pass + c.tick;
                if ((at > from || (at == 0 && from < 0)) && at <= to) out.add(c);
            }
        }
        return out;
    }

    // ---- Built-in animations ------------------------------------------------------------------

    /** Animations every god knows (used by vessel_work and as examples); the god may replay them by name. */
    public static final Map<String, String> PRESETS = presets();

    private static Map<String, String> presets() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("deep_bow", """
                once
                t=0 torso=0 head=0,0 rarm=10,8 larm=10,8
                t=0.8 torso=55 head=0,30 rarm=95,-25,40 larm=20,10 cape=10
                t=2.2 torso=58 head=0,32 rarm=95,-25,40 larm=20,10 cape=8
                t=3 torso=0 head=0,0 rarm=10,8 larm=10,8 cape=20
                """);
        m.put("dance", """
                loop
                t=0 body=0,0,0 torso=0 larm=40,80 rarm=40,80 lleg=0,4 rleg=0,4 rise=0 head=0,-5
                t=0.5 body=0,45,8 torso=-6 larm=160,40 rarm=20,60 lleg=35,8 rleg=0,4 rise=0.05 head=10,-10
                t=1 body=0,0,0 torso=0 larm=40,80 rarm=40,80 lleg=0,4 rleg=0,4 rise=0 head=0,-5
                t=1.5 body=0,-45,-8 torso=-6 larm=20,60 rarm=160,40 lleg=0,4 rleg=35,8 rise=0.05 head=-10,-10
                t=2 body=0,0,0 torso=0 larm=40,80 rarm=40,80 lleg=0,4 rleg=0,4 rise=0 head=0,-5
                t=0.5 fx=note at=above n=3
                t=1.5 fx=note at=above n=3
                """);
        m.put("dig", """
                loop
                t=0 torso=20 head=0,25 rarm=150,-10 larm=150,-10 lleg=10,6 rleg=-10,6
                t=0.4 torso=45 head=0,35 rarm=40,-15 larm=40,-15 lleg=10,6 rleg=-10,6 ease=snap
                t=0.9 torso=20 head=0,25 rarm=150,-10 larm=150,-10 lleg=10,6 rleg=-10,6
                t=0.45 fx=poof at=feet n=6 sound=block.gravel.break pitch=0.9
                """);
        m.put("place", """
                once
                t=0 rarm=20,5 torso=0 head=0,10
                t=0.35 rarm=85,-5 torso=15 head=0,30
                t=0.8 rarm=30,5 torso=4 head=0,15
                t=0.35 sound=block.stone.place pitch=1.1
                """);
        m.put("plant", """
                once
                t=0 torso=0 rise=0 lleg=0,4 rleg=0,4 rarm=10,8
                t=0.6 torso=40 rise=-0.18 lleg=70,6 rleg=-20,4 rarm=70,5 larm=30,15 head=0,40
                t=1.6 torso=40 rise=-0.18 lleg=70,6 rleg=-20,4 rarm=80,0 larm=30,15 head=0,40
                t=2.3 torso=0 rise=0 lleg=0,4 rleg=0,4 rarm=10,8 larm=10,8 head=0,5
                t=1 fx=happy_villager at=rhand n=8 sound=item.crop.plant
                """);
        m.put("cast_line", """
                once
                t=0 rarm=20,5 torso=0 head=0,0
                t=0.5 rarm=175,-5 larm=40,10 torso=-12 head=0,-15
                t=0.8 rarm=70,-5 larm=40,10 torso=12 head=0,20 ease=snap
                t=3 rarm=65,-5 larm=55,-10 torso=6 head=0,25
                t=0.8 sound=entity.fishing_bobber.throw
                """);
        m.put("bless_sweep", """
                once
                t=0 rarm=40,10 larm=40,10 head=0,0 torso=0
                t=1 rarm=150,60 larm=150,60 head=0,-25 torso=-8 cape=60
                t=2.2 rarm=80,-35,20 larm=80,90 head=-25,10 torso=4 body=0,-30,0 cape=40
                t=3.4 rarm=80,90 larm=80,-35,20 head=25,10 torso=4 body=0,30,0 cape=40
                t=4.2 rarm=20,10 larm=20,10 head=0,0 torso=0 body=0,0,0 cape=20
                t=1 fx=end_rod at=hands n=12 sound=block.amethyst_block.chime pitch=1.4
                t=2.2 fx=totem_of_undying at=rhand n=16
                t=3.4 fx=totem_of_undying at=lhand n=16 sound=block.beacon.power_select pitch=1.6
                """);
        m.put("sit", """
                loop
                t=0 rise=-0.36 lleg=88,8 rleg=80,6 torso=-4 larm=25,25 rarm=25,25 head=0,5 cape=0
                t=2 rise=-0.36 lleg=96,8 rleg=72,6 torso=-2 larm=25,25 rarm=25,25 head=12,0 cape=4
                t=4 rise=-0.36 lleg=88,8 rleg=80,6 torso=-4 larm=25,25 rarm=25,25 head=0,5 cape=0
                """);
        m.put("juggle", """
                loop
                t=0 rarm=70,-10 larm=70,-10 head=0,-20
                t=0.3 rarm=110,-15 larm=60,-10 head=-8,-30
                t=0.6 rarm=70,-10 larm=110,-15 head=8,-30
                t=0.9 rarm=70,-10 larm=70,-10 head=0,-20
                t=0.3 fx=flame at=rhand n=4
                t=0.6 fx=soul_fire_flame at=lhand n=4
                t=0.15 fx=end_rod at=above n=2
                """);
        return java.util.Collections.unmodifiableMap(m);
    }

    /** A built-in animation by name, or null. */
    public static Anim preset(String name) {
        String text = PRESETS.get(clean(name));
        return text == null ? null : fromText(clean(name), text);
    }
}
