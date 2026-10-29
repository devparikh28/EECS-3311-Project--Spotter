package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.api.Assertions
        .within;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the strength figures.
 *
 * These matter more than most: the block
 * generation agent is handed these numbers as
 * evidence, so an error here becomes a wrong
 * training plan rather than a wrong display.
 */
class StrengthAnalyticsTest {

    private static final double TOLERANCE =
            0.01;

    private static final LocalDate MON_1 =
            LocalDate.of(2026, 10, 5);
    private static final LocalDate WED_1 =
            LocalDate.of(2026, 10, 7);
    private static final LocalDate MON_2 =
            LocalDate.of(2026, 10, 12);

    private StrengthAnalytics analytics;

    @BeforeEach
    void setUp() {
        analytics = new StrengthAnalytics();
    }

    @Test
    @DisplayName(
        "a set of five at RPE 8 estimates its"
        + " one rep max")
    void estimatesFromAWorkingSet() {
        // 81.1 percent, so 100 / 0.811
        SetEntry set = SetEntry.of(
                TestData.BACK_SQUAT, 100, 5, 8);

        assertThat(analytics.computeE1RM(set))
            .isEqualTo(123.30,
                    within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "a single at RPE 10 is its own maximum")
    void aTrueSingleIsItsOwnMaximum() {
        SetEntry set = SetEntry.of(
                TestData.BACK_SQUAT, 150, 1, 10);

        assertThat(analytics.computeE1RM(set))
            .isEqualTo(150.0,
                    within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "an easier set of the same weight"
        + " implies a higher maximum")
    void easierSetImpliesHigherMaximum() {
        SetEntry hard = SetEntry.of(
                TestData.BACK_SQUAT, 100, 5, 9);
        SetEntry easy = SetEntry.of(
                TestData.BACK_SQUAT, 100, 5, 7);

        assertThat(analytics.computeE1RM(easy))
            .isGreaterThan(
                analytics.computeE1RM(hard));
    }

    @Test
    @DisplayName(
        "a set with no RPE cannot be estimated"
        + " from")
    void setWithoutRpeIsRefused() {
        SetEntry set = SetEntry.withoutRpe(
                TestData.BACK_SQUAT, 100, 5);

        assertThat(analytics.canEstimate(set))
                .isFalse();
        assertThatThrownBy(() ->
                analytics.computeE1RM(set))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("no RPE");
    }

    @Test
    @DisplayName(
        "the series keeps the best set of each"
        + " day, oldest first")
    void seriesKeepsBestOfEachDay() {
        List<WorkoutSession> history = List.of(
            TestData.session(MON_1,
                SetEntry.of(TestData.BACK_SQUAT,
                        100, 5, 8),
                SetEntry.of(TestData.BACK_SQUAT,
                        110, 5, 8)),
            TestData.session(MON_2,
                SetEntry.of(TestData.BACK_SQUAT,
                        105, 5, 8)));

        List<Point> series =
                analytics.e1rmSeries(
                        Lift.SQUAT, history);

        assertThat(series).hasSize(2);
        assertThat(series.get(0).date())
                .isEqualTo(MON_1);
        assertThat(series.get(0).value())
            .isEqualTo(135.63,
                    within(TOLERANCE));
        assertThat(series.get(1).date())
                .isEqualTo(MON_2);
    }

    @Test
    @DisplayName(
        "sets without an RPE are skipped, not"
        + " guessed at")
    void setsWithoutRpeAreSkipped() {
        List<WorkoutSession> history = List.of(
            TestData.session(MON_1,
                SetEntry.withoutRpe(
                        TestData.BACK_SQUAT,
                        200, 5)));

        assertThat(analytics.e1rmSeries(
                Lift.SQUAT, history)).isEmpty();
        assertThat(analytics.bestSet(
                Lift.SQUAT, history)).isEmpty();
    }

    @Test
    @DisplayName(
        "other lifts do not appear in a lift's"
        + " series")
    void otherLiftsAreExcluded() {
        List<WorkoutSession> history = List.of(
            TestData.session(MON_1,
                SetEntry.of(TestData.BENCH_PRESS,
                        80, 5, 8),
                SetEntry.of(TestData.LEG_CURL,
                        40, 10, 8)));

        assertThat(analytics.e1rmSeries(
                Lift.SQUAT, history)).isEmpty();
        assertThat(analytics.e1rmSeries(
                Lift.BENCH, history)).hasSize(1);
    }

    @Test
    @DisplayName(
        "weekly tonnage sums sessions within a"
        + " week and dates them to its Monday")
    void weeklyTonnageSumsTheWeek() {
        List<WorkoutSession> history = List.of(
            TestData.session(MON_1,
                SetEntry.of(TestData.BACK_SQUAT,
                        100, 5, 8)),
            TestData.session(WED_1,
                SetEntry.of(TestData.BACK_SQUAT,
                        100, 5, 8)),
            TestData.session(MON_2,
                SetEntry.of(TestData.BACK_SQUAT,
                        100, 3, 8)));

        List<Point> tonnage =
                analytics.weeklyTonnage(
                        Lift.SQUAT, history);

        assertThat(tonnage).hasSize(2);
        assertThat(tonnage.get(0).date())
                .isEqualTo(MON_1);
        assertThat(tonnage.get(0).value())
            .isEqualTo(1000, within(TOLERANCE));
        assertThat(tonnage.get(1).value())
            .isEqualTo(300, within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "tonnage counts sets with no RPE,"
        + " because it needs no estimate")
    void tonnageCountsSetsWithoutRpe() {
        List<WorkoutSession> history = List.of(
            TestData.session(MON_1,
                SetEntry.withoutRpe(
                        TestData.BACK_SQUAT,
                        100, 5)));

        assertThat(analytics.weeklyTonnage(
                Lift.SQUAT, history))
            .singleElement()
            .extracting(Point::value)
            .isEqualTo(500.0);
    }

    @Test
    @DisplayName(
        "the best set is the one implying the"
        + " highest maximum, not the heaviest")
    void bestSetIsByEstimateNotWeight() {
        SetEntry heavierButHarder = SetEntry.of(
                TestData.BACK_SQUAT, 140, 1, 10);
        SetEntry lighterButEasier = SetEntry.of(
                TestData.BACK_SQUAT, 130, 1, 8);

        List<WorkoutSession> history = List.of(
            TestData.session(MON_1,
                heavierButHarder,
                lighterButEasier));

        assertThat(analytics.bestSet(
                Lift.SQUAT, history))
            .contains(lighterButEasier);
    }

    @Test
    @DisplayName(
        "current maxima are reported per lift")
    void currentMaximaPerLift() {
        List<WorkoutSession> history = List.of(
            TestData.session(MON_1,
                SetEntry.of(TestData.BACK_SQUAT,
                        100, 5, 8),
                SetEntry.of(TestData.BENCH_PRESS,
                        70, 5, 8)));

        Map<Lift, Double> current =
                analytics.currentE1RMs(history);

        assertThat(current)
                .containsOnlyKeys(Lift.SQUAT,
                        Lift.BENCH);
        assertThat(current.get(Lift.SQUAT))
            .isEqualTo(123.30,
                    within(TOLERANCE));
    }

    @Test
    @DisplayName(
        "a lift never trained is left out"
        + " rather than reported as zero")
    void untrainedLiftIsLeftOut() {
        List<WorkoutSession> history = List.of(
            TestData.session(MON_1,
                SetEntry.of(TestData.BACK_SQUAT,
                        100, 5, 8)));

        assertThat(analytics.currentE1RMs(
                history))
            .doesNotContainKey(Lift.DEADLIFT);
    }

    @Test
    @DisplayName(
        "an empty history gives empty results,"
        + " not a failure")
    void emptyHistoryIsHandled() {
        assertThat(analytics.e1rmSeries(
                Lift.SQUAT, List.of()))
                .isEmpty();
        assertThat(analytics.currentE1RMs(
                List.of())).isEmpty();
        assertThat(analytics.weeklyTonnage(
                Lift.SQUAT, null)).isEmpty();
    }

    @Test
    @DisplayName(
        "a trend needs two points; one"
        + " measurement is not a trend")
    void trendNeedsTwoPoints() {
        List<Point> single = List.of(
                new Point(MON_1, 120));

        assertThat(analytics.trend(single))
                .isEmpty();
    }

    @Test
    @DisplayName(
        "a rising trend is positive and a"
        + " falling one negative")
    void trendDirection() {
        List<Point> rising = List.of(
                new Point(MON_1, 120),
                new Point(MON_2, 130));
        List<Point> falling = List.of(
                new Point(MON_1, 130),
                new Point(MON_2, 120));

        assertThat(analytics.trend(rising))
                .contains(10.0);
        assertThat(analytics.trend(falling))
                .contains(-10.0);
    }

    @Test
    @DisplayName(
        "a week starts on its Monday whichever"
        + " day is given")
    void weekStartsOnMonday() {
        assertThat(StrengthAnalytics.weekStart(
                WED_1)).isEqualTo(MON_1);
        assertThat(StrengthAnalytics.weekStart(
                MON_1)).isEqualTo(MON_1);
        assertThat(StrengthAnalytics.weekStart(
                LocalDate.of(2026, 10, 11)))
                .isEqualTo(MON_1);
    }
}
