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
        .ExerciseDBTool;
import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;

/**
 * F14: adopts a programme the lifter already
 * follows.
 *
 * Someone with a coach, or a template from a
 * book, should not have to abandon it to use
 * Spotter. Once the programme is in, everything
 * else applies to it: adherence, substitutions,
 * recovery adjustments, analytics and the chat.
 *
 * This is the free text route. The lifter pastes
 * what they were given, in whatever shape it
 * arrived, and the model turns it into weeks and
 * sessions. That is reading comprehension over a
 * document with no fixed format, which is the one
 * thing here no parser could do.
 *
 * It is cheap to build, which is worth stating
 * plainly, because Stage 1 called this the
 * hardest feature in the design. The template
 * supplies the retry, ResponseParser already
 * resolves exercise names through the catalogue
 * and already builds weeks and sessions, and
 * BlockRepository already stores a block. What
 * had to be written was a prompt and a gate. That
 * saving is the whole argument for Template
 * Method, arriving two months after the decision
 * to use it.
 *
 * The gate is deliberately not the one generated
 * blocks face. An adopted programme was written
 * by somebody else, so holding it to the lifter's
 * progression would reject every programme that
 * does not match the scheme Spotter would have
 * chosen, which is most of them.
 */
public final class ProgrammeParserAgent
        extends CoachAgent<TrainingBlock> {

    private final String programme;
    private final LifterProfile profile;
    private final ExerciseCatalog catalog;
    private final Map<Lift, Double> currentE1RM;
    private final LocalDate startDate;
    private final ResponseParser parser;
    private final PlanValidator validator;

    public ProgrammeParserAgent(
            LLMClient model,
            PromptBuilder prompts,
            String programme,
            LifterProfile profile,
            ExerciseCatalog catalog,
            Map<Lift, Double> currentE1RM,
            LocalDate startDate,
            ResponseParser parser,
            PlanValidator validator) {
        super(model, prompts);
        if (programme == null
                || programme.isBlank()) {
            throw new IllegalArgumentException(
                "There is no programme to read");
        }
        if (catalog == null || parser == null
                || validator == null) {
            throw new IllegalArgumentException(
                "A catalog, parser and validator"
                + " are required");
        }
        this.programme = programme.strip();
        this.profile = profile;
        this.catalog = catalog;
        this.currentE1RM = currentE1RM == null
                ? Map.of()
                : Map.copyOf(currentE1RM);
        this.startDate = startDate == null
                ? LocalDate.now()
                : startDate;
        this.parser = parser;
        this.validator = validator;
    }

    @Override
    public String name() {
        return "programme parser";
    }

    /** Reading an unfamiliar document, so the
     * stronger model. */
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
            You are reading a training programme \
            the lifter already follows and \
            turning it into structure.

            Rules you must follow.
            Record what the programme says. You \
            are not improving it, and you are \
            not writing a programme of your \
            own.
            Every exercise must be named exactly \
            as available_exercises spells it. \
            Call that tool to find the catalogue \
            name for each movement the text \
            mentions.
            Where the text gives a percentage of \
            a maximum, work the weight out from \
            the maxima you are given. Where it \
            gives a weight, use that weight. \
            Where it gives neither, leave loadKg \
            as 0.
            If the text describes one week that \
            repeats, return it as one week \
            rather than inventing the repeats.
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
        context.append("The block starts ")
            .append(startDate)
            .append(".");

        if (profile != null) {
            context.append("\nThe lifter trains ")
                .append(profile
                    .trainingDaysPerWeek())
                .append(" days a week, so no"
                    + " week may have more"
                    + " sessions than that.");
        }

        if (currentE1RM.isEmpty()) {
            context.append("\n\nNo estimated"
                + " maxima are known, so a"
                + " percentage cannot be turned"
                + " into a weight. Leave loadKg"
                + " as 0 where the programme"
                + " gives only a percentage.");
        } else {
            context.append("\n\nEstimated"
                    + " maxima, for turning"
                    + " percentages into"
                    + " weights:");
            currentE1RM.forEach((lift, e1rm) ->
                context.append(String.format(
                    "%n  %s %.1f kg",
                    lift, e1rm)));
        }

        context.append("\n\nThe programme, as"
                + " the lifter received it:\n")
            .append(programme);
        return context.toString();
    }

    @Override
    protected String describeTask() {
        return "Turn this programme into weeks"
            + " and sessions, keeping what it"
            + " says.";
    }

    @Override
    protected ToolManager createToolset() {
        return new ToolManager()
            .register(new ExerciseDBTool(
                    catalog, profile));
    }

    @Override
    protected Optional<TrainingBlock> parse(
            String reply) {
        return parser.toBlock(reply, startDate,
                BlockOrigin.ADOPTED);
    }

    @Override
    protected Optional<String> validate(
            TrainingBlock block) {
        return validator.validateAdoptedBlock(
                block, profile, currentE1RM);
    }

    /** Why a parse failed, for the trace. */
    public List<String> parseProblems() {
        return parser.problems();
    }
}
