package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.api.Assertions
        .within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for a single performed set. */
class SetEntryTest {

    @Test
    @DisplayName("tonnage is weight times reps")
    void tonnageIsWeightTimesReps() {
        SetEntry set = SetEntry.of(
                TestData.BACK_SQUAT,
                100, 5, 8);

        assertThat(set.tonnage())
                .isEqualTo(500, within(0.001));
    }

    @Test
    @DisplayName("an RPE is kept when given")
    void rpeIsKept() {
        SetEntry set = SetEntry.of(
                TestData.BACK_SQUAT,
                100, 5, 8.5);

        assertThat(set.hasRpe()).isTrue();
        assertThat(set.rpe()).contains(8.5);
    }

    @Test
    @DisplayName(
        "a set without an RPE reports that"
        + " rather than inventing one")
    void missingRpeIsReported() {
        SetEntry set = SetEntry.withoutRpe(
                TestData.BACK_SQUAT, 100, 5);

        assertThat(set.hasRpe()).isFalse();
        assertThat(set.rpe()).isEmpty();
    }

    @Test
    @DisplayName("an RPE outside 1 to 10 is"
        + " refused")
    void outOfRangeRpeIsRefused() {
        assertThatThrownBy(() -> SetEntry.of(
                TestData.BACK_SQUAT,
                100, 5, 11))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("RPE");

        assertThatThrownBy(() -> SetEntry.of(
                TestData.BACK_SQUAT,
                100, 5, 0.5))
            .isInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a set needs at least one rep")
    void zeroRepsIsRefused() {
        assertThatThrownBy(() ->
                SetEntry.withoutRpe(
                    TestData.BACK_SQUAT, 100, 0))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("rep");
    }

    @Test
    @DisplayName("negative weight is refused")
    void negativeWeightIsRefused() {
        assertThatThrownBy(() ->
                SetEntry.withoutRpe(
                    TestData.BACK_SQUAT, -5, 5))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("negative");
    }

    @Test
    @DisplayName(
        "bodyweight work at zero kilograms"
        + " is allowed")
    void zeroWeightIsAllowed() {
        SetEntry set = SetEntry.withoutRpe(
                TestData.PUSH_UP, 0, 20);

        assertThat(set.tonnage()).isZero();
    }
}
