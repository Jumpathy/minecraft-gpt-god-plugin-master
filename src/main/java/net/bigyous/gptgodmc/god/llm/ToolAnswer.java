package net.bigyous.gptgodmc.god.llm;

import net.bigyous.gptgodmc.god.tools.ToolResult;

/** The result of one tool call, to send back to the model. */
public record ToolAnswer(String id, String name, ToolResult result) {
}
