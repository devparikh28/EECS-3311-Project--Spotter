package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the progression strategies.
 *
 * The range each returns is what PlanValidator
 * checks a model's proposed load against, so an
 * error here would let a bad weight through the
 * gate.
 */
class ProgressionStrategyTest {

    private static final double E1RM = 200;

    private ProgressionStrategyFactory factory;

    @BeforeEach
    void setUp() {
        factory =
            new ProgressionStrategyFactory();
    }

    @Test
    @DisplayName(
        "the range sits around what the reps"
        + " and RPE imply")
    void rangeSitsAroundTheImpliedLoad() {
        // 5 reps at RPE 8 is 81.1 percent,
        // so 162 kg of a 200 kg maximum.
        Range range = new RPEProgression()
                .loadRange(E1RM, 5, 8, 1);

        assertThat(range.contains(162.2))
                .isTrue();
        assertThat(range.midpoint())
                .isBetween(160.0, 165.0);
    }

    @Test
    @DisplayName(
        "RPE progression does not add weight"
        + " per week; the effort does that")
    void rpeDoesNotAddWeeklyWeight() {
        ProgressionStrategy rpe =
                new RPEProgression();

        assertThat(rpe.loadRange(E1RM, 5, 8, 1)
                .midpoint())
            .isEqualTo(rpe.loadRange(
                E1RM, 5, 8, 6).midpoint());
    }

    @Test
    @DisplayName(
        "linear progression adds 2.5 kg a week")
    void linearAddsWeekly() {
        ProgressionStrategy linear =
                new LinearProgression();

        double week1 = linear.loadRange(
                E1RM, 5, 8, 1).midpoint();
        double week5 = linear.loadRange(
                E1RM, 5, 8, 5).midpoint();

        assertThat(week5 - week1)
                .isEqualTo(10.0);
    }

    @Test
    @DisplayName(
        "a novice progresses twice as fast as"
        + " an intermediate")
    void noviceProgressesFaster() {
        double novice =
            new NoviceLinearProgression()
                .loadRange(E1RM, 5, 8, 5)
                .midpoint();
        double intermediate =
            new LinearProgression()
                .loadRange(E1RM, 5, 8, 5)
                .midpoint();

        assertThat(novice - intermediate)
                .isEqualTo(10.0);
    }

    @Test
    @DisplayName(
        "meet preparation allows the least"
        + " drift, RPE the most")
    void toleranceMatchesThePurpose() {
        double meet =
            new PercentageProgression()
                .loadRange(E1RM, 5, 8, 1)
                .width();
        double linear =
            new LinearProgression()
                .loadRange(E1RM, 5, 8, 1)
                .width();
        double rpe =
            new RPEProgression()
                .loadRange(E1RM, 5, 8, 1)
                .width();

        assertThat(meet).isLessThan(linear);
        assertThat(linear).isLessThan(rpe);
    }

    @Test
    @DisplayName(
        "a heavier prescription gives a higher"
        + " range than a lighter one")
    void harderWorkMeansMoreWeight() {
        ProgressionStrategy strategy =
                new RPEProgression();

        double threeAtNine = strategy
            .loadRange(E1RM, 3, 9, 1)
            .midpoint();
        double eightAtSeven = strategy
            .loadRange(E1RM, 8, 7, 1)
            .midpoint();

        assertThat(threeAtNine)
                .isGreaterThan(eightAtSeven);
    }

    @Test
    @DisplayName(
        "a novice gets the novice progression"
        + " whatever their goal")
    void experienceOutranksGoal() {
        for (TrainingGoal goal
                : TrainingGoal.values()) {
            assertThat(factory.forGoal(goal,
                    ExperienceLevel.NOVICE))
                .isInstanceOf(
                    NoviceLinearProgression
                        .class);
        }
    }

    @Test
    @DisplayName(
        "meet preparation gets fixed"
        + " percentages so the peak lands on"
        + " the date")
    void meetPrepGetsPercentages() {
        assertThat(factory.forGoal(
                TrainingGoal.MEET_PREP,
                ExperienceLevel.INTERMEDIATE))
            .isInstanceOf(
                PercentageProgression.class);
    }

    @Test
    @DisplayName(
        "an intermediate chasing strength gets"
        + " RPE based progression")
    void strengthGetsRpe() {
        assertThat(factory.forGoal(
                TrainingGoal.STRENGTH,
                ExperienceLevel.INTERMEDIATE))
            .isInstanceOf(
                RPEProgression.class);
    }

    @Test
    @DisplayName(
        "every goal and experience pair"
        + " produces a strategy")
    void everyCombinationIsCovered() {
        for (TrainingGoal goal
                : TrainingGoal.values()) {
            for (ExperienceLevel level
                    : ExperienceLevel.values()) {
                ProgressionStrategy strategy =
                    factory.forGoal(goal, level);

                assertThat(strategy).isNotNull();
                assertThat(strategy.describe())
                        .isNotBlank();
                assertThat(strategy.name())
                        .isNotBlank();
            }
        }
    }

    @Test
    @DisplayName(
        "a missing goal or level is refused"
        + " rather than quietly defaulted")
    void missingInputIsRefused() {
        assertThatThrownBy(() ->
            factory.forGoal(null,
                ExperienceLevel.NOVICE))
            .isInstanceOf(
                IllegalArgumentException.class);

        assertThatThrownBy(() ->
            factory.forGoal(
                TrainingGoal.STRENGTH, null))
            .isInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    @DisplayName(
        "an estimate of zero is refused,"
        + " because there is nothing to"
        + " progress from")
    void zeroMaximumIsRefused() {
        assertThatThrownBy(() ->
            new RPEProgression()
                .loadRange(0, 5, 8, 1))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("above zero");
    }

    @Test
    @DisplayName("weeks are numbered from one")
    void weekZeroIsRefused() {
        assertThatThrownBy(() ->
            new RPEProgression()
                .loadRange(E1RM, 5, 8, 0))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("from 1");
    }
}
