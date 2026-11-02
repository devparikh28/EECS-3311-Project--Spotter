package ca.yorku.eecs3311.spotter
        .infrastructure.catalog;

import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain.FoodItem;

/**
 * The foods Spotter knows about.
 *
 * The same arrangement as the exercise catalog
 * and for the same reason: nutrition figures come
 * from here, never from a model's reply. A meal
 * suggestion naming a food that is not in the
 * catalog fails to parse, and the calories of one
 * that is are whatever this says they are.
 */
public interface FoodCatalog {

    /** Finds by name, ignoring case. */
    Optional<FoodItem> byName(String name);

    List<FoodItem> all();

    /**
     * Everything the lifter will actually eat.
     * This is the deterministic filter that runs
     * before the agent is asked for a meal, so a
     * food they do not eat never reaches it.
     */
    List<FoodItem> permittedBy(
            DietaryConstraints constraints);

    /** Names closest to the one given. */
    List<FoodItem> closestMatches(
            String name, int limit);

    /** Adds a food, returning it. */
    FoodItem add(FoodItem food);
}
