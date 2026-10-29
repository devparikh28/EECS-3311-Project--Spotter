package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;

/** Stored training history. */
public interface WorkoutRepository
        extends Repository<WorkoutSession> {

    /**
     * Sessions containing work on one lift, on or
     * after a date, oldest first.
     */
    List<WorkoutSession> findByLift(
            Lift lift, LocalDate since);

    /** Every session on or after a date. */
    List<WorkoutSession> findSince(
            LocalDate since);
}
