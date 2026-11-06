package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent.agents
        .BlockGenerationAgent;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategyFactory;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter
        .presentation.gui.GuiContext;
import ca.yorku.eecs3311.spotter
        .presentation.gui.Panel;
import ca.yorku.eecs3311.spotter
        .presentation.support.DemoReplies;
import ca.yorku.eecs3311.spotter
        .presentation.support.ModelAccess;

import ca.yorku.eecs3311.spotter.application
        .event.SpotterEvent;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * F05 on screen: generating and keeping a block.
 *
 * The screen is arranged around the claim the
 * whole design rests on. The lifter's current
 * maxima are shown first, labelled as what the
 * coach is given rather than what it works out.
 * Then the plan. Then, for anyone who wants it,
 * every step the agent took, including the loads
 * that were rejected and sent back.
 *
 * Generating does not save. A plan becomes the
 * block being trained only when the lifter says
 * so, because a coach that silently replaced your
 * programme while you looked at it would not be
 * one you kept using.
 */
public final class PlanPanel implements Panel {

    /** Block length offered by default. */
    public static final int DEFAULT_WEEKS = 4;

    private final GuiContext context;
    private final VBox root = new VBox(
            Cards.GAP);
    private final Spinner<Integer> weeks =
            new Spinner<>(1, 16,
                    DEFAULT_WEEKS);
    private final Spinner<Integer> days =
            new Spinner<>(1, 7, 3);
    private final Button generate =
            new Button("Generate");
    private final Button keep =
            Cards.primary(new Button(
                "Train this block"));
    private boolean generating;
    private javafx.animation.Timeline clock;
    private final Label status =
            Cards.note("");
    private final VBox output = new VBox(8);

    private TrainingBlock proposed;
    private AgentResult<TrainingBlock> last;

    public PlanPanel(GuiContext context) {
        this.context = context;
        weeks.setPrefWidth(80);
        days.setPrefWidth(80);
        generate.setOnAction(event ->
                generate());
        keep.setOnAction(event -> keep());
        keep.setDisable(true);
    }

    /**
     * The plan it lists, and the profile it
     * generates from. A profile saved on another
     * screen changes what this one would
     * produce.
     */
    @Override
    public java.util.Set<SpotterEvent>
            changedBy() {
        return java.util.Set.of(SpotterEvent.BLOCK_CHANGED,
            SpotterEvent.PROFILE_CHANGED);
    }

    @Override
    public String title() {
        return "Plan";
    }

    @Override
    public String description() {
        return "A training block written for"
            + " your current numbers and checked"
            + " against them.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().clear();
        output.getChildren().clear();
        keep.setDisable(true);
        proposed = null;
        if (!generating) {
            status.setText("");
        }

        Map<Lift, Double> current =
                context.controller()
                    .currentE1RMs();

        if (current.isEmpty()) {
            root.getChildren().add(
                Cards.emptyState(
                    "There is no training"
                    + " history, so there is"
                    + " nothing to plan from."
                    + " A block built on a"
                    + " guessed maximum is"
                    + " worse than no block.",
                    "spotter import <file>"));
            return;
        }

        root.getChildren().add(
                givenAsFact(current));
        root.getChildren().add(controls());
        context.blocks().active().ifPresent(
            block -> root.getChildren().add(
                    activeCard(block)));
        root.getChildren().add(output);
    }

    private Node givenAsFact(
            Map<Lift, Double> current) {
        VBox card = Cards.card(
            "GIVEN TO THE COACH AS FACT");
        for (Lift lift : Lift.values()) {
            Double e1rm = current.get(lift);
            if (e1rm != null) {
                card.getChildren().add(
                    Cards.styled(String.format(
                        "%-9s %6.1f kg",
                        lift, e1rm), "mono"));
            }
        }
        card.getChildren().add(
            Cards.note("The coach is told these"
                + " rather than asked to work"
                + " them out, and every load it"
                + " proposes is checked back"
                + " against them."));
        return card;
    }

    private Node controls() {
        HBox row = new HBox(14,
            Cards.styled("WEEKS",
                    "card-heading"), weeks,
            Cards.styled("DAYS A WEEK",
                    "card-heading"), days,
            generate, keep);
        VBox card = Cards.card(null, row,
                status);
        if (!context.hasModel()) {
            card.getChildren().add(
                Cards.note("No API key is"
                    + " configured, so a"
                    + " prepared reply will be"
                    + " used. The agent, the"
                    + " parser and the"
                    + " validator still run"
                    + " exactly as they would"
                    + " on a real one."));
        }
        return card;
    }

    private Node activeCard(
            TrainingBlock block) {
        VBox card = Cards.card(
                "CURRENTLY TRAINING");
        card.getChildren().addAll(
            Cards.field("started",
                block.startDate().toString()),
            Cards.field("weeks",
                String.valueOf(
                        block.weekCount())),
            Cards.field("sessions",
                String.valueOf(
                    block.allSessions()
                        .size())));
        block.splitName().ifPresent(name ->
            card.getChildren().add(
                Cards.field("split", name)));
        return card;
    }

    /**
     * Shows that a block is being written, and for
     * how long.
     *
     * A live four week block takes a minute or
     * more, and a screen that only greys a button
     * looks the same as one that has hung. The
     * count says it is working, and it is kept
     * through a refresh so another screen saving
     * something cannot wipe it.
     */
    private void startClock() {
        generating = true;
        long began = System.currentTimeMillis();
        Runnable tick = () -> status.setText(
            "The coach is writing a block... "
            + (System.currentTimeMillis() - began)
                / 1000 + " s. A live four week"
            + " block can take a minute or more.");
        tick.run();
        clock = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(1),
                event -> tick.run()));
        clock.setCycleCount(
            javafx.animation.Animation.INDEFINITE);
        clock.play();
    }

    private void stopClock() {
        generating = false;
        if (clock != null) {
            clock.stop();
        }
    }

    /** Runs the agent off the interface thread. */
    private void generate() {
        output.getChildren().clear();
        generate.setDisable(true);
        keep.setDisable(true);
        startClock();

        LifterProfile profile = profile();
        ProgressionStrategy progression =
            new ProgressionStrategyFactory()
                .forGoal(profile.goal(),
                    profile.experience());
        Map<Lift, Double> current =
                context.controller()
                    .currentE1RMs();
        LocalDate start = LocalDate.now();
        int howMany = weeks.getValue();

        ModelAccess.Access access =
            ModelAccess.open(context.config(),
                false,
                DemoReplies.block(current,
                    progression, start, howMany,
                    days.getValue()));

        BlockGenerationAgent agent =
            new BlockGenerationAgent(
                access.client(),
                context.prompts(), profile,
                context.controller().catalog(),
                context.controller().workouts()
                    .findAll(),
                progression, start, howMany);

        context.tasks().run(agent::run,
            outcome -> {
                stopClock();
                generate.setDisable(false);
                // The card already explains
                // prepared mode, so repeating
                // it here says the same thing
                // twice on one screen.
                status.setText(access.isLive()
                        ? "The coach answered."
                        : "");
                show(outcome, agent);
            },
            failure -> {
                stopClock();
                generate.setDisable(false);
                status.setText("Something went"
                    + " wrong: "
                    + failure.getMessage());
            });
    }

    /**
     * The stored profile, or a reasonable stand
     * in.
     *
     * A lifter who has not onboarded still gets a
     * plan, built for someone training the chosen
     * number of days in a full gym, and the
     * screen says as much rather than implying
     * the plan was tailored.
     */
    private LifterProfile profile() {
        return context.profile().orElseGet(() ->
            LifterProfile
                .with(TrainingGoal.STRENGTH,
                    ExperienceLevel.INTERMEDIATE)
                .trainingDaysPerWeek(
                        days.getValue())
                .equipment(fullGym())
                .build());
    }

    private static Set<Equipment> fullGym() {
        return EnumSet.allOf(Equipment.class);
    }

    private void show(
            AgentResult<TrainingBlock> outcome,
            BlockGenerationAgent agent) {
        last = outcome;

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
            card.getChildren().add(
                Cards.note("Nothing was shown"
                    + " to you as a plan,"
                    + " because it did not pass"
                    + " the checks. That is the"
                    + " design working rather"
                    + " than failing."));
            output.getChildren().add(card);
            output.getChildren().add(
                    traceCard());
            return;
        }

        proposed = outcome.value().orElseThrow();
        keep.setDisable(false);

        if (context.profile().isEmpty()) {
            output.getChildren().add(
                Cards.card(null, Cards.note(
                    "No profile is saved, so"
                    + " this was built for a"
                    + " generic intermediate"
                    + " lifter in a full gym."
                    + " Describe yourself on"
                    + " the Profile screen for"
                    + " a plan that fits"
                    + " you.")));
        }
        output.getChildren().add(
                blockCard(proposed));
        output.getChildren().add(traceCard());
    }

    private Node blockCard(
            TrainingBlock block) {
        VBox card = Cards.card(
            "PROPOSED, AND CHECKED");
        block.splitName().ifPresent(name ->
            card.getChildren().add(
                Cards.note(name)));

        for (Week week : block.weeks()) {
            card.getChildren().add(
                Cards.styled("Week "
                    + week.number(),
                    "card-heading"));
            for (Session session
                    : week.sessions()) {
                card.getChildren().add(
                    Cards.styled(String.format(
                        "  %s  %s",
                        session.date(),
                        session.focus()
                            .orElse("training")),
                        "mono"));
                for (ExercisePrescription p
                        : session
                            .prescriptions()) {
                    card.getChildren().add(
                        Cards.styled(
                            String.format(
                                "      %-22s"
                                + " %d x %d @"
                                + " %.1f kg",
                                p.exercise()
                                    .name(),
                                p.sets(),
                                p.reps(),
                                p.loadKg()),
                            "mono"));
                }
            }
        }
        card.getChildren().add(
            Cards.note("Every load above sits"
                + " inside the range your"
                + " progression allows. Press"
                + " Train this block to keep"
                + " it."));
        return card;
    }

    /** What the agent actually did, step by step. */
    private Node traceCard() {
        VBox card = Cards.card(
                "WHAT THE COACH DID");
        if (last == null) {
            return card;
        }
        for (String step : last.trace()
                .steps()) {
            card.getChildren().add(
                Cards.styled(step, "mono"));
        }
        if (last.trace().validationFailures()
                > 0) {
            card.getChildren().add(
                Cards.note(last.trace()
                    .validationFailures()
                    + " proposal(s) were"
                    + " rejected and sent back"
                    + " with the reason"
                    + " attached."));
        }
        return card;
    }

    /** Keeps the plan, which is a deliberate act. */
    private void keep() {
        if (proposed == null) {
            return;
        }
        keep.setDisable(true);
        status.setText("Saving the block...");

        TrainingBlock saving = proposed;
        context.tasks().run(
            () -> {
                TrainingBlock saved =
                    context.blocks()
                        .save(saving);
                context.blocks().activate(
                        saved.id());
                return saved;
            },
            saved -> {
                context.events().publish(
                    SpotterEvent
                        .BLOCK_CHANGED);
                status.setText("Saved. This is"
                    + " now the block you are"
                    + " training.");
            },
            failure -> {
                keep.setDisable(false);
                status.setText("Could not"
                    + " save: "
                    + failure.getMessage());
            });
    }
}
