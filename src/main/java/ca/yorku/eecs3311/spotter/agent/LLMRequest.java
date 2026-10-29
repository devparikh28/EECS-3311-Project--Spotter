package ca.yorku.eecs3311.spotter.agent;

import java.util.List;

/**
 * One request to the model.
 *
 * @param systemPrompt who the model is and what
 *                     the rules are
 * @param userPrompt   the task, with its context
 * @param tools        what it may look up
 * @param tier         which model to use
 * @param maxTokens    upper bound on the reply
 */
public record LLMRequest(
        String systemPrompt,
        String userPrompt,
        List<ToolSchema> tools,
        ModelTier tier,
        int maxTokens) {

    public LLMRequest {
        if (userPrompt == null
                || userPrompt.isBlank()) {
            throw new IllegalArgumentException(
                    "A request needs a prompt");
        }
        systemPrompt = systemPrompt == null
                ? ""
                : systemPrompt;
        tools = tools == null
                ? List.of()
                : List.copyOf(tools);
        tier = tier == null
                ? ModelTier.FAST
                : tier;
        if (maxTokens <= 0) {
            maxTokens = 4096;
        }
    }

    public boolean hasTools() {
        return !tools.isEmpty();
    }
}
