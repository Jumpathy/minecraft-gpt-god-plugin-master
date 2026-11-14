package net.bigyous.gptgodmc.god;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Two guards for mortals the gods have thrown about.
 *
 * <p>Nothing moves a mortal who is on the death screen. A teleport there (a god's teleport, fling,
 * labyrinth or script reaching a player who just died) leaves them in the void with a Respawn button that
 * does nothing. Teleports by a plugin or a command are cancelled while the player is dead; the game's own
 * respawn is not a teleport and goes through.
 *
 * <p>A mortal who was saved in mid-air (mid-jump, flung, dropped by a god when the server stopped) is set
 * down on the ground below them when they join. Otherwise the server's anti-flight check sees them hang
 * in the air while their game is still loading and kicks them ("floating too long"), every time they join.
 */
public final class DeathGuard implements Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!e.getPlayer().isDead()) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c == PlayerTeleportEvent.TeleportCause.PLUGIN || c == PlayerTeleportEvent.TeleportCause.COMMAND
                || c == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.isDead() || p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isInsideVehicle()) return;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
        Location at = p.getLocation();
        Double ground = groundBelow(at);
        if (ground == null || at.getY() - ground < 0.05) return;
        Location down = at.clone();
        down.setY(ground);
        p.setFallDistance(0);
        p.teleport(down);
    }

    /** The top of the first solid block under a place (water and climbable blocks count), or null over the void. */
    static Double groundBelow(Location at) {
        if (at.getWorld() == null) return null;
        int min = at.getWorld().getMinHeight();
        Block b = at.getBlock();
        // Standing in water, on a ladder or a vine is not hanging in the air.
        if (b.isLiquid() || org.bukkit.Tag.CLIMBABLE.isTagged(b.getType())) return at.getY();
        for (int y = at.getBlockY(); y >= min; y--) {
            Block under = at.getWorld().getBlockAt(at.getBlockX(), y, at.getBlockZ());
            if (under.isLiquid()) return Math.min(at.getY(), y + 1.0);
            if (under.isPassable()) continue;
            double top = under.getBoundingBox().getMaxY();
            if (top <= at.getY() + 1e-6) return top;
        }
        return null;
    }
}
