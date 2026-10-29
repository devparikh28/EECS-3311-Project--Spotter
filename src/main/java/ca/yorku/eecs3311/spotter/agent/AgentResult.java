package ca.yorku.eecs3311.spotter.agent;

import java.util.List;
import java.util.Optional;

/**
 * What an agent produced, or why it could not.
 *
 * An agent never returns a half answer. Either
 * the output passed validation, or this carries
 * the reason it did not, so a caller always knows
 * which it is holding.
 *
 * @param <T> what the agent produces
 */
public final class AgentResult<T> {

    private final T value;
    private final String failure;
    private final AgentTrace trace;

    private AgentResult(T value,
                        String failure,
                        AgentTrace trace) {
        this.value = value;
        this.failure = failure;
        this.trace = trace == null
                ? new AgentTrace()
                : trace;
    }

    public static <T> AgentResult<T> success(
            T value, AgentTrace trace) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "A successful result needs"
                    + " a value");
        }
        return new AgentResult<>(value, null,
                trace);
    }

    public static <T> AgentResult<T> failure(
            String reason, AgentTrace trace) {
        if (reason == null
                || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "A failure needs a reason");
        }
        return new AgentResult<>(null, reason,
                trace);
    }

    public boolean succeeded() {
        return value != null;
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    /** Why it failed, in words a lifter can read. */
    public Optional<String> failureReason() {
        return Optional.ofNullable(failure);
    }

    /** What the agent did, for the trace output. */
    public AgentTrace trace() {
        return trace;
    }

    public T orElseThrow() {
        if (value == null) {
            throw new LLMException(failure);
        }
        return value;
    }

    public List<String> steps() {
        return trace.steps();
    }
}
