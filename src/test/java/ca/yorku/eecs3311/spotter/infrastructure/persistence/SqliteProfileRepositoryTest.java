package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .Limitation;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter.domain
        .WeightUnit;

/**
 * Tests for storing the lifter's profile.
 *
 * A profile spans four tables, so what is worth
 * checking is that everything survives the round
 * trip and that saving twice does not accumulate
 * duplicate equipment or limitations, which is
 * the mistake this shape of code invites.
 */
class SqliteProfileRepositoryTest {

    private Database database;
    private ProfileRepository repository;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        repository =
            new SqliteProfileRepository(
                    database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static LifterProfile sample() {
        return LifterProfile
            .with(TrainingGoal.STRENGTH,
                  ExperienceLevel.INTERMEDIATE)
            .bodyweightKg(82.5)
            .weightClassKg(83)
            .trainingDaysPerWeek(4)
            .displayUnit(WeightUnit.LB)
            .meetDate(
                LocalDate.of(2026, 12, 5))
            .equipment(Set.of(
                Equipment.BARBELL,
                Equipment.SQUAT_RACK,
                Equipment.BENCH))
            .diet(new DietaryConstraints(
                    true, true, false))
            .addLimitation(new Limitation(
                "left shoulder objects to"
                + " flat pressing",
                Set.of("horizontal press"),
                Set.of("Bench Press"), true))
            .build();
    }

    @Test
    @DisplayName(
        "a profile survives the round trip"
        + " whole")
    void aProfileSurvivesTheRoundTrip() {
        LifterProfile saved =
                repository.save(sample());

        assertThat(saved.id()).isPositive();

        LifterProfile read = repository
                .findById(saved.id())
                .orElseThrow();

        assertThat(read.goal())
            .isEqualTo(TrainingGoal.STRENGTH);
        assertThat(read.experience())
            .isEqualTo(ExperienceLevel
                    .INTERMEDIATE);
        assertThat(read.bodyweightKg())
            .isEqualTo(82.5);
        assertThat(read.weightClassKg()
                .orElseThrow())
            .isEqualTo(83.0);
        assertThat(read.trainingDaysPerWeek())
            .isEqualTo(4);
        assertThat(read.displayUnit())
            .isEqualTo(WeightUnit.LB);
        assertThat(read.meetDate()
                .orElseThrow())
            .isEqualTo(
                LocalDate.of(2026, 12, 5));
        assertThat(read.equipment())
            .containsExactlyInAnyOrder(
                Equipment.BARBELL,
                Equipment.SQUAT_RACK,
                Equipment.BENCH);
        assertThat(read.diet().vegetarian())
            .isTrue();
        assertThat(read.diet().allowsEggs())
            .isFalse();
    }

    @Test
    @DisplayName(
        "a limitation keeps its patterns, its"
        + " exercises and whether it hurts")
    void aLimitationSurvivesWhole() {
        LifterProfile read = repository
            .findById(repository.save(sample())
                    .id())
            .orElseThrow();

        assertThat(read.limitations())
            .hasSize(1);
        Limitation limitation =
                read.limitations().get(0);
        assertThat(limitation.description())
            .contains("left shoulder");
        assertThat(limitation.avoidPatterns())
            .containsExactly(
                "horizontal press");
        assertThat(limitation.avoidExercises())
            .containsExactly("bench press");
        assertThat(limitation.painful())
            .isTrue();
    }

    @Test
    @DisplayName(
        "saving the same profile twice does not"
        + " duplicate its equipment or"
        + " limitations")
    void savingTwiceDoesNotDuplicate() {
        LifterProfile first =
                repository.save(sample());
        LifterProfile second =
                repository.save(first);

        assertThat(second.id())
            .isEqualTo(first.id());
        assertThat(second.equipment())
            .hasSize(3);
        assertThat(second.limitations())
            .hasSize(1);
        assertThat(repository.findAll())
            .hasSize(1);
    }

    @Test
    @DisplayName(
        "an edit replaces what was there rather"
        + " than adding to it")
    void anEditReplaces() {
        LifterProfile saved =
                repository.save(sample());

        LifterProfile changed = LifterProfile
            .with(TrainingGoal.HYPERTROPHY,
                  ExperienceLevel.ADVANCED)
            .id(saved.id())
            .bodyweightKg(85)
            .trainingDaysPerWeek(5)
            .equipment(Set.of(
                    Equipment.DUMBBELL))
            .build();

        LifterProfile read =
                repository.save(changed);

        assertThat(read.goal())
            .isEqualTo(
                TrainingGoal.HYPERTROPHY);
        assertThat(read.equipment())
            .containsExactly(
                    Equipment.DUMBBELL);
        assertThat(read.limitations())
            .isEmpty();
        assertThat(read.meetDate()).isEmpty();
    }

    @Test
    @DisplayName(
        "no profile means onboarding has not"
        + " happened, not an error")
    void noProfileIsAnEmptyOptional() {
        assertThat(repository.current())
            .isEmpty();
        assertThat(repository.findAll())
            .isEmpty();
    }

    @Test
    @DisplayName(
        "current returns the profile to work"
        + " with")
    void currentReturnsTheProfile() {
        LifterProfile saved =
                repository.save(sample());

        assertThat(repository.current()
                .orElseThrow().id())
            .isEqualTo(saved.id());
    }

    @Test
    @DisplayName(
        "deleting a profile takes its equipment"
        + " and limitations with it")
    void deletingCascades() {
        LifterProfile saved =
                repository.save(sample());

        repository.delete(saved.id());

        assertThat(repository.findAll())
            .isEmpty();
        assertThat(repository
                .findById(saved.id()))
            .isEmpty();
    }

    @Test
    @DisplayName(
        "deleting something that is not there"
        + " is not an error")
    void deletingNothingIsFine() {
        repository.delete(404);

        assertThat(repository.findAll())
            .isEmpty();
    }
}
