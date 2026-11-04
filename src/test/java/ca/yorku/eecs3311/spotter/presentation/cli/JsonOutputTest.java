package ca.yorku.eecs3311.spotter
        .presentation.cli;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind
        .ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the machine readable output.
 *
 * Stage 3 drives the application from a harness,
 * so what matters here is not that the figures
 * are right, which the domain tests cover, but
 * that a program can read the answer at all: one
 * JSON object, nothing else on the stream, the
 * same keys whether the run succeeded or was
 * refused.
 *
 * Every command is run through the real command
 * objects against a temporary database, so a
 * stray print statement anywhere in the prose
 * path shows up here as a parse failure rather
 * than as a surprise in Stage 3.
 */
class JsonOutputTest {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    @TempDir
    Path tempDir;

    private CoachCLI cli;
    private StringWriter out;
    private StringWriter err;
    private Path env;

    @BeforeEach
    void setUp() throws IOException {
        cli = new CoachCLI();
        out = new StringWriter();
        err = new StringWriter();
        env = tempDir.resolve(".env");
        Files.write(env, List.of(
            "SPOTTER_DB_PATH="
                + tempDir.resolve("test.db")));
    }

    private int run(String... argv) {
        List<String> all =
                new java.util.ArrayList<>(
                    List.of(argv));
        all.add("--env");
        all.add(env.toString());
        return cli.run(all,
            new PrintWriter(out, true),
            new PrintWriter(err, true));
    }

    /** Parses what the command printed. */
    private Map<String, Object> parsed() {
        try {
            return MAPPER.readValue(
                out.toString(), Map.class);
        } catch (Exception broken) {
            throw new AssertionError(
                "the command did not print one"
                + " JSON object. It printed:\n"
                + out, broken);
        }
    }

    /** A nested object, typed so asserts work. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(
            Object value) {
        return (Map<String, Object>) value;
    }

    private void seed() {
        out.getBuffer().setLength(0);
        cli.run(List.of("seed", "--env",
                env.toString()),
            new PrintWriter(new StringWriter()),
            new PrintWriter(new StringWriter()));
        out.getBuffer().setLength(0);
    }

    @Test
    @DisplayName(
        "a check in prints one JSON object and no"
        + " prose")
    void checkInIsReadable() {
        int code = run("checkin", "--json",
            "--soreness", "5", "--energy", "1",
            "--sleep", "4.5");

        assertThat(code).isZero();
        Map<String, Object> body = parsed();
        assertThat(body.get("command"))
                .isEqualTo("checkin");
        assertThat(body.get("ok"))
                .isEqualTo(true);

        Map<String, Object> value =
                map(body.get("value"));
        assertThat(value.get("soreness"))
                .isEqualTo(5);
        assertThat(value.get("flagged"))
                .isEqualTo(true);
        assertThat(value.get("significant"))
                .isEqualTo(true);
    }

    /**
     * The field a harness reads. A reported day
     * that triggers nothing has to say so, rather
     * than leaving the caller to infer it from a
     * missing key.
     */
    @Test
    @DisplayName(
        "a check in that triggers nothing says"
        + " flagged is false")
    void anUnflaggedCheckInSaysSo() {
        run("checkin", "--json",
            "--soreness", "1", "--energy", "5",
            "--sleep", "8.5");

        Map<String, Object> value =
                map(parsed().get("value"));

        assertThat(value.get("flagged"))
                .isEqualTo(false);
        assertThat(value)
                .doesNotContainKey("summary");
    }

    @Test
    @DisplayName(
        "a refusal is still one JSON object, with"
        + " ok false and a reason")
    void aRefusalIsReadable() {
        int code = run("checkin", "--json");

        assertThat(code).isEqualTo(
            CheckInCommand.EXIT_NOTHING);
        Map<String, Object> body = parsed();
        assertThat(body.get("ok"))
                .isEqualTo(false);
        assertThat((String) body.get("failure"))
                .contains("nothing was"
                        + " reported");
        assertThat(body)
                .doesNotContainKey("value");
    }

    @Test
    @DisplayName(
        "a plan comes back as a block a program"
        + " can walk")
    void aPlanIsReadable() {
        seed();
        int code = run("plan", "--json",
            "--demo", "--weeks", "2",
            "--days", "3");

        assertThat(code).isZero();
        Map<String, Object> body = parsed();
        Map<String, Object> block =
                map(body.get("value"));

        assertThat(block.get("weeks"))
                .isEqualTo(2);
        assertThat(block.get("sessions"))
                .isEqualTo(6);
        assertThat((List<?>) block.get("week"))
                .hasSize(2);

        Map<String, Object> week = map(
            ((List<?>) block.get("week"))
                .get(0));
        Map<String, Object> session = map(
            ((List<?>) week.get("sessions"))
                .get(0));
        assertThat(session)
            .containsKeys("date", "focus",
                "plannedTonnageKg",
                "prescriptions");
    }

    /**
     * The trace is what Stage 3 compares between
     * runs, so the counts are part of the
     * contract and not debugging output.
     */
    @Test
    @DisplayName(
        "the trace carries the counts without the"
        + " steps unless asked")
    void theTraceIsReportedWithoutSteps() {
        seed();
        run("plan", "--json", "--demo",
            "--weeks", "1", "--days", "2");

        Map<String, Object> trace =
                map(parsed().get("trace"));

        assertThat(trace).containsKeys(
            "modelCalls", "toolsCalled",
            "validationFailures", "usage");
        assertThat(trace)
                .doesNotContainKey("steps");
        assertThat((Integer)
                trace.get("modelCalls"))
            .isPositive();
    }

    @Test
    @DisplayName(
        "the steps are included when --trace is"
        + " given as well")
    void theStepsAreIncludedOnRequest() {
        seed();
        run("plan", "--json", "--demo",
            "--trace", "--weeks", "1",
            "--days", "2");

        Map<String, Object> trace =
                map(parsed().get("trace"));

        assertThat((List<?>) trace.get("steps"))
                .isNotEmpty();
    }

    @Test
    @DisplayName(
        "a substitution names both movements and"
        + " the prescription it becomes")
    void aSubstitutionIsReadable() {
        seed();
        int code = run("substitute", "--json",
            "--demo", "Back Squat",
            "--load", "140");

        assertThat(code).isZero();
        Map<String, Object> value =
                map(parsed().get("value"));

        assertThat(value.get("replaced"))
                .isEqualTo("Back Squat");
        assertThat(value)
            .containsKeys("replacement",
                "reason", "loadKg",
                "prescription");
    }

    @Test
    @DisplayName(
        "an unknown exercise is refused in JSON"
        + " rather than in prose")
    void anUnknownExerciseIsRefusedInJson() {
        seed();
        int code = run("substitute", "--json",
            "--demo", "Nonexistent",
            "--load", "100");

        assertThat(code).isEqualTo(
            SubstituteCommand.EXIT_UNKNOWN);
        assertThat((String)
                parsed().get("failure"))
            .contains("Nonexistent");
    }

    @Test
    @DisplayName(
        "a meal reports its portions and totals")
    void aMealIsReadable() {
        seed();
        int code = run("meal", "--json",
            "--demo", "--calories", "800",
            "--protein", "50");

        assertThat(code).isZero();
        Map<String, Object> value =
                map(parsed().get("value"));

        assertThat(value).containsKeys("name",
                "totals", "portions");
        assertThat((List<?>)
                value.get("portions"))
            .isNotEmpty();
    }

    @Test
    @DisplayName(
        "attempts are reported with no model"
        + " involved")
    void attemptsAreReadable() {
        seed();
        int code = run("attempts", "--json",
                "squat");

        assertThat(code).isZero();
        Map<String, Object> value =
                map(parsed().get("value"));

        assertThat(value.get("lift"))
                .isEqualTo("SQUAT");
        assertThat(value).containsKeys(
            "openerKg", "secondKg", "thirdKg",
            "rationale");
    }

    @Test
    @DisplayName(
        "an adjustment with nothing flagged says"
        + " the session was not adjusted")
    void anUnflaggedAdjustmentSaysSo() {
        seed();
        int code = run("adjust", "--json",
                "--demo");

        assertThat(code).isZero();
        Map<String, Object> value =
                map(parsed().get("value"));

        assertThat(value.get("adjusted"))
                .isEqualTo(false);
        assertThat(value)
                .containsKey("planned");
    }

    @Test
    @DisplayName(
        "the rule engine's own reduction says it"
        + " was not the model's")
    void theFallbackNamesWhoDecided() {
        seed();
        run("checkin", "--soreness", "5",
            "--energy", "1", "--sleep", "4.0");
        out.getBuffer().setLength(0);

        int code = run("adjust", "--json",
                "--fallback");

        assertThat(code).isZero();
        Map<String, Object> value =
                map(parsed().get("value"));

        assertThat(value.get("adjusted"))
                .isEqualTo(true);
        assertThat(value.get("decidedBy"))
                .isEqualTo("ruleEngine");
    }

    @Test
    @DisplayName(
        "a review with no block is refused in"
        + " JSON")
    void aReviewWithNoBlockIsRefused() {
        seed();
        int code = run("review", "--json");

        assertThat(code).isEqualTo(
                ReviewCommand.EXIT_NO_BLOCK);
        assertThat((String)
                parsed().get("failure"))
            .contains("no block");
    }

    @Test
    @DisplayName(
        "a typed programme is adopted and names"
        + " the route it came in by")
    void anAdoptedProgrammeNamesItsRoute() {
        seed();
        int code = run("adopt", "--json",
            "--entry",
            "1/1 Back Squat 5x5@140");

        assertThat(code).isZero();
        Map<String, Object> value =
                map(parsed().get("value"));

        assertThat(value.get("route"))
                .isEqualTo("typedEntry");
        assertThat(value.get("kept"))
                .isEqualTo(false);
        assertThat(value)
                .containsKeys("block",
                        "rowsPassedOver");
    }

    @Test
    @DisplayName(
        "a line that cannot be read is refused"
        + " with the line in the reason")
    void anUnreadableEntryIsRefused() {
        seed();
        int code = run("adopt", "--json",
                "--entry", "garbage");

        assertThat(code).isEqualTo(
                AdoptCommand.EXIT_REFUSED);
        assertThat((String)
                parsed().get("failure"))
            .contains("garbage");
    }
}
