package ca.yorku.eecs3311.spotter
        .presentation.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .RecoveryFlag;
import ca.yorku.eecs3311.spotter.domain.Session;

/**
 * A chat answer without a model.
 *
 * This one cannot pretend. The other prepared
 * replies stand in for a judgement the model
 * would make, and the result is close enough to
 * the real thing to demonstrate the feature. An
 * answer to an arbitrary question is not
 * something keyword matching can fake, so this
 * does not try.
 *
 * Instead it recites what Spotter knows, accurate
 * figures from the same lookups the real agent
 * would call, and says plainly that it is
 * reciting rather than answering. That is honest,
 * it exercises the whole pipeline, and it makes
 * the gap obvious: everything here is retrieval,
 * and the part that is missing is the part worth
 * paying for.
 */
public final class PreparedChat {

    private PreparedChat() {
    }

    /**
     * The JSON a chat agent would have produced.
     *
     * @param lifts   estimated maxima by lift
     * @param flags   recently flagged days
     * @param today   today's session, or null
     */
    public static String of(
            Map<Lift, Double> lifts,
            List<RecoveryFlag> flags,
            Session today) {

        List<String> parts = new ArrayList<>();
        parts.add("I cannot answer that without"
            + " a model, so here is what I know"
            + " instead.");

        if (lifts.isEmpty()) {
            parts.add("No training is logged"
                + " yet.");
        } else {
            List<String> figures =
                    new ArrayList<>();
            for (Lift lift : Lift.values()) {
                Double e1rm = lifts.get(lift);
                if (e1rm != null) {
                    figures.add(String.format(
                        "%s around %.1f kg",
                        lift.name().toLowerCase(
                            java.util.Locale
                                .ROOT),
                        e1rm));
                }
            }
            parts.add("Estimated maxima: "
                + String.join(", ", figures)
                + ".");
        }

        if (flags.isEmpty()) {
            parts.add("Nothing has been flagged"
                + " for recovery recently.");
        } else {
            RecoveryFlag latest = flags.get(
                    flags.size() - 1);
            parts.add("Recovery was flagged on "
                + latest.date() + ": "
                + latest.summary() + ".");
        }

        if (today == null) {
            parts.add("Nothing is scheduled for"
                + " today.");
        } else {
            parts.add("Today is "
                + today.focus()
                    .orElse("training")
                + ", "
                + today.prescriptions().size()
                + " exercise(s), "
                + String.format("%.0f kg",
                    today.plannedTonnage())
                + " planned.");
        }

        parts.add("Put an API key in .env and I"
            + " can actually answer the"
            + " question.");

        return "{\"reply\":\""
            + escape(String.join(" ", parts))
            + "\"}";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
