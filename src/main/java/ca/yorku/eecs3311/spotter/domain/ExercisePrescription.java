package ca.yorku.eecs3311.spotter.domain;

import java.util.Optional;

/**
 * One prescribed exercise: what to do, how much,
 * and how hard.
 *
 * Immutable. A change produces a new instance,
 * which is what makes the undo stack simple: a
 * command keeps the instance it replaced and puts
 * it back.
 */
public final class ExercisePrescription {

    private final int id;
    private final Exercise exercise;
    private final int sets;
    private final int reps;
    private final Double targetRpe;
    private final double loadKg;

    public ExercisePrescription(
            int id,
            Exercise exercise,
            int sets,
            int reps,
            Double targetRpe,
            double loadKg) {
        if (exercise == null) {
            throw new IllegalArgumentException(
                    "A prescription needs an"
                    + " exercise");
        }
        if (sets <= 0) {
            throw new IllegalArgumentException(
                    "A prescription needs at"
                    + " least one set");
        }
        if (reps <= 0) {
            throw new IllegalArgumentException(
                    "A prescription needs at"
                    + " least one rep");
        }
        if (loadKg < 0) {
            throw new IllegalArgumentException(
                    "A load cannot be negative");
        }
        if (targetRpe != null
                && (targetRpe < 1
                    || targetRpe > 10)) {
            throw new IllegalArgumentException(
                    "A target RPE must be"
                    + " between 1 and 10");
        }
        this.id = id;
        this.exercise = exercise;
        this.sets = sets;
        this.reps = reps;
        this.targetRpe = targetRpe;
        this.loadKg = loadKg;
    }

    public int id() {
        return id;
    }

    public Exercise exercise() {
        return exercise;
    }

    public int sets() {
        return sets;
    }

    public int reps() {
        return reps;
    }

    public Optional<Double> targetRpe() {
        return Optional.ofNullable(targetRpe);
    }

    public double loadKg() {
        return loadKg;
    }

    /** Planned weight moved, in kilograms. */
    public double plannedTonnage() {
        return loadKg * sets * reps;
    }

    public ExercisePrescription withLoad(
            double newLoadKg) {
        return new ExercisePrescription(id,
                exercise, sets, reps, targetRpe,
                newLoadKg);
    }

    public ExercisePrescription withExercise(
            Exercise replacement) {
        return new ExercisePrescription(id,
                replacement, sets, reps,
                targetRpe, loadKg);
    }

    public ExercisePrescription withSets(
            int newSets) {
        return new ExercisePrescription(id,
                exercise, newSets, reps,
                targetRpe, loadKg);
    }

    @Override
    public String toString() {
        return String.format(
                "%s %dx%d @ %.1fkg%s",
                exercise.name(), sets, reps,
                loadKg,
                targetRpe == null
                    ? ""
                    : " RPE " + targetRpe);
    }
}
