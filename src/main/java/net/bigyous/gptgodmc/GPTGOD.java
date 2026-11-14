package net.bigyous.gptgodmc;

import java.io.File;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.bigyous.gptgodmc.bridge.DebugBridge;
import net.bigyous.gptgodmc.commands.CommandmentsCommand;
import net.bigyous.gptgodmc.commands.GodCommand;
import net.bigyous.gptgodmc.commands.OfferCommand;
import net.bigyous.gptgodmc.commands.PrayCommand;
import net.bigyous.gptgodmc.enums.GptGameMode;
import net.bigyous.gptgodmc.god.Devotion;
import net.bigyous.gptgodmc.god.Edicts;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodClient;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.QuestBoard;
import net.bigyous.gptgodmc.god.TabList;
import net.bigyous.gptgodmc.god.build.Blueprints;
import net.bigyous.gptgodmc.god.vessel.VesselListener;
import net.bigyous.gptgodmc.gui.GodManager;
import net.bigyous.gptgodmc.utils.NicknameCommand;
import net.bigyous.gptgodmc.voice.VoiceModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class GPTGOD extends JavaPlugin {

    public static final String PLUGIN_ID = "gptgodmc";
    public static final Logger LOGGER = LogManager.getLogger(PLUGIN_ID);
    public static Server SERVER;
    public static GptGameMode gameMode = GptGameMode.SANDBOX;
    public static Scoreboard SCOREBOARD;
    public static Team RED_TEAM;
    public static Team BLUE_TEAM;
    public static Objective GPT_OBJECTIVES;

    private static GPTGOD instance;

    public static GPTGOD get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        SERVER = getServer();
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        if (GodSettings.migrateLegacy(getConfig())) {
            LOGGER.info("Moved your old settings into the new config layout.");
        }
        saveConfig();
        // Re-read so sections that only existed as defaults (e.g. personality archetypes) are real.
        reloadConfig();
        Blueprints.schematicFolder();
        if (!new File(Blueprints.schematicFolder(), "catalog.yml").exists()) {
            saveResource("schematics/catalog.yml", false);
        }

        try {
            gameMode = GptGameMode.valueOf(GodSettings.gameMode().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown rounds.gamemode, using SANDBOX");
            gameMode = GptGameMode.SANDBOX;
        }
        setupScoreboard();

        if (GodSettings.roundsEnabled()) {
            String world = GodSettings.startingWorld();
            if (world == null || world.isBlank()) {
                LOGGER.warn("rounds.enabled is true but rounds.starting-world is empty; rounds are off.");
            } else if (WorldManager.loadMap(world)) {
                getServer().getPluginManager().registerEvents(new RoundSystem(), this);
            }
        }

        getServer().getPluginManager().registerEvents(new LoggableEventHandler(), this);
        getServer().getPluginManager().registerEvents(new Presence(), this);
        getServer().getPluginManager().registerEvents(new StructureManager(), this);
        getServer().getPluginManager().registerEvents(new VesselListener(), this);
        getServer().getPluginManager().registerEvents(new Devotion(), this);
        getServer().getPluginManager().registerEvents(new net.bigyous.gptgodmc.god.music.Music(), this);
        getServer().getPluginManager().registerEvents(new GodManager(), this);
        getServer().getPluginManager().registerEvents(new net.bigyous.gptgodmc.god.DemigodPowers(), this);
        getServer().getPluginManager().registerEvents(new net.bigyous.gptgodmc.god.DemigodBane(), this);
        getServer().getPluginManager().registerEvents(new net.bigyous.gptgodmc.god.Senses(), this);
        getServer().getPluginManager().registerEvents(new net.bigyous.gptgodmc.god.Dropped(), this);
        getServer().getPluginManager().registerEvents(new net.bigyous.gptgodmc.god.Sky(), this);
        getServer().getPluginManager().registerEvents(new net.bigyous.gptgodmc.god.DeedWatch(), this);
        net.bigyous.gptgodmc.god.Senses.start();
        net.bigyous.gptgodmc.god.AvatarWatch.enable(this);
        getServer().getPluginManager().registerEvents(new net.bigyous.gptgodmc.god.DeathGuard(), this);
        net.bigyous.gptgodmc.god.DeedWatch.start();
        net.bigyous.gptgodmc.god.DemigodPowers.start();
        getServer().getPluginManager().registerEvents(new TabList(), this);

        GodCommand god = new GodCommand();
        for (String name : new String[] { "god", "sudogod", "try", "tokens", "godinfo" }) {
            bind(name, god);
        }
        PluginCommand pray = getCommand("pray");
        if (pray != null) pray.setExecutor(new PrayCommand());
        PluginCommand nick = getCommand("nickname");
        if (nick != null) nick.setExecutor(new NicknameCommand());
        bind("offer", new OfferCommand());
        PluginCommand swear = getCommand("swear");
        if (swear != null) swear.setExecutor(new net.bigyous.gptgodmc.commands.SwearCommand());
        PluginCommand laws = getCommand("commandments");
        if (laws != null) laws.setExecutor(new CommandmentsCommand());
        PluginCommand gods = getCommand("gods");
        if (gods != null) gods.setExecutor(new net.bigyous.gptgodmc.commands.GodsCommand());
        bind("godslayer", new net.bigyous.gptgodmc.commands.GodslayerCommand());
        // The guide to the gods: handed out on first arrival in a world with a god, /guide brings it back.
        net.bigyous.gptgodmc.guide.Guides guides = new net.bigyous.gptgodmc.guide.Guides();
        getServer().getPluginManager().registerEvents(guides, this);
        bind("guide", guides);

        God.enable();
        net.bigyous.gptgodmc.god.living.Living.enable(this);
        // Worlds the gods made, and the labyrinth (after God.enable: its sentences name realms).
        net.bigyous.gptgodmc.god.worlds.GodWorlds.enable(this);
        // Before the resource pack is built: it holds the relics the gods have already made.
        net.bigyous.gptgodmc.god.art.RelicsModule.enable(this);
        net.bigyous.gptgodmc.god.Godslayer.enable(this);
        // Optional plugins the god can use (WorldGuard, GriefPrevention, Towny, CoreProtect, LibsDisguises).
        net.bigyous.gptgodmc.god.hooks.Hooks.enable(this);
        // Operators' binding commands: expire lapsed ones, remind the god of unfinished ones.
        Bukkit.getScheduler().runTaskTimer(this, Edicts::tickAll, 100L, 100L);
        TabList.start();
        VoiceModule.enable();
        DebugBridge.start();
        net.bigyous.gptgodmc.viewer.MindViewer.start();
        net.bigyous.gptgodmc.god.vessel.VesselPackHost.start();
        net.bigyous.gptgodmc.client.ModLink.enable(this);
        banner();
        // EssentialsX and WorldGuard also define /god: take it back once every plugin has loaded.
        net.bigyous.gptgodmc.commands.GodLabel.enable(this);

        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            Bukkit.getOnlinePlayers().forEach(QuestBoard::show);
            Devotion.refreshAll();
            God.refreshPresence();
        }
    }

    /** Bukkit and Multiverse ask for a world's generator by "gptgodmc:<id>": the god-made void worlds use it. */
    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return net.bigyous.gptgodmc.god.worlds.GodWorlds.generator(id);
    }

    @Override
    public void onDisable() {
        DebugBridge.stop();
        net.bigyous.gptgodmc.viewer.MindViewer.stop();
        net.bigyous.gptgodmc.god.FxShow.stopAll();
        net.bigyous.gptgodmc.client.ModLink.disable();
        net.bigyous.gptgodmc.god.vessel.VesselPackHost.stop();
        net.bigyous.gptgodmc.god.Rites.finishAll();
        VoiceModule.shutdown();
        net.bigyous.gptgodmc.god.Godslayer.disable();
        net.bigyous.gptgodmc.god.AvatarWatch.disable();
        net.bigyous.gptgodmc.god.DemigodRig.stop();
        net.bigyous.gptgodmc.god.living.Living.disable();
        net.bigyous.gptgodmc.god.worlds.GodWorlds.disable();
        net.bigyous.gptgodmc.god.art.RelicsModule.disable();
        God.disable();
        WorldManager.unload();
    }

    private void bind(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    private void setupScoreboard() {
        SCOREBOARD = Bukkit.getScoreboardManager().getNewScoreboard();
        GPT_OBJECTIVES = SCOREBOARD.registerNewObjective("gpt", Criteria.DUMMY,
                Component.text("✦ Divine Quests ✦").color(NamedTextColor.LIGHT_PURPLE));
        if (gameMode == GptGameMode.DEATHMATCH) {
            RED_TEAM = SCOREBOARD.registerNewTeam("Red");
            BLUE_TEAM = SCOREBOARD.registerNewTeam("Blue");
            RED_TEAM.color(NamedTextColor.RED);
            BLUE_TEAM.color(NamedTextColor.BLUE);
            Component redDisplay = Component.text("Red Team").color(NamedTextColor.RED);
            Component blueDisplay = Component.text("Blue Team").color(NamedTextColor.BLUE);
            RED_TEAM.prefix(redDisplay);
            BLUE_TEAM.prefix(blueDisplay);
            RED_TEAM.displayName(redDisplay);
            BLUE_TEAM.displayName(blueDisplay);
        }
    }

    private void banner() {
        Persona p = Persona.current();
        LOGGER.info("==============================================");
        LOGGER.info(" GPTGOD " + getDescription().getVersion() + " — an omniscient god for this world");
        net.bigyous.gptgodmc.god.Llm.installPrices();
        if (!net.bigyous.gptgodmc.god.Llm.anthropic()) {
            LOGGER.info(" Provider: " + net.bigyous.gptgodmc.god.Llm.provider().name().toLowerCase()
                    + (net.bigyous.gptgodmc.god.Llm.baseUrl().isEmpty() ? "" : " at "
                            + net.bigyous.gptgodmc.god.Llm.baseUrl())
                    + (net.bigyous.gptgodmc.god.Llm.lite() ? " (lite profile: core powers, short prompt)" : ""));
        }
        LOGGER.info(" Mind: " + GodSettings.model() + " (effort " + GodSettings.effort() + "), mode "
                + GodSettings.brainMode());
        LOGGER.info(" God: " + (p == null ? "not yet born (archetype: " + Persona.configuredArchetype() + ")"
                : p.title() + " [" + p.archetype + "]"));
        if (GodClient.hasCredentials()) {
            LOGGER.info(" API key: found in " + GodSettings.apiKeySource());
        } else {
            if (net.bigyous.gptgodmc.god.Llm.anthropic()) {
                LOGGER.warn(" API key: MISSING. Run `gptgod setkey <key>` in this console, or set ANTHROPIC_API_KEY.");
                LOGGER.warn(" Get a key at https://console.anthropic.com/");
            } else {
                LOGGER.warn(" Model not ready: " + net.bigyous.gptgodmc.god.Llm.notReady());
            }
        }
        LOGGER.info("==============================================");
    }

    /**
     * Wakes a god when the first mortal arrives in its worlds and lets it sleep when the last one
     * leaves them. (Stepping between worlds is handled in LoggableEventHandler.onWorldChange.)
     */
    private static final class Presence implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            QuestBoard.show(event.getPlayer());
            God.refreshPresence();
            if (event.getPlayer().isOp() && !GodClient.hasCredentials()) {
                event.getPlayer().sendMessage(Component.text(net.bigyous.gptgodmc.god.Llm.anthropic()
                        ? "[GPTGOD] No Anthropic API key yet. Run `gptgod setkey <key>` in the server console."
                        : "[GPTGOD] The god's model is not set up yet (see the llm: section of config.yml).",
                        NamedTextColor.GOLD));
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            // A tick later the player is really gone; any god left with an empty realm goes to sleep.
            Bukkit.getScheduler().runTaskLater(GPTGOD.get(), God::refreshPresence, 20L);
        }
    }
}
