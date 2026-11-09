package net.bigyous.gptgodmc.GPT.Json;

import java.util.List;

public class ClaudeRequest {
    public String model;
    public int max_tokens;
    public String system;
    public List<ClaudeMessage> messages;
    public ClaudeTool[] tools;
    public Object tool_choice;
}
