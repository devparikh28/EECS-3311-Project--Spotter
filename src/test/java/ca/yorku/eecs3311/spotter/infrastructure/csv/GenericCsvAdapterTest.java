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
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .WeightUnit;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .ImportProfileRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteImportProfileRepository;

/**
 * Tests for reading an export from an application
 * Spotter does not know.
 *
 * The Hevy adapter knows one vendor's answer to
 * where the weight lives. This one is told, and
 * what these tests pin down is that being told
 * wrongly fails loudly rather than importing
 * nothing and reporting success.
 */
class GenericCsvAdapterTest {

    @TempDir
    Path folder;

    private Database database;
    private ExerciseCatalog catalog;

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
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private Path write(String contents)
            throws IOException {
        Path file = folder.resolve(
                "export.csv");
        Files.writeString(file, contents);
        return file;
    }

    private static final String EXPORT =
        "Day,Movement,Load (lbs),Repetitions,"
        + "Effort,Kind\n"
        + "2026-10-05,Back Squat,300,5,8,"
        + "working\n"
        + "2026-10-05,Back Squat,300,5,9,"
        + "working\n"
        + "2026-10-05,Back Squat,135,5,,"
        + "warmup\n"
        + "2026-10-07,Bench Press,200,5,8,"
        + "working\n";

    private static ColumnMapping mapping() {
        return new ColumnMapping(Map.of(
                "date", "Day",
                "exercise", "Movement",
                "weight", "Load (lbs)",
                "reps", "Repetitions",
                "rpe", "Effort",
                "set_type", "Kind"),
            WeightUnit.LB, null);
    }

    @Test
    @DisplayName(
        "a mapping is suggested from the header,"
        + " including the unit")
    void aMappingIsSuggested()
            throws IOException {
        ColumnMapping suggested =
            ColumnMapping.suggestFor(
                CsvFileReader.readHeader(
                    write(EXPORT)));

        // The reader lowercases headers, and
        // every comparison against a file
        // ignores case, so a suggestion holds
        // the lowercased name.
        assertThat(suggested.columnFor("date")
                .orElseThrow())
            .isEqualToIgnoringCase("Day");
        assertThat(suggested.columnFor(
                "exercise").orElseThrow())
            .isEqualToIgnoringCase("Movement");
        assertThat(suggested.columnFor("weight")
                .orElseThrow())
            .isEqualToIgnoringCase(
                    "Load (lbs)");
        assertThat(suggested.unit())
            .isEqualTo(WeightUnit.LB);
        assertThat(suggested.isComplete())
            .isTrue();
    }

    @Test
    @DisplayName(
        "a mapped export reads, converting the"
        + " unit and grouping by day")
    void aMappedExportReads()
            throws IOException {
        List<WorkoutSession> sessions =
            new GenericCsvAdapter(catalog,
                mapping())
                .read(write(EXPORT));

        assertThat(sessions).hasSize(2);
        WorkoutSession first = sessions.get(0);
        assertThat(first.date())
            .isEqualTo(
                LocalDate.of(2026, 10, 5));
        assertThat(first.sets()).hasSize(2);
        // 300 lb is 136.1 kg
        assertThat(first.sets().get(0)
                .weightKg())
            .isEqualTo(WeightUnit.LB.toKg(300));
    }

    @Test
    @DisplayName(
        "a warm up is skipped when the set type"
        + " column is mapped")
    void warmUpsAreSkipped()
            throws IOException {
        GenericCsvAdapter adapter =
            new GenericCsvAdapter(catalog,
                    mapping());
        adapter.read(write(EXPORT));

        assertThat(adapter.skipped())
            .anyMatch(reason -> reason
                .contains("not training data"));
    }

    @Test
    @DisplayName(
        "a row naming an unknown movement is"
        + " skipped with its row number rather"
        + " than failing the import")
    void anUnknownMovementIsSkipped()
            throws IOException {
        Path file = write(
            "Day,Movement,Load (lbs),"
            + "Repetitions,Effort,Kind\n"
            + "2026-10-05,Zercher Carry,300,5,"
            + "8,working\n"
            + "2026-10-05,Back Squat,300,5,8,"
            + "working\n");

        GenericCsvAdapter adapter =
            new GenericCsvAdapter(catalog,
                    mapping());
        List<WorkoutSession> sessions =
                adapter.read(file);

        assertThat(sessions).hasSize(1);
        assertThat(adapter.skipped())
            .anyMatch(reason ->
                reason.contains("row 2")
                && reason.contains(
                        "Zercher Carry"));
    }

    @Test
    @DisplayName(
        "a mapping naming a column the file does"
        + " not have is refused, rather than"
        + " importing nothing and calling it"
        + " success")
    void aWrongColumnIsRefused()
            throws IOException {
        ColumnMapping wrong = new ColumnMapping(
            Map.of("date", "Day",
                "exercise", "Movement",
                "weight", "Kilos",
                "reps", "Repetitions"),
            WeightUnit.KG, null);

        assertThat(wrong.validate(
                CsvFileReader.readHeader(
                    write(EXPORT))))
            .get()
            .asString()
            .contains("no column called");
    }

    @Test
    @DisplayName(
        "an incomplete mapping names what is"
        + " still missing")
    void anIncompleteMappingNamesTheGap() {
        ColumnMapping thin = new ColumnMapping(
            Map.of("date", "Day"),
            WeightUnit.KG, null);

        assertThat(thin.missing())
            .containsExactly("exercise",
                    "weight", "reps");
        assertThat(thin.validate(List.of("Day")))
            .get().asString()
            .contains("exercise");
    }

    @Test
    @DisplayName(
        "an unusual date format is read when"
        + " the mapping gives one")
    void aMappedDateFormatIsRead()
            throws IOException {
        Path file = write(
            "Day,Movement,Kg,Reps\n"
            + "05/10/2026,Back Squat,140,5\n");

        List<WorkoutSession> sessions =
            new GenericCsvAdapter(catalog,
                new ColumnMapping(Map.of(
                    "date", "Day",
                    "exercise", "Movement",
                    "weight", "Kg",
                    "reps", "Reps"),
                    WeightUnit.KG,
                    "dd/MM/yyyy"))
                .read(file);

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).date())
            .isEqualTo(
                LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName(
        "a date that matches no format is"
        + " skipped with advice rather than"
        + " throwing")
    void anUnreadableDateIsSkipped()
            throws IOException {
        Path file = write(
            "Day,Movement,Kg,Reps\n"
            + "last Tuesday,Back Squat,140,5\n");

        GenericCsvAdapter adapter =
            new GenericCsvAdapter(catalog,
                new ColumnMapping(Map.of(
                    "date", "Day",
                    "exercise", "Movement",
                    "weight", "Kg",
                    "reps", "Reps"),
                    WeightUnit.KG, null));

        assertThat(adapter.read(file)).isEmpty();
        assertThat(adapter.skipped())
            .anyMatch(reason -> reason
                .contains("date format"));
    }

    @Test
    @DisplayName(
        "a mapping survives being saved and"
        + " read back")
    void aMappingSurvivesStorage() {
        ImportProfileRepository profiles =
            new SqliteImportProfileRepository(
                    database);

        profiles.save("mytracker",
            SourceKind.UNKNOWN, mapping());

        ColumnMapping read = profiles
                .byName("mytracker")
                .orElseThrow();

        assertThat(read.columnFor("weight"))
            .contains("Load (lbs)");
        assertThat(read.unit())
            .isEqualTo(WeightUnit.LB);
        assertThat(profiles.names())
            .containsExactly("mytracker");
    }

    @Test
    @DisplayName(
        "saving the same name twice replaces"
        + " the mapping rather than adding one")
    void savingTwiceReplaces() {
        ImportProfileRepository profiles =
            new SqliteImportProfileRepository(
                    database);

        profiles.save("mytracker",
            SourceKind.UNKNOWN, mapping());
        profiles.save("mytracker",
            SourceKind.UNKNOWN,
            new ColumnMapping(Map.of(
                "date", "Date",
                "exercise", "Lift",
                "weight", "Kg",
                "reps", "Reps"),
                WeightUnit.KG, null));

        assertThat(profiles.names()).hasSize(1);
        assertThat(profiles.byName("mytracker")
                .orElseThrow().unit())
            .isEqualTo(WeightUnit.KG);
    }

    @Test
    @DisplayName(
        "a mapping nobody saved is empty rather"
        + " than an error")
    void anUnknownProfileIsEmpty() {
        assertThat(
            new SqliteImportProfileRepository(
                    database)
                .byName("nothing"))
            .isEmpty();
    }
}
