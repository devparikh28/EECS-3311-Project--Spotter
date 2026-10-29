package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain
        .RPEProgression;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;

/**
 * Tests for the gate.
 *
 * Every claim in the Stage 1 report about model
 * output being checked before it reaches the
 * lifter rests on these.
 */
class PlanValidatorTest {

    private static final LocalDate START =
            LocalDate.of(2026, 11, 2);

    private static final Exercise SQUAT =
        new Exercise(1, "Back Squat",
            Set.of("quads"), "squat",
            Set.of(Equipment.BARBELL,
                   Equipment.SQUAT_RACK),
            Lift.SQUAT);

    private static final Exercise BELT_SQUAT =
        new Exercise(2, "Belt Squat",
            Set.of("quads"), "squat",
            Set.of(Equipment.BELT_SQUAT),
            null);

    private static final Set<Equipment> GYM =
        Set.of(Equipment.BARBELL,
               Equipment.SQUAT_RACK,
               Equipment.BENCH);

    private static final Map<Lift, Double> MAXES =
            Map.of(Lift.SQUAT, 200.0);

    private PlanValidator validator;
    private ProgressionStrategy strategy;

    @BeforeEach
    void setUp() {
        validator = new PlanValidator();
        strategy = new RPEProgression();
    }

    private TrainingBlock blockWith(
            ExercisePrescription... prescriptions) {
        return new TrainingBlock(0, START,
            "RPE", "test",
            BlockOrigin.GENERATED,
            List.of(new Week(1, List.of(
                new Session(1, START, "day",
                    List.of(prescriptions))))));
    }

    private ExercisePrescription squatAt(
            double loadKg) {
        return new ExercisePrescription(1,
            SQUAT, 4, 5, 8.0, loadKg);
    }

    @Test
    @DisplayName(
        "a sound plan passes")
    void soundPlanPasses() {
        // 5 at RPE 8 of a 200 kg max is 162 kg
        Optional<String> problem =
            validator.validateBlock(
                blockWith(squatAt(162)),
                strategy, MAXES, GYM);

        assertThat(problem).isEmpty();
    }

    @Test
    @DisplayName(
        "a load above what the lifter can"
        + " handle is rejected")
    void tooHeavyIsRejected() {
        Optional<String> problem =
            validator.validateBlock(
                blockWith(squatAt(250)),
                strategy, MAXES, GYM);

        assertThat(problem).isPresent();
        assertThat(problem.get())
            .contains("250")
            .contains("Back Squat")
            .contains("must be between");
    }

    @Test
    @DisplayName(
        "a load far below the prescription is"
        + " rejected too, since it wastes the"
        + " session")
    void tooLightIsRejected() {
        assertThat(validator.validateBlock(
                blockWith(squatAt(60)),
                strategy, MAXES, GYM))
            .isPresent();
    }

    @Test
    @DisplayName(
        "the rejection names the acceptable"
        + " range, so the model can correct"
        + " itself")
    void rejectionIsActionable() {
        String reason = validator.validateBlock(
                blockWith(squatAt(250)),
                strategy, MAXES, GYM)
            .orElseThrow();

        assertThat(reason)
            .contains("RPE")
            .contains("estimated maximum")
            .contains("Use a weight in that"
                    + " range");
    }

    @Test
    @DisplayName(
        "an exercise the lifter has no"
        + " equipment for is rejected")
    void unavailableEquipmentIsRejected() {
        ExercisePrescription beltSquat =
            new ExercisePrescription(1,
                BELT_SQUAT, 3, 10, 8.0, 100);

        String reason = validator.validateBlock(
                blockWith(beltSquat),
                strategy, MAXES, GYM)
            .orElseThrow();

        assertThat(reason)
            .contains("Belt Squat")
            .contains("BELT_SQUAT")
            .contains("Choose something else");
    }

    @Test
    @DisplayName(
        "accessory work is not judged against a"
        + " maximum it does not have")
    void accessoryLoadIsNotJudged() {
        Exercise curl = new Exercise(3,
            "Barbell Curl", Set.of("biceps"),
            "curl",
            Set.of(Equipment.BARBELL), null);

        Optional<String> problem =
            validator.validateBlock(
                blockWith(
                    new ExercisePrescription(1,
                        curl, 3, 12, 8.0, 40)),
                strategy, MAXES, GYM);

        assertThat(problem).isEmpty();
    }

    @Test
    @DisplayName(
        "a lift with no history is not judged,"
        + " rather than judged against zero")
    void liftWithoutHistoryIsNotJudged() {
        Optional<String> problem =
            validator.validateBlock(
                blockWith(squatAt(300)),
                strategy, Map.of(), GYM);

        assertThat(problem).isEmpty();
    }

    @Test
    @DisplayName(
        "an absurd number of sets is rejected")
    void absurdSetsAreRejected() {
        ExercisePrescription tooMany =
            new ExercisePrescription(1, SQUAT,
                20, 5, 8.0, 162);

        assertThat(validator.validateBlock(
                blockWith(tooMany), strategy,
                MAXES, GYM).orElseThrow())
            .contains("20 sets");
    }

    @Test
    @DisplayName(
        "an absurd number of reps is rejected")
    void absurdRepsAreRejected() {
        ExercisePrescription tooMany =
            new ExercisePrescription(1, SQUAT,
                3, 50, 8.0, 50);

        assertThat(validator.validateBlock(
                blockWith(tooMany), strategy,
                MAXES, GYM).orElseThrow())
            .contains("50 reps");
    }

    @Test
    @DisplayName(
        "a week with no sessions is rejected")
    void emptyWeekIsRejected() {
        TrainingBlock block =
            new TrainingBlock(0, START, "RPE",
                "test", BlockOrigin.GENERATED,
                List.of(new Week(1, List.of())));

        assertThat(validator.validateBlock(
                block, strategy, MAXES, GYM)
            .orElseThrow())
            .contains("no sessions");
    }

    @Test
    @DisplayName(
        "more sessions than days in a week is"
        + " rejected")
    void tooManySessionsIsRejected() {
        List<Session> sessions =
                new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            sessions.add(new Session(i,
                START.plusDays(i), "day",
                List.of(squatAt(162))));
        }
        TrainingBlock block =
            new TrainingBlock(0, START, "RPE",
                "test", BlockOrigin.GENERATED,
                List.of(new Week(1, sessions)));

        assertThat(validator.validateBlock(
                block, strategy, MAXES, GYM)
            .orElseThrow())
            .contains("days in a week");
    }

    @Test
    @DisplayName(
        "a plan with no weeks at all is"
        + " rejected")
    void emptyPlanIsRejected() {
        TrainingBlock empty =
            new TrainingBlock(0, START, "RPE",
                "test", BlockOrigin.GENERATED,
                List.of());

        assertThat(validator.validateBlock(
                empty, strategy, MAXES, GYM))
            .isPresent();
        assertThat(validator.validateBlock(
                null, strategy, MAXES, GYM))
            .isPresent();
    }

    @Test
    @DisplayName(
        "every problem is reported at once, not"
        + " one per retry")
    void allProblemsAreReportedTogether() {
        ExercisePrescription tooHeavy =
            new ExercisePrescription(1, SQUAT,
                4, 5, 8.0, 250);
        ExercisePrescription unavailable =
            new ExercisePrescription(2,
                BELT_SQUAT, 3, 10, 8.0, 100);

        String reason = validator.validateBlock(
                blockWith(tooHeavy, unavailable),
                strategy, MAXES, GYM)
            .orElseThrow();

        assertThat(reason)
            .contains("Back Squat")
            .contains("Belt Squat");
    }

    @Test
    @DisplayName(
        "a later week allows more weight under"
        + " a progressing strategy")
    void laterWeeksAllowMore() {
        ProgressionStrategy linear =
            new ca.yorku.eecs3311.spotter.domain
                .LinearProgression();

        // 5 at RPE 8 of 200 kg is 162.2 kg.
        // Linear allows 5 percent either way,
        // so week 1 tops out near 170 kg and
        // week 4, after three increments of
        // 2.5 kg, reaches past 178 kg.
        double onlyValidLater = 176;

        TrainingBlock week4 =
            new TrainingBlock(0, START, "linear",
                "test", BlockOrigin.GENERATED,
                List.of(new Week(4, List.of(
                    new Session(1, START, "day",
                        List.of(squatAt(
                            onlyValidLater))))))); 

        assertThat(validator.validateBlock(
                week4, linear, MAXES, GYM))
            .isEmpty();
        assertThat(validator.validateBlock(
                blockWith(squatAt(
                    onlyValidLater)),
                linear, MAXES, GYM))
            .isPresent();
    }
}
