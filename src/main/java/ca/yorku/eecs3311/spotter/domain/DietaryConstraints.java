package ca.yorku.eecs3311.spotter.domain;

/**
 * What the lifter will and will not eat.
 *
 * permits is the deterministic filter the meal
 * suggestion feature applies: a food the lifter
 * does not eat never reaches the model, so the
 * model cannot propose it.
 *
 * @param vegetarian  no meat or fish
 * @param allowsDairy dairy is acceptable
 * @param allowsEggs  eggs are acceptable
 */
public record DietaryConstraints(
        boolean vegetarian,
        boolean allowsDairy,
        boolean allowsEggs) {

    /** No restrictions at all. */
    public static DietaryConstraints none() {
        return new DietaryConstraints(
                false, true, true);
    }

    /** True when this food is acceptable. */
    public boolean permits(FoodItem food) {
        if (food == null) {
            return false;
        }
        if (vegetarian && !food.vegetarian()) {
            return false;
        }
        if (!allowsDairy
                && food.containsDairy()) {
            return false;
        }
        if (!allowsEggs && food.containsEgg()) {
            return false;
        }
        return true;
    }
}
