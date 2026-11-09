package net.bigyous.gptgodmc.GPT;

import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.enums.GptGameMode;

public class Prompts {
    private static FileConfiguration config = JavaPlugin.getPlugin(GPTGOD.class).getConfig();
    private static Map<GptGameMode, String> defaultPrompts = Map.ofEntries(
        Map.entry(GptGameMode.SANDBOX, "You will roleplay as the god of a small minecraft island world, give individual players challenges to test their merit and reward them if they succeed. You will recieve information about what has happened on the island. Only use tool calls, other responses will be ignored. **CRITICAL RULES**: " +
            "- Review the event log carefully before acting " +
            "- DO NOT repeat the same action on the same player unless their behavior has significantly changed " +
            "- If you recently healed someone, let them prove themselves before healing again " +
            "- If you recently punished someone, give them time to change their behavior " +
            "- Variety is divine—use different tools and target different players each cycle " +
            "- Silence is acceptable if no action is warranted"),
        Map.entry(GptGameMode.DEATHMATCH, "You will roleplay as the god of a small minecraft world, the players are split into two teams that must fight to the death. Each team spawns on their own floating island. You will give the teams challenges to complete and reward the teams that succeed. Only use tool calls, other responses will be ignored.")
    );
    private static Map<GptGameMode, String> configKeys = Map.of(
        GptGameMode.SANDBOX, "sandboxPrompt",
        GptGameMode.DEATHMATCH, "deathmatchPrompt"
    );

    public static String getGamemodePrompt(GptGameMode gamemode){
        String override = config.getString("promptOverride");
        if (override != null && !override.isBlank()) {
            return override;
        }
        String gamemodeKey = configKeys.get(gamemode);
        if (gamemodeKey != null) {
            String gamemodePrompt = config.getString(gamemodeKey);
            if (gamemodePrompt != null && !gamemodePrompt.isBlank()) {
                return gamemodePrompt;
            }
        }
        return defaultPrompts.get(gamemode);
    }

}
