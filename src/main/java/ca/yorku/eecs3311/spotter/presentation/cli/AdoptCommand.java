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
        names = {"--entry"},
        paramLabel = "LINE",
        description =
            "A programme line, such as"
            + " --entry \"1/2 Back Squat"
            + " 5x5@140 rpe8\". Repeatable,"
            + " and needs no model.")
    java.util.List<String> entries =
            new java.util.ArrayList<>();

    @Option(
        names = {"--csv"},
        description =
            "Read the file as a programme"
            + " spreadsheet rather than as"
            + " prose. Needs no model.")
    boolean csv;

    @Option(
        names = {"--set"},
        paramLabel = "FIELD=COLUMN",
        description =
            "Map one column of the"
            + " spreadsheet, such as --set"
            + " week=Week. Repeatable.")
    java.util.List<String> corrections =
            new java.util.ArrayList<>();

    @Option(
        names = {"--unit"},
        description =
            "KG or LB, for the weight"
            + " column.")
    ca.yorku.eecs3311.spotter.domain.WeightUnit
            unit;

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

        boolean deterministic =
                !entries.isEmpty() || csv;

        if (!deterministic
                && !config.hasApiKey()) {
            out.println("Adopting a programme as"
                + " prose needs a model.");
            out.println("  Interpreting prose"
                + " is the one thing here that"
                + " cannot be demonstrated with"
                + " a prepared reply.");
            out.println("  A programme you can"
                + " type or export needs no"
                + " model:");
            out.println("    spotter adopt"
                + " --entry \"1/1 Back Squat"
                + " 5x5@140\"");
            out.println("    spotter adopt"
                + " plan.csv --csv --set"
                + " week=Week --set"
                + " exercise=Lift");
            out.flush();
            return EXIT_NO_MODEL;
        }

        if (deterministic) {
            return adoptWithoutAModel(out,
                    config);
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

    /**
     * The two routes that need no model.
     *
     * A programme given as a spreadsheet, or
     * typed a line at a time, already has a
     * structure. Nothing needs interpreting, so
     * nothing is sent anywhere, and the same gate
     * runs afterwards as for the prose route.
     */
    private Integer adoptWithoutAModel(
            PrintWriter out,
            SpotterConfig config) {

        try (CoachController controller =
                new CoachController(config)) {

            LifterProfile profile = controller
                .profiles().current()
                .orElseGet(AdoptCommand
                        ::assumedProfile);
            LocalDate from = start == null
                    ? LocalDate.now()
                    : start;

            ca.yorku.eecs3311.spotter
                .infrastructure.csv
                .ProgrammeImportService reader =
                    new ca.yorku.eecs3311.spotter
                        .infrastructure.csv
                        .ProgrammeImportService(
                            controller.catalog());

            java.util.Optional<TrainingBlock>
                read;
            if (!entries.isEmpty()) {
                out.printf("Reading %d entered"
                        + " line(s).%n",
                        entries.size());
                read = reader.fromManualEntry(
                        entries, from);
            } else {
                if (file == null) {
                    out.println("Give the"
                        + " spreadsheet to"
                        + " read.");
                    out.flush();
                    return EXIT_NOTHING;
                }
                out.printf("Reading %s as a"
                        + " programme"
                        + " spreadsheet.%n",
                        file.getFileName());
                read = reader.fromCsv(file,
                        mappingFor(), from);
            }

            if (read.isEmpty()) {
                out.println();
                out.println("The programme could"
                        + " not be read.");
                for (String problem
                        : reader.problems()) {
                    out.println("  " + problem);
                }
                out.flush();
                return EXIT_REFUSED;
            }

            TrainingBlock block = read.get();

            java.util.Optional<String> refused =
                new PlanValidator()
                    .validateAdoptedBlock(block,
                        profile,
                        controller
                            .currentE1RMs());
            if (refused.isPresent()) {
                out.println();
                out.println("The programme was"
                        + " not accepted.");
                out.println("  "
                        + refused.get());
                out.flush();
                return EXIT_REFUSED;
            }

            if (!reader.problems().isEmpty()) {
                out.println();
                out.println("  Rows passed"
                        + " over");
                for (String problem
                        : reader.problems()) {
                    out.println("    "
                            + problem);
                }
            }

            out.println();
            print(out, block);

            if (keep) {
                TrainingBlock saved = controller
                        .blocks().save(block);
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
            out.println();
            out.println("  No model was involved"
                + " in any of the above.");
            out.flush();
        }
        return 0;
    }

    /** The mapping for a programme spreadsheet. */
    private ca.yorku.eecs3311.spotter
            .infrastructure.csv.ColumnMapping
                mappingFor() {

        ca.yorku.eecs3311.spotter
            .infrastructure.csv.ColumnMapping
                suggested =
                    ca.yorku.eecs3311.spotter
                        .infrastructure.csv
                        .ColumnMapping
                        .suggestFor(
                            ca.yorku.eecs3311
                                .spotter
                                .infrastructure
                                .csv
                                .CsvFileReader
                                .readHeader(
                                    file));

        java.util.Map<String, String> columns =
            new java.util.LinkedHashMap<>(
                suggested.columns());

        // A programme has columns a workout log
        // does not, so the suggestion is only a
        // starting point here.
        for (String correction : corrections) {
            int at = correction.indexOf('=');
            if (at <= 0) {
                continue;
            }
            columns.put(
                correction.substring(0, at)
                    .trim(),
                correction.substring(at + 1)
                    .trim());
        }

        return new ca.yorku.eecs3311.spotter
            .infrastructure.csv.ColumnMapping(
                columns,
                unit == null
                    ? suggested.unit()
                    : unit,
                null);
    }
}
