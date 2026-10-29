package ca.yorku.eecs3311.spotter.domain;

import java.util.Map;

/**
 * The reps and RPE to percentage of one rep max
 * table.
 *
 * RPE is how hard a set felt: an RPE of 8 means
 * roughly two reps were left, 10 means none. So
 * five reps at RPE 8 is a known fraction of a
 * maximum, and that fraction is what lets a
 * working set estimate a one rep max without
 * anyone having to attempt one.
 *
 * The figures are the widely used chart. They are
 * a lookup rather than a formula on purpose: the
 * relationship is not linear, and a formula that
 * looks close in the middle is badly wrong at the
 * edges.
 */
public final class RPEChart {

    /** Lowest RPE the chart covers. */
    public static final double MIN_RPE = 6.0;

    /** Highest RPE, meaning no reps left. */
    public static final double MAX_RPE = 10.0;

    /** Most reps the chart covers. */
    public static final int MAX_REPS = 12;

    /**
     * Percentages by reps, from RPE 10 down to
     * RPE 6 in half point steps.
     */
    private static final Map<Integer, double[]>
            CHART = Map.ofEntries(
        Map.entry(1, new double[] {
            100.0, 97.8, 95.5, 93.9, 92.2,
            90.7, 89.2, 87.8, 86.3}),
        Map.entry(2, new double[] {
            95.5, 93.9, 92.2, 90.7, 89.2,
            87.8, 86.3, 85.0, 83.7}),
        Map.entry(3, new double[] {
            92.2, 90.7, 89.2, 87.8, 86.3,
            85.0, 83.7, 82.4, 81.1}),
        Map.entry(4, new double[] {
            89.2, 87.8, 86.3, 85.0, 83.7,
            82.4, 81.1, 79.9, 78.6}),
        Map.entry(5, new double[] {
            86.3, 85.0, 83.7, 82.4, 81.1,
            79.9, 78.6, 77.4, 76.2}),
        Map.entry(6, new double[] {
            83.7, 82.4, 81.1, 79.9, 78.6,
            77.4, 76.2, 75.1, 73.9}),
        Map.entry(7, new double[] {
            81.1, 79.9, 78.6, 77.4, 76.2,
            75.1, 73.9, 72.3, 70.7}),
        Map.entry(8, new double[] {
            78.6, 77.4, 76.2, 75.1, 73.9,
            72.3, 70.7, 69.4, 68.0}),
        Map.entry(9, new double[] {
            76.2, 75.1, 73.9, 72.3, 70.7,
            69.4, 68.0, 66.7, 65.3}),
        Map.entry(10, new double[] {
            73.9, 72.3, 70.7, 69.4, 68.0,
            66.7, 65.3, 64.0, 62.6}),
        Map.entry(11, new double[] {
            70.7, 69.4, 68.0, 66.7, 65.3,
            64.0, 62.6, 61.3, 59.9}),
        Map.entry(12, new double[] {
            68.0, 66.7, 65.3, 64.0, 62.6,
            61.3, 59.9, 58.6, 57.4}));

    /**
     * The percentage of a one rep max that this
     * many reps at this RPE represents.
     *
     * @param reps reps performed, 1 to 12
     * @param rpe  how hard it felt, 6 to 10
     * @return the percentage, for example 81.1
     * @throws IllegalArgumentException when the
     *         set falls outside the chart
     */
    public double percentOf1RM(
            int reps, double rpe) {
        requireCovered(reps, rpe);
        double[] row = CHART.get(reps);
        return row[columnFor(rpe)];
    }

    /**
     * True when a set of this shape can be
     * estimated from. Callers use this to skip a
     * set rather than guess at it.
     */
    public boolean covers(int reps, double rpe) {
        return reps >= 1 && reps <= MAX_REPS
                && rpe >= MIN_RPE
                && rpe <= MAX_RPE
                && isHalfPoint(rpe);
    }

    private void requireCovered(
            int reps, double rpe) {
        if (reps < 1 || reps > MAX_REPS) {
            throw new IllegalArgumentException(
                "The chart covers 1 to "
                + MAX_REPS + " reps, but was"
                + " given " + reps
                + ". A set this long cannot"
                + " estimate a one rep max"
                + " reliably.");
        }
        if (rpe < MIN_RPE || rpe > MAX_RPE) {
            throw new IllegalArgumentException(
                "The chart covers RPE "
                + MIN_RPE + " to " + MAX_RPE
                + ", but was given " + rpe
                + ". Below RPE " + MIN_RPE
                + " a set is too easy to"
                + " estimate from.");
        }
        if (!isHalfPoint(rpe)) {
            throw new IllegalArgumentException(
                "RPE must be a whole or half"
                + " point, but was " + rpe);
        }
    }

    private static boolean isHalfPoint(
            double rpe) {
        double doubled = rpe * 2;
        return Math.abs(doubled
                - Math.round(doubled)) < 1e-9;
    }

    /**
     * Column index for an RPE. The table runs
     * from RPE 10 at index 0 down to RPE 6 at
     * index 8, in half point steps.
     */
    private static int columnFor(double rpe) {
        return (int) Math.round(
                (MAX_RPE - rpe) * 2);
    }
}
