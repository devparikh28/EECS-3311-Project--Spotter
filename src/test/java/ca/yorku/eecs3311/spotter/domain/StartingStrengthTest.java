package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the step after onboarding.
 *
 * The distinction that matters is between a
 * lifter with nothing, who has to supply
 * something before anything can be planned, and a
 * lifter with a little, who only has to keep
 * going. Telling the second group to type in
 * their maxima would be asking for numbers
 * Spotter is about to work out for itself.
 */
class StartingStrengthTest {

    private final StartingStrength starting =
            new StartingStrength();

    private static Map<Lift, Double> all() {
        return Map.of(Lift.SQUAT, 190.0,
                Lift.BENCH, 95.0,
                Lift.DEADLIFT, 200.0);
    }

    @Test
    @DisplayName(
        "a lifter with all three lifts and"
        + " enough history is ready")
    void aKnownLifterIsReady() {
        assertThat(starting.needsCalibration(
                all(), 20))
            .isFalse();
        assertThat(starting.firstStep(all(), 20))
            .isEqualTo(StartingStrength
                    .FirstStep.READY);
        assertThat(starting.describe(all(), 20))
            .contains("Generate a block");
    }

    @Test
    @DisplayName(
        "a lifter with nothing is asked for"
        + " something to plan from")
    void anEmptyLifterIsAsked() {
        assertThat(starting.firstStep(Map.of(),
                0))
            .isEqualTo(StartingStrength
                    .FirstStep
                        .ENTER_KNOWN_MAXES);
        assertThat(starting.describe(Map.of(), 0))
            .contains("Nothing is logged");
    }

    @Test
    @DisplayName(
        "a lifter with a little history is told"
        + " to keep going rather than to type"
        + " numbers in")
    void aThinHistoryMeansTrainOn() {
        assertThat(starting.firstStep(all(), 2))
            .isEqualTo(StartingStrength
                    .FirstStep.TRAIN_A_WEEK);
        assertThat(starting.describe(all(), 2))
            .contains("Train a week");
    }

    @Test
    @DisplayName(
        "a missing lift is named, so the lifter"
        + " knows which one to train")
    void aMissingLiftIsNamed() {
        Map<Lift, Double> partial = Map.of(
                Lift.SQUAT, 190.0);

        assertThat(starting.unknownLifts(
                partial))
            .containsExactly(Lift.BENCH,
                    Lift.DEADLIFT);
        assertThat(starting.describe(partial, 2))
            .contains("bench")
            .contains("deadlift");
    }

    @Test
    @DisplayName(
        "an estimate of zero counts as no"
        + " estimate")
    void zeroIsNotAnEstimate() {
        Map<Lift, Double> zeroed = Map.of(
                Lift.SQUAT, 0.0,
                Lift.BENCH, 95.0,
                Lift.DEADLIFT, 200.0);

        assertThat(starting.unknownLifts(zeroed))
            .containsExactly(Lift.SQUAT);
        assertThat(starting.needsCalibration(
                zeroed, 20))
            .isTrue();
    }

    @Test
    @DisplayName(
        "no estimates at all is handled rather"
        + " than thrown at")
    void nullIsHandled() {
        assertThat(starting.unknownLifts(null))
            .hasSize(3);
        assertThat(starting.needsCalibration(
                null, 0))
            .isTrue();
    }
}
