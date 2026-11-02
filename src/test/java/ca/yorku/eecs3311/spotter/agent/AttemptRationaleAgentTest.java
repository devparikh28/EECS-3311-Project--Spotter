package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.agent.agents
        .AttemptRationaleAgent;
import ca.yorku.eecs3311.spotter.domain
        .AttemptCalculator;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .MeetAttempts;
import ca.yorku.eecs3311.spotter.domain.SetEntry;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;

/**
 * Tests for F08's explanation, the narrowest job
 * any agent in Spotter has.
 *
 * The three attempts are arithmetic and are
 * settled before the model is asked anything. All
 * it supplies is two sentences of prose over
 * figures it has been handed, so what these tests
 * mostly establish is that it cannot change a
 * number by talking about it, and that prose
 * containing a figure nobody calculated never
 * reaches the lifter.
 */
class AttemptRationaleAgentTest {

    private static final LocalDate MEET =
            LocalDate.of(2026, 12, 5);
    private static final double E1RM = 200.0;

    private Database database;
    private ScriptedLLMClient model;
    private MeetAttempts attempts;
    private List<WorkoutSession> history;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        attempts = new AttemptCalculator()
            .calculateMeetAttempts(Lift.SQUAT,
                    E1RM);

        Exercise squat = new Exercise(1,
            "Back Squat", Set.of("quads"),
            "squat", Set.of(Equipment.BARBELL),
            Lift.SQUAT);

        history = List.of(
            new WorkoutSession(0,
                MEET.minusWeeks(8), "test",
                List.of(SetEntry.of(squat,
                        150.0, 5, 8))),
            new WorkoutSession(0,
                MEET.minusWeeks(1), "test",
                List.of(SetEntry.of(squat,
                        162.5, 5, 8))));

        model = new ScriptedLLMClient();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private AttemptRationaleAgent agent() {
        return new AttemptRationaleAgent(model,
            new PromptBuilder(), attempts, E1RM,
            history, MEET,
            new ResponseParser(
                new SqliteExerciseCatalog(
                        database)),
            new PlanValidator());
    }

    /** An explanation using only real figures. */
    private String soundRationale() {
        return String.format(
            "Open at %.1f kg, which you have"
            + " done for five reps in training,"
            + " then take %.1f kg for a personal"
            + " best. If that goes up fast, %.1f"
            + " kg is there.",
            attempts.opener(),
            attempts.second(),
            attempts.third());
    }

    private static String asJson(
            String rationale) {
        return "{\"rationale\":\""
            + rationale.replace("\"", "\\\"")
            + "\"}";
    }

    @Test
    @DisplayName(
        "an explanation using only the"
        + " calculated figures is accepted")
    void soundExplanationIsAccepted() {
        model.reply(asJson(soundRationale()));

        AgentResult<MeetAttempts> result =
                agent().run();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.value().orElseThrow()
                .rationale())
            .contains("personal best");
    }

    @Test
    @DisplayName(
        "the three weights come through"
        + " untouched, whatever the reply says")
    void theWeightsAreNotReadFromTheReply() {
        model.reply("{\"rationale\":\""
            + soundRationale()
            + "\",\"opener\":999.0,"
            + "\"third\":5.0}");

        MeetAttempts explained = agent().run()
                .value().orElseThrow();

        assertThat(explained.opener())
            .isEqualTo(attempts.opener());
        assertThat(explained.second())
            .isEqualTo(attempts.second());
        assertThat(explained.third())
            .isEqualTo(attempts.third());
        assertThat(explained.lift())
            .isEqualTo(Lift.SQUAT);
    }

    @Test
    @DisplayName(
        "a figure nobody calculated is"
        + " rejected")
    void aninventedFigureIsRejected() {
        String wrong = String.format(
            "Open at %.1f kg, then %.1f kg,"
            + " then reach for %.1f kg.",
            attempts.opener() + 1.0,
            attempts.second(),
            attempts.third());
        model.reply(asJson(wrong))
             .reply(asJson(wrong));

        AgentResult<MeetAttempts> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("not one of the calculated"
                + " numbers");
    }

    @Test
    @DisplayName(
        "a stray figure is corrected on the"
        + " retry")
    void theRetryCorrectsAStrayFigure() {
        String wrong = String.format(
            "Open at %.1f kg, then %.1f kg,"
            + " then %.1f kg.",
            attempts.opener() + 1.0,
            attempts.second(),
            attempts.third());
        model.reply(asJson(wrong))
             .reply(asJson(soundRationale()));

        AgentResult<MeetAttempts> result =
                agent().run();

        assertThat(result.succeeded()).isTrue();
        assertThat(model.received().get(1)
                .userPrompt())
            .contains("not one of the calculated"
                + " numbers");
    }

    @Test
    @DisplayName(
        "an explanation leaving out an attempt"
        + " is rejected")
    void allThreeAttemptsMustBeNamed() {
        String partial = String.format(
            "Open at %.1f kg and then take %.1f"
            + " kg. Play the rest by feel.",
            attempts.opener(),
            attempts.second());
        model.reply(asJson(partial))
             .reply(asJson(partial));

        AgentResult<MeetAttempts> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("all three attempts");
    }

    @Test
    @DisplayName(
        "the percentages the attempts came from"
        + " may be quoted")
    void thePercentagesAreAllowed() {
        model.reply(asJson(String.format(
            "Your opener of %.1f kg is 91"
            + " percent of your estimated 200"
            + " kg max, %.1f kg is 96 percent,"
            + " and %.1f kg is 101 percent.",
            attempts.opener(),
            attempts.second(),
            attempts.third())));

        assertThat(agent().run().succeeded())
            .isTrue();
    }

    @Test
    @DisplayName(
        "an explanation running long is"
        + " rejected")
    void anOverlongExplanationIsRejected() {
        String padded = soundRationale()
            + " You have trained well."
                .repeat(40);
        model.reply(asJson(padded))
             .reply(asJson(padded));

        AgentResult<MeetAttempts> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("characters");
    }

    @Test
    @DisplayName(
        "a reply with no rationale text fails"
        + " at parse")
    void aMissingRationaleFailsAtParse() {
        model.reply("{\"notes\":\"go heavy\"}")
             .reply("{\"notes\":\"go heavy\"}");

        AttemptRationaleAgent agent = agent();

        assertThat(agent.run().succeeded())
            .isFalse();
        assertThat(agent.parseProblems())
            .anyMatch(problem -> problem
                .contains("rationale"));
    }

    @Test
    @DisplayName(
        "no tools are offered, because every"
        + " figure it may use is in the prompt")
    void noToolsAreOffered() {
        model.reply(asJson(soundRationale()));

        agent().run();

        assertThat(model.lastRequest().tools())
            .isEmpty();
    }

    @Test
    @DisplayName(
        "the cheaper model is used for two"
        + " sentences of prose")
    void usesTheFastModel() {
        model.reply(asJson(soundRationale()));

        agent().run();

        assertThat(model.lastRequest().tier())
            .isEqualTo(ModelTier.FAST);
    }

    @Test
    @DisplayName(
        "the prompt carries all three attempts"
        + " and the meet date")
    void thePromptCarriesTheAttempts() {
        model.reply(asJson(soundRationale()));

        agent().run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains(String.format("%.1f",
                    attempts.opener()))
            .contains(String.format("%.1f",
                    attempts.second()))
            .contains(String.format("%.1f",
                    attempts.third()))
            .contains("2026-12-05");
    }

    @Test
    @DisplayName(
        "the trend reaches the prompt in words,"
        + " never as a figure that would then be"
        + " rejected")
    void theTrendIsDescribedInWords() {
        model.reply(asJson(soundRationale()));

        agent().run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("getting stronger")
            .doesNotContain("12.5 kg across");
    }

    @Test
    @DisplayName(
        "the model being unavailable gives a"
        + " clear failure, not a crash")
    void modelUnavailableFailsCleanly() {
        model.failWith("no API key configured");

        AgentResult<MeetAttempts> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("unavailable");
    }
}
