package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import java.nio.file.Path;
import java.util.List;

import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;

/**
 * Picks the adapter that can read a file.
 *
 * This is the Factory Method from the Stage 1
 * design. The import service asks what a file is
 * and gets back something implementing
 * DataSource; it never names a vendor, so adding
 * a third export means adding one class here and
 * changing nothing above.
 */
public final class DataSourceFactory {

    private final ExerciseCatalog catalog;

    public DataSourceFactory(
            ExerciseCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "A catalog is required");
        }
        this.catalog = catalog;
    }

    /**
     * Works out what a file is by looking at its
     * header, not its name. A file renamed
     * export.csv is still recognised, and a file
     * named hevy.csv that is not one is not
     * mistaken for it.
     */
    public SourceKind detect(Path file) {
        for (SourceKind kind
                : List.of(SourceKind.HEVY,
                          SourceKind.WHOOP)) {
            if (create(kind).canRead(file)) {
                return kind;
            }
        }
        return SourceKind.UNKNOWN;
    }

    /** The adapter for a known export. */
    public DataSource<?> create(
            SourceKind kind) {
        return switch (kind) {
            case HEVY ->
                new HevyCsvAdapter(catalog);
            case WHOOP ->
                new WhoopCsvAdapter();
            case UNKNOWN ->
                throw new IllegalArgumentException(
                    "There is no adapter for an"
                    + " unrecognised format."
                    + " A column mapping is"
                    + " needed instead.");
        };
    }
}
