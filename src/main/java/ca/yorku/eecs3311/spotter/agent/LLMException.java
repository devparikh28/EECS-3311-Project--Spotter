package ca.yorku.eecs3311.spotter.agent;

/**
 * Thrown when a model call cannot be completed.
 *
 * Features that can carry on without the model
 * catch this and fall back, which is how the
 * recovery adjustment degrades rather than fails.
 */
public class LLMException
        extends RuntimeException {

    private static final long
            serialVersionUID = 1L;

    public LLMException(String message) {
        super(message);
    }

    public LLMException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
