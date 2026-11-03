package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter
        .infrastructure.csv.ColumnMapping;
import ca.yorku.eecs3311.spotter
        .infrastructure.csv.SourceKind;

/** Saved column mappings, stored in SQLite. */
public final class SqliteImportProfileRepository
        implements ImportProfileRepository {

    private final Database database;

    public SqliteImportProfileRepository(
            Database database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "A database is required");
        }
        this.database = database;
    }

    @Override
    public void save(String name,
                     SourceKind kind,
                     ColumnMapping mapping) {
        if (name == null || name.isBlank()
                || mapping == null) {
            throw new IllegalArgumentException(
                "A name and a mapping are"
                + " required");
        }
        String sql = "INSERT OR REPLACE INTO"
            + " import_profile (name,"
            + " import_kind, mapping)"
            + " VALUES (?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1, name.trim());
            s.setString(2, kind == null
                    ? SourceKind.UNKNOWN.name()
                    : kind.name());
            s.setString(3,
                    mapping.serialise());
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save the import"
                + " profile", e);
        }
    }

    @Override
    public Optional<ColumnMapping> byName(
            String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT mapping FROM"
            + " import_profile WHERE name = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1, name.trim());
            try (ResultSet rs =
                    s.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(
                        ColumnMapping.parse(
                            rs.getString(1)));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the import"
                + " profile", e);
        }
        return Optional.empty();
    }

    @Override
    public List<String> names() {
        List<String> found = new ArrayList<>();
        String sql = "SELECT name FROM"
            + " import_profile ORDER BY name";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql);
                ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                found.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not list the import"
                + " profiles", e);
        }
        return List.copyOf(found);
    }

    @Override
    public void delete(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(
                        "DELETE FROM"
                        + " import_profile"
                        + " WHERE name = ?")) {
            s.setString(1, name.trim());
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not remove the import"
                + " profile", e);
        }
    }
}
