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
import ca.yorku.eecs3311.spotter.application
        .plan.ApplyProgressionCommand;
import ca.yorku.eecs3311.spotter.application
        .plan.PlanEditHistory;
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
        names = {"--json"},
        description =
            "Print the result as JSON instead of"
            + " prose, for a harness rather than"
            + " a person.")
    boolean json;

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
                if (json) {
                    JsonOutput.refusal(out,
                        "review",
                        "no block is being"
                        + " trained, so there"
                        + " is nothing to"
                        + " review");
                    return EXIT_NO_BLOCK;
                }
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

            if (!json) {
                report(out, block, adherence);
            }

            if (!revise) {
                if (json) {
                    JsonOutput.write(out,
                        "review", true,
                        JsonOutput.adherence(
                                adherence));
                    return 0;
                }
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
                if (json) {
                    JsonOutput.refusal(out,
                        "review",
                        "revising needs a"
                        + " model, and no API"
                        + " key is"
                        + " configured");
                    return EXIT_NO_MODEL;
                }
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

        if (json) {
            java.util.Map<String, Object>
                    value = new java.util
                        .LinkedHashMap<>();
            value.put("adherence",
                JsonOutput.adherence(
                        adherence));
            value.put("kept", false);
            if (result.succeeded()) {
                TrainingBlock accepted =
                    result.value()
                        .orElseThrow();
                value.put("revised",
                    JsonOutput.block(
                            accepted));
                if (keep) {
                    value.put("kept", true);
                    keepIt(controller, block,
                            accepted);
                }
            }
            JsonOutput.write(out, "review",
                    result, value, trace);
            return result.succeeded()
                ? 0 : EXIT_REFUSED;
        }

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
            out.println();
            out.printf("  Saved: %s. The"
                + " remaining weeks are now as"
                + " above.%n",
                keepIt(controller, block,
                        revised));
        } else {
            out.println();
            out.println("  Nothing was saved."
                + " Run again with --keep to"
                + " train it.");
        }
        out.flush();
        return 0;
    }

    /**
     * Saves a revision over the block it revises.
     *
     * Goes through the Command rather than
     * writing directly, so the one place that
     * rebuilds a block with its original
     * identity is also the place that knows how
     * to describe the change.
     *
     * @return what changed, for a person to read
     */
    private static String keepIt(
            CoachController controller,
            TrainingBlock block,
            TrainingBlock revised) {

        ApplyProgressionCommand change =
            new ApplyProgressionCommand(
                new TrainingBlock(
                    block.id(),
                    revised.startDate(),
                    revised.progression()
                        .orElse(null),
                    revised.splitName()
                        .orElse(null),
                    revised.origin(),
                    revised.weeks()));
        PlanEditHistory edits =
                new PlanEditHistory(block);
        TrainingBlock saved = controller
                .blocks().save(
                    edits.apply(change));
        controller.blocks().activate(
                saved.id());
        return change.describe();
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
