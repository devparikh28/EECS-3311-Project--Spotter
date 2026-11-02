package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent.agents
        .AdjustmentAgent;
import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategyFactory;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryRuleEngine;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F07: reduces today's session after poor
 * recovery.
 *
 * Uses the lifter's real readings. Nothing is
 * adjusted unless the rule engine flagged a day,
 * which is the point: the decision to act is
 * deterministic and only the shape of the
 * reduction is the coach's.
 *
 * Today's session is built from the current
 * estimated maxima at a load the progression
 * allows, since there is no stored block yet.
 * That part involves no model and is labelled as
 * planned rather than suggested.
 */
@Command(
    name = "adjust",
    description =
        "Reduce today's session after poor"
        + " recovery.")
public final class AdjustCommand
        implements Callable<Integer> {

    /** Returned when there is no history. */
    public static final int EXIT_NO_DATA = 3;

    /** Returned when no reduction was accepted. */
    public static final int EXIT_REFUSED = 4;

    /** Sets in the session built for today. */
    public static final int SETS = 4;

    /** Reps in the session built for today. */
    public static final int REPS = 5;

    /** Effort in the session built for today. */
    public static final double RPE = 8;

    @Spec
    CommandSpec spec;

    @Option(
        names = {"--weeks"},
        paramLabel = "N",
        description =
            "How far back to look for a flagged"
            + " day. Default 2.")
    int weeks = 2;

    @Option(
        names = {"--fallback"},
        description =
            "Skip the coach and use the rule"
            + " engine's own reduction.")
    boolean fallback;

    @Option(
        names = {"--demo"},
        description =
            "Use a prepared reply instead of"
            + " calling the model.")
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

            Map<Lift, Double> current =
                    controller.currentE1RMs();
            if (current.isEmpty()) {
                out.println("There is no"
                    + " training history, so"
                    + " there is no session to"
                    + " adjust.");
                out.println("  Run: spotter"
                    + " seed");
                out.flush();
                return EXIT_NO_DATA;
            }

            LocalDate since = LocalDate.now()
                    .minusWeeks(weeks);
            List<RecoveryFlag> flags =
                controller.recoveryFlags(since);

            Session planned = buildSession(
                    controller, current);
            printSession(out,
                    "Today's session as planned",
                    planned);

            if (flags.isEmpty()) {
                out.println();
                out.println("Nothing in the last"
                    + " " + weeks + " week(s)"
                    + " was flagged, so the"
                    + " session stands as"
                    + " written.");
                out.println("  The decision to"
                    + " adjust is the rule"
                    + " engine's, not the"
                    + " coach's.");
                out.flush();
                return 0;
            }

            RecoveryFlag flag = flags.get(
                    flags.size() - 1);
            out.println();
            out.printf("Flagged on %s, %s%n",
                flag.date(),
                flag.isSignificant()
                    ? "significantly"
                    : "mildly");
            out.println("  " + flag.summary());
            out.println();

            RecoveryRuleEngine rules =
                    new RecoveryRuleEngine();

            if (fallback) {
                printSession(out,
                    "The rule engine's own"
                    + " reduction, with no model"
                    + " involved",
                    rules.fallbackAdjustment(
                            planned));
                out.flush();
                return 0;
            }

            List<RecoveryDay> readings =
                controller.recovery()
                    .findSince(since);

            ModelAccess.Access access =
                ModelAccess.open(config, demo,
                    DemoReplies.adjustment(
                            planned));
            out.println("  "
                    + access.explanation());
            out.println();

            AdjustmentAgent agent =
                new AdjustmentAgent(
                    access.client(),
                    new PromptBuilder(), planned,
                    flag, readings, rules,
                    new ResponseParser(
                        controller.catalog()));

            AgentResult<Session> result =
                    agent.run();

            if (trace) {
                out.println(
                        "  What the agent did");
                for (String step
                        : result.trace()
                            .steps()) {
                    out.println("    " + step);
                }
                out.println();
            }

            if (!result.succeeded()) {
                out.println("The coach's"
                    + " reduction was refused.");
                out.println("  "
                    + result.failureReason()
                        .orElse("unknown"));
                out.println();
                printSession(out,
                    "Falling back to the rule"
                    + " engine, so the lifter"
                    + " still trains",
                    agent.fallback());
                out.flush();
                return EXIT_REFUSED;
            }

            Session adjusted =
                    result.value().orElseThrow();
            printSession(out,
                    "Accepted reduction",
                    adjusted);
            out.println();
            out.printf(
                "  planned %.0f kg, reduced to"
                + " %.0f kg, which is %.0f"
                + " percent%n",
                planned.plannedTonnage(),
                adjusted.plannedTonnage(),
                adjusted.plannedTonnage()
                    / planned.plannedTonnage()
                    * 100);
            adjusted.adjustmentNote().ifPresent(
                note -> out.println("  " + note));
            out.println();
            out.println("  Checked before being"
                + " shown: nothing was added,"
                + " nothing went up, and the"
                + " session is easier without"
                + " being gutted.");
            out.flush();
        }
        return 0;
    }

    /**
     * Today's session, built from what the
     * lifter can currently do. No model is
     * involved in this part.
     */
    private Session buildSession(
            CoachController controller,
            Map<Lift, Double> current) {

        ProgressionStrategy progression =
            new ProgressionStrategyFactory()
                .forGoal(TrainingGoal.STRENGTH,
                    ExperienceLevel.INTERMEDIATE);

        List<ExercisePrescription>
            prescriptions = new ArrayList<>();
        int id = 1;

        for (Lift lift : Lift.values()) {
            Double e1rm = current.get(lift);
            if (e1rm == null) {
                continue;
            }
            Exercise exercise = controller
                .catalog().forLift(lift)
                .stream().findFirst()
                .orElse(null);
            if (exercise == null) {
                continue;
            }
            prescriptions.add(
                new ExercisePrescription(id++,
                    exercise, SETS, REPS, RPE,
                    progression.loadRange(e1rm,
                            REPS, RPE, 1)
                        .midpoint()));
        }
        return new Session(1, LocalDate.now(),
                "full body", prescriptions);
    }

    private void printSession(
            PrintWriter out,
            String heading,
            Session session) {
        out.println(heading);
        out.println();
        for (ExercisePrescription p
                : session.prescriptions()) {
            out.printf(
                "  %-22s %d x %d @ %.1f kg%n",
                p.exercise().name(), p.sets(),
                p.reps(), p.loadKg());
        }
        out.printf("  %-22s %.0f kg%n",
                "planned total",
                session.plannedTonnage());
        if (session.wasAdjusted()
                && heading.startsWith(
                        "The rule engine")) {
            out.println();
            out.println("  "
                + session.adjustmentNote()
                    .orElse(""));
        }
    }
}
