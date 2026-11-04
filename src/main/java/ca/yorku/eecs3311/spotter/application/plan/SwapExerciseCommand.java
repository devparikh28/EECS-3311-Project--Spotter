package ca.yorku.eecs3311.spotter
        .application.plan;

import ca.yorku.eecs3311.spotter.domain.Session;
import ca.yorku.eecs3311.spotter.domain
        .Substitution;

/**
 * Swaps one movement in a session for another
 * (F06).
 */
public final class SwapExerciseCommand
        extends ReplaceSessionCommand {

    private final Substitution substitution;

    public SwapExerciseCommand(
            Session withTheSwap,
            Substitution substitution) {
        super(withTheSwap);
        if (substitution == null) {
            throw new IllegalArgumentException(
                "A substitution is required");
        }
        this.substitution = substitution;
    }

    @Override
    public String describe() {
        return String.format(
            "swapped %s for %s at %.1f kg",
            substitution.replaced().name(),
            substitution.replacement().name(),
            substitution.loadKg());
    }
}
