package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import java.nio.file.Path;
import java.util.List;

/**
 * Reads one kind of export into domain objects.
 *
 * This is the Adapter from the Stage 1 design.
 * Each implementation knows one vendor's column
 * names, date format and units, and everything
 * above this interface is identical whichever one
 * read the file.
 *
 * @param <T> what the export produces
 */
public interface DataSource<T> {

    /** A name for messages, such as "Hevy". */
    String name();

    /** True when this file looks like ours. */
    boolean canRead(Path file);

    /** Reads the file into domain objects. */
    List<T> read(Path file);

    /** Rows passed over, and why. */
    List<String> skipped();
}
