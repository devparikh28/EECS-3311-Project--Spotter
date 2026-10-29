package ca.yorku.eecs3311.spotter.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes a tool to the model: what it is
 * called, what it does, and what it takes.
 *
 * Spotter's own type rather than the LLM
 * library's, so no agent imports LangChain4j and
 * the library can be replaced without touching
 * anything above the client.
 */
public record ToolSchema(
        String name,
        String description,
        Map<String, String> parameters) {

    public ToolSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "A tool needs a name");
        }
        if (description == null
                || description.isBlank()) {
            throw new IllegalArgumentException(
                "A tool needs a description;"
                + " it is what the model uses"
                + " to decide whether to call"
                + " it");
        }
        parameters = parameters == null
                ? Map.of()
                : Map.copyOf(
                    new LinkedHashMap<>(
                        parameters));
    }
}
