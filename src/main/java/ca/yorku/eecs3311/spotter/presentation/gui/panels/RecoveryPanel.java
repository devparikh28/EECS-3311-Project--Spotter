package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.time.LocalDate;
import java.util.List;

import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryRuleEngine;
import ca.yorku.eecs3311.spotter
        .presentation.gui.GuiContext;
import ca.yorku.eecs3311.spotter
        .presentation.gui.Panel;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Recovery readings and what Spotter makes of
 * them.
 *
 * Each day is shown with its own verdict
 * attached, from the same rule engine the
 * adjustment feature uses, so the lifter sees why
 * a session was questioned rather than only that
 * it was. The thresholds are printed too, because
 * a judgement whose standard is hidden is hard to
 * trust and easy to argue with.
 */
public final class RecoveryPanel
        implements Panel {

    /** Weeks shown when the screen opens. */
    public static final int DEFAULT_WEEKS = 4;

    private final GuiContext context;
    private final RecoveryRuleEngine rules =
            new RecoveryRuleEngine();
    private final VBox root = new VBox(
            Cards.GAP);
    private final Spinner<Integer> weeks =
            new Spinner<>(1, 52,
                    DEFAULT_WEEKS);

    public RecoveryPanel(GuiContext context) {
        this.context = context;
        weeks.setPrefWidth(90);
        weeks.valueProperty().addListener(
            (observed, before, after) ->
                refresh());
    }

    @Override
    public String title() {
        return "Recovery";
    }

    @Override
    public String description() {
        return "Sleep, heart rate variability"
            + " and what they imply for"
            + " training.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().clear();

        HBox controls = new HBox(8,
            Cards.styled("WEEKS",
                    "card-heading"),
            weeks);
        root.getChildren().add(controls);

        LocalDate since = LocalDate.now()
            .minusWeeks(weeks.getValue());
        List<RecoveryDay> days = context
            .controller().recovery()
            .findSince(since);

        if (days.isEmpty()) {
            root.getChildren().add(
                Cards.emptyState(
                    "No recovery readings have"
                    + " been imported for this"
                    + " period. Spotter reads"
                    + " Whoop physiological"
                    + " cycle exports.",
                    "spotter import"
                    + " physiological_cycles.csv"));
            return;
        }

        List<RecoveryFlag> flags = context
            .controller()
            .recoveryFlags(since);

        root.getChildren().add(
                summaryRow(days, flags));
        root.getChildren().add(
                daysCard(days));
        root.getChildren().add(
                thresholdsCard());
    }

    private Node summaryRow(
            List<RecoveryDay> days,
            List<RecoveryFlag> flags) {
        VBox recorded = Cards.figure(
            "DAYS RECORDED",
            String.valueOf(days.size()),
            "readings imported");

        VBox flagged = Cards.figure(
            "DAYS FLAGGED",
            String.valueOf(flags.size()),
            flags.isEmpty()
                ? "nothing worth acting on"
                : "worth easing off for");

        long significant = flags.stream()
            .filter(RecoveryFlag::isSignificant)
            .count();
        VBox serious = Cards.figure(
            "SIGNIFICANT",
            String.valueOf(significant),
            "more than one reading off");

        return Cards.row(recorded, flagged,
                serious);
    }

    /**
     * Every day, newest first, with its verdict.
     */
    private Node daysCard(
            List<RecoveryDay> days) {
        VBox card = Cards.card("BY DAY");

        days.stream()
            .sorted((a, b) -> b.date()
                    .compareTo(a.date()))
            .forEach(day -> card.getChildren()
                .add(line(day)));
        return card;
    }

    private Label line(RecoveryDay day) {
        StringBuilder text =
                new StringBuilder();
        text.append(day.date()).append("   ");

        day.recoveryScore().ifPresent(score ->
            text.append(String.format(
                "recovery %3d%%   ", score)));
        day.sleepHours().ifPresent(hours ->
            text.append(String.format(
                "slept %4.1f h   ", hours)));
        day.hrvMs().ifPresent(hrv ->
            text.append(String.format(
                "HRV %3.0f ms   ", hrv)));
        day.soreness().ifPresent(soreness ->
            text.append("soreness ")
                .append(soreness)
                .append("/5   "));

        var flag = rules.evaluate(day);
        String style = "mono";
        if (flag.isPresent()) {
            text.append("  ")
                .append(flag.get().summary());
            style = flag.get().isSignificant()
                    ? "flag-significant"
                    : "flag-mild";
        }
        Label label = Cards.styled(
                text.toString(), style);
        label.setWrapText(true);
        return label;
    }

    /** The standards being applied, in the open. */
    private Node thresholdsCard() {
        VBox card = Cards.card(
                "WHAT COUNTS AS POOR");
        card.getChildren().addAll(
            Cards.field("recovery score below",
                RecoveryRuleEngine
                    .MIN_RECOVERY_SCORE
                    + " percent"),
            Cards.field("sleep below",
                String.format("%.1f hours",
                    RecoveryRuleEngine
                        .MIN_SLEEP_HOURS)),
            Cards.field("soreness at or above",
                RecoveryRuleEngine.MAX_SORENESS
                    + " out of 5"),
            Cards.field("energy at or below",
                RecoveryRuleEngine.MIN_ENERGY
                    + " out of 5"),
            Cards.note("One reading off is a"
                + " mild flag, more than one is"
                + " significant. Only a flagged"
                + " day lets the coach change a"
                + " session."));
        return card;
    }
}
