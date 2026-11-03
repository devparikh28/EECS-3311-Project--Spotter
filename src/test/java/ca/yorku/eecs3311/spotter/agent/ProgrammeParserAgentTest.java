package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.agent.agents
        .ProgrammeParserAgent;
import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;

/**
 * Tests for F14, adopting somebody else's
 * programme.
 *
 * The gate here is different from the one
 * generated blocks face, and the tests exist
 * mostly to pin that difference down. An adopted
 * programme is not held to the lifter's
 * progression, because nobody's progression
 * produced it and refusing everything that does
 * not match Spotter's own scheme would be
 * refusing to adopt anything. What it is still
 * held to is possibility: the movements exist,
 * the lifter can perform them, the week is not
 * busier than they train, and no load is one
 * nobody could lift.
 */
class ProgrammeParserAgentTest {

    private static final LocalDate START =
            LocalDate.of(2026, 11, 9);

    private Database database;
    private ExerciseCatalog catalog;
    private ScriptedLLMClient model;
    private LifterProfile profile;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        catalog = new SqliteExerciseCatalog(
                database);

        catalog.add(new Exercise(0, "Back Squat",
            Set.of("quads"), "squat",
            Set.of(Equipment.BARBELL),
            Lift.SQUAT));
        catalog.add(new Exercise(0,
            "Bench Press", Set.of("chest"),
            "horizontal press",
            Set.of(Equipment.BARBELL),
            Lift.BENCH));
        catalog.add(new Exercise(0, "Leg Press",
            Set.of("quads"), "squat",
            Set.of(Equipment.LEG_PRESS), null));

        profile = LifterProfile
            .with(TrainingGoal.STRENGTH,
                  ExperienceLevel.INTERMEDIATE)
            .trainingDaysPerWeek(3)
            .equipment(Set.of(Equipment.BARBELL))
            .build();

        model = new ScriptedLLMClient();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private ProgrammeParserAgent agent() {
        return agent(Map.of(Lift.SQUAT, 190.0,
                Lift.BENCH, 95.0));
    }

    private ProgrammeParserAgent agent(
            Map<Lift, Double> maxima) {
        return new ProgrammeParserAgent(model,
            new PromptBuilder(),
            "Week 1, day 1: squat 5x5 at 140,"
            + " bench 3x8 at 60.",
            profile, catalog, maxima, START,
            new ResponseParser(catalog),
            new PlanValidator());
    }

    private static String week(
            String exercise,
            int sets, int reps, double kg) {
        return "{\"splitName\":\"coach's"
            + " block\",\"weeks\":[{\"number\":1,"
            + "\"sessions\":[{\"date\":"
            + "\"2026-11-09\",\"focus\":\"day"
            + " one\",\"exercises\":[{\"name\":\""
            + exercise + "\",\"sets\":" + sets
            + ",\"reps\":" + reps
            + ",\"loadKg\":" + kg + "}]}]}]}";
    }

    @Test
    @DisplayName(
        "a programme becomes a block marked as"
        + " adopted rather than generated")
    void aProgrammeBecomesAnAdoptedBlock() {
        model.reply(week("Back Squat", 5, 5,
                140.0));

        AgentResult<TrainingBlock> result =
                agent().run();

        assertThat(result.succeeded()).isTrue();
        TrainingBlock block =
                result.value().orElseThrow();
        assertThat(block.origin())
            .isEqualTo(BlockOrigin.ADOPTED);
        assertThat(block.splitName()
                .orElseThrow())
            .isEqualTo("coach's block");
        assertThat(block.allSessions())
            .hasSize(1);
    }

    @Test
    @DisplayName(
        "a load the lifter's progression would"
        + " refuse is accepted, because somebody"
        + " else wrote this")
    void anUnusualLoadIsAccepted() {
        // 170 kg for 5 at an 190 kg max is
        // outside what any progression here
        // allows, and is a coach's choice to
        // make rather than Spotter's.
        model.reply(week("Back Squat", 5, 5,
                170.0));

        assertThat(agent().run().succeeded())
            .isTrue();
    }

    @Test
    @DisplayName(
        "a load nobody could lift is refused,"
        + " since it means the text was misread")
    void anImpossibleLoadIsRefused() {
        model.alwaysReply(week("Back Squat", 5,
                5, 400.0));

        AgentResult<TrainingBlock> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("read that line again");
    }

    @Test
    @DisplayName(
        "a movement the lifter cannot perform is"
        + " refused")
    void anUnavailableMovementIsRefused() {
        model.alwaysReply(week("Leg Press", 3,
                10, 100.0));

        AgentResult<TrainingBlock> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("cannot be performed");
    }

    @Test
    @DisplayName(
        "a week busier than the lifter trains is"
        + " refused")
    void tooManySessionsIsRefused() {
        StringBuilder sessions =
                new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                sessions.append(',');
            }
            sessions.append("{\"date\":"
                + "\"2026-11-0" + (9 + 0)
                + "\",\"focus\":\"day\","
                + "\"exercises\":[{\"name\":"
                + "\"Back Squat\",\"sets\":3,"
                + "\"reps\":5,\"loadKg\":100.0}]}");
        }
        String busy = "{\"weeks\":[{\"number\":1,"
            + "\"sessions\":[" + sessions
            + "]}]}";
        model.alwaysReply(busy);

        AgentResult<TrainingBlock> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("trains 3 days a week");
    }

    @Test
    @DisplayName(
        "a movement that is not in the catalogue"
        + " is caught at parse time")
    void anUnknownMovementIsCaught() {
        model.alwaysReply(week("Zercher Carry",
                3, 10, 60.0));

        ProgrammeParserAgent agent = agent();

        assertThat(agent.run().succeeded())
            .isFalse();
        assertThat(agent.parseProblems())
            .anyMatch(problem -> problem
                .contains("Zercher Carry"));
    }

    @Test
    @DisplayName(
        "a programme with no weights keeps the"
        + " exercises and leaves the loads at"
        + " zero")
    void aProgrammeWithoutWeightsIsFine() {
        model.reply(week("Back Squat", 5, 5,
                0.0));

        AgentResult<TrainingBlock> result =
                agent(Map.of()).run();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.value().orElseThrow()
                .allSessions().get(0)
                .prescriptions().get(0)
                .loadKg())
            .isZero();
    }

    @Test
    @DisplayName(
        "the maxima reach the prompt, so a"
        + " percentage can become a weight")
    void theMaximaReachThePrompt() {
        model.reply(week("Back Squat", 5, 5,
                140.0));

        agent().run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("190.0")
            .contains("trains 3 days a week")
            .contains("squat 5x5 at 140");
    }

    @Test
    @DisplayName(
        "with no maxima the prompt says so"
        + " rather than inviting a guess")
    void noMaximaIsStated() {
        model.reply(week("Back Squat", 5, 5,
                0.0));

        agent(Map.of()).run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("No estimated"
                    + " maxima are known");
    }

    @Test
    @DisplayName(
        "the exercise lookup is offered, since"
        + " names have to be matched to the"
        + " catalogue")
    void theLookupIsOffered() {
        model.reply(week("Back Squat", 5, 5,
                140.0));

        agent().run();

        assertThat(model.lastRequest().tools())
            .extracting(ToolSchema::name)
            .containsExactly(
                    "available_exercises");
    }

    @Test
    @DisplayName(
        "an empty programme is refused before"
        + " the model is called")
    void anEmptyProgrammeIsRefused() {
        try {
            new ProgrammeParserAgent(model,
                new PromptBuilder(), "   ",
                profile, catalog, Map.of(),
                START,
                new ResponseParser(catalog),
                new PlanValidator());
            assertThat(false).isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                .contains("no programme");
        }
    }

    @Test
    @DisplayName(
        "the model being unavailable gives a"
        + " clear failure, not a crash")
    void modelUnavailableFailsCleanly() {
        model.failWith("no API key configured");

        AgentResult<TrainingBlock> result =
                agent().run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("unavailable");
    }
}
