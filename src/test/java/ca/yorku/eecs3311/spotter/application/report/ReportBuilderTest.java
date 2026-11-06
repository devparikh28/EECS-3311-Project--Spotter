package ca.yorku.eecs3311.spotter
        .application.report;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.List;

import ca.yorku.eecs3311.spotter.application
        .CoachController;
import ca.yorku.eecs3311.spotter.application
        .report.TrainingReport.Section;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteBlockRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteNutritionRepository;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteProfileRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportBuilderTest {

    private Database database;
    private CoachController controller;
    private ReportBuilder builder;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        controller =
            new CoachController(database);
        builder = new ReportBuilder(controller,
            new SqliteProfileRepository(
                database),
            new SqliteBlockRepository(database,
                controller.catalog()),
            new SqliteNutritionRepository(
                database, controller.foods()));
    }

    @AfterEach
    void tearDown() {
        controller.close();
    }

    private static Section section(
            TrainingReport report, String name) {
        return report.sections().stream()
            .filter(each -> each.heading()
                .equals(name))
            .findFirst().orElseThrow();
    }

    @Test
    void anEmptyDatabaseStillYieldsEverySection() {
        TrainingReport report = builder.build(
                LocalDate.of(2026, 11, 6));

        List<String> headings = report
            .sections().stream()
            .map(Section::heading).toList();
        assertThat(headings).containsExactly(
            "Lifter", "Strength",
            "Training block", "Recovery",
            "Nutrition");
    }

    @Test
    void anEmptySectionSaysSoInsteadOfVanishing() {
        TrainingReport report = builder.build(
                LocalDate.of(2026, 11, 6));

        Section recovery =
            section(report, "Recovery");
        assertThat(recovery.hasTable()).isFalse();
        assertThat(recovery.lines()).isNotEmpty();
        assertThat(recovery.lines().get(0))
            .contains("No recovery readings");
    }

    @Test
    void trainingHistoryProducesAStrengthTable() {
        LocalDate today =
                LocalDate.of(2026, 11, 6);
        controller.loadSampleData(today);

        TrainingReport report =
                builder.build(today);

        Section strength =
            section(report, "Strength");
        assertThat(strength.hasTable()).isTrue();
        assertThat(strength.columns())
            .containsExactly("Lift", "Current",
                "Best ever", "Trend");
        assertThat(strength.rows())
            .allSatisfy(row -> assertThat(row)
                .hasSize(4));
    }
}
