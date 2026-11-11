package net.bigyous.gptgodmc.god.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/**
 * Everything that names a WorldGuard class. Loaded by name from {@link Lands} and {@link Wards}, and only on
 * servers that have WorldGuard.
 */
public final class WorldGuardBridge implements Lands.Reader, Wards.Bridge {

    private static RegionManager manager(World world) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    }

    @Override
    public String plugin() {
        return "WorldGuard";
    }

    @Override
    public Lands.Claim at(Location l) {
        RegionManager m = manager(l.getWorld());
        if (m == null) return null;
        for (ProtectedRegion r : m.getApplicableRegions(BlockVector3.at(l.getBlockX(), l.getBlockY(), l.getBlockZ())).getRegions()) {
            // The gods' own wards and the world-wide region are nobody's claim.
            if (r.getId().startsWith(Wards.PREFIX) || r.getId().equals(ProtectedRegion.GLOBAL_REGION)) continue;
            List<String> owners = new ArrayList<>(r.getOwners().getPlayers());
            for (UUID id : r.getOwners().getUniqueIds()) {
                String name = Bukkit.getOfflinePlayer(id).getName();
                if (name != null && owners.size() < 4) owners.add(name);
            }
            return new Lands.Claim("WorldGuard", r.getId(), owners.isEmpty() ? null : String.join(", ", owners));
        }
        return null;
    }

    @Override
    public void raise(World world, Wards.Ward ward, String greeting) throws Wards.Refusal {
        RegionManager m = manager(world);
        if (m == null) throw new Wards.Refusal("WorldGuard does not keep regions in this world.");
        int r = ward.radius;
        int low = Math.max(world.getMinHeight(), ward.y - r), high = Math.min(world.getMaxHeight() - 1, ward.y + r);
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(ward.id, BlockVector3.at(ward.x - r, low, ward.z - r),
                BlockVector3.at(ward.x + r, high, ward.z + r));
        region.setPriority(10);
        // A region with no members protects its blocks by default; a ward does that only when asked to.
        if (!ward.effects.contains("no_build")) region.setFlag(Flags.PASSTHROUGH, StateFlag.State.ALLOW);
        for (String effect : ward.effects) {
            switch (effect) {
                case "no_pvp" -> region.setFlag(Flags.PVP, StateFlag.State.DENY);
                case "no_mobs" -> {
                    region.setFlag(Flags.MOB_SPAWNING, StateFlag.State.DENY);
                    region.setFlag(Flags.MOB_DAMAGE, StateFlag.State.DENY);
                }
                case "no_build" -> {
                    region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
                    region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
                }
                case "no_entry" -> region.setFlag(Flags.ENTRY, StateFlag.State.DENY);
                case "no_explosions" -> {
                    region.setFlag(Flags.TNT, StateFlag.State.DENY);
                    region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
                    region.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
                }
                case "no_fall" -> region.setFlag(Flags.FALL_DAMAGE, StateFlag.State.DENY);
                case "no_hunger" -> region.setFlag(Flags.HUNGER_DRAIN, StateFlag.State.DENY);
                case "healing" -> {
                    region.setFlag(Flags.HEAL_AMOUNT, 1);
                    region.setFlag(Flags.HEAL_DELAY, 2);
                }
                default -> throw new Wards.Refusal("A ward cannot do '" + effect + "'.");
            }
        }
        if (greeting != null) region.setFlag(Flags.GREET_MESSAGE, greeting);
        m.addRegion(region); // replaces a region of the same id
    }

    @Override
    public boolean lift(World world, String id) {
        if (!id.startsWith(Wards.PREFIX)) return false; // never a region the god did not make
        RegionManager m = manager(world);
        if (m == null || !m.hasRegion(id)) return false;
        m.removeRegion(id);
        return true;
    }
}
