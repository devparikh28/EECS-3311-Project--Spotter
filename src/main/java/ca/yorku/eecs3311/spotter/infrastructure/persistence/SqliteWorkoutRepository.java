package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain.SetEntry;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;

/** Training history stored in SQLite. */
public final class SqliteWorkoutRepository
        implements WorkoutRepository {

    private final Database database;
    private final ExerciseCatalog catalog;

    public SqliteWorkoutRepository(
            Database database,
            ExerciseCatalog catalog) {
        if (database == null
                || catalog == null) {
            throw new IllegalArgumentException(
                "A database and a catalog are"
                + " required");
        }
        this.database = database;
        this.catalog = catalog;
    }

    @Override
    public WorkoutSession save(
            WorkoutSession session) {
        if (session == null) {
            throw new IllegalArgumentException(
                    "A session is required");
        }
        String sql = "INSERT OR IGNORE INTO"
                + " workout_session"
                + " (date, source)"
                + " VALUES (?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql,
                        Statement
                          .RETURN_GENERATED_KEYS)) {
            s.setString(1,
                    session.date().toString());
            s.setString(2, session.source());
            s.executeUpdate();

            int id = generatedOrExisting(s,
                    session);
            saveSets(id, session);
            return findById(id).orElseThrow();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save the session", e);
        }
    }

    /**
     * SQLite returns no key when INSERT OR IGNORE
     * skipped a duplicate, so the existing row is
     * looked up instead. That is what makes
     * importing the same export twice harmless.
     */
    private int generatedOrExisting(
            PreparedStatement s,
            WorkoutSession session)
            throws SQLException {
        try (ResultSet keys =
                s.getGeneratedKeys()) {
            if (keys.next()) {
                int id = keys.getInt(1);
                if (id > 0) {
                    return id;
                }
            }
        }
        String sql = "SELECT id FROM"
                + " workout_session"
                + " WHERE date = ?"
                + " AND source = ?";
        try (PreparedStatement lookup =
                database.connection()
                    .prepareStatement(sql)) {
            lookup.setString(1,
                    session.date().toString());
            lookup.setString(2,
                    session.source());
            try (ResultSet rs =
                    lookup.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void saveSets(
            int sessionId,
            WorkoutSession session)
            throws SQLException {
        try (PreparedStatement clear =
                database.connection()
                    .prepareStatement(
                      "DELETE FROM set_entry"
                      + " WHERE session_id = ?")) {
            clear.setInt(1, sessionId);
            clear.executeUpdate();
        }

        String sql = "INSERT INTO set_entry"
                + " (session_id, exercise_id,"
                + " weight_kg, reps, rpe,"
                + " set_order)"
                + " VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            int order = 0;
            for (SetEntry set : session.sets()) {
                Exercise stored = catalog.add(
                        set.exercise());
                s.setInt(1, sessionId);
                s.setInt(2, stored.id());
                s.setDouble(3, set.weightKg());
                s.setInt(4, set.reps());
                if (set.hasRpe()) {
                    s.setDouble(5, set.rpe()
                            .orElseThrow());
                } else {
                    s.setNull(5,
                        java.sql.Types.REAL);
                }
                s.setInt(6, order++);
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    @Override
    public Optional<WorkoutSession> findById(
            int id) {
        String sql = "SELECT id, date, source"
                + " FROM workout_session"
                + " WHERE id = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, id);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                        sessionFrom(rs));
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the session", e);
        }
    }

    @Override
    public List<WorkoutSession> findAll() {
        return query("SELECT id, date, source"
                + " FROM workout_session"
                + " ORDER BY date", null);
    }

    @Override
    public List<WorkoutSession> findSince(
            LocalDate since) {
        if (since == null) {
            return findAll();
        }
        return query("SELECT id, date, source"
                + " FROM workout_session"
                + " WHERE date >= ?"
                + " ORDER BY date",
                since.toString());
    }

    @Override
    public List<WorkoutSession> findByLift(
            Lift lift, LocalDate since) {
        List<WorkoutSession> matching =
                new ArrayList<>();
        for (WorkoutSession session
                : findSince(since)) {
            if (!session.setsFor(lift)
                    .isEmpty()) {
                matching.add(session);
            }
        }
        return List.copyOf(matching);
    }

    private List<WorkoutSession> query(
            String sql, String parameter) {
        List<WorkoutSession> found =
                new ArrayList<>();
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            if (parameter != null) {
                s.setString(1, parameter);
            }
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    found.add(sessionFrom(rs));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read sessions", e);
        }
        return List.copyOf(found);
    }

    private WorkoutSession sessionFrom(
            ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        return new WorkoutSession(
                id,
                LocalDate.parse(
                        rs.getString("date")),
                rs.getString("source"),
                setsFor(id));
    }

    private List<SetEntry> setsFor(
            int sessionId) throws SQLException {
        String sql = "SELECT e.name,"
                + " s.weight_kg, s.reps, s.rpe"
                + " FROM set_entry s"
                + " JOIN exercise e"
                + "   ON e.id = s.exercise_id"
                + " WHERE s.session_id = ?"
                + " ORDER BY s.set_order";
        List<SetEntry> sets = new ArrayList<>();
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, sessionId);
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    Exercise exercise =
                        catalog.byName(
                            rs.getString("name"))
                        .orElseThrow();
                    double weight =
                        rs.getDouble("weight_kg");
                    int reps = rs.getInt("reps");
                    double rpe =
                        rs.getDouble("rpe");
                    sets.add(rs.wasNull()
                        ? SetEntry.withoutRpe(
                            exercise, weight,
                            reps)
                        : SetEntry.of(exercise,
                            weight, reps, rpe));
                }
            }
        }
        return sets;
    }

    @Override
    public void delete(int id) {
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(
                      "DELETE FROM"
                      + " workout_session"
                      + " WHERE id = ?")) {
            s.setInt(1, id);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not delete the session",
                e);
        }
    }
}
