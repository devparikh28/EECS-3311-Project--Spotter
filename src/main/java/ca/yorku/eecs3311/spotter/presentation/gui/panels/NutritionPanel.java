package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent.agents
        .MealSuggestionAgent;
import ca.yorku.eecs3311.spotter.domain
        .DailyIntake;
import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain
        .FoodEntry;
import ca.yorku.eecs3311.spotter.domain.FoodItem;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .MacroTarget;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter.domain
        .MealSuggestion;
import ca.yorku.eecs3311.spotter
        .presentation.gui.GuiContext;
import ca.yorku.eecs3311.spotter
        .presentation.gui.Panel;
import ca.yorku.eecs3311.spotter
        .presentation.support.DemoReplies;
import ca.yorku.eecs3311.spotter
        .presentation.support.ModelAccess;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * F09 and F10 on screen: what has been eaten and
 * what to eat next.
 *
 * The two belong together because the second
 * depends on the first. A meal suggestion is only
 * worth anything if Spotter knows what is left for
 * the day, and what is left is the target less
 * what has been logged. Both halves on one screen
 * make that visible rather than implied.
 *
 * The nutrition under a suggested meal is
 * computed from the catalog after the coach
 * answers. It is asked for foods and portions and
 * nothing else, and the screen says so, because a
 * figure a model produced and a figure Spotter
 * calculated look identical once they are on
 * screen.
 */
public final class NutritionPanel
        implements Panel {

    /** Portion offered by default, in grams. */
    public static final int DEFAULT_GRAMS = 150;

    private final GuiContext context;
    private final VBox root = new VBox(
            Cards.GAP);
    private final VBox output = new VBox(8);
    private final Label status =
            Cards.note("");
    private final ChoiceBox<FoodItem> foodChoice =
            new ChoiceBox<>();
    private final Spinner<Integer> grams =
            new Spinner<>(5, 2000,
                    DEFAULT_GRAMS, 5);

    private MealSuggestion proposed;

    public NutritionPanel(GuiContext context) {
        this.context = context;
        grams.setPrefWidth(100);
        grams.setEditable(true);
        foodChoice.setPrefWidth(220);
        foodChoice.setConverter(
            new javafx.util.StringConverter<>() {
                @Override
                public String toString(
                        FoodItem food) {
                    return food == null
                            ? ""
                            : food.name();
                }

                @Override
                public FoodItem fromString(
                        String text) {
                    return null;
                }
            });
    }

    @Override
    public String title() {
        return "Nutrition";
    }

    @Override
    public String description() {
        return "What you have eaten today, and"
            + " what fits in the rest of it.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().clear();
        output.getChildren().clear();
        status.setText("");
        proposed = null;

        LifterProfile profile =
                context.profile().orElse(null);
        if (profile == null) {
            root.getChildren().add(
                Cards.emptyState(
                    "Nutrition is tracked"
                    + " against a target, and a"
                    + " target belongs to"
                    + " somebody. Describe"
                    + " yourself on the Profile"
                    + " screen first.", null));
            return;
        }

        Optional<MacroTarget> target =
            context.nutrition().targetFor(
                    profile.id());
        if (target.isEmpty()) {
            root.getChildren().add(
                    targetSetup(profile));
            return;
        }

        DailyIntake today = context.nutrition()
            .findByDate(LocalDate.now())
            .orElseGet(() -> new DailyIntake(
                LocalDate.now()));

        MacroTotals eaten = today.totals();
        MacroTotals remaining = target.get()
                .asTotals().minus(eaten);

        root.getChildren().add(
            progress(target.get(), eaten,
                    remaining));
        root.getChildren().add(
                logging(profile, today));
        root.getChildren().add(
            suggestion(profile, remaining));
        root.getChildren().add(output);
    }

    /**
     * Asked once, when no target exists.
     *
     * Offered rather than assumed: a calorie
     * target invented on the lifter's behalf
     * would silently decide what every meal
     * suggestion looks like.
     */
    private Node targetSetup(
            LifterProfile profile) {
        Spinner<Integer> calories =
                new Spinner<>(1200, 6000, 2800,
                        50);
        Spinner<Integer> protein =
                new Spinner<>(40, 400, 180, 5);
        Spinner<Integer> carbs =
                new Spinner<>(0, 800, 320, 10);
        Spinner<Integer> fat =
                new Spinner<>(20, 250, 80, 5);
        for (Spinner<Integer> spinner
                : List.of(calories, protein,
                        carbs, fat)) {
            spinner.setPrefWidth(110);
            spinner.setEditable(true);
        }

        Button save = new Button("Save target");
        save.setOnAction(event ->
            context.tasks().run(
                () -> context.nutrition()
                    .saveTarget(profile.id(),
                        new MacroTarget(
                            calories.getValue(),
                            protein.getValue(),
                            carbs.getValue(),
                            fat.getValue())),
                saved -> refresh(),
                failure -> status.setText(
                    "Could not save: "
                    + failure.getMessage())));

        VBox card = Cards.card(
            "SET A DAILY TARGET",
            new HBox(14,
                Cards.styled("CALORIES",
                        "card-heading"),
                calories,
                Cards.styled("PROTEIN",
                        "card-heading"),
                protein),
            new HBox(14,
                Cards.styled("CARBS",
                        "card-heading"),
                carbs,
                Cards.styled("FAT",
                        "card-heading"),
                fat),
            save, status);
        card.getChildren().add(
            Cards.note("Nothing is assumed here."
                + " A target invented on your"
                + " behalf would decide what"
                + " every meal suggestion looks"
                + " like."));
        return card;
    }

    /** Target, eaten and remaining, with bars. */
    private Node progress(
            MacroTarget target,
            MacroTotals eaten,
            MacroTotals remaining) {

        VBox card = Cards.card("TODAY");
        card.getChildren().addAll(
            bar("calories", eaten.calories(),
                    target.calories(), "kcal"),
            bar("protein", eaten.proteinG(),
                    target.proteinG(), "g"),
            bar("carbs", eaten.carbsG(),
                    target.carbsG(), "g"),
            bar("fat", eaten.fatG(),
                    target.fatG(), "g"));
        card.getChildren().add(
            Cards.field("left today",
                String.format(
                    "%.0f kcal, %.0f g protein,"
                    + " %.0f g carbs, %.0f g"
                    + " fat",
                    remaining.calories(),
                    remaining.proteinG(),
                    remaining.carbsG(),
                    remaining.fatG())));
        return card;
    }

    private Node bar(String name,
                     double eaten,
                     double target,
                     String unit) {
        Label label = Cards.styled(name,
                "card-heading");
        label.setMinWidth(80);

        ProgressBar progress =
                new ProgressBar(target <= 0
                    ? 0
                    : Math.min(1,
                        eaten / target));
        progress.setPrefWidth(260);

        Label figures = Cards.styled(
            String.format("%.0f of %.0f %s",
                eaten, target, unit), "mono");
        return new HBox(10, label, progress,
                figures);
    }

    /** F09: adding a food to today. */
    private Node logging(
            LifterProfile profile,
            DailyIntake today) {

        List<FoodItem> allowed = context
            .controller().foods()
            .permittedBy(profile.diet());
        foodChoice.setItems(
            FXCollections.observableArrayList(
                    allowed));
        if (foodChoice.getValue() == null
                && !allowed.isEmpty()) {
            foodChoice.setValue(allowed.get(0));
        }

        Button log = new Button("Log it");
        log.setDisable(allowed.isEmpty());
        log.setOnAction(event -> log());

        VBox card = Cards.card("LOG A FOOD",
            new HBox(10, foodChoice, grams,
                Cards.styled("grams",
                        "card-heading"),
                log),
            status);

        if (allowed.isEmpty()) {
            card.getChildren().add(
                Cards.note("The food catalogue"
                    + " is empty, or nothing in"
                    + " it suits your diet. Run"
                    + " spotter seed to load"
                    + " it."));
            return card;
        }

        if (today.isEmpty()) {
            card.getChildren().add(
                Cards.note("Nothing logged"
                    + " today yet."));
            return card;
        }

        for (FoodEntry entry
                : today.entries()) {
            MacroTotals totals =
                    entry.totals();
            card.getChildren().add(
                Cards.styled(String.format(
                    "%-20s %5.0f g   %5.0f kcal,"
                    + " %5.1f g protein",
                    entry.food().name(),
                    entry.grams(),
                    totals.calories(),
                    totals.proteinG()),
                    "mono"));
        }
        return card;
    }

    private void log() {
        FoodItem food = foodChoice.getValue();
        if (food == null) {
            return;
        }
        FoodEntry entry = new FoodEntry(food,
                grams.getValue());
        status.setText("Logging...");

        context.tasks().run(
            () -> context.nutrition().log(
                    LocalDate.now(), entry),
            saved -> refresh(),
            failure -> status.setText(
                "Could not log that: "
                + failure.getMessage()));
    }

    /** F10: asking the coach for a meal. */
    private Node suggestion(
            LifterProfile profile,
            MacroTotals remaining) {

        Button ask = new Button("Suggest a"
                + " meal");
        boolean anythingLeft =
                remaining.calories() > 0;
        ask.setDisable(!anythingLeft);
        ask.setOnAction(event ->
            suggest(profile, remaining));

        Region filler = new Region();
        HBox.setHgrow(filler, Priority.ALWAYS);

        VBox card = Cards.card(null,
            new HBox(8, ask, filler));
        if (!anythingLeft) {
            card.getChildren().add(
                Cards.note("You have reached"
                    + " today's target, so"
                    + " there is nothing left"
                    + " to fit a meal into."));
        } else if (!context.hasModel()) {
            card.getChildren().add(
                Cards.note("No API key is"
                    + " configured, so a"
                    + " prepared reply will be"
                    + " used. The filter, the"
                    + " parser and the"
                    + " validator still run"
                    + " exactly as they would"
                    + " on a real one."));
        }
        return card;
    }

    private void suggest(
            LifterProfile profile,
            MacroTotals remaining) {

        output.getChildren().clear();
        status.setText("The coach is putting"
                + " something together...");

        DietaryConstraints diet =
                profile.diet();
        List<FoodItem> allowed = context
            .controller().foods()
            .permittedBy(diet);

        ModelAccess.Access access =
            ModelAccess.open(context.config(),
                false,
                DemoReplies.meal(allowed,
                        remaining));

        MealSuggestionAgent agent =
            new MealSuggestionAgent(
                access.client(),
                context.prompts(), remaining,
                diet,
                context.controller().foods(),
                null, context.parser(),
                context.planValidator());

        context.tasks().run(agent::run,
            outcome -> {
                status.setText(access.isLive()
                    ? "The coach answered."
                    : "");
                show(outcome, agent, remaining);
            },
            failure -> status.setText(
                "Something went wrong: "
                + failure.getMessage()));
    }

    private void show(
            AgentResult<MealSuggestion> outcome,
            MealSuggestionAgent agent,
            MacroTotals remaining) {

        if (!outcome.succeeded()) {
            VBox card = Cards.card("REFUSED");
            card.getChildren().add(
                Cards.note(outcome
                    .failureReason()
                    .orElse("unknown")));
            for (String problem
                    : agent.parseProblems()) {
                card.getChildren().add(
                        Cards.note(problem));
            }
            output.getChildren().add(card);
            return;
        }

        proposed = outcome.value().orElseThrow();
        MacroTotals totals = proposed.totals();

        VBox card = Cards.card("SUGGESTED: "
                + proposed.name());
        for (MealSuggestion.Portion portion
                : proposed.portions()) {
            MacroTotals each =
                    portion.totals();
            card.getChildren().add(
                Cards.styled(String.format(
                    "%-20s %5.0f g   %5.0f kcal,"
                    + " %5.1f g protein",
                    portion.food().name(),
                    portion.grams(),
                    each.calories(),
                    each.proteinG()), "mono"));
        }
        card.getChildren().addAll(
            Cards.field("meal total",
                String.format(
                    "%.0f kcal, %.0f g protein,"
                    + " %.0f g carbs, %.0f g"
                    + " fat",
                    totals.calories(),
                    totals.proteinG(),
                    totals.carbsG(),
                    totals.fatG())),
            Cards.field("of what remains",
                String.format("%.0f percent",
                    totals.calories()
                        / remaining.calories()
                        * 100)));
        proposed.rationale().ifPresent(why ->
            card.getChildren().add(
                    Cards.note(why)));
        card.getChildren().add(
            Cards.note("Every figure above was"
                + " computed from the catalogue"
                + " after the coach answered."
                + " It was asked for foods and"
                + " portions, not for"
                + " nutrition."));

        Button accept = new Button("Log this"
                + " meal");
        accept.setOnAction(event -> logMeal());
        card.getChildren().add(accept);
        output.getChildren().add(card);
    }

    /** Writes a suggested meal into today. */
    private void logMeal() {
        if (proposed == null) {
            return;
        }
        MealSuggestion meal = proposed;
        status.setText("Logging the meal...");

        context.tasks().run(
            () -> {
                DailyIntake day = null;
                for (MealSuggestion.Portion
                        portion
                            : meal.portions()) {
                    day = context.nutrition()
                        .log(LocalDate.now(),
                            new FoodEntry(
                                portion.food(),
                                portion
                                    .grams()));
                }
                return day;
            },
            saved -> refresh(),
            failure -> status.setText(
                "Could not log the meal: "
                + failure.getMessage()));
    }
}
