package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.api.Assertions
        .within;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for attempt and test day planning. */
class AttemptCalculatorTest {

    private static final double TOLERANCE =
            0.001;

    private AttemptCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new AttemptCalculator();
    }

    @Test
    @DisplayName(
        "attempts come out at the right"
        + " percentages of the estimate")
    void attemptsAreAtTheRightPercentages() {
        MeetAttempts attempts =
            calculator.calculateMeetAttempts(
                Lift.SQUAT, 200);

        assertThat(attempts.opener())
            .isEqualTo(182.5, within(TOLERANCE));
        assertThat(attempts.second())
            .isEqualTo(192.5, within(TOLERANCE));
        assertThat(attempts.third())
            .isEqualTo(202.5, within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "attempts always increase, even when"
        + " rounding would collapse them")
    void attemptsAlwaysIncrease() {
        // A small maximum puts the three
        // percentages within one increment
        // of each other.
        MeetAttempts attempts =
            calculator.calculateMeetAttempts(
                Lift.BENCH, 40);

        assertThat(attempts.opener())
            .isLessThan(attempts.second());
        assertThat(attempts.second())
            .isLessThan(attempts.third());
    }

    @ParameterizedTest(name =
        "a {0} kg maximum gives increasing"
        + " attempts")
    @ValueSource(doubles = {
        20, 37.5, 60, 100, 142.5, 200, 350})
    @DisplayName(
        "attempts increase at every size of"
        + " lifter")
    void attemptsIncreaseAtEverySize(
            double e1rm) {
        MeetAttempts attempts =
            calculator.calculateMeetAttempts(
                Lift.DEADLIFT, e1rm);

        assertThat(attempts.opener())
            .isLessThan(attempts.second());
        assertThat(attempts.second())
            .isLessThan(attempts.third());
    }

    @Test
    @DisplayName(
        "every attempt is a weight that can"
        + " actually be loaded")
    void attemptsAreLoadable() {
        MeetAttempts attempts =
            calculator.calculateMeetAttempts(
                Lift.SQUAT, 187.3);

        assertThat(isLoadable(attempts.opener()))
                .isTrue();
        assertThat(isLoadable(attempts.second()))
                .isTrue();
        assertThat(isLoadable(attempts.third()))
                .isTrue();
    }

    private boolean isLoadable(double kg) {
        double steps = kg
            / AttemptCalculator.PLATE_INCREMENT;
        return Math.abs(steps
                - Math.round(steps)) < 1e-9;
    }

    @Test
    @DisplayName(
        "rounding goes to the nearest loadable"
        + " weight in both directions")
    void roundingGoesToNearest() {
        assertThat(calculator.roundToPlate(101.2))
            .isEqualTo(100.0, within(TOLERANCE));
        assertThat(calculator.roundToPlate(101.3))
            .isEqualTo(102.5, within(TOLERANCE));
        assertThat(calculator.roundToPlate(100.0))
            .isEqualTo(100.0, within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "rounding a weight at or below zero"
        + " gives zero")
    void roundingNonPositiveGivesZero() {
        assertThat(calculator.roundToPlate(0))
                .isZero();
        assertThat(calculator.roundToPlate(-10))
                .isZero();
    }

    @Test
    @DisplayName(
        "the rationale explains the numbers")
    void rationaleExplainsTheNumbers() {
        MeetAttempts attempts =
            calculator.calculateMeetAttempts(
                Lift.SQUAT, 200);

        assertThat(attempts.rationale())
                .contains("200")
                .contains("91")
                .isNotBlank();
    }

    @Test
    @DisplayName(
        "a test day builds up to a top single")
    void testDayBuildsToATopSingle() {
        TestDayPlan plan =
            calculator.projectTestDay(
                Lift.SQUAT, 200);

        assertThat(plan.topSingle())
            .isEqualTo(192.5, within(TOLERANCE));
        assertThat(plan.warmups())
                .isNotEmpty();
    }

    @Test
    @DisplayName(
        "warm ups get heavier and stay below"
        + " the top single")
    void warmupsRiseAndStayBelow() {
        TestDayPlan plan =
            calculator.projectTestDay(
                Lift.DEADLIFT, 220);

        List<Double> warmups = plan.warmups();
        for (int i = 1; i < warmups.size(); i++) {
            assertThat(warmups.get(i))
                .isGreaterThan(
                    warmups.get(i - 1));
        }
        assertThat(warmups)
            .allMatch(w -> w < plan.topSingle());
    }

    @Test
    @DisplayName(
        "the top single is below the estimate,"
        + " because the point is to make it")
    void topSingleIsBelowTheEstimate() {
        TestDayPlan plan =
            calculator.projectTestDay(
                Lift.BENCH, 100);

        assertThat(plan.topSingle())
                .isLessThan(100);
    }

    @Test
    @DisplayName(
        "an estimate of zero or less is"
        + " refused for both plans")
    void unusableEstimateIsRefused() {
        assertThatThrownBy(() ->
            calculator.calculateMeetAttempts(
                Lift.SQUAT, 0))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("above zero");

        assertThatThrownBy(() ->
            calculator.projectTestDay(
                Lift.SQUAT, -5))
            .isInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    @DisplayName(
        "attempts in the wrong order are"
        + " refused by the record itself")
    void backwardsAttemptsAreRefused() {
        assertThatThrownBy(() ->
            new MeetAttempts(Lift.SQUAT,
                200, 190, 210, ""))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("lighter");
    }

    @Test
    @DisplayName(
        "a warm up heavier than the top single"
        + " is refused")
    void impossibleWarmupIsRefused() {
        assertThatThrownBy(() ->
            new TestDayPlan(Lift.SQUAT, 100,
                List.of(110.0), ""))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("heavier");
    }

    @Test
    @DisplayName(
        "the total jump is reported")
    void totalJumpIsReported() {
        MeetAttempts attempts =
            calculator.calculateMeetAttempts(
                Lift.SQUAT, 200);

        assertThat(attempts.totalJump())
            .isEqualTo(20.0, within(TOLERANCE));
    }
}
