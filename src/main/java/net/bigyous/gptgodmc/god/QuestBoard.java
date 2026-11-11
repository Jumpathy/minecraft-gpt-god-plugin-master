package net.bigyous.gptgodmc.god;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

/** Shows the god's active quests on the sidebar and expires overdue ones. Server thread only. */
public final class QuestBoard {
    /** Must match the objective GPTGOD registers on the main board. */
    private static final String OBJECTIVE = "gpt";

    private QuestBoard() {
    }

    /** Sidebars of the gods beyond the first, by realm id. The first god uses the plugin's main board. */
    private static final Map<String, Scoreboard> BOARDS = new HashMap<>();

    /** Each god keeps its own sidebar, so its quests never show to mortals in another god's worlds. */
    private static Scoreboard boardOf(Realm realm) {
        // Gods sharing a world share its sidebar (each quest line names its god).
        realm = realm.host();
        if (realm.isPrimary()) {
            return GPTGOD.SCOREBOARD;
        }
        return BOARDS.computeIfAbsent(realm.id, id -> {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                    Component.text("✦ Divine Quests ✦").color(NamedTextColor.LIGHT_PURPLE));
            return board;
        });
    }

    /**
     * Puts the right sidebar in front of a player: the quests of the god who rules the world they
     * stand in, or the server's ordinary scoreboard where no god rules. Call on join and world change.
     */
    public static void show(Player player) {
        Realm realm = Realm.of(player.getWorld());
        Scoreboard board = realm == null ? Bukkit.getScoreboardManager().getMainScoreboard() : boardOf(realm);
        if (board != null && player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    public static void refresh() {
        Scoreboard board = boardOf(Realm.current());
        Objective objective = board == null ? null : board.getObjective(OBJECTIVE);
        if (objective == null) {
            return;
        }
        for (String entry : board.getEntries()) {
            objective.getScore(entry).resetScore();
        }
        long now = System.currentTimeMillis();
        List<Realm> gods = Realm.current().domainGods();
        boolean any = false;
        for (Realm god : gods) {
            if (Godslayer.isSlain(god)) continue; // a dead god's quests hang unjudged until it returns
            for (Memory.Quest q : god.memory().activeQuests()) {
                any = true;
                String who = "all".equals(q.assignee) ? "All" : q.assignee;
                String line = (Realm.current().livingDomainGods().size() > 1 ? god.displayName() + "·" : "") + who
                        + ": " + q.title;
                if (line.length() > 40) {
                    line = line.substring(0, 40);
                }
                objective.getScore(line).setScore((int) Math.max(0, (q.expiresAt - now) / 60_000));
            }
        }
        objective.setDisplaySlot(any ? DisplaySlot.SIDEBAR : null);
    }

    /** Called every few seconds by the god's heartbeat. */
    public static void tick() {
        for (Memory.Quest q : Memory.get().expireQuests()) {
            EventLogger.addLoggable(new GenericEventLoggable(
                    String.format("Quest %s \"%s\" for %s EXPIRED without being resolved", q.id, q.title, q.assignee),
                    "a quest expired"));
        }
        refresh();
    }

    public static void announceNew(Memory.Quest q) {
        for (Player p : recipients(q)) {
            p.showTitle(Title.title(Component.text("A Divine Quest", Speech.DEEP_VIOLET),
                    Component.text(q.title, Speech.VIOLET),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 0.7f);
        }
    }

    public static void announceResolved(Memory.Quest q) {
        boolean success = "completed".equals(q.status);
        for (Player p : recipients(q)) {
            p.sendMessage(Component.text((success ? "✦ Quest fulfilled: " : "✦ Quest failed: ") + q.title,
                    success ? NamedTextColor.GOLD : NamedTextColor.DARK_RED));
            p.playSound(p.getLocation(), success ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_WITHER_AMBIENT, 0.6f,
                    success ? 1.2f : 0.6f);
        }
    }

    private static List<? extends Player> recipients(Memory.Quest q) {
        if ("all".equals(q.assignee)) {
            return Realm.current().players();
        }
        Player p = Bukkit.getPlayerExact(q.assignee);
        return p == null ? List.of() : List.of(p);
    }
}
