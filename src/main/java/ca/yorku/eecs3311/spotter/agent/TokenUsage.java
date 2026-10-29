package ca.yorku.eecs3311.spotter.agent;

/**
 * What one model call consumed.
 *
 * Tracked because Stage 2 asks for the token and
 * money cost of the AI tools used, and because a
 * cost nobody measures is a cost nobody controls.
 *
 * @param inputTokens  tokens sent
 * @param outputTokens tokens returned
 */
public record TokenUsage(
        long inputTokens,
        long outputTokens) {

    public static final TokenUsage NONE =
            new TokenUsage(0, 0);

    public TokenUsage {
        if (inputTokens < 0
                || outputTokens < 0) {
            throw new IllegalArgumentException(
                    "Token counts cannot be"
                    + " negative");
        }
    }

    public long total() {
        return inputTokens + outputTokens;
    }

    public TokenUsage plus(TokenUsage other) {
        return other == null
            ? this
            : new TokenUsage(
                inputTokens + other.inputTokens,
                outputTokens
                    + other.outputTokens);
    }

    /**
     * Rough cost in US dollars at the given rates
     * per million tokens. Published rates change,
     * so they are passed in rather than compiled
     * in and quietly going stale.
     */
    public double costUsd(
            double inputPerMillion,
            double outputPerMillion) {
        return inputTokens / 1_000_000.0
                * inputPerMillion
            + outputTokens / 1_000_000.0
                * outputPerMillion;
    }
}
