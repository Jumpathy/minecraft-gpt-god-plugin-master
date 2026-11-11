package net.bigyous.gptgodmc.god.tools;

import java.util.List;

import net.bigyous.gptgodmc.god.vision.MapView;
import net.bigyous.gptgodmc.god.vision.Vision;

/** The god's minimap: where every mortal is, as a small picture. */
final class MapTools {
    private MapTools() {
    }

    static List<GodTool> all() {
        return List.of(map(), GodTool.async("see_build",
                "SEE one build as a model: only its own blocks, lifted out of the world and shown from four "
                        + "corners and from above in a single picture, with its size and what it is made of. For "
                        + "a mortal's structure, for what a mortal is building right now (give their name), or for "
                        + "one of your own works. Use it sparingly, when understanding a build truly matters: "
                        + "judging a temple raised for you or a quest to build something, admiring or mocking a "
                        + "house, checking your own work came out right. Not every thought, and not twice for a "
                        + "build that has not changed. It is a snapshot of how the build stood when you looked.",
                Schema.object().str("target", "Whose build: a mortal's name (what they are building now, else the "
                        + "nearest thing they built), a structure name, or the id or name of one of your works.",
                        true),
                a -> {
                    Vision.Result r = net.bigyous.gptgodmc.god.vision.BuildView.render(a.str("target"));
                    return ToolResult.image(r.caption(), r.png());
                }));
    }

    private static GodTool map() {
        return GodTool.async("map",
                "A MINIMAP: a small top-down picture of where mortals are, each drawn as a dart pointing where they "
                        + "face with their name and x y z, plus hostile creatures near them, vessels, structures "
                        + "and your works, a scale bar and north. It is cut at the mortal's own level: in a cave "
                        + "or indoors you see the cave or room they are in, not the ground above them. Mortals far "
                        + "apart or on different levels get a panel each. Use it to know where mortals stand "
                        + "relative to each other, to danger and to your works: before you build, smite or send "
                        + "your vessel near them, or when someone speaks of 'here' or 'there'. Smaller than look. "
                        + "It is a snapshot: mortals keep moving while you think, so prefer a mortal's name over "
                        + "coordinates as a target, or map again before acting on a position.",
                Schema.object()
                        .str("target", "Who or where: 'all' (every mortal; the default), a mortal's name, several "
                                + "names separated by commas, a structure, 'vessel', or coordinates \"x y z\".",
                                false)
                        .integer("radius", "Blocks from the centre to the edge (12-96). Default 32; with several "
                                + "mortals it grows to fit them.", false, 12, 96),
                a -> {
                    Vision.Result r = MapView.render(a.str("target", "all"), a.integer("radius", 0, 0, 96));
                    return ToolResult.image(r.caption(), r.png());
                });
    }
}
