package ca.yorku.eecs3311.spotter.domain;

/**
 * Shared arithmetic for the progression schemes.
 *
 * Each one starts from the same place, the load
 * that reps at an RPE imply against the current
 * maximum, and differs in how much it adds per
 * week and how much slack it allows.
 */
abstract class AbstractProgression
        implements ProgressionStrategy {

    private final RPEChart chart = new RPEChart();

    /** Kilograms added per week of the block. */
    protected abstract double weeklyIncrement();

    /** Fraction of the target allowed either way. */
    protected abstract double tolerance();

    @Override
    public Range loadRange(double e1rm, int reps,
                           double rpe, int week) {
        if (e1rm <= 0) {
            throw new IllegalArgumentException(
                "A progression needs an"
                + " estimated maximum above"
                + " zero, but was given "
                + e1rm);
        }
        if (week < 1) {
            throw new IllegalArgumentException(
                    "Weeks are numbered from 1");
        }
        double percent =
            chart.percentOf1RM(reps, rpe) / 100.0;
        double target = e1rm * percent
            + weeklyIncrement() * (week - 1);

        double slack = target * tolerance();
        return new Range(
                Math.max(0, target - slack),
                target + slack);
    }
}
