package ca.yorku.eecs3311.spotter
        .presentation.cli;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the ask command: the same question
 * arriving by argument, by file and by pipe.
 *
 * All of them use --demo, so no model is called and
 * no key is needed.
 */
class AskCommandTest {

    @TempDir
    Path tempDir;

    private StringWriter out;
    private InputStream realIn;
    private Path env;

    @BeforeEach
    void setUp() throws IOException {
        out = new StringWriter();
        realIn = System.in;
        env = tempDir.resolve(".env");
        Files.write(env, List.of(
            "SPOTTER_DB_PATH="
                + tempDir.resolve("ask.db")));
    }

    @AfterEach
    void restore() {
        System.setIn(realIn);
    }

    private int run(String... argv) {
        List<String> all = new java.util.ArrayList<>(
                List.of(argv));
        all.add("--env");
        all.add(env.toString());
        return new CoachCLI().run(all,
            new PrintWriter(out, true),
            new PrintWriter(
                new StringWriter(), true));
    }

    @Test
    @DisplayName("a typed question is answered")
    void typedQuestion() {
        int code = run("ask", "--demo",
            "How is my squat going?");

        assertThat(code).isZero();
        assertThat(out.toString())
            .contains("How is my squat going?")
            .contains("Coach");
    }

    @Test
    @DisplayName(
        "a question can be read from a file")
    void questionFromFile() throws IOException {
        Path task = tempDir.resolve("task.txt");
        Files.writeString(task,
            "Should I train today?\n");

        int code = run("ask", "--demo",
            "--input", task.toString());

        assertThat(code).isZero();
        assertThat(out.toString())
            .contains("Should I train today?");
    }

    @Test
    @DisplayName(
        "a question can be piped on standard input")
    void questionFromStdin() {
        System.setIn(new ByteArrayInputStream(
            "What should I eat tonight?"
                .getBytes(
                    StandardCharsets.UTF_8)));

        int code = run("ask", "--demo", "-i", "-");

        assertThat(code).isZero();
        assertThat(out.toString())
            .contains("What should I eat tonight?");
    }

    @Test
    @DisplayName("piped input is used with no flag")
    void pipeWithoutFlag() {
        System.setIn(new ByteArrayInputStream(
            "Any advice for deadlifts?"
                .getBytes(
                    StandardCharsets.UTF_8)));

        int code = run("ask", "--demo");

        assertThat(code).isZero();
        assertThat(out.toString())
            .contains("Any advice for deadlifts?");
    }

    @Test
    @DisplayName("json output carries the reply")
    void jsonOutput() {
        int code = run("ask", "--demo", "--json",
            "How am I doing?");

        assertThat(code).isZero();
        assertThat(out.toString())
            .contains("\"command\"")
            .contains("ask")
            .contains("\"ok\" : true")
            .contains("\"reply\"")
            .contains("How am I doing?");
    }

    @Test
    @DisplayName("an empty file is refused clearly")
    void emptyFile() throws IOException {
        Path task = tempDir.resolve("empty.txt");
        Files.writeString(task, "   \n");

        int code = run("ask", "--demo",
            "--input", task.toString());

        assertThat(code).isEqualTo(
                AskCommand.EXIT_NO_QUESTION);
        assertThat(out.toString())
            .contains("no question");
    }

    @Test
    @DisplayName("a missing file is reported")
    void missingFile() {
        int code = run("ask", "--demo", "--input",
            tempDir.resolve("nope.txt")
                .toString());

        assertThat(code).isEqualTo(
                AskCommand.EXIT_UNREADABLE);
        assertThat(out.toString())
            .contains("could not read");
    }

    @Test
    @DisplayName("a file wins over a typed question")
    void fileWinsOverArgument()
            throws IOException {
        Path task = tempDir.resolve("task.txt");
        Files.writeString(task, "From the file");

        run("ask", "--demo", "--input",
            task.toString(), "From the argument");

        assertThat(out.toString())
            .contains("From the file")
            .doesNotContain("From the argument");
    }
}
