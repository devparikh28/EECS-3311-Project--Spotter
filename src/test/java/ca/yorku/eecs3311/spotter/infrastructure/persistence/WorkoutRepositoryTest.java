package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .within;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain.SetEntry;
import ca.yorku.eecs3311.spotter.domain
        .StrengthAnalytics;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.sample.SampleData;

/**
 * Tests for storing and reading training history.
 *
 * These are integration tests: a real in memory
 * database, the real catalog and the real
 * repository together, because the thing worth
 * checking is that a session survives the round
 * trip intact.
 */
class WorkoutRepositoryTest {

    private static final LocalDate DAY =
            LocalDate.of(2026, 10, 5);

    private Database database;
    private ExerciseCatalog catalog;
    private WorkoutRepository workouts;
    private Exercise squat;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        catalog = new SqliteExerciseCatalog(
                database);
        workouts =
            new SqliteWorkoutRepository(
                database, catalog);
        squat = catalog.add(new Exercise(0,
            "Back Squat", Set.of("quads"),
            "squat",
            Set.of(Equipment.BARBELL),
            Lift.SQUAT));
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName(
        "a session survives the round trip")
    void sessionSurvivesRoundTrip() {
        WorkoutSession saved = workouts.save(
            new WorkoutSession(0, DAY, "hevy",
                List.of(
                    SetEntry.of(squat,
                        102.5, 5, 8),
                    SetEntry.withoutRpe(squat,
                        60, 10))));

        WorkoutSession read = workouts
                .findById(saved.id())
                .orElseThrow();

        assertThat(read.date()).isEqualTo(DAY);
        assertThat(read.source())
                .isEqualTo("hevy");
        assertThat(read.sets()).hasSize(2);
        assertThat(read.sets().get(0).weightKg())
                .isEqualTo(102.5,
                        within(0.001));
        assertThat(read.sets().get(0).rpe())
                .contains(8.0);
    }

    @Test
    @DisplayName(
        "a set logged without an RPE comes back"
        + " without one, not as zero")
    void missingRpeSurvivesAsMissing() {
        WorkoutSession saved = workouts.save(
            new WorkoutSession(0, DAY, "manual",
                List.of(SetEntry.withoutRpe(
                        squat, 60, 10))));

        SetEntry read = workouts
                .findById(saved.id())
                .orElseThrow()
                .sets().get(0);

        assertThat(read.hasRpe()).isFalse();
        assertThat(read.rpe()).isEmpty();
    }

    @Test
    @DisplayName("set order is preserved")
    void setOrderIsPreserved() {
        WorkoutSession saved = workouts.save(
            new WorkoutSession(0, DAY, "hevy",
                List.of(
                    SetEntry.of(squat, 60, 5, 6),
                    SetEntry.of(squat, 80, 5, 7),
                    SetEntry.of(squat,
                        100, 5, 8))));

        List<SetEntry> read = workouts
                .findById(saved.id())
                .orElseThrow().sets();

        assertThat(read).extracting(
                SetEntry::weightKg)
            .containsExactly(60.0, 80.0, 100.0);
    }

    @Test
    @DisplayName(
        "importing the same session twice does"
        + " not duplicate it")
    void reimportDoesNotDuplicate() {
        WorkoutSession session =
            new WorkoutSession(0, DAY, "hevy",
                List.of(SetEntry.of(squat,
                        100, 5, 8)));

        workouts.save(session);
        workouts.save(session);

        assertThat(workouts.findAll())
                .hasSize(1);
        assertThat(workouts.findAll().get(0)
                .sets()).hasSize(1);
    }

    @Test
    @DisplayName(
        "sessions can be filtered by date")
    void sessionsFilterByDate() {
        workouts.save(new WorkoutSession(0,
            DAY, "hevy", List.of(
                SetEntry.of(squat, 100, 5, 8))));
        workouts.save(new WorkoutSession(0,
            DAY.plusDays(7), "hevy", List.of(
                SetEntry.of(squat, 105, 5, 8))));

        assertThat(workouts.findSince(
                DAY.plusDays(1))).hasSize(1);
        assertThat(workouts.findSince(DAY))
                .hasSize(2);
    }

    @Test
    @DisplayName(
        "sessions can be filtered by lift")
    void sessionsFilterByLift() {
        workouts.save(new WorkoutSession(0,
            DAY, "hevy", List.of(
                SetEntry.of(squat, 100, 5, 8))));

        assertThat(workouts.findByLift(
                Lift.SQUAT, DAY)).hasSize(1);
        assertThat(workouts.findByLift(
                Lift.BENCH, DAY)).isEmpty();
    }

    @Test
    @DisplayName("a session can be deleted")
    void sessionCanBeDeleted() {
        WorkoutSession saved = workouts.save(
            new WorkoutSession(0, DAY, "hevy",
                List.of(SetEntry.of(squat,
                        100, 5, 8))));

        workouts.delete(saved.id());

        assertThat(workouts.findAll()).isEmpty();
    }

    @Test
    @DisplayName(
        "the sample data loads and the"
        + " analytics can read it")
    void sampleDataLoadsAndReads() {
        int written = SampleData.load(
                catalog, workouts,
                LocalDate.of(2026, 10, 26));

        assertThat(written).isPositive();
        assertThat(workouts.findAll())
                .hasSize(written);

        StrengthAnalytics analytics =
                new StrengthAnalytics();
        List<WorkoutSession> history =
                workouts.findAll();

        assertThat(analytics.currentE1RMs(
                history))
            .containsKeys(Lift.SQUAT,
                    Lift.BENCH, Lift.DEADLIFT);
    }

    @Test
    @DisplayName(
        "the sample squat improves and the"
        + " sample bench stalls, which is what"
        + " makes it worth demonstrating")
    void sampleShowsBothProgressAndAStall() {
        SampleData.load(catalog, workouts,
                LocalDate.of(2026, 10, 26));

        StrengthAnalytics analytics =
                new StrengthAnalytics();
        List<WorkoutSession> history =
                workouts.findAll();

        double squatTrend = analytics.trend(
            analytics.e1rmSeries(Lift.SQUAT,
                history)).orElseThrow();
        double benchTrend = analytics.trend(
            analytics.e1rmSeries(Lift.BENCH,
                history)).orElseThrow();

        assertThat(squatTrend).isPositive();
        assertThat(benchTrend)
                .isLessThan(squatTrend);
    }
}
