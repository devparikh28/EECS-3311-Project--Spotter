package ca.yorku.eecs3311.spotter.agent.agents;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.agent
        .CoachAgent;
import ca.yorku.eecs3311.spotter.agent
        .LLMClient;
import ca.yorku.eecs3311.spotter.agent
        .ProfileValidator;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent
        .ToolManager;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain
        .ExperienceLevel;
import ca.yorku.eecs3311.spotter.domain
        .ProfileDraft;
import ca.yorku.eecs3311.spotter.domain
        .SplitPreference;
import ca.yorku.eecs3311.spotter.domain
        .TrainingGoal;

/**
 * F13: turns what a lifter says about themselves
 * into a profile.
 *
 * Onboarding is where a model earns its place
 * most cheaply. A form asking for goal,
 * experience, training days, equipment, diet and
 * injuries is nine screens that people abandon.
 * A paragraph in their own words carries all of
 * it, and reading a paragraph into structured
 * fields is exactly what a model is for.
 *
 * The risk is the mirror image. A form cannot
 * invent an answer and a model cheerfully can,
 * and an invented training frequency would shape
 * every plan the lifter is ever given while they
 * had no way of knowing a number they never
 * supplied was driving their training. So the
 * agent produces a draft rather than a profile,
 * a field nobody mentioned stays absent, and the
 * gate rejects a draft that guessed.
 *
 * It uses the cheaper model. Reading a paragraph
 * into fields is not open ended work.
 */
public final class OnboardingAgent
        extends CoachAgent<ProfileDraft> {

    private final String description;
    private final LocalDate today;
    private final ResponseParser parser;
    private final ProfileValidator validator;

    public OnboardingAgent(
            LLMClient model,
            PromptBuilder prompts,
            String description,
            LocalDate today,
            ResponseParser parser,
            ProfileValidator validator) {
        super(model, prompts);
        if (description == null
                || description.isBlank()) {
            throw new IllegalArgumentException(
                "There is nothing to read:"
                + " onboarding needs the"
                + " lifter's own description");
        }
        if (parser == null
                || validator == null) {
            throw new IllegalArgumentException(
                "A parser and a validator are"
                + " required");
        }
        this.description = description.strip();
        this.today = today == null
                ? LocalDate.now()
                : today;
        this.parser = parser;
        this.validator = validator;
    }

    @Override
    public String name() {
        return "onboarding";
    }

    @Override
    protected int maxTokens() {
        return 2048;
    }

    @Override
    protected String systemPrompt() {
        return """
            You are reading a lifter's own \
            description of themselves and \
            filling in a profile.

            Rules you must follow.
            Record only what they said. If they \
            did not mention something, leave \
            that field out entirely. Never \
            guess, and never fill a field with \
            what is typical.
            Use only the values listed below. \
            Anything else is rejected.
            Convert pounds to kilograms, and \
            set displayUnit to LB when they \
            spoke in pounds.
            A gym described as commercial, \
            fully equipped or similar means the \
            usual barbell equipment. A home \
            setup means only what they named.
            Answer with one JSON object and \
            nothing else: no explanation, no \
            code fence.

            The shape is:
            {"bodyweightKg": 82.5, \
            "weightClassKg": 83, "goal": "...", \
            "experience": "...", \
            "trainingDaysPerWeek": 4, \
            "splitPreference": "...", \
            "displayUnit": "KG", "meetDate": \
            "YYYY-MM-DD", "equipment": ["..."], \
            "diet": {"vegetarian": false, \
            "allowsDairy": true, "allowsEggs": \
            true}, "limitations": \
            [{"description": "...", \
            "avoidPatterns": ["..."], \
            "avoidExercises": ["..."], \
            "painful": true}]}

            Every field is optional except that \
            you must not invent one.
            """;
    }

    @Override
    protected String gatherContext() {
        StringBuilder context =
                new StringBuilder();
        context.append("Today is ")
            .append(today)
            .append(", which is what any"
                + " relative date is measured"
                + " from.");

        context.append("\n\nAllowed goals: ")
            .append(names(
                    TrainingGoal.values()));
        context.append("\nAllowed experience"
                + " levels: ")
            .append(names(
                    ExperienceLevel.values()));
        context.append("\nAllowed splits: ")
            .append(names(
                    SplitPreference.values()));
        context.append("\nAllowed equipment: ")
            .append(names(
                    Equipment.values()));

        context.append("\n\nThe lifter said:\n")
            .append(description);
        return context.toString();
    }

    private static String names(
            Enum<?>[] constants) {
        StringBuilder listed =
                new StringBuilder();
        for (int i = 0; i < constants.length;
                i++) {
            if (i > 0) {
                listed.append(", ");
            }
            listed.append(constants[i].name());
        }
        return listed.toString();
    }

    @Override
    protected String describeTask() {
        return "Fill in the profile from what"
            + " the lifter said, and leave out"
            + " anything they did not.";
    }

    /** No lookups: the answer is in the words. */
    @Override
    protected ToolManager createToolset() {
        return new ToolManager();
    }

    @Override
    protected Optional<ProfileDraft> parse(
            String reply) {
        return parser.toProfileDraft(reply);
    }

    @Override
    protected Optional<String> validate(
            ProfileDraft draft) {
        return validator.validate(draft, today);
    }

    /** Why a parse failed, for the trace. */
    public List<String> parseProblems() {
        return parser.problems();
    }
}
