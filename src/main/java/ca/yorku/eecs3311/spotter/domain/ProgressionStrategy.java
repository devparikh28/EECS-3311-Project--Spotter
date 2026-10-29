package ca.yorku.eecs3311.spotter.domain;

/**
 * How load should advance across a block.
 *
 * This is the Strategy from the Stage 1 design,
 * and it exists because the right answer genuinely
 * differs by lifter. A novice can add weight every
 * session; an advanced lifter adding five kilos a
 * week would stall within a month. Encoding that
 * as conditionals inside the plan generator would
 * put four training philosophies in one method.
 *
 * The range returned is what PlanValidator checks
 * a model's proposed load against. That is the
 * gate: an agent may suggest any weight it likes,
 * and only one inside this range reaches the
 * lifter.
 */
public interface ProgressionStrategy {

    /**
     * Acceptable loads for one prescription.
     *
     * @param e1rm current estimated one rep max
     * @param reps reps prescribed
     * @param rpe  effort prescribed
     * @param week which week of the block, from 1
     */
    Range loadRange(double e1rm, int reps,
                    double rpe, int week);

    /** How this progresses, in plain words. */
    String describe();

    /** A short name for traces and prompts. */
    String name();
}
