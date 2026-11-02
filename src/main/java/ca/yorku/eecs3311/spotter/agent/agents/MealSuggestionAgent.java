package ca.yorku.eecs3311.spotter.agent.agents;

import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.agent
        .CoachAgent;
import ca.yorku.eecs3311.spotter.agent
        .LLMClient;
import ca.yorku.eecs3311.spotter.agent
        .PlanValidator;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent
        .ToolManager;
import ca.yorku.eecs3311.spotter.agent.tools
        .FoodDBTool;
import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter.domain
        .MealSuggestion;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.FoodCatalog;

/**
 * F10: suggests a meal that fits what is left of
 * the day.
 *
 * The lifter has eaten some of their target and
 * wants to know what to have next. The
 * arithmetic of what remains is Spotter's, the
 * nutrition figures are the catalog's, and the
 * dietary filter has already removed everything
 * they will not eat. What is left for the model
 * is choosing a combination that adds up, which
 * is a search problem over a filtered list and
 * exactly the kind of thing it is good at.
 *
 * Nothing nutritional is read from the reply. A
 * meal's totals are computed from the catalog
 * every time they are asked for, so a model's
 * own arithmetic, right or wrong, never reaches
 * the lifter. It can get the sum wrong in its
 * explanation and still be rejected for it,
 * because the gate compares the computed total
 * with what remains.
 *
 * The cheaper model is enough: the hard
 * constraints are already enforced below it.
 */
public final class MealSuggestionAgent
        extends CoachAgent<MealSuggestion> {

    private final MacroTotals remaining;
    private final DietaryConstraints diet;
    private final FoodCatalog foods;
    private final String request;
    private final ResponseParser parser;
    private final PlanValidator validator;

    public MealSuggestionAgent(
            LLMClient model,
            PromptBuilder prompts,
            MacroTotals remaining,
            DietaryConstraints diet,
            FoodCatalog foods,
            String request,
            ResponseParser parser,
            PlanValidator validator) {
        super(model, prompts);
        if (remaining == null) {
            throw new IllegalArgumentException(
                "What remains for the day is"
                + " required");
        }
        if (remaining.calories() <= 0) {
            throw new IllegalArgumentException(
                "Nothing remains for today, so"
                + " there is no meal to"
                + " suggest");
        }
        if (foods == null) {
            throw new IllegalArgumentException(
                "A food catalog is required");
        }
        if (parser == null
                || validator == null) {
            throw new IllegalArgumentException(
                "A parser and a validator are"
                + " required");
        }
        this.remaining = remaining;
        this.diet = diet == null
                ? DietaryConstraints.none()
                : diet;
        this.foods = foods;
        this.request = request;
        this.parser = parser;
        this.validator = validator;
    }

    @Override
    public String name() {
        return "meal suggestion";
    }

    @Override
    protected int maxTokens() {
        return 2048;
    }

    @Override
    protected String systemPrompt() {
        return """
            You are a nutrition coach suggesting \
            one meal to a lifter who has already \
            eaten part of their daily target.

            Rules you must follow.
            Call available_foods before choosing \
            anything. Only use foods it returns, \
            spelled exactly as it spells them.
            Give each food a portion in grams.
            Do not state calories or \
            macronutrients. They are worked out \
            from the catalog, and any figure you \
            write is ignored.
            The meal must fit what remains for \
            the day without using nearly all of \
            it or nearly none.
            Answer with one JSON object and \
            nothing else: no explanation around \
            it, no code fence.

            The shape is:
            {"name": "...", "foods": [{"name": \
            "...", "grams": 150}], \
            "rationale": "one sentence"}
            """;
    }

    @Override
    protected String gatherContext() {
        StringBuilder context =
                new StringBuilder();
        context.append(String.format(
            "Left for today: %.0f kcal, %.0f g"
            + " protein, %.0f g carbs, %.0f g"
            + " fat.",
            remaining.calories(),
            remaining.proteinG(),
            remaining.carbsG(),
            remaining.fatG()));

        context.append(String.format(
            "%nKeep the meal between %.0f and"
            + " %.0f kcal.",
            remaining.calories()
                * PlanValidator
                    .MIN_CALORIE_SHARE,
            remaining.calories()
                * PlanValidator
                    .MAX_CALORIE_SHARE));

        context.append("\n\nWhat this lifter"
                + " eats: ")
            .append(describeDiet());

        if (request != null
                && !request.isBlank()) {
            context.append("\nThey asked for: ")
                .append(request.strip());
        }
        return context.toString();
    }

    private String describeDiet() {
        if (!diet.vegetarian()
                && diet.allowsDairy()
                && diet.allowsEggs()) {
            return "anything";
        }
        StringBuilder described =
                new StringBuilder();
        described.append(diet.vegetarian()
                ? "vegetarian"
                : "no restriction on meat");
        if (!diet.allowsDairy()) {
            described.append(", no dairy");
        }
        if (!diet.allowsEggs()) {
            described.append(", no eggs");
        }
        return described.toString();
    }

    @Override
    protected String describeTask() {
        return "Suggest one meal for the rest of"
            + " today.";
    }

    @Override
    protected ToolManager createToolset() {
        return new ToolManager()
            .register(new FoodDBTool(foods,
                    diet));
    }

    @Override
    protected Optional<MealSuggestion> parse(
            String reply) {
        return parser.toMeal(reply);
    }

    @Override
    protected Optional<String> validate(
            MealSuggestion meal) {
        return validator.validateMeal(meal,
                remaining, diet);
    }

    /** Why a parse failed, for the trace. */
    public List<String> parseProblems() {
        return parser.problems();
    }
}
