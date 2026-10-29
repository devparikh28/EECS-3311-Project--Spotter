package ca.yorku.eecs3311.spotter.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returns a stored reply when the same request is
 * made twice.
 *
 * Asking an identical question should not be paid
 * for twice. This matters during development and
 * demonstration, where the same command is run
 * repeatedly, and it makes a recorded
 * demonstration reproducible.
 *
 * The cache lives for the life of the process
 * only. Replies are not written to disk, because
 * a stale reply surviving a restart would be
 * worse than paying for a fresh one.
 */
public final class CachingLLMClient
        extends LLMClientDecorator {

    private final Map<String, LLMResponse> cache =
            new LinkedHashMap<>();
    private int hits;
    private int misses;

    public CachingLLMClient(LLMClient wrapped) {
        super(wrapped);
    }

    @Override
    public LLMResponse complete(
            LLMRequest request) {
        String key = keyFor(request);
        LLMResponse stored = cache.get(key);
        if (stored != null) {
            hits++;
            // Usage is reported as nothing: the
            // tokens were paid for on the first
            // call and counting them again would
            // overstate the bill.
            return new LLMResponse(
                stored.text(),
                stored.toolCalls(),
                TokenUsage.NONE);
        }
        misses++;
        LLMResponse fresh =
                wrapped().complete(request);
        cache.put(key, fresh);
        return fresh;
    }

    private static String keyFor(
            LLMRequest request) {
        return request.tier() + "|"
            + request.systemPrompt() + "|"
            + request.userPrompt() + "|"
            + request.tools().size();
    }

    public int hits() {
        return hits;
    }

    public int misses() {
        return misses;
    }

    public int size() {
        return cache.size();
    }

    /** Fraction of calls served without paying. */
    public double hitRate() {
        int total = hits + misses;
        return total == 0
                ? 0
                : (double) hits / total;
    }
}
