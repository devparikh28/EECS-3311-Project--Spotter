package ca.yorku.eecs3311.spotter
        .infrastructure.sample;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain.SetEntry;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .WorkoutRepository;

/**
 * Sample data, so the application can be run and
 * demonstrated without anyone's real export.
 *
 * The training history is deliberately realistic
 * rather than tidy: squat progresses, bench
 * stalls, and some sets carry no RPE. A history
 * where everything improves smoothly would hide
 * exactly the behaviour the analytics exist to
 * show.
 */
public final class SampleData {

    /** Weeks of history the sample covers. */
    public static final int WEEKS = 8;

    private SampleData() {
    }

    /** The movements the sample history uses. */
    public static List<Exercise> exercises() {
        return List.of(
            new Exercise(0, "Back Squat",
                Set.of("quads", "glutes"),
                "squat",
                Set.of(Equipment.BARBELL,
                       Equipment.SQUAT_RACK),
                Lift.SQUAT),
            new Exercise(0, "Bench Press",
                Set.of("chest", "triceps"),
                "horizontal press",
                Set.of(Equipment.BARBELL,
                       Equipment.BENCH),
                Lift.BENCH),
            new Exercise(0, "Deadlift",
                Set.of("hamstrings", "back"),
                "hinge",
                Set.of(Equipment.BARBELL,
                       Equipment.PLATFORM),
                Lift.DEADLIFT),
            new Exercise(0, "Front Squat",
                Set.of("quads"),
                "squat",
                Set.of(Equipment.BARBELL,
                       Equipment.SQUAT_RACK),
                null),
            new Exercise(0, "Romanian Deadlift",
                Set.of("hamstrings"),
                "hinge",
                Set.of(Equipment.BARBELL),
                null),
            new Exercise(0, "Leg Press",
                Set.of("quads"),
                "squat",
                Set.of(Equipment.LEG_PRESS),
                null),
            new Exercise(0, "Dumbbell Bench",
                Set.of("chest"),
                "horizontal press",
                Set.of(Equipment.DUMBBELL,
                       Equipment.BENCH),
                null),
            new Exercise(0, "Barbell Row",
                Set.of("back"),
                "horizontal pull",
                Set.of(Equipment.BARBELL),
                null),
            new Exercise(0, "Pull Up",
                Set.of("back"),
                "vertical pull",
                Set.of(Equipment.PULL_UP_BAR),
                null),
            new Exercise(0, "Leg Curl",
                Set.of("hamstrings"),
                "knee flexion",
                Set.of(Equipment.LEG_CURL),
                null));
    }

    /**
     * Loads the sample into a database, ending on
     * the day given.
     *
     * @return how many sessions were written
     */
    public static int load(
            ExerciseCatalog catalog,
            WorkoutRepository workouts,
            LocalDate today) {
        for (Exercise exercise : exercises()) {
            catalog.add(exercise);
        }
        Exercise squat = catalog
                .byName("Back Squat")
                .orElseThrow();
        Exercise bench = catalog
                .byName("Bench Press")
                .orElseThrow();
        Exercise deadlift = catalog
                .byName("Deadlift")
                .orElseThrow();
        Exercise row = catalog
                .byName("Barbell Row")
                .orElseThrow();
        Exercise legCurl = catalog
                .byName("Leg Curl")
                .orElseThrow();

        LocalDate start = today.minusWeeks(WEEKS);
        int written = 0;

        for (int week = 0; week < WEEKS; week++) {
            LocalDate monday =
                    start.plusWeeks(week);

            // Squat climbs 2.5 kg a week.
            double squatLoad = 100 + week * 2.5;
            workouts.save(new WorkoutSession(0,
                monday, "sample",
                List.of(
                    SetEntry.of(squat,
                        squatLoad, 5, 7.5),
                    SetEntry.of(squat,
                        squatLoad, 5, 8),
                    SetEntry.of(squat,
                        squatLoad, 5, 8.5),
                    SetEntry.withoutRpe(legCurl,
                        45, 12))));
            written++;

            // Bench stalls after week four,
            // which is what the analytics
            // should surface.
            double benchLoad = week < 4
                    ? 70 + week * 2.5
                    : 77.5;
            workouts.save(new WorkoutSession(0,
                monday.plusDays(2), "sample",
                List.of(
                    SetEntry.of(bench,
                        benchLoad, 5, 8),
                    SetEntry.of(bench,
                        benchLoad, 5, 8.5),
                    SetEntry.of(row,
                        65, 8, 8))));
            written++;

            // Deadlift is trained heavy every
            // other week.
            if (week % 2 == 0) {
                double pullLoad =
                        140 + week * 2.5;
                workouts.save(
                    new WorkoutSession(0,
                        monday.plusDays(4),
                        "sample",
                        List.of(
                            SetEntry.of(deadlift,
                                pullLoad, 3, 8),
                            SetEntry.of(deadlift,
                                pullLoad, 3,
                                8.5))));
                written++;
            }
        }
        return written;
    }

    /** Convenience for a load ending today. */
    public static int load(
            ExerciseCatalog catalog,
            WorkoutRepository workouts) {
        return load(catalog, workouts,
                LocalDate.now());
    }

    /** Sessions the sample writes, for messages. */
    public static List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add(WEEKS + " weeks of training");
        lines.add("squat progressing 2.5 kg a"
                + " week");
        lines.add("bench stalling after week"
                + " four");
        lines.add("deadlift trained every other"
                + " week");
        lines.add("some accessory sets logged"
                + " without an RPE");
        return List.copyOf(lines);
    }
}
