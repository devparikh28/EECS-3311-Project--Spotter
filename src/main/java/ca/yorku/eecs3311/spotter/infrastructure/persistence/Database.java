package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The local SQLite database.
 *
 * One connection is kept open for the life of the
 * application, which suits a single user desktop
 * tool and keeps an in memory database alive
 * between calls in tests.
 *
 * migrate() applies schema.sql. Every statement in
 * that file is written to be idempotent, so it is
 * safe to call on every startup.
 */
public final class Database
        implements AutoCloseable {

    /** Current schema version. */
    public static final int SCHEMA_VERSION = 1;

    private static final String SCHEMA_RESOURCE =
            "/schema.sql";

    private final String url;
    private Connection connection;

    private Database(String url) {
        this.url = url;
    }

    /** A database stored in the given file. */
    public static Database atPath(Path path) {
        if (path == null) {
            throw new PersistenceException(
                    "Database path is required");
        }
        Path parent = path.toAbsolutePath()
                .getParent();
        if (parent != null
                && !Files.isDirectory(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new PersistenceException(
                        "Could not create "
                                + parent, e);
            }
        }
        return new Database("jdbc:sqlite:"
                + path.toAbsolutePath());
    }

    /**
     * A database that lives only in memory, for
     * tests. Nothing is written to disk.
     */
    public static Database inMemory() {
        return new Database(
                "jdbc:sqlite::memory:");
    }

    /**
     * The open connection, created on first use.
     * Foreign keys are enforced, which SQLite
     * does not do by default.
     */
    public Connection connection() {
        try {
            if (connection == null
                    || connection.isClosed()) {
                connection = DriverManager
                        .getConnection(url);
                try (Statement s =
                        connection
                            .createStatement()) {
                    s.execute(
                      "PRAGMA foreign_keys = ON");
                }
            }
            return connection;
        } catch (SQLException e) {
            throw new PersistenceException(
                    "Could not open the database",
                    e);
        }
    }

    /**
     * Creates any missing tables and records the
     * schema version. Safe to call repeatedly.
     */
    public void migrate() {
        String script = readSchema();
        Connection c = connection();
        try (Statement s = c.createStatement()) {
            for (String statement
                    : splitStatements(script)) {
                s.execute(statement);
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                    "Could not apply the schema",
                    e);
        }
        recordVersion();
    }

    /** The highest applied version, or 0. */
    public int schemaVersion() {
        String sql = "SELECT MAX(version)"
                + " FROM schema_version";
        try (Statement s =
                     connection()
                         .createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private void recordVersion() {
        String sql = "INSERT OR IGNORE INTO"
                + " schema_version"
                + " (version, applied_at)"
                + " VALUES (" + SCHEMA_VERSION
                + ", '" + Instant.now() + "')";
        try (Statement s =
                connection().createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new PersistenceException(
                    "Could not record the schema"
                    + " version", e);
        }
    }

    private String readSchema() {
        try (InputStream in = Database.class
                .getResourceAsStream(
                        SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new PersistenceException(
                        "schema.sql is missing"
                        + " from the build");
            }
            return new String(in.readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenceException(
                    "Could not read schema.sql",
                    e);
        }
    }

    /**
     * Splits the script on semicolons, dropping
     * comment lines and blank statements. The
     * schema deliberately contains no semicolons
     * inside string literals, so this stays
     * simple.
     */
    static List<String> splitStatements(
            String script) {
        List<String> statements =
                new ArrayList<>();
        StringBuilder current =
                new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")
                    || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String statement =
                        current.toString().trim();
                statement = statement.substring(
                        0,
                        statement.length() - 1)
                        .trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    @Override
    public void close() {
        try {
            if (connection != null
                    && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                    "Could not close the"
                    + " database", e);
        }
    }
}
