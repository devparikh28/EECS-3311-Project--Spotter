package ca.yorku.eecs3311.spotter.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a lifter should do first, once their
 * profile exists.
 *
 * Onboarding ends with a profile and nothing to
 * train from, which is the moment someone decides
 * the application is not for them. Everything
 * Spotter does needs an estimated maximum: a
 * block cannot be written, attempts cannot be
 * calculated, an adjustment has nothing to
 * reduce. A lifter who has never imported
 * anything has none.
 *
 * So the question is narrow and deterministic.
 * Which lifts can be planned for, and for the
 * rest, should the lifter type in what they know
 * or train a week and let Spotter work it out.
 */
public final class StartingStrength {

    /**
     * Sessions of history before an estimate is
     * worth trusting.
     *
     * One heavy single is a measurement, not a
     * pattern, and a block built on it inherits
     * whatever that day happened to be.
     */
    public static final int ENOUGH_SESSIONS = 3;

    /** Which lifts still have no usable estimate. */
    public List<Lift> unknownLifts(
            Map<Lift, Double> current) {
        List<Lift> unknown = new ArrayList<>();
        for (Lift lift : Lift.values()) {
            Double e1rm = current == null
                    ? null
                    : current.get(lift);
            if (e1rm == null || e1rm <= 0) {
                unknown.add(lift);
            }
        }
        return List.copyOf(unknown);
    }

    /**
     * True when Spotter cannot plan for a lift
     * yet.
     *
     * @param current  estimated maxima by lift
     * @param sessions how many sessions are
     *                 logged in total
     */
    public boolean needsCalibration(
            Map<Lift, Double> current,
            int sessions) {
        return !unknownLifts(current).isEmpty()
                || sessions < ENOUGH_SESSIONS;
    }

    /**
     * Whether to ask for known maxima or to
     * suggest training a week first.
     *
     * A lifter with some history but not enough
     * is better served by training, since they
     * are already logging and the estimate
     * arrives on its own. A lifter with nothing
     * logged has to supply something, or there is
     * no first session to write.
     */
    public FirstStep firstStep(
            Map<Lift, Double> current,
            int sessions) {

        if (!needsCalibration(current,
                sessions)) {
            return FirstStep.READY;
        }
        if (sessions == 0) {
            return FirstStep.ENTER_KNOWN_MAXES;
        }
        return FirstStep.TRAIN_A_WEEK;
    }

    /** What to put in front of the lifter next. */
    public enum FirstStep {
        /** Enough is known to plan. */
        READY,
        /** Nothing is logged; ask what they lift. */
        ENTER_KNOWN_MAXES,
        /** Some history; let it accumulate. */
        TRAIN_A_WEEK
    }

    /** The first step in words, for a screen. */
    public String describe(
            Map<Lift, Double> current,
            int sessions) {

        List<Lift> unknown =
                unknownLifts(current);

        return switch (firstStep(current,
                sessions)) {
            case READY ->
                "Spotter knows what you lift."
                + " Generate a block on the Plan"
                + " screen.";
            case ENTER_KNOWN_MAXES ->
                "Nothing is logged yet, so"
                + " there is nothing to plan"
                + " from. Import a Hevy export,"
                + " or log a session for each"
                + " lift you want trained.";
            case TRAIN_A_WEEK ->
                sessions + " session(s) are"
                + " logged, which is not enough"
                + " to estimate from yet"
                + (unknown.isEmpty()
                    ? ""
                    : ", and nothing at all for "
                        + describeLifts(unknown))
                + ". Train a week and the"
                + " estimates arrive on their"
                + " own.";
        };
    }

    private static String describeLifts(
            List<Lift> lifts) {
        List<String> named = new ArrayList<>();
        for (Lift lift : lifts) {
            named.add(lift.name().toLowerCase(
                    java.util.Locale.ROOT));
        }
        return String.join(", ", named);
    }
}
