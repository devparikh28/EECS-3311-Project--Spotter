package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoverySource;

/**
 * Tests for the Whoop adapter, using the real
 * export's column names.
 */
class WhoopCsvAdapterTest {

    private static final String HEADER =
        "Cycle start time,Cycle end time,"
        + "Cycle timezone,Recovery score %,"
        + "Resting heart rate (bpm),"
        + "Heart rate variability (ms),"
        + "Skin temp (celsius),Blood oxygen %,"
        + "Day Strain,Energy burned (cal),"
        + "Max HR (bpm),Average HR (bpm),"
        + "Sleep onset,Wake onset,"
        + "Sleep performance %,"
        + "Respiratory rate (rpm),"
        + "Asleep duration (min)";

    @TempDir
    Path tempDir;

    private WhoopCsvAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new WhoopCsvAdapter();
    }

    private Path write(String... dataRows)
            throws IOException {
        Path file = tempDir.resolve("whoop.csv");
        java.util.List<String> lines =
                new java.util.ArrayList<>();
        lines.add(HEADER);
        lines.addAll(List.of(dataRows));
        Files.write(file, lines);
        return file;
    }

    /** A row with the fields we read filled in. */
    private String row(String start,
                       String recovery,
                       String hrv,
                       String asleep) {
        return start + ",,UTC-04:00," + recovery
            + ",67," + hrv
            + ",33.10,96.90,,,,,,,81,13.8,"
            + asleep;
    }

    @Test
    @DisplayName(
        "a real export's header is recognised")
    void realHeaderIsRecognised()
            throws IOException {
        assertThat(adapter.canRead(write()))
                .isTrue();
    }

    @Test
    @DisplayName(
        "a file that is not Whoop is not"
        + " claimed")
    void otherFileIsNotClaimed()
            throws IOException {
        Path other = tempDir.resolve("x.csv");
        Files.write(other, List.of(
                "title,start_time,reps"));

        assertThat(adapter.canRead(other))
                .isFalse();
    }

    @Test
    @DisplayName(
        "readings are read, with sleep"
        + " converted from minutes to hours")
    void readingsAreRead() throws IOException {
        Path file = write(row(
            "2026-10-29 03:16:55",
            "60", "38", "398"));

        RecoveryDay day =
                adapter.read(file).get(0);

        assertThat(day.date()).isEqualTo(
                LocalDate.of(2026, 10, 29));
        assertThat(day.source()).isEqualTo(
                RecoverySource.WEARABLE_CSV);
        assertThat(day.recoveryScore())
                .hasValue(60);
        assertThat(day.hrvMs())
                .hasValue(38.0);
        assertThat(day.sleepHours()
                .getAsDouble())
            .isEqualTo(6.633, within(0.01));
    }

    @Test
    @DisplayName(
        "when a day has two cycles the longer"
        + " sleep is kept, which is the night"
        + " rather than the nap")
    void longerSleepWinsForADay()
            throws IOException {
        Path file = write(
            row("2026-10-20 14:00:00",
                "70", "50", "45"),
            row("2026-10-20 02:00:00",
                "40", "30", "420"));

        List<RecoveryDay> days =
                adapter.read(file);

        assertThat(days).hasSize(1);
        assertThat(days.get(0).recoveryScore())
                .hasValue(40);
        assertThat(days.get(0).sleepHours()
                .getAsDouble())
            .isEqualTo(7.0, within(0.01));
    }

    @Test
    @DisplayName(
        "a cycle still in progress is skipped"
        + " with a reason, not read as zero")
    void incompleteCycleIsSkipped()
            throws IOException {
        Path file = write(
            row("2026-10-29 03:16:55",
                "", "", ""));

        assertThat(adapter.read(file)).isEmpty();
        assertThat(adapter.skipped())
            .singleElement()
            .asString()
            .contains("still in progress");
    }

    @Test
    @DisplayName(
        "a partial row is kept with only what"
        + " it has")
    void partialRowIsKept() throws IOException {
        Path file = write(row(
            "2026-10-29 03:16:55",
            "", "", "398"));

        RecoveryDay day =
                adapter.read(file).get(0);

        assertThat(day.recoveryScore())
                .isEmpty();
        assertThat(day.hasObjectiveScore())
                .isFalse();
        assertThat(day.sleepHours())
                .isPresent();
    }

    @Test
    @DisplayName(
        "an unreadable date is skipped with a"
        + " reason")
    void unreadableDateIsSkipped()
            throws IOException {
        Path file = write(row(
            "not-a-date", "60", "38", "398"));

        assertThat(adapter.read(file)).isEmpty();
        assertThat(adapter.skipped())
            .singleElement()
            .asString()
            .contains("unreadable date");
    }

    @Test
    @DisplayName(
        "a recovery score is rounded to a whole"
        + " number")
    void scoreIsRounded() throws IOException {
        Path file = write(row(
            "2026-10-29 03:16:55",
            "59.6", "38", "398"));

        assertThat(adapter.read(file).get(0)
                .recoveryScore()).hasValue(60);
    }
}
