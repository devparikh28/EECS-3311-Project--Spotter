package ca.yorku.eecs3311.spotter.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A model that replays prepared replies.
 *
 * This is what makes the agent loop testable. The
 * whole path, prompt to tools to parse to
 * validate, runs against this with no network, no
 * key and no cost, so a test can state exactly
 * what the model said and check what the system
 * did with it, including when the model says
 * something wrong.
 */
public final class ScriptedLLMClient
        implements LLMClient {

    private final Deque<LLMResponse> scripted =
            new ArrayDeque<>();
    private final List<LLMRequest> received =
            new ArrayList<>();
    private TokenUsage usage = TokenUsage.NONE;
    private LLMException failure;

    /** Queues a plain text reply. */
    public ScriptedLLMClient reply(String text) {
        scripted.add(LLMResponse.of(text));
        return this;
    }

    /** Queues a prepared reply. */
    public ScriptedLLMClient reply(
            LLMResponse response) {
        scripted.add(response);
        return this;
    }

    /** Queues a request for tools. */
    public ScriptedLLMClient requestTool(
            String toolName,
            java.util.Map<String, String> args) {
        scripted.add(new LLMResponse("",
            List.of(new ToolCall(toolName,
                    toolName, args)),
            TokenUsage.NONE));
        return this;
    }

    /** Makes every call fail, to test fallbacks. */
    public ScriptedLLMClient failWith(
            String message) {
        this.failure =
                new LLMException(message);
        return this;
    }

    @Override
    public String name() {
        return "scripted";
    }

    @Override
    public LLMResponse complete(
            LLMRequest request) {
        received.add(request);
        if (failure != null) {
            throw failure;
        }
        if (scripted.isEmpty()) {
            throw new LLMException(
                "The script ran out after "
                + received.size()
                + " call(s). The agent asked"
                + " the model more times than"
                + " the test expected.");
        }
        LLMResponse response =
                scripted.removeFirst();
        usage = usage.plus(response.usage());
        return response;
    }

    @Override
    public TokenUsage totalUsage() {
        return usage;
    }

    /** Every request made, in order. */
    public List<LLMRequest> received() {
        return List.copyOf(received);
    }

    public LLMRequest lastRequest() {
        if (received.isEmpty()) {
            throw new IllegalStateException(
                    "No request was made");
        }
        return received.get(
                received.size() - 1);
    }

    public int callCount() {
        return received.size();
    }

    public boolean scriptExhausted() {
        return scripted.isEmpty();
    }
}
