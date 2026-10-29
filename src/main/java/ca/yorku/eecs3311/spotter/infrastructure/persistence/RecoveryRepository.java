package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;

/** Stored recovery readings. */
public interface RecoveryRepository {

    /** Saves, replacing any reading for that day. */
    RecoveryDay save(RecoveryDay day);

    Optional<RecoveryDay> findByDate(
            LocalDate date);

    /** Every day on or after a date, oldest first. */
    List<RecoveryDay> findSince(LocalDate since);

    List<RecoveryDay> findAll();
}
