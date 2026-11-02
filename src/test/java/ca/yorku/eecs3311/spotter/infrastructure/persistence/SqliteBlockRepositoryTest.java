package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;

/**
 * Tests for storing training blocks.
 *
 * A block is four levels deep, so the round trip
 * is the main thing. The other cases worth
 * covering are the ones that would corrupt a
 * lifter's plan rather than merely lose it:
 * saving an edited block leaving the old weeks
 * behind, and a prescription coming back pointing
 * at the wrong movement.
 */
class SqliteBlockRepositoryTest {

    private static final LocalDate START =
            LocalDate.of(2026, 11, 2);

    private Database database;
    private ExerciseCatalog catalog;
    private BlockRepository repository;
    private Exercise squat;
    private Exercise bench;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        catalog = new SqliteExerciseCatalog(
                database);

        squat = catalog.add(new Exercise(0,
            "Back Squat", Set.of("quads"),
            "squat", Set.of(Equipment.BARBELL),
            Lift.SQUAT));
        bench = catalog.add(new Exercise(0,
            "Bench Press", Set.of("chest"),
            "horizontal press",
            Set.of(Equipment.BARBELL),
            Lift.BENCH));

        repository = new SqliteBlockRepository(
                database, catalog);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private TrainingBlock sample() {
        Session monday = new Session(0, START,
            "squat", List.of(
                new ExercisePrescription(0,
                    squat, 5, 5, 8.0, 140.0)));
        Session wednesday = new Session(0,
            START.plusDays(2), "bench",
            List.of(
                new ExercisePrescription(0,
                    bench, 4, 6, 7.5, 95.0)));
        Session second = new Session(0,
            START.plusWeeks(1), "squat",
            List.of(
                new ExercisePrescription(0,
                    squat, 5, 5, 8.5, 145.0)));

        return new TrainingBlock(0, START,
            "RPE based", "upper lower",
            BlockOrigin.GENERATED,
            List.of(
                new Week(1, List.of(monday,
                        wednesday)),
                new Week(2,
                        List.of(second))));
    }

    @Test
    @DisplayName(
        "a block survives the round trip to the"
        + " last prescription")
    void aBlockSurvivesTheRoundTrip() {
        TrainingBlock saved =
                repository.save(sample());

        assertThat(saved.id()).isPositive();

        TrainingBlock read = repository
                .findById(saved.id())
                .orElseThrow();

        assertThat(read.startDate())
            .isEqualTo(START);
        assertThat(read.progression()
                .orElseThrow())
            .isEqualTo("RPE based");
        assertThat(read.splitName()
                .orElseThrow())
            .isEqualTo("upper lower");
        assertThat(read.origin())
            .isEqualTo(BlockOrigin.GENERATED);
        assertThat(read.weekCount())
            .isEqualTo(2);
        assertThat(read.allSessions())
            .hasSize(3);

        ExercisePrescription first = read
            .findWeek(1).orElseThrow()
            .sessions().get(0)
            .prescriptions().get(0);
        assertThat(first.exercise().name())
            .isEqualTo("Back Squat");
        assertThat(first.sets()).isEqualTo(5);
        assertThat(first.reps()).isEqualTo(5);
        assertThat(first.targetRpe()
                .orElseThrow())
            .isEqualTo(8.0);
        assertThat(first.loadKg())
            .isEqualTo(140.0);
    }

    @Test
    @DisplayName(
        "sessions come back in the order they"
        + " are trained")
    void sessionsKeepTheirOrder() {
        TrainingBlock read = repository
            .findById(repository.save(sample())
                    .id())
            .orElseThrow();

        assertThat(read.allSessions())
            .extracting(Session::date)
            .containsExactly(START,
                START.plusDays(2),
                START.plusWeeks(1));
    }

    @Test
    @DisplayName(
        "an adjusted session keeps its note")
    void anAdjustmentNoteSurvives() {
        TrainingBlock block = sample();
        block.allSessions().get(0)
            .setAdjustmentNote(
                "cut a set after a short"
                + " night");

        TrainingBlock read = repository
            .findById(repository.save(block)
                    .id())
            .orElseThrow();

        assertThat(read.allSessions().get(0)
                .wasAdjusted())
            .isTrue();
        assertThat(read.allSessions().get(0)
                .adjustmentNote()
                .orElseThrow())
            .contains("short night");
    }

    @Test
    @DisplayName(
        "saving an edited block replaces its"
        + " weeks rather than adding to them")
    void savingAnEditReplacesTheWeeks() {
        TrainingBlock saved =
                repository.save(sample());

        TrainingBlock shortened =
            new TrainingBlock(saved.id(), START,
                "linear", "full body",
                BlockOrigin.GENERATED,
                List.of(new Week(1, List.of(
                    new Session(0, START,
                        "squat", List.of(
                            new ExercisePrescription(
                                0, squat, 3, 5,
                                7.0, 130.0)))))));

        TrainingBlock read =
                repository.save(shortened);

        assertThat(read.weekCount())
            .isEqualTo(1);
        assertThat(read.allSessions())
            .hasSize(1);
        assertThat(read.progression()
                .orElseThrow())
            .isEqualTo("linear");
        assertThat(repository.findAll())
            .hasSize(1);
    }

    @Test
    @DisplayName(
        "a saved block is the active one")
    void aSavedBlockIsActive() {
        TrainingBlock saved =
                repository.save(sample());

        assertThat(repository.active()
                .orElseThrow().id())
            .isEqualTo(saved.id());
    }

    @Test
    @DisplayName(
        "activating a block retires the others")
    void activatingRetiresTheOthers() {
        TrainingBlock first =
                repository.save(sample());
        TrainingBlock second =
                repository.save(sample());

        repository.activate(first.id());

        assertThat(repository.active()
                .orElseThrow().id())
            .isEqualTo(first.id());
        assertThat(repository.findAll())
            .hasSize(2);

        repository.activate(second.id());

        assertThat(repository.active()
                .orElseThrow().id())
            .isEqualTo(second.id());
    }

    @Test
    @DisplayName(
        "no block means nothing has been"
        + " planned yet, not an error")
    void noBlockIsAnEmptyOptional() {
        assertThat(repository.active())
            .isEmpty();
        assertThat(repository.findAll())
            .isEmpty();
    }

    @Test
    @DisplayName(
        "deleting a block takes its weeks,"
        + " sessions and prescriptions with it")
    void deletingCascades() {
        TrainingBlock saved =
                repository.save(sample());

        repository.delete(saved.id());

        assertThat(repository.findAll())
            .isEmpty();
        assertThat(repository.active())
            .isEmpty();
    }

    @Test
    @DisplayName(
        "exercises are read back through the"
        + " catalog, not copied into the plan")
    void exercisesComeFromTheCatalog() {
        TrainingBlock read = repository
            .findById(repository.save(sample())
                    .id())
            .orElseThrow();

        Exercise stored = read.allSessions()
                .get(0).prescriptions().get(0)
                .exercise();

        assertThat(stored.id())
            .isEqualTo(squat.id());
        assertThat(stored.requiredEquipment())
            .containsExactly(Equipment.BARBELL);
        assertThat(stored.competitionLift()
                .orElseThrow())
            .isEqualTo(Lift.SQUAT);
    }
}
