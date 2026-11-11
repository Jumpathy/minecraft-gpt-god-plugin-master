package net.bigyous.gptgodmc.god.art;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.bigyous.gptgodmc.god.art.Relics.Relic;

/**
 * What a relic is imbued with, as the god writes it: one power a line,
 * "<when>: <what> [| cooldown <seconds>] [| when on|off] [| chance <percent>]". A power is a property
 * (stat), something it does while held, or something that happens when its bearer strikes, swings,
 * uses it, kills or is hurt: plain effects (ignite, a potion, a heal), movement (dash, blink, leap) and
 * whole attacks with their own choreography (a sweeping slash, a wave or beam of light, a ground slam, a
 * whirl, a throw that returns). The god composes from this library; how each looks and behaves is made
 * once, with care, in {@link RelicImbue}.
 *
 * <p>Every number is clamped, and every power has a cost in "power"; a relic's powers together may not
 * exceed the budget operators set (creations.power-budget), so a god can make a relic remarkable but not
 * absurd. Pure: this is the reading, the checking and the reckoning.
 */
public final class RelicPowers {
    private RelicPowers() {
    }

    public enum Trigger {
        STAT("stat"), HELD("held"), HIT("on_hit"), SWING("on_swing"), USE("on_use"), KILL("on_kill"), HURT("on_hurt");

        public final String id;

        Trigger(String id) {
            this.id = id;
        }

        static Trigger of(String s) {
            for (Trigger t : values()) {
                if (t.id.equals(s) || t.id.replace("on_", "").equals(s)) return t;
            }
            return switch (s) {
                case "while_held", "hold", "passive" -> HELD;
                case "use", "right_click", "on_click" -> USE;
                case "stats" -> STAT;
                default -> null;
            };
        }
    }

    private static final Set<Trigger> ACTS = EnumSet.of(Trigger.HIT, Trigger.SWING, Trigger.USE, Trigger.KILL, Trigger.HURT);
    private static final Set<Trigger> ANY = EnumSet.of(Trigger.HELD, Trigger.HIT, Trigger.SWING, Trigger.USE, Trigger.KILL,
            Trigger.HURT);

    /** Everything a relic can do: where it may be used, the range of its strength, and what a point of it costs. */
    public enum Effect {
        DAMAGE(EnumSet.of(Trigger.HIT), 1, 12, 0.5, 0), IGNITE(EnumSet.of(Trigger.HIT, Trigger.HURT), 1, 10, 0.25, 0),
        FREEZE(EnumSet.of(Trigger.HIT, Trigger.HURT), 1, 10, 0.25, 0), POTION(ANY, 1, 3, 0, 0),
        HEAL(EnumSet.of(Trigger.HIT, Trigger.USE, Trigger.KILL, Trigger.HURT), 1, 8, 0.5, 0),
        KNOCKBACK(EnumSet.of(Trigger.HIT, Trigger.HURT), 1, 4, 0.4, 0), LAUNCH(EnumSet.of(Trigger.HIT), 1, 3, 0.6, 0),
        PULL(EnumSet.of(Trigger.USE), 4, 20, 0, 1), LIGHTNING(EnumSet.of(Trigger.HIT, Trigger.USE, Trigger.KILL), 0, 10, 0.4, 1),
        DASH(EnumSet.of(Trigger.USE), 2, 12, 0.2, 0), BLINK(EnumSet.of(Trigger.USE), 2, 20, 0.2, 0),
        LEAP(EnumSet.of(Trigger.USE), 1, 3, 0.5, 0), BOLT(EnumSet.of(Trigger.USE), 0, 0, 0, 2),
        BURST(EnumSet.of(Trigger.HIT, Trigger.USE, Trigger.KILL), 1, 10, 0.6, 0),
        SHIELD(EnumSet.of(Trigger.USE, Trigger.KILL, Trigger.HURT), 2, 12, 0.3, 0),
        SLASH(EnumSet.of(Trigger.SWING, Trigger.USE), 0, 8, 0.6, 0.5), WAVE(EnumSet.of(Trigger.USE), 1, 10, 0.5, 0.5),
        BEAM(EnumSet.of(Trigger.USE), 1, 10, 0.5, 0.5), SLAM(EnumSet.of(Trigger.USE), 1, 10, 0.6, 1),
        SPIN(EnumSet.of(Trigger.USE), 1, 10, 0.6, 0.5), THROW(EnumSet.of(Trigger.USE), 1, 10, 0.5, 0.5),
        LIGHT(EnumSet.of(Trigger.HELD), 1, 15, 0, 0.5), AURA(EnumSet.of(Trigger.HELD), 0, 0, 0, 0),
        TRAIL(EnumSet.of(Trigger.SWING), 0, 0, 0, 0), SOUND(ANY, 0, 0, 0, 0), FX(ACTS, 0, 0, 0, 0.5),
        TOGGLE(EnumSet.of(Trigger.USE), 0, 0, 0, 0), SCRIPT(ACTS, 0, 0, 0, 4);

        final Set<Trigger> where;
        final double min, max, each, flat;

        Effect(Set<Trigger> where, double min, double max, double each, double flat) {
            this.where = where;
            this.min = min;
            this.max = max;
            this.each = each;
            this.flat = flat;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Effect of(String s) {
            for (Effect e : values()) {
                if (e.id().equals(s)) return e;
            }
            return switch (s) {
                case "fire", "burn" -> IGNITE;
                case "smite", "bolt_of_lightning" -> LIGHTNING;
                case "teleport" -> BLINK;
                case "jump" -> LEAP;
                case "shoot", "projectile" -> BOLT;
                case "explode", "blast" -> BURST;
                case "absorb", "ward" -> SHIELD;
                case "sweep" -> SLASH;
                case "whirl" -> SPIN;
                case "glow" -> LIGHT;
                case "particles" -> AURA;
                case "wake", "awaken", "kindle" -> TOGGLE;
                case "effect", "scene" -> FX;
                case "program", "run" -> SCRIPT;
                default -> null;
            };
        }
    }

    /** The properties a relic may lend its bearer: the lowest and highest value, and the cost of each point beyond `free`. */
    public enum Stat {
        ATTACK_DAMAGE(1, 14, 6, 0.8), ATTACK_SPEED(0.5, 3, 1.6, 2), SPEED(-30, 40, 0, 0.1), ARMOR(0, 10, 0, 0.5),
        TOUGHNESS(0, 6, 0, 0.5), MAX_HEALTH(-6, 12, 0, 0.4), REACH(0, 3, 0, 2), KNOCKBACK_RESIST(0, 100, 0, 0.04),
        LUCK(0, 5, 0, 0.5);

        final double min, max, free, each;

        Stat(double min, double max, double free, double each) {
            this.min = min;
            this.max = max;
            this.free = free;
            this.each = each;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Stat of(String s) {
            for (Stat st : values()) {
                if (st.id().equals(s)) return st;
            }
            return switch (s) {
                case "damage" -> ATTACK_DAMAGE;
                case "swiftness", "movement_speed" -> SPEED;
                case "health" -> MAX_HEALTH;
                case "range" -> REACH;
                case "knockback_resistance" -> KNOCKBACK_RESIST;
                default -> null;
            };
        }
    }

    /** The kinds of bolt a relic can loose. */
    public static final List<String> BOLTS = List.of("fireball", "arrow", "snowball", "wind");

    /**
     * One power, read and checked.
     *
     * @param amount   its strength (a stat's value; a potion's level)
     * @param seconds  how long a potion lasts
     * @param word     a potion's effect, a bolt's kind, a sound's key, an aura's particle, an effect's name, a
     *                 potion's target (in `aim`)
     * @param pitch    a sound's pitch
     * @param color    RGB for the attacks and trails that take one, or -1
     * @param cooldown seconds between uses (0 = none)
     * @param when     "on", "off" or "" (either form)
     * @param chance   percent
     * @param data     a script's arguments, as the JSON object text the god wrote ("" when none)
     */
    public record Power(Trigger trigger, Effect effect, Stat stat, double amount, int seconds, String word, String aim,
            double pitch, int color, int cooldown, String when, int chance, double cost, String text, String data) {
    }

    /** The least rest between two runs of a relic's script, in seconds, whatever sets it off. */
    public static final int SCRIPT_REST = 3;
    /** The longest arguments a relic's script may be given, in characters. */
    public static final int MAX_DATA = 1000;

    /** What is wrong with a power, said to the god that wrote it. */
    public static final class Flaw extends Exception {
        private static final long serialVersionUID = 1L;

        public Flaw(String message) {
            super(message);
        }
    }

    /** The whole language in a paragraph, for the tools that take powers. */
    public static final String HELP = "Each power is one line: \"<when>: <what> [| cooldown <seconds>] [| when on|off] "
            + "[| chance <percent>]\". <when> is one of: stat, held, on_hit, on_swing, on_use (right-click), on_kill, "
            + "on_hurt. After stat: attack_damage N, attack_speed N, speed N (percent), armor N, toughness N, "
            + "max_health N, reach N, knockback_resist N (percent), luck N. Otherwise <what> is one of (N = strength): "
            + "damage N; ignite SECONDS; freeze SECONDS; potion <effect> <level 1-3> <seconds> [self|foe|near]; heal N; "
            + "knockback N; launch N; pull RANGE; lightning N; dash BLOCKS; blink BLOCKS; leap N; "
            + "bolt fireball|arrow|snowball|wind; burst N; shield N; and the attacks with their own look, which take a "
            + "color: slash N (a sweeping arc), wave N (a crescent of light that flies ahead), beam N, slam N (a ground "
            + "shockwave), spin N (a whirl about the bearer), throw N (the relic flies out and returns); and for show: "
            + "light LEVEL (the bearer carries light), aura <particle or #color>, trail #color (on_swing), "
            + "sound <key> [pitch], fx <one of your saved effects>; and toggle (on_use: wakes or sleeps a relic that has "
            + "two forms). \"when on\" makes a power work only while it is awake. A relic that toggles wakes on a "
            + "right-click; if it has other on_use powers while awake, a right-click then uses those and sneak + "
            + "right-click puts it back to sleep; its on_use sounds and fx play as it wakes or sleeps. And beyond all "
            + "these, script <one of your saved scripts> [{\"key\": value}] (after on_hit, on_swing, on_use, on_kill or "
            + "on_hurt) runs a program you saved with the script power whenever it happens, so a relic can do anything "
            + "you can program: its args are the ones it was saved with, then those written here, and also args.bearer "
            + "(the mortal holding it), args.target ({name, type, x, y, z, health} of what was struck, or null), "
            + "args.at and args.aim ({x,y,z} where the bearer stands and where they look), args.look (the direction they "
            + "look) and args.relic ({id, name, awake, trigger}). It runs in a moment (no on/every handlers), at most "
            + "every " + SCRIPT_REST + " s, on a smaller budget than your own scripts. All powers of a relic together must "
            + "fit its power budget. Example, a hilt that kindles a blade of light: \"on_use: toggle\", "
            + "\"on_use: sound block.beacon.activate 1.9\", \"held: light 12 | when on\", "
            + "\"held: sound block.beacon.ambient 1.8 | when on\", \"on_hit: damage 5 | when on\", "
            + "\"on_hit: ignite 3 | when on\", \"on_swing: slash 2 #7fe8ff | when on\".";

    // ---- Reading ----------------------------------------------------------------------------------------

    /** Reads one line. */
    public static Power parse(String line) throws Flaw {
        String text = line == null ? "" : line.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) throw new Flaw("An empty power.");
        // A script's arguments are a JSON object: taken out whole first, so its commas and bars are its own.
        String data = "", rest0 = text;
        int brace = text.indexOf('{');
        if (brace >= 0) {
            int end = closing(text, brace);
            if (end < 0) throw new Flaw("\"" + text + "\": the { of the script's arguments is never closed.");
            data = text.substring(brace, end + 1);
            rest0 = text.substring(0, brace) + " " + text.substring(end + 1);
        }
        String[] parts = rest0.split("\\|");
        int colon = parts[0].indexOf(':');
        if (colon < 1) throw new Flaw("\"" + text + "\": write a power as \"<when>: <what>\", e.g. \"on_hit: ignite 3\".");
        Trigger trigger = Trigger.of(parts[0].substring(0, colon).trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (trigger == null) {
            throw new Flaw("\"" + text + "\": it must begin with stat, held, on_hit, on_swing, on_use, on_kill or on_hurt.");
        }
        List<String> words = new ArrayList<>();
        int color = -1;
        for (String w : parts[0].substring(colon + 1).trim().split("[\\s,]+")) {
            if (w.isEmpty()) continue;
            if (w.startsWith("#")) color = color(text, w);
            else words.add(w.toLowerCase(Locale.ROOT));
        }
        if (words.isEmpty()) throw new Flaw("\"" + text + "\": say what it does.");
        int cooldown = -1, chance = 100;
        String when = "";
        for (int i = 1; i < parts.length; i++) {
            String[] opt = parts[i].trim().toLowerCase(Locale.ROOT).split("\\s+");
            if (opt.length == 2 && opt[0].equals("cooldown")) {
                cooldown = (int) Math.round(clamp(number(text, opt[1].replace("s", "")), 0, 600));
            } else if (opt.length == 2 && opt[0].equals("when") && (opt[1].equals("on") || opt[1].equals("off"))) {
                when = opt[1];
            } else if (opt.length == 2 && opt[0].equals("chance")) {
                chance = (int) Math.round(clamp(number(text, opt[1].replace("%", "")), 1, 100));
            } else {
                throw new Flaw("\"" + text + "\": after | write \"cooldown <seconds>\", \"when on\", \"when off\" or "
                        + "\"chance <percent>\".");
            }
        }
        if (trigger == Trigger.STAT) return stat(text, words, when);

        Effect effect = Effect.of(words.get(0));
        if (effect == null) throw new Flaw("\"" + text + "\": there is no power called " + words.get(0) + ". " + HELP);
        if (!data.isEmpty() && effect != Effect.SCRIPT) {
            throw new Flaw("\"" + text + "\": only script takes {arguments}.");
        }
        if (!effect.where.contains(trigger)) {
            List<String> ok = new ArrayList<>();
            effect.where.forEach(t -> ok.add(t.id));
            throw new Flaw("\"" + text + "\": " + effect.id() + " belongs after " + String.join(" or ", ok) + ".");
        }
        double amount = 0, pitch = 1;
        int seconds = 0;
        String word = "", aim = "";
        switch (effect) {
            case POTION -> {
                if (words.size() < 2) throw new Flaw("\"" + text + "\": potion <effect> <level> <seconds>.");
                word = words.get(1).replace("minecraft:", "");
                amount = Math.round(clamp(words.size() > 2 ? number(text, words.get(2)) : 1, 1, trigger == Trigger.HELD ? 2 : 3));
                seconds = (int) Math.round(clamp(words.size() > 3 ? number(text, words.get(3).replace("s", "")) : 8, 1, 60));
                aim = words.size() > 4 ? words.get(4) : trigger == Trigger.HIT ? "foe" : "self";
                if (!List.of("self", "foe", "near").contains(aim)) {
                    throw new Flaw("\"" + text + "\": a potion falls on self, foe or near.");
                }
                if (trigger == Trigger.HELD) aim = "self";
            }
            case BOLT -> {
                word = words.size() > 1 ? words.get(1) : "";
                if (!BOLTS.contains(word)) throw new Flaw("\"" + text + "\": bolt " + String.join("|", BOLTS) + ".");
            }
            case SOUND -> {
                if (words.size() < 2 || !words.get(1).matches("[a-z0-9_.:]{3,80}")) {
                    throw new Flaw("\"" + text + "\": sound <key> [pitch], e.g. sound block.beacon.activate 1.6.");
                }
                word = words.get(1).contains(":") ? words.get(1) : "minecraft:" + words.get(1);
                pitch = clamp(words.size() > 2 ? number(text, words.get(2)) : 1, 0.5, 2);
            }
            case AURA -> {
                word = words.size() > 1 ? words.get(1) : "";
                if (word.isEmpty() && color < 0) throw new Flaw("\"" + text + "\": aura <particle name or #color>.");
            }
            case TRAIL -> {
                if (color < 0) color = 0xFFFFFF;
            }
            case FX -> {
                if (words.size() < 2) throw new Flaw("\"" + text + "\": fx <the name of one of your saved effects>.");
                word = String.join(" ", words.subList(1, words.size()));
            }
            case TOGGLE -> {
            }
            case SCRIPT -> {
                if (words.size() != 2 || !words.get(1).matches("[a-z0-9_\\-]{1,32}")) {
                    throw new Flaw("\"" + text + "\": script <the name of one of your saved scripts> [{\"key\": value}].");
                }
                word = words.get(1);
                if (!data.isEmpty()) data = arguments(text, data);
            }
            default -> {
                if (words.size() < 2 && effect != Effect.LIGHTNING) {
                    throw new Flaw("\"" + text + "\": " + effect.id() + " needs a number (" + (int) effect.min + " to "
                            + (int) effect.max + ").");
                }
                amount = Math.round(clamp(words.size() > 1 ? number(text, words.get(1).replaceAll("[a-z%]+$", ""))
                        : effect == Effect.LIGHTNING ? 5 : effect.min, effect.min, effect.max));
            }
        }
        // How often it may happen: an act by hand rests a while, a swing a moment, being hurt a few seconds.
        int rest = switch (trigger) {
            case USE -> cooldown < 0 ? (effect == Effect.TOGGLE || effect == Effect.SOUND ? 1 : 6) : Math.max(1, cooldown);
            case SWING -> Math.max(1, cooldown);
            case HURT -> cooldown < 0 ? 4 : Math.max(2, cooldown);
            case HELD -> 0;
            default -> Math.max(0, cooldown);
        };
        if (effect == Effect.SCRIPT) rest = Math.max(SCRIPT_REST, rest);
        double cost = effect.flat + effect.each * amount;
        if (effect == Effect.POTION) cost = amount * (trigger == Trigger.HELD ? 2 : Math.min(seconds, 30) / 15.0)
                * (aim.equals("near") ? 1.5 : 1);
        if (trigger == Trigger.USE && cost > 0) cost *= clamp(6.0 / rest, 0.35, 3);
        if (trigger == Trigger.SWING && cost > 0) cost *= clamp(2.0 / rest, 0.5, 2);
        cost *= Math.max(0.2, chance / 100.0);
        return new Power(trigger, effect, null, amount, seconds, word, aim, pitch, color, rest, when, chance, cost, text,
                data);
    }

    /** Where the } that closes the { at `open` is, minding quoted strings, or -1. */
    private static int closing(String text, int open) {
        int depth = 0;
        boolean quoted = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '\\') i++;
                else if (c == '"') quoted = false;
            } else if (c == '"') {
                quoted = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** A script's arguments, checked: a JSON object, not too long; written back compactly. */
    private static String arguments(String text, String json) throws Flaw {
        try {
            com.google.gson.JsonElement e = com.google.gson.JsonParser.parseString(json);
            if (!e.isJsonObject()) throw new IllegalStateException();
            String compact = e.toString();
            if (compact.length() > MAX_DATA) {
                throw new Flaw("\"" + text + "\": the script's arguments are too long (at most " + MAX_DATA + " characters).");
            }
            return compact;
        } catch (RuntimeException e) {
            throw new Flaw("\"" + text + "\": the script's arguments must be a JSON object, like {\"radius\": 4}.");
        }
    }

    private static Power stat(String text, List<String> words, String when) throws Flaw {
        Stat stat = Stat.of(words.get(0));
        if (stat == null) {
            List<String> all = new ArrayList<>();
            for (Stat s : Stat.values()) all.add(s.id());
            throw new Flaw("\"" + text + "\": there is no such property. Properties: " + String.join(", ", all) + ".");
        }
        if (words.size() < 2) throw new Flaw("\"" + text + "\": give " + stat.id() + " a value.");
        double v = clamp(number(text, words.get(1).replace("%", "").replace("+", "")), stat.min, stat.max);
        double cost = Math.max(0, Math.abs(v - stat.free) * stat.each) * (v < stat.free ? 0 : 1);
        return new Power(Trigger.STAT, null, stat, v, 0, "", "", 1, -1, 0, when, 100, cost, text, "");
    }

    private static int color(String text, String word) throws Flaw {
        try {
            String hex = word.substring(1);
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            }
            if (hex.length() != 6) throw new NumberFormatException();
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new Flaw("\"" + text + "\": " + word + " is not a color like #7fe8ff.");
        }
    }

    private static double number(String text, String word) throws Flaw {
        try {
            return Double.parseDouble(word);
        } catch (NumberFormatException e) {
            throw new Flaw("\"" + text + "\": \"" + word + "\" should be a number.");
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** A relic's powers read and checked together. */
    public record Reading(List<Power> powers, double cost) {
        public boolean toggles() {
            return powers.stream().anyMatch(p -> p.effect() == Effect.TOGGLE);
        }

        /** True when any power cares which form the relic is in. */
        public boolean needsTwoForms() {
            return toggles() || powers.stream().anyMatch(p -> !p.when().isEmpty());
        }
    }

    /**
     * Reads every line and checks them as a whole: at most 12, within the budget, and nothing that
     * depends on a second form unless the relic has (or will have) one and a way to wake it.
     *
     * @param twoFormed whether the relic has a second form
     */
    public static Reading read(List<String> lines, boolean twoFormed, double budget) throws Flaw {
        List<Power> out = new ArrayList<>();
        double cost = 0;
        for (String line : lines == null ? List.<String>of() : lines) {
            if (line == null || line.isBlank()) continue;
            Power p = parse(line);
            out.add(p);
            cost += p.cost();
        }
        if (out.size() > 12) throw new Flaw("A relic holds at most 12 powers; this has " + out.size() + ".");
        Reading r = new Reading(List.copyOf(out), cost);
        if (!out.isEmpty() && budget <= 0) {
            throw new Flaw("The keeper of this world does not let relics be imbued (creations.power-budget is 0).");
        }
        if (cost > budget + 1e-9) {
            throw new Flaw(String.format(Locale.ROOT, "This is more power than one relic can hold: %.1f of %.0f. Weaken "
                    + "something, lengthen a cooldown, or leave a power out.", cost, budget));
        }
        if (r.needsTwoForms() && !twoFormed) {
            throw new Flaw("\"toggle\" and \"when on\" need a relic with two forms (forge it as special, with a waking "
                    + "form).");
        }
        if (r.needsTwoForms() && !r.toggles()) {
            throw new Flaw("Some powers say \"when on\" or \"when off\", but nothing wakes the relic: add \"on_use: toggle\".");
        }
        return r;
    }

    // ---- For the relic as it is kept ---------------------------------------------------------------------

    private static final Map<String, List<Power>> READ = new ConcurrentHashMap<>();

    /** A kept relic's powers (they were checked when it was imbued; a line that no longer reads is left out). */
    public static List<Power> of(Relic r) {
        return READ.computeIfAbsent(String.join("\n", r.powers()), k -> {
            List<Power> out = new ArrayList<>();
            for (String line : r.powers()) {
                try {
                    out.add(parse(line));
                } catch (Flaw e) {
                    // an old relic with a power this version does not know: it simply lacks it
                }
            }
            return List.copyOf(out);
        });
    }

    /** A few lines for the item's tooltip: what it does, in words a mortal can act on. */
    public static List<String> lore(List<String> powers) {
        List<Power> read = new ArrayList<>();
        for (String line : powers) {
            try {
                read.add(parse(line));
            } catch (Flaw e) {
                // a power this version does not know says nothing
            }
        }
        // Once awake, a relic that does something on a click is put back to sleep by sneaking.
        boolean actsAwake = read.stream().anyMatch(p -> p.trigger() == Trigger.USE && p.effect() != Effect.TOGGLE
                && p.effect() != Effect.SOUND && p.effect() != Effect.FX && !p.when().equals("off"));
        List<String> out = new ArrayList<>();
        for (Power p : read) {
            // Properties show in the tooltip by themselves; sounds and sparkle need no saying.
            if (p.trigger() == Trigger.STAT || p.effect() == Effect.SOUND || p.effect() == Effect.TRAIL
                    || p.effect() == Effect.AURA || p.effect() == Effect.FX) {
                continue;
            }
            if (out.size() == 6) {
                out.add("...");
                break;
            }
            if (p.effect() == Effect.TOGGLE) {
                out.add("\u25C6 Right-click: wake" + (actsAwake ? " (sneak + right-click: sleep)" : " or sleep"));
                continue;
            }
            String when = switch (p.trigger()) {
                case HELD -> "Held";
                case HIT -> "On hit";
                case SWING -> "On swing";
                case USE -> "Right-click";
                case KILL -> "On kill";
                default -> "When hurt";
            };
            // A script is told by its name ("frost nova"); its arguments are for the god, not the tooltip.
            String what = p.effect() == Effect.SCRIPT ? p.word() : p.text().substring(p.text().indexOf(':') + 1);
            if (what.indexOf('|') >= 0) what = what.substring(0, what.indexOf('|'));
            what = what.replaceAll("#[0-9a-fA-F]{3,6}", "").replace('_', ' ').trim().replaceAll("\\s+", " ");
            out.add("\u25C6 " + when + (p.when().isEmpty() ? "" : p.when().equals("on") ? ", awake" : ", asleep") + ": " + what
                    + (p.trigger() == Trigger.USE && p.cooldown() >= 2 ? " (" + p.cooldown() + "s)" : "")
                    + (p.chance() < 100 ? " (" + p.chance() + "%)" : ""));
        }
        return out;
    }
}
