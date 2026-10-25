package ca.yorku.eecs3311.spotter.domain;

/**
 * How long the lifter has trained seriously.
 * This selects the progression scheme: a novice
 * can add load session to session, an advanced
 * lifter cannot.
 */
public enum ExperienceLevel {
    NOVICE,
    INTERMEDIATE,
    ADVANCED
}
