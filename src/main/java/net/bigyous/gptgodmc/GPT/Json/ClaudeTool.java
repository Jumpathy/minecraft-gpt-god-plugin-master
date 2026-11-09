package net.bigyous.gptgodmc.GPT.Json;

import java.util.Map;

public class ClaudeTool {
    public String name;
    public String description;
    public ClaudeInputSchema input_schema;

    public ClaudeTool(String name, String description, ClaudeInputSchema inputSchema) {
        this.name = name;
        this.description = description;
        this.input_schema = inputSchema;
    }

    public static ClaudeTool fromGptFunction(GptFunction f) {
        Map<String, Parameter> properties = f.getParameters().getProperties();
        String[] required = properties.keySet().toArray(new String[0]);
        ClaudeInputSchema schema = new ClaudeInputSchema(properties, required);
        return new ClaudeTool(f.getName(), f.getDescription(), schema);
    }

    public static class ClaudeInputSchema {
        public String type = "object";
        public Map<String, Parameter> properties;
        public String[] required;

        public ClaudeInputSchema(Map<String, Parameter> properties, String[] required) {
            this.properties = properties;
            this.required = required;
        }
    }
}
