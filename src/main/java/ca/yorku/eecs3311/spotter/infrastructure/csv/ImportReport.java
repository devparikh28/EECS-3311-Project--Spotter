package ca.yorku.eecs3311.spotter
        .infrastructure.csv;

import java.util.List;

/**
 * What an import did.
 *
 * Skipped rows are reported rather than silently
 * dropped, because a real export contains rows
 * that cannot become training data, and the
 * lifter should be able to see how many and why.
 *
 * @param imported  sessions written
 * @param duplicates sessions already present
 * @param setsRead  sets that became training data
 * @param skipped   reasons rows were passed over
 */
public record ImportReport(
        int imported,
        int duplicates,
        int setsRead,
        List<String> skipped) {

    public ImportReport {
        skipped = skipped == null
                ? List.of()
                : List.copyOf(skipped);
    }

    public static ImportReport empty() {
        return new ImportReport(0, 0, 0,
                List.of());
    }

    public boolean isEmpty() {
        return imported == 0 && duplicates == 0;
    }

    public int skippedCount() {
        return skipped.size();
    }
}
