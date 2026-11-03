package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Scanner;
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
        .ProgrammeParserAgent;
import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F14: bring in a programme you already follow.
 *
 * Reads a file, or standard input, so a
 * programme can be pasted straight from a coach's
 * message.
 *
 * This is the one feature with no prepared reply.
 * The others stand in for a judgement and land
 * close enough to demonstrate the feature;
 * reading an arbitrary document is not something
 * keyword matching can fake, and pretending
 * otherwise would be worse than saying so.
 */
@Command(
    name = "adopt",
    description =
        "Adopt a programme you already follow,"
        + " from a file or pasted text.")
public final class AdoptCommand
        implements Callable<Integer> {

    /** Returned when there is nothing to read. */
    public static final int EXIT_NOTHING = 3;

    /** Returned when no block was accepted. */
    public static final int EXIT_REFUSED = 4;

    /** Returned when no model is configured. */
    public static final int EXIT_NO_MODEL = 5;

    @Spec
    CommandSpec spec;

    @Parameters(
        index = "0",
        arity = "0..1",
        paramLabel = "FILE",
        description =
            "A file holding the programme. Omit"
            + " it to paste the programme"
            + " instead.")
    Path file;

    @Option(
        names = {"--start"},
        paramLabel = "DATE",
        description =
            "First day of the block, as"
            + " YYYY-MM-DD. Default today.")
    LocalDate start;

    @Option(
        names = {"--keep"},
        description =
            "Save it as the block you are"
            + " training.")
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

        if (!config.hasApiKey()) {
            out.println("Adopting a programme"
                + " needs a model.");
            out.println("  Reading somebody"
                + " else's programme is the one"
                + " thing here that cannot be"
                + " demonstrated with a prepared"
                + " reply, so there is no"
                + " offline version of this"
                + " command.");
            out.println("  Put a key in .env and"
                + " try again.");
            out.flush();
            return EXIT_NO_MODEL;
        }

        String programme = read(out);
        if (programme == null
                || programme.isBlank()) {
            out.println("Nothing was given to"
                    + " read.");
            out.println("  Try: spotter adopt"
                    + " programme.txt");
            out.flush();
            return EXIT_NOTHING;
        }

        LocalDate from = start == null
                ? LocalDate.now()
                : start;

        try (CoachController controller =
                new CoachController(config)) {

            LifterProfile profile = controller
                .profiles().current()
                .orElseGet(AdoptCommand
                        ::assumedProfile);

            out.printf(
                "Reading a programme of %d"
                + " characters%n",
                programme.length());
            out.println();

            ProgrammeParserAgent agent =
                new ProgrammeParserAgent(
                    new ClaudeClient(config),
                    new PromptBuilder(),
                    programme, profile,
                    controller.catalog(),
                    controller.currentE1RMs(),
                    from,
                    new ResponseParser(
                        controller.catalog()),
                    new PlanValidator());

            AgentResult<TrainingBlock> result =
                    agent.run();

            if (trace) {
                out.println("  What the agent"
                        + " did");
                for (String step
                        : result.trace()
                            .steps()) {
                    out.println("    " + step);
                }
                out.println();
            }

            if (!result.succeeded()) {
                out.println("The programme could"
                    + " not be adopted.");
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

            TrainingBlock block =
                    result.value().orElseThrow();
            print(out, block);

            if (keep) {
                TrainingBlock saved =
                    controller.blocks()
                        .save(block);
                controller.blocks().activate(
                        saved.id());
                out.println();
                out.println("  Saved. This is"
                    + " now the block you are"
                    + " training.");
            } else {
                out.println();
                out.println("  Nothing was"
                    + " saved. Run again with"
                    + " --keep to train it.");
            }
            out.flush();
        }
        return 0;
    }

    /**
     * A stand in for a lifter who has not
     * onboarded, so adopting works before a
     * profile exists.
     */
    private static LifterProfile
            assumedProfile() {
        return LifterProfile
            .with(TrainingGoal.STRENGTH,
                ExperienceLevel.INTERMEDIATE)
            .trainingDaysPerWeek(7)
            .equipment(EnumSet.allOf(
                    Equipment.class))
            .build();
    }

    /** The programme, from a file or pasted in. */
    private String read(PrintWriter out) {
        if (file != null) {
            try {
                return Files.readString(file,
                    StandardCharsets.UTF_8);
            } catch (IOException e) {
                out.println("Could not read "
                    + file + ": "
                    + e.getMessage());
                return null;
            }
        }

        out.println("Paste the programme, then"
            + " press control D.");
        out.println();
        out.flush();

        StringBuilder pasted =
                new StringBuilder();
        try (Scanner scanner =
                new Scanner(System.in,
                    StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                pasted.append(scanner.nextLine())
                      .append('\n');
            }
        }
        return pasted.toString();
    }

    private void print(PrintWriter out,
                       TrainingBlock block) {
        out.println("Adopted. Every movement"
            + " below exists in the catalogue"
            + " and can be performed.");
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
                        "      %-28s %d x %d"
                        + "%s%n",
                        p.exercise().name(),
                        p.sets(), p.reps(),
                        p.loadKg() > 0
                            ? String.format(
                                " @ %.1f kg",
                                p.loadKg())
                            : " at your own"
                                + " weight");
                }
            }
        }
    }
}
