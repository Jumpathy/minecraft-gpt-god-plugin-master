package net.bigyous.gptgodmc.GPT;

import java.util.Map;

import net.bigyous.gptgodmc.GPT.Json.GptFunction;
import net.bigyous.gptgodmc.GPT.Json.GptTool;

public interface IGptAPI {
    IGptAPI addContext(String context, String name);
    IGptAPI addContext(String context, String name, int index);
    IGptAPI addLogs(String logs, String name);
    IGptAPI addLogs(String logs, String name, int index);
    IGptAPI setTools(GptTool[] tools);
    IGptAPI setToolChoice(Object toolChoice);
    void removeLastMessage();
    int getMaxTokens();
    String getModelName();
    void send();
    void send(Map<String, GptFunction> functions);
    boolean isSending();
    void checkRequestBody();
}
