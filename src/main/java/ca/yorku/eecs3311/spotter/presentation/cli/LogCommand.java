package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain.SetEntry;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F02: recording training without an export.
 *
 * Two ways in, because there are two situations.
 * A lifter who did something not in the plan
 * types it. A lifter who did the plan should not
 * have to type it at all: --as-prescribed takes
 * today's session and records it as performed,
 * which is one command rather than a dozen.
 *
 * The second is the one that gets used. Typing
 * out a session you have just completed, exactly
 * as it was written down for you, is the kind of
 * work that makes people stop logging.
 */
@Command(
    name = "log",
    description =
        "Record training by hand, or record"
        + " today's planned session as done.")
public final class LogCommand
        implements Callable<Integer> {

    /** Returned when there is nothing to record. */
    public static final int EXIT_NOTHING = 3;

    /** Returned when an exercise is unknown. */
    public static final int EXIT_UNKNOWN = 4;

    @Spec
    CommandSpec spec;

    @Option(
        names = {"--as-prescribed"},
        description =
            "Record the planned session as"
            + " performed, as written.")
    boolean asPrescribed;

    @Option(
        names = {"--exercise"},
        paramLabel = "NAME",
        description =
            "The movement, as the catalogue"
            + " spells it.")
    String exercise;

    @Option(
        names = {"--sets"},
        paramLabel = "N",
        description = "Sets performed."
            + " Default 1.")
    int sets = 1;

    @Option(
        names = {"--reps"},
        paramLabel = "N",
        description = "Reps in each set.")
    int reps;

    @Option(
        names = {"--weight"},
        paramLabel = "KG",
        description = "Weight in kilograms.")
    double weight;

    @Option(
        names = {"--rpe"},
        paramLabel = "N",
        description =
            "Effort, 1 to 10. Optional.")
    Double rpe;

    @Option(
        names = {"--date"},
        paramLabel = "DATE",
        description =
            "The day trained, as YYYY-MM-DD."
            + " Default today.")
    LocalDate date;

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
        LocalDate when = date == null
                ? LocalDate.now()
                : date;

        try (CoachController controller =
                new CoachController(config)) {

            return asPrescribed
                ? logPlanned(out, controller,
                        when)
                : logTyped(out, controller,
                        when);
        }
    }

    /**
     * Today's planned session, recorded as done.
     *
     * Deliberately records what was prescribed
     * rather than asking what was deviated from.
     * A lifter who changed something can log that
     * set by hand afterwards; a lifter who did
     * the plan is finished in one command.
     */
    private Integer logPlanned(
            PrintWriter out,
            CoachController controller,
            LocalDate when) {

        TrainingBlock block = controller
                .blocks().active().orElse(null);
        if (block == null) {
            out.println("No block is being"
                + " trained, so there is nothing"
                + " prescribed to record.");
            out.flush();
            return EXIT_NOTHING;
        }

        Session planned = block.allSessions()
            .stream()
            .filter(each -> each.date()
                    .equals(when))
            .findFirst()
            .orElse(null);
        if (planned == null) {
            out.printf("Nothing was planned for"
                    + " %s.%n", when);
            out.flush();
            return EXIT_NOTHING;
        }

        List<SetEntry> performed =
                new ArrayList<>();
        for (ExercisePrescription p
                : planned.prescriptions()) {
            for (int i = 0; i < p.sets(); i++) {
                performed.add(
                    p.targetRpe().isPresent()
                        ? SetEntry.of(
                            p.exercise(),
                            p.loadKg(), p.reps(),
                            p.targetRpe().get())
                        : SetEntry.withoutRpe(
                            p.exercise(),
                            p.loadKg(),
                            p.reps()));
            }
        }

        controller.workouts().save(
            new WorkoutSession(0, when,
                "as prescribed",
                "prescribed-" + when, null,
                performed));

        out.printf("Recorded %s as performed.%n",
                when);
        out.println();
        for (ExercisePrescription p
                : planned.prescriptions()) {
            out.printf("  %-24s %d x %d @ %.1f"
                    + " kg%n",
                p.exercise().name(), p.sets(),
                p.reps(), p.loadKg());
        }
        out.println();
        out.printf("  %d sets logged. Change"
            + " anything that went differently"
            + " with spotter log"
            + " --exercise ...%n",
            performed.size());
        out.flush();
        return 0;
    }

    /** One movement, typed in. */
    private Integer logTyped(
            PrintWriter out,
            CoachController controller,
            LocalDate when) {

        if (exercise == null
                || exercise.isBlank()
                || reps <= 0) {
            out.println("Nothing was given to"
                    + " record.");
            out.println("  Try: spotter log"
                + " --exercise \"Back Squat\""
                + " --sets 5 --reps 5"
                + " --weight 140 --rpe 8");
            out.println("  Or:  spotter log"
                    + " --as-prescribed");
            out.flush();
            return EXIT_NOTHING;
        }

        Optional<Exercise> found =
            controller.catalog()
                .byName(exercise);
        if (found.isEmpty()) {
            out.printf("There is no exercise"
                    + " called \"%s\".%n",
                    exercise);
            List<Exercise> close =
                controller.catalog()
                    .closestMatches(exercise, 5);
            if (!close.isEmpty()) {
                out.println("  Did you mean:");
                for (Exercise candidate
                        : close) {
                    out.println("    "
                        + candidate.name());
                }
            }
            out.flush();
            return EXIT_UNKNOWN;
        }

        List<SetEntry> performed =
                new ArrayList<>();
        for (int i = 0; i < Math.max(1, sets);
                i++) {
            performed.add(rpe == null
                ? SetEntry.withoutRpe(
                    found.get(), weight, reps)
                : SetEntry.of(found.get(),
                    weight, reps, rpe));
        }

        controller.workouts().save(
            new WorkoutSession(0, when, "manual",
                "manual-" + when + "-"
                    + found.get().name(),
                null, performed));

        out.printf("Recorded on %s.%n", when);
        out.println();
        out.printf("  %-24s %d x %d @ %.1f kg%s%n",
            found.get().name(),
            Math.max(1, sets), reps, weight,
            rpe == null
                ? ""
                : String.format(" at RPE %.1f",
                        rpe));
        out.flush();
        return 0;
    }
}
