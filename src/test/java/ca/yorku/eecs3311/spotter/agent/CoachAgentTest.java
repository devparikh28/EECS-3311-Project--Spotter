package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the agent template.
 *
 * These matter more than any others in the
 * project. The whole design rests on one claim:
 * that model output cannot reach the rest of the
 * system without passing validation. These are
 * what make that a guarantee rather than an
 * intention.
 *
 * Everything runs against ScriptedLLMClient, so
 * no network is touched and no tokens are spent.
 */
class CoachAgentTest {

    private ScriptedLLMClient model;
    private ToolManager tools;

    @BeforeEach
    void setUp() {
        model = new ScriptedLLMClient();
        tools = new ToolManager();
    }

    /**
     * A minimal agent: it accepts a reply that is
     * a number, and rejects one that is not
     * positive.
     */
    private final class NumberAgent
            extends CoachAgent<Integer> {

        private final String context;

        NumberAgent(String context) {
            super(model, new PromptBuilder());
            this.context = context;
        }

        @Override
        public String name() {
            return "number";
        }

        @Override
        protected String systemPrompt() {
            return "Answer with one number.";
        }

        @Override
        protected String gatherContext() {
            if (context == null) {
                throw new IllegalStateException(
                        "no data available");
            }
            return context;
        }

        @Override
        protected String describeTask() {
            return "Give me a number.";
        }

        @Override
        protected ToolManager createToolset() {
            return tools;
        }

        @Override
        protected Optional<Integer> parse(
                String reply) {
            try {
                return Optional.of(
                    Integer.parseInt(
                        reply.strip()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        @Override
        protected Optional<String> validate(
                Integer parsed) {
            return parsed > 0
                ? Optional.empty()
                : Optional.of("The number must"
                    + " be above zero, but was "
                    + parsed + ".");
        }
    }

    @Test
    @DisplayName(
        "a valid reply becomes the result")
    void validReplySucceeds() {
        model.reply("42");

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.value()).contains(42);
        assertThat(model.callCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
        "a reply that fails validation is sent"
        + " back once with the reason")
    void invalidReplyIsRetriedWithTheReason() {
        model.reply("-5").reply("10");

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.value()).contains(10);
        assertThat(model.callCount())
                .isEqualTo(2);

        String retryPrompt = model.received()
                .get(1).userPrompt();
        assertThat(retryPrompt)
                .contains("rejected")
                .contains("above zero");
    }

    @Test
    @DisplayName(
        "a second failure returns a failure,"
        + " not the bad value")
    void secondFailureIsNotAccepted() {
        model.reply("-5").reply("-9");

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.value()).isEmpty();
        assertThat(result.failureReason()
                .orElseThrow())
                .contains("above zero");
        assertThat(model.callCount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName(
        "an unreadable reply is retried with an"
        + " instruction about the format")
    void unreadableReplyIsRetried() {
        model.reply("I think about 42 maybe")
             .reply("42");

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        assertThat(result.succeeded()).isTrue();
        assertThat(model.received().get(1)
                .userPrompt())
                .contains("could not be read");
    }

    @Test
    @DisplayName(
        "the model is never retried more than"
        + " once, however wrong it is")
    void retriesAreBounded() {
        model.reply("-1").reply("-2")
             .reply("-3").reply("-4");

        new NumberAgent("ctx").run();

        assertThat(model.callCount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName(
        "a model failure becomes a failed"
        + " result, so a caller can fall back")
    void modelFailureIsReportedNotThrown() {
        model.failWith("connection refused");

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
                .contains("unavailable")
                .contains("connection refused");
    }

    @Test
    @DisplayName(
        "failing to gather context fails before"
        + " the model is ever called")
    void contextFailureSkipsTheModel() {
        AgentResult<Integer> result =
                new NumberAgent(null).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(model.callCount()).isZero();
        assertThat(result.failureReason()
                .orElseThrow())
                .contains("no data available");
    }

    @Test
    @DisplayName(
        "the context gathered first appears in"
        + " the prompt")
    void contextReachesThePrompt() {
        model.reply("42");

        new NumberAgent(
            "squat 1RM is 190 kg").run();

        assertThat(model.lastRequest()
                .userPrompt())
                .contains("190 kg");
    }

    @Test
    @DisplayName(
        "a tool the model asks for is run and"
        + " its result sent back")
    void toolResultIsFedBack() {
        tools.register(new FixedTool("lookup",
                "the answer is 42"));
        model.requestTool("lookup", Map.of())
             .reply("42");

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        assertThat(result.succeeded()).isTrue();
        assertThat(model.received().get(1)
                .userPrompt())
                .contains("the answer is 42");
        assertThat(result.trace().toolsCalled())
                .containsExactly("lookup");
    }

    @Test
    @DisplayName(
        "asking for a tool that does not exist"
        + " is answered, not thrown")
    void unknownToolIsAnswered() {
        model.requestTool("nonsense", Map.of())
             .reply("42");

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        assertThat(result.succeeded()).isTrue();
        assertThat(model.received().get(1)
                .userPrompt())
                .contains("no tool called");
    }

    @Test
    @DisplayName(
        "a tool that throws is reported to the"
        + " model rather than ending the task")
    void failingToolIsReported() {
        tools.register(new ThrowingTool());
        model.requestTool("broken", Map.of())
             .reply("42");

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        assertThat(result.succeeded()).isTrue();
        assertThat(model.received().get(1)
                .userPrompt())
                .contains("failed")
                .contains("database is down");
    }

    @Test
    @DisplayName(
        "a model that only ever asks for tools"
        + " is stopped rather than looping")
    void toolLoopIsBounded() {
        tools.register(new FixedTool("lookup",
                "something"));
        for (int i = 0; i < 20; i++) {
            model.requestTool("lookup",
                    Map.of());
        }

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        assertThat(result.succeeded()).isFalse();
        assertThat(model.callCount())
            .isLessThanOrEqualTo(
                (CoachAgent.MAX_TOOL_ROUNDS + 1)
                * (CoachAgent.MAX_RETRIES + 1)
                + 2);
    }

    @Test
    @DisplayName(
        "only the tools an agent registers are"
        + " offered to the model")
    void onlyRegisteredToolsAreOffered() {
        tools.register(new FixedTool("allowed",
                "fine"));
        model.reply("42");

        new NumberAgent("ctx").run();

        assertThat(model.lastRequest().tools())
            .extracting(ToolSchema::name)
            .containsExactly("allowed");
    }

    @Test
    @DisplayName(
        "the trace records the calls, the tools"
        + " and the rejections")
    void traceRecordsWhatHappened() {
        tools.register(new FixedTool("lookup",
                "data"));
        model.requestTool("lookup", Map.of())
             .reply("-1")
             .reply("7");

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        AgentTrace trace = result.trace();
        assertThat(trace.modelCalls())
                .isEqualTo(3);
        assertThat(trace.toolsCalled())
                .containsExactly("lookup");
        assertThat(trace.validationFailures())
                .isEqualTo(1);
        assertThat(trace.steps())
                .isNotEmpty();
    }

    @Test
    @DisplayName(
        "token usage is accumulated across the"
        + " whole task")
    void usageIsAccumulated() {
        model.reply(new LLMResponse("-1",
                List.of(),
                new TokenUsage(100, 20)))
             .reply(new LLMResponse("5",
                List.of(),
                new TokenUsage(150, 10)));

        AgentResult<Integer> result =
                new NumberAgent("ctx").run();

        assertThat(result.trace().usage()
                .inputTokens()).isEqualTo(250);
        assertThat(result.trace().usage()
                .outputTokens()).isEqualTo(30);
        assertThat(result.trace().usage()
                .total()).isEqualTo(280);
    }

    /** A tool that always returns the same text. */
    private static final class FixedTool
            implements Tool {
        private final String name;
        private final String result;

        FixedTool(String name, String result) {
            this.name = name;
            this.result = result;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolSchema schema() {
            return new ToolSchema(name,
                    "a test tool", Map.of());
        }

        @Override
        public String execute(
                Map<String, String> arguments) {
            return result;
        }
    }

    /** A tool that always fails. */
    private static final class ThrowingTool
            implements Tool {
        @Override
        public String name() {
            return "broken";
        }

        @Override
        public ToolSchema schema() {
            return new ToolSchema("broken",
                    "a tool that fails",
                    Map.of());
        }

        @Override
        public String execute(
                Map<String, String> arguments) {
            throw new IllegalStateException(
                    "database is down");
        }
    }
}
