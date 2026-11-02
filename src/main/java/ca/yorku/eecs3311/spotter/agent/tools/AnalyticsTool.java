package ca.yorku.eecs3311.spotter.agent.tools;

import java.util.List;
import java.util.Map;

import ca.yorku.eecs3311.spotter.agent.Tool;
import ca.yorku.eecs3311.spotter.agent
        .ToolSchema;
import ca.yorku.eecs3311.spotter.domain.Lift;
import ca.yorku.eecs3311.spotter.domain.Point;
import ca.yorku.eecs3311.spotter.domain
        .StrengthAnalytics;
import ca.yorku.eecs3311.spotter.domain
        .WorkoutSession;

/**
 * Lets an agent look up how strong the lifter
 * currently is.
 *
 * The figures are computed here and handed over
 * as facts. The agent is never asked to work out
 * a one rep max from raw sets, because that has a
 * correct answer and a model would sometimes get
 * it wrong.
 */
public final class AnalyticsTool
        implements Tool {

    private final StrengthAnalytics analytics;
    private final List<WorkoutSession> history;

    public AnalyticsTool(
            StrengthAnalytics analytics,
            List<WorkoutSession> history) {
        this.analytics = analytics == null
                ? new StrengthAnalytics()
                : analytics;
        this.history = history == null
                ? List.of()
                : List.copyOf(history);
    }

    @Override
    public String name() {
        return "strength_analytics";
    }

    @Override
    public ToolSchema schema() {
        return new ToolSchema(name(),
            "Current estimated one rep max and"
            + " recent trend for a lift. Call"
            + " this before prescribing any"
            + " load. Never estimate a maximum"
            + " yourself.",
            Map.of("lift",
                "squat, bench or deadlift;"
                + " omit for all three"));
    }

    @Override
    public String execute(
            Map<String, String> arguments) {
        String requested = arguments == null
                ? null
                : arguments.get("lift");

        if (requested == null
                || requested.isBlank()) {
            StringBuilder all =
                    new StringBuilder();
            for (Lift lift : Lift.values()) {
                all.append(describe(lift))
                   .append('\n');
            }
            return all.toString().strip();
        }
        try {
            return describe(Lift.valueOf(
                requested.trim()
                    .toUpperCase(
                        java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return "\"" + requested + "\" is not"
                + " a lift. Use squat, bench or"
                + " deadlift.";
        }
    }

    private String describe(Lift lift) {
        List<Point> series =
            analytics.e1rmSeries(lift, history);
        if (series.isEmpty()) {
            return lift
                + ": no sets with an RPE"
                + " logged, so no estimate is"
                + " available.";
        }
        double current = series
                .get(series.size() - 1).value();
        String trend = analytics.trend(series)
            .map(value -> String.format(
                ", %+.1f kg across %d sessions",
                value, series.size()))
            .orElse("");
        return String.format(
            "%s: estimated 1RM %.1f kg%s.",
            lift, current, trend);
    }
}
