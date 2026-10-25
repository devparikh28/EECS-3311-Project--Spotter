package ca.yorku.eecs3311.spotter.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One movement in the catalog.
 *
 * Equipment is what makes substitution possible:
 * when a gym lacks something, the deterministic
 * filter keeps only the exercises whose required
 * equipment the lifter actually has, and the
 * agent chooses among those.
 */
public final class Exercise {

    private final int id;
    private final String name;
    private final Set<String> muscleGroups;
    private final String movementPattern;
    private final Set<Equipment> requiredEquipment;
    private final Lift competitionLift;

    public Exercise(
            int id,
            String name,
            Set<String> muscleGroups,
            String movementPattern,
            Set<Equipment> requiredEquipment,
            Lift competitionLift) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "An exercise needs a name");
        }
        this.id = id;
        this.name = name.trim();
        this.muscleGroups =
                copyOf(muscleGroups);
        this.movementPattern = movementPattern;
        this.requiredEquipment =
                copyOf(requiredEquipment);
        this.competitionLift = competitionLift;
    }

    private static <T> Set<T> copyOf(
            Set<T> source) {
        if (source == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(source));
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Set<String> muscleGroups() {
        return muscleGroups;
    }

    public Optional<String> movementPattern() {
        return Optional.ofNullable(
                movementPattern);
    }

    public Set<Equipment> requiredEquipment() {
        return requiredEquipment;
    }

    /**
     * The competition lift this counts as, when
     * it is one. Only these feed the strength
     * analytics for squat, bench and deadlift.
     */
    public Optional<Lift> competitionLift() {
        return Optional.ofNullable(
                competitionLift);
    }

    public boolean isCompetitionLift() {
        return competitionLift != null;
    }

    /**
     * True when the lifter has everything this
     * movement needs. An exercise requiring
     * nothing, such as a push up, is always
     * performable.
     */
    public boolean isPerformableWith(
            Set<Equipment> available) {
        if (requiredEquipment.isEmpty()) {
            return true;
        }
        if (available == null) {
            return false;
        }
        return available.containsAll(
                requiredEquipment);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Exercise that)) {
            return false;
        }
        return name.equalsIgnoreCase(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name.toLowerCase());
    }

    @Override
    public String toString() {
        return name;
    }
}
