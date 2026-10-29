package ca.yorku.eecs3311.spotter.domain;

/**
 * Fixed percentages of the maximum, week by week.
 *
 * What meet preparation wants: the weights are
 * known in advance so the peak lands on the right
 * date, and the lifter is not deciding anything
 * on the day. The tightest tolerance of the four,
 * because a block built to peak on a date cannot
 * drift.
 */
public final class PercentageProgression
        extends AbstractProgression {

    @Override
    protected double weeklyIncrement() {
        return 2.0;
    }

    @Override
    protected double tolerance() {
        return 0.025;
    }

    @Override
    public String name() {
        return "percentage";
    }

    @Override
    public String describe() {
        return "Weights are fixed percentages"
            + " of the current maximum, rising"
            + " each week so the peak lands on"
            + " the meet date rather than"
            + " whenever it happens.";
    }
}
