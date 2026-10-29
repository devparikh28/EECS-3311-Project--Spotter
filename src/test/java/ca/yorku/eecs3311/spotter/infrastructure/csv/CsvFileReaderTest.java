package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the CSV reader. */
class CsvFileReaderTest {

    @TempDir
    Path tempDir;

    private Path write(String... lines)
            throws IOException {
        Path file = tempDir.resolve("test.csv");
        Files.write(file, List.of(lines));
        return file;
    }

    @Test
    @DisplayName(
        "a comma inside quotes does not split"
        + " the field")
    void quotedCommaDoesNotSplit() {
        List<String> fields =
            CsvFileReader.splitLine(
                "\"DEADLIFT\","
                + "\"28 Oct 2026, 16:17\",375");

        assertThat(fields).containsExactly(
                "DEADLIFT",
                "28 Oct 2026, 16:17",
                "375");
    }

    @Test
    @DisplayName(
        "a doubled quote inside a field is a"
        + " literal quote")
    void doubledQuoteIsLiteral() {
        List<String> fields =
            CsvFileReader.splitLine(
                "\"he said \"\"hi\"\"\",2");

        assertThat(fields).containsExactly(
                "he said \"hi\"", "2");
    }

    @Test
    @DisplayName("empty fields are preserved")
    void emptyFieldsArePreserved() {
        List<String> fields =
            CsvFileReader.splitLine("a,,b,");

        assertThat(fields).containsExactly(
                "a", "", "b", "");
    }

    @Test
    @DisplayName(
        "the header is lower cased so callers"
        + " need not match the export's casing")
    void headerIsLowerCased() throws IOException {
        Path file = write(
            "\"Cycle Start Time\",\"Recovery"
            + " Score %\"",
            "2026-10-29 03:16:55,60");

        assertThat(CsvFileReader.readHeader(file))
            .containsExactly("cycle start time",
                    "recovery score %");
    }

    @Test
    @DisplayName("rows are keyed by column name")
    void rowsAreKeyedByColumn()
            throws IOException {
        Path file = write(
            "name,weight,reps",
            "\"Squat (Barbell)\",225,5");

        List<Map<String, String>> rows =
                CsvFileReader.read(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
            .containsEntry("name",
                    "Squat (Barbell)")
            .containsEntry("weight", "225")
            .containsEntry("reps", "5");
    }

    @Test
    @DisplayName(
        "a short row leaves later columns"
        + " empty rather than failing")
    void shortRowIsPadded() throws IOException {
        Path file = write(
            "a,b,c",
            "1,2");

        assertThat(CsvFileReader.read(file)
                .get(0))
            .containsEntry("c", "");
    }

    @Test
    @DisplayName("blank lines are ignored")
    void blankLinesAreIgnored()
            throws IOException {
        Path file = write(
            "a,b", "1,2", "", "3,4");

        assertThat(CsvFileReader.read(file))
                .hasSize(2);
    }

    @Test
    @DisplayName(
        "a header only file yields no rows")
    void headerOnlyYieldsNothing()
            throws IOException {
        assertThat(CsvFileReader.read(
                write("a,b,c"))).isEmpty();
    }

    @Test
    @DisplayName("a missing file is reported")
    void missingFileIsReported() {
        assertThatThrownBy(() ->
            CsvFileReader.read(
                tempDir.resolve("absent.csv")))
            .isInstanceOf(
                CsvFormatException.class)
            .hasMessageContaining("No such file");
    }
}
