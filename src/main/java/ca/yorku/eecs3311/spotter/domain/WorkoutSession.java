package ca.yorku.eecs3311.spotter.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One training day that was actually performed. */
public final class WorkoutSession {

    private final int id;
    private final LocalDate date;
    private final String source;
    private final List<SetEntry> sets;

    public WorkoutSession(
            int id,
            LocalDate date,
            String source,
            List<SetEntry> sets) {
        if (date == null) {
            throw new IllegalArgumentException(
                    "A session needs a date");
        }
        this.id = id;
        this.date = date;
        this.source = source == null
                ? "manual"
                : source;
        this.sets = Collections
                .unmodifiableList(
                    new ArrayList<>(
                        sets == null
                            ? List.of()
                            : sets));
    }

    public int id() {
        return id;
    }

    public LocalDate date() {
        return date;
    }

    /** Where this came from, such as "hevy". */
    public String source() {
        return source;
    }

    public List<SetEntry> sets() {
        return sets;
    }

    /** Every set performed on one lift. */
    public List<SetEntry> setsFor(Lift lift) {
        List<SetEntry> matching =
                new ArrayList<>();
        for (SetEntry set : sets) {
            if (set.exercise().competitionLift()
                    .filter(l -> l == lift)
                    .isPresent()) {
                matching.add(set);
            }
        }
        return List.copyOf(matching);
    }

    /** Total weight moved across every set. */
    public double totalTonnage() {
        double total = 0;
        for (SetEntry set : sets) {
            total += set.tonnage();
        }
        return total;
    }

    /** Total weight moved on one lift. */
    public double tonnageFor(Lift lift) {
        double total = 0;
        for (SetEntry set : setsFor(lift)) {
            total += set.tonnage();
        }
        return total;
    }

    public boolean isEmpty() {
        return sets.isEmpty();
    }

    @Override
    public String toString() {
        return date + " (" + source + ", "
                + sets.size() + " sets)";
    }
}
