package net.bigyous.gptgodmc.god.vessel;

import java.util.Locale;

import com.google.gson.JsonArray;

import net.bigyous.gptgodmc.god.FxScenes;
import net.bigyous.gptgodmc.god.vessel.Rift.Palette;
import net.bigyous.gptgodmc.god.vessel.Rift.Timeline;

/**
 * What players with the client mod see when a god tears its way into the world, or leaves through a rift,
 * in the god's effect language ({@link FxScenes}), on a {@link Timeline}: a seam of light, the mod's own
 * rift mesh tearing open (standing still, turned by "yaw" to face the way the god walks out), light
 * streaming from it; arriving, at the climax a burst, a ring over the ground and the
 * world bending for a moment; leaving, light drawn into it as the body is swallowed; and when it shuts a
 * last pop of light. No darkened sky or screen: they should see it clearly. Pure; offsets are from the
 * rift's base on the ground.
 */
public final class RiftScenes {
    private RiftScenes() {
    }

    /** The mod's rift is, on average, this many times its "width" across. */
    static final double MOD_WIDTH = 1.9;

    /**
     * @param body how tall the god's body is, in blocks
     * @param yaw  which way it walks out (Minecraft degrees): the rift faces that way
     */
    public static JsonArray arrival(Palette p, double body, float yaw) {
        return scene(p, body, yaw, Rift.ARRIVAL, true);
    }

    /** The rift a god walks back into, on a departure timeline. */
    public static JsonArray departure(Palette p, double body, float yaw, Timeline when) {
        return scene(p, body, yaw, when, false);
    }

    static JsonArray scene(Palette p, double body, float yaw, Timeline when, boolean arriving) {
        double h = Rift.height(body), bottom = Rift.bottom(body), mid = Rift.centre(body);
        double width = Rift.width(body) / MOD_WIDTH;
        String core = Rift.hex(p.rim), glow = Rift.hex(p.glow), band = Rift.hex(p.band);
        String at = "[0," + n(bottom) + ",0]", heart = "[0," + n(mid) + ",0]";
        int lead = 4;
        StringBuilder s = new StringBuilder("[");
        // The seam, then the rift itself.
        s.append("""
                {"type":"beam","height":%s,"width":%s,"duration":%d,"offset":%s,"color":["#FFFFFF","%s"]},
                {"type":"rift","height":%s,"width":%s,"delay":%d,"duration":%d,"spin":0,"yaw":%s,
                 "offset":%s,"color":["%s","%s"]},
                """.formatted(n(h), n(Math.max(0.08, body * 0.04)), when.crack() + 8, at, glow,
                n(h), n(width), lead, when.shut() - lead, n(yaw), at, core, band));
        // Sparks drawn into it while it gathers, light streaming from it, a soft bloom throughout.
        s.append("""
                {"type":"particles","shape":"sphere","motion":"inward","radius":%s,"rate":18,"duration":%d,
                 "speed":%s,"sprite":"spark","color":["%s","%s"],"size":[0.22,0.04],"life":[14,24],"offset":%s},
                {"type":"post","kind":"bloom","intensity":0.65,"duration":%d},
                {"type":"post","kind":"rays","intensity":0.4,"delay":10,"duration":%d,"offset":%s,"color":"%s"},
                {"type":"sound","key":"minecraft:block.glass.break","delay":%d,"volume":1,"pitch":0.5},
                """.formatted(n(Math.max(4, h * 1.3)), when.climax(), n(Math.max(0.2, h * 0.06)), core, glow, heart,
                when.length(), Math.max(20, when.seal() - 2), heart, glow, when.crack()));
        if (arriving) {
            // The body forms: a burst, a ring over the ground, the world bending for a moment.
            s.append("""
                    {"type":"post","kind":"aberration","intensity":0.35,"delay":%1$d,"duration":16},
                    {"type":"post","kind":"shock","intensity":0.6,"delay":%1$d,"duration":24,"offset":%2$s},
                    {"type":"particles","shape":"burst","count":240,"speed":%3$s,"spread":0.5,"life":[16,34],
                     "sprite":"star","color":["%4$s","%5$s"],"size":[0.3,0.05],"drag":0.08,"delay":%1$d,"offset":%2$s},
                    {"type":"shockwave","radius":%6$s,"width":0.8,"duration":20,"delay":%1$d,"offset":[0,0.1,0],
                     "color":["%4$s","%7$s"]},
                    {"type":"screen","kind":"flash","intensity":0.2,"duration":6,"delay":%1$d,"color":"%4$s"},
                    {"type":"sound","key":"minecraft:block.end_portal.spawn","delay":%1$d,"volume":0.6,"pitch":0.9},
                    """.formatted(when.climax(), heart, n(Math.max(0.35, body * 0.15)), core, glow,
                    n(Math.max(6, body * 3)), band));
        } else {
            // The body is swallowed: light and motes rush into the far side after it.
            s.append("""
                    {"type":"particles","shape":"sphere","motion":"inward","count":220,"radius":%3$s,"speed":%4$s,
                     "sprite":"star","color":["%5$s","%6$s"],"size":[0.28,0.04],"life":[10,16],"delay":%1$d,"offset":%2$s},
                    {"type":"post","kind":"shock","intensity":0.45,"delay":%1$d,"duration":18,"offset":%2$s},
                    {"type":"post","kind":"aberration","intensity":0.3,"delay":%1$d,"duration":12},
                    {"type":"sound","key":"minecraft:entity.enderman.teleport","delay":%1$d,"volume":1,"pitch":0.5},
                    """.formatted(when.climax(), heart, n(Math.max(2.5, body * 1.2)), n(Math.max(0.2, body * 0.1)), core,
                    glow));
        }
        // It shuts with a last pop of light.
        s.append("""
                {"type":"particles","shape":"burst","count":90,"speed":%1$s,"spread":0.4,"life":[10,22],
                 "sprite":"spark","color":["#FFFFFF","%2$s"],"size":[0.25,0.04],"drag":0.1,"delay":%3$d,"offset":%4$s},
                {"type":"shockwave","radius":%5$s,"width":0.5,"duration":14,"delay":%3$d,"offset":[0,0.1,0],
                 "color":["%6$s","%7$s"]},
                {"type":"sound","key":"minecraft:block.beacon.deactivate","delay":%3$d,"volume":1,"pitch":1.3},
                {"type":"sound","key":"minecraft:block.amethyst_block.chime","delay":%3$d,"volume":1,"pitch":0.8}]
                """.formatted(n(Math.max(0.25, body * 0.1)), glow, when.shut(), heart, n(Math.max(3, body * 1.4)), core, band));
        try {
            return FxScenes.check(s.toString());
        } catch (FxScenes.Invalid e) {
            throw new IllegalStateException("rift scene: " + e.getMessage(), e);
        }
    }

    private static String n(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
