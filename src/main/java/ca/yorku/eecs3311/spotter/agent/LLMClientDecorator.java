package ca.yorku.eecs3311.spotter.agent;

/**
 * Base for behaviour wrapped around a model call.
 *
 * This is the Decorator from the Stage 1 design.
 * Caching and recording are concerns that belong
 * to neither the agents nor the client: putting
 * them in ClaudeClient would mean that class grew
 * a cache, a file writer and flags to switch them
 * on and off, and testing the cache would then
 * need a live model.
 *
 * They compose. Recording around caching around
 * the real client is a valid arrangement, and any
 * of them can be left out.
 */
public abstract class LLMClientDecorator
        implements LLMClient {

    private final LLMClient wrapped;

    protected LLMClientDecorator(
            LLMClient wrapped) {
        if (wrapped == null) {
            throw new IllegalArgumentException(
                "A decorator needs a client to"
                + " wrap");
        }
        this.wrapped = wrapped;
    }

    protected LLMClient wrapped() {
        return wrapped;
    }

    @Override
    public String name() {
        return wrapped.name();
    }

    @Override
    public TokenUsage totalUsage() {
        return wrapped.totalUsage();
    }
}
