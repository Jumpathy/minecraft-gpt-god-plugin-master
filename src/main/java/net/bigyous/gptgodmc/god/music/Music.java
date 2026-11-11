package net.bigyous.gptgodmc.god.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.bigyous.gptgodmc.GPTGOD;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Plays the god's songs with note-block sounds, keeps the songs it composed, and makes discs of
 * them: right-click with a disc to play it around you, or on a jukebox to play it from there.
 * Server thread only.
 */
public final class Music implements Listener {
    private static final Gson GSON = new Gson();
    private static final int KEEP = 40;
    private static final int MAX_PLAYING = 4;
    private static final Map<String, Song> songs = new LinkedHashMap<>();
    private static final List<Playing> playing = new ArrayList<>();
    private static BukkitTask task;
    private static boolean loaded;

    private static final Material[] DISCS = { Material.MUSIC_DISC_CAT, Material.MUSIC_DISC_BLOCKS,
            Material.MUSIC_DISC_CHIRP, Material.MUSIC_DISC_FAR, Material.MUSIC_DISC_MALL, Material.MUSIC_DISC_MELLOHI,
            Material.MUSIC_DISC_STAL, Material.MUSIC_DISC_STRAD, Material.MUSIC_DISC_WAIT, Material.MUSIC_DISC_OTHERSIDE,
            Material.MUSIC_DISC_PIGSTEP, Material.MUSIC_DISC_RELIC };

    private static final class Playing {
        final Song song;
        final List<Song.Note> notes;
        final Supplier<Location> where;
        final String source;
        final int length;
        int tick;
        int next;

        Playing(Song song, Supplier<Location> where, String source) {
            this.song = song;
            this.notes = song.notes();
            this.where = where;
            this.source = source;
            this.length = song.length();
        }
    }

    // ---- The god's songbook ------------------------------------------------------------------------

    private static Path file() {
        return GPTGOD.get().getDataFolder().toPath().resolve("songs.json");
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(file())) {
                List<Song> list = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8),
                        new TypeToken<List<Song>>() {
                        }.getType());
                if (list != null) list.forEach(s -> songs.put(key(s.title()), s));
            }
        } catch (IOException | RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not read songs.json: " + e.getMessage());
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(new ArrayList<>(songs.values())), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save songs.json: " + e.getMessage());
        }
    }

    private static String key(String title) {
        return title.trim().toLowerCase(Locale.ROOT);
    }

    /** Keeps a new song (replacing one with the same title). */
    public static synchronized void keep(Song s) {
        load();
        songs.remove(key(s.title()));
        songs.put(key(s.title()), s);
        while (songs.size() > KEEP) songs.remove(songs.keySet().iterator().next());
        save();
    }

    public static synchronized Song find(String title) {
        load();
        return title == null ? null : songs.get(key(title));
    }

    public static synchronized List<String> titles() {
        load();
        List<String> out = new ArrayList<>();
        songs.values().forEach(s -> out.add(s.title()));
        return out;
    }

    // ---- Playing -------------------------------------------------------------------------------------

    /** Starts a song (stopping whatever already plays from the same source). */
    public static void play(Song song, Supplier<Location> where, String source) {
        stop(source);
        if (playing.size() >= MAX_PLAYING) playing.remove(0);
        playing.add(new Playing(song, where, source));
        if (task == null) task = GPTGOD.get().getServer().getScheduler().runTaskTimer(GPTGOD.get(), Music::tick, 1, 1);
    }

    /** Stops what plays from this source; true if something was playing. */
    public static boolean stop(String source) {
        return playing.removeIf(p -> p.source.equals(source));
    }

    public static int stopAll() {
        int n = playing.size();
        playing.clear();
        return n;
    }

    private static void tick() {
        Iterator<Playing> it = playing.iterator();
        while (it.hasNext()) {
            Playing p = it.next();
            Location at;
            try {
                at = p.where.get();
            } catch (RuntimeException e) {
                at = null;
            }
            if (at == null || at.getWorld() == null || p.tick > p.length + 10) {
                it.remove();
                continue;
            }
            while (p.next < p.notes.size() && p.notes.get(p.next).tick() <= p.tick) {
                Song.Note n = p.notes.get(p.next++);
                Sound sound = sound(n.bass() ? "bass" : p.song.instrument());
                at.getWorld().playSound(at, sound, SoundCategory.RECORDS, 3f, Song.pitch(n.step()));
                at.getWorld().spawnParticle(Particle.NOTE, at.clone().add((Math.random() - 0.5) * 1.2,
                        1.2 + Math.random() * 0.6, (Math.random() - 0.5) * 1.2), 0, n.step() / 24.0, 0, 0, 1);
            }
            p.tick++;
        }
        if (playing.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    static Sound sound(String instrument) {
        return switch (instrument) {
            case "bell" -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case "flute" -> Sound.BLOCK_NOTE_BLOCK_FLUTE;
            case "chime" -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case "guitar" -> Sound.BLOCK_NOTE_BLOCK_GUITAR;
            case "xylophone" -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case "iron_xylophone" -> Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case "pling" -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case "bit" -> Sound.BLOCK_NOTE_BLOCK_BIT;
            case "banjo" -> Sound.BLOCK_NOTE_BLOCK_BANJO;
            case "didgeridoo" -> Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
            case "cow_bell" -> Sound.BLOCK_NOTE_BLOCK_COW_BELL;
            case "bass" -> Sound.BLOCK_NOTE_BLOCK_BASS;
            default -> Sound.BLOCK_NOTE_BLOCK_HARP;
        };
    }

    // ---- Discs ---------------------------------------------------------------------------------------

    private static NamespacedKey discKey() {
        return new NamespacedKey(GPTGOD.get(), "god_song");
    }

    /** A music disc that plays this song. */
    public static ItemStack disc(Song s) {
        Material m = DISCS[Math.floorMod(s.title().hashCode(), DISCS.length)];
        ItemStack stack = new ItemStack(m);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(s.title(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A song of " + (s.by() == null ? "the god" : s.by()), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.text(s.instrument() + " · " + s.tempo() + " bpm · " + (s.length() / 20) + " s",
                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Right-click to play; on a jukebox", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("it plays from there.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(discKey(), PersistentDataType.STRING, GSON.toJson(s));
        stack.setItemMeta(meta);
        return stack;
    }

    static Song songOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String json = stack.getItemMeta().getPersistentDataContainer().get(discKey(), PersistentDataType.STRING);
        if (json == null) return null;
        try {
            return GSON.fromJson(json, Song.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Song song = songOf(e.getItem());
        if (song == null) return;
        e.setCancelled(true); // never let the vanilla disc play or be inserted
        Player p = e.getPlayer();
        Block jukebox = e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.JUKEBOX
                ? e.getClickedBlock() : null;
        String source = jukebox != null ? "jukebox:" + jukebox.getLocation().toVector() : "player:" + p.getUniqueId();
        if (stop(source)) {
            p.sendActionBar(Component.text("The song falls silent.", NamedTextColor.GRAY));
            return;
        }
        try {
            if (jukebox != null) {
                Location at = jukebox.getLocation().add(0.5, 0.6, 0.5);
                play(song, () -> at, source);
            } else {
                play(song, () -> p.isOnline() ? p.getLocation() : null, source);
            }
            p.sendActionBar(Component.text("♫ " + song.title(), NamedTextColor.LIGHT_PURPLE));
        } catch (IllegalArgumentException ex) {
            p.sendActionBar(Component.text("This disc is scratched: " + ex.getMessage(), NamedTextColor.RED));
        }
    }

    /** Glow-colored note particles look like the colour of the god; kept for future shows. */
    static Color tint(int step) {
        return Color.fromRGB(150 + step * 4, 60, 255 - step * 4);
    }
}
