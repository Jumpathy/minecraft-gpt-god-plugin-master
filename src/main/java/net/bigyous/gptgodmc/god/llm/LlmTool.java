package net.bigyous.gptgodmc.god.llm;

import java.util.List;
import java.util.Map;

/**
 * A tool as any provider can be told about it: a name, a description and the JSON schema of its
 * arguments (each property a map with "type", "description" and optionally "enum", "minimum",
 * "maximum", "items").
 */
public record LlmTool(String name, String description, Map<String, Map<String, Object>> properties,
        List<String> required) {
}
