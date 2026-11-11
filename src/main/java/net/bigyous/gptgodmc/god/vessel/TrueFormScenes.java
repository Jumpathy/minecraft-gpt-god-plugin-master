package net.bigyous.gptgodmc.god.vessel;

import com.google.gson.JsonArray;

import net.bigyous.gptgodmc.god.FxScenes;
import net.bigyous.gptgodmc.god.vessel.TrueForm.Form;

/**
 * What players with the client mod see on top of a true form, written in the god's own effect language
 * ({@link FxScenes}): per form a looping aura (bloom so its light spills, god rays, haloes and beams of
 * solid light, a tinted sky, shader passes that make the air wrong round it), one scene for the moment
 * its eyes open (the world bends in a ring, the ground seems to rise, bolts fork down), and one for a
 * mortal struck by its gaze. Pure; offsets are from the form's centre, which hangs {@code form.rise}
 * above the ground.
 */
public final class TrueFormScenes {
    /** How long one turn of an aura lasts before it loops, in ticks. */
    public static final int LOOP = 100;

    private TrueFormScenes() {
    }

    /** The looping aura of a form. */
    public static JsonArray aura(Form form) {
        String ground = "[0," + (-form.rise) + ",0]";
        String scene = switch (form) {
            case OPHANIM -> """
                    [{"type":"post","kind":"bloom","intensity":0.7,"duration":100},
                     {"type":"post","kind":"rays","intensity":0.55,"duration":100,"color":"#FFE9A8"},
                     {"type":"sky","color":"#3A1A04","strength":0.6,"duration":100},
                     {"type":"halo","radius":11,"height":0.5,"count":24,"duration":100,"color":["#FFF6DC","#F2CF6A"]},
                     {"type":"particles","shape":"ring","motion":"orbit","sprite":"ember","radius":10,"rate":10,
                      "speed":0.25,"life":[20,40],"size":[0.5,0.1],"color":["#FFE08A","#FF7A20"],"glow":1,"duration":100},
                     {"type":"particles","shape":"sphere","motion":"inward","sprite":"mote","radius":9,"rate":8,
                      "speed":0.12,"life":[30,50],"size":[0.35,0.05],"color":["#FFF6DC","#F2CF6A"],"glow":1,"duration":100},
                     {"type":"particles","shape":"sphere","motion":"orbit","sprite":"rune","radius":8,"rate":2,
                      "speed":0.08,"life":[40,60],"size":[0.6,0.3],"color":"#FFE08A","glow":1,"duration":100}]
                    """;
            case SERAPHIM -> """
                    [{"type":"post","kind":"bloom","intensity":0.85,"duration":100},
                     {"type":"post","kind":"rays","intensity":0.7,"duration":100,"color":"#FFF6DC","offset":[0,3,0]},
                     {"type":"sky","color":"#40280A","strength":0.6,"duration":100},
                     {"type":"halo","radius":6,"height":0.3,"count":16,"duration":100,"offset":[0,3.3,0],
                      "color":["#FFFFFF","#F2CF6A"]},
                     {"type":"particles","shape":"wings","motion":"fall","sprite":"ember","radius":11,"rate":14,
                      "speed":0.05,"gravity":0.01,"life":[30,60],"size":[0.4,0.05],"color":["#FFFFFF","#FF9A30"],
                      "glow":1,"duration":100},
                     {"type":"particles","shape":"column","motion":"up","sprite":"ray","radius":0.6,"height":9,
                      "rate":4,"speed":0.2,"life":[15,25],"size":[1.2,0.2],"color":["#FFFFFF","#FFE9B0"],"glow":1,
                      "duration":100,"offset":[0,-5,0]}]
                    """;
            case EYE -> """
                    [{"type":"post","kind":"desaturate","intensity":0.65,"duration":100},
                     {"type":"post","kind":"aberration","intensity":0.3,"duration":100},
                     {"type":"post","kind":"grain","intensity":0.3,"duration":100},
                     {"type":"post","kind":"tint","intensity":0.2,"duration":100,"color":"#3A1A60"},
                     {"type":"post","kind":"bloom","intensity":0.6,"duration":100},
                     {"type":"sky","color":"#08020F","strength":0.85,"duration":100},
                     {"type":"tendrils","radius":7,"height":14,"count":9,"duration":100,"offset":%s,
                      "color":["#F4F0FF","#5A2A9A"]},
                     {"type":"particles","shape":"sphere","motion":"inward","sprite":"star","radius":14,"rate":8,
                      "speed":0.1,"life":[40,70],"size":[0.3,0.02],"color":["#F4F0FF","#B070FF"],"glow":1,"duration":100},
                     {"type":"particles","shape":"ring","motion":"orbit","sprite":"shard","radius":8,"rate":3,
                      "speed":0.05,"life":[50,80],"size":[0.5,0.2],"color":["#000000","#2A1040"],"glow":0,"duration":100}]
                    """.formatted(ground);
            case PILLAR -> """
                    [{"type":"post","kind":"bloom","intensity":0.8,"duration":100},
                     {"type":"post","kind":"rays","intensity":0.75,"duration":100,"color":"#FFF6DC","offset":[0,13.5,0]},
                     {"type":"sky","color":"#40280A","strength":0.55,"duration":100},
                     {"type":"beam","height":90,"width":3.2,"duration":100,"offset":%s,"color":["#FFFFFF","#FFE9B0"]},
                     {"type":"halo","radius":8,"height":0.5,"count":24,"duration":100,"offset":[0,13.5,0],
                      "color":["#FFFFFF","#F2CF6A"]},
                     {"type":"particles","shape":"helix","motion":"up","sprite":"rune","radius":4,"height":30,"turns":3,
                      "rate":6,"speed":0.15,"life":[40,70],"size":[0.6,0.2],"color":["#FFF6DC","#F2CF6A"],"glow":1,
                      "duration":100,"offset":%s},
                     {"type":"particles","shape":"disc","motion":"up","sprite":"ember","radius":6,"rate":8,"speed":0.1,
                      "life":[30,50],"size":[0.4,0.05],"color":["#FFE08A","#FF7A20"],"glow":1,"duration":100,"offset":%s}]
                    """.formatted(ground, ground, ground);
            default -> """
                    [{"type":"post","kind":"aberration","intensity":0.3,"duration":100},
                     {"type":"post","kind":"bloom","intensity":0.7,"duration":100},
                     {"type":"sky","color":"#12061E","strength":0.7,"duration":100},
                     {"type":"rift","height":9,"width":2.5,"duration":100,"color":["#FFFFFF","#9150FF"]},
                     {"type":"particles","shape":"sphere","motion":"orbit","sprite":"star","radius":8,"rate":8,
                      "speed":0.12,"life":[30,60],"size":[0.3,0.05],"color":["#EFE4FF","#9150FF"],"glow":1,"duration":100}]
                    """;
        };
        return parse(scene);
    }

    /** The moment its eyes open. */
    public static JsonArray opening(Form form) {
        String ground = "[0," + (-form.rise) + ",0]";
        boolean cold = form == Form.EYE || form == Form.HYPERCUBE;
        String core = cold ? "#F4F0FF" : "#FFF6DC", edge = cold ? "#B070FF" : "#F2CF6A";
        String wrong = form == Form.EYE
                ? "{\"type\":\"post\",\"kind\":\"invert\",\"intensity\":1,\"duration\":4},"
                        + "{\"type\":\"post\",\"kind\":\"blur\",\"intensity\":0.6,\"delay\":4,\"duration\":16},"
                : "{\"type\":\"post\",\"kind\":\"contrast\",\"intensity\":0.6,\"duration\":30},";
        String scene = """
                [{"type":"post","kind":"shock","intensity":1,"duration":34},
                 %s
                 {"type":"post","kind":"aberration","intensity":0.8,"duration":24},
                 {"type":"shockwave","radius":44,"width":2.5,"duration":34,"offset":%s,"color":["%s","%s"]},
                 {"type":"lightning","height":%s,"radius":16,"count":6,"width":0.5,"duration":14,"offset":%s,
                  "color":["#FFFFFF","%s"]},
                 {"type":"illusion","mode":"rise","count":28,"radius":18,"height":5,"delay":4,"duration":60,"offset":%s},
                 {"type":"debris","count":40,"radius":14,"speed":0.5,"up":0.6,"spin":1,"delay":2,"duration":50,"offset":%s},
                 {"type":"particles","shape":"burst","motion":"outward","sprite":"ray","count":260,"radius":2,
                  "speed":1.1,"drag":0.06,"life":[14,30],"size":[1.4,0.1],"color":["%s","%s"],"glow":1}]
                """.formatted(wrong, ground, core, edge, form.rise, ground, edge, ground, ground, core, edge);
        return parse(scene);
    }

    /** What a mortal struck by the gaze sees, on themselves. */
    public static JsonArray struck() {
        return parse("""
                [{"type":"post","kind":"shock","intensity":0.9,"duration":16},
                 {"type":"post","kind":"aberration","intensity":1,"duration":30},
                 {"type":"post","kind":"blur","intensity":0.7,"duration":40},
                 {"type":"post","kind":"desaturate","intensity":0.9,"duration":120},
                 {"type":"particles","shape":"burst","motion":"outward","sprite":"shard","count":80,"radius":0.5,
                  "speed":0.7,"drag":0.1,"life":[10,24],"size":[0.5,0.05],"color":["#FFFFFF","#8A0010"],"glow":1}]
                """);
    }

    /**
     * A mortal seized: a tendril of solid light from the form to them, their own storm, and after
     * {@code ticks} the burst that unmakes them. The scene sits where they hang; {@code dx, dy, dz} lead
     * from there to the form's heart.
     */
    public static JsonArray seized(double dx, double dy, double dz, int ticks, boolean cold) {
        String core = cold ? "#F4F0FF" : "#FFFFFF", edge = cold ? "#B070FF" : "#F2CF6A";
        String to = String.format(java.util.Locale.ROOT, "[%.1f,%.1f,%.1f]", dx, dy, dz);
        return parse("""
                [{"type":"beam","to":%1$s,"width":0.6,"duration":%2$d,"color":["%3$s","%4$s"]},
                 {"type":"tendrils","radius":2.5,"height":5,"count":5,"duration":%2$d,"offset":[0,-3,0],
                  "color":["%3$s","%4$s"]},
                 {"type":"particles","shape":"vortex","motion":"swirl","sprite":"spark","radius":3,"height":6,
                  "rate":22,"speed":0.3,"life":[14,26],"size":[0.4,0.05],"color":["%3$s","%4$s"],"glow":1,
                  "duration":%2$d,"offset":[0,-3,0]},
                 {"type":"particles","shape":"disc","motion":"orbit","sprite":"smoke","radius":4,"rate":8,"speed":0.2,
                  "life":[30,50],"size":[1.6,2.4],"color":["#3A3A48","#14141C"],"glow":0,"duration":%2$d,
                  "offset":[0,4,0]},
                 {"type":"lightning","height":7,"radius":3,"count":2,"width":0.25,"delay":40,"duration":8,
                  "offset":[0,-3,0],"color":["#FFFFFF","%4$s"]},
                 {"type":"lightning","height":7,"radius":3,"count":3,"width":0.25,"delay":80,"duration":8,
                  "offset":[0,-3,0],"color":["#FFFFFF","%4$s"]},
                 {"type":"post","kind":"rays","intensity":0.5,"duration":%2$d,"color":"%3$s"},
                 {"type":"post","kind":"shock","intensity":1,"delay":%2$d,"duration":24},
                 {"type":"shockwave","radius":18,"width":1.5,"delay":%2$d,"duration":18,"color":["%3$s","%4$s"]},
                 {"type":"particles","shape":"burst","motion":"outward","sprite":"shard","count":420,"radius":0.6,
                  "speed":1.2,"drag":0.07,"life":[14,34],"size":[0.6,0.05],"color":["%3$s","%4$s"],"glow":1,
                  "delay":%2$d},
                 {"type":"post","kind":"bloom","intensity":1,"delay":%2$d,"duration":20}]
                """.formatted(to, ticks, core, edge));
    }

    private static JsonArray parse(String scene) {
        try {
            return FxScenes.check(scene);
        } catch (FxScenes.Invalid e) {
            throw new IllegalStateException("true form scene: " + e.getMessage(), e);
        }
    }
}
