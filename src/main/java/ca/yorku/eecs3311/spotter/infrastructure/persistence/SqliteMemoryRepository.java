package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .MemoryNote;

/** The coach's memory, stored in SQLite. */
public final class SqliteMemoryRepository
        implements MemoryRepository {

    private final Database database;

    public SqliteMemoryRepository(
            Database database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "A database is required");
        }
        this.database = database;
    }

    @Override
    public MemoryNote save(MemoryNote note) {
        if (note == null) {
            throw new IllegalArgumentException(
                    "A note is required");
        }
        String sql = "INSERT INTO memory_note"
            + " (created_at, content)"
            + " VALUES (?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql,
                        Statement
                            .RETURN_GENERATED_KEYS)) {
            s.setString(1, note.createdAt()
                    .toString());
            s.setString(2, note.content());
            s.executeUpdate();
            try (ResultSet keys =
                    s.getGeneratedKeys()) {
                if (keys.next()) {
                    return new MemoryNote(
                        keys.getInt(1),
                        note.createdAt(),
                        note.content());
                }
            }
            throw new PersistenceException(
                "Saving a note returned no id");
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save the note", e);
        }
    }

    @Override
    public Optional<MemoryNote> findById(
            int id) {
        String sql = "SELECT id, created_at,"
            + " content FROM memory_note"
            + " WHERE id = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, id);
            try (ResultSet rs =
                    s.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(read(rs));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the note", e);
        }
        return Optional.empty();
    }

    @Override
    public List<MemoryNote> findAll() {
        return query("SELECT id, created_at,"
            + " content FROM memory_note"
            + " ORDER BY created_at DESC,"
            + " id DESC", 0);
    }

    @Override
    public List<MemoryNote> recent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return query("SELECT id, created_at,"
            + " content FROM memory_note"
            + " ORDER BY created_at DESC,"
            + " id DESC LIMIT ?", limit);
    }

    private List<MemoryNote> query(String sql,
                                   int limit) {
        List<MemoryNote> notes =
                new ArrayList<>();
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            if (limit > 0) {
                s.setInt(1, limit);
            }
            try (ResultSet rs =
                    s.executeQuery()) {
                while (rs.next()) {
                    notes.add(read(rs));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the notes", e);
        }
        return List.copyOf(notes);
    }

    @Override
    public int count() {
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(
                        "SELECT COUNT(*) FROM"
                        + " memory_note");
                ResultSet rs = s.executeQuery()) {
            return rs.next()
                    ? rs.getInt(1)
                    : 0;
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not count the notes", e);
        }
    }

    @Override
    public void delete(int id) {
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(
                        "DELETE FROM memory_note"
                        + " WHERE id = ?")) {
            s.setInt(1, id);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not remove the note", e);
        }
    }

    @Override
    public void clear() {
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(
                        "DELETE FROM"
                        + " memory_note")) {
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not clear the memory", e);
        }
    }

    private static MemoryNote read(ResultSet rs)
            throws SQLException {
        return new MemoryNote(rs.getInt("id"),
            LocalDateTime.parse(
                rs.getString("created_at")),
            rs.getString("content"));
    }
}
