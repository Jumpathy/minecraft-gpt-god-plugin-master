package net.bigyous.gptgodmc.god;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** Shows each god's leading mission as a boss bar to the mortals in its worlds. Server thread only. */
public final class PurposeBar {
    private PurposeBar() {
    }

    private static final Map<String, BossBar> BARS = new HashMap<>();
    private static final Map<String, Set<UUID>> SHOWN = new HashMap<>();
    private static final Map<String, Long> LAST = new HashMap<>();

    /** Called from the heartbeat; refreshes at most every two seconds. */
    public static void tick(Realm r, long now) {
        Long last = LAST.get(r.id);
        if (last != null && now - last < 2000) return;
        LAST.put(r.id, now);
        Purpose.Goal g = GodSettings.purposeEnabled() && GodSettings.purposeBar() ? Purpose.banner(r.memory()) : null;
        Set<UUID> shown = SHOWN.computeIfAbsent(r.id, k -> new HashSet<>());
        if (g == null) {
            BossBar bar = BARS.get(r.id);
            if (bar != null) {
                for (UUID id : shown) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.hideBossBar(bar);
                }
            }
            shown.clear();
            return;
        }
        Persona persona = Persona.current();
        String name = persona == null ? "The god" : persona.shortName();
        BossBar bar = BARS.computeIfAbsent(r.id, k -> BossBar.bossBar(Component.empty(), 0f, BossBar.Color.PURPLE,
                BossBar.Overlay.NOTCHED_10));
        bar.name(Component.text("✦ " + name + " wills: " + g.title + " — " + g.progress + "%",
                TextColor.color(0xD9A6FF)));
        bar.progress(Math.max(0f, Math.min(1f, g.progress / 100f)));
        Set<UUID> now2 = new HashSet<>();
        for (Player p : r.players()) {
            // Players with the client mod see the mission in the mod's HUD instead of the boss bar.
            if (net.bigyous.gptgodmc.client.ModLink.has(p, net.bigyous.gptgodmc.client.ModSessions.HUD)) continue;
            now2.add(p.getUniqueId());
            if (!shown.contains(p.getUniqueId())) p.showBossBar(bar);
        }
        for (UUID id : shown) {
            if (!now2.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.hideBossBar(bar);
            }
        }
        shown.clear();
        shown.addAll(now2);
    }
}
