package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import ca.yorku.eecs3311.spotter.application
        .event.SpotterEvent;
import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport;
import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport.Section;
import ca.yorku.eecs3311.spotter
        .infrastructure.report.CsvReportWriter;
import ca.yorku.eecs3311.spotter
        .infrastructure.report.PdfReportWriter;
import ca.yorku.eecs3311.spotter
        .infrastructure.report.ReportWriter;
import ca.yorku.eecs3311.spotter
        .presentation.gui.GuiContext;
import ca.yorku.eecs3311.spotter
        .presentation.gui.Panel;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Your training on one page, to keep or to hand
 * to a coach.
 *
 * The screen shows exactly what an export will
 * contain, built by the same ReportBuilder, so
 * there is no surprise in the file. Export as PDF
 * for a document, or as CSV for a spreadsheet.
 */
public final class ReportPanel
        implements Panel {

    private final GuiContext context;
    private final VBox root = new VBox(
            Cards.GAP);
    private final Label status = Cards.note("");

    private TrainingReport current;

    public ReportPanel(GuiContext context) {
        this.context = context;
    }

    /** Every kind of change alters the report. */
    @Override
    public Set<SpotterEvent> changedBy() {
        return EnumSet.allOf(SpotterEvent.class);
    }

    @Override
    public String title() {
        return "Reports";
    }

    @Override
    public String description() {
        return "Your training on one page. Export"
            + " it as a PDF or a spreadsheet.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().clear();
        current = context.reports()
                .build(LocalDate.now());

        Button pdf = Cards.primary(
            new Button("Export PDF..."));
        pdf.setOnAction(event ->
            export(new PdfReportWriter()));
        Button csv = new Button(
                "Export CSV...");
        csv.setOnAction(event ->
            export(new CsvReportWriter()));

        root.getChildren().add(Cards.card(
            "EXPORT",
            Cards.note("What is below is exactly"
                + " what the file will contain."),
            new HBox(8, pdf, csv),
            status));

        for (Section section
                : current.sections()) {
            root.getChildren().add(
                    preview(section));
        }
    }

    private Node preview(Section section) {
        VBox card = Cards.card(
            section.heading().toUpperCase());
        for (String line : section.lines()) {
            card.getChildren().add(
                    Cards.note(line));
        }
        if (section.hasTable()) {
            GridPane grid = new GridPane();
            grid.setHgap(28);
            grid.setVgap(6);
            int col = 0;
            for (String name : section.columns()) {
                grid.add(Cards.styled(name,
                    "card-heading"), col++, 0);
            }
            int rowIndex = 1;
            for (List<String> cells
                    : section.rows()) {
                col = 0;
                for (String cell : cells) {
                    grid.add(Cards.styled(cell,
                        "field-value"),
                        col++, rowIndex);
                }
                rowIndex++;
            }
            card.getChildren().add(grid);
        }
        return card;
    }

    private void export(ReportWriter writer) {
        FileChooser dialog = new FileChooser();
        dialog.setTitle("Save report");
        dialog.setInitialFileName(
            "spotter-report-" + LocalDate.now()
                + "." + writer.extension());
        dialog.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                writer.extension().toUpperCase()
                    + " file",
                "*." + writer.extension()));
        File picked = dialog.showSaveDialog(
            root.getScene() == null
                ? null
                : root.getScene().getWindow());
        if (picked != null) {
            save(writer, picked.toPath());
        }
    }

    /** Writes the report; separate so a test can call it. */
    void save(ReportWriter writer, Path file) {
        TrainingReport report = current;
        status.setText("Saving...");
        context.tasks().run(
            () -> {
                try {
                    writer.write(report, file);
                    return file;
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(
                        e.getMessage(), e);
                }
            },
            saved -> status.setText("Saved "
                + saved.getFileName()
                + " in "
                + saved.getParent() + "."),
            failure -> status.setText(
                "Could not save: "
                + failure.getMessage()));
    }
}
