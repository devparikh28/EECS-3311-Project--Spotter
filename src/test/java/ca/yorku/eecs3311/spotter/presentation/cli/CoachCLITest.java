package ca.yorku.eecs3311.spotter
        .presentation.cli;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the command line interface.
 *
 * These run the real command objects through
 * CoachCLI.run, capturing what would have gone to
 * the terminal, so no process is started and
 * nothing is written outside the temporary
 * directory.
 */
class CoachCLITest {

    @TempDir
    Path tempDir;

    private CoachCLI cli;
    private StringWriter out;
    private StringWriter err;

    @BeforeEach
    void setUp() {
        cli = new CoachCLI();
        out = new StringWriter();
        err = new StringWriter();
    }

    private int run(String... argv) {
        PrintWriter outWriter =
                new PrintWriter(out, true);
        PrintWriter errWriter =
                new PrintWriter(err, true);
        return cli.run(List.of(argv),
                outWriter, errWriter);
    }

    /** Writes a config file in the temp dir. */
    private Path envWith(String... lines)
            throws IOException {
        Path file = tempDir.resolve(".env");
        Files.write(file, List.of(lines));
        return file;
    }

    private String dbLine() {
        return "SPOTTER_DB_PATH="
                + tempDir.resolve("test.db");
    }

    @Test
    @DisplayName(
        "no subcommand shows what is available")
    void noSubcommandShowsUsage() {
        int code = run();

        assertThat(code).isEqualTo(
                CoachCLI.EXIT_NO_SUBCOMMAND);
        assertThat(out.toString())
                .contains("Usage")
                .contains("spotter")
                .contains("status");
    }

    @Test
    @DisplayName("help is available and succeeds")
    void helpSucceeds() {
        int code = run("--help");

        assertThat(code).isZero();
        assertThat(out.toString())
                .contains("Usage")
                .contains("status");
    }

    @Test
    @DisplayName("the version is reported")
    void versionIsReported() {
        int code = run("--version");

        assertThat(code).isZero();
        assertThat(out.toString())
                .contains("spotter");
    }

    @Test
    @DisplayName("an unknown command fails")
    void unknownCommandFails() {
        int code = run("nonsense");

        assertThat(code).isNotZero();
        assertThat(err.toString())
                .contains("nonsense");
    }

    @Test
    @DisplayName(
        "status creates the database on"
        + " first run")
    void statusCreatesDatabase()
            throws IOException {
        Path env = envWith(dbLine());
        Path db = tempDir.resolve("test.db");

        assertThat(Files.exists(db)).isFalse();

        int code = run("status",
                "--env", env.toString());

        assertThat(code).isZero();
        assertThat(Files.exists(db)).isTrue();
        assertThat(out.toString())
                .contains("schema version  1");
    }

    @Test
    @DisplayName(
        "status reports the configured model")
    void statusReportsModel()
            throws IOException {
        Path env = envWith(
                "LLM_MODEL=some-test-model",
                dbLine());

        run("status", "--env", env.toString());

        assertThat(out.toString())
                .contains("some-test-model");
    }

    @Test
    @DisplayName(
        "status says when no key is set")
    void statusSaysWhenKeyMissing()
            throws IOException {
        Path env = envWith(dbLine());

        run("status", "--env", env.toString());

        assertThat(out.toString())
                .contains("not set")
                .contains(".env.example");
    }

    @Test
    @DisplayName(
        "status never prints the key itself")
    void statusNeverPrintsTheKey()
            throws IOException {
        Path env = envWith(
                "LLM_API_KEY=sk-secret-value",
                dbLine());

        run("status", "--env", env.toString());

        assertThat(out.toString())
                .contains("found")
                .doesNotContain("sk-secret-value");
        assertThat(err.toString())
                .doesNotContain("sk-secret-value");
    }

    @Test
    @DisplayName(
        "status is safe to run twice")
    void statusIsRepeatable()
            throws IOException {
        Path env = envWith(dbLine());

        int first = run("status",
                "--env", env.toString());
        int second = run("status",
                "--env", env.toString());

        assertThat(first).isZero();
        assertThat(second).isZero();
        assertThat(out.toString())
                .contains("tables");
    }

    @Test
    @DisplayName(
        "a missing config file is not fatal")
    void missingConfigIsNotFatal() {
        Path absent = tempDir.resolve("absent");

        int code = run("status",
                "--env", absent.toString());

        assertThat(code).isZero();
        assertThat(out.toString())
                .contains("not set");
    }
}
