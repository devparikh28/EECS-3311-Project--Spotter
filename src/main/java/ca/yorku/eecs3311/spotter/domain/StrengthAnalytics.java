package ca.yorku.eecs3311.spotter.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Turns logged training into the figures the rest
 * of the system reasons over.
 *
 * Everything here is arithmetic with a correct
 * answer, so none of it involves the model. The
 * block generation agent is handed these numbers
 * as evidence rather than being asked to work
 * them out, which is the central split in the
 * design.
 *
 * Sets logged without an RPE are skipped rather
 * than guessed at. A set of five at an unknown
 * effort could be anything from easy to a limit
 * attempt, and inventing a value there would
 * quietly corrupt every figure downstream.
 */
public final class StrengthAnalytics {

    private final RPEChart rpeChart;

    public StrengthAnalytics() {
        this(new RPEChart());
    }

    public StrengthAnalytics(RPEChart rpeChart) {
        if (rpeChart == null) {
            throw new IllegalArgumentException(
                    "An RPE chart is required");
        }
        this.rpeChart = rpeChart;
    }

    /**
     * The one rep max this set implies.
     *
     * @throws IllegalArgumentException when the
     *         set has no RPE, or its reps and RPE
     *         fall outside the chart
     */
    public double computeE1RM(SetEntry set) {
        if (set == null) {
            throw new IllegalArgumentException(
                    "A set is required");
        }
        double rpe = set.rpe().orElseThrow(() ->
            new IllegalArgumentException(
                "Cannot estimate a one rep max"
                + " from a set with no RPE: "
                + set));
        double percent = rpeChart.percentOf1RM(
                set.reps(), rpe);
        return set.weightKg() / (percent / 100.0);
    }

    /** True when this set can be estimated from. */
    public boolean canEstimate(SetEntry set) {
        return set != null
                && set.hasRpe()
                && rpeChart.covers(set.reps(),
                        set.rpe().orElseThrow());
    }

    /**
     * The best estimated one rep max on each day
     * this lift was trained, oldest first.
     *
     * One point per session rather than per set,
     * because a day's best set is what represents
     * that day's strength.
     */
    public List<Point> e1rmSeries(
            Lift lift,
            List<WorkoutSession> history) {
        Map<LocalDate, Double> bestByDate =
                new TreeMap<>();
        for (WorkoutSession session
                : safe(history)) {
            for (SetEntry set
                    : session.setsFor(lift)) {
                if (!canEstimate(set)) {
                    continue;
                }
                double estimate =
                        computeE1RM(set);
                bestByDate.merge(session.date(),
                        estimate, Math::max);
            }
        }
        List<Point> series = new ArrayList<>();
        bestByDate.forEach((date, value) ->
                series.add(
                    new Point(date, value)));
        return List.copyOf(series);
    }

    /**
     * Total weight moved on this lift per week,
     * oldest first. Each point is dated at the
     * Monday of its week.
     */
    public List<Point> weeklyTonnage(
            Lift lift,
            List<WorkoutSession> history) {
        Map<LocalDate, Double> byWeek =
                new TreeMap<>();
        for (WorkoutSession session
                : safe(history)) {
            double tonnage =
                    session.tonnageFor(lift);
            if (tonnage <= 0) {
                continue;
            }
            byWeek.merge(
                    weekStart(session.date()),
                    tonnage, Double::sum);
        }
        List<Point> series = new ArrayList<>();
        byWeek.forEach((week, total) ->
                series.add(
                    new Point(week, total)));
        return List.copyOf(series);
    }

    /**
     * The set in this history implying the
     * highest one rep max for this lift.
     *
     * Empty when the lift was never trained, or
     * every set of it was logged without an RPE.
     */
    public Optional<SetEntry> bestSet(
            Lift lift,
            List<WorkoutSession> history) {
        SetEntry best = null;
        double bestEstimate = -1;
        for (WorkoutSession session
                : safe(history)) {
            for (SetEntry set
                    : session.setsFor(lift)) {
                if (!canEstimate(set)) {
                    continue;
                }
                double estimate =
                        computeE1RM(set);
                if (estimate > bestEstimate) {
                    bestEstimate = estimate;
                    best = set;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The current estimated one rep max for each
     * competition lift, from the best set in the
     * history supplied.
     *
     * A lift with no usable set is left out
     * rather than reported as zero, so a caller
     * can tell "never trained" from "very weak".
     */
    public Map<Lift, Double> currentE1RMs(
            List<WorkoutSession> history) {
        Map<Lift, Double> current =
                new EnumMap<>(Lift.class);
        for (Lift lift : Lift.values()) {
            bestSet(lift, history).ifPresent(
                set -> current.put(lift,
                        computeE1RM(set)));
        }
        return Map.copyOf(current);
    }

    /**
     * How much the estimated one rep max moved
     * across the series, in kilograms. Positive
     * means stronger.
     *
     * Empty when there are fewer than two points,
     * because one measurement is not a trend.
     */
    public Optional<Double> trend(
            List<Point> series) {
        if (series == null || series.size() < 2) {
            return Optional.empty();
        }
        List<Point> sorted =
                new ArrayList<>(series);
        sorted.sort(Comparator.comparing(
                Point::date));
        double first = sorted.get(0).value();
        double last = sorted
                .get(sorted.size() - 1).value();
        return Optional.of(last - first);
    }

    /** The Monday on or before this date. */
    static LocalDate weekStart(LocalDate date) {
        return date.with(
            TemporalAdjusters.previousOrSame(
                DayOfWeek.MONDAY));
    }

    private static List<WorkoutSession> safe(
            List<WorkoutSession> history) {
        return history == null
                ? List.of()
                : history;
    }
}
