package net.bigyous.gptgodmc.GPT;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.GPT.Json.GptFunction;
import net.bigyous.gptgodmc.GPT.Json.GptFunctionReference;
import net.bigyous.gptgodmc.GPT.Json.GptTool;
import net.bigyous.gptgodmc.GPT.Json.Parameter;
import net.bigyous.gptgodmc.interfaces.Function;
import net.bigyous.gptgodmc.utils.GPTUtils;

import java.util.Map;

public class SummarizeLogs {
    private static String context = """
        You are a helpful assistant that will recieve a log of events from a minecraft server, \
        or a historical summary and a log of events. \
        You will create a short summary based on this information that preserves the plot detailed by both, you are viewing these logs from the perspective of a god that rewards %s and punishes %s \
        Keep track of the reputation of each player, if information in the logs isn't important to the plot omit it. Do not add any extra flourishes, just state the facts, pay attention to actions that align with any objectives listed in the objectives and promises that god makes to the players.
        These logs are the history of the server so keep everything in the past tense.
        """;
    private static Gson gson = new Gson();
    private static Function<String> submitSummary = (String args) ->{
        JsonObject argObject = JsonParser.parseString(args).getAsJsonObject();
        GPTGOD.LOGGER.info("summary submitted with args: ", args);
        EventLogger.setSummary(gson.fromJson(argObject.get("summary"), String.class));
    };
    private static Map<String, GptFunction> functionMap = Map.of("submitSummary",
        new GptFunction("submitSummary", "input the summary, keep the summary below 1000 tokens",
            Map.of("summary", new Parameter("string","the summary")),
            submitSummary));
    private static GptTool[] tools = GptActions.wrapFunctions(functionMap);
    private static String formattedContext = String.format(context,
        String.join(",", Personality.getLikes()), String.join(",", Personality.getDislikes()));
    private static IGptAPI gpt = GPTModels.createSummaryAPI(tools)
        .addContext(formattedContext, "prompt")
        .setToolChoice(new GptFunctionReference(functionMap.get("submitSummary")));

    public static void summarize(String log, String summary) {
        // Enforce token budget so the cheaper summary model isn't overwhelmed
        int contextTokens = GPTUtils.countTokens(formattedContext);
        int summaryTokens = summary != null ? GPTUtils.countTokens(summary) : 0;
        // Reserve 2000 tokens for the model output and request overhead
        int logBudget = gpt.getMaxTokens() - contextTokens - summaryTokens - 2000;
        int logTokens = GPTUtils.countTokens(log);
        if (logBudget > 0 && logTokens > logBudget) {
            // Keep the most recent (tail) portion of the log
            int targetLength = (int)(log.length() * (double) logBudget / logTokens);
            int cutPoint = log.length() - targetLength;
            int newlineIdx = log.indexOf('\n', cutPoint);
            log = newlineIdx >= 0 ? log.substring(newlineIdx + 1) : log.substring(cutPoint);
            GPTGOD.LOGGER.info(String.format(
                "Summary log truncated: %d tokens → ~%d tokens to fit %s budget",
                logTokens, logBudget, gpt.getModelName()));
        }

        String content = String.format("Write a short summary that summarizes the events of these logs: %s%s", log,
            summary != null? String.format(":and this History Summary %s", summary): "");
        gpt.addLogs(content, "logs").send(functionMap);
    }
}
