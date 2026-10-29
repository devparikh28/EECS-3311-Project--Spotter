package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for caching and recording. */
class LLMDecoratorTest {

    @TempDir
    Path tempDir;

    private ScriptedLLMClient underlying;

    @BeforeEach
    void setUp() {
        underlying = new ScriptedLLMClient();
    }

    private LLMRequest ask(String prompt) {
        return new LLMRequest("rules", prompt,
                List.of(), ModelTier.FAST, 1000);
    }

    @Test
    @DisplayName(
        "the same question is only paid for"
        + " once")
    void repeatedQuestionIsNotPaidForTwice() {
        underlying.reply(new LLMResponse("yes",
            List.of(), new TokenUsage(100, 50)));
        CachingLLMClient caching =
            new CachingLLMClient(underlying);

        LLMResponse first =
                caching.complete(ask("same"));
        LLMResponse second =
                caching.complete(ask("same"));

        assertThat(first.text())
                .isEqualTo("yes");
        assertThat(second.text())
                .isEqualTo("yes");
        assertThat(underlying.callCount())
                .isEqualTo(1);
        assertThat(caching.hits()).isEqualTo(1);
        assertThat(caching.misses())
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
        "a cached reply reports no tokens, so"
        + " the bill is not overstated")
    void cachedReplyCostsNothing() {
        underlying.reply(new LLMResponse("yes",
            List.of(), new TokenUsage(100, 50)));
        CachingLLMClient caching =
            new CachingLLMClient(underlying);

        caching.complete(ask("same"));
        LLMResponse cached =
                caching.complete(ask("same"));

        assertThat(cached.usage().total())
                .isZero();
    }

    @Test
    @DisplayName(
        "a different question is asked again")
    void differentQuestionIsAsked() {
        underlying.reply("one").reply("two");
        CachingLLMClient caching =
            new CachingLLMClient(underlying);

        assertThat(caching.complete(ask("a"))
                .text()).isEqualTo("one");
        assertThat(caching.complete(ask("b"))
                .text()).isEqualTo("two");
        assertThat(underlying.callCount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName(
        "the exchange is written to the"
        + " recording")
    void exchangeIsRecorded() throws Exception {
        underlying.reply("the answer");
        Path log = tempDir.resolve(
                "nested/run.log");
        RecordingLLMClient recording =
            new RecordingLLMClient(underlying,
                    log);

        recording.complete(
                ask("what is the answer"));

        assertThat(Files.exists(log)).isTrue();
        String written =
                Files.readString(log);
        assertThat(written)
            .contains("what is the answer")
            .contains("the answer");
        assertThat(recording.recorded())
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
        "a failure is recorded too, which is"
        + " what makes a failure analysable")
    void failureIsRecorded() throws Exception {
        underlying.failWith("rate limited");
        Path log = tempDir.resolve("run.log");
        RecordingLLMClient recording =
            new RecordingLLMClient(underlying,
                    log);

        try {
            recording.complete(ask("anything"));
        } catch (LLMException expected) {
            // recorded before rethrowing
        }

        assertThat(Files.readString(log))
            .contains("failed")
            .contains("rate limited");
    }

    @Test
    @DisplayName(
        "recording around caching around the"
        + " real client works as one chain")
    void decoratorsCompose() throws Exception {
        underlying.reply("answer");
        Path log = tempDir.resolve("run.log");

        LLMClient chain =
            new RecordingLLMClient(
                new CachingLLMClient(underlying),
                log);

        chain.complete(ask("question"));
        chain.complete(ask("question"));

        assertThat(underlying.callCount())
                .isEqualTo(1);
        assertThat(Files.readString(log))
                .contains("question");
    }

    @Test
    @DisplayName(
        "a decorator reports the wrapped"
        + " client's name and usage")
    void decoratorIsTransparent() {
        underlying.reply(new LLMResponse("x",
            List.of(), new TokenUsage(10, 5)));
        LLMClient caching =
            new CachingLLMClient(underlying);

        caching.complete(ask("q"));

        assertThat(caching.name())
                .isEqualTo("scripted");
        assertThat(caching.totalUsage().total())
                .isEqualTo(15);
    }
}
