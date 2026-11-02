package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;
import ca.yorku.eecs3311.spotter
        .infrastructure.sample.SampleData;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Loads the bundled sample training history, so
 * the application can be run and demonstrated
 * without anyone's real export.
 */
@Command(
    name = "seed",
    description =
        "Load the bundled sample training"
        + " history.")
public final class SeedCommand
        implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(
        names = {"--env"},
        paramLabel = "FILE",
        description = "Configuration file.")
    Path envFile = Path.of(".env");

    @Option(
        names = {"--force"},
        description =
            "Load even when history already"
            + " exists.")
    boolean force;

    @Override
    public Integer call() {
        SpotterConfig config =
                SpotterConfig.load(envFile);
        var out = spec.commandLine().getOut();

        try (CoachController controller =
                new CoachController(config)) {

            // Foods are written first and
            // unconditionally. They are
            // reference data rather than
            // history, writing them twice
            // changes nothing, and a lifter
            // with their own training logged
            // still needs a food catalogue to
            // plan meals from.
            int foodsWritten =
                controller.loadSampleFoods();

            if (controller.hasTrainingHistory()
                    && !force) {
                out.printf(
                    "The food catalogue has %d"
                    + " foods.%n", foodsWritten);
                out.println(
                    "Training history already"
                    + " exists. Use --force to"
                    + " load the sample anyway.");
                out.flush();
                return 0;
            }

            int written =
                controller.loadSampleData(
                        LocalDate.now());
            out.printf(
                "Loaded %d sample sessions and"
                + " %d foods.%n",
                written, foodsWritten);
            out.println();
            for (String line
                    : SampleData.describe()) {
                out.println("  " + line);
            }
            out.println();
            out.println(
                "Try: spotter analytics squat");
            out.println(
                "     spotter meal"
                + " --calories 900");
            out.flush();
        }
        return 0;
    }
}
