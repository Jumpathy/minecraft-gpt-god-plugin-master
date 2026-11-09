package net.bigyous.gptgodmc.GPT;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.GPT.Json.GptModel;
import net.bigyous.gptgodmc.GPT.Json.GptTool;

public class GPTModels {
    private static FileConfiguration config = JavaPlugin.getPlugin(GPTGOD.class).getConfig();
    public static final GptModel GPT_4o = new GptModel("gpt-4o", 100000);
    public static final GptModel GPT_4o_mini = new GptModel("gpt-4o-mini", 85000);
    public static final GptModel CLAUDE_SONNET = new GptModel("claude-sonnet-4-6", 100000);
    public static final GptModel CLAUDE_HAIKU = new GptModel("claude-haiku-4-5-20251001", 100000);
    public static final GptModel CLAUDE_OPUS = new GptModel("claude-opus-4-6", 100000);

    public static GptModel getMainModel(){
        String modelName;
        if (config.isSet("gpt-model-name")) {
            modelName = config.getString("gpt-model-name");
        } else if (config.isSet("use-gpt-4")) {
            // for passivity
            modelName = config.getBoolean("use-gpt-4")? "gpt-4o": "gpt-4o-mini";
        } else {
            throw new RuntimeException("Please set a value for gpt-model-name.");
        }

        int tokenLimit;

        if (config.isSet("gpt-model-token-limit")) {
            tokenLimit = config.getInt("gpt-model-token-limit");
        } else {
            tokenLimit = switch (modelName) {
                case "gpt-4o", "gpt-4o-2024-08-06" -> 100000;
                case "gpt-4o-mini" -> 85000;
                default -> throw new RuntimeException(String.format("Could not automatically determine token limit for %s. Please set gpt-model-token-limit in the config.", modelName));
            };
        }
        return new GptModel(modelName, tokenLimit) ;
    }

    public static boolean isClaudeConfigured() {
        String key = config.getString("claudeApiKey", "");
        return key != null && !key.isBlank();
    }

    public static GptModel getClaudeModel() {
        String modelName = config.getString("claude-model-name", "claude-sonnet-4-6");
        int tokenLimit = config.getInt("claude-model-token-limit", 100000);
        return new GptModel(modelName, tokenLimit);
    }

    public static IGptAPI createAPI(GptTool[] tools) {
        if (isClaudeConfigured()) {
            return new ClaudeAPI(getClaudeModel(), tools);
        }
        return new GptAPI(getMainModel(), tools);
    }

    public static GptModel getSummaryModel() {
        String configuredName = config.getString("summary-model-name", "");
        int configuredLimit = config.getInt("summary-model-token-limit", 0);

        if (isClaudeConfigured()) {
            String modelName = (configuredName != null && !configuredName.isBlank())
                    ? configuredName
                    : "claude-haiku-4-5-20251001";
            int tokenLimit = configuredLimit > 0 ? configuredLimit : 100000;
            return new GptModel(modelName, tokenLimit);
        } else {
            String modelName = (configuredName != null && !configuredName.isBlank())
                    ? configuredName
                    : "gpt-4o-mini";
            int tokenLimit = configuredLimit > 0 ? configuredLimit : 85000;
            return new GptModel(modelName, tokenLimit);
        }
    }

    public static IGptAPI createSummaryAPI(GptTool[] tools) {
        GptModel summaryModel = getSummaryModel();
        if (isClaudeConfigured()) {
            return new ClaudeAPI(summaryModel, tools);
        }
        return new GptAPI(summaryModel, tools);
    }
}
