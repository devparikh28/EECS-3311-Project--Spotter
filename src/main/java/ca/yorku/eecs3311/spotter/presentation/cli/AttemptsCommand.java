package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent
        .PlanValidator;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent.agents
        .AttemptRationaleAgent;
import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain
        .AttemptCalculator;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .MeetAttempts;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F08: three meet attempts, and optionally the
 * coach's explanation of them.
 *
 * The numbers are arithmetic and need no model.
 * The explanation does, and this command is the
 * clearest demonstration of the division: run it
 * plain and the figures appear with no model
 * involved at all; add --explain and the same
 * figures appear with words around them, having
 * passed a gate that rejects any explanation
 * containing a number Spotter did not calculate.
 */
@Command(
    name = "attempts",
    description =
        "Show the three meet attempts for a"
        + " lift.")
public final class AttemptsCommand
        implements Callable<Integer> {

    /** Returned when the lift has no history. */
    public static final int EXIT_NO_DATA = 3;

    /** Returned when no explanation passed. */
    public static final int EXIT_REFUSED = 4;

    @Spec
    CommandSpec spec;

    @Parameters(
        index = "0",
        paramLabel = "LIFT",
        description =
            "squat, bench or deadlift.")
    Lift lift;

    @Option(
        names = {"--explain"},
        description =
            "Also ask the coach to explain the"
            + " attempts.")
    boolean explain;

    @Option(
        names = {"--demo"},
        description =
            "Use a prepared reply instead of"
            + " calling the model, even when a"
            + " key is configured.")
    boolean demo;

    @Option(
        names = {"--trace"},
        description =
            "Show every step the agent took.")
    boolean trace;

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

            Optional<MeetAttempts> calculated =
                controller.calculateAttempts(
                        lift);
            if (calculated.isEmpty()) {
                out.println("There is no usable"
                    + " history for this lift,"
                    + " so there is nothing to"
                    + " plan attempts from.");
                out.println(
                    "  Run: spotter seed");
                out.flush();
                return EXIT_NO_DATA;
            }

            MeetAttempts attempts =
                    calculated.get();
            Map<Lift, Double> current =
                    controller.currentE1RMs();
            double e1rm = current.get(lift);

            printAttempts(out, attempts, e1rm);

            if (!explain) {
                out.println();
                out.println("  No model was"
                    + " involved in any of the"
                    + " above. Add --explain to"
                    + " have the coach talk"
                    + " through it.");
                out.flush();
                return 0;
            }

            return explain(out, config,
                    controller, attempts, e1rm);
        }
    }

    private void printAttempts(
            PrintWriter out,
            MeetAttempts attempts,
            double e1rm) {
        out.printf("Meet attempts for %s%n",
                attempts.lift());
        out.println();
        out.printf(
            "  estimated max   %6.1f kg%n",
            e1rm);
        out.println();
        out.printf(
            "  opener          %6.1f kg   %.0f"
            + " percent%n",
            attempts.opener(),
            AttemptCalculator.OPENER_PCT * 100);
        out.printf(
            "  second          %6.1f kg   %.0f"
            + " percent%n",
            attempts.second(),
            AttemptCalculator.SECOND_PCT * 100);
        out.printf(
            "  third           %6.1f kg   %.0f"
            + " percent%n",
            attempts.third(),
            AttemptCalculator.THIRD_PCT * 100);
        out.println();
        out.printf(
            "  total jump      %6.1f kg%n",
            attempts.totalJump());
    }

    private Integer explain(
            PrintWriter out,
            SpotterConfig config,
            CoachController controller,
            MeetAttempts attempts,
            double e1rm) {

        ModelAccess.Access access =
            ModelAccess.open(config, demo,
                DemoReplies.attemptRationale(
                    attempts.opener(),
                    attempts.second(),
                    attempts.third()));

        out.println();
        out.println("  " + access.explanation());

        AttemptRationaleAgent agent =
            new AttemptRationaleAgent(
                access.client(),
                new PromptBuilder(), attempts,
                e1rm,
                controller.workouts().findAll(),
                null,
                new ResponseParser(
                        controller.catalog()),
                new PlanValidator());

        AgentResult<MeetAttempts> result =
                agent.run();

        if (trace) {
            out.println();
            out.println("  What the agent did");
            for (String step
                    : result.trace().steps()) {
                out.println("    " + step);
            }
        }

        out.println();
        if (!result.succeeded()) {
            out.println("The explanation was"
                + " refused.");
            out.println("  "
                + result.failureReason()
                    .orElse("unknown"));
            for (String problem
                    : agent.parseProblems()) {
                out.println("  " + problem);
            }
            out.println();
            out.println("  The attempts above"
                + " still stand. They were"
                + " calculated, not"
                + " generated.");
            out.flush();
            return EXIT_REFUSED;
        }

        out.println("What the coach says");
        out.println();
        out.println("  "
            + result.value().orElseThrow()
                .rationale());
        out.println();
        out.println("  Every figure in that"
            + " sentence was checked against the"
            + " calculated attempts before it"
            + " was shown.");
        out.flush();
        return 0;
    }
}
