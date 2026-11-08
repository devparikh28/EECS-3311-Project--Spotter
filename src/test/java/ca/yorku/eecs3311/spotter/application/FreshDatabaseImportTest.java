package ca.yorku.eecs3311.spotter.application;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.nio.file.Path;

import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;

import org.junit.jupiter.api.Test;

/**
 * A workout export imported into a database that
 * has never been seeded must still count as squat,
 * bench and deadlift training.
 *
 * Found while recording the demonstration: on an
 * empty database the importer created exercises
 * that were only names, so analytics and the Plan
 * screen saw no history at all.
 */
class FreshDatabaseImportTest {

    @Test
    void importIntoEmptyDatabaseIsAnalysable() {
        Database database = Database.inMemory();
        try (CoachController controller =
                new CoachController(database)) {
            controller.importFile(Path.of(
                "sample-data/"
                + "hevy-export-sample.csv"));

            assertThat(controller
                .getAnalytics(Lift.SQUAT)
                .hasData()).isTrue();
        }
    }
}
