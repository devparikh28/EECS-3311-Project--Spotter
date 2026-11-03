package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .TestDayPlan;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F08 for the lifter with no meet.
 *
 * Most people who want to know what they can lift
 * are not competing. The arithmetic for a test
 * day has existed since the attempt calculator
 * landed, and only the way in was missing, so a
 * feature built for everybody was reachable only
 * by someone with a meet date.
 *
 * No model is involved, and the command says so.
 */
@Command(
    name = "testday",
    description =
        "Plan a heavy single, with warm ups, for"
        + " a lifter with no meet.")
public final class TestDayCommand
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
        PrintWriter out =
                spec.commandLine().getOut();

        try (CoachController controller =
                new CoachController(config)) {

            Optional<TestDayPlan> planned =
                controller.projectTestDay(lift);
            if (planned.isEmpty()) {
                out.println("There is no usable"
                    + " history for this lift,"
                    + " so there is nothing to"
                    + " work a single up"
                    + " from.");
                out.println(
                    "  Run: spotter seed");
                out.flush();
                return EXIT_NO_DATA;
            }

            TestDayPlan plan = planned.get();

            out.printf("Test day for %s%n",
                    plan.lift());
            out.println();

            int step = 1;
            for (double warmup
                    : plan.warmups()) {
                out.printf(
                    "  warm up %d     %6.1f"
                    + " kg%n", step++, warmup);
            }
            out.printf(
                "  top single    %6.1f kg%n",
                plan.topSingle());
            out.println();
            out.printf("  %d singles in all%n",
                    plan.totalSingles());

            if (!plan.rationale().isBlank()) {
                out.println();
                out.println("  "
                        + plan.rationale());
            }

            out.println();
            out.println("  No model was involved"
                + " in any of the above. With a"
                + " meet date set, use spotter"
                + " attempts instead.");
            out.flush();
        }
        return 0;
    }
}
