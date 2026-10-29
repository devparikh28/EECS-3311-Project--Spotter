package ca.yorku.eecs3311.spotter.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out what to attempt on a heavy day.
 *
 * Entirely arithmetic. The agent may later
 * explain these numbers in terms of how the block
 * went, but it never produces them: PlanValidator
 * checks that every figure in an explanation
 * matches what this class calculated.
 */
public final class AttemptCalculator {

    /** Opener, a weight that is certain. */
    public static final double OPENER_PCT = 0.91;

    /** Second, usually a personal best. */
    public static final double SECOND_PCT = 0.96;

    /** Third, a reach. */
    public static final double THIRD_PCT = 1.01;

    /**
     * Smallest change that can actually be loaded
     * on a bar, in kilograms. Competition plates
     * go down to 1.25 kg a side.
     */
    public static final double PLATE_INCREMENT =
            2.5;

    /** Warm up percentages of the top single. */
    private static final double[] WARMUP_PCTS = {
            0.40, 0.55, 0.70, 0.80, 0.88, 0.94};

    /**
     * Three attempts from an estimated maximum.
     *
     * @param lift the lift
     * @param e1rm current estimated one rep max
     */
    public MeetAttempts calculateMeetAttempts(
            Lift lift, double e1rm) {
        requireUsable(e1rm);
        double opener =
                roundToPlate(e1rm * OPENER_PCT);
        double second =
                roundToPlate(e1rm * SECOND_PCT);
        double third =
                roundToPlate(e1rm * THIRD_PCT);

        second = atLeastOneIncrementAbove(
                second, opener);
        third = atLeastOneIncrementAbove(
                third, second);

        String rationale = String.format(
            "From an estimated %.1f kg max:"
            + " open at %.0f percent, second at"
            + " %.0f percent, third at %.0f"
            + " percent, each rounded to the"
            + " nearest %.1f kg.",
            e1rm, OPENER_PCT * 100,
            SECOND_PCT * 100, THIRD_PCT * 100,
            PLATE_INCREMENT);

        return new MeetAttempts(lift, opener,
                second, third, rationale);
    }

    /**
     * A top single with its warm ups, for a
     * lifter with no meet date.
     */
    public TestDayPlan projectTestDay(
            Lift lift, double e1rm) {
        requireUsable(e1rm);
        double topSingle =
                roundToPlate(e1rm * SECOND_PCT);

        List<Double> warmups = new ArrayList<>();
        double previous = 0;
        for (double pct : WARMUP_PCTS) {
            double load = roundToPlate(
                    topSingle * pct);
            if (load > 0 && load < topSingle
                    && load > previous) {
                warmups.add(load);
                previous = load;
            }
        }

        String rationale = String.format(
            "From an estimated %.1f kg max, a"
            + " top single at %.0f percent."
            + " Attempting the estimate itself"
            + " risks a miss; this is a weight"
            + " that should move.",
            e1rm, SECOND_PCT * 100);

        return new TestDayPlan(lift, topSingle,
                warmups, rationale);
    }

    /**
     * The nearest weight that can actually be
     * loaded. Half a kilogram cannot be put on a
     * bar, so an unrounded figure is a number
     * nobody can lift.
     */
    public double roundToPlate(double kg) {
        if (kg <= 0) {
            return 0;
        }
        return Math.round(kg / PLATE_INCREMENT)
                * PLATE_INCREMENT;
    }

    /**
     * Keeps attempts strictly increasing. Rounding
     * can collapse two close percentages onto the
     * same weight, and taking the same weight
     * twice at a meet is a wasted attempt.
     */
    private double atLeastOneIncrementAbove(
            double value, double floor) {
        if (value > floor) {
            return value;
        }
        return floor + PLATE_INCREMENT;
    }

    private static void requireUsable(
            double e1rm) {
        if (e1rm <= 0) {
            throw new IllegalArgumentException(
                "An estimated maximum above zero"
                + " is needed to plan attempts,"
                + " but was given " + e1rm);
        }
    }
}
