package ca.yorku.eecs3311.spotter.agent.agents;

import java.time.LocalDate;
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
        .AnalyticsTool;
import ca.yorku.eecs3311.spotter.agent.tools
        .ExerciseDBTool;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain
        .StrengthAnalytics;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;

/**
 * F05: writes a training block.
 *
 * The first real agent, and the one the design
 * was shaped around. It is given the lifter's
 * current strength as fact, told which movements
 * are available, and asked for structure. Every
 * load it proposes is then checked against the
 * progression the lifter's experience and goal
 * select, and a plan that fails is sent back once
 * with the specific weights that were wrong.
 *
 * It uses the stronger model, because unlike
 * choosing a substitute from a list this is open
 * ended work where the cheaper one produces
 * noticeably worse blocks.
 */
public final class BlockGenerationAgent
        extends CoachAgent<TrainingBlock> {

    private final LifterProfile profile;
    private final ExerciseCatalog catalog;
    private final List<WorkoutSession> history;
    private final ProgressionStrategy progression;
    private final StrengthAnalytics analytics;
    private final ResponseParser parser;
    private final PlanValidator validator;
    private final LocalDate startDate;
    private final int weeks;

    public BlockGenerationAgent(
            LLMClient model,
            PromptBuilder prompts,
            LifterProfile profile,
            ExerciseCatalog catalog,
            List<WorkoutSession> history,
            ProgressionStrategy progression,
            LocalDate startDate,
            int weeks) {
        super(model, prompts);
        if (profile == null
                || catalog == null
                || progression == null) {
            throw new IllegalArgumentException(
                "A profile, catalog and"
                + " progression are required");
        }
        if (weeks < 1 || weeks > 16) {
            throw new IllegalArgumentException(
                "A block must be between 1 and"
                + " 16 weeks, but " + weeks
                + " was asked for");
        }
        this.profile = profile;
        this.catalog = catalog;
        this.history = history == null
                ? List.of()
                : List.copyOf(history);
        this.progression = progression;
        this.analytics = new StrengthAnalytics();
        this.parser =
                new ResponseParser(catalog);
        this.validator = new PlanValidator();
        this.startDate = startDate == null
                ? LocalDate.now()
                : startDate;
        this.weeks = weeks;
    }

    @Override
    public String name() {
        return "block generation";
    }

    @Override
    protected ModelTier tier() {
        return ModelTier.REASONING;
    }

    /**
     * Room for a long plan.
     *
     * Four weeks of four sessions is a large
     * JSON document, and a reply cut off partway
     * through is not a bad plan but an
     * unreadable one, which wastes an attempt
     * and reports a truncation as a parse
     * failure.
     */
    @Override
    protected int maxTokens() {
        return 16384;
    }

    @Override
    protected String systemPrompt() {
        return """
            You are a strength coach writing a \
            training block.

            Rules you must follow.
            Only prescribe exercises the \
            available_exercises tool returns. \
            Never invent a movement.
            Call strength_analytics before \
            choosing any load. Never estimate a \
            one rep max yourself.
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
        Map<Lift, Double> current =
            analytics.currentE1RMs(history);

        StringBuilder context =
                new StringBuilder();
        context.append("Goal: ")
            .append(profile.goal())
            .append("\nExperience: ")
            .append(profile.experience())
            .append("\nTrains ")
            .append(profile.trainingDaysPerWeek())
            .append(" days a week")
            .append("\nProgression to follow: ")
            .append(progression.describe());

        profile.weeksUntilMeet(startDate)
            .ifPresent(until ->
                context.append("\nA meet is ")
                    .append(until)
                    .append(" weeks away, so"
                        + " the block must peak"
                        + " for it."));

        if (current.isEmpty()) {
            context.append("\n\nNo estimated"
                + " maxima are available yet.");
        } else {
            context.append(
                    "\n\nCurrent estimated"
                    + " maxima:");
            current.forEach((lift, value) ->
                context.append(
                    String.format("%n  %s %.1f kg",
                        lift, value)));
        }

        if (!profile.limitations().isEmpty()) {
            context.append("\n\nLimitations:");
            profile.limitations().forEach(
                limitation -> context
                    .append("\n  ")
                    .append(limitation
                        .description()));
        }
        return context.toString();
    }

    @Override
    protected String describeTask() {
        return "Write a " + weeks
            + " week block starting "
            + startDate
            + ". Use "
            + profile.trainingDaysPerWeek()
            + " sessions a week.";
    }

    @Override
    protected ToolManager createToolset() {
        return new ToolManager()
            .register(new AnalyticsTool(
                    analytics, history))
            .register(new ExerciseDBTool(
                    catalog, profile));
    }

    @Override
    protected Optional<TrainingBlock> parse(
            String reply) {
        return parser.toBlock(reply, startDate);
    }

    @Override
    protected Optional<String> validate(
            TrainingBlock block) {
        Optional<String> problem =
            validator.validateBlock(block,
                progression,
                analytics.currentE1RMs(history),
                profile.equipment());
        if (problem.isPresent()) {
            return problem;
        }
        if (block.weekCount() != weeks) {
            return Optional.of("The block has "
                + block.weekCount()
                + " weeks but " + weeks
                + " were asked for.");
        }
        return Optional.empty();
    }

    /** Why a parse failed, for the trace. */
    public List<String> parseProblems() {
        return parser.problems();
    }
}
