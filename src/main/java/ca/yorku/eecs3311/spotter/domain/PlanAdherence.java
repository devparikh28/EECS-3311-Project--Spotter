package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How closely the lifter actually trained the
 * plan they were given.
 *
 * The missing half of every progression decision.
 * A block that is not working and a block that
 * was not done look identical from the analytics
 * alone, and the right response to each is
 * opposite: one wants changing and the other
 * wants doing. So this is computed before any
 * review, and it is arithmetic rather than
 * judgement.
 *
 * Sessions are matched by date. That is crude and
 * deliberately so: matching by content would mean
 * deciding whether a session with the right lifts
 * at the wrong loads counts as the planned one,
 * which is a judgement, and judgements belong on
 * the other side of the line.
 */
public final class PlanAdherence {

    /**
     * How close a load has to be to count as
     * the one prescribed. Plates come in 2.5 kg
     * steps and people round, so demanding an
     * exact match would report a lifter who did
     * everything right as having missed.
     */
    public static final double LOAD_TOLERANCE =
            0.05;

    /** One planned session and what became of it. */
    public record SessionOutcome(
            LocalDate date,
            double plannedTonnage,
            double actualTonnage,
            boolean trained) {

        /** Share of the planned work that was done. */
        public double completion() {
            if (plannedTonnage <= 0) {
                return trained ? 1 : 0;
            }
            return actualTonnage
                    / plannedTonnage;
        }
    }

    private final List<SessionOutcome> outcomes;
    private final Map<Lift, Double> plannedTop;
    private final Map<Lift, Double> actualTop;

    private PlanAdherence(
            List<SessionOutcome> outcomes,
            Map<Lift, Double> plannedTop,
            Map<Lift, Double> actualTop) {
        this.outcomes = List.copyOf(outcomes);
        this.plannedTop = Map.copyOf(plannedTop);
        this.actualTop = Map.copyOf(actualTop);
    }

    /**
     * Compares a block with what was logged.
     *
     * @param block    the plan the lifter was on
     * @param training everything they logged
     * @param upTo     the last day to judge, so a
     *                 block still being trained
     *                 is not marked down for
     *                 sessions that have not
     *                 happened yet
     */
    public static PlanAdherence of(
            TrainingBlock block,
            List<WorkoutSession> training,
            LocalDate upTo) {

        List<SessionOutcome> outcomes =
                new ArrayList<>();
        Map<Lift, Double> plannedTop =
                new LinkedHashMap<>();
        Map<Lift, Double> actualTop =
                new LinkedHashMap<>();

        if (block == null) {
            return new PlanAdherence(outcomes,
                    plannedTop, actualTop);
        }
        LocalDate last = upTo == null
                ? LocalDate.now()
                : upTo;
        List<WorkoutSession> logged =
                training == null
                    ? List.of()
                    : training;

        for (Session planned
                : block.allSessions()) {
            if (planned.date().isAfter(last)) {
                continue;
            }
            double actual = 0;
            boolean trained = false;
            for (WorkoutSession done : logged) {
                if (done.date().equals(
                        planned.date())) {
                    trained = true;
                    actual += done
                            .totalTonnage();
                }
            }
            outcomes.add(new SessionOutcome(
                planned.date(),
                planned.plannedTonnage(),
                actual, trained));

            recordPlannedTop(planned,
                    plannedTop);
        }

        for (WorkoutSession done : logged) {
            if (done.date().isAfter(last)) {
                continue;
            }
            for (Lift lift : Lift.values()) {
                for (SetEntry set
                        : done.setsFor(lift)) {
                    actualTop.merge(lift,
                        set.weightKg(),
                        Math::max);
                }
            }
        }

        return new PlanAdherence(outcomes,
                plannedTop, actualTop);
    }

    private static void recordPlannedTop(
            Session planned,
            Map<Lift, Double> plannedTop) {
        for (ExercisePrescription p
                : planned.prescriptions()) {
            p.exercise().competitionLift()
                .ifPresent(lift ->
                    plannedTop.merge(lift,
                        p.loadKg(), Math::max));
        }
    }

    public List<SessionOutcome> sessions() {
        return outcomes;
    }

    public int planned() {
        return outcomes.size();
    }

    public int completed() {
        return (int) outcomes.stream()
            .filter(SessionOutcome::trained)
            .count();
    }

    public int missed() {
        return planned() - completed();
    }

    /** Days planned that were trained at all. */
    public double sessionRate() {
        if (outcomes.isEmpty()) {
            return 0;
        }
        return (double) completed()
                / planned();
    }

    public double plannedTonnage() {
        return outcomes.stream()
            .mapToDouble(SessionOutcome
                    ::plannedTonnage)
            .sum();
    }

    public double actualTonnage() {
        return outcomes.stream()
            .mapToDouble(SessionOutcome
                    ::actualTonnage)
            .sum();
    }

    /**
     * Work done against work planned.
     *
     * The figure that matters most, because a
     * lifter who trained every day at half the
     * prescribed loads has a perfect session rate
     * and is not doing the programme.
     */
    public double tonnageRate() {
        double planned = plannedTonnage();
        if (planned <= 0) {
            return actualTonnage() > 0 ? 1 : 0;
        }
        return actualTonnage() / planned;
    }

    public Map<Lift, Double> plannedTopLoads() {
        return plannedTop;
    }

    public Map<Lift, Double> actualTopLoads() {
        return actualTop;
    }

    /**
     * Lifts where the heaviest load reached was
     * short of the heaviest prescribed, beyond
     * what rounding explains.
     */
    public List<Lift> liftsShortOfPlan() {
        List<Lift> short_ = new ArrayList<>();
        plannedTop.forEach((lift, planned) -> {
            double actual = actualTop
                    .getOrDefault(lift, 0.0);
            if (actual
                    < planned
                        * (1 - LOAD_TOLERANCE)) {
                short_.add(lift);
            }
        });
        return List.copyOf(short_);
    }

    public boolean isEmpty() {
        return outcomes.isEmpty();
    }

    /** The figures as lines, for a prompt. */
    public String describe() {
        if (isEmpty()) {
            return "No sessions from this block"
                + " have come due yet, so there"
                + " is nothing to judge.";
        }
        StringBuilder text =
                new StringBuilder();
        text.append(String.format(
            "Sessions: %d of %d trained (%.0f"
            + " percent).%n",
            completed(), planned(),
            sessionRate() * 100));
        text.append(String.format(
            "Work: %.0f kg done against %.0f kg"
            + " planned (%.0f percent).%n",
            actualTonnage(), plannedTonnage(),
            tonnageRate() * 100));

        plannedTop.forEach((lift, planned) ->
            text.append(String.format(
                "%s: heaviest planned %.1f kg,"
                + " heaviest done %.1f kg.%n",
                lift, planned,
                actualTop.getOrDefault(lift,
                        0.0))));
        return text.toString().strip();
    }
}
