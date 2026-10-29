package ca.yorku.eecs3311.spotter.agent;

import java.util.Map;

/**
 * The model asking for a tool to be run.
 *
 * @param id        identifies the call so its
 *                  result can be matched back
 * @param toolName  which tool
 * @param arguments what to run it with
 */
public record ToolCall(
        String id,
        String toolName,
        Map<String, String> arguments) {

    public ToolCall {
        if (toolName == null
                || toolName.isBlank()) {
            throw new IllegalArgumentException(
                    "A tool call needs a name");
        }
        id = id == null ? toolName : id;
        arguments = arguments == null
                ? Map.of()
                : Map.copyOf(arguments);
    }
}
