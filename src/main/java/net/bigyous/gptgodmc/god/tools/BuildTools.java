package net.bigyous.gptgodmc.god.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.structure.Structure;

import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.build.Blueprints;
import net.bigyous.gptgodmc.god.build.Builder;
import net.bigyous.gptgodmc.god.build.Designs;
import net.bigyous.gptgodmc.god.build.Plan;
import net.bigyous.gptgodmc.god.build.Style;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Raising (and unmaking) buildings. */
final class BuildTools {
    private BuildTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("blueprints",
                        "List what you can build: your own designs (temple, shrine, cottage, prison, tower, obelisk, "
                                + "arena, statue, town), whole vanilla structures (villages, desert/jungle temples, "
                                + "igloo, witch hut, outpost, mansion, ancient city...), single vanilla pieces "
                                + "('templates', e.g. one village house — search them by keyword), and schematics "
                                + "the server owner added.",
                        Schema.object()
                                .choice("category", "Which list. Default: everything except templates.", false,
                                        List.of("designs", "vanilla", "templates", "schematics", "all"))
                                .str("search", "Keywords to filter templates, e.g. 'desert house' or 'temple'.", false),
                        BuildTools::blueprints),
                GodTool.sync("build",
                        "Build something beautiful (or terrible). blueprint is a design name (e.g. 'temple', "
                                + "'prison'), 'vanilla:<structure>' (e.g. vanilla:village_plains), "
                                + "'template:<path>' (from blueprints search) or 'schematic:<name>'. Built beside the "
                                + "target and facing it (a prison or arena is built around the target). The ground "
                                + "is cleared and leveled; it will not build over mortals' structures, and a design "
                                + "whose spot already holds something built moves a little aside to spare it (the "
                                + "result says so).",
                        Schema.object()
                                .str("blueprint", "What to build.", true)
                                .str("at", "Where: " + Targets.TARGET_HELP + ".", true)
                                .choice("style", "Materials for designs: violet, divine, dark, rustic, stone, or auto "
                                        + "(suits your personality). Default auto.", false,
                                        List.of("auto", "violet", "divine", "dark", "rustic", "stone"))
                                .choice("facing", "Which way the front faces. Default: toward the target mortal.",
                                        false, List.of("toward", "north", "south", "east", "west"))
                                .str("name", "A name for this work, e.g. 'Temple of the Violet Hour'.", false)
                                .str("material", "Optional: build every full block of it in this one block, e.g. "
                                        + "diamond_block for a diamond mansion (stairs, slabs, glass and doors keep "
                                        + "theirs).", false)
                                .bool("over", "true = raise a design exactly on the spot even if something built "
                                        + "already stands there and will be erased. Default false.", false),
                        BuildTools::build),
                GodTool.sync("unbuild",
                        "Unmake one of your works, restoring what was there before.",
                        Schema.object().str("work", "Work id (like w2) or its name.", true),
                        a -> ToolResult.ok(Builder.undo(a.str("work")))));
    }

    private static ToolResult blueprints(Args a) {
        String category = a.str("category", "default").toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        boolean all = category.equals("all") || category.equals("default");
        if (all || category.equals("designs")) {
            sb.append("Designs (any style):\n");
            Designs.ALL.values().forEach(d -> sb.append("- ").append(d.id()).append(": ").append(d.description())
                    .append("\n"));
        }
        if (all || category.equals("vanilla")) {
            sb.append("Vanilla structures (use vanilla:<id>):\n");
            Blueprints.VANILLA.forEach((id, desc) -> sb.append("- ").append(id).append(": ").append(desc).append("\n"));
        }
        if (all || category.equals("schematics")) {
            Map<String, String> schems = Blueprints.schematics();
            sb.append("Schematics (use schematic:<name>):\n");
            if (schems.isEmpty()) sb.append("(none installed)\n");
            schems.forEach((id, desc) -> sb.append("- ").append(id).append(": ").append(desc).append("\n"));
        }
        if (category.equals("templates") || category.equals("all") || a.has("search")) {
            List<String> hits = Blueprints.searchTemplates(a.str("search", ""), 40);
            sb.append("Templates (use template:<path>)").append(a.has("search") ? " matching '" + a.str("search", "")
                    + "'" : "").append(":\n");
            hits.forEach(t -> sb.append("- ").append(t).append("\n"));
            if (hits.isEmpty()) sb.append("(no matches)\n");
        } else {
            sb.append("Templates: ").append(Blueprints.templates().size())
                    .append(" single vanilla pieces; search them with category='templates' and a keyword.\n");
        }
        return ToolResult.ok(sb.toString().trim());
    }

    private static ToolResult build(Args a) throws ToolException {
        int before = Memory.get().workCounter;
        ToolResult r = buildPlain(a);
        String mat = a.str("material", null);
        if (mat == null || mat.isBlank() || r.isError() || Memory.get().workCounter == before) return r;
        Memory.Work w = Memory.get().works.get(Memory.get().works.size() - 1);
        Material m = Material.matchMaterial(mat.trim());
        if (m == null || !m.isBlock()) {
            return ToolResult.ok(r.text() + " (Unknown block '" + mat + "', so it keeps its style.)");
        }
        try {
            net.bigyous.gptgodmc.god.build.Builder.recolor(w.id, m);
            return ToolResult.ok(r.text() + " Every full block of it is " + m.getKey().getKey() + ".");
        } catch (ToolException e) {
            return ToolResult.ok(r.text() + " (" + e.getMessage() + ")");
        }
    }

    private static ToolResult buildPlain(Args a) throws ToolException {
        String blueprint = a.str("blueprint").trim();
        String lower = blueprint.toLowerCase(Locale.ROOT);
        String target = a.str("at");
        Location targetLoc = Targets.location(target);
        Player targetPlayer = null;
        try {
            targetPlayer = Targets.player(target);
        } catch (ToolException ignored) {
            // not a player
        }
        String name = a.str("name", null);

        if (lower.startsWith("vanilla:") || Blueprints.VANILLA.containsKey(lower)) {
            String id = lower.startsWith("vanilla:") ? lower.substring(8) : lower;
            Location at = beside(targetLoc, targetPlayer, 24);
            return vanilla(id, at, name);
        }
        if (lower.startsWith("template:")) {
            String id = blueprint.substring(9).trim();
            Structure s = Bukkit.getStructureManager().loadStructure(NamespacedKey.minecraft(id));
            if (s == null) {
                return ToolResult.error("No vanilla template '" + id + "'. Use blueprints with category=templates.");
            }
            int depth = Math.max(s.getSize().getBlockX(), s.getSize().getBlockZ());
            Location at = beside(targetLoc, targetPlayer, depth / 2 + 3);
            StructureRotation rot = rotation(a.str("facing", "toward"), at, targetLoc);
            Memory.Work w = Builder.placeStructure(s, at, rot, name, "template:" + id);
            return ToolResult.ok("Raised %s [%s] (%s) at %s.", w.name, w.id, w.size, Targets.describe(at));
        }
        if (lower.startsWith("schematic:") || Blueprints.schematics().containsKey(blueprint)) {
            String id = lower.startsWith("schematic:") ? blueprint.substring(10).trim() : blueprint;
            File file = Blueprints.schematicFile(id);
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".nbt")) {
                Structure s;
                try {
                    s = Bukkit.getStructureManager().loadStructure(file);
                } catch (IOException e) {
                    return ToolResult.error("Could not read " + file.getName() + ": " + e.getMessage());
                }
                int depth = Math.max(s.getSize().getBlockX(), s.getSize().getBlockZ());
                Location at = beside(targetLoc, targetPlayer, depth / 2 + 3);
                Memory.Work w = Builder.placeStructure(s, at, rotation(a.str("facing", "toward"), at, targetLoc),
                        name, "schematic:" + id);
                return ToolResult.ok("Raised %s [%s] (%s) at %s.", w.name, w.id, w.size, Targets.describe(at));
            }
            Plan plan = Blueprints.loadSponge(file);
            Location at = beside(targetLoc, targetPlayer, Math.max(plan.width(), plan.depth()) / 2 + 3);
            Plan rotated = plan.rotated(rotation(a.str("facing", "toward"), at, targetLoc));
            Memory.Work w = Builder.build(rotated, at, name, "schematic:" + id, true, Material.DIRT);
            return ToolResult.ok("Building %s [%s] (%s, %d blocks) at %s; it rises over the next moments.", w.name,
                    w.id, w.size, w.blocks, Targets.describe(at));
        }

        Designs.Design design = Designs.ALL.get(lower);
        if (design == null) {
            return ToolResult.error("Unknown blueprint '" + blueprint + "'. Designs: " + Designs.ALL.keySet()
                    + "; or use vanilla:<id>, template:<path>, schematic:<name>. Call blueprints to see options.");
        }
        Style style = Style.resolve(a.str("style", "auto"));
        Plan plan = design.generator().apply(style);
        Location at;
        StructureRotation rot;
        if (design.centerOnTarget()) {
            at = targetPlayer != null ? targetPlayer.getLocation().getBlock().getLocation() : ground(targetLoc);
            rot = rotation(a.str("facing", "south"), at, targetLoc);
        } else {
            at = beside(targetLoc, targetPlayer, Math.max(plan.width(), plan.depth()) / 2 + 3);
            rot = rotation(a.str("facing", "toward"), at, targetLoc);
        }
        Plan placed = plan.rotated(rot);
        String moved = "";
        if (!design.centerOnTarget() && !a.bool("over", false)) {
            // Much of what mortals make is on no record: do not raise this on top of it.
            net.bigyous.gptgodmc.god.build.Site.Choice site = net.bigyous.gptgodmc.god.build.Site.settle(placed, at,
                    targetLoc, BuildTools::ground);
            at = site.at();
            moved = site.note();
        }
        Memory.Work w = Builder.build(placed, at, name, design.id() + " (" + style.id() + ")",
                !design.id().equals("prison"), style.base());
        return ToolResult.ok("Building %s [%s] (%s, %d blocks, %s style) at %s; it rises over the next moments.%s",
                w.name, w.id, w.size, w.blocks, style.id(), Targets.describe(at), moved);
    }

    private static ToolResult vanilla(String id, Location at, String name) {
        if (!Blueprints.VANILLA.containsKey(id)) {
            return ToolResult.error("Unknown vanilla structure '" + id + "'. Known: " + Blueprints.VANILLA.keySet());
        }
        List<String> feedback = new ArrayList<>();
        CommandSender sender = Bukkit.createCommandSender(c -> feedback.add(
                PlainTextComponentSerializer.plainText().serialize(c)));
        String cmd = String.format("execute in %s run place structure minecraft:%s %d %d %d",
                at.getWorld().getKey().asString(), id, at.getBlockX(), at.getBlockY(), at.getBlockZ());
        boolean ok = Bukkit.dispatchCommand(sender, cmd);
        String out = String.join(" | ", feedback);
        if (!ok || out.toLowerCase(Locale.ROOT).contains("fail") || out.toLowerCase(Locale.ROOT).contains("error")) {
            return ToolResult.error("The world refused: " + (out.isBlank() ? "unknown error" : out)
                    + ". Some structures need a fitting place (e.g. monument in deep ocean, bastion in the Nether).");
        }
        Memory.Work w = Memory.get().addWork(name, "vanilla:" + id, at.getWorld().getName(), at.getBlockX(),
                at.getBlockY(), at.getBlockZ(), 0, "vanilla-sized");
        return ToolResult.ok("Raised %s [%s] at %s. %s (vanilla structures cannot be unbuilt)", w.name, w.id,
                Targets.describe(at), out);
    }

    /** For a player target, a spot in front of them; otherwise the target itself. Snapped to the ground. */
    private static Location beside(Location target, Player player, int distance) {
        if (player == null) {
            return ground(target);
        }
        double yaw = Math.toRadians(player.getLocation().getYaw());
        Location front = target.clone().add(-Math.sin(yaw) * distance, 0, Math.cos(yaw) * distance);
        boolean underground = player.getLocation().getBlock().getLightFromSky() < 4;
        if (underground) {
            front.setY(target.getBlockY());
            return front.getBlock().getLocation();
        }
        return ground(front);
    }

    private static Location ground(Location l) {
        int top = l.getWorld().getHighestBlockYAt(l.getBlockX(), l.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Location out = l.getBlock().getLocation();
        if (Math.abs(top + 1 - l.getY()) < 24) {
            out.setY(top + 1);
        }
        return out;
    }

    /** Rotation so the build's front (+z) faces the requested way. */
    private static StructureRotation rotation(String facing, Location at, Location toward) {
        String f = facing.toLowerCase(Locale.ROOT);
        if (f.equals("toward")) {
            double dx = toward.getX() - at.getX();
            double dz = toward.getZ() - at.getZ();
            if (Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5) {
                f = "south";
            } else if (Math.abs(dz) >= Math.abs(dx)) {
                f = dz > 0 ? "south" : "north";
            } else {
                f = dx > 0 ? "east" : "west";
            }
        }
        return switch (f) {
            case "north" -> StructureRotation.CLOCKWISE_180;
            case "east" -> StructureRotation.COUNTERCLOCKWISE_90;
            case "west" -> StructureRotation.CLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
    }
}
