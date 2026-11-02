package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.agent.agents
        .OnboardingAgent;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .ProfileDraft;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter.domain
        .WeightUnit;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;

/**
 * Tests for F13, onboarding.
 *
 * The acceptance case matters least here. What
 * matters is what happens when the model fills in
 * a blank: a training frequency nobody supplied
 * would shape every plan the lifter is ever
 * given, and they would have no way of knowing.
 * So most of these tests are about absence being
 * preserved and invention being refused.
 */
class OnboardingAgentTest {

    private static final LocalDate TODAY =
            LocalDate.of(2026, 11, 2);

    private Database database;
    private ScriptedLLMClient model;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        model = new ScriptedLLMClient();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private OnboardingAgent agent(
            String description) {
        return new OnboardingAgent(model,
            new PromptBuilder(), description,
            TODAY,
            new ResponseParser(
                new SqliteExerciseCatalog(
                        database)),
            new ProfileValidator());
    }

    private static final String SAID =
        "I am 82 kg, been lifting about three"
        + " years, train four days a week at a"
        + " commercial gym, want to get stronger."
        + " My left shoulder objects to flat"
        + " benching. I am vegetarian but I eat"
        + " dairy.";

    private static String complete() {
        return "{\"bodyweightKg\":82.0,"
            + "\"goal\":\"STRENGTH\","
            + "\"experience\":\"INTERMEDIATE\","
            + "\"trainingDaysPerWeek\":4,"
            + "\"equipment\":[\"BARBELL\","
            + "\"SQUAT_RACK\",\"BENCH\"],"
            + "\"diet\":{\"vegetarian\":true,"
            + "\"allowsDairy\":true,"
            + "\"allowsEggs\":false},"
            + "\"limitations\":[{\"description\":"
            + "\"left shoulder objects to flat"
            + " benching\",\"avoidPatterns\":"
            + "[\"horizontal press\"],"
            + "\"painful\":true}]}";
    }

    @Test
    @DisplayName(
        "a paragraph becomes a profile")
    void aParagraphBecomesAProfile() {
        model.reply(complete());

        AgentResult<ProfileDraft> result =
                agent(SAID).run();

        assertThat(result.succeeded()).isTrue();
        ProfileDraft draft =
                result.value().orElseThrow();
        assertThat(draft.isComplete()).isTrue();

        LifterProfile profile =
                draft.toProfile();
        assertThat(profile.bodyweightKg())
            .isEqualTo(82.0);
        assertThat(profile.goal())
            .isEqualTo(TrainingGoal.STRENGTH);
        assertThat(profile.experience())
            .isEqualTo(ExperienceLevel
                    .INTERMEDIATE);
        assertThat(profile.trainingDaysPerWeek())
            .isEqualTo(4);
        assertThat(profile.equipment())
            .contains(Equipment.BARBELL,
                    Equipment.SQUAT_RACK);
        assertThat(profile.diet().vegetarian())
            .isTrue();
        assertThat(profile.diet().allowsDairy())
            .isTrue();
        assertThat(profile.limitations())
            .hasSize(1);
    }

    @Test
    @DisplayName(
        "a limitation keeps the pattern it"
        + " rules out")
    void aLimitationKeepsItsPattern() {
        model.reply(complete());

        ProfileDraft draft = agent(SAID).run()
                .value().orElseThrow();

        assertThat(draft.limitations().get(0)
                .avoidPatterns())
            .containsExactly(
                    "horizontal press");
        assertThat(draft.limitations().get(0)
                .painful())
            .isTrue();
    }

    @Test
    @DisplayName(
        "a field nobody mentioned is left"
        + " absent rather than guessed")
    void anUnmentionedFieldStaysAbsent() {
        model.alwaysReply("{\"bodyweightKg\":82.0,"
            + "\"goal\":\"STRENGTH\","
            + "\"experience\":\"INTERMEDIATE\","
            + "\"equipment\":[\"BARBELL\"]}");

        AgentResult<ProfileDraft> result =
            agent("I am 82 kg and want to get"
                + " stronger. I have a barbell.")
                .run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("has not said")
            .contains("how many days a week");
    }

    @Test
    @DisplayName(
        "the missing answers are phrased as"
        + " questions a lifter could be asked")
    void missingAnswersReadAsQuestions() {
        ProfileDraft draft = new ProfileDraft()
            .goal(TrainingGoal.STRENGTH);

        assertThat(draft.missing())
            .contains("how much they weigh",
                "how many days a week they"
                + " train",
                "what equipment they have");
        assertThat(draft.isComplete()).isFalse();
    }

    @Test
    @DisplayName(
        "an incomplete draft refuses to become"
        + " a profile rather than inventing"
        + " one")
    void anIncompleteDraftRefusesToBuild() {
        ProfileDraft draft = new ProfileDraft()
            .goal(TrainingGoal.STRENGTH);

        try {
            draft.toProfile();
            assertThat(false).isTrue();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                .contains("not finished")
                .contains("how much they weigh");
        }
    }

    @Test
    @DisplayName(
        "equipment Spotter does not know about"
        + " is caught at parse time")
    void inventedEquipmentIsCaught() {
        String invented =
            "{\"bodyweightKg\":82.0,"
            + "\"goal\":\"STRENGTH\","
            + "\"experience\":\"NOVICE\","
            + "\"trainingDaysPerWeek\":3,"
            + "\"equipment\":[\"REVERSE_HYPER\"]}";
        model.alwaysReply(invented);

        OnboardingAgent agent = agent(SAID);

        assertThat(agent.run().succeeded())
            .isFalse();
        assertThat(agent.parseProblems())
            .anyMatch(problem -> problem
                .contains("REVERSE_HYPER"));
    }

    @Test
    @DisplayName(
        "a goal Spotter has no progression for"
        + " is caught at parse time")
    void anInventedGoalIsCaught() {
        String invented =
            "{\"bodyweightKg\":82.0,"
            + "\"goal\":\"POWERBUILDING\","
            + "\"experience\":\"NOVICE\","
            + "\"trainingDaysPerWeek\":3,"
            + "\"equipment\":[\"BARBELL\"]}";
        model.alwaysReply(invented);

        OnboardingAgent agent = agent(SAID);
        AgentResult<ProfileDraft> result =
                agent.run();

        assertThat(result.succeeded()).isFalse();
        assertThat(agent.parseProblems())
            .anyMatch(problem -> problem
                .contains("POWERBUILDING"));
    }

    @Test
    @DisplayName(
        "a value written in words rather than"
        + " capitals still resolves")
    void looselyWrittenValuesResolve() {
        model.reply("{\"bodyweightKg\":82.0,"
            + "\"goal\":\"strength\","
            + "\"experience\":\"intermediate\","
            + "\"trainingDaysPerWeek\":4,"
            + "\"splitPreference\":\"upper"
            + " lower\","
            + "\"equipment\":[\"barbell\"]}");

        ProfileDraft draft = agent(SAID).run()
                .value().orElseThrow();

        assertThat(draft.goal().orElseThrow())
            .isEqualTo(TrainingGoal.STRENGTH);
        assertThat(draft.splitPreference())
            .isPresent();
    }

    @Test
    @DisplayName(
        "a bodyweight that cannot be real is"
        + " rejected")
    void anImpossibleBodyweightIsRejected() {
        String wrong =
            "{\"bodyweightKg\":820.0,"
            + "\"goal\":\"STRENGTH\","
            + "\"experience\":\"NOVICE\","
            + "\"trainingDaysPerWeek\":3,"
            + "\"equipment\":[\"BARBELL\"]}";
        model.alwaysReply(wrong);

        AgentResult<ProfileDraft> result =
                agent(SAID).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("cannot be right")
            .contains("820");
    }

    @Test
    @DisplayName(
        "a meet date already past is rejected,"
        + " since it means the year was"
        + " misread")
    void aPastMeetDateIsRejected() {
        String stale = "{\"bodyweightKg\":82.0,"
            + "\"goal\":\"MEET_PREP\","
            + "\"experience\":\"ADVANCED\","
            + "\"trainingDaysPerWeek\":5,"
            + "\"meetDate\":\"2025-03-01\","
            + "\"equipment\":[\"BARBELL\"]}";
        model.alwaysReply(stale);

        AgentResult<ProfileDraft> result =
                agent(SAID).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("already passed");
    }

    @Test
    @DisplayName(
        "pounds are converted and the display"
        + " unit is remembered")
    void poundsAreConverted() {
        model.reply("{\"bodyweightKg\":81.6,"
            + "\"displayUnit\":\"LB\","
            + "\"goal\":\"HYPERTROPHY\","
            + "\"experience\":\"NOVICE\","
            + "\"trainingDaysPerWeek\":3,"
            + "\"equipment\":[\"DUMBBELL\"]}");

        ProfileDraft draft = agent(
            "I weigh 180 pounds and train three"
            + " days a week with dumbbells. New"
            + " to this.").run()
                .value().orElseThrow();

        assertThat(draft.displayUnit()
                .orElseThrow())
            .isEqualTo(WeightUnit.LB);
        assertThat(draft.bodyweightKg()
                .orElseThrow())
            .isEqualTo(81.6);
    }

    @Test
    @DisplayName(
        "the prompt lists the values the model"
        + " may use and the lifter's own words")
    void thePromptListsTheAllowedValues() {
        model.reply(complete());

        agent(SAID).run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("Allowed goals")
            .contains("MEET_PREP")
            .contains("SQUAT_RACK")
            .contains("left shoulder objects")
            .contains("2026-11-02");
    }

    @Test
    @DisplayName(
        "no tools are offered, since the answer"
        + " is in the words")
    void noToolsAreOffered() {
        model.reply(complete());

        agent(SAID).run();

        assertThat(model.lastRequest().tools())
            .isEmpty();
    }

    @Test
    @DisplayName(
        "an empty description is refused before"
        + " the model is called")
    void anEmptyDescriptionIsRefused() {
        try {
            agent("   ");
            assertThat(false).isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                .contains("nothing to read");
        }
    }

    @Test
    @DisplayName(
        "the validator can tell a draft that"
        + " merely needs more answers from one"
        + " that is wrong")
    void needingAnswersIsNotTheSameAsWrong() {
        ProfileValidator validator =
                new ProfileValidator();

        ProfileDraft thin = new ProfileDraft()
            .goal(TrainingGoal.STRENGTH);
        ProfileDraft wrong = new ProfileDraft()
            .bodyweightKg(820.0);

        assertThat(validator
                .onlyNeedsMoreAnswers(thin,
                        TODAY))
            .isTrue();
        assertThat(validator
                .onlyNeedsMoreAnswers(wrong,
                        TODAY))
            .isFalse();
    }

    @Test
    @DisplayName(
        "the model being unavailable gives a"
        + " clear failure, not a crash")
    void modelUnavailableFailsCleanly() {
        model.failWith("no API key configured");

        AgentResult<ProfileDraft> result =
                agent(SAID).run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("unavailable");
    }
}
