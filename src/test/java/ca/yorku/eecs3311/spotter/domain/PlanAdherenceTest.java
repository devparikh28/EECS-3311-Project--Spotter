package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the missing half of every progression
 * decision.
 *
 * A block that is not working and a block that
 * was not done look identical from the analytics
 * alone, and the right response to each is
 * opposite. These tests pin down the distinction,
 * including the case that matters most: a lifter
 * who trains every session at half the prescribed
 * loads has a perfect attendance record and is
 * not doing the programme.
 */
class PlanAdherenceTest {

    private static final LocalDate START =
            LocalDate.of(2026, 10, 5);

    private Exercise squat;
    private Exercise bench;
    private TrainingBlock block;

    @BeforeEach
    void setUp() {
        squat = new Exercise(1, "Back Squat",
            Set.of("quads"), "squat",
            Set.of(Equipment.BARBELL),
            Lift.SQUAT);
        bench = new Exercise(2, "Bench Press",
            Set.of("chest"),
            "horizontal press",
            Set.of(Equipment.BARBELL),
            Lift.BENCH);

        // 5 x 5 at 140 is 3500 kg
        Session monday = new Session(1, START,
            "squat", List.of(
                new ExercisePrescription(1,
                    squat, 5, 5, 8.0, 140.0)));
        // 5 x 5 at 100 is 2500 kg
        Session wednesday = new Session(2,
            START.plusDays(2), "bench",
            List.of(
                new ExercisePrescription(2,
                    bench, 5, 5, 8.0, 100.0)));

        block = new TrainingBlock(1, START,
            "linear", "upper lower",
            BlockOrigin.GENERATED,
            List.of(new Week(1,
                List.of(monday, wednesday))));
    }

    private WorkoutSession trained(
            LocalDate date,
            Exercise exercise,
            double kg,
            int sets) {
        List<SetEntry> entries =
                new java.util.ArrayList<>();
        for (int i = 0; i < sets; i++) {
            entries.add(SetEntry.of(exercise,
                    kg, 5, 8));
        }
        return new WorkoutSession(0, date,
                "test", entries);
    }

    @Test
    @DisplayName(
        "a block trained as written reports"
        + " full adherence")
    void aBlockDoneAsWritten() {
        PlanAdherence adherence =
            PlanAdherence.of(block, List.of(
                trained(START, squat, 140, 5),
                trained(START.plusDays(2),
                        bench, 100, 5)),
                START.plusDays(7));

        assertThat(adherence.planned())
            .isEqualTo(2);
        assertThat(adherence.completed())
            .isEqualTo(2);
        assertThat(adherence.missed()).isZero();
        assertThat(adherence.sessionRate())
            .isEqualTo(1.0);
        assertThat(adherence.tonnageRate())
            .isEqualTo(1.0);
        assertThat(adherence.liftsShortOfPlan())
            .isEmpty();
    }

    @Test
    @DisplayName(
        "a missed session is counted as missed"
        + " rather than as light")
    void aMissedSession() {
        PlanAdherence adherence =
            PlanAdherence.of(block, List.of(
                trained(START, squat, 140, 5)),
                START.plusDays(7));

        assertThat(adherence.completed())
            .isEqualTo(1);
        assertThat(adherence.missed())
            .isEqualTo(1);
        assertThat(adherence.sessionRate())
            .isEqualTo(0.5);
        assertThat(adherence.tonnageRate())
            .isEqualTo(3500.0 / 6000.0);
    }

    @Test
    @DisplayName(
        "training every session at half the"
        + " loads is perfect attendance and poor"
        + " adherence")
    void perfectAttendanceIsNotAdherence() {
        PlanAdherence adherence =
            PlanAdherence.of(block, List.of(
                trained(START, squat, 70, 5),
                trained(START.plusDays(2),
                        bench, 50, 5)),
                START.plusDays(7));

        assertThat(adherence.sessionRate())
            .isEqualTo(1.0);
        assertThat(adherence.tonnageRate())
            .isEqualTo(0.5);
        assertThat(adherence.liftsShortOfPlan())
            .containsExactlyInAnyOrder(
                Lift.SQUAT, Lift.BENCH);
    }

    @Test
    @DisplayName(
        "rounding to the nearest plate does not"
        + " count as falling short")
    void roundingIsNotFallingShort() {
        PlanAdherence adherence =
            PlanAdherence.of(block, List.of(
                trained(START, squat, 137.5, 5),
                trained(START.plusDays(2),
                        bench, 97.5, 5)),
                START.plusDays(7));

        assertThat(adherence.liftsShortOfPlan())
            .isEmpty();
    }

    @Test
    @DisplayName(
        "sessions that have not come due yet"
        + " are not counted against the lifter")
    void futureSessionsAreNotJudged() {
        PlanAdherence adherence =
            PlanAdherence.of(block, List.of(
                trained(START, squat, 140, 5)),
                START);

        assertThat(adherence.planned())
            .isEqualTo(1);
        assertThat(adherence.missed()).isZero();
        assertThat(adherence.sessionRate())
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName(
        "the heaviest load planned and the"
        + " heaviest reached are both reported")
    void topLoadsAreReported() {
        PlanAdherence adherence =
            PlanAdherence.of(block, List.of(
                trained(START, squat, 145, 3),
                trained(START.plusDays(2),
                        bench, 100, 5)),
                START.plusDays(7));

        assertThat(adherence.plannedTopLoads())
            .containsEntry(Lift.SQUAT, 140.0)
            .containsEntry(Lift.BENCH, 100.0);
        assertThat(adherence.actualTopLoads())
            .containsEntry(Lift.SQUAT, 145.0);
        assertThat(adherence.liftsShortOfPlan())
            .isEmpty();
    }

    @Test
    @DisplayName(
        "no block means nothing to judge rather"
        + " than nothing done")
    void noBlockIsEmpty() {
        PlanAdherence adherence =
            PlanAdherence.of(null, List.of(),
                    START);

        assertThat(adherence.isEmpty()).isTrue();
        assertThat(adherence.describe())
            .contains("nothing to judge");
    }

    @Test
    @DisplayName(
        "the summary states the figures a"
        + " review would need")
    void theSummaryStatesTheFigures() {
        PlanAdherence adherence =
            PlanAdherence.of(block, List.of(
                trained(START, squat, 140, 5)),
                START.plusDays(7));

        assertThat(adherence.describe())
            .contains("1 of 2 trained")
            .contains("3500")
            .contains("6000")
            .contains("SQUAT");
    }

    @Test
    @DisplayName(
        "extra work on a planned day counts"
        + " towards the day's total")
    void extraWorkCounts() {
        PlanAdherence adherence =
            PlanAdherence.of(block, List.of(
                trained(START, squat, 140, 5),
                trained(START, bench, 100, 2),
                trained(START.plusDays(2),
                        bench, 100, 5)),
                START.plusDays(7));

        assertThat(adherence.tonnageRate())
            .isGreaterThan(1.0);
        assertThat(adherence.sessions().get(0)
                .actualTonnage())
            .isEqualTo(4500.0);
    }
}
