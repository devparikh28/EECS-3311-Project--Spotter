package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * A warning that a day's readings were poor
 * enough to be worth acting on.
 *
 * Carries its reasons in plain words, because the
 * lifter is told why their session is being
 * questioned rather than just that it is.
 *
 * @param date     the day flagged
 * @param reasons  what was wrong, in plain words
 * @param severity how bad
 */
public record RecoveryFlag(
        LocalDate date,
        List<String> reasons,
        Severity severity) {

    /** How poor the readings were. */
    public enum Severity {
        /** One reading is off. */
        MILD,
        /** Several readings are off. */
        SIGNIFICANT
    }

    public RecoveryFlag {
        if (date == null) {
            throw new IllegalArgumentException(
                    "A flag needs a date");
        }
        if (reasons == null
                || reasons.isEmpty()) {
            throw new IllegalArgumentException(
                    "A flag needs at least one"
                    + " reason");
        }
        reasons = List.copyOf(reasons);
        if (severity == null) {
            severity = reasons.size() > 1
                    ? Severity.SIGNIFICANT
                    : Severity.MILD;
        }
    }

    public boolean isSignificant() {
        return severity
                == Severity.SIGNIFICANT;
    }

    /** The reasons as one readable sentence. */
    public String summary() {
        return String.join("; ", reasons);
    }
}
