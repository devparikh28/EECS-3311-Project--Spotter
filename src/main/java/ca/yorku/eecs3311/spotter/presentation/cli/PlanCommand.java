package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent.agents
        .BlockGenerationAgent;
import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategyFactory;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F05: writes a training block.
 *
 * The first command that involves a model. What
 * it prints is deliberately more than the plan:
 * the agent's trace, including every tool it
 * called and every rejection, because the point
 * of the design is that a plan is checked before
 * it is shown and that is only visible if the
 * checking is visible.
 */
@Command(
    name = "plan",
    description =
        "Write a training block with the coach.")
public final class PlanCommand
        implements Callable<Integer> {

    /** Returned when there is no history. */
    public static final int EXIT_NO_DATA = 3;

    /** Returned when no acceptable plan came. */
    public static final int EXIT_REFUSED = 4;

    @Spec
    CommandSpec spec;

    @Option(
        names = {"--weeks"},
        paramLabel = "N",
        description =
            "Length of the block. Default 4.")
    int weeks = 4;

    @Option(
        names = {"--days"},
        paramLabel = "N",
        description =
            "Training days a week. Default 3.")
    int days = 3;

    @Option(
        names = {"--goal"},
        description =
            "STRENGTH, HYPERTROPHY or"
            + " GENERAL_FITNESS.")
    TrainingGoal goal =
            TrainingGoal.STRENGTH;

    @Option(
        names = {"--experience"},
        description =
            "NOVICE, INTERMEDIATE or ADVANCED.")
    ExperienceLevel experience =
            ExperienceLevel.INTERMEDIATE;

    @Option(
        names = {"--start"},
        paramLabel = "DATE",
        description =
            "First day of the block, as"
            + " YYYY-MM-DD. Default today.")
    LocalDate start;

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
        LocalDate from = start == null
                ? LocalDate.now()
                : start;

        try (CoachController controller =
                new CoachController(config)) {

            Map<Lift, Double> current =
                    controller.currentE1RMs();
            if (current.isEmpty()) {
                out.println(
                    "There is no training"
                    + " history to plan from.");
                out.println(
                    "  Run: spotter seed");
                out.flush();
                return EXIT_NO_DATA;
            }

            LifterProfile profile =
                    LifterProfile
                        .with(goal, experience)
                        .trainingDaysPerWeek(
                                days)
                        .equipment(
                                fullGymEquipment())
                        .build();

            ProgressionStrategy progression =
                new ProgressionStrategyFactory()
                    .forGoal(goal, experience);

            List<WorkoutSession> history =
                    controller.workouts()
                        .findAll();

            ModelAccess.Access access =
                ModelAccess.open(config, demo,
                    DemoReplies.block(current,
                        progression, from,
                        weeks, days));

            printHeader(out, profile,
                    progression, current,
                    access);

            BlockGenerationAgent agent =
                new BlockGenerationAgent(
                    access.client(),
                    new PromptBuilder(), profile,
                    controller.catalog(),
                    history, progression, from,
                    weeks);

            AgentResult<TrainingBlock> result =
                    agent.run();

            if (trace) {
                printTrace(out, result);
            }

            if (!result.succeeded()) {
                out.println();
                out.println("No plan was"
                    + " accepted.");
                out.println("  "
                    + result.failureReason()
                        .orElse("unknown"));
                for (String problem
                        : agent.parseProblems()) {
                    out.println("  " + problem);
                }
                out.flush();
                return EXIT_REFUSED;
            }

            printBlock(out,
                    result.value().orElseThrow());
            out.flush();
        }
        return 0;
    }

    /**
     * What a commercial gym has. A stored profile
     * replaces this once onboarding lands; until
     * then assuming a full gym is the choice that
     * rules out the fewest exercises.
     */
    private static Set<Equipment>
            fullGymEquipment() {
        return Set.of(Equipment.values());
    }

    private void printHeader(
            PrintWriter out,
            LifterProfile profile,
            ProgressionStrategy progression,
            Map<Lift, Double> current,
            ModelAccess.Access access) {

        out.println("Writing a " + weeks
            + " week block");
        out.println();
        out.printf("  goal          %s%n",
                profile.goal());
        out.printf("  experience    %s%n",
                profile.experience());
        out.printf("  progression   %s%n",
                progression.describe());
        out.println();
        out.println("  Current estimated maxima,"
            + " which the coach is given as"
            + " fact");
        current.forEach((lift, e1rm) ->
            out.printf("    %-9s %6.1f kg%n",
                    lift, e1rm));
        out.println();
        out.println("  " + access.explanation());
        out.println();
    }

    private void printTrace(
            PrintWriter out,
            AgentResult<TrainingBlock> result) {
        out.println("  What the agent did");
        for (String step
                : result.trace().steps()) {
            out.println("    " + step);
        }
        out.println();
    }

    private void printBlock(
            PrintWriter out,
            TrainingBlock block) {
        out.println("Accepted. Every load below"
            + " was checked against the"
            + " progression before being"
            + " shown.");
        out.println();
        block.splitName().ifPresent(name ->
            out.println("  " + name));

        for (Week week : block.weeks()) {
            out.println();
            out.println("  Week "
                    + week.number());
            for (Session session
                    : week.sessions()) {
                out.printf("    %s  %s%n",
                    session.date(),
                    session.focus()
                        .orElse("training"));
                for (ExercisePrescription p
                        : session
                            .prescriptions()) {
                    out.printf(
                        "      %-22s %d x %d"
                        + " @ %.1f kg%n",
                        p.exercise().name(),
                        p.sets(), p.reps(),
                        p.loadKg());
                }
            }
        }
    }
}
