package ca.yorku.eecs3311.spotter.domain;

/**
 * A quantity of one food, eaten.
 *
 * @param food  what was eaten
 * @param grams how much
 */
public record FoodEntry(
        FoodItem food,
        double grams) {

    public FoodEntry {
        if (food == null) {
            throw new IllegalArgumentException(
                    "An entry needs a food");
        }
        if (grams <= 0) {
            throw new IllegalArgumentException(
                    "An entry needs a weight"
                    + " above zero");
        }
    }

    public MacroTotals totals() {
        return food.forGrams(grams);
    }
}
