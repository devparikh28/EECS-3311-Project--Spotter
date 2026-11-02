package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .Limitation;
import ca.yorku.eecs3311.spotter.domain
        .SplitPreference;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter.domain
        .WeightUnit;

/**
 * The lifter's profile, stored in SQLite.
 *
 * A profile spans four tables, because equipment
 * and limitations are lists and a limitation
 * carries lists of its own. Saving is therefore a
 * delete and rewrite of the children rather than
 * an attempt to work out which rows changed:
 * profiles are saved once in a while by one
 * person, so the simple approach is the right
 * one, and it cannot leave an orphan behind.
 */
public final class SqliteProfileRepository
        implements ProfileRepository {

    private final Database database;

    public SqliteProfileRepository(
            Database database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "A database is required");
        }
        this.database = database;
    }

    @Override
    public LifterProfile save(
            LifterProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException(
                    "A profile is required");
        }
        int id = profile.id() > 0
                ? update(profile)
                : insert(profile);
        saveEquipment(id, profile.equipment());
        saveLimitations(id,
                profile.limitations());
        return findById(id).orElseThrow();
    }

    private int insert(LifterProfile profile) {
        String sql = "INSERT INTO lifter_profile"
            + " (bodyweight_kg, weight_class_kg,"
            + " goal, experience,"
            + " split_preference, display_unit,"
            + " meet_date,"
            + " training_days_per_week,"
            + " vegetarian, allows_dairy,"
            + " allows_eggs)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?,"
            + " ?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql,
                        Statement
                            .RETURN_GENERATED_KEYS)) {
            bind(s, profile);
            s.executeUpdate();
            try (ResultSet keys =
                    s.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new PersistenceException(
                "Saving a profile returned no"
                + " id");
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save the profile", e);
        }
    }

    private int update(LifterProfile profile) {
        String sql = "INSERT OR REPLACE INTO"
            + " lifter_profile"
            + " (bodyweight_kg, weight_class_kg,"
            + " goal, experience,"
            + " split_preference, display_unit,"
            + " meet_date,"
            + " training_days_per_week,"
            + " vegetarian, allows_dairy,"
            + " allows_eggs, id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?,"
            + " ?, ?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            bind(s, profile);
            s.setInt(12, profile.id());
            s.executeUpdate();
            return profile.id();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not update the profile",
                e);
        }
    }

    private static void bind(
            PreparedStatement s,
            LifterProfile profile)
            throws SQLException {
        s.setDouble(1, profile.bodyweightKg());
        if (profile.weightClassKg()
                .isPresent()) {
            s.setDouble(2, profile
                    .weightClassKg().get());
        } else {
            s.setNull(2, Types.REAL);
        }
        s.setString(3, profile.goal().name());
        s.setString(4,
                profile.experience().name());
        s.setString(5, profile
                .splitPreference().name());
        s.setString(6,
                profile.displayUnit().name());
        if (profile.meetDate().isPresent()) {
            s.setString(7, profile.meetDate()
                    .get().toString());
        } else {
            s.setNull(7, Types.VARCHAR);
        }
        s.setInt(8,
                profile.trainingDaysPerWeek());
        DietaryConstraints diet =
                profile.diet();
        s.setInt(9,
                diet.vegetarian() ? 1 : 0);
        s.setInt(10,
                diet.allowsDairy() ? 1 : 0);
        s.setInt(11,
                diet.allowsEggs() ? 1 : 0);
    }

    private void saveEquipment(
            int profileId,
            Set<Equipment> equipment) {
        execute("DELETE FROM profile_equipment"
            + " WHERE profile_id = ?",
            profileId);

        String sql = "INSERT OR IGNORE INTO"
            + " profile_equipment (profile_id,"
            + " equipment) VALUES (?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            for (Equipment item : equipment) {
                s.setInt(1, profileId);
                s.setString(2, item.name());
                s.addBatch();
            }
            s.executeBatch();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save the profile's"
                + " equipment", e);
        }
    }

    private void saveLimitations(
            int profileId,
            List<Limitation> limitations) {
        execute("DELETE FROM profile_limitation"
            + " WHERE profile_id = ?",
            profileId);

        for (Limitation limitation
                : limitations) {
            int id = insertLimitation(profileId,
                    limitation);
            insertChildren(
                "limitation_pattern",
                "pattern", id,
                limitation.avoidPatterns());
            insertChildren(
                "limitation_exercise",
                "exercise_name", id,
                limitation.avoidExercises());
        }
    }

    private int insertLimitation(
            int profileId,
            Limitation limitation) {
        String sql = "INSERT INTO"
            + " profile_limitation (profile_id,"
            + " description, painful)"
            + " VALUES (?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql,
                        Statement
                            .RETURN_GENERATED_KEYS)) {
            s.setInt(1, profileId);
            s.setString(2,
                    limitation.description());
            s.setInt(3,
                    limitation.painful() ? 1 : 0);
            s.executeUpdate();
            try (ResultSet keys =
                    s.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new PersistenceException(
                "Saving a limitation returned no"
                + " id");
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save a limitation",
                e);
        }
    }

    private void insertChildren(
            String table,
            String column,
            int limitationId,
            Set<String> values) {
        if (values.isEmpty()) {
            return;
        }
        String sql = "INSERT OR IGNORE INTO "
            + table + " (limitation_id, "
            + column + ") VALUES (?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            for (String value : values) {
                s.setInt(1, limitationId);
                s.setString(2, value);
                s.addBatch();
            }
            s.executeBatch();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save a limitation's "
                + column + " rows", e);
        }
    }

    @Override
    public Optional<LifterProfile> findById(
            int id) {
        String sql = "SELECT * FROM"
            + " lifter_profile WHERE id = ?";
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
                "Could not read the profile", e);
        }
        return Optional.empty();
    }

    @Override
    public List<LifterProfile> findAll() {
        List<LifterProfile> found =
                new ArrayList<>();
        String sql = "SELECT * FROM"
            + " lifter_profile ORDER BY id";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql);
                ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                found.add(read(rs));
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the profiles",
                e);
        }
        return List.copyOf(found);
    }

    @Override
    public Optional<LifterProfile> current() {
        List<LifterProfile> all = findAll();
        return all.isEmpty()
            ? Optional.empty()
            : Optional.of(
                all.get(all.size() - 1));
    }

    @Override
    public void delete(int id) {
        execute("DELETE FROM lifter_profile"
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

    private LifterProfile read(ResultSet rs)
            throws SQLException {
        int id = rs.getInt("id");

        LifterProfile.Builder builder =
            LifterProfile.with(
                TrainingGoal.valueOf(
                    rs.getString("goal")),
                ExperienceLevel.valueOf(
                    rs.getString("experience")))
                .id(id)
                .bodyweightKg(rs.getDouble(
                        "bodyweight_kg"))
                .splitPreference(
                    SplitPreference.valueOf(
                        rs.getString(
                            "split_preference")))
                .displayUnit(
                    WeightUnit.valueOf(
                        rs.getString(
                            "display_unit")))
                .trainingDaysPerWeek(
                    rs.getInt(
                        "training_days_per_week"))
                .equipment(readEquipment(id))
                .diet(new DietaryConstraints(
                    rs.getInt("vegetarian") == 1,
                    rs.getInt("allows_dairy")
                            == 1,
                    rs.getInt("allows_eggs")
                            == 1));

        double weightClass =
                rs.getDouble("weight_class_kg");
        if (!rs.wasNull()) {
            builder.weightClassKg(weightClass);
        }
        String meetDate =
                rs.getString("meet_date");
        if (meetDate != null
                && !meetDate.isBlank()) {
            builder.meetDate(
                    LocalDate.parse(meetDate));
        }
        for (Limitation limitation
                : readLimitations(id)) {
            builder.addLimitation(limitation);
        }
        return builder.build();
    }

    private Set<Equipment> readEquipment(
            int profileId) {
        Set<Equipment> equipment =
                new LinkedHashSet<>();
        String sql = "SELECT equipment FROM"
            + " profile_equipment"
            + " WHERE profile_id = ?"
            + " ORDER BY equipment";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, profileId);
            try (ResultSet rs =
                    s.executeQuery()) {
                while (rs.next()) {
                    equipment.add(
                        Equipment.valueOf(
                            rs.getString(1)));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the profile's"
                + " equipment", e);
        }
        return equipment;
    }

    private List<Limitation> readLimitations(
            int profileId) {
        List<Limitation> limitations =
                new ArrayList<>();
        String sql = "SELECT id, description,"
            + " painful FROM profile_limitation"
            + " WHERE profile_id = ?"
            + " ORDER BY id";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, profileId);
            try (ResultSet rs =
                    s.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    limitations.add(
                        new Limitation(
                            rs.getString(
                                "description"),
                            readChildren(
                                "limitation_pattern",
                                "pattern", id),
                            readChildren(
                                "limitation_exercise",
                                "exercise_name",
                                id),
                            rs.getInt("painful")
                                    == 1));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the profile's"
                + " limitations", e);
        }
        return limitations;
    }

    private Set<String> readChildren(
            String table,
            String column,
            int limitationId) {
        Set<String> values =
                new LinkedHashSet<>();
        String sql = "SELECT " + column
            + " FROM " + table
            + " WHERE limitation_id = ?"
            + " ORDER BY " + column;
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, limitationId);
            try (ResultSet rs =
                    s.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read a limitation's "
                + column + " rows", e);
        }
        return values;
    }
}
