package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .WeightUnit;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;

/**
 * Tests for the two routes into F14 that need no
 * model.
 *
 * A programme given as a spreadsheet, or typed a
 * line at a time, already has a structure.
 * Nothing needs interpreting, so nothing should
 * be sent anywhere, and these tests exist partly
 * to hold that line: if either route ever starts
 * needing a model, a deterministic import has
 * become a network call.
 */
class ProgrammeImportServiceTest {

    private static final LocalDate START =
            LocalDate.of(2026, 11, 9);

    @TempDir
    Path folder;

    private Database database;
    private ExerciseCatalog catalog;
    private ProgrammeImportService service;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        catalog = new SqliteExerciseCatalog(
                database);
        catalog.add(new Exercise(0, "Back Squat",
            Set.of("quads"), "squat",
            Set.of(Equipment.BARBELL),
            Lift.SQUAT));
        catalog.add(new Exercise(0,
            "Bench Press", Set.of("chest"),
            "horizontal press",
            Set.of(Equipment.BARBELL),
            Lift.BENCH));
        service = new ProgrammeImportService(
                catalog);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName(
        "typed lines become a block marked as"
        + " adopted")
    void typedLinesBecomeABlock() {
        Optional<TrainingBlock> read =
            service.fromManualEntry(List.of(
                "1/1 Back Squat 5x5@140 rpe8",
                "1/3 Bench Press 3x8@70",
                "2/1 Back Squat 5x5@145 rpe8"),
                START);

        assertThat(read).isPresent();
        TrainingBlock block = read.get();
        assertThat(block.origin())
            .isEqualTo(BlockOrigin.ADOPTED);
        assertThat(block.weekCount())
            .isEqualTo(2);
        assertThat(block.allSessions())
            .hasSize(3);
    }

    @Test
    @DisplayName(
        "a day number becomes a date counted"
        + " from the start")
    void daysBecomeDates() {
        TrainingBlock block =
            service.fromManualEntry(List.of(
                "1/1 Back Squat 5x5@140",
                "1/3 Bench Press 3x8@70",
                "2/1 Back Squat 5x5@145"),
                START).orElseThrow();

        assertThat(block.allSessions())
            .extracting(session ->
                    session.date())
            .containsExactly(START,
                START.plusDays(2),
                START.plusWeeks(1));
    }

    @Test
    @DisplayName(
        "an RPE is optional and a weight may be"
        + " left off")
    void weightAndRpeAreOptional() {
        TrainingBlock block =
            service.fromManualEntry(List.of(
                "1/1 Back Squat 5x5"),
                START).orElseThrow();

        var prescription = block.allSessions()
                .get(0).prescriptions().get(0);
        assertThat(prescription.loadKg())
            .isZero();
        assertThat(prescription.targetRpe())
            .isEmpty();
    }

    @Test
    @DisplayName(
        "a line in the wrong shape is refused"
        + " with its number")
    void aMalformedLineIsRefused() {
        assertThat(service.fromManualEntry(
            List.of("1/1 Back Squat 5x5@140",
                "squat heavy"), START))
            .isEmpty();
        assertThat(service.problems())
            .anyMatch(problem ->
                problem.contains("line 2"));
    }

    @Test
    @DisplayName(
        "a movement not in the catalogue is"
        + " refused rather than invented")
    void anUnknownMovementIsRefused() {
        assertThat(service.fromManualEntry(
            List.of("1/1 Zercher Carry 3x10@60"),
            START))
            .isEmpty();
        assertThat(service.problems())
            .anyMatch(problem -> problem
                .contains("Zercher Carry"));
    }

    @Test
    @DisplayName(
        "a programme spreadsheet becomes a"
        + " block")
    void aSpreadsheetBecomesABlock()
            throws IOException {
        Path file = write(
            "Week,Day,Lift,Sets,Reps,Kg,RPE\n"
            + "1,1,Back Squat,5,5,140,8\n"
            + "1,1,Bench Press,3,8,70,7\n"
            + "2,1,Back Squat,5,5,145,8\n");

        TrainingBlock block = service.fromCsv(
                file, mapping(), START)
            .orElseThrow();

        assertThat(block.weekCount())
            .isEqualTo(2);
        assertThat(block.allSessions())
            .hasSize(2);
        assertThat(block.allSessions().get(0)
                .prescriptions())
            .hasSize(2);
        assertThat(block.origin())
            .isEqualTo(BlockOrigin.ADOPTED);
    }

    @Test
    @DisplayName(
        "a row that cannot be read is passed"
        + " over while the rest imports")
    void oneBadRowDoesNotLoseTheOthers()
            throws IOException {
        Path file = write(
            "Week,Day,Lift,Sets,Reps,Kg,RPE\n"
            + "1,1,Back Squat,5,5,140,8\n"
            + "1,1,Zercher Carry,3,10,60,7\n");

        TrainingBlock block = service.fromCsv(
                file, mapping(), START)
            .orElseThrow();

        assertThat(block.allSessions().get(0)
                .prescriptions())
            .hasSize(1);
        assertThat(service.problems())
            .anyMatch(problem -> problem
                .contains("Zercher Carry"));
    }

    @Test
    @DisplayName(
        "a mapping missing a required field is"
        + " refused before the file is read")
    void anIncompleteMappingIsRefused()
            throws IOException {
        Path file = write(
            "Week,Lift,Sets,Reps\n"
            + "1,Back Squat,5,5\n");

        assertThat(service.fromCsv(file,
            new ColumnMapping(Map.of(
                "week", "Week"),
                WeightUnit.KG, null), START))
            .isEmpty();
        assertThat(service.problems())
            .anyMatch(problem ->
                problem.contains("exercise"));
    }

    @Test
    @DisplayName(
        "a weight in pounds is converted")
    void poundsAreConverted()
            throws IOException {
        Path file = write(
            "Week,Day,Lift,Sets,Reps,Load\n"
            + "1,1,Back Squat,5,5,300\n");

        TrainingBlock block = service.fromCsv(
            file,
            new ColumnMapping(Map.of(
                "week", "Week", "day", "Day",
                "exercise", "Lift",
                "sets", "Sets", "reps", "Reps",
                "weight", "Load"),
                WeightUnit.LB, null),
            START).orElseThrow();

        assertThat(block.allSessions().get(0)
                .prescriptions().get(0)
                .loadKg())
            .isEqualTo(WeightUnit.LB.toKg(300));
    }

    @Test
    @DisplayName(
        "a day left out of the spreadsheet is"
        + " treated as day one")
    void aMissingDayIsDayOne()
            throws IOException {
        Path file = write(
            "Week,Lift,Sets,Reps,Kg\n"
            + "1,Back Squat,5,5,140\n");

        TrainingBlock block = service.fromCsv(
            file,
            new ColumnMapping(Map.of(
                "week", "Week",
                "exercise", "Lift",
                "sets", "Sets", "reps", "Reps",
                "weight", "Kg"),
                WeightUnit.KG, null),
            START).orElseThrow();

        assertThat(block.allSessions().get(0)
                .date())
            .isEqualTo(START);
    }

    @Test
    @DisplayName(
        "nothing entered is refused rather than"
        + " producing an empty block")
    void nothingEnteredIsRefused() {
        assertThat(service.fromManualEntry(
                List.of(), START))
            .isEmpty();
        assertThat(service.problems())
            .isNotEmpty();
    }

    private Path write(String contents)
            throws IOException {
        Path file = folder.resolve("plan.csv");
        Files.writeString(file, contents);
        return file;
    }

    private static ColumnMapping mapping() {
        return new ColumnMapping(Map.of(
                "week", "Week", "day", "Day",
                "exercise", "Lift",
                "sets", "Sets", "reps", "Reps",
                "weight", "Kg", "rpe", "RPE"),
            WeightUnit.KG, null);
    }
}
