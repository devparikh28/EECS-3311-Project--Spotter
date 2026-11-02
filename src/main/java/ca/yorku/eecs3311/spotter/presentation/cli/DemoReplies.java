package ca.yorku.eecs3311.spotter
        .presentation.cli;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .ProgressionStrategy;

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
