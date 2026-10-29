package ca.yorku.eecs3311.spotter.agent;

import java.util.List;

/**
 * What the model said.
 *
 * A reply either asks for tools or answers. When
 * it asks, the agent runs them and comes back
 * with the results.
 *
 * @param text      the reply, possibly empty when
 *                  tools were requested
 * @param toolCalls tools the model wants run
 * @param usage     what the call consumed
 */
public record LLMResponse(
        String text,
        List<ToolCall> toolCalls,
        TokenUsage usage) {

    public LLMResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null
                ? List.of()
                : List.copyOf(toolCalls);
        usage = usage == null
                ? TokenUsage.NONE
                : usage;
    }

    public static LLMResponse of(String text) {
        return new LLMResponse(text, List.of(),
                TokenUsage.NONE);
    }

    public boolean wantsTools() {
        return !toolCalls.isEmpty();
    }

    public boolean isEmpty() {
        return text.isBlank() && !wantsTools();
    }
}
