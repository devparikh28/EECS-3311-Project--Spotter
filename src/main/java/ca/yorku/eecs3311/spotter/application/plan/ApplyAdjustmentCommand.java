package ca.yorku.eecs3311.spotter
        .application.plan;

import ca.yorku.eecs3311.spotter.domain.Session;

/**
 * Replaces a session with a lighter one (F07).
 *
 * The edit the undo stack was designed for. A
 * lifter who eases off at seven in the morning
 * after a bad night often feels fine by the
 * evening, and should be able to put the session
 * back rather than regenerate it.
 */
public final class ApplyAdjustmentCommand
        extends ReplaceSessionCommand {

    private final double before;

    public ApplyAdjustmentCommand(
            Session original,
            Session adjusted) {
        super(adjusted);
        this.before = original == null
                ? 0
                : original.plannedTonnage();
    }

    @Override
    public String describe() {
        double after =
                replacement().plannedTonnage();
        if (before <= 0) {
            return "eased off "
                    + replacement().date();
        }
        return String.format(
            "eased off %s, %.0f kg down to"
            + " %.0f kg",
            replacement().date(), before,
            after);
    }
}
