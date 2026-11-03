package ca.yorku.eecs3311.spotter.agent.agents;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.agent
        .CoachAgent;
import ca.yorku.eecs3311.spotter.agent
        .LLMClient;
import ca.yorku.eecs3311.spotter.agent
        .ModelTier;
import ca.yorku.eecs3311.spotter.agent
        .PlanValidator;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent
        .ToolManager;
import ca.yorku.eecs3311.spotter.agent.tools
        .AdherenceTool;
import ca.yorku.eecs3311.spotter.agent.tools
        .AnalyticsTool;
import ca.yorku.eecs3311.spotter.agent.tools
        .LoadRangeTool;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .PlanAdherence;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .StrengthAnalytics;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;

/**
 * F12: revises the rest of a block in light of
 * how it has actually gone.
 *
 * The decision this agent exists for is the one
 * the analytics cannot make alone. A lifter whose
 * squat has not moved might be under trained,
 * over trained, or simply absent, and the right
 * response differs completely: add load, hold, or
 * stop changing the plan and go to the gym.
 * `PlanAdherence` supplies the missing half, and
 * the agent decides what to do with it.
 *
 * The gate is tighter here than anywhere else,
 * because this is the only agent that rewrites
 * training the lifter is already part way
 * through. A revision may not drop a session or a
 * competition lift, may not add load to a lift
 * that fell short of its plan, and may not raise
 * anything by more than a week's worth. The
 * second of those is the one that matters: a lift
 * falling behind is rarely a sign that it was too
 * easy.
 */
public final class ProgressionAgent
        extends CoachAgent<TrainingBlock> {

    private final TrainingBlock block;
    private final PlanAdherence adherence;
    private final ProgressionStrategy
            progression;
    private final Map<Lift, Double> currentE1RM;
    private final List<WorkoutSession> history;
    private final StrengthAnalytics analytics =
            new StrengthAnalytics();
    private final ResponseParser parser;
    private final PlanValidator validator;

    public ProgressionAgent(
            LLMClient model,
            PromptBuilder prompts,
            TrainingBlock block,
            PlanAdherence adherence,
            ProgressionStrategy progression,
            Map<Lift, Double> currentE1RM,
            List<WorkoutSession> history,
            ResponseParser parser,
            PlanValidator validator) {
        super(model, prompts);
        if (block == null) {
            throw new IllegalArgumentException(
                "There is no block to review");
        }
        if (parser == null
                || validator == null) {
            throw new IllegalArgumentException(
                "A parser and a validator are"
                + " required");
        }
        this.block = block;
        this.adherence = adherence;
        this.progression = progression;
        this.currentE1RM = currentE1RM == null
                ? Map.of()
                : Map.copyOf(currentE1RM);
        this.history = history == null
                ? List.of()
                : List.copyOf(history);
        this.parser = parser;
        this.validator = validator;
    }

    @Override
    public String name() {
        return "progression review";
    }

    @Override
    protected ModelTier tier() {
        return ModelTier.REASONING;
    }

    @Override
    protected int maxTokens() {
        return 16384;
    }

    @Override
    protected String systemPrompt() {
        return """
            You are reviewing a training block \
            the lifter is part way through and \
            deciding what the rest of it should \
            be.

            Rules you must follow.
            Call plan_adherence first. How much \
            of the plan was trained decides \
            everything else, and a lift that \
            fell behind is rarely a lift that \
            was too easy.
            A lift short of its plan may not be \
            given more weight. Hold it or \
            reduce it.
            Keep every session and every \
            competition lift. You are revising \
            the plan, not replacing it.
            No weight may rise by more than five \
            percent from one week to the next.
            Call allowed_load before writing any \
            competition lift weight.
            Return the whole block, including \
            the weeks already trained, \
            unchanged.
            Answer with one JSON object and \
            nothing else: no explanation, no \
            code fence.

            The shape is:
            {"splitName": "...", \
            "progression": "...", "weeks": \
            [{"number": 1, "sessions": \
            [{"date": "YYYY-MM-DD", "focus": \
            "...", "exercises": [{"name": \
            "...", "sets": 4, "reps": 5, \
            "rpe": 8, "loadKg": 100.0}]}]}]}
            """;
    }

    @Override
    protected String gatherContext() {
        StringBuilder context =
                new StringBuilder();
        context.append("The block started ")
            .append(block.startDate())
            .append(" and runs ")
            .append(block.weekCount())
            .append(" week(s).");

        if (progression != null) {
            context.append("\nThe lifter's"
                    + " progression: ")
                .append(progression.describe());
        }

        if (!currentE1RM.isEmpty()) {
            context.append("\n\nCurrent"
                    + " estimated maxima:");
            currentE1RM.forEach((lift, e1rm) ->
                context.append(String.format(
                    "%n  %s %.1f kg",
                    lift, e1rm)));
        }

        context.append("\n\nThe block as it"
                + " stands:");
        for (Week week : block.weeks()) {
            context.append("\n  Week ")
                .append(week.number());
            for (Session session
                    : week.sessions()) {
                context.append("\n    ")
                    .append(session.date())
                    .append(' ')
                    .append(session.focus()
                        .orElse("training"));
                for (ExercisePrescription p
                        : session
                            .prescriptions()) {
                    context.append(
                        String.format(
                        "%n      %s %d x %d at"
                        + " %.1f kg",
                        p.exercise().name(),
                        p.sets(), p.reps(),
                        p.loadKg()));
                }
            }
        }
        return context.toString();
    }

    @Override
    protected String describeTask() {
        return "Review this block and return it"
            + " with the remaining weeks"
            + " revised.";
    }

    @Override
    protected ToolManager createToolset() {
        ToolManager tools = new ToolManager()
            .register(new AdherenceTool(
                    adherence))
            .register(new AnalyticsTool(
                    analytics, history));
        if (progression != null) {
            tools.register(new LoadRangeTool(
                    progression, currentE1RM));
        }
        return tools;
    }

    @Override
    protected Optional<TrainingBlock> parse(
            String reply) {
        return parser.toBlock(reply,
                block.startDate(),
                block.origin());
    }

    @Override
    protected Optional<String> validate(
            TrainingBlock revised) {
        return validator.validateProgression(
                revised, block, adherence);
    }

    /** Why a parse failed, for the trace. */
    public List<String> parseProblems() {
        return parser.problems();
    }
}
