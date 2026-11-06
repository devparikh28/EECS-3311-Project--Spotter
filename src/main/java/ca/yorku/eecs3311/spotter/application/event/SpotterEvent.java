package ca.yorku.eecs3311.spotter
        .application.event;

/**
 * Something changed that a screen might be
 * showing.
 *
 * Four values. These are the kinds of change the
 * application makes to itself while the window is
 * open: a plan saved, a profile saved, food or a
 * macro target recorded, and training data brought
 * in through the Import screen.
 *
 * Logging a set or importing from `spotter log`
 * and `spotter import` still happens in a separate
 * process, where a bus in this window cannot hear
 * it, which is why TRAINING_CHANGED was not
 * defined until the window itself gained a way to
 * import. An event with no publisher would have
 * been worse than a short list.
 *
 * The grain is deliberate. One event per table, or
 * per field, would let a panel refresh less, but
 * no panel here shows little enough for that to be
 * worth the bookkeeping.
 */
public enum SpotterEvent {

    /** A plan was saved, revised or edited. */
    BLOCK_CHANGED,

    /** The lifter's profile was saved. */
    PROFILE_CHANGED,

    /** Food or a macro target was recorded. */
    NUTRITION_CHANGED,

    /** Workouts or recovery readings came in. */
    TRAINING_CHANGED
}
