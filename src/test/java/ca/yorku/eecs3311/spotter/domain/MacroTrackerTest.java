package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.api.Assertions
        .within;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for macro targets and adherence. */
class MacroTrackerTest {

    private static final double TOLERANCE =
            0.01;

    private static final LocalDate MONDAY =
            LocalDate.of(2026, 10, 5);

    /** 100 g of chicken breast. */
    private static final FoodItem CHICKEN =
            new FoodItem("Chicken Breast",
                new MacroTotals(165, 31, 0, 3.6),
                false, false, false);

    /** 100 g of rice, dry weight. */
    private static final FoodItem RICE =
            new FoodItem("White Rice",
                new MacroTotals(360, 7, 79, 0.6),
                true, false, false);

    private static final FoodItem CHEESE =
            new FoodItem("Cheddar",
                new MacroTotals(403, 25, 1.3,
                        33),
                true, true, false);

    private MacroTarget target;
    private MacroTracker tracker;

    @BeforeEach
    void setUp() {
        target = new MacroTarget(
                2800, 180, 320, 80);
        tracker = new MacroTracker(target);
    }

    @Test
    @DisplayName(
        "logging food adds to the day's total")
    void loggingAddsToTheDay() {
        MacroTotals after =
            tracker.recordIntake(MONDAY,
                new FoodEntry(CHICKEN, 200));

        assertThat(after.calories())
            .isEqualTo(330, within(TOLERANCE));
        assertThat(after.proteinG())
            .isEqualTo(62, within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "several entries on a day accumulate")
    void entriesAccumulate() {
        tracker.recordIntake(MONDAY,
                new FoodEntry(CHICKEN, 200));
        tracker.recordIntake(MONDAY,
                new FoodEntry(RICE, 150));

        assertThat(tracker.consumedOn(MONDAY)
                .calories())
            .isEqualTo(330 + 540,
                    within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "remaining is the target less what was"
        + " eaten")
    void remainingIsTargetLessEaten() {
        tracker.recordIntake(MONDAY,
                new FoodEntry(CHICKEN, 200));

        MacroTotals left =
                tracker.getRemaining(MONDAY);

        assertThat(left.calories())
            .isEqualTo(2470, within(TOLERANCE));
        assertThat(left.proteinG())
            .isEqualTo(118, within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "eating past the target leaves nothing"
        + " remaining, not a negative amount")
    void overeatingLeavesNothing() {
        tracker.recordIntake(MONDAY,
                new FoodEntry(RICE, 1000));

        MacroTotals left =
                tracker.getRemaining(MONDAY);

        assertThat(left.calories()).isZero();
        assertThat(left.carbsG()).isZero();
    }

    @Test
    @DisplayName(
        "a day with nothing logged leaves the"
        + " whole target remaining")
    void untouchedDayLeavesTheWholeTarget() {
        MacroTotals left =
                tracker.getRemaining(MONDAY);

        assertThat(left.calories())
            .isEqualTo(2800, within(TOLERANCE));
        assertThat(tracker.consumedOn(MONDAY))
            .isEqualTo(MacroTotals.ZERO);
    }

    @Test
    @DisplayName("days do not bleed into each"
        + " other")
    void daysAreSeparate() {
        tracker.recordIntake(MONDAY,
                new FoodEntry(CHICKEN, 200));

        assertThat(tracker.consumedOn(
                MONDAY.plusDays(1)))
            .isEqualTo(MacroTotals.ZERO);
    }

    @Test
    @DisplayName(
        "hitting the target every day scores"
        + " full adherence")
    void perfectWeekScoresOne() {
        for (int day = 0; day < 7; day++) {
            // 777.8 g of rice is 2800 kcal
            tracker.recordIntake(
                MONDAY.plusDays(day),
                new FoodEntry(RICE,
                        2800 / 3.60));
        }

        assertThat(tracker.weeklyAdherence(
                MONDAY))
            .isEqualTo(1.0, within(0.01));
    }

    @Test
    @DisplayName(
        "a week with nothing logged scores"
        + " zero, not full marks")
    void emptyWeekScoresZero() {
        assertThat(tracker.weeklyAdherence(
                MONDAY)).isZero();
    }

    @Test
    @DisplayName(
        "logging only part of the week scores"
        + " proportionally")
    void partialWeekScoresProportionally() {
        for (int day = 0; day < 4; day++) {
            tracker.recordIntake(
                MONDAY.plusDays(day),
                new FoodEntry(RICE,
                        2800 / 3.60));
        }

        assertThat(tracker.weeklyAdherence(
                MONDAY))
            .isEqualTo(4.0 / 7.0, within(0.01));
        assertThat(tracker.daysLogged(MONDAY))
                .isEqualTo(4);
    }

    @Test
    @DisplayName(
        "being far off the target lowers the"
        + " score for that day")
    void beingFarOffLowersTheScore() {
        // half the target on each of 7 days
        for (int day = 0; day < 7; day++) {
            tracker.recordIntake(
                MONDAY.plusDays(day),
                new FoodEntry(RICE,
                        1400 / 3.60));
        }

        assertThat(tracker.weeklyAdherence(
                MONDAY))
            .isEqualTo(0.5, within(0.01));
    }

    @Test
    @DisplayName(
        "days outside the week are not counted")
    void daysOutsideTheWeekAreIgnored() {
        tracker.recordIntake(
            MONDAY.plusDays(7),
            new FoodEntry(RICE, 2800 / 3.60));

        assertThat(tracker.weeklyAdherence(
                MONDAY)).isZero();
        assertThat(tracker.daysLogged(MONDAY))
                .isZero();
    }

    @Test
    @DisplayName(
        "dietary constraints filter foods"
        + " before the model ever sees them")
    void constraintsFilterFoods() {
        DietaryConstraints vegetarian =
            new DietaryConstraints(
                true, true, true);
        DietaryConstraints vegan =
            new DietaryConstraints(
                true, false, false);

        assertThat(vegetarian.permits(CHICKEN))
                .isFalse();
        assertThat(vegetarian.permits(RICE))
                .isTrue();
        assertThat(vegetarian.permits(CHEESE))
                .isTrue();

        assertThat(vegan.permits(CHEESE))
                .isFalse();
        assertThat(vegan.permits(RICE))
                .isTrue();

        assertThat(DietaryConstraints.none()
                .permits(CHICKEN)).isTrue();
    }

    @Test
    @DisplayName(
        "nutrition scales with the weight eaten")
    void nutritionScalesWithWeight() {
        assertThat(CHICKEN.forGrams(250)
                .proteinG())
            .isEqualTo(77.5, within(TOLERANCE));
        assertThat(CHICKEN.forGrams(0)
                .isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a zero weight entry is refused")
    void zeroWeightEntryIsRefused() {
        assertThatThrownBy(() ->
                new FoodEntry(CHICKEN, 0))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("above zero");
    }

    @Test
    @DisplayName(
        "a target without calories is refused")
    void targetNeedsCalories() {
        assertThatThrownBy(() ->
                new MacroTarget(0, 180, 320, 80))
            .isInstanceOf(
                IllegalArgumentException.class);
    }
}
