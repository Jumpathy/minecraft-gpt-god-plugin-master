package net.bigyous.gptgodmc.god;

import java.util.Locale;
import java.util.Set;

/**
 * One thing a mortal did in the world, as the server saw it ({@link DeedWatch}): what oaths are checked
 * against and what the land's ledger counts. kind is the creature, block or mortal it was done to
 * ("villager", "oak_log", "Ash"); tags are its broader kinds ("animal", "log", "sapling"). Pure.
 */
public record WorldDeed(String player, String act, String kind, Set<String> tags, String world, int x, int y, int z,
        long at) {

    public boolean is(String tag) {
        return kind.equalsIgnoreCase(tag) || tags.contains(tag.toLowerCase(Locale.ROOT));
    }
}
