package ca.yorku.eecs3311.spotter.agent.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ca.yorku.eecs3311.spotter.agent.Tool;
import ca.yorku.eecs3311.spotter.agent
        .ToolSchema;
import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain.FoodItem;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.FoodCatalog;

/**
 * Lets an agent look up foods and what is in
 * them.
 *
 * Only foods the lifter will eat are returned:
 * the dietary filter runs here, before the model
 * sees anything, which is more reliable than
 * listing everything and asking it to avoid some.
 *
 * The figures are reported per hundred grams
 * because that is how the catalog stores them and
 * because it leaves the arithmetic to Spotter
 * rather than to the model.
 */
public final class FoodDBTool implements Tool {

    /** Most foods returned in one reply. */
    public static final int MAX_RESULTS = 30;

    private final FoodCatalog catalog;
    private final DietaryConstraints constraints;

    public FoodDBTool(
            FoodCatalog catalog,
            DietaryConstraints constraints) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "A catalog is required");
        }
        this.catalog = catalog;
        this.constraints = constraints;
    }

    @Override
    public String name() {
        return "available_foods";
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(),
            "Foods this lifter eats, with"
            + " calories and macronutrients per"
            + " hundred grams. Only suggest"
            + " foods this returns, and do not"
            + " work out a meal's totals"
            + " yourself.",
            Map.of("search",
                "optional: part of a food name,"
                + " such as chicken or rice"));
    }

    @Override
    public String execute(
            Map<String, String> arguments) {
        String asked = arguments == null
                ? null
                : arguments.get("search");
        String wanted = asked == null
                ? ""
                : asked.trim()
                    .toLowerCase(Locale.ROOT);

        List<String> lines = new ArrayList<>();
        for (FoodItem food
                : catalog.permittedBy(
                        constraints)) {
            if (!wanted.isEmpty()
                    && !food.name()
                        .toLowerCase(Locale.ROOT)
                        .contains(wanted)) {
                continue;
            }
            lines.add(describe(food));
            if (lines.size() >= MAX_RESULTS) {
                break;
            }
        }

        if (lines.isEmpty()) {
            return wanted.isEmpty()
                ? "No foods are available for"
                  + " this lifter."
                : "Nothing matching \"" + asked
                  + "\". Ask again without a"
                  + " search to see everything.";
        }
        return String.join("\n", lines);
    }

    private static String describe(
            FoodItem food) {
        MacroTotals per100g = food.per100g();
        return String.format(
            "%s per 100 g: %.0f kcal, %.1f g"
            + " protein, %.1f g carbs, %.1f g"
            + " fat",
            food.name(), per100g.calories(),
            per100g.proteinG(),
            per100g.carbsG(),
            per100g.fatG());
    }
}
