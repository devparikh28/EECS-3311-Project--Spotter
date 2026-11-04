package ca.yorku.eecs3311.spotter
        .application.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;

/**
 * The edits made to a plan, newest last.
 *
 * The reason this exists is a sentence from the
 * Stage 1 walkthrough: a lifter eases off a
 * session in the morning and wants it back in the
 * evening when they feel better. Without a
 * history that is a regeneration; with one it is
 * a button.
 *
 * Bounded, because an undo stack that grows all
 * day is a memory leak with a friendly name, and
 * nobody reverses the fortieth change back.
 */
public final class PlanEditHistory {

    /** Edits kept before the oldest is dropped. */
    public static final int MAX_EDITS = 20;

    private final Deque<PlanEditCommand> done =
            new ArrayDeque<>();
    private TrainingBlock current;

    public PlanEditHistory(TrainingBlock block) {
        if (block == null) {
            throw new IllegalArgumentException(
                "A history needs a block to"
                + " edit");
        }
        this.current = block;
    }

    /** The block as it now stands. */
    public TrainingBlock current() {
        return current;
    }

    /**
     * Applies a change and remembers how to
     * reverse it.
     *
     * @return the block with the change applied
     */
    public TrainingBlock apply(
            PlanEditCommand command) {
        if (command == null) {
            throw new IllegalArgumentException(
                    "A command is required");
        }
        current = command.apply(current);
        done.addLast(command);
        while (done.size() > MAX_EDITS) {
            done.removeFirst();
        }
        return current;
    }

    public boolean canUndo() {
        return !done.isEmpty();
    }

    /** What would be undone, for a button. */
    public Optional<String> nextToUndo() {
        return done.isEmpty()
            ? Optional.empty()
            : Optional.of(done.peekLast()
                    .describe());
    }

    /**
     * Reverses the most recent change.
     *
     * @return the block as it was before it, or
     *         empty when there is nothing to
     *         undo
     */
    public Optional<TrainingBlock> undo() {
        if (done.isEmpty()) {
            return Optional.empty();
        }
        PlanEditCommand last =
                done.removeLast();
        current = last.undo(current);
        return Optional.of(current);
    }

    /** Every change still on the stack. */
    public List<String> describe() {
        List<String> described =
                new ArrayList<>();
        for (PlanEditCommand command : done) {
            described.add(command.describe());
        }
        return List.copyOf(described);
    }

    public int size() {
        return done.size();
    }

    /**
     * Points the history at a reloaded block.
     *
     * Saving sends a block to the database and
     * reading it back gives an equal block that
     * is not the same object. The edits still
     * describe the same changes and still hold
     * the sessions they replaced, so the stack
     * is kept and only the block underneath it
     * is replaced.
     */
    public void rebase(TrainingBlock block) {
        if (block == null) {
            throw new IllegalArgumentException(
                "A history needs a block to"
                + " edit");
        }
        this.current = block;
    }

    /** Forgets the history, keeping the block. */
    public void clear() {
        done.clear();
    }
}
