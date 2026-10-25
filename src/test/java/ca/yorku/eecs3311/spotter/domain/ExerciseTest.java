package ca.yorku.eecs3311.spotter.domain;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for the exercise catalog entry. */
class ExerciseTest {

    @Test
    @DisplayName(
        "an exercise is performable when the"
        + " lifter has its equipment")
    void performableWithRequiredEquipment() {
        Set<Equipment> gym = Set.of(
                Equipment.BARBELL,
                Equipment.SQUAT_RACK,
                Equipment.BENCH);

        assertThat(TestData.BACK_SQUAT
                .isPerformableWith(gym))
                .isTrue();
    }

    @Test
    @DisplayName(
        "a missing piece makes it unavailable")
    void notPerformableWhenSomethingIsMissing() {
        Set<Equipment> gym = Set.of(
                Equipment.BARBELL);

        assertThat(TestData.BACK_SQUAT
                .isPerformableWith(gym))
                .isFalse();
    }

    @Test
    @DisplayName(
        "an exercise needing nothing is always"
        + " performable")
    void bodyweightIsAlwaysPerformable() {
        assertThat(TestData.PUSH_UP
                .isPerformableWith(Set.of()))
                .isTrue();
    }

    @Test
    @DisplayName(
        "competition lifts are identified")
    void competitionLiftsAreIdentified() {
        assertThat(TestData.BACK_SQUAT
                .isCompetitionLift()).isTrue();
        assertThat(TestData.BACK_SQUAT
                .competitionLift())
                .contains(Lift.SQUAT);

        assertThat(TestData.LEG_CURL
                .isCompetitionLift()).isFalse();
        assertThat(TestData.LEG_CURL
                .competitionLift()).isEmpty();
    }

    @Test
    @DisplayName(
        "names match regardless of case, so a"
        + " CSV spelling still resolves")
    void namesMatchIgnoringCase() {
        Exercise lowerCase = new Exercise(99,
                "back squat", Set.of(), null,
                Set.of(), Lift.SQUAT);

        assertThat(lowerCase)
                .isEqualTo(TestData.BACK_SQUAT);
        assertThat(lowerCase.hashCode())
                .isEqualTo(TestData.BACK_SQUAT
                        .hashCode());
    }

    @Test
    @DisplayName("a blank name is refused")
    void blankNameIsRefused() {
        assertThatThrownBy(() ->
                new Exercise(1, "   ",
                    Set.of(), null, Set.of(),
                    null))
            .isInstanceOf(
                IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    @DisplayName(
        "the equipment set cannot be changed"
        + " from outside")
    void equipmentIsNotModifiable() {
        assertThatThrownBy(() ->
                TestData.BACK_SQUAT
                    .requiredEquipment()
                    .add(Equipment.BANDS))
            .isInstanceOf(
                UnsupportedOperationException
                    .class);
    }
}
