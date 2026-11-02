package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.util.List;

import ca.yorku.eecs3311.spotter.domain
        .MemoryNote;

/**
 * What the coach remembers between conversations.
 *
 * Newest first everywhere, because the only
 * question ever asked of this is what to put in
 * front of the model now, and recent notes are
 * the ones worth the space.
 */
public interface MemoryRepository
        extends Repository<MemoryNote> {

    /** The most recent notes, newest first. */
    List<MemoryNote> recent(int limit);

    /** How many notes are held. */
    int count();

    /**
     * Removes everything.
     *
     * A lifter is entitled to tell their coach to
     * forget what it thinks it knows, and that
     * has to be one action rather than a hunt
     * through a list.
     */
    void clear();
}
