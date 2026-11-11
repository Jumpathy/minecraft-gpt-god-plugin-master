package net.bigyous.gptgodmc.god.tools;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Sync;
import net.bigyous.gptgodmc.god.art.ArtSettings;
import net.bigyous.gptgodmc.god.art.Atelier;
import net.bigyous.gptgodmc.god.art.RelicImbue;
import net.bigyous.gptgodmc.god.art.RelicPowers;
import net.bigyous.gptgodmc.god.art.RelicShrines;
import net.bigyous.gptgodmc.god.art.Relics;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.art.RelicsModule;
import net.bigyous.gptgodmc.god.art.Sprite;
import net.bigyous.gptgodmc.god.art.SpritePainter;
import net.bigyous.gptgodmc.god.art.SpriteSheet;
import net.bigyous.gptgodmc.god.vessel.VesselLook;

/**
 * The god's forge: making a relic with its own painted look (slow and costly: a separate, stronger model
 * draws it), imbuing relics with properties and powers (free: the god composes them from
 * {@link RelicPowers}), handing relics to mortals, and setting them or spirits afloat in the world.
 * Appended last in {@link ToolRegistry}. When the keeper has not lit the forge (creations.enabled),
 * forge_relic says so.
 */
public final class RelicTools {
    private RelicTools() {
    }

    public static List<GodTool> all() {
        List<GodTool> tools = new ArrayList<>();
        tools.add(GodTool.async("forge_relic",
                "Make a relic of your own design: a thing with its own painted look that exists nowhere else, such as "
                        + "a blade, a staff, an amulet, a crown, a tome, or a small spirit to float in the world. An "
                        + "artist paints it from your words, which takes a minute or more and is costly, so do it "
                        + "rarely, for a moment or a mortal that deserves a thing made only for them; for ordinary "
                        + "gifts use give_item. Say what it is made of, its colors and its mood; the artist draws "
                        + "small, so one clear idea is better than many. A relic is a solid thing in the hand. Most "
                        + "are the painting given an even thickness; mark one special and the artist sculpts it "
                        + "(thicker guards, rounded rods, films of light), and a special relic may have a second, "
                        + "awakened form that its bearer wakes (a hilt whose blade of light kindles, a lantern that "
                        + "opens). You may imbue it as you forge it (powers), or later with imbue_relic. You are "
                        + "shown the finished relic. Afterwards give_relic hands it to a mortal and show_relic sets it "
                        + "in the world; a relic can be given many times.",
                Schema.object()
                        .str("name", "The relic's name, as mortals will read it (at most 32 characters).", true)
                        .choice("kind", "blade = a weapon (a real sword that never breaks); staff = a staff, wand, rod "
                                + "or tool; trinket = an amulet, ring, crown, mask, vial or idol; tome = a book, tablet "
                                + "or scroll; spirit = a small floating being for show_relic.", true,
                                List.copyOf(Atelier.KINDS.keySet()))
                        .str("look", "What it looks like, in one to three sentences: its shape, what it is made of, "
                                + "its colors, what in it shines. For a relic with two forms, say what it is asleep "
                                + "and what it becomes awake.", true)
                        .bool("special", "True for a relic that matters: the artist sculpts its form instead of "
                                + "leaving it flat. Needed for a second, awakened form. Default false.", false)
                        .strings("powers", "Optional: what it is imbued with, one power per line, written as "
                                + "imbue_relic describes. Powers that say \"toggle\" or \"when on\" make it a relic of "
                                + "two forms.", false, 12)
                        .str("give_to", "Optional: a mortal to hand it to as soon as it is made.", false),
                RelicTools::forge));
        tools.add(GodTool.sync("give_relic", () -> "Hand a mortal a relic you have forged (see forge_relic). " + known(),
                Schema.object()
                        .str("player", "The mortal who receives it.", true)
                        .str("relic", "The relic's name or id.", true),
                RelicTools::give));
        tools.add(GodTool.sync("show_relic",
                () -> "Set a relic or spirit you have forged afloat in the world, where it slowly turns and glows: "
                        + "enshrine = at a place, until dismissed; follow = at a mortal's shoulder for some minutes; "
                        + "dismiss = take every showing of it out of the world. " + known(),
                Schema.object()
                        .str("relic", "The relic's name or id.", true)
                        .choice("action", "What to do with it.", true, List.of("enshrine", "follow", "dismiss"))
                        .str("target", "For enshrine: where (" + Targets.TARGET_HELP + "). For follow: the mortal.", false)
                        .integer("minutes", "For follow: how long it stays (default 10).", false, 1, 120),
                RelicTools::show));
        tools.add(GodTool.sync("imbue_relic",
                "Imbue a relic you have forged: give it properties, things it does when its bearer strikes, swings, "
                        + "uses it, kills or is hurt, attacks with their own look, and what it does while merely "
                        + "held. This replaces everything it was imbued with before, costs nothing, and every copy "
                        + "mortals already hold changes with it. Send no powers to leave it a thing only to look at. "
                        + RelicPowers.HELP,
                Schema.object()
                        .str("relic", "The relic's name or id.", true)
                        .strings("powers", "The powers, one per line, at most 12. Omit or send none to strip it.", false, 12),
                RelicTools::imbue));
        return tools;
    }

    /** The relics that exist, for the tools' descriptions. */
    private static String known() {
        List<Relic> all = Relics.all();
        if (all.isEmpty()) return "No relics exist yet.";
        StringBuilder sb = new StringBuilder("Relics that exist: ");
        for (int i = 0; i < all.size() && i < 24; i++) {
            Relic r = all.get(i);
            sb.append(i > 0 ? ", " : "").append(r.name()).append(" (").append(r.kind()).append(")");
        }
        return sb.append(all.size() > 24 ? ", and more." : ".").toString();
    }

    private static Relic relic(Args a) throws ToolException {
        Relic r = Relics.find(a.str("relic"));
        if (r == null) throw new ToolException("There is no relic called '" + a.str("relic") + "'. " + known());
        return r;
    }

    /** Who is asking, for the artist: the god's name and nature and the colors of its body. */
    static String patron(Realm realm) {
        try {
            Persona p = realm.get(Persona::current);
            String hint = p == null ? null : (p.epithet == null ? "" : p.epithet) + " " + (p.name == null ? "" : p.name);
            VesselLook look = VesselLook.resolve(realm.lookSetting(), p == null ? null : p.archetype, hint);
            String who = p == null || p.name == null ? "A god" : "The god " + p.name
                    + (p.epithet == null || p.epithet.isBlank() ? "" : ", " + p.epithet);
            return who + (p == null || p.archetype == null ? "" : " (a " + p.archetype + " god)") + ". Its body wears the "
                    + "look \"" + look.name() + "\": " + VesselLook.description(look.name()) + " The light about it is "
                    + String.format("#%06x", look.outline().asRGB() & 0xFFFFFF) + ".";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Reads and checks powers as a god or an operator wrote them.
     *
     * @param twoFormed whether the relic has (or will be given) a second form
     * @param realm     whose saved effects an "fx" power may name; may be null
     */
    public static RelicPowers.Reading powers(List<String> lines, boolean twoFormed, Realm realm) throws ToolException {
        try {
            RelicPowers.Reading reading = RelicPowers.read(lines, twoFormed, ArtSettings.powerBudget());
            String problem = RelicImbue.problem(reading.powers(), realm);
            if (problem != null) throw new ToolException(problem);
            return reading;
        } catch (RelicPowers.Flaw e) {
            throw new ToolException(e.getMessage());
        }
    }

    private static List<String> lines(Args a) {
        List<String> out = new ArrayList<>();
        if (!a.has("powers")) return out;
        for (String line : a.strings("powers")) {
            // A god sometimes sends several powers on one line, parted by semicolons or newlines.
            for (String part : line.split("[;\\n]+")) {
                if (!part.isBlank()) out.add(part.trim());
            }
        }
        return out;
    }

    private static ToolResult forge(Args a) throws ToolException {
        Realm realm = Realm.current();
        long now = System.currentTimeMillis();
        String no = Relics.refusal(realm.id, now);
        if (no != null) throw new ToolException(no);
        String name = a.str("name").replaceAll("[\\p{Cntrl}§]", "").trim();
        if (name.length() > 32) name = name.substring(0, 32).trim();
        if (name.isEmpty()) throw new ToolException("Give the relic a name.");
        String kind = a.str("kind").toLowerCase(Locale.ROOT);
        if (!Atelier.KINDS.containsKey(kind)) {
            throw new ToolException("A relic is one of: " + String.join(", ", Atelier.KINDS.keySet()) + ".");
        }
        String look = a.str("look").trim();
        if (look.length() > 400) look = look.substring(0, 400);
        // Its powers are read before the artist is asked: a mistake in them should cost nothing.
        List<String> lines = lines(a);
        RelicPowers.Reading reading = powers(lines, true, realm);
        boolean twoForms = reading.needsTwoForms(), special = twoForms || a.bool("special", false);
        Journal.add(Journal.Kind.INFO, "Forging \"" + name + "\" (" + kind + (special ? ", special" : "") + ") with "
                + ArtSettings.model() + " at " + ArtSettings.effort() + " effort");
        // The forge takes a minute or more: let mortals hear that something is being made.
        String smith = realm.displayName();
        org.bukkit.Bukkit.getScheduler().runTask(net.bigyous.gptgodmc.GPTGOD.get(), () -> {
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                p.sendActionBar(net.kyori.adventure.text.Component.text(smith + " is at its forge...",
                        net.bigyous.gptgodmc.god.Speech.VIOLET));
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.25f, 0.6f);
            }
        });
        Atelier.Made made;
        try {
            made = Atelier.artist.make(new Atelier.Brief(name, kind, look, patron(realm), special, twoForms));
        } catch (Atelier.Failed e) {
            throw new ToolException(e.getMessage());
        }
        Sprite awake = made.sprite().alt();
        String imbued = "";
        if (twoForms && awake == null) {
            // The artist drew one form only: powers that wake it cannot hold.
            lines = List.of();
            imbued = " The artist gave it one form only, so the powers that wake it could not be set; give it others "
                    + "with imbue_relic, or forge it again.";
        } else if (!lines.isEmpty()) {
            imbued = String.format(Locale.ROOT, " It is imbued with %d power%s (%.1f of %.0f).", reading.powers().size(),
                    reading.powers().size() == 1 ? "" : "s", reading.cost(), ArtSettings.powerBudget());
        }
        Relic r = Relics.add(realm.id, realm.displayName(), name, kind, look, made.sprite(), lines, now);
        RelicsModule.changed();
        String given = "";
        if (a.has("give_to")) {
            String who = a.str("give_to");
            try {
                given = " " + Sync.call(() -> hand(Targets.player(who), r), 15_000);
            } catch (ToolException e) {
                given = " It could not be given to " + who + " (" + e.getMessage() + "); use give_relic.";
            }
        }
        int scale = Math.max(4, 192 / made.art().w());
        BufferedImage picture = SpriteSheet.enlarged(made.art(), 0, scale, 0x17111F);
        if (awake != null) {
            picture = SpriteSheet.stacked(picture, SpriteSheet.enlarged(SpritePainter.paint(awake, Atelier.halo(kind)), 0,
                    scale, 0x17111F));
        }
        return ToolResult.image("Forged \"" + r.name() + "\" (" + r.kind() + ", id " + r.id() + ") in " + made.attempts()
                + " attempt" + (made.attempts() == 1 ? "" : "s") + ". This is how it looks"
                + (awake != null ? " (asleep above, awake below)." : ".") + imbued + given
                + " give_relic hands it to a mortal; show_relic sets it in the world; imbue_relic changes its powers.",
                SpriteSheet.png(picture));
    }

    /** Puts a relic in a mortal's hands (or at their feet). Server thread. */
    public static String hand(Player p, Relic r) {
        ItemStack stack = Relics.item(r);
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        left.values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.2, 0), 40, 0.4, 0.7, 0.4, 0.04);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.6, 0), 60, 0.5, 0.6, 0.5, 0.8);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.7f);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.6f);
        return "Gave \"" + r.name() + "\" to " + p.getName() + (left.isEmpty() ? "." : " (their hands were full; it lies "
                + "at their feet).");
    }

    private static ToolResult give(Args a) throws ToolException {
        return ToolResult.ok(hand(Targets.player(a.str("player")), relic(a)));
    }

    private static ToolResult imbue(Args a) throws ToolException {
        Relic r = relic(a);
        Realm realm = Realm.current();
        if (!r.god().equals(realm.id) && !r.god().equals("samples")) {
            throw new ToolException("\"" + r.name() + "\" was made by " + r.godName() + "; only its maker may imbue it.");
        }
        List<String> lines = lines(a);
        RelicPowers.Reading reading = powers(lines, r.twoFormed(), realm);
        Relic now = Relics.imbue(r.id(), lines);
        if (now == null) throw new ToolException("\"" + r.name() + "\" no longer exists.");
        if (lines.isEmpty()) {
            return ToolResult.ok("\"%s\" holds no powers now; it is a thing to look at.", now.name());
        }
        return ToolResult.ok(String.format(Locale.ROOT, "\"%s\" is imbued with %d power%s, using %.1f of the %.0f one "
                + "relic can hold. Every copy of it changes the next time it is held.", now.name(), reading.powers().size(),
                reading.powers().size() == 1 ? "" : "s", reading.cost(), ArtSettings.powerBudget()));
    }

    private static ToolResult show(Args a) throws ToolException {
        Relic r = relic(a);
        String action = a.str("action").toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "dismiss" -> {
                    int n = RelicShrines.dismiss(r.id());
                    return ToolResult.ok(n == 0 ? "\"" + r.name() + "\" was not showing anywhere."
                            : "\"" + r.name() + "\" is gone from the world (" + n + " showing" + (n == 1 ? "" : "s")
                                    + "). The relic itself remains yours to give or show.");
                }
                case "follow" -> {
                    if (!a.has("target")) throw new ToolException("Say which mortal it should follow (target).");
                    Player p = Targets.player(a.str("target"));
                    int minutes = a.integer("minutes", 10, 1, 120);
                    RelicShrines.follow(r, p, minutes);
                    return ToolResult.ok("\"%s\" floats at %s's shoulder for %d minutes.", r.name(), p.getName(), minutes);
                }
                default -> {
                    if (!a.has("target")) throw new ToolException("Say where to enshrine it (target).");
                    Location at = Targets.location(a.str("target"));
                    RelicShrines.enshrine(r, at);
                    return ToolResult.ok("\"%s\" now floats at %s, until you dismiss it.", r.name(), Targets.describe(at));
                }
            }
        } catch (IllegalStateException e) {
            throw new ToolException(e.getMessage());
        }
    }
}
