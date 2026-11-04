package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter
        .presentation.gui.GuiContext;
import ca.yorku.eecs3311.spotter
        .presentation.gui.Panel;

import ca.yorku.eecs3311.spotter.application
        .event.SpotterEvent;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * The first screen: where the lifter stands
 * today.
 *
 * Shows the three estimated maxima, today's
 * session if a block is being trained, and
 * anything recovery has flagged this week. The
 * figures are read rather than computed here,
 * since the analytics already exist and a second
 * implementation would eventually disagree with
 * the first.
 */
public final class DashboardPanel
        implements Panel {

    /** How far back flags are worth showing. */
    public static final int FLAG_WEEKS = 1;

    private final GuiContext context;
    private final VBox root = new VBox(
            Cards.GAP);

    public DashboardPanel(GuiContext context) {
        this.context = context;
    }

    /**
     * Everything, because the dashboard is a
     * summary of every other screen. A panel
     * that shows a figure from each repository
     * falls behind on any change.
     */
    @Override
    public java.util.Set<SpotterEvent>
            changedBy() {
        return java.util.EnumSet.allOf(
                SpotterEvent.class);
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    @Override
    public String description() {
        return "Where you stand today.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().clear();

        if (!context.controller()
                .hasTrainingHistory()) {
            root.getChildren().add(
                Cards.emptyState(
                    "No training has been"
                    + " imported yet, so there"
                    + " is nothing to show."
                    + " Import a Hevy export or"
                    + " load the sample data.",
                    "spotter seed"));
            return;
        }

        root.getChildren().add(strengthRow());
        root.getChildren().add(todayCard());
        root.getChildren().add(recoveryCard());
        root.getChildren().add(profileCard());
    }

    /** The three competition lifts, side by side. */
    private Node strengthRow() {
        Map<Lift, Double> current =
                context.controller()
                    .currentE1RMs();

        VBox squat = figureFor(Lift.SQUAT,
                current);
        VBox bench = figureFor(Lift.BENCH,
                current);
        VBox deadlift = figureFor(
                Lift.DEADLIFT, current);
        return Cards.row(squat, bench,
                deadlift);
    }

    private VBox figureFor(
            Lift lift,
            Map<Lift, Double> current) {
        Double e1rm = current.get(lift);
        String heading = switch (lift) {
            case SQUAT -> "SQUAT";
            case BENCH -> "BENCH";
            case DEADLIFT -> "DEADLIFT";
        };
        if (e1rm == null) {
            return Cards.figure(heading, "no data",
                    "nothing logged for this"
                    + " lift");
        }
        String trend = context.controller()
            .trendFor(lift)
            .map(moved -> String.format(
                "%+.1f kg across the window",
                moved))
            .orElse("one session only, so no"
                    + " trend yet");
        return Cards.figure(heading,
            String.format("%.1f kg", e1rm),
            trend);
    }

    /** Today's session, when a block is active. */
    private Node todayCard() {
        TrainingBlock block = context.blocks()
                .active().orElse(null);
        if (block == null) {
            return Cards.card("TODAY",
                Cards.note("No block is being"
                    + " trained. Generate one"
                    + " from the Plan screen."));
        }

        LocalDate today = LocalDate.now();
        Session session = block.allSessions()
            .stream()
            .filter(each -> each.date()
                    .equals(today))
            .findFirst()
            .orElse(null);

        if (session == null) {
            return Cards.card("TODAY",
                Cards.note("Nothing is scheduled"
                    + " for today in the current"
                    + " block."));
        }

        VBox card = Cards.card("TODAY, "
                + session.focus()
                    .orElse("training"));
        session.prescriptions().forEach(p ->
            card.getChildren().add(
                Cards.styled(String.format(
                    "%s   %d x %d @ %.1f kg",
                    p.exercise().name(),
                    p.sets(), p.reps(),
                    p.loadKg()), "mono")));

        session.adjustmentNote().ifPresent(note ->
            card.getChildren().add(
                    Cards.note(note)));
        return card;
    }

    /** Anything recovery flagged recently. */
    private Node recoveryCard() {
        List<RecoveryFlag> flags = context
            .controller().recoveryFlags(
                LocalDate.now().minusWeeks(
                        FLAG_WEEKS));

        if (flags.isEmpty()) {
            VBox card = Cards.card("RECOVERY");
            card.getChildren().add(
                Cards.styled("Nothing flagged"
                    + " this week", "good"));
            return card;
        }

        VBox card = Cards.card("RECOVERY");
        for (RecoveryFlag flag : flags) {
            Label line = Cards.styled(
                flag.date() + "   "
                + flag.summary(),
                flag.isSignificant()
                    ? "flag-significant"
                    : "flag-mild");
            line.setWrapText(true);
            card.getChildren().add(line);
        }
        return card;
    }

    /** Who Spotter thinks it is coaching. */
    private Node profileCard() {
        LifterProfile profile =
                context.profile().orElse(null);
        if (profile == null) {
            return Cards.card("LIFTER",
                Cards.note("No profile yet."
                    + " Set one up on the"
                    + " Profile screen so plans"
                    + " can be built for you"
                    + " rather than for"
                    + " nobody."));
        }
        VBox card = Cards.card("LIFTER");
        card.getChildren().addAll(
            Cards.field("goal",
                profile.goal().name()),
            Cards.field("experience",
                profile.experience().name()),
            Cards.field("trains",
                profile.trainingDaysPerWeek()
                    + " days a week"),
            Cards.field("bodyweight",
                String.format("%.1f kg",
                    profile.bodyweightKg())));
        return card;
    }
}
