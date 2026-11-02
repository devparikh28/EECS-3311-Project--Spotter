package ca.yorku.eecs3311.spotter.agent.agents;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import ca.yorku.eecs3311.spotter.agent
        .CoachAgent;
import ca.yorku.eecs3311.spotter.agent
        .LLMClient;
import ca.yorku.eecs3311.spotter.agent
        .PlanValidator;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent
        .ToolManager;
import ca.yorku.eecs3311.spotter.domain
        .AttemptCalculator;
import ca.yorku.eecs3311.spotter.domain
        .MeetAttempts;
import ca.yorku.eecs3311.spotter.domain
        .StrengthAnalytics;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;

/**
 * F08: explains the three meet attempts.
 *
 * This is the clearest case in the design of the
 * model being given the part it is good at and
 * nothing else. The attempts are arithmetic:
 * AttemptCalculator works them out from the
 * estimated maximum, and they are decided before
 * the model is asked anything. What a lifter
 * standing in a warm up room actually wants is
 * not three numbers but a reason to trust them,
 * and writing that reason in terms of how the
 * block went is work no formula does well.
 *
 * So the numbers travel through untouched. The
 * parser does not read them from the reply, and
 * the gate rejects any explanation containing a
 * figure Spotter did not calculate. A model
 * writing "open at 142.5" when the plan says 140
 * is giving the lifter a wrong number in a
 * confident voice, which is worse than giving
 * them none.
 *
 * It runs on the cheaper model: this is two
 * sentences of prose over figures it has been
 * handed.
 */
public final class AttemptRationaleAgent
        extends CoachAgent<MeetAttempts> {

    private final MeetAttempts attempts;
    private final double e1rm;
    private final List<WorkoutSession> history;
    private final LocalDate meetDate;
    private final StrengthAnalytics analytics;
    private final ResponseParser parser;
    private final PlanValidator validator;

    public AttemptRationaleAgent(
            LLMClient model,
            PromptBuilder prompts,
            MeetAttempts attempts,
            double e1rm,
            List<WorkoutSession> history,
            LocalDate meetDate,
            ResponseParser parser,
            PlanValidator validator) {
        super(model, prompts);
        if (attempts == null) {
            throw new IllegalArgumentException(
                "There are no attempts to"
                + " explain");
        }
        if (e1rm <= 0) {
            throw new IllegalArgumentException(
                "The attempts came from an"
                + " estimated maximum, which"
                + " must be above zero");
        }
        if (parser == null
                || validator == null) {
            throw new IllegalArgumentException(
                "A parser and a validator are"
                + " required");
        }
        this.attempts = attempts;
        this.e1rm = e1rm;
        this.history = history == null
                ? List.of()
                : List.copyOf(history);
        this.meetDate = meetDate;
        this.analytics = new StrengthAnalytics();
        this.parser = parser;
        this.validator = validator;
    }

    @Override
    public String name() {
        return "attempt rationale";
    }

    /**
     * Which way the lift has moved, in words.
     *
     * Deliberately not a number. Anything
     * numeric in the prompt is a figure the model
     * may echo, and only the calculated ones are
     * allowed through the gate, so a trend of
     * "up 7.5 kg" would invite an explanation
     * that is then rejected.
     */
    private Optional<String> describeTrend() {
        Optional<Double> moved =
                analytics.trend(
                    analytics.e1rmSeries(
                        attempts.lift(),
                        history));
        if (moved.isEmpty()) {
            return Optional.empty();
        }
        double change = moved.get();
        if (change > 1) {
            return Optional.of("getting"
                    + " stronger");
        }
        if (change < -1) {
            return Optional.of("going"
                    + " backwards");
        }
        return Optional.of("flat");
    }

    @Override
    protected int maxTokens() {
        return 1024;
    }

    @Override
    protected String systemPrompt() {
        return """
            You are a strength coach telling a \
            lifter why their three meet attempts \
            are the right ones.

            Rules you must follow.
            The three weights have already been \
            decided. You are explaining them, \
            not choosing them.
            Use only the figures you are given. \
            Never work out a weight, a \
            percentage or a difference \
            yourself. An explanation containing \
            a number nobody calculated will be \
            rejected.
            Name all three attempts.
            Two or three sentences, spoken to \
            the lifter, no lists.
            Answer with one JSON object and \
            nothing else: no explanation around \
            it, no code fence.

            The shape is:
            {"rationale": "..."}
            """;
    }

    @Override
    protected String gatherContext() {
        StringBuilder context =
                new StringBuilder();
        context.append("Lift: ")
            .append(attempts.lift())
            .append(String.format(
                "%nEstimated maximum: %.1f kg",
                e1rm))
            .append(String.format(
                "%nOpener: %.1f kg, which is %.0f"
                + " percent of it",
                attempts.opener(),
                AttemptCalculator.OPENER_PCT
                    * 100))
            .append(String.format(
                "%nSecond: %.1f kg, %.0f percent",
                attempts.second(),
                AttemptCalculator.SECOND_PCT
                    * 100))
            .append(String.format(
                "%nThird: %.1f kg, %.0f percent",
                attempts.third(),
                AttemptCalculator.THIRD_PCT
                    * 100));

        if (meetDate != null) {
            context.append("\nMeet date: ")
                .append(meetDate);
        }

        if (!attempts.rationale().isBlank()) {
            context.append("\n\nHow these were"
                    + " calculated: ")
                .append(attempts.rationale());
        }

        describeTrend().ifPresent(trend ->
            context.append("\n\nThis lift has"
                    + " been ")
                .append(trend)
                .append(" across the block."));
        return context.toString();
    }

    @Override
    protected String describeTask() {
        return "Explain these three attempts to"
            + " the lifter.";
    }

    /**
     * No tools, deliberately.
     *
     * Every figure this agent is allowed to
     * write is already in the prompt, and the
     * gate rejects any other. A lookup returning
     * a fourth number would only tempt it into
     * prose that is then thrown away.
     */
    @Override
    protected ToolManager createToolset() {
        return new ToolManager();
    }

    @Override
    protected Optional<MeetAttempts> parse(
            String reply) {
        return parser.toRationale(reply,
                attempts);
    }

    @Override
    protected Optional<String> validate(
            MeetAttempts explained) {
        return validator.validateRationale(
                explained.rationale(),
                attempts, e1rm);
    }

    /** Why a parse failed, for the trace. */
    public List<String> parseProblems() {
        return parser.problems();
    }
}
