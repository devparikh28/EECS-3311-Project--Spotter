package ca.yorku.eecs3311.spotter.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .AnalyticsSummary;
import ca.yorku.eecs3311.spotter.domain
        .AttemptCalculator;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .MeetAttempts;
import ca.yorku.eecs3311.spotter.domain.Point;
import ca.yorku.eecs3311.spotter.domain.SetEntry;
import ca.yorku.eecs3311.spotter.domain
        .StrengthAnalytics;
import ca.yorku.eecs3311.spotter.domain
        .TestDayPlan;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.FoodCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .BlockRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .ImportProfileRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .ProfileRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteBlockRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteImportProfileRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteProfileRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteFoodCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.sample.SampleFoods;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.CsvImportService;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.DataSourceFactory;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.ImportReport;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.SourceKind;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .RecoveryRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteRecoveryRepository;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryRuleEngine;
import java.nio.file.Path;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteWorkoutRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .WorkoutRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.sample.SampleData;

/**
 * The one way in.
 *
 * This is the Facade from the Stage 1 design.
 * Both interfaces, the JavaFX dashboard and the
 * command line, call these methods and nothing
 * else, so neither one holds any logic and the
 * two can never drift apart in behaviour.
 */
public final class CoachController
        implements AutoCloseable {

    private final Database database;
    private final ExerciseCatalog catalog;
    private final FoodCatalog foods;
    private final ProfileRepository profiles;
    private final BlockRepository blocks;
    private final ImportProfileRepository
            importProfiles;
    private final WorkoutRepository workouts;
    private final StrengthAnalytics analytics;
    private final AttemptCalculator attempts;
    private final RecoveryRepository recovery;
    private final RecoveryRuleEngine recoveryRules;
    private final CsvImportService imports;

    public CoachController(
            SpotterConfig config) {
        this(Database.atPath(
                config.databasePath()));
    }

    /** Used by tests, with a database of their own. */
    public CoachController(Database database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "A database is required");
        }
        this.database = database;
        this.database.migrate();
        this.catalog =
            new SqliteExerciseCatalog(database);
        // The standard exercises must exist before
        // anything is imported. A name the importer
        // meets first would otherwise become an
        // exercise nobody knows is a squat.
        for (Exercise standard
                : SampleData.exercises()) {
            this.catalog.add(standard);
        }
        this.foods =
            new SqliteFoodCatalog(database);
        this.profiles =
            new SqliteProfileRepository(
                    database);
        this.blocks =
            new SqliteBlockRepository(database,
                    catalog);
        this.importProfiles =
            new SqliteImportProfileRepository(
                    database);
        this.workouts =
            new SqliteWorkoutRepository(
                database, catalog);
        this.analytics =
            new StrengthAnalytics();
        this.attempts =
            new AttemptCalculator();
        this.recovery =
            new SqliteRecoveryRepository(
                database);
        this.recoveryRules =
            new RecoveryRuleEngine();
        this.imports = new CsvImportService(
            new DataSourceFactory(catalog),
            workouts, recovery);
    }

    /** What an export is, without importing. */
    public SourceKind detect(Path file) {
        return imports.detect(file);
    }

    /**
     * Imports an export, working out what it is
     * (F02 and F03).
     */
    public ImportReport importFile(Path file) {
        return imports.importFile(file);
    }

    /**
     * Imports a file Spotter does not recognise,
     * through a mapping a person supplied (F02).
     */
    public ImportReport importMapped(
            Path file,
            ca.yorku.eecs3311.spotter
                .infrastructure.csv.ColumnMapping
                    mapping) {
        return imports.importMapped(file,
                mapping);
    }

    /** Saved column mappings (F02). */
    public ImportProfileRepository
            importProfiles() {
        return importProfiles;
    }

    public RecoveryRepository recovery() {
        return recovery;
    }

    /**
     * Days whose readings were poor enough to
     * question the session planned for them
     * (F07).
     */
    public List<RecoveryFlag> recoveryFlags(
            LocalDate since) {
        List<RecoveryFlag> flags =
                new ArrayList<>();
        for (RecoveryDay day
                : recovery.findSince(since)) {
            recoveryRules.evaluate(day)
                    .ifPresent(flags::add);
        }
        return List.copyOf(flags);
    }

    /** The lifter's profile (F01, F13). */
    public ProfileRepository profiles() {
        return profiles;
    }

    /** Stored training blocks (F05, F14). */
    public BlockRepository blocks() {
        return blocks;
    }

    /** The food catalog, for F09 and F10. */
    public FoodCatalog foods() {
        return foods;
    }

    /**
     * Loads the sample foods (F10), returning how
     * many were written.
     */
    public int loadSampleFoods() {
        return SampleFoods.load(foods);
    }

    public ExerciseCatalog catalog() {
        return catalog;
    }

    public WorkoutRepository workouts() {
        return workouts;
    }

    /** Loads the bundled sample history. */
    public int loadSampleData(LocalDate today) {
        return SampleData.load(catalog,
                workouts, today);
    }

    public boolean hasTrainingHistory() {
        return !workouts.findAll().isEmpty();
    }

    /**
     * Everything known about one lift (F04).
     *
     * Warnings name what is missing rather than
     * leaving a thin figure unexplained.
     */
    public AnalyticsSummary getAnalytics(
            Lift lift) {
        List<WorkoutSession> history =
                workouts.findAll();
        List<Point> series =
            analytics.e1rmSeries(lift, history);
        if (series.isEmpty()
                && analytics.weeklyTonnage(
                    lift, history).isEmpty()) {
            return AnalyticsSummary.empty(lift);
        }
        return new AnalyticsSummary(lift,
            series,
            analytics.weeklyTonnage(lift,
                    history),
            analytics.bestSet(lift, history),
            warningsFor(lift, history, series));
    }

    private List<String> warningsFor(
            Lift lift,
            List<WorkoutSession> history,
            List<Point> series) {
        List<String> warnings =
                new ArrayList<>();
        int withoutRpe = 0;
        for (WorkoutSession session : history) {
            for (SetEntry set
                    : session.setsFor(lift)) {
                if (!analytics.canEstimate(set)) {
                    withoutRpe++;
                }
            }
        }
        if (withoutRpe > 0) {
            warnings.add(withoutRpe
                + " set(s) had no RPE and were"
                + " not used for the estimate");
        }
        if (series.size() == 1) {
            warnings.add(
                "Only one session with an RPE,"
                + " so there is no trend yet");
        }
        return warnings;
    }

    /** How far back "current" strength reaches. */
    public static final int RECENT_WEEKS = 8;

    /**
     * Current estimated maxima, per lift.
     *
     * Only the last eight weeks count. Against a
     * seventeen month history the all time best
     * is not what to plan from: strength from a
     * year ago is not strength now, and planning
     * attempts off it would prescribe weights the
     * lifter cannot currently hit.
     */
    public Map<Lift, Double> currentE1RMs() {
        return analytics.currentE1RMs(
            workouts.findSince(
                LocalDate.now().minusWeeks(
                        RECENT_WEEKS)));
    }

    /** Best ever, for comparison against current. */
    public Map<Lift, Double> allTimeE1RMs() {
        return analytics.currentE1RMs(
                workouts.findAll());
    }

    /** How far the estimate moved (F04). */
    public Optional<Double> trendFor(Lift lift) {
        return analytics.trend(
                getAnalytics(lift)
                        .e1rmSeries());
    }

    /**
     * Three meet attempts (F08). Empty when the
     * lift has no usable history, rather than
     * inventing a number to plan from.
     */
    public Optional<MeetAttempts>
            calculateAttempts(Lift lift) {
        return currentE1RMs().containsKey(lift)
            ? Optional.of(
                attempts.calculateMeetAttempts(
                    lift,
                    currentE1RMs().get(lift)))
            : Optional.empty();
    }

    /** A test day plan (F08), for no meet date. */
    public Optional<TestDayPlan> projectTestDay(
            Lift lift) {
        return currentE1RMs().containsKey(lift)
            ? Optional.of(
                attempts.projectTestDay(lift,
                    currentE1RMs().get(lift)))
            : Optional.empty();
    }

    @Override
    public void close() {
        database.close();
    }
}
