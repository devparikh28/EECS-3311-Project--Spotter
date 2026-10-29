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
    private final String externalId;
    private final String title;
    private final List<SetEntry> sets;

    public WorkoutSession(
            int id,
            LocalDate date,
            String source,
            List<SetEntry> sets) {
        this(id, date, source, "", null, sets);
    }

    /**
     * @param externalId distinguishes sessions on
     *        the same day, since a real export can
     *        contain two
     * @param title      the name the source gave it
     */
    public WorkoutSession(
            int id,
            LocalDate date,
            String source,
            String externalId,
            String title,
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
        this.externalId = externalId == null
                ? ""
                : externalId;
        this.title = title;
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

    /**
     * Distinguishes two sessions on one day.
     * Empty when the source gave nothing.
     */
    public String externalId() {
        return externalId;
    }

    /** The name the source gave, if any. */
    public java.util.Optional<String> title() {
        return java.util.Optional
                .ofNullable(title);
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
