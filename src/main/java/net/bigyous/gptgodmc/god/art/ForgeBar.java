package net.bigyous.gptgodmc.god.art;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Realm;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/**
 * The bar mortals see while a god forges a relic. The artist takes a minute or more (it paints, looks at
 * its own work and paints again), and a silent wait reads as a stuck server: the bar names the god and the
 * relic, says which stage the work is at, and fills as attempts are spent. Any thread; it shows and moves
 * on the server thread.
 */
public final class ForgeBar {
    private final BossBar bar;
    private final World world;
    private final String smith, relic;
    private volatile boolean closed;

    private ForgeBar(World world, String smith, String relic) {
        this.world = world;
        this.smith = smith;
        this.relic = relic;
        this.bar = BossBar.bossBar(Component.text(words(smith, relic, 0, 1)), 0.04f, BossBar.Color.PURPLE,
                BossBar.Overlay.PROGRESS);
    }

    public static ForgeBar open(Realm realm, String smith, String relic) {
        World w;
        try {
            w = realm.world();
        } catch (RuntimeException e) {
            w = null;
        }
        ForgeBar f = new ForgeBar(w, smith, relic);
        f.onServer(() -> {
            if (f.world == null) return;
            for (Player p : f.world.getPlayers()) p.showBossBar(f.bar);
        });
        return f;
    }

    /** What the bar reads at a stage. Pure. */
    public static String words(String smith, String relic, int painted, int attempts) {
        String stage = painted <= 0 ? "sketching it"
                : painted >= attempts ? "finishing it" : "painting it, try " + (painted + 1) + " of " + attempts;
        return "✦ " + smith + " forges " + (relic == null || relic.isBlank() ? "a relic" : relic) + ": " + stage;
    }

    /** How full the bar is at a stage (0..1). Pure. */
    public static float progress(int painted, int attempts) {
        if (attempts <= 0) return 0.5f;
        return Math.max(0.04f, Math.min(1f, (painted + 0.3f) / (attempts + 0.3f)));
    }

    /** The artist begins its next step: sprites painted so far, of the attempts allowed. */
    public void step(Integer painted, Integer attempts) {
        int done = painted == null ? 0 : painted, of = attempts == null ? 1 : attempts;
        onServer(() -> {
            if (closed) return;
            bar.name(Component.text(words(smith, relic, done, of)));
            bar.progress(progress(done, of));
            if (world == null) return;
            for (Player p : world.getPlayers()) {
                p.showBossBar(bar); // mortals who arrived since
                if (done > 0) p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.15f, 0.7f + 0.1f * done);
            }
        });
    }

    public void close() {
        closed = true;
        onServer(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        });
    }

    private void onServer(Runnable r) {
        GPTGOD plugin = GPTGOD.get();
        if (plugin == null || !plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }
}
