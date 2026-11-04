package ca.yorku.eecs3311.spotter
        .application.plan;

import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;

/**
 * Replaces the whole block with a revision
 * (F12).
 *
 * The one edit that is not a single session, so
 * it keeps the whole block rather than one
 * session of it. That is heavier than the others
 * and correct for what it does: a progression
 * review rewrites every remaining week, and
 * undoing it half way would leave a plan that
 * nobody wrote.
 */
public final class ApplyProgressionCommand
        implements PlanEditCommand {

    private final TrainingBlock revised;
    private TrainingBlock previous;

    public ApplyProgressionCommand(
            TrainingBlock revised) {
        if (revised == null) {
            throw new IllegalArgumentException(
                    "A revision is required");
        }
        this.revised = revised;
    }

    @Override
    public String describe() {
        return "revised the remaining weeks";
    }

    @Override
    public TrainingBlock apply(
            TrainingBlock block) {
        previous = block;
        return revised;
    }

    @Override
    public TrainingBlock undo(
            TrainingBlock block) {
        return previous == null
                ? block
                : previous;
    }
}
