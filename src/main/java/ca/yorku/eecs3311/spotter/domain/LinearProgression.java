package ca.yorku.eecs3311.spotter.domain;

/**
 * A steady 2.5 kg a week.
 *
 * What an intermediate lifter can usually sustain
 * across a block. Faster than that stalls; slower
 * wastes the block.
 */
public final class LinearProgression
        extends AbstractProgression {

    @Override
    protected double weeklyIncrement() {
        return 2.5;
    }

    @Override
    protected double tolerance() {
        return 0.05;
    }

    @Override
    public String name() {
        return "linear";
    }

    @Override
    public String describe() {
        return "Load rises by 2.5 kg each week"
            + " of the block, which is what an"
            + " intermediate lifter can usually"
            + " hold without stalling.";
    }
}
