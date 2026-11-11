package net.bigyous.gptgodmc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class WorldManager {
    private static Path WORLD_FOLDER = JavaPlugin.getPlugin(GPTGOD.class).getDataFolder().toPath().resolve("worlds");
    private static HashMap<String, LocalGameMap> worldMaps = null;
    private static LocalGameMap currentGameMap = null;
    private static boolean worldCurrentlyLoaded = false;
    public static void init(){
        if (worldMaps != null){
            return;
        }
        worldMaps = new HashMap<String, LocalGameMap>();
        if (!Files.isDirectory(WORLD_FOLDER)) {
            GPTGOD.LOGGER.warn("No worlds folder at " + WORLD_FOLDER + "; round worlds are unavailable.");
            return;
        }
        try (var walk = Files.walk(WORLD_FOLDER, 1)) {
            walk.filter(p -> !p.equals(WORLD_FOLDER)).forEach(world -> {
                GPTGOD.LOGGER.info(String.format("world %s found in %s", world.getFileName().toString(), WORLD_FOLDER.toString()));
                worldMaps.put(world.getFileName().toString(), new LocalGameMap(WORLD_FOLDER.toFile(), world.getFileName().toString(), false));
            });
        } catch (IOException e) {
            GPTGOD.LOGGER.error("Getting world Folder failed :(", e);
        }
    }

    private static LocalGameMap getGameMap(String mapName){
        if (worldMaps == null){
            init();
        }
        if(worldMaps.containsKey(mapName)){
            return worldMaps.get(mapName);
        }
        return null;
    }

    public static boolean loadMap(String mapName){
        LocalGameMap map = getGameMap(mapName);
        if(map != null){
            map.load();
            currentGameMap = map;
            worldCurrentlyLoaded = true;
            GPTGOD.LOGGER.info(String.format("Map %s has been loaded", mapName));
            currentGameMap.getWorld().setGameRule(GameRules.MOB_GRIEFING, true);
            currentGameMap.getWorld().setGameRule(GameRules.SPAWN_MOBS, true);
            currentGameMap.getWorld().setGameRule(GameRules.ADVANCE_WEATHER, true);
            currentGameMap.getWorld().setGameRule(GameRules.ADVANCE_TIME, true);
            currentGameMap.getWorld().setGameRule(GameRules.RAIDS, false);
            return true;
        }
        else{
            GPTGOD.LOGGER.warn(String.format("mapName %s does not exist in %s", mapName, WORLD_FOLDER.toString()));
            return false;
        }
    }
    public static void resetCurrentMap(){
        if(currentGameMap == null){
            GPTGOD.LOGGER.warn("no Managed GameMap loaded, reset failed");
            return;
        }
        currentGameMap.restoreFromSource();
    }

    public static boolean hasWorldLoaded(){
        return worldCurrentlyLoaded;
    }

    public static void unload(){
        if(currentGameMap == null){
            return;
        }
        currentGameMap.unload();
        GPTGOD.LOGGER.info(String.format("Map %s has been unloaded", currentGameMap.getWorldName()));
        currentGameMap = null;
        worldCurrentlyLoaded = false;
        
    }

    public static void teleportPlayer(Player player){
        if(!hasWorldLoaded()) return;
        player.setRespawnLocation(currentGameMap.getWorld().getSpawnLocation(), true);
        player.teleport(currentGameMap.getWorld().getSpawnLocation());
        if(!player.isDead()){
            player.getInventory().clear();
            player.updateInventory();
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
            player.clearActiveItem();
            player.clearActivePotionEffects();
            player.setLevel(0);
        }        
    }

    public static String getDimensionName(){
        return getCurrentWorld().getKey().asString();
    }

    /** The round world when rounds are on, otherwise the server's main world. */
    public static World getCurrentWorld(){
        if (currentGameMap != null && currentGameMap.getWorld() != null) {
            return currentGameMap.getWorld();
        }
        return org.bukkit.Bukkit.getWorlds().get(0);
    }

}
