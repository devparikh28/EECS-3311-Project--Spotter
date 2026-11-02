package ca.yorku.eecs3311.spotter
        .presentation.support;

import ca.yorku.eecs3311.spotter.agent
        .ClaudeClient;
import ca.yorku.eecs3311.spotter.agent
        .LLMClient;
import ca.yorku.eecs3311.spotter.agent
        .ScriptedLLMClient;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

/**
 * Decides which model a command talks to.
 *
 * With an API key configured, commands use the
 * real one. Without a key, or when a
 * demonstration is asked for, they play a
 * prepared reply through the same agent instead:
 * the template, the tools, the parser and the
 * gate all run exactly as they would otherwise,
 * so what appears on screen is the real pipeline
 * rather than a mock up of it.
 *
 * This exists for two reasons. A feature that can
 * only be shown with a working API key cannot be
 * demonstrated or marked when the key is missing.
 * And a demonstration that took a different path
 * through the code than a real run would be
 * worthless as evidence that the real run works.
 */
public final class ModelAccess {

    private ModelAccess() {
    }

    /** Which model a command ended up using. */
    public enum Mode {
        /** The real model, over the network. */
        LIVE,
        /** A prepared reply, no network. */
        PREPARED
    }

    /** A client and what kind it turned out to be. */
    public record Access(
            LLMClient client,
            Mode mode,
            String explanation) {

        public boolean isLive() {
            return mode == Mode.LIVE;
        }
    }

    /**
     * Opens access for one command.
     *
     * @param config         configuration, for
     *                       the key
     * @param forceDemo      true to use the
     *                       prepared reply even
     *                       with a key available
     * @param preparedReply  the reply to play
     *                       when the model is not
     *                       being called
     */
    public static Access open(
            SpotterConfig config,
            boolean forceDemo,
            String preparedReply) {

        if (!forceDemo && config != null
                && config.hasApiKey()) {
            return new Access(
                new ClaudeClient(config),
                Mode.LIVE,
                "Calling the configured model.");
        }

        if (preparedReply == null
                || preparedReply.isBlank()) {
            throw new IllegalStateException(
                "There is no prepared reply for"
                + " this command, so it cannot"
                + " run without an API key");
        }

        ScriptedLLMClient prepared =
                new ScriptedLLMClient();
        prepared.reply(preparedReply);

        String why = forceDemo
            ? "Demonstration mode. A prepared"
              + " reply is being used; the"
              + " agent, its tools, the parser"
              + " and the validator all run"
              + " exactly as they would on a"
              + " real one."
            : "No API key is configured, so a"
              + " prepared reply is being used."
              + " Put a key in .env to call the"
              + " real model.";
        return new Access(prepared,
                Mode.PREPARED, why);
    }
}
