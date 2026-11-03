package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.CsvFormatException;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.ImportReport;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.ColumnMapping;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.CsvFileReader;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.SourceKind;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F02 and F03: bring a workout or recovery export
 * into Spotter.
 *
 * The format is worked out from the file's own
 * header, so there is no separate command per
 * app and no flag to remember.
 */
@Command(
    name = "import",
    description =
        "Import a workout or recovery export."
        + " The format is detected"
        + " automatically.")
public final class ImportCommand
        implements Callable<Integer> {

    /** Returned when no adapter recognises it. */
    public static final int
            EXIT_UNRECOGNISED = 4;

    @Spec
    CommandSpec spec;

    @Parameters(
        index = "0",
        paramLabel = "FILE",
        description = "The CSV export to read.")
    Path file;

    @Option(
        names = {"--env"},
        paramLabel = "FILE",
        description = "Configuration file.")
    Path envFile = Path.of(".env");

    @Option(
        names = {"--map"},
        description =
            "Read an unrecognised file through"
            + " a column mapping, suggesting"
            + " one from the header.")
    boolean map;

    @Option(
        names = {"--set"},
        paramLabel = "FIELD=COLUMN",
        description =
            "Correct one mapped field, such as"
            + " --set weight=\"Weight (kg)\"."
            + " Repeatable.")
    java.util.List<String> corrections =
            new java.util.ArrayList<>();

    @Option(
        names = {"--unit"},
        description =
            "KG or LB, for the weight column.")
    ca.yorku.eecs3311.spotter.domain.WeightUnit
            unit;

    @Option(
        names = {"--date-format"},
        paramLabel = "PATTERN",
        description =
            "Date pattern, such as dd/MM/yyyy,"
            + " when the dates are not"
            + " YYYY-MM-DD.")
    String dateFormat;

    @Option(
        names = {"--profile"},
        paramLabel = "NAME",
        description =
            "Use a mapping saved earlier.")
    String profile;

    @Option(
        names = {"--save-as"},
        paramLabel = "NAME",
        description =
            "Save this mapping for next time.")
    String saveAs;

    @Option(
        names = {"--dry-run"},
        description =
            "Show the mapping and stop, without"
            + " importing anything.")
    boolean dryRun;

    @Option(
        names = {"--show-skipped"},
        description =
            "List every row that was passed"
            + " over, not just the count.")
    boolean showSkipped;

    @Override
    public Integer call() {
        SpotterConfig config =
                SpotterConfig.load(envFile);
        var out = spec.commandLine().getOut();
        var err = spec.commandLine().getErr();

        try (CoachController controller =
                new CoachController(config)) {

            SourceKind kind =
                    controller.detect(file);

            if (kind == SourceKind.UNKNOWN
                    && !map
                    && profile == null) {
                err.printf(
                    "Could not recognise %s.%n",
                    file);
                err.println(
                    "Spotter reads Hevy workout"
                    + " exports and Whoop"
                    + " physiological cycle"
                    + " exports.");
                err.println(
                    "For anything else, run"
                    + " again with --map and"
                    + " Spotter will suggest a"
                    + " column mapping from the"
                    + " header.");
                err.flush();
                return EXIT_UNRECOGNISED;
            }

            if (map || profile != null) {
                return importMapped(out, err,
                        controller);
            }

            out.printf("Reading %s as %s.%n",
                    file.getFileName(),
                    kind.description());

            ImportReport report =
                    controller.importFile(file);

            out.println();
            out.printf("  imported    %d%n",
                    report.imported());
            out.printf("  already had %d%n",
                    report.duplicates());
            out.printf("  rows read   %d%n",
                    report.setsRead());
            out.printf("  skipped     %d%n",
                    report.skippedCount());

            if (report.skippedCount() > 0) {
                out.println();
                if (showSkipped) {
                    out.println("  Skipped rows");
                    for (String reason
                            : report.skipped()) {
                        out.println("    "
                                + reason);
                    }
                } else {
                    out.println(
                        "  Run again with"
                        + " --show-skipped to"
                        + " see why rows were"
                        + " passed over.");
                }
            }

            out.println();
            out.println(kind == SourceKind.WHOOP
                ? "Try: spotter recovery"
                : "Try: spotter analytics squat");
            out.flush();
            return 0;

        } catch (CsvFormatException e) {
            err.println("Could not import: "
                    + e.getMessage());
            err.flush();
            return EXIT_UNRECOGNISED;
        }
    }

    /**
     * The mapped path: suggest, correct, save,
     * import.
     *
     * Stage 1 put this behind a dialog. On the
     * command line the same conversation is
     * three options: Spotter suggests, the
     * lifter corrects with --set, and --save-as
     * means the question is never asked again
     * for that application.
     */
    private Integer importMapped(
            java.io.PrintWriter out,
            java.io.PrintWriter err,
            CoachController controller) {

        ColumnMapping mapping;
        if (profile != null) {
            mapping = controller.importProfiles()
                .byName(profile).orElse(null);
            if (mapping == null) {
                err.println("There is no saved"
                    + " mapping called \""
                    + profile + "\".");
                err.println("  Saved mappings: "
                    + controller.importProfiles()
                        .names());
                err.flush();
                return EXIT_UNRECOGNISED;
            }
            out.printf("Using the saved mapping"
                    + " \"%s\".%n", profile);
        } else {
            mapping = ColumnMapping.suggestFor(
                CsvFileReader.readHeader(file));
            out.printf("Suggesting a mapping for"
                    + " %s.%n",
                    file.getFileName());
        }

        mapping = withCorrections(mapping);

        out.println();
        out.println("  Mapping");
        for (String field
                : ColumnMapping.REQUIRED) {
            out.printf("    %-10s %s%n", field,
                mapping.columnFor(field)
                    .orElse("not mapped"));
        }
        for (String field
                : ColumnMapping.OPTIONAL) {
            mapping.columnFor(field).ifPresent(
                column -> out.printf(
                    "    %-10s %s%n", field,
                    column));
        }
        out.printf("    %-10s %s%n", "unit",
                mapping.unit());
        mapping.dateFormat().ifPresent(format ->
            out.printf("    %-10s %s%n",
                    "dates", format));

        java.util.Optional<String> problem =
            mapping.validate(
                CsvFileReader.readHeader(file));
        if (problem.isPresent()) {
            out.println();
            err.println("That mapping cannot be"
                    + " used.");
            err.println("  "
                    + problem.get());
            err.println("  Correct it with --set"
                + " field=column, for example"
                + " --set weight=Weight.");
            err.flush();
            return EXIT_UNRECOGNISED;
        }

        if (saveAs != null
                && !saveAs.isBlank()) {
            controller.importProfiles().save(
                saveAs, SourceKind.UNKNOWN,
                mapping);
            out.println();
            out.printf("  Saved as \"%s\". Next"
                + " time: spotter import <file>"
                + " --profile %s%n",
                saveAs, saveAs);
        }

        if (dryRun) {
            out.println();
            out.println("  Nothing was imported."
                + " Run again without --dry-run"
                + " to import.");
            out.flush();
            return 0;
        }

        ImportReport report =
            controller.importMapped(file,
                    mapping);

        out.println();
        out.printf("  imported    %d%n",
                report.imported());
        out.printf("  already had %d%n",
                report.duplicates());
        out.printf("  rows read   %d%n",
                report.setsRead());
        out.printf("  skipped     %d%n",
                report.skippedCount());

        if (report.skippedCount() > 0) {
            out.println();
            if (showSkipped) {
                out.println("  Skipped rows");
                for (String reason
                        : report.skipped()) {
                    out.println("    " + reason);
                }
            } else {
                out.println("  Run again with"
                    + " --show-skipped to see"
                    + " why rows were passed"
                    + " over.");
            }
        }
        out.flush();
        return 0;
    }

    /** The mapping with any --set applied. */
    private ColumnMapping withCorrections(
            ColumnMapping mapping) {

        java.util.Map<String, String> columns =
            new java.util.LinkedHashMap<>(
                mapping.columns());

        for (String correction : corrections) {
            int at = correction.indexOf('=');
            if (at <= 0) {
                continue;
            }
            String field = correction
                    .substring(0, at).trim();
            String column = correction
                    .substring(at + 1).trim();
            if (column.isEmpty()) {
                columns.remove(field);
            } else {
                columns.put(field, column);
            }
        }

        return new ColumnMapping(columns,
            unit == null
                ? mapping.unit()
                : unit,
            dateFormat == null
                ? mapping.dateFormat()
                    .orElse(null)
                : dateFormat);
    }
}
