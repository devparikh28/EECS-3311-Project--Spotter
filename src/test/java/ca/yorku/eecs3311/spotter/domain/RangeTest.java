package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.api.Assertions
        .within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for the acceptable load range. */
class RangeTest {

    @Test
    @DisplayName(
        "a load inside the range is accepted")
    void loadInsideIsAccepted() {
        Range range = new Range(100, 110);

        assertThat(range.contains(105))
                .isTrue();
    }

    @Test
    @DisplayName("the ends are included")
    void endsAreIncluded() {
        Range range = new Range(100, 110);

        assertThat(range.contains(100))
                .isTrue();
        assertThat(range.contains(110))
                .isTrue();
    }

    @Test
    @DisplayName(
        "a load outside the range is refused")
    void loadOutsideIsRefused() {
        Range range = new Range(100, 110);

        assertThat(range.contains(99.9))
                .isFalse();
        assertThat(range.contains(110.1))
                .isFalse();
    }

    @Test
    @DisplayName("midpoint and width are reported")
    void midpointAndWidth() {
        Range range = new Range(100, 110);

        assertThat(range.midpoint())
                .isEqualTo(105, within(0.001));
        assertThat(range.width())
                .isEqualTo(10, within(0.001));
    }

    @Test
    @DisplayName("a single value range is valid")
    void singleValueRangeIsValid() {
        Range range = new Range(100, 100);

        assertThat(range.contains(100))
                .isTrue();
        assertThat(range.width()).isZero();
    }

    @Test
    @DisplayName(
        "a range that ends below its start"
        + " is refused")
    void backwardsRangeIsRefused() {
        assertThatThrownBy(
                () -> new Range(110, 100))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("below");
    }

    @Test
    @DisplayName("a negative range is refused")
    void negativeRangeIsRefused() {
        assertThatThrownBy(
                () -> new Range(-5, 100))
            .isInstanceOf(
                IllegalArgumentException.class);
    }
}
