package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tracks what was eaten against the daily target.
 *
 * Arithmetic only. The meal suggestion feature
 * asks this what is left and hands that figure to
 * the agent; the agent never works out the
 * remainder itself.
 */
public final class MacroTracker {

    /** Days in the adherence window. */
    public static final int WEEK_LENGTH = 7;

    private final MacroTarget target;
    private final Map<LocalDate, DailyIntake>
            byDate = new HashMap<>();

    public MacroTracker(MacroTarget target) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "A macro target is required");
        }
        this.target = target;
    }

    public MacroTarget target() {
        return target;
    }

    /**
     * Records something eaten and returns the
     * running total for that day.
     */
    public MacroTotals recordIntake(
            LocalDate date, FoodEntry entry) {
        if (date == null) {
            throw new IllegalArgumentException(
                    "A date is required");
        }
        return byDate.computeIfAbsent(date,
                DailyIntake::new).add(entry);
    }

    /** Everything eaten on a day. */
    public MacroTotals consumedOn(
            LocalDate date) {
        DailyIntake intake = byDate.get(date);
        return intake == null
                ? MacroTotals.ZERO
                : intake.totals();
    }

    /**
     * What is left of the target for a day.
     *
     * Floored at zero by MacroTotals, so eating
     * past the target leaves nothing remaining
     * rather than a negative amount for the meal
     * suggestion to chase.
     */
    public MacroTotals getRemaining(
            LocalDate date) {
        return target.asTotals()
                .minus(consumedOn(date));
    }

    /** The recorded intake for a day, if any. */
    public Optional<DailyIntake> intakeOn(
            LocalDate date) {
        return Optional.ofNullable(
                byDate.get(date));
    }

    /** Days in the week that have any entries. */
    public int daysLogged(LocalDate weekStart) {
        requireDate(weekStart);
        int logged = 0;
        for (int day = 0; day < WEEK_LENGTH;
                day++) {
            DailyIntake intake = byDate.get(
                    weekStart.plusDays(day));
            if (intake != null
                    && !intake.isEmpty()) {
                logged++;
            }
        }
        return logged;
    }

    /**
     * How closely the week matched the calorie
     * target, from 0 to 1.
     *
     * Each day scores 1 minus its relative error,
     * floored at 0, and the seven scores are
     * averaged. A day with nothing logged scores
     * zero, because an untracked day is not an
     * adhered to day; daysLogged() lets a caller
     * show how much of the week was recorded
     * alongside the figure.
     */
    public double weeklyAdherence(
            LocalDate weekStart) {
        requireDate(weekStart);
        double sum = 0;
        for (int day = 0; day < WEEK_LENGTH;
                day++) {
            sum += dayScore(
                    weekStart.plusDays(day));
        }
        return sum / WEEK_LENGTH;
    }

    private double dayScore(LocalDate date) {
        DailyIntake intake = byDate.get(date);
        if (intake == null || intake.isEmpty()) {
            return 0;
        }
        double actual =
                intake.totals().calories();
        double wanted = target.calories();
        double error = Math.abs(actual - wanted)
                / wanted;
        return Math.max(0, 1 - error);
    }

    private static void requireDate(
            LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException(
                    "A week start is required");
        }
    }
}
