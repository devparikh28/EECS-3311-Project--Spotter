package ca.yorku.eecs3311.spotter
        .application.event;

/**
 * Something changed that a screen might be
 * showing.
 *
 * Three values, and that is deliberate. These are
 * the only kinds of change the application makes
 * to itself while it is running: a plan saved, a
 * profile saved, food or a macro target recorded.
 * Logging a set and importing recovery readings
 * change a screen too, but both arrive from
 * `spotter log` and `spotter import` in a
 * separate process, where a bus in this window
 * cannot hear them. Adding WORKOUT_LOGGED before
 * anything could publish it would be an event
 * with no subject, which is worse than a short
 * list.
 *
 * The grain is also deliberate. One event per
 * table, or per field, would let a panel refresh
 * less, but no panel here shows little enough for
 * that to be worth the bookkeeping.
 */
public enum SpotterEvent {

    /** A plan was saved, revised or edited. */
    BLOCK_CHANGED,

    /** The lifter's profile was saved. */
    PROFILE_CHANGED,

    /** Food or a macro target was recorded. */
    NUTRITION_CHANGED
}
