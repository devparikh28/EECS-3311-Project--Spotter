package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent
        .PlanValidator;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent.agents
        .MealSuggestionAgent;
import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain.FoodItem;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter.domain
        .MealSuggestion;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import ca.yorku.eecs3311.spotter
        .presentation.support.DemoReplies;
import ca.yorku.eecs3311.spotter
        .presentation.support.ModelAccess;
import ca.yorku.eecs3311.spotter
        .presentation.support.UsageReport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F10: suggests a meal for what is left of the
 * day.
 *
 * The figures on screen are worth watching
 * closely. The coach is told what remains and
 * which foods the lifter eats, and it answers
 * with foods and portions only. Every calorie and
 * gram printed below its answer was computed from
 * the catalog afterwards, so nothing the model
 * believes about nutrition appears anywhere in
 * the output.
 */
@Command(
    name = "meal",
    description =
        "Suggest a meal that fits what remains"
        + " for the day.")
public final class MealCommand
        implements Callable<Integer> {

    /** Returned when the catalog is empty. */
    public static final int EXIT_NO_FOODS = 3;

    /** Returned when no meal was accepted. */
    public static final int EXIT_REFUSED = 4;

    @Spec
    CommandSpec spec;

    @Option(
        names = {"--calories"},
        paramLabel = "KCAL",
        description =
            "Calories left for today."
            + " Default 900.")
    double calories = 900;

    @Option(
        names = {"--protein"},
        paramLabel = "G",
        description =
            "Protein left, in grams."
            + " Default 70.")
    double protein = 70;

    @Option(
        names = {"--carbs"},
        paramLabel = "G",
        description =
            "Carbohydrate left, in grams."
            + " Default 90.")
    double carbs = 90;

    @Option(
        names = {"--fat"},
        paramLabel = "G",
        description =
            "Fat left, in grams. Default 25.")
    double fat = 25;

    @Option(
        names = {"--vegetarian"},
        description = "No meat or fish.")
    boolean vegetarian;

    @Option(
        names = {"--no-dairy"},
        description = "No dairy.")
    boolean noDairy;

    @Option(
        names = {"--no-eggs"},
        description = "No eggs.")
    boolean noEggs;

    @Option(
        names = {"--want"},
        paramLabel = "TEXT",
        description =
            "What the lifter feels like"
            + " eating.")
    String want;

    @Option(
        names = {"--demo"},
        description =
            "Use a prepared reply instead of"
            + " calling the model.")
    boolean demo;

    @Option(
        names = {"--trace"},
        description =
            "Show every step the agent took.")
    boolean trace;

    @Option(
        names = {"--json"},
        description =
            "Print the result as JSON instead of"
            + " prose, for a harness rather than"
            + " a person.")
    boolean json;

    @Option(
        names = {"--env"},
        paramLabel = "FILE",
        description = "Configuration file.")
    Path envFile = Path.of(".env");

    @Override
    public Integer call() {
        SpotterConfig config =
                SpotterConfig.load(envFile);
        PrintWriter out =
                spec.commandLine().getOut();

        try (CoachController controller =
                new CoachController(config)) {

            DietaryConstraints diet =
                new DietaryConstraints(
                    vegetarian, !noDairy,
                    !noEggs);

            List<FoodItem> permitted =
                controller.foods()
                    .permittedBy(diet);
            if (permitted.isEmpty()) {
                if (json) {
                    JsonOutput.refusal(out,
                        "meal",
                        "there are no foods"
                        + " this lifter eats in"
                        + " the catalog");
                    return EXIT_NO_FOODS;
                }
                out.println("There are no foods"
                    + " this lifter eats in the"
                    + " catalog.");
                out.println("  Run: spotter"
                    + " seed");
                out.flush();
                return EXIT_NO_FOODS;
            }

            MacroTotals remaining =
                new MacroTotals(calories,
                    protein, carbs, fat);

            allCount = controller.foods()
                    .all().size();
            if (!json) {
                printHeader(out, remaining,
                        diet, permitted);
            }

            ModelAccess.Access access =
                ModelAccess.open(config, demo,
                    DemoReplies.meal(permitted,
                            remaining));
            if (!json) {
                out.println("  "
                        + access.explanation());
                out.println();
            }

            MealSuggestionAgent agent =
                new MealSuggestionAgent(
                    access.client(),
                    new PromptBuilder(),
                    remaining, diet,
                    controller.foods(), want,
                    new ResponseParser(
                        controller.catalog(),
                        controller.foods()),
                    new PlanValidator());

            AgentResult<MealSuggestion> result =
                    agent.run();

            if (json) {
                JsonOutput.write(out, "meal",
                    result,
                    result.succeeded()
                        ? JsonOutput.meal(
                            result.value()
                                .orElseThrow())
                        : null,
                    trace);
                return result.succeeded()
                    ? 0 : EXIT_REFUSED;
            }

            if (trace) {
                out.println(
                        "  What the agent did");
                for (String step
                        : result.trace()
                            .steps()) {
                    out.println("    " + step);
                }
                out.println("    " + UsageReport
                        .describe(result.trace(),
                                config));
                out.println();
            }

            if (!result.succeeded()) {
                out.println("No meal was"
                    + " accepted.");
                out.println("  "
                    + result.failureReason()
                        .orElse("unknown"));
                for (String problem
                        : agent.parseProblems()) {
                    out.println("  " + problem);
                }
                out.flush();
                return EXIT_REFUSED;
            }

            printMeal(out,
                    result.value().orElseThrow(),
                    remaining);
            out.flush();
        }
        return 0;
    }

    private void printHeader(
            PrintWriter out,
            MacroTotals remaining,
            DietaryConstraints diet,
            List<FoodItem> permitted) {

        out.println("Suggesting a meal");
        out.println();
        out.printf(
            "  left today      %.0f kcal,"
            + " %.0f g protein, %.0f g carbs,"
            + " %.0f g fat%n",
            remaining.calories(),
            remaining.proteinG(),
            remaining.carbsG(),
            remaining.fatG());
        out.printf(
            "  foods allowed   %d of %d in the"
            + " catalog%n",
            permitted.size(),
            countAll());
        if (diet.vegetarian() || !diet
                .allowsDairy()
                || !diet.allowsEggs()) {
            out.printf(
                "  restrictions    %s%n",
                describe(diet));
        }
        out.println();
    }

    private int countAll() {
        return allCount;
    }

    private int allCount;

    private static String describe(
            DietaryConstraints diet) {
        StringBuilder described =
                new StringBuilder();
        if (diet.vegetarian()) {
            described.append("vegetarian");
        }
        if (!diet.allowsDairy()) {
            described.append(
                described.isEmpty()
                    ? "no dairy"
                    : ", no dairy");
        }
        if (!diet.allowsEggs()) {
            described.append(
                described.isEmpty()
                    ? "no eggs"
                    : ", no eggs");
        }
        return described.toString();
    }

    private void printMeal(
            PrintWriter out,
            MealSuggestion meal,
            MacroTotals remaining) {

        out.println("Accepted: " + meal.name());
        out.println();
        for (MealSuggestion.Portion portion
                : meal.portions()) {
            MacroTotals totals =
                    portion.totals();
            out.printf(
                "  %-18s %5.0f g   %5.0f kcal,"
                + " %5.1f g protein%n",
                portion.food().name(),
                portion.grams(),
                totals.calories(),
                totals.proteinG());
        }

        MacroTotals totals = meal.totals();
        out.println();
        out.printf(
            "  meal total      %.0f kcal,"
            + " %.0f g protein, %.0f g carbs,"
            + " %.0f g fat%n",
            totals.calories(),
            totals.proteinG(),
            totals.carbsG(),
            totals.fatG());
        out.printf(
            "  share of what remains   %.0f"
            + " percent%n",
            totals.calories()
                / remaining.calories() * 100);
        meal.rationale().ifPresent(why ->
            out.printf("  because         %s%n",
                    why));
        out.println();
        out.println("  Every figure above was"
            + " computed from the catalog after"
            + " the coach answered. It was asked"
            + " for foods and portions, not for"
            + " nutrition.");
    }
}
