package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import ca.yorku.eecs3311.spotter.domain
        .AnalyticsSummary;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain.Point;
import ca.yorku.eecs3311.spotter
        .presentation.gui.GuiContext;
import ca.yorku.eecs3311.spotter
        .presentation.gui.Panel;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Strength over time for one lift.
 *
 * The chart is the reason this screen exists: a
 * stall is obvious in a line and invisible in a
 * column of numbers, which is the whole argument
 * for a dashboard over the command line.
 *
 * The warnings the analytics produce are shown
 * rather than hidden. A thin figure with its
 * reason stated is useful; the same figure
 * presented as solid is misleading, and the
 * difference costs nothing to show.
 */
public final class AnalyticsPanel
        implements Panel {

    private final GuiContext context;
    private final VBox root = new VBox(
            Cards.GAP);
    private final ChoiceBox<Lift> chooser =
            new ChoiceBox<>();

    public AnalyticsPanel(GuiContext context) {
        this.context = context;
        chooser.setItems(
            FXCollections.observableArrayList(
                Lift.values()));
        chooser.setValue(Lift.SQUAT);
        chooser.valueProperty().addListener(
            (observed, before, after) ->
                refresh());
    }

    @Override
    public String title() {
        return "Analytics";
    }

    @Override
    public String description() {
        return "Estimated maxima and volume,"
            + " computed from your logged sets.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().clear();

        HBox controls = new HBox(8,
            Cards.styled("LIFT",
                    "card-heading"),
            chooser);
        root.getChildren().add(controls);

        Lift lift = chooser.getValue();
        AnalyticsSummary summary =
            context.controller()
                .getAnalytics(lift);

        if (!summary.hasData()) {
            root.getChildren().add(
                Cards.emptyState(
                    "Nothing is logged for this"
                    + " lift, so there is"
                    + " nothing to estimate"
                    + " from.",
                    "spotter import <file>"));
            return;
        }

        root.getChildren().add(
                figures(summary, lift));
        root.getChildren().add(
                chartCard(summary));
        root.getChildren().add(
                volumeCard(summary));

        if (!summary.warnings().isEmpty()) {
            VBox notes = Cards.card("WORTH"
                    + " KNOWING");
            summary.warnings().forEach(warning ->
                notes.getChildren().add(
                    Cards.note(warning)));
            root.getChildren().add(notes);
        }
    }

    private Node figures(
            AnalyticsSummary summary,
            Lift lift) {
        VBox current = Cards.figure(
            "ESTIMATED MAX",
            summary.currentE1RM()
                .map(e1rm -> String.format(
                        "%.1f kg", e1rm))
                .orElse("no data"),
            "from your heaviest usable set");

        VBox peak = Cards.figure("BEST EVER",
            summary.peakE1RM()
                .map(e1rm -> String.format(
                        "%.1f kg", e1rm))
                .orElse("no data"),
            "across everything logged");

        VBox trend = Cards.figure("TREND",
            context.controller().trendFor(lift)
                .map(moved -> String.format(
                        "%+.1f kg", moved))
                .orElse("not yet"),
            summary.e1rmSeries().size()
                + " sessions in the window");

        return Cards.row(current, peak, trend);
    }

    /**
     * The estimated maximum over time.
     *
     * The vertical axis does not start at zero on
     * purpose. A lifter's maximum moving from 140
     * to 150 kg is the entire story, and an axis
     * from zero renders it as a flat line near
     * the top of the chart.
     */
    private Node chartCard(
            AnalyticsSummary summary) {
        NumberAxis days = new NumberAxis();
        days.setLabel("days ago");
        NumberAxis kilos = new NumberAxis();
        kilos.setLabel("estimated max, kg");
        kilos.setForceZeroInRange(false);

        LineChart<Number, Number> chart =
            new LineChart<>(days, kilos);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(260);

        XYChart.Series<Number, Number> series =
                new XYChart.Series<>();
        java.time.LocalDate today =
                java.time.LocalDate.now();
        for (Point point
                : summary.e1rmSeries()) {
            long ago = java.time.temporal
                .ChronoUnit.DAYS.between(
                    point.date(), today);
            series.getData().add(
                new XYChart.Data<>(-ago,
                        point.value()));
        }
        chart.getData().add(series);

        return Cards.card("ESTIMATED MAX OVER"
                + " TIME", chart);
    }

    private Node volumeCard(
            AnalyticsSummary summary) {
        VBox card = Cards.card("WEEKLY VOLUME");
        if (summary.tonnage().isEmpty()) {
            card.getChildren().add(
                Cards.note("Not enough logged"
                    + " weeks to show volume"
                    + " yet."));
            return card;
        }
        for (Point week : summary.tonnage()) {
            Label line = Cards.styled(
                String.format(
                    "week of %s   %8.0f kg",
                    week.date(), week.value()),
                "mono");
            card.getChildren().add(line);
        }
        return card;
    }
}
