package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Shared fixtures so each test reads as what it
 * is checking rather than as setup.
 */
final class TestData {

    static final Exercise BACK_SQUAT =
            new Exercise(1, "Back Squat",
                Set.of("quads", "glutes"),
                "squat",
                Set.of(Equipment.BARBELL,
                       Equipment.SQUAT_RACK),
                Lift.SQUAT);

    static final Exercise BENCH_PRESS =
            new Exercise(2, "Bench Press",
                Set.of("chest", "triceps"),
                "horizontal press",
                Set.of(Equipment.BARBELL,
                       Equipment.BENCH),
                Lift.BENCH);

    static final Exercise DEADLIFT =
            new Exercise(3, "Deadlift",
                Set.of("hamstrings", "back"),
                "hinge",
                Set.of(Equipment.BARBELL,
                       Equipment.PLATFORM),
                Lift.DEADLIFT);

    /** An accessory, so not a competition lift. */
    static final Exercise LEG_CURL =
            new Exercise(4, "Leg Curl",
                Set.of("hamstrings"),
                "knee flexion",
                Set.of(Equipment.LEG_CURL),
                null);

    /** Needs nothing, so always performable. */
    static final Exercise PUSH_UP =
            new Exercise(5, "Push Up",
                Set.of("chest"),
                "horizontal press",
                Set.of(),
                null);

    static WorkoutSession session(
            LocalDate date, SetEntry... sets) {
        return new WorkoutSession(0, date,
                "test", List.of(sets));
    }

    private TestData() {
    }
}
