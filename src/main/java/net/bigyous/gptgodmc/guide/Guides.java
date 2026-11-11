package net.bigyous.gptgodmc.guide;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModGuide;
import net.bigyous.gptgodmc.god.Godslayer;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.god.TabList;
import net.bigyous.gptgodmc.gui.GodManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Hands every mortal the guide (a written book) the first time they enter a world with a god, once ever
 * (a mark on the player, so it survives restarts), and never a second copy while they carry one. /guide
 * (also /god tutorial) opens it again. Players with the client mod's "guide" feature get the mod's
 * screen when they use the book. The book is rewritten for its reader when used (gods' names change;
 * operators read extra pages). Server thread only.
 */
public final class Guides implements Listener, TabExecutor {
    private static NamespacedKey key(String name) {
        return new NamespacedKey(GPTGOD.get(), name);
    }

    /** On the book: 1 = a mortal's edition, 2 = an operator's. */
    private static NamespacedKey itemKey() {
        return key("guide");
    }

    /** On the book: a hash of what it says, to rewrite only stale books. */
    private static NamespacedKey revKey() {
        return key("guide_rev");
    }

    /** On the player: they were given the guide once. */
    private static NamespacedKey givenKey() {
        return key("guide_given");
    }

    private static FileConfiguration cfg() {
        return GPTGOD.get().getConfig();
    }

    public static boolean enabled() {
        return cfg().getBoolean("guide.enabled", true);
    }

    static boolean giveOnJoin() {
        return enabled() && cfg().getBoolean("guide.give-on-join", true);
    }

    // ---- The book ----------------------------------------------------------------------------------

    /** What this reader's guide says: operators get their pages, the gods are those of their world. */
    public static Guide.Book book(Player p) {
        return Guide.build(context(p.hasPermission(GodManager.PERMISSION), p.getWorld()));
    }

    static Guide.Context context(boolean operator, World world) {
        List<Guide.GodLine> gods = new ArrayList<>();
        List<Realm> realms = world == null ? List.of() : Realm.godsOf(world);
        for (Realm r : realms) {
            Persona persona = r.persona();
            String epithet = persona == null ? null : persona.epithet;
            int color = TabList.archetypeColor(persona == null ? null : persona.archetype).value();
            gods.add(new Guide.GodLine(persona == null ? "A god not yet awoken" : r.displayName(), epithet, color,
                    Godslayer.isSlain(r)));
        }
        Set<Guide.Feature> off = EnumSet.noneOf(Guide.Feature.class);
        FileConfiguration c = cfg();
        if (!c.getBoolean("oaths.enabled", true)) off.add(Guide.Feature.OATHS);
        if (!c.getBoolean("ecology.enabled", true)) off.add(Guide.Feature.ECOLOGY);
        if (!c.getBoolean("godslayer.enabled", true)) off.add(Guide.Feature.GODSLAYER);
        if (!c.getBoolean("godslayer.craftable", true)) off.add(Guide.Feature.GODSLAYER_CRAFT);
        if (!c.getBoolean("creations.enabled", false)) off.add(Guide.Feature.RELICS);
        if (!c.getBoolean("servants.enabled", true)) off.add(Guide.Feature.SERVANTS);
        if (!c.getBoolean("events.enabled", true)) off.add(Guide.Feature.HOLY_DAYS);
        if (!c.getBoolean("purpose.enabled", true)) off.add(Guide.Feature.PURPOSE);
        if (!c.getBoolean("apotheosis.enabled", false)) off.add(Guide.Feature.APOTHEOSIS);
        if (!Bukkit.getPluginManager().isPluginEnabled("voicechat")) off.add(Guide.Feature.VOICE);
        return new Guide.Context(operator, gods, off);
    }

    /** The guide as a written book for this reader. */
    public static ItemStack item(Player p) {
        Guide.Book book = book(p);
        List<GuideBook.Page> pages = GuideBook.pages(book);
        ItemStack stack = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) stack.getItemMeta();
        meta.setTitle(Guide.TITLE);
        meta.setAuthor("the gods");
        meta.pages(GuideBook.components(pages));
        meta.lore(List.of(Component.text("Right-click to read. /guide brings it back.", NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemKey(), PersistentDataType.BYTE, (byte) (book.operator() ? 2 : 1));
        pdc.set(revKey(), PersistentDataType.INTEGER, rev(pages));
        stack.setItemMeta(meta);
        return stack;
    }

    private static int rev(List<GuideBook.Page> pages) {
        StringBuilder sb = new StringBuilder();
        for (GuideBook.Page page : pages) sb.append(page.text()).append('\f');
        return sb.toString().hashCode();
    }

    public static boolean isGuide(ItemStack stack) {
        if (stack == null || stack.getType() != Material.WRITTEN_BOOK || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(itemKey(), PersistentDataType.BYTE);
    }

    public static boolean carries(Player p) {
        for (ItemStack s : p.getInventory().getContents()) {
            if (isGuide(s)) return true;
        }
        return isGuide(p.getItemOnCursor());
    }

    /** Gives the guide unless the player already carries one (an empty hotbar slot first). */
    public static boolean give(Player p) {
        if (carries(p)) return false;
        ItemStack book = item(p);
        PlayerInventory inv = p.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack s = inv.getItem(slot);
            if (s == null || s.getType().isAir()) {
                inv.setItem(slot, book);
                return true;
            }
        }
        inv.addItem(book).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
        return true;
    }

    /** Shows the guide: the mod's screen for players who have it, else the book (without giving one). */
    public static void open(Player p) {
        Guide.Book book = book(p);
        if (ModGuide.has(p) && ModGuide.open(p, book)) return;
        p.openBook(item(p));
    }

    private static boolean hasGod(World world) {
        return world != null && !Realm.godsOf(world).isEmpty();
    }

    /** The first time a player stands in a world with a god: the guide, once. */
    private static void welcome(Player p) {
        if (!p.isOnline() || !giveOnJoin() || !hasGod(p.getWorld())) return;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (pdc.has(givenKey(), PersistentDataType.BYTE)) return;
        pdc.set(givenKey(), PersistentDataType.BYTE, (byte) 1);
        if (!give(p)) return;
        p.sendMessage(Component.text("You were handed " + Guide.TITLE + ". Right-click it to read; /guide "
                + "opens it again.", Speech.DEEP_VIOLET));
    }

    // ---- Events ------------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // A moment later, so the player is fully in (and other plugins' join items are in place).
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> welcome(p), 40L);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> welcome(p), 20L);
    }

    /**
     * Using the guide: the mod's screen, or the book rewritten for this reader (and the copy in hand
     * brought up to date, so it reads the same when lent).
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isGuide(event.getItem()) || event.getHand() == null) return;
        Player p = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND && isGuide(p.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        Guide.Book book = book(p);
        if (ModGuide.has(p) && ModGuide.open(p, book)) return;
        ItemStack fresh = item(p);
        Integer had = event.getItem().getItemMeta().getPersistentDataContainer().get(revKey(),
                PersistentDataType.INTEGER);
        Integer now = fresh.getItemMeta().getPersistentDataContainer().get(revKey(), PersistentDataType.INTEGER);
        if (had == null || !had.equals(now)) {
            fresh.setAmount(event.getItem().getAmount());
            p.getInventory().setItem(event.getHand(), fresh);
        }
        p.openBook(fresh);
    }

    // ---- /guide [player] (also /god tutorial) ------------------------------------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            String[] args) {
        command(sender, args);
        return true;
    }

    /** /guide, /tutorial and /god tutorial|guide: open your guide (and get the book back); operators: give one. */
    public static void command(CommandSender sender, String[] args) {
        boolean admin = !(sender instanceof Player) || sender.hasPermission(GodManager.PERMISSION);
        if (args.length > 0 && admin) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("No player called " + args[0] + " is online.", NamedTextColor.RED));
                return;
            }
            boolean given = give(target);
            open(target);
            sender.sendMessage(Component.text(given ? "Gave " + target.getName() + " the guide."
                    : target.getName() + " already carries the guide; it was opened for them.", Speech.DEEP_VIOLET));
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Usage: guide <player>");
            return;
        }
        if (!enabled()) {
            p.sendMessage(Component.text("The guide is turned off on this server.", NamedTextColor.GRAY));
            return;
        }
        boolean given = give(p);
        open(p);
        if (given) {
            p.sendMessage(Component.text(Guide.TITLE + " is in your inventory.", Speech.DEEP_VIOLET));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            String[] args) {
        return complete(sender, args);
    }

    public static List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1 || (sender instanceof Player && !sender.hasPermission(GodManager.PERMISSION))) {
            return List.of();
        }
        String start = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(start)).toList();
    }
}
