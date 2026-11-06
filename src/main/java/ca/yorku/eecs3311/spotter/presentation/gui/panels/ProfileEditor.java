package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain
        .Equipment;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .Limitation;
import ca.yorku.eecs3311.spotter.domain
        .SplitPreference;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter.domain
        .WeightUnit;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A saved profile as a form.
 *
 * Describing yourself in a paragraph is the way in,
 * but it is the wrong way to change one number.
 * Someone who has put on two kilos or moved from
 * three training days to four should not have to
 * retype who they are, so every field of a stored
 * profile is here, filled in, and Save changes
 * writes the same profile back under its own id
 * instead of creating a second one.
 *
 * Injuries and limitations are shown but not
 * edited here: they are free text the onboarding
 * agent reads, and a form field would only lose
 * the nuance. They are kept as they are.
 */
final class ProfileEditor {

    private final LifterProfile original;
    private final TextField bodyweight =
            new TextField();
    private final ChoiceBox<TrainingGoal> goal =
            new ChoiceBox<>();
    private final ChoiceBox<ExperienceLevel>
        experience = new ChoiceBox<>();
    private final ChoiceBox<Integer> days =
            new ChoiceBox<>();
    private final ChoiceBox<SplitPreference>
        split = new ChoiceBox<>();
    private final ChoiceBox<WeightUnit> unit =
            new ChoiceBox<>();
    private final TextField meet =
            new TextField();
    private final CheckBox vegetarian =
            new CheckBox("Vegetarian");
    private final CheckBox dairy =
            new CheckBox("Eats dairy");
    private final CheckBox eggs =
            new CheckBox("Eats eggs");
    private final FlowPane gear =
            new FlowPane(10, 6);
    private final Set<Equipment> chosen =
            EnumSet.noneOf(Equipment.class);
    private final Label message =
            Cards.note("");

    ProfileEditor(LifterProfile profile) {
        this.original = profile;

        goal.getItems().addAll(
                TrainingGoal.values());
        experience.getItems().addAll(
                ExperienceLevel.values());
        for (int d = 1; d <= 7; d++) {
            days.getItems().add(d);
        }
        split.getItems().addAll(
                SplitPreference.values());
        unit.getItems().addAll(
                WeightUnit.values());

        bodyweight.setText(String.format(
            Locale.ROOT, "%.1f",
            profile.bodyweightKg()));
        goal.setValue(profile.goal());
        experience.setValue(
                profile.experience());
        days.setValue(
            profile.trainingDaysPerWeek());
        split.setValue(
                profile.splitPreference());
        unit.setValue(profile.displayUnit());
        meet.setPrefWidth(300);
        meet.setPromptText("YYYY-MM-DD, or"
            + " leave empty for no meet");
        profile.meetDate().ifPresent(date ->
            meet.setText(date.toString()));

        DietaryConstraints diet = profile.diet();
        vegetarian.setSelected(
                diet.vegetarian());
        dairy.setSelected(diet.allowsDairy());
        eggs.setSelected(diet.allowsEggs());

        chosen.addAll(profile.equipment());
        for (Equipment item
                : Equipment.values()) {
            CheckBox box = new CheckBox(
                readable(item.name()));
            box.setSelected(
                    chosen.contains(item));
            box.selectedProperty().addListener(
                (o, was, now) -> {
                    if (now) {
                        chosen.add(item);
                    } else {
                        chosen.remove(item);
                    }
                });
            gear.getChildren().add(box);
        }
    }

    /** The form, with a Save changes button. */
    Node build(Consumer<LifterProfile> onSave) {
        Button save = Cards.primary(
            new Button("Save changes"));
        save.setOnAction(event -> {
            try {
                onSave.accept(collect());
            } catch (IllegalArgumentException
                    | DateTimeParseException e) {
                message.setText(problem(e));
            }
        });

        VBox card = Cards.card("YOUR PROFILE",
            row("bodyweight (kg)", bodyweight),
            row("goal", goal),
            row("experience", experience),
            row("training days / week", days),
            row("split", split),
            row("show weights in", unit),
            row("meet date", meet),
            row("diet", new HBox(14,
                vegetarian, dairy, eggs)),
            row("equipment", gear));

        if (!original.limitations().isEmpty()) {
            VBox notes = new VBox(2);
            for (Limitation limitation
                    : original.limitations()) {
                notes.getChildren().add(
                    Cards.note(limitation
                        .description()));
            }
            card.getChildren().add(
                row("limitations", notes));
            card.getChildren().add(Cards.note(
                "Limitations are changed by"
                + " describing yourself again"
                + " below."));
        }
        card.getChildren().addAll(
                Cards.actions(save), message);
        return card;
    }

    /** What the form holds, as a profile. */
    private LifterProfile collect() {
        double weight;
        try {
            weight = Double.parseDouble(
                bodyweight.getText().trim()
                    .replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Bodyweight must be a number"
                + " of kilograms.");
        }
        if (weight < 30 || weight > 300) {
            throw new IllegalArgumentException(
                "Bodyweight of " + weight
                + " kg is outside what"
                + " Spotter treats as real"
                + " (30 to 300).");
        }

        String typed = meet.getText() == null
                ? "" : meet.getText().trim();
        LocalDate date = typed.isEmpty()
                ? null
                : LocalDate.parse(typed);

        LifterProfile.Builder builder =
            LifterProfile.with(goal.getValue(),
                    experience.getValue())
                .id(original.id())
                .bodyweightKg(weight)
                .trainingDaysPerWeek(
                    days.getValue())
                .splitPreference(
                    split.getValue())
                .displayUnit(unit.getValue())
                .meetDate(date)
                .equipment(chosen)
                .diet(new DietaryConstraints(
                    vegetarian.isSelected(),
                    dairy.isSelected(),
                    eggs.isSelected()));
        original.weightClassKg().ifPresent(
                builder::weightClassKg);
        original.limitations().forEach(
                builder::addLimitation);
        return builder.build();
    }

    private static String problem(Exception e) {
        if (e instanceof DateTimeParseException) {
            return "The meet date must look like"
                + " 2027-03-14, or be empty.";
        }
        return e.getMessage();
    }

    private static HBox row(String name,
                            Node control) {
        Label left = Cards.styled(name,
                "field-name");
        left.setMinWidth(170);
        HBox box = new HBox(8, left, control);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    private static String readable(String name) {
        String spaced = name.toLowerCase(
                Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(
            spaced.charAt(0))
            + spaced.substring(1);
    }
}
