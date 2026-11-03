package ca.yorku.eecs3311.spotter.agent.tools;

import java.util.Map;

import ca.yorku.eecs3311.spotter.agent.Tool;
import ca.yorku.eecs3311.spotter.agent
        .ToolSchema;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain
        .PlanAdherence;

/**
 * Tells an agent how closely the plan was
 * actually trained.
 *
 * The figure a progression decision turns on, and
 * the one a model cannot infer from strength
 * alone. A block that is not working and a block
 * that was not done look identical from the
 * analytics, and the right response to each is
 * the opposite one.
 */
public final class AdherenceTool
        implements Tool {

    private final PlanAdherence adherence;

    public AdherenceTool(
            PlanAdherence adherence) {
        this.adherence = adherence;
    }

    @Override
    public String name() {
        return "plan_adherence";
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(),
            "How much of the plan was actually"
            + " trained: sessions done against"
            + " sessions planned, work done"
            + " against work planned, and the"
            + " heaviest load reached against the"
            + " heaviest prescribed. Call this"
            + " before deciding whether loads"
            + " should advance.",
            Map.of());
    }

    @Override
    public String execute(
            Map<String, String> arguments) {
        if (adherence == null
                || adherence.isEmpty()) {
            return "No sessions from this block"
                + " have come due yet, so there"
                + " is nothing to judge.";
        }

        StringBuilder report =
                new StringBuilder(
                    adherence.describe());

        if (adherence.liftsShortOfPlan()
                .isEmpty()) {
            report.append("\nEvery lift reached"
                + " the heaviest weight it was"
                + " prescribed.");
        } else {
            report.append("\nShort of plan: ");
            boolean first = true;
            for (Lift lift
                    : adherence
                        .liftsShortOfPlan()) {
                if (!first) {
                    report.append(", ");
                }
                first = false;
                report.append(lift);
            }
            report.append(". Loads for these"
                + " must not advance.");
        }
        return report.toString();
    }
}
