package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * The command line interface.
 *
 * Both interfaces reach the same operations: this
 * one and the JavaFX dashboard call the same
 * controller methods. The CLI also makes the
 * agent scriptable, which is what the Stage 3
 * behavioural tests drive.
 *
 * Subcommands are registered here as each feature
 * lands.
 */
@Command(
    name = "spotter",
    mixinStandardHelpOptions = true,
    versionProvider =
        CoachCLI.VersionProvider.class,
    description =
        "An AI strength training and nutrition"
        + " coach.",
    subcommands = {
        StatusCommand.class
    })
public final class CoachCLI
        implements Callable<Integer> {

    /** Returned when no subcommand was given. */
    public static final int
            EXIT_NO_SUBCOMMAND = 2;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    /**
     * Runs one invocation and returns the exit
     * code. Tests call this directly rather than
     * starting a process.
     */
    public int run(List<String> argv) {
        return buildCommandLine()
                .execute(argv.toArray(
                        new String[0]));
    }

    /**
     * Runs with output sent somewhere of the
     * caller's choosing, which is how tests read
     * what was printed.
     */
    public int run(List<String> argv,
                   PrintWriter out,
                   PrintWriter err) {
        return buildCommandLine()
                .setOut(out)
                .setErr(err)
                .execute(argv.toArray(
                        new String[0]));
    }

    private CommandLine buildCommandLine() {
        return new CommandLine(this)
                .setCaseInsensitiveEnumValuesAllowed(
                        true);
    }

    /** With no subcommand, show what is on offer. */
    @Override
    public Integer call() {
        spec.commandLine().usage(
                spec.commandLine()
                    .getOut());
        return EXIT_NO_SUBCOMMAND;
    }

    public static void main(String[] args) {
        System.exit(new CoachCLI()
                .run(List.of(args)));
    }

    /** Reports the version shown by --version. */
    static class VersionProvider
            implements
            CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() {
            String version = CoachCLI.class
                    .getPackage()
                    .getImplementationVersion();
            return new String[] {
                "spotter "
                + (version == null
                    ? "0.1.0-SNAPSHOT"
                    : version)
            };
        }
    }
}
