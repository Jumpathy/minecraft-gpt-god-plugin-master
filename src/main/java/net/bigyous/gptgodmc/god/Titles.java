package net.bigyous.gptgodmc.god;

import java.util.List;
import java.util.Locale;

import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.utils.NicknameCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Titles the god bestows ("the Faithful", "Oathbreaker"). Each title lives in the memory of the god who
 * gave it, so a mortal shows the title of the realm they stand in, or none. Shown after the name in chat
 * and the tab list.
 */
public final class Titles {
    public static final int MAX_LENGTH = 24;
    public static final List<String> COLORS = List.of("gold", "yellow", "white", "aqua", "light_purple",
            "dark_purple", "green", "blue", "red", "dark_red", "gray", "dark_gray");
    public static final String DEFAULT_COLOR = "gold";

    private Titles() {
    }

    /** One line of plain text: no color codes, tags, control characters or doubled spaces. */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        String t = raw.replaceAll("[\\u00a7&][0-9a-fk-orA-FK-ORxX#]", "")
                .replaceAll("<[^>]{0,24}>", "")
                .replaceAll("[\\p{Cntrl}\\p{Cf}\\s]+", " ")
                .replaceAll("[\\[\\]{}<>\\\\]", "")
                .trim();
        // Quotes the god may wrap the title in.
        if (t.length() >= 2 && (t.startsWith("\"") && t.endsWith("\"") || t.startsWith("'") && t.endsWith("'"))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    /** A known color name (case-insensitive), or the default. */
    public static String color(String name) {
        if (name == null) return DEFAULT_COLOR;
        String n = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (n.equals("purple")) n = "light_purple";
        return COLORS.contains(n) ? n : DEFAULT_COLOR;
    }

    static TextColor textColor(String name) {
        NamedTextColor c = NamedTextColor.NAMES.value(color(name));
        return c == null ? NamedTextColor.GOLD : c;
    }

    /** The name with its title, as other mortals see it: "Steve the Faithful". */
    public static Component styled(String name, TextColor nameColor, String title, String color) {
        Component base = Component.text(name, nameColor);
        if (title == null || title.isBlank()) return base;
        return base.append(Component.text(" " + title, textColor(color)).decoration(TextDecoration.ITALIC, true));
    }

    /**
     * Shows the player's title (from the god of the world they stand in) in chat and the tab list, or
     * clears it. Call on join, on crossing between realms and whenever a title changes. Server thread.
     */
    public static void apply(Player player) {
        // With several gods, the first god (in order) that gave this mortal a title names them.
        String title = null;
        String color = null;
        for (Realm realm : Realm.godsOf(player.getWorld())) {
            Memory.Subject s = realm.memory().subjectIfAny(player.getName());
            if (s != null && s.title != null) {
                title = s.title;
                color = s.titleColor;
                break;
            }
        }
        // Keep any color others gave the name (e.g. team colors in rounds).
        TextColor nameColor = player.displayName() == null ? null : player.displayName().color();
        player.displayName(styled(player.getName(), nameColor, title, color));
        Component list = styled(player.getName(), null, title, color);
        String nick = NicknameCommand.getNickname(player);
        if (!nick.isBlank()) {
            list = list.append(Component.text(" - " + nick, NamedTextColor.YELLOW));
        }
        player.playerListName(list);
    }
}
