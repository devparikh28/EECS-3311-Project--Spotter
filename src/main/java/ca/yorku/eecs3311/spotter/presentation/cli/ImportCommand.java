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

            if (kind == SourceKind.UNKNOWN) {
                err.printf(
                    "Could not recognise %s.%n",
                    file);
                err.println(
                    "Spotter reads Hevy workout"
                    + " exports and Whoop"
                    + " physiological cycle"
                    + " exports.");
                err.flush();
                return EXIT_UNRECOGNISED;
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
}
