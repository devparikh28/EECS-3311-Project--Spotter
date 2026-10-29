package ca.yorku.eecs3311.spotter.domain;

/**
 * A faster 5 kg a week, for a lifter new enough
 * to earn it.
 *
 * A novice really does add weight that quickly,
 * and prescribing an intermediate's progression
 * to one wastes the window where it is possible.
 * Nobody stays on this for long, which is why it
 * is a separate strategy rather than a parameter.
 */
public final class NoviceLinearProgression
        extends AbstractProgression {

    @Override
    protected double weeklyIncrement() {
        return 5.0;
    }

    @Override
    protected double tolerance() {
        return 0.05;
    }

    @Override
    public String name() {
        return "novice linear";
    }

    @Override
    public String describe() {
        return "Load rises by 5 kg each week."
            + " A lifter this new adds strength"
            + " fast enough to earn it, and the"
            + " window does not last.";
    }
}
