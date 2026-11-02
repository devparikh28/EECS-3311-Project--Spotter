package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .DailyIntake;
import ca.yorku.eecs3311.spotter.domain
        .FoodEntry;
import ca.yorku.eecs3311.spotter.domain.FoodItem;
import ca.yorku.eecs3311.spotter.domain
        .MacroTarget;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.FoodCatalog;

/**
 * Nutrition, stored in SQLite.
 *
 * Foods are referenced by catalog row rather than
 * copied into the log, the same arrangement the
 * training blocks use. A logged entry therefore
 * cannot disagree with the catalog about what a
 * food contains, and correcting a figure in the
 * catalog corrects every day it appears in.
 */
public final class SqliteNutritionRepository
        implements NutritionRepository {

    private final Database database;
    private final FoodCatalog foods;

    public SqliteNutritionRepository(
            Database database,
            FoodCatalog foods) {
        if (database == null || foods == null) {
            throw new IllegalArgumentException(
                "A database and a food catalog"
                + " are required");
        }
        this.database = database;
        this.foods = foods;
    }

    @Override
    public DailyIntake save(
            DailyIntake intake) {
        if (intake == null) {
            throw new IllegalArgumentException(
                    "An intake is required");
        }
        ensureDay(intake.date());
        execute("DELETE FROM food_entry WHERE"
            + " intake_date = ?",
            intake.date().toString());

        for (FoodEntry entry
                : intake.entries()) {
            insert(intake.date(), entry);
        }
        return findByDate(intake.date())
                .orElseThrow();
    }

    @Override
    public DailyIntake log(LocalDate date,
                           FoodEntry entry) {
        if (date == null || entry == null) {
            throw new IllegalArgumentException(
                "A date and an entry are"
                + " required");
        }
        ensureDay(date);
        insert(date, entry);
        return findByDate(date).orElseThrow();
    }

    private void ensureDay(LocalDate date) {
        execute("INSERT OR IGNORE INTO"
            + " daily_intake (date)"
            + " VALUES (?)",
            date.toString());
    }

    /**
     * Writes one entry, resolving the food by
     * name so the log points at the catalog row
     * rather than at a copy of its figures.
     */
    private void insert(LocalDate date,
                        FoodEntry entry) {
        String sql = "INSERT INTO food_entry"
            + " (intake_date, food_item_id,"
            + " grams) VALUES (?, (SELECT id"
            + " FROM food_item WHERE name = ?),"
            + " ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1, date.toString());
            s.setString(2,
                    entry.food().name());
            s.setDouble(3, entry.grams());
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not log "
                + entry.food().name()
                + ". Foods must be in the"
                + " catalog before they can be"
                + " eaten.", e);
        }
    }

    @Override
    public Optional<DailyIntake> findByDate(
            LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        String sql = "SELECT 1 FROM daily_intake"
            + " WHERE date = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1, date.toString());
            try (ResultSet rs =
                    s.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the day", e);
        }
        return Optional.of(new DailyIntake(date,
                entriesOn(date)));
    }

    @Override
    public List<DailyIntake> findSince(
            LocalDate since) {
        List<DailyIntake> days =
                new ArrayList<>();
        String sql = "SELECT date FROM"
            + " daily_intake WHERE date >= ?"
            + " ORDER BY date";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1, since == null
                    ? "0000-01-01"
                    : since.toString());
            try (ResultSet rs =
                    s.executeQuery()) {
                while (rs.next()) {
                    LocalDate date =
                        LocalDate.parse(
                            rs.getString(1));
                    days.add(new DailyIntake(
                        date,
                        entriesOn(date)));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the days", e);
        }
        return List.copyOf(days);
    }

    private List<FoodEntry> entriesOn(
            LocalDate date) {
        List<FoodEntry> entries =
                new ArrayList<>();
        String sql = "SELECT f.name, e.grams"
            + " FROM food_entry e"
            + " JOIN food_item f"
            + "   ON f.id = e.food_item_id"
            + " WHERE e.intake_date = ?"
            + " ORDER BY e.id";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1, date.toString());
            try (ResultSet rs =
                    s.executeQuery()) {
                while (rs.next()) {
                    String name =
                            rs.getString("name");
                    FoodItem food = foods
                        .byName(name)
                        .orElseThrow(() ->
                            new PersistenceException(
                                "A logged entry"
                                + " names \""
                                + name + "\","
                                + " which is no"
                                + " longer in"
                                + " the"
                                + " catalog"));
                    entries.add(new FoodEntry(
                        food,
                        rs.getDouble("grams")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read a day's"
                + " entries", e);
        }
        return entries;
    }

    @Override
    public void delete(LocalDate date) {
        if (date == null) {
            return;
        }
        execute("DELETE FROM daily_intake WHERE"
            + " date = ?", date.toString());
    }

    @Override
    public Optional<MacroTarget> targetFor(
            int profileId) {
        String sql = "SELECT calories,"
            + " protein_g, carbs_g, fat_g FROM"
            + " macro_target WHERE profile_id"
            + " = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, profileId);
            try (ResultSet rs =
                    s.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(
                        new MacroTarget(
                            rs.getInt(
                                "calories"),
                            rs.getDouble(
                                "protein_g"),
                            rs.getDouble(
                                "carbs_g"),
                            rs.getDouble(
                                "fat_g")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the macro"
                + " target", e);
        }
        return Optional.empty();
    }

    @Override
    public MacroTarget saveTarget(
            int profileId,
            MacroTarget target) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "A target is required");
        }
        String sql = "INSERT OR REPLACE INTO"
            + " macro_target (profile_id,"
            + " calories, protein_g, carbs_g,"
            + " fat_g) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setInt(1, profileId);
            s.setInt(2, target.calories());
            s.setDouble(3, target.proteinG());
            s.setDouble(4, target.carbsG());
            s.setDouble(5, target.fatG());
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save the macro"
                + " target", e);
        }
        return target;
    }

    private void execute(String sql,
                         String argument) {
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1, argument);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not run: " + sql, e);
        }
    }
}
