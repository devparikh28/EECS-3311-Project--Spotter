package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.agent.agents
        .MealSuggestionAgent;
import ca.yorku.eecs3311.spotter.agent.tools
        .FoodDBTool;
import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain.FoodItem;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter.domain
        .MealSuggestion;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.FoodCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteFoodCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;

/**
 * Tests for F10, the meal suggestion agent.
 *
 * The lifter has 900 kcal and 70 g of protein
 * left for the day. The arithmetic of what
 * remains is Spotter's, the nutrition figures are
 * the catalog's, and the dietary filter has
 * already run. What these tests establish is that
 * the model's own arithmetic never reaches the
 * lifter, and that a meal using a food they do
 * not eat, an unservable portion, or most of the
 * day's remaining calories is refused.
 */
class MealSuggestionAgentTest {

    private static final MacroTotals REMAINING =
            new MacroTotals(900, 70, 90, 25);

    private Database database;
    private FoodCatalog foods;
    private ScriptedLLMClient model;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        foods = new SqliteFoodCatalog(database);

        // 165 kcal and 31 g protein per 100 g
        foods.add(new FoodItem("Chicken Breast",
            new MacroTotals(165, 31, 0, 3.6),
            false, false, false));
        foods.add(new FoodItem("White Rice",
            new MacroTotals(130, 2.7, 28, 0.3),
            true, false, false));
        foods.add(new FoodItem("Greek Yogurt",
            new MacroTotals(59, 10, 3.6, 0.4),
            true, true, false));
        foods.add(new FoodItem("Broccoli",
            new MacroTotals(34, 2.8, 7, 0.4),
            true, false, false));

        model = new ScriptedLLMClient();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private MealSuggestionAgent agent() {
        return agent(DietaryConstraints.none(),
                REMAINING);
    }

    private MealSuggestionAgent agent(
            DietaryConstraints diet,
            MacroTotals remaining) {
        return new MealSuggestionAgent(model,
            new PromptBuilder(), remaining, diet,
            foods, "something quick",
            new ResponseParser(
                new SqliteExerciseCatalog(
                        database),
                foods),
            new PlanValidator());
    }

    /** 200 g chicken, 200 g rice, 100 g broccoli. */
    private static String soundMeal() {
        return "{\"name\":\"Chicken and rice\","
            + "\"foods\":["
            + "{\"name\":\"Chicken Breast\","
            + "\"grams\":200},"
            + "{\"name\":\"White Rice\","
            + "\"grams\":200},"
            + "{\"name\":\"Broccoli\","
            + "\"grams\":100}],"
            + "\"rationale\":\"Hits the protein"
            + " you have left.\"}";
    }

    @Test
    @DisplayName(
        "a meal that fits what remains is"
        + " accepted")
    void aFittingMealIsAccepted() {
        model.reply(soundMeal());

        AgentResult<MealSuggestion> result =
                agent().run();

        assertThat(result.succeeded()).isTrue();
        MealSuggestion meal =
                result.value().orElseThrow();
        assertThat(meal.name())
            .isEqualTo("Chicken and rice");
        assertThat(meal.foodCount())
            .isEqualTo(3);
        assertThat(meal.rationale())
            .isPresent();
    }

    @Test
    @DisplayName(
        "the totals are computed from the"
        + " catalog, not taken from the reply")
    void totalsComeFromTheCatalog() {
        model.reply("{\"name\":\"Chicken and"
            + " rice\",\"calories\":50,"
            + "\"proteinG\":999,\"foods\":["
            + "{\"name\":\"Chicken Breast\","
            + "\"grams\":200,"
            + "\"calories\":12},"
            + "{\"name\":\"White Rice\","
            + "\"grams\":200}]}");

        MealSuggestion meal = agent().run()
                .value().orElseThrow();

        // 2 x 165 plus 2 x 130
        assertThat(meal.totals().calories())
            .isEqualTo(590.0);
        // 2 x 31 plus 2 x 2.7
        assertThat(meal.totals().proteinG())
            .isEqualTo(67.4, within());
    }

    private static
            org.assertj.core.data.Offset<Double>
            within() {
        return org.assertj.core.data.Offset
                .offset(0.01);
    }

    @Test
    @DisplayName(
        "a food the lifter does not eat is"
        + " refused, and the retry is accepted")
    void aForbiddenFoodIsRefused() {
        DietaryConstraints vegan =
                new DietaryConstraints(true,
                        false, false);

        model.reply(soundMeal())
             .reply("{\"name\":\"Rice and"
                + " broccoli\",\"foods\":["
                + "{\"name\":\"White Rice\","
                + "\"grams\":400},"
                + "{\"name\":\"Broccoli\","
                + "\"grams\":300}]}");

        AgentResult<MealSuggestion> result =
                agent(vegan, REMAINING).run();

        assertThat(result.succeeded()).isTrue();
        assertThat(model.received().get(1)
                .userPrompt())
            .contains("does not eat")
            .contains("Chicken Breast");
    }

    @Test
    @DisplayName(
        "the food lookup hides what the lifter"
        + " will not eat")
    void theLookupHidesForbiddenFoods() {
        String offered = new FoodDBTool(foods,
            new DietaryConstraints(true, false,
                    false))
            .execute(Map.of());

        assertThat(offered)
            .contains("White Rice")
            .doesNotContain("Chicken Breast")
            .doesNotContain("Greek Yogurt");
    }

    @Test
    @DisplayName(
        "a meal using nearly all the day's"
        + " remaining calories is refused")
    void anOversizedMealIsRefused() {
        String huge = "{\"name\":\"Everything\","
            + "\"foods\":["
            + "{\"name\":\"Chicken Breast\","
            + "\"grams\":500},"
            + "{\"name\":\"White Rice\","
            + "\"grams\":500}]}";
        model.alwaysReply(huge);

        AgentResult<MealSuggestion> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("Make it smaller");
    }

    @Test
    @DisplayName(
        "a meal that would not feed anyone is"
        + " refused")
    void aTokenMealIsRefused() {
        String tiny = "{\"name\":\"A little"
            + " broccoli\",\"foods\":["
            + "{\"name\":\"Broccoli\","
            + "\"grams\":50}]}";
        model.alwaysReply(tiny);

        AgentResult<MealSuggestion> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("Make it bigger");
    }

    @Test
    @DisplayName(
        "a portion nobody would serve is"
        + " refused")
    void anUnservablePortionIsRefused() {
        String absurd = "{\"name\":\"Rice"
            + " mountain\",\"foods\":["
            + "{\"name\":\"White Rice\","
            + "\"grams\":900}]}";
        model.alwaysReply(absurd);

        AgentResult<MealSuggestion> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("more than anyone serves");
    }

    @Test
    @DisplayName(
        "a portion too small to weigh is"
        + " refused")
    void aTinyPortionIsRefused() {
        String crumbs = "{\"name\":\"Crumbs\","
            + "\"foods\":["
            + "{\"name\":\"Chicken Breast\","
            + "\"grams\":200},"
            + "{\"name\":\"White Rice\","
            + "\"grams\":200},"
            + "{\"name\":\"Broccoli\","
            + "\"grams\":2}]}";
        model.alwaysReply(crumbs);

        AgentResult<MealSuggestion> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("too little to bother");
    }

    @Test
    @DisplayName(
        "an invented food is caught at parse"
        + " time")
    void anInventedFoodIsCaught() {
        String invented = "{\"name\":\"Mystery"
            + " bowl\",\"foods\":["
            + "{\"name\":\"Protein Sludge\","
            + "\"grams\":200}]}";
        model.alwaysReply(invented);

        MealSuggestionAgent agent = agent();

        assertThat(agent.run().succeeded())
            .isFalse();
        assertThat(agent.parseProblems())
            .anyMatch(problem -> problem
                .contains("Protein Sludge"));
    }

    @Test
    @DisplayName(
        "a food with no portion is caught at"
        + " parse time")
    void aMissingPortionIsCaught() {
        String portionless = "{\"name\":\"Rice\","
            + "\"foods\":["
            + "{\"name\":\"White Rice\"}]}";
        model.alwaysReply(portionless);

        MealSuggestionAgent agent = agent();

        assertThat(agent.run().succeeded())
            .isFalse();
        assertThat(agent.parseProblems())
            .isNotEmpty();
    }

    @Test
    @DisplayName(
        "the prompt states what remains and the"
        + " calorie band to stay inside")
    void thePromptStatesWhatRemains() {
        model.reply(soundMeal());

        agent().run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("900 kcal")
            .contains("70 g protein")
            .contains("135")
            .contains("990")
            .contains("something quick");
    }

    @Test
    @DisplayName(
        "the prompt names a dietary restriction"
        + " in words")
    void thePromptNamesTheDiet() {
        model.reply("{\"name\":\"Rice and"
            + " broccoli\",\"foods\":["
            + "{\"name\":\"White Rice\","
            + "\"grams\":400},"
            + "{\"name\":\"Broccoli\","
            + "\"grams\":300}]}");

        agent(new DietaryConstraints(true, false,
                false), REMAINING).run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("vegetarian")
            .contains("no dairy")
            .contains("no eggs");
    }

    @Test
    @DisplayName(
        "only the food lookup is offered")
    void onlyTheFoodToolIsOffered() {
        model.reply(soundMeal());

        agent().run();

        assertThat(model.lastRequest().tools())
            .extracting(ToolSchema::name)
            .containsExactly("available_foods");
    }

    @Test
    @DisplayName(
        "the cheaper model is used, since the"
        + " hard limits are enforced below it")
    void usesTheFastModel() {
        model.reply(soundMeal());

        agent().run();

        assertThat(model.lastRequest().tier())
            .isEqualTo(ModelTier.FAST);
    }

    @Test
    @DisplayName(
        "asking for a meal when nothing remains"
        + " is refused outright")
    void nothingRemainingIsRefused() {
        try {
            agent(DietaryConstraints.none(),
                    MacroTotals.ZERO);
            assertThat(false).isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                .contains("Nothing remains");
        }
    }

    @Test
    @DisplayName(
        "the model being unavailable gives a"
        + " clear failure, not a crash")
    void modelUnavailableFailsCleanly() {
        model.failWith("no API key configured");

        AgentResult<MealSuggestion> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("unavailable");
    }
}
