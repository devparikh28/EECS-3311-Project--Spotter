package ca.yorku.eecs3311.spotter.agent;

import static org.assertj.core.api.Assertions
        .assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.agent.agents
        .ChatAgent;
import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain
        .ChatReply;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoverySource;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain.SetEntry;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog
        .SqliteExerciseCatalog;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence.Database;
import ca.yorku.eecs3311.spotter
        .infrastructure.persistence
        .SqliteMemoryRepository;

/**
 * Tests for F11, the coach you can talk to.
 *
 * The agent with no single task, so what is
 * tested is the opposite of the other agents:
 * not that one shape of answer is correct, but
 * that the lookups are offered, the conversation
 * carries, and a note the coach wants to keep is
 * refused when it is the coach's own reasoning
 * rather than the lifter's words.
 */
class ChatAgentTest {

    private static final LocalDate TODAY =
            LocalDate.now();

    private Database database;
    private ExerciseCatalog catalog;
    private ScriptedLLMClient model;
    private MemoryManager memory;
    private LifterProfile profile;
    private List<WorkoutSession> training;
    private List<RecoveryDay> readings;
    private TrainingBlock block;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        database.migrate();
        catalog = new SqliteExerciseCatalog(
                database);

        Exercise squat = catalog.add(
            new Exercise(0, "Back Squat",
                Set.of("quads"), "squat",
                Set.of(Equipment.BARBELL),
                Lift.SQUAT));

        training = List.of(new WorkoutSession(0,
            TODAY.minusWeeks(1), "test",
            List.of(SetEntry.of(squat,
                    162.2, 5, 8))));

        readings = List.of(RecoveryDay
            .on(TODAY, RecoverySource
                    .WEARABLE_CSV)
            .recoveryScore(41)
            .sleepHours(5.0)
            .build());

        block = new TrainingBlock(1, TODAY,
            "RPE based", "upper lower",
            BlockOrigin.GENERATED,
            List.of(new Week(1, List.of(
                new Session(1, TODAY, "squat",
                    List.of(
                        new ExercisePrescription(
                            1, squat, 5, 5, 8.0,
                            140.0)))))));

        profile = LifterProfile
            .with(TrainingGoal.STRENGTH,
                  ExperienceLevel.INTERMEDIATE)
            .trainingDaysPerWeek(4)
            .equipment(Set.of(Equipment.BARBELL))
            .build();

        memory = new MemoryManager(
            new SqliteMemoryRepository(
                    database));
        model = new ScriptedLLMClient();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private ChatAgent agent(String question) {
        return agent(question,
                new ConversationHistory());
    }

    private ChatAgent agent(
            String question,
            ConversationHistory history) {
        return new ChatAgent(model,
            new PromptBuilder(), question,
            history, memory, profile, block,
            training, readings, null,
            new ResponseParser(catalog));
    }

    private static String reply(String said) {
        return "{\"reply\":\"" + said + "\"}";
    }

    @Test
    @DisplayName(
        "a plain answer comes back as written")
    void aPlainAnswerComesBack() {
        model.reply(reply("Your squat is moving"
            + " well. Keep going."));

        AgentResult<ChatReply> result =
            agent("is my squat stalling?").run();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.value().orElseThrow()
                .reply())
            .contains("moving well");
        assertThat(result.value().orElseThrow()
                .note())
            .isEmpty();
    }

    @Test
    @DisplayName(
        "all four lookups are offered, since"
        + " the question decides which are"
        + " needed")
    void theLookupsAreOffered() {
        model.reply(reply("Looks fine."));

        agent("how am I doing?").run();

        assertThat(model.lastRequest().tools())
            .extracting(ToolSchema::name)
            .contains("strength_analytics",
                "recovery_readings",
                "training_plan");
    }

    @Test
    @DisplayName(
        "the stronger model is used, because an"
        + " open question is open ended work")
    void usesTheReasoningModel() {
        model.reply(reply("Looks fine."));

        agent("how am I doing?").run();

        assertThat(model.lastRequest().tier())
            .isEqualTo(ModelTier.REASONING);
    }

    @Test
    @DisplayName(
        "the conversation so far reaches the"
        + " prompt, so the coach does not ask"
        + " the same question twice")
    void theConversationReachesThePrompt() {
        ConversationHistory history =
                new ConversationHistory();
        history.said("is my squat stalling?")
               .replied("no, it has gone up"
                    + " 7.5 kg");

        model.reply(reply("Then keep the same"
                + " progression."));

        agent("and what about my bench?",
                history).run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("is my squat stalling?")
            .contains("gone up 7.5 kg")
            .contains("and what about my"
                    + " bench?");
    }

    @Test
    @DisplayName(
        "what the coach already knows reaches"
        + " the prompt")
    void memoryReachesThePrompt() {
        memory.remember("competes in the 83 kg"
                + " class in December");

        model.reply(reply("Then we should peak"
                + " for it."));

        agent("what should I focus on?").run();

        assertThat(model.lastRequest()
                .userPrompt())
            .contains("83 kg class");
    }

    @Test
    @DisplayName(
        "a fact the lifter stated is kept")
    void aStatedFactIsKept() {
        model.reply("{\"reply\":\"Noted, I will"
            + " work around that.\","
            + "\"remember\":\"cannot train"
            + " Thursdays because of night"
            + " shifts\"}");

        ChatAgent agent = agent("I can never"
            + " make Thursdays work");
        AgentResult<ChatReply> result =
                agent.run();

        assertThat(result.succeeded()).isTrue();
        assertThat(agent.remember(
                result.value().orElseThrow()))
            .isPresent();
        assertThat(memory.describe(10))
            .contains("night shifts");
    }

    @Test
    @DisplayName(
        "a note in the coach's own voice is"
        + " sent back for correction rather"
        + " than silently dropped")
    void coachVoiceIsSentBack() {
        String ownVoice = "{\"reply\":\"Drop a"
            + " set this week.\",\"remember\":"
            + "\"I suggested dropping a set"
            + " this week\"}";
        model.reply(ownVoice)
             .reply("{\"reply\":\"Drop a set"
                + " this week.\"}");

        AgentResult<ChatReply> result =
            agent("I feel beaten up").run();

        assertThat(result.succeeded()).isTrue();
        assertThat(model.received().get(1)
                .userPrompt())
            .contains("your own reasoning");
        assertThat(memory.describe(10))
            .isEmpty();
    }

    @Test
    @DisplayName(
        "a coach voice note refused twice loses"
        + " the note, not the answer")
    void refusedTwiceStillFails() {
        String ownVoice = "{\"reply\":\"Drop a"
            + " set.\",\"remember\":\"I"
            + " recommended dropping a set\"}";
        model.alwaysReply(ownVoice);

        AgentResult<ChatReply> result =
            agent("I feel beaten up").run();

        assertThat(result.succeeded()).isFalse();
        assertThat(memory.describe(10))
            .isEmpty();
    }

    @Test
    @DisplayName(
        "an answer running to an essay is sent"
        + " back")
    void anEssayIsSentBack() {
        String essay = reply("and so on "
                .repeat(200));
        model.alwaysReply(essay);

        AgentResult<ChatReply> result =
            agent("how am I doing?").run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("few sentences");
    }

    @Test
    @DisplayName(
        "a reply with no answer in it fails at"
        + " parse")
    void anEmptyAnswerFailsAtParse() {
        model.alwaysReply("{\"remember\":\"something\"}");

        ChatAgent agent =
                agent("how am I doing?");

        assertThat(agent.run().succeeded())
            .isFalse();
        assertThat(agent.parseProblems())
            .anyMatch(problem -> problem
                .contains("reply"));
    }

    @Test
    @DisplayName(
        "the plan lookup reports the session as"
        + " written, including that it was"
        + " adjusted")
    void thePlanLookupReportsAdjustments() {
        block.allSessions().get(0)
            .setAdjustmentNote("eased off after"
                + " a short night");

        String reported = new ca.yorku.eecs3311
            .spotter.agent.tools.PlanLookupTool(
                block, TODAY)
            .execute(java.util.Map.of("when",
                    "today"));

        assertThat(reported)
            .contains("Back Squat")
            .contains("140.0 kg")
            .contains("eased off");
    }

    @Test
    @DisplayName(
        "the plan lookup says so plainly when"
        + " nothing is being trained")
    void thePlanLookupHandlesNoBlock() {
        String reported = new ca.yorku.eecs3311
            .spotter.agent.tools.PlanLookupTool(
                null, TODAY)
            .execute(java.util.Map.of());

        assertThat(reported)
            .contains("No block is being"
                    + " trained");
    }

    @Test
    @DisplayName(
        "an empty question is refused before"
        + " the model is called")
    void anEmptyQuestionIsRefused() {
        try {
            agent("   ");
            assertThat(false).isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                .contains("no question");
        }
    }

    @Test
    @DisplayName(
        "the model being unavailable gives a"
        + " clear failure, not a crash")
    void modelUnavailableFailsCleanly() {
        model.failWith("no API key configured");

        AgentResult<ChatReply> result =
            agent("how am I doing?").run();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureReason()
                .orElseThrow())
            .contains("unavailable");
    }
}
