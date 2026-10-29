package ca.yorku.eecs3311.spotter.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether a day's recovery readings are
 * poor enough to question the session planned for
 * it.
 *
 * Fixed thresholds, no model. That is deliberate:
 * whether a recovery score of 28 is low has a
 * correct answer, and asking a model would make a
 * simple comparison slow, costly and
 * unpredictable. The agent's job is the harder
 * question of what to do about it.
 *
 * fallbackAdjustment is what makes this feature
 * degrade rather than fail. When the model is
 * unavailable the lifter still gets a sensible
 * reduction instead of an error.
 */
public final class RecoveryRuleEngine {

    /** Below this, a wearable score is poor. */
    public static final int MIN_RECOVERY_SCORE =
            33;

    /** Below this, sleep is too short. */
    public static final double MIN_SLEEP_HOURS =
            6.0;

    /** At or above this, soreness is high. */
    public static final int MAX_SORENESS = 4;

    /** At or below this, energy is low. */
    public static final int MIN_ENERGY = 2;

    /** How much the fallback cuts the load. */
    public static final double
            FALLBACK_LOAD_FACTOR = 0.90;

    private final AttemptCalculator rounding =
            new AttemptCalculator();

    /**
     * Examines a day's readings.
     *
     * @return a flag when something is poor
     *         enough to act on, empty otherwise
     */
    public Optional<RecoveryFlag> evaluate(
            RecoveryDay day) {
        if (day == null || day.isEmpty()) {
            return Optional.empty();
        }
        List<String> reasons = new ArrayList<>();

        day.recoveryScore().ifPresent(score -> {
            if (score < MIN_RECOVERY_SCORE) {
                reasons.add(
                    "recovery score of " + score
                    + " is below "
                    + MIN_RECOVERY_SCORE);
            }
        });

        day.sleepHours().ifPresent(hours -> {
            if (hours < MIN_SLEEP_HOURS) {
                reasons.add(String.format(
                    "only %.1f hours of sleep",
                    hours));
            }
        });

        day.soreness().ifPresent(soreness -> {
            if (soreness >= MAX_SORENESS) {
                reasons.add(
                    "soreness reported at "
                    + soreness + " out of 5");
            }
        });

        day.energy().ifPresent(energy -> {
            if (energy <= MIN_ENERGY) {
                reasons.add(
                    "energy reported at "
                    + energy + " out of 5");
            }
        });

        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RecoveryFlag(
                day.date(), reasons, null));
    }

    /**
     * A sensible reduction, worked out without
     * the model.
     *
     * Takes ten percent off every load, rounded
     * to a weight that can be loaded, and drops
     * the last set of each exercise while always
     * leaving at least one. Reducing both at once
     * is the conservative choice: on a bad day
     * the session should be clearly easier, not
     * marginally so.
     *
     * @return a new session, leaving the original
     *         untouched so the edit can be undone
     */
    public Session fallbackAdjustment(
            Session session) {
        if (session == null) {
            throw new IllegalArgumentException(
                    "A session is required");
        }
        List<ExercisePrescription> reduced =
                new ArrayList<>();
        for (ExercisePrescription p
                : session.prescriptions()) {
            double lighter =
                rounding.roundToPlate(
                    p.loadKg()
                    * FALLBACK_LOAD_FACTOR);
            int fewer = Math.max(1,
                    p.sets() - 1);
            reduced.add(p.withLoad(lighter)
                    .withSets(fewer));
        }

        Session adjusted = new Session(
                session.id(), session.date(),
                session.focus().orElse(null),
                reduced);
        adjusted.setAdjustmentNote(
            "Reduced automatically after poor"
            + " recovery: loads cut to "
            + Math.round(
                FALLBACK_LOAD_FACTOR * 100)
            + " percent and one set removed"
            + " from each exercise. The coach"
            + " was unavailable, so this is the"
            + " standard reduction rather than"
            + " a tailored one.");
        return adjusted;
    }
}
