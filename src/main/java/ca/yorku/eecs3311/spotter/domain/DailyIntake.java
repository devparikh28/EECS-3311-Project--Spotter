package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Everything eaten on one day. */
public final class DailyIntake {

    private final LocalDate date;
    private final List<FoodEntry> entries;

    public DailyIntake(LocalDate date) {
        this(date, List.of());
    }

    public DailyIntake(
            LocalDate date,
            List<FoodEntry> entries) {
        if (date == null) {
            throw new IllegalArgumentException(
                    "An intake needs a date");
        }
        this.date = date;
        this.entries = new ArrayList<>(
                entries == null
                    ? List.of()
                    : entries);
    }

    public LocalDate date() {
        return date;
    }

    public List<FoodEntry> entries() {
        return Collections.unmodifiableList(
                entries);
    }

    /** Adds an entry and returns the new total. */
    public MacroTotals add(FoodEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException(
                    "An entry is required");
        }
        entries.add(entry);
        return totals();
    }

    /** Everything eaten so far today. */
    public MacroTotals totals() {
        MacroTotals running = MacroTotals.ZERO;
        for (FoodEntry entry : entries) {
            running = running.plus(
                    entry.totals());
        }
        return running;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
