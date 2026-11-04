package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent.agents
        .AdjustmentAgent;
import ca.yorku.eecs3311.spotter.agent.agents
        .SubstitutionAgent;
import ca.yorku.eecs3311.spotter.application
        .plan.ApplyAdjustmentCommand;
import ca.yorku.eecs3311.spotter.application
        .plan.PlanEditHistory;
import ca.yorku.eecs3311.spotter.application
        .plan.SwapExerciseCommand;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryRuleEngine;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .Substitution;
import ca.yorku.eecs3311.spotter.domain
        .SubstitutionReason;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Today's session, and the two things a lifter
 * actually wants to do to it.
 *
 * F07 and F06 on screen. If recovery flagged the
 * morning, Ease off asks the coach where the cut
 * should fall. If one movement is bothering them,
 * Swap asks for a replacement they can perform.
 *
 * Both follow the same rule as the Plan screen:
 * the proposal is shown and nothing is written
 * until the lifter accepts it. Ease off is
 * disabled when nothing was flagged, because the
 * decision to reduce belongs to the rule engine
 * rather than to whoever feels like pressing a
 * button.
 */
public final class TodayPanel
        implements Panel {

    /** How far back a flag still counts. */
    public static final int FLAG_WEEKS = 1;

    private final GuiContext context;
    private final RecoveryRuleEngine rules =
            new RecoveryRuleEngine();
    private final VBox root = new VBox(
            Cards.GAP);
    private final VBox output = new VBox(8);
    private final Label status =
            Cards.note("");

    private TrainingBlock block;
    private Session session;
    private Session proposedSession;
    private Substitution proposedSwap;
    private PlanEditHistory history;

    public TodayPanel(GuiContext context) {
        this.context = context;
    }

    /**
     * A swap or a reduction made here changes
     * the plan, and so does a plan kept on the
     * Plan screen while this one is open.
     */
    @Override
    public java.util.Set<SpotterEvent>
            changedBy() {
        return java.util.Set.of(SpotterEvent.BLOCK_CHANGED);
    }

    @Override
    public String title() {
        return "Today";
    }

    @Override
    public String description() {
        return "The session in front of you, and"
            + " what to do if it does not suit"
            + " the day.";
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
        proposedSession = null;
        proposedSwap = null;

        block = context.blocks().active()
                .orElse(null);
        if (block == null) {
            root.getChildren().add(
                Cards.emptyState(
                    "No block is being trained,"
                    + " so there is no session"
                    + " today. Generate one on"
                    + " the Plan screen and"
                    + " keep it.", null));
            return;
        }

        rememberEdits();
        session = sessionForToday();
        if (session == null) {
            root.getChildren().add(
                Cards.emptyState(
                    "Nothing is scheduled for"
                    + " today in the current"
                    + " block. Rest days are"
                    + " part of the plan.",
                    null));
            return;
        }

        Optional<RecoveryFlag> flag = flag();

        root.getChildren().add(
                sessionCard(session));
        root.getChildren().add(
                recoveryCard(flag));
        root.getChildren().add(
                actions(flag));
        root.getChildren().add(output);
    }

    /**
     * Keeps the undo stack across a refresh.
     *
     * Every refresh reads the block back from the
     * database, so the history is pointed at the
     * block that came back rather than thrown
     * away. A different block means a different
     * plan, and the edits made to the old one no
     * longer apply to anything.
     */
    private void rememberEdits() {
        if (history == null
                || history.current().id()
                    != block.id()) {
            history = new PlanEditHistory(block);
            return;
        }
        history.rebase(block);
    }

    private Session sessionForToday() {
        LocalDate today = LocalDate.now();
        return block.allSessions().stream()
            .filter(each -> each.date()
                    .equals(today))
            .findFirst()
            .orElse(null);
    }

    /** The most recent flag, if there is one. */
    private Optional<RecoveryFlag> flag() {
        List<RecoveryFlag> flags = context
            .controller().recoveryFlags(
                LocalDate.now().minusWeeks(
                        FLAG_WEEKS));
        return flags.isEmpty()
            ? Optional.empty()
            : Optional.of(flags.get(
                    flags.size() - 1));
    }

    private Node sessionCard(Session showing) {
        VBox card = Cards.card(
            "TODAY, " + showing.focus()
                .orElse("training"));

        for (ExercisePrescription p
                : showing.prescriptions()) {
            Label line = Cards.styled(
                String.format(
                    "%-24s %d x %d @ %.1f kg",
                    p.exercise().name(),
                    p.sets(), p.reps(),
                    p.loadKg()), "mono");
            Region filler = new Region();
            HBox.setHgrow(filler,
                    Priority.ALWAYS);

            Button swap = new Button("Swap");
            swap.setOnAction(event ->
                    swap(p));

            card.getChildren().add(
                new HBox(8, line, filler,
                        swap));
        }

        card.getChildren().add(
            Cards.field("planned total",
                String.format("%.0f kg",
                    showing.plannedTonnage())));
        showing.adjustmentNote().ifPresent(note ->
            card.getChildren().add(
                    Cards.note(note)));
        return card;
    }

    private Node recoveryCard(
            Optional<RecoveryFlag> flag) {
        VBox card = Cards.card("THIS MORNING");
        if (flag.isEmpty()) {
            card.getChildren().addAll(
                Cards.styled("Nothing flagged",
                        "good"),
                Cards.note("The session stands"
                    + " as written. Only a"
                    + " flagged day lets the"
                    + " coach change it."));
            return card;
        }
        RecoveryFlag flagged = flag.get();
        Label line = Cards.styled(
            flagged.date() + "   "
            + flagged.summary(),
            flagged.isSignificant()
                ? "flag-significant"
                : "flag-mild");
        line.setWrapText(true);
        card.getChildren().add(line);
        return card;
    }

    private Node actions(
            Optional<RecoveryFlag> flag) {
        Button ease = new Button("Ease off"
                + " today");
        ease.setDisable(flag.isEmpty());
        ease.setOnAction(event ->
                ease(flag.orElseThrow()));

        Button fallback = new Button("Use the"
                + " standard reduction");
        fallback.setDisable(flag.isEmpty());
        fallback.setOnAction(event ->
                fallback());

        Button undo = new Button(
            history.nextToUndo()
                .map(what -> "Undo: " + what)
                .orElse("Nothing to undo"));
        undo.setDisable(!history.canUndo());
        undo.setOnAction(event -> undo());

        VBox card = Cards.card(null,
            new HBox(8, ease, fallback, undo),
            status);
        if (history.canUndo()) {
            card.getChildren().add(
                Cards.note("Edits this session: "
                    + String.join("; ",
                        history.describe())));
        }
        if (flag.isEmpty()) {
            card.getChildren().add(
                Cards.note("Both are disabled"
                    + " because nothing was"
                    + " flagged. A lifter who"
                    + " simply wants an easier"
                    + " day can swap a movement"
                    + " instead."));
        }
        return card;
    }

    /** Asks the coach where the cut should fall. */
    private void ease(RecoveryFlag flag) {
        output.getChildren().clear();
        status.setText("The coach is deciding"
                + " where to take it off...");

        List<RecoveryDay> readings = context
            .controller().recovery()
            .findSince(LocalDate.now()
                .minusWeeks(FLAG_WEEKS * 2L));

        Session working = session;
        ModelAccess.Access access =
            ModelAccess.open(context.config(),
                false,
                DemoReplies.adjustment(working));

        AdjustmentAgent agent =
            new AdjustmentAgent(access.client(),
                context.prompts(), working, flag,
                readings, rules,
                context.parser());

        context.tasks().run(agent::run,
            outcome -> {
                status.setText(access.isLive()
                    ? "The coach answered."
                    : access.explanation());
                showAdjustment(outcome, agent,
                        working);
            },
            failure -> status.setText(
                "Something went wrong: "
                + failure.getMessage()));
    }

    private void showAdjustment(
            AgentResult<Session> outcome,
            AdjustmentAgent agent,
            Session before) {

        if (!outcome.succeeded()) {
            VBox card = Cards.card("REFUSED");
            card.getChildren().addAll(
                Cards.note(outcome
                    .failureReason()
                    .orElse("unknown")),
                Cards.note("The standard"
                    + " reduction is still"
                    + " available, so you can"
                    + " train either way."));
            output.getChildren().add(card);
            return;
        }

        proposedSession =
                outcome.value().orElseThrow();
        output.getChildren().add(
            comparison(before, proposedSession,
                "PROPOSED REDUCTION"));
    }

    /** The rule engine's version, with no model. */
    private void fallback() {
        output.getChildren().clear();
        Session reduced =
                rules.fallbackAdjustment(
                        session);
        proposedSession = reduced;
        status.setText("No model was involved in"
                + " this one.");
        output.getChildren().add(
            comparison(session, reduced,
                "STANDARD REDUCTION"));
    }

    /**
     * Before and after, side by side, with the
     * accept button attached.
     */
    private Node comparison(
            Session before,
            Session after,
            String heading) {

        VBox card = Cards.card(heading);
        for (ExercisePrescription was
                : before.prescriptions()) {
            ExercisePrescription now = after
                .findPrescription(was.id())
                .orElse(was);
            card.getChildren().add(
                Cards.styled(String.format(
                    "%-24s %d x %d @ %.1f kg"
                    + "   ->   %d x %d @ %.1f"
                    + " kg",
                    was.exercise().name(),
                    was.sets(), was.reps(),
                    was.loadKg(),
                    now.sets(), now.reps(),
                    now.loadKg()), "mono"));
        }

        double planned =
                before.plannedTonnage();
        double reduced = after.plannedTonnage();
        card.getChildren().add(
            Cards.field("total",
                String.format(
                    "%.0f kg down to %.0f kg,"
                    + " which is %.0f percent",
                    planned, reduced,
                    reduced / planned * 100)));
        after.adjustmentNote().ifPresent(note ->
            card.getChildren().add(
                    Cards.note(note)));

        Button accept = new Button("Train this"
                + " instead");
        accept.setOnAction(event ->
                acceptSession());
        card.getChildren().add(accept);
        return card;
    }

    /** Asks for a replacement movement. */
    private void swap(
            ExercisePrescription original) {
        output.getChildren().clear();
        status.setText("The coach is looking for"
                + " a replacement for "
                + original.exercise().name()
                + "...");

        LifterProfile profile = profile();
        Exercise stand = standIn(profile,
                original.exercise());

        ModelAccess.Access access =
            ModelAccess.open(context.config(),
                false,
                stand == null
                    ? "{}"
                    : DemoReplies.substitution(
                        stand,
                        original.loadKg()));

        SubstitutionAgent agent =
            new SubstitutionAgent(
                access.client(),
                context.prompts(), profile,
                context.controller().catalog(),
                original,
                SubstitutionReason
                    .PAIN_OR_DISCOMFORT,
                "bothering them today");

        context.tasks().run(agent::run,
            outcome -> {
                status.setText(access.isLive()
                    ? "The coach answered."
                    : access.explanation());
                showSwap(outcome, agent);
            },
            failure -> status.setText(
                "Something went wrong: "
                + failure.getMessage()));
    }

    private void showSwap(
            AgentResult<Substitution> outcome,
            SubstitutionAgent agent) {

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

        proposedSwap =
                outcome.value().orElseThrow();
        VBox card = Cards.card(
                "PROPOSED SWAP");
        card.getChildren().addAll(
            Cards.styled(String.format(
                "%s   ->   %s",
                proposedSwap.replaced().name(),
                proposedSwap.replacement()
                    .name()), "mono"),
            Cards.field("at", String.format(
                "%d x %d @ %.1f kg",
                proposedSwap.apply().sets(),
                proposedSwap.apply().reps(),
                proposedSwap.loadKg())));
        proposedSwap.rationale().ifPresent(why ->
            card.getChildren().add(
                    Cards.note(why)));
        card.getChildren().add(
            Cards.note("Checked before being"
                + " shown: it exists, you can"
                + " perform it, it trains what"
                + " the original trained, and"
                + " the load suits it."));

        Button accept = new Button("Swap it");
        accept.setOnAction(event ->
                acceptSwap());
        card.getChildren().add(accept);
        output.getChildren().add(card);
    }

    /**
     * A movement for the prepared reply to
     * suggest, chosen the way the filter would:
     * performable, similar, and not the original.
     */
    private Exercise standIn(
            LifterProfile profile,
            Exercise original) {
        for (Exercise candidate
                : context.controller().catalog()
                    .all()) {
            if (candidate.equals(original)
                    || !profile.canPerform(
                            candidate)) {
                continue;
            }
            boolean sharesPattern = candidate
                .movementPattern().isPresent()
                && original.movementPattern()
                    .isPresent()
                && candidate.movementPattern()
                    .get().equalsIgnoreCase(
                        original.movementPattern()
                            .get());
            if (sharesPattern) {
                return candidate;
            }
        }
        return null;
    }

    private LifterProfile profile() {
        return context.profile().orElseGet(() ->
            LifterProfile
                .with(TrainingGoal.STRENGTH,
                    ExperienceLevel.INTERMEDIATE)
                .equipment(EnumSet.allOf(
                        Equipment.class))
                .build());
    }

    /**
     * Accepting goes through a command.
     *
     * The panel no longer rebuilds the block
     * itself. It hands the history a change that
     * knows how to reverse itself, which is the
     * whole reason Undo can be a button rather
     * than a regeneration.
     */
    private void acceptSession() {
        if (proposedSession == null) {
            return;
        }
        save(history.apply(
                new ApplyAdjustmentCommand(
                    session, proposedSession)),
            "The session has been changed, and"
            + " can be put back.");
    }

    private void acceptSwap() {
        if (proposedSwap == null) {
            return;
        }
        Session changed = copyOf(session);
        changed.replacePrescription(
                proposedSwap.apply());
        save(history.apply(
                new SwapExerciseCommand(changed,
                    proposedSwap)),
            "Swapped. The block has been"
            + " updated, and can be put back.");
    }

    /** Puts the most recent edit back. */
    private void undo() {
        Optional<String> what =
                history.nextToUndo();
        Optional<TrainingBlock> before =
                history.undo();
        if (before.isEmpty()) {
            return;
        }
        save(before.get(),
            "Put back: " + what.orElse("the"
                + " last edit") + ".");
    }

    private void save(TrainingBlock changed,
                      String message) {
        status.setText("Saving...");
        context.tasks().run(
            () -> context.blocks()
                    .save(changed),
            saved -> {
                context.events().publish(
                    SpotterEvent
                        .BLOCK_CHANGED);
                status.setText(message);
            },
            failure -> status.setText(
                "Could not save: "
                + failure.getMessage()));
    }

    /** A session that can be edited in place. */
    private static Session copyOf(
            Session original) {
        Session copy = new Session(
            original.id(), original.date(),
            original.focus().orElse(null),
            new ArrayList<>(
                original.prescriptions()));
        original.adjustmentNote().ifPresent(
                copy::setAdjustmentNote);
        return copy;
    }
}
