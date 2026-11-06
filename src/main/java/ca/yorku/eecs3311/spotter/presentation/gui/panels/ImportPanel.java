package ca.yorku.eecs3311.spotter
        .presentation.gui.panels;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.yorku.eecs3311.spotter.application
        .event.SpotterEvent;
import ca.yorku.eecs3311.spotter.domain
        .WeightUnit;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.ColumnMapping;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.CsvFileReader;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.ImportReport;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.SourceKind;
import ca.yorku.eecs3311.spotter
        .presentation.gui.GuiContext;
import ca.yorku.eecs3311.spotter
        .presentation.gui.Panel;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * F02 and F03 on screen: bring your data in.
 *
 * The same import the command line has, with a
 * file dialog in front of it. Hevy and Whoop
 * exports are recognised from their headers and
 * need one click. Any other spreadsheet is shown
 * as a column mapping, pre-filled with a guess,
 * because only the lifter knows which column is
 * the weight, and a mapping can be saved so the
 * question is asked once per tracker.
 *
 * Nothing is imported until the button is
 * pressed, and the result says how many sessions
 * were added, how many were already there and
 * which rows were skipped, so a bad row in a real
 * export is visible rather than silently lost.
 */
public final class ImportPanel
        implements Panel {

    private static final String NONE = "(none)";

    private final GuiContext context;
    private final VBox root = new VBox(
            Cards.GAP);
    private final VBox detail = new VBox(
            Cards.GAP);
    private final Label chosen = Cards.styled(
        "No file chosen yet.", "mono");
    private final Label status = Cards.note("");

    private Path file;
    private SourceKind kind;
    private List<String> header = List.of();

    private final Map<String, ChoiceBox<String>>
        pickers = new LinkedHashMap<>();
    private final ChoiceBox<WeightUnit> unit =
            new ChoiceBox<>();
    private final TextField dateFormat =
            new TextField();
    private final TextField saveAs =
            new TextField();

    public ImportPanel(GuiContext context) {
        this.context = context;
        unit.getItems().addAll(
                WeightUnit.values());
        unit.setValue(WeightUnit.KG);
        dateFormat.setPrefWidth(300);
        saveAs.setPrefWidth(300);
        dateFormat.setPromptText(
            "only if not YYYY-MM-DD, e.g."
            + " dd/MM/yyyy");
        saveAs.setPromptText(
            "name to save this mapping as"
            + " (optional)");
    }

    /** Nothing it shows is changed by other screens. */
    @Override
    public Set<SpotterEvent> changedBy() {
        return Set.of();
    }

    @Override
    public String title() {
        return "Import";
    }

    @Override
    public String description() {
        return "Bring in workouts and recovery"
            + " from a CSV export.";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().clear();

        Button choose = Cards.primary(
            new Button("Choose a CSV file..."));
        choose.setOnAction(event -> choose());

        root.getChildren().add(Cards.card(
            "YOUR DATA",
            Cards.note("Spotter reads Hevy"
                + " workout exports and Whoop"
                + " recovery exports on sight."
                + " Any other spreadsheet works"
                + " too: you say which column is"
                + " which, once, and Spotter"
                + " remembers."),
            new HBox(12, choose, chosen),
            status));
        root.getChildren().add(detail);
    }

    private void choose() {
        FileChooser dialog = new FileChooser();
        dialog.setTitle("Choose a CSV export");
        dialog.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                "CSV files", "*.csv", "*.CSV"));
        File picked = dialog.showOpenDialog(
            root.getScene() == null
                ? null
                : root.getScene().getWindow());
        if (picked != null) {
            open(picked.toPath());
        }
    }

    /** Looks at a file without importing it. */
    void open(Path path) {
        file = path;
        chosen.setText(
                path.getFileName().toString());
        detail.getChildren().clear();
        status.setText("Reading the header...");

        context.tasks().run(
            () -> new Object[] {
                context.controller().detect(path),
                CsvFileReader.readHeader(path)},
            found -> {
                kind = (SourceKind) found[0];
                @SuppressWarnings("unchecked")
                List<String> columns =
                    (List<String>) found[1];
                header = columns;
                status.setText("");
                offer();
            },
            failure -> status.setText(
                "Could not read that file: "
                + failure.getMessage()));
    }

    private void offer() {
        detail.getChildren().clear();
        if (kind == SourceKind.UNKNOWN) {
            detail.getChildren().add(
                    mappingCard());
            return;
        }
        Button go = Cards.primary(
                new Button("Import"));
        go.setOnAction(event -> run(
            () -> context.controller()
                    .importFile(file)));
        detail.getChildren().add(Cards.card(
            "THIS FILE",
            Cards.badge("RECOGNISED", "good"),
            Cards.styled(kind.description(),
                "figure-unit"),
            Cards.note("Nothing is stored until"
                + " you press Import. Sessions"
                + " already in Spotter are"
                + " skipped, not doubled."),
            go));
    }

    private Node mappingCard() {
        pickers.clear();
        ColumnMapping guess =
            ColumnMapping.suggestFor(header);

        VBox card = Cards.card(
            "WHICH COLUMN IS WHICH",
            Cards.note("Spotter does not know"
                + " this format. The guesses"
                + " below come from the column"
                + " names; correct any that are"
                + " wrong."));

        for (String field
                : ColumnMapping.REQUIRED) {
            card.getChildren().add(
                    pickerRow(field, true,
                        guess));
        }
        for (String field
                : ColumnMapping.OPTIONAL) {
            card.getChildren().add(
                    pickerRow(field, false,
                        guess));
        }

        unit.setValue(guess.unit());
        HBox unitRow = new HBox(8,
            fieldName("weights are in"), unit);
        unitRow.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(unitRow,
            labelled("date format", dateFormat),
            labelled("save as", saveAs));

        List<String> saved = context.controller()
                .importProfiles().names();
        if (!saved.isEmpty()) {
            ChoiceBox<String> earlier =
                new ChoiceBox<>();
            earlier.getItems().addAll(saved);
            earlier.setOnAction(event ->
                useSaved(earlier.getValue()));
            card.getChildren().add(
                labelled("use saved", earlier));
        }

        Button go = Cards.primary(
                new Button("Import"));
        go.setOnAction(event -> importMapped());
        card.getChildren().add(go);
        return card;
    }

    private HBox pickerRow(String field,
                           boolean required,
                           ColumnMapping guess) {
        ChoiceBox<String> picker =
                new ChoiceBox<>();
        picker.getItems().add(NONE);
        picker.getItems().addAll(header);
        picker.setValue(
            guess.columnFor(field)
                .orElse(NONE));
        pickers.put(field, picker);
        return labelled(required
                ? field + " *"
                : field, picker);
    }

    private static Label fieldName(String text) {
        Label label = Cards.styled(text,
                "field-name");
        label.setMinWidth(150);
        return label;
    }

    private static HBox labelled(String name,
                                 Node control) {
        HBox row = new HBox(8,
                fieldName(name), control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void useSaved(String name) {
        if (name == null) {
            return;
        }
        context.controller().importProfiles()
            .byName(name).ifPresent(mapping -> {
                mapping.columns().forEach(
                    (field, column) -> {
                        ChoiceBox<String> picker =
                            pickers.get(field);
                        if (picker != null
                                && picker.getItems()
                                    .contains(column)) {
                            picker.setValue(column);
                        }
                    });
                unit.setValue(mapping.unit());
                dateFormat.setText(
                    mapping.dateFormat()
                        .orElse(""));
            });
    }

    private void importMapped() {
        Map<String, String> columns =
                new LinkedHashMap<>();
        pickers.forEach((field, picker) -> {
            String column = picker.getValue();
            if (column != null
                    && !NONE.equals(column)) {
                columns.put(field, column);
            }
        });
        ColumnMapping mapping =
            new ColumnMapping(columns,
                unit.getValue(),
                dateFormat.getText());

        var problem = mapping.validate(header);
        if (problem.isPresent()) {
            status.setText(problem.get());
            return;
        }
        status.setText("");

        String name = saveAs.getText() == null
                ? ""
                : saveAs.getText().trim();
        run(() -> {
            ImportReport report = context
                .controller()
                .importMapped(file, mapping);
            if (!name.isEmpty()) {
                context.controller()
                    .importProfiles()
                    .save(name, SourceKind.UNKNOWN,
                            mapping);
            }
            return report;
        });
    }

    private void run(
            java.util.function.Supplier
                <ImportReport> work) {
        status.setText("Importing...");
        context.tasks().run(work,
            report -> {
                status.setText("");
                detail.getChildren().add(
                        result(report));
                if (!report.isEmpty()) {
                    context.events().publish(
                        SpotterEvent
                            .TRAINING_CHANGED);
                }
            },
            failure -> status.setText(
                "Import failed: "
                + failure.getMessage()));
    }

    private Node result(ImportReport report) {
        VBox box = new VBox(Cards.GAP);
        box.getChildren().add(
            Cards.sectionHeader("RESULT"));
        box.getChildren().add(Cards.row(
            Cards.figure("SESSIONS ADDED",
                String.valueOf(
                    report.imported()), null),
            Cards.figure("ALREADY HERE",
                String.valueOf(
                    report.duplicates()),
                "skipped, not doubled"),
            Cards.figure("SETS READ",
                String.valueOf(
                    report.setsRead()), null),
            Cards.figure("ROWS SKIPPED",
                String.valueOf(
                    report.skippedCount()),
                null)));

        if (report.isEmpty()) {
            box.getChildren().add(Cards.note(
                "Nothing could be read from"
                + " that file. Check the column"
                + " choices and try again."));
        }
        if (report.skippedCount() > 0) {
            VBox skipped = Cards.card(
                    "WHAT WAS SKIPPED");
            report.skipped().stream().limit(8)
                .forEach(reason ->
                    skipped.getChildren().add(
                        Cards.note(reason)));
            if (report.skippedCount() > 8) {
                skipped.getChildren().add(
                    Cards.note("and "
                        + (report.skippedCount()
                            - 8) + " more."));
            }
            box.getChildren().add(skipped);
        }
        return box;
    }
}
