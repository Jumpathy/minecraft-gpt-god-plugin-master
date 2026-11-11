package net.bigyous.gptgodmc.god;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The god at the top of the player list (Tab): its name and epithet in the header, coloured by its
 * archetype (or its mood while it has one), a short line saying what it is doing, and a footer.
 * Each mortal sees the god of the realm they stand in. A real fake-player row would need packets, so
 * the header is used instead. Refreshed on join, on changing worlds and every 5 seconds, sending only
 * when the text changed. Config: god.tab-list.
 */
public final class TabList implements Listener {
    /** What each player was last sent, so the timer only re-sends changes. */
    private static final Map<UUID, Component> SENT = new ConcurrentHashMap<>();

    /** What the god is doing, as one short line. */
    public enum State {
        UNBORN("has not yet awoken"),
        SLEEPS("sleeps"),
        SILENT("is paused, silent"),
        WATCHES("watches unseen"),
        WALKS("walks among you"),
        TRUE_FORM("has revealed its true form"),
        SLAIN("has been slain");

        public final String line;

        State(String line) {
            this.line = line;
        }
    }

    /** The header and footer one player should see. */
    public record Text(Component header, Component footer) {
        Component key() {
            return header.append(Component.newline()).append(footer);
        }
    }

    public static boolean enabled() {
        return GPTGOD.get() == null || GPTGOD.get().getConfig().getBoolean("god.tab-list", true);
    }

    // ---- Pure text -----------------------------------------------------------------------------

    /** Colour of an archetype from config (unknown ones get violet). */
    public static TextColor archetypeColor(String archetype) {
        if (archetype == null) return Speech.VIOLET;
        return switch (archetype.toLowerCase(Locale.ROOT)) {
            case "benevolent" -> TextColor.color(0xFFE066);
            case "chill" -> TextColor.color(0x6FD3FF);
            case "trickster" -> TextColor.color(0x7CF06B);
            case "evil" -> TextColor.color(0xD0302A);
            case "wrathful" -> TextColor.color(0xFF8A1F);
            case "cosmic" -> TextColor.color(0xB070FF);
            default -> Speech.VIOLET;
        };
    }

    /** The colour the god's name is shown in: its mood's while it has one, else its archetype's. */
    public static TextColor nameColor(String archetype, VesselMood mood) {
        if (mood == VesselMood.OMINOUS) {
            return TextColor.color(0x9A5CD6); // its glow is too dark to read on the list's background
        }
        if (mood != null && mood != VesselMood.CALM && mood.outline != null) {
            return TextColor.color(mood.outline.asRGB());
        }
        return archetypeColor(archetype);
    }

    /** Builds the header and footer. name may be null (not yet born). */
    public static Text compose(String name, String epithet, String archetype, VesselMood mood, State state) {
        return compose(name, epithet, archetype, mood, state, false);
    }

    /** @param thinking a thought is running right now: the state line ends in "thinking…" */
    public static Text compose(String name, String epithet, String archetype, VesselMood mood, State state,
            boolean thinking) {
        TextColor color = nameColor(archetype, mood);
        Component header = Component.text("✦ ", Speech.DEEP_VIOLET)
                .append(Component.text(name == null || name.isBlank() ? "The God" : name, color)
                        .decoration(TextDecoration.BOLD, true))
                .append(Component.text(" ✦", Speech.DEEP_VIOLET));
        if (epithet != null && !epithet.isBlank()) {
            header = header.append(Component.newline())
                    .append(Component.text(epithet, color).decoration(TextDecoration.ITALIC, true));
        }
        String line = state.line;
        if (mood != null && mood != VesselMood.CALM && !quiet(state)) {
            line = line + " · " + mood.id();
        }
        if (thinking && !quiet(state) && state != State.SILENT) {
            line = line + " · thinking…";
        }
        header = header.append(Component.newline()).append(Component.text(line, NamedTextColor.GRAY));
        Component footer = Component.text("/pray to be heard", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, true);
        return new Text(header, footer);
    }

    /** States that show no mood or thought: it sleeps, is unborn, or lies slain. */
    private static boolean quiet(State s) {
        return s == State.SLEEPS || s == State.UNBORN || s == State.SLAIN;
    }

    /** One god's row when several share a world. */
    public record Row(String name, String epithet, String archetype, VesselMood mood, State state, boolean thinking) {
    }

    /** At most this many gods are listed by name; the rest are counted. */
    static final int MAX_ROWS = 5;

    /** The header when several gods share the world: one row per god, each in its own colour. */
    public static Text composeMany(List<Row> rows) {
        Component header = Component.text("✦ The Gods of this World ✦", Speech.DEEP_VIOLET)
                .decoration(TextDecoration.BOLD, true);
        int shown = 0;
        for (Row r : rows) {
            if (shown++ >= MAX_ROWS) break;
            TextColor color = nameColor(r.archetype(), r.mood());
            String line = r.state().line;
            if (r.mood() != null && r.mood() != VesselMood.CALM && !quiet(r.state())) {
                line = line + " · " + r.mood().id();
            }
            if (r.thinking() && !quiet(r.state()) && r.state() != State.SILENT) {
                line = line + " · thinking…";
            }
            header = header.append(Component.newline())
                    .append(Component.text(r.name() == null || r.name().isBlank() ? "An unborn god" : r.name(), color)
                            .decoration(TextDecoration.BOLD, true))
                    .append(r.epithet() == null || r.epithet().isBlank() ? Component.empty()
                            : Component.text(", " + r.epithet(), color).decoration(TextDecoration.ITALIC, true))
                    .append(Component.text(" — " + line, NamedTextColor.GRAY));
        }
        if (rows.size() > MAX_ROWS) {
            header = header.append(Component.newline())
                    .append(Component.text("+" + (rows.size() - MAX_ROWS) + " more", NamedTextColor.GRAY));
        }
        Component footer = Component.text("/pray <god> <words> to be heard · /gods", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, true);
        return new Text(header, footer);
    }

    /** What every god of this world looks like in the list right now. Server thread. */
    public static Text textForAll(List<Realm> gods) {
        if (gods.size() == 1) return textFor(gods.get(0));
        List<Row> rows = new java.util.ArrayList<>();
        for (Realm r : gods) {
            Persona p = r.persona;
            Vessel v = r.vesselIfCreated();
            rows.add(new Row(p == null ? null : p.shortName(), p == null ? null : p.epithet,
                    p == null ? null : p.archetype, v == null ? null : v.mood(), stateOf(r, p), isThinking(r)));
        }
        return composeMany(rows);
    }

    /** The state line for the current realm's god. */
    static State stateOf(Realm r, Persona p) {
        if (p == null) return State.UNBORN;
        if (Godslayer.isSlain(r)) return State.SLAIN;
        if (!r.awake) return State.SLEEPS;
        Vessel v = r.vesselIfCreated();
        if (v != null && v.inTrueForm()) return State.TRUE_FORM;
        if (r.paused) return State.SILENT;
        if (v != null && (v.hasBody() || v.isMaterializing())) return State.WALKS;
        return State.WATCHES;
    }

    /** What the god of this realm looks like in the list right now. Server thread. */
    public static Text textFor(Realm r) {
        Persona p = r.persona;
        Vessel v = r.vesselIfCreated();
        VesselMood mood = v == null ? null : v.mood();
        return compose(p == null ? null : p.shortName(), p == null ? null : p.epithet, p == null ? null : p.archetype,
                mood, stateOf(r, p), isThinking(r));
    }

    private static boolean isThinking(Realm r) {
        GodBrain b = r.brainIfStarted();
        return b != null && b.isThinking();
    }

    /** Refreshes soon on the server thread (from any thread), so "thinking…" appears and clears at once. */
    public static void refreshSoon() {
        GPTGOD plugin = GPTGOD.get();
        if (plugin == null || !plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, TabList::refreshAll);
        } catch (IllegalStateException e) {
            // shutting down
        }
    }

    // ---- Sending -------------------------------------------------------------------------------

    /** Shows (or clears) the header for one player, if it changed. Server thread. */
    public static void refresh(Player player) {
        // The slain are not listed; with every god dead the header is cleared.
        List<Realm> gods = Realm.livingGodsOf(player.getWorld());
        if (!enabled() || gods.isEmpty()) {
            if (SENT.remove(player.getUniqueId()) != null) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
            return;
        }
        Text t = textForAll(gods);
        Component key = t.key();
        if (key.equals(SENT.get(player.getUniqueId()))) return;
        SENT.put(player.getUniqueId(), key);
        player.sendPlayerListHeaderAndFooter(t.header(), t.footer());
    }

    public static void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    /** Starts the 5-second refresh. Call once when the plugin enables. */
    public static void start() {
        Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), TabList::refreshAll, 60L, 100L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        SENT.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> {
            if (p.isOnline()) refresh(p);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SENT.remove(event.getPlayer().getUniqueId());
    }
}
