package ca.yorku.eecs3311.spotter.domain;

/**
 * One food in the catalog, with its nutrition per
 * hundred grams.
 *
 * The dietary flags are what the meal suggestion
 * feature filters on, and they are facts read
 * from here rather than from the model's reply.
 *
 * @param name           display name
 * @param per100g        nutrition per 100 g
 * @param vegetarian     contains no meat or fish
 * @param containsDairy  contains dairy
 * @param containsEgg    contains egg
 */
public record FoodItem(
        String name,
        MacroTotals per100g,
        boolean vegetarian,
        boolean containsDairy,
        boolean containsEgg) {

    public FoodItem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "A food needs a name");
        }
        if (per100g == null) {
            throw new IllegalArgumentException(
                    "A food needs its nutrition");
        }
        name = name.trim();
    }

    /** Nutrition for a given weight in grams. */
    public MacroTotals forGrams(double grams) {
        if (grams < 0) {
            throw new IllegalArgumentException(
                    "Grams cannot be negative");
        }
        return per100g.scaled(grams / 100.0);
    }
}
