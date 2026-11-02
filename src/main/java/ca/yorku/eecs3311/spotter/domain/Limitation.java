package ca.yorku.eecs3311.spotter.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Something the lifter cannot or should not do.
 *
 * Recorded as movement patterns rather than only
 * named exercises, because a shoulder that
 * objects to flat barbell pressing objects to
 * every flat barbell press, not just the one the
 * lifter happened to name.
 *
 * @param description what the lifter said
 * @param avoidPatterns movement patterns to skip
 * @param avoidExercises specific movements to skip
 * @param painful whether it hurts rather than
 *        merely being awkward
 */
public record Limitation(
        String description,
        Set<String> avoidPatterns,
        Set<String> avoidExercises,
        boolean painful) {

    public Limitation {
        if (description == null
                || description.isBlank()) {
            throw new IllegalArgumentException(
                "A limitation needs a"
                + " description");
        }
        description = description.trim();
        avoidPatterns = lowerCased(
                avoidPatterns);
        avoidExercises = lowerCased(
                avoidExercises);
    }

    private static Set<String> lowerCased(
            Set<String> source) {
        if (source == null) {
            return Set.of();
        }
        Set<String> copy =
                new LinkedHashSet<>();
        for (String value : source) {
            if (value != null
                    && !value.isBlank()) {
                copy.add(value.trim()
                    .toLowerCase(
                        java.util.Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    /** True when this rules the exercise out. */
    public boolean rulesOut(Exercise exercise) {
        if (exercise == null) {
            return false;
        }
        String name = exercise.name()
            .toLowerCase(java.util.Locale.ROOT);
        if (avoidExercises.contains(name)) {
            return true;
        }
        return exercise.movementPattern()
            .map(pattern -> avoidPatterns
                .contains(pattern.toLowerCase(
                    java.util.Locale.ROOT)))
            .orElse(false);
    }
}
