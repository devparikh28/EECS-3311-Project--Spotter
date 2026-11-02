package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .DailyIntake;
import ca.yorku.eecs3311.spotter.domain
        .MacroTarget;

/**
 * What the lifter eats and what they are aiming
 * for.
 *
 * Keyed by date rather than by an integer id,
 * like the recovery readings and for the same
 * reason: there is one day's eating per day, and
 * an id would be a second way of naming something
 * the date already names. So this does not extend
 * Repository, whose contract assumes an assigned
 * id.
 *
 * The target lives here too. It is one row of
 * nutrition storage, and a repository of its own
 * holding a single row would be ceremony rather
 * than structure.
 */
public interface NutritionRepository {

    /**
     * Saves a day's eating, replacing whatever
     * was recorded for that date.
     */
    DailyIntake save(DailyIntake intake);

    /**
     * Records one food against a date, keeping
     * what is already there.
     *
     * @return the day as it now stands
     */
    DailyIntake log(LocalDate date,
                    ca.yorku.eecs3311.spotter
                        .domain.FoodEntry entry);

    Optional<DailyIntake> findByDate(
            LocalDate date);

    /** Every day on or after a date, oldest first. */
    List<DailyIntake> findSince(LocalDate since);

    void delete(LocalDate date);

    /**
     * The macro target for a profile, if one has
     * been set. Empty means the lifter has not
     * said what they are aiming for, which is a
     * state to handle rather than a default to
     * invent.
     */
    Optional<MacroTarget> targetFor(
            int profileId);

    MacroTarget saveTarget(
            int profileId, MacroTarget target);
}
