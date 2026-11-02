package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.agent.agents
        .AdjustmentAgent;
import ca.yorku.eecs3311.spotter.agent.tools
        .RecoveryLookupTool;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryRuleEngine;
import ca.yorku.eecs3311.spotter.domain
        .RecoverySource;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;

/**
 * Tests for F07, the adjustment agent.
 *
 * Today is a 5900 kg session after five hours of
 * sleep. The rule engine has already decided the
 * day is worth acting on; what is tested here is
 * what Spotter accepts as an answer to that, and
 * what it refuses. The refusals matter more than
 * the acceptance: a model that raises a load, or
 * empties the session, or returns an exercise
 * that is not in it, must not reach the lifter.
 */
class AdjustmentAgentTest {

    private static final LocalDate TODAY =
            LocalDate.of(2026, 11, 2);

    private Database database;
    private ScriptedLLMClient model;
    private ResponseParser parser;
    private Session session;
    private List<RecoveryDay> readings;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        ExerciseCatalog catalog =
                new SqliteExerciseCatalog(
                        database);

        Exercise squat = catalog.add(
            new Exercise(0, "Back Squat",
                Set.of("quads"), "squat",
                Set.of(Equipment.BARBELL),
                Lift.SQUAT));
        Exercise rdl = catalog.add(
            new Exercise(0,
                "Romanian Deadlift",
                Set.of("hamstrings"), "hinge",
                Set.of(Equipment.BARBELL),
                null));

        // 3500 kg plus 2400 kg, so 5900 kg
        session = new Session(1, TODAY, "squat",
            List.of(
                new ExercisePrescription(1,
                        squat, 5, 5, 8.0, 140.0),
                new ExercisePrescription(2,
                        rdl, 3, 8, 7.0, 100.0)));

        readings = List.of(
            RecoveryDay.on(TODAY,
                    RecoverySource.WEARABLE_CSV)
                .recoveryScore(41)
                .sleepHours(5.0)
                .hrvMs(38)
                .build(),
            RecoveryDay.on(TODAY.minusDays(1),
                    RecoverySource.WEARABLE_CSV)
                .recoveryScore(78)
                .sleepHours(8.1)
                .hrvMs(72)
                .build());

        parser = new ResponseParser(catalog);
        model = new ScriptedLLMClient();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private RecoveryFlag mild() {
        return new RecoveryFlag(TODAY,
            List.of("only 5.0 hours of sleep"),
            null);
    }

    private RecoveryFlag significant() {
        return new RecoveryFlag(TODAY,
            List.of("only 5.0 hours of sleep",
                "recovery score of 41 is below"
                + " 50"),
            null);
    }

    private AdjustmentAgent agent(
            RecoveryFlag flag) {
        return new AdjustmentAgent(model,
            new PromptBuilder(), session, flag,
            readings, new RecoveryRuleEngine(),
            parser);
    }

    @Test
    @DisplayName(
        "a reduction weighted towards the main"
        + " lift is accepted")
    void sensibleReductionIsAccepted() {
        model.reply("{\"note\":\"Short night,"
            + " so the squat takes the cut.\","
            + "\"exercises\":[{\"id\":1,"
            + "\"sets\":4,\"loadKg\":125.0},"
            + "{\"id\":2,\"sets\":3,"
            + "\"loadKg\":100.0}]}");

        AgentResult<Session> result =
                agent(mild()).run();

        assertThat(result.succeeded()).isTrue();
        Session adjusted =
                result.value().orElseThrow();
        assertThat(adjusted.plannedTonnage())
            .isEqualTo(4900.0);
        assertThat(adjusted.wasAdjusted())
            .isTrue();
        assertThat(adjusted.adjustmentNote()
                .orElseThrow())
            .contains("squat");
        assertThat(adjusted.findPrescription(1)
                .orElseThrow().sets())
            .isEqualTo(4);
        assertThat(adjusted.findPrescription(2)
                .orElseThrow().loadKg())
            .isEqualTo(100.0);
    }

    @Test
    @DisplayName(
        "the reps the coach wrote are not"
        + " touched, only sets and load")
    void repsSurviveTheAdjustment() {
        model.reply("{\"note\":\"easier\","
            + "\"exercises\":[{\"id\":1,"
            + "\"sets\":4,\"loadKg\":125.0},"
            + "{\"id\":2,\"sets\":3,"
            + "\"loadKg\":100.0}]}");

        Session adjusted = agent(mild()).run()
                .value().orElseThrow();

        assertThat(adjusted.findPrescription(1)
                .orElseThrow().reps())
            .isEqualTo(5);
        assertThat(adjusted.findPrescription(2)
                .orElseThrow().reps())
            .isEqualTo(8);
    }

    @Test
    @DisplayName(
        "raising a load is rejected")
    void raisingALoadIsRejected() {
        String heavier = "{\"note\":\"push"
            + " through it\",\"exercises\":["
            + "{\"id\":1,\"sets\":5,"
            + "\"loadKg\":150.0},"
            + "{\"id\":2,\"sets\":3,"
            + "\"loadKg\":100.0}]}";
        model.alwaysReply(heavier);

        AgentResult<Session> result =
                agent(mild()).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("only go down");
    }

    @Test
    @DisplayName(
        "adding sets is rejected")
    void addingSetsIsRejected() {
        String more = "{\"note\":\"more"
            + " volume\",\"exercises\":["
            + "{\"id\":1,\"sets\":7,"
            + "\"loadKg\":140.0},"
            + "{\"id\":2,\"sets\":3,"
            + "\"loadKg\":100.0}]}";
        model.alwaysReply(more);

        AgentResult<Session> result =
                agent(mild()).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("sets");
    }

    @Test
    @DisplayName(
        "a token reduction is rejected as"
        + " barely easier")
    void tokenReductionIsRejected() {
        String token = "{\"note\":\"tiny"
            + " trim\",\"exercises\":["
            + "{\"id\":1,\"sets\":5,"
            + "\"loadKg\":138.0},"
            + "{\"id\":2,\"sets\":3,"
            + "\"loadKg\":100.0}]}";
        model.alwaysReply(token);

        AgentResult<Session> result =
                agent(mild()).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("barely easier");
    }

    @Test
    @DisplayName(
        "gutting the session is rejected as not"
        + " worth doing")
    void guttingTheSessionIsRejected() {
        String gutted = "{\"note\":\"take it"
            + " easy\",\"exercises\":["
            + "{\"id\":1,\"sets\":1,"
            + "\"loadKg\":100.0},"
            + "{\"id\":2,\"sets\":1,"
            + "\"loadKg\":60.0}]}";
        model.alwaysReply(gutted);

        AgentResult<Session> result =
                agent(mild()).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("too");
    }

    @Test
    @DisplayName(
        "a deeper cut is allowed when the"
        + " readings are significantly poor")
    void significantRecoveryAllowsADeeperCut() {
        String deep = "{\"note\":\"rough"
            + " night, keep it light\","
            + "\"exercises\":["
            + "{\"id\":1,\"sets\":3,"
            + "\"loadKg\":100.0},"
            + "{\"id\":2,\"sets\":2,"
            + "\"loadKg\":80.0}]}";
        model.reply(deep);

        AgentResult<Session> result =
                agent(significant()).run();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.value().orElseThrow()
                .plannedTonnage())
            .isEqualTo(2780.0);
    }

    @Test
    @DisplayName(
        "that same cut is refused when only one"
        + " reading was off")
    void theSameCutIsRefusedWhenOnlyMild() {
        String deep = "{\"note\":\"keep it"
            + " light\",\"exercises\":["
            + "{\"id\":1,\"sets\":3,"
            + "\"loadKg\":100.0},"
            + "{\"id\":2,\"sets\":2,"
            + "\"loadKg\":80.0}]}";
        model.alwaysReply(deep);

        AgentResult<Session> result =
                agent(mild()).run();

        assertThat(result.succeeded()).isFalse();
    }

    @Test
    @DisplayName(
        "an exercise the session does not have"
        + " is caught at parse time")
    void anUnknownIdIsCaught() {
        String invented = "{\"note\":\"swap"
            + " it\",\"exercises\":["
            + "{\"id\":99,\"sets\":3,"
            + "\"loadKg\":100.0}]}";
        model.alwaysReply(invented);

        AdjustmentAgent agent = agent(mild());
        AgentResult<Session> result =
                agent.run();

        assertThat(result.succeeded()).isFalse();
        assertThat(agent.parseProblems())
            .anyMatch(problem -> problem
                .contains("id 99"));
    }

    @Test
    @DisplayName(
        "an entry without an id is caught,"
        + " because there is no way to tell what"
        + " it means")
    void anEntryWithoutAnIdIsCaught() {
        String nameless = "{\"note\":\"lighter"
            + "\",\"exercises\":[{\"sets\":3,"
            + "\"loadKg\":100.0}]}";
        model.alwaysReply(nameless);

        AdjustmentAgent agent = agent(mild());

        assertThat(agent.run().succeeded())
            .isFalse();
        assertThat(agent.parseProblems())
            .anyMatch(problem -> problem
                .contains("id"));
    }

    @Test
    @DisplayName(
        "removing every set from an exercise is"
        + " caught at parse time")
    void zeroSetsIsCaught() {
        String empty = "{\"note\":\"skip the"
            + " squat\",\"exercises\":["
            + "{\"id\":1,\"sets\":0,"
            + "\"loadKg\":140.0}]}";
        model.alwaysReply(empty);

        AdjustmentAgent agent = agent(mild());

        assertThat(agent.run().succeeded())
            .isFalse();
        assertThat(agent.parseProblems())
            .isNotEmpty();
    }

    @Test
    @DisplayName(
        "an exercise the reply leaves out keeps"
        + " what it had")
    void anOmittedExerciseIsUnchanged() {
        model.reply("{\"note\":\"squat only\","
            + "\"exercises\":[{\"id\":1,"
            + "\"sets\":3,\"loadKg\":125.0}]}");

        Session adjusted =
                agent(mild()).run().value()
                    .orElseThrow();

        ExercisePrescription rdl =
                adjusted.findPrescription(2)
                    .orElseThrow();
        assertThat(rdl.sets()).isEqualTo(3);
        assertThat(rdl.loadKg())
            .isEqualTo(100.0);
    }

    @Test
    @DisplayName(
        "the prompt states every exercise with"
        + " its id and the band to stay inside")
    void thePromptStatesTheSession() {
        model.reply("{\"note\":\"easier\","
            + "\"exercises\":[{\"id\":1,"
            + "\"sets\":4,\"loadKg\":125.0}]}");

        agent(mild()).run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("id 1: Back Squat")
            .contains("id 2: Romanian Deadlift")
            .contains("5900")
            .contains("2950");
    }

    @Test
    @DisplayName(
        "only the recovery lookup is offered")
    void onlyTheRecoveryToolIsOffered() {
        model.reply("{\"note\":\"easier\","
            + "\"exercises\":[{\"id\":1,"
            + "\"sets\":4,\"loadKg\":125.0}]}");

        agent(mild()).run();

        assertThat(model.lastRequest().tools())
            .extracting(ToolSchema::name)
            .containsExactly(
                "recovery_readings");
    }

    @Test
    @DisplayName(
        "the recovery lookup reports Spotter's"
        + " own verdict, not raw numbers alone")
    void theLookupCarriesTheVerdict() {
        String report = new RecoveryLookupTool(
                readings,
                new RecoveryRuleEngine())
            .execute(Map.of());

        assertThat(report)
            .contains("slept 5.0 hours")
            .contains("[flagged")
            .contains("[not flagged]");
    }

    @Test
    @DisplayName(
        "the rule engine still reduces the"
        + " session when the coach cannot be"
        + " reached")
    void theFallbackWorksWithoutTheModel() {
        model.failWith("no API key configured");

        AdjustmentAgent agent = agent(mild());
        AgentResult<Session> result =
                agent.run();

        assertThat(result.succeeded()).isFalse();

        Session fallback = agent.fallback();
        assertThat(fallback.plannedTonnage())
            .isLessThan(
                session.plannedTonnage());
        assertThat(fallback.adjustmentNote()
                .orElseThrow())
            .contains("unavailable");
        assertThat(fallback.prescriptions())
            .allMatch(p -> p.sets() >= 1);
    }
}
