package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;

/**
 * One dated value on a chart, such as an
 * estimated one rep max on a given day.
 *
 * @param date  the day
 * @param value the figure on that day
 */
public record Point(
        LocalDate date,
        double value) {

    public Point {
        if (date == null) {
            throw new IllegalArgumentException(
                    "A point needs a date");
        }
    }
}
