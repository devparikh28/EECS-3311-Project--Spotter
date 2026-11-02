package ca.yorku.eecs3311.spotter
        .infrastructure.persistence;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.domain
        .DailyIntake;
import ca.yorku.eecs3311.spotter.domain
        .FoodEntry;
import ca.yorku.eecs3311.spotter.domain.FoodItem;
import ca.yorku.eecs3311.spotter.domain
        .MacroTarget;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.FoodCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteFoodCatalog;

/**
 * Tests for storing what the lifter eats.
 *
 * The case worth being careful about is that a
 * logged entry points at the catalog rather than
 * holding its own copy of a food's figures. If
 * that ever changed, a corrected calorie count
 * would apply to future days and not to past
 * ones, and the week's totals would quietly stop
 * adding up.
 */
class SqliteNutritionRepositoryTest {

    private static final LocalDate TODAY =
            LocalDate.of(2026, 11, 2);

    private Database database;
    private FoodCatalog foods;
    private NutritionRepository repository;
    private ProfileRepository profiles;
    private FoodItem chicken;
    private FoodItem rice;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        foods = new SqliteFoodCatalog(database);

        chicken = foods.add(new FoodItem(
            "Chicken Breast",
            new MacroTotals(165, 31, 0, 3.6),
            false, false, false));
        rice = foods.add(new FoodItem(
            "White Rice",
            new MacroTotals(130, 2.7, 28, 0.3),
            true, false, false));

        repository =
            new SqliteNutritionRepository(
                database, foods);
        profiles =
            new SqliteProfileRepository(
                database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName(
        "a logged food comes back with its"
        + " weight and its nutrition")
    void aLoggedFoodComesBack() {
        repository.log(TODAY,
            new FoodEntry(chicken, 200));

        DailyIntake day = repository
                .findByDate(TODAY)
                .orElseThrow();

        assertThat(day.entries()).hasSize(1);
        assertThat(day.entries().get(0).food()
                .name())
            .isEqualTo("Chicken Breast");
        assertThat(day.entries().get(0).grams())
            .isEqualTo(200.0);
        assertThat(day.totals().calories())
            .isEqualTo(330.0);
        assertThat(day.totals().proteinG())
            .isEqualTo(62.0);
    }

    @Test
    @DisplayName(
        "logging adds to the day rather than"
        + " replacing it")
    void loggingAccumulates() {
        repository.log(TODAY,
            new FoodEntry(chicken, 200));
        DailyIntake day = repository.log(TODAY,
            new FoodEntry(rice, 300));

        assertThat(day.entries()).hasSize(2);
        // 330 plus 390
        assertThat(day.totals().calories())
            .isEqualTo(720.0);
    }

    @Test
    @DisplayName(
        "saving a day replaces what was"
        + " recorded for it")
    void savingReplacesTheDay() {
        repository.log(TODAY,
            new FoodEntry(chicken, 200));

        repository.save(new DailyIntake(TODAY,
            List.of(new FoodEntry(rice, 100))));

        DailyIntake day = repository
                .findByDate(TODAY)
                .orElseThrow();

        assertThat(day.entries()).hasSize(1);
        assertThat(day.entries().get(0).food()
                .name())
            .isEqualTo("White Rice");
    }

    @Test
    @DisplayName(
        "entries keep the order they were"
        + " eaten in")
    void entriesKeepTheirOrder() {
        repository.log(TODAY,
            new FoodEntry(rice, 100));
        repository.log(TODAY,
            new FoodEntry(chicken, 150));
        repository.log(TODAY,
            new FoodEntry(rice, 50));

        DailyIntake day = repository
                .findByDate(TODAY)
                .orElseThrow();

        assertThat(day.entries())
            .extracting(entry -> entry.food()
                    .name())
            .containsExactly("White Rice",
                "Chicken Breast",
                "White Rice");
    }

    @Test
    @DisplayName(
        "a day nobody logged is empty rather"
        + " than an error")
    void anUnloggedDayIsEmpty() {
        assertThat(repository.findByDate(TODAY))
            .isEmpty();
        assertThat(repository.findSince(
                TODAY.minusWeeks(1)))
            .isEmpty();
    }

    @Test
    @DisplayName(
        "days come back oldest first, from the"
        + " date asked for")
    void daysComeBackInOrder() {
        repository.log(TODAY.minusDays(2),
            new FoodEntry(rice, 100));
        repository.log(TODAY.minusDays(1),
            new FoodEntry(rice, 100));
        repository.log(TODAY,
            new FoodEntry(rice, 100));

        List<DailyIntake> days =
            repository.findSince(
                TODAY.minusDays(1));

        assertThat(days).hasSize(2);
        assertThat(days.get(0).date())
            .isEqualTo(TODAY.minusDays(1));
        assertThat(days.get(1).date())
            .isEqualTo(TODAY);
    }

    @Test
    @DisplayName(
        "a food's figures come from the"
        + " catalog, so correcting one corrects"
        + " every day it appears in")
    void nutritionComesFromTheCatalog() {
        repository.log(TODAY,
            new FoodEntry(chicken, 100));

        // The same name with different figures
        foods.add(new FoodItem("Chicken Breast",
            new MacroTotals(200, 25, 0, 10),
            false, false, false));

        DailyIntake day = repository
                .findByDate(TODAY)
                .orElseThrow();

        assertThat(day.entries().get(0).grams())
            .isEqualTo(100.0);
        assertThat(day.totals().calories())
            .isEqualTo(165.0);
    }

    @Test
    @DisplayName(
        "logging a food that is not in the"
        + " catalog fails plainly")
    void anUnknownFoodIsRefused() {
        FoodItem stranger = new FoodItem(
            "Protein Sludge",
            new MacroTotals(400, 80, 10, 5),
            true, false, false);

        try {
            repository.log(TODAY,
                new FoodEntry(stranger, 100));
            assertThat(false).isTrue();
        } catch (PersistenceException e) {
            assertThat(e.getMessage())
                .contains("Protein Sludge")
                .contains("catalog");
        }
    }

    @Test
    @DisplayName(
        "deleting a day takes its entries with"
        + " it")
    void deletingCascades() {
        repository.log(TODAY,
            new FoodEntry(chicken, 200));

        repository.delete(TODAY);

        assertThat(repository.findByDate(TODAY))
            .isEmpty();
    }

    /**
     * A target belongs to a lifter, so one has
     * to exist first. The database enforces
     * that rather than trusting callers to.
     */
    private int aLifter() {
        return profiles.save(LifterProfile
            .with(TrainingGoal.STRENGTH,
                ExperienceLevel.INTERMEDIATE)
            .build()).id();
    }

    @Test
    @DisplayName(
        "no target means the lifter has not"
        + " said what they are aiming for")
    void noTargetIsAnEmptyOptional() {
        assertThat(repository.targetFor(
                aLifter()))
            .isEmpty();
    }

    @Test
    @DisplayName(
        "a target cannot be stored for a lifter"
        + " who does not exist")
    void aTargetNeedsALifter() {
        try {
            repository.saveTarget(404,
                new MacroTarget(2800, 180, 320,
                        80));
            assertThat(false).isTrue();
        } catch (PersistenceException e) {
            assertThat(e.getMessage())
                .contains("macro target");
        }
    }

    @Test
    @DisplayName(
        "a macro target survives the round"
        + " trip and can be changed")
    void aTargetSurvivesTheRoundTrip() {
        int lifter = aLifter();
        repository.saveTarget(lifter,
            new MacroTarget(2800, 180, 320, 80));

        MacroTarget read = repository
                .targetFor(lifter)
                .orElseThrow();
        assertThat(read.calories())
            .isEqualTo(2800);
        assertThat(read.proteinG())
            .isEqualTo(180.0);

        repository.saveTarget(lifter,
            new MacroTarget(2400, 190, 240, 70));

        assertThat(repository.targetFor(lifter)
                .orElseThrow().calories())
            .isEqualTo(2400);
    }

    @Test
    @DisplayName(
        "two lifters keep separate targets")
    void targetsArePerProfile() {
        int first = aLifter();
        int second = aLifter();
        repository.saveTarget(first,
            new MacroTarget(2800, 180, 320, 80));
        repository.saveTarget(second,
            new MacroTarget(2000, 150, 200, 60));

        assertThat(repository.targetFor(first)
                .orElseThrow().calories())
            .isEqualTo(2800);
        assertThat(repository.targetFor(second)
                .orElseThrow().calories())
            .isEqualTo(2000);
    }
}
