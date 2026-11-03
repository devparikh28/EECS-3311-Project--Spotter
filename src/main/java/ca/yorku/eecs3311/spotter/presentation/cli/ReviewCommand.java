package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent
        .ClaudeClient;
import ca.yorku.eecs3311.spotter.agent
        .PlanValidator;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent.agents
        .ProgressionAgent;
import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .PlanAdherence;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategyFactory;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F12: how the block is going, and what the rest
 * of it should be.
 *
 * Run plain it reports adherence and nothing
 * else, which needs no model and is often the
 * only answer wanted. With --revise it asks the
 * coach to rewrite the remaining weeks in light
 * of those figures.
 */
@Command(
    name = "review",
    description =
        "Report how closely the block was"
        + " trained, and optionally revise the"
        + " rest of it.")
public final class ReviewCommand
        implements Callable<Integer> {

    /** Returned when no block is being trained. */
    public static final int EXIT_NO_BLOCK = 3;

    /** Returned when no revision was accepted. */
    public static final int EXIT_REFUSED = 4;

    /** Returned when a revision needs a model. */
    public static final int EXIT_NO_MODEL = 5;

    @Spec
    CommandSpec spec;

    @Option(
        names = {"--revise"},
        description =
            "Ask the coach to revise the"
            + " remaining weeks.")
    boolean revise;

    @Option(
        names = {"--keep"},
        description =
            "Save the revision as the block you"
            + " are training.")
    boolean keep;

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

            TrainingBlock block = controller
                    .blocks().active()
                    .orElse(null);
            if (block == null) {
                out.println("No block is being"
                    + " trained, so there is"
                    + " nothing to review.");
                out.println("  Run: spotter"
                        + " plan");
                out.flush();
                return EXIT_NO_BLOCK;
            }

            PlanAdherence adherence =
                PlanAdherence.of(block,
                    controller.workouts()
                        .findAll(),
                    LocalDate.now());

            report(out, block, adherence);

            if (!revise) {
                out.println();
                out.println("  No model was"
                    + " involved in any of the"
                    + " above. Add --revise to"
                    + " have the coach rewrite"
                    + " the remaining weeks.");
                out.flush();
                return 0;
            }

            if (!config.hasApiKey()) {
                out.println();
                out.println("Revising needs a"
                    + " model, and no API key is"
                    + " configured.");
                out.println("  The figures above"
                    + " stand. They were"
                    + " measured, not"
                    + " generated.");
                out.flush();
                return EXIT_NO_MODEL;
            }

            return reviseWith(out, config,
                    controller, block,
                    adherence);
        }
    }

    private void report(
            PrintWriter out,
            TrainingBlock block,
            PlanAdherence adherence) {

        out.printf("Review of the block started"
                + " %s%n", block.startDate());
        out.println();

        if (adherence.isEmpty()) {
            out.println("  No sessions have come"
                + " due yet, so there is nothing"
                + " to judge.");
            return;
        }

        out.printf(
            "  sessions        %d of %d"
            + " trained%n",
            adherence.completed(),
            adherence.planned());
        out.printf(
            "  work done       %.0f kg of"
            + " %.0f kg planned%n",
            adherence.actualTonnage(),
            adherence.plannedTonnage());
        out.printf(
            "  adherence       %.0f percent of"
            + " the work, %.0f percent of the"
            + " sessions%n",
            adherence.tonnageRate() * 100,
            adherence.sessionRate() * 100);

        out.println();
        adherence.plannedTopLoads().forEach(
            (lift, planned) -> out.printf(
                "  %-9s heaviest planned %.1f"
                + " kg, heaviest done %.1f kg%n",
                lift, planned,
                adherence.actualTopLoads()
                    .getOrDefault(lift, 0.0)));

        if (!adherence.liftsShortOfPlan()
                .isEmpty()) {
            out.println();
            out.printf(
                "  Short of plan: %s. Loads for"
                + " these will not be allowed to"
                + " advance.%n",
                adherence.liftsShortOfPlan());
        }
    }

    private Integer reviseWith(
            PrintWriter out,
            SpotterConfig config,
            CoachController controller,
            TrainingBlock block,
            PlanAdherence adherence) {

        ProgressionStrategy progression =
            new ProgressionStrategyFactory()
                .forGoal(goalOf(controller),
                    experienceOf(controller));

        out.println();
        out.println("  Calling the configured"
                + " model.");
        out.println();

        ProgressionAgent agent =
            new ProgressionAgent(
                new ClaudeClient(config),
                new PromptBuilder(), block,
                adherence, progression,
                controller.currentE1RMs(),
                controller.workouts().findAll(),
                new ResponseParser(
                        controller.catalog()),
                new PlanValidator());

        AgentResult<TrainingBlock> result =
                agent.run();

        if (trace) {
            out.println("  What the agent did");
            for (String step
                    : result.trace().steps()) {
                out.println("    " + step);
            }
            out.println();
        }

        if (!result.succeeded()) {
            out.println("No revision was"
                    + " accepted.");
            out.println("  "
                + result.failureReason()
                    .orElse("unknown"));
            for (String problem
                    : agent.parseProblems()) {
                out.println("  " + problem);
            }
            out.println();
            out.println("  The block you are on"
                + " is unchanged.");
            out.flush();
            return EXIT_REFUSED;
        }

        TrainingBlock revised =
                result.value().orElseThrow();
        printRevision(out, block, revised);

        if (keep) {
            TrainingBlock saved = controller
                    .blocks().save(
                        new TrainingBlock(
                            block.id(),
                            revised.startDate(),
                            revised.progression()
                                .orElse(null),
                            revised.splitName()
                                .orElse(null),
                            revised.origin(),
                            revised.weeks()));
            controller.blocks().activate(
                    saved.id());
            out.println();
            out.println("  Saved. The remaining"
                + " weeks are now as above.");
        } else {
            out.println();
            out.println("  Nothing was saved."
                + " Run again with --keep to"
                + " train it.");
        }
        out.flush();
        return 0;
    }

    private static TrainingGoal goalOf(
            CoachController controller) {
        return controller.profiles().current()
            .map(profile -> profile.goal())
            .orElse(TrainingGoal.STRENGTH);
    }

    private static ExperienceLevel experienceOf(
            CoachController controller) {
        return controller.profiles().current()
            .map(profile ->
                    profile.experience())
            .orElse(ExperienceLevel
                    .INTERMEDIATE);
    }

    /** Only what changed, so the eye can find it. */
    private void printRevision(
            PrintWriter out,
            TrainingBlock before,
            TrainingBlock after) {

        out.println("Accepted. Nothing was"
            + " dropped, nothing short of plan"
            + " was given more, and no load"
            + " rose by more than a week's"
            + " worth.");
        out.println();

        int changes = 0;
        for (Week week : after.weeks()) {
            for (Session session
                    : week.sessions()) {
                for (ExercisePrescription now
                        : session
                            .prescriptions()) {
                    Double was = loadIn(before,
                        session.date(),
                        now.exercise().name());
                    if (was == null
                            || Math.abs(was
                                - now.loadKg())
                                < 0.05) {
                        continue;
                    }
                    out.printf(
                        "  %s  %-24s %.1f kg"
                        + " -> %.1f kg%n",
                        session.date(),
                        now.exercise().name(),
                        was, now.loadKg());
                    changes++;
                }
            }
        }

        if (changes == 0) {
            out.println("  Nothing changed. The"
                + " plan as written is still"
                + " the right one.");
        }
    }

    private static Double loadIn(
            TrainingBlock block,
            LocalDate date,
            String exercise) {
        for (Session session
                : block.allSessions()) {
            if (!session.date().equals(date)) {
                continue;
            }
            for (ExercisePrescription p
                    : session.prescriptions()) {
                if (p.exercise().name()
                        .equals(exercise)) {
                    return p.loadKg();
                }
            }
        }
        return null;
    }
}
