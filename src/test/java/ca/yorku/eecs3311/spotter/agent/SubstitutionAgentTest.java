package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.agent.agents
        .SubstitutionAgent;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .Limitation;
import ca.yorku.eecs3311.spotter.domain
        .Substitution;
import ca.yorku.eecs3311.spotter.domain
        .SubstitutionReason;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;

/**
 * Tests for F06, the substitution agent.
 *
 * The lifter is at a gym with dumbbells and a
 * bench but no barbell, and the plan says to
 * bench press 100 kg. Every test below asks what
 * happens when the model answers in a particular
 * way, including the three answers that would do
 * real harm if they were accepted: the movement
 * the lifter cannot perform, the movement that
 * trains something else entirely, and the right
 * movement at the barbell's weight.
 */
class SubstitutionAgentTest {

    private Database database;
    private ExerciseCatalog catalog;
    private ScriptedLLMClient model;
    private LifterProfile profile;
    private ExercisePrescription original;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        catalog = new SqliteExerciseCatalog(
                database);

        Exercise bench = catalog.add(
            new Exercise(0,
                "Barbell Bench Press",
                Set.of("chest", "triceps"),
                "horizontal press",
                Set.of(Equipment.BARBELL,
                       Equipment.BENCH),
                Lift.BENCH));
        catalog.add(new Exercise(0,
            "Dumbbell Bench Press",
            Set.of("chest", "triceps"),
            "horizontal press",
            Set.of(Equipment.DUMBBELL,
                   Equipment.BENCH),
            null));
        catalog.add(new Exercise(0,
            "Machine Chest Press",
            Set.of("chest"),
            "horizontal press",
            Set.of(Equipment
                .CHEST_PRESS_MACHINE),
            null));
        catalog.add(new Exercise(0,
            "Dumbbell Curl",
            Set.of("biceps"),
            "elbow flexion",
            Set.of(Equipment.DUMBBELL),
            null));

        // The gym has dumbbells and a bench,
        // so the prescribed barbell press
        // cannot be done today.
        profile = LifterProfile
            .with(TrainingGoal.STRENGTH,
                  ExperienceLevel.INTERMEDIATE)
            .trainingDaysPerWeek(4)
            .equipment(Set.of(
                Equipment.DUMBBELL,
                Equipment.BENCH))
            .build();

        original = new ExercisePrescription(1,
                bench, 4, 5, 8.0, 100.0);

        model = new ScriptedLLMClient();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private SubstitutionAgent agent() {
        return agent(profile, null);
    }

    private SubstitutionAgent agent(
            LifterProfile lifter, String note) {
        return new SubstitutionAgent(model,
            new PromptBuilder(), lifter,
            catalog, original,
            SubstitutionReason
                .EQUIPMENT_UNAVAILABLE,
            note);
    }

    private static String choice(String name,
                                 double loadKg) {
        return "{\"name\":\"" + name
            + "\",\"loadKg\":" + loadKg
            + ",\"rationale\":\"closest"
            + " pressing movement available\"}";
    }

    @Test
    @DisplayName(
        "a sensible substitute is accepted and"
        + " keeps the prescribed sets and reps")
    void sensibleSubstituteIsAccepted() {
        model.reply(choice(
            "Dumbbell Bench Press", 70.0));

        AgentResult<Substitution> result =
                agent().run();

        assertThat(result.succeeded()).isTrue();
        Substitution substitution =
                result.value().orElseThrow();
        assertThat(substitution.replacement()
                .name())
            .isEqualTo(
                "Dumbbell Bench Press");
        assertThat(substitution.loadKg())
            .isEqualTo(70.0);
        assertThat(substitution.rationale())
            .isPresent();
        assertThat(substitution.apply().sets())
            .isEqualTo(4);
        assertThat(substitution.apply().reps())
            .isEqualTo(5);
        assertThat(substitution.apply()
                .exercise().name())
            .isEqualTo(
                "Dumbbell Bench Press");
    }

    @Test
    @DisplayName(
        "answering with the exercise being"
        + " replaced is rejected")
    void theSameExerciseIsRejected() {
        model.alwaysReply(choice(
                "Barbell Bench Press", 100.0));

        AgentResult<Substitution> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("being replaced");
    }

    @Test
    @DisplayName(
        "equipment the lifter does not have is"
        + " rejected, and the retry is accepted")
    void unavailableEquipmentIsRejected() {
        model.reply(choice(
                "Machine Chest Press", 70.0))
             .reply(choice(
                "Dumbbell Bench Press", 70.0));

        AgentResult<Substitution> result =
                agent().run();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.value().orElseThrow()
                .replacement().name())
            .isEqualTo(
                "Dumbbell Bench Press");
        assertThat(model.received().get(1)
                .userPrompt())
            .contains("cannot perform")
            .contains("Machine Chest Press");
    }

    @Test
    @DisplayName(
        "a movement training something else is"
        + " rejected")
    void unrelatedMovementIsRejected() {
        model.alwaysReply(choice("Dumbbell Curl", 50.0));

        AgentResult<Substitution> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("trains neither");
    }

    @Test
    @DisplayName(
        "the barbell's weight on a dumbbell"
        + " press is rejected as too heavy")
    void tooHeavyIsRejected() {
        model.alwaysReply(choice(
                "Dumbbell Bench Press", 120.0));

        AgentResult<Substitution> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("too heavy");
    }

    @Test
    @DisplayName(
        "a load far below the original is"
        + " rejected as too light")
    void tooLightIsRejected() {
        model.alwaysReply(choice(
                "Dumbbell Bench Press", 20.0));

        AgentResult<Substitution> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("too light");
    }

    @Test
    @DisplayName(
        "a load just inside the band is"
        + " accepted")
    void loadInsideTheBandIsAccepted() {
        model.reply(choice(
            "Dumbbell Bench Press", 105.0));

        AgentResult<Substitution> result =
                agent().run();

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    @DisplayName(
        "an invented movement is caught at"
        + " parse time")
    void inventedExerciseIsCaught() {
        model.alwaysReply(choice("Ultra Chest Blaster",
                70.0));

        SubstitutionAgent agent = agent();
        AgentResult<Substitution> result =
                agent.run();

        assertThat(result.succeeded()).isFalse();
        assertThat(agent.parseProblems())
            .anyMatch(problem -> problem
                .contains("Ultra Chest"
                    + " Blaster"));
    }

    @Test
    @DisplayName(
        "a limitation on file rules a movement"
        + " out even when the equipment is"
        + " there")
    void limitationRulesAMovementOut() {
        LifterProfile sore = LifterProfile
            .with(TrainingGoal.STRENGTH,
                  ExperienceLevel.INTERMEDIATE)
            .equipment(Set.of(
                Equipment.DUMBBELL,
                Equipment.BENCH))
            .addLimitation(new Limitation(
                "left shoulder objects to"
                + " pressing",
                Set.of("horizontal press"),
                Set.of(), true))
            .build();

        model.alwaysReply(choice(
                "Dumbbell Bench Press", 70.0));

        AgentResult<Substitution> result =
                agent(sore, "shoulder is sore")
                    .run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("cannot perform");
    }

    @Test
    @DisplayName(
        "the cheaper model is used, because"
        + " this is a choice from a list")
    void usesTheFastModel() {
        model.reply(choice(
            "Dumbbell Bench Press", 70.0));

        agent().run();

        assertThat(model.lastRequest().tier())
            .isEqualTo(ModelTier.FAST);
    }

    @Test
    @DisplayName(
        "only the exercise lookup is offered,"
        + " since no analytics are needed")
    void onlyTheExerciseToolIsOffered() {
        model.reply(choice(
            "Dumbbell Bench Press", 70.0));

        agent().run();

        assertThat(model.lastRequest().tools())
            .extracting(ToolSchema::name)
            .containsExactly(
                "available_exercises");
    }

    @Test
    @DisplayName(
        "the prompt states what is being"
        + " replaced and why")
    void thePromptStatesTheProblem() {
        model.reply(choice(
            "Dumbbell Bench Press", 70.0));

        agent(profile, "no barbell here today")
                .run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("Barbell Bench Press")
            .contains("100.0 kg")
            .contains("not"
                + " available")
            .contains("no barbell here today");
    }

    @Test
    @DisplayName(
        "the model being unavailable gives a"
        + " clear failure, not a crash")
    void modelUnavailableFailsCleanly() {
        model.failWith("no API key configured");

        AgentResult<Substitution> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("unavailable");
    }
}
