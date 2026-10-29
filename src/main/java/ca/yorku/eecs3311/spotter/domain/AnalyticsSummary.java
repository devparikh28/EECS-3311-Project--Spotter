package ca.yorku.eecs3311.spotter.domain;

import java.util.List;
import java.util.Optional;

/**
 * Everything the analytics know about one lift.
 *
 * Warnings carry the reasons a figure may be
 * incomplete, such as sets logged without an RPE.
 * Saying so is better than quietly presenting a
 * number built from half the data.
 *
 * @param lift       the lift
 * @param e1rmSeries estimated maxima over time
 * @param tonnage    weekly volume
 * @param bestSet    the set implying the highest
 *                   maximum, when there is one
 * @param warnings   reasons the picture is partial
 */
public record AnalyticsSummary(
        Lift lift,
        List<Point> e1rmSeries,
        List<Point> tonnage,
        Optional<SetEntry> bestSet,
        List<String> warnings) {

    public AnalyticsSummary {
        if (lift == null) {
            throw new IllegalArgumentException(
                    "A summary needs a lift");
        }
        e1rmSeries = e1rmSeries == null
                ? List.of()
                : List.copyOf(e1rmSeries);
        tonnage = tonnage == null
                ? List.of()
                : List.copyOf(tonnage);
        bestSet = bestSet == null
                ? Optional.empty()
                : bestSet;
        warnings = warnings == null
                ? List.of()
                : List.copyOf(warnings);
    }

    /** Nothing known about this lift yet. */
    public static AnalyticsSummary empty(
            Lift lift) {
        return new AnalyticsSummary(lift,
                List.of(), List.of(),
                Optional.empty(),
                List.of("No training logged for "
                        + lift));
    }

    public boolean hasData() {
        return !e1rmSeries.isEmpty()
                || !tonnage.isEmpty();
    }

    /** The most recent estimated maximum. */
    public Optional<Double> currentE1RM() {
        if (e1rmSeries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(e1rmSeries
                .get(e1rmSeries.size() - 1)
                .value());
    }

    /** The highest estimate ever reached. */
    public Optional<Double> peakE1RM() {
        return e1rmSeries.stream()
                .map(Point::value)
                .max(Double::compare);
    }
}
