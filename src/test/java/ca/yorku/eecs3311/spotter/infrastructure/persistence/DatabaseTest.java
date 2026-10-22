package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the local database.
 *
 * Each test gets a fresh in memory database, so
 * nothing on disk is touched and no test can see
 * another test's tables.
 */
class DatabaseTest {

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private List<String> tableNames() {
        String sql = "SELECT name"
                + " FROM sqlite_master"
                + " WHERE type = 'table'"
                + " ORDER BY name";
        List<String> names = new ArrayList<>();
        try (Statement s = database.connection()
                     .createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                    "could not list tables", e);
        }
        return names;
    }

    @Test
    @DisplayName(
        "migrate creates the whole schema")
    void migrateCreatesSchema() {
        database.migrate();

        assertThat(tableNames()).contains(
                "lifter_profile",
                "profile_equipment",
                "profile_limitation",
                "exercise",
                "exercise_alias",
                "workout_session",
                "set_entry",
                "recovery_day",
                "training_block",
                "week",
                "planned_session",
                "exercise_prescription",
                "macro_target",
                "food_item",
                "daily_intake",
                "food_entry",
                "memory_note",
                "import_profile",
                "schema_version");
    }

    @Test
    @DisplayName(
        "migrate twice leaves the schema alone")
    void migrateIsIdempotent() {
        database.migrate();
        List<String> afterFirst = tableNames();

        database.migrate();

        assertThat(tableNames())
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("migrate records the version")
    void migrateRecordsVersion() {
        assertThat(database.schemaVersion())
                .isZero();

        database.migrate();

        assertThat(database.schemaVersion())
                .isEqualTo(
                    Database.SCHEMA_VERSION);
    }

    @Test
    @DisplayName(
        "the version is recorded only once")
    void versionRecordedOnce()
            throws SQLException {
        database.migrate();
        database.migrate();
        database.migrate();

        String sql = "SELECT COUNT(*)"
                + " FROM schema_version";
        try (Statement s = database.connection()
                     .createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            assertThat(rs.getInt(1))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("foreign keys are enforced")
    void foreignKeysAreEnforced() {
        database.migrate();

        String orphan = "INSERT INTO"
                + " profile_equipment"
                + " (profile_id, equipment)"
                + " VALUES (999, 'BARBELL')";

        assertThatThrownBy(() -> {
            try (Statement s =
                    database.connection()
                        .createStatement()) {
                s.execute(orphan);
            }
        }).isInstanceOf(SQLException.class)
          .hasMessageContaining("FOREIGN KEY");
    }

    @Test
    @DisplayName(
        "deleting a profile removes its rows")
    void cascadeDeleteWorks()
            throws SQLException {
        database.migrate();

        try (Statement s = database.connection()
                .createStatement()) {
            s.execute("INSERT INTO lifter_profile"
                + " (id, bodyweight_kg, goal,"
                + " experience,"
                + " training_days_per_week)"
                + " VALUES (1, 82.5, 'STRENGTH',"
                + " 'INTERMEDIATE', 4)");
            s.execute("INSERT INTO"
                + " profile_equipment"
                + " (profile_id, equipment)"
                + " VALUES (1, 'BARBELL')");
            s.execute("DELETE FROM lifter_profile"
                + " WHERE id = 1");

            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM"
                    + " profile_equipment")) {
                rs.next();
                assertThat(rs.getInt(1))
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName(
        "a file database is created on disk")
    void fileDatabaseIsCreated() {
        Path file = tempDir.resolve("nested")
                .resolve("spotter.db");

        try (Database onDisk =
                     Database.atPath(file)) {
            onDisk.migrate();

            assertThat(Files.exists(file))
                    .isTrue();
            assertThat(onDisk.schemaVersion())
                    .isEqualTo(
                        Database.SCHEMA_VERSION);
        }
    }

    @Test
    @DisplayName(
        "data survives reopening the file")
    void dataSurvivesReopen()
            throws SQLException {
        Path file = tempDir.resolve("spotter.db");

        try (Database first =
                     Database.atPath(file)) {
            first.migrate();
            try (Statement s = first.connection()
                    .createStatement()) {
                s.execute("INSERT INTO exercise"
                    + " (name) VALUES"
                    + " ('Back Squat')");
            }
        }

        try (Database second =
                     Database.atPath(file)) {
            try (Statement s = second.connection()
                     .createStatement();
                 ResultSet rs = s.executeQuery(
                   "SELECT name FROM exercise")) {
                rs.next();
                assertThat(rs.getString(1))
                        .isEqualTo("Back Squat");
            }
        }
    }

    @Test
    @DisplayName("a null path is refused")
    void nullPathIsRefused() {
        assertThatThrownBy(
                () -> Database.atPath(null))
            .isInstanceOf(
                PersistenceException.class)
            .hasMessageContaining("path");
    }

    @Test
    @DisplayName(
        "the script splitter drops comments")
    void splitterDropsComments() {
        String script = "-- a comment\n"
                + "CREATE TABLE a (x INT);\n"
                + "\n"
                + "-- another\n"
                + "CREATE TABLE b (y INT);\n";

        List<String> statements =
                Database.splitStatements(script);

        assertThat(statements).hasSize(2);
        assertThat(statements.get(0))
                .startsWith("CREATE TABLE a")
                .doesNotContain(";");
        assertThat(statements.get(1))
                .startsWith("CREATE TABLE b");
    }

    @Test
    @DisplayName(
        "the connection reopens after close")
    void connectionReopensAfterClose() {
        database.migrate();

        database.close();

        assertThat(database.schemaVersion())
                .isZero();
    }
}
