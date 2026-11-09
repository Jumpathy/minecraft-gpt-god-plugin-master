package net.bigyous.gptgodmc.GPT.Json;

public class ClaudeMessage {
    public String role;
    public String content;

    public ClaudeMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
