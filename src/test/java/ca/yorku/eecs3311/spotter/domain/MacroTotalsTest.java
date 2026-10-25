package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.api.Assertions
        .within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for macro arithmetic. */
class MacroTotalsTest {

    private static final double TOLERANCE =
            0.001;

    @Test
    @DisplayName("adding sums every figure")
    void addingSumsEveryFigure() {
        MacroTotals a = new MacroTotals(
                500, 40, 50, 15);
        MacroTotals b = new MacroTotals(
                300, 25, 30, 10);

        MacroTotals sum = a.plus(b);

        assertThat(sum.calories())
                .isEqualTo(800, within(TOLERANCE));
        assertThat(sum.proteinG())
                .isEqualTo(65, within(TOLERANCE));
        assertThat(sum.carbsG())
                .isEqualTo(80, within(TOLERANCE));
        assertThat(sum.fatG())
                .isEqualTo(25, within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "subtracting gives what is left")
    void subtractingGivesRemainder() {
        MacroTotals target = new MacroTotals(
                2800, 180, 300, 80);
        MacroTotals eaten = new MacroTotals(
                1800, 120, 200, 50);

        MacroTotals left =
                target.minus(eaten);

        assertThat(left.calories())
            .isEqualTo(1000, within(TOLERANCE));
        assertThat(left.proteinG())
            .isEqualTo(60, within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "eating past the target leaves zero,"
        + " not a negative amount")
    void remainderIsFlooredAtZero() {
        MacroTotals target = new MacroTotals(
                2000, 150, 200, 60);
        MacroTotals eaten = new MacroTotals(
                2500, 200, 260, 90);

        MacroTotals left =
                target.minus(eaten);

        assertThat(left.calories()).isZero();
        assertThat(left.proteinG()).isZero();
        assertThat(left.carbsG()).isZero();
        assertThat(left.fatG()).isZero();
        assertThat(left.isEmpty()).isTrue();
    }

    @Test
    @DisplayName(
        "scaling works out a portion size")
    void scalingWorksOutAPortion() {
        MacroTotals per100g = new MacroTotals(
                165, 31, 0, 3.6);

        MacroTotals for150g =
                per100g.scaled(1.5);

        assertThat(for150g.calories())
            .isEqualTo(247.5, within(TOLERANCE));
        assertThat(for150g.proteinG())
            .isEqualTo(46.5, within(TOLERANCE));
    }

    @Test
    @DisplayName("scaling by zero gives nothing")
    void scalingByZeroGivesNothing() {
        MacroTotals food = new MacroTotals(
                165, 31, 0, 3.6);

        assertThat(food.scaled(0).isEmpty())
                .isTrue();
    }

    @Test
    @DisplayName("negative figures are refused")
    void negativeFiguresAreRefused() {
        assertThatThrownBy(() ->
                new MacroTotals(-1, 0, 0, 0))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("negative");
    }

    @Test
    @DisplayName(
        "a negative scale factor is refused")
    void negativeScaleIsRefused() {
        assertThatThrownBy(
                () -> MacroTotals.ZERO
                        .scaled(-1))
            .isInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    @DisplayName("adding to zero changes nothing")
    void zeroIsTheIdentity() {
        MacroTotals food = new MacroTotals(
                400, 30, 45, 12);

        assertThat(MacroTotals.ZERO.plus(food))
                .isEqualTo(food);
    }
}
