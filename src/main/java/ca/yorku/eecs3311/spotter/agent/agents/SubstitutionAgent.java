package ca.yorku.eecs3311.spotter.agent.agents;

import java.util.Optional;

import ca.yorku.eecs3311.spotter.agent
        .CoachAgent;
import ca.yorku.eecs3311.spotter.agent
        .LLMClient;
import ca.yorku.eecs3311.spotter.agent
        .PromptBuilder;
import ca.yorku.eecs3311.spotter.agent
        .ResponseParser;
import ca.yorku.eecs3311.spotter.agent
        .ToolManager;
import ca.yorku.eecs3311.spotter.agent.tools
        .ExerciseDBTool;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .LifterProfile;
import ca.yorku.eecs3311.spotter.domain
        .Limitation;
import ca.yorku.eecs3311.spotter.domain
        .Substitution;
import ca.yorku.eecs3311.spotter.domain
        .SubstitutionReason;
import ca.yorku.eecs3311.spotter
        .infrastructure.catalog.ExerciseCatalog;

/**
 * F06: swaps one movement for another.
 *
 * The lifter arrives at a gym without a rack, or
 * a shoulder complains, and one prescription in
 * today's session cannot be done. This agent
 * picks the replacement.
 *
 * It uses the cheaper model on purpose. Unlike
 * writing a block this is a choice from a list
 * the deterministic layer has already narrowed to
 * what the lifter can perform, so there is little
 * for a stronger model to add.
 *
 * Four things are checked before an answer is
 * accepted: the replacement exists in the
 * catalog, the lifter can actually perform it,
 * it trains something the original trained, and
 * the load is in a believable band around the
 * original. The last check matters because a
 * model will cheerfully suggest a dumbbell press
 * at the barbell's weight.
 */
public final class SubstitutionAgent
        extends CoachAgent<Substitution> {

    /** Lightest sensible replacement load. */
    public static final double MIN_LOAD_RATIO =
            0.4;

    /** Heaviest sensible replacement load. */
    public static final double MAX_LOAD_RATIO =
            1.1;

    private final LifterProfile profile;
    private final ExerciseCatalog catalog;
    private final ExercisePrescription original;
    private final SubstitutionReason reason;
    private final String note;
    private final ResponseParser parser;

    public SubstitutionAgent(
            LLMClient model,
            PromptBuilder prompts,
            LifterProfile profile,
            ExerciseCatalog catalog,
            ExercisePrescription original,
            SubstitutionReason reason,
            String note) {
        super(model, prompts);
        if (profile == null
                || catalog == null
                || original == null
                || reason == null) {
            throw new IllegalArgumentException(
                "A profile, catalog,"
                + " prescription and reason are"
                + " required");
        }
        this.profile = profile;
        this.catalog = catalog;
        this.original = original;
        this.reason = reason;
        this.note = note;
        this.parser =
                new ResponseParser(catalog);
    }

    @Override
    public String name() {
        return "substitution";
    }

    @Override
    protected String systemPrompt() {
        return """
            You are a strength coach choosing a \
            replacement for one exercise the \
            lifter cannot do today.

            Rules you must follow.
            Only choose from what the \
            available_exercises tool returns. \
            Never invent a movement, and never \
            choose the exercise being replaced.
            Choose something training the same \
            pattern or the same muscles.
            Give a load suited to the \
            replacement, not the original. A \
            machine or dumbbell version of a \
            barbell lift is almost always \
            lighter.
            Answer with one JSON object and \
            nothing else: no explanation, no \
            code fence.

            The shape is:
            {"name": "...", "loadKg": 40.0, \
            "rationale": "one sentence"}
            """;
    }

    @Override
    protected String gatherContext() {
        StringBuilder context =
                new StringBuilder();
        context.append("Cannot be done: ")
            .append(original.exercise().name())
            .append(String.format(
                ", prescribed as %d sets of %d"
                + " at %.1f kg",
                original.sets(),
                original.reps(),
                original.loadKg()));

        original.exercise().movementPattern()
            .ifPresent(pattern -> context
                .append("\nMovement pattern: ")
                .append(pattern));

        if (!original.exercise()
                .muscleGroups().isEmpty()) {
            context.append("\nTrains: ")
                .append(String.join(", ",
                    original.exercise()
                        .muscleGroups()));
        }

        context.append("\nReason for the swap: ")
            .append(describeReason());

        if (note != null && !note.isBlank()) {
            context.append("\nThe lifter said: ")
                .append(note.strip());
        }

        if (!profile.limitations().isEmpty()) {
            context.append("\n\nLimitations on"
                + " file:");
            for (Limitation limitation
                    : profile.limitations()) {
                context.append("\n  ")
                    .append(limitation
                        .description());
            }
        }
        return context.toString();
    }

    private String describeReason() {
        return switch (reason) {
            case EQUIPMENT_UNAVAILABLE ->
                "the equipment it needs is not"
                + " available";
            case PAIN_OR_DISCOMFORT ->
                "it hurts today";
            case LIMITATION ->
                "a limitation on file rules it"
                + " out";
            case PREFERENCE ->
                "the lifter would rather do"
                + " something else";
        };
    }

    @Override
    protected String describeTask() {
        return "Choose one replacement for "
            + original.exercise().name()
            + ". Keep "
            + original.sets() + " sets of "
            + original.reps()
            + " and give the load for the"
            + " replacement.";
    }

    @Override
    protected ToolManager createToolset() {
        return new ToolManager()
            .register(new ExerciseDBTool(
                    catalog, profile));
    }

    @Override
    protected Optional<Substitution> parse(
            String reply) {
        return parser.toSubstitution(reply,
                original, reason);
    }

    @Override
    protected Optional<String> validate(
            Substitution substitution) {

        if (substitution.isNoChange()) {
            return Optional.of(
                substitution.replacement().name()
                + " is the exercise being"
                + " replaced. Choose a"
                + " different movement.");
        }

        if (!profile.canPerform(
                substitution.replacement())) {
            return Optional.of("This lifter"
                + " cannot perform "
                + substitution.replacement()
                    .name()
                + ". Choose something the"
                + " available_exercises tool"
                + " returned.");
        }

        if (!substitution
                .trainsSomethingSimilar()) {
            return Optional.of(
                substitution.replacement().name()
                + " trains neither the same"
                + " pattern nor any of the same"
                + " muscles as "
                + substitution.replaced().name()
                + ". Choose a closer"
                + " movement.");
        }

        Optional<Double> ratio =
                substitution.loadRatio();
        if (ratio.isPresent()) {
            double value = ratio.get();
            if (value > MAX_LOAD_RATIO) {
                return Optional.of(String.format(
                    "%.1f kg is too heavy for"
                    + " %s when the original was"
                    + " %.1f kg. Stay at or"
                    + " below %.1f kg.",
                    substitution.loadKg(),
                    substitution.replacement()
                        .name(),
                    original.loadKg(),
                    original.loadKg()
                        * MAX_LOAD_RATIO));
            }
            if (value < MIN_LOAD_RATIO) {
                return Optional.of(String.format(
                    "%.1f kg is too light for"
                    + " %s when the original was"
                    + " %.1f kg. Stay at or"
                    + " above %.1f kg.",
                    substitution.loadKg(),
                    substitution.replacement()
                        .name(),
                    original.loadKg(),
                    original.loadKg()
                        * MIN_LOAD_RATIO));
            }
        }
        return Optional.empty();
    }

    /** Why a parse failed, for the trace. */
    public java.util.List<String>
            parseProblems() {
        return parser.problems();
    }
}
