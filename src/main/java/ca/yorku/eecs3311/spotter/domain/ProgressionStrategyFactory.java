package ca.yorku.eecs3311.spotter.domain;

/**
 * Chooses the progression that suits a lifter.
 *
 * This is the Factory from the Stage 1 design.
 * Experience decides more than the goal does: a
 * novice preparing for a meet still progresses
 * like a novice, because what their body does is
 * not changed by what they have entered.
 */
public final class ProgressionStrategyFactory {

    /**
     * The progression for a goal and experience.
     *
     * @throws IllegalArgumentException when
     *         either is missing, rather than
     *         quietly defaulting
     */
    public ProgressionStrategy forGoal(
            TrainingGoal goal,
            ExperienceLevel experience) {
        if (goal == null
                || experience == null) {
            throw new IllegalArgumentException(
                "A goal and an experience level"
                + " are both needed to choose a"
                + " progression");
        }

        if (experience
                == ExperienceLevel.NOVICE) {
            return new
                NoviceLinearProgression();
        }

        return switch (goal) {
            case MEET_PREP ->
                new PercentageProgression();
            case STRENGTH ->
                new RPEProgression();
            case HYPERTROPHY ->
                experience
                    == ExperienceLevel.ADVANCED
                    ? new RPEProgression()
                    : new LinearProgression();
            case GENERAL_FITNESS ->
                new LinearProgression();
        };
    }
}
