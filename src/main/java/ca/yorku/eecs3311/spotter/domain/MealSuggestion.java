package ca.yorku.eecs3311.spotter.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A meal the coach has suggested.
 *
 * The nutrition is not stored. It is computed
 * from the catalog's figures for each food and
 * the portion in grams, every time it is asked
 * for, so there is no way for a stated total to
 * drift from the foods it is supposed to
 * describe. A model that says "roughly 40 grams
 * of protein" is simply not consulted on the
 * matter.
 */
public final class MealSuggestion {

    /** One food and how much of it. */
    public record Portion(
            FoodItem food,
            double grams) {

        public Portion {
            if (food == null) {
                throw new IllegalArgumentException(
                    "A portion needs a food");
            }
            if (grams <= 0) {
                throw new IllegalArgumentException(
                    "A portion must be above"
                    + " zero grams");
            }
        }

        public MacroTotals totals() {
            return food.forGrams(grams);
        }
    }

    private final String name;
    private final List<Portion> portions;
    private final String rationale;

    public MealSuggestion(
            String name,
            List<Portion> portions,
            String rationale) {
        if (portions == null
                || portions.isEmpty()) {
            throw new IllegalArgumentException(
                "A meal needs at least one"
                + " food");
        }
        this.name = name == null
                || name.isBlank()
                    ? "Meal"
                    : name.trim();
        this.portions = Collections
                .unmodifiableList(
                    new ArrayList<>(portions));
        this.rationale = rationale;
    }

    public String name() {
        return name;
    }

    public List<Portion> portions() {
        return portions;
    }

    public Optional<String> rationale() {
        return Optional.ofNullable(rationale);
    }

    /** Computed from the catalog, every time. */
    public MacroTotals totals() {
        MacroTotals total = MacroTotals.ZERO;
        for (Portion portion : portions) {
            total = total.plus(portion.totals());
        }
        return total;
    }

    public int foodCount() {
        return portions.size();
    }

    /** True when every food suits the lifter. */
    public boolean permittedBy(
            DietaryConstraints constraints) {
        if (constraints == null) {
            return true;
        }
        for (Portion portion : portions) {
            if (!constraints.permits(
                    portion.food())) {
                return false;
            }
        }
        return true;
    }

    /** The first food the lifter will not eat. */
    public Optional<FoodItem> firstNotPermitted(
            DietaryConstraints constraints) {
        if (constraints == null) {
            return Optional.empty();
        }
        for (Portion portion : portions) {
            if (!constraints.permits(
                    portion.food())) {
                return Optional.of(
                        portion.food());
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        MacroTotals totals = totals();
        return String.format(
            "%s: %.0f kcal, %.0f g protein",
            name, totals.calories(),
            totals.proteinG());
    }
}
