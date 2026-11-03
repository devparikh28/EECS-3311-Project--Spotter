package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter
        .infrastructure.csv.ColumnMapping;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.SourceKind;

/**
 * Column mappings the lifter has already
 * supplied.
 *
 * The reason this exists rather than asking every
 * time is that the question is about an
 * application, not about a file. A lifter who has
 * told Spotter where the weight lives in their
 * tracker's export has answered for every export
 * that tracker will ever produce.
 */
public interface ImportProfileRepository {

    /** Saves under a name, replacing any match. */
    void save(String name,
              SourceKind kind,
              ColumnMapping mapping);

    Optional<ColumnMapping> byName(String name);

    /** Every saved mapping's name. */
    List<String> names();

    void delete(String name);
}
