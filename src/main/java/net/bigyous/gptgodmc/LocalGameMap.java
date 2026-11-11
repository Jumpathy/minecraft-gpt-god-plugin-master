package net.bigyous.gptgodmc;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/** A world copied from plugins/gptgodmc/worlds so it can be reset each round. */
public class LocalGameMap {
    private final File sourceWorldFolder;
    private File activeWorldFolder;
    private final String worldName;
    private World bukkitWorld;

    public LocalGameMap(File worldFolder, String worldName, boolean loadOnInit) {
        this.sourceWorldFolder = new File(worldFolder, worldName);
        this.worldName = worldName;
        if (loadOnInit) load();
    }

    public boolean load() {
        if (isLoaded()) return true;
        this.activeWorldFolder = new File(Bukkit.getWorldContainer(),
                sourceWorldFolder.getName() + "_active_" + System.currentTimeMillis());
        try {
            copy(sourceWorldFolder.toPath(), activeWorldFolder.toPath());
            // A copied uid.dat makes Bukkit think the world is already loaded.
            Files.deleteIfExists(activeWorldFolder.toPath().resolve("uid.dat"));
        } catch (IOException e) {
            GPTGOD.LOGGER.error("Loading GameMap Failed", e);
            return false;
        }
        this.bukkitWorld = Bukkit.createWorld(new WorldCreator(activeWorldFolder.getName()));
        if (bukkitWorld != null) {
            this.bukkitWorld.setAutoSave(false);
        }
        return isLoaded();
    }

    public void unload() {
        if (bukkitWorld != null) Bukkit.unloadWorld(bukkitWorld, false);
        if (activeWorldFolder != null) {
            try {
                delete(activeWorldFolder.toPath());
            } catch (IOException e) {
                GPTGOD.LOGGER.error("Unloading World failed", e);
                return;
            }
        }
        this.bukkitWorld = null;
        this.activeWorldFolder = null;
    }

    public boolean restoreFromSource() {
        unload();
        return load();
    }

    public boolean isLoaded() {
        return bukkitWorld != null;
    }

    public World getWorld() {
        return bukkitWorld;
    }

    public String getWorldName() {
        return worldName;
    }

    private static void copy(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.getFileName().toString().equals("session.lock")) {
                    Files.copy(file, to.resolve(from.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void delete(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
