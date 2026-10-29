package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain
        .AnalyticsSummary;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain.Point;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F04: strength analytics for one lift.
 *
 * Entirely deterministic. No model is involved
 * and no API key is needed.
 */
@Command(
    name = "analytics",
    description =
        "Show estimated one rep max, trend and"
        + " weekly volume for a lift.")
public final class AnalyticsCommand
        implements Callable<Integer> {

    /** Returned when the lift has no history. */
    public static final int EXIT_NO_DATA = 3;

    @Spec
    CommandSpec spec;

    @Parameters(
        index = "0",
        paramLabel = "LIFT",
        description =
            "squat, bench or deadlift.")
    Lift lift;

    @Option(
        names = {"--env"},
        paramLabel = "FILE",
        description = "Configuration file.")
    Path envFile = Path.of(".env");

    @Override
    public Integer call() {
        SpotterConfig config =
                SpotterConfig.load(envFile);
        var out = spec.commandLine().getOut();

        try (CoachController controller =
                new CoachController(config)) {

            AnalyticsSummary summary =
                controller.getAnalytics(lift);

            out.printf("%s%n", title(lift));
            out.println();

            if (!summary.hasData()) {
                out.println(
                    "  No training logged for"
                    + " this lift yet.");
                out.println(
                    "  Run: spotter seed");
                out.flush();
                return EXIT_NO_DATA;
            }

            summary.currentE1RM().ifPresent(
                e1rm -> out.printf(
                    "  estimated 1RM   %.1f kg%n",
                    e1rm));
            summary.peakE1RM().ifPresent(
                peak -> out.printf(
                    "  best estimate   %.1f kg%n",
                    peak));
            controller.trendFor(lift).ifPresent(
                trend -> out.printf(
                    "  trend           %+.1f kg"
                    + " across %d sessions%n",
                    trend,
                    summary.e1rmSeries().size()));
            summary.bestSet().ifPresent(
                set -> out.printf(
                    "  best set        %s%n",
                    set));

            printSeries(out, "Estimated 1RM",
                    summary.e1rmSeries(), "kg");
            printSeries(out, "Weekly volume",
                    summary.tonnage(), "kg");

            if (!summary.warnings().isEmpty()) {
                out.println();
                out.println("  Notes");
                for (String warning
                        : summary.warnings()) {
                    out.println(
                            "    " + warning);
                }
            }
            out.flush();
        }
        return 0;
    }

    private void printSeries(
            java.io.PrintWriter out,
            String heading,
            List<Point> series,
            String unit) {
        if (series.isEmpty()) {
            return;
        }
        out.println();
        out.println("  " + heading);
        for (Point point : series) {
            out.printf("    %s  %8.1f %s%n",
                    point.date(), point.value(),
                    unit);
        }
    }

    private static String title(Lift lift) {
        return switch (lift) {
            case SQUAT -> "Squat analytics";
            case BENCH -> "Bench analytics";
            case DEADLIFT ->
                    "Deadlift analytics";
        };
    }
}
