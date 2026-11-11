package net.bigyous.gptgodmc.god;

import net.bigyous.gptgodmc.god.vessel.Vessel;

/** Read-only views of a realm's package-private state for god/living (rivalries, holy days, servants). */
public final class LivingAccess {
    private LivingAccess() {
    }

    public static boolean awake(Realm r) {
        return r.awake;
    }

    public static boolean paused(Realm r) {
        return r.paused;
    }

    /** Its vessel if one was ever made (never creates one), else null. */
    public static Vessel vessel(Realm r) {
        return r.vesselIfCreated();
    }

    /** Its body's location when it walks the world, else null. */
    public static org.bukkit.Location body(Realm r) {
        Vessel v = r.vesselIfCreated();
        return v != null && v.isManifested() ? v.location() : null;
    }
}
