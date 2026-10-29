package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.api.Assertions
        .within;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for recovery flagging and the fallback. */
class RecoveryRuleEngineTest {

    private static final LocalDate DAY =
            LocalDate.of(2026, 11, 4);

    private RecoveryRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RecoveryRuleEngine();
    }

    private RecoveryDay.Builder wearable() {
        return RecoveryDay.on(DAY,
                RecoverySource.WEARABLE_CSV);
    }

    private RecoveryDay.Builder checkIn() {
        return RecoveryDay.on(DAY,
                RecoverySource.MANUAL_CHECK_IN);
    }

    @Test
    @DisplayName(
        "a good day raises no flag")
    void goodDayRaisesNothing() {
        RecoveryDay day = wearable()
                .recoveryScore(78)
                .sleepHours(8.0)
                .build();

        assertThat(engine.evaluate(day))
                .isEmpty();
    }

    @Test
    @DisplayName(
        "a low recovery score is flagged with"
        + " the number that caused it")
    void lowScoreIsFlagged() {
        RecoveryDay day = wearable()
                .recoveryScore(28)
                .sleepHours(7.5)
                .build();

        Optional<RecoveryFlag> flag =
                engine.evaluate(day);

        assertThat(flag).isPresent();
        assertThat(flag.get().summary())
                .contains("28");
        assertThat(flag.get().date())
                .isEqualTo(DAY);
    }

    @Test
    @DisplayName("short sleep is flagged")
    void shortSleepIsFlagged() {
        RecoveryDay day = wearable()
                .recoveryScore(70)
                .sleepHours(4.5)
                .build();

        assertThat(engine.evaluate(day))
                .isPresent();
        assertThat(engine.evaluate(day).get()
                .summary())
                .contains("4.5");
    }

    @Test
    @DisplayName(
        "high soreness and low energy are"
        + " flagged from a manual check in")
    void checkInReadingsAreFlagged() {
        RecoveryDay day = checkIn()
                .sleepHours(7.0)
                .soreness(5)
                .energy(1)
                .build();

        RecoveryFlag flag =
                engine.evaluate(day).orElseThrow();

        assertThat(flag.reasons()).hasSize(2);
        assertThat(flag.summary())
                .contains("soreness")
                .contains("energy");
    }

    @Test
    @DisplayName(
        "one poor reading is mild, several is"
        + " significant")
    void severityFollowsTheCount() {
        RecoveryDay oneProblem = wearable()
                .recoveryScore(20)
                .sleepHours(8.0)
                .build();
        RecoveryDay severalProblems = wearable()
                .recoveryScore(20)
                .sleepHours(4.0)
                .build();

        assertThat(engine.evaluate(oneProblem)
                .orElseThrow().isSignificant())
                .isFalse();
        assertThat(engine.evaluate(
                severalProblems).orElseThrow()
                .isSignificant()).isTrue();
    }

    @Test
    @DisplayName(
        "readings exactly at the threshold are"
        + " not flagged")
    void thresholdItselfIsAcceptable() {
        RecoveryDay day = wearable()
            .recoveryScore(
                RecoveryRuleEngine
                    .MIN_RECOVERY_SCORE)
            .sleepHours(
                RecoveryRuleEngine
                    .MIN_SLEEP_HOURS)
            .build();

        assertThat(engine.evaluate(day))
                .isEmpty();
    }

    @Test
    @DisplayName(
        "a day with no readings raises nothing")
    void emptyDayRaisesNothing() {
        assertThat(engine.evaluate(
                checkIn().build())).isEmpty();
        assertThat(engine.evaluate(null))
                .isEmpty();
    }

    @Test
    @DisplayName(
        "missing readings are not treated as"
        + " zero")
    void missingReadingsAreNotZero() {
        // Sleep absent entirely. If it were
        // read as 0 this would be flagged.
        RecoveryDay day = wearable()
                .recoveryScore(80)
                .build();

        assertThat(engine.evaluate(day))
                .isEmpty();
    }

    @Test
    @DisplayName(
        "the fallback cuts load and removes a"
        + " set")
    void fallbackCutsLoadAndSets() {
        Session original = new Session(1, DAY,
            "Squat", List.of(
                new ExercisePrescription(101,
                    TestData.BACK_SQUAT,
                    4, 5, 8.0, 100)));

        Session reduced =
                engine.fallbackAdjustment(
                        original);

        ExercisePrescription p =
                reduced.prescriptions().get(0);
        assertThat(p.loadKg())
                .isEqualTo(90, within(0.001));
        assertThat(p.sets()).isEqualTo(3);
        assertThat(p.reps()).isEqualTo(5);
    }

    @Test
    @DisplayName(
        "the reduced load is a weight that can"
        + " actually be loaded")
    void reducedLoadIsLoadable() {
        Session original = new Session(1, DAY,
            null, List.of(
                new ExercisePrescription(101,
                    TestData.BACK_SQUAT,
                    3, 5, 8.0, 142.5)));

        double reduced =
            engine.fallbackAdjustment(original)
                .prescriptions().get(0).loadKg();

        assertThat(reduced % 2.5)
                .isEqualTo(0, within(1e-9));
    }

    @Test
    @DisplayName(
        "a single set exercise keeps its set")
    void singleSetIsNotRemoved() {
        Session original = new Session(1, DAY,
            null, List.of(
                new ExercisePrescription(101,
                    TestData.BACK_SQUAT,
                    1, 1, 9.0, 180)));

        assertThat(engine.fallbackAdjustment(
                original).prescriptions()
                .get(0).sets()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "the fallback leaves the original"
        + " session untouched, so the edit can"
        + " be undone")
    void originalIsUntouched() {
        ExercisePrescription prescription =
            new ExercisePrescription(101,
                TestData.BACK_SQUAT,
                4, 5, 8.0, 100);
        Session original = new Session(1, DAY,
                "Squat",
                List.of(prescription));

        engine.fallbackAdjustment(original);

        assertThat(original.prescriptions())
                .containsExactly(prescription);
        assertThat(original.wasAdjusted())
                .isFalse();
    }

    @Test
    @DisplayName(
        "the adjusted session explains that"
        + " the coach was unavailable")
    void adjustmentExplainsItself() {
        Session original = new Session(1, DAY,
            null, List.of(
                new ExercisePrescription(101,
                    TestData.BACK_SQUAT,
                    4, 5, 8.0, 100)));

        Session reduced =
            engine.fallbackAdjustment(original);

        assertThat(reduced.wasAdjusted())
                .isTrue();
        assertThat(reduced.adjustmentNote()
                .orElseThrow())
                .contains("unavailable")
                .contains("90");
    }

    @Test
    @DisplayName(
        "adjusting a session with no exercises"
        + " is harmless")
    void emptySessionIsHarmless() {
        Session empty = new Session(1, DAY,
                null, List.of());

        assertThat(engine.fallbackAdjustment(
                empty).prescriptions())
                .isEmpty();
    }

    @Test
    @DisplayName("a null session is refused")
    void nullSessionIsRefused() {
        assertThatThrownBy(() ->
            engine.fallbackAdjustment(null))
            .isInstanceOf(
                IllegalArgumentException.class);
    }
}
