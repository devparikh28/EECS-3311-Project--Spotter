package ca.yorku.eecs3311.spotter
        .application.plan;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ca.yorku.eecs3311.spotter.domain
        .BlockOrigin;
import ca.yorku.eecs3311.spotter.domain
        .Equipment;
import ca.yorku.eecs3311.spotter.domain.Exercise;
import ca.yorku.eecs3311.spotter.domain
        .ExercisePrescription;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .Substitution;
import ca.yorku.eecs3311.spotter.domain
        .SubstitutionReason;
import ca.yorku.eecs3311.spotter.domain
        .TrainingBlock;
import ca.yorku.eecs3311.spotter.domain.Week;

/**
 * What the undo stack is for.
 *
 * The case these tests describe is the one from
 * the Stage 1 walkthrough: a lifter eases off a
 * session in the morning and wants it back in the
 * evening. Each test is written as that story
 * rather than as a check on a data structure.
 */
class PlanEditHistoryTest {

    private static final LocalDate MONDAY =
            LocalDate.of(2026, 11, 2);

    private static final Exercise SQUAT =
            new Exercise(1, "Back Squat",
                Set.of("quads"), "squat",
                Set.of(Equipment.BARBELL,
                       Equipment.SQUAT_RACK),
                Lift.SQUAT);

    private static final Exercise LEG_PRESS =
            new Exercise(2, "Leg Press",
                Set.of("quads"), "squat",
                Set.of(Equipment.LEG_PRESS),
                null);

    @Test
    void anEasedSessionCanBePutBack() {
        TrainingBlock block = blockWith(
                session(100.0));
        PlanEditHistory history =
                new PlanEditHistory(block);

        Session original = only(block);
        Session lighter = session(85.0);

        TrainingBlock eased = history.apply(
            new ApplyAdjustmentCommand(
                original, lighter));

        assertThat(only(eased).plannedTonnage())
            .isLessThan(
                original.plannedTonnage());

        TrainingBlock back =
                history.undo().orElseThrow();

        assertThat(only(back).prescriptions()
                .get(0).loadKg())
            .isEqualTo(100.0);
    }

    @Test
    void aSwapCanBePutBack() {
        TrainingBlock block = blockWith(
                session(100.0));
        PlanEditHistory history =
                new PlanEditHistory(block);

        ExercisePrescription prescribed =
            only(block).prescriptions().get(0);
        Substitution swap = new Substitution(
                prescribed, LEG_PRESS, 120.0,
                SubstitutionReason
                    .PAIN_OR_DISCOMFORT,
                "knee is sore");

        Session changed = session(100.0);
        changed.replacePrescription(
                swap.apply());

        TrainingBlock swapped = history.apply(
            new SwapExerciseCommand(changed,
                swap));

        assertThat(only(swapped)
                .prescriptions().get(0)
                .exercise().name())
            .isEqualTo("Leg Press");

        TrainingBlock back =
                history.undo().orElseThrow();

        assertThat(only(back).prescriptions()
                .get(0).exercise().name())
            .isEqualTo("Back Squat");
    }

    @Test
    void aRevisionCanBePutBack() {
        TrainingBlock block = blockWith(
                session(100.0));
        TrainingBlock revised = blockWith(
                session(105.0));
        PlanEditHistory history =
                new PlanEditHistory(block);

        history.apply(
            new ApplyProgressionCommand(
                revised));
        assertThat(firstLoad(
                history.current()))
            .isEqualTo(105.0);

        assertThat(firstLoad(history.undo()
                .orElseThrow()))
            .isEqualTo(100.0);
    }

    @Test
    void editsAreUndoneNewestFirst() {
        TrainingBlock block = blockWith(
                session(100.0));
        PlanEditHistory history =
                new PlanEditHistory(block);

        history.apply(
            new ApplyAdjustmentCommand(
                only(block), session(90.0)));
        history.apply(
            new ApplyAdjustmentCommand(
                only(block), session(80.0)));

        assertThat(history.undo().orElseThrow())
            .extracting(PlanEditHistoryTest
                ::firstLoad)
            .isEqualTo(90.0);
        assertThat(history.undo().orElseThrow())
            .extracting(PlanEditHistoryTest
                ::firstLoad)
            .isEqualTo(100.0);
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    void anEmptyHistoryHasNothingToUndo() {
        PlanEditHistory history =
            new PlanEditHistory(
                blockWith(session(100.0)));

        assertThat(history.canUndo()).isFalse();
        assertThat(history.nextToUndo())
                .isEmpty();
        assertThat(history.undo()).isEmpty();
    }

    @Test
    void theNextUndoIsNamedForAButton() {
        TrainingBlock block = blockWith(
                session(100.0));
        PlanEditHistory history =
                new PlanEditHistory(block);

        history.apply(
            new ApplyAdjustmentCommand(
                only(block), session(85.0)));

        assertThat(history.nextToUndo()
                .orElseThrow())
            .contains("eased off")
            .contains("300")
            .contains("255");
    }

    /**
     * The stack is bounded, so a lifter who
     * fiddles all day does not accumulate an
     * unbounded list of reversals nobody will
     * ever press.
     */
    @Test
    void theOldestEditsAreForgotten() {
        TrainingBlock block = blockWith(
                session(100.0));
        PlanEditHistory history =
                new PlanEditHistory(block);

        for (int i = 0;
                i < PlanEditHistory.MAX_EDITS
                        + 5;
                i++) {
            history.apply(
                new ApplyAdjustmentCommand(
                    only(block),
                    session(100.0 - i)));
        }

        assertThat(history.size())
            .isEqualTo(
                PlanEditHistory.MAX_EDITS);
    }

    /**
     * A refresh reads the block back from the
     * database, which gives an equal block that
     * is not the same object. The stack has to
     * survive that or Undo would vanish every
     * time the screen redrew.
     */
    @Test
    void rebasingKeepsTheStack() {
        TrainingBlock block = blockWith(
                session(100.0));
        PlanEditHistory history =
                new PlanEditHistory(block);

        history.apply(
            new ApplyAdjustmentCommand(
                only(block), session(85.0)));

        TrainingBlock reloaded = blockWith(
                session(85.0));
        history.rebase(reloaded);

        assertThat(history.canUndo()).isTrue();
        assertThat(firstLoad(history.undo()
                .orElseThrow()))
            .isEqualTo(100.0);
    }

    @Test
    void everyEditIsDescribed() {
        TrainingBlock block = blockWith(
                session(100.0));
        PlanEditHistory history =
                new PlanEditHistory(block);

        history.apply(
            new ApplyAdjustmentCommand(
                only(block), session(85.0)));
        history.apply(
            new ApplyProgressionCommand(
                blockWith(session(105.0))));

        assertThat(history.describe())
            .hasSize(2)
            .last()
            .isEqualTo("revised the remaining"
                + " weeks");
    }

    @Test
    void aHistoryNeedsABlock() {
        assertThatThrownBy(() ->
                new PlanEditHistory(null))
            .isInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    void aCommandIsRequired() {
        PlanEditHistory history =
            new PlanEditHistory(
                blockWith(session(100.0)));

        assertThatThrownBy(() ->
                history.apply(null))
            .isInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    void aRevisionIsRequired() {
        assertThatThrownBy(() ->
            new ApplyProgressionCommand(null))
            .isInstanceOf(
                IllegalArgumentException.class);
    }

    private static double firstLoad(
            TrainingBlock block) {
        return only(block).prescriptions()
                .get(0).loadKg();
    }

    private static Session only(
            TrainingBlock block) {
        return block.allSessions().get(0);
    }

    private static Session session(
            double loadKg) {
        return new Session(7, MONDAY, "lower",
            List.of(new ExercisePrescription(
                70, SQUAT, 3, 1, null, loadKg)));
    }

    private static TrainingBlock blockWith(
            Session session) {
        return new TrainingBlock(1, MONDAY,
            "linear", "lower upper",
            BlockOrigin.GENERATED,
            List.of(new Week(1,
                List.of(session))));
    }
}
