package ca.yorku.eecs3311.spotter.domain;

/**
 * What the lifter is training for. Chosen in the
 * profile and used to pick a progression and a
 * split. A meet date is only required for
 * MEET_PREP.
 */
public enum TrainingGoal {
    MEET_PREP,
    STRENGTH,
    HYPERTROPHY,
    GENERAL_FITNESS
}
