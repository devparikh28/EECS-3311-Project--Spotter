package ca.yorku.eecs3311.spotter
        .presentation.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.FoodItem;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .MacroTotals;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;
import ca.yorku.eecs3311.spotter.domain.Session;

/**
 * Replies to play when there is no API key.
 *
 * These are built from the lifter's real numbers
 * rather than stored as fixed text, which matters
 * for a reason worth stating: a canned plan with
 * hard coded weights would be rejected by the
 * gate the moment the lifter's estimated maximum
 * differed from whatever it was written against,
 * so a demonstration would fail for reasons
 * having nothing to do with the code being
 * demonstrated.
 *
 * Each load is the midpoint of the range the
 * lifter's progression allows, so a prepared
 * reply passes validation for any history. It is
 * a stand in for a model, not a model: it chooses
 * the obvious answer rather than a thoughtful
 * one, which is exactly the difference the real
 * thing is paying for.
 */
public final class DemoReplies {

    /** Reps the prepared plan prescribes. */
    public static final int DEMO_REPS = 5;

    /** Effort the prepared plan prescribes. */
    public static final double DEMO_RPE = 8;

    private DemoReplies() {
    }

    /**
     * A training block for F05.
     *
     * One session per trained lift per week, at a
     * load the progression allows for that week.
     *
     * @param lifts       estimated maxima by lift
     * @param progression the lifter's progression
     * @param startDate   the block's first day
     * @param weeks       how many weeks
     * @param daysPerWeek sessions a week
     */
    public static String block(
            Map<Lift, Double> lifts,
            ProgressionStrategy progression,
            LocalDate startDate,
            int weeks,
            int daysPerWeek) {

        // Enum order, not map order. A demo
        // whose sessions came out in a
        // different order on every run would
        // look like a bug.
        List<Lift> trained = new ArrayList<>();
        for (Lift lift : Lift.values()) {
            if (lifts.containsKey(lift)) {
                trained.add(lift);
            }
        }
        if (trained.isEmpty()) {
            throw new IllegalStateException(
                "There is no training history to"
                + " build a prepared plan from."
                + " Run seed or import first.");
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"splitName\":\"prepared"
            + " demonstration split\",")
            .append("\"progression\":\"")
            .append(progression.name())
            .append("\",\"weeks\":[");

        for (int week = 1; week <= weeks;
                week++) {
            if (week > 1) {
                json.append(',');
            }
            json.append("{\"number\":")
                .append(week)
                .append(",\"sessions\":[");

            int sessions = Math.min(daysPerWeek,
                    trained.size());
            for (int day = 0; day < sessions;
                    day++) {
                if (day > 0) {
                    json.append(',');
                }
                Lift lift = trained.get(
                        day % trained.size());
                appendSession(json, lift,
                    lifts.get(lift), progression,
                    startDate.plusWeeks(
                            week - 1L)
                        .plusDays(day * 2L),
                    week);
            }
            json.append("]}");
        }
        return json.append("]}").toString();
    }

    /**
     * An explanation of meet attempts for F08.
     *
     * Only the three calculated figures appear
     * in it, because the gate rejects any other,
     * and a prepared reply that failed
     * validation would demonstrate nothing.
     *
     * @param opener first attempt
     * @param second second attempt
     * @param third  third attempt
     */
    public static String attemptRationale(
            double opener,
            double second,
            double third) {
        return String.format(
            "{\"rationale\":\"Open at %.1f kg,"
            + " which you have handled for reps"
            + " in training, then take %.1f kg"
            + " for a small personal best. If"
            + " that moves well, %.1f kg is"
            + " there on the third.\"}",
            opener, second, third);
    }

    /** How much lighter a prepared swap goes. */
    public static final double
            DEMO_SUBSTITUTE_FACTOR = 0.7;

    /** How much a prepared reduction takes off. */
    public static final double
            DEMO_REDUCTION_FACTOR = 0.9;

    /** Share of what remains a prepared meal uses. */
    public static final double
            DEMO_MEAL_SHARE = 0.5;

    /**
     * A substitution for F06.
     *
     * The load is a fixed fraction of the
     * original, which lands inside the band the
     * gate allows for any starting weight. A
     * real model would reason about the
     * replacement movement instead; this only
     * has to be acceptable.
     *
     * @param replacement the movement to suggest
     * @param originalKg  the load being replaced
     */
    public static String substitution(
            Exercise replacement,
            double originalKg) {
        return String.format(
            "{\"name\":\"%s\",\"loadKg\":%.1f,"
            + "\"rationale\":\"Closest"
            + " available movement to the one"
            + " prescribed.\"}",
            replacement.name(),
            originalKg
                * DEMO_SUBSTITUTE_FACTOR);
    }

    /**
     * A session reduction for F07.
     *
     * Ten percent off every load and the sets
     * left alone. That keeps the reduced session
     * at nine tenths of the planned tonnage,
     * inside the band the gate allows whatever
     * the session looks like, where dropping a
     * set as well could fall through the floor
     * on a short session.
     *
     * @param session today's session as planned
     */
    public static String adjustment(
            Session session) {
        StringBuilder json = new StringBuilder(
            "{\"note\":\"Loads cut by a tenth"
            + " after poor recovery; the sets"
            + " stand.\",\"exercises\":[");

        boolean first = true;
        for (ExercisePrescription p
                : session.prescriptions()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"id\":")
                .append(p.id())
                .append(",\"sets\":")
                .append(p.sets())
                .append(",\"loadKg\":")
                .append(String.format("%.1f",
                    p.loadKg()
                    * DEMO_REDUCTION_FACTOR))
                .append('}');
        }
        return json.append("]}").toString();
    }

    /**
     * A meal for F10.
     *
     * Foods are taken in the order the catalog
     * offers them, which is already filtered to
     * what the lifter eats, until about half of
     * what remains for the day is covered. A
     * real model would choose a combination that
     * suits the macronutrients left rather than
     * the first things on the list.
     *
     * @param available foods the lifter eats
     * @param remaining what is left for today
     */
    public static String meal(
            List<FoodItem> available,
            MacroTotals remaining) {

        if (available.isEmpty()) {
            throw new IllegalStateException(
                "There are no foods to build a"
                + " prepared meal from. Run"
                + " seed first.");
        }

        // Protein first. Taking the catalogue
        // in its own order gives a technically
        // valid meal of whatever is
        // alphabetically first, which for a
        // lifter is not a meal at all. A model
        // would weigh far more than this; the
        // ordering only keeps the prepared
        // answer from looking absurd.
        List<FoodItem> byProtein =
                new ArrayList<>(available);
        byProtein.sort((a, b) -> Double.compare(
            proteinDensity(b),
            proteinDensity(a)));

        double wanted = remaining.calories()
                * DEMO_MEAL_SHARE;
        StringBuilder foods = new StringBuilder();
        double covered = 0;
        int used = 0;

        for (FoodItem food : byProtein) {
            if (covered >= wanted || used >= 4) {
                break;
            }
            double per100g =
                    food.per100g().calories();
            if (per100g <= 0) {
                continue;
            }
            double grams = Math.min(400,
                (wanted - covered) / per100g
                    * 100.0);
            if (grams < 20) {
                continue;
            }
            if (used > 0) {
                foods.append(',');
            }
            foods.append("{\"name\":\"")
                .append(food.name())
                .append("\",\"grams\":")
                .append(Math.round(grams))
                .append('}');
            covered += per100g * grams / 100.0;
            used++;
        }

        if (used == 0) {
            throw new IllegalStateException(
                "No sensible prepared meal could"
                + " be built from the available"
                + " foods");
        }

        return "{\"name\":\"prepared"
            + " demonstration meal\",\"foods\":["
            + foods
            + "],\"rationale\":\"About half of"
            + " what is left for the day.\"}";
    }

    /** Protein per calorie, for the ordering. */
    private static double proteinDensity(
            FoodItem food) {
        double calories =
                food.per100g().calories();
        return calories <= 0
                ? 0
                : food.per100g().proteinG()
                    / calories;
    }

    private static void appendSession(
            StringBuilder json,
            Lift lift,
            double e1rm,
            ProgressionStrategy progression,
            LocalDate date,
            int week) {

        double load = progression.loadRange(
                e1rm, DEMO_REPS, DEMO_RPE, week)
            .midpoint();

        json.append("{\"date\":\"")
            .append(date)
            .append("\",\"focus\":\"")
            .append(describe(lift))
            .append("\",\"exercises\":[")
            .append("{\"name\":\"")
            .append(exerciseFor(lift))
            .append("\",\"sets\":4,\"reps\":")
            .append(DEMO_REPS)
            .append(",\"rpe\":")
            .append((int) DEMO_RPE)
            .append(",\"loadKg\":")
            .append(String.format("%.1f", load))
            .append("}]}");
    }

    private static String describe(Lift lift) {
        return switch (lift) {
            case SQUAT -> "squat";
            case BENCH -> "bench";
            case DEADLIFT -> "deadlift";
        };
    }

    /**
     * The catalog name of the competition lift.
     * The parser resolves this through the
     * catalog like any other, so a prepared
     * reply naming something absent fails in
     * exactly the same way a model's would.
     */
    private static String exerciseFor(
            Lift lift) {
        return switch (lift) {
            case SQUAT -> "Back Squat";
            case BENCH -> "Bench Press";
            case DEADLIFT -> "Deadlift";
        };
    }
}
