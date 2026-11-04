package ca.yorku.eecs3311.spotter
        .application.plan;

import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;

/**
 * One change to a plan, which can be undone.
 *
 * This is the Command from the Stage 1 design.
 * Every edit the coach proposes, a swap, a
 * reduction, a revision, arrives as one of these
 * rather than as a direct write, so the thing
 * that applies a change and the thing that
 * reverses it are written together and cannot
 * drift apart.
 *
 * A command takes a block and returns a new one
 * rather than mutating it, which is what makes
 * undo a matter of keeping the old value rather
 * than of carefully reversing arithmetic.
 */
public interface PlanEditCommand {

    /** What this did, for a person to read. */
    String describe();

    /**
     * The block with this change applied.
     *
     * Called once. An implementation keeps
     * whatever it needs to reverse itself.
     */
    TrainingBlock apply(TrainingBlock block);

    /** The block as it was before apply. */
    TrainingBlock undo(TrainingBlock block);
}
