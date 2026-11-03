package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.agent.agents
        .ProgressionAgent;
import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .PlanAdherence;
import ca.yorku.eecs3311.spotter.domain
        .RPEProgression;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain.SetEntry;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;

/**
 * Tests for F12, revising a block part way
 * through.
 *
 * The rule that carries the feature is that a
 * lift which fell short of its plan may not be
 * given more weight. A lift falling behind is
 * rarely a sign that it was too easy, and a model
 * looking only at strength figures cannot tell
 * the difference between a block that is not
 * working and one that was not done.
 */
class ProgressionAgentTest {

    private static final LocalDate START =
            LocalDate.of(2026, 10, 5);

    private Database database;
    private ExerciseCatalog catalog;
    private ScriptedLLMClient model;
    private Exercise squat;
    private TrainingBlock block;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        catalog = new SqliteExerciseCatalog(
                database);

        squat = catalog.add(new Exercise(0,
            "Back Squat", Set.of("quads"),
            "squat", Set.of(Equipment.BARBELL),
            Lift.SQUAT));

        block = new TrainingBlock(1, START,
            "RPE based", "two weeks",
            BlockOrigin.GENERATED,
            List.of(
                new Week(1, List.of(
                    new Session(1, START,
                        "squat", List.of(
                            new ExercisePrescription(
                                1, squat, 5, 5,
                                8.0, 140.0))))),
                new Week(2, List.of(
                    new Session(2,
                        START.plusWeeks(1),
                        "squat", List.of(
                            new ExercisePrescription(
                                2, squat, 5, 5,
                                8.0, 145.0)))))));

        model = new ScriptedLLMClient();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    /** Training that met the plan, or did not. */
    private PlanAdherence adherence(
            double kgLifted) {
        List<WorkoutSession> logged =
                new ArrayList<>();
        List<SetEntry> sets =
                new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sets.add(SetEntry.of(squat,
                    kgLifted, 5, 8));
        }
        logged.add(new WorkoutSession(0, START,
                "test", sets));
        return PlanAdherence.of(block, logged,
                START.plusDays(2));
    }

    private ProgressionAgent agent(
            PlanAdherence adherence) {
        return new ProgressionAgent(model,
            new PromptBuilder(), block,
            adherence, new RPEProgression(),
            Map.of(Lift.SQUAT, 190.0),
            List.of(),
            new ResponseParser(catalog),
            new PlanValidator());
    }

    /** A revision of both weeks at given loads. */
    private static String revision(
            double weekOne, double weekTwo) {
        return "{\"splitName\":\"two weeks\","
            + "\"weeks\":["
            + "{\"number\":1,\"sessions\":["
            + "{\"date\":\"2026-10-05\","
            + "\"focus\":\"squat\",\"exercises\":"
            + "[{\"name\":\"Back Squat\","
            + "\"sets\":5,\"reps\":5,\"rpe\":8,"
            + "\"loadKg\":" + weekOne + "}]}]},"
            + "{\"number\":2,\"sessions\":["
            + "{\"date\":\"2026-10-12\","
            + "\"focus\":\"squat\",\"exercises\":"
            + "[{\"name\":\"Back Squat\","
            + "\"sets\":5,\"reps\":5,\"rpe\":8,"
            + "\"loadKg\":" + weekTwo + "}]}]}"
            + "]}";
    }

    @Test
    @DisplayName(
        "a modest advance after a week trained"
        + " as written is accepted")
    void anAdvanceAfterAGoodWeek() {
        model.reply(revision(140.0, 146.0));

        AgentResult<TrainingBlock> result =
                agent(adherence(140.0)).run();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.value().orElseThrow()
                .weekCount())
            .isEqualTo(2);
    }

    @Test
    @DisplayName(
        "a lift short of its plan may not be"
        + " given more weight")
    void aShortLiftMayNotAdvance() {
        // Trained 110 kg against 140 planned.
        model.alwaysReply(
                revision(140.0, 150.0));

        AgentResult<TrainingBlock> result =
                agent(adherence(110.0)).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("fell short of its plan")
            .contains("Hold or reduce it");
    }

    @Test
    @DisplayName(
        "holding a short lift where it is, is"
        + " accepted")
    void holdingAShortLiftIsFine() {
        model.reply(revision(140.0, 140.0));

        assertThat(agent(adherence(110.0)).run()
                .succeeded())
            .isTrue();
    }

    @Test
    @DisplayName(
        "reducing a short lift is accepted")
    void reducingAShortLiftIsFine() {
        model.reply(revision(140.0, 130.0));

        assertThat(agent(adherence(110.0)).run()
                .succeeded())
            .isTrue();
    }

    @Test
    @DisplayName(
        "a jump of more than five percent in a"
        + " week is refused")
    void aLargeWeeklyJumpIsRefused() {
        // 140 to 160 is over fourteen percent.
        model.alwaysReply(
                revision(140.0, 160.0));

        AgentResult<TrainingBlock> result =
                agent(adherence(140.0)).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("more than 5 percent");
    }

    @Test
    @DisplayName(
        "dropping a session is refused, since a"
        + " revision may not quietly remove"
        + " work")
    void droppingASessionIsRefused() {
        String onlyWeekOne = "{\"weeks\":["
            + "{\"number\":1,\"sessions\":["
            + "{\"date\":\"2026-10-05\","
            + "\"focus\":\"squat\",\"exercises\":"
            + "[{\"name\":\"Back Squat\","
            + "\"sets\":5,\"reps\":5,"
            + "\"loadKg\":140.0}]}]}]}";
        model.alwaysReply(onlyWeekOne);

        AgentResult<TrainingBlock> result =
                agent(adherence(140.0)).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("missing from the"
                    + " revision");
    }

    @Test
    @DisplayName(
        "the adherence figures reach the model"
        + " through the lookup")
    void adherenceReachesTheModel() {
        String report = new ca.yorku.eecs3311
            .spotter.agent.tools.AdherenceTool(
                adherence(110.0))
            .execute(Map.of());

        assertThat(report)
            .contains("1 of 1 trained")
            .contains("Short of plan: SQUAT")
            .contains("must not advance");
    }

    @Test
    @DisplayName(
        "with nothing yet due the lookup says"
        + " so rather than reporting zero")
    void nothingDueIsStated() {
        PlanAdherence nothing =
            PlanAdherence.of(block, List.of(),
                START.minusWeeks(1));

        String report = new ca.yorku.eecs3311
            .spotter.agent.tools.AdherenceTool(
                nothing).execute(Map.of());

        assertThat(report)
            .contains("nothing to judge");
    }

    @Test
    @DisplayName(
        "the block as it stands reaches the"
        + " prompt, so the model revises rather"
        + " than invents")
    void theBlockReachesThePrompt() {
        model.reply(revision(140.0, 146.0));

        agent(adherence(140.0)).run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("Back Squat 5 x 5 at"
                    + " 140.0 kg")
            .contains("Week 2");
    }

    @Test
    @DisplayName(
        "all three lookups are offered")
    void theLookupsAreOffered() {
        model.reply(revision(140.0, 146.0));

        agent(adherence(140.0)).run();

        assertThat(model.lastRequest().tools())
            .extracting(ToolSchema::name)
            .containsExactlyInAnyOrder(
                "plan_adherence",
                "strength_analytics",
                "allowed_load");
    }

    @Test
    @DisplayName(
        "the revision keeps where the block came"
        + " from")
    void theOriginSurvives() {
        model.reply(revision(140.0, 146.0));

        assertThat(agent(adherence(140.0)).run()
                .value().orElseThrow().origin())
            .isEqualTo(BlockOrigin.GENERATED);
    }

    @Test
    @DisplayName(
        "the model being unavailable gives a"
        + " clear failure, not a crash")
    void modelUnavailableFailsCleanly() {
        model.failWith("no API key configured");

        AgentResult<TrainingBlock> result =
                agent(adherence(140.0)).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("unavailable");
    }
}
