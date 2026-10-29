package ca.yorku.eecs3311.spotter.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import ca.yorku.eecs3311.spotter.domain.Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Lift;
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
