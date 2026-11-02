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
        .BlockGenerationAgent;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .Limitation;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain
        .RPEProgression;
import ca.yorku.eecs3311.spotter.domain.SetEntry;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
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
 * Tests for F05, the first real agent.
 *
 * Run against prepared replies, so what the model
 * said is stated exactly and the system's
 * response to it is checked, including the cases
 * that matter most: a plan that is too heavy, one
 * naming an exercise that does not exist, and one
 * using equipment the lifter does not own.
 */
class BlockGenerationAgentTest {

    private static final LocalDate START =
            LocalDate.of(2026, 11, 2);

    private Database database;
    private ExerciseCatalog catalog;
    private ScriptedLLMClient model;
    private LifterProfile profile;
    private List<WorkoutSession> history;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        catalog = new SqliteExerciseCatalog(
                database);

        Exercise squat = catalog.add(
            new Exercise(0, "Back Squat",
                Set.of("quads"), "squat",
                Set.of(Equipment.BARBELL),
                Lift.SQUAT));
        catalog.add(new Exercise(0, "Leg Press",
            Set.of("quads"), "squat",
            Set.of(Equipment.LEG_PRESS), null));

        // A 200 kg squat: 162 kg for 5 at RPE 8
        history = List.of(new WorkoutSession(0,
            START.minusWeeks(1), "test",
            List.of(SetEntry.of(squat,
                    162.2, 5, 8))));

        profile = LifterProfile
            .with(TrainingGoal.STRENGTH,
                  ExperienceLevel.INTERMEDIATE)
            .trainingDaysPerWeek(2)
            .equipment(Set.of(Equipment.BARBELL))
            .build();

        model = new ScriptedLLMClient();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private BlockGenerationAgent agent(
            int weeks) {
        ProgressionStrategy progression =
                new RPEProgression();
        return new BlockGenerationAgent(model,
            new PromptBuilder(), profile,
            catalog, history, progression,
            START, weeks);
    }

    private String plan(String exerciseName,
                        double loadKg) {
        return """
            {"splitName":"test",
             "weeks":[{"number":1,"sessions":[
               {"date":"2026-11-02",
                "focus":"squat",
                "exercises":[
                  {"name":"%s","sets":4,
                   "reps":5,"rpe":8,
                   "loadKg":%s}]}]}]}
            """.formatted(exerciseName, loadKg);
    }

    @Test
    @DisplayName(
        "a sound plan becomes a training block")
    void soundPlanIsAccepted() {
        model.reply(plan("Back Squat", 162.0));

        AgentResult<TrainingBlock> result =
                agent(1).run();

        assertThat(result.succeeded()).isTrue();
        TrainingBlock block =
                result.value().orElseThrow();
        assertThat(block.weekCount())
                .isEqualTo(1);
        assertThat(block.allSessions())
                .hasSize(1);
    }

    @Test
    @DisplayName(
        "a plan prescribing more than the"
        + " lifter can handle is rejected and"
        + " retried with the real numbers")
    void tooHeavyIsRejectedAndRetried() {
        model.reply(plan("Back Squat", 250.0))
             .reply(plan("Back Squat", 162.0));

        AgentResult<TrainingBlock> result =
                agent(1).run();

        assertThat(result.succeeded()).isTrue();
        assertThat(model.callCount())
                .isEqualTo(2);
        assertThat(model.received().get(1)
                .userPrompt())
            .contains("250")
            .contains("must be between");
    }

    @Test
    @DisplayName(
        "a plan that stays too heavy is"
        + " refused rather than handed over")
    void persistentlyTooHeavyIsRefused() {
        model.alwaysReply(
            plan("Back Squat", 250.0));

        AgentResult<TrainingBlock> result =
                agent(1).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.value()).isEmpty();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("Back Squat");
    }

    @Test
    @DisplayName(
        "an invented exercise is caught at"
        + " parse time")
    void inventedExerciseIsCaught() {
        model.reply(plan("Hack Squat Machine",
                    100.0))
             .reply(plan("Back Squat", 162.0));

        AgentResult<TrainingBlock> result =
                agent(1).run();

        assertThat(result.succeeded()).isTrue();
        assertThat(model.received().get(1)
                .userPrompt())
            .contains("could not be read");
    }

    @Test
    @DisplayName(
        "an exercise needing equipment the"
        + " lifter lacks is rejected")
    void unavailableEquipmentIsRejected() {
        model.reply(plan("Leg Press", 100.0))
             .reply(plan("Back Squat", 162.0));

        AgentResult<TrainingBlock> result =
                agent(1).run();

        assertThat(result.succeeded()).isTrue();
        assertThat(model.received().get(1)
                .userPrompt())
            .contains("Leg Press")
            .contains("LEG_PRESS");
    }

    @Test
    @DisplayName(
        "a block of the wrong length is"
        + " rejected")
    void wrongLengthIsRejected() {
        model.alwaysReply(plan("Back Squat", 162.0));

        AgentResult<TrainingBlock> result =
                agent(4).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("4 were asked for");
    }

    @Test
    @DisplayName(
        "the lifter's current strength reaches"
        + " the prompt as fact")
    void currentStrengthIsInThePrompt() {
        model.reply(plan("Back Squat", 162.0));

        agent(1).run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("SQUAT")
            .contains("200");
    }

    @Test
    @DisplayName(
        "the stronger model is used, because"
        + " this is open ended work")
    void usesTheReasoningModel() {
        model.reply(plan("Back Squat", 162.0));

        agent(1).run();

        assertThat(model.lastRequest().tier())
            .isEqualTo(ModelTier.REASONING);
    }

    @Test
    @DisplayName(
        "both tools are offered to the model")
    void toolsAreOffered() {
        model.reply(plan("Back Squat", 162.0));

        agent(1).run();

        assertThat(model.lastRequest().tools())
            .extracting(ToolSchema::name)
            .containsExactlyInAnyOrder(
                "strength_analytics",
                "available_exercises");
    }

    @Test
    @DisplayName(
        "the available exercises tool hides"
        + " what an injury rules out")
    void limitationsHideExercises() {
        LifterProfile injured = LifterProfile
            .with(TrainingGoal.STRENGTH,
                  ExperienceLevel.INTERMEDIATE)
            .equipment(Set.of(Equipment.BARBELL))
            .addLimitation(new Limitation(
                "left knee objects to squatting",
                Set.of("squat"), Set.of(), true))
            .build();

        String offered = new ca.yorku.eecs3311
            .spotter.agent.tools.ExerciseDBTool(
                catalog, injured)
            .execute(Map.of());

        assertThat(offered)
            .doesNotContain("Back Squat");
    }

    @Test
    @DisplayName(
        "the model being unavailable gives a"
        + " clear failure, not a crash")
    void modelUnavailableFailsCleanly() {
        model.failWith("no API key configured");

        AgentResult<TrainingBlock> result =
                agent(1).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("unavailable");
    }
}
