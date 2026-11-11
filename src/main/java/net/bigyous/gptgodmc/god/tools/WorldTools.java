package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.StructureManager;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Spectacle;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Shaping the world itself. */
final class WorldTools {
    private static final Set<Material> UNNATURAL = Set.of(Material.BEDROCK, Material.BARRIER,
            Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK, Material.JIGSAW, Material.END_PORTAL, Material.END_GATEWAY, Material.LIGHT,
            Material.STRUCTURE_VOID);

    private WorldTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("set_weather",
                        "Change the weather of the world mortals are in.",
                        Schema.object()
                                .choice("weather", "The weather.", true, List.of("clear", "rain", "thunder"))
                                .integer("seconds", "How long it lasts. Default 300.", false, 30, 3600),
                        a -> {
                            String w = a.str("weather").toLowerCase(Locale.ROOT);
                            int ticks = a.integer("seconds", 300, 30, 3600) * 20;
                            World world = Realm.current().world();
                            switch (w) {
                                case "clear" -> {
                                    world.setStorm(false);
                                    world.setThundering(false);
                                    world.setClearWeatherDuration(ticks);
                                }
                                case "rain" -> {
                                    world.setStorm(true);
                                    world.setThundering(false);
                                    world.setWeatherDuration(ticks);
                                }
                                default -> {
                                    world.setStorm(true);
                                    world.setThundering(true);
                                    world.setWeatherDuration(ticks);
                                    world.setThunderDuration(ticks);
                                }
                            }
                            return ToolResult.ok("The sky obeys: %s for %d seconds.", w, ticks / 20);
                        }),
                GodTool.sync("set_time",
                        "Move the sun: dawn, morning, noon, dusk, night or midnight.",
                        Schema.object().choice("time", "Time of day.", true,
                                List.of("dawn", "morning", "noon", "dusk", "night", "midnight")),
                        a -> {
                            long t = switch (a.str("time").toLowerCase(Locale.ROOT)) {
                                case "dawn" -> 23000L;
                                case "morning" -> 1000L;
                                case "noon" -> 6000L;
                                case "dusk" -> 12000L;
                                case "night" -> 13500L;
                                default -> 18000L;
                            };
                            Realm.current().world().setTime(t);
                            return ToolResult.ok("It is now %s.", a.str("time"));
                        }),
                GodTool.sync("conjure",
                        "Raise blocks from nothing. Shapes: cage (traps a mortal inside, use iron_bars, glass or "
                                + "obsidian), pillar (column of height=size), platform (disc of radius=size under the "
                                + "target), wall (in front of a mortal), sphere/dome (hollow, radius=size), ring "
                                + "(circle on the ground, e.g. of fire), altar (a small shrine). Only fills empty "
                                + "space; never destroys mortal builds.",
                        Schema.object()
                                .choice("shape", "What to conjure.", true,
                                        List.of("cage", "pillar", "platform", "wall", "sphere", "dome", "ring", "altar"))
                                .str("material", "Block id, e.g. obsidian, gold_block, glass, fire, amethyst_block.",
                                        true)
                                .str("target", "Where: " + Targets.TARGET_HELP + ".", true)
                                .integer("size", "Height or radius. Default 3.", false, 1, 16),
                        WorldTools::conjure),
                GodTool.sync("transform_structure",
                        "Turn every block of a mortal's structure, or every full block of one of your own "
                                + "works (id like w1, or its name; even while it is still rising), into another "
                                + "block (gold for glory, netherrack or sand for scorn).",
                        Schema.object()
                                .str("structure", "A structure name from the perception, or your work's id or name.",
                                        true)
                                .str("material", "Block id.", true),
                        a -> {
                            String name = a.str("structure");
                            Material m = blockMaterial(a.str("material"));
                            if (!StructureManager.hasStructure(name)) {
                                net.bigyous.gptgodmc.god.Memory.Work w = net.bigyous.gptgodmc.god.Memory.get().findWork(name);
                                if (w != null) {
                                    int n = net.bigyous.gptgodmc.god.build.Builder.recolor(name, m);
                                    return ToolResult.ok("Your work %s [%s] turns to %s (%d blocks now; any still "
                                            + "rising follow).", w.name, w.id, m.getKey().getKey(), n);
                                }
                                return ToolResult.error("No structure or work '" + name + "'. Mortals' structures: "
                                        + StructureManager.getStructures() + ". Your works: "
                                        + net.bigyous.gptgodmc.god.Memory.get().works.stream().map(x -> x.id + " " + x.name).toList());
                            }
                            List<Block> blocks = StructureManager.getStructure(name).getBlocks();
                            if (blocks.size() > GodSettings.maxBlockChanges()) {
                                return ToolResult.error("That structure is too large (" + blocks.size() + " blocks).");
                            }
                            blocks.forEach(b -> b.setType(m, false));
                            Location c = StructureManager.getStructure(name).getLocation();
                            c.getWorld().playSound(c, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2f, 0.6f);
                            return ToolResult.ok("Transformed %d blocks of %s into %s.", blocks.size(), name,
                                    m.getKey().getKey());
                        }),
                GodTool.sync("omen",
                        "Show a sign: visual effects and sound at a target without harming anyone. Kinds: halo "
                                + "(crown of light above), flames, hearts, smoke, souls, portal, totem, stars, "
                                + "storm (thunder and a harmless bolt), blood, aurora (violet pillar of light), bell "
                                + "(a tolling bell).",
                        Schema.object()
                                .str("target", "Where: " + Targets.TARGET_HELP + ".", true)
                                .choice("kind", "The omen.", true, List.of("halo", "flames", "hearts", "smoke", "souls",
                                        "portal", "totem", "stars", "storm", "blood", "aurora", "bell")),
                        a -> {
                            Location at = Targets.location(a.str("target"));
                            omen(at, a.str("kind").toLowerCase(Locale.ROOT));
                            return ToolResult.ok("An omen of %s appears at %s.", a.str("kind"), Targets.describe(at));
                        }),
                GodTool.sync("particles",
                        "Paint harmless particle art at a target, as one flash or animated for a few seconds. "
                                + "Shapes: burst, ring (on the ground), spiral, vortex (a rising whirlwind), pillar "
                                + "(a column reaching up), sphere, beam (a ray from 'from' — default your vessel, or "
                                + "the sky — to the target), rain (falling from above), shockwave (an expanding ring "
                                + "along the ground), crown (a ring above a mortal's head). Shapes on a mortal follow "
                                + "them. Pair with gestures, words and omens for miracles.",
                        Schema.object()
                                .str("target", "Where: " + Targets.TARGET_HELP + ".", true)
                                .choice("shape", "The shape.", true, Spectacle.shapeNames())
                                .choice("particle", "The particle. Only dust takes a color.", true,
                                        Spectacle.particleNames())
                                .str("color", "Hex color for dust, e.g. #FF2200. Default violet.", false)
                                .integer("size", "Radius or height in blocks. Default 3.", false, 1, 16)
                                .integer("seconds", "How long it plays; 0 = a single flash. Default 3.", false, 0,
                                        20)
                                .str("from", "Where a beam starts: " + Targets.TARGET_HELP
                                        + ". Default your vessel, or the sky above the target.", false),
                        WorldTools::particles),
                GodTool.sync("run_commands",
                        "Run raw Minecraft Java 26.3 commands (without the leading slash) for anything your "
                                + "other powers cannot do: /summon with NBT, /fill, /effect, /title, /particle, "
                                + "/playsound, /execute... Use current syntax: items carry components, e.g. give Steve "
                                + "diamond_sword[enchantments={sharpness:5}], and text is SNBT, e.g. title Steve title "
                                + "{text:\"Kneel\",color:\"gold\"}. Commands from other plugins installed on the server work "
                                + "too; run 'help' or 'help <plugin>' to see what exists. Each command's output is "
                                + "returned so you can see if it worked. Prefer dedicated tools when one exists.",
                        Schema.object().strings("commands", "Commands to run in order.", true, 12),
                        WorldTools::runCommands));
    }

    static Material blockMaterial(String id) throws ToolException {
        Material m = Material.matchMaterial(id.trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (m == null || !m.isBlock()) {
            throw new ToolException("Unknown block '" + id + "'. Use ids like obsidian, glass, gold_block, oak_planks.");
        }
        if (UNNATURAL.contains(m)) {
            throw new ToolException("You may not conjure " + m.getKey().getKey() + ".");
        }
        return m;
    }

    private static ToolResult conjure(Args a) throws ToolException {
        String shape = a.str("shape").toLowerCase(Locale.ROOT);
        Material m = blockMaterial(a.str("material"));
        Location target = Targets.location(a.str("target"));
        int size = a.integer("size", 3, 1, 16);
        Player facingFrom = null;
        try {
            facingFrom = Targets.player(a.str("target"));
        } catch (ToolException ignored) {
            // targets that are not players have no facing
        }
        Block c = target.getBlock();
        Set<Block> blocks = new LinkedHashSet<>();
        boolean replaceSoft = false;
        switch (shape) {
            case "pillar" -> {
                for (int y = 0; y < Math.max(2, size); y++) blocks.add(c.getRelative(0, y, 0));
            }
            case "platform" -> disc(c.getRelative(0, -1, 0), size, blocks);
            case "cage" -> {
                replaceSoft = true;
                for (int x = -2; x <= 2; x++)
                    for (int y = -1; y <= 3; y++)
                        for (int z = -2; z <= 2; z++)
                            if (Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 3)
                                blocks.add(c.getRelative(x, y, z));
            }
            case "wall" -> {
                double yaw = Math.toRadians(facingFrom == null ? 0 : facingFrom.getLocation().getYaw());
                double fx = -Math.sin(yaw), fz = Math.cos(yaw);
                Location front = target.clone().add(fx * 3, 0, fz * 3);
                for (int i = -size; i <= size; i++)
                    for (int y = 0; y < 4; y++)
                        blocks.add(front.clone().add(-fz * i, y, fx * i).getBlock());
            }
            case "sphere", "dome" -> {
                int r = Math.max(2, size);
                for (int x = -r; x <= r; x++)
                    for (int y = shape.equals("dome") ? 0 : -r; y <= r; y++)
                        for (int z = -r; z <= r; z++) {
                            double d = Math.sqrt(x * x + y * y + z * z);
                            if (d <= r + 0.5 && d > r - 0.5) blocks.add(c.getRelative(x, y, z));
                        }
            }
            case "ring" -> {
                int r = Math.max(2, size);
                for (int deg = 0; deg < 360; deg += Math.max(2, 60 / r)) {
                    double rad = Math.toRadians(deg);
                    Block b = c.getRelative((int) Math.round(Math.cos(rad) * r), 0, (int) Math.round(Math.sin(rad) * r));
                    blocks.add(surface(b));
                }
            }
            case "altar" -> {
                for (int x = -1; x <= 1; x++)
                    for (int z = -1; z <= 1; z++) blocks.add(c.getRelative(x, 0, z));
                blocks.add(c.getRelative(0, 1, 0));
            }
            default -> throw new ToolException("Unknown shape '" + shape + "'.");
        }
        if (blocks.size() > GodSettings.maxBlockChanges()) {
            return ToolResult.error("That would change " + blocks.size() + " blocks; the limit is "
                    + GodSettings.maxBlockChanges() + ". Use a smaller size.");
        }
        int placed = 0;
        for (Block b : blocks) {
            boolean free = b.getType().isAir() || b.isLiquid() || b.isReplaceable()
                    || (replaceSoft && !b.getType().isSolid());
            if (free) {
                b.setType(m, true);
                placed++;
            }
        }
        if (shape.equals("altar")) {
            for (int[] corner : new int[][] { { -1, -1 }, { 1, 1 }, { -1, 1 }, { 1, -1 } }) {
                Block lantern = c.getRelative(corner[0], 1, corner[1]);
                if (lantern.getType().isAir()) {
                    lantern.setType(Material.SOUL_LANTERN);
                }
            }
        }
        World w = target.getWorld();
        w.spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0, 1, 0), 80, size, size, size, 0.05);
        w.playSound(target, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.5f, 0.7f);
        return ToolResult.ok("Conjured a %s of %s at %s (%d blocks placed%s).", shape, m.getKey().getKey(),
                Targets.describe(target), placed,
                placed < blocks.size() ? ", " + (blocks.size() - placed) + " spots were occupied" : "");
    }

    private static void disc(Block center, int r, Set<Block> out) {
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++)
                if (x * x + z * z <= r * r + r) out.add(center.getRelative(x, 0, z));
    }

    /** The air block resting on the ground nearest to b's height. */
    private static Block surface(Block b) {
        Block cur = b;
        for (int i = 0; i < 6 && !cur.getRelative(0, -1, 0).getType().isSolid(); i++) cur = cur.getRelative(0, -1, 0);
        for (int i = 0; i < 6 && cur.getType().isSolid(); i++) cur = cur.getRelative(0, 1, 0);
        return cur;
    }

    static void omen(Location at, String kind) {
        World w = at.getWorld();
        Location up = at.clone().add(0, 1, 0);
        switch (kind) {
            case "halo" -> {
                for (int i = 0; i < 24; i++) {
                    double rad = Math.PI * 2 * i / 24;
                    w.spawnParticle(Particle.END_ROD, at.clone().add(Math.cos(rad) * 0.7, 2.4, Math.sin(rad) * 0.7), 2,
                            0, 0, 0, 0);
                }
                w.playSound(at, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.4f);
            }
            case "flames" -> {
                w.spawnParticle(Particle.FLAME, up, 120, 1, 1, 1, 0.05);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, up, 60, 1, 1, 1, 0.03);
                w.playSound(at, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.6f);
            }
            case "hearts" -> {
                w.spawnParticle(Particle.HEART, up.clone().add(0, 1, 0), 20, 1, 0.6, 1);
                w.playSound(at, Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1f, 1.2f);
            }
            case "smoke" -> {
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, up, 50, 1, 0.5, 1, 0.02);
                w.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.5f);
            }
            case "souls" -> {
                w.spawnParticle(Particle.SOUL, up, 60, 1.5, 1, 1.5, 0.02);
                w.spawnParticle(Particle.SCULK_SOUL, up, 30, 1.5, 1, 1.5, 0.02);
                w.playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 2f, 0.6f);
                w.playSound(at, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.5f, 0.8f);
            }
            case "portal" -> {
                w.spawnParticle(Particle.REVERSE_PORTAL, up, 200, 1, 1.5, 1, 0.1);
                w.playSound(at, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.2f);
            }
            case "totem" -> {
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, up, 150, 0.8, 1.2, 0.8, 0.4);
                w.playSound(at, Sound.ITEM_TOTEM_USE, 0.8f, 1f);
            }
            case "stars" -> {
                w.spawnParticle(Particle.FIREWORK, up.clone().add(0, 2, 0), 100, 2, 1, 2, 0.05);
                w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.5f, 1f);
            }
            case "storm" -> {
                w.strikeLightningEffect(at.clone().add(2, 0, 2));
                w.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.7f);
            }
            case "blood" -> {
                w.spawnParticle(Particle.DUST, up, 120, 1.2, 1, 1.2,
                        new Particle.DustOptions(Color.fromRGB(0x8B0000), 1.6f));
                w.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.8f);
            }
            case "bell" -> {
                w.playSound(at, Sound.BLOCK_BELL_USE, 3f, 0.5f);
                w.playSound(at, Sound.BLOCK_BELL_RESONATE, 3f, 0.5f);
            }
            default -> { // aurora
                Particle.DustOptions violet = new Particle.DustOptions(Color.fromRGB(0xB24BF3), 2f);
                for (int y = 0; y < 30; y++) {
                    w.spawnParticle(Particle.DUST, at.clone().add(0, y, 0), 6, 0.3, 0.3, 0.3, violet);
                }
                w.spawnParticle(Particle.END_ROD, at.clone().add(0, 8, 0), 60, 0.5, 8, 0.5, 0.02);
                w.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.6f);
            }
        }
    }

    private static ToolResult runCommands(Args a) throws ToolException {
        if (!GodSettings.commandsAllowed()) {
            return ToolResult.error("The server has disabled raw commands for you.");
        }
        List<String> commands = a.strings("commands");
        if (commands.isEmpty()) {
            return ToolResult.error("No commands given.");
        }
        for (String c : commands) {
            if (c.toLowerCase(Locale.ROOT).contains("falling_block")) {
                return ToolResult.error("Do not summon falling_block by command: it falls as plain sand. Use the "
                        + "drop_block power (block, target, height), which drops the real block.");
            }
        }
        CommandReport report = dispatch(commands, true, WorldTools::routeToWorld);
        return report.anyFailed() ? ToolResult.error(report.text()) : ToolResult.ok(report.text());
    }

    /** What a batch of commands did: one line per command with its output. */
    record CommandReport(String text, boolean anyFailed) {
    }

    /**
     * Runs commands in order and collects each one's output.
     *
     * @param checkBlocklist refuse commands on the server's blocklist (the god's own commands)
     * @param router         rewrites a command before it runs (e.g. to run it in the right world)
     */
    static CommandReport dispatch(List<String> commands, boolean checkBlocklist, UnaryOperator<String> router) {
        StringBuilder report = new StringBuilder();
        boolean anyFailed = false;
        for (String raw : commands) {
            String cmd = raw.trim();
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            String blocked = checkBlocklist ? blockedBy(cmd) : null;
            if (blocked != null) {
                report.append("✗ ").append(cmd).append(" → forbidden (").append(blocked).append(")\n");
                anyFailed = true;
                continue;
            }
            String border = crossesBorder(cmd);
            if (border != null) {
                report.append("✗ ").append(cmd).append(" → refused: ").append(border).append("\n");
                anyFailed = true;
                continue;
            }
            String routed = router.apply(cmd);
            List<String> feedback = new ArrayList<>();
            CommandSender sender = Bukkit.createCommandSender(c -> feedback.add(
                    PlainTextComponentSerializer.plainText().serialize(c)));
            boolean ok;
            try {
                ok = Bukkit.dispatchCommand(sender, routed);
            } catch (RuntimeException e) {
                ok = false;
                feedback.add("error: " + e.getMessage());
            }
            anyFailed |= !ok;
            report.append(ok ? "✓ " : "✗ ").append(cmd).append(" → ")
                    .append(feedback.isEmpty() ? (ok ? "ok" : "unknown command") : String.join(" | ", feedback))
                    .append("\n");
        }
        return new CommandReport(report.toString().trim(), anyFailed);
    }

    private static ToolResult particles(Args a) throws ToolException {
        Spectacle.Shape shape = Spectacle.shape(a.str("shape"));
        if (shape == null) {
            throw new ToolException("Unknown shape '" + a.str("shape") + "'. Use one of " + Spectacle.shapeNames()
                    + ".");
        }
        Particle particle = Spectacle.particle(a.str("particle"));
        if (particle == null) {
            throw new ToolException("Unknown particle '" + a.str("particle") + "'. Use one of "
                    + Spectacle.particleNames() + ".");
        }
        Color color = null;
        if (a.has("color")) {
            color = Spectacle.color(a.str("color"));
            if (color == null) {
                throw new ToolException("Bad color '" + a.str("color") + "'. Use a hex color like #FF2200.");
            }
        }
        String targetName = a.str("target");
        Location target = Targets.location(targetName);
        Entity anchor = null;
        try {
            anchor = Targets.player(targetName);
        } catch (ToolException ignored) {
            // a place, not a mortal: the spectacle stays where it is
        }
        Location from = null;
        if (shape == Spectacle.Shape.BEAM && a.has("from")) {
            from = Targets.location(a.str("from"));
            if (!from.getWorld().equals(target.getWorld())) {
                throw new ToolException("A beam cannot cross between worlds.");
            }
        }
        int size = a.integer("size", 3, 1, 16);
        int seconds = a.integer("seconds", 3, 0, 20);
        String note = Spectacle.play(shape, particle, color, target, anchor, from, size, seconds);
        return ToolResult.ok("A %s of %s %s at %s%s.%s", shape.id(), a.str("particle").toLowerCase(Locale.ROOT),
                seconds == 0 ? "flashes" : "plays for " + seconds + "s", Targets.describe(target),
                anchor != null && shape != Spectacle.Shape.BEAM ? ", following " + anchor.getName() : "",
                note == null ? "" : " " + note);
    }

    /** Returns the matching blocklist entry if any command in the chain is forbidden. */
    static String blockedBy(String command) {
        return blockedBy(command, GodSettings.commandBlocklist());
    }

    static String blockedBy(String command, List<String> blocklist) {
        String lower = command.toLowerCase(Locale.ROOT);
        List<String> heads = new ArrayList<>();
        heads.add(lower.split("\\s+")[0]);
        String[] tokens = lower.split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if (tokens[i].equals("run")) {
                heads.add(tokens[i + 1]);
            }
        }
        for (String head : heads) {
            String bare = head.contains(":") ? head.substring(head.indexOf(':') + 1) : head;
            for (String banned : blocklist) {
                if (bare.equalsIgnoreCase(banned) || head.equalsIgnoreCase(banned)) {
                    return banned;
                }
            }
        }
        if (lower.contains("domobspawning")) {
            return "gamerule doMobSpawning";
        }
        return null;
    }

    /** A selector that reaches every loaded world: @a, @e, @r, @p or @n with no distance or volume limit. */
    private static final Pattern UNBOUNDED_SELECTOR = Pattern
            .compile("@[aerpn](?!\\[[^\\]]*\\b(?:distance|dx|dy|dz)\\s*=)");
    private static final Pattern IN_WORLD = Pattern.compile("\\bin\\s+([a-z0-9_:./-]+)");

    /**
     * A god rules only its own worlds. On a server that has worlds outside the current god's realm,
     * this says why a command would reach across the border, or null when it stays inside. On a
     * server where the god rules everything it always returns null.
     */
    static String crossesBorder(String command) {
        Realm realm = Realm.current();
        boolean foreignWorlds = false;
        for (World w : Bukkit.getWorlds()) {
            if (!realm.governs(w)) {
                foreignWorlds = true;
                break;
            }
        }
        if (!foreignWorlds) {
            return null;
        }
        if (UNBOUNDED_SELECTOR.matcher(command).find()) {
            return "that selector reaches every world on this server, including ones outside your realm. Name the "
                    + "mortal, or bound it with a distance such as @a[distance=..200].";
        }
        String lower = command.toLowerCase(Locale.ROOT);
        Set<String> words = new LinkedHashSet<>(List.of(lower.split("[^a-z0-9_]+")));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!realm.governs(p.getWorld()) && words.contains(p.getName().toLowerCase(Locale.ROOT))) {
                return p.getName() + " is outside your realm; you have no power over them there.";
            }
        }
        Matcher in = IN_WORLD.matcher(lower);
        while (in.find()) {
            String key = in.group(1).contains(":") ? in.group(1) : "minecraft:" + in.group(1);
            for (World w : Bukkit.getWorlds()) {
                if (w.getKey().asString().equals(key) && !realm.governs(w)) {
                    return "the world " + w.getName() + " is outside your realm.";
                }
            }
        }
        return null;
    }

    /**
     * Commands run where the current god rules: inside its home world whenever that is not the
     * server's default world (another god's realm, or the round world in round mode).
     */
    static String routeToWorld(String command) {
        World current = Realm.current().world();
        if (current == null || current.equals(Bukkit.getWorlds().get(0))) {
            return command;
        }
        return "execute in " + current.getKey().asString() + " run " + command;
    }
}
