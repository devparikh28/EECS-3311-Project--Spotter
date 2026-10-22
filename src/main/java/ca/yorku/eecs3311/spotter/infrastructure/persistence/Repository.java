package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Storage for one kind of entity.
 *
 * This is the Repository pattern from the Stage 1
 * design. The application and domain layers
 * depend on this interface only, so SQLite can be
 * replaced, or swapped for an in memory fake in a
 * test, without changing anything above it.
 *
 * @param <T> the entity stored
 */
public interface Repository<T> {

    /**
     * Inserts or updates, and returns the stored
     * entity, which carries the assigned id.
     */
    T save(T item);

    /** The entity with this id, if it exists. */
    Optional<T> findById(int id);

    /** Every stored entity. */
    List<T> findAll();

    /**
     * Removes the entity with this id. Removing
     * an id that is not there is not an error.
     */
    void delete(int id);
}
