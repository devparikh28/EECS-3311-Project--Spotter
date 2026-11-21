package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import ca.yorku.eecs3311.spotter.agent
        .AgentResult;
import ca.yorku.eecs3311.spotter.agent
        .ConversationHistory;
import ca.yorku.eecs3311.spotter.agent
        .MemoryManager;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent.agents
        .ChatAgent;
import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.domain
        .ChatReply;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteMemoryRepository;
import ca.yorku.eecs3311.spotter
        .presentation.support.ModelAccess;
import ca.yorku.eecs3311.spotter
        .presentation.support.PreparedChat;
import ca.yorku.eecs3311.spotter
        .presentation.support.UsageReport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * F11 from the terminal: ask the coach a question.
 *
 * The question comes from whichever source suits
 * the caller, which is what makes the agent
 * scriptable. It can be typed after the command,
 * read from a file with --input, or piped in on
 * standard input (with --input - or with nothing
 * at all when input is being piped). A test
 * harness uses the file or the pipe, a person
 * uses the argument.
 *
 * It is the same agent the Coach screen runs,
 * with the same lookups and the same memory, so
 * an answer given here is one the window would
 * have given.
 */
@Command(
    name = "ask",
    description =
        "Ask the coach a question. The question"
        + " can be typed, read from a file with"
        + " --input, or piped in on standard"
        + " input.")
public final class AskCommand
        implements Callable<Integer> {

    /** Returned when there is no question. */
    public static final int EXIT_NO_QUESTION = 3;

    /** Returned when the coach gave no answer. */
    public static final int EXIT_REFUSED = 4;

    /** Returned when the input file cannot be read. */
    public static final int EXIT_UNREADABLE = 5;

    @Spec
    CommandSpec spec;

    @Parameters(
        arity = "0..1",
        paramLabel = "QUESTION",
        description =
            "The question, in quotes.")
    String question;

    @Option(
        names = {"-i", "--input"},
        paramLabel = "FILE",
        description =
            "Read the question from this file."
            + " Use - to read standard input.")
    String input;

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

    /** Where standard input is read from. */
    InputStream stdin = System.in;

    /**
     * Whether standard input is being piped. A
     * terminal has nothing to read, and waiting on
     * it would look like a hang.
     */
    boolean stdinPiped = System.console() == null;

    @Override
    public Integer call() {
        PrintWriter out =
                spec.commandLine().getOut();

        String asked;
        try {
            asked = readQuestion();
        } catch (IOException problem) {
            return fail(out, EXIT_UNREADABLE,
                "could not read the question: "
                + problem.getMessage());
        }
        if (asked == null || asked.isBlank()) {
            return fail(out, EXIT_NO_QUESTION,
                "there is no question to answer."
                + " Type one after the command,"
                + " or use --input FILE, or pipe"
                + " it in.");
        }
        asked = asked.strip();

        SpotterConfig config =
                SpotterConfig.load(envFile);

        try (CoachController controller =
                new CoachController(config);
             Database database = Database.atPath(
                    config.databasePath())) {
            database.migrate();

            MemoryManager memory =
                new MemoryManager(
                    new SqliteMemoryRepository(
                            database));

            TrainingBlock block =
                controller.blocks().active()
                    .orElse(null);
            List<RecoveryFlag> flags =
                controller.recoveryFlags(
                    LocalDate.now()
                        .minusWeeks(2));

            ModelAccess.Access access =
                ModelAccess.open(config, demo,
                    PreparedChat.of(
                        controller.currentE1RMs(),
                        flags, today(block)));
            if (!json) {
                out.println("Asking the coach");
                out.println();
                out.println("  question  "
                        + asked);
                out.println("  "
                        + access.explanation());
                out.println();
            }

            ChatAgent agent = new ChatAgent(
                access.client(),
                new PromptBuilder(), asked,
                new ConversationHistory(),
                memory,
                controller.profiles().current()
                    .orElse(null),
                block,
                controller.workouts().findAll(),
                controller.recovery().findSince(
                    LocalDate.now()
                        .minusWeeks(4)),
                controller.foods(),
                new ResponseParser(
                    controller.catalog(),
                    controller.foods()));

            AgentResult<ChatReply> result =
                    agent.run();

            Optional<String> kept =
                result.succeeded()
                    ? agent.remember(
                        result.value()
                            .orElseThrow())
                    : Optional.empty();

            if (json) {
                Map<String, Object> value = null;
                if (result.succeeded()) {
                    value = new LinkedHashMap<>();
                    value.put("question", asked);
                    value.put("reply", result
                        .value().orElseThrow()
                        .reply());
                    value.put("remembered",
                        kept.orElse(null));
                }
                JsonOutput.write(out, "ask",
                        result, value, trace);
                return result.succeeded()
                    ? 0 : EXIT_REFUSED;
            }

            if (trace) {
                out.println(
                        "  What the agent did");
                for (String step
                        : result.trace()
                            .steps()) {
                    out.println("    " + step);
                }
                out.println("    " + UsageReport
                    .describe(result.trace(),
                            config));
                out.println();
            }

            if (!result.succeeded()) {
                out.println("The coach gave no"
                        + " answer.");
                out.println("  " + result
                    .failureReason()
                    .orElse("unknown"));
                out.flush();
                return EXIT_REFUSED;
            }

            out.println("Coach");
            out.println();
            out.println("  " + result.value()
                    .orElseThrow().reply());
            kept.ifPresent(note -> {
                out.println();
                out.println("  remembered  "
                        + note);
            });
            out.flush();
        }
        return 0;
    }

    /**
     * Chooses where the question comes from. A
     * file or a dash wins over the argument, and
     * the argument wins over a pipe that happens to
     * be open.
     */
    private String readQuestion()
            throws IOException {
        if (input != null) {
            if (input.equals("-")) {
                return readAll(stdin);
            }
            return Files.readString(
                Path.of(input),
                StandardCharsets.UTF_8);
        }
        if (question != null
                && !question.isBlank()) {
            return question;
        }
        if (stdinPiped) {
            return readAll(stdin);
        }
        return null;
    }

    private static String readAll(InputStream in)
            throws IOException {
        return new String(in.readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static Session today(
            TrainingBlock block) {
        if (block == null) {
            return null;
        }
        return block.allSessions().stream()
            .filter(each -> each.date()
                .equals(LocalDate.now()))
            .findFirst()
            .orElse(null);
    }

    private int fail(PrintWriter out, int code,
                     String why) {
        if (json) {
            JsonOutput.refusal(out, "ask", why);
        } else {
            out.println("Cannot ask: " + why);
        }
        out.flush();
        return code;
    }
}
