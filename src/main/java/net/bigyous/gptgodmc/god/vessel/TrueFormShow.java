package net.bigyous.gptgodmc.god.vessel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.destroystokyo.paper.ParticleBuilder;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Shapes;
import net.bigyous.gptgodmc.god.vessel.AngelForms.Sprite;
import net.bigyous.gptgodmc.god.vessel.TrueForm.Form;
import net.bigyous.gptgodmc.god.vessel.TrueForm.Gaze;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;
import net.bigyous.gptgodmc.voice.VoiceModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * One showing of the god's true form in the world: it unfolds above a spot in one of its shapes
 * ({@link Form}), warns every mortal near it to avert their eyes, then opens its eyes — and whoever
 * looks at it (within the gaze cone, with nothing solid in between) dies, is hurled back and struck
 * down without dying ("You dare impose upon me?"), or is struck blind and mad ({@link Gaze}). Then it
 * folds back into a point. Drawn with particles and display entities (textured from the god's
 * resource pack for players who have it). Driven by {@link Vessel}'s 2-tick timer; server thread only.
 */
final class TrueFormShow {
    /** Mortals being slain by the gaze right now, with the death message their death should carry. */
    private static final Map<UUID, String> DOOMED = new ConcurrentHashMap<>();
    /** Smitten mortals who cannot die until this time (millis): the smite never kills, nor does its fall. */
    private static final Map<UUID, Long> GUARDED = new ConcurrentHashMap<>();
    private static boolean listening;
    /** How long a mortal who looked but lived (blind) stays blind and terrified, in ticks. */
    private static final int TERROR_TICKS = 200;
    /** How soon a mortal who keeps looking is smitten again, in ticks. */
    private static final int SMITE_TICKS = 120;

    private final World world;
    private final Location ground;
    private final Location centre;
    private final int warnTicks;
    private final int holdTicks;
    private boolean returnBody;
    private final String godName;
    private final Form form;
    private final Gaze gaze;
    private final double gazeDegrees;
    private final double range;

    private int age;
    private int foldAt = -1;
    private double eyeOpen;
    private double eyeYaw;
    private double eyePitch;
    private final Set<UUID> warned = new HashSet<>();
    private final Set<UUID> darkened = new HashSet<>();
    private final Set<UUID> terrified = new HashSet<>();
    private final Set<UUID> slainIds = new HashSet<>();
    private final List<String> slain = new ArrayList<>();
    private final List<String> maddened = new ArrayList<>();
    private final List<String> smitten = new ArrayList<>();
    private final Map<UUID, Integer> struckAt = new HashMap<>();
    private final Map<UUID, Integer> smites = new HashMap<>();
    private final Map<Long, Particle.DustOptions> dust = new HashMap<>();
    /** The form's display entities, one slot per sprite, and which players see the textured ones. */
    private final List<Slot> slots = new ArrayList<>();
    private final Map<UUID, Boolean> viewers = new HashMap<>();
    /** Players whose sky this form has turned to a storm at midnight (given back when it folds away). */
    private final Set<UUID> skied = new HashSet<>();
    private final boolean darkenSky = GodSettings.trueFormDarkenSky();
    private final java.util.Random random = new java.util.Random();
    /** The Watcher: how fixed it is on a mortal (0..1), when its gaze may next jump, and whom it watches. */
    private double focus;
    private int fixUntil;
    private int watching;
    /** The tick its eyes opened (a shockwave runs out over the ground from then), or -1. */
    private int shockAt = -1;
    /** Players with the client mod who have been sent this form's looping aura scene, and its handle. */
    private final Set<UUID> modded = new HashSet<>();
    private final String sceneId = "trueform-" + Integer.toHexString(System.identityHashCode(this));

    TrueFormShow(Location ground, int seconds, boolean returnBody, String godName, Form form, Gaze gaze) {
        this.world = ground.getWorld();
        this.ground = ground.clone();
        this.form = form == null ? Form.HYPERCUBE : form;
        double top = world.getMaxHeight() - this.form.rise - 2;
        this.centre = ground.clone().add(0, this.form.rise, 0);
        if (centre.getY() > top) centre.setY(Math.max(ground.getY() + 3, top));
        this.warnTicks = GodSettings.trueFormWarningSeconds() * 20;
        this.holdTicks = Math.max(warnTicks + 40, seconds * 20);
        this.returnBody = returnBody;
        this.godName = godName == null || godName.isBlank() ? "the god" : godName;
        this.gaze = gaze == null ? GodSettings.trueFormGaze() : gaze;
        this.gazeDegrees = GodSettings.trueFormGazeDegrees();
        this.range = GodSettings.trueFormRange();
    }

    // ---- Lifecycle -----------------------------------------------------------------------------

    void start() {
        listen();
        world.playSound(centre, Sound.BLOCK_END_PORTAL_SPAWN, 5f, 0.5f);
        world.playSound(centre, Sound.ENTITY_WARDEN_EMERGE, 5f, 0.5f);
        world.spawnParticle(Particle.FLASH, centre, 2, 0, 0, 0, 0, Color.WHITE, true);
        net.bigyous.gptgodmc.client.ModFx.trueForm(centre);
        modAura();
        darkenSky();
        warnNewcomers();
        Journal.add(Journal.Kind.INFO, "True form (" + form.id + ", gaze " + gaze.id + ") revealed at "
                + describe(ground) + " for " + holdTicks / 20 + "s");
    }

    /** Starts folding away. withBody=false: the god goes formless afterwards instead of taking its body back. */
    void fold(boolean withBody) {
        if (!withBody) returnBody = false;
        if (foldAt >= 0) return;
        foldAt = age;
        world.playSound(centre, Sound.BLOCK_BEACON_DEACTIVATE, 5f, 0.5f);
        world.playSound(centre, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 5f, 0.5f);
        endModAura();
        release();
        liftDarkness();
    }

    boolean folding() {
        return foldAt >= 0;
    }

    boolean returnsBody() {
        return returnBody;
    }

    Location ground() {
        return ground.clone();
    }

    Location centre() {
        return centre.clone();
    }

    /** Advances 2 ticks; returns true once it has folded away completely (cleanup done). */
    boolean tick() {
        age += 2;
        double unfold = Shapes.ease(age / (double) TrueForm.UNFOLD_TICKS);
        if (foldAt >= 0) unfold = Math.min(unfold, 1 - Shapes.ease((age - foldAt) / (double) TrueForm.FOLD_TICKS));
        if (foldAt >= 0) {
            eyeOpen = Math.max(0, eyeOpen - 0.25);
        } else if (age >= warnTicks) {
            eyeOpen = Shapes.ease((age - warnTicks) / (double) TrueForm.EYE_OPEN_TICKS);
        }
        // It does not fold away while it still holds someone (but it does not wait for ever).
        if (foldAt < 0 && age >= holdTicks && (seized.isEmpty() || age >= holdTicks + SEIZE_HOLD + 20)) fold(returnBody);
        aimEye();
        if (age % 10 == 0) refreshViewers();
        draw(unfold);
        sounds();
        if (foldAt < 0) {
            if (age % 10 == 0) {
                warnNewcomers();
                darkenSky();
                modAura();
            }
            if (age < warnTicks && age % 20 == 0) countdown();
            if (age == warnTicks) eyeOpens();
            if (age > warnTicks) dread();
            if (eyeOpen >= 0.5 && age < holdTicks) gaze();
            seizures();
        }
        if (foldAt >= 0 && age - foldAt >= TrueForm.FOLD_TICKS) {
            finish();
            return true;
        }
        return false;
    }

    /** Ends at once without folding (plugin shutdown): no gaze, no darkness, no terror left behind. */
    void abort() {
        endModAura();
        release();
        liftDarkness();
        restoreSky();
        removeSprites();
        for (UUID id : terrified) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            for (PotionEffectType t : terrorEffects()) p.removePotionEffect(t);
        }
        terrified.clear();
    }

    private void finish() {
        liftDarkness();
        restoreSky();
        removeSprites();
        int held = Math.max(1, (foldAt >= 0 ? foldAt : age) / 20);
        StringBuilder sb = new StringBuilder("Your true form (").append(form.id).append(") folded back into itself after ")
                .append(held).append("s. ");
        if (slain.isEmpty() && maddened.isEmpty() && smitten.isEmpty() && released.isEmpty()) {
            sb.append("No mortal looked upon it; all heeded the warning or were too far away.");
        } else {
            if (!slain.isEmpty()) sb.append("Slain by your gaze: ").append(String.join(", ", slain)).append(". ");
            if (!smitten.isEmpty()) {
                sb.append("Hurled back and struck down by your gaze (alive): ").append(String.join(", ", smitten))
                        .append(". ");
            }
            if (!maddened.isEmpty()) {
                sb.append("Struck blind and mad with terror: ").append(String.join(", ", maddened)).append(". ");
            }
        }
        if (!released.isEmpty()) {
            sb.append(" Seized, but let go alive when it folded away: ").append(String.join(", ", released)).append(".");
        }
        EventLogger.addLoggable(new GenericEventLoggable(sb.toString().trim()));
        Journal.add(Journal.Kind.INFO, sb.toString().trim());
    }

    // ---- What the god sees ---------------------------------------------------------------------

    String describe() {
        StringBuilder sb = new StringBuilder("Your TRUE FORM (").append(form.id).append(": ").append(form.text)
                .append(") hangs above ").append(describe(ground)).append(": ");
        if (foldAt >= 0) {
            sb.append("it is folding back into itself");
            sb.append(returnBody ? "; your vessel returns in a moment" : "; you become formless again");
        } else if (age < warnTicks) {
            sb.append("its eyes are still shut while mortals are warned to avert their eyes (")
                    .append(Math.max(1, (warnTicks - age + 19) / 20)).append("s until they open, ")
                    .append(Math.max(1, (holdTicks - age + 19) / 20)).append("s left in all)");
        } else {
            sb.append("its eyes are OPEN — any mortal who looks at it ").append(gaze.fate)
                    .append(" (").append(Math.max(1, (holdTicks - age + 19) / 20)).append("s left)");
        }
        if (!seized.isEmpty()) {
            sb.append(". Held aloft in your tendrils, about to be unmade: ").append(String.join(", ",
                    seized.values().stream().map(s -> s.player().getName()).toList()));
        }
        if (!slain.isEmpty()) sb.append(". Slain so far: ").append(String.join(", ", slain));
        if (!smitten.isEmpty()) sb.append(". Struck down so far: ").append(String.join(", ", smitten));
        if (!maddened.isEmpty()) sb.append(". Maddened so far: ").append(String.join(", ", maddened));
        return sb.append(". End it early with vessel_true_form action end.").toString();
    }

    // ---- Drawing -------------------------------------------------------------------------------

    private void draw(double unfold) {
        AngelForms.Frame frame = AngelForms.frame(form, age, unfold, eyeOpen, eyeYaw, eyePitch, focus);
        double cx = centre.getX(), cy = centre.getY(), cz = centre.getZ();
        shockwave();
        for (TrueForm.Mote m : frame.motes()) spawn(m, cx, cy, cz, null);
        if (!frame.fallback().isEmpty()) {
            // Eyes and feathers in particles, for those whose client did not load the god's pack.
            List<Player> bare = new ArrayList<>();
            double reach = range + 32;
            for (Player p : world.getPlayers()) {
                if (!VesselPackHost.has(p) && p.getLocation().distanceSquared(centre) <= reach * reach) bare.add(p);
            }
            if (!bare.isEmpty()) for (TrueForm.Mote m : frame.fallback()) spawn(m, cx, cy, cz, bare);
        }
        sprites(frame.sprites());
        if (foldAt < 0 && age < warnTicks && age % 4 == 0) {
            // While it waits, the air below darkens and smoke is drawn up into it.
            world.spawnParticle(Particle.LARGE_SMOKE, ground.getX(), ground.getY() + 1, ground.getZ(), 6, 3, 0.5, 3,
                    0.02, null, true);
            world.spawnParticle(Particle.REVERSE_PORTAL, cx, cy, cz, 12, 5, 5, 5, 0.05, null, true);
        }
        if (foldAt < 0 && age % 8 == 0 && unfold > 0.9) ambient(cx, cy, cz);
    }

    /** A little of each form's own weather. */
    private void ambient(double cx, double cy, double cz) {
        switch (form) {
            case OPHANIM -> world.spawnParticle(Particle.LAVA, cx, cy, cz, 5, 5, 5, 5, 0, null, true);
            case SERAPHIM -> world.spawnParticle(Particle.WHITE_ASH, cx, cy, cz, 60, 9, 5, 3, 0.02, null, true);
            case EYE -> {
                if (eyeOpen > 0.5) {
                    // It weeps.
                    world.spawnParticle(Particle.FALLING_OBSIDIAN_TEAR, cx, cy - 5, cz, 8, 2.2, 0.3, 2.2, 0, null, true);
                }
            }
            case PILLAR -> world.spawnParticle(Particle.SCULK_SOUL, ground.getX(), ground.getY() + 0.5, ground.getZ(),
                    6, 2.5, 0.3, 2.5, 0.04, null, true);
            default -> {
            }
        }
    }

    private void spawn(TrueForm.Mote m, double cx, double cy, double cz, List<Player> only) {
        double x = cx + m.x(), y = cy + m.y(), z = cz + m.z();
        Particle particle = switch (m.glyph()) {
            case GLOW -> Particle.END_ROD;
            case SOUL -> Particle.SOUL_FIRE_FLAME;
            case FLAME -> Particle.FLAME;
            case SPARK -> Particle.ELECTRIC_SPARK;
            default -> Particle.DUST;
        };
        Object data = particle == Particle.DUST ? dust(m.rgb(), m.size()) : null;
        if (only == null) {
            world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0, data, true);
        } else {
            new ParticleBuilder(particle).location(world, x, y, z).count(1).offset(0, 0, 0).extra(0).data(data)
                    .force(true).receivers(only).spawn();
        }
    }

    private Particle.DustOptions dust(int rgb, float size) {
        float s = Math.round(size * 1.6f * 10) / 10f;
        long key = ((long) rgb << 8) | (long) (s * 10);
        return dust.computeIfAbsent(key, k -> new Particle.DustOptions(Color.fromRGB(rgb), s));
    }

    // ---- Display entities ------------------------------------------------------------------------

    /** One sprite's entities: a block everyone sees, or a textured piece plus its vanilla stand-in. */
    private static final class Slot {
        Display main;
        Display fallback;
        String key;
        boolean twins;
        /** Whether each is a block display (a block's corner is its origin, an item's centre is). */
        boolean mainIsBlock;
        boolean fallbackIsBlock;
        Transformation sent;
        Transformation sentFallback;
    }

    private void sprites(List<Sprite> sprites) {
        if (sprites.isEmpty()) return;
        if (slots.isEmpty()) spawnSprites(sprites);
        for (int i = 0; i < Math.min(slots.size(), sprites.size()); i++) {
            Sprite s = sprites.get(i);
            Slot slot = slots.get(i);
            if (slot.main != null && slot.main.isValid()) {
                if (s.textured() && !s.key().equals(slot.key) && slot.main instanceof ItemDisplay d) {
                    d.setItemStack(model(s.key()));
                    slot.key = s.key();
                }
                Transformation t = transformation(s, slot.mainIsBlock, 1);
                if (!t.equals(slot.sent)) {
                    slot.main.setInterpolationDelay(0);
                    slot.main.setInterpolationDuration(2);
                    slot.main.setTransformation(t);
                    slot.sent = t;
                }
            }
            if (slot.fallback != null && slot.fallback.isValid()) {
                Transformation t = transformation(s, slot.fallbackIsBlock, s.fallbackScale());
                if (!t.equals(slot.sentFallback)) {
                    slot.fallback.setInterpolationDelay(0);
                    slot.fallback.setInterpolationDuration(2);
                    slot.fallback.setTransformation(t);
                    slot.sentFallback = t;
                }
            }
        }
    }

    private void spawnSprites(List<Sprite> sprites) {
        boolean pack = VesselPackHost.active();
        for (Sprite s : sprites) {
            Slot slot = new Slot();
            slots.add(slot);
            if (!s.textured()) {
                Material m = Material.matchMaterial(s.block());
                if (m == null || !m.isBlock()) m = Material.GOLD_BLOCK;
                Material block = m;
                // With the pack, a textured box of engraved metal or light stands in for the plain block.
                boolean boxed = pack && s.box() != null;
                BlockDisplay plain = world.spawn(centre, BlockDisplay.class, b -> {
                    setup(b);
                    if (boxed) b.setVisibleByDefault(false);
                    b.setBlock(block.createBlockData());
                    b.setTransformation(transformation(s, true, 1));
                });
                if (!boxed) {
                    slot.main = plain;
                    slot.mainIsBlock = true;
                    continue;
                }
                slot.twins = true;
                slot.fallback = plain;
                slot.fallbackIsBlock = true;
                slot.main = world.spawn(centre, ItemDisplay.class, d -> {
                    setup(d);
                    d.setVisibleByDefault(false);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setItemStack(model(s.box()));
                    d.setTransformation(transformation(s, false, 1));
                });
                continue;
            }
            slot.twins = pack;
            if (pack) {
                slot.key = s.key();
                slot.main = world.spawn(centre, ItemDisplay.class, d -> {
                    setup(d);
                    d.setVisibleByDefault(false);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setItemStack(model(s.key()));
                    d.setBillboard(s.billboard() ? Display.Billboard.CENTER : Display.Billboard.FIXED);
                    d.setTransformation(transformation(s, false, 1));
                });
            }
            Material item = s.fallback() == null ? null : Material.matchMaterial(s.fallback());
            if (item != null) {
                slot.fallback = world.spawn(centre, ItemDisplay.class, d -> {
                    setup(d);
                    if (pack) d.setVisibleByDefault(false);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setItemStack(new ItemStack(item));
                    d.setTransformation(transformation(s, false, s.fallbackScale()));
                });
            }
        }
        viewers.clear();
        refreshViewers();
    }

    /** Shows each player the textured pieces if their client loaded the pack, else the stand-ins. */
    private void refreshViewers() {
        if (slots.isEmpty()) return;
        viewers.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player p : world.getPlayers()) {
            boolean has = VesselPackHost.has(p);
            Boolean was = viewers.put(p.getUniqueId(), has);
            if (was != null && was == has) continue;
            for (Slot s : slots) {
                if (!s.twins) continue;
                if (s.main != null && s.main.isValid()) {
                    if (has) p.showEntity(GPTGOD.get(), s.main);
                    else p.hideEntity(GPTGOD.get(), s.main);
                }
                if (s.fallback != null && s.fallback.isValid()) {
                    if (has) p.hideEntity(GPTGOD.get(), s.fallback);
                    else p.showEntity(GPTGOD.get(), s.fallback);
                }
            }
        }
    }

    private void removeSprites() {
        for (Slot s : slots) {
            if (s.main != null) s.main.remove();
            if (s.fallback != null) s.fallback.remove();
        }
        slots.clear();
        viewers.clear();
    }

    private static void setup(Display d) {
        d.getPersistentDataContainer().set(new NamespacedKey(GPTGOD.get(), Vessel.TAG), PersistentDataType.BYTE,
                (byte) 1);
        d.setPersistent(false);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(4.0f);
        d.setShadowRadius(0);
    }

    private static ItemStack model(String key) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(TrueFormPack.modelId(key)));
        stack.setItemMeta(meta);
        return stack;
    }

    /** A sprite as a display transformation; blocks are shifted so the box is centred on its position. */
    static Transformation transformation(Sprite s, boolean block, double extra) {
        Quaternionf q = s.billboard() ? new Quaternionf()
                : new Quaternionf((float) s.qx(), (float) s.qy(), (float) s.qz(), (float) s.qw());
        Vector3f scale = extra == 1 ? new Vector3f((float) s.sx(), (float) s.sy(), (float) s.sz())
                : new Vector3f((float) (s.sx() * extra), (float) (s.sy() * extra), (float) (s.sx() * extra));
        Vector3f at = new Vector3f((float) s.x(), (float) s.y(), (float) s.z());
        if (block) at.sub(q.transform(new Vector3f(scale).mul(0.5f)));
        return new Transformation(at, q, scale, new Quaternionf());
    }

    // ---- The client mod's share ---------------------------------------------------------------------

    private static boolean hasMod(Player p) {
        return net.bigyous.gptgodmc.client.ModLink.has(p, net.bigyous.gptgodmc.client.ModSessions.FX);
    }

    private void sendScene(Player p, String id, Location at, boolean loop, com.google.gson.JsonArray layers) {
        net.bigyous.gptgodmc.client.ModLink.send(p, net.bigyous.gptgodmc.client.ModSessions.FX,
                net.bigyous.gptgodmc.god.FxScenes.message(id, new double[] { at.getX(), at.getY(), at.getZ() }, -1, 1,
                        loop, layers));
    }

    /** Mod players near it get its aura (bloom, rays, haloes, a wrong sky), looping until it folds away. */
    private void modAura() {
        double reach = range + 32;
        for (Player p : world.getPlayers()) {
            if (!hasMod(p) || p.getLocation().distanceSquared(centre) > reach * reach) continue;
            if (modded.add(p.getUniqueId())) sendScene(p, sceneId, centre, true, TrueFormScenes.aura(form));
        }
    }

    private void endModAura() {
        for (UUID id : modded) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                net.bigyous.gptgodmc.client.ModLink.send(p, net.bigyous.gptgodmc.client.ModSessions.FX,
                        net.bigyous.gptgodmc.god.FxScenes.stop(sceneId));
            }
        }
        modded.clear();
    }

    // ---- Dread -------------------------------------------------------------------------------------

    /** Day ends for everyone near it: their sky turns to a storm at midnight until it folds away. */
    private void darkenSky() {
        if (!darkenSky) return;
        double reach = range + 32;
        for (Player p : world.getPlayers()) {
            if (skied.contains(p.getUniqueId())) continue;
            if (p.getLocation().distanceSquared(centre) > reach * reach) continue;
            skied.add(p.getUniqueId());
            p.setPlayerTime(18000, false);
            p.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
        }
    }

    private void restoreSky() {
        for (UUID id : skied) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.resetPlayerTime();
            p.resetPlayerWeather();
        }
        skied.clear();
    }

    /** A harmless bolt (no fire, no damage) on the ground somewhere within {@code spread} blocks of it. */
    private void bolt(double spread) {
        double a = random.nextDouble() * Math.PI * 2, r = 4 + random.nextDouble() * spread;
        Location at = ground.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
        at.setY(world.getHighestBlockYAt(at) + 1);
        world.strikeLightningEffect(at);
    }

    /** From the moment its eyes open, a ring of force runs out over the ground beneath it. */
    private void shockwave() {
        if (shockAt < 0 || age - shockAt > 36) return;
        double r = 2 + (age - shockAt) * 0.9;
        int n = (int) Math.min(64, 12 + r * 2.5);
        double y = ground.getY() + 0.4;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + age * 0.1;
            double x = ground.getX() + Math.cos(a) * r, z = ground.getZ() + Math.sin(a) * r;
            world.spawnParticle(i % 3 == 0 ? Particle.END_ROD : Particle.LARGE_SMOKE, x, y, z, 1, 0, 0.05, 0, 0.01, null,
                    true);
        }
    }

    /** While its eyes are open the world does not sit still: thunder, a pulse in the skull, things behind you. */
    private void dread() {
        int since = age - warnTicks;
        if (since % 50 == 20) bolt(26);
        if (since % 20 == 0) {
            net.bigyous.gptgodmc.client.ModFx.near(centre, range, net.bigyous.gptgodmc.client.ModMessages.Screen.PULSE,
                    0.35, 12, 0x5A0010);
        }
        if (since % 40 != 10) return;
        for (Player p : world.getPlayers()) {
            if (!inRange(p) || !vulnerable(p)) continue;
            // Something breathes just behind them, wherever they turn.
            Location eye = p.getEyeLocation();
            Location behind = eye.clone().subtract(eye.getDirection().setY(0).normalize().multiply(2.5));
            Sound s = switch (random.nextInt(3)) {
                case 0 -> Sound.ENTITY_WARDEN_NEARBY_CLOSEST;
                case 1 -> Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD;
                default -> Sound.AMBIENT_CAVE;
            };
            if (Double.isFinite(behind.getX())) p.playSound(behind, s, 0.9f, 0.6f);
        }
    }

    /** Its eyes turn, slowly, toward the nearest mortal they could strike. */
    private void aimEye() {
        if (form == Form.EYE) {
            dart();
            return;
        }
        Player near = null;
        double best = range * range;
        for (Player p : world.getPlayers()) {
            if (!vulnerable(p)) continue;
            double d = p.getEyeLocation().distanceSquared(centre);
            if (d < best) {
                best = d;
                near = p;
            }
        }
        double wantYaw, wantPitch;
        if (near != null) {
            Location e = near.getEyeLocation();
            double dx = e.getX() - centre.getX(), dy = e.getY() - centre.getY(), dz = e.getZ() - centre.getZ();
            wantYaw = Math.atan2(dx, dz);
            wantPitch = -Math.atan2(dy, Math.hypot(dx, dz));
        } else {
            wantYaw = eyeYaw + 0.02;
            wantPitch = 0.2 * Math.sin(age * 0.02);
        }
        double turn = form == Form.SERAPHIM ? 0.035 : 0.07;
        double dYaw = Math.atan2(Math.sin(wantYaw - eyeYaw), Math.cos(wantYaw - eyeYaw));
        eyeYaw += Math.max(-turn, Math.min(turn, dYaw));
        eyePitch += Math.max(-0.05, Math.min(0.05, wantPitch - eyePitch));
    }

    /**
     * The Watcher does not turn smoothly. It holds still on one mortal, then flicks to the next, the way a
     * living eye does; with no one to find, it throws glances about the sky. Its pupils close on whom it holds.
     */
    private void dart() {
        List<Player> seen = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (vulnerable(p) && p.getEyeLocation().distanceSquared(centre) <= range * range) seen.add(p);
        }
        seen.sort(java.util.Comparator.comparing(Player::getUniqueId));
        if (age >= fixUntil) {
            // Time to look elsewhere: the next mortal, or (alone) somewhere at random.
            watching++;
            fixUntil = age + 16 + random.nextInt(seen.size() > 1 ? 30 : 50);
            if (seen.isEmpty()) {
                eyeYaw = random.nextDouble() * Math.PI * 2;
                eyePitch = (random.nextDouble() - 0.3) * 0.9;
            }
            focus = 0;
        }
        if (seen.isEmpty()) {
            focus = Math.max(0, focus - 0.1);
            return;
        }
        Location e = seen.get(Math.floorMod(watching, seen.size())).getEyeLocation();
        double dx = e.getX() - centre.getX(), dy = e.getY() - centre.getY(), dz = e.getZ() - centre.getZ();
        // Once fixed, it follows without the least lag.
        eyeYaw = Math.atan2(dx, dz);
        eyePitch = -Math.atan2(dy, Math.hypot(dx, dz));
        focus = Math.min(1, focus + 0.17);
    }

    private void play(Sound s, float volume, float pitch) {
        world.playSound(centre, s, volume, pitch);
    }

    private void sounds() {
        if (foldAt >= 0) return;
        if (age < warnTicks) {
            // The heartbeat quickens as the eye gets ready to open.
            int beat = age > warnTicks - 40 ? 8 : age > warnTicks / 2 ? 14 : 20;
            if (age % beat == 0) play(Sound.ENTITY_WARDEN_HEARTBEAT, 4f, 0.5f);
            if (age % 60 == 0) play(Sound.AMBIENT_CAVE, 4f, 0.5f);
        } else {
            if (age % 20 == 0) play(Sound.ENTITY_WARDEN_HEARTBEAT, 4f, 0.6f);
        }
        boolean open = age >= warnTicks;
        switch (form) {
            case OPHANIM -> {
                if (age % 24 == 0) play(Sound.BLOCK_GRINDSTONE_USE, 3f, 0.5f);
                if (age % 10 == 0) play(Sound.BLOCK_FIRE_AMBIENT, 3f, 0.6f);
                if (age % 60 == 0) play(Sound.BLOCK_BELL_RESONATE, 4f, 0.5f);
                if (open && age % 40 == 0) play(Sound.BLOCK_CONDUIT_AMBIENT, 4f, 0.5f);
            }
            case SERAPHIM -> {
                if (age % 20 == 0) play(Sound.ENTITY_ENDER_DRAGON_FLAP, 4f, 0.6f);
                if (age % 24 == 0) play(Sound.ENTITY_BLAZE_BURN, 3f, 0.5f);
                if (age % 50 == 0) play(Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 4f, 0.5f);
                if (open && age % 80 == 0) play(Sound.BLOCK_BEACON_POWER_SELECT, 4f, 0.5f);
            }
            case EYE -> {
                if (age % 50 == 0) play(Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 4f, 0.5f);
                if (open && age % 60 == 0) play(Sound.ENTITY_ENDERMAN_STARE, 3f, 0.5f);
            }
            case PILLAR -> {
                if (age % 30 == 0) play(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 4f, 0.5f);
                if (age % 20 == 0) play(Sound.BLOCK_SCULK_SENSOR_CLICKING, 3f, 0.6f);
                if (age % 60 == 0) play(Sound.BLOCK_SCULK_CATALYST_BLOOM, 4f, 0.5f);
                if (age % 40 == 0) play(Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 4f, 0.5f);
            }
            default -> {
                if (open && age % 40 == 0) play(Sound.BLOCK_BEACON_AMBIENT, 4f, 0.5f);
                if (open && age % 70 == 0) play(Sound.BLOCK_PORTAL_AMBIENT, 4f, 0.5f);
            }
        }
    }

    // ---- Warning -------------------------------------------------------------------------------

    private boolean inRange(Player p) {
        return p.getWorld().equals(world) && p.getEyeLocation().distanceSquared(centre) <= range * range;
    }

    private static boolean vulnerable(Player p) {
        GameMode g = p.getGameMode();
        return (g == GameMode.SURVIVAL || g == GameMode.ADVENTURE) && !p.isDead() && p.isValid();
    }

    /** Everyone who comes within reach is told, once, unmistakably, not to look. */
    private void warnNewcomers() {
        double reach = range + 16;
        for (Player p : world.getPlayers()) {
            if (warned.contains(p.getUniqueId())) continue;
            if (p.getEyeLocation().distanceSquared(centre) > reach * reach) continue;
            warned.add(p.getUniqueId());
            int secs = Math.max(0, (warnTicks - age) / 20);
            p.showTitle(Title.title(
                    Component.text("AVERT YOUR EYES", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                    Component.text(cap(godName) + " reveals its true form. Do not look upon it — " + gaze.threat + ".",
                            NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(Math.max(2, secs)),
                            Duration.ofMillis(600))));
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.6f);
            p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.5f);
        }
    }

    private void countdown() {
        int left = (warnTicks - age) / 20;
        if (left <= 0 || left > 5) return;
        for (Player p : world.getPlayers()) {
            if (!inRange(p)) continue;
            p.sendActionBar(Component.text("It opens its eyes in " + left + "… LOOK AWAY", NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true));
        }
    }

    private void eyeOpens() {
        liftDarkness();
        switch (form) {
            case OPHANIM -> {
                play(Sound.BLOCK_BELL_USE, 6f, 0.5f);
                play(Sound.ITEM_TRIDENT_THUNDER, 6f, 0.6f);
            }
            case SERAPHIM -> {
                play(Sound.ENTITY_ENDER_DRAGON_FLAP, 6f, 0.5f);
                play(Sound.BLOCK_BEACON_ACTIVATE, 6f, 0.5f);
            }
            case EYE -> {
                play(Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 6f, 0.5f);
                play(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 6f, 0.5f);
            }
            case PILLAR -> {
                play(Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 6f, 0.6f);
                play(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 6f, 0.5f);
            }
            default -> {
            }
        }
        play(Sound.ENTITY_WARDEN_SONIC_BOOM, 6f, 0.5f);
        play(Sound.ENTITY_ENDER_DRAGON_GROWL, 6f, 0.4f);
        world.spawnParticle(Particle.FLASH, centre, 3, 0.5, 0.5, 0.5, 0, Color.WHITE, true);
        // The world answers: bolts strike round it, the ground heaves, every head is jolted.
        shockAt = age;
        for (int i = 0; i < 4; i++) bolt(14);
        net.bigyous.gptgodmc.client.ModFx.near(centre, range + 16, net.bigyous.gptgodmc.client.ModMessages.Screen.SHAKE,
                0.9, 30, 0xFFFFFF);
        net.bigyous.gptgodmc.client.ModFx.near(centre, range + 16, net.bigyous.gptgodmc.client.ModMessages.Screen.FLASH,
                0.8, 8, 0xFFFFFF);
        net.bigyous.gptgodmc.client.ModFx.near(centre, range + 16,
                net.bigyous.gptgodmc.client.ModMessages.Screen.VIGNETTE, 0.7, Math.max(40, holdTicks - age), 0x12000A);
        for (Player p : world.getPlayers()) {
            if (!inRange(p)) continue;
            p.sendHurtAnimation(0);
            // With the mod: the world bends in a ring, the ground seems to rise, bolts of solid light fork down.
            if (hasMod(p)) sendScene(p, null, centre, false, TrueFormScenes.opening(form));
        }
        String seen = switch (form) {
            case OPHANIM -> "THE WHEELS SEE";
            case SERAPHIM -> "HOLY, HOLY, HOLY";
            case EYE -> "IT SEES YOU";
            case PILLAR -> "A THOUSAND EYES OPEN";
            default -> "IT SEES";
        };
        for (Player p : world.getPlayers()) {
            if (!inRange(p)) continue;
            p.showTitle(Title.title(Component.text(seen, NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                    Component.text("Do not look.", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(500))));
        }
        Journal.add(Journal.Kind.INFO, "The true form opens its eyes");
    }

    private void liftDarkness() {
        for (UUID id : darkened) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.removePotionEffect(PotionEffectType.DARKNESS);
        }
        darkened.clear();
    }

    // ---- The gaze ------------------------------------------------------------------------------

    /** Where a mortal may meet its gaze: its heart, and for the tall pillar its crown and foot too. */
    private List<Vector> hearts() {
        Vector c = centre.toVector();
        if (form != Form.PILLAR) return List.of(c);
        return List.of(c, c.clone().add(new Vector(0, 7, 0)), c.clone().add(new Vector(0, 13.5, 0)),
                c.clone().add(new Vector(0, -6, 0)));
    }

    private void gaze() {
        List<Vector> hearts = hearts();
        for (Player p : new ArrayList<>(world.getPlayers())) {
            if (!vulnerable(p) || slainIds.contains(p.getUniqueId()) || !inRange(p)) continue;
            Location eye = p.getEyeLocation();
            boolean looks = false, sees = false;
            for (Vector heart : hearts) {
                if (!TrueForm.gazes(eye.toVector(), eye.getDirection(), heart, gazeDegrees, range, form.heart)) continue;
                looks = true;
                if (clearSight(eye, heart)) {
                    sees = true;
                    break;
                }
            }
            if (sees) {
                strike(p);
            } else if (age % 10 == 0) {
                cue(p, eye, looks);
            }
        }
    }

    /** Nothing solid between the mortal's eye and the form's heart. */
    private boolean clearSight(Location eye, Vector heart) {
        Vector to = heart.clone().subtract(eye.toVector());
        double dist = to.length() - form.heart * 0.5;
        if (dist <= 0.1) return true;
        RayTraceResult hit = world.rayTraceBlocks(eye, to.normalize(), dist, FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }

    /** A faint sign for those who are not looking: how close their gaze strays. */
    private void cue(Player p, Location eye, boolean shielded) {
        double angle = TrueForm.angleTo(eye.toVector(), eye.getDirection(), centre.toVector());
        if (shielded) {
            p.sendActionBar(Component.text("Something vast stares at you through the stone…", NamedTextColor.DARK_GRAY));
        } else if (angle < Math.max(30, gazeDegrees * 2.5)) {
            p.sendActionBar(Component.text("Something pulls at your eyes — LOOK AWAY", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.2f);
        } else {
            p.sendActionBar(Component.text(gaze.lethal() ? "You keep your eyes averted. You live."
                    : "You keep your eyes averted.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        }
    }

    // ---- Seized: taken up, shown everything, unmade ------------------------------------------------

    /** Ticks the tendril takes to reach a mortal, and ticks from being seized to being unmade. */
    private static final int SEIZE_REACH = 24;
    private static final int SEIZE_HOLD = 110;

    /** A mortal the form has hold of: since when, and where they stood. */
    private record Seized(Player player, int since, Location anchor) {
    }

    private final Map<UUID, Seized> seized = new HashMap<>();
    private final List<String> released = new ArrayList<>();

    /**
     * It does not strike at once. A tendril of light goes out to the one who looked, lifts them into a storm
     * of their own before it, lets them see it whole, and then unmakes them.
     */
    private void seize(Player p) {
        UUID id = p.getUniqueId();
        if (seized.containsKey(id)) return;
        slainIds.add(id);
        Location anchor = p.getLocation().clone();
        seized.put(id, new Seized(p, age, anchor));
        // Nothing else may have them first.
        GUARDED.put(id, System.currentTimeMillis() + (SEIZE_HOLD + 100) * 50L);
        p.showTitle(Title.title(Component.text("IT HAS SEEN YOU", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Behold.", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(800))));
        world.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 2f, 0.6f);
        world.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
        Location hold = anchor.clone().add(0, 7, 0);
        com.google.gson.JsonArray scene = TrueFormScenes.seized(centre.getX() - hold.getX(), centre.getY() - hold.getY(),
                centre.getZ() - hold.getZ(), SEIZE_HOLD, form == Form.EYE || form == Form.HYPERCUBE);
        for (Player q : world.getPlayers()) {
            if (hasMod(q) && q.getLocation().distanceSquared(hold) < 96 * 96) sendScene(q, null, hold, false, scene);
        }
        String name = p.getName();
        EventLogger.addLoggable(new GenericEventLoggable(name + " looked upon your true form; a tendril of your light has "
                + "seized them and lifts them before you. In a few seconds they are unmade (end the form early to let "
                + "them go).", name + " looked upon your true form"));
        Journal.add(Journal.Kind.INFO, name + " looked upon the true form and was seized");
    }

    /** Draws every tendril, holds the seized aloft in their storms, and unmakes those whose time has come. */
    private void seizures() {
        if (seized.isEmpty()) return;
        Vector heart = centre.toVector();
        for (Seized s : new ArrayList<>(seized.values())) {
            Player p = s.player();
            int t = age - s.since();
            if (!p.isOnline() || p.isDead() || !p.getWorld().equals(world)) {
                seized.remove(p.getUniqueId());
                GUARDED.remove(p.getUniqueId());
                continue;
            }
            Location at = p.getLocation();
            Vector chest = at.toVector().add(new Vector(0, 1, 0));
            // The tendril: a wavering line of light from its heart, reaching further every tick until it has them.
            Vector span = chest.clone().subtract(heart);
            double reach = Math.min(1, t / (double) SEIZE_REACH);
            Vector side = span.clone().crossProduct(new Vector(0, 1, 0));
            if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0);
            side.normalize();
            int points = 28;
            for (int i = 1; i <= points * reach; i++) {
                double f = i / (double) points;
                double wave = Math.sin(f * 9 - age * 0.35) * 0.7 * Math.sin(f * Math.PI);
                Vector q = heart.clone().add(span.clone().multiply(f)).add(side.clone().multiply(wave))
                        .add(new Vector(0, Math.cos(f * 7 + age * 0.3) * 0.4 * Math.sin(f * Math.PI), 0));
                world.spawnParticle(i % 5 == 0 ? Particle.ELECTRIC_SPARK : Particle.END_ROD, q.getX(), q.getY(), q.getZ(),
                        1, 0, 0, 0, 0, null, true);
            }
            if (t >= SEIZE_REACH) {
                int held = t - SEIZE_REACH;
                if (held == 0) {
                    world.playSound(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 2f, 0.5f);
                    // Levitation keeps the server from taking the hover for cheating; the pull below does the lifting.
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, SEIZE_HOLD - SEIZE_REACH + 10, 0, false,
                            false, false));
                }
                // Lifted to hang seven blocks above where they stood, turned loose to look.
                Vector target = s.anchor().toVector().add(new Vector(0, 7 * Shapes.ease(held / 40.0), 0));
                Vector pull = target.subtract(at.toVector()).multiply(0.3);
                if (pull.length() > 0.7) pull.normalize().multiply(0.7);
                p.setVelocity(pull);
                p.setFallDistance(0);
                // Their own storm turns round them, tightening as the end comes.
                double tight = 1 - 0.5 * held / (double) (SEIZE_HOLD - SEIZE_REACH);
                for (int k = 0; k < 8; k++) {
                    double a = age * 0.4 + k * Math.PI / 4;
                    double r = (2.2 + 0.4 * Math.sin(age * 0.1 + k)) * tight;
                    double y = ((age * 0.11 + k * 0.45) % 3.6) - 0.8;
                    world.spawnParticle(k % 3 == 0 ? Particle.ELECTRIC_SPARK : Particle.END_ROD, at.getX() + Math.cos(a) * r,
                            at.getY() + y, at.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, null, true);
                }
                world.spawnParticle(Particle.CLOUD, at.getX(), at.getY() + 3.4, at.getZ(), 4, 1.6 * tight, 0.3, 1.6 * tight,
                        0.01, null, true);
                if (held % 16 == 0) {
                    float pitch = 0.5f + 1.2f * held / (SEIZE_HOLD - SEIZE_REACH);
                    world.playSound(at, Sound.BLOCK_BEACON_AMBIENT, 2f, pitch);
                    world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.6f);
                }
            }
            if (t >= SEIZE_HOLD) {
                seized.remove(p.getUniqueId());
                unmake(p);
            }
        }
    }

    private void unmake(Player p) {
        UUID id = p.getUniqueId();
        Location at = p.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0, null, true);
        world.spawnParticle(Particle.FLASH, at, 2, 0.2, 0.2, 0.2, 0, Color.WHITE, true);
        world.spawnParticle(Particle.END_ROD, at, 160, 0.2, 0.2, 0.2, 0.7, null, true);
        world.spawnParticle(Particle.SOUL, at, 40, 0.4, 0.6, 0.4, 0.08, null, true);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.8f);
        world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.7f);
        net.bigyous.gptgodmc.client.ModFx.near(at, 48, net.bigyous.gptgodmc.client.ModMessages.Screen.FLASH, 0.8, 8,
                0xFFFFFF);
        net.bigyous.gptgodmc.client.ModFx.near(at, 48, net.bigyous.gptgodmc.client.ModMessages.Screen.SHAKE, 0.7, 14,
                0xFFFFFF);
        slain.add(p.getName());
        p.removePotionEffect(PotionEffectType.LEVITATION);
        GUARDED.remove(id);
        DOOMED.put(id, p.getName() + " was taken up and unmade by the true form of " + godName);
        try {
            p.setHealth(0);
        } finally {
            DOOMED.remove(id);
        }
        Journal.add(Journal.Kind.INFO, p.getName() + " was unmade by the true form");
    }

    /** Lets go of everyone it holds, alive: they drift down, and nothing kills them on the way. */
    private void release() {
        for (Seized s : seized.values()) {
            Player p = s.player();
            if (!p.isOnline() || p.isDead()) continue;
            p.removePotionEffect(PotionEffectType.LEVITATION);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, false, true));
            GUARDED.put(p.getUniqueId(), System.currentTimeMillis() + 10_000L);
            slainIds.remove(p.getUniqueId());
            released.add(p.getName());
        }
        seized.clear();
    }

    private void strike(Player p) {
        switch (gaze) {
            case KILL -> kill(p);
            case SEIZE -> seize(p);
            case SMITE -> smite(p);
            default -> blind(p);
        }
    }

    private void kill(Player p) {
        UUID id = p.getUniqueId();
        Location at = p.getEyeLocation();
        slainIds.add(id);
        slain.add(p.getName());
        world.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2f, 0.5f);
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 1.4f);
        world.spawnParticle(Particle.SOUL, at, 30, 0.4, 0.6, 0.4, 0.05, null, true);
        world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.WHITE, true);
        DOOMED.put(id, p.getName() + " looked upon the true form of " + godName);
        try {
            p.setHealth(0);
        } finally {
            DOOMED.remove(id);
        }
        Journal.add(Journal.Kind.INFO, p.getName() + " looked upon the true form and died");
    }

    /**
     * "You dare impose upon me?": the mortal is hurled back from the form, crushed with darkness, nausea,
     * slowness and weakness, and loses half a heart at most; for a while after nothing can kill them
     * (the fall from being hurled included).
     */
    private void smite(Player p) {
        UUID id = p.getUniqueId();
        Integer last = struckAt.get(id);
        if (last != null && age - last < SMITE_TICKS) return;
        struckAt.put(id, age);
        terrified.add(id);
        int n = smites.merge(id, 1, Integer::sum) - 1;
        GUARDED.put(id, System.currentTimeMillis() + TrueForm.SMITE_GUARD_TICKS * 50L);
        net.bigyous.gptgodmc.client.ModFx.smitten(p);
        if (hasMod(p)) sendScene(p, null, p.getEyeLocation(), false, TrueFormScenes.struck());
        Location eye = p.getEyeLocation();
        double hurt = TrueForm.smiteDamage(p.getHealth());
        if (hurt > 0) p.damage(hurt);
        Vector away = p.getLocation().toVector().subtract(centre.toVector()).setY(0);
        if (away.lengthSquared() < 1e-4) away = new Vector(1, 0, 0);
        p.setVelocity(away.normalize().multiply(1.7).setY(0.75));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 140, 2, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 300, 1, false, false, true));
        // A blast of force from its heart to the one who looked.
        Vector from = centre.toVector(), to = eye.toVector();
        for (int i = 1; i <= 6; i++) {
            Vector at = from.clone().add(to.clone().subtract(from).multiply(i / 7.0));
            world.spawnParticle(Particle.SONIC_BOOM, at.getX(), at.getY(), at.getZ(), 1, 0, 0, 0, 0, null, true);
        }
        world.spawnParticle(Particle.EXPLOSION, eye, 2, 0.3, 0.3, 0.3, 0, null, true);
        world.spawnParticle(Particle.FLASH, eye, 1, 0, 0, 0, 0, Color.WHITE, true);
        world.spawnParticle(Particle.SOUL, eye, 20, 0.4, 0.6, 0.4, 0.05, null, true);
        world.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.6f);
        world.playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.6f);
        world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        world.playSound(eye, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.5f);
        // The god's own voice booms the words; the title shows them as the voice starts.
        String line = TrueForm.smiteLine(n);
        Title title = Title.title(Component.text(line.toUpperCase(Locale.ROOT), NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true), Component.text("— " + cap(godName), NamedTextColor.GOLD),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(800)));
        Component chat = Component.text("[" + cap(godName) + "] ", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(line, NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, false));
        VoiceModule.speak(line, List.of(p), "commanding", () -> {
            if (!p.isOnline()) return;
            p.showTitle(title);
            p.sendMessage(chat);
        });
        if (last == null) {
            String name = p.getName();
            smitten.add(name);
            EventLogger.addLoggable(new GenericEventLoggable(name + " looked upon your true form and was hurled back "
                    + "and struck down by your gaze (\"" + line + "\"); they live.", name + " looked upon your true form"));
            Journal.add(Journal.Kind.INFO, name + " looked upon the true form and was smitten");
        }
    }

    private void blind(Player p) {
        UUID id = p.getUniqueId();
        Location at = p.getEyeLocation();
        Integer last = struckAt.get(id);
        if (last != null && age - last < TERROR_TICKS) return;
        struckAt.put(id, age);
        terrified.add(id);
        for (PotionEffectType t : terrorEffects()) {
            int amp = t == PotionEffectType.SLOWNESS ? 2 : 0;
            p.addPotionEffect(new PotionEffect(t, TERROR_TICKS + (t == PotionEffectType.NAUSEA ? 60 : 0), amp,
                    false, false, true));
        }
        p.showTitle(Title.title(Component.text("YOU LOOKED", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Your mind breaks against what you saw.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(800))));
        p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f);
        world.spawnParticle(Particle.SOUL, at, 20, 0.4, 0.6, 0.4, 0.03, null, true);
        if (last == null) {
            maddened.add(p.getName());
            String name = p.getName();
            EventLogger.addLoggable(new GenericEventLoggable(name + " looked upon your true form and was struck "
                    + "blind and mad with terror.", name + " looked upon your true form"));
            Journal.add(Journal.Kind.INFO, name + " looked upon the true form and was maddened");
        }
    }

    private static List<PotionEffectType> terrorEffects() {
        return List.of(PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA,
                PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS);
    }

    // ---- Death messages and the smite's guard --------------------------------------------------

    /** Registers the listener the first time a form is shown. */
    private static void listen() {
        if (listening) return;
        listening = true;
        Bukkit.getPluginManager().registerEvents(new Deaths(), GPTGOD.get());
    }

    /**
     * Gives a death by the gaze its own message (the death itself is then logged once, by the usual
     * death logger at MONITOR, as "DEATH: X looked upon the true form of …"), and keeps smitten mortals
     * alive: a blow that would kill them while their guard lasts leaves them at half a heart instead.
     */
    static final class Deaths implements Listener {
        @EventHandler(priority = EventPriority.HIGH)
        public void onDeath(PlayerDeathEvent event) {
            String msg = DOOMED.remove(event.getPlayer().getUniqueId());
            if (msg != null) event.deathMessage(Component.text(msg));
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onDamage(EntityDamageEvent event) {
            if (!(event.getEntity() instanceof Player p)) return;
            Long until = GUARDED.get(p.getUniqueId());
            if (until == null) return;
            if (System.currentTimeMillis() > until) {
                GUARDED.remove(p.getUniqueId());
                return;
            }
            double health = p.getHealth() + p.getAbsorptionAmount();
            if (!TrueForm.guardNeeded(health, event.getFinalDamage())) return;
            event.setCancelled(true);
            p.setHealth(Math.max(0.5, TrueForm.guardedHealth(p.getHealth(), event.getFinalDamage())));
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static String describe(Location l) {
        return String.format(Locale.ROOT, "(%d, %d, %d in %s)", l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                l.getWorld().getName());
    }
}
