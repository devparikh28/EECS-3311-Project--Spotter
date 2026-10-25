package ca.yorku.eecs3311.spotter.domain;

/**
 * Calories and the three macronutrients.
 *
 * Immutable: every operation returns a new value,
 * so a total can be handed around without any
 * caller being able to change it underneath
 * another.
 *
 * @param calories kilocalories
 * @param proteinG grams of protein
 * @param carbsG   grams of carbohydrate
 * @param fatG     grams of fat
 */
public record MacroTotals(
        double calories,
        double proteinG,
        double carbsG,
        double fatG) {

    /** All zero, the identity for summing. */
    public static final MacroTotals ZERO =
            new MacroTotals(0, 0, 0, 0);

    public MacroTotals {
        if (calories < 0 || proteinG < 0
                || carbsG < 0 || fatG < 0) {
            throw new IllegalArgumentException(
                    "Macro totals cannot be"
                    + " negative");
        }
    }

    public MacroTotals plus(MacroTotals other) {
        return new MacroTotals(
                calories + other.calories,
                proteinG + other.proteinG,
                carbsG + other.carbsG,
                fatG + other.fatG);
    }

    /**
     * This total less another, floored at zero.
     * Used for macros remaining today, where
     * eating past a target leaves nothing
     * remaining rather than a negative amount.
     */
    public MacroTotals minus(MacroTotals other) {
        return new MacroTotals(
                Math.max(0,
                    calories - other.calories),
                Math.max(0,
                    proteinG - other.proteinG),
                Math.max(0,
                    carbsG - other.carbsG),
                Math.max(0, fatG - other.fatG));
    }

    /** Scales every figure, for a portion size. */
    public MacroTotals scaled(double factor) {
        if (factor < 0) {
            throw new IllegalArgumentException(
                    "Scale factor cannot be"
                    + " negative");
        }
        return new MacroTotals(
                calories * factor,
                proteinG * factor,
                carbsG * factor,
                fatG * factor);
    }

    /** True when nothing is left. */
    public boolean isEmpty() {
        return calories == 0 && proteinG == 0
                && carbsG == 0 && fatG == 0;
    }
}
