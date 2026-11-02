package ca.yorku.eecs3311.spotter.domain;

import java.util.Optional;

/**
 * One exercise swapped for another.
 *
 * A substitution keeps the prescription it came
 * from, so the sets and reps the coach wrote are
 * not lost when the movement changes. Only the
 * movement and the load move, because a dumbbell
 * press at the same weight as a barbell press is
 * a different session entirely.
 *
 * The rationale is kept because the lifter
 * deserves to know why they are being asked to do
 * something other than what the plan says, and
 * because Stage 2 has to show that the agent's
 * reasoning was recorded rather than discarded.
 */
public final class Substitution {

    private final ExercisePrescription original;
    private final Exercise replacement;
    private final double loadKg;
    private final SubstitutionReason reason;
    private final String rationale;

    public Substitution(
            ExercisePrescription original,
            Exercise replacement,
            double loadKg,
            SubstitutionReason reason,
            String rationale) {
        if (original == null) {
            throw new IllegalArgumentException(
                "A substitution needs the"
                + " prescription it replaces");
        }
        if (replacement == null) {
            throw new IllegalArgumentException(
                "A substitution needs a"
                + " replacement movement");
        }
        if (loadKg < 0) {
            throw new IllegalArgumentException(
                "A load cannot be negative");
        }
        if (reason == null) {
            throw new IllegalArgumentException(
                "A substitution needs a reason");
        }
        this.original = original;
        this.replacement = replacement;
        this.loadKg = loadKg;
        this.reason = reason;
        this.rationale = rationale;
    }

    public ExercisePrescription original() {
        return original;
    }

    public Exercise replaced() {
        return original.exercise();
    }

    public Exercise replacement() {
        return replacement;
    }

    public double loadKg() {
        return loadKg;
    }

    public SubstitutionReason reason() {
        return reason;
    }

    public Optional<String> rationale() {
        return Optional.ofNullable(rationale);
    }

    /**
     * True when the replacement is the movement
     * it is meant to replace, which is never a
     * useful answer.
     */
    public boolean isNoChange() {
        return replacement.equals(
                original.exercise());
    }

    /**
     * How the new load compares with the old one,
     * or empty when the original was unloaded and
     * there is nothing to compare.
     */
    public Optional<Double> loadRatio() {
        if (original.loadKg() <= 0) {
            return Optional.empty();
        }
        return Optional.of(
                loadKg / original.loadKg());
    }

    /**
     * True when the replacement trains something
     * the original trained. A substitute sharing
     * neither the movement pattern nor a single
     * muscle group is not a substitute.
     */
    public boolean trainsSomethingSimilar() {
        Exercise from = original.exercise();
        Optional<String> fromPattern =
                from.movementPattern();
        Optional<String> toPattern =
                replacement.movementPattern();
        if (fromPattern.isPresent()
                && toPattern.isPresent()
                && fromPattern.get()
                    .equalsIgnoreCase(
                        toPattern.get())) {
            return true;
        }
        for (String muscle
                : from.muscleGroups()) {
            for (String other
                    : replacement
                        .muscleGroups()) {
                if (muscle.equalsIgnoreCase(
                        other)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The prescription with the swap applied. */
    public ExercisePrescription apply() {
        return original
                .withExercise(replacement)
                .withLoad(loadKg);
    }

    @Override
    public String toString() {
        return original.exercise().name()
            + " to " + replacement.name()
            + String.format(" at %.1f kg",
                    loadKg);
    }
}
