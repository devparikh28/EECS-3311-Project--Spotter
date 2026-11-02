package ca.yorku.eecs3311.spotter.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.yorku.eecs3311.spotter.domain
        .AttemptCalculator;
import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain
        .DietaryConstraints;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter.domain
        .MealSuggestion;
import ca.yorku.eecs3311.spotter.domain
        .MeetAttempts;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain.Range;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;

/**
 * The gate.
 *
 * Everything else in the agent design exists so
 * that this class gets the final word. A model
 * may propose any plan it likes; only one that
 * passes here becomes training the lifter
 * actually does.
 *
 * What it checks is deliberately narrow and
 * deterministic: that every load sits inside the
 * range the lifter's progression allows, that
 * every exercise can be performed with the
 * equipment they have, and that the structure is
 * coherent. It does not judge whether a plan is
 * good, because that has no correct answer and is
 * what the model was asked for.
 *
 * Failures are phrased as instructions, because
 * the reason is sent back to the model on the
 * retry and a precise complaint is usually
 * corrected.
 */
public final class PlanValidator {

    /** Most sets one exercise may be given. */
    public static final int MAX_SETS = 12;

    /** Most reps one set may be given. */
    public static final int MAX_REPS = 30;

    /** Most sessions one week may hold. */
    public static final int MAX_SESSIONS = 7;

    /** Most foods one suggested meal may have. */
    public static final int MAX_MEAL_FOODS = 6;

    /** Smallest portion worth prescribing. */
    public static final double MIN_PORTION_G =
            10;

    /** Largest portion of one food. */
    public static final double MAX_PORTION_G =
            600;

    /**
     * The most of the day's remaining calories
     * one meal may use. A little over is
     * tolerable, since portions are rounded.
     */
    public static final double
            MAX_CALORIE_SHARE = 1.1;

    /**
     * The least it may use. A meal supplying
     * almost nothing is not an answer to being
     * hungry.
     */
    public static final double
            MIN_CALORIE_SHARE = 0.15;

    /** Longest explanation of meet attempts. */
    public static final int
            MAX_RATIONALE_CHARS = 600;

    /**
     * How close a figure in an explanation must
     * be to a calculated one to count as the
     * same number.
     */
    public static final double FIGURE_TOLERANCE =
            0.05;

    /** Any number written in an explanation. */
    private static final Pattern FIGURE =
            Pattern.compile(
                "\\d+(?:\\.\\d+)?");

    /**
     * Checks a generated block.
     *
     * @param block       what the model proposed
     * @param strategy    the lifter's progression
     * @param currentE1RM estimated maxima by lift
     * @param available   equipment the lifter has
     * @return empty when acceptable, otherwise
     *         the reason, written for the model
     */
    public Optional<String> validateBlock(
            TrainingBlock block,
            ProgressionStrategy strategy,
            Map<Lift, Double> currentE1RM,
            Set<Equipment> available) {

        if (block == null) {
            return Optional.of(
                    "No plan was produced.");
        }
        if (block.weeks().isEmpty()) {
            return Optional.of(
                "The plan has no weeks in it.");
        }

        List<String> problems =
                new ArrayList<>();

        for (Week week : block.weeks()) {
            if (week.sessions().isEmpty()) {
                problems.add("Week "
                    + week.number()
                    + " has no sessions.");
                continue;
            }
            if (week.sessions().size()
                    > MAX_SESSIONS) {
                problems.add("Week "
                    + week.number() + " has "
                    + week.sessions().size()
                    + " sessions, which is more"
                    + " than days in a week.");
            }
            for (Session session
                    : week.sessions()) {
                checkSession(session, week,
                    strategy, currentE1RM,
                    available, problems);
            }
        }

        return problems.isEmpty()
            ? Optional.empty()
            : Optional.of(
                String.join(" ", problems));
    }

    /**
     * Checks an explanation of meet attempts.
     *
     * The attempts themselves are arithmetic and
     * were decided before the model was asked
     * anything. All it supplies is the words, so
     * the only thing worth checking is that the
     * words do not contradict the numbers.
     *
     * Every figure appearing in the explanation
     * must be one Spotter actually calculated. A
     * model writing "open at 142.5 and jump to
     * 155" when the plan says 140 and 150 is
     * giving the lifter wrong numbers in a
     * confident voice, which is worse than
     * giving them none, and it is the failure
     * this method exists to catch.
     *
     * @param rationale what the model wrote
     * @param attempts  the calculated attempts
     * @param e1rm      the maximum they came from
     * @return empty when acceptable, otherwise
     *         the reason, written for the model
     */
    public Optional<String> validateRationale(
            String rationale,
            MeetAttempts attempts,
            double e1rm) {

        if (attempts == null) {
            return Optional.of("There are no"
                + " attempts to explain.");
        }
        if (rationale == null
                || rationale.isBlank()) {
            return Optional.of("No explanation"
                + " was written.");
        }
        if (rationale.length()
                > MAX_RATIONALE_CHARS) {
            return Optional.of("The explanation"
                + " is "
                + rationale.length()
                + " characters. Keep it under "
                + MAX_RATIONALE_CHARS + ".");
        }

        List<Double> allowed = List.of(
                attempts.opener(),
                attempts.second(),
                attempts.third(),
                round(e1rm),
                attempts.totalJump(),
                attempts.second()
                    - attempts.opener(),
                attempts.third()
                    - attempts.second(),
                AttemptCalculator.OPENER_PCT
                    * 100,
                AttemptCalculator.SECOND_PCT
                    * 100,
                AttemptCalculator.THIRD_PCT
                    * 100,
                AttemptCalculator
                    .PLATE_INCREMENT,
                1.0, 2.0, 3.0);

        Matcher numbers = FIGURE.matcher(
                rationale);
        while (numbers.find()) {
            String token = numbers.group();
            double value;
            try {
                value = Double.parseDouble(
                        token);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!isAllowed(value, allowed)) {
                return Optional.of("The figure "
                    + token + " is not one of"
                    + " the calculated numbers."
                    + " Use only "
                    + describe(attempts)
                    + ", and do not work out"
                    + " any figure yourself.");
            }
        }

        if (!mentionsAll(rationale, attempts)) {
            return Optional.of("The explanation"
                + " must name all three"
                + " attempts: "
                + describe(attempts) + ".");
        }
        return Optional.empty();
    }

    private static boolean isAllowed(
            double value, List<Double> allowed) {
        for (double candidate : allowed) {
            if (Math.abs(value - candidate)
                    < FIGURE_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsAll(
            String rationale,
            MeetAttempts attempts) {
        return mentions(rationale,
                    attempts.opener())
            && mentions(rationale,
                    attempts.second())
            && mentions(rationale,
                    attempts.third());
    }

    private static boolean mentions(
            String rationale, double figure) {
        Matcher numbers =
                FIGURE.matcher(rationale);
        while (numbers.find()) {
            try {
                if (Math.abs(Double.parseDouble(
                        numbers.group())
                        - figure)
                        < FIGURE_TOLERANCE) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // not a figure after all
            }
        }
        return false;
    }

    private static String describe(
            MeetAttempts attempts) {
        return String.format(
            "%.1f kg, %.1f kg and %.1f kg",
            attempts.opener(),
            attempts.second(),
            attempts.third());
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * Checks a suggested meal.
     *
     * The nutrition is never in question, because
     * MealSuggestion computes it from the
     * catalog rather than from anything the
     * model said. What is checked is whether the
     * meal suits the lifter: that every food is
     * one they eat, that the portions are
     * weights a person would actually serve, and
     * that it fits what they have left for the
     * day without using almost all of it or
     * almost none.
     *
     * @param meal      what the model proposed
     * @param remaining macros left for today
     * @param diet      what the lifter eats
     * @return empty when acceptable, otherwise
     *         the reason, written for the model
     */
    public Optional<String> validateMeal(
            MealSuggestion meal,
            MacroTotals remaining,
            DietaryConstraints diet) {

        if (meal == null) {
            return Optional.of(
                    "No meal was produced.");
        }
        if (meal.foodCount() > MAX_MEAL_FOODS) {
            return Optional.of("The meal has "
                + meal.foodCount()
                + " foods. Keep it to "
                + MAX_MEAL_FOODS
                + " or fewer.");
        }

        Optional<String> refused =
                meal.firstNotPermitted(diet)
                    .map(food -> "This lifter"
                        + " does not eat "
                        + food.name()
                        + ". Use only foods the"
                        + " available_foods tool"
                        + " returned.");
        if (refused.isPresent()) {
            return refused;
        }

        for (MealSuggestion.Portion portion
                : meal.portions()) {
            if (portion.grams()
                    < MIN_PORTION_G) {
                return Optional.of(String.format(
                    "%.0f g of %s is too little"
                    + " to bother weighing."
                    + " Use at least %.0f g.",
                    portion.grams(),
                    portion.food().name(),
                    MIN_PORTION_G));
            }
            if (portion.grams()
                    > MAX_PORTION_G) {
                return Optional.of(String.format(
                    "%.0f g of %s is more than"
                    + " anyone serves. Stay at"
                    + " or below %.0f g.",
                    portion.grams(),
                    portion.food().name(),
                    MAX_PORTION_G));
            }
        }

        if (remaining == null
                || remaining.calories() <= 0) {
            return Optional.empty();
        }

        MacroTotals totals = meal.totals();
        double share = totals.calories()
                / remaining.calories();

        if (share > MAX_CALORIE_SHARE) {
            return Optional.of(String.format(
                "The meal comes to %.0f kcal but"
                + " only %.0f remain today."
                + " Make it smaller.",
                totals.calories(),
                remaining.calories()));
        }
        if (share < MIN_CALORIE_SHARE) {
            return Optional.of(String.format(
                "The meal is only %.0f kcal"
                + " against %.0f remaining,"
                + " which will not feed anyone."
                + " Make it bigger.",
                totals.calories(),
                remaining.calories()));
        }
        return Optional.empty();
    }

    private void checkSession(
            Session session,
            Week week,
            ProgressionStrategy strategy,
            Map<Lift, Double> currentE1RM,
            Set<Equipment> available,
            List<String> problems) {

        for (ExercisePrescription prescription
                : session.prescriptions()) {

            Exercise exercise =
                    prescription.exercise();

            if (!exercise.isPerformableWith(
                    available)) {
                problems.add(exercise.name()
                    + " needs equipment this"
                    + " lifter does not have ("
                    + missing(exercise,
                        available)
                    + "). Choose something"
                    + " else.");
            }

            if (prescription.sets() > MAX_SETS) {
                problems.add(exercise.name()
                    + " is given "
                    + prescription.sets()
                    + " sets, which is more"
                    + " than anyone should do"
                    + " in one session.");
            }
            if (prescription.reps() > MAX_REPS) {
                problems.add(exercise.name()
                    + " is given "
                    + prescription.reps()
                    + " reps, which is outside"
                    + " strength training.");
            }

            checkLoad(prescription, exercise,
                week, strategy, currentE1RM,
                problems);
        }
    }

    /**
     * The check that matters most.
     *
     * A load is only judged for a competition
     * lift, because those are the ones with an
     * estimated maximum to judge against.
     * Accessory work is left alone rather than
     * compared against a number that does not
     * exist.
     */
    private void checkLoad(
            ExercisePrescription prescription,
            Exercise exercise,
            Week week,
            ProgressionStrategy strategy,
            Map<Lift, Double> currentE1RM,
            List<String> problems) {

        Optional<Lift> lift =
                exercise.competitionLift();
        if (lift.isEmpty()
                || strategy == null
                || currentE1RM == null) {
            return;
        }
        Double e1rm = currentE1RM.get(lift.get());
        if (e1rm == null || e1rm <= 0) {
            return;
        }

        double rpe = prescription.targetRpe()
                .orElse(8.0);
        Range allowed;
        try {
            allowed = strategy.loadRange(e1rm,
                prescription.reps(), rpe,
                week.number());
        } catch (IllegalArgumentException e) {
            problems.add(exercise.name()
                + ": " + e.getMessage());
            return;
        }

        if (!allowed.contains(
                prescription.loadKg())) {
            problems.add(String.format(
                "%s in week %d is prescribed"
                + " %.1f kg, but for %d reps at"
                + " RPE %.1f against an"
                + " estimated maximum of"
                + " %.1f kg the load must be"
                + " between %.1f and %.1f kg."
                + " Use a weight in that"
                + " range.",
                exercise.name(), week.number(),
                prescription.loadKg(),
                prescription.reps(), rpe, e1rm,
                allowed.minKg(),
                allowed.maxKg()));
        }
    }

    private static String missing(
            Exercise exercise,
            Set<Equipment> available) {
        List<String> absent = new ArrayList<>();
        for (Equipment needed
                : exercise.requiredEquipment()) {
            if (available == null
                    || !available.contains(
                            needed)) {
                absent.add(needed.name());
            }
        }
        return String.join(", ", absent);
    }
}
