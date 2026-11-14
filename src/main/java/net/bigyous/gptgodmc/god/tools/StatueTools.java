package net.bigyous.gptgodmc.god.tools;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.AvatarWatch;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Sync;
import net.bigyous.gptgodmc.god.build.Builder;
import net.bigyous.gptgodmc.god.build.Plan;
import net.bigyous.gptgodmc.god.build.SkinStatue;

/**
 * The `statue` power: a statue of a mortal built from their own skin, pixel for pixel. The colours are
 * read by code from the skin the server already knows, so the likeness is exact and no model is asked.
 */
public final class StatueTools {
    private StatueTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.async("statue",
                "Raise a statue of a mortal who is here, copied exactly from their own skin: every block's colour is "
                        + "taken from the skin's pixels, so it looks like them without your having to imagine it. Use "
                        + "this, never a script, whenever a statue of a PLAYER (their skin, their character) is wanted. "
                        + "At scale 1 it is 16 wide, 32 high and 8 deep (about 1,100 blocks); scale 2 and 3 are two and "
                        + "three times that in every direction. It is one of your works (unbuild takes it back). For a "
                        + "likeness of anything else (a character from a story, a creature), write a script and use "
                        + "its preview.",
                Schema.object()
                        .str("player", "Whose statue: the name of a mortal who is online.", true)
                        .str("at", "Where its feet stand: " + Targets.TARGET_HELP + ". Default: a few blocks beside them.",
                                false)
                        .integer("scale", "1 (32 high, default), 2 (64 high) or 3 (96 high).", false, 1, 3)
                        .choice("facing", "Which way it looks. Default south.", false,
                                List.of("north", "south", "east", "west")),
                StatueTools::statue));
    }

    private record Asked(String name, String skin, Location at) {
    }

    private static ToolResult statue(Args a) throws ToolException {
        ScriptWorld.gate("build");
        int scale = a.integer("scale", 1, 1, 3);
        String facing = a.str("facing", "south").toLowerCase(Locale.ROOT);
        Asked asked = Sync.call(() -> {
            Player p = Targets.player(a.str("player"));
            Location at = a.has("at") ? Targets.location(a.str("at")).clone() : p.getLocation().clone().add(6 + 8 * scale, 0, 0);
            if (!a.has("at")) at.setY(at.getWorld().getHighestBlockYAt(at) + 1);
            return new Asked(p.getName(), AvatarWatch.skinUrl(p), at);
        }, 8000);
        if (asked.skin() == null) {
            throw new ToolException(asked.name() + " wears the game's default skin, which this server cannot read. "
                    + "Build their statue another way, or pick a mortal with a skin of their own.");
        }
        BufferedImage skin = AvatarWatch.image(asked.skin());
        if (skin == null) throw new ToolException("Could not read " + asked.name() + "'s skin just now. Try again in a moment.");
        List<SkinStatue.Voxel> voxels;
        try {
            voxels = SkinStatue.voxels(skin, scale);
        } catch (IllegalArgumentException e) {
            throw new ToolException(asked.name() + "'s skin is not one this power can read (" + e.getMessage() + ").");
        }
        Plan plan = new Plan();
        int half = 8 * scale, deep = 4 * scale;
        for (SkinStatue.Voxel v : voxels) {
            Material m = Material.matchMaterial(SkinStatue.block(v.rgb()));
            if (m != null) plan.set(v.x() - half, v.y(), v.z() - deep, m);
        }
        Plan turned = switch (facing) {
            case "south" -> plan.rotated(StructureRotation.CLOCKWISE_180);
            case "east" -> plan.rotated(StructureRotation.CLOCKWISE_90);
            case "west" -> plan.rotated(StructureRotation.COUNTERCLOCKWISE_90);
            default -> plan;
        };
        Memory.Work work = Sync.call(() -> Builder.build(turned, asked.at(), "Statue of " + asked.name(), "statue", false,
                null), 15_000);
        return ToolResult.ok("A statue of %s rises at %s, facing %s: %d blocks, %d high, copied from their own skin. "
                + "It is your work %s (unbuild %s takes it back). It is placed over the next moments.", asked.name(),
                Targets.describe(asked.at()), facing, turned.size(), 32 * scale, work.id, work.id);
    }
}
