package net.bigyous.gptgodmc.god.tools;

import java.util.List;
import java.util.Locale;

import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.vessel.VesselLook;

/** The god changes the look of its own body. */
final class LookTools {
    private LookTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("vessel_look",
                "Change how your body looks (it reshapes at once and keeps the look). Looks: "
                        + String.join(", ", VesselLook.NAMES)
                        + "; auto = whatever suits your name and nature. Change it rarely and for a reason: a "
                        + "new era, a mood that lasts, a guise for a purpose.",
                Schema.object().str("look", "One of the looks listed, or auto.", true),
                a -> {
                    String look = a.str("look").trim().toLowerCase(Locale.ROOT);
                    if (!look.equals("auto") && !VesselLook.NAMES.contains(look)) {
                        throw new ToolException("No look '" + look + "'. Choose one of: "
                                + String.join(", ", VesselLook.NAMES) + ", or auto.");
                    }
                    Realm r = Realm.current();
                    if (r.look != null) {
                        throw new ToolException("The server keeper fixed your look in config.yml; you cannot change it.");
                    }
                    r.profile().look = look.equals("auto") ? null : look;
                    r.profile().save();
                    God.vessel().restyle();
                    return ToolResult.ok("Your body now wears the %s look%s.", look,
                            look.equals("auto") ? " (whatever suits your nature)"
                                    : ": " + VesselLook.description(look));
                }));
    }
}
