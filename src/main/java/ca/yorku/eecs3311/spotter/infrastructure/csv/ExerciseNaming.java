package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import java.util.Locale;
import java.util.Set;

import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.Lift;

/**
 * Turns an export's exercise name into a catalog
 * entry.
 *
 * Hevy writes equipment in brackets, as in
 * "Squat (Barbell)", which is enough to recognise
 * the competition lifts and the equipment a
 * movement needs.
 */
public final class ExerciseNaming {

    private ExerciseNaming() {
    }

    /** Builds a catalog entry from a Hevy name. */
    public static Exercise fromHevy(String name) {
        String lower = name.toLowerCase(
                Locale.ROOT);
        return new Exercise(0, name,
                Set.of(),
                null,
                equipmentIn(lower),
                competitionLiftIn(lower));
    }

    /**
     * The competition lift a name denotes, or
     * null. Only the barbell versions count: a
     * dumbbell bench is not a competition bench
     * and must not feed the attempt planner.
     */
    static Lift competitionLiftIn(String lower) {
        boolean barbell =
                lower.contains("barbell")
                && !lower.contains("dumbbell");
        if (!barbell) {
            return null;
        }
        if (lower.startsWith("squat")) {
            return Lift.SQUAT;
        }
        if (lower.startsWith("bench press")) {
            return Lift.BENCH;
        }
        if (lower.startsWith("deadlift")) {
            return Lift.DEADLIFT;
        }
        return null;
    }

    static Set<Equipment> equipmentIn(
            String lower) {
        if (lower.contains("dumbbell")) {
            return Set.of(Equipment.DUMBBELL);
        }
        if (lower.contains("barbell")) {
            return Set.of(Equipment.BARBELL);
        }
        if (lower.contains("cable")) {
            return Set.of(Equipment.CABLE);
        }
        if (lower.contains("machine")) {
            return Set.of();
        }
        if (lower.contains("smith")) {
            return Set.of(
                    Equipment.SMITH_MACHINE);
        }
        return Set.of();
    }
}
