package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import java.nio.file.Path;
import java.util.List;

import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .RecoveryRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .WorkoutRepository;

/**
 * Brings an export into the database.
 *
 * The path through here is identical whichever
 * adapter read the file, which is the Adapter
 * pattern doing its job: detect, read, save,
 * report. Nothing in this class names a vendor.
 */
public final class CsvImportService {

    private final DataSourceFactory factory;
    private final WorkoutRepository workouts;
    private final RecoveryRepository recovery;

    public CsvImportService(
            DataSourceFactory factory,
            WorkoutRepository workouts,
            RecoveryRepository recovery) {
        if (factory == null
                || workouts == null
                || recovery == null) {
            throw new IllegalArgumentException(
                "A factory and both"
                + " repositories are required");
        }
        this.factory = factory;
        this.workouts = workouts;
        this.recovery = recovery;
    }

    /** What a file is, without importing it. */
    public SourceKind detect(Path file) {
        return factory.detect(file);
    }

    /**
     * Imports a file, working out for itself what
     * it is.
     *
     * @throws CsvFormatException when nothing can
     *         read it
     */
    public ImportReport importFile(Path file) {
        SourceKind kind = factory.detect(file);
        if (kind == SourceKind.UNKNOWN) {
            throw new CsvFormatException(
                "This file matches no export"
                + " Spotter recognises. Its"
                + " columns can be mapped by"
                + " hand instead.");
        }
        return importAs(file, kind);
    }

    /** Imports a file read as a given export. */
    public ImportReport importAs(
            Path file, SourceKind kind) {
        DataSource<?> source =
                factory.create(kind);
        return switch (kind) {
            case HEVY -> importWorkouts(
                (DataSource<WorkoutSession>)
                    source, file);
            case WHOOP -> importRecovery(
                (DataSource<RecoveryDay>)
                    source, file);
            case UNKNOWN ->
                throw new CsvFormatException(
                    "Nothing can read this"
                    + " file");
        };
    }

    private ImportReport importWorkouts(
            DataSource<WorkoutSession> source,
            Path file) {
        List<WorkoutSession> sessions =
                source.read(file);
        int before = workouts.findAll().size();
        int sets = 0;
        for (WorkoutSession session : sessions) {
            workouts.save(session);
            sets += session.sets().size();
        }
        int after = workouts.findAll().size();
        int written = after - before;
        return new ImportReport(written,
                sessions.size() - written,
                sets, source.skipped());
    }

    private ImportReport importRecovery(
            DataSource<RecoveryDay> source,
            Path file) {
        List<RecoveryDay> days =
                source.read(file);
        int before = recovery.findAll().size();
        for (RecoveryDay day : days) {
            recovery.save(day);
        }
        int after = recovery.findAll().size();
        int written = after - before;
        return new ImportReport(written,
                days.size() - written,
                days.size(), source.skipped());
    }
}
