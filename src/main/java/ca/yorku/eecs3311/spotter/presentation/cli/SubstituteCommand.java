package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent.agents
        .SubstitutionAgent;
import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .Limitation;
import ca.yorku.eecs3311.spotter.domain
        .Substitution;
import ca.yorku.eecs3311.spotter.domain
        .SubstitutionReason;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import ca.yorku.eecs3311.spotter
        .presentation.support.DemoReplies;
import ca.yorku.eecs3311.spotter
        .presentation.support.ModelAccess;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F06: swaps one movement for another.
 *
 * The interesting part to watch is what the model
 * is not allowed to do. Whatever it answers, the
 * replacement must exist in the catalog, must be
 * performable with the equipment the lifter has,
 * must train something the original trained, and
 * must carry a believable load. The command
 * prints the filtered list the model was given,
 * so the narrowing is visible before the answer
 * arrives.
 */
@Command(
    name = "substitute",
    description =
        "Swap an exercise the lifter cannot do"
        + " today.")
public final class SubstituteCommand
        implements Callable<Integer> {

    /** Returned when the exercise is unknown. */
    public static final int EXIT_UNKNOWN = 3;

    /** Returned when no swap was accepted. */
    public static final int EXIT_REFUSED = 4;

    @Spec
    CommandSpec spec;

    @Parameters(
        index = "0",
        paramLabel = "EXERCISE",
        description =
            "The exercise that cannot be done,"
            + " as the catalog spells it.")
    String exerciseName;

    @Option(
        names = {"--sets"},
        paramLabel = "N",
        description = "Sets prescribed."
            + " Default 4.")
    int sets = 4;

    @Option(
        names = {"--reps"},
        paramLabel = "N",
        description = "Reps prescribed."
            + " Default 5.")
    int reps = 5;

    @Option(
        names = {"--load"},
        paramLabel = "KG",
        description = "Load prescribed, in"
            + " kilograms. Default 100.")
    double load = 100;

    @Option(
        names = {"--without"},
        paramLabel = "EQUIPMENT",
        split = ",",
        description =
            "Equipment the gym does not have"
            + " today, comma separated, such as"
            + " BARBELL,SQUAT_RACK.")
    List<Equipment> without = new ArrayList<>();

    @Option(
        names = {"--hurts"},
        description =
            "Swap because it hurts rather than"
            + " because of equipment.")
    boolean hurts;

    @Option(
        names = {"--avoid"},
        paramLabel = "PATTERN",
        description =
            "A movement pattern to avoid, such"
            + " as squat or hinge.")
    String avoidPattern;

    @Option(
        names = {"--note"},
        paramLabel = "TEXT",
        description =
            "What the lifter said about it.")
    String note;

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

            Optional<Exercise> original =
                controller.catalog()
                    .byName(exerciseName);
            if (original.isEmpty()) {
                out.println("There is no"
                    + " exercise called \""
                    + exerciseName + "\" in the"
                    + " catalog.");
                out.println("  Run: spotter"
                    + " seed");
                out.flush();
                return EXIT_UNKNOWN;
            }

            LifterProfile profile =
                    buildProfile();
            ExercisePrescription prescription =
                new ExercisePrescription(1,
                    original.get(), sets, reps,
                    null, load);

            List<Exercise> offered =
                    performable(controller,
                            profile);

            printHeader(out, prescription,
                    profile, offered);

            if (offered.isEmpty()) {
                out.println("Nothing in the"
                    + " catalog can be"
                    + " performed under these"
                    + " restrictions, so there"
                    + " is nothing to suggest.");
                out.flush();
                return EXIT_REFUSED;
            }

            ModelAccess.Access access =
                ModelAccess.open(config, demo,
                    DemoReplies.substitution(
                        pickSimilar(offered,
                            original.get()),
                        load));
            out.println("  "
                    + access.explanation());
            out.println();

            SubstitutionAgent agent =
                new SubstitutionAgent(
                    access.client(),
                    new PromptBuilder(), profile,
                    controller.catalog(),
                    prescription, reason(), note);

            AgentResult<Substitution> result =
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
                out.println("No swap was"
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

            printResult(out,
                    result.value().orElseThrow());
            out.flush();
        }
        return 0;
    }

    private SubstitutionReason reason() {
        if (hurts) {
            return SubstitutionReason
                    .PAIN_OR_DISCOMFORT;
        }
        if (avoidPattern != null
                && !avoidPattern.isBlank()) {
            return SubstitutionReason.LIMITATION;
        }
        return SubstitutionReason
                .EQUIPMENT_UNAVAILABLE;
    }

    /**
     * A full gym less whatever is unavailable
     * today, plus any pattern being avoided.
     */
    private LifterProfile buildProfile() {
        Set<Equipment> available =
                EnumSet.allOf(Equipment.class);
        available.removeAll(without);

        LifterProfile.Builder builder =
            LifterProfile.with(
                    TrainingGoal.STRENGTH,
                    ExperienceLevel.INTERMEDIATE)
                .equipment(available);

        if (avoidPattern != null
                && !avoidPattern.isBlank()) {
            builder.addLimitation(
                new Limitation(
                    "avoiding " + avoidPattern
                        + " today",
                    Set.of(avoidPattern),
                    Set.of(), hurts));
        }
        return builder.build();
    }

    private List<Exercise> performable(
            CoachController controller,
            LifterProfile profile) {
        List<Exercise> offered =
                new ArrayList<>();
        for (Exercise exercise
                : controller.catalog().all()) {
            if (profile.canPerform(exercise)) {
                offered.add(exercise);
            }
        }
        return offered;
    }

    /**
     * The first offered movement sharing the
     * original's pattern, for the prepared reply.
     * A real model weighs more than that.
     */
    private static Exercise pickSimilar(
            List<Exercise> offered,
            Exercise original) {
        for (Exercise candidate : offered) {
            if (candidate.equals(original)) {
                continue;
            }
            if (candidate.movementPattern()
                    .isPresent()
                    && original.movementPattern()
                        .isPresent()
                    && candidate
                        .movementPattern().get()
                        .equalsIgnoreCase(
                            original
                                .movementPattern()
                                .get())) {
                return candidate;
            }
        }
        for (Exercise candidate : offered) {
            if (!candidate.equals(original)) {
                return candidate;
            }
        }
        return offered.get(0);
    }

    private void printHeader(
            PrintWriter out,
            ExercisePrescription prescription,
            LifterProfile profile,
            List<Exercise> offered) {

        out.printf("Cannot do %s today%n",
            prescription.exercise().name());
        out.println();
        out.printf("  prescribed      %d x %d"
            + " @ %.1f kg%n",
            prescription.sets(),
            prescription.reps(),
            prescription.loadKg());
        prescription.exercise()
            .movementPattern()
            .ifPresent(pattern ->
                out.printf(
                    "  pattern         %s%n",
                    pattern));
        out.printf("  reason          %s%n",
                reason());
        if (!without.isEmpty()) {
            out.printf("  unavailable     %s%n",
                    without);
        }
        out.println();
        out.printf(
            "  The coach may only choose from"
            + " the %d movement(s) this lifter"
            + " can perform:%n",
            offered.size());
        for (Exercise exercise : offered) {
            out.println("    "
                    + exercise.name());
        }
        out.println();
    }

    private void printResult(
            PrintWriter out,
            Substitution substitution) {
        out.println("Accepted.");
        out.println();
        out.printf("  do instead      %s%n",
            substitution.replacement().name());
        out.printf("  at              %d x %d"
            + " @ %.1f kg%n",
            substitution.apply().sets(),
            substitution.apply().reps(),
            substitution.loadKg());
        substitution.rationale().ifPresent(why ->
            out.printf("  because         %s%n",
                    why));
        out.println();
        out.println("  Checked before being"
            + " shown: the movement exists, the"
            + " lifter can perform it, it trains"
            + " what the original trained, and"
            + " the load is believable for it.");
    }
}
