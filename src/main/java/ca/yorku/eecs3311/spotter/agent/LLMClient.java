package ca.yorku.eecs3311.spotter.agent;

/**
 * A model Spotter can talk to.
 *
 * This is the Strategy from the Stage 1 design,
 * and it is the most load bearing interface in
 * the project. Because the agents depend on this
 * and not on a vendor, the whole agent loop can
 * be tested against a scripted reply with no
 * network and no cost, and the provider can be
 * swapped without touching an agent.
 */
public interface LLMClient {

    /** A name for traces and messages. */
    String name();

    /**
     * Sends a request and returns the reply.
     *
     * @throws LLMException when the call cannot be
     *         completed, so callers that can carry
     *         on without the model may fall back
     */
    LLMResponse complete(LLMRequest request);

    /** Everything this client has consumed. */
    TokenUsage totalUsage();
}
