package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.api.Assertions
        .within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for the reps and RPE lookup. */
class RPEChartTest {

    private static final double TOLERANCE =
            0.001;

    private RPEChart chart;

    @BeforeEach
    void setUp() {
        chart = new RPEChart();
    }

    @Test
    @DisplayName(
        "one rep at RPE 10 is the whole maximum")
    void singleAtTenIsTheMaximum() {
        assertThat(chart.percentOf1RM(1, 10))
            .isEqualTo(100.0,
                    within(TOLERANCE));
    }

    @ParameterizedTest(name =
        "{0} reps at RPE {1} is {2} percent")
    @CsvSource({
        "1, 10,  100.0",
        "1,  9,   95.5",
        "2,  8,   89.2",
        "3,  8,   86.3",
        "5,  8,   81.1",
        "5,  9,   83.7",
        "8,  7,   70.7",
        "10, 8,   68.0",
        "12, 6,   57.4"
    })
    @DisplayName("known chart values are correct")
    void knownValuesAreCorrect(
            int reps, double rpe,
            double expected) {
        assertThat(chart.percentOf1RM(reps, rpe))
            .isEqualTo(expected,
                    within(TOLERANCE));
    }

    @Test
    @DisplayName("half point RPE is supported")
    void halfPointsAreSupported() {
        assertThat(chart.percentOf1RM(5, 8.5))
            .isEqualTo(82.4, within(TOLERANCE));
        assertThat(chart.percentOf1RM(1, 9.5))
            .isEqualTo(97.8, within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "harder sets are a higher percentage"
        + " at the same reps")
    void harderIsHigherAtSameReps() {
        double atSeven =
                chart.percentOf1RM(5, 7);
        double atEight =
                chart.percentOf1RM(5, 8);
        double atNine =
                chart.percentOf1RM(5, 9);

        assertThat(atSeven)
                .isLessThan(atEight);
        assertThat(atEight)
                .isLessThan(atNine);
    }

    @Test
    @DisplayName(
        "more reps are a lower percentage at"
        + " the same effort")
    void moreRepsIsLowerAtSameEffort() {
        double three =
                chart.percentOf1RM(3, 8);
        double five =
                chart.percentOf1RM(5, 8);
        double eight =
                chart.percentOf1RM(8, 8);

        assertThat(three).isGreaterThan(five);
        assertThat(five).isGreaterThan(eight);
    }

    @Test
    @DisplayName(
        "a set longer than the chart is"
        + " refused, not extrapolated")
    void tooManyRepsIsRefused() {
        assertThatThrownBy(() ->
                chart.percentOf1RM(13, 8))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("12 reps");
    }

    @Test
    @DisplayName("an RPE below 6 is refused")
    void tooEasyIsRefused() {
        assertThatThrownBy(() ->
                chart.percentOf1RM(5, 5.5))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("too easy");
    }

    @Test
    @DisplayName("an RPE above 10 is refused")
    void impossibleRpeIsRefused() {
        assertThatThrownBy(() ->
                chart.percentOf1RM(5, 10.5))
            .isInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    @DisplayName(
        "a quarter point RPE is refused")
    void quarterPointIsRefused() {
        assertThatThrownBy(() ->
                chart.percentOf1RM(5, 8.25))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("half point");
    }

    @Test
    @DisplayName(
        "covers reports what the chart holds")
    void coversReportsWhatIsHeld() {
        assertThat(chart.covers(5, 8)).isTrue();
        assertThat(chart.covers(12, 6)).isTrue();
        assertThat(chart.covers(13, 8))
                .isFalse();
        assertThat(chart.covers(5, 5))
                .isFalse();
        assertThat(chart.covers(5, 8.25))
                .isFalse();
    }
}
