package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.time.LocalDate;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent.agents
        .OnboardingAgent;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .Limitation;
import ca.yorku.eecs3311.spotter.domain
        .ProfileDraft;
import ca.yorku.eecs3311.spotter
        .presentation.gui.GuiContext;
import ca.yorku.eecs3311.spotter
        .presentation.gui.Panel;
import ca.yorku.eecs3311.spotter
        .presentation.support.ModelAccess;
import ca.yorku.eecs3311.spotter
        .presentation.support.PreparedProfile;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * F13 on screen: onboarding in one paragraph.
 *
 * The lifter types what they would tell a coach
 * in the first five minutes and Spotter reads a
 * profile out of it. The alternative is nine
 * fields, which is the version people abandon.
 *
 * What the screen shows afterwards is the point.
 * It lists what was understood, and separately
 * lists what the lifter never said, because a
 * field nobody mentioned is left blank rather
 * than guessed. Saving is refused while anything
 * required is still blank, so a profile with
 * invented values cannot quietly become the basis
 * of every plan.
 */
public final class ProfilePanel
        implements Panel {

    private final GuiContext context;
    private final VBox root = new VBox(
            Cards.GAP);
    private final TextArea description =
            new TextArea();
    private final Button read =
            new Button("Read this");
    private final Button save =
            new Button("Save profile");
    private final VBox result = new VBox(8);
    private final Label status = Cards.note("");

    private ProfileDraft draft;

    public ProfilePanel(GuiContext context) {
        this.context = context;
        description.setPromptText(
            "Tell the coach about yourself."
            + " For example: I am 82 kg, been"
            + " lifting three years, train four"
            + " days a week at a commercial gym,"
            + " want to get stronger. My left"
            + " shoulder objects to flat"
            + " benching. Vegetarian, but I eat"
            + " dairy.");
        description.setWrapText(true);
        description.setPrefRowCount(5);

        read.setOnAction(event -> readIt());
        save.setOnAction(event -> saveIt());
        save.setDisable(true);
    }

    @Override
    public String title() {
        return "Profile";
    }

    @Override
    public String description() {
        return "Who the coach is planning for.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().clear();
        result.getChildren().clear();
        status.setText("");
        save.setDisable(draft == null
                || !draft.isComplete());

        context.profile().ifPresent(profile ->
            root.getChildren().add(
                    existing(profile)));

        VBox entry = Cards.card(
            "DESCRIBE YOURSELF", description,
            new HBox(8, read, save), status);
        root.getChildren().add(entry);
        root.getChildren().add(result);
    }

    /** What is already stored, if anything. */
    private Node existing(
            LifterProfile profile) {
        VBox card = Cards.card(
                "CURRENT PROFILE");
        card.getChildren().addAll(
            Cards.field("goal",
                profile.goal().name()),
            Cards.field("experience",
                profile.experience().name()),
            Cards.field("bodyweight",
                String.format("%.1f kg",
                    profile.bodyweightKg())),
            Cards.field("trains",
                profile.trainingDaysPerWeek()
                    + " days a week"),
            Cards.field("equipment",
                profile.equipment().size()
                    + " items"),
            Cards.field("limitations",
                String.valueOf(
                    profile.limitations()
                        .size())));
        profile.meetDate().ifPresent(meet ->
            card.getChildren().add(
                Cards.field("meet", meet
                        .toString())));
        card.getChildren().add(
            Cards.note("Describing yourself"
                + " again replaces this."));
        return card;
    }

    /** Hands the paragraph to the agent. */
    private void readIt() {
        String said = description.getText();
        if (said == null || said.isBlank()) {
            status.setText("Write something"
                + " first, in your own words.");
            return;
        }

        read.setDisable(true);
        save.setDisable(true);
        result.getChildren().clear();
        status.setText("Reading what you"
                + " wrote...");

        ModelAccess.Access access =
            ModelAccess.open(context.config(),
                false,
                PreparedProfile.of(said));

        OnboardingAgent agent =
            new OnboardingAgent(access.client(),
                context.prompts(), said,
                LocalDate.now(),
                context.parser(),
                context.profileValidator());

        context.tasks().run(agent::run,
            outcome -> {
                read.setDisable(false);
                status.setText(
                        access.explanation());
                // kept here, since this screen
                // has no standing note of its
                // own
                show(outcome, agent);
            },
            failure -> {
                read.setDisable(false);
                status.setText("Something went"
                    + " wrong: "
                    + failure.getMessage());
            });
    }

    private void show(
            AgentResult<ProfileDraft> outcome,
            OnboardingAgent agent) {

        if (!outcome.succeeded()) {
            VBox card = Cards.card(
                    "NOT YET");
            card.getChildren().add(
                Cards.note(outcome
                    .failureReason()
                    .orElse("The coach could"
                        + " not read that.")));
            for (String problem
                    : agent.parseProblems()) {
                card.getChildren().add(
                        Cards.note(problem));
            }
            card.getChildren().add(
                Cards.note("Add the missing"
                    + " detail and try again."
                    + " Nothing is guessed on"
                    + " your behalf."));
            result.getChildren().add(card);
            draft = null;
            save.setDisable(true);
            return;
        }

        draft = outcome.value().orElseThrow();
        result.getChildren().add(
                understood(draft));

        if (draft.isComplete()) {
            save.setDisable(false);
        } else {
            VBox asking = Cards.card(
                    "STILL NEEDED");
            draft.missing().forEach(question ->
                asking.getChildren().add(
                    Cards.note("You have not"
                        + " said " + question
                        + ".")));
            asking.getChildren().add(
                Cards.note("These are left"
                    + " blank rather than"
                    + " filled in with what is"
                    + " typical, because a"
                    + " number you never gave"
                    + " would shape every plan"
                    + " you are offered."));
            result.getChildren().add(asking);
        }
    }

    private Node understood(
            ProfileDraft read) {
        VBox card = Cards.card("UNDERSTOOD");
        read.bodyweightKg().ifPresent(kg ->
            card.getChildren().add(
                Cards.field("bodyweight",
                    String.format("%.1f kg",
                            kg))));
        read.goal().ifPresent(goal ->
            card.getChildren().add(
                Cards.field("goal",
                        goal.name())));
        read.experience().ifPresent(level ->
            card.getChildren().add(
                Cards.field("experience",
                        level.name())));
        read.trainingDaysPerWeek().ifPresent(
            days -> card.getChildren().add(
                Cards.field("trains",
                    days + " days a week")));
        read.splitPreference().ifPresent(split ->
            card.getChildren().add(
                Cards.field("split",
                        split.name())));
        read.meetDate().ifPresent(meet ->
            card.getChildren().add(
                Cards.field("meet",
                        meet.toString())));
        if (!read.equipment().isEmpty()) {
            card.getChildren().add(
                Cards.field("equipment",
                    String.valueOf(
                        read.equipment()
                            .size())));
        }
        read.diet().ifPresent(diet ->
            card.getChildren().add(
                Cards.field("diet",
                    diet.vegetarian()
                        ? "vegetarian"
                        : "no restriction")));
        for (Limitation limitation
                : read.limitations()) {
            card.getChildren().add(
                Cards.field("limitation",
                    limitation.description()));
        }
        return card;
    }

    /** Stores the finished profile. */
    private void saveIt() {
        if (draft == null
                || !draft.isComplete()) {
            return;
        }
        LifterProfile profile =
                draft.toProfile();
        save.setDisable(true);
        status.setText("Saving...");

        context.tasks().run(
            () -> context.profiles()
                    .save(profile),
            saved -> {
                status.setText("Saved. Plans"
                    + " will be built for you"
                    + " from now on.");
                draft = null;
                refresh();
            },
            failure -> {
                save.setDisable(false);
                status.setText("Could not"
                    + " save: "
                    + failure.getMessage());
            });
    }
}
