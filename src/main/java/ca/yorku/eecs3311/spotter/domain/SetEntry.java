package ca.yorku.eecs3311.spotter.domain;

import java.util.Optional;

/**
 * One performed set.
 *
 * RPE is optional because not every logged set
 * carries one. That matters: the one rep max
 * estimate needs an RPE, so sets without one are
 * skipped by the analytics rather than guessed
 * at.
 */
public final class SetEntry {

    private final Exercise exercise;
    private final double weightKg;
    private final int reps;
    private final Double rpe;

    private SetEntry(
            Exercise exercise,
            double weightKg,
            int reps,
            Double rpe) {
        if (exercise == null) {
            throw new IllegalArgumentException(
                    "A set needs an exercise");
        }
        if (weightKg < 0) {
            throw new IllegalArgumentException(
                    "Weight cannot be negative");
        }
        if (reps <= 0) {
            throw new IllegalArgumentException(
                    "A set needs at least one"
                    + " rep");
        }
        if (rpe != null
                && (rpe < 1 || rpe > 10)) {
            throw new IllegalArgumentException(
                    "RPE must be between 1 and"
                    + " 10, but was " + rpe);
        }
        this.exercise = exercise;
        this.weightKg = weightKg;
        this.reps = reps;
        this.rpe = rpe;
    }

    public static SetEntry of(
            Exercise exercise,
            double weightKg,
            int reps,
            double rpe) {
        return new SetEntry(exercise, weightKg,
                reps, rpe);
    }

    /** A set logged without an RPE. */
    public static SetEntry withoutRpe(
            Exercise exercise,
            double weightKg,
            int reps) {
        return new SetEntry(exercise, weightKg,
                reps, null);
    }

    public Exercise exercise() {
        return exercise;
    }

    public double weightKg() {
        return weightKg;
    }

    public int reps() {
        return reps;
    }

    public Optional<Double> rpe() {
        return Optional.ofNullable(rpe);
    }

    public boolean hasRpe() {
        return rpe != null;
    }

    /** Weight moved by this set, in kilograms. */
    public double tonnage() {
        return weightKg * reps;
    }

    @Override
    public String toString() {
        return String.format(
                "%s %.1fkg x %d%s",
                exercise.name(), weightKg, reps,
                rpe == null
                    ? ""
                    : " @" + rpe);
    }
}
