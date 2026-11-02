package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import java.util.Optional;

import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;

/**
 * Storage for the lifter's profile.
 *
 * In practice there is one profile, since Spotter
 * runs on the lifter's own machine, but the
 * repository is written for many because
 * assuming one everywhere would make a second
 * profile a rewrite rather than a feature. The
 * current method is the convenience the rest of
 * the system actually uses.
 */
public interface ProfileRepository
        extends Repository<LifterProfile> {

    /**
     * The profile to work with, which is the
     * only one unless several exist, in which
     * case it is the most recently saved.
     *
     * Empty means onboarding has not been done,
     * which callers treat as a state to handle
     * rather than an error.
     */
    Optional<LifterProfile> current();
}
