package ca.yorku.eecs3311.spotter
        .infrastructure.catalog;

import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.Lift;

import java.util.Set;

/**
 * The movements Spotter knows about.
 *
 * This is where exercise facts come from. The
 * response parser reads them from here rather
 * than from the model's reply, which is what
 * stops a generated plan prescribing a movement
 * that does not exist.
 */
public interface ExerciseCatalog {

    /**
     * Finds by name or a learned alias, ignoring
     * case.
     */
    Optional<Exercise> byName(String name);

    List<Exercise> all();

    /** Everything counting as one competition lift. */
    List<Exercise> forLift(Lift lift);

    /**
     * Everything the lifter can actually perform
     * with the equipment they have. This is the
     * deterministic filter that runs before the
     * agent chooses a substitute.
     */
    List<Exercise> performableWith(
            Set<Equipment> available);

    /**
     * Names closest to the one given, for when an
     * import meets a name it does not recognise.
     */
    List<Exercise> closestMatches(
            String name, int limit);

    /** Adds an exercise, returning it with its id. */
    Exercise add(Exercise exercise);

    /**
     * Records that a name seen in an import means
     * this exercise, so the next import resolves
     * it without asking.
     */
    void addAlias(String alias, Exercise exercise);
}
