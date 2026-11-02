package ca.yorku.eecs3311.spotter.agent.agents;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.agent
        .CoachAgent;
import ca.yorku.eecs3311.spotter.agent
        .ConversationHistory;
import ca.yorku.eecs3311.spotter.agent
        .LLMClient;
import ca.yorku.eecs3311.spotter.agent
        .MemoryManager;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent
        .ToolManager;
import ca.yorku.eecs3311.spotter.agent.tools
        .AnalyticsTool;
import ca.yorku.eecs3311.spotter.agent.tools
        .FoodDBTool;
import ca.yorku.eecs3311.spotter.agent.tools
        .PlanLookupTool;
import ca.yorku.eecs3311.spotter.agent.tools
        .RecoveryLookupTool;
import ca.yorku.eecs3311.spotter.domain
        .ChatReply;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryRuleEngine;
import ca.yorku.eecs3311.spotter.domain
        .StrengthAnalytics;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.FoodCatalog;

/**
 * F11: the coach you can talk to.
 *
 * The agent that justifies the architecture. It
 * has no single task, so it is given the four
 * lookups and left to decide which the question
 * needs, which is the one job on the list a
 * model does better than a menu would.
 *
 * Everything that makes it safe was built for
 * other features. The lookups return figures
 * Spotter computed rather than inviting the model
 * to estimate them. The memory gate decides what
 * carries forward. The conversation is capped.
 * The template handles the retry. The only thing
 * added here is the rule that a note must pass
 * the gate before the answer is accepted, so a
 * note in the coach's own voice is corrected
 * rather than silently dropped after the lifter
 * has already read the reply.
 *
 * It uses the stronger model, because an open
 * question about someone's training is the
 * definition of open ended work.
 */
public final class ChatAgent
        extends CoachAgent<ChatReply> {

    /** Notes put in front of the model. */
    public static final int RECALLED = 12;

    /** Days of readings the lookup may report. */
    public static final int RECOVERY_DAYS = 14;

    private final String question;
    private final ConversationHistory history;
    private final MemoryManager memory;
    private final LifterProfile profile;
    private final TrainingBlock block;
    private final List<WorkoutSession> training;
    private final List<RecoveryDay> readings;
    private final FoodCatalog foods;
    private final StrengthAnalytics analytics =
            new StrengthAnalytics();
    private final ResponseParser parser;

    public ChatAgent(
            LLMClient model,
            PromptBuilder prompts,
            String question,
            ConversationHistory history,
            MemoryManager memory,
            LifterProfile profile,
            TrainingBlock block,
            List<WorkoutSession> training,
            List<RecoveryDay> readings,
            FoodCatalog foods,
            ResponseParser parser) {
        super(model, prompts);
        if (question == null
                || question.isBlank()) {
            throw new IllegalArgumentException(
                    "There is no question to"
                    + " answer");
        }
        if (memory == null || parser == null) {
            throw new IllegalArgumentException(
                "A memory manager and a parser"
                + " are required");
        }
        this.question = question.strip();
        this.history = history == null
                ? new ConversationHistory()
                : history;
        this.memory = memory;
        this.profile = profile;
        this.block = block;
        this.training = training == null
                ? List.of()
                : List.copyOf(training);
        this.readings = readings == null
                ? List.of()
                : List.copyOf(readings);
        this.foods = foods;
        this.parser = parser;
    }

    @Override
    public String name() {
        return "chat";
    }

    @Override
    protected ca.yorku.eecs3311.spotter.agent
            .ModelTier tier() {
        return ca.yorku.eecs3311.spotter.agent
                .ModelTier.REASONING;
    }

    @Override
    protected int maxTokens() {
        return 2048;
    }

    @Override
    protected String systemPrompt() {
        return """
            You are this lifter's strength and \
            nutrition coach, answering a \
            question they have asked you.

            Rules you must follow.
            Look things up before stating them. \
            You have tools for their strength, \
            their recovery, their training plan \
            and the food catalogue, and a \
            figure you did not look up is a \
            figure you invented.
            Say you do not know when the \
            lookups do not tell you. A coach \
            who guesses is worse than one who \
            asks.
            Speak plainly, a few sentences, as \
            you would in a gym. No lists unless \
            they asked for one.
            You may record one fact the lifter \
            told you about themselves, in their \
            terms, if it would matter weeks \
            from now. Never record your own \
            advice or reasoning, and leave it \
            out entirely when nothing new was \
            said.
            Answer with one JSON object and \
            nothing else: no explanation around \
            it, no code fence.

            The shape is:
            {"reply": "...", "remember": "..."}

            Leave out remember when there is \
            nothing worth keeping.
            """;
    }

    @Override
    protected String gatherContext() {
        StringBuilder context =
                new StringBuilder();

        if (profile != null) {
            context.append("The lifter: ")
                .append(profile.goal())
                .append(", ")
                .append(profile.experience())
                .append(", trains ")
                .append(profile
                    .trainingDaysPerWeek())
                .append(" days a week, ")
                .append(String.format("%.1f kg",
                    profile.bodyweightKg()));
            profile.limitations().forEach(
                limitation -> context
                    .append("\n  limitation: ")
                    .append(limitation
                        .description()));
        } else {
            context.append("No profile has been"
                + " set up, so you know nothing"
                + " about this lifter beyond"
                + " what the lookups say.");
        }

        String remembered =
                memory.describe(RECALLED);
        if (!remembered.isBlank()) {
            context.append("\n\nWhat you know"
                    + " about them already:\n")
                .append(remembered);
        }

        if (!history.isEmpty()) {
            context.append("\n\nThe conversation"
                    + " so far:\n")
                .append(history.transcript());
        }
        return context.toString();
    }

    @Override
    protected String describeTask() {
        return "They have asked:\n" + question;
    }

    /**
     * All four lookups.
     *
     * A question about training often turns into
     * one about recovery or eating, and deciding
     * in advance which tools a question needs
     * would be the menu this feature exists to
     * replace.
     */
    @Override
    protected ToolManager createToolset() {
        ToolManager tools = new ToolManager()
            .register(new AnalyticsTool(
                    analytics, training))
            .register(new RecoveryLookupTool(
                    readings,
                    new RecoveryRuleEngine()))
            .register(new PlanLookupTool(block,
                    LocalDate.now()));
        if (foods != null) {
            tools.register(new FoodDBTool(foods,
                profile == null
                    ? null
                    : profile.diet()));
        }
        return tools;
    }

    @Override
    protected Optional<ChatReply> parse(
            String reply) {
        return parser.toChatReply(reply);
    }

    @Override
    protected Optional<String> validate(
            ChatReply reply) {
        if (reply.isTooLong()) {
            return Optional.of("That answer is "
                + reply.reply().length()
                + " characters. Keep it to a few"
                + " sentences, under "
                + ChatReply.MAX_REPLY + ".");
        }
        return reply.note()
            .flatMap(memory::wouldRefuse);
    }

    /** Why a parse failed, for the trace. */
    public List<String> parseProblems() {
        return parser.problems();
    }

    /**
     * Keeps whatever the accepted reply proposed.
     *
     * Separate from the agent run on purpose: the
     * lifter gets their answer whether or not
     * anything was worth remembering, and the
     * caller decides when to write.
     */
    public Optional<String> remember(
            ChatReply reply) {
        return reply.note()
            .flatMap(note -> memory
                .remember(note)
                .map(saved ->
                        saved.content()));
    }
}
