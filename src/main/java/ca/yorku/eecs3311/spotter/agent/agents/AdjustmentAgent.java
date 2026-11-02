package ca.yorku.eecs3311.spotter.agent.agents;

import java.util.List;
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
        .RecoveryLookupTool;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryDay;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryRuleEngine;
import ca.yorku.eecs3311.spotter.domain.Session;

/**
 * F07: makes today's session easier after poor
 * recovery.
 *
 * The rule engine has already decided the day is
 * worth acting on. What it cannot do well is
 * decide where the reduction should fall, and a
 * blanket ten percent off everything is a blunt
 * answer: on a bad day the main lift usually
 * deserves most of the cut while the accessories
 * can stand. That judgement is what the model is
 * for.
 *
 * The rule engine remains the fallback. If the
 * model is unavailable or answers badly twice,
 * fallbackAdjustment produces the blunt reduction
 * and the lifter still trains. A feature that
 * stops working when an API is down would be a
 * poor dependency to design in.
 *
 * What the model may change is deliberately
 * narrow. It may lower a load and it may remove
 * sets. It cannot add an exercise, swap one,
 * raise anything, or empty the session, and those
 * limits are enforced here rather than requested
 * in the prompt.
 */
public final class AdjustmentAgent
        extends CoachAgent<Session> {

    /**
     * The least of the original tonnage a
     * reduced session may keep. Below this the
     * session has been gutted rather than
     * adjusted.
     */
    public static final double
            MIN_TONNAGE_FRACTION = 0.5;

    /** The same floor when recovery is poor. */
    public static final double
            MIN_TONNAGE_FRACTION_SIGNIFICANT =
                0.3;

    /**
     * The most of the original tonnage a reduced
     * session may keep. Above this nothing
     * meaningful was done.
     */
    public static final double
            MAX_TONNAGE_FRACTION = 0.95;

    private final Session original;
    private final RecoveryFlag flag;
    private final List<RecoveryDay> readings;
    private final RecoveryRuleEngine rules;
    private final ResponseParser parser;

    public AdjustmentAgent(
            LLMClient model,
            PromptBuilder prompts,
            Session original,
            RecoveryFlag flag,
            List<RecoveryDay> readings,
            RecoveryRuleEngine rules,
            ResponseParser parser) {
        super(model, prompts);
        if (original == null || flag == null) {
            throw new IllegalArgumentException(
                "A session and a recovery flag"
                + " are required");
        }
        if (original.isEmpty()) {
            throw new IllegalArgumentException(
                "There is nothing to adjust in"
                + " an empty session");
        }
        if (parser == null) {
            throw new IllegalArgumentException(
                "A response parser is"
                + " required");
        }
        this.original = original;
        this.flag = flag;
        this.readings = readings == null
                ? List.of()
                : List.copyOf(readings);
        this.rules = rules == null
                ? new RecoveryRuleEngine()
                : rules;
        this.parser = parser;
    }

    @Override
    public String name() {
        return "session adjustment";
    }

    @Override
    protected String systemPrompt() {
        return """
            You are a strength coach reducing a \
            session the lifter is not recovered \
            enough for.

            Rules you must follow.
            Call recovery_readings before \
            deciding anything. Do not judge for \
            yourself what counts as poor \
            recovery.
            Return every exercise by the id it \
            was given. You cannot add an \
            exercise, remove one, or replace \
            one.
            You may lower a load and you may \
            remove sets. You may never raise \
            either.
            Leave at least one set on every \
            exercise.
            Make the session clearly easier but \
            still worth doing. Taking almost \
            everything away is as wrong as \
            taking nothing.
            Answer with one JSON object and \
            nothing else: no explanation, no \
            code fence.

            The shape is:
            {"note": "one sentence the lifter \
            will read", "exercises": [{"id": 1, \
            "sets": 3, "loadKg": 140.0}]}
            """;
    }

    @Override
    protected String gatherContext() {
        StringBuilder context =
                new StringBuilder();
        context.append("Today is ")
            .append(original.date())
            .append(", and the readings were"
                + " flagged as ")
            .append(flag.isSignificant()
                    ? "significant"
                    : "mild")
            .append(": ")
            .append(flag.summary());

        context.append("\n\nToday's session as"
                + " written:");
        for (ExercisePrescription p
                : original.prescriptions()) {
            context.append(String.format(
                "%n  id %d: %s, %d sets of %d"
                + " at %.1f kg",
                p.id(), p.exercise().name(),
                p.sets(), p.reps(),
                p.loadKg()));
        }

        context.append(String.format(
            "%n%nPlanned total weight: %.0f kg."
            + " Keep the reduced session between"
            + " %.0f and %.0f kg.",
            original.plannedTonnage(),
            original.plannedTonnage()
                * minFraction(),
            original.plannedTonnage()
                * MAX_TONNAGE_FRACTION));
        return context.toString();
    }

    private double minFraction() {
        return flag.isSignificant()
            ? MIN_TONNAGE_FRACTION_SIGNIFICANT
            : MIN_TONNAGE_FRACTION;
    }

    @Override
    protected String describeTask() {
        return "Reduce today's session. Decide"
            + " where the cut should fall rather"
            + " than taking the same amount off"
            + " everything.";
    }

    @Override
    protected ToolManager createToolset() {
        return new ToolManager()
            .register(new RecoveryLookupTool(
                    readings, rules));
    }

    @Override
    protected Optional<Session> parse(
            String reply) {
        return parser.toAdjustedSession(reply,
                original);
    }

    @Override
    protected Optional<String> validate(
            Session adjusted) {

        if (adjusted.prescriptions().size()
                != original.prescriptions()
                    .size()) {
            return Optional.of("The session must"
                + " keep all "
                + original.prescriptions().size()
                + " exercises.");
        }

        for (ExercisePrescription before
                : original.prescriptions()) {
            Optional<ExercisePrescription> after =
                adjusted.findPrescription(
                        before.id());
            if (after.isEmpty()) {
                return Optional.of(
                    before.exercise().name()
                    + " is missing from the"
                    + " answer. Return every"
                    + " exercise by its id.");
            }
            Optional<String> problem =
                compare(before, after.get());
            if (problem.isPresent()) {
                return problem;
            }
        }

        double planned =
                original.plannedTonnage();
        if (planned <= 0) {
            return Optional.empty();
        }
        double fraction =
                adjusted.plannedTonnage()
                / planned;

        if (fraction > MAX_TONNAGE_FRACTION) {
            return Optional.of(String.format(
                "The session is barely easier:"
                + " %.0f kg against %.0f kg"
                + " planned. Take more off.",
                adjusted.plannedTonnage(),
                planned));
        }
        if (fraction < minFraction()) {
            return Optional.of(String.format(
                "The session has been cut to"
                + " %.0f kg, which is too"
                + " little to be worth doing."
                + " Stay at or above %.0f kg.",
                adjusted.plannedTonnage(),
                planned * minFraction()));
        }
        return Optional.empty();
    }

    private Optional<String> compare(
            ExercisePrescription before,
            ExercisePrescription after) {

        if (!after.exercise()
                .equals(before.exercise())) {
            return Optional.of("Exercise id "
                + before.id() + " must stay "
                + before.exercise().name()
                + ". Swapping movements is not"
                + " part of this.");
        }
        if (after.loadKg() > before.loadKg()) {
            return Optional.of(String.format(
                "%s was raised from %.1f to"
                + " %.1f kg. Loads may only go"
                + " down.",
                before.exercise().name(),
                before.loadKg(),
                after.loadKg()));
        }
        if (after.sets() > before.sets()) {
            return Optional.of(before.exercise()
                .name() + " was raised from "
                + before.sets() + " to "
                + after.sets() + " sets. Sets"
                + " may only go down.");
        }
        return Optional.empty();
    }

    /**
     * The reduction the rule engine makes on its
     * own, for when the model cannot be reached
     * or cannot produce something acceptable.
     */
    public Session fallback() {
        return rules.fallbackAdjustment(original);
    }

    /** Why a parse failed, for the trace. */
    public List<String> parseProblems() {
        return parser.problems();
    }
}
