package ca.yorku.eecs3311.spotter
        .infrastructure.sample;

import java.util.List;

import ca.yorku.eecs3311.spotter.domain.FoodItem;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.FoodCatalog;

/**
 * A small food catalog to work from.
 *
 * Enough to demonstrate the meal feature and to
 * exercise the dietary filter, which needs foods
 * on both sides of every restriction: meat and
 * not, dairy and not, egg and not.
 *
 * The figures are per hundred grams and are the
 * ordinary published ones for these foods. They
 * are facts the model is given rather than asked
 * for, which is the whole point of the catalog.
 */
public final class SampleFoods {

    private SampleFoods() {
    }

    /**
     * Adds the sample foods.
     *
     * @return how many were written
     */
    public static int load(FoodCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "A catalog is required");
        }
        int written = 0;
        for (FoodItem food : foods()) {
            catalog.add(food);
            written++;
        }
        return written;
    }

    private static List<FoodItem> foods() {
        return List.of(
            // meat and fish
            food("Chicken Breast", 165, 31,
                    0, 3.6, false, false, false),
            food("Lean Beef Mince", 215, 26,
                    0, 12, false, false, false),
            food("Salmon Fillet", 208, 20,
                    0, 13, false, false, false),
            food("Tinned Tuna", 116, 26,
                    0, 1, false, false, false),

            // dairy and egg
            food("Greek Yogurt", 59, 10,
                    3.6, 0.4, true, true, false),
            food("Cottage Cheese", 98, 11,
                    3.4, 4.3, true, true, false),
            food("Whole Milk", 61, 3.2,
                    4.8, 3.3, true, true, false),
            food("Whole Egg", 155, 13,
                    1.1, 11, true, false, true),

            // plants
            food("White Rice", 130, 2.7,
                    28, 0.3, true, false, false),
            food("Brown Rice", 123, 2.7,
                    26, 1, true, false, false),
            food("Rolled Oats", 389, 17,
                    66, 7, true, false, false),
            food("Wholemeal Bread", 247, 13,
                    41, 3.4, true, false, false),
            food("Sweet Potato", 86, 1.6,
                    20, 0.1, true, false, false),
            food("Potato", 77, 2,
                    17, 0.1, true, false, false),
            food("Red Lentils", 116, 9,
                    20, 0.4, true, false, false),
            food("Chickpeas", 164, 9,
                    27, 2.6, true, false, false),
            food("Firm Tofu", 144, 17,
                    2.8, 9, true, false, false),
            food("Broccoli", 34, 2.8,
                    7, 0.4, true, false, false),
            food("Spinach", 23, 2.9,
                    3.6, 0.4, true, false, false),
            food("Banana", 89, 1.1,
                    23, 0.3, true, false, false),
            food("Olive Oil", 884, 0,
                    0, 100, true, false, false),
            food("Peanut Butter", 588, 25,
                    20, 50, true, false, false),
            food("Almonds", 579, 21,
                    22, 50, true, false, false));
    }

    private static FoodItem food(
            String name,
            double calories,
            double protein,
            double carbs,
            double fat,
            boolean vegetarian,
            boolean dairy,
            boolean egg) {
        return new FoodItem(name,
            new MacroTotals(calories, protein,
                    carbs, fat),
            vegetarian, dairy, egg);
    }
}
