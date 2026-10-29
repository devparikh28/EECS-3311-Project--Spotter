package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.api.Assertions
        .within;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for the planned block structure. */
class TrainingBlockTest {

    private static final LocalDate START =
            LocalDate.of(2026, 11, 2);

    private ExercisePrescription squatSets;
    private ExercisePrescription benchSets;
    private Session monday;
    private Session thursday;
    private TrainingBlock block;

    @BeforeEach
    void setUp() {
        squatSets = new ExercisePrescription(
                101, TestData.BACK_SQUAT,
                4, 5, 8.0, 100);
        benchSets = new ExercisePrescription(
                102, TestData.BENCH_PRESS,
                3, 8, 7.5, 70);

        monday = new Session(1, START,
                "Squat focus",
                List.of(squatSets));
        thursday = new Session(2,
                START.plusDays(3),
                "Bench focus",
                List.of(benchSets));

        block = new TrainingBlock(1, START,
                "RPE", "Upper Lower",
                BlockOrigin.GENERATED,
                List.of(new Week(1, List.of(
                        monday, thursday))));
    }

    @Test
    @DisplayName(
        "a session can be found by its id")
    void sessionIsFoundById() {
        assertThat(block.findSession(2))
                .contains(thursday);
        assertThat(block.findSession(99))
                .isEmpty();
    }

    @Test
    @DisplayName(
        "a prescription is found anywhere in"
        + " the block, without knowing its"
        + " session")
    void prescriptionIsFoundAnywhere() {
        assertThat(block.findPrescription(102))
                .contains(benchSets);
        assertThat(block.sessionContaining(102))
                .contains(thursday);
        assertThat(block.findPrescription(999))
                .isEmpty();
    }

    @Test
    @DisplayName(
        "replacing a prescription keeps its"
        + " place and returns the old one")
    void replacementKeepsItsPlace() {
        ExercisePrescription lighter =
                squatSets.withLoad(85);

        assertThat(monday.replacePrescription(
                lighter)).contains(squatSets);
        assertThat(monday.prescriptions())
                .containsExactly(lighter);
        assertThat(block.findPrescription(101))
                .contains(lighter);
    }

    @Test
    @DisplayName(
        "replacing an id that is not there"
        + " changes nothing")
    void unknownReplacementChangesNothing() {
        ExercisePrescription stranger =
                new ExercisePrescription(777,
                    TestData.DEADLIFT,
                    1, 1, 9.0, 180);

        assertThat(monday.replacePrescription(
                stranger)).isEmpty();
        assertThat(monday.prescriptions())
                .containsExactly(squatSets);
    }

    @Test
    @DisplayName(
        "a changed prescription is a new one,"
        + " leaving the original intact")
    void changesProduceNewInstances() {
        ExercisePrescription lighter =
                squatSets.withLoad(85);
        ExercisePrescription swapped =
                squatSets.withExercise(
                        TestData.DEADLIFT);

        assertThat(squatSets.loadKg())
                .isEqualTo(100);
        assertThat(lighter.loadKg())
                .isEqualTo(85);
        assertThat(lighter.id())
                .isEqualTo(squatSets.id());
        assertThat(swapped.exercise())
                .isEqualTo(TestData.DEADLIFT);
        assertThat(swapped.sets())
                .isEqualTo(squatSets.sets());
    }

    @Test
    @DisplayName(
        "planned tonnage adds up through the"
        + " block")
    void plannedTonnageAddsUp() {
        assertThat(squatSets.plannedTonnage())
            .isEqualTo(2000, within(0.001));
        assertThat(monday.plannedTonnage())
            .isEqualTo(2000, within(0.001));
        assertThat(block.weeks().get(0)
                .plannedTonnage())
            .isEqualTo(2000 + 1680,
                    within(0.001));
    }

    @Test
    @DisplayName(
        "an adjustment note records that a"
        + " session was changed")
    void adjustmentNoteIsRecorded() {
        assertThat(monday.wasAdjusted())
                .isFalse();

        monday.setAdjustmentNote(
                "Reduced after poor recovery");

        assertThat(monday.wasAdjusted())
                .isTrue();
        assertThat(monday.adjustmentNote())
            .contains(
                "Reduced after poor recovery");
    }

    @Test
    @DisplayName(
        "an adopted block is marked as such")
    void adoptedBlockIsMarked() {
        TrainingBlock adopted =
            new TrainingBlock(2, START,
                "Linear", "5x5",
                BlockOrigin.ADOPTED,
                List.of());

        assertThat(adopted.origin())
                .isEqualTo(BlockOrigin.ADOPTED);
        assertThat(block.origin())
            .isEqualTo(BlockOrigin.GENERATED);
    }

    @Test
    @DisplayName(
        "the origin defaults to generated")
    void originDefaultsToGenerated() {
        TrainingBlock noOrigin =
            new TrainingBlock(3, START,
                null, null, null, List.of());

        assertThat(noOrigin.origin())
            .isEqualTo(BlockOrigin.GENERATED);
    }

    @Test
    @DisplayName(
        "sessions cannot be added from outside"
        + " the block")
    void structureIsNotModifiableFromOutside() {
        assertThatThrownBy(() ->
                block.weeks().get(0).sessions()
                    .add(monday))
            .isInstanceOf(
                UnsupportedOperationException
                    .class);
        assertThatThrownBy(() ->
                monday.prescriptions()
                    .add(benchSets))
            .isInstanceOf(
                UnsupportedOperationException
                    .class);
    }

    @Test
    @DisplayName(
        "a prescription with no sets or reps"
        + " is refused")
    void emptyPrescriptionIsRefused() {
        assertThatThrownBy(() ->
            new ExercisePrescription(1,
                TestData.BACK_SQUAT,
                0, 5, 8.0, 100))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("one set");

        assertThatThrownBy(() ->
            new ExercisePrescription(1,
                TestData.BACK_SQUAT,
                4, 0, 8.0, 100))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("one rep");
    }

    @Test
    @DisplayName("weeks are numbered from one")
    void weeksAreNumberedFromOne() {
        assertThatThrownBy(() ->
                new Week(0, List.of()))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("from 1");
    }

    @Test
    @DisplayName(
        "every session in the block can be"
        + " listed in order")
    void allSessionsAreListed() {
        assertThat(block.allSessions())
            .containsExactly(monday, thursday);
    }
}
