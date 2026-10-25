package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for weight conversion. Every weight is
 * stored in kilograms, so a CSV exported in
 * pounds has to convert correctly or every
 * downstream figure is wrong.
 */
class WeightUnitTest {

    private static final double TOLERANCE =
            0.0001;

    @Test
    @DisplayName("kilograms convert to themselves")
    void kilogramsAreUnchanged() {
        assertThat(WeightUnit.KG.toKg(102.5))
            .isEqualTo(102.5, within(TOLERANCE));
        assertThat(WeightUnit.KG.fromKg(102.5))
            .isEqualTo(102.5, within(TOLERANCE));
    }

    @Test
    @DisplayName("pounds convert to kilograms")
    void poundsConvertToKilograms() {
        assertThat(WeightUnit.LB.toKg(225))
            .isEqualTo(102.0583, within(0.001));
    }

    @Test
    @DisplayName("kilograms convert to pounds")
    void kilogramsConvertToPounds() {
        assertThat(WeightUnit.LB.fromKg(100))
            .isEqualTo(220.462, within(0.001));
    }

    @Test
    @DisplayName(
        "converting there and back returns"
        + " the original")
    void conversionRoundTrips() {
        double original = 142.5;

        double roundTripped = WeightUnit.LB.toKg(
                WeightUnit.LB.fromKg(original));

        assertThat(roundTripped)
            .isEqualTo(original,
                    within(TOLERANCE));
    }

    @Test
    @DisplayName("zero converts to zero")
    void zeroConvertsToZero() {
        assertThat(WeightUnit.LB.toKg(0))
                .isZero();
    }
}
