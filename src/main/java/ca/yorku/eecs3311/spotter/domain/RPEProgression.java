package ca.yorku.eecs3311.spotter.domain;

/**
 * Load follows how the lifter feels.
 *
 * Nothing is added per week on paper: the RPE
 * prescribed does the progressing, because a
 * lifter who has got stronger will move more
 * weight at the same effort. Suits intermediate
 * and advanced lifters, whose progress is too
 * uneven to schedule in advance.
 *
 * The widest tolerance of the four, deliberately,
 * since the whole point is that the exact weight
 * is decided on the day.
 */
public final class RPEProgression
        extends AbstractProgression {

    @Override
    protected double weeklyIncrement() {
        return 0;
    }

    @Override
    protected double tolerance() {
        return 0.075;
    }

    @Override
    public String name() {
        return "RPE";
    }

    @Override
    public String describe() {
        return "Loads are set by effort rather"
            + " than a schedule: the prescribed"
            + " RPE decides the weight on the"
            + " day, so progress shows as more"
            + " weight at the same effort.";
    }
}
