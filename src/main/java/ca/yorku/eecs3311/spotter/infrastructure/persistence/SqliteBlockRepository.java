package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;

/**
 * Training blocks, stored in SQLite across four
 * tables.
 *
 * Exercises are stored by their catalog id and
 * read back through the catalog, never copied
 * into the plan. A prescription therefore cannot
 * drift from the movement it names, and the same
 * rule that stops a model inventing an exercise
 * stops the database holding a stale copy of one.
 *
 * Saving an existing block rewrites its weeks
 * rather than reconciling them. A block is
 * written once and then edited rarely, by one
 * person, so the simple approach is correct and
 * leaves nothing orphaned.
 */
public final class SqliteBlockRepository
        implements BlockRepository {

    private final Database database;
    private final ExerciseCatalog catalog;

    public SqliteBlockRepository(
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
    public TrainingBlock save(
            TrainingBlock block) {
        if (block == null) {
            throw new IllegalArgumentException(
                    "A block is required");
        }
        int id = block.id() > 0
                ? rewrite(block)
                : insert(block);

        for (Week week : block.weeks()) {
            int weekId = insertWeek(id, week);
            for (Session session
                    : week.sessions()) {
                int sessionId = insertSession(
                        weekId, session);
                insertPrescriptions(sessionId,
                        session);
            }
        }
        return findById(id).orElseThrow();
    }

    private int insert(TrainingBlock block) {
        String sql = "INSERT INTO training_block"
            + " (start_date, progression,"
            + " split_name, origin, active)"
            + " VALUES (?, ?, ?, ?, 1)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql,
                        Statement
                            .RETURN_GENERATED_KEYS)) {
            bind(s, block);
            s.executeUpdate();
            try (ResultSet keys =
                    s.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new PersistenceException(
                "Saving a block returned no id");
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save the block", e);
        }
    }

    /** Updates the block and clears its weeks. */
    private int rewrite(TrainingBlock block) {
        String sql = "UPDATE training_block SET"
            + " start_date = ?, progression = ?,"
            + " split_name = ?, origin = ?"
            + " WHERE id = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            bind(s, block);
            s.setInt(5, block.id());
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not update the block", e);
        }
        execute("DELETE FROM week WHERE block_id"
            + " = ?", block.id());
        return block.id();
    }

    private static void bind(
            PreparedStatement s,
            TrainingBlock block)
            throws SQLException {
        s.setString(1,
                block.startDate().toString());
        s.setString(2, block.progression()
                .orElse("unspecified"));
        if (block.splitName().isPresent()) {
            s.setString(3,
                    block.splitName().get());
        } else {
            s.setNull(3, Types.VARCHAR);
        }
        s.setString(4, block.origin().name());
    }

    private int insertWeek(int blockId,
                           Week week) {
        String sql = "INSERT INTO week (block_id,"
            + " number) VALUES (?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql,
                        Statement
                            .RETURN_GENERATED_KEYS)) {
            s.setInt(1, blockId);
            s.setInt(2, week.number());
            s.executeUpdate();
            try (ResultSet keys =
                    s.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new PersistenceException(
                "Saving a week returned no id");
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save week "
                + week.number(), e);
        }
    }

    private int insertSession(
            int weekId, Session session) {
        String sql = "INSERT INTO"
            + " planned_session (week_id, date,"
            + " focus, adjustment_note)"
            + " VALUES (?, ?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql,
                        Statement
                            .RETURN_GENERATED_KEYS)) {
            s.setInt(1, weekId);
            s.setString(2,
                    session.date().toString());
            setNullable(s, 3,
                    session.focus().orElse(null));
            setNullable(s, 4, session
                    .adjustmentNote()
                    .orElse(null));
            s.executeUpdate();
            try (ResultSet keys =
                    s.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new PersistenceException(
                "Saving a session returned no"
                + " id");
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save the session on "
                + session.date(), e);
        }
    }

    private void insertPrescriptions(
            int sessionId, Session session) {
        String sql = "INSERT INTO"
            + " exercise_prescription"
            + " (session_id, exercise_id, sets,"
            + " reps, target_rpe, load_kg,"
            + " prescription_order)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            int order = 0;
            for (ExercisePrescription p
                    : session.prescriptions()) {
                s.setInt(1, sessionId);
                s.setInt(2, p.exercise().id());
                s.setInt(3, p.sets());
                s.setInt(4, p.reps());
                if (p.targetRpe().isPresent()) {
                    s.setDouble(5, p.targetRpe()
                            .get());
                } else {
                    s.setNull(5, Types.REAL);
                }
                s.setDouble(6, p.loadKg());
                s.setInt(7, order++);
                s.addBatch();
            }
            s.executeBatch();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save the"
                + " prescriptions", e);
        }
    }

    private static void setNullable(
            PreparedStatement s,
            int index,
            String value)
            throws SQLException {
        if (value == null || value.isBlank()) {
            s.setNull(index, Types.VARCHAR);
        } else {
            s.setString(index, value);
        }
    }

    @Override
    public Optional<TrainingBlock> findById(
            int id) {
        String sql = "SELECT * FROM"
            + " training_block WHERE id = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, id);
            try (ResultSet rs =
                    s.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(
                            read(rs));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the block", e);
        }
        return Optional.empty();
    }

    @Override
    public List<TrainingBlock> findAll() {
        List<TrainingBlock> found =
                new ArrayList<>();
        String sql = "SELECT * FROM"
            + " training_block"
            + " ORDER BY start_date, id";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql);
                ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                found.add(read(rs));
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the blocks", e);
        }
        return List.copyOf(found);
    }

    @Override
    public Optional<TrainingBlock> active() {
        String sql = "SELECT * FROM"
            + " training_block WHERE active = 1"
            + " ORDER BY start_date DESC, id DESC";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql);
                ResultSet rs = s.executeQuery()) {
            if (rs.next()) {
                return Optional.of(read(rs));
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the active"
                + " block", e);
        }
        return Optional.empty();
    }

    @Override
    public void activate(int id) {
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(
                        "UPDATE training_block"
                        + " SET active = 0")) {
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not retire the previous"
                + " blocks", e);
        }
        execute("UPDATE training_block SET"
            + " active = 1 WHERE id = ?", id);
    }

    @Override
    public void delete(int id) {
        execute("DELETE FROM training_block"
            + " WHERE id = ?", id);
    }

    private void execute(String sql, int id) {
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, id);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not run: " + sql, e);
        }
    }

    private TrainingBlock read(ResultSet rs)
            throws SQLException {
        int id = rs.getInt("id");
        return new TrainingBlock(id,
            LocalDate.parse(
                rs.getString("start_date")),
            rs.getString("progression"),
            rs.getString("split_name"),
            BlockOrigin.valueOf(
                rs.getString("origin")),
            readWeeks(id));
    }

    private List<Week> readWeeks(int blockId) {
        List<Week> weeks = new ArrayList<>();
        String sql = "SELECT id, number FROM week"
            + " WHERE block_id = ?"
            + " ORDER BY number";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, blockId);
            try (ResultSet rs =
                    s.executeQuery()) {
                while (rs.next()) {
                    weeks.add(new Week(
                        rs.getInt("number"),
                        readSessions(
                            rs.getInt("id"))));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the block's"
                + " weeks", e);
        }
        return weeks;
    }

    private List<Session> readSessions(
            int weekId) {
        List<Session> sessions =
                new ArrayList<>();
        String sql = "SELECT id, date, focus,"
            + " adjustment_note FROM"
            + " planned_session"
            + " WHERE week_id = ?"
            + " ORDER BY date, id";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, weekId);
            try (ResultSet rs =
                    s.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    Session session =
                        new Session(id,
                            LocalDate.parse(
                                rs.getString(
                                    "date")),
                            rs.getString("focus"),
                            readPrescriptions(
                                    id));
                    String note = rs.getString(
                            "adjustment_note");
                    if (note != null
                            && !note.isBlank()) {
                        session
                            .setAdjustmentNote(
                                note);
                    }
                    sessions.add(session);
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read a week's"
                + " sessions", e);
        }
        return sessions;
    }

    private List<ExercisePrescription>
            readPrescriptions(int sessionId) {
        List<ExercisePrescription> found =
                new ArrayList<>();
        String sql = "SELECT id, exercise_id,"
            + " sets, reps, target_rpe, load_kg"
            + " FROM exercise_prescription"
            + " WHERE session_id = ?"
            + " ORDER BY prescription_order, id";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, sessionId);
            try (ResultSet rs =
                    s.executeQuery()) {
                while (rs.next()) {
                    found.add(
                        readPrescription(rs));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read a session's"
                + " prescriptions", e);
        }
        return found;
    }

    private ExercisePrescription
            readPrescription(ResultSet rs)
            throws SQLException {
        int exerciseId =
                rs.getInt("exercise_id");
        Exercise exercise = findExercise(
                exerciseId);

        Double rpe = rs.getObject("target_rpe")
                == null
                    ? null
                    : rs.getDouble("target_rpe");

        return new ExercisePrescription(
            rs.getInt("id"), exercise,
            rs.getInt("sets"), rs.getInt("reps"),
            rpe, rs.getDouble("load_kg"));
    }

    /**
     * The exercise from the catalog, which is
     * where exercise facts live. A plan holding
     * an id the catalog no longer knows is a
     * broken database rather than a plan with a
     * missing field, so it fails loudly.
     */
    private Exercise findExercise(int id) {
        for (Exercise exercise
                : catalog.all()) {
            if (exercise.id() == id) {
                return exercise;
            }
        }
        throw new PersistenceException(
            "A stored prescription refers to"
            + " exercise " + id + ", which is"
            + " not in the catalog");
    }
}
