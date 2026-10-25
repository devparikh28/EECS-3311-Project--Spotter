package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.api.Assertions
        .within;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for one performed training day. */
class WorkoutSessionTest {

    private static final LocalDate DAY =
            LocalDate.of(2026, 10, 20);

    private WorkoutSession session;

    @BeforeEach
    void setUp() {
        session = TestData.session(DAY,
                SetEntry.of(TestData.BACK_SQUAT,
                        100, 5, 8),
                SetEntry.of(TestData.BACK_SQUAT,
                        100, 5, 8.5),
                SetEntry.of(TestData.BENCH_PRESS,
                        70, 8, 7),
                SetEntry.withoutRpe(
                        TestData.LEG_CURL,
                        40, 12));
    }

    @Test
    @DisplayName(
        "total tonnage sums every set")
    void totalTonnageSumsEverySet() {
        double expected = 500 + 500 + 560 + 480;

        assertThat(session.totalTonnage())
                .isEqualTo(expected,
                        within(0.001));
    }

    @Test
    @DisplayName(
        "tonnage for one lift ignores the rest")
    void tonnageForOneLift() {
        assertThat(session.tonnageFor(
                        Lift.SQUAT))
                .isEqualTo(1000, within(0.001));
        assertThat(session.tonnageFor(
                        Lift.BENCH))
                .isEqualTo(560, within(0.001));
        assertThat(session.tonnageFor(
                        Lift.DEADLIFT))
                .isZero();
    }

    @Test
    @DisplayName(
        "accessory work is not counted as a"
        + " competition lift")
    void accessoriesAreNotCompetitionLifts() {
        List<SetEntry> squatSets =
                session.setsFor(Lift.SQUAT);

        assertThat(squatSets).hasSize(2);
        assertThat(squatSets)
                .allMatch(s -> s.exercise()
                        .equals(TestData
                                .BACK_SQUAT));
    }

    @Test
    @DisplayName("an empty session is allowed")
    void emptySessionIsAllowed() {
        WorkoutSession empty =
                TestData.session(DAY);

        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.totalTonnage())
                .isZero();
    }

    @Test
    @DisplayName("a session needs a date")
    void dateIsRequired() {
        assertThatThrownBy(() ->
                new WorkoutSession(1, null,
                        "hevy", List.of()))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("date");
    }

    @Test
    @DisplayName(
        "the set list cannot be changed from"
        + " outside")
    void setsAreNotModifiable() {
        assertThatThrownBy(() ->
                session.sets().add(
                    SetEntry.withoutRpe(
                        TestData.PUSH_UP,
                        0, 10)))
            .isInstanceOf(
                UnsupportedOperationException
                    .class);
    }

    @Test
    @DisplayName(
        "a missing source defaults to manual")
    void missingSourceDefaultsToManual() {
        WorkoutSession s = new WorkoutSession(
                1, DAY, null, List.of());

        assertThat(s.source())
                .isEqualTo("manual");
    }
}
