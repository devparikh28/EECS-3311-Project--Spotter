package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F07: days whose recovery was poor enough to
 * question the session planned for them.
 *
 * Deterministic. The thresholds are fixed and no
 * model is involved; rewriting the session is the
 * agent's job, and this is what tells it there is
 * something to rewrite.
 */
@Command(
    name = "recovery",
    description =
        "Show days flagged for poor recovery.")
public final class RecoveryCommand
        implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(
        names = {"--weeks"},
        paramLabel = "N",
        description =
            "How far back to look."
            + " Default 8.")
    int weeks = 8;

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

            LocalDate since = LocalDate.now()
                    .minusWeeks(weeks);
            int days = controller.recovery()
                    .findSince(since).size();
            List<RecoveryFlag> flags =
                controller.recoveryFlags(since);

            out.printf(
                "Recovery, last %d weeks%n",
                weeks);
            out.println();

            if (days == 0) {
                out.println(
                    "  No recovery data yet.");
                out.println(
                    "  Import a Whoop export:"
                    + " spotter import"
                    + " <file.csv>");
                out.flush();
                return 0;
            }

            out.printf("  days recorded   %d%n",
                    days);
            out.printf("  days flagged    %d%n",
                    flags.size());

            if (flags.isEmpty()) {
                out.println();
                out.println(
                    "  Nothing to act on."
                    + " Train as planned.");
                out.flush();
                return 0;
            }

            out.println();
            for (RecoveryFlag flag : flags) {
                out.printf("  %s  %s%n",
                    flag.date(),
                    flag.isSignificant()
                        ? "[significant]"
                        : "[mild]");
                out.printf("    %s%n",
                        flag.summary());
            }
            out.flush();
        }
        return 0;
    }
}
