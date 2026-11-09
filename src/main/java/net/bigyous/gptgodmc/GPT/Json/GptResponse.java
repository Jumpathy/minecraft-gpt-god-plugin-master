package net.bigyous.gptgodmc.GPT.Json;

import java.util.Map;

public class GptResponse {
    private String id;
    private String object;
    private int created;
    private String model;
    private Choice[] choices;
    private Usage usage;

    public static class Usage {
        public int prompt_tokens;
        public int completion_tokens;
        public int total_tokens;
    }

    public Choice[] getChoices() {
        return choices;
    }
    public int getCreated() {
        return created;
    }
    public String getId() {
        return id;
    }
    public String getModel() {
        return model;
    }

    public String getObject() {
        return object;
    }

    public Usage getUsage() {
        return usage;
    }
}
