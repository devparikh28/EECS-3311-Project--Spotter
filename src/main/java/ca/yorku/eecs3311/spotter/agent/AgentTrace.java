package ca.yorku.eecs3311.spotter.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * A record of what an agent did.
 *
 * Written for two readers. A lifter who wants to
 * know why the coach said something, and the
 * Stage 3 behavioural tests, which assert on
 * which tools were called and whether validation
 * passed. The CLI prints this as JSON.
 */
public final class AgentTrace {

    private final List<String> steps =
            new ArrayList<>();
    private final List<String> toolsCalled =
            new ArrayList<>();
    private TokenUsage usage = TokenUsage.NONE;
    private int modelCalls;
    private int validationFailures;

    public void step(String description) {
        if (description != null
                && !description.isBlank()) {
            steps.add(description);
        }
    }

    public void toolCalled(String name,
                           String result) {
        toolsCalled.add(name);
        step("called " + name + " and got: "
                + summarise(result));
    }

    public void modelCalled(TokenUsage used) {
        modelCalls++;
        usage = usage.plus(used);
    }

    public void validationFailed(String reason) {
        validationFailures++;
        step("validation rejected the reply: "
                + reason);
    }

    private static String summarise(
            String result) {
        if (result == null) {
            return "nothing";
        }
        String trimmed = result.strip();
        return trimmed.length() <= 120
            ? trimmed
            : trimmed.substring(0, 117) + "...";
    }

    public List<String> steps() {
        return List.copyOf(steps);
    }

    public List<String> toolsCalled() {
        return List.copyOf(toolsCalled);
    }

    public TokenUsage usage() {
        return usage;
    }

    public int modelCalls() {
        return modelCalls;
    }

    public int validationFailures() {
        return validationFailures;
    }
}
