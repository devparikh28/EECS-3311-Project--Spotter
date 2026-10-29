package ca.yorku.eecs3311.spotter
        .infrastructure.catalog;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .PersistenceException;

/** The exercise catalog, stored in SQLite. */
public final class SqliteExerciseCatalog
        implements ExerciseCatalog {

    private final Database database;

    public SqliteExerciseCatalog(
            Database database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "A database is required");
        }
        this.database = database;
    }

    @Override
    public Optional<Exercise> byName(
            String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim()
                .toLowerCase(Locale.ROOT);

        for (Exercise exercise : all()) {
            if (exercise.name()
                    .toLowerCase(Locale.ROOT)
                    .equals(wanted)) {
                return Optional.of(exercise);
            }
        }
        return byAlias(wanted);
    }

    private Optional<Exercise> byAlias(
            String wanted) {
        String sql = "SELECT exercise_id FROM"
                + " exercise_alias"
                + " WHERE LOWER(alias) = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1, wanted);
            try (ResultSet rs = s.executeQuery()) {
                if (rs.next()) {
                    return byId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                    "Could not look up an alias",
                    e);
        }
        return Optional.empty();
    }

    private Optional<Exercise> byId(int id) {
        for (Exercise exercise : all()) {
            if (exercise.id() == id) {
                return Optional.of(exercise);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Exercise> all() {
        String sql = "SELECT id, name,"
                + " movement_pattern,"
                + " competition_lift"
                + " FROM exercise ORDER BY name";
        List<Exercise> found = new ArrayList<>();
        try (Statement s = database.connection()
                     .createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                found.add(new Exercise(
                    id,
                    rs.getString("name"),
                    musclesFor(id),
                    rs.getString(
                        "movement_pattern"),
                    equipmentFor(id),
                    liftOf(rs.getString(
                        "competition_lift"))));
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the catalog", e);
        }
        return List.copyOf(found);
    }

    private static Lift liftOf(String value) {
        return value == null || value.isBlank()
                ? null
                : Lift.valueOf(value);
    }

    private Set<Equipment> equipmentFor(int id) {
        Set<Equipment> equipment =
                new LinkedHashSet<>();
        String sql = "SELECT equipment FROM"
                + " exercise_equipment"
                + " WHERE exercise_id = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, id);
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    equipment.add(
                        Equipment.valueOf(
                            rs.getString(1)));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read equipment", e);
        }
        return equipment;
    }

    private Set<String> musclesFor(int id) {
        Set<String> muscles =
                new LinkedHashSet<>();
        String sql = "SELECT muscle_group FROM"
                + " exercise_muscle"
                + " WHERE exercise_id = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, id);
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    muscles.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read muscle groups", e);
        }
        return muscles;
    }

    @Override
    public List<Exercise> forLift(Lift lift) {
        List<Exercise> matching =
                new ArrayList<>();
        for (Exercise exercise : all()) {
            if (exercise.competitionLift()
                    .filter(l -> l == lift)
                    .isPresent()) {
                matching.add(exercise);
            }
        }
        return List.copyOf(matching);
    }

    @Override
    public List<Exercise> performableWith(
            Set<Equipment> available) {
        List<Exercise> matching =
                new ArrayList<>();
        for (Exercise exercise : all()) {
            if (exercise.isPerformableWith(
                    available)) {
                matching.add(exercise);
            }
        }
        return List.copyOf(matching);
    }

    /**
     * Ranks catalog names by edit distance to the
     * one given, so an unrecognised import name
     * can be offered sensible candidates.
     */
    @Override
    public List<Exercise> closestMatches(
            String name, int limit) {
        if (name == null || name.isBlank()
                || limit <= 0) {
            return List.of();
        }
        String wanted = name.trim()
                .toLowerCase(Locale.ROOT);
        List<Exercise> ranked =
                new ArrayList<>(all());
        ranked.sort(Comparator.comparingInt(
            e -> distance(wanted,
                e.name().toLowerCase(
                        Locale.ROOT))));
        return List.copyOf(ranked.subList(0,
                Math.min(limit, ranked.size())));
    }

    /** Levenshtein distance between two names. */
    static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1;
                    j <= b.length(); j++) {
                int cost = a.charAt(i - 1)
                        == b.charAt(j - 1)
                        ? 0 : 1;
                current[j] = Math.min(
                    Math.min(current[j - 1] + 1,
                             previous[j] + 1),
                    previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    @Override
    public Exercise add(Exercise exercise) {
        if (exercise == null) {
            throw new IllegalArgumentException(
                    "An exercise is required");
        }
        Optional<Exercise> existing =
                byName(exercise.name());
        if (existing.isPresent()) {
            return existing.get();
        }
        String sql = "INSERT INTO exercise"
                + " (name, movement_pattern,"
                + " competition_lift)"
                + " VALUES (?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql,
                        Statement
                          .RETURN_GENERATED_KEYS)) {
            s.setString(1, exercise.name());
            s.setString(2, exercise
                    .movementPattern()
                    .orElse(null));
            s.setString(3, exercise
                    .competitionLift()
                    .map(Enum::name)
                    .orElse(null));
            s.executeUpdate();
            int id;
            try (ResultSet keys =
                    s.getGeneratedKeys()) {
                keys.next();
                id = keys.getInt(1);
            }
            saveEquipment(id, exercise);
            saveMuscles(id, exercise);
            return byId(id).orElseThrow();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not add "
                + exercise.name(), e);
        }
    }

    private void saveEquipment(
            int id, Exercise exercise)
            throws SQLException {
        String sql = "INSERT OR IGNORE INTO"
                + " exercise_equipment"
                + " (exercise_id, equipment)"
                + " VALUES (?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            for (Equipment equipment
                    : exercise
                        .requiredEquipment()) {
                s.setInt(1, id);
                s.setString(2, equipment.name());
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private void saveMuscles(
            int id, Exercise exercise)
            throws SQLException {
        String sql = "INSERT OR IGNORE INTO"
                + " exercise_muscle"
                + " (exercise_id, muscle_group)"
                + " VALUES (?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            for (String muscle
                    : exercise.muscleGroups()) {
                s.setInt(1, id);
                s.setString(2, muscle);
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    @Override
    public void addAlias(
            String alias, Exercise exercise) {
        if (alias == null || alias.isBlank()
                || exercise == null) {
            throw new IllegalArgumentException(
                "An alias and an exercise are"
                + " required");
        }
        String sql = "INSERT OR REPLACE INTO"
                + " exercise_alias"
                + " (alias, exercise_id)"
                + " VALUES (?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1, alias.trim());
            s.setInt(2, exercise.id());
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not record the alias", e);
        }
    }
}
