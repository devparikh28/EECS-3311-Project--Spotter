package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoverySource;

/** Recovery readings stored in SQLite. */
public final class SqliteRecoveryRepository
        implements RecoveryRepository {

    private final Database database;

    public SqliteRecoveryRepository(
            Database database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "A database is required");
        }
        this.database = database;
    }

    @Override
    public RecoveryDay save(RecoveryDay day) {
        if (day == null) {
            throw new IllegalArgumentException(
                    "A recovery day is"
                    + " required");
        }
        String sql = "INSERT OR REPLACE INTO"
                + " recovery_day"
                + " (date, source,"
                + " recovery_score, hrv_ms,"
                + " sleep_hours, soreness,"
                + " energy)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1,
                    day.date().toString());
            s.setString(2,
                    day.source().name());
            setInt(s, 3, day.recoveryScore());
            setDouble(s, 4, day.hrvMs());
            setDouble(s, 5, day.sleepHours());
            setInt(s, 6, day.soreness());
            setInt(s, 7, day.energy());
            s.executeUpdate();
            return day;
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not save the recovery"
                + " day", e);
        }
    }

    private static void setInt(
            PreparedStatement s, int index,
            OptionalInt value)
            throws SQLException {
        if (value.isPresent()) {
            s.setInt(index, value.getAsInt());
        } else {
            s.setNull(index, Types.INTEGER);
        }
    }

    private static void setDouble(
            PreparedStatement s, int index,
            OptionalDouble value)
            throws SQLException {
        if (value.isPresent()) {
            s.setDouble(index,
                    value.getAsDouble());
        } else {
            s.setNull(index, Types.REAL);
        }
    }

    @Override
    public Optional<RecoveryDay> findByDate(
            LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM recovery_day"
                + " WHERE date = ?";
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            s.setString(1, date.toString());
            try (ResultSet rs = s.executeQuery()) {
                return rs.next()
                    ? Optional.of(dayFrom(rs))
                    : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read the recovery"
                + " day", e);
        }
    }

    @Override
    public List<RecoveryDay> findSince(
            LocalDate since) {
        String sql = since == null
            ? "SELECT * FROM recovery_day"
              + " ORDER BY date"
            : "SELECT * FROM recovery_day"
              + " WHERE date >= ?"
              + " ORDER BY date";
        List<RecoveryDay> found =
                new ArrayList<>();
        try (PreparedStatement s =
                database.connection()
                    .prepareStatement(sql)) {
            if (since != null) {
                s.setString(1,
                        since.toString());
            }
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    found.add(dayFrom(rs));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(
                "Could not read recovery days",
                e);
        }
        return List.copyOf(found);
    }

    @Override
    public List<RecoveryDay> findAll() {
        return findSince(null);
    }

    private RecoveryDay dayFrom(ResultSet rs)
            throws SQLException {
        RecoveryDay.Builder builder =
            RecoveryDay.on(
                LocalDate.parse(
                        rs.getString("date")),
                RecoverySource.valueOf(
                        rs.getString("source")));

        int score = rs.getInt("recovery_score");
        if (!rs.wasNull()) {
            builder.recoveryScore(score);
        }
        double hrv = rs.getDouble("hrv_ms");
        if (!rs.wasNull()) {
            builder.hrvMs(hrv);
        }
        double sleep =
                rs.getDouble("sleep_hours");
        if (!rs.wasNull()) {
            builder.sleepHours(sleep);
        }
        int soreness = rs.getInt("soreness");
        if (!rs.wasNull()) {
            builder.soreness(soreness);
        }
        int energy = rs.getInt("energy");
        if (!rs.wasNull()) {
            builder.energy(energy);
        }
        return builder.build();
    }
}
