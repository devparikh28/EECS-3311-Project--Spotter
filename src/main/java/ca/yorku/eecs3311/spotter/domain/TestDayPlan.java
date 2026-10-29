package ca.yorku.eecs3311.spotter.domain;

import java.util.List;

/**
 * A planned heavy single for a lifter who is not
 * preparing for a meet.
 *
 * Same purpose as meet attempts, which is to find
 * out what the current maximum actually is, but
 * arranged as one top single with a warm up
 * progression leading to it.
 *
 * @param lift      which lift
 * @param topSingle the single to attempt, in kg
 * @param warmups   loads leading up to it, in
 *                  order, lightest first
 * @param rationale why this number
 */
public record TestDayPlan(
        Lift lift,
        double topSingle,
        List<Double> warmups,
        String rationale) {

    public TestDayPlan {
        if (lift == null) {
            throw new IllegalArgumentException(
                    "A test day needs a lift");
        }
        if (topSingle <= 0) {
            throw new IllegalArgumentException(
                    "A top single must be above"
                    + " zero");
        }
        warmups = warmups == null
                ? List.of()
                : List.copyOf(warmups);
        for (double warmup : warmups) {
            if (warmup > topSingle) {
                throw new IllegalArgumentException(
                        "A warm up cannot be"
                        + " heavier than the top"
                        + " single");
            }
        }
        rationale = rationale == null
                ? ""
                : rationale;
    }

    /** Warm ups plus the top single. */
    public int totalSingles() {
        return warmups.size() + 1;
    }
}
