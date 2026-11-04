package ca.yorku.eecs3311.spotter
        .application.plan;

import java.util.ArrayList;
import java.util.List;

import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;

/**
 * Puts one session in place of another.
 *
 * The shared machinery behind the edits a lifter
 * actually makes. Swapping a movement and easing
 * a session off both come down to replacing one
 * session with a different one, and neither needs
 * its own idea of how a block is rebuilt.
 */
abstract class ReplaceSessionCommand
        implements PlanEditCommand {

    private final Session replacement;
    private Session previous;

    ReplaceSessionCommand(Session replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException(
                "A replacement session is"
                + " required");
        }
        this.replacement = replacement;
    }

    protected Session replacement() {
        return replacement;
    }

    @Override
    public TrainingBlock apply(
            TrainingBlock block) {
        previous = sessionWithId(block,
                replacement.id());
        return withSession(block, replacement);
    }

    @Override
    public TrainingBlock undo(
            TrainingBlock block) {
        if (previous == null) {
            return block;
        }
        return withSession(block, previous);
    }

    private static Session sessionWithId(
            TrainingBlock block, int id) {
        for (Session session
                : block.allSessions()) {
            if (session.id() == id) {
                return session;
            }
        }
        return null;
    }

    /**
     * The block with one session swapped.
     *
     * A block is rebuilt rather than reached
     * into, because its weeks hold their sessions
     * and a command that mutated them would make
     * undo depend on nobody else having touched
     * the same object.
     */
    private static TrainingBlock withSession(
            TrainingBlock block,
            Session session) {

        List<Week> weeks = new ArrayList<>();
        for (Week week : block.weeks()) {
            List<Session> sessions =
                    new ArrayList<>();
            for (Session each
                    : week.sessions()) {
                sessions.add(
                    each.id() == session.id()
                        ? session
                        : each);
            }
            weeks.add(new Week(week.number(),
                    sessions));
        }
        return new TrainingBlock(block.id(),
            block.startDate(),
            block.progression().orElse(null),
            block.splitName().orElse(null),
            block.origin(), weeks);
    }
}
