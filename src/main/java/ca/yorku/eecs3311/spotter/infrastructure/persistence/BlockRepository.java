package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;

/**
 * Storage for training blocks.
 *
 * One block is active at a time, which is what
 * the dashboard shows and what today's session
 * comes from. Older blocks are kept rather than
 * deleted, because a lifter looking at a stalled
 * lift wants to see what they were doing three
 * months ago.
 */
public interface BlockRepository
        extends Repository<TrainingBlock> {

    /**
     * The block currently being trained, if
     * there is one. Empty means no plan has been
     * generated yet.
     */
    Optional<TrainingBlock> active();

    /**
     * Makes this block the active one and
     * retires every other.
     */
    void activate(int id);
}
