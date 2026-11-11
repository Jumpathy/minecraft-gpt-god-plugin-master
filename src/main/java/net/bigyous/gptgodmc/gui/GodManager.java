package net.bigyous.gptgodmc.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.commands.GodCommand;
import net.bigyous.gptgodmc.god.Edicts;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.TabList;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.bigyous.gptgodmc.gui.MenuModel.Decision;
import net.bigyous.gptgodmc.gui.MenuModel.Icon;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;
import net.bigyous.gptgodmc.gui.MenuModel.Page;
import net.bigyous.gptgodmc.gui.MenuModel.Preset;
import net.bigyous.gptgodmc.gui.MenuModel.Setting;
import net.bigyous.gptgodmc.gui.MenuModel.State;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * The God Manager: a chest menu for operators (/god manager, or right-click the God Manager item).
 * Layout and click meaning live in {@link MenuLayout}; this class only turns them into items, keeps
 * every item inside the menu (all clicks and drags in it are cancelled), and carries out the clicks.
 * Only holders of gptgodmc.admin can open or use it.
 */
public final class GodManager implements Listener {
    public static final String PERMISSION = "gptgodmc.admin";
    private static final long PROMPT_MS = 60_000;
    /** Operators typing a command in chat, by player. */
    private static final Map<UUID, Prompt> PROMPTS = new ConcurrentHashMap<>();
    private static final AtomicInteger PROMPT_IDS = new AtomicInteger();

    /**
     * @param kind   command | note | deed | name
     * @param target the mortal a note or deed is about
     */
    private record Prompt(int id, String realm, long deadline, String kind, String target) {
    }

    /** Which mortal and which page of the Players list each operator is looking at. */
    private static final class View {
        volatile String selected;
        volatile int listPage;
        volatile String tab = "Mind";
        /** The god being made on the New god page. */
        volatile String draftName;
        volatile String draftArchetype = "random";
        volatile String draftPower;
    }

    private static final Map<UUID, View> VIEWS = new ConcurrentHashMap<>();

    private static View view(Player p) {
        return VIEWS.computeIfAbsent(p.getUniqueId(), k -> new View());
    }

    /** Marks our inventories, so clicks in them are recognised and cancelled. */
    static final class Holder implements InventoryHolder {
        final String realm;
        Menu menu;
        MenuModel.Action confirm;
        Inventory inventory;

        Holder(String realm) {
            this.realm = realm;
        }

        Realm realm() {
            Realm r = Realm.byId(realm);
            return r != null ? r : Realm.primary();
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    // ---- Opening -------------------------------------------------------------------------------

    /** Opens the God Manager for the god of the current realm. Server thread. */
    public static void open(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Component.text("Only operators may command the god.", NamedTextColor.RED));
            return;
        }
        openFor(player, Realm.current());
    }

    /**
     * With one god on the server, opens straight on its page (Back leads to the hub). With several, opens
     * on the hub, where every god is listed.
     */
    private static void openFor(Player player, Realm realm) {
        show(player, realm, Realm.several() ? Page.HUB : Page.MAIN, null);
    }

    /** Every god on the server (the hub's list) and this god's might and mind (Might & Mind). */
    static MenuModel.Gods gods(Realm realm) {
        List<MenuModel.Deity> list = new ArrayList<>();
        for (Realm g : Realm.all()) {
            Persona p = g.persona();
            boolean awake = g.get(God::isAwake);
            boolean paused = g.get(God::isPaused);
            list.add(new MenuModel.Deity(g.id, p == null ? null : p.shortName(), p == null ? null : p.epithet,
                    p == null ? g.archetype : p.archetype, g.power().id,
                    net.bigyous.gptgodmc.god.Godslayer.isSlain(g) ? "slain" : g.dormant() ? "off"
                            : !awake ? "asleep" : paused ? "paused" : "awake", g == realm,
                    net.bigyous.gptgodmc.god.Pantheon.created(g.id)));
        }
        net.bigyous.gptgodmc.god.GodProfile prof = realm.profile();
        return new MenuModel.Gods(list, realm.power().id, net.bigyous.gptgodmc.god.GodProfile.blankOrNull(prof.model),
                net.bigyous.gptgodmc.god.GodProfile.blankOrNull(prof.effort),
                net.bigyous.gptgodmc.god.GodSettings.configuredModel(),
                net.bigyous.gptgodmc.god.GodSettings.configuredEffort(),
                net.bigyous.gptgodmc.god.Pantheon.created(realm.id)).withPeers(peers(realm));
    }

    /** The other gods of this god's world and how it stands with each, for the Bonds page. Server thread. */
    static List<MenuModel.Peer> peers(Realm realm) {
        List<MenuModel.Peer> out = new ArrayList<>();
        if (!realm.shared()) return out;
        net.bigyous.gptgodmc.god.living.Bonds bonds = net.bigyous.gptgodmc.god.living.Rivalry.bonds(realm);
        for (Realm o : realm.peers()) {
            int score = bonds.score(o.id);
            net.bigyous.gptgodmc.god.living.Bonds.Bond b = bonds.find(o.id);
            Persona p = o.persona();
            out.add(new MenuModel.Peer(o.id, p == null ? "An unborn god (@" + o.id + ")" : p.shortName(), o.power().id,
                    score, net.bigyous.gptgodmc.god.living.Bonds.stance(score),
                    net.bigyous.gptgodmc.god.living.Rivalry.pact(realm, o).id(), b == null ? null : b.why));
        }
        return out;
    }

    /** Draws a page for the player, reusing the open menu when only its icons change. */
    private static void show(Player player, Realm realm, Page page, MenuModel.Action confirm) {
        realm.run(() -> {
            State state = snapshot(realm).withConfirm(confirm);
            if (page == Page.PLAYERS || page == Page.PLAYER) {
                View v = view(player);
                List<MenuModel.Mortal> mortals = mortals(realm);
                state = state.withPlayers(mortals, page == Page.PLAYER ? mortal(v.selected, realm) : null, v.listPage);
            }
            if (page == Page.CREATE) {
                View v = view(player);
                if (v.draftPower == null) v.draftPower = net.bigyous.gptgodmc.god.GodSettings.pantheonDefaultPower();
                state = state.withDraft(new MenuModel.Draft(v.draftName, v.draftArchetype, v.draftPower));
            }
            if (page == Page.ADVANCED) {
                Map<String, String> values = new LinkedHashMap<>();
                for (Advanced.Option o : Advanced.OPTIONS) {
                    Object v = GPTGOD.get().getConfig().get(o.key());
                    values.put(o.key(), v == null ? o.fallback() : String.valueOf(v));
                }
                state = state.withTuning(new MenuModel.Tuning(view(player).tab, values));
            }
            Menu menu = MenuLayout.build(page, state);
            Inventory top = player.getOpenInventory().getTopInventory();
            // Reuse the open inventory only for the same page and title (a different mortal = a new title).
            Holder holder = top.getHolder() instanceof Holder h && h.realm.equals(realm.id) && h.menu != null
                    && h.menu.page() == page && h.menu.title().equals(menu.title()) ? h : null;
            boolean fresh = holder == null;
            if (fresh) {
                holder = new Holder(realm.id);
                holder.inventory = Bukkit.createInventory(holder, MenuModel.SIZE,
                        Component.text(menu.title(), TextColor.color(0x5A1E8C)));
            }
            holder.menu = menu;
            holder.confirm = confirm;
            Inventory inv = holder.inventory;
            for (int slot = 0; slot < MenuModel.SIZE; slot++) {
                Icon icon = menu.at(slot);
                inv.setItem(slot, icon == null ? null : item(icon, player));
            }
            if (fresh) player.openInventory(inv);
        });
    }

    /** What the god of this realm is like now, for the layout. */
    static State snapshot(Realm realm) {
        Persona p = Persona.current();
        Vessel v = God.vessel();
        List<MenuModel.Archetype> archetypes = new ArrayList<>();
        for (Map.Entry<String, ConfigurationSection> e : Persona.archetypes().entrySet()) {
            archetypes.add(new MenuModel.Archetype(e.getKey(), e.getValue().getString("description", "")));
        }
        List<MenuModel.Pending> pending = new ArrayList<>();
        long now = System.currentTimeMillis();
        int retries = Edicts.retries();
        for (Edicts.Edict e : Edicts.book().pending()) {
            String status = e.standing()
                    ? Math.max(1, (e.until - now + 59_999) / 60_000) + " min left"
                    : e.thoughts == 0 ? "Not yet seen by the god" : "Seen in " + e.thoughts + " of " + retries
                            + " thoughts";
            pending.add(new MenuModel.Pending(e.number, e.text, e.standing(), status));
        }
        Map<String, Boolean> settings = new LinkedHashMap<>();
        for (Setting s : MenuLayout.SETTINGS) {
            settings.put(s.key(), GPTGOD.get().getConfig().getBoolean(s.key(), s.fallback()));
        }
        settings.put(MenuLayout.gazeKey(net.bigyous.gptgodmc.god.GodSettings.trueFormGaze().id), true);
        settings.put("god.dormant", realm.dormant());
        return new State(p == null ? null : p.shortName(), p == null ? null : p.epithet,
                p == null ? Persona.configuredArchetype() : p.archetype, God.isAwake(), God.isPaused(),
                v.hasBody() || v.isMaterializing(), v.inTrueForm(), v.mood(), archetypes, pending, settings,
                Realm.several() ? realm.id : null, null)
                .withGods(gods(realm))
                .withSoul(realm.lookSetting(),
                        Memory.get().heart.summary(System.currentTimeMillis()))
                .withDials(dials(p))
                .withVoice(new MenuModel.VoiceNow(net.bigyous.gptgodmc.god.GodSettings.ttsEngine(),
                        net.bigyous.gptgodmc.god.GodSettings.ttsVoice(),
                        net.bigyous.gptgodmc.god.GodSettings.voiceEffect()))
                .withAware(net.bigyous.gptgodmc.god.Awareness.lines(realm, System.currentTimeMillis()));
    }

    /** The god says a short line to this player, so they hear its voice as it is now. */
    private static void sample(Player p, String tone) {
        if (!net.bigyous.gptgodmc.voice.VoiceModule.isActive()) {
            say(p, "Voice is off (needs Simple Voice Chat and voice.enabled).");
            return;
        }
        Persona persona = Persona.current();
        String name = persona == null ? "your god" : persona.shortName();
        net.bigyous.gptgodmc.voice.VoiceModule.speak("I am " + name + ". Hear me, mortal: this is my voice.",
                List.of(p), tone);
    }

    /** Turns one Advanced setting ("key|L", "key|RS", ...) and saves config.yml. */
    private static void tune(Player p, String arg) {
        if (arg == null || !arg.contains("|")) return;
        String key = arg.substring(0, arg.indexOf('|'));
        String how = arg.substring(arg.indexOf('|') + 1);
        Advanced.Option o = Advanced.find(key);
        if (o == null) return;
        org.bukkit.configuration.file.FileConfiguration cfg = GPTGOD.get().getConfig();
        Object raw = cfg.get(key);
        String before = raw == null ? o.fallback() : String.valueOf(raw);
        String after = Advanced.after(o, before, how.contains("R"), how.contains("S"));
        if (after.equals(before)) {
            say(p, o.label() + " is already at its limit.");
            return;
        }
        switch (o.type()) {
            case BOOL -> cfg.set(key, Boolean.parseBoolean(after));
            case INT -> cfg.set(key, Long.parseLong(after));
            case DOUBLE -> cfg.set(key, Double.parseDouble(after));
            default -> cfg.set(key, after);
        }
        GPTGOD.get().saveConfig();
        net.bigyous.gptgodmc.god.Journal.add(net.bigyous.gptgodmc.god.Journal.Kind.INFO, "Setting " + key
                + " changed by " + p.getName() + ": " + before + " -> " + after);
        GPTGOD.LOGGER.info("[God Manager] " + p.getName() + " set " + key + ": " + before + " -> " + after);
        if (key.equals("god.tab-list")) TabList.refreshAll();
        if (key.equals("creations.enabled") && after.equals("true")) {
            // Lighting the forge is the one switch here that can spend real money fast: say so.
            p.sendMessage(net.kyori.adventure.text.Component.text("Warning: ", net.kyori.adventure.text.format.NamedTextColor.RED)
                    .append(net.kyori.adventure.text.Component.text(net.bigyous.gptgodmc.god.art.ArtSettings.warning(),
                            net.kyori.adventure.text.format.NamedTextColor.GOLD)));
            net.bigyous.gptgodmc.god.art.RelicsModule.warn();
        }
        if (key.startsWith("viewer.")) say(p, net.bigyous.gptgodmc.viewer.MindViewer.sync());
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
        if (o.restart()) say(p, o.label() + ": " + Advanced.show(o, after) + ". It takes hold after a restart.");
    }

    /** The living god's dials, with where its archetype starts each. */
    static List<MenuModel.Dial> dials(Persona p) {
        if (p == null) return List.of();
        List<MenuModel.Dial> out = new ArrayList<>();
        org.bukkit.configuration.ConfigurationSection a = Persona.archetypes().get(p.archetype);
        for (String key : Persona.DIALS) {
            int base = a == null || !a.contains("dials." + key) ? 5 : Math.max(0, Math.min(10, a.getInt("dials." + key)));
            out.add(new MenuModel.Dial(key, p.dial(key), base));
        }
        return out;
    }

    /** Turns a dial of the living god (or resets them all) and saves persona.json. */
    private static void turnDial(Player p, String arg) {
        Persona persona = Persona.current();
        if (persona == null || arg == null) {
            say(p, "The god is not born yet.");
            return;
        }
        Map<String, Integer> next = new LinkedHashMap<>(persona.dials);
        List<String> moved = new ArrayList<>();
        for (MenuModel.Dial d : dials(persona)) {
            String change = arg.equals("reset") ? "reset" : arg.startsWith(d.key() + ":") ? arg.substring(d.key().length() + 1) : null;
            if (change == null) continue;
            int now = MenuLayout.dialAfter(d.value(), d.base(), change);
            if (now != d.value()) moved.add(d.key() + " " + d.value() + " → " + now);
            next.put(d.key(), now);
        }
        if (moved.isEmpty()) {
            say(p, arg.equals("reset") ? "Its nature is already as its archetype made it." : "That dial is already at its limit.");
            return;
        }
        // A new map, so a thought reading the old one is never disturbed.
        persona.dials = next;
        Persona.save(persona);
        net.bigyous.gptgodmc.god.Journal.add(net.bigyous.gptgodmc.god.Journal.Kind.INFO,
                "Nature changed by " + p.getName() + ": " + String.join(", ", moved));
        say(p, "Its nature shifts: " + String.join(", ", moved) + ". It takes hold from its next thought.");
    }

    /** Every mortal the god keeps a record on, plus anyone online: online first, then most recently seen. */
    static List<MenuModel.Mortal> mortals(Realm realm) {
        Memory mem = Memory.get();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Player p : realm.players()) names.add(p.getName());
        names.addAll(mem.knownNames());
        List<MenuModel.Mortal> out = new ArrayList<>();
        java.util.Set<String> seenLower = new java.util.HashSet<>();
        for (String n : names) {
            if (seenLower.add(n.toLowerCase(java.util.Locale.ROOT))) out.add(mortal(n, realm));
        }
        out.sort(java.util.Comparator.comparing((MenuModel.Mortal m) -> !m.online())
                .thenComparing(m -> lastSeen(mem, m.name()), java.util.Comparator.reverseOrder())
                .thenComparing(m -> m.name().toLowerCase(java.util.Locale.ROOT)));
        return out;
    }

    private static long lastSeen(Memory mem, String name) {
        synchronized (mem) {
            Memory.Subject s = mem.subjectIfAny(name);
            return s == null ? 0 : s.lastSeen;
        }
    }

    /** What the god remembers about one mortal (an empty record when it knows nothing), or null for no name. */
    static MenuModel.Mortal mortal(String name, Realm realm) {
        if (name == null) return null;
        boolean online = false;
        for (Player p : realm.players()) online |= p.getName().equalsIgnoreCase(name);
        Memory mem = Memory.get();
        long now = System.currentTimeMillis();
        synchronized (mem) {
            Memory.Subject s = mem.subjectIfAny(name);
            if (s == null) return new MenuModel.Mortal(name, 0, online, null, null, List.of(), List.of(), 0, null);
            return new MenuModel.Mortal(s.name == null ? name : s.name, s.favor, online, s.title, s.summary,
                    entries(s.notes, now), entries(s.deeds, now), s.offerings,
                    s.lastSeen > 0 ? Memory.age(now - s.lastSeen) : null);
        }
    }

    private static List<MenuModel.Entry> entries(List<Memory.Note> notes, long now) {
        List<MenuModel.Entry> out = new ArrayList<>();
        for (int i = notes.size() - 1; i >= 0; i--) {
            Memory.Note n = notes.get(i);
            out.add(new MenuModel.Entry(n.at, n.text, n.at > 0 ? Memory.age(now - n.at) : null));
        }
        return out;
    }

    private static ItemStack item(Icon icon, Player viewer) {
        ItemStack stack = new ItemStack(icon.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(Component.text(icon.name(), TextColor.color(icon.rgb())).decoration(TextDecoration.ITALIC,
                false));
        List<Component> lore = new ArrayList<>();
        for (String line : icon.lore()) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        if (icon.glow()) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        // The painted icon, only on this menu copy and only for a client that loaded the pack: the item
        // type itself (and every such item in the world) keeps its vanilla look.
        String art = MenuIcons.modelFor(icon.art());
        if (art != null && net.bigyous.gptgodmc.god.vessel.VesselPackHost.has(viewer)
                && net.bigyous.gptgodmc.god.Godslayer.packHas(MenuIcons.itemFile(icon.art()))) {
            meta.setItemModel(NamespacedKey.fromString(art));
        }
        if (icon.viewerHead() && meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(viewer);
        } else if (icon.owner() != null && meta instanceof SkullMeta skull) {
            // Only players the server already knows: never a blocking profile lookup on the server thread.
            Player online = Bukkit.getPlayerExact(icon.owner());
            org.bukkit.OfflinePlayer known = online != null ? online : Bukkit.getOfflinePlayerIfCached(icon.owner());
            if (known != null) skull.setOwningPlayer(known);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    // ---- The God Manager item ------------------------------------------------------------------

    private static NamespacedKey key() {
        return new NamespacedKey(GPTGOD.get(), "god_manager");
    }

    /** A glowing nether star that opens the menu on right-click (for operators only). */
    public static ItemStack managerItem() {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("God Manager", TextColor.color(0xC77DFF)).decoration(TextDecoration.ITALIC,
                false).decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Right-click to command the god.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Bound to you: drop it twice to put", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("it away (/god manager brings it back).", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Gives the player the God Manager star unless they already carry one (prefers an empty hotbar slot). */
    public static boolean giveItem(Player p) {
        for (ItemStack s : p.getInventory().getContents()) {
            if (isManagerItem(s)) return false;
        }
        if (isManagerItem(p.getItemOnCursor())) return false;
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack s = inv.getItem(slot);
            if (s == null || s.getType().isAir()) {
                inv.setItem(slot, managerItem());
                return true;
            }
        }
        inv.addItem(managerItem()).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
        return true;
    }

    // ---- A star that stays with its bearer -------------------------------------------------------

    /** When each player last tried to drop the star; a second drop within this time puts it away. */
    private static final Map<UUID, Long> FIRST_DROP = new ConcurrentHashMap<>();
    private static final long DROP_AGAIN_MS = 3000;
    /** Players who died holding the star get it back when they respawn. */
    private static final java.util.Set<UUID> KEEP_ON_RESPAWN = ConcurrentHashMap.newKeySet();

    @EventHandler(priority = EventPriority.HIGH)
    public void onDropStar(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (!isManagerItem(event.getItemDrop().getItemStack())) return;
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        Long first = FIRST_DROP.get(p.getUniqueId());
        if (first != null && now - first <= DROP_AGAIN_MS) {
            // Second drop: the star is put away, never left lying in the world.
            FIRST_DROP.remove(p.getUniqueId());
            event.getItemDrop().remove();
            p.sendActionBar(Component.text("The God Manager is put away. /god manager brings it back.",
                    NamedTextColor.GRAY));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.4f);
            return;
        }
        FIRST_DROP.put(p.getUniqueId(), now);
        event.setCancelled(true);
        p.sendActionBar(Component.text("The God Manager stays with you. Drop it again to put it away.",
                TextColor.color(0xC77DFF)));
    }

    /** The star never goes into chests, hoppers or other containers. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onStoreStar(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof Holder) return; // the menu itself cancels every click
        org.bukkit.event.inventory.InventoryType type = top.getType();
        if (type == org.bukkit.event.inventory.InventoryType.CRAFTING
                || type == org.bukkit.event.inventory.InventoryType.CREATIVE) {
            return; // only the player's own inventory is open
        }
        boolean intoTop = event.getClickedInventory() == top;
        boolean moving = (intoTop && isManagerItem(event.getCursor()))
                || (event.isShiftClick() && !intoTop && isManagerItem(event.getCurrentItem()))
                || (intoTop && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                        && isManagerItem(event.getWhoClicked().getInventory().getItem(event.getHotbarButton())))
                || (intoTop && event.getClick() == ClickType.SWAP_OFFHAND
                        && isManagerItem(event.getWhoClicked().getInventory().getItemInOffHand()));
        if (moving) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDragStar(InventoryDragEvent event) {
        if (!isManagerItem(event.getOldCursor())) return;
        int topSize = event.getView().getTopInventory().getSize();
        org.bukkit.event.inventory.InventoryType type = event.getView().getTopInventory().getType();
        if (type == org.bukkit.event.inventory.InventoryType.CRAFTING) return;
        if (event.getRawSlots().stream().anyMatch(s -> s < topSize)) event.setCancelled(true);
    }

    @EventHandler
    public void onDeathWithStar(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (event.getDrops().removeIf(GodManager::isManagerItem)) {
            KEEP_ON_RESPAWN.add(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onRespawnWithStar(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (KEEP_ON_RESPAWN.remove(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(GPTGOD.get(), () -> giveItem(p));
        }
    }

    public static boolean isManagerItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.NETHER_STAR || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(key(), PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isManagerItem(event.getItem())) return;
        Player p = event.getPlayer();
        if (!p.hasPermission(PERMISSION)) return; // a plain nether star for everyone else
        if (event.getHand() == EquipmentSlot.OFF_HAND && isManagerItem(p.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        Realm r = Realm.of(p.getWorld());
        Realm realm = r != null ? r : Realm.primary();
        Bukkit.getScheduler().runTask(GPTGOD.get(), () -> {
            // Sneaking keeps the chest menu; otherwise operators with the client mod get its screen.
            if (!p.isSneaking() && net.bigyous.gptgodmc.client.ModGui.open(p)) return;
            openFor(p, realm);
        });
    }

    // ---- Keeping items in place ----------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;
        // Nothing moves in or out: not shift-clicks, number keys, double-click collecting or drops.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        // Clicks in the player's own inventory (below the menu) are cancelled above and otherwise ignored.
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT
                && click != ClickType.SHIFT_RIGHT) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize() || holder.menu == null) return;
        Decision d = MenuLayout.click(holder.menu, slot, holder.confirm, click.isRightClick(), click.isShiftClick());
        Page page = holder.menu.page();
        Bukkit.getScheduler().runTask(GPTGOD.get(), () -> handle(p, holder, page, d));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    // ---- Carrying out clicks -------------------------------------------------------------------

    private static void handle(Player p, Holder holder, Page page, Decision d) {
        if (!p.isOnline()) return;
        if (!p.hasPermission(PERMISSION)) {
            p.closeInventory();
            return;
        }
        Realm realm = holder.realm();
        MenuModel.Action run = d.run();
        // Picking a god on the hub opens that god's page; removing one goes back to the hub.
        if (run != null && run.kind() == MenuModel.Kind.SELECT_GOD) {
            Realm chosen = Realm.byId(run.arg());
            if (chosen == null) chosen = realm;
            show(p, chosen, Page.MAIN, null);
            return;
        }
        if (run != null && run.kind() == MenuModel.Kind.REMOVE_GOD) {
            Realm host = realm.host();
            GodCommand.remove(p, new String[] { "remove", realm.id, "confirm" });
            show(p, host, Page.HUB, null);
            return;
        }
        realm.run(() -> {
            Page next = d.open() != null ? d.open() : page;
            if (d.run() != null && !perform(p, d.run())) return;
            // Still looking at this menu? Redraw it (a closed menu stays closed).
            if (p.getOpenInventory().getTopInventory().getHolder() == holder || d.open() != null) {
                show(p, realm, next, d.confirm());
            }
        });
    }

    /** Does what a click asked. Returns false when the menu closes. Current realm is bound. */
    static boolean perform(Player p, MenuModel.Action a) {
        String arg = a.arg();
        switch (a.kind()) {
            case CLOSE -> {
                p.closeInventory();
                return false;
            }
            case WAKE -> {
                God.wake(null);
                say(p, "The god turns its attention to the world.");
            }
            case TOGGLE_PAUSE -> {
                boolean pause = !God.isPaused();
                God.setPaused(pause);
                say(p, pause ? "The god's thoughts are paused (powers and the vessel still work)." : "The god thinks again.");
                TabList.refreshAll();
            }
            case PERSONA -> GodCommand.rebirth(p, arg);
            case MOOD -> mood(p, VesselMood.parse(arg));
            case LOOK -> {
                String look = arg == null ? "auto" : arg;
                if (!look.equals("auto") && !net.bigyous.gptgodmc.god.vessel.VesselLook.NAMES.contains(look)) return true;
                if (Realm.several()) {
                    // With several gods, each keeps its own look.
                    Realm.current().profile().look = look.equals("auto") ? null : look;
                    Realm.current().profile().save();
                } else if (Realm.current().look != null) {
                    say(p, "This realm's god has its own look in config.yml (realms.gods." + Realm.current().id
                            + ".look); change it there.");
                    return true;
                } else {
                    GPTGOD.get().getConfig().set("vessel.look", look);
                    GPTGOD.get().saveConfig();
                }
                God.vessel().restyle();
                say(p, look.equals("auto") ? "Its body now takes the look that suits its nature."
                        : "Its body now wears the " + look + " look.");
            }
            case DIAL -> turnDial(p, arg);
            case DRAFT_ARCHETYPE -> view(p).draftArchetype = arg == null ? "random" : arg;
            case DRAFT_POWER -> view(p).draftPower = arg;
            case DRAFT_NO_NAME -> view(p).draftName = null;
            case DRAFT_NAME -> {
                prompt(p, "godname", null);
                return false;
            }
            case DRAFT_CREATE -> {
                View v = view(p);
                List<String> args = new ArrayList<>(List.of("create", v.draftName == null ? "-" : v.draftName));
                if (v.draftArchetype != null) args.add(v.draftArchetype);
                if (v.draftPower != null) args.add(v.draftPower);
                Realm made = GodCommand.create(p, args.toArray(new String[0]));
                if (made != null) {
                    v.draftName = null;
                    v.draftArchetype = "random";
                    v.draftPower = null;
                    show(p, made, Page.MAIN, null);
                    return false;
                }
            }
            case TOGGLE_DORMANT -> {
                Realm r = Realm.current();
                boolean off = !r.dormant();
                r.profile().dormant = off ? Boolean.TRUE : null;
                r.profile().save();
                if (off) {
                    God.vessel().vanish();
                    net.bigyous.gptgodmc.god.Journal.add(net.bigyous.gptgodmc.god.Journal.Kind.INFO,
                            "Turned off by " + p.getName());
                } else {
                    net.bigyous.gptgodmc.god.Journal.add(net.bigyous.gptgodmc.god.Journal.Kind.INFO,
                            "Turned on by " + p.getName());
                    God.wake(null);
                }
                TabList.refreshAll();
                say(p, off ? "The god is turned off: it no longer thinks or takes form. Its memory is kept."
                        : "The god is turned on again.");
            }
            case TAB -> {
                if (arg != null && Advanced.TABS.contains(arg)) view(p).tab = arg;
            }
            case TUNE -> tune(p, arg);
            case CREATE_GOD -> {
                if (!p.hasPermission(GodCommand.PANTHEON_PERMISSION)) {
                    say(p, "Creating gods needs " + GodCommand.PANTHEON_PERMISSION + ".");
                    return true;
                }
                prompt(p, "create", null);
                return false;
            }
            case POWER -> {
                net.bigyous.gptgodmc.god.Power power = net.bigyous.gptgodmc.god.Power.parse(arg);
                if (power == null) return true;
                Realm.current().profile().power = power.id;
                Realm.current().profile().save();
                net.bigyous.gptgodmc.god.Journal.add(net.bigyous.gptgodmc.god.Journal.Kind.INFO,
                        "Power set to " + power.id + " by " + p.getName());
                say(p, Realm.current().displayName() + " is now a " + power.label.toLowerCase(java.util.Locale.ROOT)
                        + ". It takes hold from its next thought.");
            }
            case MODEL -> {
                if (arg == null || !(arg.equals("default") || MenuLayout.MODELS.contains(arg))) return true;
                Realm.current().profile().model = arg.equals("default") ? null : arg;
                Realm.current().profile().save();
                say(p, Realm.current().displayName() + (arg.equals("default") ? " thinks with the server's model again."
                        : " now thinks with " + MenuLayout.shortModel(arg) + "."));
            }
            case EFFORT -> {
                if (arg == null || !MenuLayout.EFFORT_SLOTS.containsValue(arg)) return true;
                Realm.current().profile().effort = arg.equals("default") ? null : arg;
                Realm.current().profile().save();
                say(p, Realm.current().displayName() + (arg.equals("default") ? "'s effort follows the server again."
                        : " now thinks with effort " + arg + "."));
            }
            case VOICE -> {
                if (arg == null || !arg.matches("[a-z]{2}_[a-z0-9_]+")) return true;
                if (Realm.several()) {
                    Realm.current().profile().voice = arg; // each god keeps its own voice
                    Realm.current().profile().save();
                } else {
                    GPTGOD.get().getConfig().set("voice.tts.voice", arg);
                    GPTGOD.get().saveConfig();
                }
                say(p, "The god now speaks with the voice " + arg + ".");
                sample(p, null);
            }
            case VOICE_EFFECT -> {
                if (arg == null || !(arg.equals("auto") || net.bigyous.gptgodmc.voice.VoiceTone.isEffect(arg))) {
                    return true;
                }
                if (Realm.several()) {
                    Realm.current().profile().effect = arg;
                    Realm.current().profile().save();
                } else {
                    GPTGOD.get().getConfig().set("voice.tts.effect", arg);
                    GPTGOD.get().saveConfig();
                }
                say(p, arg.equals("auto") ? "Its voice now follows its mood." : "Its voice now sounds " + arg + ".");
                sample(p, arg.equals("auto") ? null : arg);
            }
            case VOICE_SAMPLE -> sample(p, null);
            case CALM_HEART -> {
                Memory mem = Memory.get();
                mem.heart.clear(System.currentTimeMillis());
                mem.save();
                say(p, "Its heart lets go of what it felt; it is at peace (until mortals stir it again).");
            }
            case SUMMON -> power(p, "vessel_go", "target", p.getName(), "mode", "teleport");
            case FLY_TO -> power(p, "vessel_go", "target", p.getName(), "mode", "fly");
            case FOLLOW -> power(p, "vessel_go", "target", p.getName(), "mode", "follow");
            case VANISH -> power(p, "vessel_vanish");
            case TRUE_FORM -> power(p, "vessel_true_form", "action", "reveal", "target", p.getName());
            case END_TRUE_FORM -> power(p, "vessel_true_form", "action", "end");
            case DELIVER -> {
                prompt(p, "command", null);
                return false;
            }
            case SELECT_PLAYER -> view(p).selected = arg;
            case PLAYERS_PAGE -> {
                try {
                    view(p).listPage = Math.max(0, Integer.parseInt(arg));
                } catch (NumberFormatException e) {
                    // not ours
                }
            }
            case NAME_PLAYER -> {
                prompt(p, "name", null);
                return false;
            }
            case ADD_NOTE -> {
                prompt(p, "note", arg);
                return false;
            }
            case ADD_DEED -> {
                prompt(p, "deed", arg);
                return false;
            }
            case FAVOR -> favor(p, view(p).selected, arg);
            case CLEAR_NOTES -> {
                int n = Memory.get().clearNotes(arg);
                Memory.get().save();
                say(p, "The god forgets " + n + " notes about " + arg + ".");
            }
            case CLEAR_DEEDS -> {
                int n = Memory.get().clearDeeds(arg);
                Memory.get().save();
                say(p, "The god forgets " + n + " deeds of " + arg + " and the summary of their past.");
            }
            case FORGET_PLAYER -> {
                boolean had = Memory.get().forget(arg);
                Memory.get().save();
                view(p).selected = null;
                say(p, had ? "The god forgets " + arg + " entirely. (Its annals still record what happened.)"
                        : "The god kept no record on " + arg + ".");
            }
            case DELETE_NOTE, DELETE_DEED -> {
                String who = view(p).selected;
                int bar = arg == null ? -1 : arg.indexOf('|');
                if (who == null || bar < 0) return true;
                try {
                    long at = Long.parseLong(arg.substring(0, bar));
                    String text = arg.substring(bar + 1);
                    boolean gone = a.kind() == MenuModel.Kind.DELETE_NOTE ? Memory.get().removeNote(who, at, text)
                            : Memory.get().removeDeed(who, at, text);
                    Memory.get().save();
                    say(p, gone ? "Erased from its memory of " + who + "." : "That memory is already gone.");
                } catch (NumberFormatException e) {
                    // not ours
                }
            }
            case PRESET -> {
                for (Preset preset : MenuLayout.PRESETS) {
                    if (preset.id().equals(arg)) command(p, preset.command(), 0, null);
                }
            }
            case CANCEL_COMMAND -> {
                try {
                    List<Edicts.Edict> gone = Edicts.cancel(Integer.parseInt(arg));
                    say(p, gone.isEmpty() ? "That command is no longer pending." : "Command #" + arg + " cancelled.");
                } catch (NumberFormatException e) {
                    // not ours
                }
            }
            case TOGGLE_SETTING -> toggle(p, arg);
            case BOND -> {
                int bar = arg == null ? -1 : arg.indexOf('|');
                Realm other = bar < 0 ? null : Realm.byId(arg.substring(0, bar));
                if (other == null) {
                    say(p, "That god is gone.");
                    return true;
                }
                try {
                    say(p, net.bigyous.gptgodmc.god.living.Rivalry.operatorSet(Realm.current(), other,
                            arg.substring(bar + 1), p.getName()));
                } catch (IllegalArgumentException ex) {
                    say(p, ex.getMessage());
                }
            }
            default -> {
            }
        }
        TabList.refreshAll();
        return true;
    }

    private static void mood(Player p, VesselMood m) {
        if (m == null) return;
        if (m == VesselMood.CALM) {
            Edicts.book().removeKind("mood");
            if (God.vessel().mood() != VesselMood.CALM) power(p, "vessel_mood", "mood", "calm");
            say(p, "The imposed mood is lifted: the god acts by its own nature again.");
            return;
        }
        int minutes = Edicts.moodMinutes();
        if (command(p, MenuLayout.moodCommand(m, minutes), minutes, "mood")
                && GPTGOD.get().getConfig().getBoolean("vessel.enabled", true)) {
            JsonObject in = new JsonObject();
            in.addProperty("mood", m.id());
            in.addProperty("seconds", Math.min(900, minutes * 60));
            GodCommand.runPower(p, "vessel_mood", in);
        }
    }

    /** Changes how the god feels about a mortal. The extremes leave a note it reads as its own thought. */
    private static void favor(Player p, String who, String button) {
        if (who == null || button == null || button.length() < 5) return;
        int before = Memory.get().favorOf(who);
        int after;
        try {
            after = MenuLayout.favorAfter(before, button);
        } catch (NumberFormatException e) {
            return;
        }
        String note = switch (button) {
            case "set:-10" -> "You have come to despise them; they are beneath your notice.";
            case "set:10" -> "They have become your chosen; you hold them dearest of all mortals.";
            case "set:0" -> "You have set aside whatever you felt about them.";
            default -> null;
        };
        int now = Memory.get().setFavor(who, after, note);
        Memory.get().save();
        say(p, who + "'s favor: " + MenuLayout.favorText(before) + " → " + MenuLayout.favorText(now) + ".");
    }

    /** A player name as typed: an online player's exact name, else a remembered one, else the text if valid. */
    static String resolveName(String text) {
        String t = text.trim();
        Player online = Bukkit.getPlayerExact(t);
        if (online != null) return online.getName();
        Memory.Subject known = Memory.get().findSubject(t);
        if (known != null && known.name != null && !"world".equalsIgnoreCase(known.name)) return known.name;
        return t.matches("[A-Za-z0-9_]{1,16}") ? t : null;
    }

    /** Gives a binding command and tells the operator. */
    static boolean command(Player p, String text, int minutes, String kind) {
        try {
            Edicts.Edict e = Edicts.issue(text, p, "God Manager", minutes, kind);
            say(p, "Command #" + e.number + " delivered: the god must obey." + Edicts.readiness());
            return true;
        } catch (IllegalArgumentException ex) {
            say(p, ex.getMessage());
            return false;
        }
    }

    private static void power(Player p, String name, String... pairs) {
        JsonObject in = new JsonObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            in.addProperty(pairs[i], pairs[i + 1]);
        }
        GodCommand.runPower(p, name, in);
    }

    private static void toggle(Player p, String key) {
        Setting setting = null;
        for (Setting s : MenuLayout.SETTINGS) {
            if (s.key().equals(key)) setting = s;
        }
        if (setting == null) return; // only the listed options can be changed here
        boolean now = !GPTGOD.get().getConfig().getBoolean(key, setting.fallback());
        GPTGOD.get().getConfig().set(key, now);
        GPTGOD.get().saveConfig();
        if (key.equals("vessel.enabled") && !now) {
            for (Realm r : Realm.all()) {
                r.run(() -> {
                    if (God.vessel().isManifested()) God.vessel().vanish();
                });
            }
        }
        say(p, setting.label() + ": " + (now ? "ON" : "OFF") + " (saved to config.yml).");
    }

    // ---- Typing a command in chat --------------------------------------------------------------

    private static void prompt(Player p, String kind, String target) {
        Realm realm = Realm.current();
        Prompt prompt = new Prompt(PROMPT_IDS.incrementAndGet(), realm.id, System.currentTimeMillis() + PROMPT_MS,
                kind, target);
        PROMPTS.put(p.getUniqueId(), prompt);
        p.closeInventory();
        Persona persona = Persona.current();
        String god = persona == null ? "the god" : persona.shortName();
        say(p, switch (kind) {
            case "note" -> "Type a thought " + god + " will hold about " + target + " in chat (only you see it). It "
                    + "will read it as its own memory. Type cancel to stop (60 seconds).";
            case "deed" -> "Type something " + target + " did, in chat (only you see it). It is kept among their "
                    + "deeds, dated today. Type cancel to stop (60 seconds).";
            case "name" -> "Type a player's name in chat to open what " + god + " remembers about them. Type cancel "
                    + "to stop (60 seconds).";
            case "create" -> "Type the new god's name in chat (or - to let it name itself), then optionally an "
                    + "archetype (" + String.join(", ", Persona.archetypes().keySet()) + ") and a power (lesser, "
                    + "greater, supreme). Example: Vorthak wrathful lesser. Type cancel to stop (60 seconds).";
            case "godname" -> "Type just the new god's name in chat: one word, like Vorthak. Type cancel to go "
                    + "back (60 seconds).";
            default -> "Type your command for " + god + " in chat. Only the god will hear it, and it must obey. Type "
                    + "cancel to stop (60 seconds).";
        });
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> {
            if (PROMPTS.remove(id, prompt)) {
                Player still = Bukkit.getPlayer(id);
                if (still != null) say(still, "Nothing was given (timed out).");
            }
        }, PROMPT_MS / 50);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        Prompt prompt = PROMPTS.get(p.getUniqueId());
        if (prompt == null) return;
        if (System.currentTimeMillis() > prompt.deadline()) {
            PROMPTS.remove(p.getUniqueId(), prompt);
            return; // too late: this is ordinary chat
        }
        if (!PROMPTS.remove(p.getUniqueId(), prompt)) return;
        // Nobody else sees it, and the god does not hear it as a mortal's words.
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(GPTGOD.get(), () -> {
            if (!p.isOnline() || !p.hasPermission(PERMISSION)) return;
            if (text.equalsIgnoreCase("cancel") || text.isEmpty()) {
                say(p, "Nothing was given.");
                return;
            }
            Realm found = Realm.byId(prompt.realm());
            Realm realm = found != null ? found : Realm.primary();
            realm.run(() -> {
                switch (prompt.kind()) {
                    case "note" -> {
                        Memory.get().remember(prompt.target(), clip(text));
                        Memory.get().save();
                        say(p, "It will remember that about " + prompt.target() + ".");
                        show(p, realm, Page.PLAYER, null);
                    }
                    case "deed" -> {
                        Memory.get().addDeed(prompt.target(), clip(text));
                        Memory.get().save();
                        say(p, "Recorded among " + prompt.target() + "'s deeds.");
                        show(p, realm, Page.PLAYER, null);
                    }
                    case "name" -> {
                        String name = resolveName(text);
                        if (name == null) {
                            say(p, "\"" + text + "\" is not a player name.");
                            return;
                        }
                        view(p).selected = name;
                        show(p, realm, Page.PLAYER, null);
                    }
                    case "godname" -> {
                        String name = text.replaceAll("[,.;:!?\"]", " ").trim().split("\\s+")[0];
                        String problem = net.bigyous.gptgodmc.god.GodNames.operatorProblem(name);
                        if (problem != null) {
                            say(p, problem);
                        } else {
                            view(p).draftName = name;
                        }
                        show(p, realm, Page.CREATE, null);
                    }
                    case "create" -> {
                        String[] words = text.trim().split("\\s+");
                        String[] args = new String[words.length + 1];
                        args[0] = "create";
                        System.arraycopy(words, 0, args, 1, words.length);
                        Realm made = GodCommand.create(p, args);
                        if (made != null) {
                            show(p, made, Page.MAIN, null);
                        }
                    }
                    default -> command(p, text, 0, null);
                }
            });
        });
    }

    static String clip(String text) {
        String t = text.replace('\n', ' ').trim();
        return t.length() > 200 ? t.substring(0, 199) + "…" : t;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PROMPTS.remove(event.getPlayer().getUniqueId());
        VIEWS.remove(event.getPlayer().getUniqueId());
        FIRST_DROP.remove(event.getPlayer().getUniqueId());
    }

    private static void say(Player p, String text) {
        if (Feedback.take(p, text)) return; // the client mod's God Manager screen shows it instead
        p.sendMessage(Component.text(text, NamedTextColor.LIGHT_PURPLE));
    }
}
