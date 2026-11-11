package net.bigyous.gptgodmc.god;

import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.client.ModMessages;
import net.bigyous.gptgodmc.client.ModMessages.GodView;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.god.vessel.VesselMood;

/** What the client mod is told about a god and a player's standing with it. Server thread only. */
public final class ClientViews {
    private ClientViews() {
    }

    public static GodView god(Realm r, Player viewer) {
        long now = System.currentTimeMillis();
        Persona p = r.persona;
        Vessel v = r.vesselIfCreated();
        Heart heart = r.memory().heart;
        boolean body = v != null && v.hasBody();
        VesselMood mood = body ? v.mood() : heart.mood(now);
        TabList.State state = TabList.stateOf(r, p);
        GodBrain brain = r.brainIfStarted();
        Double health = null;
        Double max = null;
        Mortality m = r.memory().mortality;
        if (m.hurt() || m.isSlain()) {
            max = GodSettings.godslayerHealth();
            health = m.health(max);
        }
        double[] pos = null;
        Location at = body ? v.location() : null;
        if (at != null && viewer != null && at.getWorld() != null && at.getWorld().equals(viewer.getWorld())) {
            pos = new double[] { at.getX(), at.getY(), at.getZ() };
        }
        String archetype = p == null ? null : p.archetype;
        return new GodView(r.id, p == null ? null : p.shortName(), p == null ? null : p.epithet, archetype,
                TabList.nameColor(archetype, mood).value(), mood.name().toLowerCase(Locale.ROOT),
                heart.level(Heart.Feeling.WRATH), heart.level(Heart.Feeling.JOY), heart.level(Heart.Feeling.SORROW),
                heart.level(Heart.Feeling.DREAD), heart.level(Heart.Feeling.GLORY),
                state.name().toLowerCase(Locale.ROOT), brain != null && brain.isThinking(), health, max, null, pos,
                viewer != null && r.governs(viewer.getWorld()));
    }

    /** The viewer's favor tier, title and mark with this god (never the favor number). */
    public static com.google.gson.JsonObject standing(Realm r, Player viewer) {
        Memory.Subject s = r.memory().subjectIfAny(viewer.getName());
        int favor = s == null ? 0 : r.memory().favorOf(viewer.getName());
        String mark = s != null && s.mark != null && s.markUntil > System.currentTimeMillis() ? s.mark : null;
        return ModMessages.standing(r.id, Memory.favorLabel(favor), s == null ? null : s.title,
                s == null ? null : s.titleColor, mark);
    }

    /** The god's announced mission, as the boss bar shows it. */
    public static com.google.gson.JsonObject quest(Realm r) {
        Purpose.Goal g = GodSettings.purposeEnabled() && GodSettings.purposeBar() ? Purpose.banner(r.memory()) : null;
        Persona p = r.persona;
        return g == null ? ModMessages.quest(r.id, null, null, 0)
                : ModMessages.quest(r.id, p == null ? null : p.shortName(), g.title, g.progress);
    }
}
