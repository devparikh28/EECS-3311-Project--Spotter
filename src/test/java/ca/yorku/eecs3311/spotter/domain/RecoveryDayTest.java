package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for a day's recovery readings. */
class RecoveryDayTest {

    private static final LocalDate DAY =
            LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName(
        "a wearable day carries an objective"
        + " score")
    void wearableDayHasObjectiveScore() {
        RecoveryDay day = RecoveryDay
                .on(DAY,
                    RecoverySource.WEARABLE_CSV)
                .recoveryScore(31)
                .hrvMs(42.5)
                .sleepHours(5.5)
                .build();

        assertThat(day.hasObjectiveScore())
                .isTrue();
        assertThat(day.recoveryScore())
                .hasValue(31);
        assertThat(day.soreness()).isEmpty();
    }

    @Test
    @DisplayName(
        "a manual check in has no objective"
        + " score")
    void manualCheckInHasNoScore() {
        RecoveryDay day = RecoveryDay
                .on(DAY,
                    RecoverySource
                        .MANUAL_CHECK_IN)
                .sleepHours(6.5)
                .soreness(4)
                .energy(2)
                .build();

        assertThat(day.hasObjectiveScore())
                .isFalse();
        assertThat(day.recoveryScore())
                .isEmpty();
        assertThat(day.soreness()).hasValue(4);
        assertThat(day.energy()).hasValue(2);
    }

    @Test
    @DisplayName(
        "a day with no readings reports itself"
        + " as empty")
    void emptyDayIsReported() {
        RecoveryDay day = RecoveryDay
                .on(DAY,
                    RecoverySource
                        .MANUAL_CHECK_IN)
                .build();

        assertThat(day.isEmpty()).isTrue();
    }

    @Test
    @DisplayName(
        "a recovery score outside 0 to 100"
        + " is refused")
    void outOfRangeScoreIsRefused() {
        assertThatThrownBy(() -> RecoveryDay
                .on(DAY,
                    RecoverySource.WEARABLE_CSV)
                .recoveryScore(101))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining(
                "Recovery score");
    }

    @Test
    @DisplayName(
        "more than 24 hours of sleep is"
        + " refused")
    void impossibleSleepIsRefused() {
        assertThatThrownBy(() -> RecoveryDay
                .on(DAY,
                    RecoverySource.WEARABLE_CSV)
                .sleepHours(25))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("24");
    }

    @Test
    @DisplayName(
        "soreness outside 1 to 5 is refused")
    void outOfRangeSorenessIsRefused() {
        assertThatThrownBy(() -> RecoveryDay
                .on(DAY,
                    RecoverySource
                        .MANUAL_CHECK_IN)
                .soreness(6))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("Soreness");
    }

    @Test
    @DisplayName("a date is required")
    void dateIsRequired() {
        assertThatThrownBy(() -> RecoveryDay
                .on(null,
                    RecoverySource.WEARABLE_CSV)
                .build())
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("date");
    }
}
