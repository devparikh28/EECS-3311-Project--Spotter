package ca.yorku.eecs3311.spotter.agent.tools;

import java.util.Map;

import ca.yorku.eecs3311.spotter.agent.Tool;
import ca.yorku.eecs3311.spotter.agent
        .ToolSchema;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain.Range;

/**
 * Tells an agent what load is acceptable before
 * it proposes one.
 *
 * Added after watching a real run. The model
 * produced sound plans three times and had every
 * one refused for loads outside the range the
 * lifter's progression allows, usually too light
 * rather than too heavy. It was guessing what an
 * RPE implies, which is a lookup table it cannot
 * see, and the gate was the first time it found
 * out.
 *
 * Rejecting an answer teaches the model what not
 * to do, one attempt at a time. Letting it ask
 * means it never has to be told. The figure comes
 * from the same ProgressionStrategy the validator
 * checks against, so the tool and the gate cannot
 * disagree.
 */
public final class LoadRangeTool
        implements Tool {

    private final ProgressionStrategy strategy;
    private final Map<Lift, Double> currentE1RM;

    public LoadRangeTool(
            ProgressionStrategy strategy,
            Map<Lift, Double> currentE1RM) {
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "A progression is required");
        }
        this.strategy = strategy;
        this.currentE1RM = currentE1RM == null
                ? Map.of()
                : Map.copyOf(currentE1RM);
    }

    @Override
    public String name() {
        return "allowed_load";
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(),
            "The range of weights acceptable for"
            + " a competition lift at given reps"
            + " and RPE in a given week. Call"
            + " this before writing any load for"
            + " squat, bench or deadlift. A load"
            + " outside the range it returns is"
            + " rejected, so asking first saves"
            + " an attempt.",
            Map.of(
                "lift",
                    "squat, bench or deadlift",
                "reps", "reps prescribed",
                "rpe", "effort prescribed,"
                    + " such as 8",
                "week", "which week of the"
                    + " block, from 1"));
    }

    @Override
    public String execute(
            Map<String, String> arguments) {
        if (arguments == null) {
            return "Give a lift, reps, rpe and"
                + " week.";
        }

        Lift lift;
        try {
            lift = Lift.valueOf(arguments
                .getOrDefault("lift", "")
                .trim()
                .toUpperCase(
                    java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "\"" + arguments.get("lift")
                + "\" is not squat, bench or"
                + " deadlift. Only the"
                + " competition lifts have a"
                + " checked range; accessories"
                + " are left to your judgement.";
        }

        Double e1rm = currentE1RM.get(lift);
        if (e1rm == null || e1rm <= 0) {
            return "There is no estimated"
                + " maximum for " + lift
                + ", so no range is checked for"
                + " it.";
        }

        int reps = number(arguments, "reps", 5);
        double rpe = decimal(arguments, "rpe",
                8.0);
        int week = number(arguments, "week", 1);

        try {
            Range allowed = strategy.loadRange(
                    e1rm, reps, rpe, week);
            return String.format(
                "%s, %d reps at RPE %.1f in week"
                + " %d: between %.1f and %.1f"
                + " kg. Aim near %.1f kg.",
                lift, reps, rpe, week,
                allowed.minKg(), allowed.maxKg(),
                allowed.midpoint());
        } catch (IllegalArgumentException e) {
            return "That combination has no"
                + " acceptable range: "
                + e.getMessage();
        }
    }

    private static int number(
            Map<String, String> arguments,
            String key, int fallback) {
        try {
            return Integer.parseInt(arguments
                .getOrDefault(key,
                    String.valueOf(fallback))
                .trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static double decimal(
            Map<String, String> arguments,
            String key, double fallback) {
        try {
            return Double.parseDouble(arguments
                .getOrDefault(key,
                    String.valueOf(fallback))
                .trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
