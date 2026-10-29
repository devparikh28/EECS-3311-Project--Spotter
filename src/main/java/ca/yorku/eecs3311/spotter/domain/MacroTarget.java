package ca.yorku.eecs3311.spotter.domain;

/**
 * What the lifter is aiming to eat in a day.
 *
 * @param calories kilocalories
 * @param proteinG grams of protein
 * @param carbsG   grams of carbohydrate
 * @param fatG     grams of fat
 */
public record MacroTarget(
        int calories,
        double proteinG,
        double carbsG,
        double fatG) {

    public MacroTarget {
        if (calories <= 0) {
            throw new IllegalArgumentException(
                    "A calorie target must be"
                    + " above zero");
        }
        if (proteinG < 0 || carbsG < 0
                || fatG < 0) {
            throw new IllegalArgumentException(
                    "Macro targets cannot be"
                    + " negative");
        }
    }

    public MacroTotals asTotals() {
        return new MacroTotals(calories,
                proteinG, carbsG, fatG);
    }
}
