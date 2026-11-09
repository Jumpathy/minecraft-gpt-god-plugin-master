package net.bigyous.gptgodmc.GPT.Json;

public class ClaudeResponse {
    public ClaudeContentBlock[] content;
    public String stop_reason;
    public ClaudeUsage usage;

    public static class ClaudeUsage {
        public int input_tokens;
        public int output_tokens;
    }
}
