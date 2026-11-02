package ca.yorku.eecs3311.spotter
        .infrastructure.catalog;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain.FoodItem;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .PersistenceException;

/** The food catalog, stored in SQLite. */
public final class SqliteFoodCatalog
        implements FoodCatalog {

    private final Database database;

    /**
     * Read once and kept, for the same reason
     * the exercise catalog is: a meal suggestion
     * asks for several foods by name and every
     * one of those was a full table read.
     */
    private List<FoodItem> cached;

    public SqliteFoodCatalog(Database database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "A database is required");
        }
        this.database = database;
    }

    @Override
    public Optional<FoodItem> byName(
            String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim()
                .toLowerCase(Locale.ROOT);
        for (FoodItem food : all()) {
            if (food.name()
                    .toLowerCase(Locale.ROOT)
                    .equals(wanted)) {
                return Optional.of(food);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<FoodItem> all() {
        if (cached == null) {
            cached = readAll();
        }
        return cached;
    }

    @Override
    public List<FoodItem> permittedBy(
            DietaryConstraints constraints) {
        if (constraints == null) {
            return all();
        }
        List<FoodItem> allowed =
                new ArrayList<>();
        for (FoodItem food : all()) {
            if (constraints.permits(food)) {
                allowed.add(food);
            }
        }
        return List.copyOf(allowed);
    }

    @Override
    public List<FoodItem> closestMatches(
            String name, int limit) {
        if (name == null || name.isBlank()
                || limit <= 0) {
            return List.of();
        }
        String wanted = name.trim()
                .toLowerCase(Locale.ROOT);
        List<FoodItem> matches =
                new ArrayList<>();
        for (FoodItem food : all()) {
            String candidate = food.name()
                    .toLowerCase(Locale.ROOT);
            if (candidate.contains(wanted)
                    || wanted.contains(
                        candidate)) {
                matches.add(food);
            }
        }
        matches.sort(Comparator.comparingInt(
            food -> food.name().length()));
        return matches.size() <= limit
                ? List.copyOf(matches)
                : List.copyOf(matches.subList(
                        0, limit));
    }

    @Override
    public FoodItem add(FoodItem food) {
        if (food == null) {
            throw new IllegalArgumentException(
                    "A food is required");
        }
        String sql = "INSERT OR IGNORE INTO"
            + " food_item (name, calories_100g,"
            + " protein_100g, carbs_100g,"
            + " fat_100g, vegetarian,"
            + " contains_dairy, contains_egg)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            MacroTotals per100g = food.per100g();
            s.setString(1, food.name());
            s.setDouble(2, per100g.calories());
            s.setDouble(3, per100g.proteinG());
            s.setDouble(4, per100g.carbsG());
            s.setDouble(5, per100g.fatG());
            s.setInt(6, food.vegetarian()
                    ? 1 : 0);
            s.setInt(7, food.containsDairy()
                    ? 1 : 0);
            s.setInt(8, food.containsEgg()
                    ? 1 : 0);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(
                    "Could not save a food", e);
        }
        cached = null;
        return food;
    }

    private List<FoodItem> readAll() {
        String sql = "SELECT name,"
            + " calories_100g, protein_100g,"
            + " carbs_100g, fat_100g,"
            + " vegetarian, contains_dairy,"
            + " contains_egg FROM food_item"
            + " ORDER BY name";
        List<FoodItem> found = new ArrayList<>();
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql);
                ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                found.add(new FoodItem(
                    rs.getString("name"),
                    new MacroTotals(
                        rs.getDouble(
                            "calories_100g"),
                        rs.getDouble(
                            "protein_100g"),
                        rs.getDouble(
                            "carbs_100g"),
                        rs.getDouble(
                            "fat_100g")),
                    rs.getInt("vegetarian") == 1,
                    rs.getInt("contains_dairy")
                            == 1,
                    rs.getInt("contains_egg")
                            == 1));
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                    "Could not read the food"
                    + " catalog", e);
        }
        return List.copyOf(found);
    }
}
